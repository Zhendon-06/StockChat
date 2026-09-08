#import "StockChatStreamRequest.h"

static NSDictionary *SCStreamFailure(NSString *code, NSString *message) {
    return @{@"success": @0, @"errorCode": code, @"errorMessage": message};
}

static NSDictionary *SCDictionary(id value) {
    if ([value isKindOfClass:NSString.class]) {
        value = [NSJSONSerialization JSONObjectWithData:[value dataUsingEncoding:NSUTF8StringEncoding]
                                               options:0 error:nil];
    }
    return [value isKindOfClass:NSDictionary.class] ? value : @{};
}

static NSString *SCContentText(id value) {
    if ([value isKindOfClass:NSString.class]) {
        return value;
    }
    if ([value isKindOfClass:NSArray.class]) {
        NSMutableString *text = [NSMutableString string];
        for (id part in value) {
            [text appendString:SCContentText(part)];
        }
        return text;
    }
    if ([value isKindOfClass:NSDictionary.class]) {
        return SCContentText(value[@"text"] ?: value[@"content"]);
    }
    return @"";
}

// Decode complete SSE lines as UTF-8, never individual network chunks: a Chinese
// character, CRLF, JSON object or event can straddle any number of packets.
@interface StockChatStreamDecoder : NSObject
@property (nonatomic, strong) NSMutableData *buffer;
@property (nonatomic, strong) NSMutableArray<NSString *> *dataLines;
@property (nonatomic, copy) KuiklyRenderCallback emit;
@property (nonatomic, assign) BOOL terminal;
- (void)appendData:(NSData *)data;
- (void)finish;
@end

@implementation StockChatStreamDecoder
- (instancetype)init {
    if (self = [super init]) {
        _buffer = [NSMutableData data];
        _dataLines = [NSMutableArray array];
    }
    return self;
}

- (void)fail:(NSString *)message {
    if (!self.terminal) {
        self.terminal = YES;
        self.emit(SCStreamFailure(@"INVALID_STREAM_RESPONSE", message));
    }
}

- (void)consumeEvent {
    if (!self.dataLines.count || self.terminal) {
        return;
    }
    NSString *data = [self.dataLines componentsJoinedByString:@"\n"];
    [self.dataLines removeAllObjects];
    if ([[data stringByTrimmingCharactersInSet:NSCharacterSet.whitespaceAndNewlineCharacterSet] isEqualToString:@"[DONE]"]) {
        self.terminal = YES;
        self.emit(@{@"success": @1, @"event": @"end"});
        return;
    }
    NSDictionary *json = SCDictionary(data);
    if (!json.count) {
        [self fail:@"AI 流式响应格式无效，请重新生成。"];
        return;
    }
    if (json[@"error"] && json[@"error"] != NSNull.null) {
        NSString *message = SCDictionary(json[@"error"])[@"message"];
        [self fail:[message isKindOfClass:NSString.class] ? message : @"AI 服务返回错误，请重新生成。"];
        return;
    }
    id choices = json[@"choices"] ?: SCDictionary(json[@"output"])[@"choices"];
    NSDictionary *choice = [choices isKindOfClass:NSArray.class] && [choices count]
        ? SCDictionary(choices[0]) : @{};
    NSDictionary *delta = SCDictionary(choice[@"delta"] ?: choice[@"message"]);
    NSString *content = SCContentText(delta[@"content"] ?: json[@"content"]);
    if (content.length) {
        self.emit(@{@"success": @1, @"event": @"delta", @"content": content});
    }
    id reason = choice[@"finish_reason"];
    if ([reason isKindOfClass:NSString.class] && [reason length]) {
        self.terminal = YES;
        self.emit(@{@"success": @1, @"event": @"end"});
    }
}

- (void)consumeLine:(NSData *)data {
    NSString *line = [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding];
    if (!line) {
        [self fail:@"AI 流式响应编码无效，请重新生成。"];
        return;
    }
    if ([line hasPrefix:@"\uFEFF"]) {
        line = [line substringFromIndex:1];
    }
    if (!line.length) {
        [self consumeEvent];
    } else if ([line hasPrefix:@"data:"]) {
        NSString *value = [line substringFromIndex:5];
        [self.dataLines addObject:[value hasPrefix:@" "] ? [value substringFromIndex:1] : value];
    } else if ([line isEqualToString:@"data"]) {
        [self.dataLines addObject:@""];
    }
}

- (void)appendData:(NSData *)data {
    if (self.terminal) {
        return;
    }
    [self.buffer appendData:data];
    NSUInteger start = 0;
    const unsigned char *bytes = self.buffer.bytes;
    for (NSUInteger i = 0; i < self.buffer.length && !self.terminal; i++) {
        if (bytes[i] != '\n' && bytes[i] != '\r') {
            continue;
        }
        if (bytes[i] == '\r' && i + 1 == self.buffer.length) {
            break; // CRLF may be split between packets.
        }
        [self consumeLine:[self.buffer subdataWithRange:NSMakeRange(start, i - start)]];
        if (bytes[i] == '\r' && i + 1 < self.buffer.length && bytes[i + 1] == '\n') {
            i++;
        }
        start = i + 1;
    }
    [self.buffer replaceBytesInRange:NSMakeRange(0, start) withBytes:NULL length:0];
    if (self.buffer.length > 1024 * 1024 || self.dataLines.count > 4096) {
        [self fail:@"AI 流式响应过大，请重新生成。"];
    }
}

- (void)finish {
    // Flush an unterminated final line/event, but do not silently accept a
    // connection that closed without [DONE] or a finish_reason.
    [self appendData:[@"\n\n" dataUsingEncoding:NSUTF8StringEncoding]];
    if (!self.terminal) {
        [self fail:@"AI 流式连接提前结束，请重新生成。"];
    }
}
@end

@interface StockChatStreamRequest () <NSURLSessionDataDelegate>
@property (nonatomic, copy) NSDictionary *parameters;
@property (nonatomic, copy) KuiklyRenderCallback callback;
@property (nonatomic, strong) NSURLSession *session;
@property (nonatomic, strong) NSURLSessionDataTask *task;
@property (nonatomic, strong) StockChatStreamDecoder *decoder;
@property (nonatomic, strong) NSMutableData *responseBody;
@property (nonatomic, strong) NSMutableString *pendingDelta;
@property (nonatomic, strong) dispatch_queue_t queue;
@property (nonatomic, assign) NSInteger statusCode;
@property (nonatomic, assign) BOOL isEventStream;
@property (nonatomic, assign) BOOL finished;
@property (nonatomic, assign) BOOL flushScheduled;
@property (nonatomic, assign) BOOL emittedDelta;
@end

@implementation StockChatStreamRequest
- (instancetype)initWithParameters:(NSDictionary *)parameters callback:(KuiklyRenderCallback)callback {
    if (self = [super init]) {
        _parameters = [parameters copy];
        _callback = [callback copy];
        _queue = dispatch_queue_create("com.stockchat.ios.chat-stream", DISPATCH_QUEUE_SERIAL);
        _responseBody = [NSMutableData data];
        _pendingDelta = [NSMutableString string];
        _decoder = [StockChatStreamDecoder new];
        __weak typeof(self) weakSelf = self;
        _decoder.emit = ^(NSDictionary *event) { [weakSelf receiveEvent:event]; };
    }
    return self;
}

- (void)deliver:(NSDictionary *)event {
    KuiklyRenderCallback callback = self.callback;
    dispatch_async(dispatch_get_main_queue(), ^{ if (callback) callback(event); });
}

- (void)flushDelta {
    self.flushScheduled = NO;
    if (self.pendingDelta.length) {
        [self deliver:@{@"success": @1, @"event": @"delta", @"content": self.pendingDelta.copy}];
        [self.pendingDelta setString:@""];
    }
}

- (void)receiveEvent:(NSDictionary *)event {
    if (self.finished) return;
    if ([event[@"event"] isEqual:@"delta"]) {
        [self.pendingDelta appendString:event[@"content"]];
        if (!self.emittedDelta) {
            self.emittedDelta = YES;
            [self flushDelta];
        } else if (!self.flushScheduled) {
            self.flushScheduled = YES;
            dispatch_after(dispatch_time(DISPATCH_TIME_NOW, 50 * NSEC_PER_MSEC), self.queue, ^{
                if (!self.finished) [self flushDelta];
            });
        }
        return;
    }
    [self flushDelta];
    self.finished = YES;
    [self deliver:event];
    self.callback = nil;
    self.parameters = nil;
    [self.session invalidateAndCancel];
    self.session = nil;
    self.task = nil;
}

- (void)start {
    dispatch_async(self.queue, ^{
        if (self.finished || self.session) return;
        NSString *urlString = self.parameters[@"url"];
        NSURL *url = [urlString isKindOfClass:NSString.class] ? [NSURL URLWithString:urlString] : nil;
        NSMutableDictionary *body = [SCDictionary(self.parameters[@"requestBody"]) mutableCopy];
        if (!url.host.length || ![@[@"https", @"http"] containsObject:url.scheme.lowercaseString] || !body.count) {
            [self receiveEvent:SCStreamFailure(@"INVALID_STREAM_REQUEST", @"AI 流式请求参数无效。")];
            return;
        }
        body[@"stream"] = @YES;
        NSMutableURLRequest *request = [NSMutableURLRequest requestWithURL:url];
        request.HTTPMethod = @"POST";
        request.timeoutInterval = 90;
        request.HTTPBody = [NSJSONSerialization dataWithJSONObject:body options:0 error:nil];
        NSDictionary *headers = SCDictionary(self.parameters[@"headers"]);
        for (NSString *key in headers) {
            if ([headers[key] isKindOfClass:NSString.class]) [request setValue:headers[key] forHTTPHeaderField:key];
        }
        NSString *apiKey = self.parameters[@"apiKey"];
        if (![request valueForHTTPHeaderField:@"Authorization"].length && [apiKey isKindOfClass:NSString.class] && apiKey.length) {
            [request setValue:[@"Bearer " stringByAppendingString:apiKey] forHTTPHeaderField:@"Authorization"];
        }
        [request setValue:@"application/json" forHTTPHeaderField:@"Content-Type"];
        [request setValue:@"text/event-stream" forHTTPHeaderField:@"Accept"];
        NSURLSessionConfiguration *config = NSURLSessionConfiguration.ephemeralSessionConfiguration;
        config.timeoutIntervalForRequest = 90;
        config.timeoutIntervalForResource = 180;
        NSOperationQueue *delegateQueue = [NSOperationQueue new];
        delegateQueue.maxConcurrentOperationCount = 1;
        delegateQueue.underlyingQueue = self.queue;
        self.session = [NSURLSession sessionWithConfiguration:config delegate:self delegateQueue:delegateQueue];
        self.task = [self.session dataTaskWithRequest:request];
        self.parameters = nil;
        [self.task resume];
    });
}

- (void)cancel {
    dispatch_async(self.queue, ^{
        [self receiveEvent:SCStreamFailure(@"STREAM_CANCELLED", @"AI 请求已取消。")];
    });
}

- (void)URLSession:(NSURLSession *)session dataTask:(NSURLSessionDataTask *)dataTask
didReceiveResponse:(NSURLResponse *)response completionHandler:(void (^)(NSURLSessionResponseDisposition))completionHandler {
    self.statusCode = [(NSHTTPURLResponse *)response statusCode];
    self.isEventStream = [response.MIMEType.lowercaseString isEqualToString:@"text/event-stream"];
    completionHandler(self.finished ? NSURLSessionResponseCancel : NSURLSessionResponseAllow);
}

- (void)URLSession:(NSURLSession *)session dataTask:(NSURLSessionDataTask *)dataTask didReceiveData:(NSData *)data {
    if (self.finished) return;
    if (self.statusCode >= 200 && self.statusCode < 300 && self.isEventStream) {
        [self.decoder appendData:data];
    } else if (self.responseBody.length + data.length <= 4 * 1024 * 1024) {
        [self.responseBody appendData:data];
    } else {
        [self receiveEvent:SCStreamFailure(@"RESPONSE_TOO_LARGE", @"AI 响应过大，请重新生成。")];
    }
}

- (void)URLSession:(NSURLSession *)session task:(NSURLSessionTask *)task didCompleteWithError:(NSError *)error {
    if (self.finished) return;
    if (error) {
        [self receiveEvent:SCStreamFailure(@"STREAM_NETWORK_ERROR", error.localizedDescription)];
    } else if (self.statusCode < 200 || self.statusCode >= 300) {
        NSDictionary *json = SCDictionary([[NSString alloc] initWithData:self.responseBody encoding:NSUTF8StringEncoding]);
        id message = SCDictionary(json[@"error"])[@"message"];
        [self receiveEvent:SCStreamFailure(@"STREAM_HTTP_ERROR", [message isKindOfClass:NSString.class]
            ? message : [NSString stringWithFormat:@"AI 请求失败（HTTP %ld）。", (long)self.statusCode])];
    } else if (self.isEventStream) {
        [self.decoder finish];
    } else {
        // Some compatible providers ignore stream=true and return a JSON result.
        NSDictionary *json = SCDictionary([[NSString alloc] initWithData:self.responseBody encoding:NSUTF8StringEncoding]);
        id choices = json[@"choices"];
        NSDictionary *choice = [choices isKindOfClass:NSArray.class] && [choices count] ? SCDictionary(choices[0]) : @{};
        NSString *text = SCContentText(SCDictionary(choice[@"message"])[@"content"]);
        if (text.length) {
            [self receiveEvent:@{@"success": @1, @"event": @"delta", @"content": text}];
            [self receiveEvent:@{@"success": @1, @"event": @"end"}];
        } else {
            [self receiveEvent:SCStreamFailure(@"INVALID_STREAM_RESPONSE", @"AI 没有返回有效回答，请重新生成。")];
        }
    }
}
@end
