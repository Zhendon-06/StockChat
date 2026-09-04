package com.guet.liang.stockchat.model

/** A dated close price supplied to the prediction model. */
internal data class StockPredictionHistoryPoint(
    val timestamp: String,
    val close: Float,
)

/** The market context sent to an AI prediction provider. */
internal data class StockPredictionInput(
    val quote: StockQuote,
    val history: List<StockPredictionHistoryPoint>,
    val forecastHorizon: Int = DEFAULT_STOCK_PREDICTION_HORIZON,
    val sourceUpdatedAt: String = quote.updatedAt,
) {
    val historyPoints: List<StockPredictionHistoryPoint>
        get() = history

    companion object {
        const val DEFAULT_STOCK_PREDICTION_HORIZON = 8
    }
}

/** One model-generated future price and its optional uncertainty interval. */
internal data class StockPredictionPoint(
    val timestamp: String,
    val predictedPrice: Float,
    val lowerBound: Float? = null,
    val upperBound: Float? = null,
)

/** Structured output returned by an AI prediction provider. */
internal data class StockPrediction(
    val forecastPoints: List<StockPredictionPoint>,
    val horizon: Int,
    val direction: String,
    val confidence: Float,
    val rationale: String,
    val modelName: String,
    val generatedAt: String,
    val sourceUpdatedAt: String,
    val historyPointCount: Int,
) {
    val predictions: List<StockPredictionPoint>
        get() = forecastPoints
}

/** OpenAI-compatible credentials and endpoint used for a prediction request. */
internal data class StockPredictionConfig(
    val apiKey: String,
    val baseUrl: String,
    val model: String,
    val providerDisplayName: String = "AI 模型",
    val requestTimeoutSeconds: Int = 60,
    val useAliyunExtensions: Boolean = false,
)

internal sealed class StockPredictionResult {
    data class Success(val prediction: StockPrediction) : StockPredictionResult()

    data class Unavailable(val message: String) : StockPredictionResult()

    data class Failure(
        val message: String,
        val statusCode: Int? = null,
    ) : StockPredictionResult()
}
