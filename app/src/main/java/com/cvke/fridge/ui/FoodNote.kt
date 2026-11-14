package com.cvke.fridge.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.cvke.fridge.R
import com.cvke.fridge.inventory.Notes

/**
 * The note of a product on the screen of the food. A product with a note shows it in a card, and a tap on the card
 * edits it. A product with no note has "Add a note". The person edits the note in place: "Save" saves it, and "Cancel"
 * keeps the note as it was. A note with only spaces is removed.
 *
 * @param productId The product. The text field starts again for another product.
 * @param onSave Saves the cleaned note, or null for no note. It is not called when the note did not change.
 */
@Composable
fun FoodNote(productId: String, note: String?, onSave: (String?) -> Unit, modifier: Modifier = Modifier) {
    var editing by rememberSaveable(productId) { mutableStateOf(false) }
    var draft by rememberSaveable(productId) { mutableStateOf("") }
    val scheme = MaterialTheme.colorScheme
    when {
        editing -> Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { focus.requestFocus() }
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("food_note_field"),
                label = { Text(stringResource(R.string.field_note)) },
                placeholder = { Text(stringResource(R.string.field_note_placeholder)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                minLines = 3,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = { editing = false }, Modifier.testTag("food_note_cancel")) {
                    Text(stringResource(R.string.action_cancel))
                }
                Button(
                    onClick = {
                        editing = false
                        val cleaned = Notes.clean(draft)
                        if (cleaned != note) onSave(cleaned)
                    },
                    Modifier.testTag("food_note_save"),
                ) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }
        note != null -> Card(
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .clickable(onClickLabel = stringResource(R.string.food_edit_note)) {
                    draft = note
                    editing = true
                }
                .testTag("food_note"),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.food_note),
                        Modifier.weight(1f).semantics { heading() },
                        color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Icon(painterResource(R.drawable.ic_edit), contentDescription = null, Modifier.size(18.dp), tint = scheme.onSurfaceVariant)
                }
                Text(note, color = scheme.onSurface, style = MaterialTheme.typography.bodyLarge)
            }
        }
        else -> TextButton(
            onClick = {
                draft = ""
                editing = true
            },
            modifier.testTag("food_add_note"),
        ) {
            Icon(painterResource(R.drawable.ic_pencil_plus), contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.food_add_note))
        }
    }
}
