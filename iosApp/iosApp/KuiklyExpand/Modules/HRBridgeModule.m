#import "HRBridgeModule.h"

#import "KuiklyRenderViewController.h"
#import <OpenKuiklyIOSRender/NSObject+KR.h>

#define REQ_PARAM_KEY @"reqParam"
#define CMD_KEY @"cmd"
#define FROM_HIPPY_RENDER @"from_hippy_render"
// 扩展桥接接口
/*
 * @brief Native暴露接口到kotlin侧，提供kotlin侧调用native能力
 */

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

@end
