package com.guet.liang.stockchat.base

import com.tencent.kuikly.core.module.CallbackFn
import com.tencent.kuikly.core.module.CallbackRef
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

// Voice and streaming bridge operations share the existing module registration.

internal fun BridgeModule.startVoiceRecording(responseCallbackFn: CallbackFn) {
    callNativeMethod(BridgeModule.START_VOICE_RECORDING, null, responseCallbackFn)
}

internal fun BridgeModule.stopVoiceRecording(responseCallbackFn: CallbackFn) {
    callNativeMethod(BridgeModule.STOP_VOICE_RECORDING, null, responseCallbackFn)
}

internal fun BridgeModule.cancelVoiceRecording() {
    callNativeMethod(BridgeModule.CANCEL_VOICE_RECORDING, null, null)
}

internal fun BridgeModule.playBase64Audio(audioBase64: String, mimeType: String, responseCallbackFn: CallbackFn) {
    val methodArgs =
        JSONObject().apply {
            put("audioBase64", audioBase64)
            put("mimeType", mimeType)
        }
    callNativeMethod(BridgeModule.PLAY_BASE64_AUDIO, methodArgs, responseCallbackFn)
}

internal fun BridgeModule.stopAudioPlayback() {
    callNativeMethod(BridgeModule.STOP_AUDIO_PLAYBACK, null, null)
}

internal fun BridgeModule.streamSpeechSynthesis(apiKey: String, url: String, requestBody: JSONObject, responseCallbackFn: CallbackFn) {
    val methodArgs =
        JSONObject().apply {
            put("apiKey", apiKey)
            put("url", url)
            put("requestBody", requestBody.toString())
        }
    var callbackRef: CallbackRef? = null
    callbackRef =
        toNative(
                keepCallbackAlive = true,
                methodName = BridgeModule.STREAM_SPEECH_SYNTHESIS,
                param = methodArgs.toString(),
                callback = { payload ->
                    responseCallbackFn(payload)
                    if (payload?.optInt("success", 0) == 0 || payload?.optString("event") == "end") {
                        callbackRef?.let(::removeCallback)
                        callbackRef = null
                    }
                },
                syncCall = false,
            )
            .callbackRef
}

internal fun BridgeModule.streamChatCompletion(
    apiKey: String,
    url: String,
    requestBody: JSONObject,
    responseCallbackFn: CallbackFn,
    headers: JSONObject? = null,
    providerDisplayName: String = "",
) {
    val methodArgs =
        JSONObject().apply {
            put("apiKey", apiKey)
            put("url", url)
            put("requestBody", requestBody.toString())
            headers?.let { put("headers", it.toString()) }
            if (providerDisplayName.isNotBlank()) {
                put("providerDisplayName", providerDisplayName)
            }
        }
    var callbackRef: CallbackRef? = null
    callbackRef =
        toNative(
                keepCallbackAlive = true,
                methodName = BridgeModule.STREAM_CHAT_COMPLETION,
                param = methodArgs.toString(),
                callback = { payload ->
                    responseCallbackFn(payload)
                    if (payload?.optInt("success", 0) == 0 || payload?.optString("event") == "end") {
                        callbackRef?.let(::removeCallback)
                        callbackRef = null
                    }
                },
                syncCall = false,
            )
            .callbackRef
}
