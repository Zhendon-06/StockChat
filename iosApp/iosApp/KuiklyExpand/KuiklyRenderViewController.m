#import "KuiklyRenderViewController.h"
#import "UINavigationController+FDFullscreenPopGesture.h"
#import <OpenKuiklyIOSRender/KuiklyRenderViewControllerBaseDelegator.h>
#import <OpenKuiklyIOSRender/KuiklyRenderContextProtocol.h>

#define HRWeakSelf __weak typeof(self) weakSelf = self;
@interface KuiklyRenderViewController()<KuiklyRenderViewControllerBaseDelegatorDelegate>

@property (nonatomic, strong) KuiklyRenderViewControllerBaseDelegator *delegator;

@end

@implementation KuiklyRenderViewController {
    NSDictionary *_pageData;
}

- (instancetype)initWithPageName:(NSString *)pageName pageData:(NSDictionary *)pageData {
    if (self = [super init]) {
        pageData = [self p_mergeExtParamsWithOriditalParam:pageData];
        _pageData = pageData;
        _delegator = [[KuiklyRenderViewControllerBaseDelegator alloc] initWithPageName:pageName pageData:pageData];
        _delegator.delegate = self;
    }
    return self;
}

- (void)viewDidLoad {
    [super viewDidLoad];
    self.fd_prefersNavigationBarHidden = YES;
    self.view.backgroundColor = [UIColor systemBackgroundColor];
    [_delegator viewDidLoadWithView:self.view];
    [self.navigationController setNavigationBarHidden:YES animated:NO];

}

- (void)viewDidLayoutSubviews {
    [super viewDidLayoutSubviews];
    [_delegator viewDidLayoutSubviews];

}

- (void)viewWillAppear:(BOOL)animated {
    [super viewWillAppear:animated];
    [_delegator viewWillAppear];
    [self.navigationController setNavigationBarHidden:YES animated:NO];
}

- (void)viewDidAppear:(BOOL)animated {
    [super viewDidAppear:animated];
    [_delegator viewDidAppear];
    [self.navigationController setNavigationBarHidden:YES animated:NO];
}

- (void)viewWillDisappear:(BOOL)animated {
    [super viewWillDisappear:animated];
    [_delegator viewWillDisappear];
}

- (void)viewDidDisappear:(BOOL)animated {
    [super viewDidDisappear:animated];
    [_delegator viewDidDisappear];
}

- (void)traitCollectionDidChange:(UITraitCollection *)previousTraitCollection {
    [super traitCollectionDidChange:previousTraitCollection];
    if (@available(iOS 13.0, *)) {
        UIUserInterfaceStyle previousStyle = previousTraitCollection.userInterfaceStyle;
        UIUserInterfaceStyle currentStyle = self.traitCollection.userInterfaceStyle;
        if (previousStyle != currentStyle && currentStyle != UIUserInterfaceStyleUnspecified) {
            [_delegator sendWithEvent:@"themeDidChanged"
                                  data:@{
                                      @"isNightMode": @([self stockChatIsNightMode])
                                  }];
        }
    }
}

#pragma mark - private

- (NSDictionary *)p_mergeExtParamsWithOriditalParam:(NSDictionary *)pageParam {
    NSMutableDictionary *mParam = [(pageParam ?: @{}) mutableCopy];

    return mParam;
}

#pragma mark - KuiklyRenderViewControllerDelegatorDelegate

- (UIView *)createLoadingView {
    UIView *loadingView = [[UIView alloc] init];
    loadingView.backgroundColor = [UIColor systemBackgroundColor];
    return loadingView;
}

- (UIView *)createErrorView {
    UIView *errorView = [[UIView alloc] init];
    errorView.backgroundColor = [UIColor systemBackgroundColor];
    return errorView;
}

- (NSDictionary<NSString *, NSObject *> *)contextPageData {
    NSMutableDictionary *params = [@{
        @"isNightMode": @([self stockChatIsNightMode]),
        @"aliyunNativeStreaming": @1,
    } mutableCopy];
#if DEBUG
    // Match Android's local development configuration without putting keys in source.
    NSURL *configURL = [[NSBundle mainBundle] URLForResource:@"StockChatLocalConfig" withExtension:@"plist"];
    NSDictionary *config = configURL ? [NSDictionary dictionaryWithContentsOfURL:configURL] : nil;
    NSDictionary *environment = NSProcessInfo.processInfo.environment;
    NSDictionary *keys = @{
        @"QWEN_API_KEY": @"qwenApiKey",
        @"MIMO_VOICE_API_KEY": @"mimoVoiceApiKey",
        @"AI_PROXY_BASE_URL": @"aiProxyBaseUrl",
        @"AI_PROXY_TOKEN": @"aiProxyToken",
    };
    for (NSString *key in keys) {
        NSString *value = [environment[key] stringByTrimmingCharactersInSet:NSCharacterSet.whitespaceAndNewlineCharacterSet];
        if (!value.length) {
            value = config[key];
        }
        // A route may already carry a provider-specific configuration.
        if ([value isKindOfClass:NSString.class] && value.length && ![_pageData[keys[key]] length]) {
            params[keys[key]] = value;
        }
    }
    if ([params[@"aiProxyBaseUrl"] length]) {
        params[@"qwenApiKey"] = [params[@"aiProxyToken"] length] ? params[@"aiProxyToken"] : @"proxy";
    }
#endif
    return params;
}

- (void)fetchContextCodeWithPageName:(NSString *)pageName resultCallback:(KuiklyContextCodeCallback)callback {
    if (callback) {
        // 返回对应framework名字
        callback(@"shared", nil);
    }
}

- (BOOL)stockChatIsNightMode {
    if (@available(iOS 13.0, *)) {
        return self.traitCollection.userInterfaceStyle == UIUserInterfaceStyleDark;
    }
    return NO;
}

- (void)dealloc {
    [[NSNotificationCenter defaultCenter] removeObserver:self];
}

@end
