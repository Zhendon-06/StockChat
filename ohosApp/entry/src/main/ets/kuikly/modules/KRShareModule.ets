import { common } from '@kit.AbilityKit';
import { uniformTypeDescriptor as utd } from '@kit.ArkData';
import { BusinessError } from '@kit.BasicServicesKit';
import { systemShare } from '@kit.ShareKit';
import { KRAny, KuiklyRenderBaseModule, KuiklyRenderCallback } from '@kuikly-open/render';

export class KRShareModule extends KuiklyRenderBaseModule {
  static readonly MODULE_NAME = 'HRShareModule';
  private static readonly SHARE = 'share';

  syncMode(): boolean {
    return false;
  }

  call(method: string, params: KRAny, callback: KuiklyRenderCallback): KRAny {
    if (method !== KRShareModule.SHARE) {
      callback?.({
        'success': 0,
        'cancelled': 0,
        'errorCode': 'METHOD_NOT_FOUND',
        'errorMessage': '分享模块不支持该操作。'
      });
      return null;
    }
    this.share(params, callback);
    return null;
  }

  private share(params: KRAny, callback: KuiklyRenderCallback | null) {
    if (params == null || typeof params !== 'string') {
      this.fail(callback, 'INVALID_SHARE_PAYLOAD', '分享内容格式无效。');
      return;
    }

    let payload: Record<string, string>;
    try {
      payload = JSON.parse(params as string) as Record<string, string>;
    } catch (error) {
      this.fail(callback, 'INVALID_SHARE_PAYLOAD', '分享内容格式无效。');
      return;
    }
    const title = this.normalizedString(payload['title']);
    const text = this.normalizedString(payload['text']);
    const url = this.normalizedString(payload['url']);
    const contentParts: string[] = [];
    if (text.length > 0) {
      contentParts.push(text);
    }
    if (url.length > 0) {
      contentParts.push(url);
    }
    let content = contentParts.join('\n\n');
    if (content.length === 0) {
      content = title;
    }
    if (content.length === 0) {
      this.fail(callback, 'EMPTY_SHARE_CONTENT', '没有可分享的内容。');
      return;
    }

    const context = getContext(this) as common.UIAbilityContext;
    const shareData: systemShare.SharedData = new systemShare.SharedData({
      utd: utd.UniformDataType.PLAIN_TEXT,
      title: title,
      content: content
    });
    const controller = new systemShare.ShareController(shareData);
    controller.show(context, {
      selectionMode: systemShare.SelectionMode.SINGLE,
      previewMode: systemShare.SharePreviewMode.DETAIL
    }).then(() => {
      callback?.({
        'success': 1,
        'cancelled': 0,
        'errorCode': '',
        'errorMessage': ''
      });
    }).catch((error: BusinessError) => {
      this.fail(
        callback,
        `SHARE_FAILED_${error.code}`,
        error.message || '系统分享打开失败，请稍后重试。'
      );
    });
  }

  private normalizedString(value: string | undefined): string {
    return typeof value === 'string' ? value.trim() : '';
  }

  private fail(
    callback: KuiklyRenderCallback | null,
    errorCode: string,
    errorMessage: string
  ) {
    callback?.({
      'success': 0,
      'cancelled': 0,
      'errorCode': errorCode,
      'errorMessage': errorMessage
    });
  }
}
