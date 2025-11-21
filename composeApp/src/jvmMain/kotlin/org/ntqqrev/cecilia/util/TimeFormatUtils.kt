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