package com.cvke.fridge.inventory

import com.cvke.fridge.core.CalendarDate

/**
 * The later expiry date of a batch that is expiring or expired, when the person says that the food is still good.
 *
 * The quick choices add days to the expiry date. An expired batch starts from today, not from its expiry date: one more
 * day for food that expired three days ago means good until tomorrow, not still expired.
 */
object LaterExpiry {
    /** The quick choices, in days: one day and one week. */
    val quickDays = listOf(1, 7)

    /** The expiry date [days] after the expiry date, or after [today] when the expiry date is before today. */
    fun date(expiry: CalendarDate, today: CalendarDate, days: Int): CalendarDate {
        require(days > 0) { "The days must be more than 0" }
        return checkNotNull(maxOf(expiry, today).plusDays(days)) { "The date is out of range" }
    }
}
