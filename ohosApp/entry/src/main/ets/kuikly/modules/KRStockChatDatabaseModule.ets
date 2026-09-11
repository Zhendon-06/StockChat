import { KRAny, KuiklyRenderBaseModule, KuiklyRenderCallback } from '@kuikly-open/render';
import { relationalStore } from '@kit.ArkData';
import { BusinessError } from '@kit.BasicServicesKit';
import { stockChatDatabaseStore } from '../StockChatDatabaseStore';

// 与 Kotlin 侧 StockChatDatabaseModule 对应：同步执行 SQL，单元格编码
// {"s": 文本} / {"n": 数值} / {}(NULL)。
export class KRStockChatDatabaseModule extends KuiklyRenderBaseModule {
  static readonly MODULE_NAME = 'KRStockChatDatabaseModule';
  static readonly PING = 'ping';
  static readonly QUERY = 'query';
  static readonly EXECUTE = 'execute';
  static readonly BEGIN_TRANSACTION = 'beginTransaction';
  static readonly COMMIT = 'commit';
  static readonly ROLLBACK = 'rollback';

  // relationalStore 的 querySqlSync 始终走只读连接，事务内看不到写连接尚未提交
  // 的行；SQLDelight 仓库依赖"插入后按键查 id"，因此事务内的查询前先落盘一次
  private inTransaction: boolean = false;

  syncMode(): boolean {
    return true;
  }

  call(method: string, params: KRAny, callback: KuiklyRenderCallback | null): KRAny {
    try {
      switch (method) {
        case KRStockChatDatabaseModule.PING:
          return this.withStore((store: relationalStore.RdbStore) => this.success());
        case KRStockChatDatabaseModule.QUERY:
          return this.withStore((store: relationalStore.RdbStore) => this.query(store, params));
        case KRStockChatDatabaseModule.EXECUTE:
          return this.withStore((store: relationalStore.RdbStore) => this.execute(store, params));
        case KRStockChatDatabaseModule.BEGIN_TRANSACTION:
          return this.withStore((store: relationalStore.RdbStore) => {
            store.beginTransaction();
            this.inTransaction = true;
            return this.success();
          });
        case KRStockChatDatabaseModule.COMMIT:
          return this.withStore((store: relationalStore.RdbStore) => {
            this.inTransaction = false;
            store.commit();
            return this.success();
          });
        case KRStockChatDatabaseModule.ROLLBACK:
          return this.withStore((store: relationalStore.RdbStore) => {
            this.inTransaction = false;
            store.rollBack();
            return this.success();
          });
        default:
          return this.failure(`unknown method ${method}`);
      }
    } catch (error) {
      return this.failure(this.describe(error));
    }
  }

  private withStore(action: (store: relationalStore.RdbStore) => string): string {
    const store = stockChatDatabaseStore.current();
    if (store == null) {
      return this.failure('relational store is not open');
    }
    return action(store);
  }

  private query(store: relationalStore.RdbStore, params: KRAny): string {
    const sql = this.requestSql(params);
    const args = this.requestArgs(params);
    if (this.inTransaction) {
      // 先提交已有写入再续开事务，只读连接才能读到刚插入的行
      store.commit();
      store.beginTransaction();
    }
    const resultSet = store.querySqlSync(sql, args.length > 0 ? args : undefined);
    const rows: Array<Array<Record<string, Object>>> = [];
    try {
      const names = resultSet.columnNames;
      while (resultSet.goToNextRow()) {
        const bucket = resultSet.getRow();
        const row: Array<Record<string, Object>> = [];
        for (const name of names) {
          row.push(this.encodeCell(bucket[name]));
        }
        rows.push(row);
      }
    } finally {
      resultSet.close();
    }
    const response: Record<string, Object> = { 'ok': 1, 'rows': rows };
    return JSON.stringify(response);
  }

  private execute(store: relationalStore.RdbStore, params: KRAny): string {
    const sql = this.requestSql(params);
    const args = this.requestArgs(params);
    // executeSync 对 INSERT 返回 rowId、UPDATE/DELETE 返回影响行数；
    // 事务外不能再去 querySql 查 changes()，那会落到只读连接上得到 0
    const result = store.executeSync(sql, args.length > 0 ? args : undefined);
    const statement = sql.trimStart().slice(0, 6).toUpperCase();
    let changes = 0;
    if (typeof result === 'number') {
      changes = statement === 'INSERT' ? (result > 0 ? 1 : 0) : Math.max(0, Math.trunc(result));
    } else if (typeof result === 'bigint') {
      changes = statement === 'INSERT' ? (result > 0n ? 1 : 0) : Math.max(0, Number(result));
    }
    const response: Record<string, Object> = { 'ok': 1, 'changes': changes };
    return JSON.stringify(response);
  }

  private requestSql(params: KRAny): string {
    const request = JSON.parse(params as string) as Record<string, Object>;
    const sql = request['sql'];
    if (typeof sql !== 'string' || sql.length === 0) {
      throw new Error('missing sql');
    }
    return sql;
  }

  private requestArgs(params: KRAny): Array<relationalStore.ValueType> {
    const request = JSON.parse(params as string) as Record<string, Object>;
    const rawArgs = request['args'];
    if (!Array.isArray(rawArgs)) {
      return [];
    }
    const args: Array<relationalStore.ValueType> = [];
    for (const rawArg of rawArgs as Array<Object>) {
      args.push(this.decodeCell(rawArg as Record<string, Object>));
    }
    return args;
  }

  private decodeCell(cell: Record<string, Object>): relationalStore.ValueType {
    const text = cell['s'];
    if (typeof text === 'string') {
      return text;
    }
    const numberValue = cell['n'];
    if (typeof numberValue === 'number') {
      return numberValue;
    }
    return null;
  }

  private encodeCell(value: relationalStore.ValueType | undefined): Record<string, Object> {
    const cell: Record<string, Object> = {};
    if (typeof value === 'string') {
      cell['s'] = value;
    } else if (typeof value === 'number') {
      cell['n'] = value;
    } else if (typeof value === 'boolean') {
      cell['n'] = value ? 1 : 0;
    } else if (typeof value === 'bigint') {
      cell['n'] = Number(value);
    }
    return cell;
  }

  private success(): string {
    const response: Record<string, Object> = { 'ok': 1 };
    return JSON.stringify(response);
  }

  private failure(message: string): string {
    const response: Record<string, Object> = { 'ok': 0, 'error': message };
    return JSON.stringify(response);
  }

  private describe(error: Object): string {
    const businessError = error as BusinessError;
    const code = businessError.code != null ? `${businessError.code} ` : '';
    const message = businessError.message != null ? businessError.message : JSON.stringify(error);
    return `${code}${message}`;
  }
}
