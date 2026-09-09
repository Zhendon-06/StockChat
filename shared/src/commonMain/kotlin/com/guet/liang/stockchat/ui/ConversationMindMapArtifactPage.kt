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

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
private sealed class MindMapArtifactDetailUiState {
    data object Loading : MindMapArtifactDetailUiState()
    data object NotFound : MindMapArtifactDetailUiState()
    data class Content(val artifact: ConversationMindMapArtifact) : MindMapArtifactDetailUiState()
    data class Error(val message: String) : MindMapArtifactDetailUiState()
}

@Page(CONVERSATION_MIND_MAP_ARTIFACT_PAGE_NAME, supportInLocal = true)
/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal class ConversationMindMapArtifactPage : BasePager() {
    private var uiState by observable<MindMapArtifactDetailUiState>(MindMapArtifactDetailUiState.Loading)
    private var artifactIdText = ""
    private lateinit var artifactController: ArtifactController

    override fun created() {
        super.created()
        artifactController = artifactController()
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
                        themedBorder()
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
        val mindMap = artifactController.mindMapTree(artifact)
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

    private companion object {
        const val HEADER_HEIGHT = 68f
    }

    private fun loadArtifact() {
        uiState = MindMapArtifactDetailUiState.Loading
        uiState = when (val result = artifactController.loadMindMap(artifactIdText)) {
            is ArtifactResult.Failure -> MindMapArtifactDetailUiState.Error(result.message)
            is ArtifactResult.Success -> result.value?.let { MindMapArtifactDetailUiState.Content(it) }
                ?: MindMapArtifactDetailUiState.NotFound
        }
    }

}
