package com.borealis.planner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class QuickParseTest {
    @Test fun dayTimeAndPriority() {
        val r = QuickParse.parse("Gym tomorrow 6pm !!")
        assertEquals("Gym", r.title)
        assertEquals(1, r.dayOffset)
        assertEquals(18 * 60, r.time)
        assertEquals(Priority.MEDIUM, r.priority)
    }

    @Test fun twentyFourHour() {
        val r = QuickParse.parse("Standup 09:30")
        assertEquals("Standup", r.title)
        assertEquals(9 * 60 + 30, r.time)
    }

    @Test fun atHourGuessesEvening() {
        val r = QuickParse.parse("Call mum at 7")
        assertEquals("Call mum", r.title)
        assertEquals(19 * 60, r.time)
    }

    @Test fun minutesWithAmPm() {
        val r = QuickParse.parse("Dentist at 10:15am today")
        assertEquals("Dentist", r.title)
        assertEquals(0, r.dayOffset)
        assertEquals(10 * 60 + 15, r.time)
    }

    @Test fun noon() {
        assertEquals(12 * 60, QuickParse.parse("Lunch with Sam noon").time)
    }

    @Test fun highPriorityOnly() {
        val r = QuickParse.parse("Pay rent !!!")
        assertEquals("Pay rent", r.title)
        assertEquals(Priority.HIGH, r.priority)
        assertNull(r.time)
    }

    @Test fun plainTitleUntouched() {
        val r = QuickParse.parse("Read chapter 12 of the book")
        assertEquals("Read chapter 12 of the book", r.title)
        assertFalse(r.found)
    }

    @Test fun attachedBangIsNotPriority() {
        val r = QuickParse.parse("Ship it!")
        assertEquals("Ship it!", r.title)
        assertNull(r.priority)
    }

    @Test fun hashtagPicksCategory() {
        val r = QuickParse.parse("Lab report #bio101 tomorrow 9am")
        assertEquals("Lab report", r.title)
        assertEquals("bio101", r.tag)
        assertEquals(1, r.dayOffset)
        assertEquals(9 * 60, r.time)
    }

    @Test fun hashInsideWordIgnored() {
        val r = QuickParse.parse("Fix C# homework")
        assertEquals("Fix C# homework", r.title)
        assertNull(r.tag)
    }

    @Test fun wordsContainingTodayAreKept() {
        val r = QuickParse.parse("Todays notes")
        assertEquals("Todays notes", r.title)
        assertNull(r.dayOffset)
    }
}
