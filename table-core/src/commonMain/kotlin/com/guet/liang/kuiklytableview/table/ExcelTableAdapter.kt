package com.guet.liang.kuiklytableview.table

import com.tencent.kuikly.core.reactive.collection.ObservableList

/** A workbook parsed from an Excel file. */
public class ExcelWorkbook(
    sheets: List<ExcelSheet>,
) {
    public val sheets: List<ExcelSheet> = sheets.toList()

    init {
        require(this.sheets.isNotEmpty()) { "Excel workbook must contain at least one sheet" }
    }

    public fun sheet(index: Int): ExcelSheet {
        require(index in sheets.indices) {
            "Excel sheet index $index is out of range (sheet count: ${sheets.size})"
        }
        return sheets[index]
    }
}

/** Cell display values from one Excel worksheet. */
public class ExcelSheet(
    public val name: String,
    cells: List<List<String>>,
) {
    public val cells: List<List<String>> = cells.map { row -> row.toList() }
}

/** A normalized Excel row consumed by [KuiklyTable]. */
public data class ExcelRow(
    public val sheetRowIndex: Int,
    public val cells: List<String>,
) {
    public operator fun get(columnIndex: Int): String = cells.getOrElse(columnIndex) { "" }
}

/** Options used when converting a worksheet into a [TableSpec]. */
public data class ExcelTableOptions(
    /** Zero-based header row. Set to `null` to render every row as data with A/B/C column titles. */
    public val headerRowIndex: Int? = 0,
    public val columnWidth: Float = 120f,
    public val rowHeight: Float = 44f,
) {
    init {
        require(headerRowIndex == null || headerRowIndex >= 0) {
            "Excel header row index must be non-negative"
        }
        require(columnWidth > 0f) { "Excel column width must be greater than zero" }
        require(rowHeight > 0f) { "Excel row height must be greater than zero" }
    }
}

public class ExcelFileException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

/** Converts a parsed workbook sheet directly to a renderable table specification. */
public fun ExcelWorkbook.toTableSpec(
    sheetIndex: Int = 0,
    options: ExcelTableOptions = ExcelTableOptions(),
    configure: TableSpecBuilder<ExcelRow>.() -> Unit = {},
): TableSpec<ExcelRow> = sheet(sheetIndex).toTableSpec(options, configure)

/** Converts this worksheet directly to a renderable table specification. */
public fun ExcelSheet.toTableSpec(
    options: ExcelTableOptions = ExcelTableOptions(),
    configure: TableSpecBuilder<ExcelRow>.() -> Unit = {},
): TableSpec<ExcelRow> {
    require(cells.isNotEmpty()) { "Excel sheet '$name' does not contain any cells" }

    val headerRowIndex = options.headerRowIndex
    if (headerRowIndex != null) {
        require(headerRowIndex in cells.indices) {
            "Excel header row index $headerRowIndex is out of range for sheet '$name'"
        }
    }

    val firstIncludedRow = headerRowIndex ?: 0
    val columnCount = cells
        .drop(firstIncludedRow)
        .maxOfOrNull { row -> row.size }
        ?: 0
    require(columnCount > 0) { "Excel sheet '$name' does not contain any columns" }

    val dataStartRow = headerRowIndex?.plus(1) ?: 0
    val tableRows = cells.drop(dataStartRow).mapIndexed { offset, row ->
        ExcelRow(
            sheetRowIndex = dataStartRow + offset,
            cells = List(columnCount) { columnIndex -> row.getOrElse(columnIndex) { "" } },
        )
    }
    val headerCells = headerRowIndex?.let(cells::get)

    return tableSpec {
        rows(ObservableList(tableRows.toMutableList()))
        rowHeight = options.rowHeight
        columns {
            repeat(columnCount) { columnIndex ->
                val title = headerCells
                    ?.getOrNull(columnIndex)
                    ?.takeIf(String::isNotBlank)
                    ?: excelColumnName(columnIndex)
                column(
                    id = "excel_column_$columnIndex",
                    title = title,
                    width = options.columnWidth,
                ) {
                    value { row -> row[columnIndex] }
                }
            }
        }
        configure()
    }
}

private fun excelColumnName(columnIndex: Int): String {
    var remaining = columnIndex + 1
    val name = StringBuilder()
    while (remaining > 0) {
        remaining--
        name.append(('A'.code + remaining % 26).toChar())
        remaining /= 26
    }
    return name.reverse().toString()
}
