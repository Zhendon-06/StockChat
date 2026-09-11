package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.base.bridgeModule
import com.guet.liang.stockchat.base.openRoute
import com.guet.liang.stockchat.base.stockDetailRouteParams
import com.guet.liang.stockchat.controller.ArtifactResult
import com.guet.liang.stockchat.model.StockQuote
import com.guet.liang.stockchat.model.VoiceInputState
import com.guet.liang.stockchat.ui.settings.SETTINGS_PAGE_NAME
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

// 页面跳转：详情页、图片预览、设置、表格/思维导图产物与返回处理。

internal fun StockChatPage.openStockDetail(quote: StockQuote, sourceTab: Int) {
    if (selectedHomeTab != sourceTab) {
        return
    }
    cancelVoiceInput()
    if (inputRefReady) {
        inputRef.view?.blur()
    }
    openRoute(
        STOCK_DETAIL_PAGE_NAME,
        // 把卡片上已有的行情一起带过去：详情页首帧就能显示名称与价格，网络请求期间只有细节是骨架
        stockDetailRouteParams(
            artifactController.providerSymbol(quote),
            pageData.params.optString("qwenApiKey"),
            preview = quote,
            aiProxyBaseUrl = pageData.params.optString("aiProxyBaseUrl"),
        ),
    )
}

internal fun StockChatPage.openImagePreview(imageUri: String) {
    if (selectedHomeTab != HOME_TAB_CHAT || imageUri.isBlank()) {
        return
    }
    cancelVoiceInput()
    if (inputRefReady) {
        inputRef.view?.blur()
    }
    val params = JSONObject()
    params.put(ImagePreviewPage.IMAGE_URI_PARAM, imageUri)
    openRoute(IMAGE_PREVIEW_PAGE_NAME, params)
}

internal fun StockChatPage.openSettings() {
    closeDrawer()
    openRoute(SETTINGS_PAGE_NAME)
}

internal fun StockChatPage.createConversationStockComparison() {
    closeConversationMenu()
    when (val result = artifactController.createTable(activeSessionId, conversationTitle, sessionController.messages)) {
        is ArtifactResult.Success -> {
            refreshRecentSessions()
            openTableArtifact(result.value)
        }
        is ArtifactResult.Failure -> bridgeModule.toast(result.message)
    }
}

internal fun StockChatPage.createConversationMindMapArtifact() {
    closeConversationMenu()
    when (val result = artifactController.createMindMap(activeSessionId, conversationTitle, sessionController.messages)) {
        is ArtifactResult.Success -> {
            refreshRecentSessions()
            openMindMapArtifact(result.value)
        }
        is ArtifactResult.Failure -> bridgeModule.toast(result.message)
    }
}

internal fun StockChatPage.openStockComparisonLibrary() {
    closeDrawer()
    closeConversationMenu()
    val params = JSONObject()
    pageData.params.optString("qwenApiKey").trim().takeIf(String::isNotBlank)?.let { params.put("qwenApiKey", it) }
    pageData.params.optString("aiProxyBaseUrl").trim().takeIf(String::isNotBlank)?.let { params.put("aiProxyBaseUrl", it) }
    openRoute(CONVERSATION_TABLE_ARTIFACTS_PAGE_NAME, params)
}

internal fun StockChatPage.openMindMapArtifactLibrary() {
    closeDrawer()
    closeConversationMenu()
    openRoute(CONVERSATION_MIND_MAP_ARTIFACTS_PAGE_NAME, JSONObject())
}

internal fun StockChatPage.openFavoriteCards() {
    closeDrawer()
    closeConversationMenu()
    val params = JSONObject()
    pageData.params.optString("qwenApiKey").trim().takeIf(String::isNotBlank)?.let { params.put("qwenApiKey", it) }
    pageData.params.optString("aiProxyBaseUrl").trim().takeIf(String::isNotBlank)?.let { params.put("aiProxyBaseUrl", it) }
    openRoute(FAVORITE_CARDS_PAGE_NAME, params)
}

internal fun StockChatPage.openTableArtifact(artifactId: Long) {
    val params = JSONObject()
    params.put(CONVERSATION_TABLE_ARTIFACT_ID_PARAM, artifactId.toString())
    pageData.params.optString("qwenApiKey").trim().takeIf(String::isNotBlank)?.let { params.put("qwenApiKey", it) }
    pageData.params.optString("aiProxyBaseUrl").trim().takeIf(String::isNotBlank)?.let { params.put("aiProxyBaseUrl", it) }
    openRoute(CONVERSATION_TABLE_ARTIFACT_PAGE_NAME, params)
}

internal fun StockChatPage.openMindMapArtifact(artifactId: Long) {
    val params = JSONObject()
    params.put(CONVERSATION_MIND_MAP_ARTIFACT_ID_PARAM, artifactId.toString())
    openRoute(CONVERSATION_MIND_MAP_ARTIFACT_PAGE_NAME, params)
}

internal fun StockChatPage.handleBackRequest() {
    val layer =
        StockChatBackStack.topLayer(
            StockChatBackState(
                renameDialogOpen = renameSessionId.isNotEmpty(),
                voiceRecording = voicePressActive || voiceInputState != VoiceInputState.IDLE,
                modelMenuOpen = modelMenuOpen,
                conversationMenuOpen = conversationMenuOpen,
                messageMenuOpen = messageMenuTargetId.isNotEmpty(),
                imagePickerOpen = imagePickerOpen,
                drawerOpen = drawerOpen,
                composerOpen = composerFocused || keyboardVisible || keyboardHeight > 0f,
            )
        )
    when (layer) {
        StockChatBackLayer.RENAME_DIALOG -> closeRenameDialog()
        StockChatBackLayer.VOICE_RECORDING -> cancelVoiceInput()
        StockChatBackLayer.MODEL_MENU -> closeModelMenu()
        StockChatBackLayer.CONVERSATION_MENU -> closeConversationMenu()
        StockChatBackLayer.MESSAGE_MENU -> messageMenuTargetId = ""
        StockChatBackLayer.IMAGE_PICKER -> imagePickerOpen = false
        StockChatBackLayer.DRAWER -> closeDrawer()
        StockChatBackLayer.COMPOSER -> handleBlankAreaTap()
        StockChatBackLayer.PAGE -> {
            acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
        }
    }
}
