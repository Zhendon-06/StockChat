package com.guet.liang.stockchat.base

import com.tencent.kuikly.core.base.BaseObject
import com.tencent.kuikly.core.manager.PagerManager

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal object Utils : BaseObject() {

    fun bridgeModule(pager: String): BridgeModule {
        return PagerManager.getPager(pager).acquireModule<BridgeModule>(BridgeModule.MODULE_NAME)
    }
}
