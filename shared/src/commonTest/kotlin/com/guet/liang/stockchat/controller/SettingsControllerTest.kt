package com.guet.liang.stockchat.controller

import com.guet.liang.stockchat.model.ChatBackgroundSettings
import com.guet.liang.stockchat.model.ChatSessionSummary
import com.guet.liang.stockchat.model.FontSizeSettings
import com.guet.liang.stockchat.model.ModelCatalogResult
import com.guet.liang.stockchat.model.ShareContent
import com.guet.liang.stockchat.model.TableStylePreset
import com.guet.liang.stockchat.model.TableStyleSettings
import com.guet.liang.stockchat.model.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SettingsControllerTest {
    @Test
    fun appearanceChangesReachRepositoryAndPublishedSnapshot() {
        val repository = FakeSettingsRepository()
        val controller = SettingsController(repository)
        controller.setThemeMode(ThemeMode.DARK)
        controller.setFontSize(FontSizeSettings(followsSystem = false, scale = CUSTOM_FONT_SCALE))
        controller.setTableStyle(TableStyleSettings(preset = TableStylePreset.COMPACT))
        controller.setChatBackground(ChatBackgroundSettings(customImageUri = "local://wallpaper"))

        assertEquals(ThemeMode.DARK, controller.snapshot.value.appearance.themeMode)
        assertEquals(CUSTOM_FONT_SCALE, controller.snapshot().appearance.fontSize.scale)
        assertEquals(TableStylePreset.COMPACT, controller.snapshot().appearance.tableStyle.preset)
        assertEquals("local://wallpaper", controller.snapshot().appearance.chatBackground.customImageUri)
    }

    @Test
    fun providerSaveSelectionAndDeletionAreDelegated() {
        val repository = FakeSettingsRepository()
        val controller = SettingsController(repository)
        controller.saveProvider(settingsTestProvider.copy(id = "secondary"))
        assertTrue(controller.selectModel("secondary", "model-b"))
        assertEquals("secondary" to "model-b", repository.selectedModel)
        assertTrue(controller.deleteProvider("secondary"))
        assertFalse(controller.deleteProvider("missing"))
    }

    @Test
    fun catalogReceivesNormalizedSavedCredentials() {
        val repository = FakeSettingsRepository()
        var request: Pair<String, String>? = null
        val controller =
            SettingsController(
                repository,
                catalog =
                    SettingsCatalogRepository { url, key, callback ->
                        request = url to key
                        callback(ModelCatalogResult.Success(emptyList()))
                    },
            )
        var result: ModelCatalogResult? = null
        controller.fetchCatalog(settingsTestProvider.id) { result = it }

        assertEquals("https://example.com/v1" to "demo-key", request)
        assertIs<ModelCatalogResult.Success>(result)
    }

    @Test
    fun missingProviderAndCatalogReturnUsefulFailures() {
        val controller = SettingsController(FakeSettingsRepository())
        var missingProvider: ModelCatalogResult? = null
        var missingService: ModelCatalogResult? = null
        controller.fetchCatalog("missing") { missingProvider = it }
        controller.fetchCatalog(settingsTestProvider.id) { missingService = it }

        assertEquals("未找到模型服务商。", assertIs<ModelCatalogResult.Failure>(missingProvider).message)
        assertEquals("模型目录服务不可用。", assertIs<ModelCatalogResult.Failure>(missingService).message)
    }

    @Test
    fun catalogExceptionsAreMappedToUserFacingErrors() {
        val controller =
            SettingsController(FakeSettingsRepository(), catalog = SettingsCatalogRepository { _, _, _ -> error("request timed out") })
        var result: ModelCatalogResult? = null
        controller.fetchCatalog(settingsTestProvider.id) { result = it }

        assertEquals("网络请求超时，请稍后重试。", assertIs<ModelCatalogResult.Failure>(result).message)
    }

    @Test
    fun archivedSessionRestoreUsesInjectedHistory() {
        val archived = ChatSessionSummary("session", "市场分析", 1L, isArchived = true)
        var restoredId = ""
        val controller =
            SettingsController(
                FakeSettingsRepository(),
                history =
                    object : SettingsHistoryRepository {
                        override fun archivedSessions() = listOf(archived)

                        override fun restoreSession(sessionId: String): Boolean {
                            restoredId = sessionId
                            return sessionId == archived.id
                        }
                    },
            )
        assertEquals(listOf(archived), controller.archivedSessions())
        assertTrue(controller.restoreSession(archived.id))
        assertEquals(archived.id, restoredId)
        assertTrue(SettingsController(FakeSettingsRepository()).archivedSessions().isEmpty())
        assertFalse(SettingsController(FakeSettingsRepository()).restoreSession("session"))
    }

    @Test
    fun sharedChatsCanBeRecordedAndDeletedThroughController() {
        val controller = SettingsController(FakeSettingsRepository())
        val record = controller.recordSharedChat("session", "行情如何", ShareContent("title", "演示行情"))
        assertEquals(listOf(record), controller.sharedChats())
        assertTrue(controller.deleteSharedChat(record.id))
        assertTrue(controller.sharedChats().isEmpty())
        assertFalse(controller.deleteSharedChat(record.id))
    }
}

private const val CUSTOM_FONT_SCALE = 1.2f
