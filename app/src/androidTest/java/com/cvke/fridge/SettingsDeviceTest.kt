package com.cvke.fridge

import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isOff
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import com.cvke.fridge.settings.AppearanceSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Settings is a tab, and the wallpaper colours can be turned off. The setting stays after a restart of the app. */
class SettingsDeviceTest : DeviceTest() {
    @Test
    fun wallpaperColoursTurnOffAndStayOffAfterARestart() {
        // Android 10 and 11 have no wallpaper colours, so the switch is not there.
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        start(SampleInventory.changes(today()))
        rule.onNodeWithTag("tab_settings").performClick()
        rule.onNodeWithTag("settings_screen").assertIsDisplayed()
        rule.onNodeWithTag("wallpaper_colours_switch").assertIsOn().performClick()
        waitFor(hasTestTag("wallpaper_colours_switch") and isOff())
        assertFalse(runBlocking { app.appearance.data.first().wallpaperColours })

        scenario?.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor(hasTestTag("add_food"))
        rule.onNodeWithTag("tab_settings").performClick()
        rule.onNodeWithTag("wallpaper_colours_switch").assertIsOff()
    }

    @After
    fun resetAppearance() {
        runBlocking { app.appearance.updateData { AppearanceSettings() } }
    }
}
