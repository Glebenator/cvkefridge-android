package com.cvke.fridge.reminder

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The dates and the times of the daily reminder. The app keeps the date of the next reminder, not an instant. The
 * instant comes from that date, the time of the settings, and the time zone of the device at the moment of the
 * calculation. A change of the time zone, of the clock, or of daylight saving time then moves the reminder to the same
 * time on the clock of the device.
 *
 * Rules:
 * - There is not more than one reminder on one date of the device.
 * - When the person turns reminders on or changes the time, the first reminder is today if the time is still ahead
 *   and today had no reminder. Otherwise it is tomorrow. A change never sends a reminder at once.
 * - After a reminder, the next one is on the next date.
 * - When the phone was off, or the clock or the time zone changed, at the time of a reminder, the reminder of today
 *   comes when the app can run again, on the same date. The reminders of earlier dates do not come.
 */
object ReminderSchedule {
    /** The date of an instant in a time zone. */
    fun dateOf(instant: Instant, zone: ZoneId): LocalDate = instant.atZone(zone).toLocalDate()

    /** The instant of the reminder of [date]. A time that the clock skips (daylight saving time) moves forward by the gap. */
    fun triggerAt(date: LocalDate, time: LocalTime, zone: ZoneId): Instant = ZonedDateTime.of(date, time, zone).toInstant()

    /** The date of the first reminder after the person turns reminders on or changes the time. */
    fun firstDate(now: Instant, zone: ZoneId, time: LocalTime, lastDate: LocalDate?): LocalDate {
        val today = dateOf(now, zone)
        return if (lastDate != today && triggerAt(today, time, zone).isAfter(now)) today else today.plusDays(1)
    }

    /** The date of the next reminder after the reminder of [ranOn], the date of the device when it ran. */
    fun dateAfterRun(ranOn: LocalDate): LocalDate = ranOn.plusDays(1)

    /**
     * The date of the next reminder after a restart of the phone, an update or a start of the app, or a change of the
     * clock or the time zone. A [stored] date in the past becomes today, so that the reminder of today comes. Its
     * instant can then be in the past, and the alarm goes off at once. A stored date more than one day ahead (the clock
     * went back) is calculated again.
     */
    fun restoredDate(stored: LocalDate?, now: Instant, zone: ZoneId, time: LocalTime, lastDate: LocalDate?): LocalDate {
        val today = dateOf(now, zone)
        return when {
            stored == null || stored > today.plusDays(1) -> firstDate(now, zone, time, lastDate)
            lastDate == today -> today.plusDays(1)
            stored < today -> today
            else -> stored
        }
    }
}
