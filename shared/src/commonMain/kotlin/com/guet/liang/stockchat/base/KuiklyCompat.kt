package com.guet.liang.stockchat.base

import com.tencent.kuikly.core.views.InputAttr
import com.tencent.kuikly.core.views.TextAreaAttr
import com.tencent.kuikly.core.views.TextAreaView

/*
 * Kuikly 已弃用 API 的兼容封装。
 *
 * 这些 API 的替代品在各端 render 上并不是等价实现，直接迁移会改变现有交互行为；
 * 因此把弃用调用集中收敛到这里，业务代码只依赖这一处，后续升级时也只需改这一处。
 */

/**
 * `maxTextLength(length)` 不带 `lengthLimitType` 时，各端 render 走「legacy 后置截断」路径； 新签名 `maxTextLength(length, type)` 会切换到前置拦截 + 超限回调的新路径，行为并不相同。
 * 为保持现有输入体验不变，这里保留 legacy 调用。
 */
@Suppress("DEPRECATION")
internal fun TextAreaAttr.maxTextLengthLegacy(length: Int) {
    maxTextLength(length)
}

/** 见 [TextAreaAttr.maxTextLengthLegacy]。 */
@Suppress("DEPRECATION")
internal fun InputAttr.maxTextLengthLegacy(length: Int) {
    maxTextLength(length)
}

/**
 * `TextAreaView.setText` 被标记弃用，推荐只用 `attr { text(...) }` 响应式绑定。 输入框已经绑定了 `text(inputText)`，但这里的调用是在原生输入（含输入法组合态） 与 Kotlin
 * 状态不一致时强制把文本回写到原生控件，语义与响应式 diff 不同，故保留。
 */
@Suppress("DEPRECATION")
internal fun TextAreaView.replaceNativeText(text: String) {
    setText(text)
}
