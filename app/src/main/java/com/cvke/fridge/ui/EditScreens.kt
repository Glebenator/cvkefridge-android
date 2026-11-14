package com.cvke.fridge.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.cvke.fridge.R
import com.cvke.fridge.core.CalendarDate
import com.cvke.fridge.core.Category
import com.cvke.fridge.core.MeasureUnit
import com.cvke.fridge.inventory.BatchForm
import com.cvke.fridge.inventory.ExpiryChoice
import com.cvke.fridge.inventory.InventoryAction
import com.cvke.fridge.inventory.ProductForm
import java.text.DecimalFormatSymbols
import java.util.UUID

/**
 * The form that edits a product: its name, its category, its unit, and its note. The unit is the unit of all batches of the
 * product. A write that fails leaves the form as it is, with a message.
 */
@Composable
fun EditProductScreen(
    screen: Screen.EditProduct,
    state: FridgeUiState,
    formWrite: FormWrite,
    snackbar: SnackbarHostState,
    onPerform: (InventoryAction, String) -> Unit,
    onFormWriteRead: (String) -> Unit,
    onTryAgain: () -> Unit,
    onClose: () -> Unit,
) {
    val title = stringResource(R.string.edit_food)
    val ready = state as? FridgeUiState.Ready
    val product = ready?.inventory?.products?.firstOrNull { it.id == screen.productId }
    if (ready == null || product == null) {
        if (ready != null) LaunchedEffect(Unit) { onClose() }
        SubScreen(title, onClose, snackbar, "edit_food_screen", close = true) { NotReady(state, onTryAgain, it) }
        return
    }
    val products = ready.inventory.products
    val token = rememberSaveable { UUID.randomUUID().toString() }
    var form by rememberSaveable(stateSaver = ProductFormSaver) { mutableStateOf(ProductForm.of(product)) }
    val saving = formWrite == FormWrite.Saving(token)
    val failed = (formWrite as? FormWrite.Failed)?.takeIf { it.token == token }
    SavedEffect(formWrite, token, onFormWriteRead, onClose)

    val nameError = form.nameError(product, products)?.takeIf { form.checked }?.let { stringResource(it.foodMessage()) }
    SubScreen(
        title = title,
        onBack = onClose,
        snackbar = snackbar,
        tag = "edit_food_screen",
        close = true,
        bottomBar = {
            SaveButton(saving) {
                val action = form.action(product, products, ready.foods)
                if (action == null) form = form.copy(checked = true) else onPerform(action, token)
            }
        },
    ) { padding ->
        FormColumn(padding) {
            OutlinedTextField(
                value = form.name,
                onValueChange = { form = form.copy(name = it) },
                modifier = Modifier.fillMaxWidth().semantics { if (nameError != null) error(nameError) }.testTag("field_name"),
                label = { Text(stringResource(R.string.field_name)) },
                supportingText = nameError?.let { { Text(it, Modifier.testTag("error_name")) } },
                isError = nameError != null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                singleLine = true,
            )
            SelectField(
                label = stringResource(R.string.field_category),
                value = stringResource(form.category.label()),
                options = Category.entries,
                optionLabel = { stringResource(it.label()) },
                optionTag = { "category_option_${it.key}" },
                onSelect = { form = form.copy(category = it) },
                tag = "field_category",
            )
            SelectField(
                label = stringResource(R.string.field_unit),
                value = stringResource(unitName(form.unit)),
                options = listOf<MeasureUnit?>(null) + MeasureUnit.entries,
                optionLabel = { stringResource(unitName(it)) },
                optionTag = { "unit_option_${it?.key ?: "none"}" },
                onSelect = { form = form.copy(unit = it) },
                tag = "field_unit",
            )
            Text(
                stringResource(R.string.edit_food_unit_note),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = form.note,
                onValueChange = { form = form.copy(note = it) },
                modifier = Modifier.fillMaxWidth().testTag("field_note"),
                label = { Text(stringResource(R.string.field_note)) },
                placeholder = { Text(stringResource(R.string.field_note_placeholder)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                minLines = 3,
            )
            if (failed != null) SaveFailedNote(failed.reason)
        }
    }
}

/**
 * The form that edits a batch: its quantity, its storage area, its date added, and its expiry date. An estimated
 * date follows a change of the storage area or of the date added. A printed date stays.
 */
@Composable
fun EditBatchScreen(
    screen: Screen.EditBatch,
    state: FridgeUiState,
    formWrite: FormWrite,
    snackbar: SnackbarHostState,
    onPerform: (InventoryAction, String) -> Unit,
    onFormWriteRead: (String) -> Unit,
    onManageAreas: () -> Unit,
    onTryAgain: () -> Unit,
    onClose: () -> Unit,
) {
    val title = stringResource(R.string.edit_batch)
    val ready = state as? FridgeUiState.Ready
    val batch = ready?.inventory?.batches?.firstOrNull { it.id == screen.batchId }
    val product = ready?.inventory?.products?.firstOrNull { it.id == batch?.productId }
    if (ready == null || batch == null || product == null) {
        if (ready != null) LaunchedEffect(Unit) { onClose() }
        SubScreen(title, onClose, snackbar, "edit_batch_screen", close = true) { NotReady(state, onTryAgain, it) }
        return
    }
    val areas = ready.inventory.storageAreas
    val locale = LocalConfiguration.current.locales[0]
    val token = rememberSaveable { UUID.randomUUID().toString() }
    var form by rememberSaveable(stateSaver = BatchFormSaver) {
        mutableStateOf(BatchForm.of(batch, DecimalFormatSymbols.getInstance(locale).decimalSeparator))
    }
    var pickingDateAdded by rememberSaveable { mutableStateOf(false) }
    val saving = formWrite == FormWrite.Saving(token)
    val failed = (formWrite as? FormWrite.Failed)?.takeIf { it.token == token }
    SavedEffect(formWrite, token, onFormWriteRead, onClose)

    if (pickingDateAdded) {
        DateDialog(
            initial = form.dateAdded,
            onConfirm = {
                pickingDateAdded = false
                form = form.copy(dateAdded = it)
            },
            onDismiss = { pickingDateAdded = false },
            // A batch cannot come into the inventory after today.
            latest = ready.today,
        )
    }
    val area = areas.firstOrNull { it.id == form.storageAreaId }
    val suggested = form.suggested(batch, product, areas, ready.foods)
    val food = product.foodId?.let(ready.foods::food)
    val tableEstimate = if (food == null || area == null) null else ready.foods.estimate(food.id, area.kind, form.dateAdded)
    SubScreen(
        title = title,
        onBack = onClose,
        snackbar = snackbar,
        tag = "edit_batch_screen",
        close = true,
        bottomBar = {
            SaveButton(saving) {
                val action = form.action(batch, product, areas, ready.foods)
                if (action == null) form = form.copy(checked = true) else onPerform(action, token)
            }
        },
    ) { padding ->
        FormColumn(padding) {
            Text(product.name, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge)
            QuantityField(
                value = form.quantity,
                onChange = { form = form.copy(quantity = it) },
                error = form.quantityError()?.takeIf { form.checked },
                unit = product.unit?.let { stringResource(unitName(it)) },
            )
            StorageAreaField(
                areas = ready.content.areas,
                selectedId = form.storageAreaId,
                onSelect = { form = form.copy(storageAreaId = it) },
                onManageAreas = onManageAreas,
                error = form.checked && area == null,
            )
            PickerField(
                label = stringResource(R.string.field_date_added),
                value = dateText(form.dateAdded),
                onClick = { pickingDateAdded = true },
                tag = "field_date_added",
                icon = R.drawable.ic_calendar,
            )
            ExpiryField(
                input = form.expiry,
                expiry = ExpiryChoice.expiry(form.expiry, suggested),
                suggested = suggested,
                // The hint is true only for a date that comes from the table of foods for these values.
                estimateHint = if (suggested != null && suggested == tableEstimate) estimateHint(food, area?.kind) else null,
                onChange = { form = form.copy(expiry = it) },
            )
            if (failed != null) SaveFailedNote(failed.reason)
        }
    }
}

/** Closes the form when its changes are in the change log. */
@Composable
private fun SavedEffect(formWrite: FormWrite, token: String, onFormWriteRead: (String) -> Unit, onClose: () -> Unit) {
    LaunchedEffect(formWrite) {
        if (formWrite == FormWrite.Saved(token)) {
            onFormWriteRead(token)
            onClose()
        }
    }
}

/** The one button of an edit form. A second tap has no effect while the write runs. */
@Composable
private fun SaveButton(saving: Boolean, onSave: () -> Unit) {
    FormButtons {
        Button(onClick = { if (!saving) onSave() }, Modifier.testTag("save"), enabled = !saving) {
            Text(stringResource(R.string.action_save))
        }
    }
}

private val ProductFormSaver: Saver<ProductForm, Any> = listSaver(
    save = { listOf(it.name, it.category.key, it.unit?.key, it.note, it.checked) },
    restore = { saved ->
        ProductForm(
            name = saved[0] as String,
            category = Category.fromKey(saved[1] as String) ?: Category.OTHER,
            unit = (saved[2] as String?)?.let(MeasureUnit::fromKey),
            note = saved[3] as String,
            checked = saved[4] as Boolean,
        )
    },
)

private val BatchFormSaver: Saver<BatchForm, Any> = listSaver(
    save = { listOf(it.quantity, it.storageAreaId, it.dateAdded.text, expiryInputText(it.expiry), it.checked) },
    restore = { saved ->
        CalendarDate.parse(saved[2] as String)?.let { dateAdded ->
            BatchForm(
                quantity = saved[0] as String,
                storageAreaId = saved[1] as String,
                dateAdded = dateAdded,
                expiry = expiryInputOf(saved[3] as String),
                checked = saved[4] as Boolean,
            )
        }
    },
)
