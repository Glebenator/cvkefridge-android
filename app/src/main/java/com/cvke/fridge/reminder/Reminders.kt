package com.cvke.fridge.reminder

import androidx.annotation.VisibleForTesting
import androidx.datastore.core.DataStore
import com.cvke.fridge.core.CalendarDate
import com.cvke.fridge.core.Inventory
import com.cvke.fridge.data.ChangeLogStore
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The result of a test reminder of the debug build. */
enum class TestReminderResult { SENT, NOTHING_TO_SEND, NOT_ALLOWED }

/**
 * The daily reminder: the settings, the alarm, and the decision of what the notification says.
 *
 * When the alarm goes off, [run] reads the change log, runs the replay of the core, and gives the inventory to
 * [ReminderRules]. The notification shows only when a food needs attention. Each operation runs off the main thread,
 * one at a time.
 *
 * @param now The instant of now.
 * @param zone The time zone of the device now.
 * @param work The thread of the operations.
 * @param scope The scope of the work that a broadcast receiver starts. It lives as long as the app.
 */
class Reminders(
    private val store: DataStore<ReminderSettings>,
    private val changeLog: ChangeLogStore,
    private val notifier: ReminderNotifier,
    private val alarm: ReminderAlarm,
    private val now: () -> Instant = Instant::now,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val work: CoroutineDispatcher = Dispatchers.Default,
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val mutex = Mutex()

    /** The settings. A file that cannot be read gives the default settings. */
    val settings: Flow<ReminderSettings> = store.data.catch { failure ->
        if (failure is IOException) emit(ReminderSettingsSerializer.defaultValue) else throw failure
    }

    /** Turns reminders on or off. Off removes the alarm and the reminder in the notification shade. */
    suspend fun setEnabled(on: Boolean) = locked {
        val instant = now()
        val zone = zone()
        val settings = update { current ->
            if (on) {
                current.copy(enabled = true, nextDate = ReminderSchedule.firstDate(instant, zone, current.time, current.last).toString())
            } else {
                current.copy(enabled = false, nextDate = null)
            }
        }
        if (on) {
            notifier.createChannel()
            setAlarm(settings, zone)
        } else {
            alarm.cancel()
            notifier.cancel()
        }
    }

    /** Changes the time of the reminder, and moves the alarm. */
    suspend fun setTime(minuteOfDay: Int) = locked {
        require(minuteOfDay in 0 until ReminderSettings.MINUTES_IN_DAY) { "The time is out of range" }
        val instant = now()
        val zone = zone()
        val settings = update { current ->
            val changed = current.copy(minuteOfDay = minuteOfDay)
            if (!current.enabled) changed else changed.copy(nextDate = ReminderSchedule.firstDate(instant, zone, changed.time, current.last).toString())
        }
        if (settings.enabled) setAlarm(settings, zone)
    }

    /** Changes the lead time. The next reminder uses it. */
    suspend fun setLeadDays(days: Int) {
        require(days in ReminderRules.leadDayChoices) { "The lead time is out of range" }
        locked { update { it.copy(leadDays = days) } }
    }

    /** Home does not show the offer of reminders again. */
    suspend fun dismissOffer() {
        locked { update { it.copy(offerDismissed = true) } }
    }

    /** The app asked Android for the notification permission. */
    suspend fun permissionAsked() {
        locked { update { it.copy(permissionAsked = true) } }
    }

    /**
     * Sets the alarm again from the stored date: after a restart of the phone, an update or a start of the app, and a
     * change of the clock or the time zone. When the reminder of today did not come, the alarm goes off at once.
     *
     * @param newZone The time zone from the broadcast of a time zone change, or null for the time zone of the device.
     */
    suspend fun restore(newZone: ZoneId? = null) = locked {
        val current = read()
        if (!current.enabled) return@locked
        val zone = newZone ?: zone()
        val next = ReminderSchedule.restoredDate(current.next, now(), zone, current.time, current.last)
        val settings = update { it.copy(nextDate = next.toString()) }
        setAlarm(settings, zone)
    }

    /**
     * The work of the alarm: shows the reminder of today when a food needs attention, and sets the alarm of the next
     * reminder. An alarm that goes off before the time of the stored date only sets the alarm again.
     */
    suspend fun run() = locked {
        val current = read()
        if (!current.enabled) {
            alarm.cancel()
            return@locked
        }
        val instant = now()
        val zone = zone()
        val today = ReminderSchedule.dateOf(instant, zone)
        val due = current.next?.let { !ReminderSchedule.triggerAt(it, current.time, zone).isAfter(instant) } ?: true
        if (!due || current.last == today) {
            restoreLocked(current, instant, zone)
            return@locked
        }
        val foods = foodsOf(readInventory(), today, current.leadDays)
        // A reminder of an earlier day is old. The new reminder replaces it, and no reminder removes it.
        if (foods.isEmpty() || !notifier.show(foods, alert = true)) notifier.cancel()
        val settings = update {
            it.copy(lastDate = today.toString(), nextDate = ReminderSchedule.dateAfterRun(today).toString())
        }
        setAlarm(settings, zone)
    }

    /**
     * Changes the reminder in the notification shade after a change of the inventory. When no food needs attention
     * now, for example after the person used it, the reminder goes away. A reminder that is not shown stays hidden.
     */
    suspend fun inventoryChanged(inventory: Inventory) = locked {
        if (!notifier.isShown()) return@locked
        val foods = foodsOf(inventory, ReminderSchedule.dateOf(now(), zone()), read().leadDays)
        if (foods.isEmpty() || !notifier.show(foods, alert = false)) notifier.cancel()
    }

    /** Shows the reminder of today now, with no change to the schedule. Only the debug build has a control for it. */
    suspend fun sendTest(): TestReminderResult = locked {
        val foods = foodsOf(readInventory(), ReminderSchedule.dateOf(now(), zone()), read().leadDays)
        when {
            foods.isEmpty() -> TestReminderResult.NOTHING_TO_SEND
            notifier.show(foods, alert = true) -> TestReminderResult.SENT
            else -> TestReminderResult.NOT_ALLOWED
        }
    }

    /** Replaces the settings, for the device tests. */
    @VisibleForTesting
    suspend fun replaceSettings(settings: ReminderSettings) = locked {
        store.updateData { settings }
        if (!settings.enabled) alarm.cancel()
        notifier.cancel()
    }

    /**
     * Runs one operation at a time, off the main thread: the file of the settings, the change log, the alarm, and the
     * notification are calls to the disk or to Android.
     */
    private suspend fun <T> locked(operation: suspend () -> T): T = withContext(work) { mutex.withLock { operation() } }

    private suspend fun restoreLocked(current: ReminderSettings, instant: Instant, zone: ZoneId) {
        val next = ReminderSchedule.restoredDate(current.next, instant, zone, current.time, current.last)
        setAlarm(update { it.copy(nextDate = next.toString()) }, zone)
    }

    private fun setAlarm(settings: ReminderSettings, zone: ZoneId) {
        val next = settings.next ?: return
        alarm.set(ReminderSchedule.triggerAt(next, settings.time, zone))
    }

    private suspend fun read(): ReminderSettings = settings.first()

    private suspend fun update(transform: (ReminderSettings) -> ReminderSettings): ReminderSettings = store.updateData(transform)

    /** The inventory of the change log, or null when the change log cannot be read. */
    private suspend fun readInventory(): Inventory? = try {
        Inventory.replay(changeLog.read())
    } catch (_: IOException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun foodsOf(inventory: Inventory?, today: LocalDate, leadDays: Int): List<ReminderFood> {
        inventory ?: return emptyList()
        val date = checkNotNull(CalendarDate.of(today.year, today.monthValue, today.dayOfMonth)) { "The date is out of range" }
        return ReminderRules.foods(inventory, date, leadDays)
    }
}
