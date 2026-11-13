package com.cvke.fridge.ui

import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
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
import com.cvke.fridge.photo.PreparedPhoto
import com.cvke.fridge.photo.ProductPhoto
import com.cvke.fridge.ui.theme.BrandSpinner
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
 * The form can give the food a photo: "Take a photo" opens the camera app, and "Choose a photo" opens the photo picker
 * (see [rememberPhotoSources]). The field is at the end of the form, because the photo is optional. It is there for a
 * new food, and for a food in the inventory with no photo. The app makes the photo at once, and the product gets it
 * after the save.
 *
 * A write that fails leaves the form as it is, with a message above the buttons.
 *
 * @param photos The photo of each product that has one.
 * @param formPhotos The photos that the app makes for the add forms, by the token of the form.
 * @param onPerform Saves the action. It also gets the photo of the form, or null.
 * @param onSaved The changes are in the change log. It gets true for "Save and scan next".
 */
@Composable
fun AddFoodScreen(
    screen: Screen.AddFood,
    state: FridgeUiState,
    formWrite: FormWrite,
    snackbar: SnackbarHostState,
    photos: Map<String, ProductPhoto>,
    formPhotos: Map<String, FormPhoto>,
    pickPhoto: ActivityResultContract<Unit, Uri?>,
    takePhoto: ActivityResultContract<Uri, Boolean>,
    newCapture: suspend () -> Uri,
    onMakePhoto: (token: String, Uri) -> Unit,
    onFormPhotoRead: (token: String) -> Unit,
    onDiscardPhoto: (PreparedPhoto) -> Unit,
    onPerform: (InventoryAction, String, PreparedPhoto?) -> Unit,
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
                scan != null -> AddFoodForm.scanned(scan, area, separator).withUsualArea(ready.foods, areas)
                else -> AddFoodForm(storageAreaId = area)
            },
        )
    }
    var another by rememberSaveable { mutableStateOf(false) }
    // The path of the photo of the form. It stays when Android stops the app in the background.
    var photo by rememberSaveable { mutableStateOf<String?>(null) }
    val makingPhoto = formPhotos[token] == FormPhoto.Making
    LaunchedEffect(formPhotos[token]) {
        val made = formPhotos[token] as? FormPhoto.Ready ?: return@LaunchedEffect
        photo?.let { onDiscardPhoto(PreparedPhoto(it)) }
        photo = made.photo.path
        onFormPhotoRead(token)
    }
    val sources = rememberPhotoSources(pickPhoto, takePhoto, newCapture) { onMakePhoto(token, it) }
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
                    // The saved food got the photo.
                    photo = null
                    nameFocus.requestFocus()
                }
                else -> onSaved(false)
            }
        }
    }
    val existing = form.product(products)
    // A food in the inventory that has a photo keeps it. The form then has no photo field.
    val photoField = existing == null || photos[existing.id] == null
    fun save(addAnother: Boolean) {
        if (saving || makingPhoto) return
        val action = form.action(products, areas, ready.foods, ready.today)
        if (action == null) {
            form = form.copy(checked = true)
        } else {
            another = addAnother
            onPerform(action, token, photo?.takeIf { photoField }?.let(::PreparedPhoto))
        }
    }

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
            // A new food can come from a barcode. The card is first in the form, so the scanner is one tap away.
            if (scan == null && screen.productId == null) ScanCard(onScan)
            OutlinedTextField(
                value = form.name,
                onValueChange = { form = form.withName(it, ready.foods, areas) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(nameFocus)
                    .onFocusChanged { nameFocused = it.isFocused }
                    .semantics { if (nameError != null) error(nameError) }
                    .testTag("field_name"),
                label = { Text(stringResource(R.string.field_name)) },
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
            val showSuggestions = nameFocused && suggestions.isNotEmpty()
            // On a small screen with the keyboard open, the suggestions can be below the visible part of the form.
            val suggestionsView = remember { BringIntoViewRequester() }
            LaunchedEffect(showSuggestions, suggestions) {
                if (showSuggestions) suggestionsView.bringIntoView()
            }
            if (showSuggestions) {
                Suggestions(
                    suggestions,
                    modifier = Modifier.bringIntoViewRequester(suggestionsView),
                    inStock = inStock,
                    title = if (recent) stringResource(R.string.suggestions_recent) else null,
                    onSelect = { suggestion ->
                        val selected = form.withSuggestion(suggestion, ready.foods, areas)
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
                onSelect = { form = form.withArea(it) },
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
            if (photoField) {
                FormPhotoField(
                    photo = photo,
                    making = makingPhoto,
                    sources = sources,
                    onRemove = {
                        photo?.let { onDiscardPhoto(PreparedPhoto(it)) }
                        photo = null
                    },
                )
            }
            if (failed != null) SaveFailedNote(failed.reason)
        }
    }
}

/**
 * The photo of the add form: "Take a photo" and "Choose a photo", or the photo with "Change photo" and "Remove". While
 * the app makes the photo, the field says so, and screen readers hear it.
 */
@Composable
private fun FormPhotoField(photo: String?, making: Boolean, sources: PhotoSources, onRemove: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().testTag("form_photo"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.field_photo),
            Modifier.semantics { heading() },
            color = scheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
        )
        when {
            making -> Row(
                Modifier.heightIn(min = 48.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }.testTag("form_photo_making"),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrandSpinner()
                Text(stringResource(R.string.photo_adding), color = scheme.onSurface, style = MaterialTheme.typography.bodyLarge)
            }
            photo != null -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                ProductPhotoImage(
                    ProductPhoto(photo),
                    MaterialTheme.shapes.medium,
                    scheme.surfaceContainerHigh,
                    Modifier.size(96.dp).testTag("form_photo_image"),
                )
                Column {
                    ChangePhotoButton(sources)
                    PhotoButton(R.drawable.ic_image_remove, R.string.photo_remove, "form_photo_remove", onRemove)
                }
            }
            else -> PhotoButtons(sources)
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
            form.areaKept,
            form.startAreaId,
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
            areaKept = saved[10] as Boolean,
            startAreaId = saved[11] as String,
        )
    },
)

/** The text of an [ExpiryInput] in saved state, and back. */
fun expiryInputText(input: ExpiryInput): String = when (input) {
    ExpiryInput.Suggested -> "suggested"
    ExpiryInput.None -> "none"
    is ExpiryInput.Printed -> input.date.text
    is ExpiryInput.Chosen -> CHOSEN_PREFIX + input.date.text
}

fun expiryInputOf(text: String): ExpiryInput = when (text) {
    "suggested" -> ExpiryInput.Suggested
    "none" -> ExpiryInput.None
    else -> if (text.startsWith(CHOSEN_PREFIX)) {
        CalendarDate.parse(text.removePrefix(CHOSEN_PREFIX))?.let(ExpiryInput::Chosen) ?: ExpiryInput.Suggested
    } else {
        CalendarDate.parse(text)?.let(ExpiryInput::Printed) ?: ExpiryInput.Suggested
    }
}

private const val CHOSEN_PREFIX = "chosen:"

/**
 * The large button at the top of the form for a new food: it opens the scanner, and the scan fills in the form. It
 * is larger than the fields, because a scan is the fast way to add food that has a barcode.
 */
@Composable
private fun ScanCard(onScan: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onScan,
        modifier = Modifier.fillMaxWidth().testTag("name_scan"),
        shape = RoundedCornerShape(24.dp),
        color = scheme.primaryContainer,
        contentColor = scheme.onPrimaryContainer,
    ) {
        Row(Modifier.heightIn(min = 88.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(56.dp), shape = CircleShape, color = scheme.primary, contentColor = scheme.onPrimary) {
                Icon(painterResource(R.drawable.ic_barcode_scan), contentDescription = null, Modifier.padding(16.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.action_scan_barcode), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.scan_card_text), style = MaterialTheme.typography.bodyMedium)
            }
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null)
        }
    }
}
