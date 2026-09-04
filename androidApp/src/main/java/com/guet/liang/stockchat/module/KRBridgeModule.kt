package com.guet.liang.stockchat.module

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.widget.Toast
import com.guet.liang.stockchat.KRApplication
import com.guet.liang.stockchat.KuiklyRenderActivity
import com.tencent.kuikly.core.render.android.export.KuiklyRenderBaseModule
import com.tencent.kuikly.core.render.android.export.KuiklyRenderCallback
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date

class KRBridgeModule : KuiklyRenderBaseModule() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val recordingLock = Any()

    @Volatile
    private var destroyed = false
    private var activeRecording: VoiceRecordingSession? = null
    private var startRequestPending = false
    private var startRequestSequence = 0
    private var pendingStartCallback: KuiklyRenderCallback? = null
    private var recordingCleanupInProgress = false
    private var audioPlayer: MediaPlayer? = null
    private var audioPlaybackFile: File? = null
    private var streamingAudioTrack: AudioTrack? = null
    private var streamingSpeechConnection: HttpURLConnection? = null
    @Volatile
    private var audioPlaybackSequence = 0

    override fun call(method: String, params: String?, callback: KuiklyRenderCallback?): Any? {
        return when (method) {
            "ssoRequest" -> {
                ssoRequest(params, callback)
            }

            "showAlert" -> {
                showAlert(params, callback)
            }

            "closePage" -> {
                closePage(params)
            }

            "openPage" -> {
                openPage(params)
            }

            "copyToPasteboard" -> {
                copyToPasteboard(params)
            }

            "toast" -> {
                toast(params)
            }

            "log" -> {
                log(params)
            }

            "reportDT" -> {
                reportDT(params)
            }

            "reportRealtime" -> {
                reportRealtime(params)
            }

            "qqLiveSSORequest" -> {
                qqLiveSSORequest(params, callback)
            }

            "localServeTime" -> {
                localServeTime(params, callback)
            }

            "currentTimestamp" -> {
                currentTimestamp(params)
            }

            "dateFormatter" -> {
                dateFormatter(params)
            }

            "startVoiceRecording" -> {
                startVoiceRecording(callback)
            }

            "stopVoiceRecording" -> {
                stopVoiceRecording(callback)
            }

            "cancelVoiceRecording" -> {
                cancelVoiceRecording(callback)
            }

            "playBase64Audio" -> {
                playBase64Audio(params, callback)
            }

            "stopAudioPlayback" -> {
                stopAudioPlayback(callback)
            }

            "streamSpeechSynthesis" -> {
                streamSpeechSynthesis(params, callback)
            }

            "pickImages" -> {
                pickImages(params, callback)
            }

            "streamChatCompletion" -> {
                streamChatCompletion(params, callback)
            }

            "observeDrawerGestures" -> {
                observeDrawerGestures(callback)
            }

            "stopObservingDrawerGestures" -> {
                stopObservingDrawerGestures()
            }

            "observeBackRequests" -> {
                observeBackRequests(callback)
            }

            "stopObservingBackRequests" -> {
                stopObservingBackRequests()
            }

            else -> callback?.invoke(
                mapOf(
                    "code" to -1,
                    "message" to "方法不存在"
                )
            )
        }
    }

    override fun onDestroy() {
        destroyed = true
        stopObservingDrawerGestures()
        stopObservingBackRequests()
        val recording = synchronized(recordingLock) {
            startRequestSequence += 1
            startRequestPending = false
            pendingStartCallback = null
            activeRecording.also { activeRecording = null }
        }
        recording?.let(::cancelRecordingSession)
        releaseStreamingSpeechPlayback()
        releaseAudioPlayer()
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun playBase64Audio(
        params: String?,
        callback: KuiklyRenderCallback?,
    ) {
        audioPlaybackSequence += 1
        releaseStreamingSpeechPlayback()
        val playbackSequence = audioPlaybackSequence
        val payload = runCatching { JSONObject(params ?: "{}") }.getOrNull()
        val audioBase64 = payload?.optString("audioBase64").orEmpty()
        if (audioBase64.isBlank()) {
            deliverCallback(
                callback,
                failureResult("EMPTY_AUDIO", "MiMo 没有返回可播放的语音。"),
            )
            return
        }
        Thread(
            {
                val audioBytes = runCatching {
                    Base64.decode(audioBase64, Base64.DEFAULT)
                }.getOrElse {
                    deliverCallback(
                        callback,
                        failureResult("INVALID_AUDIO", "MiMo 返回的语音数据无效。"),
                    )
                    return@Thread
                }
                if (audioBytes.isEmpty() || audioBytes.size > MAX_PLAYBACK_AUDIO_BYTES) {
                    deliverCallback(
                        callback,
                        failureResult("INVALID_AUDIO_SIZE", "语音数据为空或超过播放大小限制。"),
                    )
                    return@Thread
                }
                val audioFile = runCatching {
                    File.createTempFile(
                        "stockchat_mimo_tts_",
                        ".wav",
                        KRApplication.application.cacheDir,
                    ).apply { writeBytes(audioBytes) }
                }.getOrElse { throwable ->
                    deliverCallback(
                        callback,
                        failureResult(
                            "AUDIO_FILE_FAILED",
                            throwable.message ?: "语音缓存失败，请稍后重试。",
                        ),
                    )
                    return@Thread
                }
                runOnMain {
                    if (destroyed || playbackSequence != audioPlaybackSequence) {
                        audioFile.delete()
                        if (!destroyed) {
                            deliverCallback(callback, successResult())
                        }
                        return@runOnMain
                    }
                    startAudioPlayback(audioFile, callback)
                }
            },
            "StockChatMimoAudioDecode",
        ).start()
    }

    private fun startAudioPlayback(
        audioFile: File,
        callback: KuiklyRenderCallback?,
    ) {
        releaseAudioPlayer()
        var playbackStarted = false
        val player = MediaPlayer()
        audioPlayer = player
        audioPlaybackFile = audioFile
        player.setOnPreparedListener {
            if (audioPlayer !== player || destroyed) {
                return@setOnPreparedListener
            }
            playbackStarted = true
            player.start()
            deliverCallback(callback, successResult())
        }
        player.setOnCompletionListener {
            if (audioPlayer === player) {
                releaseAudioPlayer()
            }
        }
        player.setOnErrorListener { _, _, _ ->
            if (!playbackStarted) {
                deliverCallback(
                    callback,
                    failureResult("AUDIO_PLAYBACK_FAILED", "当前设备无法播放 MiMo 语音。"),
                )
            }
            if (audioPlayer === player) {
                releaseAudioPlayer()
            }
            true
        }
        runCatching {
            player.setDataSource(audioFile.absolutePath)
            player.prepareAsync()
        }.onFailure { throwable ->
            releaseAudioPlayer()
            deliverCallback(
                callback,
                failureResult(
                    "AUDIO_PLAYBACK_FAILED",
                    throwable.message ?: "语音播放失败，请稍后重试。",
                ),
            )
        }
    }

    private fun stopAudioPlayback(callback: KuiklyRenderCallback?) {
        audioPlaybackSequence += 1
        releaseStreamingSpeechPlayback()
        releaseAudioPlayer()
        deliverCallback(callback, successResult())
    }

    private fun streamSpeechSynthesis(
        params: String?,
        callback: KuiklyRenderCallback?,
    ) {
        val payload = runCatching { JSONObject(params ?: "{}") }.getOrNull()
        val apiKey = payload?.optString("apiKey")?.trim().orEmpty()
        val requestUrl = payload?.optString("url")?.trim().orEmpty()
        val requestBody = payload?.optString("requestBody").orEmpty()
        if (apiKey.isEmpty() || requestUrl.isEmpty() || requestBody.isEmpty()) {
            deliverCallback(
                callback,
                failureResult("INVALID_TTS_STREAM_REQUEST", "MiMo 流式语音请求参数不完整。"),
            )
            return
        }

        audioPlaybackSequence += 1
        val playbackSequence = audioPlaybackSequence
        releaseStreamingSpeechPlayback()
        releaseAudioPlayer()
        Thread(
            {
                var connection: HttpURLConnection? = null
                var audioTrack: AudioTrack? = null
                try {
                    connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        doInput = true
                        doOutput = true
                        connectTimeout = STREAM_CONNECT_TIMEOUT_MS
                        readTimeout = STREAM_READ_TIMEOUT_MS
                        setRequestProperty("Content-Type", "application/json")
                        setRequestProperty("Accept", "text/event-stream")
                        setRequestProperty("api-key", apiKey)
                    }
                    synchronized(recordingLock) {
                        if (destroyed || playbackSequence != audioPlaybackSequence) {
                            connection.disconnect()
                            deliverCallback(
                                callback,
                                mapOf("success" to 1, "event" to "end"),
                            )
                            return@Thread
                        }
                        streamingSpeechConnection = connection
                    }
                    connection.outputStream.use { output ->
                        output.write(requestBody.toByteArray(Charsets.UTF_8))
                    }
                    val statusCode = connection.responseCode
                    if (statusCode !in 200..299) {
                        val responseText = (connection.errorStream ?: connection.inputStream)
                            .bufferedReader(Charsets.UTF_8)
                            .use(BufferedReader::readText)
                        val message = runCatching {
                            JSONObject(responseText).optJSONObject("error")?.optString("message")
                        }.getOrNull().orEmpty().ifBlank {
                            "MiMo 语音请求失败（HTTP $statusCode）。"
                        }
                        deliverCallback(
                            callback,
                            failureResult("MIMO_TTS_HTTP_$statusCode", message),
                        )
                        return@Thread
                    }

                    var receivedAudio = false
                    var playbackStarted = false
                    var writtenFrames = 0L
                    connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                        reader.forEachLine { line ->
                            if (destroyed || playbackSequence != audioPlaybackSequence) {
                                return@forEachLine
                            }
                            if (!line.startsWith("data:")) {
                                return@forEachLine
                            }
                            val data = line.removePrefix("data:").trim()
                            if (data.isEmpty() || data == "[DONE]") {
                                return@forEachLine
                            }
                            val audioBase64 = runCatching {
                                JSONObject(data)
                                    .optJSONArray("choices")
                                    ?.optJSONObject(0)
                                    ?.optJSONObject("delta")
                                    ?.optJSONObject("audio")
                                    ?.optString("data")
                                    .orEmpty()
                            }.getOrNull().orEmpty()
                            if (audioBase64.isEmpty()) {
                                return@forEachLine
                            }
                            val pcmBytes = runCatching {
                                Base64.decode(audioBase64, Base64.DEFAULT)
                            }.getOrElse {
                                throw IllegalArgumentException("MiMo 返回了无效的音频分片。")
                            }
                            if (pcmBytes.isEmpty()) {
                                return@forEachLine
                            }
                            val nextWrittenBytes = writtenFrames * TTS_BYTES_PER_FRAME + pcmBytes.size
                            if (nextWrittenBytes > MAX_PLAYBACK_AUDIO_BYTES) {
                                throw IllegalArgumentException("语音数据超过播放大小限制。")
                            }
                            if (audioTrack == null) {
                                audioTrack = createStreamingAudioTrack()
                                synchronized(recordingLock) {
                                    if (destroyed || playbackSequence != audioPlaybackSequence) {
                                        return@forEachLine
                                    }
                                    streamingAudioTrack = audioTrack
                                }
                                audioTrack?.play()
                            }
                            writeStreamingAudio(audioTrack ?: return@forEachLine, pcmBytes)
                            writtenFrames += pcmBytes.size / TTS_BYTES_PER_FRAME
                            receivedAudio = true
                            if (!playbackStarted) {
                                playbackStarted = true
                                deliverCallback(
                                    callback,
                                    mapOf("success" to 1, "event" to "start"),
                                )
                            }
                        }
                    }
                    if (destroyed || playbackSequence != audioPlaybackSequence) {
                        deliverCallback(callback, mapOf("success" to 1, "event" to "end"))
                        return@Thread
                    }
                    if (!receivedAudio || audioTrack == null) {
                        deliverCallback(
                            callback,
                            failureResult("EMPTY_AUDIO", "MiMo 没有返回可播放的语音。"),
                        )
                        return@Thread
                    }
                    awaitStreamingPlayback(audioTrack ?: return@Thread, writtenFrames, playbackSequence)
                    deliverCallback(callback, mapOf("success" to 1, "event" to "end"))
                } catch (throwable: Throwable) {
                    if (destroyed || playbackSequence != audioPlaybackSequence) {
                        deliverCallback(callback, mapOf("success" to 1, "event" to "end"))
                    } else {
                        deliverCallback(
                            callback,
                            failureResult(
                                "MIMO_TTS_STREAM_FAILED",
                                throwable.message ?: "MiMo 流式语音生成失败，请稍后重试。",
                            ),
                        )
                    }
                } finally {
                    synchronized(recordingLock) {
                        if (streamingSpeechConnection === connection) {
                            streamingSpeechConnection = null
                        }
                        if (streamingAudioTrack === audioTrack) {
                            streamingAudioTrack = null
                        }
                    }
                    connection?.disconnect()
                    releaseAudioTrack(audioTrack)
                }
            },
            "StockChatMimoTtsStream",
        ).start()
    }

    private fun createStreamingAudioTrack(): AudioTrack {
        val minBufferSize = AudioTrack.getMinBufferSize(
            TTS_SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBufferSize <= 0) {
            throw IllegalStateException("当前设备不支持 MiMo 流式音频格式。")
        }
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(TTS_SAMPLE_RATE_HZ)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
            .also { audioTrack ->
                if (audioTrack.state != AudioTrack.STATE_INITIALIZED) {
                    releaseAudioTrack(audioTrack)
                    throw IllegalStateException("当前设备无法初始化流式语音播放。")
                }
            }
    }

    private fun writeStreamingAudio(audioTrack: AudioTrack, pcmBytes: ByteArray) {
        var offset = 0
        while (offset < pcmBytes.size) {
            val written = audioTrack.write(
                pcmBytes,
                offset,
                pcmBytes.size - offset,
                AudioTrack.WRITE_BLOCKING,
            )
            if (written <= 0) {
                throw IllegalStateException("流式语音播放写入失败（$written）。")
            }
            offset += written
        }
    }

    private fun awaitStreamingPlayback(
        audioTrack: AudioTrack,
        writtenFrames: Long,
        playbackSequence: Int,
    ) {
        val deadline = System.currentTimeMillis() + STREAM_PLAYBACK_DRAIN_TIMEOUT_MS
        while (
            !destroyed &&
            playbackSequence == audioPlaybackSequence &&
            audioTrack.playbackHeadPosition.toLong() < writtenFrames &&
            System.currentTimeMillis() < deadline
        ) {
            Thread.sleep(STREAM_PLAYBACK_POLL_INTERVAL_MS)
        }
        if (
            !destroyed &&
            playbackSequence == audioPlaybackSequence &&
            audioTrack.playbackHeadPosition.toLong() < writtenFrames
        ) {
            throw IllegalStateException("流式语音播放未能正常结束。")
        }
    }

    private fun releaseStreamingSpeechPlayback() {
        val connection: HttpURLConnection?
        val audioTrack: AudioTrack?
        synchronized(recordingLock) {
            connection = streamingSpeechConnection
            streamingSpeechConnection = null
            audioTrack = streamingAudioTrack
            streamingAudioTrack = null
        }
        connection?.disconnect()
        releaseAudioTrack(audioTrack)
    }

    private fun releaseAudioTrack(audioTrack: AudioTrack?) {
        audioTrack ?: return
        runCatching { audioTrack.pause() }
        runCatching { audioTrack.flush() }
        runCatching { audioTrack.stop() }
        runCatching { audioTrack.release() }
    }

    private fun releaseAudioPlayer() {
        audioPlayer?.let { player ->
            runCatching {
                if (player.isPlaying) {
                    player.stop()
                }
            }
            runCatching { player.reset() }
            runCatching { player.release() }
        }
        audioPlayer = null
        audioPlaybackFile?.delete()
        audioPlaybackFile = null
    }

    private fun streamChatCompletion(
        params: String?,
        callback: KuiklyRenderCallback?,
    ) {
        val payload = runCatching { JSONObject(params ?: "{}") }.getOrNull()
        val apiKey = payload?.optString("apiKey")?.trim().orEmpty()
        val requestUrl = payload?.optString("url")?.trim().orEmpty()
        val requestBody = payload?.optString("requestBody").orEmpty()
        val providerDisplayName = payload?.optString("providerDisplayName")?.trim().orEmpty()
            .ifBlank { "模型服务" }
        val requestHeaders = runCatching {
            payload?.optString("headers")?.takeIf { it.isNotBlank() }?.let(::JSONObject)
        }.getOrNull()
        if (apiKey.isEmpty() || requestUrl.isEmpty() || requestBody.isEmpty()) {
            deliverCallback(
                callback,
                failureResult("INVALID_STREAM_REQUEST", "$providerDisplayName 流式请求参数不完整。"),
            )
            return
        }
        Thread(
            {
                var connection: HttpURLConnection? = null
                try {
                    connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        doInput = true
                        doOutput = true
                        connectTimeout = STREAM_CONNECT_TIMEOUT_MS
                        readTimeout = STREAM_READ_TIMEOUT_MS
                        setRequestProperty("Content-Type", "application/json")
                        setRequestProperty("Accept", "text/event-stream")
                        var hasAuthorization = false
                        requestHeaders?.keys()?.forEach { key ->
                            val value = requestHeaders.optString(key).trim()
                            if (key.isNotBlank() && value.isNotBlank()) {
                                setRequestProperty(key, value)
                                if (key.equals("Authorization", ignoreCase = true)) {
                                    hasAuthorization = true
                                }
                            }
                        }
                        if (!hasAuthorization) {
                            setRequestProperty("Authorization", "Bearer $apiKey")
                        }
                    }
                    connection.outputStream.use { output ->
                        output.write(requestBody.toByteArray(Charsets.UTF_8))
                    }
                    val statusCode = connection.responseCode
                    val responseStream = if (statusCode in 200..299) {
                        connection.inputStream
                    } else {
                        connection.errorStream ?: connection.inputStream
                    }
                    val responseText = responseStream.bufferedReader(Charsets.UTF_8).use { reader ->
                        if (statusCode !in 200..299) {
                            reader.readText()
                        } else if (connection.contentType.orEmpty().contains("text/event-stream", ignoreCase = true)) {
                            readSseResponse(reader, callback)
                            ""
                        } else {
                            val body = reader.readText()
                            deliverJsonStreamResponse(body, providerDisplayName, callback)
                            ""
                        }
                    }
                    if (statusCode !in 200..299) {
                        val message = runCatching {
                            JSONObject(responseText).optJSONObject("error")?.optString("message")
                        }.getOrNull().orEmpty().ifBlank {
                            "$providerDisplayName 请求失败（HTTP $statusCode）。"
                        }
                        deliverCallback(callback, failureResult("STREAM_HTTP_$statusCode", message))
                    }
                } catch (throwable: Throwable) {
                    deliverCallback(
                        callback,
                        failureResult(
                            "STREAM_FAILED",
                            throwable.message ?: "$providerDisplayName 流式请求失败，请稍后重试。",
                        ),
                    )
                } finally {
                    connection?.disconnect()
                }
            },
            "StockChatModelStream",
        ).start()
    }

    private fun readSseResponse(
        reader: BufferedReader,
        callback: KuiklyRenderCallback?,
    ) {
        var terminalEventReceived = false
        var eventName = ""
        reader.forEachLine { line ->
            if (terminalEventReceived) {
                return@forEachLine
            }
            val normalizedLine = line.removePrefix("\uFEFF").trimStart()
            if (normalizedLine.isBlank()) {
                eventName = ""
                return@forEachLine
            }
            val separatorIndex = normalizedLine.indexOf(':')
            if (separatorIndex <= 0) {
                return@forEachLine
            }
            val field = normalizedLine.substring(0, separatorIndex).trim()
            var value = normalizedLine.substring(separatorIndex + 1)
            if (value.startsWith(' ')) {
                value = value.substring(1)
            }
            when (field) {
                "event" -> {
                    eventName = value.trim()
                }
                "data" -> {
                    val data = value.trim()
                    if (data.isEmpty()) {
                        return@forEachLine
                    }
                    if (data.equals("[DONE]", ignoreCase = true)) {
                        terminalEventReceived = true
                        deliverCallback(callback, mapOf("success" to 1, "event" to "end"))
                        return@forEachLine
                    }
                    val payload = runCatching { JSONObject(data) }.getOrNull()
                    if (payload == null) {
                        return@forEachLine
                    }
                    val providerError = streamErrorMessage(payload)
                    if (eventName.equals("error", ignoreCase = true) || providerError != null) {
                        terminalEventReceived = true
                        deliverCallback(
                            callback,
                            failureResult(
                                "STREAM_PROVIDER_ERROR",
                                providerError ?: "模型服务返回了流式错误。",
                            ),
                        )
                        return@forEachLine
                    }
                    val delta = streamDeltaContent(payload)
                    if (delta.isNotEmpty()) {
                        deliverCallback(
                            callback,
                            mapOf("success" to 1, "event" to "delta", "content" to delta),
                        )
                    }
                    eventName = ""
                }
            }
        }
        if (!terminalEventReceived) {
            deliverCallback(callback, mapOf("success" to 1, "event" to "end"))
        }
    }

    private fun deliverJsonStreamResponse(
        responseText: String,
        providerDisplayName: String,
        callback: KuiklyRenderCallback?,
    ) {
        val payload = runCatching { JSONObject(responseText) }.getOrNull()
        if (payload == null) {
            deliverCallback(
                callback,
                failureResult("STREAM_INVALID_RESPONSE", "$providerDisplayName 返回了无法解析的响应。"),
            )
            return
        }
        val errorMessage = streamErrorMessage(payload)
        if (errorMessage != null) {
            deliverCallback(callback, failureResult("STREAM_PROVIDER_ERROR", errorMessage))
            return
        }
        val content = streamCompletionContent(payload)
        if (content.isBlank()) {
            deliverCallback(
                callback,
                failureResult("STREAM_EMPTY_RESPONSE", "$providerDisplayName 没有返回可展示的回答。"),
            )
            return
        }
        deliverCallback(
            callback,
            mapOf("success" to 1, "event" to "delta", "content" to content),
        )
        deliverCallback(callback, mapOf("success" to 1, "event" to "end"))
    }

    private fun streamCompletionContent(payload: JSONObject): String {
        val choice = payload.optJSONArray("choices")?.optJSONObject(0)
        return listOf(
            choice?.optJSONObject("message")?.opt("content"),
            choice?.optJSONObject("delta")?.opt("content"),
            payload.optJSONObject("output")
                ?.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.opt("content"),
            payload.optJSONObject("output")
                ?.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("delta")
                ?.opt("content"),
            payload.opt("content"),
        )
            .asSequence()
            .map(::streamContentText)
            .firstOrNull(String::isNotBlank)
            .orEmpty()
    }

    private fun streamContentText(value: Any?): String {
        return when (value) {
            is String -> value
            is JSONArray -> buildString {
                for (index in 0 until value.length()) {
                    val part = streamContentText(value.opt(index))
                    if (part.isNotEmpty()) {
                        append(part)
                    }
                }
            }
            is JSONObject -> listOf(value.opt("text"), value.opt("content"))
                .asSequence()
                .map(::streamContentText)
                .firstOrNull(String::isNotBlank)
                .orEmpty()
            else -> ""
        }
    }

    private fun streamDeltaContent(payload: JSONObject): String {
        val choice = payload.optJSONArray("choices")?.optJSONObject(0)
        val delta = choice?.optJSONObject("delta")
        val deltaContent = streamContentText(delta?.opt("content"))
        if (deltaContent.isNotEmpty()) {
            return deltaContent
        }
        val messageContent = streamContentText(choice?.optJSONObject("message")?.opt("content"))
        if (messageContent.isNotEmpty()) {
            return messageContent
        }
        val outputChoice = payload.optJSONObject("output")
            ?.optJSONArray("choices")
            ?.optJSONObject(0)
        val outputDelta = streamContentText(outputChoice?.optJSONObject("delta")?.opt("content"))
        if (outputDelta.isNotEmpty()) {
            return outputDelta
        }
        return streamContentText(outputChoice?.optJSONObject("message")?.opt("content"))
    }

    private fun streamErrorMessage(payload: JSONObject): String? {
        val objectMessage = payload.optJSONObject("error")?.optString("message").orEmpty().trim()
        if (objectMessage.isNotEmpty()) {
            return objectMessage
        }
        val directError = payload.optString("error").trim()
        return directError.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
    }

    private fun observeDrawerGestures(callback: KuiklyRenderCallback?) {
        runOnMain {
            (activity as? KuiklyRenderActivity)?.setDrawerGestureCallback { direction ->
                deliverCallback(
                    callback,
                    mapOf("success" to 1, "direction" to direction),
                )
            }
        }
    }

    private fun stopObservingDrawerGestures() {
        runOnMain {
            (activity as? KuiklyRenderActivity)?.setDrawerGestureCallback(null)
        }
    }

    private fun observeBackRequests(callback: KuiklyRenderCallback?) {
        runOnMain {
            (activity as? KuiklyRenderActivity)?.setBackRequestCallback {
                deliverCallback(callback, mapOf("success" to 1))
            }
        }
    }

    private fun stopObservingBackRequests() {
        runOnMain {
            (activity as? KuiklyRenderActivity)?.setBackRequestCallback(null)
        }
    }

    private fun reportRealtime(params: String?) {
    }

    private fun reportDT(params: String?) {
    }

    private fun log(params: String?) {
        if (params == null) {
            return
        }

        val paramJSON = JSONObject(params)
        Log.i("KuiklyRender", paramJSON.optString("content"))
    }

    private fun toast(params: String?) {
        if (params == null) {
            return
        }
        val paramJSON = JSONObject(params)
        Toast.makeText(
            KRApplication.application,
            paramJSON.optString("content"),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun copyToPasteboard(params: String?) {
        if (params == null) {
            return
        }

        val paramJSON = JSONObject(params)
        (context?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.also {
            it.setPrimaryClip(ClipData.newPlainText(MODULE_NAME, paramJSON.optString("content")))
        }
    }

    private fun openPage(params: String?) {
        if (params == null) {
            return
        }
        val ctx = context ?: return
        val paramJSON = JSONObject(params)
        val url = paramJSON.optString("url")
    }

    private fun closePage(params: String?) {
        activity?.finish()
    }

    private fun showAlert(params: String?, callback: KuiklyRenderCallback?) {
        if (params == null) {
            return
        }
        val paramJSON = JSONObject(params)
        val titleText = paramJSON.optString("title")
        val message = paramJSON.optString("message")
        val buttons = paramJSON.optJSONArray("buttons") ?: JSONArray()
    }

    private fun ssoRequest(params: String?, callback: KuiklyRenderCallback?) {}

    private fun qqLiveSSORequest(params: String?, callback: KuiklyRenderCallback?) {
    }

    private fun localServeTime(params: String?, callback: KuiklyRenderCallback?) {
        val time = (System.currentTimeMillis() / 1000.0)
        callback?.invoke(
            mapOf(
                "time" to time
            )
        )
    }

    private fun currentTimestamp(params: String?): String {
        return (System.currentTimeMillis()).toString()
    }

    private fun dateFormatter(params: String?): String {
        val paramJSONObject = JSONObject(params ?: "{}")
        val data = Date(paramJSONObject.optLong("timeStamp"))
        val format = SimpleDateFormat(paramJSONObject.optString("format"))
        return format.format(data)
    }

    private fun pickImages(params: String?, callback: KuiklyRenderCallback?) {
        val maxCount = runCatching {
            JSONObject(params ?: "{}").optInt("maxCount", MAX_IMAGE_SELECTION_COUNT)
        }.getOrDefault(MAX_IMAGE_SELECTION_COUNT).coerceIn(1, MAX_IMAGE_SELECTION_COUNT)
        runOnMain {
            if (destroyed) {
                deliverCallback(
                    callback,
                    failureResult("MODULE_DESTROYED", "图片选择模块已释放。"),
                )
                return@runOnMain
            }
            val hostActivity = activity as? KuiklyRenderActivity
            if (hostActivity == null) {
                deliverCallback(
                    callback,
                    failureResult("ACTIVITY_UNAVAILABLE", "当前页面无法打开图片选择器。"),
                )
                return@runOnMain
            }
            hostActivity.pickImages(maxCount) imagePickerResult@{ result ->
                if (result.errorCode != null) {
                    deliverCallback(
                        callback,
                        failureResult(
                            result.errorCode,
                            result.errorMessage ?: "图片选择失败，请稍后重试。",
                        ),
                    )
                    return@imagePickerResult
                }
                deliverCallback(
                    callback,
                    mapOf(
                        "success" to 1,
                        "cancelled" to if (result.cancelled) 1 else 0,
                        "images" to result.images,
                        "previewImages" to result.previewImages,
                        "truncated" to if (result.truncated) 1 else 0,
                    ),
                )
            }
        }
    }

    private fun startVoiceRecording(callback: KuiklyRenderCallback?) {
        runOnMain {
            if (destroyed) {
                deliverCallback(
                    callback,
                    failureResult("MODULE_DESTROYED", "语音录音模块已释放。"),
                )
                return@runOnMain
            }

            var rejectionCode = ""
            val requestSequence = synchronized(recordingLock) {
                when {
                    activeRecording != null -> {
                        rejectionCode = "ALREADY_RECORDING"
                        null
                    }
                    startRequestPending -> {
                        rejectionCode = "START_IN_PROGRESS"
                        null
                    }
                    recordingCleanupInProgress -> {
                        rejectionCode = "RECORDING_CLEANUP_IN_PROGRESS"
                        null
                    }
                    else -> {
                        startRequestPending = true
                        pendingStartCallback = callback
                        startRequestSequence += 1
                        startRequestSequence
                    }
                }
            }
            if (requestSequence == null) {
                val errorMessage = when (rejectionCode) {
                    "ALREADY_RECORDING" -> "语音录音正在进行中。"
                    "RECORDING_CLEANUP_IN_PROGRESS" -> "上一段录音正在处理，请稍候。"
                    else -> "正在申请麦克风权限，请稍候。"
                }
                deliverCallback(callback, failureResult(rejectionCode, errorMessage))
                return@runOnMain
            }

            val hostActivity = activity as? KuiklyRenderActivity
            if (hostActivity == null) {
                clearPendingStart(requestSequence)
                deliverCallback(
                    callback,
                    failureResult("ACTIVITY_UNAVAILABLE", "当前页面无法访问麦克风。"),
                )
                return@runOnMain
            }

            hostActivity.requestRecordAudioPermission permissionResult@{ granted ->
                val startCallback = synchronized(recordingLock) {
                    if (!startRequestPending || requestSequence != startRequestSequence) {
                        return@permissionResult
                    }
                    startRequestPending = false
                    pendingStartCallback.also { pendingStartCallback = null }
                }
                if (!granted) {
                    deliverCallback(
                        startCallback,
                        failureResult(
                            "RECORD_AUDIO_PERMISSION_DENIED",
                            "需要麦克风权限才能使用语音输入。",
                        ),
                    )
                    return@permissionResult
                }
                startAudioCapture(startCallback)
            }
        }
    }

    private fun stopVoiceRecording(callback: KuiklyRenderCallback?) {
        runOnMain {
            val recording = synchronized(recordingLock) {
                activeRecording?.also {
                    activeRecording = null
                    recordingCleanupInProgress = true
                }
            }
            if (recording == null) {
                deliverCallback(
                    callback,
                    failureResult("NOT_RECORDING", "当前没有正在进行的语音录音。"),
                )
                return@runOnMain
            }
            Thread(
                {
                    val result = runCatching {
                        finishRecordingSession(recording)
                    }.getOrElse {
                        failureResult(
                            "RECORDING_FINALIZE_FAILED",
                            it.message ?: "处理语音录音失败。",
                        )
                    }
                    synchronized(recordingLock) {
                        recordingCleanupInProgress = false
                    }
                    deliverCallback(callback, result)
                },
                "StockChatVoiceEncoder",
            ).start()
        }
    }

    private fun cancelVoiceRecording(callback: KuiklyRenderCallback?) {
        runOnMain {
            val pendingCallback: KuiklyRenderCallback?
            val recording: VoiceRecordingSession?
            synchronized(recordingLock) {
                startRequestSequence += 1
                startRequestPending = false
                pendingCallback = pendingStartCallback
                pendingStartCallback = null
                recording = activeRecording
                activeRecording = null
                if (recording != null) {
                    recordingCleanupInProgress = true
                }
            }
            pendingCallback?.let {
                deliverCallback(
                    it,
                    failureResult("RECORDING_CANCELLED", "语音录音已取消。"),
                )
            }
            if (recording != null) {
                Thread(
                    {
                        cancelRecordingSession(recording)
                        synchronized(recordingLock) {
                            recordingCleanupInProgress = false
                        }
                        deliverCallback(callback, successResult())
                    },
                    "StockChatVoiceCanceller",
                ).start()
            } else {
                deliverCallback(callback, successResult())
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startAudioCapture(callback: KuiklyRenderCallback?) {
        val minimumBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minimumBufferSize <= 0) {
            deliverCallback(
                callback,
                failureResult("AUDIO_RECORD_INIT_FAILED", "设备不支持 16kHz 单声道录音。"),
            )
            return
        }
        val requestedBufferSize = maxOf(minimumBufferSize, SAMPLE_RATE_HZ * BYTES_PER_SAMPLE / 5)
        val bufferSize = if (requestedBufferSize % BYTES_PER_SAMPLE == 0) {
            requestedBufferSize
        } else {
            requestedBufferSize + 1
        }
        val audioRecord = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
            )
        } catch (throwable: Throwable) {
            deliverCallback(
                callback,
                failureResult(
                    "AUDIO_RECORD_INIT_FAILED",
                    throwable.message ?: "无法初始化麦克风。",
                ),
            )
            return
        }
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            deliverCallback(
                callback,
                failureResult("AUDIO_RECORD_INIT_FAILED", "麦克风初始化失败。"),
            )
            return
        }

        try {
            audioRecord.startRecording()
        } catch (throwable: Throwable) {
            audioRecord.release()
            deliverCallback(
                callback,
                failureResult(
                    "AUDIO_RECORD_START_FAILED",
                    throwable.message ?: "无法开始语音录音。",
                ),
            )
            return
        }
        if (audioRecord.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            audioRecord.release()
            deliverCallback(
                callback,
                failureResult("AUDIO_RECORD_START_FAILED", "麦克风没有进入录音状态。"),
            )
            return
        }

        val recording = VoiceRecordingSession(audioRecord, bufferSize)
        synchronized(recordingLock) {
            if (destroyed || activeRecording != null) {
                cancelRecordingSession(recording)
                deliverCallback(
                    callback,
                    failureResult("ALREADY_RECORDING", "语音录音正在进行中。"),
                )
                return
            }
            activeRecording = recording
        }
        recording.captureThread = Thread(
            { captureAudio(recording) },
            "StockChatVoiceRecorder",
        ).apply { start() }
        deliverCallback(callback, successResult())
    }

    private fun captureAudio(recording: VoiceRecordingSession) {
        val buffer = ByteArray(recording.bufferSize)
        while (recording.capturing) {
            val bytesRead = try {
                recording.audioRecord.read(buffer, 0, buffer.size)
            } catch (throwable: Throwable) {
                if (recording.capturing) {
                    recording.captureError = throwable.message ?: "读取麦克风数据失败。"
                }
                break
            }
            when {
                bytesRead > 0 -> {
                    if (!recording.capturing) {
                        continue
                    }
                    val reachedDurationLimit = synchronized(recording.pcmOutput) {
                        val remainingBytes = MAX_PCM_BYTES - recording.pcmOutput.size()
                        if (remainingBytes > 0) {
                            recording.pcmOutput.write(buffer, 0, minOf(bytesRead, remainingBytes))
                        }
                        recording.pcmOutput.size() >= MAX_PCM_BYTES
                    }
                    if (reachedDurationLimit) {
                        recording.capturing = false
                        runCatching {
                            if (recording.audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                                recording.audioRecord.stop()
                            }
                        }
                    }
                }

                bytesRead < 0 && recording.capturing -> {
                    recording.captureError = audioReadErrorMessage(bytesRead)
                    break
                }
            }
        }
        recording.capturing = false
    }

    private fun finishRecordingSession(recording: VoiceRecordingSession): Map<String, Any> {
        stopAndReleaseRecording(recording)
        recording.captureError?.let {
            return failureResult("RECORDING_FAILED", it)
        }
        val pcmBytes = synchronized(recording.pcmOutput) {
            recording.pcmOutput.toByteArray()
        }
        if (pcmBytes.isEmpty()) {
            return failureResult("EMPTY_AUDIO", "没有录到有效的语音内容。")
        }
        val durationMs = pcmBytes.size.toLong() * 1000L /
            (SAMPLE_RATE_HZ.toLong() * BYTES_PER_SAMPLE)
        if (durationMs < MIN_RECORDING_DURATION_MS) {
            return failureResult(
                "AUDIO_TOO_SHORT",
                "语音时间过短，请至少录制 ${MIN_RECORDING_DURATION_MS}ms。",
            )
        }
        val wavBytes = createWavFile(pcmBytes)
        return mapOf(
            "success" to 1,
            "audioBase64" to Base64.encodeToString(wavBytes, Base64.NO_WRAP),
            "mimeType" to WAV_MIME_TYPE,
            "durationMs" to durationMs,
        )
    }

    private fun cancelRecordingSession(recording: VoiceRecordingSession) {
        stopAndReleaseRecording(recording)
        synchronized(recording.pcmOutput) {
            recording.pcmOutput.reset()
        }
    }

    private fun stopAndReleaseRecording(recording: VoiceRecordingSession) {
        recording.capturing = false
        runCatching {
            if (recording.audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                recording.audioRecord.stop()
            }
        }
        val captureThread = recording.captureThread
        joinCaptureThread(captureThread)
        if (captureThread?.isAlive == true) {
            runCatching { recording.audioRecord.release() }
            joinCaptureThread(captureThread)
        } else {
            runCatching { recording.audioRecord.release() }
        }
        captureThread?.takeIf(Thread::isAlive)?.interrupt()
    }

    private fun joinCaptureThread(captureThread: Thread?) {
        try {
            captureThread?.join(RECORDING_THREAD_JOIN_TIMEOUT_MS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun clearPendingStart(requestSequence: Int) {
        synchronized(recordingLock) {
            if (requestSequence == startRequestSequence) {
                startRequestPending = false
                pendingStartCallback = null
            }
        }
    }

    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    private fun deliverCallback(
        callback: KuiklyRenderCallback?,
        result: Map<String, Any>,
    ) {
        runOnMain {
            if (!destroyed) {
                callback?.invoke(result)
            }
        }
    }

    private fun successResult(): Map<String, Any> = mapOf("success" to 1)

    private fun failureResult(errorCode: String, errorMessage: String): Map<String, Any> = mapOf(
        "success" to 0,
        "errorCode" to errorCode,
        "errorMessage" to errorMessage,
    )

    private fun audioReadErrorMessage(errorCode: Int): String = when (errorCode) {
        AudioRecord.ERROR_BAD_VALUE -> "麦克风读取参数无效。"
        AudioRecord.ERROR_INVALID_OPERATION -> "麦克风当前无法读取。"
        AudioRecord.ERROR_DEAD_OBJECT -> "麦克风连接已断开。"
        else -> "读取麦克风数据失败（$errorCode）。"
    }

    private fun createWavFile(pcmBytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream(WAV_HEADER_SIZE + pcmBytes.size)
        output.write("RIFF".toByteArray(Charsets.US_ASCII))
        output.writeLittleEndianInt(36 + pcmBytes.size)
        output.write("WAVE".toByteArray(Charsets.US_ASCII))
        output.write("fmt ".toByteArray(Charsets.US_ASCII))
        output.writeLittleEndianInt(16)
        output.writeLittleEndianShort(1)
        output.writeLittleEndianShort(CHANNEL_COUNT)
        output.writeLittleEndianInt(SAMPLE_RATE_HZ)
        output.writeLittleEndianInt(SAMPLE_RATE_HZ * CHANNEL_COUNT * BYTES_PER_SAMPLE)
        output.writeLittleEndianShort(CHANNEL_COUNT * BYTES_PER_SAMPLE)
        output.writeLittleEndianShort(BITS_PER_SAMPLE)
        output.write("data".toByteArray(Charsets.US_ASCII))
        output.writeLittleEndianInt(pcmBytes.size)
        output.write(pcmBytes)
        return output.toByteArray()
    }

    private fun ByteArrayOutputStream.writeLittleEndianInt(value: Int) {
        write(value and 0xFF)
        write(value shr 8 and 0xFF)
        write(value shr 16 and 0xFF)
        write(value shr 24 and 0xFF)
    }

    private fun ByteArrayOutputStream.writeLittleEndianShort(value: Int) {
        write(value and 0xFF)
        write(value shr 8 and 0xFF)
    }

    companion object {
        const val MODULE_NAME = "HRBridgeModule"

        private const val SAMPLE_RATE_HZ = 16_000
        private const val CHANNEL_COUNT = 1
        private const val BITS_PER_SAMPLE = 16
        private const val BYTES_PER_SAMPLE = BITS_PER_SAMPLE / 8
        private const val WAV_HEADER_SIZE = 44
        private const val WAV_MIME_TYPE = "audio/wav"
        private const val MIN_RECORDING_DURATION_MS = 300L
        private const val MAX_RECORDING_DURATION_SECONDS = 30
        private const val MAX_PCM_BYTES =
            SAMPLE_RATE_HZ * CHANNEL_COUNT * BYTES_PER_SAMPLE * MAX_RECORDING_DURATION_SECONDS
        private const val RECORDING_THREAD_JOIN_TIMEOUT_MS = 2_000L
        private const val MAX_IMAGE_SELECTION_COUNT = 9
        private const val MAX_PLAYBACK_AUDIO_BYTES = 24 * 1024 * 1024
        private const val TTS_SAMPLE_RATE_HZ = 24_000
        private const val TTS_BYTES_PER_FRAME = 2
        private const val STREAM_PLAYBACK_POLL_INTERVAL_MS = 20L
        private const val STREAM_PLAYBACK_DRAIN_TIMEOUT_MS = 10_000L
        private const val STREAM_CONNECT_TIMEOUT_MS = 30_000
        private const val STREAM_READ_TIMEOUT_MS = 90_000
    }

    private class VoiceRecordingSession(
        val audioRecord: AudioRecord,
        val bufferSize: Int,
    ) {
        val pcmOutput = ByteArrayOutputStream()

        @Volatile
        var capturing = true

        @Volatile
        var captureError: String? = null

        @Volatile
        var captureThread: Thread? = null
    }
}

private fun JSONObject.toMap(): Map<Any, Any> {
    val map = mutableMapOf<Any, Any>()
    val keys = keys()
    while (keys.hasNext()) {
        val key = keys.next()
        when (val v = opt(key)) {
            is JSONObject -> {
                map[key] = v.toMap()
            }

            else -> {
                v?.also {
                    map[key] = it
                }
            }
        }
    }
    return map
}
