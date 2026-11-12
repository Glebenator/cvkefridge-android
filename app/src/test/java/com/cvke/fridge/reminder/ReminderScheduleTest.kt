package com.cvke.fridge.reminder

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** The dates and the instants of the daily reminder across midnight, daylight saving time, and time zone changes. */
class ReminderScheduleTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private val denver = ZoneId.of("America/Denver")
    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val nine = LocalTime.of(9, 0)

    private fun at(text: String, zone: ZoneId): Instant = ZonedDateTime.of(java.time.LocalDateTime.parse(text), zone).toInstant()

    private fun day(text: String) = LocalDate.parse(text)

    @Test
    fun theFirstReminderIsTodayWhenTheTimeIsAheadAndTomorrowWhenItPassed() {
        assertEquals(day("2026-11-12"), ReminderSchedule.firstDate(at("2026-11-12T08:59", berlin), berlin, nine, null))
        assertEquals(day("2026-11-13"), ReminderSchedule.firstDate(at("2026-11-12T09:00", berlin), berlin, nine, null))
        assertEquals(day("2026-11-13"), ReminderSchedule.firstDate(at("2026-11-12T23:59", berlin), berlin, nine, null))
        // A change of the time after the reminder of today does not give a second reminder today.
        assertEquals(day("2026-11-13"), ReminderSchedule.firstDate(at("2026-11-12T10:00", berlin), berlin, LocalTime.of(18, 0), day("2026-11-12")))
    }

    @Test
    fun theNextReminderAfterARunIsOnTheNextDateAlsoAcrossMidnight() {
        assertEquals(day("2026-11-13"), ReminderSchedule.dateAfterRun(day("2026-11-12")))
        // A reminder at 23:30 that Doze delays to 00:20 belongs to the date on which it ran.
        val late = at("2026-11-13T00:20", berlin)
        val ranOn = ReminderSchedule.dateOf(late, berlin)
        assertEquals(day("2026-11-14"), ReminderSchedule.dateAfterRun(ranOn))
        assertEquals(at("2026-12-31T23:30", berlin), ReminderSchedule.triggerAt(day("2026-12-31"), LocalTime.of(23, 30), berlin))
        assertEquals(at("2027-01-01T09:00", berlin), ReminderSchedule.triggerAt(ReminderSchedule.dateAfterRun(day("2026-12-31")), nine, berlin))
    }

    @Test
    fun theReminderStaysAtTheSameClockTimeWhenDaylightSavingTimeChanges() {
        // Berlin goes from UTC+1 to UTC+2 on 29 March 2026, and back on 25 October 2026.
        assertEquals(Instant.parse("2026-03-28T08:00:00Z"), ReminderSchedule.triggerAt(day("2026-03-28"), nine, berlin))
        assertEquals(Instant.parse("2026-03-29T07:00:00Z"), ReminderSchedule.triggerAt(day("2026-03-29"), nine, berlin))
        assertEquals(Instant.parse("2026-10-25T08:00:00Z"), ReminderSchedule.triggerAt(day("2026-10-25"), nine, berlin))
        // 02:30 does not exist on 29 March: the reminder moves forward by the gap of one hour.
        assertEquals(Instant.parse("2026-03-29T01:30:00Z"), ReminderSchedule.triggerAt(day("2026-03-29"), LocalTime.of(2, 30), berlin))
        // 02:30 exists two times on 25 October: the reminder is at the first.
        assertEquals(Instant.parse("2026-10-25T00:30:00Z"), ReminderSchedule.triggerAt(day("2026-10-25"), LocalTime.of(2, 30), berlin))
    }

    @Test
    fun aTimeZoneChangeMovesTheReminderToTheSameClockTimeInTheNewZone() {
        val stored = day("2026-11-13")
        val now = at("2026-11-12T20:00", berlin)
        assertEquals(stored, ReminderSchedule.restoredDate(stored, now, denver, nine, day("2026-11-12")))
        assertEquals(at("2026-11-13T09:00", denver), ReminderSchedule.triggerAt(stored, nine, denver))
        assertEquals(at("2026-11-13T09:00", tokyo), ReminderSchedule.triggerAt(stored, nine, tokyo))
    }

    @Test
    fun aMissedReminderOfTodayComesAtOnceAndOlderOnesDoNot() {
        // The phone was off at 09:00 and starts at 11:00. The reminder of today has its instant in the past.
        val boot = at("2026-11-12T11:00", berlin)
        val today = ReminderSchedule.restoredDate(day("2026-11-12"), boot, berlin, nine, day("2026-11-11"))
        assertEquals(day("2026-11-12"), today)
        assertEquals(true, ReminderSchedule.triggerAt(today, nine, berlin) < boot)
        // The phone was off for three days: only the reminder of today comes.
        assertEquals(day("2026-11-12"), ReminderSchedule.restoredDate(day("2026-11-09"), boot, berlin, nine, day("2026-11-08")))
        // A flight to the east: in Tokyo it is already 19:00, and the reminder of today did not come.
        val east = at("2026-11-12T19:00", tokyo)
        assertEquals(day("2026-11-12"), ReminderSchedule.restoredDate(day("2026-11-12"), east, tokyo, nine, day("2026-11-11")))
        // The reminder of today came: the next one is tomorrow.
        assertEquals(day("2026-11-13"), ReminderSchedule.restoredDate(day("2026-11-12"), boot, berlin, nine, day("2026-11-12")))
    }

    @Test
    fun aClockThatGoesBackDoesNotStopTheRemindersForDays() {
        // The next reminder was on 20 November, and the clock goes back to 12 November.
        val now = at("2026-11-12T08:00", berlin)
        assertEquals(day("2026-11-12"), ReminderSchedule.restoredDate(day("2026-11-20"), now, berlin, nine, day("2026-11-19")))
        // A clock that goes back one hour after the reminder of today does not give a second reminder today.
        val back = at("2026-11-12T08:30", berlin)
        assertEquals(day("2026-11-13"), ReminderSchedule.restoredDate(day("2026-11-13"), back, berlin, nine, day("2026-11-12")))
    }

    @Test
    fun aLastDateAfterTodayFromAClockThatWentBackIsForgotten() {
        val now = at("2026-11-12T16:30", denver)
        assertEquals(null, ReminderSchedule.knownLastDate(day("2026-11-13"), now, denver))
        assertEquals(day("2026-11-12"), ReminderSchedule.knownLastDate(day("2026-11-12"), now, denver))
        // With the last date forgotten, the reminder of 13 November comes on 13 November.
        assertEquals(day("2026-11-13"), ReminderSchedule.restoredDate(day("2026-11-14"), now, denver, LocalTime.of(16, 20), null))
    }

    @Test
    fun noStoredDateGivesTheFirstDate() {
        assertEquals(day("2026-11-12"), ReminderSchedule.restoredDate(null, at("2026-11-12T07:00", berlin), berlin, nine, null))
    }
}
