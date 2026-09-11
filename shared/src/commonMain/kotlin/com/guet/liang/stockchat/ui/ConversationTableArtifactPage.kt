package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.controller.ArtifactController
import com.guet.liang.stockchat.controller.artifactController

import com.guet.liang.stockchat.controller.ComparisonRefreshPhase
import com.guet.liang.stockchat.controller.ComparisonContentUi
import com.guet.liang.stockchat.controller.ComparisonDetailUiState

import com.guet.liang.stockchat.base.openRoute
import com.guet.liang.stockchat.base.BasePager
import com.guet.liang.stockchat.model.ConversationStockComparisonRow
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.reactive.handler.observable
import com.tencent.kuikly.core.views.View

private const val STOCK_DETAIL_SYMBOL_PARAM = "symbol"

internal const val HEADER_HEIGHT = 68f

internal const val SUMMARY_HEIGHT = 142f

internal const val REFRESH_STATUS_HEIGHT = 42f

internal const val TABLE_SECTION_HEADER_HEIGHT = 42f

internal const val RISK_NOTICE_HEIGHT = 58f

internal const val NON_TABLE_CONTENT_HEIGHT = 342f

internal const val MIN_TABLE_HEIGHT = 180f

@Page(CONVERSATION_TABLE_ARTIFACT_PAGE_NAME, supportInLocal = true)
/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal class ConversationTableArtifactPage : BasePager() {
    private var uiState by observable<ComparisonDetailUiState>(ComparisonDetailUiState.Loading)
    private var artifactIdText = ""
    private lateinit var artifactController: ArtifactController

    override fun created() {
        super.created()
        artifactIdText = pageData.params
            .optString(CONVERSATION_TABLE_ARTIFACT_ID_PARAM)
            .trim()
        artifactController = artifactController { uiState = it }
        loadComparison()
    }

    override fun pageWillDestroy() {
        artifactController.cancelRefresh()
        super.pageWillDestroy()
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
                vif({ ctx.uiState is ComparisonDetailUiState.Loading }) {
                    ctx.LoadingState(this)
                }
                vif({ ctx.uiState is ComparisonDetailUiState.NotFound }) {
                    ctx.NotFoundState(this)
                }
                vif({ ctx.uiState is ComparisonDetailUiState.Empty }) {
                    ctx.EmptyState(this, ctx.uiState as ComparisonDetailUiState.Empty)
                }
                vif({ ctx.uiState is ComparisonDetailUiState.Error }) {
                    ctx.ErrorState(this, (ctx.uiState as ComparisonDetailUiState.Error).message)
                }
                vif({ ctx.uiState is ComparisonDetailUiState.Refreshing }) {
                    ctx.ComparisonContent(
                        this,
                        (ctx.uiState as ComparisonDetailUiState.Refreshing).content,
                    )
                }
                vif({ ctx.uiState is ComparisonDetailUiState.Content }) {
                    ctx.ComparisonContent(
                        this,
                        (ctx.uiState as ComparisonDetailUiState.Content).content,
                    )
                }
            }
        }
    }

    internal fun loadComparison() {
        artifactController.loadComparison(artifactIdText)
    }

    internal fun refreshMarketData() {
        artifactController.refreshComparison()
    }

    internal fun openStockDetail(row: ConversationStockComparisonRow) {
        val symbol = row.providerSymbol.ifBlank { row.symbol }.trim()
        if (symbol.isBlank()) {
            return
        }
        val params = JSONObject()
        params.put(STOCK_DETAIL_SYMBOL_PARAM, symbol)
        pageData.params.optString("qwenApiKey").trim()
            .takeIf(String::isNotBlank)
            ?.let { params.put("qwenApiKey", it) }
        pageData.params.optString("aiProxyBaseUrl").trim()
            .takeIf(String::isNotBlank)
            ?.let { params.put("aiProxyBaseUrl", it) }
        openRoute(
            STOCK_DETAIL_PAGE_NAME,
            params,
        )
    }
}
