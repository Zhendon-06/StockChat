package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.base.BridgeModule
import com.guet.liang.stockchat.base.bridgeModule
import com.guet.liang.stockchat.base.replaceNativeText
import com.guet.liang.stockchat.base.setTimeout

// 底部输入面板：输入框、展开/收缩、键盘跟随、图片选择与行数估算。

internal const val MAX_COMPOSER_TEXT_LENGTH = 300

internal const val MAX_INPUT_LINES = 5

// 已选图片条：面板顶部的横向缩略图列表

// 原生多行输入框：文本、占位、聚焦/键盘/回车事件

// 语音模式下替代输入框显示的提示文案

// 折叠态点击聚焦 / 语音模式按住说话的手势层

// 面板底部工具行：输入模式切换、模型选择、加号与发送按钮

// 输入区底部安全区留白

internal fun StockChatPage.focusComposer() {
    if (selectedHomeTab != HOME_TAB_CHAT) {
        return
    }
    composerFocused = true
    composerExpanded = true
    collapseComposerAfterSettle = false
    voiceMode = false
    conversationMenuOpen = false
    closeDrawer()
    focusTextInputAfterLayout()
}

// 点击/滑动输入框以外的区域：用户焦点离开输入，面板收缩还给页面空间。
// 键盘在场时先收键盘，收缩挂起到回落动画播完再播（两个动画在同一批节点上
// 并发会互相打断）；键盘静止时立即收缩
internal fun StockChatPage.handleBlankAreaTap() {
    if (voiceMode || voicePressActive) {
        return
    }
    val keyboardWasUp = keyboardVisible || keyboardHeight > 0f
    if (composerFocused || keyboardWasUp) {
        if (inputRefReady) {
            inputRef.view?.blur()
        }
        resetKeyboardState()
    }
    // 输入框有内容时保持展开：收缩态按单行布局排版，多行文本会挤乱
    if (inputText.isNotEmpty()) {
        return
    }
    if (keyboardWasUp || keyboardDropSettling) {
        // 回落窗口结束时由 beginComposerDockSettle 的定时器执行收缩
        collapseComposerAfterSettle = true
    } else {
        collapseComposer()
    }
}

internal fun StockChatPage.resetKeyboardState() {
    val keyboardWasUp = keyboardHeight > 0f
    keyboardHeight = 0f
    keyboardVisible = false
    if (keyboardWasUp) {
        beginComposerDockSettle()
    }
    composerFocused = false
}

// 键盘开始落下时调用：标记回落窗口（keyboardDropSettling）。窗口内主页
// 内容不回挂（避免挂载大子树拖慢回落）、空白点击不触发面板收缩（避免
// 收缩动画打断同节点的回落动画），并调度 nudge 兜底归位
internal fun StockChatPage.beginComposerDockSettle() {
    keyboardDropSettling = true
    val generation = ++dockSettleGeneration
    setTimeout(((keyboardAnimDuration + 0.03f) * 1000).toInt()) {
        if (generation == dockSettleGeneration) {
            keyboardDropSettling = false
            // 回落期间挂起的空白交互收缩，此刻键盘动画已结束，可安全播放
            if (collapseComposerAfterSettle) {
                collapseComposerAfterSettle = false
                collapseComposer()
            }
        }
    }
    scheduleComposerDockResync()
}

// 键盘落底动画应结束的时刻，再强制同步一次跟随键盘的容器位置：
// composerDockNudge 自增会让 bottom 产生 0.1px 的无感变化，以一次无动画的
// 属性更新把原生侧位置拉回正确值——兜底修复回落动画被打断后面板悬停的问题
internal fun StockChatPage.scheduleComposerDockResync() {
    setTimeout(((keyboardAnimDuration + 0.12f) * 1000).toInt()) {
        if (keyboardHeight <= 0f && !keyboardVisible) {
            composerDockNudge += 1
        }
    }
}

internal fun StockChatPage.focusTextInputAfterLayout() {
    updateInputLineMetrics(inputText)
    setTimeout(0) {
        if (composerFocused && !voiceMode && inputRefReady) {
            inputRef.view?.replaceNativeText(inputText)
            inputRef.view?.focus()
        }
    }
}

internal fun StockChatPage.collapseComposer() {
    composerExpanded = false
    val generation = ++composerInputLayoutResyncGeneration
    setTimeout(260) {
        if (generation == composerInputLayoutResyncGeneration && composerHasCollapsedInput()) {
            composerInputLayoutNudge += 1
        }
    }
}

// 输入面板处于展开非语音态时，超出单行的部分行数，用于撑高输入框和面板
internal fun StockChatPage.composerExtraInputLines(): Int {
    return if (composerExpanded && !voiceMode) {
        (inputLineCount - 1).coerceAtLeast(0)
    } else {
        0
    }
}

internal fun StockChatPage.updateInputLineMetrics(text: String) {
    val metrics = layoutMetrics
    // 展开态输入框可用宽 = 页宽 - 面板左右外边距 18*2 - 输入框左右内边距 20*2
    val availableWidth = (pagerData.pageViewWidth - metrics.dp(76f)) * 0.97f
    inputLineCount = estimateWrappedLineCount(text, metrics.dp(17f), availableWidth).coerceIn(1, MAX_INPUT_LINES)
}

internal fun StockChatPage.resetInputLineMetrics() {
    inputLineCount = 1
}

internal fun StockChatPage.openImagePicker() {
    if (imagePickerOpen) {
        bridgeModule.toast("图片选择器已打开")
        return
    }
    val remainingCount = BridgeModule.MAX_IMAGE_SELECTION_COUNT - selectedImageCount
    if (remainingCount <= 0) {
        bridgeModule.toast("最多选择 9 张图片")
        return
    }
    imagePickerOpen = true
    if (inputRefReady) {
        inputRef.view?.blur()
    }
    resetKeyboardState()
    bridgeModule.pickImages(remainingCount) pickerResult@{ result ->
        resetKeyboardState()
        imagePickerOpen = false
        if (result == null) {
            bridgeModule.toast("图片选择暂时不可用")
            return@pickerResult
        }
        if (result.optInt("cancelled", 0) == 1) {
            return@pickerResult
        }
        if (result.optInt("success", 0) != 1) {
            bridgeModule.toast(result.optString("errorMessage").ifBlank { "图片选择失败，请稍后重试" })
            return@pickerResult
        }
        appendPickedImages(result)
    }
}

internal fun StockChatPage.removeSelectedImage(imageUri: String) {
    val imageIndex = selectedImagePreviews.indexOf(imageUri)
    if (imageIndex < 0) {
        return
    }
    selectedImagePreviews.removeAt(imageIndex)
    selectedImages.removeAt(imageIndex)
    if (imageIndex < selectedImagePayloads.size) {
        selectedImagePayloads.removeAt(imageIndex)
    }
    selectedImageCount = selectedImagePreviews.size
}

private fun StockChatPage.composerHasCollapsedInput(): Boolean = !composerExpanded && !voiceMode && selectedImageCount == 0

private fun StockChatPage.appendPickedImages(result: com.tencent.kuikly.core.nvi.serialization.json.JSONObject) {
    val imageArray = result.optJSONArray("images")
    val previewImageArray = result.optJSONArray("previewImages")
    val newImagePreviews = mutableListOf<String>()
    val newImagePayloads = mutableListOf<String>()
    if (imageArray != null) {
        for (index in 0 until imageArray.length()) {
            if (selectedImagePreviews.size + newImagePreviews.size >= BridgeModule.MAX_IMAGE_SELECTION_COUNT) {
                break
            }
            val imagePayload = imageArray.optString(index).orEmpty().trim()
            val imagePreview = previewImageArray?.optString(index).orEmpty().trim().ifBlank { imagePayload }
            if (imagePayload.isNotEmpty() && isNewImagePreview(imagePreview, newImagePreviews)) {
                newImagePreviews.add(imagePreview)
                newImagePayloads.add(imagePayload)
            }
        }
    }
    selectedImagePreviews.addAll(newImagePreviews)
    selectedImagePayloads.addAll(newImagePayloads)
    selectedImages.addAll(newImagePreviews)
    selectedImageCount = selectedImagePreviews.size
    if (newImagePreviews.isEmpty()) {
        bridgeModule.toast("没有选择新的图片")
    } else {
        if (result.optInt("truncated", 0) == 1) {
            bridgeModule.toast("已保留前 9 张图片")
        }
    }
}

private fun StockChatPage.isNewImagePreview(preview: String, newPreviews: List<String>): Boolean =
    preview.isNotEmpty() && preview !in selectedImagePreviews && preview !in newPreviews
