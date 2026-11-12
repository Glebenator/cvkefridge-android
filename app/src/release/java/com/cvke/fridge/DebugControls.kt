package com.cvke.fridge

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** The release build has no test reminder, so it shows no control. The debug build has its own file with this name. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun TestReminderButton(onClick: () -> Unit, modifier: Modifier = Modifier) = Unit
