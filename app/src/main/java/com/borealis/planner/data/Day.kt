package com.borealis.planner.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Helpers around epoch-day numbers, which is how every task stores its date. */
object Day {
    fun today(): Long = LocalDate.now().toEpochDay()

    fun date(day: Long): LocalDate = LocalDate.ofEpochDay(day)

    fun dayOfWeek(day: Long): Int = date(day).dayOfWeek.value

    fun fromUtcMillis(millis: Long): Long = Math.floorDiv(millis, 86_400_000L)

    fun toUtcMillis(day: Long): Long = day * 86_400_000L

    fun epochMillis(day: Long, minutes: Int, zone: ZoneId = ZoneId.systemDefault()): Long {
        val time = LocalTime.of((minutes / 60).coerceIn(0, 23), (minutes % 60).coerceIn(0, 59))
        return LocalDateTime.of(date(day), time).atZone(zone).toInstant().toEpochMilli()
    }

    fun minutesNow(): Int = LocalTime.now().let { it.hour * 60 + it.minute }

    fun label(day: Long, today: Long): String = when (day - today) {
        0L -> "Today"
        1L -> "Tomorrow"
        -1L -> "Yesterday"
        else -> date(day).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
    }

    /** "Mon 26" */
    fun short(day: Long): String =
        date(day).format(DateTimeFormatter.ofPattern("EEE d", Locale.getDefault()))

    /** "TUESDAY · 27 OCT" */
    fun eyebrow(day: Long): String {
        val d = date(day)
        val weekday = d.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault()))
        val rest = d.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
        return "$weekday · $rest".uppercase(Locale.getDefault())
    }

    fun weekdayLetter(isoDay: Int): String = when (isoDay) {
        1 -> "M"; 2 -> "T"; 3 -> "W"; 4 -> "T"; 5 -> "F"; 6 -> "S"; else -> "S"
    }

    fun weekdayShort(isoDay: Int): String = when (isoDay) {
        1 -> "Mon"; 2 -> "Tue"; 3 -> "Wed"; 4 -> "Thu"; 5 -> "Fri"; 6 -> "Sat"; else -> "Sun"
    }

    fun formatTime(minutes: Int, use24h: Boolean): String {
        val h = minutes / 60
        val m = minutes % 60
        if (use24h) return "%02d:%02d".format(h, m)
        val h12 = if (h % 12 == 0) 12 else h % 12
        val suffix = if (h < 12) "AM" else "PM"
        return "%d:%02d %s".format(h12, m, suffix)
    }

    fun formatLead(minutes: Int): String = when {
        minutes == 0 -> "At time"
        minutes < 60 -> "$minutes min before"
        minutes % 60 == 0 -> "${minutes / 60} hr before"
        else -> "${minutes / 60} hr ${minutes % 60} min before"
    }
}
