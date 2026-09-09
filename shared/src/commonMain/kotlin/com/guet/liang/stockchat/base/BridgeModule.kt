package com.guet.liang.stockchat.base

import com.tencent.kuikly.core.module.CallbackFn
import com.tencent.kuikly.core.module.CallbackRef
import com.tencent.kuikly.core.module.Module
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

internal class BridgeModule : Module() {
    private var drawerGestureCallbackRef: CallbackRef? = null
    private var backRequestCallbackRef: CallbackRef? = null

    override fun moduleName(): String {
        return MODULE_NAME
    }

    fun closePage() {
        callNativeMethod(CLOSE_PAGE, null, null)
    }

    fun log(content: String) {
        val methodArgs = JSONObject()
        methodArgs.put("content", content)
        callNativeMethod(LOG, methodArgs, null)
    }

    fun copyToPasteboard(content: String) {
        val methodArgs = JSONObject()
        methodArgs.put("content", content)
        callNativeMethod("copyToPasteboard", methodArgs, null)
    }

    fun toast(content: String) {
        val methodArgs = JSONObject()
        methodArgs.put("content", content)
        callNativeMethod("toast", methodArgs, null)
    }

    fun startVoiceRecording(responseCallbackFn: CallbackFn) {
        callNativeMethod(START_VOICE_RECORDING, null, responseCallbackFn)
    }

    fun stopVoiceRecording(responseCallbackFn: CallbackFn) {
        callNativeMethod(STOP_VOICE_RECORDING, null, responseCallbackFn)
    }

    fun cancelVoiceRecording() {
        callNativeMethod(CANCEL_VOICE_RECORDING, null, null)
    }

    fun playBase64Audio(
        audioBase64: String,
        mimeType: String,
        responseCallbackFn: CallbackFn,
    ) {
        val methodArgs = JSONObject().apply {
            put("audioBase64", audioBase64)
            put("mimeType", mimeType)
        }
        callNativeMethod(PLAY_BASE64_AUDIO, methodArgs, responseCallbackFn)
    }

    fun stopAudioPlayback() {
        callNativeMethod(STOP_AUDIO_PLAYBACK, null, null)
    }

    fun streamSpeechSynthesis(
        apiKey: String,
        url: String,
        requestBody: JSONObject,
        responseCallbackFn: CallbackFn,
    ) {
        val methodArgs = JSONObject().apply {
            put("apiKey", apiKey)
            put("url", url)
            put("requestBody", requestBody.toString())
        }
        var callbackRef: CallbackRef? = null
        callbackRef = toNative(
            keepCallbackAlive = true,
            methodName = STREAM_SPEECH_SYNTHESIS,
            param = methodArgs.toString(),
            callback = { payload ->
                responseCallbackFn(payload)
                if (payload?.optInt("success", 0) == 0 || payload?.optString("event") == "end") {
                    callbackRef?.let(::removeCallback)
                    callbackRef = null
                }
            },
            syncCall = false,
        ).callbackRef
    }

    fun pickImages(maxCount: Int, responseCallbackFn: CallbackFn) {
        val methodArgs = JSONObject().apply {
            put("maxCount", maxCount.coerceIn(1, MAX_IMAGE_SELECTION_COUNT))
        }
        callNativeMethod(PICK_IMAGES, methodArgs, responseCallbackFn)
    }

    fun streamChatCompletion(
        apiKey: String,
        url: String,
        requestBody: JSONObject,
        responseCallbackFn: CallbackFn,
        headers: JSONObject? = null,
        providerDisplayName: String = "",
    ) {
        val methodArgs = JSONObject().apply {
            put("apiKey", apiKey)
            put("url", url)
            put("requestBody", requestBody.toString())
            headers?.let { put("headers", it.toString()) }
            if (providerDisplayName.isNotBlank()) {
                put("providerDisplayName", providerDisplayName)
            }
        }
        var callbackRef: CallbackRef? = null
        callbackRef = toNative(
            keepCallbackAlive = true,
            methodName = STREAM_CHAT_COMPLETION,
            param = methodArgs.toString(),
            callback = { payload ->
                responseCallbackFn(payload)
                if (payload?.optInt("success", 0) == 0 || payload?.optString("event") == "end") {
                    callbackRef?.let(::removeCallback)
                    callbackRef = null
                }
            },
            syncCall = false,
        ).callbackRef
    }

    fun observeDrawerGestures(responseCallbackFn: CallbackFn) {
        stopObservingDrawerGestures()
        drawerGestureCallbackRef = toNative(
            keepCallbackAlive = true,
            methodName = OBSERVE_DRAWER_GESTURES,
            param = null,
            callback = responseCallbackFn,
            syncCall = false,
        ).callbackRef
    }

    fun stopObservingDrawerGestures() {
        callNativeMethod(STOP_OBSERVING_DRAWER_GESTURES, null, null)
        drawerGestureCallbackRef?.let(::removeCallback)
        drawerGestureCallbackRef = null
    }

    fun observeBackRequests(responseCallbackFn: CallbackFn) {
        stopObservingBackRequests()
        backRequestCallbackRef = toNative(
            keepCallbackAlive = true,
            methodName = OBSERVE_BACK_REQUESTS,
            param = null,
            callback = responseCallbackFn,
            syncCall = false,
        ).callbackRef
    }

    fun stopObservingBackRequests() {
        callNativeMethod(STOP_OBSERVING_BACK_REQUESTS, null, null)
        backRequestCallbackRef?.let(::removeCallback)
        backRequestCallbackRef = null
    }

    // 同步获取日期格式化
    fun dateFormatter(timeStamp: Long, format: String): String {
        val params = JSONObject()
        params.put("timeStamp", timeStamp)
        params.put("format", format)
        return syncCallNativeMethod(DATE_FORMATTER, params, null)
    }

    private fun callNativeMethod(methodName: String, data: JSONObject?, callbackFn: CallbackFn?) {
        toNative(
            false,
            methodName,
            data?.toString(),
            callbackFn,
            false
        )
    }

    // --------- 同步调用Native方法 -------
    private fun syncCallNativeMethod(
        methodName: String,
        data: JSONObject?,
        callbackFn: CallbackFn?
    ): String {
        return toNative(
            false,
            methodName,
            data?.toString(),
            callbackFn,
            true
        ).toString()
    }

    companion object {
        const val MODULE_NAME = "HRBridgeModule"
        const val CLOSE_PAGE = "closePage"
        const val LOG = "log"
        const val DATE_FORMATTER = "dateFormatter"
        const val START_VOICE_RECORDING = "startVoiceRecording"
        const val STOP_VOICE_RECORDING = "stopVoiceRecording"
        const val CANCEL_VOICE_RECORDING = "cancelVoiceRecording"
        const val PLAY_BASE64_AUDIO = "playBase64Audio"
        const val STOP_AUDIO_PLAYBACK = "stopAudioPlayback"
        const val STREAM_SPEECH_SYNTHESIS = "streamSpeechSynthesis"
        const val PICK_IMAGES = "pickImages"
        const val STREAM_CHAT_COMPLETION = "streamChatCompletion"
        const val OBSERVE_DRAWER_GESTURES = "observeDrawerGestures"
        const val STOP_OBSERVING_DRAWER_GESTURES = "stopObservingDrawerGestures"
        const val OBSERVE_BACK_REQUESTS = "observeBackRequests"
        const val STOP_OBSERVING_BACK_REQUESTS = "stopObservingBackRequests"
        /** 单次图片选择上限；输入面板与原生选图桥接共用。 */
        const val MAX_IMAGE_SELECTION_COUNT = 9
    }

}
