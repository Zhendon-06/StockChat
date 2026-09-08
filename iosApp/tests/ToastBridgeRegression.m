#import <UIKit/UIKit.h>
#import "../iosApp/KuiklyExpand/Modules/StockChatStreamRequest.m"
#import "../iosApp/KuiklyExpand/Modules/HRBridgeModule.m"

static void Check(BOOL condition, NSString *message) {
    if (!condition) { NSLog(@"FAIL: %@", message); exit(1); }
}

static void Drain(NSTimeInterval duration) {
    [NSRunLoop.currentRunLoop runUntilDate:[NSDate dateWithTimeIntervalSinceNow:duration]];
}

static NSArray<UIView *> *Toasts(UIView *host) {
    NSMutableArray *result = [NSMutableArray array];
    for (UIView *view in host.subviews) {
        if ([view.accessibilityIdentifier isEqual:@"stockchat.toast"]) [result addObject:view];
    }
    return result;
}

int main(int argc, char **argv) {
    @autoreleasepool {
        [UIApplication sharedApplication];
        UIWindow *window = [[UIWindow alloc] initWithFrame:CGRectMake(0, 0, 390, 844)];
        UIView *host = [[UIView alloc] initWithFrame:window.bounds];
        [window addSubview:host];
        HRBridgeModule *module = [HRBridgeModule new];
        module.hr_rootView = (id)host;

        // Use the exact native dispatch and nil callback used by toggleFavorite.
        [module hrv_callWithMethod:@"toast" params:@"{\"content\":\"已收藏行情卡片\"}" callback:nil];
        Drain(0.05);
        Check(Toasts(host).count == 1, @"Favorite toast is implemented and visible");
        UIView *first = Toasts(host).firstObject;
        Check(!first.userInteractionEnabled, @"Feedback does not block taps or navigation");
        Check([[(UILabel *)first.subviews.firstObject text] isEqual:@"已收藏行情卡片"], @"Favorite feedback is correct");
        [host layoutIfNeeded];
        Check(first.bounds.size.width > 0 && first.bounds.size.width <= 342, @"Toast lays out inside the host margins");

        Drain(1.0);
        dispatch_async(dispatch_get_global_queue(QOS_CLASS_DEFAULT, 0), ^{
            [module hrv_callWithMethod:@"toast" params:@"{\"content\":\"已取消收藏\"}" callback:nil];
        });
        Drain(0.1);
        Check(Toasts(host).count == 1 && first.superview == nil, @"Rapid toggles replace the previous feedback");
        Check([[(UILabel *)Toasts(host).firstObject.subviews.firstObject text] isEqual:@"已取消收藏"], @"Background bridge calls update UI safely");
        Drain(1.05);
        Check(Toasts(host).count == 1, @"The old timer must not dismiss the newer toast");
        Drain(1.0);
        Check(Toasts(host).count == 0, @"Feedback dismisses automatically");

        [module hrv_callWithMethod:@"toast" params:@"{\"content\":null}" callback:nil];
        [module hrv_callWithMethod:@"toast" params:@"{\"content\":\"\"}" callback:nil];
        Drain(0.05);
        Check(Toasts(host).count == 0, @"Invalid and empty feedback is ignored");
        [host removeFromSuperview];
        [module hrv_callWithMethod:@"toast" params:@"{\"content\":\"页面已关闭\"}" callback:nil];
        Drain(0.05);
        Check(Toasts(host).count == 0, @"Detached pages do not display a stale toast");
        NSLog(@"PASS: iOS favorite toast bridge regression checks");
    }
    return 0;
}
