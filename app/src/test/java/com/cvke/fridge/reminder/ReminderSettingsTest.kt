package com.cvke.fridge.reminder

import androidx.datastore.core.CorruptionException
import com.cvke.fridge.core.Freshness
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

/** The default reminder settings, and the file that keeps them. */
class ReminderSettingsTest {
    private fun read(text: String) = runBlocking { ReminderSettingsSerializer.readFrom(ByteArrayInputStream(text.encodeToByteArray())) }

    private fun write(settings: ReminderSettings) = runBlocking {
        ByteArrayOutputStream().also { ReminderSettingsSerializer.writeTo(settings, it) }.toByteArray().decodeToString()
    }

    @Test
    fun theDefaultsAreOffAt0900WithTheExpiringRule() {
        val settings = ReminderSettingsSerializer.defaultValue
        assertFalse(settings.enabled)
        assertEquals(LocalTime.of(9, 0), settings.time)
        assertEquals(Freshness.expiringWithinDays, settings.leadDays)
        assertFalse(settings.offerDismissed)
        assertFalse(settings.permissionAsked)
        assertEquals(null, settings.next)
        assertEquals(null, settings.last)
        assertEquals(settings, read(""))
    }

    @Test
    fun theSettingsGoThroughTheFile() {
        val settings = ReminderSettings(
            enabled = true,
            minuteOfDay = 7 * 60 + 30,
            leadDays = 0,
            offerDismissed = true,
            permissionAsked = true,
            nextDate = "2026-11-13",
            lastDate = "2026-11-12",
        )
        assertEquals(settings, read(write(settings)))
        assertEquals(LocalTime.of(7, 30), settings.time)
    }

    @Test
    fun aMissingValueGetsItsDefaultAndAnUnknownKeyIsIgnored() {
        assertEquals(ReminderSettings(enabled = true), read("""{"enabled":true,"newerSetting":3}"""))
    }

    @Test
    fun aValueOutOfRangeGetsItsDefault() {
        val settings = read("""{"enabled":true,"minuteOfDay":1440,"leadDays":9,"nextDate":"2026-13-01","lastDate":"yesterday"}""")
        assertEquals(ReminderSettings(enabled = true), settings)
    }

    @Test
    fun aFileThatIsNotJsonIsDamaged() {
        assertThrows(CorruptionException::class.java) { read("not json") }
        assertThrows(CorruptionException::class.java) { read("""{"enabled":"maybe"}""") }
    }
}
