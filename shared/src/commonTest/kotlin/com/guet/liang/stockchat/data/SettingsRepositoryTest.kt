package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.ChatBackgroundSettings
import com.guet.liang.stockchat.model.FontSizeSettings
import com.guet.liang.stockchat.model.ModelCapability
import com.guet.liang.stockchat.model.ModelOption
import com.guet.liang.stockchat.model.ModelProviderConfig
import com.guet.liang.stockchat.model.ModelProviderKind
import com.guet.liang.stockchat.model.ShareContent
import com.guet.liang.stockchat.model.SharedChatRecord
import com.guet.liang.stockchat.model.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsRepositoryTest {
    @Test
    fun defaultRepositoryStartsWithNoSharedChats() {
        assertTrue(InMemorySettingsRepository().loadSnapshot().sharedChats.isEmpty())
    }

    @Test
    fun builtInProvidersHaveNoHardcodedApiKeys() {
        val providers = InMemorySettingsRepository()
            .loadSnapshot()
            .modelConfiguration
            .providers

        assertEquals(
            setOf(
                ModelProviderKind.ALIYUN,
                ModelProviderKind.DEEPSEEK,
                ModelProviderKind.GLM,
                ModelProviderKind.KIMI,
                ModelProviderKind.MIMO,
                ModelProviderKind.DEFAULT,
            ),
            providers.map { provider -> provider.kind }.toSet(),
        )
        assertTrue(providers.all { provider -> provider.apiKey.isBlank() })
    }

    @Test
    fun appearanceValuesAreClampedBeforeTheyAreStored() {
        val repository = InMemorySettingsRepository()

        repository.setFontSize(FontSizeSettings(followsSystem = false, scale = 5f))
        repository.setChatBackground(
            ChatBackgroundSettings(
                blurRadius = -5f,
                maskOpacity = 2f,
                maskBrightness = 5f,
                chatTextSizeSp = 40f,
            )
        )

        val appearance = repository.loadSnapshot().appearance
        assertEquals(FontSizeSettings.MAX_SCALE, appearance.fontSize.scale)
        assertEquals(
            ChatBackgroundSettings.MIN_BLUR_RADIUS,
            appearance.chatBackground.blurRadius,
        )
        assertEquals(
            ChatBackgroundSettings.MAX_MASK_OPACITY,
            appearance.chatBackground.maskOpacity,
        )
        assertEquals(
            ChatBackgroundSettings.MAX_MASK_BRIGHTNESS,
            appearance.chatBackground.maskBrightness,
        )
        assertEquals(
            ChatBackgroundSettings.MAX_CHAT_TEXT_SIZE_SP,
            appearance.chatBackground.chatTextSizeSp,
        )
    }

    @Test
    fun selectingModelAlsoActivatesItsProvider() {
        val repository = InMemorySettingsRepository()
        // 第三方 Provider 初始不预置模型，先写入一组真实拉取的模型再验证选择逻辑
        val deepseek = repository.loadSnapshot().modelConfiguration.providers
            .first { provider -> provider.kind == ModelProviderKind.DEEPSEEK }
        repository.saveModelProvider(
            deepseek.copy(
                models = listOf(
                    ModelOption(id = "deepseek-chat", displayName = "DeepSeek Chat", contextWindowLabel = "64K"),
                    ModelOption(
                        id = "deepseek-reasoner",
                        displayName = "DeepSeek Reasoner",
                        contextWindowLabel = "64K",
                    ),
                ),
            ),
        )

        assertTrue(repository.selectModel("deepseek", "deepseek-reasoner"))

        val configuration = repository.loadSnapshot().modelConfiguration
        assertEquals("deepseek", configuration.activeProviderId)
        assertEquals(
            "deepseek-reasoner",
            configuration.providers.first { provider -> provider.id == "deepseek" }.selectedModelId,
        )
        // 模型不存在时仍会激活 Provider，并保留原有的选中模型
        assertTrue(repository.selectModel("kimi", "missing-model"))
        val reactivated = repository.loadSnapshot().modelConfiguration
        assertEquals("kimi", reactivated.activeProviderId)
        assertFalse(repository.selectModel("missing-provider", "deepseek-reasoner"))
    }

    @Test
    fun builtInProvidersStartWithEmptyModelCatalogs() {
        val providers = InMemorySettingsRepository()
            .loadSnapshot()
            .modelConfiguration
            .providers

        assertTrue(providers.filter { provider -> provider.kind != ModelProviderKind.DEFAULT }
            .all { provider -> provider.models.isEmpty() })
        val defaultProvider = providers.first { provider -> provider.kind == ModelProviderKind.DEFAULT }
        assertEquals(3, defaultProvider.models.size)
        assertEquals(
            listOf("qwen3-vl-flash", "qwen3-vl-plus", "qwen-vl-plus"),
            defaultProvider.models.map(ModelOption::id),
        )
        assertEquals("qwen3-vl-flash", defaultProvider.selectedModelId)
        assertTrue(
            defaultProvider.models.all { model ->
                ModelCapability.VISION in model.capabilities &&
                    ModelCapability.STREAMING in model.capabilities
            },
        )
        assertEquals(
            "https://dashscope.aliyuncs.com/compatible-mode/v1",
            defaultProvider.baseUrl,
        )
    }

    @Test
    fun persistedEmptyCustomModelCatalogIsRestored() {
        val persistence = FakeSettingsPersistence()
        val repository = InMemorySettingsRepository(initialPersistence = persistence)

        repository.saveModelProvider(
            ModelProviderConfig(
                id = "custom-provider",
                kind = ModelProviderKind.CUSTOM,
                displayName = "自定义服务",
                baseUrl = "https://example.com/v1",
                models = emptyList(),
                selectedModelId = "",
            ),
        )

        val restoredProvider = InMemorySettingsRepository(initialPersistence = persistence)
            .loadSnapshot()
            .modelConfiguration
            .providers
            .first { provider -> provider.id == "custom-provider" }

        assertTrue(restoredProvider.models.isEmpty())
        assertEquals("", restoredProvider.selectedModelId)
    }

    @Test
    fun persistedThirdPartyModelsAndApiKeyAreRestored() {
        val persistence = FakeSettingsPersistence()
        val firstRepository = InMemorySettingsRepository(initialPersistence = persistence)
        val deepseek = firstRepository.loadSnapshot().modelConfiguration.providers
            .first { provider -> provider.kind == ModelProviderKind.DEEPSEEK }
        firstRepository.saveModelProvider(
            deepseek.copy(
                apiKey = "runtime-only-key",
                models = listOf(
                    ModelOption(
                        id = "deepseek-chat",
                        displayName = "DeepSeek Chat",
                        contextWindowLabel = "64K",
                    ),
                ),
                selectedModelId = "deepseek-chat",
            ),
        )

        val restoredProvider = InMemorySettingsRepository(initialPersistence = persistence)
            .loadSnapshot()
            .modelConfiguration
            .providers
            .first { provider -> provider.kind == ModelProviderKind.DEEPSEEK }

        assertEquals("runtime-only-key", restoredProvider.apiKey)
        assertEquals(listOf("deepseek-chat"), restoredProvider.models.map(ModelOption::id))
        assertEquals("deepseek-chat", restoredProvider.selectedModelId)
    }

    @Test
    fun savingShareKeepsNewestRecordFirst() {
        val repository = InMemorySettingsRepository()
        val newRecord = SharedChatRecord(
            id = "new-share",
            sessionId = "session-new",
            question = "比较两只股票",
            content = ShareContent(
                title = "StockChat｜股票对比",
                text = "演示对比内容。仅供参考，不构成投资建议。",
            ),
            sharedAtEpochMillis = Long.MAX_VALUE,
        )

        repository.saveSharedChat(newRecord)

        assertEquals("new-share", repository.loadSnapshot().sharedChats.first().id)
        assertTrue(repository.deleteSharedChat("new-share"))
        assertFalse(repository.deleteSharedChat("new-share"))
    }

    @Test
    fun recordingSuccessfulShareBuildsStableHistoryEntry() {
        val repository = InMemorySettingsRepository(currentTimeMillis = { Long.MAX_VALUE })

        val record = repository.recordSharedChat(
            sessionId = "session-1",
            question = "沪深300怎么样？",
            content = ShareContent(
                title = "StockChat｜沪深300",
                text = "演示行情。仅供参考，不构成投资建议。",
            ),
        )

        assertEquals("share-${Long.MAX_VALUE}-1", record.id)
        assertEquals(record, repository.loadSnapshot().sharedChats.first())
        assertFalse(record.isDemo)
    }

    @Test
    fun persistedSnapshotRestoresSettingsAndShares() {
        val persistence = FakeSettingsPersistence()
        val sharedRecord = SharedChatRecord(
            id = "real-share",
            sessionId = "session-1",
            question = "沪深300怎么样？",
            content = ShareContent(
                title = "StockChat｜沪深300",
                text = "真实分享内容。仅供参考，不构成投资建议。",
            ),
            sharedAtEpochMillis = 1L,
        )
        val firstRepository = InMemorySettingsRepository(
            initialSharedChats = listOf(sharedRecord),
            initialPersistence = persistence,
        )
        firstRepository.setThemeMode(ThemeMode.DARK)

        val restoredRepository = InMemorySettingsRepository(initialPersistence = persistence)
        val restoredSnapshot = restoredRepository.loadSnapshot()

        assertEquals(ThemeMode.DARK, restoredSnapshot.appearance.themeMode)
        assertEquals(listOf(sharedRecord), restoredSnapshot.sharedChats)
    }

    @Test
    fun restoringLegacyDemoSharesDoesNotRepopulateShareHistory() {
        val persistence = FakeSettingsPersistence()
        val legacyDemoRecord = SharedChatRecord(
            id = "legacy-demo-share",
            sessionId = "legacy-demo-session",
            question = "演示问题",
            content = ShareContent(
                title = "演示分享",
                text = "演示内容",
            ),
            sharedAtEpochMillis = 1L,
            isDemo = true,
        )
        val firstRepository = InMemorySettingsRepository(
            initialSharedChats = listOf(legacyDemoRecord),
            initialPersistence = persistence,
        )

        firstRepository.setThemeMode(ThemeMode.DARK)

        val restoredSnapshot = InMemorySettingsRepository(initialPersistence = persistence)
            .loadSnapshot()

        assertTrue(restoredSnapshot.sharedChats.isEmpty())
    }

    @Test
    fun apiKeysAndModelCatalogAreRestoredFromPersistence() {
        val persistence = FakeSettingsPersistence()
        val repository = InMemorySettingsRepository(initialPersistence = persistence)
        val provider = repository.loadSnapshot()
            .modelConfiguration
            .providers
            .first { it.kind == ModelProviderKind.DEEPSEEK }
            .copy(
                apiKey = "secret-demo-key",
                models = listOf(
                    ModelOption(
                        id = "demo-model",
                        displayName = "Demo Model",
                        contextWindowLabel = "32K",
                    ),
                ),
                selectedModelId = "demo-model",
            )

        repository.saveModelProvider(provider)

        val restoredProvider = InMemorySettingsRepository(initialPersistence = persistence)
            .loadSnapshot()
            .modelConfiguration
            .providers
            .first { it.kind == ModelProviderKind.DEEPSEEK }

        assertEquals("secret-demo-key", restoredProvider.apiKey)
        assertEquals(listOf("demo-model"), restoredProvider.models.map(ModelOption::id))
        assertEquals("demo-model", restoredProvider.selectedModelId)
    }

    private class FakeSettingsPersistence : SettingsPersistence {
        var value: String? = null

        override fun read(): String? = value

        override fun write(serializedSnapshot: String) {
            value = serializedSnapshot
        }
    }
}
