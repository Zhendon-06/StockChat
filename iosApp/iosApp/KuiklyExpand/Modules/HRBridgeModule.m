#import "HRBridgeModule.h"
#import "StockChatStreamRequest.h"

#import "KuiklyRenderViewController.h"
#import <AVFoundation/AVFoundation.h>
#import <OpenKuiklyIOSRender/NSObject+KR.h>
#import <PhotosUI/PhotosUI.h>

#define REQ_PARAM_KEY @"reqParam"
#define CMD_KEY @"cmd"
#define FROM_HIPPY_RENDER @"from_hippy_render"
// 扩展桥接接口
/*
 * @brief Native暴露接口到kotlin侧，提供kotlin侧调用native能力
 */

@interface HRBridgeModule () <PHPickerViewControllerDelegate, AVAudioPlayerDelegate, AVAudioRecorderDelegate>

@property (nonatomic, copy, nullable) KuiklyRenderCallback pendingImagePickerCallback;
@property (nonatomic, strong, nullable) AVAudioPlayer *audioPlayer;
// 语音录音状态只在主线程读写；与 Android 端 KRBridgeModule 的录音状态机保持一致
@property (nonatomic, strong, nullable) AVAudioRecorder *voiceRecorder;
@property (nonatomic, strong, nullable) NSURL *voiceRecordingURL;
@property (nonatomic, copy, nullable) NSString *voiceCaptureError;
@property (nonatomic, copy, nullable) KuiklyRenderCallback pendingVoiceStartCallback;
@property (nonatomic, assign) BOOL voiceStartPending;
@property (nonatomic, assign) BOOL voiceCleanupInProgress;
@property (nonatomic, assign) NSUInteger voiceStartSequence;
@property (nonatomic, strong) NSMutableArray *pendingImageResults;
@property (nonatomic, assign) NSInteger pendingImageLoadCount;
@property (nonatomic, assign) NSInteger pendingImageMaxCount;
@property (nonatomic, assign) BOOL pendingImageReadFailed;
@property (nonatomic, assign) BOOL pendingImageTruncated;
@property (nonatomic, assign) NSUInteger imagePickerRequestID;
@property (nonatomic, strong) StockChatStreamRequest *chatStreamRequest;
@property (nonatomic, weak) UIView *toastView;

@end

// 与 Android 端一致：16kHz 单声道 16bit PCM WAV，最短 300ms，最长 30s
static const double kStockChatVoiceSampleRate = 16000.0;
static const uint16_t kStockChatVoiceChannelCount = 1;
static const uint16_t kStockChatVoiceBitsPerSample = 16;
static const NSUInteger kStockChatVoiceBytesPerSample = kStockChatVoiceBitsPerSample / 8;
static const NSUInteger kStockChatVoiceMinDurationMs = 300;
static const NSTimeInterval kStockChatVoiceMaxDurationSeconds = 30.0;
static const NSUInteger kStockChatVoiceWavHeaderSize = 44;
static NSString *const kStockChatVoiceMimeType = @"audio/wav";

@implementation HRBridgeModule

@synthesize hr_rootView;

- (void)copyToPasteboard:(NSDictionary *)args {
    NSDictionary *params = [args[KR_PARAM_KEY] hr_stringToDictionary];
    NSString *content = params[@"content"];
    UIPasteboard *pasteboard = [UIPasteboard generalPasteboard];
    pasteboard.string = content;
}

- (void)log:(NSDictionary *)args {
    NSDictionary *params = [args[KR_PARAM_KEY] hr_stringToDictionary];
    NSString *content = params[@"content"];
    NSLog(@"KuiklyRender:%@", content);
}

- (void)toast:(NSDictionary *)args {
    NSDictionary *params = [args[KR_PARAM_KEY] hr_stringToDictionary];
    NSString *content = params[@"content"];
    if (![content isKindOfClass:NSString.class] || !content.length) {
        return;
    }
    dispatch_async(dispatch_get_main_queue(), ^{
        UIView *hostView = (UIView *)self.hr_rootView;
        if (!hostView.window) {
            return;
        }
        [self.toastView removeFromSuperview];
        UIView *toast = [UIView new];
        toast.translatesAutoresizingMaskIntoConstraints = NO;
        toast.userInteractionEnabled = NO;
        toast.backgroundColor = [UIColor.labelColor colorWithAlphaComponent:0.9];
        toast.layer.cornerRadius = 12;
        toast.accessibilityIdentifier = @"stockchat.toast";

        UILabel *label = [UILabel new];
        label.translatesAutoresizingMaskIntoConstraints = NO;
        label.text = content;
        label.numberOfLines = 0;
        label.textAlignment = NSTextAlignmentCenter;
        label.font = [UIFont preferredFontForTextStyle:UIFontTextStyleSubheadline];
        label.adjustsFontForContentSizeCategory = YES;
        label.textColor = UIColor.systemBackgroundColor;
        [toast addSubview:label];
        [hostView addSubview:toast];
        [NSLayoutConstraint activateConstraints:@[
            [toast.centerXAnchor constraintEqualToAnchor:hostView.safeAreaLayoutGuide.centerXAnchor],
            [toast.widthAnchor constraintLessThanOrEqualToAnchor:hostView.safeAreaLayoutGuide.widthAnchor constant:-48],
            [toast.bottomAnchor constraintEqualToAnchor:hostView.safeAreaLayoutGuide.bottomAnchor constant:-24],
            [label.leadingAnchor constraintEqualToAnchor:toast.leadingAnchor constant:16],
            [label.trailingAnchor constraintEqualToAnchor:toast.trailingAnchor constant:-16],
            [label.topAnchor constraintEqualToAnchor:toast.topAnchor constant:10],
            [label.bottomAnchor constraintEqualToAnchor:toast.bottomAnchor constant:-10],
        ]];
        self.toastView = toast;
        UIAccessibilityPostNotification(UIAccessibilityAnnouncementNotification, content);
        // Each timer removes only its own view, so rapid toggles keep the latest
        // feedback visible. A toast never captures touches or blocks navigation.
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, 2 * NSEC_PER_SEC), dispatch_get_main_queue(), ^{
            [toast removeFromSuperview];
        });
    });
}

- (void)startVoiceRecording:(NSDictionary *)args {
    KuiklyRenderCallback callback = args[KR_CALLBACK_KEY];
    dispatch_async(dispatch_get_main_queue(), ^{
        if (self.voiceRecorder != nil) {
            [self deliverVoiceCallback:callback payload:[self voiceFailurePayloadWithCode:@"ALREADY_RECORDING"
                                                                                    message:@"语音录音正在进行中。"]];
            return;
        }
        if (self.voiceStartPending) {
            [self deliverVoiceCallback:callback payload:[self voiceFailurePayloadWithCode:@"START_IN_PROGRESS"
                                                                                    message:@"正在申请麦克风权限，请稍候。"]];
            return;
        }
        if (self.voiceCleanupInProgress) {
            [self deliverVoiceCallback:callback payload:[self voiceFailurePayloadWithCode:@"RECORDING_CLEANUP_IN_PROGRESS"
                                                                                    message:@"上一段录音正在处理，请稍候。"]];
            return;
        }
        self.voiceStartPending = YES;
        self.pendingVoiceStartCallback = callback;
        self.voiceStartSequence += 1;
        NSUInteger sequence = self.voiceStartSequence;
        __weak typeof(self) weakSelf = self;
        [[AVAudioSession sharedInstance] requestRecordPermission:^(BOOL granted) {
            dispatch_async(dispatch_get_main_queue(), ^{
                __strong typeof(weakSelf) strongSelf = weakSelf;
                if (strongSelf == nil) {
                    return;
                }
                // 权限弹窗期间被 cancelVoiceRecording 打断：回调已由取消路径送出
                if (!strongSelf.voiceStartPending || sequence != strongSelf.voiceStartSequence) {
                    return;
                }
                strongSelf.voiceStartPending = NO;
                KuiklyRenderCallback startCallback = strongSelf.pendingVoiceStartCallback;
                strongSelf.pendingVoiceStartCallback = nil;
                if (!granted) {
                    [strongSelf deliverVoiceCallback:startCallback
                                             payload:[strongSelf voiceFailurePayloadWithCode:@"RECORD_AUDIO_PERMISSION_DENIED"
                                                                                       message:@"需要麦克风权限才能使用语音输入。"]];
                    return;
                }
                [strongSelf beginVoiceCaptureWithCallback:startCallback];
            });
        }];
    });
}

- (void)beginVoiceCaptureWithCallback:(KuiklyRenderCallback)callback {
    NSError *error = nil;
    AVAudioSession *session = [AVAudioSession sharedInstance];
    // PlayAndRecord 保证朗读播放不会因为切到录音而被打断
    [session setCategory:AVAudioSessionCategoryPlayAndRecord
                    mode:AVAudioSessionModeDefault
                 options:AVAudioSessionCategoryOptionDefaultToSpeaker
                   error:&error];
    if (error == nil) {
        [session setActive:YES error:&error];
    }
    if (error != nil) {
        [self deliverVoiceCallback:callback payload:[self voiceFailurePayloadWithCode:@"AUDIO_SESSION_FAILED"
                                                                                message:error.localizedDescription ?: @"无法启用麦克风。"]];
        return;
    }

    NSString *fileName = [NSString stringWithFormat:@"stockchat-voice-%@.wav", NSUUID.UUID.UUIDString];
    NSURL *fileURL = [NSURL fileURLWithPath:[NSTemporaryDirectory() stringByAppendingPathComponent:fileName]];
    NSDictionary *settings = @{
        AVFormatIDKey: @(kAudioFormatLinearPCM),
        AVSampleRateKey: @(kStockChatVoiceSampleRate),
        AVNumberOfChannelsKey: @(kStockChatVoiceChannelCount),
        AVLinearPCMBitDepthKey: @(kStockChatVoiceBitsPerSample),
        AVLinearPCMIsBigEndianKey: @NO,
        AVLinearPCMIsFloatKey: @NO,
        AVLinearPCMIsNonInterleaved: @NO,
    };
    AVAudioRecorder *recorder = [[AVAudioRecorder alloc] initWithURL:fileURL settings:settings error:&error];
    if (recorder == nil || error != nil) {
        [self deliverVoiceCallback:callback payload:[self voiceFailurePayloadWithCode:@"AUDIO_RECORD_INIT_FAILED"
                                                                                message:error.localizedDescription ?: @"设备不支持 16kHz 单声道录音。"]];
        return;
    }
    recorder.delegate = self;
    recorder.meteringEnabled = NO;
    if (![recorder prepareToRecord] || ![recorder recordForDuration:kStockChatVoiceMaxDurationSeconds]) {
        recorder.delegate = nil;
        [[NSFileManager defaultManager] removeItemAtURL:fileURL error:nil];
        [self deliverVoiceCallback:callback payload:[self voiceFailurePayloadWithCode:@"RECORDING_START_FAILED"
                                                                                message:@"无法启动麦克风，请稍后重试。"]];
        return;
    }
    self.voiceRecorder = recorder;
    self.voiceRecordingURL = fileURL;
    self.voiceCaptureError = nil;
    [self deliverVoiceCallback:callback payload:@{ @"success": @1 }];
}

- (void)stopVoiceRecording:(NSDictionary *)args {
    KuiklyRenderCallback callback = args[KR_CALLBACK_KEY];
    dispatch_async(dispatch_get_main_queue(), ^{
        AVAudioRecorder *recorder = self.voiceRecorder;
        NSURL *fileURL = self.voiceRecordingURL;
        if (recorder == nil || fileURL == nil) {
            [self deliverVoiceCallback:callback payload:[self voiceFailurePayloadWithCode:@"NOT_RECORDING"
                                                                                    message:@"当前没有正在进行的语音录音。"]];
            return;
        }
        self.voiceRecorder = nil;
        self.voiceRecordingURL = nil;
        self.voiceCleanupInProgress = YES;
        NSString *captureError = self.voiceCaptureError;
        self.voiceCaptureError = nil;
        recorder.delegate = nil;
        [recorder stop];

        __weak typeof(self) weakSelf = self;
        dispatch_async(dispatch_get_global_queue(QOS_CLASS_USER_INITIATED, 0), ^{
            NSDictionary *result = nil;
            if (captureError.length > 0) {
                result = [weakSelf voiceFailurePayloadWithCode:@"RECORDING_FAILED" message:captureError];
                [[NSFileManager defaultManager] removeItemAtURL:fileURL error:nil];
            } else {
                result = [weakSelf finishVoiceRecordingAtURL:fileURL];
            }
            dispatch_async(dispatch_get_main_queue(), ^{
                __strong typeof(weakSelf) strongSelf = weakSelf;
                strongSelf.voiceCleanupInProgress = NO;
                [strongSelf deliverVoiceCallback:callback
                                         payload:result ?: [strongSelf voiceFailurePayloadWithCode:@"RECORDING_FINALIZE_FAILED"
                                                                                             message:@"处理语音录音失败。"]];
            });
        });
    });
}

- (void)cancelVoiceRecording:(NSDictionary *)args {
    KuiklyRenderCallback callback = args[KR_CALLBACK_KEY];
    dispatch_async(dispatch_get_main_queue(), ^{
        // 让还在等待权限结果的启动请求失效
        self.voiceStartSequence += 1;
        self.voiceStartPending = NO;
        KuiklyRenderCallback pendingCallback = self.pendingVoiceStartCallback;
        self.pendingVoiceStartCallback = nil;
        if (pendingCallback) {
            [self deliverVoiceCallback:pendingCallback payload:[self voiceFailurePayloadWithCode:@"RECORDING_CANCELLED"
                                                                                           message:@"语音录音已取消。"]];
        }
        [self discardActiveVoiceRecording];
        [self deliverVoiceCallback:callback payload:@{ @"success": @1 }];
    });
}

- (void)discardActiveVoiceRecording {
    AVAudioRecorder *recorder = self.voiceRecorder;
    NSURL *fileURL = self.voiceRecordingURL;
    self.voiceRecorder = nil;
    self.voiceRecordingURL = nil;
    self.voiceCaptureError = nil;
    if (recorder != nil) {
        recorder.delegate = nil;
        [recorder stop];
    }
    if (fileURL != nil) {
        [[NSFileManager defaultManager] removeItemAtURL:fileURL error:nil];
    }
}

- (NSDictionary *)finishVoiceRecordingAtURL:(NSURL *)fileURL {
    NSData *fileData = [NSData dataWithContentsOfURL:fileURL];
    [[NSFileManager defaultManager] removeItemAtURL:fileURL error:nil];
    NSData *pcmData = [self pcmDataFromWavData:fileData];
    if (pcmData.length == 0) {
        return [self voiceFailurePayloadWithCode:@"EMPTY_AUDIO" message:@"没有录到有效的语音内容。"];
    }
    NSUInteger durationMs = (NSUInteger)((double)pcmData.length * 1000.0 /
                                         (kStockChatVoiceSampleRate * kStockChatVoiceBytesPerSample * kStockChatVoiceChannelCount));
    if (durationMs < kStockChatVoiceMinDurationMs) {
        return [self voiceFailurePayloadWithCode:@"AUDIO_TOO_SHORT"
                                         message:[NSString stringWithFormat:@"语音时间过短，请至少录制 %lums。", (unsigned long)kStockChatVoiceMinDurationMs]];
    }
    // 重新封装为标准 44 字节头的 WAV，去掉 CoreAudio 写入的 FLLR 填充块，和 Android 端产物一致
    NSData *wavData = [self wavDataFromPCM:pcmData];
    return @{
        @"success": @1,
        @"audioBase64": [wavData base64EncodedStringWithOptions:0],
        @"mimeType": kStockChatVoiceMimeType,
        @"durationMs": @(durationMs),
    };
}

// 解析 RIFF 块，提取 data 块内容；找不到时退回“跳过 44 字节头”
- (NSData *)pcmDataFromWavData:(NSData *)wavData {
    if (wavData.length <= kStockChatVoiceWavHeaderSize) {
        return [NSData data];
    }
    const uint8_t *bytes = wavData.bytes;
    NSUInteger length = wavData.length;
    if (memcmp(bytes, "RIFF", 4) == 0 && memcmp(bytes + 8, "WAVE", 4) == 0) {
        NSUInteger offset = 12;
        while (offset + 8 <= length) {
            uint32_t chunkSize = (uint32_t)bytes[offset + 4] | ((uint32_t)bytes[offset + 5] << 8) |
                                 ((uint32_t)bytes[offset + 6] << 16) | ((uint32_t)bytes[offset + 7] << 24);
            NSUInteger payloadOffset = offset + 8;
            if (memcmp(bytes + offset, "data", 4) == 0) {
                NSUInteger available = length - payloadOffset;
                NSUInteger payloadLength = MIN((NSUInteger)chunkSize, available);
                payloadLength -= payloadLength % kStockChatVoiceBytesPerSample;
                return [wavData subdataWithRange:NSMakeRange(payloadOffset, payloadLength)];
            }
            offset = payloadOffset + chunkSize + (chunkSize & 1);
        }
    }
    NSUInteger fallbackLength = length - kStockChatVoiceWavHeaderSize;
    fallbackLength -= fallbackLength % kStockChatVoiceBytesPerSample;
    return [wavData subdataWithRange:NSMakeRange(kStockChatVoiceWavHeaderSize, fallbackLength)];
}

- (NSData *)wavDataFromPCM:(NSData *)pcmData {
    uint32_t dataSize = (uint32_t)pcmData.length;
    uint32_t sampleRate = (uint32_t)kStockChatVoiceSampleRate;
    uint32_t byteRate = sampleRate * kStockChatVoiceChannelCount * (uint32_t)kStockChatVoiceBytesPerSample;
    uint16_t blockAlign = kStockChatVoiceChannelCount * (uint16_t)kStockChatVoiceBytesPerSample;
    uint32_t riffSize = 36 + dataSize;
    uint32_t fmtSize = 16;
    uint16_t audioFormat = 1;
    uint16_t channels = kStockChatVoiceChannelCount;
    uint16_t bitsPerSample = kStockChatVoiceBitsPerSample;

    NSMutableData *wav = [NSMutableData dataWithCapacity:kStockChatVoiceWavHeaderSize + pcmData.length];
    [wav appendBytes:"RIFF" length:4];
    [wav appendBytes:&riffSize length:4];
    [wav appendBytes:"WAVE" length:4];
    [wav appendBytes:"fmt " length:4];
    [wav appendBytes:&fmtSize length:4];
    [wav appendBytes:&audioFormat length:2];
    [wav appendBytes:&channels length:2];
    [wav appendBytes:&sampleRate length:4];
    [wav appendBytes:&byteRate length:4];
    [wav appendBytes:&blockAlign length:2];
    [wav appendBytes:&bitsPerSample length:2];
    [wav appendBytes:"data" length:4];
    [wav appendBytes:&dataSize length:4];
    [wav appendData:pcmData];
    return wav;
}

- (NSDictionary *)voiceFailurePayloadWithCode:(NSString *)errorCode message:(NSString *)errorMessage {
    return @{
        @"success": @0,
        @"errorCode": errorCode,
        @"errorMessage": errorMessage,
    };
}

- (void)deliverVoiceCallback:(KuiklyRenderCallback)callback payload:(NSDictionary *)payload {
    if (callback == nil) {
        return;
    }
    if ([NSThread isMainThread]) {
        callback(payload);
    } else {
        dispatch_async(dispatch_get_main_queue(), ^{
            callback(payload);
        });
    }
}

#pragma mark - AVAudioRecorderDelegate

- (void)audioRecorderEncodeErrorDidOccur:(AVAudioRecorder *)recorder error:(NSError *)error {
    dispatch_async(dispatch_get_main_queue(), ^{
        if (self.voiceRecorder == recorder) {
            self.voiceCaptureError = error.localizedDescription ?: @"录音过程中发生错误。";
        }
    });
}

- (void)audioRecorderDidFinishRecording:(AVAudioRecorder *)recorder successfully:(BOOL)flag {
    // 到达 30s 上限时系统自动停止；文件保留，等 Kotlin 侧的超时逻辑调用 stopVoiceRecording 取走
    if (!flag) {
        dispatch_async(dispatch_get_main_queue(), ^{
            if (self.voiceRecorder == recorder) {
                self.voiceCaptureError = @"录音意外中断，请重试。";
            }
        });
    }
}

- (void)playBase64Audio:(NSDictionary *)args {
    KuiklyRenderCallback callback = args[KR_CALLBACK_KEY];
    NSDictionary *params = [args[KR_PARAM_KEY] hr_stringToDictionary] ?: @{};
    NSString *audioBase64 = params[@"audioBase64"];
    NSData *audioData = [[NSData alloc] initWithBase64EncodedString:audioBase64 ?: @""
                                                           options:NSDataBase64DecodingIgnoreUnknownCharacters];
    if (audioData.length == 0) {
        if (callback) {
            callback(@{
                @"success": @0,
                @"errorCode": @"INVALID_AUDIO",
                @"errorMessage": @"MiMo 返回的语音数据无效。",
            });
        }
        return;
    }
    dispatch_async(dispatch_get_main_queue(), ^{
        [self.audioPlayer stop];
        self.audioPlayer = nil;
        NSError *sessionError = nil;
        [[AVAudioSession sharedInstance] setCategory:AVAudioSessionCategoryPlayback error:&sessionError];
        [[AVAudioSession sharedInstance] setActive:YES error:&sessionError];
        NSError *playerError = nil;
        AVAudioPlayer *player = [[AVAudioPlayer alloc] initWithData:audioData error:&playerError];
        if (player == nil || playerError != nil) {
            if (callback) {
                callback(@{
                    @"success": @0,
                    @"errorCode": @"AUDIO_PLAYBACK_FAILED",
                    @"errorMessage": playerError.localizedDescription ?: @"语音播放失败，请稍后重试。",
                });
            }
            return;
        }
        player.delegate = self;
        self.audioPlayer = player;
        [player prepareToPlay];
        if (![player play]) {
            self.audioPlayer = nil;
            if (callback) {
                callback(@{
                    @"success": @0,
                    @"errorCode": @"AUDIO_PLAYBACK_FAILED",
                    @"errorMessage": @"当前设备无法播放 MiMo 语音。",
                });
            }
            return;
        }
        if (callback) {
            callback(@{ @"success": @1 });
        }
    });
}

- (void)stopAudioPlayback:(NSDictionary *)args {
    KuiklyRenderCallback callback = args[KR_CALLBACK_KEY];
    dispatch_async(dispatch_get_main_queue(), ^{
        [self.audioPlayer stop];
        self.audioPlayer = nil;
        if (callback) {
            callback(@{ @"success": @1 });
        }
    });
}

- (void)streamChatCompletion:(NSDictionary *)args {
    KuiklyRenderCallback callback = args[KR_CALLBACK_KEY];
    NSDictionary *params = [args[KR_PARAM_KEY] hr_stringToDictionary] ?: @{};
    if (!callback) return;
    dispatch_async(dispatch_get_main_queue(), ^{
        [self.chatStreamRequest cancel];
        self.chatStreamRequest = [[StockChatStreamRequest alloc] initWithParameters:params callback:callback];
        [self.chatStreamRequest start];
    });
}

- (void)dealloc {
    [_chatStreamRequest cancel];
    if (_voiceRecorder != nil) {
        _voiceRecorder.delegate = nil;
        [_voiceRecorder stop];
    }
    if (_voiceRecordingURL != nil) {
        [[NSFileManager defaultManager] removeItemAtURL:_voiceRecordingURL error:nil];
    }
}

- (void)observeDrawerGestures:(NSDictionary *)args {
}

- (void)stopObservingDrawerGestures:(NSDictionary *)args {
}

- (void)observeBackRequests:(NSDictionary *)args {
}

- (void)stopObservingBackRequests:(NSDictionary *)args {
}

- (NSString *)dateFormatter:(NSDictionary *)args {
    NSDictionary *params = [args[KR_PARAM_KEY] hr_stringToDictionary] ?: @{};
    NSTimeInterval timeStamp = [params[@"timeStamp"] doubleValue] / 1000.0;
    NSString *format = params[@"format"];
    if (![format isKindOfClass:[NSString class]] || format.length == 0) {
        return @"";
    }
    NSDateFormatter *dateFormatter = [[NSDateFormatter alloc] init];
    dateFormatter.locale = [NSLocale localeWithLocaleIdentifier:@"en_US_POSIX"];
    dateFormatter.dateFormat = format;
    return [dateFormatter stringFromDate:[NSDate dateWithTimeIntervalSince1970:timeStamp]] ?: @"";
}

- (void)audioPlayerDidFinishPlaying:(AVAudioPlayer *)player successfully:(BOOL)flag {
    if (self.audioPlayer == player) {
        self.audioPlayer = nil;
    }
}

- (void)pickImages:(NSDictionary *)args {
    KuiklyRenderCallback callback = args[KR_CALLBACK_KEY];
    NSDictionary *params = [args[KR_PARAM_KEY] hr_stringToDictionary] ?: @{};
    NSInteger maxCount = [params[@"maxCount"] integerValue];
    maxCount = MAX(1, MIN(9, maxCount));

    dispatch_async(dispatch_get_main_queue(), ^{
        if (self.pendingImagePickerCallback != nil) {
            [self finishImagePickerWithCallback:callback payload:[self imagePickerFailurePayloadWithCode:@"IMAGE_PICKER_BUSY"
                                                                                                  message:@"图片选择器已打开。"]];
            return;
        }
        if (@available(iOS 14.0, *)) {
            UIViewController *viewController = [self.hr_rootView kr_viewController];
            if (viewController == nil || viewController.view.window == nil) {
                [self finishImagePickerWithCallback:callback payload:[self imagePickerFailurePayloadWithCode:@"VIEW_CONTROLLER_UNAVAILABLE"
                                                                                                      message:@"当前页面无法打开图片选择器。"]];
                return;
            }
            if (viewController.presentedViewController != nil) {
                [self finishImagePickerWithCallback:callback payload:[self imagePickerFailurePayloadWithCode:@"PRESENTATION_BUSY"
                                                                                                      message:@"请先关闭当前弹窗后再选择图片。"]];
                return;
            }

            self.imagePickerRequestID += 1;
            self.pendingImagePickerCallback = callback;
            self.pendingImageMaxCount = maxCount;
            PHPickerConfiguration *configuration = [[PHPickerConfiguration alloc] init];
            configuration.filter = [PHPickerFilter imagesFilter];
            configuration.selectionLimit = maxCount;
            PHPickerViewController *picker = [[PHPickerViewController alloc] initWithConfiguration:configuration];
            picker.delegate = self;
            [viewController presentViewController:picker animated:YES completion:nil];
        } else {
            [self finishImagePickerWithCallback:callback payload:[self imagePickerFailurePayloadWithCode:@"IMAGE_PICKER_UNAVAILABLE"
                                                                                                  message:@"当前系统版本不支持图片选择器。"]];
        }
    });
}

- (void)picker:(PHPickerViewController *)picker didFinishPicking:(NSArray<PHPickerResult *> *)results API_AVAILABLE(ios(14)) {
    NSUInteger requestID = self.imagePickerRequestID;
    NSInteger maxCount = self.pendingImageMaxCount;
    [picker dismissViewControllerAnimated:YES completion:nil];

    if (results.count == 0) {
        [self completeImagePickerRequest:requestID payload:@{
            @"success": @1,
            @"cancelled": @1,
            @"images": @[],
            @"truncated": @0,
        }];
        return;
    }

    NSInteger resultCount = MIN((NSInteger)results.count, maxCount);
    self.pendingImageResults = [NSMutableArray arrayWithCapacity:resultCount];
    for (NSInteger index = 0; index < resultCount; index += 1) {
        [self.pendingImageResults addObject:[NSNull null]];
    }
    self.pendingImageLoadCount = resultCount;
    self.pendingImageReadFailed = NO;
    self.pendingImageTruncated = results.count > maxCount;

    __weak typeof(self) weakSelf = self;
    for (NSInteger index = 0; index < resultCount; index += 1) {
        PHPickerResult *result = results[index];
        [result.itemProvider loadObjectOfClass:[UIImage class]
                              completionHandler:^(id<NSItemProviderReading> object, NSError *error) {
            __strong typeof(weakSelf) strongSelf = weakSelf;
            if (strongSelf == nil) {
                return;
            }
            NSString *dataURI = nil;
            if (error == nil && [object isKindOfClass:[UIImage class]]) {
                dataURI = [strongSelf imageDataURIFromImage:(UIImage *)object];
            }
            dispatch_async(dispatch_get_main_queue(), ^{
                if (strongSelf.imagePickerRequestID != requestID || strongSelf.pendingImagePickerCallback == nil) {
                    return;
                }
                if (dataURI.length > 0) {
                    strongSelf.pendingImageResults[index] = dataURI;
                } else {
                    strongSelf.pendingImageReadFailed = YES;
                }
                strongSelf.pendingImageLoadCount -= 1;
                if (strongSelf.pendingImageLoadCount == 0) {
                    [strongSelf finishLoadedImagePickerRequest:requestID];
                }
            });
        }];
    }
}

- (void)finishLoadedImagePickerRequest:(NSUInteger)requestID {
    if (self.pendingImageReadFailed) {
        [self completeImagePickerRequest:requestID payload:[self imagePickerFailurePayloadWithCode:@"IMAGE_READ_FAILED"
                                                                                             message:@"部分图片读取失败，请重新选择。"]];
        return;
    }
    NSMutableArray<NSString *> *images = [NSMutableArray arrayWithCapacity:self.pendingImageResults.count];
    for (id value in self.pendingImageResults) {
        if ([value isKindOfClass:[NSString class]]) {
            [images addObject:value];
        }
    }
    [self completeImagePickerRequest:requestID payload:@{
        @"success": @1,
        @"cancelled": @0,
        @"images": images,
        @"truncated": self.pendingImageTruncated ? @1 : @0,
    }];
}

- (void)completeImagePickerRequest:(NSUInteger)requestID payload:(NSDictionary *)payload {
    dispatch_async(dispatch_get_main_queue(), ^{
        if (self.imagePickerRequestID != requestID) {
            return;
        }
        KuiklyRenderCallback callback = self.pendingImagePickerCallback;
        self.pendingImagePickerCallback = nil;
        self.pendingImageResults = nil;
        self.pendingImageLoadCount = 0;
        self.pendingImageMaxCount = 0;
        self.pendingImageReadFailed = NO;
        self.pendingImageTruncated = NO;
        if (callback) {
            callback(payload);
        }
    });
}

- (void)finishImagePickerWithCallback:(KuiklyRenderCallback)callback payload:(NSDictionary *)payload {
    if ([NSThread isMainThread]) {
        if (callback) {
            callback(payload);
        }
    } else {
        dispatch_async(dispatch_get_main_queue(), ^{
            if (callback) {
                callback(payload);
            }
        });
    }
}

- (NSDictionary *)imagePickerFailurePayloadWithCode:(NSString *)errorCode message:(NSString *)errorMessage {
    return @{
        @"success": @0,
        @"cancelled": @0,
        @"images": @[],
        @"truncated": @0,
        @"errorCode": errorCode,
        @"errorMessage": errorMessage,
    };
}

- (NSString *)imageDataURIFromImage:(UIImage *)image {
    UIImage *scaledImage = [self scaledImage:image maximumDimension:2048.0];
    NSData *imageData = UIImageJPEGRepresentation(scaledImage, 0.82);
    if (imageData.length == 0) {
        return nil;
    }
    NSString *base64 = [imageData base64EncodedStringWithOptions:0];
    return [NSString stringWithFormat:@"data:image/jpeg;base64,%@", base64];
}

- (UIImage *)scaledImage:(UIImage *)image maximumDimension:(CGFloat)maximumDimension {
    CGFloat width = image.size.width;
    CGFloat height = image.size.height;
    CGFloat largestDimension = MAX(width, height);
    if (largestDimension <= maximumDimension || largestDimension <= 0) {
        return image;
    }
    CGFloat scale = maximumDimension / largestDimension;
    CGSize targetSize = CGSizeMake(floor(width * scale), floor(height * scale));
    UIGraphicsImageRendererFormat *format = [UIGraphicsImageRendererFormat defaultFormat];
    format.opaque = YES;
    format.scale = 1.0;
    UIGraphicsImageRenderer *renderer = [[UIGraphicsImageRenderer alloc] initWithSize:targetSize format:format];
    return [renderer imageWithActions:^(UIGraphicsImageRendererContext *context) {
        [[UIColor whiteColor] setFill];
        [context fillRect:CGRectMake(0, 0, targetSize.width, targetSize.height)];
        [image drawInRect:CGRectMake(0, 0, targetSize.width, targetSize.height)];
    }];
}

@end
