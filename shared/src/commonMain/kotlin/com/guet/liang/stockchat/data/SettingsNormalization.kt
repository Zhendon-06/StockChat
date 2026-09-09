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

internal fun AppearanceSettings.normalized(): AppearanceSettings {
    return copy(
        fontSize = fontSize.normalized(),
        tableStyle = tableStyle.normalized(),
        chatBackground = chatBackground.normalized(),
    )
}

internal fun TableStyleSettings.normalized(): TableStyleSettings {
    return copy(
        preset = when (preset) {
            TableStylePreset.BLUE,
            TableStylePreset.DARK,
            -> TableStylePreset.DEFAULT
            else -> preset
        },
        customColorArgb = customColorArgb
            .coerceIn(OPAQUE_ALPHA_MASK, MAX_ARGB_COLOR)
            .or(OPAQUE_ALPHA_MASK),
    )
}

internal fun FontSizeSettings.normalized(): FontSizeSettings {
    return copy(
        scale = scale.coerceIn(FontSizeSettings.MIN_SCALE, FontSizeSettings.MAX_SCALE),
    )
}

internal fun ChatBackgroundSettings.normalized(): ChatBackgroundSettings {
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

internal fun ModelConfiguration.normalized(): ModelConfiguration {
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

internal fun ModelConfiguration.withBuiltInDefaultProvider(): ModelConfiguration {
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

internal fun ModelProviderConfig.normalized(): ModelProviderConfig {
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

private const val OPAQUE_ALPHA_MASK = 0xFF000000L
private const val MAX_ARGB_COLOR = 0xFFFFFFFFL
