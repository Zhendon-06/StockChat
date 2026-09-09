package com.guet.liang.stockchat.ui

import com.guet.liang.kuiklytableview.ui.KuiklyTable
import com.guet.liang.kuiklytableview.table.TableAlignment
import com.guet.liang.kuiklytableview.table.TableCellContext
import com.guet.liang.kuiklytableview.table.TableHeaderContext
import com.guet.liang.kuiklytableview.table.TableHeaderStyle
import com.guet.liang.kuiklytableview.table.TableStyleOptions
import com.guet.liang.kuiklytableview.table.TableStylePreset
import com.guet.liang.kuiklytableview.table.tableSpec
import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.directives.velse
import com.tencent.kuikly.core.views.Text

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal enum class StockTableStyleChoice(
    val title: String,
    val description: String,
    val preset: TableStylePreset,
) {
    DEFAULT("经典", "完整网格与斑马纹", TableStylePreset.Default),
    COMPACT("紧凑", "同屏展示更多行情", TableStylePreset.Compact),
    SPACIOUS("宽松", "更大的行距与留白", TableStylePreset.Spacious),
    MINIMAL("极简", "仅保留横向分隔线", TableStylePreset.Minimal),
    ;

    fun styleOptions(): TableStyleOptions = TableStyleOptions.preset(preset)

    companion object {
        val all: List<StockTableStyleChoice> = values().toList()
    }
}

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
internal data class StockTablePreviewRow(
    val name: String,
    val code: String,
    val price: String,
    val change: String,
    val turnover: String,
)

internal val stockTablePreviewRows = listOf(
    StockTablePreviewRow("上证指数", "000001.SH", "3,857.93", "+0.41%", "5,826 亿"),
    StockTablePreviewRow("贵州茅台", "600519.SH", "1,478.20", "+1.26%", "42.8 亿"),
    StockTablePreviewRow("宁德时代", "300750.SZ", "284.56", "-0.73%", "68.3 亿"),
    StockTablePreviewRow("中国平安", "601318.SH", "56.88", "+0.18%", "31.6 亿"),
)
