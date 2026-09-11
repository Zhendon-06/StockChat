import { common } from '@kit.AbilityKit';
import util from '@ohos.util';

// Keys are supplied by the build's rawfile resource, never embedded in source.
export function loadStockChatLocalConfig(context: common.UIAbilityContext): Record<string, string> {
  try {
    const content = context.resourceManager.getRawFileContentSync('stockchat_local_config.json');
    const decoder = util.TextDecoder.create('utf-8');
    const config = JSON.parse(decoder.decodeToString(content)) as Record<string, string>;
    const localConfig: Record<string, string> = {};
    localConfig['qwenApiKey'] = typeof config['qwenApiKey'] === 'string' ? config['qwenApiKey'] : '';
    localConfig['mimoVoiceApiKey'] = typeof config['mimoVoiceApiKey'] === 'string' ? config['mimoVoiceApiKey'] : '';
    localConfig['aiProxyBaseUrl'] = typeof config['aiProxyBaseUrl'] === 'string' ? config['aiProxyBaseUrl'] : '';
    localConfig['aiProxyToken'] = typeof config['aiProxyToken'] === 'string' ? config['aiProxyToken'] : '';
    return localConfig;
  } catch {
    return {};
  }
}
