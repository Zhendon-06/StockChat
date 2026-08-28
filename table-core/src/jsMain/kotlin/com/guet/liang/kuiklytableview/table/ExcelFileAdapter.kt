package com.guet.liang.kuiklytableview.table

import org.khronos.webgl.ArrayBuffer
import org.w3c.files.File
import org.w3c.files.FileReader
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@JsModule("@e965/xlsx")
@JsNonModule
private external object SheetJs {
    fun read(data: ArrayBuffer, options: dynamic = definedExternally): dynamic

    val utils: dynamic
}

/** Reads every worksheet from a browser [File]. Both `.xlsx` and legacy `.xls` files are supported. */
public suspend fun File.readExcelWorkbook(): ExcelWorkbook {
    if (size == 0) {
        throw ExcelFileException("Excel file '$name' is empty")
    }

    return try {
        parseExcelWorkbook(readAsArrayBuffer())
    } catch (error: Throwable) {
        if (error is ExcelFileException) {
            throw error
        }
        throw ExcelFileException("Unable to read Excel file '$name'", error)
    }
}

/** Reads a browser [File] and converts the selected sheet directly to a renderable table spec. */
public suspend fun File.toExcelTableSpec(
    sheetIndex: Int = 0,
    options: ExcelTableOptions = ExcelTableOptions(),
    configure: TableSpecBuilder<ExcelRow>.() -> Unit = {},
): TableSpec<ExcelRow> = readExcelWorkbook().toTableSpec(sheetIndex, options, configure)

internal fun parseExcelWorkbook(buffer: ArrayBuffer): ExcelWorkbook {
    val readOptions = js("({})")
    readOptions.type = "array"
    readOptions.cellDates = true
    val workbook = SheetJs.read(buffer, readOptions)
    val sheetNames = workbook.SheetNames
    val sheetCount = sheetNames.length.unsafeCast<Int>()
    if (sheetCount == 0) {
        throw ExcelFileException("Excel workbook does not contain any sheets")
    }

    val sheets = List(sheetCount) { sheetIndex ->
        val sheetName = sheetNames[sheetIndex].unsafeCast<String>()
        val worksheet = workbook.Sheets[sheetName]
        ExcelSheet(sheetName, worksheetRows(worksheet))
    }
    return ExcelWorkbook(sheets)
}

private fun worksheetRows(worksheet: dynamic): List<List<String>> {
    val conversionOptions = js("({})")
    conversionOptions.header = 1
    conversionOptions.raw = false
    conversionOptions.defval = ""
    conversionOptions.blankrows = true
    val rawRows = SheetJs.utils.sheet_to_json(worksheet, conversionOptions)
    val rowCount = rawRows.length.unsafeCast<Int>()

    return List(rowCount) { rowIndex ->
        val rawRow = rawRows[rowIndex]
        val columnCount = rawRow.length.unsafeCast<Int>()
        List(columnCount) { columnIndex ->
            rawRow[columnIndex]?.toString() ?: ""
        }
    }.dropLastWhile { row -> row.all(String::isEmpty) }
}

private suspend fun File.readAsArrayBuffer(): ArrayBuffer = suspendCoroutine { continuation ->
    val reader = FileReader()
    var completed = false

    reader.onload = {
        if (!completed) {
            completed = true
            val result = reader.result
            if (result is ArrayBuffer) {
                continuation.resume(result)
            } else {
                continuation.resumeWithException(
                    ExcelFileException("Browser did not return binary data for Excel file '$name'"),
                )
            }
        }
    }
    reader.onerror = {
        if (!completed) {
            completed = true
            continuation.resumeWithException(
                ExcelFileException(reader.error?.message ?: "Browser failed to read Excel file '$name'"),
            )
        }
    }
    reader.onabort = {
        if (!completed) {
            completed = true
            continuation.resumeWithException(ExcelFileException("Reading Excel file '$name' was aborted"))
        }
    }
    reader.readAsArrayBuffer(this)
}
