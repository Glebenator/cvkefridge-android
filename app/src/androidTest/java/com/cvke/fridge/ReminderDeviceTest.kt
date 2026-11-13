package com.cvke.fridge

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Notification
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isOff
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.cvke.fridge.core.Inventory
import com.cvke.fridge.reminder.ReminderNotifier
import com.cvke.fridge.reminder.ReminderSchedule
import com.cvke.fridge.reminder.ReminderSettings
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * The daily reminder on the device: the explanation before the permission question of Android, the settings screen
 * and its file, the work of the alarm with the real change log and the real notification, and a tap on the reminder.
 *
 * A test cannot take the notification permission away from the app: Android then stops the process of the test. So
 * the first test starts with the permission of a new install (not allowed), and the tests run in the order of their
 * names. The test of the permission is first. The others allow the permission with the shell of the test, not with the
 * dialog of Android. Before a run on your own device, remove the debug app, or the first test fails.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ReminderDeviceTest : DeviceTest() {
    private val automation get() = InstrumentationRegistry.getInstrumentation().uiAutomation
    private val notifications get() = app.getSystemService(NotificationManager::class.java)

    private fun granted() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun allowNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) automation.grantRuntimePermission(app.packageName, Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun settings(): ReminderSettings = runBlocking { app.reminders.settings.first() }

    private fun replaceSettings(settings: ReminderSettings) = runBlocking { app.reminders.replaceSettings(settings) }

    private fun reminder(): StatusBarNotification? = notifications.activeNotifications.firstOrNull { it.id == ReminderNotifier.NOTIFICATION_ID }

    /**
     * The apps of the windows on the screen: the app, the question of Android, the settings of Android, and other
     * windows of the system, for example a dialog of an app that stopped.
     */
    private fun windowPackages(): List<String> = automation.windows.mapNotNull { it.root?.packageName?.toString() }

    /**
     * Waits for a window of [packageName]. On Android 16, a dialog of the system about another app, for example "Play
     * Store keeps stopping" on the emulator, hides the other windows from the test while it is in front. So the test
     * closes such a dialog with the system back button, not more than one time each second.
     */
    private fun waitForWindowOf(packageName: (String) -> Boolean) {
        var lastBack = 0L
        rule.waitUntil(TIMEOUT_MILLIS) {
            val packages = windowPackages()
            if (SYSTEM_PACKAGE in packages && SystemClock.uptimeMillis() - lastBack > 1_000) {
                automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                lastBack = SystemClock.uptimeMillis()
            }
            packages.any(packageName)
        }
    }

    /**
     * Closes the window of [packageName] with the system back button. The back button goes to the window in front, which
     * can be a window of another app. So the test presses it again while the window is there.
     */
    private fun closeWindowOf(packageName: (String) -> Boolean) {
        repeat(BACK_ATTEMPTS) {
            if (windowPackages().none(packageName)) return
            assertTrue(automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK))
            runCatching { rule.waitUntil(TIMEOUT_MILLIS / BACK_ATTEMPTS) { windowPackages().none(packageName) } }
        }
        assertTrue("A window of Android stays open", windowPackages().none(packageName))
    }

    private fun String.isQuestionOfAndroid() = endsWith("permissioncontroller")

    private fun String.isSettingsOfAndroid() = this == "com.android.settings"

    @Before
    fun resetReminders() {
        // The test reads all windows on the screen, not only the window in front.
        automation.serviceInfo = automation.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
        replaceSettings(ReminderSettings())
    }

    @After
    fun removeReminders() {
        replaceSettings(ReminderSettings())
        notifications.cancelAll()
    }

    @Test
    fun notificationPermissionIsExplainedBeforeAndroidAsksAndTheSettingsCanTurnItOn() {
        assertFalse("Remove the debug app from the device before the device tests", granted())
        start(SampleInventory.changes(today()))

        // Home offers reminders, because the inventory has food with an expiry date. Nothing asks at the start.
        waitFor(hasTestTag("reminder_offer"))
        rule.onNodeWithTag("reminder_offer").performScrollTo().assertIsDisplayed()
        assertTrue(windowPackages().none { it.isQuestionOfAndroid() })
        rule.onNodeWithTag("reminder_offer_turn_on").performClick()

        // The app explains the reminders first. Then "Continue" shows the question of Android in front of the app.
        rule.onNodeWithTag("notifications_explain").assertIsDisplayed()
        rule.onNodeWithTag("notifications_continue").performClick()
        waitForWindowOf { it.isQuestionOfAndroid() }
        rule.waitUntil(TIMEOUT_MILLIS) { settings().permissionAsked }

        // The person closes the question with no answer. Reminders stay off, and Home does not offer them again.
        closeWindowOf { it.isQuestionOfAndroid() }
        waitUntilGone(hasTestTag("reminder_offer"))
        assertFalse(settings().enabled)
        assertTrue(settings().offerDismissed)

        // The switch in the settings is still there. Android does not ask again, so the app sends the person to the
        // settings of Android. The test allows the notifications there with the shell, and comes back.
        rule.onNodeWithTag("settings_button").performScrollTo().performClick()
        waitFor(hasTestTag("settings_screen"))
        rule.onNodeWithTag("reminders_switch").assertIsOff().performClick()
        rule.onNodeWithTag("notifications_off").assertIsDisplayed()
        rule.onNodeWithTag("notifications_open_settings").performClick()
        waitForWindowOf { it.isSettingsOfAndroid() }
        allowNotifications()
        closeWindowOf { it.isSettingsOfAndroid() }

        // Back in the app with notifications allowed, reminders go on.
        waitFor(hasTestTag("reminders_switch") and isOn())
        assertTrue(settings().enabled)
        rule.onNodeWithTag("reminders_next").assertIsDisplayed()
        rule.onAllNodesWithTag("notification_access_problem").fetchSemanticsNodes().let { assertTrue(it.isEmpty()) }
    }

    @Test
    fun reminderWorkPostsOneNotificationForFoodAndNoneForNoFood() {
        allowNotifications()
        val now = Instant.now()
        val today = ReminderSchedule.dateOf(now, ZoneId.systemDefault())
        // The reminder of today at 00:00 is due.
        val due = ReminderSettings(enabled = true, minuteOfDay = 0, nextDate = today.toString())

        replaceSettings(due)
        runBlocking { changeLog.replace(SampleInventory.changes(today())) }
        runBlocking { app.reminders.run() }
        rule.waitUntil(TIMEOUT_MILLIS) { reminder() != null }
        val posted = checkNotNull(reminder())
        assertEquals(1, notifications.activeNotifications.count { it.packageName == app.packageName })
        val notification = posted.notification
        assertEquals(ReminderNotifier.CHANNEL_ID, notification.channelId)
        assertEquals("4 items need attention", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("Chicken thighs, Baby spinach and 2 more", notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(
            listOf(
                "Expired yesterday, estimated: Chicken thighs",
                "Expires today, estimated: Baby spinach",
                "Expires tomorrow: Whole milk",
                "Expires in 2 days: Greek yoghurt",
            ),
            notification.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.map(CharSequence::toString),
        )
        assertTrue(notification.flags and Notification.FLAG_AUTO_CANCEL != 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            assertTrue(notification.contentIntent.isImmutable)
            assertTrue(notification.contentIntent.isActivity)
        }
        // The next reminder is tomorrow. The alarm of today does not give a second notification.
        assertEquals(today.toString(), settings().lastDate)
        assertEquals(today.plusDays(1).toString(), settings().nextDate)

        // The food is used: the reminder goes away.
        runBlocking { app.reminders.inventoryChanged(Inventory.replay(emptyList())) }
        rule.waitUntil(TIMEOUT_MILLIS) { reminder() == null }

        // With no food that needs attention, the work shows no notification.
        replaceSettings(due)
        runBlocking { changeLog.replace(emptyList()) }
        runBlocking { app.reminders.run() }
        assertEquals(today.toString(), settings().lastDate)
        assertEquals(null, reminder())
    }

    @Test
    fun reminderTapOpensMyFridgeWithTheFreshnessFiltersAtAStartAndWhenTheAppIsOpen() {
        allowNotifications()
        // A start of the app from the reminder.
        start(SampleInventory.changes(today()), ReminderNotifier.showAttentionIntent(app))
        waitFor(hasTestTag("my_fridge_screen"))
        rule.onNodeWithTag("area_all").assertIsSelected()
        rule.onNodeWithTag("filter_expiring").assertIsSelected()
        rule.onNodeWithTag("filter_expired").assertIsSelected()

        // The app is open on the settings screen. A tap on the reminder closes it and shows My Fridge again.
        rule.onNodeWithTag("filter_expired").performClick().assertIsNotSelected()
        rule.onNodeWithTag("tab_home").performClick()
        rule.onNodeWithTag("settings_button").performScrollTo().performClick()
        waitFor(hasTestTag("settings_screen"))
        runBlocking { app.reminders.sendTest() }
        rule.waitUntil(TIMEOUT_MILLIS) { reminder() != null }
        checkNotNull(reminder()).notification.contentIntent.send()
        waitFor(hasTestTag("my_fridge_screen"))
        rule.onNodeWithTag("filter_expiring").assertIsSelected()
        rule.onNodeWithTag("filter_expired").assertIsSelected()
        assertTrue(rule.onAllNodesWithTag("settings_screen").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun settingsChangeAndStayAfterARestartOfTheApp() {
        allowNotifications()
        start(SampleInventory.changes(today()))
        rule.onNodeWithTag("settings_button").performScrollTo().performClick()
        waitFor(hasTestTag("settings_screen"))
        rule.onNodeWithTag("reminders_switch").assertIsOff().performClick()
        waitFor(hasTestTag("reminders_switch") and isOn())

        // The lead time.
        rule.onNodeWithTag("reminders_lead").assertIsDisplayed().performClick()
        rule.onNodeWithTag("lead_dialog").assertIsDisplayed()
        rule.onNodeWithTag("lead_1").performClick()
        waitFor(hasTestTag("reminders_lead") and hasText("1 day before the expiry date"))

        // The time, with the keyboard input of the Material time picker.
        rule.onNodeWithTag("reminders_time").performClick()
        rule.onNodeWithTag("time_dialog").assertIsDisplayed()
        rule.onNodeWithTag("time_mode").performClick()
        val fields = rule.onAllNodes(hasSetTextAction())
        fields[0].performTextReplacement("7")
        fields[1].performTextReplacement("30")
        rule.onNodeWithTag("time_confirm").performClick()
        rule.waitUntil(TIMEOUT_MILLIS) { settings().minuteOfDay == 7 * 60 + 30 }

        // A new start of the app reads the same settings from the file.
        scenario?.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor(hasTestTag("add_food"))
        rule.onNodeWithTag("settings_button").performScrollTo().performClick()
        waitFor(hasTestTag("settings_screen"))
        rule.onNodeWithTag("reminders_switch").assertIsOn()
        rule.onNode(hasTestTag("reminders_lead") and hasText("1 day before the expiry date")).assertIsDisplayed()
        assertEquals(1, settings().leadDays)
        assertEquals(true, settings().nextDate != null)

        // Off removes the next reminder.
        rule.onNodeWithTag("reminders_switch").performClick()
        waitFor(hasTestTag("reminders_switch") and isOff())
        assertFalse(settings().enabled)
        assertEquals(null, settings().nextDate)
        assertTrue(rule.onAllNodesWithTag("reminders_time").fetchSemanticsNodes().isEmpty())
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
        const val BACK_ATTEMPTS = 3

        /** The package of the dialogs of the system, for example the dialog of an app that stopped. */
        const val SYSTEM_PACKAGE = "android"
    }
}
