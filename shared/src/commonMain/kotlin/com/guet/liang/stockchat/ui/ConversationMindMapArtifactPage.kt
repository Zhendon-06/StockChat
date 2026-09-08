package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.base.BasePager
import com.guet.liang.stockchat.data.ChatHistoryDatabase
import com.guet.liang.stockchat.data.MermaidMindMapNode
import com.guet.liang.stockchat.data.MermaidMindMapParser
import com.guet.liang.stockchat.model.ConversationMindMapArtifact
import com.guet.liang.stockchat.model.ConversationTableRowStatus
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

private sealed class MindMapArtifactDetailUiState {
    data object Loading : MindMapArtifactDetailUiState()
    data object NotFound : MindMapArtifactDetailUiState()
    data class Content(val artifact: ConversationMindMapArtifact) : MindMapArtifactDetailUiState()
    data class Error(val message: String) : MindMapArtifactDetailUiState()
}

@Page(CONVERSATION_MIND_MAP_ARTIFACT_PAGE_NAME, supportInLocal = true)
internal class ConversationMindMapArtifactPage : BasePager() {
    private var uiState by observable<MindMapArtifactDetailUiState>(MindMapArtifactDetailUiState.Loading)
    private var artifactIdText = ""

    override fun created() {
        super.created()
        artifactIdText = pageData.params
            .optString(CONVERSATION_MIND_MAP_ARTIFACT_ID_PARAM)
            .trim()
        loadArtifact()
    }

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            attr {
                backgroundColor(StockChatTheme.background)
            }
            ctx.PageHeader(this)
            View {
                attr {
                    absolutePosition(
                        top = pagerData.statusBarHeight + HEADER_HEIGHT,
                        left = 0f,
                        right = 0f,
                        bottom = pagerData.safeAreaInsets.bottom,
                    )
                }
                vif({ ctx.uiState is MindMapArtifactDetailUiState.Loading }) {
                    ctx.LoadingState(this)
                }
                vif({ ctx.uiState is MindMapArtifactDetailUiState.NotFound }) {
                    ctx.NotFoundState(this)
                }
                vif({ ctx.uiState is MindMapArtifactDetailUiState.Error }) {
                    ctx.ErrorState(this, (ctx.uiState as MindMapArtifactDetailUiState.Error).message)
                }
                vif({ ctx.uiState is MindMapArtifactDetailUiState.Content }) {
                    ctx.ArtifactContent(
                        this,
                        (ctx.uiState as MindMapArtifactDetailUiState.Content).artifact,
                    )
                }
            }
        }
    }

    private fun PageHeader(container: ViewContainer<*, *>) {
        val ctx = this
        with(container) {
            View {
                attr {
                    height(pagerData.statusBarHeight + HEADER_HEIGHT)
                    padding(
                        top = pagerData.statusBarHeight + 12f,
                        left = 18f,
                        right = 18f,
                    )
                    backgroundColor(StockChatTheme.background)
                    flexDirectionRow()
                    alignItemsCenter()
                }
                View {
                    attr {
                        size(44f, 44f)
                        borderRadius(22f)
                        backgroundColor(StockChatTheme.surface)
                        border(Border(1f, BorderStyle.SOLID, StockChatTheme.border))
                        allCenter()
                    }
                    event {
                        click { ctx.closePage() }
                    }
                    Text {
                        attr {
                            text("‹")
                            fontSize(34f)
                            color(StockChatTheme.textPrimary)
                            marginBottom(3f)
                        }
                    }
                }
                Text {
                    attr {
                        text("思维导图详情")
                        fontSize(20f)
                        fontWeightBold()
                        color(StockChatTheme.textPrimary)
                        marginLeft(14f)
                        flex(1f)
                        lines(1)
                    }
                }
            }
        }
    }

    private fun LoadingState(container: ViewContainer<*, *>) {
        with(container) {
            View {
                attr {
                    absolutePositionAllZero()
                    allCenter()
                }
                View {
                    attr {
                        size(48f, 48f)
                        borderRadius(16f)
                        backgroundColor(StockChatTheme.accentSoft)
                        allCenter()
                    }
                    Text {
                        attr {
                            text("…")
                            fontSize(24f)
                            color(StockChatTheme.accent)
                            marginBottom(8f)
                        }
                    }
                }
                Text {
                    attr {
                        text("正在读取思维导图")
                        fontSize(14f)
                        color(StockChatTheme.textSecondary)
                        marginTop(14f)
                    }
                }
            }
        }
    }

    private fun NotFoundState(container: ViewContainer<*, *>) {
        with(container) {
            View {
                attr {
                    absolutePositionAllZero()
                    allCenter()
                    padding(left = 36f, right = 36f)
                }
                Text {
                    attr {
                        text("未找到该思维导图")
                        fontSize(20f)
                        fontWeightBold()
                        color(StockChatTheme.textPrimary)
                    }
                }
                Text {
                    attr {
                        text("产物可能已随原会话删除，请返回产物列表重新选择。")
                        fontSize(14f)
                        lineHeight(21f)
                        textAlignCenter()
                        color(StockChatTheme.textSecondary)
                        marginTop(8f)
                    }
                }
            }
        }
    }

    private fun ErrorState(container: ViewContainer<*, *>, message: String) {
        val ctx = this
        with(container) {
            View {
                attr {
                    absolutePositionAllZero()
                    allCenter()
                    padding(left = 36f, right = 36f)
                }
                Text {
                    attr {
                        text("思维导图读取失败")
                        fontSize(20f)
                        fontWeightBold()
                        color(StockChatTheme.textPrimary)
                    }
                }
                Text {
                    attr {
                        text(message)
                        fontSize(14f)
                        lineHeight(21f)
                        textAlignCenter()
                        color(StockChatTheme.textSecondary)
                        marginTop(8f)
                    }
                }
                View {
                    attr {
                        height(42f)
                        borderRadius(21f)
                        backgroundColor(StockChatTheme.accent)
                        padding(left = 22f, right = 22f)
                        allCenter()
                        marginTop(20f)
                    }
                    event {
                        click { ctx.loadArtifact() }
                    }
                    Text {
                        attr {
                            text("重新加载")
                            fontSize(14f)
                            fontWeightBold()
                            color(Color.WHITE)
                        }
                    }
                }
            }
        }
    }

    private fun ArtifactContent(
        container: ViewContainer<*, *>,
        artifact: ConversationMindMapArtifact,
    ) {
        val ctx = this
        val mindMap = MermaidMindMapParser.parse(artifact.mermaidSource)
            ?.takeIf { parsed -> parsed.children.isNotEmpty() || artifact.branches.isEmpty() }
            ?: ctx.fallbackMindMap(artifact)
        with(container) {
            Scroller {
                attr {
                    absolutePositionAllZero()
                    showScrollerIndicator(false)
                    bouncesEnable(true)
                    padding(top = 14f, left = 18f, right = 18f, bottom = 24f)
                }
                ctx.RootNode(this, artifact, mindMap)
                if (mindMap.children.isEmpty()) {
                    ctx.EmptyBranches(this)
                } else {
                    View {
                        attr {
                            width(2f)
                            height(18f)
                            alignSelfFlexStart()
                            marginLeft(16f)
                            backgroundColor(StockChatTheme.borderStrong)
                        }
                    }
                    ctx.MindMapTree(this, mindMap.children)
                }
                View {
                    attr {
                        width(pagerData.pageViewWidth - 36f)
                        alignSelfCenter()
                        borderRadius(14f)
                        backgroundColor(StockChatTheme.warningSoft)
                        padding(top = 12f, left = 14f, right = 14f, bottom = 12f)
                        marginTop(16f)
                    }
                    Text {
                        attr {
                            text("演示信息由 AI 会话整理生成，仅供参考，不构成投资建议。")
                            fontSize(12f)
                            lineHeight(18f)
                            color(StockChatTheme.warning)
                        }
                    }
                }
            }
        }
    }

    private fun MindMapTree(
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

    private fun RootNode(
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
                    border(Border(1f, BorderStyle.SOLID, Color(0xFFC8EBDD)))
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

    private fun EmptyBranches(container: ViewContainer<*, *>) {
        with(container) {
            View {
                attr {
                    width(pagerData.pageViewWidth - 36f)
                    alignSelfCenter()
                    height(96f)
                    borderRadius(18f)
                    backgroundColor(StockChatTheme.surface)
                    border(Border(1f, BorderStyle.SOLID, StockChatTheme.border))
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

    private fun MindMapNode(
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
                        border(Border(1f, BorderStyle.SOLID, StockChatTheme.border))
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

    private fun ChildTree(
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

    private fun fallbackMindMap(artifact: ConversationMindMapArtifact): MermaidMindMapNode {
        return MermaidMindMapNode(
            label = artifact.title.ifBlank { "当前会话" },
            children = artifact.branches.map { branch ->
                val details = mutableListOf<MermaidMindMapNode>()
                details += MermaidMindMapNode(
                    label = "洞察：${branch.insight.ifBlank { "暂无 AI 摘要" }}",
                    children = emptyList(),
                )
                if (branch.relatedInstrument.isNotBlank() && branch.relatedInstrument != "未识别") {
                    details += MermaidMindMapNode(
                        label = "标的：${branch.relatedInstrument}",
                        children = emptyList(),
                    )
                }
                details += MermaidMindMapNode(
                    label = "状态：${statusLabel(branch.status)}",
                    children = emptyList(),
                )
                MermaidMindMapNode(
                    label = "${branch.sequence}. ${branch.topic.ifBlank { "未命名问题" }}",
                    children = details,
                )
            },
        )
    }

    private fun statusLabel(status: ConversationTableRowStatus): String {
        return when (status) {
            ConversationTableRowStatus.COMPLETED -> "已完成"
            ConversationTableRowStatus.GENERATING -> "生成中"
            ConversationTableRowStatus.FAILED -> "生成失败"
            ConversationTableRowStatus.WAITING -> "等待回答"
        }
    }

    private companion object {
        const val HEADER_HEIGHT = 68f
        const val NODE_CONNECTOR_WIDTH = 34f
        const val NODE_INDENT = 16f
        const val MAX_NODE_INDENT = 64f
    }

    private fun loadArtifact() {
        val artifactId = artifactIdText.toLongOrNull()
        if (artifactId == null || artifactId <= 0L) {
            uiState = MindMapArtifactDetailUiState.Error("产物标识无效，请返回列表重新选择。")
            return
        }
        uiState = MindMapArtifactDetailUiState.Loading
        uiState = try {
            ChatHistoryDatabase.mindMapArtifactRepository().load(artifactId)?.let {
                MindMapArtifactDetailUiState.Content(it)
            } ?: MindMapArtifactDetailUiState.NotFound
        } catch (_: Throwable) {
            MindMapArtifactDetailUiState.Error("本地思维导图暂时无法读取，请稍后重试。")
        }
    }

    private fun closePage() {
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
    }

}
