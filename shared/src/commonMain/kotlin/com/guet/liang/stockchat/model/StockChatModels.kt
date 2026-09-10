package com.guet.liang.stockchat.model

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal enum class ChatRole {
    USER,
    ASSISTANT,
}

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal enum class MessageState {
    DELIVERED,
    GENERATING,
    FAILED,
}

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal enum class VoiceInputState {
    IDLE,
    STARTING,
    RECORDING,
    TRANSCRIBING,
}

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal sealed class AnswerBlock {
    data class Markdown(
        val source: String,
        val fallbackText: String,
    ) : AnswerBlock()

    data class MarketQuote(
        val quote: StockQuote,
    ) : AnswerBlock()

    data class ImageGallery(
        val images: List<String>,
        val requestImages: List<String> = emptyList(),
    ) : AnswerBlock()
}

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val blocks: List<AnswerBlock>,
    val state: MessageState = MessageState.DELIVERED,
    val retryQuestion: String = "",
    val retryAttempt: Int = 0,
    val errorMessage: String = "",
)

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal data class ChatHistoryItem(
    val role: ChatRole,
    val content: String,
)

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal data class StockQuote(
    val name: String,
    val symbol: String,
    val marketLabel: String,
    val price: String,
    val change: String,
    val changePercent: String,
    val updatedAt: String,
    val isPositive: Boolean,
    val trendPoints: List<Float>,
    val summary: String,
    val aiInsight: String,
)

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal sealed class ChatAnswer {
    data class Streaming(
        val markdown: String,
        /** Blocks already final while text is still streaming, e.g. quote cards that arrived first. */
        val blocks: List<AnswerBlock> = emptyList(),
    ) : ChatAnswer()

    data class Success(
        val blocks: List<AnswerBlock>,
        /** True when the answer was served by the local provider-aware response cache. */
        val fromCache: Boolean = false,
    ) : ChatAnswer()
    data class Failure(val message: String) : ChatAnswer()
}

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal sealed class SpeechRecognitionResult {
    data class Success(val text: String) : SpeechRecognitionResult()
    data class Failure(val message: String) : SpeechRecognitionResult()
}

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal sealed class SpeechSynthesisResult {
    data object Started : SpeechSynthesisResult()

    data object Completed : SpeechSynthesisResult()

    data class Success(
        val audioBase64: String,
        val mimeType: String,
    ) : SpeechSynthesisResult()

    data class Failure(val message: String) : SpeechSynthesisResult()
}

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal sealed class StockDetailResult {
    data class Success(val quote: StockQuote) : StockDetailResult()
    data object Empty : StockDetailResult()
    data class Failure(val message: String) : StockDetailResult()
}

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal data class ChatSessionSummary(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val isArchived: Boolean = false,
)
