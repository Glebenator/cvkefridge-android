package com.cvke.fridge.reminder

import org.junit.Assert.assertEquals
import org.junit.Test

/** The notification access from what Android says. */
class NotificationAccessTest {
    private fun access(
        sdk: Int = 35,
        granted: Boolean = true,
        appEnabled: Boolean = true,
        channelOff: Boolean = false,
        mayAskAgain: Boolean = false,
        asked: Boolean = false,
    ) = NotificationAccess.of(sdk, granted, appEnabled, channelOff, mayAskAgain, asked)

    @Test
    fun android13AndLaterAsksExplainsAgainAndThenSendsToTheSettings() {
        // A permission that is off is also off for areNotificationsEnabled.
        assertEquals(NotificationAccess.ASK, access(granted = false, appEnabled = false))
        assertEquals(NotificationAccess.DENIED, access(granted = false, appEnabled = false, mayAskAgain = true, asked = true))
        assertEquals(NotificationAccess.BLOCKED, access(granted = false, appEnabled = false, asked = true))
        assertEquals(NotificationAccess.ALLOWED, access())
    }

    @Test
    fun beforeAndroid13OnlyTheSettingsCanTurnNotificationsOff() {
        assertEquals(NotificationAccess.ALLOWED, access(sdk = 32, granted = true))
        assertEquals(NotificationAccess.BLOCKED, access(sdk = 29, appEnabled = false))
    }

    @Test
    fun theReminderCategoryCanBeOffAlone() {
        assertEquals(NotificationAccess.CHANNEL_OFF, access(channelOff = true))
        assertEquals(NotificationAccess.CHANNEL_OFF, access(sdk = 29, channelOff = true))
        // The notifications of the whole app come first.
        assertEquals(NotificationAccess.BLOCKED, access(appEnabled = false, channelOff = true))
    }
}
