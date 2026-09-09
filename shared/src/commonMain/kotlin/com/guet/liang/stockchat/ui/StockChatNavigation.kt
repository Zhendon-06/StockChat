package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.base.bridgeModule
import com.guet.liang.stockchat.data.ConversationMindMapArtifactGenerator
import com.guet.liang.stockchat.data.ConversationStockComparisonGenerator
import com.guet.liang.stockchat.data.providerSymbolForQuote
import com.guet.liang.stockchat.model.ChatRole
import com.guet.liang.stockchat.model.StockQuote
import com.guet.liang.stockchat.model.VoiceInputState
import com.guet.liang.stockchat.ui.settings.SETTINGS_PAGE_NAME
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

// 页面跳转：详情页、图片预览、设置、表格/思维导图产物与返回处理。

internal fun StockChatPage.openStockDetail(
    quote: StockQuote,
    sourceTab: Int,
) {
    if (selectedHomeTab != sourceTab) {
        return
    }
    cancelVoiceInput()
    if (inputRefReady) {
        inputRef.view?.blur()
    }
    val params = JSONObject()
    params.put("symbol", providerSymbolForQuote(quote) ?: quote.symbol)
    pageData.params.optString("qwenApiKey").trim()
        .takeIf(String::isNotBlank)
        ?.let { params.put("qwenApiKey", it) }
    acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(STOCK_DETAIL_PAGE_NAME, params)
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
    acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(IMAGE_PREVIEW_PAGE_NAME, params)
}

internal fun StockChatPage.openSettings() {
    closeDrawer()
    acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(
        SETTINGS_PAGE_NAME,
        JSONObject(),
    )
}

internal fun StockChatPage.createConversationStockComparison() {
    closeConversationMenu()
    if (messages.none { it.role == ChatRole.USER }) {
        bridgeModule.toast("当前对话还没有可对比的内容")
        return
    }
    try {
        val title = conversationTitle()
        val comparison = ConversationStockComparisonGenerator.generate(
            title = title,
            messages = messages,
        )
        if (comparison.rows.isEmpty()) {
            bridgeModule.toast("当前会话未识别到股票或指数")
            return
        }
        persistChatHistory()
        val snapshot = ConversationStockComparisonGenerator.toArtifactSnapshot(comparison)
        val artifactId = tableArtifactRepository.upsert(activeSessionId, snapshot)
        openTableArtifact(artifactId)
    } catch (_: Throwable) {
        bridgeModule.toast("会话表格对比生成失败，请重试")
    }
}

internal fun StockChatPage.createConversationMindMapArtifact() {
    closeConversationMenu()
    if (messages.none { it.role == ChatRole.USER }) {
        bridgeModule.toast("当前对话还没有可梳理的内容")
        return
    }
    try {
        persistChatHistory()
        val snapshot = ConversationMindMapArtifactGenerator.generate(
            title = conversationTitle(),
            messages = messages,
        )
        val artifactId = mindMapArtifactRepository.upsert(activeSessionId, snapshot)
        openMindMapArtifact(artifactId)
    } catch (_: Throwable) {
        bridgeModule.toast("思维导图生成失败，请重试")
    }
}

internal fun StockChatPage.openStockComparisonLibrary() {
    closeDrawer()
    closeConversationMenu()
    val params = JSONObject()
    pageData.params.optString("qwenApiKey").trim()
        .takeIf(String::isNotBlank)
        ?.let { params.put("qwenApiKey", it) }
    acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(
        CONVERSATION_TABLE_ARTIFACTS_PAGE_NAME,
        params,
    )
}

internal fun StockChatPage.openMindMapArtifactLibrary() {
    closeDrawer()
    closeConversationMenu()
    acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(
        CONVERSATION_MIND_MAP_ARTIFACTS_PAGE_NAME,
        JSONObject(),
    )
}

internal fun StockChatPage.openFavoriteCards() {
    closeDrawer()
    closeConversationMenu()
    val params = JSONObject()
    pageData.params.optString("qwenApiKey").trim()
        .takeIf(String::isNotBlank)
        ?.let { params.put("qwenApiKey", it) }
    acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(
        FAVORITE_CARDS_PAGE_NAME,
        params,
    )
}

internal fun StockChatPage.openTableArtifact(artifactId: Long) {
    val params = JSONObject()
    params.put(CONVERSATION_TABLE_ARTIFACT_ID_PARAM, artifactId.toString())
    pageData.params.optString("qwenApiKey").trim()
        .takeIf(String::isNotBlank)
        ?.let { params.put("qwenApiKey", it) }
    acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(
        CONVERSATION_TABLE_ARTIFACT_PAGE_NAME,
        params,
    )
}

internal fun StockChatPage.openMindMapArtifact(artifactId: Long) {
    val params = JSONObject()
    params.put(CONVERSATION_MIND_MAP_ARTIFACT_ID_PARAM, artifactId.toString())
    acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(
        CONVERSATION_MIND_MAP_ARTIFACT_PAGE_NAME,
        params,
    )
}

internal fun StockChatPage.handleBackRequest() {
    val layer = StockChatBackStack.topLayer(
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
