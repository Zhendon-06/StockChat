package com.guet.liang.stockchat.ui

import com.guet.liang.kuiklytableview.table.KuiklyTable
import com.guet.liang.kuiklytableview.table.TableAlignment
import com.guet.liang.kuiklytableview.table.TableBorderOptions
import com.guet.liang.kuiklytableview.table.TableDensity
import com.guet.liang.kuiklytableview.table.TableHeaderStyle
import com.guet.liang.kuiklytableview.table.TablePadding
import com.guet.liang.kuiklytableview.table.TableStyleOptions
import com.guet.liang.kuiklytableview.table.tableSpec
import com.guet.liang.stockchat.base.BasePager
import com.guet.liang.stockchat.data.ChatHistoryDatabase
import com.guet.liang.stockchat.model.ConversationTableArtifact
import com.guet.liang.stockchat.model.ConversationTableColumn
import com.guet.liang.stockchat.model.ConversationTableRow
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
import com.tencent.kuikly.core.views.Text
import com.tencent.kuikly.core.views.View

private sealed class ArtifactDetailUiState {
    data object Loading : ArtifactDetailUiState()
    data object NotFound : ArtifactDetailUiState()
    data class Content(val artifact: ConversationTableArtifact) : ArtifactDetailUiState()
    data class Error(val message: String) : ArtifactDetailUiState()
}

@Page(CONVERSATION_TABLE_ARTIFACT_PAGE_NAME, supportInLocal = true)
internal class ConversationTableArtifactPage : BasePager() {
    private var uiState by observable<ArtifactDetailUiState>(ArtifactDetailUiState.Loading)
    private var artifactIdText = ""

    override fun created() {
        super.created()
        artifactIdText = pageData.params
            .optString(CONVERSATION_TABLE_ARTIFACT_ID_PARAM)
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
                vif({ ctx.uiState is ArtifactDetailUiState.Loading }) {
                    ctx.LoadingState(this)
                }
                vif({ ctx.uiState is ArtifactDetailUiState.NotFound }) {
                    ctx.NotFoundState(this)
                }
                vif({ ctx.uiState is ArtifactDetailUiState.Error }) {
                    ctx.ErrorState(this, (ctx.uiState as ArtifactDetailUiState.Error).message)
                }
                vif({ ctx.uiState is ArtifactDetailUiState.Content }) {
                    ctx.ArtifactContent(
                        this,
                        (ctx.uiState as ArtifactDetailUiState.Content).artifact,
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
                        text("产物表格详情")
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
                        text("正在读取表格")
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
                        text("未找到该产物")
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
                        text("表格读取失败")
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
        artifact: ConversationTableArtifact,
    ) {
        val ctx = this
        val contentHeight = (
            pagerData.pageViewHeight -
                pagerData.statusBarHeight -
                HEADER_HEIGHT -
                pagerData.safeAreaInsets.bottom
            ).coerceAtLeast(0f)
        val tableHeight = (contentHeight - NON_TABLE_CONTENT_HEIGHT).coerceAtLeast(MIN_TABLE_HEIGHT)
        val tableSpec = tableSpec<ConversationTableRow> {
            rows(artifact.rows)
            rowKey { row -> row.sequence }
            rowHeight = TABLE_ROW_HEIGHT
            padding(horizontal = 10f, vertical = 8f)
            style(
                TableStyleOptions(
                    density = TableDensity.Comfortable,
                    borders = TableBorderOptions(),
                    stripedRows = true,
                    headerStyle = TableHeaderStyle.Accent,
                    rowBackgroundColor = StockChatTheme.surface,
                    alternateRowBackgroundColor = StockChatTheme.surfaceSoft,
                    textColor = StockChatTheme.textPrimary,
                    headerBackgroundColor = Color(0xFF176D57),
                    headerTextColor = Color.WHITE,
                    borderWidth = 1f,
                    borderColor = StockChatTheme.border,
                    cellPadding = TablePadding(8f, 10f, 8f, 10f),
                )
            )
            header {
                height = TABLE_HEADER_HEIGHT
            }
            columns {
                column(
                    ConversationTableColumn.SEQUENCE.key,
                    ConversationTableColumn.SEQUENCE.title,
                    width = SEQUENCE_COLUMN_WIDTH,
                ) {
                    alignment = TableAlignment.Center
                    value { row -> row.valueFor(ConversationTableColumn.SEQUENCE) }
                }
                column(
                    ConversationTableColumn.USER_QUESTION.key,
                    ConversationTableColumn.USER_QUESTION.title,
                    width = QUESTION_COLUMN_WIDTH,
                ) {
                    value { row -> row.valueFor(ConversationTableColumn.USER_QUESTION) }
                    cell { cell -> ctx.TableTextCell(this, cell.value, maxLines = 3) }
                }
                column(
                    ConversationTableColumn.AI_ANSWER_SUMMARY.key,
                    ConversationTableColumn.AI_ANSWER_SUMMARY.title,
                    width = ANSWER_COLUMN_WIDTH,
                ) {
                    value { row -> row.valueFor(ConversationTableColumn.AI_ANSWER_SUMMARY) }
                    cell { cell -> ctx.TableTextCell(this, cell.value, maxLines = 3) }
                }
                column(
                    ConversationTableColumn.RELATED_INSTRUMENT.key,
                    ConversationTableColumn.RELATED_INSTRUMENT.title,
                    width = INSTRUMENT_COLUMN_WIDTH,
                ) {
                    value { row -> row.valueFor(ConversationTableColumn.RELATED_INSTRUMENT) }
                    cell { cell -> ctx.TableTextCell(this, cell.value, maxLines = 2) }
                }
                column(
                    ConversationTableColumn.STATUS.key,
                    ConversationTableColumn.STATUS.title,
                    width = STATUS_COLUMN_WIDTH,
                ) {
                    alignment = TableAlignment.Center
                    value { row -> row.valueFor(ConversationTableColumn.STATUS) }
                    cell { cell -> ctx.StatusCell(this, cell.row.status) }
                }
            }
            emptyState {
                View {
                    attr {
                        width(TABLE_CONTENT_WIDTH)
                        height((tableHeight - TABLE_HEADER_HEIGHT).coerceAtLeast(1f))
                        allCenter()
                    }
                    Text {
                        attr {
                            text("该会话暂无可整理的问答行")
                            fontSize(14f)
                            color(StockChatTheme.textSecondary)
                        }
                    }
                }
            }
        }

        with(container) {
            View {
                attr {
                    absolutePositionAllZero()
                    padding(top = 14f, left = 16f, right = 16f, bottom = 12f)
                }
                ctx.ArtifactSummary(this, artifact)
                View {
                    attr {
                        height(TABLE_SECTION_HEADER_HEIGHT)
                        flexDirectionRow()
                        alignItemsCenter()
                        marginTop(12f)
                    }
                    Text {
                        attr {
                            text("问答汇总")
                            fontSize(16f)
                            fontWeightBold()
                            color(StockChatTheme.textPrimary)
                            flex(1f)
                        }
                    }
                    Text {
                        attr {
                            text("左右滑动查看全部列")
                            fontSize(11f)
                            color(StockChatTheme.textTertiary)
                        }
                    }
                }
                View {
                    attr {
                        height(tableHeight)
                        alignSelfStretch()
                        borderRadius(14f)
                        border(Border(1f, BorderStyle.SOLID, StockChatTheme.border))
                        backgroundColor(StockChatTheme.surface)
                        overflow(true)
                    }
                    KuiklyTable(
                        spec = tableSpec,
                        viewportHeight = tableHeight,
                    )
                }
                View {
                    attr {
                        height(RISK_NOTICE_HEIGHT)
                        borderRadius(14f)
                        backgroundColor(StockChatTheme.warningSoft)
                        padding(left = 14f, right = 14f)
                        justifyContentCenter()
                        marginTop(12f)
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

    private fun ArtifactSummary(
        container: ViewContainer<*, *>,
        artifact: ConversationTableArtifact,
    ) {
        val completedCount = artifact.rows.count { it.status == ConversationTableRowStatus.COMPLETED }
        with(container) {
            View {
                attr {
                    height(SUMMARY_HEIGHT)
                    borderRadius(20f)
                    backgroundColor(StockChatTheme.surface)
                    border(Border(1f, BorderStyle.SOLID, StockChatTheme.border))
                    padding(top = 15f, left = 16f, right = 16f, bottom = 15f)
                }
                Text {
                    attr {
                        text(artifact.title.ifBlank { "当前会话 · 产物表格" })
                        fontSize(18f)
                        fontWeightBold()
                        lineHeight(24f)
                        lines(2)
                        color(StockChatTheme.textPrimary)
                    }
                }
                Text {
                    attr {
                        text(
                            "来源消息 ${artifact.sourceMessageCount} 条 · " +
                                "表格 ${artifact.rows.size} 行 · 已完成 $completedCount 行"
                        )
                        fontSize(12f)
                        color(StockChatTheme.textSecondary)
                        marginTop(8f)
                    }
                }
                Text {
                    attr {
                        text("本地产物 #${artifact.id}")
                        fontSize(11f)
                        color(StockChatTheme.textTertiary)
                        marginTop(5f)
                    }
                }
            }
        }
    }

    private fun TableTextCell(
        container: ViewContainer<*, *>,
        value: String,
        maxLines: Int,
    ) {
        with(container) {
            Text {
                attr {
                    flex(1f)
                    text(value)
                    fontSize(13f)
                    lineHeight(17f)
                    lines(maxLines)
                    color(StockChatTheme.textPrimary)
                }
            }
        }
    }

    private fun StatusCell(
        container: ViewContainer<*, *>,
        status: ConversationTableRowStatus,
    ) {
        val backgroundColor = when (status) {
            ConversationTableRowStatus.COMPLETED -> StockChatTheme.accentSoft
            ConversationTableRowStatus.GENERATING -> StockChatTheme.warningSoft
            ConversationTableRowStatus.FAILED -> Color(0xFFFFECEA)
            ConversationTableRowStatus.WAITING -> StockChatTheme.recessed
        }
        val textColor = when (status) {
            ConversationTableRowStatus.COMPLETED -> StockChatTheme.accent
            ConversationTableRowStatus.GENERATING -> StockChatTheme.warning
            ConversationTableRowStatus.FAILED -> StockChatTheme.positive
            ConversationTableRowStatus.WAITING -> StockChatTheme.textSecondary
        }
        with(container) {
            View {
                attr {
                    height(28f)
                    borderRadius(14f)
                    backgroundColor(backgroundColor)
                    padding(left = 10f, right = 10f)
                    allCenter()
                }
                Text {
                    attr {
                        text(status.label)
                        fontSize(12f)
                        fontWeightMedium()
                        color(textColor)
                    }
                }
            }
        }
    }

    private fun loadArtifact() {
        val artifactId = artifactIdText.toLongOrNull()
        if (artifactId == null || artifactId <= 0L) {
            uiState = ArtifactDetailUiState.Error("产物标识无效，请返回列表重新选择。")
            return
        }
        uiState = ArtifactDetailUiState.Loading
        uiState = try {
            ChatHistoryDatabase.artifactRepository().load(artifactId)?.let {
                ArtifactDetailUiState.Content(it)
            } ?: ArtifactDetailUiState.NotFound
        } catch (_: Throwable) {
            ArtifactDetailUiState.Error("本地产物暂时无法读取，请稍后重试。")
        }
    }

    private fun closePage() {
        acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
    }

    private companion object {
        const val HEADER_HEIGHT = 68f
        const val SUMMARY_HEIGHT = 112f
        const val TABLE_SECTION_HEADER_HEIGHT = 34f
        const val RISK_NOTICE_HEIGHT = 58f
        const val NON_TABLE_CONTENT_HEIGHT = 256f
        const val MIN_TABLE_HEIGHT = 180f
        const val TABLE_HEADER_HEIGHT = 48f
        const val TABLE_ROW_HEIGHT = 72f
        const val SEQUENCE_COLUMN_WIDTH = 70f
        const val QUESTION_COLUMN_WIDTH = 260f
        const val ANSWER_COLUMN_WIDTH = 360f
        const val INSTRUMENT_COLUMN_WIDTH = 180f
        const val STATUS_COLUMN_WIDTH = 116f
        const val TABLE_CONTENT_WIDTH =
            SEQUENCE_COLUMN_WIDTH +
                QUESTION_COLUMN_WIDTH +
                ANSWER_COLUMN_WIDTH +
                INSTRUMENT_COLUMN_WIDTH +
                STATUS_COLUMN_WIDTH
    }
}
