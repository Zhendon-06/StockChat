import { ErrorReason, KRRecord, Kuikly, KuiklyRenderBaseView } from '@kuikly-open/render';
import router from '@ohos.router';
import { KuiklyViewDelegate } from '../kuikly/KuiklyViewDelegate';
import globalNativeManager from '../kuikly/MyNativeManager';
import { common } from '@kit.AbilityKit';
import { loadStockChatLocalConfig } from '../kuikly/StockChatLocalConfig';
import { stockChatDatabaseStore } from '../kuikly/StockChatDatabaseStore';

@Entry
@Component
struct Index {
  kuiklyViewDelegate = new KuiklyViewDelegate();
  pageName?: string;
  pageData?: KRRecord;
  @State exception?: string = "";
  // 本地数据库打开后再渲染页面，保证同步 SQL 桥接从首个查询起可用
  @State databaseReady: boolean = false;

  aboutToAppear() {
    AppStorage.setOrCreate<UIContext>("context", this.getUIContext());

    const params = router.getParams() as Record<string, Object>;
    this.pageName = params?.pageName as string;
    const incomingPageData = params?.pageData as KRRecord;
    const resolvedPageData = (incomingPageData ?? {}) as Record<string, Object>;
    const hostContext = this.getUIContext().getHostContext() as common.UIAbilityContext;
    const localConfig = loadStockChatLocalConfig(hostContext);
    const incomingQwenApiKey = resolvedPageData['qwenApiKey'] as string;
    if (incomingQwenApiKey == null || incomingQwenApiKey.length === 0) {
      resolvedPageData['qwenApiKey'] = localConfig['qwenApiKey'] ?? '';
    }
    const incomingMimoApiKey = resolvedPageData['mimoVoiceApiKey'] as string;
    if (incomingMimoApiKey == null || incomingMimoApiKey.length === 0) {
      resolvedPageData['mimoVoiceApiKey'] = localConfig['mimoVoiceApiKey'] ?? '';
    }
    resolvedPageData['aliyunNativeStreaming'] = 1;
    this.pageData = resolvedPageData as KRRecord;
    stockChatDatabaseStore.open(hostContext).then(() => {
      this.databaseReady = true;
    });
  }

  build() {
    Stack() {
      if (this.databaseReady) {
        Kuikly({
          pagerName: this.pageName ?? 'router',
          pagerData: this.pageData ?? {},
          delegate: this.kuiklyViewDelegate,
          nativeManager: globalNativeManager,
          onRenderException: (exception: Error, reason: ErrorReason) => {
            this.exception = `${exception.name}:\n${exception.message}`;
          },
        })
      }
      if (this.exception) {
        Text(this.exception)
      }
    }
    .width('100%')
    .height('100%')
    .expandSafeArea([SafeAreaType.KEYBOARD])
  }

  onPageShow(): void {
    this.kuiklyViewDelegate.pageDidAppear();
  }

  onPageHide(): void {
    this.kuiklyViewDelegate.pageDidDisappear();
  }
}
