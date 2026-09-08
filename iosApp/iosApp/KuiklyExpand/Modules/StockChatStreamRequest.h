#import <Foundation/Foundation.h>
#import <OpenKuiklyIOSRender/KRBaseModule.h>

NS_ASSUME_NONNULL_BEGIN

@interface StockChatStreamRequest : NSObject
- (instancetype)initWithParameters:(NSDictionary *)parameters callback:(KuiklyRenderCallback)callback;
- (void)start;
- (void)cancel;
@end

NS_ASSUME_NONNULL_END
