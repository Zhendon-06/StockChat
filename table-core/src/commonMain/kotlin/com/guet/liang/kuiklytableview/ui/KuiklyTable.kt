package com.guet.liang.kuiklytableview.ui

import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.reactive.collection.ObservableList
import com.guet.liang.kuiklytableview.table.KuiklyTableView
import com.guet.liang.kuiklytableview.table.TableSpec
import com.guet.liang.kuiklytableview.table.TableMetrics
import com.guet.liang.kuiklytableview.table.TableStyleOptions
import com.guet.liang.kuiklytableview.table.TableSpecBuilder
import com.guet.liang.kuiklytableview.table.tableSpec

public fun <RowT> ViewContainer<*, *>.KuiklyTable(
    spec: TableSpec<RowT>,
    viewportHeight: Float,
    metrics: TableMetrics = TableMetrics(),
    style: TableStyleOptions? = null,
    init: KuiklyTableView<RowT>.() -> Unit = {},
) {
    val effectiveSpec = style?.let(spec::withStyle) ?: spec
    require(viewportHeight > effectiveSpec.header.height) {
        "Table viewportHeight must be greater than header height"
    }
    addChild(KuiklyTableView(effectiveSpec, metrics, viewportHeight)) {
        attr {
            height(viewportHeight)
            alignSelfStretch()
        }
        init()
    }
}

/**
 * Compose-style convenience entry point. It creates the specification and mounts
 * the table in one expression while keeping the style as an explicit parameter.
 */
public fun <RowT> ViewContainer<*, *>.KuiklyTable(
    rows: ObservableList<RowT>,
    viewportHeight: Float,
    style: TableStyleOptions = TableStyleOptions(),
    metrics: TableMetrics = TableMetrics(),
    configure: TableSpecBuilder<RowT>.() -> Unit,
    init: KuiklyTableView<RowT>.() -> Unit = {},
) {
    KuiklyTable(
        spec = tableSpec {
            rows(rows)
            style(style)
            configure()
        },
        viewportHeight = viewportHeight,
        metrics = metrics,
        init = init,
    )
}

