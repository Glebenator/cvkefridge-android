package com.cvke.fridge

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.cvke.fridge.core.CalendarDate
import com.cvke.fridge.core.Change
import com.cvke.fridge.core.Inventory
import java.util.TimeZone
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule

/**
 * The base of the device tests. A test writes the change log first and then starts the activity, so the screens read
 * the log through the production code path, and the test waits until the inventory is on the screen. Each write of
 * the app goes to the real change log on the device.
 */
abstract class DeviceTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private val changeLog get() = ApplicationProvider.getApplicationContext<FridgeApplication>().changeLog
    protected var scenario: ActivityScenario<MainActivity>? = null

    protected fun start(changes: List<Change>) {
        runBlocking { changeLog.replace(changes) }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        // The app reads the change log after the start. The button that adds food is there when the inventory is loaded.
        waitFor(hasTestTag("add_food"))
    }

    protected fun today(): CalendarDate {
        val now = System.currentTimeMillis()
        return checkNotNull(CalendarDate.ofInstant(now, TimeZone.getDefault().getOffset(now) / 1000))
    }

    /** The replay of the change log on the device. */
    protected fun inventory(): Inventory = runBlocking { Inventory.replay(changeLog.read()) }

    /** Waits for a node. A write runs after the tap, so its result is not on the screen at once. */
    protected fun waitFor(matcher: SemanticsMatcher) {
        rule.waitUntil(TIMEOUT_MILLIS) { rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    }

    protected fun waitUntilGone(matcher: SemanticsMatcher) {
        rule.waitUntil(TIMEOUT_MILLIS) { rule.onAllNodes(matcher).fetchSemanticsNodes().isEmpty() }
    }

    @After
    fun close() {
        scenario?.close()
        runBlocking { changeLog.replace(emptyList()) }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
