package com.guet.liang.kuiklytableview.table

import com.tencent.kuikly.core.base.ViewContainer
import org.w3c.files.File

/** Displays a browser Excel [File] directly and handles asynchronous loading internally. */
public fun ViewContainer<*, *>.KuiklyExcelTable(
    file: File,
    viewportHeight: Float,
    sheetIndex: Int = 0,
    options: ExcelTableOptions = ExcelTableOptions(),
    metrics: TableMetrics = TableMetrics(),
    configure: TableSpecBuilder<ExcelRow>.() -> Unit = {},
    onLoaded: (TableSpec<ExcelRow>) -> Unit = {},
    onError: (Throwable) -> Unit = {},
    init: KuiklyExcelTableView.() -> Unit = {},
) {
    addKuiklyExcelTable(
        viewportHeight = viewportHeight,
        metrics = metrics,
        loader = {
            file.toExcelTableSpec(
                sheetIndex = sheetIndex,
                options = options,
                configure = configure,
            )
        },
        onLoaded = onLoaded,
        onError = onError,
        init = init,
    )
}

/** [KuiklyTable] overload that accepts and displays a browser Excel [File] directly. */
public fun ViewContainer<*, *>.KuiklyTable(
    file: File,
    viewportHeight: Float,
    sheetIndex: Int = 0,
    options: ExcelTableOptions = ExcelTableOptions(),
    metrics: TableMetrics = TableMetrics(),
    configure: TableSpecBuilder<ExcelRow>.() -> Unit = {},
    onLoaded: (TableSpec<ExcelRow>) -> Unit = {},
    onError: (Throwable) -> Unit = {},
    init: KuiklyExcelTableView.() -> Unit = {},
) {
    KuiklyExcelTable(
        file = file,
        viewportHeight = viewportHeight,
        sheetIndex = sheetIndex,
        options = options,
        metrics = metrics,
        configure = configure,
        onLoaded = onLoaded,
        onError = onError,
        init = init,
    )
}
