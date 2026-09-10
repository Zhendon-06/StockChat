package com.guet.liang.stockchat.controller

import com.guet.liang.stockchat.data.InMemorySettingsRepository
import com.guet.liang.stockchat.data.StockChatDataSource
import com.guet.liang.stockchat.model.AnswerMode
import com.guet.liang.stockchat.model.ChatAnswer
import com.guet.liang.stockchat.model.ChatHistoryItem
import com.guet.liang.stockchat.model.ModelCatalogResult
import com.guet.liang.stockchat.model.ModelConfiguration
import com.guet.liang.stockchat.model.ModelOption
import com.guet.liang.stockchat.model.ModelProviderConfig
import com.guet.liang.stockchat.model.ModelProviderKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModelSelectionControllerTest {
    @Test
    fun repeatedFetchIsDeduplicatedAndTimeoutAllowsRetry() {
        val fixture = Fixture()
        fixture.controller.configureChatProvider()
        fixture.controller.fetchDrawerModels()
        fixture.controller.fetchDrawerModels()
        assertEquals(1, fixture.requests)
        fixture.timeout()
        assertFalse(fixture.controller.state.loading)
        assertTrue(fixture.controller.state.error.contains("超时"))
        fixture.controller.retryDrawerModels()
        assertEquals(2, fixture.requests)
    }

    @Test
    fun changedCredentialsRejectOldCatalogAndDoNotOverwriteSavedModels() {
        val fixture = Fixture()
        fixture.controller.configureChatProvider()
        fixture.controller.fetchDrawerModels()
        fixture.settings.saveModelProvider(provider.copy(apiKey = "changed-test-credential"))
        fixture.callback(ModelCatalogResult.Success(listOf(model.copy(id = "stale"))))
        assertEquals(listOf(model), fixture.settings.loadSnapshot().modelConfiguration.providers.single().models)
        assertFalse(fixture.controller.state.loading)
    }

    @Test
    fun catalogKeepsSelectionWhenPresentAndRebuildsSource() {
        val fixture = Fixture()
        fixture.controller.configureChatProvider()
        fixture.controller.fetchDrawerModels()
        fixture.callback(ModelCatalogResult.Success(listOf(model, model.copy(id = "second"))))
        fixture.controller.selectModel("second")
        assertEquals("second", fixture.controller.state.modelId)
        assertEquals("second", fixture.lastConfiguredModel)
    }

    @Test
    fun answerModeIsPersistedAndRebuildsTheSourceWithIt() {
        val fixture = Fixture()
        fixture.controller.configureChatProvider()
        assertEquals(AnswerMode.FAST, fixture.controller.state.answerMode)
        assertEquals(AnswerMode.FAST, fixture.lastConfiguredAnswerMode)
        fixture.controller.selectAnswerMode(AnswerMode.PRECISE)
        assertEquals(AnswerMode.PRECISE, fixture.controller.state.answerMode)
        assertEquals(AnswerMode.PRECISE, fixture.lastConfiguredAnswerMode)
        assertEquals(AnswerMode.PRECISE, fixture.settings.loadSnapshot().modelConfiguration.answerMode)
        fixture.settings.selectModel(provider.id, model.id)
        assertEquals(AnswerMode.PRECISE, fixture.settings.loadSnapshot().modelConfiguration.answerMode)
    }

    private class Fixture {
        val settings = InMemorySettingsRepository(initialModelConfiguration = ModelConfiguration(provider.id, listOf(provider)))
        var requests = 0
        var lastConfiguredModel = ""
        var lastConfiguredAnswerMode: AnswerMode? = null
        lateinit var callback: (ModelCatalogResult) -> Unit
        lateinit var timeout: () -> Unit
        val controller =
            ModelSelectionController(
                settings,
                SettingsCatalogRepository { _, _, cb ->
                    requests += 1
                    callback = cb
                },
                { config ->
                    lastConfiguredModel = config.chatModel
                    lastConfiguredAnswerMode = config.answerMode
                    Source
                },
                { _, cb -> timeout = cb },
            )
    }

    private object Source : StockChatDataSource {
        override fun answer(
            question: String,
            history: List<ChatHistoryItem>,
            images: List<String>,
            model: String,
            attempt: Int,
            callback: (ChatAnswer) -> Unit,
        ) = Unit
    }

    companion object {
        private val model = ModelOption("model", "Demo model", "")
        private val provider =
            ModelProviderConfig(
                "provider",
                ModelProviderKind.CUSTOM,
                "Demo",
                "https://example.com/v1",
                "test-credential",
                listOf(model),
                model.id,
            )
    }
}
