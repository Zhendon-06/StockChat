#import <UIKit/UIKit.h>
#import "../iosApp/KuiklyExpand/Modules/StockChatStreamRequest.m"
#import "../iosApp/KuiklyExpand/StockChatGradientAdapter.m"

static void Check(BOOL condition, NSString *message) {
    if (!condition) { NSLog(@"FAIL: %@", message); exit(1); }
}

static NSArray *Decode(NSString *stream, NSUInteger chunkSize) {
    NSMutableArray *events = [NSMutableArray array];
    StockChatStreamDecoder *decoder = [StockChatStreamDecoder new];
    decoder.emit = ^(NSDictionary *event) { [events addObject:event]; };
    NSData *bytes = [stream dataUsingEncoding:NSUTF8StringEncoding];
    for (NSUInteger i = 0; i < bytes.length; i += chunkSize) {
        [decoder appendData:[bytes subdataWithRange:NSMakeRange(i, MIN(chunkSize, bytes.length - i))]];
    }
    [decoder finish];
    return events;
}

static void TestDecoder(void) {
    NSString *stream = @"\uFEFF: heartbeat\r\ndata: {\"choices\":[{\"delta\":{\"content\":\"你好🙂\"}}]}\r\n\r\n"
        "event: message\ndata: {\"choices\":\ndata: [{\"delta\":{\"content\":\"第二段\"}}]}\n\n"
        "data: [DONE]\n\ndata: [DONE]\n\n";
    for (NSUInteger size = 1; size <= 32; size++) {
        NSArray *events = Decode(stream, size);
        Check(events.count == 3, @"Fragmented SSE emits two deltas and exactly one end");
        Check([events[0][@"content"] isEqual:@"你好🙂"], @"UTF-8 bytes spanning packets remain intact");
        Check([events[1][@"content"] isEqual:@"第二段"], @"Multiline data fields form one JSON payload");
        Check([events[2][@"event"] isEqual:@"end"], @"DONE terminates the stream");
    }
    NSArray *events = Decode(@"data: {\"choices\":[{\"delta\":{\"content\":[{\"text\":\"text\"}]},\"finish_reason\":\"stop\"}]}\r\r", 1);
    Check(events.count == 2 && [events.lastObject[@"event"] isEqual:@"end"], @"CR and finish_reason without DONE are supported");
    events = Decode(@"data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n\n", 2);
    Check(events.count == 2 && [events.lastObject[@"success"] intValue] == 0, @"Premature EOF must fail instead of reporting success");
    events = Decode(@"data: invalid\n\ndata: [DONE]\n\n", 1);
    Check(events.count == 1 && [events[0][@"success"] intValue] == 0, @"Malformed JSON has one terminal error");
    events = Decode(@"data: {\"error\":{\"message\":\"test error\"}}\n\n", 3);
    Check([events[0][@"errorMessage"] isEqual:@"test error"], @"Provider errors reach shared state");
}

static void TestGradient(void) {
    for (UIColor *background in @[UIColor.whiteColor, [UIColor colorWithWhite:0.08 alpha:1]]) {
        for (NSNumber *reverse in @[@NO, @YES]) {
            CALayer *parent = [CALayer layer];
            parent.bounds = CGRectMake(0, 0, 300, 40);
            CSSGradientLayer *layer = [[CSSGradientLayer alloc] initWithLayer:nil
                                                               cssGradient:@"linear-gradient(0,0 0,4294967295 1)"];
            id clear = (__bridge id)UIColor.clearColor.CGColor;
            id color = (__bridge id)background.CGColor;
            layer.colors = reverse.boolValue ? @[color, clear] : @[clear, color];
            [parent addSublayer:layer];
            [layer layoutSublayers];
            NSUInteger transparentIndex = reverse.boolValue ? 1 : 0;
            CGColorRef corrected = (__bridge CGColorRef)layer.colors[transparentIndex];
            CGColorRef expected = CGColorCreateCopyWithAlpha(background.CGColor, 0);
            Check(CGColorEqualToColor(corrected, expected), @"Fade must preserve background RGB in light and dark themes");
            Check(CGColorGetAlpha(corrected) == 0, @"Fade must remain transparent, not become an opaque cover");
            CGColorRelease(expected);
        }
    }
    CSSGradientLayer *layer = [[CSSGradientLayer alloc] initWithLayer:nil cssGradient:@"linear-gradient(0,0 0,4294967295 1)"];
    NSArray *original = @[(__bridge id)UIColor.redColor.CGColor, (__bridge id)UIColor.blueColor.CGColor];
    layer.colors = original;
    [layer layoutSublayers];
    Check([layer.colors isEqual:original], @"Opaque gradients must remain unchanged");
}

@interface StreamFixtureProtocol : NSURLProtocol
@property (atomic, assign) BOOL stopped;
@end

@implementation StreamFixtureProtocol
+ (BOOL)canInitWithRequest:(NSURLRequest *)request { return [request.URL.host isEqual:@"stockchat.test"]; }
+ (NSURLRequest *)canonicalRequestForRequest:(NSURLRequest *)request { return request; }
- (void)startLoading {
    Check([self.request.HTTPMethod isEqual:@"POST"], @"Streaming request uses POST");
    Check([[self.request valueForHTTPHeaderField:@"Accept"] isEqual:@"text/event-stream"], @"Request advertises SSE");
    Check([[self.request valueForHTTPHeaderField:@"Authorization"] isEqual:@"Bearer test-only"], @"Provider credentials are forwarded");
    BOOL httpError = [self.request.URL.path isEqual:@"/error"];
    BOOL json = [self.request.URL.path isEqual:@"/json"];
    NSHTTPURLResponse *response = [[NSHTTPURLResponse alloc] initWithURL:self.request.URL statusCode:httpError ? 401 : 200
                                                          HTTPVersion:@"HTTP/1.1" headerFields:@{@"Content-Type": json || httpError ? @"application/json" : @"text/event-stream"}];
    [self.client URLProtocol:self didReceiveResponse:response cacheStoragePolicy:NSURLCacheStorageNotAllowed];
    NSString *first = httpError ? @"{\"error\":{\"message\":\"test unauthorized\"}}"
        : json ? @"{\"choices\":[{\"message\":{\"content\":\"JSON response\"}}]}"
        : @"data: {\"choices\":[{\"delta\":{\"content\":\"第一段\"}}]}\n\n";
    [self.client URLProtocol:self didLoadData:[first dataUsingEncoding:NSUTF8StringEncoding]];
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, 300 * NSEC_PER_MSEC), dispatch_get_main_queue(), ^{
        if (self.stopped) return;
        if (!json && !httpError) {
            NSString *last = @"data: {\"choices\":[{\"delta\":{\"content\":\"第二段\"}}]}\n\ndata: [DONE]\n\n";
            [self.client URLProtocol:self didLoadData:[last dataUsingEncoding:NSUTF8StringEncoding]];
        }
        [self.client URLProtocolDidFinishLoading:self];
    });
}
- (void)stopLoading { self.stopped = YES; }
@end

@implementation NSURLSessionConfiguration (StreamTest)
+ (NSURLSessionConfiguration *)stockChat_testConfiguration {
    NSURLSessionConfiguration *configuration = [self stockChat_testConfiguration];
    configuration.protocolClasses = @[StreamFixtureProtocol.class];
    return configuration;
}
@end

static void TestTransport(NSString *path) {
    __block BOOL done = NO;
    __block NSInteger terminalCount = 0;
    __block NSTimeInterval firstDelta = 0;
    NSMutableString *content = [NSMutableString string];
    __block NSDictionary *terminal = nil;
    NSTimeInterval start = NSDate.timeIntervalSinceReferenceDate;
    StockChatStreamRequest *request = [[StockChatStreamRequest alloc]
        initWithParameters:@{@"url": [@"https://stockchat.test" stringByAppendingString:path], @"apiKey": @"test-only",
                             @"requestBody": @"{\"model\":\"fixture\",\"messages\":[]}"}
        callback:^(NSDictionary *event) {
            Check(NSThread.isMainThread, @"Bridge events are delivered on the main queue in order");
            if ([event[@"event"] isEqual:@"delta"]) {
                if (!firstDelta) firstDelta = NSDate.timeIntervalSinceReferenceDate;
                [content appendString:event[@"content"]];
            } else {
                terminal = event;
                terminalCount++;
                done = YES;
            }
        }];
    [request start];
    if ([path isEqual:@"/cancel"]) {
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, 100 * NSEC_PER_MSEC), dispatch_get_main_queue(), ^{ [request cancel]; });
    }
    while (!done && NSDate.timeIntervalSinceReferenceDate - start < 5) {
        [NSRunLoop.currentRunLoop runUntilDate:[NSDate dateWithTimeIntervalSinceNow:0.01]];
    }
    Check(done && terminalCount == 1, @"Transport finishes once without hanging");
    if ([path isEqual:@"/stream"]) {
        Check([content isEqual:@"第一段第二段"], @"Transport preserves ordered incremental content");
        Check(firstDelta - start < 0.25, @"First delta arrives before the server completes its response");
        Check([terminal[@"event"] isEqual:@"end"], @"Successful SSE finishes normally");
    } else if ([path isEqual:@"/json"]) {
        Check([content isEqual:@"JSON response"] && [terminal[@"event"] isEqual:@"end"], @"JSON compatibility fallback works");
    } else {
        Check([terminal[@"success"] intValue] == 0, @"HTTP error/cancellation must fail");
        Check([terminal[@"errorCode"] isEqual:[path isEqual:@"/cancel"] ? @"STREAM_CANCELLED" : @"STREAM_HTTP_ERROR"], @"Terminal errors retain their type");
    }
    [NSRunLoop.currentRunLoop runUntilDate:[NSDate dateWithTimeIntervalSinceNow:0.35]];
    Check(terminalCount == 1, @"Late completion does not produce a second terminal event");
}

int main(int argc, char **argv) {
    @autoreleasepool {
        [UIApplication sharedApplication];
        TestDecoder();
        TestGradient();
        method_exchangeImplementations(class_getClassMethod(NSURLSessionConfiguration.class, @selector(ephemeralSessionConfiguration)),
                                       class_getClassMethod(NSURLSessionConfiguration.class, @selector(stockChat_testConfiguration)));
        for (NSString *path in @[@"/stream", @"/error", @"/json", @"/cancel"]) TestTransport(path);
        NSLog(@"PASS: iOS SSE decoder, incremental transport, cancellation and gradient regression checks");
    }
    return 0;
}
