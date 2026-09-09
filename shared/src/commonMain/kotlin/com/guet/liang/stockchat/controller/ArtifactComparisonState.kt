package com.guet.liang.stockchat.controller

import com.guet.liang.stockchat.model.ConversationStockComparisonSnapshot

// 对比表格详情页 UI 状态模型。

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal enum class ComparisonRefreshPhase {
    REFRESHING,
    CURRENT,
    PARTIAL,
    FAILED,
    SESSION_ONLY,
}

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal data class ComparisonContentUi(
    val snapshot: ConversationStockComparisonSnapshot,
    val refreshPhase: ComparisonRefreshPhase,
    val refreshedCount: Int,
    val completedCount: Int,
    val refreshTargetCount: Int,
)

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal sealed class ComparisonDetailUiState {
    data object Loading : ComparisonDetailUiState()
    data object NotFound : ComparisonDetailUiState()
    data class Empty(
        val title: String,
        val sourceMessageCount: Int,
    ) : ComparisonDetailUiState()
    data class Refreshing(val content: ComparisonContentUi) : ComparisonDetailUiState()
    data class Content(val content: ComparisonContentUi) : ComparisonDetailUiState()
    data class Error(val message: String) : ComparisonDetailUiState()
}
