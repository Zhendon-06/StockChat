package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.base.bridgeModule
import com.guet.liang.stockchat.base.setTimeout
import com.guet.liang.stockchat.model.SpeechRecognitionResult
import com.guet.liang.stockchat.model.VoiceInputState
import com.tencent.kuikly.core.base.Animation
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ColorStop
import com.tencent.kuikly.core.base.Direction
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.event.LongPressParams
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.timer.Timer
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

// 语音输入：按住说话手势、声纹动画、识别请求生命周期与录音浮层。

private const val VOICE_CANCEL_DISTANCE = 56f

internal fun StockChatPage.VoiceRecordingOverlay(container: ViewContainer<*, *>) {
    val ctx = this
    val metrics = ctx.layoutMetrics
    with(container) {
        vif({ ctx.voicePressActive }) {
            View {
                attr {
                    absolutePosition(left = 0f, right = 0f, bottom = 0f)
                    height(metrics.dp(350f) + pagerData.safeAreaInsets.bottom)
                    backgroundLinearGradient(
                        Direction.TO_BOTTOM,
                        ColorStop(Color(0x00F6F7F4), 0f),
                        // 中段起完全不透明：输入面板顶缘约在遮罩 48% 处，往下必须实色盖住输入框
                        ColorStop(
                            if (ctx.voicePressCanceled) Color(0xFFE7B35A) else Color(0xFF43D7BB),
                            0.48f,
                        ),
                        ColorStop(
                            if (ctx.voicePressCanceled) Color(0xFFE2A13C) else Color(0xFF32C9AA),
                            1f,
                        ),
                    )
                    touchEnable(false)
                    zIndex(8)
                }
                Text {
                    attr {
                        absolutePosition(
                            top = metrics.dp(122f),
                            left = metrics.dp(24f),
                            right = metrics.dp(24f),
                        )
                        text(if (ctx.voicePressCanceled) "松手取消" else "松手发送，上移取消")
                        fontSize(metrics.dp(17f))
                        fontWeightMedium()
                        textAlignCenter()
                        color(StockChatTheme.textPrimary)
                    }
                }
                View {
                    attr {
                        absolutePosition(
                            top = metrics.dp(184f),
                            left = metrics.dp(22f),
                            right = metrics.dp(22f),
                        )
                        height(metrics.dp(58f))
                        flexDirectionRow()
                        alignItemsCenter()
                        justifyContentCenter()
                    }
                    repeat(30) { barIndex ->
                        View {
                            attr {
                                val phase = ctx.voiceWavePhase.toFloat()
                                val primary = kotlin.math.abs(
                                    kotlin.math.sin((barIndex * 0.58f + phase * 0.42f).toDouble())
                                ).toFloat()
                                val secondary = kotlin.math.abs(
                                    kotlin.math.sin((barIndex * 0.21f - phase * 0.31f).toDouble())
                                ).toFloat()
                                width(metrics.dp(4f))
                                height(metrics.dp(10f + primary * 31f + secondary * 11f))
                                borderRadius(metrics.dp(2f))
                                backgroundColor(Color.WHITE)
                                margin(left = metrics.dp(2f), right = metrics.dp(2f))
                                animation(Animation.easeInOut(0.12f), ctx.voiceWavePhase)
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun StockChatPage.voiceInputHint(): String {
    return when (voiceInputState) {
        VoiceInputState.IDLE -> "问行情、学炒股或按住说话"
        VoiceInputState.STARTING -> "正在启动麦克风…"
        VoiceInputState.RECORDING -> "正在录音，再点一次结束"
        VoiceInputState.TRANSCRIBING -> "MiMo 正在识别语音…"
    }
}

internal fun StockChatPage.voiceModePrompt(): String {
    return when (voiceInputState) {
        VoiceInputState.IDLE -> "按住 说话"
        VoiceInputState.STARTING -> "正在启动麦克风…"
        VoiceInputState.RECORDING -> if (voicePressCanceled) "松手取消" else "松手发送"
        VoiceInputState.TRANSCRIBING -> "MiMo 正在识别…"
    }
}

internal fun StockChatPage.toggleVoiceMode() {
    if (voiceMode) {
        cancelVoicePress()
        composerFocused = true
        composerExpanded = true
        collapseComposerAfterSettle = false
        voiceMode = false
        closeDrawer()
        focusTextInputAfterLayout()
        return
    }
    cancelVoiceInput()
    if (inputRefReady) {
        inputRef.view?.blur()
    }
    resetKeyboardState()
    voiceMode = true
    closeDrawer()
}

// 非语音模式下，输入框未输入（无文字、无附件、未聚焦）时长按也进入按住说话；
// 有草稿时不触发，把长按留给文本相关操作
internal fun StockChatPage.composerHoldToTalkReady(): Boolean =
    !voiceMode &&
        selectedHomeTab == HOME_TAB_CHAT &&
        !composerFocused &&
        inputText.isEmpty() &&
        selectedImageCount == 0

// 识别结果落到输入框由用户确认后发送，不直接发出；追加在已有草稿之后。
// 状态顺序与 toggleVoiceMode 关闭分支一致：展开先触发、键盘动画后接管
internal fun StockChatPage.fillComposerWithVoiceResult(recognizedText: String) {
    inputText = (inputText + recognizedText).take(MAX_COMPOSER_TEXT_LENGTH)
    composerFocused = true
    composerExpanded = true
    collapseComposerAfterSettle = false
    voiceMode = false
    focusTextInputAfterLayout()
}

internal fun StockChatPage.handleVoiceLongPress(params: LongPressParams) {
    when (params.state) {
        "start" -> {
            if (isSending) {
                bridgeModule.toast("请等待当前回答完成")
                return
            }
            if (voiceInputState != VoiceInputState.IDLE) {
                bridgeModule.toast("语音输入正在处理中")
                return
            }
            voicePressStartY = params.pageY
            voicePressCanceled = false
            voicePressActive = true
            voicePressReleaseRequested = false
            startVoiceWaveAnimation()
            startVoiceInput()
        }
        "move" -> {
            if (voicePressActive) {
                voicePressCanceled = voicePressStartY - params.pageY >= VOICE_CANCEL_DISTANCE
            }
        }
        "end" -> finishVoicePress()
    }
}

internal fun StockChatPage.finishVoicePress() {
    if (!voicePressActive) {
        return
    }
    val shouldCancel = voicePressCanceled
    voicePressActive = false
    voicePressCanceled = false
    voicePressStartY = 0f
    stopVoiceWaveAnimation()
    if (shouldCancel) {
        cancelVoiceInput()
        bridgeModule.toast("已取消语音发送")
        return
    }
    when (voiceInputState) {
        VoiceInputState.STARTING -> voicePressReleaseRequested = true
        VoiceInputState.RECORDING -> stopVoiceInput()
        VoiceInputState.IDLE,
        VoiceInputState.TRANSCRIBING -> Unit
    }
}

internal fun StockChatPage.cancelVoicePress() {
    if (!voicePressActive && voiceInputState == VoiceInputState.IDLE) {
        return
    }
    voicePressActive = false
    voicePressCanceled = false
    voicePressStartY = 0f
    stopVoiceWaveAnimation()
    cancelVoiceInput()
}

internal fun StockChatPage.startVoiceWaveAnimation() {
    stopVoiceWaveAnimation()
    voiceWavePhase = 0
    voiceWaveTimer = Timer().also { timer ->
        timer.schedule(0, 120) {
            voiceWavePhase = (voiceWavePhase + 1) % 120
        }
    }
}

internal fun StockChatPage.stopVoiceWaveAnimation() {
    voiceWaveTimer?.cancel()
    voiceWaveTimer = null
}

internal fun StockChatPage.startVoiceInput() {
    if (isSending) {
        bridgeModule.toast("请等待当前回答完成")
        return
    }
    if (!speechRecognitionService.isConfigured) {
        bridgeModule.toast("MiMo 语音 Key 尚未配置")
        return
    }
    if (inputRefReady) {
        inputRef.view?.blur()
    }
    resetKeyboardState()
    voiceRequestToken += 1
    val currentVoiceToken = voiceRequestToken
    voicePressReleaseRequested = false
    voiceInputState = VoiceInputState.STARTING
    setTimeout(30_000) {
        if (
            currentVoiceToken == voiceRequestToken &&
            voiceInputState == VoiceInputState.STARTING
        ) {
            cancelVoiceInput()
            bridgeModule.toast("麦克风启动超时，请检查权限后重试")
        }
    }
    bridgeModule.startVoiceRecording { result ->
        if (currentVoiceToken != voiceRequestToken) {
            bridgeModule.cancelVoiceRecording()
            return@startVoiceRecording
        }
        if (result?.optInt("success") == 1) {
            voiceInputState = VoiceInputState.RECORDING
            if (voicePressReleaseRequested || !voicePressActive) {
                voicePressReleaseRequested = false
                stopVoiceInput()
                return@startVoiceRecording
            }
            setTimeout(30_000) {
                if (
                    currentVoiceToken == voiceRequestToken &&
                    voiceInputState == VoiceInputState.RECORDING
                ) {
                    voicePressActive = false
                    voicePressCanceled = false
                    stopVoiceWaveAnimation()
                    stopVoiceInput()
                }
            }
        } else {
            voicePressActive = false
            voicePressCanceled = false
            voicePressReleaseRequested = false
            stopVoiceWaveAnimation()
            voiceInputState = VoiceInputState.IDLE
            bridgeModule.toast(
                result?.optString("errorMessage").orEmpty().ifBlank {
                    "无法启动麦克风，请检查录音权限"
                }
            )
        }
    }
}

internal fun StockChatPage.stopVoiceInput() {
    if (voiceInputState != VoiceInputState.RECORDING) {
        return
    }
    val currentVoiceToken = voiceRequestToken
    voicePressReleaseRequested = false
    voiceInputState = VoiceInputState.TRANSCRIBING
    bridgeModule.stopVoiceRecording { result ->
        if (currentVoiceToken != voiceRequestToken) {
            return@stopVoiceRecording
        }
        val audioBase64 = result?.optString("audioBase64").orEmpty()
        if (result?.optInt("success") != 1 || audioBase64.isBlank()) {
            voiceInputState = VoiceInputState.IDLE
            bridgeModule.toast(
                result?.optString("errorMessage").orEmpty().ifBlank {
                    "没有录到有效语音，请重试"
                }
            )
            return@stopVoiceRecording
        }
        runCatching {
            speechRecognitionService.transcribe(
                audioBase64 = audioBase64,
                mimeType = result.optString("mimeType").ifBlank { "audio/wav" },
            ) { recognitionResult ->
                if (currentVoiceToken != voiceRequestToken) {
                    return@transcribe
                }
                voiceInputState = VoiceInputState.IDLE
                when (recognitionResult) {
                    is SpeechRecognitionResult.Success -> {
                        val recognizedText = recognitionResult.text.trim()
                        if (recognizedText.isEmpty()) {
                            bridgeModule.toast("没有识别到有效内容，请重试")
                        } else {
                            fillComposerWithVoiceResult(recognizedText)
                        }
                    }
                    is SpeechRecognitionResult.Failure -> {
                        bridgeModule.toast(recognitionResult.message)
                    }
                }
            }
        }.onFailure {
            if (currentVoiceToken == voiceRequestToken) {
                voiceInputState = VoiceInputState.IDLE
                bridgeModule.toast("MiMo 语音识别暂时不可用，请稍后重试")
            }
        }
    }
}

internal fun StockChatPage.cancelVoiceInput() {
    voiceRequestToken += 1
    stopVoiceWaveAnimation()
    if (
        voiceInputState == VoiceInputState.STARTING ||
        voiceInputState == VoiceInputState.RECORDING
    ) {
        bridgeModule.cancelVoiceRecording()
    }
    voicePressActive = false
    voicePressCanceled = false
    voicePressStartY = 0f
    voicePressReleaseRequested = false
    voiceInputState = VoiceInputState.IDLE
}
