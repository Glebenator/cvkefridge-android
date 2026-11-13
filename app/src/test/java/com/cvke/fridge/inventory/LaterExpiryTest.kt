package com.cvke.fridge.inventory

import com.cvke.fridge.core.CalendarDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** The later expiry date for "+1 day", "+1 week", and an expired batch. */
class LaterExpiryTest {
    private val today = date("2026-11-13")

    private fun date(text: String) = checkNotNull(CalendarDate.parse(text))

    @Test
    fun anExpiringBatchGetsTheDaysAfterItsExpiryDate() {
        assertEquals(date("2026-11-15"), LaterExpiry.date(date("2026-11-14"), today, 1))
        assertEquals(date("2026-11-20"), LaterExpiry.date(date("2026-11-13"), today, 7))
    }

    @Test
    fun anExpiredBatchGetsTheDaysAfterToday() {
        assertEquals(date("2026-11-14"), LaterExpiry.date(date("2026-11-10"), today, 1))
        assertEquals(date("2026-11-20"), LaterExpiry.date(date("2026-11-12"), today, 7))
    }

    @Test
    fun aWeekGoesAcrossTheEndOfAMonthAndAYear() {
        assertEquals(date("2027-01-03"), LaterExpiry.date(date("2026-12-27"), date("2026-12-27"), 7))
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroDaysIsNotAChoice() {
        LaterExpiry.date(today, today, 0)
    }
}
