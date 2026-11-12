package com.cvke.fridge.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cvke.fridge.FridgeApplication
import java.io.IOException
import java.time.DateTimeException
import java.time.ZoneId
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Receives the alarm of the reminder, and the broadcasts after which the alarm must be set again: the restart of the
 * phone, the update of the app, and a change of the clock or the time zone. Android removes alarms at a restart. After
 * a change of the time zone, the reminder stays at the same time on the clock of the device.
 *
 * The receiver is not exported: only the alarm of the app and Android can start it. It is off in the manifest, and
 * [ReminderAlarm] turns it on while reminders are on. The work runs off the main thread, and the receiver stays alive
 * until it is complete.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action !in ACTIONS) return
        val reminders = (context.applicationContext as FridgeApplication).reminders
        val zone = intent.getStringExtra(EXTRA_TIME_ZONE)?.let(::zoneOf)
        val result = goAsync()
        reminders.scope.launch {
            try {
                withTimeoutOrNull(WORK_TIMEOUT_MILLIS) {
                    if (action == ACTION_REMIND) reminders.run() else reminders.restore(zone)
                }
            } catch (_: IOException) {
                // The settings could not be read or written. The next start of the app sets the alarm again.
            } finally {
                result.finish()
            }
        }
    }

    private fun zoneOf(id: String): ZoneId? = try {
        ZoneId.of(id)
    } catch (_: DateTimeException) {
        null
    }

    companion object {
        /** The action of the alarm of the reminder. */
        const val ACTION_REMIND = "com.cvke.fridge.action.REMIND"

        private val ACTIONS = setOf(
            ACTION_REMIND,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )

        /**
         * The new time zone in the broadcast of a time zone change. Android 11 made the name public as
         * `Intent.EXTRA_TIMEZONE`. Earlier versions send the same name.
         */
        private const val EXTRA_TIME_ZONE = "time-zone"

        /** Android gives a receiver about 10 seconds. The work takes much less. */
        private const val WORK_TIMEOUT_MILLIS = 8_000L
    }
}
