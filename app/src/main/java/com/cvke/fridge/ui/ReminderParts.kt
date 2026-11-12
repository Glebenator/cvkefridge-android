package com.cvke.fridge.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cvke.fridge.R
import com.cvke.fridge.reminder.ReminderSettings
import com.cvke.fridge.ui.theme.IconTile
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * A time on the clock of the device, with the 12-hour or the 24-hour clock of the settings of Android: "9:00 AM" or
 * "09:00".
 */
@Composable
fun timeText(time: LocalTime): String {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val is24Hour = DateFormat.is24HourFormat(context)
    return remember(time, locale, is24Hour) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, if (is24Hour) "Hm" else "hm"), locale).format(time)
    }
}

/** The lead time in words: "On the expiry date", "1 day before the expiry date". */
@Composable
fun leadText(days: Int): String =
    if (days == 0) stringResource(R.string.reminders_lead_same_day) else pluralStringResource(R.plurals.reminders_lead_days, days, days)

/**
 * The offer of reminders on Home. Home shows it when the inventory has food with an expiry date and the person did not
 * turn reminders on or close the offer. "Turn on" explains the notification permission before Android asks for it.
 * "Not now" closes the offer for good: the switch in the settings stays.
 */
@Composable
fun ReminderOffer(settings: ReminderSettings, onTurnOn: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier.fillMaxWidth().testTag("reminder_offer"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow, contentColor = scheme.onSurface),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                IconTile(R.drawable.ic_bell, scheme.primaryContainer, scheme.onPrimaryContainer)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.reminders_offer_title), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.reminders_offer_body, timeText(settings.time)),
                        Modifier.padding(top = 4.dp),
                        color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            FlowRow(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onDismiss, Modifier.testTag("reminder_offer_dismiss")) { Text(stringResource(R.string.action_not_now)) }
                FilledTonalButton(onTurnOn, Modifier.testTag("reminder_offer_turn_on")) { Text(stringResource(R.string.action_turn_on)) }
            }
        }
    }
}
