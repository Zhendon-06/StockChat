package com.guet.liang.stockchat.ui

import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ComposeAttr
import com.tencent.kuikly.core.base.ComposeEvent
import com.tencent.kuikly.core.base.ComposeView
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.ViewRef
import com.tencent.kuikly.core.layout.Frame
import com.tencent.kuikly.core.directives.velse
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.DivView
import com.tencent.kuikly.core.views.RichText
import com.tencent.kuikly.core.views.SelectableOption
import com.tencent.kuikly.core.views.SelectionType
import com.tencent.kuikly.core.views.Span
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View
import com.tencent.kuiklybase.KuiklyMarkdown
import com.tencent.kuiklybase.config.FontWeight
import com.tencent.kuiklybase.config.MarkdownColors
import com.tencent.kuiklybase.config.MarkdownConfig
import com.tencent.kuiklybase.config.MarkdownTypography
import com.tencent.kuiklybase.config.TextStyleConfig

internal class SelectableMarkdownView(
    private val source: String,
    private val fallbackText: String,
    private val scale: Float,
    private val selectionEnabled: Boolean,
    private val onCopySelection: (String) -> Unit,
) : ComposeView<ComposeAttr, ComposeEvent>() {

    private var copyMenuVisible by observable(false)
    private var copyMenuLeft by observable(0f)
    private var copyMenuTop by observable(0f)
    private var selectableContainerRef: ViewRef<DivView>? = null

    override fun createAttr(): ComposeAttr = ComposeAttr()

    override fun createEvent(): ComposeEvent = ComposeEvent()

    override fun body(): ViewBuilder {
        val context = this
        return {
            attr {
                alignSelfStretch()
                positionRelative()
                overflow(false)
                marginBottom(9f * context.scale)
            }

            View {
                ref {
                    context.selectableContainerRef = it
                }
                attr {
                    alignSelfStretch()
                    selectable(
                        if (context.selectionEnabled) {
                            SelectableOption.ENABLE
                        } else {
                            SelectableOption.DISABLE
                        },
                    )
                    selectionColor(StockChatTheme.accent)
                }
                event {
                    longPress {
                        if (context.selectionEnabled && it.state == "start") {
                            context.copyMenuVisible = false
                            context.selectableContainerRef?.view?.createSelection(
                                it.x,
                                it.y,
                                SelectionType.WORD,
                            )
                        }
                    }
                    selectStart {
                        context.showCopyMenu(it)
                    }
                    selectChange {
                        context.copyMenuVisible = false
                    }
                    selectEnd {
                        context.showCopyMenu(it)
                    }
                    selectCancel {
                        context.copyMenuVisible = false
                    }
                }

                if (context.source.isBlank()) {
                    RichText {
                        Span {
                            text(context.fallbackText)
                            fontSize(StockChatTheme.chatTextSizeSp * context.scale)
                            lineHeight(StockChatTheme.chatTextSizeSp * 1.48f * context.scale)
                            color(StockChatTheme.chatTextColor)
                        }
                    }
                } else {
                    vif({ StockChatTheme.renderRevision % 2 == 0 }) {
                        context.Markdown(this)
                    }
                    velse {
                        context.Markdown(this)
                    }
                }
            }

            View {
                attr {
                    width(COPY_MENU_WIDTH * context.scale)
                    height(COPY_MENU_HEIGHT * context.scale)
                    absolutePosition(
                        top = context.copyMenuTop,
                        left = context.copyMenuLeft,
                    )
                    borderRadius(9f * context.scale)
                    backgroundColor(Color(0xFF252826))
                    opacity(if (context.copyMenuVisible) 1f else 0f)
                    touchEnable(context.copyMenuVisible)
                    zIndex(100)
                    allCenter()
                }
                event {
                    click {
                        context.copySelection()
                    }
                }
                Text {
                    attr {
                        text("复制")
                        fontSize(14f * context.scale)
                        color(Color.WHITE)
                    }
                }
            }
        }
    }

    private fun showCopyMenu(selectionFrame: Frame) {
        val menuWidth = COPY_MENU_WIDTH * scale
        val menuHeight = COPY_MENU_HEIGHT * scale
        val menuGap = COPY_MENU_GAP * scale
        val availableWidth = frame.width
        val centeredLeft = selectionFrame.x + selectionFrame.width / 2f - menuWidth / 2f
        copyMenuLeft = if (availableWidth > menuWidth) {
            centeredLeft.coerceIn(0f, availableWidth - menuWidth)
        } else {
            0f
        }
        copyMenuTop = if (selectionFrame.y >= menuHeight + menuGap) {
            selectionFrame.y - menuHeight - menuGap
        } else {
            selectionFrame.y + selectionFrame.height + menuGap
        }
        copyMenuVisible = true
    }

    private fun copySelection() {
        selectableContainerRef?.view?.getSelection { selection ->
            val selectedText = selection.joinToString(separator = "")
            if (selectedText.isNotBlank()) {
                onCopySelection(selectedText)
            }
            selectableContainerRef?.view?.clearSelection()
            copyMenuVisible = false
        }
    }

    private fun Markdown(container: ViewContainer<*, *>) {
        val textSize = StockChatTheme.chatTextSizeSp * scale
        val textColor = StockChatTheme.chatTextColorArgb
        val markdownSource = source
        val config = markdownConfig(textSize, textColor)
        with(container) {
            KuiklyMarkdown(
                content = markdownSource,
                config = config,
            )
        }
    }

    private fun markdownConfig(textSize: Float, textColor: Long): MarkdownConfig {
        val dark = StockChatTheme.isDark
        fun style(
            sizeMultiplier: Float = 1f,
            weight: FontWeight = FontWeight.Normal,
            lineHeightMultiplier: Float = 1.5f,
        ) = TextStyleConfig(
            fontSize = textSize * sizeMultiplier,
            fontWeight = weight,
            lineHeight = textSize * sizeMultiplier * lineHeightMultiplier,
        )
        return MarkdownConfig(
            colors = MarkdownColors(
                text = textColor,
                codeBackground = if (dark) 0xFF252D29 else 0xFFF5F6F5,
                inlineCodeBackground = if (dark) 0xFF303A35 else 0xFFE8ECE9,
                dividerColor = if (dark) 0xFF414C46 else 0xFFD9DFDC,
                tableBackground = if (dark) 0xFF222925 else 0xFFF8FAF9,
                blockQuoteBar = if (dark) 0xFF35D1A2 else 0xFF13A87A,
                blockQuoteBackground = if (dark) 0xFF173B32 else 0xFFE8F7F1,
                linkColor = if (dark) 0xFF76B7FF else 0xFF1A73E8,
                codeText = textColor,
            ),
            typography = MarkdownTypography(
                text = style(),
                code = style(sizeMultiplier = 0.88f),
                inlineCode = style(sizeMultiplier = 0.88f),
                h1 = style(sizeMultiplier = 1.75f, weight = FontWeight.Bold, lineHeightMultiplier = 1.24f),
                h2 = style(sizeMultiplier = 1.55f, weight = FontWeight.Bold, lineHeightMultiplier = 1.28f),
                h3 = style(sizeMultiplier = 1.35f, weight = FontWeight.Bold, lineHeightMultiplier = 1.32f),
                h4 = style(sizeMultiplier = 1.2f, weight = FontWeight.Bold, lineHeightMultiplier = 1.36f),
                h5 = style(sizeMultiplier = 1.1f, weight = FontWeight.Bold, lineHeightMultiplier = 1.4f),
                h6 = style(weight = FontWeight.Bold),
                quote = style(),
                paragraph = style(),
                ordered = style(),
                bullet = style(),
                list = style(),
                table = style(sizeMultiplier = 0.88f),
                textLink = style(),
            ),
            codeHighlightDarkTheme = dark,
        )
    }

    private companion object {
        const val COPY_MENU_WIDTH = 68f
        const val COPY_MENU_HEIGHT = 38f
        const val COPY_MENU_GAP = 8f
    }
}

internal fun ViewContainer<*, *>.SelectableMarkdownContent(
    source: String,
    fallbackText: String,
    scale: Float,
    selectionEnabled: Boolean,
    onCopySelection: (String) -> Unit,
) {
    addChild(
        SelectableMarkdownView(
            source = source,
            fallbackText = fallbackText,
            scale = scale,
            selectionEnabled = selectionEnabled,
            onCopySelection = onCopySelection,
        ),
    ) {}
}
