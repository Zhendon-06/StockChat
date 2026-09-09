package com.guet.liang.stockchat.base

import com.tencent.kuikly.core.base.PagerIdLazyImpl
import com.tencent.kuikly.core.base.PagerScope

/**
 * 老的方式:，需要显式传递 pagerId
 *
 * ```kotlin
 * Utils.bridgeModule(pagerId).toast("...")
 * ```
 *
 * 新方式：无需显式传递 pagerId
 *
 * ```kotlin
 * bridgeModule.toast("...")
 * ```
 */
internal val PagerScope.bridgeModule: BridgeModule by PagerIdLazyImpl { Utils.bridgeModule(it) }

internal fun PagerScope.setTimeout(delay: Int, callback: () -> Unit): String {
    return com.tencent.kuikly.core.timer.setTimeout(pagerId, delay, callback)
}
