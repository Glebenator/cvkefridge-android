package com.cvke.fridge.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cvke.fridge.R
import com.cvke.fridge.core.CalendarDate
import com.cvke.fridge.core.ExpiryDate
import com.cvke.fridge.core.ExpirySource
import com.cvke.fridge.core.Food
import com.cvke.fridge.core.RejectionReason
import com.cvke.fridge.core.StorageKind
import com.cvke.fridge.inventory.ExpiryInput
import com.cvke.fridge.inventory.TypedQuantity
import com.cvke.fridge.ui.theme.BrandSpinner
import com.cvke.fridge.ui.theme.Size
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * A screen above the tabs: a top app bar with the [title] and a button that goes back, the [content], and the
 * snackbar. The screen stays above the keyboard. A form has a close button ([close]), and its buttons are in the
 * [bottomBar].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubScreen(
    title: String,
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
    tag: String,
    modifier: Modifier = Modifier,
    close: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier.fillMaxSize().imePadding().testTag(tag),
        topBar = {
            TopAppBar(
                title = { Text(title, Modifier.semantics { heading() }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onBack, Modifier.testTag("back")) {
                        Icon(
                            painterResource(if (close) R.drawable.ic_close else R.drawable.ic_arrow_back),
                            contentDescription = stringResource(if (close) R.string.action_close else R.string.action_back),
                        )
                    }
                },
                actions = actions,
            )
        },
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = floatingActionButton,
        contentWindowInsets = WindowInsets.safeDrawing,
        content = content,
    )
}

/** The fields of a form, in a column that scrolls. The lines stay short on a wide screen. */
@Composable
fun FormColumn(padding: PaddingValues, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier.widthIn(max = Size.contentMax).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/** The bar with the buttons of a form, at the bottom edge. The buttons go to a second line when the bar is narrow. */
@Composable
fun FormButtons(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer) {
        FlowRow(
            Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) { content() }
    }
}

/** The loading line or the failed load, in the place of the content of a screen above the tabs. */
@Composable
fun NotReady(state: FridgeUiState, onTryAgain: () -> Unit, padding: PaddingValues) {
    Box(Modifier.padding(padding).fillMaxSize().padding(16.dp), contentAlignment = Alignment.TopCenter) {
        if (state is FridgeUiState.LoadFailed) {
            LoadFailed(onTryAgain)
        } else {
            Row(
                Modifier.padding(top = 32.dp).testTag("loading"),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrandSpinner()
                Text(stringResource(R.string.loading), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/**
 * A field that opens a list of values. It shows the selected value.
 *
 * @param optionTag The test tag of an option.
 * @param footer More items below the values. It gets a function that closes the list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SelectField(
    label: String,
    value: String,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    optionTag: (T) -> String,
    onSelect: (T) -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    footer: (@Composable ColumnScope.(close: () -> Unit) -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }, modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth().testTag(tag),
            readOnly = true,
            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            supportingText = error?.let { { Text(it) } },
            isError = error != null,
            singleLine = true,
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                    modifier = Modifier.testTag(optionTag(option)),
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
            if (footer != null) {
                HorizontalDivider()
                footer { open = false }
            }
        }
    }
}

/** A field that shows a value and opens a dialog when the person selects it. It has the look of the text fields. */
@Composable
fun PickerField(
    label: String,
    value: String,
    onClick: () -> Unit,
    tag: String,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    val source = remember { MutableInteractionSource() }
    val click by rememberUpdatedState(onClick)
    // A text field that is read-only gives its taps to the interaction source.
    LaunchedEffect(source) { source.interactions.collect { if (it is PressInteraction.Release) click() } }
    OutlinedTextField(
        value = value,
        onValueChange = {},
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                role = Role.Button
                onClick {
                    click()
                    true
                }
            }
            .testTag(tag),
        readOnly = true,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingIcon = { Icon(painterResource(icon), contentDescription = null) },
        supportingText = supportingText?.let { { Text(it) } },
        interactionSource = source,
    )
}

/** The quantity field: a number with the decimal keyboard. The [unit] shows after the number when the field has one. */
@Composable
fun QuantityField(
    value: String,
    onChange: (String) -> Unit,
    error: TypedQuantity.Error?,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.field_quantity),
    unit: String? = null,
    errorText: String? = error?.let { stringResource(it.message()) },
    imeAction: ImeAction = ImeAction.Done,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth().semantics { if (errorText != null) error(errorText) }.testTag("field_quantity"),
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        suffix = unit?.let { { Text(it) } },
        supportingText = errorText?.let { { Text(it, Modifier.testTag("error_quantity")) } },
        isError = errorText != null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
        singleLine = true,
    )
}

/**
 * The expiry date of a form. The field shows the date and says if it is an estimated date. A tap opens the date
 * picker for the printed date. The buttons below select no expiry date, or the estimated date again.
 *
 * @param expiry The expiry date that the form saves now.
 * @param suggested The estimated date, or null when there is no estimate.
 * @param estimateHint Where the estimate comes from, or null.
 */
@Composable
fun ExpiryField(
    input: ExpiryInput,
    expiry: ExpiryDate?,
    suggested: ExpiryDate?,
    estimateHint: String?,
    onChange: (ExpiryInput) -> Unit,
    modifier: Modifier = Modifier,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    if (picking) {
        DateDialog(
            initial = expiry?.date,
            onConfirm = {
                picking = false
                onChange(ExpiryInput.Printed(it))
            },
            onDismiss = { picking = false },
        )
    }
    val supporting = when {
        expiry?.source == ExpirySource.ESTIMATED ->
            stringResource(R.string.expiry_help_estimated, estimateHint ?: stringResource(R.string.expiry_estimate_plain))
        expiry != null -> stringResource(R.string.expiry_help_printed)
        input == ExpiryInput.None -> stringResource(R.string.expiry_help_none)
        else -> stringResource(R.string.expiry_help_no_estimate)
    }
    Column(modifier) {
        PickerField(
            label = stringResource(R.string.field_expiry_date),
            value = when {
                expiry == null -> stringResource(R.string.no_expiry_date)
                expiry.source == ExpirySource.ESTIMATED -> stringResource(R.string.expiry_estimated, dateText(expiry.date))
                else -> dateText(expiry.date)
            },
            onClick = { picking = true },
            tag = "field_expiry",
            icon = R.drawable.ic_calendar,
            supportingText = supporting,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (input != ExpiryInput.None) {
                TextButton(onClick = { onChange(ExpiryInput.None) }, Modifier.testTag("expiry_none")) {
                    Text(stringResource(R.string.no_expiry_date))
                }
            }
            if (input != ExpiryInput.Suggested && suggested != null) {
                TextButton(onClick = { onChange(ExpiryInput.Suggested) }, Modifier.testTag("expiry_suggested")) {
                    Text(stringResource(R.string.expiry_use_estimate))
                }
            }
        }
    }
}

/**
 * The Material date picker in a dialog. The person can select a day in the calendar, or type the date. On a screen
 * that is not high, for example a phone on its side, the dialog starts with the text field.
 *
 * @param initial The date that is selected at the start, or null for none. The dialog never selects today by itself.
 * @param latest The last date that the person can select, or null for no limit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateDialog(initial: CalendarDate?, onConfirm: (CalendarDate) -> Unit, onDismiss: () -> Unit, latest: CalendarDate? = null) {
    val height = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    val limit = latest?.let(::utcMillis)
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial?.let(::utcMillis),
        yearRange = 2000..2100,
        initialDisplayMode = if (height < 600.dp) DisplayMode.Input else DisplayMode.Picker,
        selectableDates = remember(limit) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = limit == null || utcTimeMillis <= limit
            }
        },
    )
    val selected = state.selectedDateMillis?.let { CalendarDate.ofInstant(it, 0) }
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { selected?.let(onConfirm) }, Modifier.testTag("date_confirm"), enabled = selected != null) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        DatePicker(state)
    }
}

private val firstDay = checkNotNull(CalendarDate.of(1970, 1, 1))

/** The instant of the start of the date in UTC. The Material date picker uses this value for a date. */
private fun utcMillis(date: CalendarDate): Long = firstDay.daysUntil(date) * 86_400_000L

/** A date in the medium format of the locale, for example "18 Nov 2026". */
@Composable
fun dateText(date: CalendarDate): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(date, locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(LocalDate.of(date.year, date.month, date.day))
    }
}

/** Where an estimate comes from: "Estimate for Milk (Fridge): 7 days". It is null when the food has no days for the kind. */
@Composable
fun estimateHint(food: Food?, kind: StorageKind?): String? {
    val days = food?.days?.get(kind) ?: return null
    val name = food.subtitle?.let { stringResource(R.string.food_with_subtitle, food.name, it) } ?: food.name
    return pluralStringResource(R.plurals.expiry_estimate_hint, days, name, stringResource(checkNotNull(kind).label()), days)
}

/** A write of a form failed: what failed and what to do, above the buttons. Screen readers hear it when it comes. */
@Composable
fun SaveFailedNote(reason: RejectionReason?, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }.testTag("save_failed"),
        colors = CardDefaults.cardColors(scheme.errorContainer, scheme.onErrorContainer),
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_alert), contentDescription = null, Modifier.size(24.dp))
            Text(stringResource(reason?.message() ?: R.string.save_failed_form), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
