package com.school.servicemonitor.ui.components

import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy")

fun LocalDate.display(): String = format(dateFormat)

/** 600 -> "10 hours", 60 -> "1 hour", 90 -> "1h 30m". Durations are stored as minutes. */
fun formatMinutes(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        m == 0 && h == 1 -> "1 hour"
        m == 0 -> "$h hours"
        h == 0 -> "$m min"
        else -> "${h}h ${m}m"
    }
}

/** 1700000000000 -> "Nov 12, 2026 4:05 PM" */
fun formatTimestamp(millis: Long): String =
    java.time.Instant.ofEpochMilli(millis)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", java.util.Locale.US))