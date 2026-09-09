package com.guet.liang.stockchat.base

import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.pager.Pager

/** 关闭当前 Kuikly 页面；各页面统一复用，避免每页各写一份同样的路由调用。 */
internal fun Pager.closePage() {
    acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
}
