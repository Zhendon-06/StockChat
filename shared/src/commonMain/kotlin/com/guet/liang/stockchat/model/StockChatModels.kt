package com.guet.liang.stockchat.model

internal enum class ChatRole {
    USER,
    ASSISTANT,
}

internal enum class MessageState {
    DELIVERED,
    GENERATING,
    FAILED,
}

internal enum class VoiceInputState {
    IDLE,
    STARTING,
    RECORDING,
    TRANSCRIBING,
}

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

internal data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val blocks: List<AnswerBlock>,
    val state: MessageState = MessageState.DELIVERED,
    val retryQuestion: String = "",
    val retryAttempt: Int = 0,
    val errorMessage: String = "",
)

internal data class ChatHistoryItem(
    val role: ChatRole,
    val content: String,
)

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

internal sealed class ChatAnswer {
    data class Streaming(
        val markdown: String,
    ) : ChatAnswer()

    data class Success(val blocks: List<AnswerBlock>) : ChatAnswer()
    data class Failure(val message: String) : ChatAnswer()
}

internal sealed class SpeechRecognitionResult {
    data class Success(val text: String) : SpeechRecognitionResult()
    data class Failure(val message: String) : SpeechRecognitionResult()
}

internal sealed class SpeechSynthesisResult {
    data object Started : SpeechSynthesisResult()

    data object Completed : SpeechSynthesisResult()

    data class Success(
        val audioBase64: String,
        val mimeType: String,
    ) : SpeechSynthesisResult()

    data class Failure(val message: String) : SpeechSynthesisResult()
}

internal sealed class StockDetailResult {
    data class Success(val quote: StockQuote) : StockDetailResult()
    data object Empty : StockDetailResult()
    data class Failure(val message: String) : StockDetailResult()
}
