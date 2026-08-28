package com.guet.liang.kuiklytableview.table

import java.io.File
import kotlin.concurrent.thread
import kotlin.coroutines.suspendCoroutine

/** Reads every worksheet from an Android/JVM `.xlsx` [File]. */
public suspend fun File.readExcelWorkbook(): ExcelWorkbook = runExcelIo {
    parseExcelWorkbook(this)
}

/** Reads an Android/JVM [File] and converts the selected sheet directly to a renderable table spec. */
public suspend fun File.toExcelTableSpec(
    sheetIndex: Int = 0,
    options: ExcelTableOptions = ExcelTableOptions(),
    configure: TableSpecBuilder<ExcelRow>.() -> Unit = {},
): TableSpec<ExcelRow> = readExcelWorkbook().toTableSpec(sheetIndex, options, configure)

internal fun parseExcelWorkbook(file: File): ExcelWorkbook {
    if (!file.isFile) {
        throw ExcelFileException("Excel file '${file.path}' does not exist")
    }
    if (file.length() == 0L) {
        throw ExcelFileException("Excel file '${file.name}' is empty")
    }

    return try {
        XlsxWorkbookParser(file).parse()
    } catch (error: Throwable) {
        if (error is ExcelFileException) {
            throw error
        }
        throw ExcelFileException("Unable to read Excel file '${file.name}'", error)
    }
}

private suspend fun <ValueT> runExcelIo(block: () -> ValueT): ValueT =
    suspendCoroutine { continuation ->
        thread(name = "KuiklyExcelReader") {
            continuation.resumeWith(runCatching(block))
        }
    }
