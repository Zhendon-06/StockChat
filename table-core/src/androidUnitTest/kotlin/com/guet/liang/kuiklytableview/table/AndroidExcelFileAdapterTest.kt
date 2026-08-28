package com.guet.liang.kuiklytableview.table

import java.io.File
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AndroidExcelFileAdapterTest {
    @Test
    fun parsesGeneratedWorkbookFromJavaFile() {
        val file = File.createTempFile("excel_file_adapter_test", ".xlsx")
        try {
            file.writeBytes(Base64.getDecoder().decode(EXCEL_FILE_ADAPTER_TEST_BASE64))

            val workbook = parseExcelWorkbook(file)
            val employeeSpec = workbook.toTableSpec()

            assertEquals(
                listOf("员工数据", "说明后表头", "宽表_28列", "空行测试"),
                workbook.sheets.map { sheet -> sheet.name },
            )
            assertEquals(8, employeeSpec.columns.size)
            assertEquals(11, employeeSpec.rows.size)
            assertEquals("张伟", employeeSpec.rows.first()[1])
            assertEquals("2022-03-15", employeeSpec.rows.first()[3])
            assertEquals("¥18,500.00", employeeSpec.rows.first()[4])
            assertEquals("¥20,351.50", workbook.sheet(0).cells[11][6])

            val blankRowSpec = workbook.toTableSpec(sheetIndex = 3)
            assertEquals(3, blankRowSpec.rows[2].sheetRowIndex)
            assertTrue(blankRowSpec.rows[2].cells.all(String::isEmpty))
        } finally {
            file.delete()
        }
    }
}
