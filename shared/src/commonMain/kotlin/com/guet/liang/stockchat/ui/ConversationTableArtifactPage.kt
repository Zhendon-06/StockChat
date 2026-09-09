package com.guet.liang.stockchat.ui

import com.guet.liang.stockchat.base.BasePager
import com.guet.liang.stockchat.data.ChatHistoryDatabase
import com.guet.liang.stockchat.data.ConversationStockComparisonDataSource
import com.guet.liang.stockchat.data.ConversationStockComparisonGenerator
import com.guet.liang.stockchat.model.ConversationStockComparisonRow
import com.guet.liang.stockchat.model.ConversationStockComparisonSnapshot
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.ViewBuilder
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.module.NetworkModule
import com.tencent.kuikly.core.module.RouterModule
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
internal class ConversationTableArtifactPage : BasePager() {
    private var uiState by observable<ComparisonDetailUiState>(ComparisonDetailUiState.Loading)
    private var artifactIdText = ""
    private var refreshToken = 0
    private var baseSnapshot: ConversationStockComparisonSnapshot? = null
    private lateinit var comparisonDataSource: ConversationStockComparisonDataSource

    override fun created() {
        super.created()
        artifactIdText = pageData.params
            .optString(CONVERSATION_TABLE_ARTIFACT_ID_PARAM)
            .trim()
        comparisonDataSource = ConversationStockComparisonDataSource(
            acquireModule<NetworkModule>(NetworkModule.MODULE_NAME)
        )
        loadComparison()
    }

    override fun pageWillDestroy() {
        refreshToken += 1
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
        val artifactId = artifactIdText.toLongOrNull()
        if (artifactId == null || artifactId <= 0L) {
            uiState = ComparisonDetailUiState.Error("对比标识无效，请返回列表重新选择。")
            return
        }
        refreshToken += 1
        uiState = ComparisonDetailUiState.Loading
        try {
            val artifact = ChatHistoryDatabase.artifactRepository().load(artifactId)
            if (artifact == null) {
                uiState = ComparisonDetailUiState.NotFound
                return
            }
            val messages = ChatHistoryDatabase.repository().loadMessages(artifact.sessionId)
            val snapshot = ConversationStockComparisonGenerator.generate(
                title = artifact.title,
                messages = messages,
            )
            baseSnapshot = snapshot
            if (snapshot.rows.isEmpty()) {
                uiState = ComparisonDetailUiState.Empty(
                    title = snapshot.title,
                    sourceMessageCount = snapshot.sourceMessageCount,
                )
            } else {
                refreshMarketData()
            }
        } catch (_: Throwable) {
            uiState = ComparisonDetailUiState.Error("当前会话暂时无法整理，请稍后重试。")
        }
    }

    internal fun refreshMarketData() {
        val snapshot = baseSnapshot ?: return
        val providerSymbols = snapshot.providerSymbols
        if (providerSymbols.isEmpty()) {
            uiState = ComparisonDetailUiState.Content(
                ComparisonContentUi(
                    snapshot = snapshot,
                    refreshPhase = ComparisonRefreshPhase.SESSION_ONLY,
                    refreshedCount = 0,
                    completedCount = 0,
                    refreshTargetCount = 0,
                )
            )
            return
        }

        val requestToken = ++refreshToken
        uiState = ComparisonDetailUiState.Refreshing(
            ComparisonContentUi(
                snapshot = snapshot,
                refreshPhase = ComparisonRefreshPhase.REFRESHING,
                refreshedCount = 0,
                completedCount = 0,
                refreshTargetCount = providerSymbols.size,
            )
        )

        comparisonDataSource.refresh(snapshot) { result ->
            if (requestToken != refreshToken) {
                return@refresh
            }
            val refreshPhase = when {
                result.requestedCount == 0 -> ComparisonRefreshPhase.SESSION_ONLY
                result.isComplete -> ComparisonRefreshPhase.CURRENT
                result.isPartial -> ComparisonRefreshPhase.PARTIAL
                else -> ComparisonRefreshPhase.FAILED
            }
            uiState = ComparisonDetailUiState.Content(
                ComparisonContentUi(
                    snapshot = result.snapshot,
                    refreshPhase = refreshPhase,
                    refreshedCount = result.refreshedCount,
                    completedCount = result.requestedCount,
                    refreshTargetCount = result.requestedCount,
                )
            )
        }
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
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).openPage(
            STOCK_DETAIL_PAGE_NAME,
            params,
        )
    }
}
