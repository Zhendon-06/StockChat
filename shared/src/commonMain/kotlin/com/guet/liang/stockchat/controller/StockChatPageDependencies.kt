package com.guet.liang.stockchat.controller

import com.guet.liang.stockchat.base.bridgeModule
import com.guet.liang.stockchat.base.setTimeout
import com.guet.liang.stockchat.data.AliyunStockChatDataSource
import com.guet.liang.stockchat.data.ChatHistoryDatabase
import com.guet.liang.stockchat.data.MimoSpeechRecognitionService
import com.guet.liang.stockchat.data.MimoSpeechSynthesisService
import com.guet.liang.stockchat.data.MimoVoiceApiConfig
import com.guet.liang.stockchat.data.ModelCatalogService
import com.guet.liang.stockchat.data.StockChatSettingsStore
import com.guet.liang.stockchat.data.TencentTodayMarketDataSource
import com.guet.liang.stockchat.model.SettingsSnapshot
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.pager.Pager

/**
 * All services used by the chat page are assembled here, outside the UI package.
 * The page receives ready-to-use controllers and platform services through this boundary.
 */
internal data class StockChatPageDependencies(
    val speechRecognitionService: SpeechRecognitionService,
    val speechSynthesisService: SpeechSynthesisService,
    val sessionController: ChatSessionController,
    val sendController: ChatSendController,
    val artifactController: ArtifactController,
    val todayMarketDataSource: TodayMarketLoader,
    val modelSelectionController: ModelSelectionController,
    val settingsController: SettingsController,
)

/** Reads saved appearance without acquiring a page, network module, or history database. */
internal fun savedAppearanceSnapshot(): SettingsSnapshot = SettingsController(StockChatSettingsStore.repository).snapshot()

/** Creates the complete chat-page object graph at the application composition boundary. */
internal fun Pager.stockChatPageDependencies(
    onModelChanged: (ModelSelectionState) -> Unit,
    onSessionChanged: (ChatSessionController) -> Unit,
    onSendingChanged: (Boolean) -> Unit,
): StockChatPageDependencies {
    val network = acquireModule<NetworkModule>(NetworkModule.MODULE_NAME)
    val voiceConfig = MimoVoiceApiConfig(apiKey = pageData.params.optString("mimoVoiceApiKey").trim())
    val modelCatalog = ModelCatalogService(network)
    val nativeStreamingEnabled =
        pageData.params.optInt("aliyunNativeStreaming", pageData.params.optInt("mimoNativeStreaming", 1)) == 1
    val modelSelection =
        ModelSelectionController(
            settings = StockChatSettingsStore.repository,
            catalog = SettingsCatalogRepository { url, key, callback -> modelCatalog.load(url, key, callback) },
            sourceFactory = { config ->
                AliyunStockChatDataSource(network, config, bridgeModule, nativeStreamingEnabled && config.supportsStreaming)
            },
            scheduleTimeout = { delay, callback -> setTimeout(delay, callback) },
            routeApiKey = pageData.params.optString("aiProxyToken").trim()
                .ifBlank { pageData.params.optString("qwenApiKey") },
            routeBaseUrl = pageData.params.optString("aiProxyBaseUrl"),
            onChanged = onModelChanged,
        )
    val history = ChatHistoryDatabase.repository()
    val session = ChatSessionController(history, onSessionChanged)
    val send = ChatSendController({ modelSelection.dataSource }, session, { modelSelection.state.selectedModel }, onSendingChanged)
    return StockChatPageDependencies(
        speechRecognitionService = MimoSpeechRecognitionService(network, voiceConfig),
        speechSynthesisService =
            MimoSpeechSynthesisService(
                networkModule = network,
                config = voiceConfig,
                bridgeModule = bridgeModule,
                useNativeStreaming = pageData.params.optInt("mimoNativeStreaming") == 1,
            ),
        sessionController = session,
        sendController = send,
        artifactController = artifactController(),
        todayMarketDataSource = TencentTodayMarketDataSource(network),
        modelSelectionController = modelSelection,
        settingsController = settingsController(),
    )
}

/** Creates settings dependencies at the UI assembly boundary. */
internal fun Pager.settingsController(): SettingsController {
    val network = acquireModule<NetworkModule>(NetworkModule.MODULE_NAME)
    val history = ChatHistoryDatabase.repository()
    val catalog = ModelCatalogService(network)
    return SettingsController(
        settings = StockChatSettingsStore.repository,
        history = object : SettingsHistoryRepository {
            override fun archivedSessions() = history.loadArchivedSessions()
            override fun restoreSession(sessionId: String) = history.restoreSession(sessionId)
        },
        catalog = SettingsCatalogRepository(catalog::load),
    )
}
