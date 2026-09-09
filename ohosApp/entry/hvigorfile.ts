import { hapTasks } from '@ohos/hvigor-ohos-plugin';
import { kuiklyCompilePlugin, kuiklyCopyAssetsPlugin } from 'kuikly-ohos-compile-plugin';
import { stockChatLocalConfigPlugin } from '../scripts/local-config-plugin';

export default {
    system: hapTasks,  /* Built-in plugin of Hvigor. It cannot be modified. */
    plugins:[kuiklyCompilePlugin(), kuiklyCopyAssetsPlugin(), stockChatLocalConfigPlugin()]
}
