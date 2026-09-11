import { common } from '@kit.AbilityKit';
import { relationalStore } from '@kit.ArkData';
import { BusinessError } from '@kit.BasicServicesKit';

// 进程内唯一的 RdbStore：页面渲染前打开，供同步桥接模块直接使用。
class StockChatDatabaseStore {
  private store: relationalStore.RdbStore | null = null;
  private opening: Promise<void> | null = null;

  current(): relationalStore.RdbStore | null {
    return this.store;
  }

  open(context: common.Context): Promise<void> {
    if (this.store != null) {
      return Promise.resolve();
    }
    if (this.opening == null) {
      const config: relationalStore.StoreConfig = {
        name: 'stockchat.db',
        securityLevel: relationalStore.SecurityLevel.S1,
      };
      this.opening = relationalStore.getRdbStore(context, config).then((store: relationalStore.RdbStore) => {
        this.store = store;
      }).catch((error: BusinessError) => {
        console.error(`StockChat open relational store failed: ${error.code} ${error.message}`);
        this.opening = null;
      });
    }
    return this.opening;
  }
}

export const stockChatDatabaseStore = new StockChatDatabaseStore();
