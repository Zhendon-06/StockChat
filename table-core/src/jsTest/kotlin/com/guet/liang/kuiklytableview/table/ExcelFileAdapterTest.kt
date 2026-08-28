package com.guet.liang.kuiklytableview.table

import org.khronos.webgl.Uint8Array
import org.w3c.files.File
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.js.Promise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExcelFileAdapterTest {
    @Test
    fun importsGeneratedWorkbookThroughBrowserFileApi(): Promise<Unit> = runSuspendTest {
        val file = testWorkbookFile()
        val employeeSpec = file.toExcelTableSpec()

        assertEquals(8, employeeSpec.columns.size)
        assertEquals(11, employeeSpec.rows.size)
        assertEquals("张伟", employeeSpec.rows.first()[1])
        assertEquals("2022-03-15", employeeSpec.rows.first()[3])
        assertEquals("¥18,500.00", employeeSpec.rows.first()[4])
        assertEquals("12.0%", employeeSpec.rows.first()[5])
        assertEquals("¥2,220.00", employeeSpec.rows.first()[6])

        val workbook = file.readExcelWorkbook()
        assertEquals(
            listOf("员工数据", "说明后表头", "宽表_28列", "空行测试"),
            workbook.sheets.map { sheet -> sheet.name },
        )
        assertEquals("¥20,351.50", workbook.sheet(0).cells[11][6])

        val offsetHeaderSpec = workbook.toTableSpec(
            sheetIndex = 1,
            options = ExcelTableOptions(headerRowIndex = 2),
        )
        assertEquals(listOf("SKU", "商品名称", "分类", "单价", "库存", "备注"), offsetHeaderSpec.columns.map { it.title })
        assertEquals(5, offsetHeaderSpec.rows.size)
        assertEquals("", offsetHeaderSpec.rows.last()[5])

        val wideSpec = workbook.toTableSpec(
            sheetIndex = 2,
            options = ExcelTableOptions(headerRowIndex = null),
        )
        assertEquals(28, wideSpec.columns.size)
        assertEquals("AA", wideSpec.columns[26].title)
        assertEquals("测试值_27", wideSpec.rows.first()[26])

        val blankRowSpec = workbook.toTableSpec(sheetIndex = 3)
        assertEquals(6, blankRowSpec.rows.size)
        assertEquals(3, blankRowSpec.rows[2].sheetRowIndex)
        assertTrue(blankRowSpec.rows[2].cells.all(String::isEmpty))
        assertEquals("空行之后", blankRowSpec.rows[3][1])
    }
}

private fun testWorkbookFile(): File {
    val bytes = decodeBase64(EXCEL_FILE_ADAPTER_TEST_BASE64)
    return File(arrayOf(bytes), "excel_file_adapter_test.xlsx")
}

private fun decodeBase64(encoded: String): Uint8Array {
    val padding = encoded.takeLast(2).count { character -> character == '=' }
    val decodedBytes = Array<Byte>(encoded.length / 4 * 3 - padding) { 0 }
    var accumulator = 0
    var bitCount = 0
    var outputIndex = 0

    encoded.forEach { character ->
        if (character == '=') {
            return@forEach
        }
        val value = BASE64_ALPHABET.indexOf(character)
        require(value >= 0) { "Invalid Base64 test fixture" }
        accumulator = (accumulator shl 6) or value
        bitCount += 6
        if (bitCount >= 8) {
            bitCount -= 8
            decodedBytes[outputIndex++] = ((accumulator shr bitCount) and 0xFF).toByte()
        }
    }
    return Uint8Array(decodedBytes.size).also { decoded -> decoded.set(decodedBytes) }
}

private fun runSuspendTest(block: suspend () -> Unit): Promise<Unit> =
    Promise { resolve, reject ->
        block.startCoroutine(
            object : Continuation<Unit> {
                override val context = EmptyCoroutineContext

                override fun resumeWith(result: Result<Unit>) {
                    result.fold(resolve, reject)
                }
            },
        )
    }

private const val BASE64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
