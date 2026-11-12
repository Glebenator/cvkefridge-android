package com.cvke.fridge

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource

/**
 * The button of the debug build that shows the reminder of today now, with no wait for the time of the reminder. The
 * release build has its own file with this name, which shows nothing.
 */
@Composable
fun TestReminderButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick, modifier.testTag("send_test_reminder")) { Text(stringResource(R.string.debug_send_test_reminder)) }
}
