package com.guet.liang.stockchat.controller

import com.guet.liang.stockchat.data.SettingsRepository
import com.guet.liang.stockchat.model.AnswerMode
import com.guet.liang.stockchat.model.AppearanceSettings
import com.guet.liang.stockchat.model.ChatBackgroundSettings
import com.guet.liang.stockchat.model.FontSizeSettings
import com.guet.liang.stockchat.model.ModelConfiguration
import com.guet.liang.stockchat.model.ModelOption
import com.guet.liang.stockchat.model.ModelProviderConfig
import com.guet.liang.stockchat.model.ModelProviderKind
import com.guet.liang.stockchat.model.SettingsSnapshot
import com.guet.liang.stockchat.model.ShareContent
import com.guet.liang.stockchat.model.SharedChatRecord
import com.guet.liang.stockchat.model.TableStyleSettings
import com.guet.liang.stockchat.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow

internal val settingsTestProvider =
    ModelProviderConfig(
        id = "provider",
        kind = ModelProviderKind.CUSTOM,
        displayName = "Provider",
        baseUrl = " https://example.com/v1/ ",
        apiKey = " demo-key ",
        models = listOf(ModelOption("model-a", "Model A", "128K", emptySet())),
        selectedModelId = "model-a",
    )

internal class FakeSettingsRepository : SettingsRepository {
    override val snapshot =
        MutableStateFlow(
            SettingsSnapshot(
                appearance = AppearanceSettings(),
                sharedChats = emptyList(),
                modelConfiguration =
                    ModelConfiguration(settingsTestProvider.id, listOf(settingsTestProvider)),
                tablePreviewRows = emptyList(),
            )
        )
    var selectedModel: Pair<String, String>? = null

    override fun loadSnapshot() = snapshot.value

    override fun setThemeMode(themeMode: ThemeMode) {
        snapshot.value =
            snapshot.value.copy(appearance = snapshot.value.appearance.copy(themeMode = themeMode))
    }

    override fun setFontSize(settings: FontSizeSettings) {
        snapshot.value =
            snapshot.value.copy(appearance = snapshot.value.appearance.copy(fontSize = settings))
    }

    override fun setTableStyle(settings: TableStyleSettings) {
        snapshot.value =
            snapshot.value.copy(appearance = snapshot.value.appearance.copy(tableStyle = settings))
    }

    override fun setChatBackground(settings: ChatBackgroundSettings) {
        snapshot.value =
            snapshot.value.copy(
                appearance = snapshot.value.appearance.copy(chatBackground = settings)
            )
    }

    override fun saveSharedChat(record: SharedChatRecord) {
        snapshot.value = snapshot.value.copy(sharedChats = snapshot.value.sharedChats + record)
    }

    override fun recordSharedChat(
        sessionId: String,
        question: String,
        content: ShareContent,
        destinationLabel: String,
    ): SharedChatRecord {
        return SharedChatRecord("share", sessionId, question, content, 1L, destinationLabel)
            .also(::saveSharedChat)
    }

    override fun deleteSharedChat(recordId: String): Boolean {
        val records = snapshot.value.sharedChats.filterNot { it.id == recordId }
        val changed = records.size != snapshot.value.sharedChats.size
        snapshot.value = snapshot.value.copy(sharedChats = records)
        return changed
    }

    override fun saveModelProvider(provider: ModelProviderConfig) {
        val configuration = snapshot.value.modelConfiguration
        snapshot.value =
            snapshot.value.copy(
                modelConfiguration =
                    configuration.copy(
                        providers =
                            configuration.providers.filterNot { it.id == provider.id } + provider
                    )
            )
    }

    override fun deleteModelProvider(providerId: String): Boolean {
        val configuration = snapshot.value.modelConfiguration
        val providers = configuration.providers.filterNot { it.id == providerId }
        snapshot.value =
            snapshot.value.copy(modelConfiguration = configuration.copy(providers = providers))
        return providers.size != configuration.providers.size
    }

    override fun selectModel(providerId: String, modelId: String): Boolean {
        selectedModel = providerId to modelId
        return true
    }

    override fun setAnswerMode(mode: AnswerMode) {
        snapshot.value =
            snapshot.value.copy(modelConfiguration = snapshot.value.modelConfiguration.copy(answerMode = mode))
    }
}
