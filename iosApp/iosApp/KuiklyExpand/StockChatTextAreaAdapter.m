#import <OpenKuiklyIOSRender/KRTextAreaView.h>
#import <objc/runtime.h>

@interface StockChatTextInputState : NSObject
@property (nonatomic, strong) NSMutableArray<NSString *> *pendingTextEchoes;
@property (nonatomic, assign) BOOL applyingTextProperty;
@end

@implementation StockChatTextInputState
- (instancetype)init {
    if (self = [super init]) {
        _pendingTextEchoes = [NSMutableArray array];
    }
    return self;
}
@end

@implementation KRTextAreaView (StockChatInput)

+ (void)load {
    // Kuikly 2.26 uses the fixed native name KRTextAreaView. Intercept only its
    // two bridge entry points in this iOS host, without patching Pods/shared.
    Method setProp = class_getInstanceMethod(self, @selector(hrv_setPropWithKey:propValue:));
    Method adaptedSetProp = class_getInstanceMethod(self, @selector(stockChat_setPropWithKey:propValue:));
    method_exchangeImplementations(setProp, adaptedSetProp);
    Method call = class_getInstanceMethod(self, @selector(hrv_callWithMethod:params:callback:));
    Method adaptedCall = class_getInstanceMethod(self, @selector(stockChat_callWithMethod:params:callback:));
    method_exchangeImplementations(call, adaptedCall);
}

- (StockChatTextInputState *)stockChat_inputState {
    StockChatTextInputState *state = objc_getAssociatedObject(self, @selector(stockChat_inputState));
    if (!state) {
        state = [StockChatTextInputState new];
        objc_setAssociatedObject(self, @selector(stockChat_inputState), state, OBJC_ASSOCIATION_RETAIN_NONATOMIC);
    }
    return state;
}

- (void)stockChat_setPropWithKey:(NSString *)propKey propValue:(id)propValue {
    if ([propKey isEqualToString:@"textDidChange"] && propValue) {
        KuiklyRenderCallback callback = propValue;
        __weak typeof(self) weakSelf = self;
        [self stockChat_setPropWithKey:propKey propValue:^(NSDictionary *payload) {
            typeof(self) self = weakSelf;
            if (!self || self.stockChat_inputState.applyingTextProperty) {
                return;
            }
            NSString *text = payload[@"text"];
            if ([text isKindOfClass:NSString.class]) {
                [self.stockChat_inputState.pendingTextEchoes addObject:text];
                // Reactive updates may coalesce, so not every event is acknowledged.
                if (self.stockChat_inputState.pendingTextEchoes.count > 128) {
                    [self.stockChat_inputState.pendingTextEchoes removeObjectAtIndex:0];
                }
            }
            callback(payload);
        }];
        return;
    }
    if ([propKey isEqualToString:@"text"] && [propValue isKindOfClass:NSString.class]) {
        NSUInteger echoIndex = [self.stockChat_inputState.pendingTextEchoes indexOfObject:propValue];
        if (echoIndex != NSNotFound) {
            [self.stockChat_inputState.pendingTextEchoes removeObjectsInRange:NSMakeRange(0, echoIndex + 1)];
            // An acknowledgement can lag behind the next native keystroke.
            // Replacing UITextView.text here rolls it back and re-enters the IME.
            return;
        }
        if ([self.text isEqualToString:propValue]) {
            return;
        }
        [self.stockChat_inputState.pendingTextEchoes removeAllObjects];
        self.stockChat_inputState.applyingTextProperty = YES;
        @try {
            [self stockChat_setPropWithKey:propKey propValue:propValue];
        } @finally {
            self.stockChat_inputState.applyingTextProperty = NO;
        }
        return;
    }
    [self stockChat_setPropWithKey:propKey propValue:propValue];
}

- (void)stockChat_callWithMethod:(NSString *)method params:(NSString *)params callback:(KuiklyRenderCallback)callback {
    if ([method isEqualToString:@"setText"] || [method isEqualToString:@"setTextInputState"]) {
        // Explicit commands (send/clear/restore draft) always remain authoritative.
        [self.stockChat_inputState.pendingTextEchoes removeAllObjects];
    }
    [self stockChat_callWithMethod:method params:params callback:callback];
}

@end
