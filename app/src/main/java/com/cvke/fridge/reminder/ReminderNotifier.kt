package com.cvke.fridge.reminder

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.cvke.fridge.MainActivity
import com.cvke.fridge.R
import com.cvke.fridge.ui.theme.LightScheme

/**
 * Shows the reminder notification and removes it. The app has one reminder notification at a time: a new reminder
 * replaces the reminder of an earlier day.
 *
 * The notification category "Expiry reminders" comes into Android when the person turns reminders on, not when the app
 * starts.
 */
class ReminderNotifier(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    /** Makes the notification category of the reminders, or gives it the name and the description of the language now. */
    fun createChannel() {
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.reminder_channel_name))
                .setDescription(context.getString(R.string.reminder_channel_description))
                .build(),
        )
    }

    /** True when the app has the notification permission. It is always true before Android 13. */
    fun hasPermission(): Boolean = Build.VERSION.SDK_INT < NotificationAccess.PERMISSION_SDK ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** True when the notifications of the app are on in the settings of Android. */
    fun appEnabled(): Boolean = manager.areNotificationsEnabled()

    /** True when the notification category of the reminders exists and the person turned it off. */
    fun channelOff(): Boolean = manager.getNotificationChannelCompat(CHANNEL_ID)?.importance == NotificationManagerCompat.IMPORTANCE_NONE

    /** True when Android can show a reminder now. */
    fun canPost(): Boolean = hasPermission() && appEnabled() && !channelOff()

    /** True when the reminder notification is in the notification shade now. */
    fun isShown(): Boolean = context.getSystemService(NotificationManager::class.java).activeNotifications.any { it.id == NOTIFICATION_ID }

    /**
     * Shows the reminder for the [foods], in the place of the reminder that is shown. With [alert] false, a reminder
     * that is shown changes with no sound or vibration. The result is false when Android does not allow the
     * notification.
     */
    fun show(foods: List<ReminderFood>, alert: Boolean): Boolean {
        val message = ReminderText.message(foods, ResourceText(context)) ?: return false
        createChannel()
        if (!canPost()) return false
        val open = PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            showAttentionIntent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val style = NotificationCompat.InboxStyle().setBigContentTitle(message.title)
        message.lines.forEach(style::addLine)
        // On a locked screen the notification shows only the number of foods, not their names.
        val public = builder(message.title, open).build()
        val notification = builder(message.title, open)
            .setContentText(message.text)
            .setStyle(style)
            .setNumber(foods.size)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setOnlyAlertOnce(!alert)
            .build()
        return try {
            manager.notify(NOTIFICATION_ID, notification)
            true
        } catch (_: SecurityException) {
            // The person took the permission away after the check.
            false
        }
    }

    /** Removes the reminder from the notification shade. */
    fun cancel() = manager.cancel(NOTIFICATION_ID)

    private fun builder(title: String, open: PendingIntent) = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_reminder)
        .setColor(LightScheme.primary.toArgb())
        .setContentTitle(title)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setContentIntent(open)
        .setAutoCancel(true)

    /** The text of the string resources of the device. */
    private class ResourceText(private val context: Context) : TextSource {
        override fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

        override fun plural(id: Int, count: Int, vararg args: Any): String = context.resources.getQuantityString(id, count, *args)
    }

    companion object {
        const val CHANNEL_ID = "expiry-reminders"
        const val NOTIFICATION_ID = 1
        private const val REQUEST_OPEN = 1

        /** The action of the intent that opens My Fridge with the expiring and the expired food. */
        const val ACTION_SHOW_ATTENTION = "com.cvke.fridge.action.SHOW_ATTENTION"

        /**
         * The intent of a tap on the reminder. It opens the app, or brings the app that is open to the front, with My
         * Fridge and the filters "Expiring" and "Expired".
         */
        fun showAttentionIntent(context: Context): Intent = Intent(context, MainActivity::class.java)
            .setAction(ACTION_SHOW_ATTENTION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
