package com.cvke.fridge

import android.content.Intent
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.cvke.fridge.core.CalendarDate
import com.cvke.fridge.core.Change
import com.cvke.fridge.core.Inventory
import java.io.File
import java.util.TimeZone
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule

/**
 * The base of the device tests. A test writes the change log first and then starts the activity, so the screens read
 * the log through the production code path, and the test waits until the inventory is on the screen. Each write of
 * the app goes to the real change log on the device. The product lookup goes to [FakeServer], not to the internet.
 */
abstract class DeviceTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    protected val app: FridgeApplication get() = ApplicationProvider.getApplicationContext()
    protected val changeLog get() = app.changeLog
    protected var scenario: ActivityScenario<MainActivity>? = null

    /** Writes the change log and starts the activity, with the [intent] of a start from a notification or the launcher. */
    protected fun start(changes: List<Change>, intent: Intent? = null) {
        runBlocking { changeLog.replace(changes) }
        // The cache of an earlier run of the tests is not used. Each test of this run looks up its own barcodes.
        File(app.filesDir, "product-lookups.json").delete()
        scenario = if (intent == null) ActivityScenario.launch(MainActivity::class.java) else ActivityScenario.launch(intent)
        // The app reads the change log after the start. The add button is there when the inventory is loaded.
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
        FakeServer.reset()
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
