package com.cvke.fridge.core

/**
 * How fresh a batch is.
 *
 * The iOS file `CVKEFridgeCore/Sources/CVKEFridgeCore/Freshness.swift` must give the same results for the same input.
 *
 * @property key The text of the value in the shared specification.
 */
enum class Freshness(val key: String) {
    FRESH("fresh"),
    EXPIRING("expiring"),
    EXPIRED("expired"),
    NO_EXPIRY_DATE("noExpiryDate");

    companion object {
        /** A batch with 0 to this number of days left is expiring. */
        const val expiringWithinDays = 2

        /** The days from [today] to [expiryDate], or null when there is no expiry date. */
        fun daysLeft(today: CalendarDate, expiryDate: CalendarDate?): Int? = expiryDate?.let { today.daysUntil(it) }

        /** The freshness for the days left. */
        fun of(daysLeft: Int?): Freshness = when {
            daysLeft == null -> NO_EXPIRY_DATE
            daysLeft < 0 -> EXPIRED
            daysLeft <= expiringWithinDays -> EXPIRING
            else -> FRESH
        }

        fun fromKey(key: String): Freshness? = entries.firstOrNull { it.key == key }
    }
}
