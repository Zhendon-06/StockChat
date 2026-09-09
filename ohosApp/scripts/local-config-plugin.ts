import { hvigor, HvigorNode, HvigorPlugin } from '@ohos/hvigor';
import * as path from 'path';

const { generateLocalConfig } = require('./local-config.cjs');

// Runs for both DevEco Run and runOhosApp.sh, including incremental builds.
export function stockChatLocalConfigPlugin(): HvigorPlugin {
    return {
        pluginId: 'stockChatLocalConfig',
        apply(node: HvigorNode) {
            node.registerTask({
                name: 'stockchat_local_config',
                run() {
                    const buildMode = hvigor.getParameter().getExtParams().buildMode || 'debug';
                    generateLocalConfig({
                        projectRoot: path.resolve(node.getNodePath(), '../..'),
                        outputPath: path.join(node.getNodePath(), 'build/default/intermediates/res/default',
                            'resources/rawfile/stockchat_local_config.json'),
                        buildMode,
                        environment: process.env,
                    });
                },
                dependencies: ['default@CompileResource'],
                postDependencies: ['default@CompileArkTS', 'default@PackageHap'],
            });
        },
    };
}
