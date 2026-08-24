#import "HRBridgeModule.h"

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

@interface HRBridgeModule () <PHPickerViewControllerDelegate, AVAudioPlayerDelegate>

@property (nonatomic, copy, nullable) KuiklyRenderCallback pendingImagePickerCallback;
@property (nonatomic, strong, nullable) AVAudioPlayer *audioPlayer;
@property (nonatomic, strong) NSMutableArray *pendingImageResults;
@property (nonatomic, assign) NSInteger pendingImageLoadCount;
@property (nonatomic, assign) NSInteger pendingImageMaxCount;
@property (nonatomic, assign) BOOL pendingImageReadFailed;
@property (nonatomic, assign) BOOL pendingImageTruncated;
@property (nonatomic, assign) NSUInteger imagePickerRequestID;

@end

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

- (void)startVoiceRecording:(NSDictionary *)args {
    KuiklyRenderCallback callback = args[KR_CALLBACK_KEY];
    if (callback) {
        callback(@{
            @"success": @0,
            @"errorCode": @"VOICE_INPUT_UNAVAILABLE",
            @"errorMessage": @"当前平台的语音录音适配尚未接入"
        });
    }
}

- (void)stopVoiceRecording:(NSDictionary *)args {
    KuiklyRenderCallback callback = args[KR_CALLBACK_KEY];
    if (callback) {
        callback(@{
            @"success": @0,
            @"errorCode": @"VOICE_INPUT_UNAVAILABLE",
            @"errorMessage": @"当前平台的语音录音适配尚未接入"
        });
    }
}

- (void)cancelVoiceRecording:(NSDictionary *)args {
    KuiklyRenderCallback callback = args[KR_CALLBACK_KEY];
    if (callback) {
        callback(@{ @"success": @1 });
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
