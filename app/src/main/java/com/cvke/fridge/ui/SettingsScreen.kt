package com.cvke.fridge.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.cvke.fridge.R
import com.cvke.fridge.TestReminderButton
import com.cvke.fridge.reminder.NotificationAccess
import com.cvke.fridge.reminder.ReminderRules
import com.cvke.fridge.reminder.ReminderSchedule
import com.cvke.fridge.reminder.ReminderSettings
import com.cvke.fridge.ui.theme.Size
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The settings. The settings button on Home opens them, at the place of the settings button of FridgeFriend. The
 * reminders are the only settings now.
 *
 * The switch turns reminders on with the explanation of the notification permission before Android asks (see
 * [NotificationAccessHost]). While reminders are on, the screen has the time, the lead time, and the date of the next
 * reminder, which it says is approximate. When Android does not show the reminders, for example because the person
 * turned the notifications off in the settings of Android, the screen says so and has the action that fixes it. The
 * screen reads the access again each time it comes back.
 */
@Composable
fun SettingsScreen(
    settings: ReminderSettings?,
    snackbar: SnackbarHostState,
    onSetRemindersOn: (Boolean) -> Unit,
    onSetTime: (minuteOfDay: Int) -> Unit,
    onSetLeadDays: (Int) -> Unit,
    onPermissionAsked: () -> Unit,
    onSendTestReminder: () -> Unit,
    onBack: () -> Unit,
) {
    var timeDialog by rememberSaveable { mutableStateOf(false) }
    var leadDialog by rememberSaveable { mutableStateOf(false) }

    SubScreen(stringResource(R.string.settings), onBack, snackbar, "settings_screen") { padding ->
        // The settings come from a small file. The screen is empty for the short time of the first read.
        settings ?: return@SubScreen
        if (timeDialog) {
            TimeDialog(settings.minuteOfDay, onConfirm = { onSetTime(it); timeDialog = false }, onDismiss = { timeDialog = false })
        }
        if (leadDialog) {
            LeadDialog(settings.leadDays, onSelect = { onSetLeadDays(it); leadDialog = false }, onDismiss = { leadDialog = false })
        }
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = Size.contentMax).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                Text(
                    stringResource(R.string.settings_reminders),
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { heading() },
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleSmall,
                )
                NotificationAccessHost(
                    asked = settings.permissionAsked,
                    onAsked = onPermissionAsked,
                    onTurnOn = { onSetRemindersOn(true) },
                    onNotAllowed = {},
                ) { access, turnOn, fix ->
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.reminders_switch)) },
                        supportingContent = { Text(stringResource(R.string.reminders_switch_body)) },
                        trailingContent = { Switch(checked = settings.enabled, onCheckedChange = null) },
                        modifier = Modifier
                            .toggleable(settings.enabled, role = Role.Switch) { on -> if (on) turnOn() else onSetRemindersOn(false) }
                            .testTag("reminders_switch"),
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    if (settings.enabled) {
                        if (access != NotificationAccess.ALLOWED) AccessProblem(access, fix, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        SettingItem(stringResource(R.string.reminders_time), timeText(settings.time), "reminders_time") { timeDialog = true }
                        SettingItem(stringResource(R.string.reminders_lead), leadText(settings.leadDays), "reminders_lead") { leadDialog = true }
                        if (access == NotificationAccess.ALLOWED) NextReminder(settings, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        // Only the debug build has this button. The release build has no control here.
                        TestReminderButton(onSendTestReminder, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                }
            }
        }
    }
}

/** One setting with its value. A tap opens the dialog that changes it. */
@Composable
private fun SettingItem(title: String, value: String, tag: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(value) },
        modifier = Modifier.clickable(onClick = onClick).testTag(tag),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

/** Why Android does not show the reminders, with the action that fixes it. */
@Composable
private fun AccessProblem(access: NotificationAccess, onFix: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val text = when (access) {
        NotificationAccess.ASK, NotificationAccess.DENIED -> R.string.notifications_denied_status
        NotificationAccess.BLOCKED -> R.string.notifications_blocked_status
        NotificationAccess.CHANNEL_OFF -> R.string.notifications_channel_off_status
        NotificationAccess.ALLOWED -> return
    }
    val action = if (access == NotificationAccess.ASK || access == NotificationAccess.DENIED) R.string.action_allow_notifications else R.string.camera_open_settings
    Card(
        modifier.fillMaxWidth().testTag("notification_access_problem"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(scheme.errorContainer, scheme.onErrorContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(painterResource(R.drawable.ic_bell_off), contentDescription = null)
                Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
            }
            FilledTonalButton(onFix, Modifier.align(Alignment.End).testTag("notification_access_fix")) { Text(stringResource(action)) }
        }
    }
}

/**
 * The date and the time of the next reminder, and that the time is approximate. The text is calculated again each
 * time the screen comes back, for example after midnight or a change of the time zone.
 */
@Composable
private fun NextReminder(settings: ReminderSettings, modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(Instant.now()) }
    LifecycleResumeEffect(Unit) {
        now = Instant.now()
        onPauseOrDispose {}
    }
    val zone = ZoneId.systemDefault()
    val today = ReminderSchedule.dateOf(now, zone)
    val date = ReminderSchedule.restoredDate(settings.next, now, zone, settings.time, settings.last)
    val time = timeText(settings.time)
    val locale = LocalConfiguration.current.locales[0]
    val text = when (date) {
        today -> stringResource(R.string.reminders_next_today, time)
        today.plusDays(1) -> stringResource(R.string.reminders_next_tomorrow, time)
        else -> stringResource(R.string.reminders_next_date, DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date), time)
    }
    Column(modifier.semantics(mergeDescendants = true) {}.testTag("reminders_next"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.reminders_next_note), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * The Material time picker for the time of the reminder, with the clock of the settings of Android (12 or 24 hours).
 * A button changes between the dial and the keyboard. On a short screen, for example a phone that is turned, the
 * keyboard input is first, because the dial does not fit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(minuteOfDay: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = minuteOfDay / 60, initialMinute = minuteOfDay % 60)
    val short = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() } < SHORT_SCREEN
    var keyboard by rememberSaveable { mutableStateOf(short) }
    TimePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }, Modifier.testTag("time_confirm")) {
                Text(stringResource(R.string.action_ok))
            }
        },
        title = { Text(stringResource(R.string.reminders_time_title)) },
        modifier = Modifier.testTag("time_dialog"),
        modeToggleButton = {
            IconButton(onClick = { keyboard = !keyboard }, Modifier.testTag("time_mode")) {
                Icon(
                    painterResource(if (keyboard) R.drawable.ic_clock else R.drawable.ic_keyboard),
                    contentDescription = stringResource(if (keyboard) R.string.time_input_clock else R.string.time_input_keyboard),
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        if (keyboard) TimeInput(state) else TimePicker(state)
    }
}

/** The lead times as a list with one selection. A tap selects a lead time and closes the dialog. */
@Composable
private fun LeadDialog(selected: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    var choice by rememberSaveable { mutableIntStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("lead_dialog"),
        title = { Text(stringResource(R.string.reminders_lead)) },
        text = {
            Column(Modifier.selectableGroup()) {
                ReminderRules.leadDayChoices.forEach { days ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = days == choice, role = Role.RadioButton) {
                                choice = days
                                onSelect(days)
                            }
                            .testTag("lead_$days"),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = days == choice, onClick = null)
                        Text(leadText(days), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** The window height below which the dial of the time picker does not fit. */
private val SHORT_SCREEN = 520.dp
