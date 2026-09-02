package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.ChatBackgroundSettings
import com.guet.liang.stockchat.model.FontSizeSettings
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

        assertTrue(repository.selectModel("deepseek", "deepseek-reasoner"))

        val configuration = repository.loadSnapshot().modelConfiguration
        assertEquals("deepseek", configuration.activeProviderId)
        assertEquals(
            "deepseek-reasoner",
            configuration.providers.first { provider -> provider.id == "deepseek" }.selectedModelId,
        )
        assertFalse(repository.selectModel("deepseek", "missing-model"))
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
        val firstRepository = InMemorySettingsRepository(initialPersistence = persistence)
        firstRepository.setThemeMode(ThemeMode.DARK)
        firstRepository.deleteSharedChat("demo-share-moutai")

        val restoredRepository = InMemorySettingsRepository(initialPersistence = persistence)
        val restoredSnapshot = restoredRepository.loadSnapshot()

        assertEquals(ThemeMode.DARK, restoredSnapshot.appearance.themeMode)
        assertTrue(restoredSnapshot.sharedChats.none { record ->
            record.id == "demo-share-moutai"
        })
    }

    @Test
    fun apiKeysStayInMemoryAndAreNotWrittenToSharedPreferences() {
        val persistence = FakeSettingsPersistence()
        val repository = InMemorySettingsRepository(initialPersistence = persistence)
        val provider = repository.loadSnapshot()
            .modelConfiguration
            .providers
            .first()
            .copy(apiKey = "secret-demo-key")

        repository.saveModelProvider(provider)

        assertEquals("secret-demo-key", repository.loadSnapshot().modelConfiguration.providers.first().apiKey)
        assertFalse(persistence.value.orEmpty().contains("secret-demo-key"))
    }

    private class FakeSettingsPersistence : SettingsPersistence {
        var value: String? = null

        override fun read(): String? = value

        override fun write(serializedSnapshot: String) {
            value = serializedSnapshot
        }
    }
}
