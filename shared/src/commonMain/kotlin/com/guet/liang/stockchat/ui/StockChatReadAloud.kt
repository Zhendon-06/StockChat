package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.base.bridgeModule
import com.guet.liang.stockchat.base.setTimeout
import com.guet.liang.stockchat.model.ChatMessage
import com.guet.liang.stockchat.model.SpeechSynthesisResult
import com.tencent.kuikly.core.timer.Timer

// 消息朗读：语音合成请求、播放指示声纹与 WAV 时长估算。

internal fun StockChatPage.readMessageAloud(message: ChatMessage) {
    // 再次点击同一条消息的声音按钮视为停止播放
    if (readAloudMessageId == message.id) {
        stopSpeechPlayback()
        return
    }
    val content = messageText(message)
    if (content.isBlank()) {
        bridgeModule.toast("没有可朗读的内容")
        return
    }
    if (!speechSynthesisService.isConfigured) {
        bridgeModule.toast("MiMo 语音 Key 尚未配置")
        return
    }
    speechSynthesisRequestToken += 1
    val currentRequestToken = speechSynthesisRequestToken
    bridgeModule.stopAudioPlayback()
    // 生成与播放进度不再弹 toast，用声音按钮上的流动声纹反馈
    beginReadAloudIndicator(message.id)
    speechSynthesisService.synthesize(content) synthesis@{ result ->
        if (currentRequestToken != speechSynthesisRequestToken) {
            return@synthesis
        }
        when (result) {
            // 流式播放开始：声纹从生成时起就已在流动，无需额外反馈
            SpeechSynthesisResult.Started -> Unit
            SpeechSynthesisResult.Completed -> endReadAloudIndicator()
            is SpeechSynthesisResult.Success -> {
                bridgeModule.playBase64Audio(
                    audioBase64 = result.audioBase64,
                    mimeType = result.mimeType,
                ) playback@{ payload ->
                    if (currentRequestToken != speechSynthesisRequestToken) {
                        return@playback
                    }
                    if (payload?.optInt("success", 0) != 1) {
                        endReadAloudIndicator()
                        bridgeModule.toast(
                            payload?.optString("errorMessage")
                                ?.ifBlank { "语音播放失败，请稍后重试" }
                                ?: "语音播放失败，请稍后重试"
                        )
                    } else {
                        scheduleReadAloudFinish(
                            currentRequestToken,
                            message.id,
                            result.audioBase64,
                        )
                    }
                }
            }
            is SpeechSynthesisResult.Failure -> {
                endReadAloudIndicator()
                bridgeModule.toast(result.message)
            }
        }
    }
}

internal fun StockChatPage.stopSpeechPlayback() {
    speechSynthesisRequestToken += 1
    bridgeModule.stopAudioPlayback()
    endReadAloudIndicator()
}

internal fun StockChatPage.beginReadAloudIndicator(messageId: String) {
    readAloudMessageId = messageId
    if (readAloudWaveTimer == null) {
        readAloudWavePhase = 0
        readAloudWaveTimer = Timer().also { timer ->
            timer.schedule(0, 120) {
                readAloudWavePhase = (readAloudWavePhase + 1) % 120
            }
        }
    }
}

internal fun StockChatPage.endReadAloudIndicator() {
    readAloudMessageId = ""
    readAloudWaveTimer?.cancel()
    readAloudWaveTimer = null
}

// 非流式播放没有完成回调：从 WAV 头解析时长，到点后收起声纹；
// 解析失败兜底 60s，避免声纹无限滚动
internal fun StockChatPage.scheduleReadAloudFinish(
    requestToken: Int,
    messageId: String,
    audioBase64: String,
) {
    val durationMs = estimateWavDurationMs(audioBase64) ?: 60_000L
    setTimeout((durationMs + 300).coerceAtMost(120_000L).toInt()) {
        if (requestToken == speechSynthesisRequestToken && readAloudMessageId == messageId) {
            endReadAloudIndicator()
        }
    }
}

internal fun estimateWavDurationMs(audioBase64: String): Long? {
    // WAV 头 44 字节：byteRate 在偏移 28、data 块大小在偏移 40（均小端 int32）
    val header = decodeBase64Prefix(audioBase64, 44) ?: return null
    if (header[0] != 'R'.code.toByte() || header[1] != 'I'.code.toByte() ||
        header[2] != 'F'.code.toByte() || header[3] != 'F'.code.toByte()
    ) {
        return null
    }
    fun littleEndianInt(offset: Int): Long {
        var value = 0L
        for (i in 3 downTo 0) {
            value = (value shl 8) or (header[offset + i].toLong() and 0xFF)
        }
        return value
    }
    val byteRate = littleEndianInt(28)
    val dataSize = littleEndianInt(40)
    if (byteRate <= 0 || dataSize <= 0) {
        return null
    }
    return (dataSize * 1000 / byteRate).coerceAtLeast(800L)
}

internal fun decodeBase64Prefix(text: String, byteCount: Int): ByteArray? {
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    val output = ByteArray(byteCount)
    var outputCount = 0
    var buffer = 0
    var bufferBits = 0
    for (char in text) {
        if (outputCount >= byteCount) {
            break
        }
        if (char == '=') {
            break
        }
        if (char == '\n' || char == '\r' || char == ' ') {
            continue
        }
        val value = alphabet.indexOf(char)
        if (value < 0) {
            return null
        }
        buffer = (buffer shl 6) or value
        bufferBits += 6
        if (bufferBits >= 8) {
            bufferBits -= 8
            output[outputCount] = ((buffer shr bufferBits) and 0xFF).toByte()
            outputCount += 1
        }
    }
    return if (outputCount >= byteCount) output else null
}
