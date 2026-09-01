package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.base.BasePager
import com.guet.liang.stockchat.data.ChatHistoryDatabase
import com.guet.liang.stockchat.model.ConversationMindMapArtifact
import com.guet.liang.stockchat.model.ConversationMindMapBranch
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
        with(container) {
            Scroller {
                attr {
                    absolutePositionAllZero()
                    showScrollerIndicator(false)
                    bouncesEnable(true)
                    padding(top = 14f, left = 18f, right = 18f, bottom = 24f)
                }
                ctx.RootNode(this, artifact)
                View {
                    attr {
                        width(pagerData.pageViewWidth - 36f)
                        alignSelfCenter()
                        height(28f)
                        flexDirectionRow()
                        alignItemsCenter()
                        marginTop(14f)
                    }
                    View {
                        attr {
                            width(26f)
                            height(1f)
                            backgroundColor(StockChatTheme.borderStrong)
                            marginLeft(19f)
                            marginRight(10f)
                        }
                    }
                    Text {
                        attr {
                            text("会话分支 ${artifact.branches.size} 个")
                            fontSize(13f)
                            color(StockChatTheme.textSecondary)
                        }
                    }
                }
                if (artifact.branches.isEmpty()) {
                    ctx.EmptyBranches(this)
                } else {
                    artifact.branches.forEachIndexed { index, branch ->
                        ctx.BranchNode(this, branch, index == artifact.branches.lastIndex)
                    }
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

    private fun RootNode(
        container: ViewContainer<*, *>,
        artifact: ConversationMindMapArtifact,
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
                        text(artifact.title.ifBlank { "当前会话 · 思维导图" })
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
                        text("中心主题")
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

    private fun BranchNode(
        container: ViewContainer<*, *>,
        branch: ConversationMindMapBranch,
        isLast: Boolean,
    ) {
        val statusLabel = statusLabel(branch.status)
        val statusColor = statusColor(branch.status)
        with(container) {
            View {
                attr {
                    width(pagerData.pageViewWidth - 36f)
                    alignSelfCenter()
                    flexDirectionRow()
                    alignItemsFlexStart()
                    minHeight(112f)
                    marginTop(10f)
                }
                View {
                    attr {
                        width(46f)
                        alignSelfStretch()
                        positionRelative()
                    }
                    if (!isLast) {
                        View {
                            attr {
                                width(1f)
                                absolutePosition(top = 30f, left = 22f, bottom = 0f)
                                backgroundColor(StockChatTheme.borderStrong)
                            }
                        }
                    }
                    View {
                        attr {
                            absolutePosition(top = 10f, left = 6f)
                            size(34f, 34f)
                            borderRadius(17f)
                            backgroundColor(StockChatTheme.accent)
                            allCenter()
                            zIndex(1)
                        }
                        Text {
                            attr {
                                text(branch.sequence.toString())
                                fontSize(13f)
                                fontWeightBold()
                                color(Color.WHITE)
                            }
                        }
                    }
                }
                View {
                    attr {
                        flex(1f)
                        borderRadius(18f)
                        backgroundColor(StockChatTheme.surface)
                        border(Border(1f, BorderStyle.SOLID, StockChatTheme.border))
                        padding(top = 14f, left = 15f, right = 14f, bottom = 14f)
                    }
                    View {
                        attr {
                            flexDirectionRow()
                            alignItemsCenter()
                        }
                        Text {
                            attr {
                                text("分支 ${branch.sequence}")
                                fontSize(11f)
                                color(StockChatTheme.accent)
                            }
                        }
                        Text {
                            attr {
                                text(statusLabel)
                                fontSize(11f)
                                color(statusColor)
                                marginLeft(9f)
                            }
                        }
                    }
                    Text {
                        attr {
                            text(branch.topic.ifBlank { "未命名问题" })
                            fontSize(17f)
                            lineHeight(23f)
                            fontWeightBold()
                            lines(4)
                            color(StockChatTheme.textPrimary)
                            marginTop(7f)
                        }
                    }
                    Text {
                        attr {
                            text(branch.insight.ifBlank { "暂无 AI 摘要" })
                            fontSize(14f)
                            lineHeight(21f)
                            lines(6)
                            color(StockChatTheme.textSecondary)
                            marginTop(8f)
                        }
                    }
                    if (branch.relatedInstrument.isNotBlank() && branch.relatedInstrument != "未识别") {
                        Text {
                            attr {
                                text("相关标的  ${branch.relatedInstrument}")
                                fontSize(12f)
                                color(StockChatTheme.textTertiary)
                                marginTop(9f)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun statusLabel(status: ConversationTableRowStatus): String {
        return when (status) {
            ConversationTableRowStatus.COMPLETED -> "已完成"
            ConversationTableRowStatus.GENERATING -> "生成中"
            ConversationTableRowStatus.FAILED -> "生成失败"
            ConversationTableRowStatus.WAITING -> "等待回答"
        }
    }

    private fun statusColor(status: ConversationTableRowStatus): Color {
        return when (status) {
            ConversationTableRowStatus.COMPLETED -> StockChatTheme.accent
            ConversationTableRowStatus.GENERATING -> StockChatTheme.warning
            ConversationTableRowStatus.FAILED -> StockChatTheme.positive
            ConversationTableRowStatus.WAITING -> StockChatTheme.textSecondary
        }
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

    private companion object {
        const val HEADER_HEIGHT = 68f
    }
}
