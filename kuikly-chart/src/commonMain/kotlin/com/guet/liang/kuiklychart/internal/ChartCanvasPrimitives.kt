package com.guet.liang.kuiklychart.internal

import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.views.CanvasContext
import com.tencent.kuikly.core.views.TextAlign
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sqrt

// Canvas 基础绘制：背景、线段、虚线、文本、圆、矩形与圆角柱。

internal fun drawBackground(context: CanvasContext, width: Float, height: Float, color: Color) {
    drawRect(context, ChartRect(0f, 0f, width, height), color)
}

internal fun drawLine(
    context: CanvasContext,
    startHorizontal: Float,
    startVertical: Float,
    endHorizontal: Float,
    endVertical: Float,
) {
    context.beginPath()
    context.moveTo(startHorizontal, startVertical)
    context.lineTo(endHorizontal, endVertical)
    context.stroke()
}

internal fun drawDashedLine(
    context: CanvasContext,
    startHorizontal: Float,
    startVertical: Float,
    endHorizontal: Float,
    endVertical: Float,
    pattern: List<Float>,
) {
    val validPattern = pattern.filter { it > 0f }
    if (validPattern.isEmpty()) {
        drawLine(context, startHorizontal, startVertical, endHorizontal, endVertical)
        return
    }
    val horizontalDelta = endHorizontal - startHorizontal
    val verticalDelta = endVertical - startVertical
    val lineLength = sqrt(horizontalDelta * horizontalDelta + verticalDelta * verticalDelta)
    if (lineLength <= 0f) {
        return
    }
    val horizontalUnit = horizontalDelta / lineLength
    val verticalUnit = verticalDelta / lineLength
    var travelled = 0f
    var patternIndex = 0
    var shouldDraw = true
    while (travelled < lineLength) {
        val segmentLength = min(validPattern[patternIndex % validPattern.size], lineLength - travelled)
        if (shouldDraw) {
            drawLine(
                context,
                startHorizontal + horizontalUnit * travelled,
                startVertical + verticalUnit * travelled,
                startHorizontal + horizontalUnit * (travelled + segmentLength),
                startVertical + verticalUnit * (travelled + segmentLength),
            )
        }
        travelled += segmentLength
        patternIndex += 1
        shouldDraw = !shouldDraw
    }
}

internal fun drawText(
    context: CanvasContext,
    text: String,
    anchorHorizontal: Float,
    baselineVertical: Float,
    alignment: TextAlign,
) {
    val textWidth = if (alignment == TextAlign.LEFT) 0f else context.measureText(text).width
    val drawHorizontal = when (alignment) {
        TextAlign.LEFT -> anchorHorizontal
        TextAlign.CENTER -> anchorHorizontal - textWidth / 2f
        TextAlign.RIGHT -> anchorHorizontal - textWidth
    }
    context.textAlign(TextAlign.LEFT)
    context.fillText(text, drawHorizontal, baselineVertical)
}

internal fun drawCircle(
    context: CanvasContext,
    centerHorizontal: Float,
    centerVertical: Float,
    radius: Float,
    color: Color,
) {
    if (radius <= 0f) {
        return
    }
    context.beginPath()
    context.arc(
        centerHorizontal,
        centerVertical,
        radius,
        0f,
        (PI * 2.0).toFloat(),
        false,
    )
    context.closePath()
    context.fillStyle(color)
    context.fill()
}

internal fun drawRect(context: CanvasContext, rect: ChartRect, color: Color) {
    context.beginPath()
    context.moveTo(rect.left, rect.top)
    context.lineTo(rect.right, rect.top)
    context.lineTo(rect.right, rect.bottom)
    context.lineTo(rect.left, rect.bottom)
    context.closePath()
    context.fillStyle(color)
    context.fill()
}

internal fun drawRoundedRect(
    context: CanvasContext,
    rect: ChartRect,
    radius: Float,
    color: Color,
) {
    val safeRadius = radius.coerceIn(0f, min(rect.width, rect.height) / 2f)
    context.beginPath()
    context.moveTo(rect.left + safeRadius, rect.top)
    context.lineTo(rect.right - safeRadius, rect.top)
    context.quadraticCurveTo(rect.right, rect.top, rect.right, rect.top + safeRadius)
    context.lineTo(rect.right, rect.bottom - safeRadius)
    context.quadraticCurveTo(rect.right, rect.bottom, rect.right - safeRadius, rect.bottom)
    context.lineTo(rect.left + safeRadius, rect.bottom)
    context.quadraticCurveTo(rect.left, rect.bottom, rect.left, rect.bottom - safeRadius)
    context.lineTo(rect.left, rect.top + safeRadius)
    context.quadraticCurveTo(rect.left, rect.top, rect.left + safeRadius, rect.top)
    context.closePath()
    context.fillStyle(color)
    context.fill()
}

internal fun drawRoundedBar(
    context: CanvasContext,
    rect: ChartRect,
    radius: Float,
    positive: Boolean,
    color: Color,
) {
    if (rect.width <= 0f || rect.height <= 0f) {
        return
    }
    val safeRadius = radius.coerceIn(0f, min(rect.width / 2f, rect.height))
    context.beginPath()
    if (positive) {
        context.moveTo(rect.left, rect.bottom)
        context.lineTo(rect.left, rect.top + safeRadius)
        context.quadraticCurveTo(rect.left, rect.top, rect.left + safeRadius, rect.top)
        context.lineTo(rect.right - safeRadius, rect.top)
        context.quadraticCurveTo(rect.right, rect.top, rect.right, rect.top + safeRadius)
        context.lineTo(rect.right, rect.bottom)
    } else {
        context.moveTo(rect.left, rect.top)
        context.lineTo(rect.right, rect.top)
        context.lineTo(rect.right, rect.bottom - safeRadius)
        context.quadraticCurveTo(rect.right, rect.bottom, rect.right - safeRadius, rect.bottom)
        context.lineTo(rect.left + safeRadius, rect.bottom)
        context.quadraticCurveTo(rect.left, rect.bottom, rect.left, rect.bottom - safeRadius)
    }
    context.closePath()
    context.fillStyle(color)
    context.fill()
}
