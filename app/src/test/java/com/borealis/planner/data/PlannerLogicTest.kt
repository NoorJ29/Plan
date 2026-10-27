package com.borealis.planner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlannerLogicTest {
    // Monday 26 Oct 2026
    private val mon = LocalDate.of(2026, 10, 26).toEpochDay()
    private val now = 1_000_000L

    private fun draft(title: String, day: Long = mon, repeat: Repeat = Repeat.NONE, weekdays: Set<Int> = emptySet(), time: Int? = null) =
        TaskDraft(title = title, day = day, repeat = repeat, weekdays = weekdays, time = time)

    @Test fun addOneOff() {
        val s = PlannerLogic.add(PlannerState(), draft("Write report"), mon)
        assertEquals(1, s.tasks.size)
        assertEquals("Write report", s.tasks[0].title)
        assertNull(s.tasks[0].seriesId)
    }

    @Test fun blankTitleIgnored() {
        val s0 = PlannerState()
        assertSame(s0, PlannerLogic.add(s0, draft("   "), mon))
    }

    @Test fun alertDroppedWithoutTime() {
        val s = PlannerLogic.add(PlannerState(), draft("x").copy(alert = AlertType.ALARM), mon)
        assertEquals(AlertType.NONE, s.tasks[0].alert)
    }

    @Test fun dailySeriesGeneratesTodayAndTomorrow() {
        val s = PlannerLogic.add(PlannerState(), draft("Water plants", repeat = Repeat.DAILY), mon)
        assertEquals(1, s.series.size)
        assertEquals(setOf(mon, mon + 1), s.tasks.map { it.day }.toSet())
        // Generating again is a no-op.
        assertSame(s, PlannerLogic.generate(s, listOf(mon, mon + 1)))
    }

    @Test fun weekdaysSkipWeekend() {
        val sat = mon + 5
        val s = PlannerLogic.add(PlannerState(), draft("Standup", day = mon, repeat = Repeat.WEEKDAYS), mon)
        val g = PlannerLogic.generate(s, (mon..mon + 6).toList())
        assertEquals(5, g.tasks.size)
        assertFalse(g.tasks.any { it.day == sat || it.day == sat + 1 })
    }

    @Test fun weeklyFirstOccurrenceCreatedEvenIfLater() {
        // Created on Monday for Wednesdays only.
        val s = PlannerLogic.add(PlannerState(), draft("Gym", repeat = Repeat.WEEKLY, weekdays = setOf(3)), mon)
        assertEquals(listOf(mon + 2), s.tasks.map { it.day })
    }

    @Test fun monthlyOn31stFallsBackToLastDay() {
        val jan31 = LocalDate.of(2027, 1, 31).toEpochDay()
        val series = Series(title = "Rent", repeat = Repeat.MONTHLY, startDay = jan31)
        assertTrue(PlannerLogic.matches(series, LocalDate.of(2027, 2, 28).toEpochDay()))
        assertFalse(PlannerLogic.matches(series, LocalDate.of(2027, 2, 27).toEpochDay()))
        assertTrue(PlannerLogic.matches(series, LocalDate.of(2027, 3, 31).toEpochDay()))
    }

    @Test fun missedAndMoveRecordsSlip() {
        var s = PlannerLogic.add(PlannerState(), draft("Call bank", day = mon), mon)
        val id = s.tasks[0].id
        val tue = mon + 1
        assertEquals(1, PlannerLogic.missed(s, tue).size)
        s = PlannerLogic.move(s, id, tue, tue)
        assertEquals(0, PlannerLogic.missed(s, tue).size)
        assertEquals(listOf(mon), s.tasks[0].slips)
        val recapMon = PlannerLogic.recap(s, mon, tue)
        assertEquals(1, recapMon.missed.size)
        assertEquals(1, PlannerLogic.recap(s, tue, tue).open.size)
    }

    @Test fun movingFutureTaskIsNotASlip() {
        var s = PlannerLogic.add(PlannerState(), draft("Plan trip", day = mon + 3), mon)
        s = PlannerLogic.move(s, s.tasks[0].id, mon + 4, mon)
        assertTrue(s.tasks[0].slips.isEmpty())
    }

    @Test fun autoCarryOnRollover() {
        var s = PlannerLogic.add(PlannerState(), draft("Email"), mon)
        s = s.copy(settings = s.settings.copy(autoCarry = true))
        s = PlannerLogic.rollover(s, mon + 1)
        assertEquals(mon + 1, s.tasks[0].day)
        assertEquals(listOf(mon), s.tasks[0].slips)
        assertEquals(mon + 1, s.lastRollover)
    }

    @Test fun recurringNotCarriedOrMissed() {
        var s = PlannerLogic.add(PlannerState(), draft("Meds", repeat = Repeat.DAILY), mon)
        s = s.copy(settings = s.settings.copy(autoCarry = true))
        s = PlannerLogic.rollover(s, mon + 1)
        assertTrue(PlannerLogic.missed(s, mon + 1).isEmpty())
        assertEquals(1, PlannerLogic.recap(s, mon, mon + 1).missed.size)
        assertEquals(setOf(mon, mon + 1, mon + 2), s.tasks.map { it.day }.toSet())
    }

    @Test fun movingRecurringInstanceDoesNotRegenerate() {
        var s = PlannerLogic.add(PlannerState(), draft("Walk", repeat = Repeat.DAILY), mon)
        val tomorrowTask = s.tasks.first { it.day == mon + 1 }
        s = PlannerLogic.move(s, tomorrowTask.id, mon + 3, mon)
        s = PlannerLogic.generate(s, listOf(mon + 1))
        assertEquals(0, s.tasks.count { it.day == mon + 1 })
    }

    @Test fun deleteSeriesStopsFuture() {
        var s = PlannerLogic.add(PlannerState(), draft("Journal", repeat = Repeat.DAILY), mon)
        val todayTask = s.tasks.first { it.day == mon }
        s = PlannerLogic.setDone(s, todayTask.id, true, now)
        val tomorrowTask = s.tasks.first { it.day == mon + 1 }
        s = PlannerLogic.delete(s, tomorrowTask.id, wholeSeries = true, today = mon)
        assertEquals(1, s.tasks.size) // finished one stays for the recap
        s = PlannerLogic.generate(s, (mon..mon + 5).toList())
        assertEquals(1, s.tasks.size)
    }

    @Test fun deleteOneInstanceSkipsDay() {
        var s = PlannerLogic.add(PlannerState(), draft("Journal", repeat = Repeat.DAILY), mon)
        val t = s.tasks.first { it.day == mon }
        s = PlannerLogic.delete(s, t.id, wholeSeries = false, today = mon)
        s = PlannerLogic.generate(s, listOf(mon))
        assertEquals(0, s.tasks.count { it.day == mon })
    }

    @Test fun lastSubtaskCompletesTask() {
        val d = draft("Pack").copy(subtasks = listOf(Subtask(id = "a", title = "Socks"), Subtask(id = "b", title = "Charger")))
        var s = PlannerLogic.add(PlannerState(), d, mon)
        val id = s.tasks[0].id
        s = PlannerLogic.toggleSubtask(s, id, "a", now)
        assertFalse(s.tasks[0].done)
        s = PlannerLogic.toggleSubtask(s, id, "b", now)
        assertTrue(s.tasks[0].done)
        s = PlannerLogic.toggleSubtask(s, id, "b", now)
        assertFalse(s.tasks[0].done)
    }

    @Test fun nextUpPrefersUpcomingTimed() {
        var s = PlannerState()
        s = PlannerLogic.add(s, draft("Anytime"), mon)
        s = PlannerLogic.add(s, draft("Morning", time = 8 * 60), mon)
        s = PlannerLogic.add(s, draft("Evening", time = 18 * 60), mon)
        assertEquals("Evening", PlannerLogic.nextUp(s, mon, 12 * 60)?.title)
        assertEquals("Morning", PlannerLogic.nextUp(s, mon, 7 * 60)?.title)
    }

    @Test fun snoozeMovesAlert() {
        var s = PlannerLogic.add(PlannerState(), draft("Stretch", time = 9 * 60).copy(alert = AlertType.REMINDER), mon)
        val base = PlannerLogic.alertAt(s.tasks[0])
        assertNotNull(base)
        s = PlannerLogic.snooze(s, s.tasks[0].id, 10, base!!)
        assertEquals(base + 600_000L, PlannerLogic.alertAt(s.tasks[0]))
        s = PlannerLogic.setDone(s, s.tasks[0].id, true, now)
        assertNull(PlannerLogic.alertAt(s.tasks[0]))
    }

    @Test fun updateTurnsOffRepeat() {
        var s = PlannerLogic.add(PlannerState(), draft("Run", repeat = Repeat.DAILY), mon)
        val t = s.tasks.first { it.day == mon }
        s = PlannerLogic.update(s, t.id, PlannerLogic.draftOf(t, s.series[0]).copy(repeat = Repeat.NONE), allUpcoming = false, today = mon)
        assertNull(s.tasks.first { it.id == t.id }.seriesId)
        assertEquals(1, s.tasks.size)
        assertSame(s, PlannerLogic.generate(s, (mon..mon + 4).toList()))
    }

    @Test fun updateAllUpcomingRenames() {
        var s = PlannerLogic.add(PlannerState(), draft("Run", repeat = Repeat.DAILY), mon)
        val t = s.tasks.first { it.day == mon }
        s = PlannerLogic.update(s, t.id, PlannerLogic.draftOf(t, s.series[0]).copy(title = "Run 5k"), allUpcoming = true, today = mon)
        assertTrue(s.tasks.all { it.title == "Run 5k" })
        assertEquals("Run 5k", s.series[0].title)
    }

    @Test fun jsonRoundTrip() {
        var s = PlannerLogic.add(PlannerState(), draft("Weekly review", repeat = Repeat.WEEKLY, weekdays = setOf(5), time = 17 * 60), mon)
        s = PlannerLogic.add(s, draft("Buy milk").copy(priority = Priority.HIGH, subtasks = listOf(Subtask(title = "Oat"))), mon)
        s = s.copy(settings = s.settings.copy(lockCard = true, widget = WidgetStyle(WidgetTheme.EMBER, 0.5f, false)), widgetTomorrow = setOf(42))
        val back = Json.decode(Json.encode(s))
        assertEquals(s, back)
    }
}
