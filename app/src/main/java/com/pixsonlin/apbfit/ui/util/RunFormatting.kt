package com.pixsonlin.apbfit.ui.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.math.max

object RunFormatting {
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun formatDateTime(millis: Long): String =
        Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault())
            .format(dateTimeFormatter)

    fun formatTime(millis: Long): String =
        Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault())
            .format(timeFormatter)

    fun formatConfiguredDuration(minutes: Int): String {
        val hours = minutes / 60
        val mins = minutes % 60
        return if (hours > 0) "${hours}h ${mins}m" else "${mins} min"
    }

    fun formatActualDuration(startTime: Long, endTime: Long?): String {
        if (endTime == null) return "—"
        val elapsedMs = max(0L, endTime - startTime)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedMs)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(elapsedMs) % 60
        return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
    }
}
