package com.cvke.fridge.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.cvke.fridge.R
import com.cvke.fridge.reminder.NotificationAccess
import com.cvke.fridge.reminder.ReminderNotifier

/** What Android says about the notifications of the app. The access comes from these values and the settings. */
private data class AndroidNotifications(val granted: Boolean, val appEnabled: Boolean, val channelOff: Boolean, val mayAskAgain: Boolean)

/** The dialog that the turn-on of reminders shows. */
private enum class TurnOnStep { NONE, EXPLAIN, OPEN_SETTINGS }

/**
 * Turns reminders on in the way that Android needs, and gives the [content] the notification access.
 *
 * - The notifications are allowed: reminders go on at once.
 * - Android can ask for the permission (Android 13 or later): a dialog explains the reminders first. "Continue" then
 *   shows the question of Android. Reminders go on when the person allows notifications. [onNotAllowed] runs when the
 *   person does not.
 * - Only the settings of Android can turn the notifications on: a dialog says so, and "Open settings" opens the
 *   notification settings of the app, or of the reminder category. When the person comes back and the notifications
 *   are on, reminders go on.
 *
 * The access is read again each time the screen comes back, so a change in the settings of Android shows at once.
 *
 * @param asked True when the app asked for the permission before (see [com.cvke.fridge.reminder.ReminderSettings]).
 * @param onAsked The app now asks Android for the permission.
 * @param content Gets the access, the function that turns reminders on, and the function that fixes the access:
 *   it asks Android again, or opens the settings.
 */
@Composable
fun NotificationAccessHost(
    asked: Boolean,
    onAsked: () -> Unit,
    onTurnOn: () -> Unit,
    onNotAllowed: () -> Unit,
    content: @Composable (access: NotificationAccess, turnOn: () -> Unit, fix: () -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val notifier = remember(context) { ReminderNotifier(context.applicationContext) }
    fun read() = AndroidNotifications(
        granted = notifier.hasPermission(),
        appEnabled = notifier.appEnabled(),
        channelOff = notifier.channelOff(),
        mayAskAgain = Build.VERSION.SDK_INT >= NotificationAccess.PERMISSION_SDK &&
            activity?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == true,
    )
    var android by remember { mutableStateOf(read()) }
    val access = NotificationAccess.of(Build.VERSION.SDK_INT, android.granted, android.appEnabled, android.channelOff, android.mayAskAgain, asked)
    var step by rememberSaveable { mutableStateOf(TurnOnStep.NONE) }
    // True while the person is in the settings of Android to turn reminders on.
    var inSettings by rememberSaveable { mutableStateOf(false) }
    val turnOnNow by rememberUpdatedState(onTurnOn)
    val notAllowed by rememberUpdatedState(onNotAllowed)

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        android = read()
        if (allowed) turnOnNow() else notAllowed()
    }
    LifecycleResumeEffect(Unit) {
        android = read()
        if (inSettings) {
            inSettings = false
            if (android.granted && android.appEnabled && !android.channelOff) turnOnNow()
        }
        onPauseOrDispose {}
    }

    fun ask() {
        // Before Android 13 there is no question: the access is then never ASK or DENIED.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        onAsked()
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    val turnOn = {
        when (access) {
            NotificationAccess.ALLOWED -> onTurnOn()
            NotificationAccess.ASK, NotificationAccess.DENIED -> step = TurnOnStep.EXPLAIN
            NotificationAccess.BLOCKED, NotificationAccess.CHANNEL_OFF -> step = TurnOnStep.OPEN_SETTINGS
        }
    }
    val fix = {
        when (access) {
            NotificationAccess.ASK, NotificationAccess.DENIED -> ask()
            NotificationAccess.BLOCKED, NotificationAccess.CHANNEL_OFF -> openNotificationSettings(context, access)
            NotificationAccess.ALLOWED -> Unit
        }
    }

    when (step) {
        TurnOnStep.EXPLAIN -> AlertDialog(
            onDismissRequest = { step = TurnOnStep.NONE },
            modifier = Modifier.testTag("notifications_explain"),
            icon = { Icon(painterResource(R.drawable.ic_bell), contentDescription = null) },
            title = { Text(stringResource(R.string.notifications_ask_title)) },
            text = { Text(stringResource(R.string.notifications_ask_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        step = TurnOnStep.NONE
                        ask()
                    },
                    Modifier.testTag("notifications_continue"),
                ) { Text(stringResource(R.string.action_continue)) }
            },
            dismissButton = { TextButton(onClick = { step = TurnOnStep.NONE }) { Text(stringResource(R.string.action_not_now)) } },
        )
        TurnOnStep.OPEN_SETTINGS -> AlertDialog(
            onDismissRequest = { step = TurnOnStep.NONE },
            modifier = Modifier.testTag("notifications_off"),
            icon = { Icon(painterResource(R.drawable.ic_bell_off), contentDescription = null) },
            title = { Text(stringResource(R.string.notifications_off_title)) },
            text = {
                Text(
                    stringResource(
                        if (access == NotificationAccess.CHANNEL_OFF) R.string.notifications_channel_off_body else R.string.notifications_blocked_body,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        step = TurnOnStep.NONE
                        inSettings = true
                        openNotificationSettings(context, access)
                    },
                    Modifier.testTag("notifications_open_settings"),
                ) { Text(stringResource(R.string.camera_open_settings)) }
            },
            dismissButton = { TextButton(onClick = { step = TurnOnStep.NONE }) { Text(stringResource(R.string.action_cancel)) } },
        )
        TurnOnStep.NONE -> Unit
    }
    content(access, turnOn, fix)
}

/**
 * Opens the notification settings of the reminder category when only that category is off, and otherwise the
 * notification settings of the app. A device with no such screen gets the settings of the app.
 */
private fun openNotificationSettings(context: Context, access: NotificationAccess) {
    val intent = if (access == NotificationAccess.CHANNEL_OFF) {
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_CHANNEL_ID, ReminderNotifier.CHANNEL_ID)
    } else {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
    }.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
    }
}
