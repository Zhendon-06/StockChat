package com.guet.liang.kuiklytableview.table

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.io.InputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.roundToLong

internal class XlsxWorkbookParser(
    private val file: File,
) {
    fun parse(): ExcelWorkbook = ZipFile(file).use { archive ->
        val workbookDocument = archive.readXml(WORKBOOK_PART)
        val relationships = archive.readRelationships()
        val sharedStrings = archive.readOptionalXml(SHARED_STRINGS_PART)
            ?.elements("si")
            ?.map { element -> element.allText() }
            .orEmpty()
        val styles = archive.readOptionalXml(STYLES_PART)?.readCellStyles().orEmpty()
        val uses1904DateSystem = workbookDocument
            .elements("workbookPr")
            .firstOrNull()
            ?.attribute("date1904")
            .toBooleanValue()

        val sheets = workbookDocument.elements("sheet").map { sheetElement ->
            val relationshipId = sheetElement.getAttributeNS(OFFICE_RELATIONSHIPS_NAMESPACE, "id")
                .ifEmpty { sheetElement.attribute("r:id") }
            val worksheetPart = relationships[relationshipId]
                ?: throw ExcelFileException(
                    "Excel worksheet '${sheetElement.attribute("name")}' has no package relationship",
                )
            ExcelSheet(
                name = sheetElement.attribute("name"),
                cells = archive.readWorksheet(
                    partName = worksheetPart,
                    sharedStrings = sharedStrings,
                    styles = styles,
                    uses1904DateSystem = uses1904DateSystem,
                ),
            )
        }

        if (sheets.isEmpty()) {
            throw ExcelFileException("Excel workbook does not contain any sheets")
        }
        ExcelWorkbook(sheets)
    }

    private fun ZipFile.readRelationships(): Map<String, String> =
        readXml(WORKBOOK_RELATIONSHIPS_PART)
            .elements("Relationship")
            .filter { relationship ->
                relationship.attribute("Type").endsWith("/worksheet") &&
                    relationship.attribute("TargetMode") != "External"
            }
            .associate { relationship ->
                relationship.attribute("Id") to resolvePart(
                    sourcePart = WORKBOOK_PART,
                    target = relationship.attribute("Target"),
                )
            }

    private fun ZipFile.readWorksheet(
        partName: String,
        sharedStrings: List<String>,
        styles: List<XlsxCellStyle>,
        uses1904DateSystem: Boolean,
    ): List<List<String>> {
        val rowValues = sortedMapOf<Int, List<String>>()
        readXml(partName).elements("row").forEachIndexed { fallbackRowIndex, rowElement ->
            val rowIndex = rowElement.attribute("r").toIntOrNull()?.minus(1) ?: fallbackRowIndex
            val cells = sortedMapOf<Int, String>()
            var fallbackColumnIndex = 0
            rowElement.childElements("c").forEach { cellElement ->
                val columnIndex = cellElement.attribute("r")
                    .takeIf(String::isNotEmpty)
                    ?.let(::columnIndexFromReference)
                    ?: fallbackColumnIndex
                val style = cellElement.attribute("s")
                    .toIntOrNull()
                    ?.let(styles::getOrNull)
                cells[columnIndex] = cellElement.displayValue(
                    sharedStrings = sharedStrings,
                    style = style,
                    uses1904DateSystem = uses1904DateSystem,
                )
                fallbackColumnIndex = columnIndex + 1
            }
            val columnCount = cells.lastKeyOrNull()?.plus(1) ?: 0
            rowValues[rowIndex] = List(columnCount) { columnIndex -> cells[columnIndex].orEmpty() }
        }

        val rowCount = rowValues.lastKeyOrNull()?.plus(1) ?: 0
        return List(rowCount) { rowIndex -> rowValues[rowIndex].orEmpty() }
            .dropLastWhile { row -> row.all(String::isEmpty) }
    }

    private fun Element.displayValue(
        sharedStrings: List<String>,
        style: XlsxCellStyle?,
        uses1904DateSystem: Boolean,
    ): String {
        val rawValue = childElements("v").firstOrNull()?.textContent.orEmpty()
        return when (attribute("t")) {
            "s" -> rawValue.toIntOrNull()?.let(sharedStrings::getOrNull).orEmpty()
            "inlineStr" -> childElements("is").firstOrNull()?.allText().orEmpty()
            "b" -> if (rawValue == "1") "TRUE" else "FALSE"
            "d", "str", "e" -> rawValue
            else -> XlsxDisplayFormatter.format(
                rawValue = rawValue,
                style = style,
                uses1904DateSystem = uses1904DateSystem,
            )
        }
    }

    private fun ZipFile.readOptionalXml(partName: String): Document? =
        getEntry(partName)?.let { entry ->
            requireReadableEntry(entry.name, entry.size)
            getInputStream(entry).use(::parseXml)
        }

    private fun ZipFile.readXml(partName: String): Document = readOptionalXml(partName)
        ?: throw ExcelFileException("Excel package is missing '$partName'")

    private fun Document.readCellStyles(): List<XlsxCellStyle> {
        val customFormats = elements("numFmt").associate { element ->
            element.attribute("numFmtId").toInt() to element.attribute("formatCode")
        }
        val cellXfs = elements("cellXfs").firstOrNull() ?: return emptyList()
        return cellXfs.childElements("xf").map { element ->
            val numberFormatId = element.attribute("numFmtId").toIntOrNull() ?: 0
            XlsxCellStyle(
                numberFormatId = numberFormatId,
                numberFormatCode = customFormats[numberFormatId] ?: BUILT_IN_NUMBER_FORMATS[numberFormatId],
            )
        }
    }

    private fun parseXml(input: InputStream): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            setSecureFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setSecureFeature("http://xml.org/sax/features/external-general-entities", false)
            setSecureFeature("http://xml.org/sax/features/external-parameter-entities", false)
            runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "") }
            runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "") }
        }
        return factory.newDocumentBuilder().parse(input)
    }

    private fun DocumentBuilderFactory.setSecureFeature(name: String, value: Boolean) {
        runCatching { setFeature(name, value) }
    }

    private fun requireReadableEntry(partName: String, uncompressedSize: Long) {
        if (uncompressedSize > MAX_XML_PART_BYTES) {
            throw ExcelFileException("Excel package part '$partName' is too large to display")
        }
    }

    private fun resolvePart(sourcePart: String, target: String): String {
        if (target.startsWith('/')) {
            return target.removePrefix("/")
        }
        val segments = sourcePart.substringBeforeLast('/').split('/').toMutableList()
        target.replace('\\', '/').split('/').forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.lastIndex)
                else -> segments += segment
            }
        }
        return segments.joinToString("/")
    }

    private fun columnIndexFromReference(reference: String): Int {
        var columnIndex = 0
        var letterCount = 0
        reference.forEach { character ->
            if (!character.isLetter()) {
                return@forEach
            }
            columnIndex = columnIndex * 26 + (character.uppercaseChar() - 'A' + 1)
            letterCount++
        }
        return if (letterCount == 0) 0 else columnIndex - 1
    }

    private fun Element.attribute(name: String): String = getAttribute(name).orEmpty()

    private fun Document.elements(localName: String): List<Element> =
        documentElement.elements(localName)

    private fun Element.elements(localName: String): List<Element> =
        getElementsByTagNameNS("*", localName).asElementList()

    private fun Element.childElements(localName: String): List<Element> =
        childNodes.asElementList().filter { element -> element.localName == localName }

    private fun org.w3c.dom.NodeList.asElementList(): List<Element> = buildList {
        for (index in 0 until length) {
            val node = item(index)
            if (node.nodeType == Node.ELEMENT_NODE) {
                add(node as Element)
            }
        }
    }

    private fun Element.allText(): String = elements("t").joinToString(separator = "") { it.textContent }

    private fun <ValueT> Map<Int, ValueT>.lastKeyOrNull(): Int? = keys.maxOrNull()

    private fun String?.toBooleanValue(): Boolean = this == "1" || equals("true", ignoreCase = true)

    private companion object {
        const val WORKBOOK_PART = "xl/workbook.xml"
        const val WORKBOOK_RELATIONSHIPS_PART = "xl/_rels/workbook.xml.rels"
        const val SHARED_STRINGS_PART = "xl/sharedStrings.xml"
        const val STYLES_PART = "xl/styles.xml"
        const val OFFICE_RELATIONSHIPS_NAMESPACE =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        const val MAX_XML_PART_BYTES = 64L * 1024L * 1024L

        val BUILT_IN_NUMBER_FORMATS = mapOf(
            1 to "0",
            2 to "0.00",
            3 to "#,##0",
            4 to "#,##0.00",
            9 to "0%",
            10 to "0.00%",
            11 to "0.00E+00",
            12 to "# ?/?",
            13 to "# ??/??",
            14 to "m/d/yy",
            15 to "d-mmm-yy",
            16 to "d-mmm",
            17 to "mmm-yy",
            18 to "h:mm AM/PM",
            19 to "h:mm:ss AM/PM",
            20 to "h:mm",
            21 to "h:mm:ss",
            22 to "m/d/yy h:mm",
            37 to "#,##0 ;(#,##0)",
            38 to "#,##0 ;[Red](#,##0)",
            39 to "#,##0.00;(#,##0.00)",
            40 to "#,##0.00;[Red](#,##0.00)",
            45 to "mm:ss",
            46 to "[h]:mm:ss",
            47 to "mmss.0",
            49 to "@",
        )
    }
}

private data class XlsxCellStyle(
    val numberFormatId: Int,
    val numberFormatCode: String?,
)

private object XlsxDisplayFormatter {
    private val decimalSymbols = DecimalFormatSymbols(Locale.US)
    private val dateFormatIds = setOf(14, 15, 16, 17, 18, 19, 20, 21, 22, 45, 46, 47)

    fun format(
        rawValue: String,
        style: XlsxCellStyle?,
        uses1904DateSystem: Boolean,
    ): String {
        if (rawValue.isEmpty()) {
            return ""
        }
        val number = rawValue.toDoubleOrNull() ?: return rawValue
        val formatCode = style?.numberFormatCode
        return when {
            style?.numberFormatId in dateFormatIds || formatCode.isDateFormat() ->
                formatDate(number, formatCode, uses1904DateSystem)
            formatCode.isNullOrEmpty() || formatCode.equals("General", ignoreCase = true) ->
                formatGeneralNumber(rawValue)
            formatCode == "@" -> rawValue
            else -> formatDecimal(number, formatCode)
        }
    }

    private fun formatGeneralNumber(rawValue: String): String = runCatching {
        BigDecimal(rawValue).stripTrailingZeros().toPlainString()
    }.getOrDefault(rawValue)

    private fun formatDate(
        serialValue: Double,
        formatCode: String?,
        uses1904DateSystem: Boolean,
    ): String {
        if (!uses1904DateSystem && serialValue >= 60.0 && serialValue < 61.0) {
            return "1900-02-29"
        }
        val unixEpochSerial = if (uses1904DateSystem) 24_107.0 else 25_569.0
        val epochMillis = ((serialValue - unixEpochSerial) * MILLIS_PER_DAY).roundToLong()
        return SimpleDateFormat(formatCode.toJavaDatePattern(), Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(epochMillis))
    }

    private fun formatDecimal(number: Double, formatCode: String): String {
        val section = formatCode.substringBefore(';').normalizeNumberFormat()
        val firstPlaceholder = section.indexOfFirst { character -> character == '0' || character == '#' || character == '?' }
        val lastPlaceholder = section.indexOfLast { character -> character == '0' || character == '#' || character == '?' }
        if (firstPlaceholder < 0 || lastPlaceholder < firstPlaceholder) {
            return formatGeneralNumber(number.toString())
        }

        val placeholderSection = section.substring(firstPlaceholder, lastPlaceholder + 1)
        if (placeholderSection.contains('E', ignoreCase = true)) {
            return DecimalFormat("0.00E0", decimalSymbols).format(number)
        }
        if (placeholderSection.contains('/')) {
            return formatGeneralNumber(number.toString())
        }

        val decimalPart = placeholderSection.substringAfter('.', missingDelimiterValue = "")
        val requiredDecimalCount = decimalPart.count { character -> character == '0' }
        val optionalDecimalCount = decimalPart.count { character -> character == '#' || character == '?' }
        val integerPattern = if (placeholderSection.substringBefore('.').contains(',')) "#,##0" else "0"
        val decimalPattern = buildString {
            repeat(requiredDecimalCount) { append('0') }
            repeat(optionalDecimalCount) { append('#') }
        }
        val pattern = if (decimalPattern.isEmpty()) integerPattern else "$integerPattern.$decimalPattern"
        val percentage = section.contains('%')
        val formattedNumber = DecimalFormat(pattern, decimalSymbols).apply {
            roundingMode = RoundingMode.HALF_UP
        }.format(if (percentage) number * 100.0 else number)
        val prefix = section.substring(0, firstPlaceholder).cleanLiteral()
        val suffix = section.substring(lastPlaceholder + 1).cleanLiteral().replace("%", "")
        return prefix + formattedNumber + if (percentage) "%" else suffix
    }

    private fun String?.isDateFormat(): Boolean {
        if (this.isNullOrEmpty()) {
            return false
        }
        val normalized = lowercase(Locale.US)
            .replace(Regex("\"[^\"]*\""), "")
            .replace(Regex("\\\\."), "")
            .replace(Regex("\\[(?!h]|m]|s])[^]]*]"), "")
        return Regex("(^|[^a-z])[ymdhis]+([^a-z]|$)").containsMatchIn(normalized)
    }

    private fun String?.toJavaDatePattern(): String {
        val normalized = this.orEmpty().lowercase(Locale.US).replace("\\", "")
        val hasDate = normalized.contains('y') || normalized.contains('d')
        val hasSeconds = normalized.contains('s')
        val hasAmPm = normalized.contains("am/pm")
        if (!hasDate) {
            return when {
                hasSeconds && hasAmPm -> "h:mm:ss a"
                hasSeconds -> "HH:mm:ss"
                hasAmPm -> "h:mm a"
                else -> "HH:mm"
            }
        }
        val datePattern = when {
            normalized.contains("yyyy-mm-dd") -> "yyyy-MM-dd"
            normalized.contains("dd/mm/yyyy") -> "dd/MM/yyyy"
            normalized.contains("mm/dd/yyyy") -> "MM/dd/yyyy"
            normalized.contains("d-mmm-yy") -> "d-MMM-yy"
            normalized.contains("d-mmm") -> "d-MMM"
            normalized.contains("mmm-yy") -> "MMM-yy"
            normalized.contains("m/d/yy") -> "M/d/yy"
            else -> "yyyy-MM-dd"
        }
        return if (normalized.contains('h')) "$datePattern HH:mm" else datePattern
    }

    private fun String.normalizeNumberFormat(): String {
        val source = this
        return buildString {
            var index = 0
            while (index < source.length) {
                when (val character = source[index]) {
                    '\\' -> {
                        if (index + 1 < source.length) append(source[index + 1])
                        index += 2
                    }
                    '_', '*' -> index += 2
                    '"' -> {
                        val closingQuote = source.indexOf('"', index + 1)
                        if (closingQuote < 0) {
                            index++
                        } else {
                            append(source.substring(index + 1, closingQuote))
                            index = closingQuote + 1
                        }
                    }
                    '[' -> {
                        val closingBracket = source.indexOf(']', index + 1)
                        if (closingBracket < 0) {
                            index++
                        } else {
                            val bracketContent = source.substring(index + 1, closingBracket)
                            if (bracketContent.startsWith('$')) {
                                append(bracketContent.removePrefix("$").substringBefore('-'))
                            }
                            index = closingBracket + 1
                        }
                    }
                    else -> {
                        append(character)
                        index++
                    }
                }
            }
        }.trim()
    }

    private fun String.cleanLiteral(): String =
        replace("?", "")
            .replace("@", "")
            .replace("(", "")
            .replace(")", "")
            .trim()

    private const val MILLIS_PER_DAY = 86_400_000.0
}
