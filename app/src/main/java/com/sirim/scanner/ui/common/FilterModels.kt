package com.sirim.scanner.ui.common

import java.util.concurrent.TimeUnit

enum class VerifiedFilter {
    ALL,
    VERIFIED,
    UNVERIFIED
}

enum class DateRangeFilter(val days: Long?) {
    ALL(null),
    LAST_7_DAYS(7),
    LAST_30_DAYS(30)
}

fun DateRangeFilter.startTimestamp(now: Long = System.currentTimeMillis()): Long? =
    days?.let { now - TimeUnit.DAYS.toMillis(it) }
