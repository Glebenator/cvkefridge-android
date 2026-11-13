package com.cvke.fridge.settings

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * How the app looks. The app keeps these settings in one small file with DataStore (see
 * [AppearanceSettingsSerializer]). Backup is off, so they stay on the phone.
 *
 * @property wallpaperColours True when the colour scheme comes from the wallpaper of the phone on Android 12 and later
 *   (ADR 0007 and ADR 0008 in the cvkefridge repository). False gives the scheme from the green of FridgeFriend, as on
 *   Android 10 and 11. The colours of fresh, expiring, and expired, and the brand parts, do not change.
 */
@Serializable
data class AppearanceSettings(val wallpaperColours: Boolean = true)

/**
 * Reads and writes [AppearanceSettings] as JSON for a typed DataStore. A file that is not JSON is damaged: DataStore
 * then replaces it with the default settings. A missing value gets its default, and an unknown key is ignored.
 */
object AppearanceSettingsSerializer : Serializer<AppearanceSettings> {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override val defaultValue: AppearanceSettings = AppearanceSettings()

    override suspend fun readFrom(input: InputStream): AppearanceSettings {
        val text = input.readBytes().decodeToString()
        if (text.isBlank()) return defaultValue
        return try {
            json.decodeFromString(AppearanceSettings.serializer(), text)
        } catch (failure: IllegalArgumentException) {
            // A SerializationException is an IllegalArgumentException.
            throw CorruptionException("The appearance settings are not valid", failure)
        }
    }

    override suspend fun writeTo(t: AppearanceSettings, output: OutputStream) {
        output.write(json.encodeToString(AppearanceSettings.serializer(), t).encodeToByteArray())
    }
}
