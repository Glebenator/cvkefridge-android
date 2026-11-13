package com.cvke.fridge.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cvke.fridge.R
import com.cvke.fridge.core.CalendarDate
import com.cvke.fridge.core.Category
import com.cvke.fridge.core.MeasureUnit
import com.cvke.fridge.inventory.AddAgain
import com.cvke.fridge.inventory.AddFoodForm
import com.cvke.fridge.inventory.ExpiryChoice
import com.cvke.fridge.inventory.ExpiryInput
import com.cvke.fridge.inventory.InventoryAction
import com.cvke.fridge.inventory.Suggestion
import com.cvke.fridge.lookup.ScanSource
import com.cvke.fridge.lookup.Scanned
import java.text.DecimalFormatSymbols
import java.util.UUID

/**
 * The form that adds food by hand: the name, the category, the quantity and the unit, the storage area, and the
 * expiry date. The form is one short screen (`docs/research/competitor-apps.md` in the cvkefridge repository,
 * section 8.3). The name field suggests the foods of the table of foods and the products in the inventory. A name of
 * a product in the inventory adds a batch to that product. The expiry date starts as the estimated date for the food
 * and the storage area, and it is never today by default. "Save and add another" keeps the form open, with the
 * storage area. An empty name field suggests the foods that the person had before and has none of now, and such a
 * food gets the storage area of its newest batch and its usual quantity.
 *
 * "Add again" and "Add a batch" ([Screen.AddFood.again]) open the form with the name of the product, the storage area
 * of its newest batch, its usual quantity, and the estimated date from today. A date of an earlier batch is never in
 * the form.
 *
 * After a scan ([Screen.AddFood.scan]), the form opens with the values of the product lookup: the name, the category,
 * the food of the table of foods, and the quantity of the package. The form says where the values come from, and the
 * expiry date shows that it is an estimate. Everything stays editable. When Open Food Facts does not have the
 * barcode, the name is empty and has the focus, and the barcode stays. "Save and scan next" saves and shows the
 * scanner again. The name field of an empty form has a button that opens the scanner.
 *
 * A write that fails leaves the form as it is, with a message above the buttons.
 *
 * @param onSaved The changes are in the change log. It gets true for "Save and scan next".
 */
@Composable
fun AddFoodScreen(
    screen: Screen.AddFood,
    state: FridgeUiState,
    formWrite: FormWrite,
    snackbar: SnackbarHostState,
    onPerform: (InventoryAction, String) -> Unit,
    onFormWriteRead: (String) -> Unit,
    onManageAreas: () -> Unit,
    onScan: () -> Unit,
    onTryAgain: () -> Unit,
    onClose: () -> Unit,
    onSaved: (scanNext: Boolean) -> Unit,
) {
    val ready = state as? FridgeUiState.Ready
    if (ready == null) {
        SubScreen(stringResource(R.string.add_food), onClose, snackbar, "add_food_screen", close = true) { NotReady(state, onTryAgain, it) }
        return
    }
    val products = ready.inventory.products
    val areas = ready.inventory.storageAreas
    val scan = screen.scan
    val locale = LocalConfiguration.current.locales[0]
    val separator = DecimalFormatSymbols.getInstance(locale).decimalSeparator
    val token = rememberSaveable { UUID.randomUUID().toString() }
    var form by rememberSaveable(stateSaver = AddFoodFormSaver) {
        val area = AddFoodForm.startArea(ready.lastAreaId, screen.openAreaId, areas)
        val existing = products.firstOrNull { it.id == screen.productId }
        mutableStateOf(
            when {
                existing != null && screen.again ->
                    AddFoodForm.again(existing, AddAgain.values(ready.inventory, existing.id), area, separator)
                existing != null -> AddFoodForm(name = existing.name, storageAreaId = area, barcode = scan?.barcode)
                scan != null -> AddFoodForm.scanned(scan, area, separator)
                else -> AddFoodForm(storageAreaId = area)
            },
        )
    }
    var another by rememberSaveable { mutableStateOf(false) }
    val nameFocus = remember { FocusRequester() }
    var nameFocused by remember { mutableStateOf(false) }
    // With no name from the lookup, the person types the name first.
    var focusAsked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!focusAsked && scan != null && form.name.isEmpty()) nameFocus.requestFocus()
        focusAsked = true
    }

    val saving = formWrite == FormWrite.Saving(token)
    val failed = (formWrite as? FormWrite.Failed)?.takeIf { it.token == token }
    LaunchedEffect(formWrite) {
        if (formWrite == FormWrite.Saved(token)) {
            onFormWriteRead(token)
            when {
                scan != null -> onSaved(another)
                another -> {
                    form = form.next()
                    nameFocus.requestFocus()
                }
                else -> onSaved(false)
            }
        }
    }
    fun save(addAnother: Boolean) {
        if (saving) return
        val action = form.action(products, areas, ready.foods, ready.today)
        if (action == null) {
            form = form.copy(checked = true)
        } else {
            another = addAnother
            onPerform(action, token)
        }
    }

    val existing = form.product(products)
    val inStock = remember(ready.inventory) { ready.inventory.activeBatches.mapTo(HashSet()) { it.productId } }
    val area = areas.firstOrNull { it.id == form.storageAreaId }
    val suggested = form.estimate(products, areas, ready.foods, ready.today)
    val nameError = form.nameError()?.takeIf { form.checked }?.let { stringResource(it.foodMessage()) }

    SubScreen(
        title = stringResource(R.string.add_food),
        onBack = onClose,
        snackbar = snackbar,
        tag = "add_food_screen",
        close = true,
        bottomBar = {
            FormButtons {
                if (scan != null) {
                    OutlinedButton(onClick = { save(addAnother = true) }, Modifier.testTag("save_scan_next"), enabled = !saving) {
                        Text(stringResource(R.string.action_save_and_scan_next))
                    }
                } else {
                    OutlinedButton(onClick = { save(addAnother = true) }, Modifier.testTag("save_another"), enabled = !saving) {
                        Text(stringResource(R.string.action_save_and_add_another))
                    }
                }
                Button(onClick = { save(addAnother = false) }, Modifier.testTag("save"), enabled = !saving) {
                    Text(stringResource(R.string.action_save))
                }
            }
        },
    ) { padding ->
        FormColumn(padding) {
            if (scan != null) ScanNote(scan)
            OutlinedTextField(
                value = form.name,
                onValueChange = { form = form.withName(it, ready.foods) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(nameFocus)
                    .onFocusChanged { nameFocused = it.isFocused }
                    .semantics { if (nameError != null) error(nameError) }
                    .testTag("field_name"),
                label = { Text(stringResource(R.string.field_name)) },
                trailingIcon = if (scan != null || form.name.isNotEmpty()) null else {
                    {
                        IconButton(onScan, Modifier.testTag("name_scan")) {
                            Icon(painterResource(R.drawable.ic_barcode_scan), contentDescription = stringResource(R.string.action_scan_barcode))
                        }
                    }
                },
                supportingText = when {
                    nameError != null -> ({ Text(nameError, Modifier.testTag("error_name")) })
                    existing != null -> ({
                        Text(
                            stringResource(if (existing.id in inStock) R.string.add_food_existing else R.string.add_food_again),
                            Modifier.testTag("name_existing"),
                        )
                    })
                    else -> null
                },
                isError = nameError != null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                singleLine = true,
            )
            // An empty name field shows the food that the person had before and has none of now. A form after a scan
            // has its own name, so it does not show them.
            val recent = scan == null && form.name.isBlank()
            val suggestions = remember(form.name, form.foodId, products, ready.foods, ready.history, recent) {
                if (recent) {
                    AddAgain.recent(ready.history.lines, ready.inventory).map(Suggestion::OfProduct)
                } else {
                    form.suggestions(products, ready.foods)
                }
            }
            if (nameFocused && suggestions.isNotEmpty()) {
                Suggestions(
                    suggestions,
                    inStock = inStock,
                    title = if (recent) stringResource(R.string.suggestions_recent) else null,
                    onSelect = { suggestion ->
                        val selected = form.withSuggestion(suggestion)
                        // A product that the person had before gets its storage area and its usual quantity.
                        val product = (suggestion as? Suggestion.OfProduct)?.product?.takeIf { it.id !in inStock }
                        form = if (product == null) selected else selected.again(AddAgain.values(ready.inventory, product.id), separator)
                    },
                )
            }

            if (existing == null) {
                SelectField(
                    label = stringResource(R.string.field_category),
                    value = stringResource(form.category.label()),
                    options = Category.entries,
                    optionLabel = { stringResource(it.label()) },
                    optionTag = { "category_option_${it.key}" },
                    onSelect = { form = form.copy(category = it, categoryChosen = true) },
                    tag = "field_category",
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuantityField(
                    value = form.quantity,
                    onChange = { form = form.copy(quantity = it) },
                    error = form.quantityError()?.takeIf { form.checked },
                    modifier = Modifier.weight(1f),
                    // The unit of a product in the inventory is the unit of all its batches.
                    unit = existing?.unit?.let { stringResource(unitName(it)) },
                )
                if (existing == null) {
                    SelectField(
                        label = stringResource(R.string.field_unit),
                        value = stringResource(unitName(form.unit)),
                        options = listOf<MeasureUnit?>(null) + MeasureUnit.entries,
                        optionLabel = { stringResource(unitName(it)) },
                        optionTag = { "unit_option_${it?.key ?: "none"}" },
                        onSelect = { form = form.copy(unit = it) },
                        tag = "field_unit",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            StorageAreaField(
                areas = ready.content.areas,
                selectedId = form.storageAreaId,
                onSelect = { form = form.copy(storageAreaId = it) },
                onManageAreas = onManageAreas,
                error = form.checked && area == null,
            )
            ExpiryField(
                input = form.expiry,
                expiry = ExpiryChoice.expiry(form.expiry, suggested),
                suggested = suggested,
                estimateHint = estimateHint(form.food(products, ready.foods), area?.kind),
                onChange = { form = form.copy(expiry = it) },
            )
            if (failed != null) SaveFailedNote(failed.reason)
        }
    }
}

/** The storage area of a form: a list of the storage areas, and an item that opens the screen of the storage areas. */
@Composable
fun StorageAreaField(
    areas: List<AreaSummary>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onManageAreas: () -> Unit,
    modifier: Modifier = Modifier,
    error: Boolean = false,
) {
    SelectField(
        label = stringResource(R.string.filter_storage_area),
        value = areas.firstOrNull { it.id == selectedId }?.name.orEmpty(),
        options = areas,
        optionLabel = { it.name },
        optionTag = { "area_option_${it.id}" },
        onSelect = { onSelect(it.id) },
        tag = "field_storage_area",
        modifier = modifier,
        error = if (error) stringResource(R.string.error_storage_area) else null,
        footer = { close ->
            DropdownMenuItem(
                text = { Text(stringResource(R.string.storage_areas_manage)) },
                onClick = {
                    close()
                    onManageAreas()
                },
                modifier = Modifier.testTag("area_manage"),
            )
        },
    )
}

/**
 * The suggestions below the name field. A product in the inventory says so, and a product that the person had before
 * says that also. A food of the table shows the text that tells it from the other foods with the same name. The list
 * of recent foods has a [title].
 *
 * @param inStock The products with an active batch.
 */
@Composable
private fun Suggestions(
    suggestions: List<Suggestion>,
    inStock: Set<String>,
    title: String?,
    onSelect: (Suggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(modifier.fillMaxWidth().testTag("suggestions")) {
        Column {
            if (title != null) {
                Text(
                    title,
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).semantics { heading() },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            suggestions.forEachIndexed { index, suggestion ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val (name, detail, tag) = when (suggestion) {
                    is Suggestion.OfProduct -> Triple(
                        suggestion.product.name,
                        stringResource(if (suggestion.product.id in inStock) R.string.suggestion_in_inventory else R.string.suggestion_had_before),
                        "suggestion_product_${suggestion.product.id}",
                    )
                    is Suggestion.OfFood -> Triple(suggestion.food.name, suggestion.food.subtitle, "suggestion_food_${suggestion.food.id}")
                }
                ListItem(
                    headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.clickable { onSelect(suggestion) }.testTag(tag),
                    supportingContent = detail?.let { { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) } },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }
}

/**
 * Where the values of a scan come from, above the fields: "From Open Food Facts", or that the person must type the
 * name. It also shows the barcode that the food keeps. A product in the inventory needs no note: the name field says
 * that the form adds a batch to it.
 */
@Composable
private fun ScanNote(scan: Scanned, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val text = when (scan.source) {
        ScanSource.FOUND -> R.string.scan_from_open_food_facts
        ScanSource.NOT_FOUND -> R.string.scan_not_found
        ScanSource.NOT_LOOKED_UP -> R.string.scan_not_looked_up
        ScanSource.IN_INVENTORY -> return
    }
    OutlinedCard(modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag("scan_note")) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(R.drawable.ic_barcode_scan), contentDescription = null, tint = scheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(text), color = scheme.onSurface, style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.scan_barcode, scan.barcode),
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** Keeps the form when Android stops the app in the background, and while another screen is above the form. */
private val AddFoodFormSaver: Saver<AddFoodForm, Any> = listSaver(
    save = { form ->
        listOf(
            form.name,
            form.foodId,
            form.category.key,
            form.categoryChosen,
            form.quantity,
            form.unit?.key,
            form.storageAreaId,
            expiryInputText(form.expiry),
            form.checked,
            form.barcode,
        )
    },
    restore = { saved ->
        AddFoodForm(
            name = saved[0] as String,
            foodId = saved[1] as String?,
            category = Category.fromKey(saved[2] as String) ?: Category.OTHER,
            categoryChosen = saved[3] as Boolean,
            quantity = saved[4] as String,
            unit = (saved[5] as String?)?.let(MeasureUnit::fromKey),
            storageAreaId = saved[6] as String,
            expiry = expiryInputOf(saved[7] as String),
            checked = saved[8] as Boolean,
            barcode = saved[9] as String?,
        )
    },
)

/** The text of an [ExpiryInput] in saved state, and back. */
fun expiryInputText(input: ExpiryInput): String = when (input) {
    ExpiryInput.Suggested -> "suggested"
    ExpiryInput.None -> "none"
    is ExpiryInput.Printed -> input.date.text
}

fun expiryInputOf(text: String): ExpiryInput = when (text) {
    "suggested" -> ExpiryInput.Suggested
    "none" -> ExpiryInput.None
    else -> CalendarDate.parse(text)?.let(ExpiryInput::Printed) ?: ExpiryInput.Suggested
}
