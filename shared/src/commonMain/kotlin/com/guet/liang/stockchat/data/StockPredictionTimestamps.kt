package com.guet.liang.stockchat.data

// 预测时间戳解析、比较与排序校验。

internal fun predictionTimestampsMatch(first: String, second: String): Boolean {
    val firstTrimmed = first.trim()
    val secondTrimmed = second.trim()
    if (firstTrimmed == secondTrimmed) return true
    val firstComparable = comparablePredictionTimestamp(firstTrimmed)
    val secondComparable = comparablePredictionTimestamp(secondTrimmed)
    return when {
        firstComparable == null || secondComparable == null -> false
        firstComparable == secondComparable -> true
        else -> stripPredictionTimestampZone(firstComparable) == stripPredictionTimestampZone(secondComparable)
    }
}

private fun stripPredictionTimestampZone(value: String): String {
    return value.removeSuffix("Z")
        .replace(Regex("[+-]\\d{2}:?\\d{2}$"), "")
}

private fun comparablePredictionTimestamp(value: String): String? {
    parsePredictionTimestamp(value)?.let { return it.comparableValue }
    return TIMESTAMP_TOKEN_PATTERN.findAll(value)
        .mapNotNull { match -> parsePredictionTimestamp(match.value)?.comparableValue }
        .firstOrNull()
}

internal fun String.isUnknownMarketTimestamp(): Boolean {
    return contains("时间未知", ignoreCase = true) ||
        contains("unknown", ignoreCase = true)
}

internal fun timestampsInOrder(timestamps: List<String>): Boolean {
    if (timestamps.isEmpty() || timestamps.any(String::isBlank) ||
        timestamps.distinct().size != timestamps.size
    ) {
        return false
    }
    return timestamps.zipWithNext().all { (previous, current) ->
        comparePredictionTimestamps(previous, current)?.let { it < 0 } == true
    }
}

internal fun timestampIsAfter(candidate: String, reference: String): Boolean {
    return comparePredictionTimestamps(reference, candidate)?.let { it < 0 } == true
}

internal fun isSupportedTimestamp(value: String): Boolean {
    return parsePredictionTimestamp(value) != null
}

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
private enum class PredictionTimestampKind {
    NUMERIC,
    CALENDAR,
}

/** Shared cross-platform type; this declaration defines a stable contract for callers. */
private data class ParsedPredictionTimestamp(
    val kind: PredictionTimestampKind,
    val comparableValue: String,
)

private val NUMERIC_TIMESTAMP_PATTERN = Regex("^[0-9]{8,17}$")

private val TIMESTAMP_TOKEN_PATTERN = Regex(
    "(?<!\\d)(?:\\d{4}[-/]\\d{2}[-/]\\d{2}(?:[T\\s]\\d{2}:\\d{2}(?::\\d{2}(?:\\.\\d{1,9})?)?(?:Z|[+-]\\d{2}:?\\d{2})?)?|\\d{8,17})(?!\\d)"
)

private val CALENDAR_TIMESTAMP_PATTERN = Regex(
    """^(\d{4})[-/](\d{2})[-/](\d{2})(?:[T\s](\d{2}):(\d{2})(?::(\d{2})(?:\.(\d{1,9}))?)?(Z|[+-]\d{2}:?\d{2})?)?$""",
)

private fun parsePredictionTimestamp(value: String): ParsedPredictionTimestamp? {
    val trimmed = value.trim()
    if (trimmed.matches(NUMERIC_TIMESTAMP_PATTERN)) {
        return trimmed.toLongOrNull()?.takeIf { it > 0L }?.let {
            ParsedPredictionTimestamp(PredictionTimestampKind.NUMERIC, it.toString())
        }
    }
    val match = CALENDAR_TIMESTAMP_PATTERN.matchEntire(trimmed) ?: return null
    return CalendarTimestampFields(match.groupValues).takeIf { it.valid() }?.let {
        ParsedPredictionTimestamp(PredictionTimestampKind.CALENDAR, it.normalized())
    }
}

/** Regex-validated calendar components before range validation and normalization. */
private class CalendarTimestampFields(private val groups: List<String>) {
    private val date = listOf(field(TimestampField.YEAR), field(TimestampField.MONTH), field(TimestampField.DAY)).joinToString("-")
    private val hour = field(TimestampField.HOUR)
    private val minute = field(TimestampField.MINUTE)
    private val second = field(TimestampField.SECOND)
    private val fraction = field(TimestampField.FRACTION)
    private val zone = field(TimestampField.ZONE)

    fun valid(): Boolean = validMarketDate(date) && validTime() && validZone()

    private fun validTime(): Boolean {
        val incompleteSeconds = second.isNotBlank() && minute.isBlank()
        val incompleteFraction = fraction.isNotBlank() && second.isBlank()
        if (hour.isBlank() != minute.isBlank() || incompleteSeconds || incompleteFraction) return false
        return if (hour.isBlank()) zone.isBlank() else {
            hour.toInt() in 0 until HOURS_PER_DAY && minute.toInt() in 0 until MINUTES_PER_HOUR &&
                second.ifBlank { "00" }.toInt() in 0 until SECONDS_PER_MINUTE
        }
    }

    private fun validZone(): Boolean {
        if (zone.isBlank() || zone == "Z") return true
        val digits = zone.removePrefix("+").removePrefix("-").replace(":", "")
        return digits.take(TIME_FIELD_WIDTH).toInt() in 0 until HOURS_PER_DAY &&
            digits.drop(TIME_FIELD_WIDTH).toInt() in 0 until MINUTES_PER_HOUR
    }

    fun normalized(): String {
        val normalizedTime = if (hour.isBlank()) "00:00:00" else {
            val normalizedFraction = fraction.takeIf(String::isNotBlank)?.padEnd(NANOSECOND_DIGITS, '0')?.let { ".$it" }.orEmpty()
            "$hour:$minute:${second.ifBlank { "00" }}$normalizedFraction"
        }
        val normalizedZone = zone.replace(Regex("""([+-]\d{2})(\d{2})$"""), "$1:$2")
        return "$date $normalizedTime$normalizedZone"
    }

    private fun field(field: TimestampField): String = groups[field.index]
}

/** Positional groups in the calendar timestamp regular expression. */
private enum class TimestampField(val index: Int) {
    YEAR(1), MONTH(2), DAY(3), HOUR(4), MINUTE(5), SECOND(6), FRACTION(7), ZONE(8),
}

private const val HOURS_PER_DAY = 24
private const val MINUTES_PER_HOUR = 60
private const val SECONDS_PER_MINUTE = 60
private const val TIME_FIELD_WIDTH = 2
private const val NANOSECOND_DIGITS = 9

private fun comparePredictionTimestamps(first: String, second: String): Int? {
    val parsedFirst = parsePredictionTimestamp(first) ?: return null
    val parsedSecond = parsePredictionTimestamp(second) ?: return null
    if (parsedFirst.kind != parsedSecond.kind) return null
    return when (parsedFirst.kind) {
        PredictionTimestampKind.NUMERIC -> {
            val firstNumber = parsedFirst.comparableValue.toLong()
            val secondNumber = parsedSecond.comparableValue.toLong()
            firstNumber.compareTo(secondNumber)
        }
        PredictionTimestampKind.CALENDAR ->
            parsedFirst.comparableValue.compareTo(parsedSecond.comparableValue)
    }
}
