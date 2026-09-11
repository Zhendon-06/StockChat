package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.base.openRoute
import com.guet.liang.stockchat.model.AnswerMode
import com.guet.liang.stockchat.model.ChatModelOption
import com.guet.liang.stockchat.ui.settings.MODEL_CONFIGURATION_PAGE_NAME
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

// 模型面板只渲染已发布选项，目录请求和服务配置由 controller 管理。

internal fun StockChatPage.selectModel(modelId: String) {
    modelSelectionController.selectModel(modelId)
    closeModelMenu()
}

internal fun StockChatPage.selectAnswerMode(mode: AnswerMode) {
    if (mode != answerMode) {
        modelSelectionController.selectAnswerMode(mode)
    }
}

internal fun StockChatPage.selectedModel(): ChatModelOption = modelSelectionController.state.selectedModel

// composer 只显示当前模型提供商名称，Drawer 展示该提供商的模型列表
internal fun StockChatPage.composerModelDisplayName(): String {
    return composerModelLabel
}

internal fun StockChatPage.composerModelIconAsset(): String {
    return composerModelIcon
}

internal fun StockChatPage.configureChatProvider() = modelSelectionController.configureChatProvider()

internal fun StockChatPage.fetchDrawerModels(force: Boolean = false) = modelSelectionController.fetchDrawerModels(force)

internal fun StockChatPage.retryDrawerModels() = modelSelectionController.retryDrawerModels()

internal fun StockChatPage.openModelConfiguration() {
    closeModelMenu()
    openRoute(MODEL_CONFIGURATION_PAGE_NAME, JSONObject())
}
