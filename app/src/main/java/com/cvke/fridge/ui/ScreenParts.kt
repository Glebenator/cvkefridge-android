package com.cvke.fridge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cvke.fridge.R
import com.cvke.fridge.ui.theme.SliceComposition

// FridgeFriend has no parts for these two cases. It shows its own sample as real food, and it hides a failed load.

/** Says that the food on the screen is the sample of the debug build. */
@Composable
fun SampleNote(modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.sample_note),
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.small)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("sample_note"),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelLarge,
    )
}

/**
 * An empty state: the slice of the house brand above the [title]. It is for a screen or a group with no food, not
 * for a search or a filter with no result. The [action] is below the title.
 */
@Composable
fun EmptyState(title: String, tag: String, modifier: Modifier = Modifier, action: @Composable () -> Unit = {}) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 32.dp).testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SliceComposition()
        Spacer(Modifier.height(16.dp))
        Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
        action()
    }
}

/** The button of an empty state that opens the form that adds food. */
@Composable
fun AddFoodButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalButton(onClick, modifier.padding(top = 16.dp).testTag("empty_add_food")) {
        Icon(painterResource(R.drawable.ic_add), contentDescription = null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.add_food))
    }
}

/** The change log could not be read: what failed, why, and the action, in the place of the content. */
@Composable
fun LoadFailed(onTryAgain: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 32.dp).testTag("load_failed"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painterResource(R.drawable.ic_alert),
            contentDescription = null,
            Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.load_failed),
            Modifier.widthIn(max = 320.dp),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(onTryAgain) { Text(stringResource(R.string.action_try_again)) }
    }
}
