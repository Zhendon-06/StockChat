@file:Suppress("MagicNumber")
package com.guet.liang.stockchat.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MarkdownTableLayoutTest {
    private val header = listOf("指数", "代码", "现价", "涨跌", "涨跌幅", "昨收", "今开", "最高", "最低", "成交量")
    private val row = listOf("上证指数", "000001", "3933.48", "-18.03", "-0.46%", "3951.51", "3939.09", "3949.25", "3927.34", "50.84亿")

    @Test
    fun wideTableBecomesScrollableWithContentSizedColumns() {
        val layout = MarkdownTableLayoutCalculator.layout(
            listOf(header, row), columnsCount = 10, fontSize = 13f, horizontalPadding = 10f, availableWidth = 340f,
        )
        assertTrue(layout.scrollable)
        assertTrue(layout.totalWidth > 340f)
        assertEquals(10, layout.columnWidths.size)
        // "上证指数" is four full-width glyphs, so its column is wider than the six-digit code column.
        assertTrue(layout.columnWidths[0] > layout.columnWidths[1])
        layout.columnWidths.forEach { width ->
            assertTrue(width >= 13f * 3 + 20f)
            assertTrue(width <= 13f * 14 + 20f)
        }
    }

    @Test
    fun narrowTableStretchesToFillTheAvailableWidth() {
        val layout = MarkdownTableLayoutCalculator.layout(
            listOf(listOf("名称", "价格"), listOf("茅台", "1500")),
            columnsCount = 2, fontSize = 13f, horizontalPadding = 10f, availableWidth = 340f,
        )
        assertFalse(layout.scrollable)
        assertEquals(340f, layout.totalWidth, absoluteTolerance = 0.01f)
    }

    @Test
    fun unknownWidthAndRaggedRowsFallBackToNaturalColumns() {
        val layout = MarkdownTableLayoutCalculator.layout(
            listOf(listOf("A", "B", "C"), listOf("only one")),
            columnsCount = 3, fontSize = 13f, horizontalPadding = 10f, availableWidth = 0f,
        )
        assertTrue(layout.scrollable)
        assertEquals(3, layout.columnWidths.size)
        assertEquals(layout.columnWidths[1], layout.columnWidths[2])
    }

    @Test
    fun veryLongCellIsCappedSoItWrapsInsteadOfGrowingForever() {
        val long = "这是一段非常长的说明文字".repeat(6)
        val layout = MarkdownTableLayoutCalculator.layout(listOf(listOf("说明"), listOf(long)), 1, 13f, 10f, 340f)
        assertEquals(340f, layout.columnWidths.single(), absoluteTolerance = 0.01f)
        assertFalse(layout.scrollable)
    }

    @Test
    fun fullWidthGlyphsMeasureWiderThanLatinCharacters() {
        val cjk = MarkdownTableLayoutCalculator.estimateTextWidth("成交量", 10f)
        val latin = MarkdownTableLayoutCalculator.estimateTextWidth("abc", 10f)
        assertEquals(30f, cjk, absoluteTolerance = 0.01f)
        assertTrue(latin < cjk)
    }

    @Test
    fun separatorAlignmentsAreParsedAndDefaultToLeft() {
        val aligns = MarkdownTableLayoutCalculator.parseAlignments("| :--- | ---: | :---: | --- |", 5)
        val expected = listOf(
            MarkdownTableAlign.LEFT, MarkdownTableAlign.RIGHT, MarkdownTableAlign.CENTER, MarkdownTableAlign.LEFT, MarkdownTableAlign.LEFT,
        )
        assertEquals(expected, aligns)
        assertEquals(listOf(MarkdownTableAlign.LEFT, MarkdownTableAlign.LEFT), MarkdownTableLayoutCalculator.parseAlignments("", 2))
    }

    @Test
    fun scrollIndicatorThumbTracksOffsetAndKeepsMinimumSize() {
        val start = MarkdownTableLayoutCalculator.scrollIndicator(
            viewportWidth = 300f, contentWidth = 900f, offsetX = 0f, trackWidth = 280f, minThumb = 24f,
        )
        assertEquals(0f, start.left, absoluteTolerance = 0.01f)
        assertEquals(280f / 3f, start.width, absoluteTolerance = 0.01f)
        val end = MarkdownTableLayoutCalculator.scrollIndicator(300f, 900f, offsetX = 600f, trackWidth = 280f, minThumb = 24f)
        assertEquals(280f - end.width, end.left, absoluteTolerance = 0.01f)
        val overscroll = MarkdownTableLayoutCalculator.scrollIndicator(300f, 900f, offsetX = 5000f, trackWidth = 280f, minThumb = 24f)
        assertEquals(end, overscroll)
        val tiny = MarkdownTableLayoutCalculator.scrollIndicator(300f, 30000f, offsetX = 0f, trackWidth = 280f, minThumb = 24f)
        assertEquals(24f, tiny.width, absoluteTolerance = 0.01f)
        val fits = MarkdownTableLayoutCalculator.scrollIndicator(300f, 200f, offsetX = 0f, trackWidth = 280f, minThumb = 24f)
        assertEquals(ScrollIndicatorThumb(0f, 280f), fits)
    }
}
