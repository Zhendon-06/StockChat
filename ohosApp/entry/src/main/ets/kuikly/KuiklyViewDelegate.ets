import {
  IKuiklyViewDelegate,
  KRRenderModuleExportCreator,
  KRRenderViewExportCreator
} from '@kuikly-open/render';
import { KRMyView } from './components/KRMyView';
import { KRBridgeModule } from './modules/KRBridgeModule';
import { KRMyModule } from './modules/KRMyModule';
import { KRShareModule } from './modules/KRShareModule';

export class KuiklyViewDelegate extends IKuiklyViewDelegate {
  getCustomRenderViewCreatorRegisterMap(): Map<string, KRRenderViewExportCreator> {
    const map: Map<string, KRRenderViewExportCreator> = new Map();
    map.set(KRMyView.VIEW_NAME, () => new KRMyView());
    return map;
  }

  getCustomRenderModuleCreatorRegisterMap(): Map<string, KRRenderModuleExportCreator> {
    const map: Map<string, KRRenderModuleExportCreator> = new Map();
    map.set(KRBridgeModule.MODULE_NAME, () => new KRBridgeModule());
    map.set(KRShareModule.MODULE_NAME, () => new KRShareModule());
    map.set(KRMyModule.MODULE_NAME, () => new KRMyModule())
    return map;
  }
}
