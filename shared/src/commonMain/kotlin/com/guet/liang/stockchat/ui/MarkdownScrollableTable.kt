package com.guet.liang.stockchat.ui

import com.tencent.kuikly.core.base.ComposeAttr
import com.tencent.kuikly.core.base.ComposeEvent
import com.tencent.kuikly.core.base.ComposeView
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.CaptureRule
import com.tencent.kuikly.core.base.attr.CaptureRuleDirection
import com.tencent.kuikly.core.base.event.layoutFrameDidChange
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.View
import com.tencent.kuiklybase.annotator.StyledTextSegment
import com.tencent.kuiklybase.annotator.buildStyledTextSegments
import com.tencent.kuiklybase.components.MarkdownComponentModel
import com.tencent.kuiklybase.config.FontWeight
import com.tencent.kuiklybase.config.TextStyleConfig
import com.tencent.kuiklybase.elements.markdownRichText
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import kotlin.math.abs

// 替换 KuiklyMarkdown 默认表格：默认实现让每列 flex(1) 平分宽度，列多时文字互相覆盖。
// 这里按内容估算列宽，放得下就铺满消息宽度，放不下就整表横向滚动（与豆包等聊天应用一致）。

private class MarkdownTableRowData(val cells: List<List<StyledTextSegment>>, val isHeader: Boolean)

/** Renders one GFM table with content-sized columns inside a horizontal scroller when it overflows. */
internal class MarkdownScrollableTableView(
    private val model: MarkdownComponentModel,
    private val availableWidth: Float,
    private val scale: Float,
) : ComposeView<ComposeAttr, ComposeEvent>() {

    private val rows: List<MarkdownTableRowData>
    private val alignments: List<MarkdownTableAlign>
    private val layout: MarkdownTableLayout
    private val cellStyle: TextStyleConfig = model.typography.table
    private val horizontalPadding = CELL_HORIZONTAL_PADDING * scale
    private val verticalPadding = CELL_VERTICAL_PADDING * scale
    private var scrollerHeight by observable(0f)
    private var scrollOffsetX by observable(0f)
    private var viewportWidth by observable(availableWidth)

    init {
        val node = model.node
        val columnsCount = node.children.firstOrNull { it.type == GFMElementTypes.HEADER }
            ?.children?.count { it.type == GFMTokenTypes.CELL } ?: 0
        rows = node.children.filter { it.type == GFMElementTypes.HEADER || it.type == GFMElementTypes.ROW }.map { rowNode ->
            MarkdownTableRowData(
                cells = rowNode.children.filter { it.type == GFMTokenTypes.CELL }.map { cell ->
                    buildStyledTextSegments(model.content, cell, model.config, model.referenceLinkHandler)
                },
                isHeader = rowNode.type == GFMElementTypes.HEADER,
            )
        }
        val separator = node.children.firstOrNull { it.type == GFMTokenTypes.TABLE_SEPARATOR }
        alignments = MarkdownTableLayoutCalculator.parseAlignments(separator?.let(::rawText).orEmpty(), columnsCount)
        layout = MarkdownTableLayoutCalculator.layout(
            cellTexts = rows.map { row -> row.cells.map { segments -> segments.joinToString("") { it.text } } },
            columnsCount = columnsCount,
            fontSize = cellStyle.fontSize,
            horizontalPadding = horizontalPadding,
            availableWidth = availableWidth,
        )
        // First-frame estimate; the real height is read back once the rows are laid out.
        val lineHeight = cellStyle.lineHeight ?: (cellStyle.fontSize * DEFAULT_LINE_HEIGHT_FACTOR)
        scrollerHeight = rows.size * (lineHeight + verticalPadding * 2) + model.config.dimens.dividerThickness
    }

    private fun rawText(node: ASTNode): String =
        model.content.substring(node.startOffset.coerceIn(0, model.content.length), node.endOffset.coerceIn(0, model.content.length))

    override fun createAttr(): ComposeAttr = ComposeAttr()

    override fun createEvent(): ComposeEvent = ComposeEvent()

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            attr {
                alignSelfStretch()
                backgroundColor(ctx.model.config.colors.tableBackground)
                borderRadius(ctx.model.config.dimens.tableCornerSize)
                marginTop(ctx.model.config.padding.block)
                marginBottom(ctx.model.config.padding.block)
                overflow(true)
            }
            if (ctx.rows.isNotEmpty() && ctx.layout.scrollable) {
                Scroller {
                    attr {
                        alignSelfStretch()
                        height(ctx.scrollerHeight)
                        flexDirectionRow()
                        showScrollerIndicator(true)
                        bouncesEnable(false)
                        // 横向手势归表格，纵向手势继续交给消息列表
                        capture(CaptureRule.pan(CaptureRuleDirection.HORIZONTAL))
                    }
                    event {
                        scroll { params ->
                            ctx.scrollOffsetX = params.offsetX
                            if (params.viewWidth > 0f) ctx.viewportWidth = params.viewWidth
                        }
                    }
                    ctx.TableBody(this, measured = true)
                }
                ctx.ScrollIndicator(this)
            } else if (ctx.rows.isNotEmpty()) {
                ctx.TableBody(this, measured = false)
            }
        }
    }

    // 常驻的细指示条：滑块长度反映可见比例，随滑动移动，提示这张表还有更多列
    private fun ScrollIndicator(container: ViewContainer<*, *>) {
        val ctx = this
        with(container) {
            View {
                attr {
                    alignSelfStretch()
                    height(INDICATOR_HEIGHT * ctx.scale)
                    marginTop(INDICATOR_GAP * ctx.scale)
                    marginBottom(INDICATOR_GAP * ctx.scale)
                    paddingLeft(ctx.horizontalPadding)
                    paddingRight(ctx.horizontalPadding)
                }
                View {
                    attr {
                        flex(1f)
                        height(INDICATOR_HEIGHT * ctx.scale)
                        borderRadius(INDICATOR_HEIGHT * ctx.scale / 2f)
                        backgroundColor(ctx.model.config.colors.dividerColor)
                    }
                    View {
                        attr {
                            val thumb = MarkdownTableLayoutCalculator.scrollIndicator(
                                viewportWidth = ctx.viewportWidth,
                                contentWidth = ctx.layout.totalWidth,
                                offsetX = ctx.scrollOffsetX,
                                trackWidth = (ctx.viewportWidth - ctx.horizontalPadding * 2).coerceAtLeast(0f),
                                minThumb = INDICATOR_MIN_THUMB * ctx.scale,
                            )
                            absolutePosition(top = 0f, left = thumb.left)
                            width(thumb.width)
                            height(INDICATOR_HEIGHT * ctx.scale)
                            borderRadius(INDICATOR_HEIGHT * ctx.scale / 2f)
                            backgroundColor(StockChatTheme.accent)
                        }
                    }
                }
            }
        }
    }

    private fun TableBody(container: ViewContainer<*, *>, measured: Boolean) {
        val ctx = this
        with(container) {
            View {
                attr { width(ctx.layout.totalWidth) }
                if (measured) {
                    event {
                        layoutFrameDidChange { frame ->
                            if (frame.height > 0f && abs(frame.height - ctx.scrollerHeight) > HEIGHT_EPSILON) {
                                ctx.scrollerHeight = frame.height
                            }
                        }
                    }
                }
                ctx.rows.forEach { row ->
                    ctx.TableRow(this, row)
                    if (row.isHeader) {
                        View {
                            attr {
                                height(ctx.model.config.dimens.dividerThickness)
                                backgroundColor(ctx.model.config.colors.dividerColor)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun TableRow(container: ViewContainer<*, *>, row: MarkdownTableRowData) {
        val ctx = this
        val style = if (row.isHeader) cellStyle.copy(fontWeight = FontWeight.Bold) else cellStyle
        with(container) {
            View {
                attr { flexDirectionRow() }
                ctx.layout.columnWidths.forEachIndexed { column, width ->
                    View {
                        attr {
                            width(width)
                            padding(ctx.verticalPadding, ctx.horizontalPadding, ctx.verticalPadding, ctx.horizontalPadding)
                            when (ctx.alignments.getOrNull(column)) {
                                MarkdownTableAlign.CENTER -> alignItemsCenter()
                                MarkdownTableAlign.RIGHT -> alignItemsFlexEnd()
                                else -> alignItemsFlexStart()
                            }
                        }
                        row.cells.getOrNull(column)?.let { segments -> markdownRichText(segments, ctx.model.config, style) }
                    }
                }
            }
        }
    }

    private companion object {
        const val CELL_HORIZONTAL_PADDING = 10f
        const val CELL_VERTICAL_PADDING = 8f
        const val DEFAULT_LINE_HEIGHT_FACTOR = 1.5f
        const val HEIGHT_EPSILON = 0.5f
        const val INDICATOR_HEIGHT = 3f
        const val INDICATOR_GAP = 6f
        const val INDICATOR_MIN_THUMB = 24f
    }
}

/** Adds the scrollable table for a `markdownComponents(table = …)` override. */
internal fun ViewContainer<*, *>.MarkdownScrollableTable(model: MarkdownComponentModel, availableWidth: Float, scale: Float) {
    addChild(MarkdownScrollableTableView(model, availableWidth, scale)) {}
}
