package com.guet.liang.kuiklytableview.table

import com.tencent.kuikly.core.base.Color
import com.tencent.kuikly.core.reactive.collection.ObservableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TableDslTest {
    private data class Person(val id: Int, val name: String)

    @Test
    fun buildsTypedColumnsAndGeometry() {
        val rows = ObservableList(mutableListOf(Person(1, "Ada")))
        val spec = tableSpec<Person> {
            rows(rows)
            rowHeight = 52f
            padding(horizontal = 10f, vertical = 6f)
            columns {
                column("id", "ID", width = 80f) {
                    alignment = TableAlignment.Center
                    value { it.id.toString() }
                }
                column("name", "Name", width = 160f) {
                    value { it.name }
                    cell { _ -> }
                    header { _ -> }
                }
            }
        }

        assertEquals(240f, spec.contentWidth)
        assertEquals(52f, spec.style.rowHeight)
        assertEquals("Ada", spec.columns[1].value(rows.first()))
        assertNotNull(spec.columns[1].cellRenderer)
        assertNotNull(spec.columns[1].headerRenderer)
    }

    @Test
    fun calculatesSharedColumnSeparatorOffsets() {
        val spec = tableSpec<Person> {
            rows(ObservableList())
            columns {
                column("id", "ID", width = 80f)
                column("name", "Name", width = 160f)
                column("note", "Note", width = 120f)
            }
        }

        assertEquals(
            listOf(80f, 240f),
            calculateColumnSeparatorOffsets(spec.columns).toList(),
        )
    }

    @Test
    fun sizesLazyRowWindowForTheViewport() {
        assertEquals(21, calculateLazyRowWindow(bodyHeight = 460f, rowHeight = 46f))
        assertEquals(12, calculateLazyRowWindow(bodyHeight = 46f, rowHeight = 46f))
        assertEquals(30, calculateLazyRowWindow(bodyHeight = 920f, rowHeight = 46f))
        assertEquals(41, calculateLazyRowWindow(bodyHeight = 1_242f, rowHeight = 46f))
        assertEquals(
            Int.MAX_VALUE,
            calculateLazyRowWindow(bodyHeight = Float.MAX_VALUE, rowHeight = Float.MIN_VALUE),
        )
    }

    @Test
    fun rejectsInvalidLazyRowWindowInputs() {
        assertFailsWith<IllegalArgumentException> {
            calculateLazyRowWindow(bodyHeight = 0f, rowHeight = 46f)
        }
        assertFailsWith<IllegalArgumentException> {
            calculateLazyRowWindow(bodyHeight = 460f, rowHeight = Float.POSITIVE_INFINITY)
        }
        assertFailsWith<IllegalArgumentException> {
            calculateLazyRowWindow(bodyHeight = 460f, rowHeight = 46f, maxWindow = 11)
        }
    }

    @Test
    fun acceptsStableRowKeysForMutableDataSets() {
        val rows = ObservableList(mutableListOf(Person(42, "Ada")))
        val spec = tableSpec<Person> {
            rows(rows)
            rowKey { it.id }
            columns { column("name", "Name") { value { it.name } } }
        }

        assertEquals("42", spec.rowKey(rows.first(), 0))
    }

    @Test
    fun rejectsBlankStableRowKeys() {
        val rows = ObservableList(mutableListOf(Person(42, "Ada")))
        val spec = tableSpec<Person> {
            rows(rows)
            rowKey { "   " }
            columns { column("name", "Name") { value { it.name } } }
        }

        assertFailsWith<IllegalArgumentException> {
            spec.rowKey(rows.first(), 0)
        }
    }

    @Test
    fun onEditMarksColumnEditableAndDeliversContext() {
        val rows = ObservableList(mutableListOf(Person(1, "Ada")))
        val edits = mutableListOf<TableEditContext<Person>>()
        val spec = tableSpec<Person> {
            rows(rows)
            columns {
                column("id", "ID") {
                    value { it.id.toString() }
                }
                column("name", "Name") {
                    value { it.name }
                    onEdit { context -> edits += context }
                }
                column("note", "Note") {
                    value { "" }
                    editable = true
                }
            }
        }

        assertFalse(spec.columns[0].editable)
        assertTrue(spec.columns[1].editable)
        assertTrue(spec.columns[2].editable)

        val column = spec.columns[1]
        column.editHandler?.invoke(
            TableEditContext(
                row = rows.first(),
                rowIndex = 0,
                column = column,
                columnIndex = 1,
                oldValue = "Ada",
                newValue = "Grace",
            ),
        )

        assertEquals(1, edits.size)
        assertEquals("Ada", edits.single().oldValue)
        assertEquals("Grace", edits.single().newValue)
    }

    @Test
    fun specCreatesOwnEditBufferOrUsesProvidedOne() {
        val implicit = tableSpec<Person> {
            rows(ObservableList())
            columns { column("id", "ID") }
        }
        assertTrue(implicit.editBuffer.isEmpty())

        val shared = TableEditBuffer<Person>()
        val explicit = tableSpec<Person> {
            rows(ObservableList())
            editBuffer(shared)
            columns { column("id", "ID") }
        }
        assertEquals(shared, explicit.editBuffer)
    }

    @Test
    fun rejectsDuplicateColumnIds() {
        val error = assertFailsWith<IllegalArgumentException> {
            tableSpec<Person> {
                rows(ObservableList())
                columns {
                    column("id", "First")
                    column("id", "Second")
                }
            }
        }

        assertEquals(true, error.message?.contains("Duplicate"))
    }

    @Test
    fun rejectsInvalidDimensions() {
        assertFailsWith<IllegalArgumentException> {
            tableSpec<Person> {
                rows(ObservableList())
                columns {
                    column("id", "ID", width = 0f)
                }
            }
        }
    }

    @Test
    fun configuresHeaderColorAndBorderWidthThroughDsl() {
        val headerColor = Color(0xFF7C3AED)
        val spec = tableSpec<Person> {
            rows(ObservableList())
            border { width = 2.5f }
            header { backgroundColor = headerColor }
            columns { column("name", "Name") }
        }

        assertEquals(headerColor.hexColor, spec.header.backgroundColor.hexColor)
        assertEquals(2.5f, spec.style.border.width)
    }

    @Test
    fun styleBuilderBuildsOptionalStyleOverrides() {
        val borderColor = Color(0xFF22C55E)
        val cellPadding = TablePadding(top = 4f, left = 8f, bottom = 6f, right = 10f)

        val options = TableStyleBuilder().apply {
            this.borderColor = borderColor
            this.cellPadding = cellPadding
            alignment = TableAlignment.End
        }.build()

        assertEquals(borderColor.hexColor, options.borderColor?.hexColor)
        assertEquals(cellPadding, options.cellPadding)
        assertEquals(TableAlignment.End, options.alignment)
    }

    @Test
    fun styleBuilderAppliesVisualAndLayoutOverrides() {
        val headerColor = Color(0xFF0F766E)
        val borderColor = Color(0xFF0891B2)
        val cellPadding = TablePadding(top = 3f, left = 7f, bottom = 5f, right = 9f)
        val spec = tableSpec<Person> {
            rows(ObservableList())
            style {
                preset(TableStylePreset.Blue)
                headerBackgroundColor = headerColor
                borderWidth = 3f
                this.borderColor = borderColor
                this.cellPadding = cellPadding
                alignment = TableAlignment.Center
            }
            columns { column("name", "Name") }
        }

        assertEquals(headerColor.hexColor, spec.header.backgroundColor.hexColor)
        assertEquals(3f, spec.style.border.width)
        assertEquals(borderColor.hexColor, spec.style.border.color.hexColor)
        assertEquals(cellPadding, spec.style.padding)
        assertEquals(TableAlignment.Center, spec.style.alignment)
    }

    @Test
    fun styleBuilderKeepsDslValuesWhenOptionalOverridesAreNull() {
        val borderColor = Color(0xFF64748B)
        val cellPadding = TablePadding(top = 4f, left = 6f, bottom = 5f, right = 7f)
        val spec = tableSpec<Person> {
            rows(ObservableList())
            border { color = borderColor }
            padding(top = 4f, left = 6f, bottom = 5f, right = 7f)
            alignment = TableAlignment.End
            style { density = TableDensity.Compact }
            columns { column("name", "Name") }
        }

        assertEquals(borderColor.hexColor, spec.style.border.color.hexColor)
        assertEquals(cellPadding, spec.style.padding)
        assertEquals(TableAlignment.End, spec.style.alignment)
    }

    @Test
    fun appliesStylePresetAndBorderOptions() {
        val spec = tableSpec<Person> {
            rows(ObservableList(mutableListOf(Person(1, "Ada"))))
            style {
                preset(TableStylePreset.Blue)
                borders { preset(TableBorderPreset.Header) }
            }
            columns { column("name", "Name") { value { it.name } } }
        }

        assertEquals(TableHeaderStyle.Accent, spec.style.headerStyle)
        assertEquals(TableBorderOptions.forPreset(TableBorderPreset.Header), spec.style.borders)
        assertTrue(spec.style.stripedRows)
    }

    @Test
    fun withStyleReusesRowsColumnsAndEditBuffer() {
        val rows = ObservableList(mutableListOf(Person(1, "Ada")))
        val buffer = TableEditBuffer<Person>()
        val spec = tableSpec<Person> {
            rows(rows)
            editBuffer(buffer)
            columns { column("name", "Name") { value { it.name } } }
        }

        val styled = spec.withStyle(
            TableStyleOptions(
                density = TableDensity.Compact,
                borders = TableBorderOptions.forPreset(TableBorderPreset.Row),
            ),
        )

        assertEquals(spec.rows, styled.rows)
        assertEquals(spec.columns, styled.columns)
        assertEquals(buffer, styled.editBuffer)
        assertEquals(TableDensity.Compact, styled.style.density)
        assertEquals(
            TablePadding(top = 6f, left = 12f, bottom = 6f, right = 12f),
            styled.style.padding,
        )
    }

    @Test
    fun withStyleOverridesVisualAndLayoutValues() {
        val originalHeaderColor = Color(0xFF1E293B)
        val styledHeaderColor = Color(0xFFEA580C)
        val originalBorderColor = Color(0xFF94A3B8)
        val styledBorderColor = Color(0xFFF97316)
        val originalPadding = TablePadding(top = 2f, left = 4f, bottom = 2f, right = 4f)
        val styledPadding = TablePadding(top = 10f, left = 12f, bottom = 14f, right = 16f)
        val spec = tableSpec<Person> {
            rows(ObservableList())
            border {
                width = 0.5f
                color = originalBorderColor
            }
            padding(horizontal = 4f, vertical = 2f)
            alignment = TableAlignment.Start
            header { backgroundColor = originalHeaderColor }
            columns { column("name", "Name") }
        }

        val styled = spec.withStyle(
            TableStyleOptions(
                headerBackgroundColor = styledHeaderColor,
                borderWidth = 4f,
                borderColor = styledBorderColor,
                cellPadding = styledPadding,
                alignment = TableAlignment.End,
            ),
        )

        assertEquals(styledHeaderColor.hexColor, styled.header.backgroundColor.hexColor)
        assertEquals(4f, styled.style.border.width)
        assertEquals(styledBorderColor.hexColor, styled.style.border.color.hexColor)
        assertEquals(styledPadding, styled.style.padding)
        assertEquals(TableAlignment.End, styled.style.alignment)
        assertEquals(0.5f, spec.style.border.width)
        assertEquals(originalBorderColor.hexColor, spec.style.border.color.hexColor)
        assertEquals(originalPadding, spec.style.padding)
        assertEquals(TableAlignment.Start, spec.style.alignment)
        assertEquals(originalHeaderColor.hexColor, spec.header.backgroundColor.hexColor)
    }

    @Test
    fun withStyleKeepsBaseValuesWhenOptionalOverridesAreNull() {
        val borderColor = Color(0xFF475569)
        val cellPadding = TablePadding(top = 3f, left = 5f, bottom = 4f, right = 7f)
        val spec = tableSpec<Person> {
            rows(ObservableList())
            border {
                width = 1.5f
                color = borderColor
            }
            padding(top = 3f, left = 5f, bottom = 4f, right = 7f)
            alignment = TableAlignment.Center
            columns { column("name", "Name") }
        }

        val styled = spec.withStyle(
            TableStyleOptions(
                borderWidth = null,
                borderColor = null,
                cellPadding = null,
                alignment = null,
            ),
        )

        assertEquals(1.5f, styled.style.border.width)
        assertEquals(borderColor.hexColor, styled.style.border.color.hexColor)
        assertEquals(cellPadding, styled.style.padding)
        assertEquals(TableAlignment.Center, styled.style.alignment)
    }

    @Test
    fun rejectsNegativeAndNonFiniteBorderWidths() {
        listOf(-0.5f, Float.NaN, Float.POSITIVE_INFINITY).forEach { invalidWidth ->
            assertFailsWith<IllegalArgumentException> {
                tableSpec<Person> {
                    rows(ObservableList())
                    border { width = invalidWidth }
                    columns { column("name", "Name") }
                }
            }
        }

        val spec = tableSpec<Person> {
            rows(ObservableList())
            columns { column("name", "Name") }
        }
        assertEquals(0f, spec.withStyle(TableStyleOptions(borderWidth = 0f)).style.border.width)
        assertFailsWith<IllegalArgumentException> {
            spec.withStyle(TableStyleOptions(borderWidth = Float.NEGATIVE_INFINITY))
        }
    }
}
