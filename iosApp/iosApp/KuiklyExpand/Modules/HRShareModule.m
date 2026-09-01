#import "HRShareModule.h"

#import <OpenKuiklyIOSRender/NSObject+KR.h>
#import <UIKit/UIKit.h>

@interface HRShareItemSource : NSObject <UIActivityItemSource>

@property (nonatomic, copy) NSString *content;
@property (nonatomic, copy) NSString *title;

- (instancetype)initWithContent:(NSString *)content title:(NSString *)title;

@end

@implementation HRShareItemSource

- (instancetype)initWithContent:(NSString *)content title:(NSString *)title {
    self = [super init];
    if (self) {
        _content = [content copy];
        _title = [title copy];
    }
    return self;
}

- (id)activityViewControllerPlaceholderItem:(UIActivityViewController *)activityViewController {
    return self.content;
}

- (id)activityViewController:(UIActivityViewController *)activityViewController
         itemForActivityType:(nullable UIActivityType)activityType {
    return self.content;
}

- (NSString *)activityViewController:(UIActivityViewController *)activityViewController
              subjectForActivityType:(nullable UIActivityType)activityType {
    return self.title;
}

@end

@implementation HRShareModule

- (void)share:(NSDictionary *)args {
    KuiklyRenderCallback callback = args[KR_CALLBACK_KEY];
    NSDictionary *params = [args[KR_PARAM_KEY] hr_stringToDictionary] ?: @{};
    NSString *title = [self normalizedString:params[@"title"]];
    NSString *text = [self normalizedString:params[@"text"]];
    NSString *url = [self normalizedString:params[@"url"]];
    NSMutableArray<NSString *> *contentParts = [NSMutableArray arrayWithCapacity:2];
    if (text.length > 0) {
        [contentParts addObject:text];
    }
    if (url.length > 0) {
        [contentParts addObject:url];
    }
    NSString *content = [contentParts componentsJoinedByString:@"\n\n"];
    if (content.length == 0) {
        content = title;
    }
    if (content.length == 0) {
        [self invokeCallback:callback
                     payload:[self failurePayloadWithCode:@"EMPTY_SHARE_CONTENT"
                                                  message:@"没有可分享的内容。"]];
        return;
    }

    dispatch_async(dispatch_get_main_queue(), ^{
        UIViewController *viewController = [self.hr_rootView kr_viewController];
        if (viewController == nil || viewController.view.window == nil) {
            [self invokeCallback:callback
                         payload:[self failurePayloadWithCode:@"VIEW_CONTROLLER_UNAVAILABLE"
                                                      message:@"当前页面无法打开系统分享。"]];
            return;
        }
        if (viewController.presentedViewController != nil) {
            [self invokeCallback:callback
                         payload:[self failurePayloadWithCode:@"PRESENTATION_BUSY"
                                                      message:@"请先关闭当前弹窗后再分享。"]];
            return;
        }

        HRShareItemSource *itemSource = [[HRShareItemSource alloc] initWithContent:content title:title];
        UIActivityViewController *activityViewController =
            [[UIActivityViewController alloc] initWithActivityItems:@[ itemSource ]
                                              applicationActivities:nil];
        activityViewController.completionWithItemsHandler = ^(
            UIActivityType activityType,
            BOOL completed,
            NSArray *returnedItems,
            NSError *activityError
        ) {
            if (activityError != nil) {
                [self invokeCallback:callback
                             payload:[self failurePayloadWithCode:@"SHARE_FAILED"
                                                          message:activityError.localizedDescription ?: @"系统分享失败，请稍后重试。"]];
                return;
            }
            [self invokeCallback:callback payload:@{
                @"success": completed ? @1 : @0,
                @"cancelled": completed ? @0 : @1,
                @"errorCode": @"",
                @"errorMessage": @"",
            }];
        };
        UIPopoverPresentationController *popoverController = activityViewController.popoverPresentationController;
        if (popoverController != nil) {
            popoverController.sourceView = viewController.view;
            popoverController.sourceRect = CGRectMake(
                CGRectGetMidX(viewController.view.bounds),
                CGRectGetMidY(viewController.view.bounds),
                1,
                1
            );
            popoverController.permittedArrowDirections = 0;
        }
        [viewController presentViewController:activityViewController animated:YES completion:nil];
    });
}

- (NSString *)normalizedString:(id)value {
    if (![value isKindOfClass:[NSString class]]) {
        return @"";
    }
    return [(NSString *)value stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]];
}

- (NSDictionary *)failurePayloadWithCode:(NSString *)errorCode message:(NSString *)errorMessage {
    return @{
        @"success": @0,
        @"cancelled": @0,
        @"errorCode": errorCode,
        @"errorMessage": errorMessage,
    };
}

- (void)invokeCallback:(nullable KuiklyRenderCallback)callback payload:(NSDictionary *)payload {
    if (callback != nil) {
        callback(payload);
    }
}

@end
