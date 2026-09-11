package com.guet.liang.stockchat.ui

// Markdown 表格的纯布局计算：按单元格文本估算列宽，决定铺满还是横向滚动。

/** Horizontal alignment declared by a GFM separator cell such as `:---:`. */
internal enum class MarkdownTableAlign { LEFT, CENTER, RIGHT }

/** Column widths plus whether the table overflows the available width and needs a horizontal scroller. */
internal data class MarkdownTableLayout(
    val columnWidths: List<Float>,
    val scrollable: Boolean,
) {
    val totalWidth: Float get() = columnWidths.sum()
}

/** Thumb geometry of the horizontal scroll indicator drawn under a scrollable table. */
internal data class ScrollIndicatorThumb(val left: Float, val width: Float)

/** Computes column sizing and scroll-indicator geometry for Markdown tables. */
internal object MarkdownTableLayoutCalculator {
    /** Maps content offset to a thumb on a track of [trackWidth]; the thumb never shrinks below [minThumb]. */
    fun scrollIndicator(
        viewportWidth: Float,
        contentWidth: Float,
        offsetX: Float,
        trackWidth: Float,
        minThumb: Float,
    ): ScrollIndicatorThumb {
        if (contentWidth <= viewportWidth || viewportWidth <= 0f || trackWidth <= 0f) {
            return ScrollIndicatorThumb(0f, trackWidth)
        }
        val width = (trackWidth * viewportWidth / contentWidth).coerceIn(minOf(minThumb, trackWidth), trackWidth)
        val maxOffset = contentWidth - viewportWidth
        val progress = (offsetX / maxOffset).coerceIn(0f, 1f)
        return ScrollIndicatorThumb(left = (trackWidth - width) * progress, width = width)
    }

    /**
     * @param cellTexts plain text per row per column (header first); ragged rows are tolerated
     * @param fontSize body font size; the header row is measured slightly wider for its bold weight
     * @param horizontalPadding padding applied on both sides of every cell
     */
    fun layout(
        cellTexts: List<List<String>>,
        columnsCount: Int,
        fontSize: Float,
        horizontalPadding: Float,
        availableWidth: Float,
    ): MarkdownTableLayout {
        val minColumn = fontSize * MIN_COLUMN_EM + horizontalPadding * 2
        val maxColumn = fontSize * MAX_COLUMN_EM + horizontalPadding * 2
        val natural = List(columnsCount) { column ->
            val widest = cellTexts.withIndex().maxOfOrNull { (rowIndex, row) ->
                val text = row.getOrNull(column).orEmpty()
                val weight = if (rowIndex == 0) HEADER_WIDTH_FACTOR else 1f
                estimateTextWidth(text, fontSize) * weight
            } ?: 0f
            (widest + horizontalPadding * 2).coerceIn(minColumn, maxColumn)
        }
        val total = natural.sum()
        if (availableWidth <= 0f || total > availableWidth) {
            return MarkdownTableLayout(natural, scrollable = true)
        }
        val ratio = availableWidth / total
        return MarkdownTableLayout(natural.map { it * ratio }, scrollable = false)
    }

    /** Rough intrinsic width: full-width glyphs take one em, Latin letters and digits a bit over half. */
    fun estimateTextWidth(text: String, fontSize: Float): Float {
        var width = 0f
        text.forEach { char ->
            width += when {
                char.code >= FULL_WIDTH_START -> fontSize
                char.isDigit() || char.isLetter() -> fontSize * LATIN_EM
                char == ' ' -> fontSize * SPACE_EM
                else -> fontSize * PUNCTUATION_EM
            }
        }
        return width
    }

    /** Parses `| :--- | ---: | :---: |`; missing or malformed cells default to left alignment. */
    fun parseAlignments(separatorLine: String, columnsCount: Int): List<MarkdownTableAlign> {
        val tokens = separatorLine.trim().trim('|').split('|').map(String::trim)
        return List(columnsCount) { index ->
            val token = tokens.getOrNull(index).orEmpty()
            when {
                token.startsWith(':') && token.endsWith(':') -> MarkdownTableAlign.CENTER
                token.endsWith(':') -> MarkdownTableAlign.RIGHT
                else -> MarkdownTableAlign.LEFT
            }
        }
    }

    private const val FULL_WIDTH_START = 0x2E80
    private const val LATIN_EM = 0.58f
    private const val SPACE_EM = 0.3f
    private const val PUNCTUATION_EM = 0.66f
    private const val HEADER_WIDTH_FACTOR = 1.06f
    private const val MIN_COLUMN_EM = 3f
    private const val MAX_COLUMN_EM = 14f
}
