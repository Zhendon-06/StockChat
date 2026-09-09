@file:Suppress("MagicNumber")
package com.guet.liang.stockchat.data

import com.guet.liang.stockchat.model.StockPredictionInput
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

// 预测请求体构建。

/** Builds the request shared by all OpenAI-compatible prediction providers. */
internal object StockPredictionRequestBuilder {
    fun build(
        input: StockPredictionInput,
        modelName: String,
        useAliyunExtensions: Boolean = false,
    ): JSONObject {
        val history = JSONArray().apply {
            input.history.forEach { point ->
                put(
                    JSONObject().apply {
                        put("timestamp", point.timestamp)
                        put("close", point.close.toDouble())
                    }
                )
            }
        }
        val context = JSONObject().apply {
            put("name", input.quote.name)
            put("symbol", input.quote.symbol)
            put("market", input.quote.marketLabel)
            put("currentPrice", input.quote.price)
            put("change", input.quote.change)
            put("changePercent", input.quote.changePercent)
            put("sourceUpdatedAt", input.sourceUpdatedAt)
            put("history", history)
            put("period", "day")
            put("forecastHorizon", input.forecastHorizon)
        }
        val messages = JSONArray().apply {
            put(
                JSONObject().apply {
                    put("role", "system")
                    put("content", STOCK_PREDICTION_SYSTEM_PROMPT)
                }
            )
            put(
                JSONObject().apply {
                    put("role", "user")
                    put(
                        "content",
                        "请仅依据下面 JSON 中的行情数据生成条件性预测，不要补造缺失数据：\n" +
                            context.toString(),
                    )
                }
            )
        }
        return JSONObject().apply {
            put("model", modelName)
            put("messages", messages)
            put("temperature", 0)
            if (useAliyunExtensions) {
                put("thinking", JSONObject().apply { put("type", "disabled") })
                put("max_completion_tokens", 1600)
            } else {
                put("max_tokens", 1600)
            }
            put("stream", false)
            if (useAliyunExtensions) {
                put("response_format", JSONObject().apply { put("type", "json_object") })
            }
        }
    }

    private const val STOCK_PREDICTION_SYSTEM_PROMPT =
        "你是一个负责金融时间序列分析的 AI 模型。" +
            "你只能根据用户提供的、按时间从旧到新排列的真实行情点做条件性预测；" +
            "不能声称确定盈利，不能把预测当作投资建议。" +
            "必须只返回一个严格 JSON 对象，不要 Markdown、代码围栏或额外解释。" +
            "JSON 必须包含 forecastPoints 数组（恰好按要求的 horizon 个点），" +
            "每个点包含 timestamp、predictedPrice，并可包含 lowerBound、upperBound；" +
            "还必须包含 horizon、direction、confidence（0 到 1）、rationale、generatedAt、sourceUpdatedAt、historyPointCount。" +
            "还必须包含 conclusions 数组（1 至 3 条）：每条包含 text 和 reference。" +
            "reference 包含 symbol（原样回显）、period（day）、startDate、endDate、metric（close）、sourceUpdatedAt（原样回显）。" +
            "结论须描述可由历史收盘价核验的变化；引用起止日期必须原样取自 history.timestamp，按先后排列且包含两端，不能引用未来预测点。" +
            "没有可核验的历史依据时 conclusions 返回空数组；禁止从结论文本推测或编造日期、成交量等未提供的指标。" +
            "所有 forecastPoints.timestamp 与 generatedAt 必须使用 YYYY-MM-DD 或 ISO 8601 日期时间格式，不能使用‘明天’等相对日期；" +
            "sourceUpdatedAt 必须原样回显输入 JSON 中的 sourceUpdatedAt，historyPointCount 必须等于输入样本数；" +
            "timestamp 必须是预测目标时间且按从旧到新排列；价格必须为正数且有限；" +
            "direction 请使用看多、偏多、中性、偏空或谨慎观望等清晰表述。"
}
