#import <UIKit/UIKit.h>
#import "../iosApp/KuiklyExpand/StockChatTextAreaAdapter.m"

static void Check(BOOL condition, NSString *message) {
    if (!condition) {
        NSLog(@"FAIL: %@", message);
        exit(1);
    }
}

static void Edit(KRTextAreaView *view, NSString *text) {
    view.text = text;
    [view.delegate textViewDidChange:view];
}

int main(int argc, char **argv) {
    @autoreleasepool {
        [UIApplication sharedApplication];
        KRTextAreaView *view = [KRTextAreaView new];
        __block NSInteger eventCount = 0;
        [view hrv_setPropWithKey:@"textDidChange" propValue:^(NSDictionary *payload) {
            eventCount += 1;
        }];
        Edit(view, @"a");
        Edit(view, @"ab");
        [view hrv_setPropWithKey:@"text" propValue:@"a"];
        Check([view.text isEqualToString:@"ab"], @"A delayed echo must not roll back the next keystroke");
        Check(eventCount == 2, @"An echo must not emit another editing event");
        [view hrv_setPropWithKey:@"text" propValue:@"ab"];
        [view hrv_setPropWithKey:@"text" propValue:@"恢复草稿"];
        Check([view.text isEqualToString:@"恢复草稿"], @"A programmatic draft must still apply");
        Check(eventCount == 2, @"Property updates must not start a feedback loop");
        Edit(view, @"中文🙂\n第二行");
        [view hrv_setPropWithKey:@"text" propValue:@"中文🙂\n第二行"];
        Check([view.text isEqualToString:@"中文🙂\n第二行"], @"Unicode and multiline input must survive acknowledgements");
        [view hrv_callWithMethod:@"setText" params:@"" callback:nil];
        Check(view.text.length == 0, @"Sending must clear the native input");
        Check(eventCount == 4, @"Explicit clear must notify shared state");
        [view hrv_setPropWithKey:@"text" propValue:@""];
        Edit(view, @"new");
        Check([view.text isEqualToString:@"new"], @"Input must work again after sending");
        [view hrv_setPropWithKey:@"text" propValue:@"new"];
        Edit(view, @"n");
        Edit(view, @"");
        Edit(view, @"下一个问题");
        [view hrv_setPropWithKey:@"text" propValue:@""];
        Check([view.text isEqualToString:@"下一个问题"], @"A delayed delete acknowledgement must not clear the next input");
        [view hrv_setPropWithKey:@"text" propValue:@"下一个问题"];
        [view setMarkedText:@"pin" selectedRange:NSMakeRange(3, 0)];
        Check(view.markedTextRange != nil, @"IME composition should be active");
        [view hrv_setPropWithKey:@"text" propValue:view.text];
        Check(view.markedTextRange != nil, @"An unchanged property must not end IME composition");
        [view unmarkText];
        KRTextAreaView *other = [KRTextAreaView new];
        [other hrv_setPropWithKey:@"text" propValue:@"独立草稿"];
        Check([other.text isEqualToString:@"独立草稿"], @"Each input must have independent echo tracking");
        NSLog(@"PASS: iOS TextArea regression checks");
    }
    return 0;
}
