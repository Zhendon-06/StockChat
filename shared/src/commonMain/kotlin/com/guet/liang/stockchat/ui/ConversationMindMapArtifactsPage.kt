package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.base.BasePager
import com.guet.liang.stockchat.data.ChatHistoryDatabase
import com.guet.liang.stockchat.model.ConversationMindMapArtifactSummary
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.Border
import com.tencent.kuikly.core.base.BorderStyle
import com.tencent.kuikly.core.base.BoxShadow
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.base.attr.ImageUri
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.Image
import com.tencent.kuikly.core.views.Scroller
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

internal const val CONVERSATION_MIND_MAP_ARTIFACTS_PAGE_NAME = "conversation_mind_map_artifacts"
internal const val CONVERSATION_MIND_MAP_ARTIFACT_PAGE_NAME = "conversation_mind_map_artifact"
internal const val CONVERSATION_MIND_MAP_ARTIFACT_ID_PARAM = "artifactId"

private sealed class MindMapArtifactListUiState {
    data object Loading : MindMapArtifactListUiState()
    data object Empty : MindMapArtifactListUiState()
    data class Content(val artifacts: List<ConversationMindMapArtifactSummary>) : MindMapArtifactListUiState()
    data class Error(val message: String) : MindMapArtifactListUiState()
}

@Page(CONVERSATION_MIND_MAP_ARTIFACTS_PAGE_NAME, supportInLocal = true)
internal class ConversationMindMapArtifactsPage : BasePager() {
    private var uiState by observable<MindMapArtifactListUiState>(MindMapArtifactListUiState.Loading)

    override fun created() {
        super.created()
        loadArtifacts()
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
                vif({ ctx.uiState is MindMapArtifactListUiState.Loading }) {
                    ctx.LoadingState(this)
                }
                vif({ ctx.uiState is MindMapArtifactListUiState.Empty }) {
                    ctx.EmptyState(this)
                }
                vif({ ctx.uiState is MindMapArtifactListUiState.Error }) {
                    ctx.ErrorState(this, (ctx.uiState as MindMapArtifactListUiState.Error).message)
                }
                vif({ ctx.uiState is MindMapArtifactListUiState.Content }) {
                    ctx.ArtifactList(
                        this,
                        (ctx.uiState as MindMapArtifactListUiState.Content).artifacts,
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
                        text("思维导图")
                        fontSize(20f)
                        fontWeightBold()
                        color(StockChatTheme.textPrimary)
                        marginLeft(14f)
                        flex(1f)
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

    private fun EmptyState(container: ViewContainer<*, *>) {
        with(container) {
            View {
                attr {
                    absolutePositionAllZero()
                    allCenter()
                    padding(left = 36f, right = 36f)
                }
                View {
                    attr {
                        size(72f, 72f)
                        borderRadius(24f)
                        backgroundColor(StockChatTheme.accentSoft)
                        allCenter()
                    }
                    Image {
                        attr {
                            size(34f, 34f)
                            resizeContain()
                            src(ImageUri.commonAssets("ranking_icon.png"))
                        }
                    }
                }
                Text {
                    attr {
                        text("暂无思维导图")
                        fontSize(20f)
                        fontWeightBold()
                        color(StockChatTheme.textPrimary)
                        marginTop(18f)
                    }
                }
                Text {
                    attr {
                        text("在聊天页右上角选择“思维导图”，即可把当前会话整理为可浏览的分支摘要。")
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
                        click { ctx.loadArtifacts() }
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

    private fun ArtifactList(
        container: ViewContainer<*, *>,
        artifacts: List<ConversationMindMapArtifactSummary>,
    ) {
        val ctx = this
        with(container) {
            Scroller {
                attr {
                    absolutePositionAllZero()
                    showScrollerIndicator(false)
                    bouncesEnable(true)
                    padding(top = 12f, left = 18f, right = 18f, bottom = 24f)
                }
                View {
                    attr {
                        width(pagerData.pageViewWidth - 36f)
                        alignSelfCenter()
                        borderRadius(18f)
                        backgroundColor(StockChatTheme.accentSoft)
                        padding(top = 14f, left = 16f, right = 16f, bottom = 14f)
                    }
                    Text {
                        attr {
                            text("会话思维导图")
                            fontSize(16f)
                            fontWeightBold()
                            color(StockChatTheme.textPrimary)
                        }
                    }
                    Text {
                        attr {
                            text("按最近更新时间排列，进入详情后可浏览每个问答分支。")
                            fontSize(13f)
                            lineHeight(19f)
                            color(StockChatTheme.textSecondary)
                            marginTop(5f)
                        }
                    }
                }
                artifacts.forEach { summary ->
                    ctx.ArtifactSummaryCard(this, summary)
                }
                View {
                    attr {
                        width(pagerData.pageViewWidth - 36f)
                        alignSelfCenter()
                        borderRadius(14f)
                        backgroundColor(StockChatTheme.warningSoft)
                        padding(top = 12f, left = 14f, right = 14f, bottom = 12f)
                        marginTop(14f)
                    }
                    Text {
                        attr {
                            text("演示信息，仅供参考，不构成投资建议。")
                            fontSize(12f)
                            lineHeight(18f)
                            color(StockChatTheme.warning)
                        }
                    }
                }
            }
        }
    }

    private fun ArtifactSummaryCard(
        container: ViewContainer<*, *>,
        summary: ConversationMindMapArtifactSummary,
    ) {
        val ctx = this
        with(container) {
            View {
                attr {
                    width(pagerData.pageViewWidth - 36f)
                    alignSelfCenter()
                    minHeight(112f)
                    borderRadius(20f)
                    backgroundColor(StockChatTheme.surface)
                    border(Border(1f, BorderStyle.SOLID, StockChatTheme.border))
                    boxShadow(BoxShadow(0f, 5f, 16f, Color(0x12000000)))
                    padding(top = 16f, left = 16f, right = 14f, bottom = 16f)
                    marginTop(14f)
                    flexDirectionRow()
                    alignItemsCenter()
                }
                event {
                    click { ctx.openArtifact(summary.id) }
                }
                View {
                    attr {
                        size(46f, 46f)
                        borderRadius(15f)
                        backgroundColor(StockChatTheme.accentSoft)
                        allCenter()
                    }
                    Image {
                        attr {
                            size(24f, 24f)
                            resizeContain()
                            src(ImageUri.commonAssets("ranking_icon.png"))
                        }
                    }
                }
                View {
                    attr {
                        flex(1f)
                        marginLeft(13f)
                    }
                    Text {
                        attr {
                            text(summary.title.ifBlank { "当前会话 · 思维导图" })
                            fontSize(16f)
                            fontWeightBold()
                            lineHeight(22f)
                            lines(2)
                            color(StockChatTheme.textPrimary)
                        }
                    }
                    Text {
                        attr {
                            text("共 ${summary.branchCount} 个分支 · 本地产物 #${summary.id}")
                            fontSize(12f)
                            color(StockChatTheme.textTertiary)
                            marginTop(7f)
                        }
                    }
                }
                Text {
                    attr {
                        text("›")
                        fontSize(28f)
                        color(StockChatTheme.textTertiary)
                        marginLeft(8f)
                    }
                }
            }
        }
    }

    private fun loadArtifacts() {
        uiState = MindMapArtifactListUiState.Loading
        uiState = try {
            val artifacts = ChatHistoryDatabase.mindMapArtifactRepository().listAll()
            if (artifacts.isEmpty()) {
                MindMapArtifactListUiState.Empty
            } else {
                MindMapArtifactListUiState.Content(artifacts)
            }
        } catch (_: Throwable) {
            MindMapArtifactListUiState.Error("本地思维导图暂时无法读取，请稍后重试。")
        }
    }

    private fun openArtifact(artifactId: Long) {
        val params = JSONObject()
        params.put(CONVERSATION_MIND_MAP_ARTIFACT_ID_PARAM, artifactId.toString())
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(
            CONVERSATION_MIND_MAP_ARTIFACT_PAGE_NAME,
            params,
        )
    }

    private fun closePage() {
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
    }

    private companion object {
        const val HEADER_HEIGHT = 68f
    }
}
