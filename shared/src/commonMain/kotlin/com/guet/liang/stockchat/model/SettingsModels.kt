package com.guet.liang.stockchat.model

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal enum class ThemeMode(
    val displayName: String,
) {
    SYSTEM("系统"),
    LIGHT("浅色"),
    DARK("深色"),
}

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
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

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
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

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
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

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal data class StockTablePreviewRow(
    val name: String,
    val symbol: String,
    val price: String,
    val changePercent: String,
    val turnover: String,
    val isPositive: Boolean,
)

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal enum class BackgroundPreset(
    val displayName: String,
) {
    DEFAULT("默认"),
    MARKET_BLUE("行情蓝"),
    GRAPHITE("石墨"),
    FOREST("森林"),
    SUNSET("暖阳"),
}

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
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

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
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

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal data class AppearanceSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val fontSize: FontSizeSettings = FontSizeSettings(),
    val tableStyle: TableStyleSettings = TableStyleSettings(),
    val chatBackground: ChatBackgroundSettings = ChatBackgroundSettings(),
)

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal data class SharedChatRecord(
    val id: String,
    val sessionId: String,
    val question: String,
    val content: ShareContent,
    val sharedAtEpochMillis: Long,
    val destinationLabel: String = "系统分享",
    val isDemo: Boolean = false,
)

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal enum class ModelCapability(
    val displayName: String,
) {
    CHAT("对话"),
    STREAMING("流式输出"),
    REASONING("深度思考"),
    VISION("视觉理解"),
    VOICE("语音"),
}

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal data class ModelOption(
    val id: String,
    val displayName: String,
    val contextWindowLabel: String,
    val capabilities: Set<ModelCapability> = setOf(ModelCapability.CHAT),
)

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal enum class ModelProviderKind(
    val displayName: String,
) {
    DEFAULT("StockChat"),
    ALIYUN("阿里云百炼"),
    DEEPSEEK("DeepSeek"),
    GLM("智谱 GLM"),
    KIMI("Kimi"),
    MIMO("小米 MiMo"),
    CUSTOM("自定义"),
}

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal data class ModelProviderConfig(
    val id: String,
    val kind: ModelProviderKind,
    val displayName: String,
    val baseUrl: String,
    val apiKey: String = "",
    val models: List<ModelOption>,
    val selectedModelId: String,
    val isEnabled: Boolean = true,
)

/** How a chat turn is answered: parallel branches for speed, or research-first for live numbers in the text. */
internal enum class AnswerMode(
    val displayName: String,
    val description: String,
) {
    FAST("更快回答速度", "正文与行情标的识别并行请求，文字先出、卡片随后附上；正文不引用实时价格"),
    PRECISE("联网精准实时数据回答", "先联网识别标的并拉取实时行情，再交给模型作答；开头多等几秒，正文可引用实时数据"),
}

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal data class ModelConfiguration(
    val activeProviderId: String,
    val providers: List<ModelProviderConfig>,
    val answerMode: AnswerMode = AnswerMode.FAST,
)

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal data class SettingsSnapshot(
    val appearance: AppearanceSettings,
    val sharedChats: List<SharedChatRecord>,
    val modelConfiguration: ModelConfiguration,
    val tablePreviewRows: List<StockTablePreviewRow>,
)

/** Result of loading the models exposed by an OpenAI-compatible endpoint. */
internal sealed class ModelCatalogResult {
    data class Success(val models: List<ModelOption>) : ModelCatalogResult()
    data class Failure(val message: String, val statusCode: Int? = null) : ModelCatalogResult()
}
