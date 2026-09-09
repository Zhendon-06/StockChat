package com.guet.liang.stockchat.data

// 预测时间戳解析、比较与排序校验。

internal fun predictionTimestampsMatch(first: String, second: String): Boolean {
    val firstTrimmed = first.trim()
    val secondTrimmed = second.trim()
    if (firstTrimmed == secondTrimmed) return true
    val firstComparable = comparablePredictionTimestamp(firstTrimmed) ?: return false
    val secondComparable = comparablePredictionTimestamp(secondTrimmed) ?: return false
    if (firstComparable == secondComparable) return true
    return stripPredictionTimestampZone(firstComparable) ==
        stripPredictionTimestampZone(secondComparable)
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

private enum class PredictionTimestampKind {
    NUMERIC,
    CALENDAR,
}

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
        val numeric = trimmed.toLongOrNull() ?: return null
        if (numeric <= 0L) return null
        return ParsedPredictionTimestamp(
            kind = PredictionTimestampKind.NUMERIC,
            comparableValue = numeric.toString(),
        )
    }
    val match = CALENDAR_TIMESTAMP_PATTERN.matchEntire(trimmed) ?: return null
    val year = match.groupValues[1].toIntOrNull() ?: return null
    val month = match.groupValues[2].toIntOrNull() ?: return null
    val day = match.groupValues[3].toIntOrNull() ?: return null
    if (year <= 0 || month !in 1..12 || day !in 1..daysInMonth(year, month)) {
        return null
    }
    val hourText = match.groupValues[4]
    val minuteText = match.groupValues[5]
    val secondText = match.groupValues[6]
    val fractionText = match.groupValues[7]
    val zoneText = match.groupValues[8]
    if (hourText.isBlank() != minuteText.isBlank() ||
        (secondText.isNotBlank() && minuteText.isBlank()) ||
        (fractionText.isNotBlank() && secondText.isBlank())
    ) {
        return null
    }
    if (hourText.isNotBlank()) {
        val hour = hourText.toIntOrNull() ?: return null
        val minute = minuteText.toIntOrNull() ?: return null
        val second = secondText.ifBlank { "00" }.toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59 || second !in 0..59) return null
    } else if (zoneText.isNotBlank()) {
        return null
    }
    if (zoneText.isNotBlank() && zoneText != "Z") {
        val zoneDigits = zoneText.removePrefix("+").removePrefix("-").replace(":", "")
        val zoneHour = zoneDigits.take(2).toIntOrNull() ?: return null
        val zoneMinute = zoneDigits.drop(2).toIntOrNull() ?: return null
        if (zoneHour !in 0..23 || zoneMinute !in 0..59) return null
    }
    val normalizedDate = "${match.groupValues[1]}-${match.groupValues[2]}-${match.groupValues[3]}"
    val normalizedTime = if (hourText.isBlank()) {
        "00:00:00"
    } else {
        val normalizedFraction = fractionText.takeIf(String::isNotBlank)
            ?.padEnd(9, '0')
            ?.let { ".$it" }
            .orEmpty()
        "${hourText}:${minuteText}:${secondText.ifBlank { "00" }}$normalizedFraction"
    }
    val normalizedZone = when {
        zoneText.isBlank() -> ""
        zoneText == "Z" -> "Z"
        else -> zoneText.replace(Regex("""([+-]\d{2})(\d{2})$"""), "$1:$2")
    }
    return ParsedPredictionTimestamp(
        kind = PredictionTimestampKind.CALENDAR,
        comparableValue = "$normalizedDate $normalizedTime$normalizedZone",
    )
}

private fun comparePredictionTimestamps(first: String, second: String): Int? {
    val parsedFirst = parsePredictionTimestamp(first) ?: return null
    val parsedSecond = parsePredictionTimestamp(second) ?: return null
    if (parsedFirst.kind != parsedSecond.kind) return null
    return when (parsedFirst.kind) {
        PredictionTimestampKind.NUMERIC -> {
            val firstNumber = parsedFirst.comparableValue.toLongOrNull() ?: return null
            val secondNumber = parsedSecond.comparableValue.toLongOrNull() ?: return null
            firstNumber.compareTo(secondNumber)
        }
        PredictionTimestampKind.CALENDAR ->
            parsedFirst.comparableValue.compareTo(parsedSecond.comparableValue)
    }
}

private fun daysInMonth(year: Int, month: Int): Int {
    return when (month) {
        2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }
}
