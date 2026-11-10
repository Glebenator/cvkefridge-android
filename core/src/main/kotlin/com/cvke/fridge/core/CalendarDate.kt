package com.cvke.fridge.core

/**
 * A date in the Gregorian calendar, from 0001-01-01 to 9999-12-31. Only a valid date can exist.
 *
 * The iOS file `CVKEFridgeCore/Sources/CVKEFridgeCore/CalendarDate.swift` must give the same results for the same
 * input. The code counts whole days from 1970-01-01. It does not use a platform date type.
 */
@ConsistentCopyVisibility
data class CalendarDate private constructor(val year: Int, val month: Int, val day: Int) : Comparable<CalendarDate> {

    /** The text form `YYYY-MM-DD`. */
    val text: String
        get() = "${pad(year, 4)}-${pad(month, 2)}-${pad(day, 2)}"

    /** The date that is [days] days later, or earlier for a negative number. It is null outside the years 1 to 9999. */
    fun plusDays(days: Int): CalendarDate? = ofEpochDay(epochDay() + days)

    /** The days from this date to [other]. The number is positive when [other] is later. */
    fun daysUntil(other: CalendarDate): Int = (other.epochDay() - epochDay()).toInt()

    override fun compareTo(other: CalendarDate): Int =
        compareValuesBy(this, other, CalendarDate::year, CalendarDate::month, CalendarDate::day)

    override fun toString(): String = text

    private fun epochDay(): Long {
        val y = (if (month <= 2) year - 1 else year).toLong()
        val era = Math.floorDiv(y, 400L)
        val yearOfEra = y - era * 400
        val dayOfYear = (153 * (if (month > 2) month - 3 else month + 9) + 2) / 5 + day - 1
        val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
        return era * 146097 + dayOfEra - 719468
    }

    companion object {
        private const val FIRST_EPOCH_DAY = -719162L // 0001-01-01
        private const val LAST_EPOCH_DAY = 2932896L // 9999-12-31

        /** The date of a valid year, month, and day, or null. */
        fun of(year: Int, month: Int, day: Int): CalendarDate? {
            if (year !in 1..9999 || month !in 1..12 || day < 1 || day > daysInMonth(year, month)) return null
            return CalendarDate(year, month, day)
        }

        /** The date of a text in the form `YYYY-MM-DD`, or null. A digit is `0` to `9` only. No space is removed. */
        fun parse(text: String): CalendarDate? {
            if (text.length != 10 || text[4] != '-' || text[7] != '-') return null
            var year = 0
            var month = 0
            var day = 0
            for (i in 0 until 10) {
                if (i == 4 || i == 7) continue
                val digit = text[i] - '0'
                if (digit !in 0..9) return null
                when {
                    i < 4 -> year = year * 10 + digit
                    i < 7 -> month = month * 10 + digit
                    else -> day = day * 10 + digit
                }
            }
            return of(year, month, day)
        }

        /**
         * The date of an instant, for a UTC offset in seconds. The instant is in milliseconds from
         * 1970-01-01T00:00:00Z. The result is null outside the years 1 to 9999.
         */
        fun ofInstant(epochMillis: Long, utcOffsetSeconds: Int): CalendarDate? {
            val local = try {
                Math.addExact(epochMillis, utcOffsetSeconds * 1000L)
            } catch (_: ArithmeticException) {
                return null
            }
            return ofEpochDay(Math.floorDiv(local, 86_400_000L))
        }

        private fun pad(number: Int, length: Int): String = number.toString().padStart(length, '0')

        private fun daysInMonth(year: Int, month: Int): Int = when (month) {
            2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }

        private fun ofEpochDay(epochDay: Long): CalendarDate? {
            if (epochDay < FIRST_EPOCH_DAY || epochDay > LAST_EPOCH_DAY) return null
            val z = epochDay + 719468
            val era = Math.floorDiv(z, 146097L)
            val dayOfEra = z - era * 146097
            val yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36524 - dayOfEra / 146096) / 365
            val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
            val monthPart = (5 * dayOfYear + 2) / 153
            val day = (dayOfYear - (153 * monthPart + 2) / 5 + 1).toInt()
            val month = (if (monthPart < 10) monthPart + 3 else monthPart - 9).toInt()
            val year = (yearOfEra + era * 400 + if (month <= 2) 1 else 0).toInt()
            return CalendarDate(year, month, day)
        }
    }
}
