package com.guet.liang.stockchat.data

internal fun validMarketDate(value: String): Boolean {
    if (!ISO_DATE.matches(value)) return false
    val fields = value.split('-').map(String::toInt)
    val (year, month, day) = fields
    if (year < 1 || month !in 1..MONTHS_PER_YEAR) return false
    return day in 1..marketMonthDays(year, month)
}

internal fun marketMonthDays(year: Int, month: Int): Int = when {
    month == FEBRUARY -> if (marketLeapYear(year)) LEAP_FEBRUARY_DAYS else FEBRUARY_DAYS
    month in SHORT_MONTHS -> SHORT_MONTH_DAYS
    else -> LONG_MONTH_DAYS
}

internal fun marketLeapYear(year: Int): Boolean =
    year % LEAP_YEAR_CYCLE == 0 && (year % CENTURY_CYCLE != 0 || year % LEAP_CENTURY_CYCLE == 0)

private val ISO_DATE = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")
private val SHORT_MONTHS = setOf(4, 6, 9, 11)
private const val MONTHS_PER_YEAR = 12
private const val FEBRUARY = 2
private const val LEAP_FEBRUARY_DAYS = 29
private const val FEBRUARY_DAYS = 28
private const val SHORT_MONTH_DAYS = 30
private const val LONG_MONTH_DAYS = 31
private const val LEAP_YEAR_CYCLE = 4
private const val CENTURY_CYCLE = 100
private const val LEAP_CENTURY_CYCLE = 400
