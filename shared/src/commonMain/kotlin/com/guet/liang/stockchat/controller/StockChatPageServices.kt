package com.guet.liang.stockchat.controller

import com.guet.liang.stockchat.model.SpeechRecognitionResult
import com.guet.liang.stockchat.model.SpeechSynthesisResult
import com.guet.liang.stockchat.model.TodayMarketResult

/** Voice input boundary; the UI does not depend on a concrete transport implementation. */
internal interface SpeechRecognitionService {
    val isConfigured: Boolean

    fun transcribe(audioBase64: String, mimeType: String, callback: (SpeechRecognitionResult) -> Unit)
}

/** Voice playback boundary; providers remain replaceable at the composition root. */
internal interface SpeechSynthesisService {
    val isConfigured: Boolean

    fun synthesize(text: String, callback: (SpeechSynthesisResult) -> Unit)
}

/** Market loading boundary exposed to the page. */
internal interface TodayMarketLoader {
    fun load(callback: (TodayMarketResult) -> Unit)
}
