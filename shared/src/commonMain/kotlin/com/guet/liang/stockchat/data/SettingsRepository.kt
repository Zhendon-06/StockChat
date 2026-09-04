package com.guet.liang.stockchat.data

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
import com.guet.liang.stockchat.model.StockTablePreviewRow
import com.guet.liang.stockchat.model.TableStylePreset
import com.guet.liang.stockchat.model.TableStyleSettings
import com.guet.liang.stockchat.model.ThemeMode
import com.tencent.kuikly.core.datetime.DateTime
import com.tencent.kuikly.core.module.SharedPreferencesModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal interface SettingsRepository {
    val snapshot: StateFlow<SettingsSnapshot>

    fun loadSnapshot(): SettingsSnapshot

    fun setThemeMode(themeMode: ThemeMode)

    fun setFontSize(settings: FontSizeSettings)

    fun setTableStyle(settings: TableStyleSettings)

    fun setChatBackground(settings: ChatBackgroundSettings)

    fun saveSharedChat(record: SharedChatRecord)

    fun recordSharedChat(
        sessionId: String,
        question: String,
        content: ShareContent,
        destinationLabel: String = "系统分享",
    ): SharedChatRecord

    fun deleteSharedChat(recordId: String): Boolean

    fun saveModelProvider(provider: ModelProviderConfig)

    fun deleteModelProvider(providerId: String): Boolean

    fun selectModel(providerId: String, modelId: String): Boolean
}

internal class InMemorySettingsRepository(
    initialAppearance: AppearanceSettings = MockSettingsData.appearance,
    initialSharedChats: List<SharedChatRecord> = MockSettingsData.sharedChats,
    initialModelConfiguration: ModelConfiguration = MockSettingsData.modelConfiguration,
    private val tablePreviewRows: List<StockTablePreviewRow> = MockSettingsData.tablePreviewRows,
    initialPersistence: SettingsPersistence? = null,
    private val currentTimeMillis: () -> Long = DateTime::currentTimestamp,
) : SettingsRepository {
    private var persistence = initialPersistence
    private var appearance = initialAppearance.normalized()
    private var sharedChats = initialSharedChats
        .distinctBy(SharedChatRecord::id)
        .sortedByDescending(SharedChatRecord::sharedAtEpochMillis)
    private var modelConfiguration = initialModelConfiguration.normalized()
    private var generatedShareSequence = 0L
    private val mutableSnapshot = MutableStateFlow(createSnapshot())

    override val snapshot: StateFlow<SettingsSnapshot> = mutableSnapshot.asStateFlow()

    init {
        restoreFromPersistence()
    }

    override fun loadSnapshot(): SettingsSnapshot {
        return snapshot.value
    }

    internal fun attachPersistence(persistence: SettingsPersistence) {
        this.persistence = persistence
        restoreFromPersistence()
    }

    private fun createSnapshot(): SettingsSnapshot {
        return SettingsSnapshot(
            appearance = appearance,
            sharedChats = sharedChats.toList(),
            modelConfiguration = modelConfiguration.copy(
                providers = modelConfiguration.providers.map { provider ->
                    provider.copy(
                        models = provider.models.toList(),
                    )
                },
            ),
            tablePreviewRows = tablePreviewRows.toList(),
        )
    }

    override fun setThemeMode(themeMode: ThemeMode) {
        appearance = appearance.copy(themeMode = themeMode)
        publishAndPersist()
    }

    override fun setFontSize(settings: FontSizeSettings) {
        appearance = appearance.copy(fontSize = settings.normalized())
        publishAndPersist()
    }

    override fun setTableStyle(settings: TableStyleSettings) {
        appearance = appearance.copy(tableStyle = settings.normalized())
        publishAndPersist()
    }

    override fun setChatBackground(settings: ChatBackgroundSettings) {
        appearance = appearance.copy(chatBackground = settings.normalized())
        publishAndPersist()
    }

    override fun saveSharedChat(record: SharedChatRecord) {
        require(record.id.isNotBlank()) { "Shared chat record id must not be blank." }
        require(record.content.text.isNotBlank()) { "Shared chat content must not be blank." }
        sharedChats = buildList {
            add(record)
            addAll(sharedChats.filterNot { existing -> existing.id == record.id })
        }.sortedByDescending(SharedChatRecord::sharedAtEpochMillis)
        publishAndPersist()
    }

    override fun recordSharedChat(
        sessionId: String,
        question: String,
        content: ShareContent,
        destinationLabel: String,
    ): SharedChatRecord {
        generatedShareSequence += 1
        val sharedAt = currentTimeMillis()
        val record = SharedChatRecord(
            id = "share-$sharedAt-$generatedShareSequence",
            sessionId = sessionId.trim(),
            question = question.trim(),
            content = content,
            sharedAtEpochMillis = sharedAt,
            destinationLabel = destinationLabel.trim().ifBlank { "系统分享" },
        )
        saveSharedChat(record)
        return record
    }

    override fun deleteSharedChat(recordId: String): Boolean {
        val updatedRecords = sharedChats.filterNot { record -> record.id == recordId }
        if (updatedRecords.size == sharedChats.size) {
            return false
        }
        sharedChats = updatedRecords
        publishAndPersist()
        return true
    }

    override fun saveModelProvider(provider: ModelProviderConfig) {
        require(provider.id.isNotBlank()) { "Model provider id must not be blank." }
        val normalizedProvider = provider.normalized()
        val existingIndex = modelConfiguration.providers.indexOfFirst { existing ->
            existing.id == normalizedProvider.id
        }
        val updatedProviders = modelConfiguration.providers.toMutableList().apply {
            if (existingIndex >= 0) {
                this[existingIndex] = normalizedProvider
            } else {
                add(normalizedProvider)
            }
        }
        modelConfiguration = modelConfiguration.copy(providers = updatedProviders).normalized()
        publishAndPersist()
    }

    override fun deleteModelProvider(providerId: String): Boolean {
        val updatedProviders = modelConfiguration.providers.filterNot { provider ->
            provider.id == providerId
        }
        if (updatedProviders.size == modelConfiguration.providers.size || updatedProviders.isEmpty()) {
            return false
        }
        modelConfiguration = modelConfiguration.copy(providers = updatedProviders).normalized()
        publishAndPersist()
        return true
    }

    override fun selectModel(providerId: String, modelId: String): Boolean {
        val providerIndex = modelConfiguration.providers.indexOfFirst { provider ->
            provider.id == providerId
        }
        if (providerIndex < 0) {
            return false
        }
        val provider = modelConfiguration.providers[providerIndex]
        val updatedProviders = modelConfiguration.providers.toMutableList().apply {
            // 模型不在列表内时保留原选中模型，但仍切换当前 Provider，
            // 避免「保存并设为当前 Provider」后 activeProviderId 不生效。
            this[providerIndex] = if (provider.models.any { model -> model.id == modelId }) {
                provider.copy(selectedModelId = modelId)
            } else {
                provider
            }
        }
        modelConfiguration = ModelConfiguration(
            activeProviderId = providerId,
            providers = updatedProviders,
        )
        publishAndPersist()
        return true
    }

    private fun restoreFromPersistence() {
        val storedState = persistence
            ?.read()
            ?.let(SettingsSnapshotJsonCodec::decode)
            ?: run {
                mutableSnapshot.value = createSnapshot()
                return
            }
        appearance = storedState.appearance.normalized()
        sharedChats = storedState.sharedChats
            .filterNot(SharedChatRecord::isDemo)
            .distinctBy(SharedChatRecord::id)
            .sortedByDescending(SharedChatRecord::sharedAtEpochMillis)
        val restoredModelConfiguration = storedState.modelConfiguration
            .withBuiltInDefaultProvider()
            .normalized()
        modelConfiguration = restoredModelConfiguration
        mutableSnapshot.value = createSnapshot()
    }

    private fun publishAndPersist() {
        val updatedSnapshot = createSnapshot()
        mutableSnapshot.value = updatedSnapshot
        persistence?.write(SettingsSnapshotJsonCodec.encode(updatedSnapshot))
    }

    private fun AppearanceSettings.normalized(): AppearanceSettings {
        return copy(
            fontSize = fontSize.normalized(),
            tableStyle = tableStyle.normalized(),
            chatBackground = chatBackground.normalized(),
        )
    }

    private fun TableStyleSettings.normalized(): TableStyleSettings {
        return copy(
            preset = when (preset) {
                TableStylePreset.BLUE,
                TableStylePreset.DARK,
                -> TableStylePreset.DEFAULT
                else -> preset
            },
            customColorArgb = customColorArgb
                .coerceIn(0xFF000000L, 0xFFFFFFFFL)
                .or(0xFF000000L),
        )
    }

    private fun FontSizeSettings.normalized(): FontSizeSettings {
        return copy(
            scale = scale.coerceIn(FontSizeSettings.MIN_SCALE, FontSizeSettings.MAX_SCALE),
        )
    }

    private fun ChatBackgroundSettings.normalized(): ChatBackgroundSettings {
        return copy(
            customImageUri = customImageUri?.trim()?.takeIf(String::isNotEmpty),
            blurRadius = blurRadius.coerceIn(
                ChatBackgroundSettings.MIN_BLUR_RADIUS,
                ChatBackgroundSettings.MAX_BLUR_RADIUS,
            ),
            maskOpacity = maskOpacity.coerceIn(
                ChatBackgroundSettings.MIN_MASK_OPACITY,
                ChatBackgroundSettings.MAX_MASK_OPACITY,
            ),
            maskBrightness = maskBrightness.coerceIn(
                ChatBackgroundSettings.MIN_MASK_BRIGHTNESS,
                ChatBackgroundSettings.MAX_MASK_BRIGHTNESS,
            ),
            chatTextSizeSp = chatTextSizeSp.coerceIn(
                ChatBackgroundSettings.MIN_CHAT_TEXT_SIZE_SP,
                ChatBackgroundSettings.MAX_CHAT_TEXT_SIZE_SP,
            ),
        )
    }

    private fun ModelConfiguration.normalized(): ModelConfiguration {
        val normalizedProviders = providers
            .distinctBy(ModelProviderConfig::id)
            .map { provider -> provider.normalized() }
        require(normalizedProviders.isNotEmpty()) { "At least one model provider is required." }
        val normalizedActiveProviderId = activeProviderId.takeIf { activeId ->
            normalizedProviders.any { provider -> provider.id == activeId }
        } ?: normalizedProviders.first().id
        return copy(
            activeProviderId = normalizedActiveProviderId,
            providers = normalizedProviders,
        )
    }

    private fun ModelConfiguration.withBuiltInDefaultProvider(): ModelConfiguration {
        val builtInDefault = MockSettingsData.modelConfiguration.providers
            .firstOrNull { provider -> provider.kind == ModelProviderKind.DEFAULT }
            ?: return this
        val existingDefault = providers.firstOrNull { provider ->
            provider.kind == ModelProviderKind.DEFAULT
        }
        val restoredDefault = builtInDefault.copy(
            apiKey = existingDefault?.apiKey.orEmpty(),
            selectedModelId = existingDefault?.selectedModelId
                ?.takeIf { modelId -> builtInDefault.models.any { model -> model.id == modelId } }
                ?: builtInDefault.selectedModelId,
        )
        return copy(
            providers = listOf(restoredDefault) + providers.filterNot { provider ->
                provider.kind == ModelProviderKind.DEFAULT
            },
        )
    }

    private fun ModelProviderConfig.normalized(): ModelProviderConfig {
        val normalizedModels = models
            .filter { model -> model.id.isNotBlank() }
            .distinctBy(ModelOption::id)
        // 第三方 Provider 的模型列表允许为空，需通过「获取可用模型」拉取后再写入。
        val normalizedSelectedModelId = selectedModelId.takeIf { selectedId ->
            normalizedModels.any { model -> model.id == selectedId }
        } ?: normalizedModels.firstOrNull()?.id.orEmpty()
        return copy(
            displayName = displayName.trim().ifBlank { kind.displayName },
            baseUrl = baseUrl.trim().trimEnd('/'),
            apiKey = apiKey.trim(),
            models = normalizedModels,
            selectedModelId = normalizedSelectedModelId,
        )
    }
}

internal object StockChatSettingsStore {
    private val mutableRepository = InMemorySettingsRepository()

    val repository: SettingsRepository = mutableRepository

    fun initialize(sharedPreferencesModule: SharedPreferencesModule) {
        mutableRepository.attachPersistence(
            KuiklySharedPreferencesSettingsPersistence(sharedPreferencesModule),
        )
    }
}
