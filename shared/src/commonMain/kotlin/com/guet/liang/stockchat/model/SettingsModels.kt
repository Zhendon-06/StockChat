package com.guet.liang.stockchat.model

internal enum class ThemeMode(
    val displayName: String,
) {
    SYSTEM("系统"),
    LIGHT("浅色"),
    DARK("深色"),
}

internal data class FontSizeSettings(
    val followsSystem: Boolean = true,
    val scale: Float = DEFAULT_SCALE,
) {
    companion object {
        const val MIN_SCALE = 0.85f
        const val DEFAULT_SCALE = 1f
        const val MAX_SCALE = 1.3f
    }
}

internal enum class TableStylePreset(
    val displayName: String,
    val description: String,
) {
    DEFAULT("经典", "完整网格与斑马纹"),
    COMPACT("紧凑", "同屏展示更多行情"),
    SPACIOUS("宽松", "更大的行距与留白"),
    MINIMAL("极简", "仅保留横向分隔线"),
    BLUE("蓝色", "蓝色强调表头"),
    DARK("深色", "深色行情表格"),
}

internal data class TableStyleSettings(
    val preset: TableStylePreset = TableStylePreset.DEFAULT,
    val showGridLines: Boolean = true,
    val highlightHeader: Boolean = true,
    val customColorArgb: Long = DEFAULT_CUSTOM_COLOR_ARGB,
) {
    companion object {
        const val DEFAULT_CUSTOM_COLOR_ARGB: Long = 0xFF0EAA7B
    }
}

internal data class StockTablePreviewRow(
    val name: String,
    val symbol: String,
    val price: String,
    val changePercent: String,
    val turnover: String,
    val isPositive: Boolean,
)

internal enum class BackgroundPreset(
    val displayName: String,
) {
    DEFAULT("默认"),
    MARKET_BLUE("行情蓝"),
    GRAPHITE("石墨"),
    FOREST("森林"),
    SUNSET("暖阳"),
}

internal enum class ChatTextColorMode(
    val displayName: String,
) {
    AUTOMATIC("自动"),
    LIGHT("浅色"),
    DARK("深色"),
    BLUE("蓝色"),
    GREEN("绿色"),
    ORANGE("橙色"),
}

internal data class ChatBackgroundSettings(
    val preset: BackgroundPreset = BackgroundPreset.DEFAULT,
    val customImageUri: String? = null,
    val blurRadius: Float = DEFAULT_BLUR_RADIUS,
    val maskOpacity: Float = DEFAULT_MASK_OPACITY,
    val maskBrightness: Float = DEFAULT_MASK_BRIGHTNESS,
    val chatTextSizeSp: Float = DEFAULT_CHAT_TEXT_SIZE_SP,
    val chatTextColorMode: ChatTextColorMode = ChatTextColorMode.AUTOMATIC,
) {
    companion object {
        const val MIN_BLUR_RADIUS = 0f
        const val DEFAULT_BLUR_RADIUS = 8f
        const val MAX_BLUR_RADIUS = 24f
        const val MIN_MASK_OPACITY = 0f
        const val DEFAULT_MASK_OPACITY = 0.18f
        const val MAX_MASK_OPACITY = 0.75f
        const val MIN_MASK_BRIGHTNESS = 0.6f
        const val DEFAULT_MASK_BRIGHTNESS = 1f
        const val MAX_MASK_BRIGHTNESS = 1.4f
        const val MIN_CHAT_TEXT_SIZE_SP = 12f
        const val DEFAULT_CHAT_TEXT_SIZE_SP = 14f
        const val MAX_CHAT_TEXT_SIZE_SP = 22f
    }
}

internal data class AppearanceSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val fontSize: FontSizeSettings = FontSizeSettings(),
    val tableStyle: TableStyleSettings = TableStyleSettings(),
    val chatBackground: ChatBackgroundSettings = ChatBackgroundSettings(),
)

internal data class SharedChatRecord(
    val id: String,
    val sessionId: String,
    val question: String,
    val content: ShareContent,
    val sharedAtEpochMillis: Long,
    val destinationLabel: String = "系统分享",
    val isDemo: Boolean = false,
)

internal enum class ModelCapability(
    val displayName: String,
) {
    CHAT("对话"),
    STREAMING("流式输出"),
    REASONING("深度思考"),
    VISION("视觉理解"),
    VOICE("语音"),
}

internal data class ModelOption(
    val id: String,
    val displayName: String,
    val contextWindowLabel: String,
    val capabilities: Set<ModelCapability> = setOf(ModelCapability.CHAT),
)

internal enum class ModelProviderKind(
    val displayName: String,
) {
    DEFAULT("StockChat Free"),
    ALIYUN("阿里云百炼"),
    DEEPSEEK("DeepSeek"),
    GLM("智谱 GLM"),
    KIMI("Kimi"),
    MIMO("小米 MiMo"),
    CUSTOM("自定义"),
}

internal data class ModelProviderConfig(
    val id: String,
    val kind: ModelProviderKind,
    val displayName: String,
    val baseUrl: String,
    val apiKey: String = "",
    val models: List<ModelOption>,
    val selectedModelId: String,
    val isEnabled: Boolean = true,
) {
    val hasApiKey: Boolean
        get() = apiKey.isNotBlank()
}

internal data class ModelConfiguration(
    val activeProviderId: String,
    val providers: List<ModelProviderConfig>,
)

internal data class SettingsSnapshot(
    val appearance: AppearanceSettings,
    val sharedChats: List<SharedChatRecord>,
    val modelConfiguration: ModelConfiguration,
    val tablePreviewRows: List<StockTablePreviewRow>,
)
