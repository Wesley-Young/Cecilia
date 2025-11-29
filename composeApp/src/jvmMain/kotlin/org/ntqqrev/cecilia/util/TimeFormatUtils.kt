package org.ntqqrev.cecilia.util

import java.time.Instant
import java.time.ZoneId

fun Instant.formatToShortTime(): String {
    // Within a day: HH:mm
    // Else: MM/dd
    val now = Instant.now()
    val duration = now.epochSecond - this.epochSecond
    val thisZoned = this.atZone(ZoneId.systemDefault())
    return if (duration < 86400) {
        String.format("%02d:%02d", thisZoned.hour, thisZoned.minute)
    } else {
        String.format("%02d/%02d", thisZoned.monthValue, thisZoned.dayOfMonth)
    }
}

fun Instant.formatToConvenientTime(): String {
    // Today: HH:mm
    // Within a year: MM/dd HH:mm
    // Else: YYYY/MM/dd HH:mm
    val now = Instant.now()
    val thisZoned = this.atZone(ZoneId.systemDefault())
    return if (
        now.atZone(ZoneId.systemDefault()).toLocalDate().equals(thisZoned.toLocalDate())
    ) {
        String.format("%02d:%02d", thisZoned.hour, thisZoned.minute)
    } else if (now.atZone(ZoneId.systemDefault()).year == thisZoned.year) {
        String.format(
            "%02d/%02d %02d:%02d",
            thisZoned.monthValue,
            thisZoned.dayOfMonth,
            thisZoned.hour,
            thisZoned.minute
        )
    } else {
        String.format(
            "%04d/%02d/%02d %02d:%02d",
            thisZoned.year,
            thisZoned.monthValue,
            thisZoned.dayOfMonth,
            thisZoned.hour,
            thisZoned.minute
        )
    }
}

fun Instant.formatToFullTime(): String {
    // YYYY-MM-DD HH:mm:ss
    val thisZoned = this.atZone(ZoneId.systemDefault())
    return String.format(
        "%04d-%02d-%02d %02d:%02d:%02d",
        thisZoned.year,
        thisZoned.monthValue,
        thisZoned.dayOfMonth,
        thisZoned.hour,
        thisZoned.minute,
        thisZoned.second
    )
}