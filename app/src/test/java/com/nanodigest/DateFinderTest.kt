package com.nanodigest

import com.nanodigest.core.text.DateFinder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class DateFinderTest {

    // Wednesday 25 Nov 2026, 09:00
    private val ref = LocalDateTime.of(2026, 11, 25, 9, 0)

    @Test
    fun weekdayWithTime() {
        val e = DateFinder.find("• Decision: release Thursday 10 AM, 10% rollout (Priya).", ref)
        assertEquals(LocalDateTime.of(2026, 11, 26, 10, 0), e.first().start)
        assertFalse(e.first().allDay)
        assertEquals("Release Thursday 10 AM".lowercase(), e.first().title.lowercase())
    }

    @Test
    fun severalDatesInOneSentence() {
        val e = DateFinder.find("Release Thursday 10 AM, full rollout Friday if stable.", ref)
        assertEquals(2, e.size)
        assertTrue(e[1].allDay)
        assertEquals(LocalDateTime.of(2026, 11, 27, 0, 0), e[1].start)
    }

    @Test
    fun tomorrowAndPmTimes() {
        val e = DateFinder.find("Dinner tomorrow at 8:30pm at Mom's.", ref).single()
        assertEquals(LocalDateTime.of(2026, 11, 26, 20, 30), e.start)
    }

    @Test
    fun monthDayRollsToNextYearWhenPast() {
        val e = DateFinder.find("Goa trip moved to March 14.", ref).single()
        assertEquals(LocalDateTime.of(2027, 3, 14, 0, 0), e.start)
        assertTrue(e.allDay)
    }

    @Test
    fun dayMonthOrderAnd24h() {
        val e = DateFinder.find("Meeting on 3rd December 14:30.", ref).single()
        assertEquals(LocalDateTime.of(2026, 12, 3, 14, 30), e.start)
    }

    @Test
    fun sameWeekdayMeansToday() {
        val e = DateFinder.find("Standup Wednesday 4 PM", ref).single()
        assertEquals(LocalDateTime.of(2026, 11, 25, 16, 0), e.start)
    }

    @Test
    fun ignoresTextWithoutDates() {
        assertTrue(DateFinder.find("Dev found a crash on Android 12 when opening the camera.", ref).isEmpty())
        // "may" as a verb is not a month without a day number.
        assertTrue(DateFinder.find("We may ship soon.", ref).isEmpty())
    }
}
