package com.cvke.fridge.reminder

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The reminder settings of the person, and the state of the schedule. The app keeps them in one small file with
 * DataStore (see [ReminderSettingsSerializer]).
 *
 * @property enabled True when the person turned reminders on.
 * @property minuteOfDay The time of the reminder, in minutes after midnight on the clock of the device.
 * @property leadDays The days before the expiry date from which a batch is in the reminder (see [ReminderRules]).
 * @property offerDismissed True when the person closed the offer of reminders on Home, or did not allow notifications
 *   from it. Home does not show the offer again. The settings screen always has the switch.
 * @property permissionAsked True after the app asked Android for the notification permission one time. With
 *   "Android does not show the question" from Android, the app can then tell a permission that is blocked from a
 *   permission that the app did not ask for.
 * @property nextDate The date of the next reminder, in the form `YYYY-MM-DD`, or null when reminders are off.
 * @property lastDate The date on which the last reminder ran, or null. The reminder of a date runs one time only.
 */
@Serializable
data class ReminderSettings(
    val enabled: Boolean = false,
    val minuteOfDay: Int = DEFAULT_MINUTE_OF_DAY,
    val leadDays: Int = ReminderRules.DEFAULT_LEAD_DAYS,
    val offerDismissed: Boolean = false,
    val permissionAsked: Boolean = false,
    val nextDate: String? = null,
    val lastDate: String? = null,
) {
    /** The time of the reminder on the clock of the device. */
    val time: LocalTime
        get() = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)

    val next: LocalDate?
        get() = nextDate.toDate()

    val last: LocalDate?
        get() = lastDate.toDate()

    /** The same settings with each value in its range. A value from an older or a damaged file can be out of range. */
    fun normalized(): ReminderSettings = copy(
        minuteOfDay = minuteOfDay.takeIf { it in 0 until MINUTES_IN_DAY } ?: DEFAULT_MINUTE_OF_DAY,
        leadDays = leadDays.takeIf { it in ReminderRules.leadDayChoices } ?: ReminderRules.DEFAULT_LEAD_DAYS,
        nextDate = nextDate?.takeIf { it.toDate() != null },
        lastDate = lastDate?.takeIf { it.toDate() != null },
    )

    companion object {
        /** 09:00, before the day of most people starts. */
        const val DEFAULT_MINUTE_OF_DAY = 9 * 60
        const val MINUTES_IN_DAY = 24 * 60

        private fun String?.toDate(): LocalDate? = this?.let {
            try {
                LocalDate.parse(it)
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }
}

/**
 * Reads and writes [ReminderSettings] as JSON for a typed DataStore. A file that is not JSON is damaged: DataStore then
 * replaces it with the default settings. A missing value gets its default, and an unknown key is ignored, so a newer or
 * older version of the app can read the file.
 */
object ReminderSettingsSerializer : Serializer<ReminderSettings> {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override val defaultValue: ReminderSettings = ReminderSettings()

    override suspend fun readFrom(input: InputStream): ReminderSettings {
        val text = input.readBytes().decodeToString()
        if (text.isBlank()) return defaultValue
        return try {
            json.decodeFromString(ReminderSettings.serializer(), text).normalized()
        } catch (failure: IllegalArgumentException) {
            // A SerializationException is an IllegalArgumentException.
            throw CorruptionException("The reminder settings are not valid", failure)
        }
    }

    override suspend fun writeTo(t: ReminderSettings, output: OutputStream) {
        output.write(json.encodeToString(ReminderSettings.serializer(), t).encodeToByteArray())
    }
}
