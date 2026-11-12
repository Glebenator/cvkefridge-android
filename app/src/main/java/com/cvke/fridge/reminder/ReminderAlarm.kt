package com.cvke.fridge.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.time.Instant

/**
 * The alarm of the next reminder, with `AlarmManager`.
 *
 * The alarm is inexact and can go off in Doze ([AlarmManager.setAndAllowWhileIdle]). Android delivers it not before
 * the time, and on Android 12 and later normally within one hour after it, but later when the battery saver is on.
 * The app does not need the permission for exact alarms. Android removes the alarms of an app when the phone restarts,
 * so [ReminderReceiver] sets the alarm again after a restart, an update of the app, and a change of the clock or the
 * time zone. That receiver is on only while reminders are on.
 */
class ReminderAlarm(private val context: Context) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    /** Sets the alarm to [at], in the place of the alarm that is set. An instant in the past makes the alarm go off at once. */
    fun set(at: Instant) {
        setReceiverOn(true)
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pendingIntent())
    }

    /** Removes the alarm, and stops the receiver of the restart and the clock changes. */
    fun cancel() {
        alarms.cancel(pendingIntent())
        setReceiverOn(false)
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_ALARM,
        Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_REMIND),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** The receiver is off in the manifest, so that a restart does not start the app when reminders are off. */
    private fun setReceiverOn(on: Boolean) {
        val receiver = ComponentName(context, ReminderReceiver::class.java)
        val wanted = if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        val packages = context.packageManager
        if (packages.getComponentEnabledSetting(receiver) != wanted) {
            packages.setComponentEnabledSetting(receiver, wanted, PackageManager.DONT_KILL_APP)
        }
    }

    private companion object {
        const val REQUEST_ALARM = 1
    }
}
