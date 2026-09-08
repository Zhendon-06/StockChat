#import <OpenKuiklyIOSRender/UIView+CSS.h>
#import <objc/runtime.h>

@implementation CSSGradientLayer (StockChatFade)

+ (void)load {
    Method original = class_getInstanceMethod(self, @selector(layoutSublayers));
    Method adapted = class_getInstanceMethod(self, @selector(stockChat_layoutSublayers));
    method_exchangeImplementations(original, adapted);
}

- (void)stockChat_layoutSublayers {
    [self stockChat_layoutSublayers];
    // Core Animation interpolates the RGB of transparent black too. A two-stop
    // opacity fade must use the visible endpoint's RGB at both ends.
    NSArray *colors = self.colors;
    if (colors.count != 2) {
        return;
    }
    CGColorRef first = (__bridge CGColorRef)colors[0];
    CGColorRef last = (__bridge CGColorRef)colors[1];
    BOOL firstTransparent = CGColorGetAlpha(first) == 0;
    BOOL lastTransparent = CGColorGetAlpha(last) == 0;
    if (firstTransparent == lastTransparent) {
        return;
    }
    CGColorRef transparent = CGColorCreateCopyWithAlpha(firstTransparent ? last : first, 0);
    if (!CGColorEqualToColor(transparent, firstTransparent ? first : last)) {
        [CATransaction begin];
        [CATransaction setDisableActions:YES];
        self.colors = firstTransparent ? @[(__bridge id)transparent, colors[1]]
                                       : @[colors[0], (__bridge id)transparent];
        [CATransaction commit];
    }
    CGColorRelease(transparent);
}

@end
