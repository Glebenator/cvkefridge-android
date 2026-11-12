package com.cvke.fridge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.cvke.fridge.R
import com.cvke.fridge.core.StorageArea
import com.cvke.fridge.core.StorageKind
import com.cvke.fridge.inventory.InventoryAction
import com.cvke.fridge.inventory.NameError
import com.cvke.fridge.inventory.StorageAreaForm
import com.cvke.fridge.ui.theme.Size
import java.util.UUID

/**
 * The storage areas: Fridge and Freezer, which are fixed, and the storage areas that the person created. Each one
 * shows its storage kind and its number of foods. The person can create a storage area, and edit or delete one that
 * is not fixed.
 *
 * The core does not delete a storage area with an active batch in it. The screen then says how many foods are there
 * and what to do. The person can undo a delete from the snackbar.
 */
@Composable
fun StorageAreasScreen(
    state: FridgeUiState,
    formWrite: FormWrite,
    snackbar: SnackbarHostState,
    onPerform: (InventoryAction, String?) -> Unit,
    onFormWriteRead: (String) -> Unit,
    onTryAgain: () -> Unit,
    onBack: () -> Unit,
) {
    val ready = state as? FridgeUiState.Ready
    // The form of the dialog, or null when no dialog is open.
    var form by rememberSaveable(stateSaver = StorageAreaFormSaver) { mutableStateOf(null) }
    // The storage area that the person cannot delete now, because it has food.
    var inUseId by rememberSaveable { mutableStateOf<String?>(null) }

    SubScreen(stringResource(R.string.storage_areas), onBack, snackbar, "storage_areas_screen") { padding ->
        if (ready == null) {
            NotReady(state, onTryAgain, padding)
            return@SubScreen
        }
        val areas = ready.inventory.storageAreas.associateBy { it.id }
        // The order and the numbers of foods are those of the tabs of My Fridge.
        val rows = ready.content.areas.mapNotNull { summary -> areas[summary.id]?.let { it to summary.foodCount } }

        form?.let { open ->
            StorageAreaDialog(
                form = open,
                areas = ready.inventory.storageAreas,
                formWrite = formWrite,
                onChange = { form = it },
                onPerform = onPerform,
                onFormWriteRead = onFormWriteRead,
                onDismiss = { form = null },
            )
        }
        rows.firstOrNull { it.first.id == inUseId }?.let { (area, count) ->
            AlertDialog(
                onDismissRequest = { inUseId = null },
                modifier = Modifier.testTag("area_in_use"),
                title = { Text(stringResource(R.string.storage_area_in_use_title, area.name)) },
                text = { Text(pluralStringResource(R.plurals.storage_area_in_use, count, area.name, count)) },
                confirmButton = { TextButton(onClick = { inUseId = null }) { Text(stringResource(R.string.action_ok)) } },
            )
        }

        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = Size.contentMax).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(rows, key = { it.first.id }) { (area, count) ->
                    AreaCard(
                        area = area,
                        foodCount = count,
                        onEdit = { form = StorageAreaForm.of(area) },
                        onDelete = {
                            // The core rejects this delete. The screen says why before the person tries.
                            if (count > 0) inUseId = area.id else onPerform(InventoryAction.DeleteStorageArea(area.id), null)
                        },
                    )
                }
                if (rows.none { !it.first.fixed }) {
                    item(key = "none") {
                        Text(
                            stringResource(R.string.storage_areas_none),
                            Modifier.padding(top = 8.dp).testTag("no_created_areas"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                item(key = "add") {
                    FilledTonalButton(onClick = { form = StorageAreaForm.of(null) }, Modifier.padding(top = 8.dp).fillMaxWidth().testTag("add_area")) {
                        Icon(painterResource(R.drawable.ic_add), contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.storage_area_add))
                    }
                }
            }
        }
    }
}

/**
 * One storage area: its name, its storage kind, and its number of foods. A fixed storage area says so and has no
 * actions. The others open the edit dialog with a tap, and their menu has "Edit" and "Delete".
 */
@Composable
private fun AreaCard(area: StorageArea, foodCount: Int, onEdit: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val separator = stringResource(R.string.meta_separator)
    val detail = listOfNotNull(
        stringResource(area.kind.label()),
        pluralStringResource(R.plurals.item_count, foodCount, foodCount),
        if (area.fixed) stringResource(R.string.storage_area_fixed) else null,
    ).joinToString(" $separator ")
    val colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow)
    val content: @Composable () -> Unit = {
        ListItem(
            headlineContent = { Text(area.name) },
            supportingContent = { Text(detail) },
            trailingContent = if (area.fixed) null else {
                { AreaMenu(area, onEdit, onDelete) }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
    val cardModifier = modifier.fillMaxWidth().testTag("area_card_${area.id}")
    if (area.fixed) {
        Card(cardModifier, shape = MaterialTheme.shapes.large, colors = colors) { content() }
    } else {
        Card(onClick = onEdit, cardModifier, shape = MaterialTheme.shapes.large, colors = colors) { content() }
    }
}

@Composable
private fun AreaMenu(area: StorageArea, onEdit: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, Modifier.testTag("area_menu_${area.id}")) {
            Icon(painterResource(R.drawable.ic_more), contentDescription = stringResource(R.string.food_more_actions, area.name))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_edit)) },
                onClick = {
                    open = false
                    onEdit()
                },
                modifier = Modifier.testTag("menu_edit_area"),
                leadingIcon = { Icon(painterResource(R.drawable.ic_edit), contentDescription = null) },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete)) },
                onClick = {
                    open = false
                    onDelete()
                },
                modifier = Modifier.testTag("menu_delete_area"),
            )
        }
    }
}

/**
 * The dialog that creates or edits a storage area: its name and its storage kind. The storage kind gives the expiry
 * estimate of the food in it. A write that fails leaves the dialog open, with a message.
 */
@Composable
private fun StorageAreaDialog(
    form: StorageAreaForm,
    areas: List<StorageArea>,
    formWrite: FormWrite,
    onChange: (StorageAreaForm) -> Unit,
    onPerform: (InventoryAction, String?) -> Unit,
    onFormWriteRead: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val token = rememberSaveable { UUID.randomUUID().toString() }
    val saving = formWrite == FormWrite.Saving(token)
    val failed = (formWrite as? FormWrite.Failed)?.takeIf { it.token == token }
    LaunchedEffect(formWrite) {
        if (formWrite == FormWrite.Saved(token)) {
            onFormWriteRead(token)
            onDismiss()
        }
    }
    val nameError = form.nameError(areas)?.takeIf { form.checked }?.let {
        stringResource(
            when (it) {
                NameError.EMPTY -> R.string.error_name_empty
                NameError.TOO_LONG -> R.string.error_area_name_too_long
                NameError.IN_USE -> R.string.error_area_name_in_use
            },
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("area_dialog"),
        title = { Text(stringResource(if (form.id == null) R.string.storage_area_add else R.string.storage_area_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = form.name,
                    onValueChange = { onChange(form.copy(name = it)) },
                    modifier = Modifier.fillMaxWidth().semantics { if (nameError != null) error(nameError) }.testTag("field_area_name"),
                    label = { Text(stringResource(R.string.field_name)) },
                    supportingText = nameError?.let { { Text(it, Modifier.testTag("error_area_name")) } },
                    isError = nameError != null,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    singleLine = true,
                )
                Text(
                    stringResource(R.string.field_storage_kind),
                    Modifier.padding(top = 8.dp).semantics { heading() },
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(stringResource(R.string.storage_kind_help), style = MaterialTheme.typography.bodyMedium)
                Column(Modifier.selectableGroup()) {
                    StorageKind.entries.forEach { kind ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(selected = form.kind == kind, role = Role.RadioButton, onClick = { onChange(form.copy(kind = kind)) })
                                .testTag("kind_${kind.key}"),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = form.kind == kind, onClick = null)
                            Text(stringResource(kind.label()), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                if (failed != null) SaveFailedNote(failed.reason)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val action = form.action(areas)
                    when {
                        saving -> {}
                        action == null -> onChange(form.copy(checked = true))
                        else -> onPerform(action, token)
                    }
                },
                Modifier.testTag("area_save"),
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Keeps the form of the dialog when Android stops the app in the background. An empty list is "no dialog". */
private val StorageAreaFormSaver: Saver<StorageAreaForm?, Any> = listSaver(
    save = { form -> if (form == null) emptyList() else listOf(form.id, form.name, form.kind.key, form.checked) },
    restore = { saved ->
        val kind = (saved.getOrNull(2) as String?)?.let(StorageKind::fromKey)
        if (kind == null) null else StorageAreaForm(saved[0] as String?, saved[1] as String, kind, saved[3] as Boolean)
    },
)
