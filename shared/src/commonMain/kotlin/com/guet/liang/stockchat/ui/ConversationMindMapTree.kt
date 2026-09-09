package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.controller.ArtifactController
import com.guet.liang.stockchat.controller.artifactController
import com.guet.liang.stockchat.controller.ArtifactResult

import com.guet.liang.stockchat.base.BasePager
import com.guet.liang.stockchat.base.closePage
import com.guet.liang.stockchat.model.ConversationMindMapArtifact
import com.guet.liang.stockchat.model.MermaidMindMapNode
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal fun ConversationMindMapArtifactPage.MindMapTree(
    container: ViewContainer<*, *>,
    nodes: List<MermaidMindMapNode>,
) {
    val ctx = this
    with(container) {
        View {
            attr {
                width(pagerData.pageViewWidth - 36f)
                alignSelfCenter()
                positionRelative()
            }
            View {
                attr {
                    width(2f)
                    absolutePosition(top = 0f, left = 16f, bottom = 20f)
                    backgroundColor(StockChatTheme.borderStrong)
                }
            }
            nodes.forEach { node ->
                ctx.MindMapNode(
                    container = this,
                    node = node,
                    depth = 0,
                )
            }
        }
    }
}

internal fun ConversationMindMapArtifactPage.RootNode(
    container: ViewContainer<*, *>,
    artifact: ConversationMindMapArtifact,
    node: MermaidMindMapNode,
) {
    with(container) {
        View {
            attr {
                width(pagerData.pageViewWidth - 36f)
                alignSelfCenter()
                borderRadius(22f)
                backgroundColor(StockChatTheme.accentSoft)
                border(Border(1f, BorderStyle.SOLID, Color(StockChatTheme.COLOR_FFC8EBDD)))
                padding(top = 17f, left = 18f, right = 18f, bottom = 17f)
            }
            Text {
                attr {
                    text(node.label.ifBlank { artifact.title.ifBlank { "当前会话" } })
                    fontSize(20f)
                    lineHeight(27f)
                    fontWeightBold()
                    lines(2)
                    color(StockChatTheme.textPrimary)
                }
            }
            Text {
                attr {
                    text("来源消息 ${artifact.sourceMessageCount} 条 · 本地产物 #${artifact.id}")
                    fontSize(12f)
                    color(StockChatTheme.textSecondary)
                    marginTop(8f)
                }
            }
            Text {
                attr {
                    text("Mermaid mindmap · 树状分支 ${node.children.size} 个")
                    fontSize(11f)
                    color(StockChatTheme.accent)
                    marginTop(5f)
                }
            }
        }
    }
}

internal fun ConversationMindMapArtifactPage.EmptyBranches(container: ViewContainer<*, *>) {
    with(container) {
        View {
            attr {
                width(pagerData.pageViewWidth - 36f)
                alignSelfCenter()
                height(96f)
                borderRadius(18f)
                backgroundColor(StockChatTheme.surface)
                themedBorder()
                allCenter()
                marginTop(10f)
            }
            Text {
                attr {
                    text("该会话暂时没有可展开的分支")
                    fontSize(14f)
                    color(StockChatTheme.textSecondary)
                }
            }
        }
    }
}

internal fun ConversationMindMapArtifactPage.MindMapNode(
    container: ViewContainer<*, *>,
    node: MermaidMindMapNode,
    depth: Int,
) {
    val ctx = this
    val leftInset = (depth * NODE_INDENT).coerceAtMost(MAX_NODE_INDENT)
    with(container) {
        View {
            attr {
                width((pagerData.pageViewWidth - 36f - leftInset).coerceAtLeast(1f))
                alignSelfFlexStart()
                marginLeft(leftInset)
                flexDirectionRow()
                alignItemsFlexStart()
                marginTop(8f)
            }
            View {
                attr {
                    width(NODE_CONNECTOR_WIDTH)
                    alignSelfStretch()
                    positionRelative()
                }
                View {
                    attr {
                        absolutePosition(top = 14f, left = 10f)
                        size(12f, 12f)
                        borderRadius(6f)
                        backgroundColor(if (depth == 0) StockChatTheme.accent else StockChatTheme.borderStrong)
                        allCenter()
                        zIndex(1)
                    }
                }
                View {
                    attr {
                        height(1f)
                        flex(1f)
                        marginTop(20f)
                        backgroundColor(StockChatTheme.borderStrong)
                    }
                }
            }
            View {
                attr {
                    flex(1f)
                    borderRadius(if (depth == 0) 16f else 13f)
                    backgroundColor(if (depth == 0) StockChatTheme.accentSoft else StockChatTheme.surface)
                    themedBorder()
                    padding(top = 12f, left = 13f, right = 13f, bottom = 12f)
                }
                Text {
                    attr {
                        text(node.label)
                        fontSize(if (depth == 0) 16f else 13f)
                        lineHeight(if (depth == 0) 22f else 19f)
                        if (depth == 0) {
                            fontWeightBold()
                        }
                        lines(if (depth == 0) 5 else 4)
                        color(StockChatTheme.textPrimary)
                    }
                }
            }
        }
        if (node.children.isNotEmpty()) {
            ctx.ChildTree(
                container = this,
                nodes = node.children,
                depth = depth + 1,
            )
        }
    }
}

internal fun ConversationMindMapArtifactPage.ChildTree(
    container: ViewContainer<*, *>,
    nodes: List<MermaidMindMapNode>,
    depth: Int,
) {
    val ctx = this
    val connectorLeft = (depth * NODE_INDENT).coerceAtMost(MAX_NODE_INDENT) + 16f
    val parentConnectorLeft = ((depth - 1) * NODE_INDENT).coerceAtLeast(0f)
        .coerceAtMost(MAX_NODE_INDENT) + 16f
    with(container) {
        View {
            attr {
                width(pagerData.pageViewWidth - 36f)
                alignSelfCenter()
                positionRelative()
            }
            View {
                attr {
                    height(2f)
                    width((connectorLeft - parentConnectorLeft).coerceAtLeast(1f))
                    absolutePosition(top = 0f, left = parentConnectorLeft)
                    backgroundColor(StockChatTheme.borderStrong)
                }
            }
            View {
                attr {
                    width(2f)
                    absolutePosition(top = 0f, left = connectorLeft, bottom = 20f)
                    backgroundColor(StockChatTheme.borderStrong)
                }
            }
            nodes.forEach { child ->
                ctx.MindMapNode(
                    container = this,
                    node = child,
                    depth = depth,
                )
            }
        }
    }
}


private const val NODE_CONNECTOR_WIDTH = 34f
private const val NODE_INDENT = 16f
private const val MAX_NODE_INDENT = 64f
