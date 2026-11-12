package com.cvke.fridge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
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
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.cvke.fridge.R
import com.cvke.fridge.core.Category
import com.cvke.fridge.ui.theme.IconTile
import com.cvke.fridge.ui.theme.Size
import com.cvke.fridge.ui.theme.fadingEdges

/**
 * My Fridge, the screen that lists all food. Its contents are those of the My Fridge screen of FridgeFriend
 * (`app/(tabs)/fridge.tsx` in the `basic_ff` repository): a search field, chips for the storage area and the
 * category, a sort control, and one card for each food in each storage area. The parts are Material 3 components: a
 * top app bar with the sort actions, filter chips, and list items. The add button and the actions of a card are not
 * here, because this app cannot add or edit food yet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyFridgeScreen(state: FridgeUiState, onTryAgain: () -> Unit, insets: PaddingValues, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val direction = LocalLayoutDirection.current
    val side = PaddingValues(
        start = insets.calculateStartPadding(direction) + 16.dp,
        end = insets.calculateEndPadding(direction) + 16.dp,
    )
    var areaId by rememberSaveable { mutableStateOf<String?>(null) }
    var category by rememberSaveable { mutableStateOf<Category?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var sortKey by rememberSaveable { mutableStateOf(SortKey.EXPIRY) }
    var ascending by rememberSaveable { mutableStateOf(SortKey.EXPIRY.ascendingFirst) }

    val content = (state as? FridgeUiState.Ready)?.content
    // A storage area or a category that has no food now shows all.
    val area = content?.areas?.firstOrNull { it.id == areaId }
    val shownCategory = category?.takeIf { content?.categories?.contains(it) == true }
    val rows = remember(content, area, shownCategory, query, sortKey, ascending) {
        content?.let { InventoryContent.sort(InventoryContent.filter(it.foods, area?.id, shownCategory, query), sortKey, ascending) }
            .orEmpty()
    }

    // The bar stays at the top and gets the colour of a raised surface when the list is below it.
    val barScroll = TopAppBarDefaults.pinnedScrollBehavior()
    Column(modifier.fillMaxSize().background(scheme.surface)) {
        TopAppBar(
            title = { Text(stringResource(R.string.tab_my_fridge), Modifier.semantics { heading() }) },
            actions = {
                SortActions(sortKey, ascending) { key, order ->
                    sortKey = key
                    ascending = order
                }
            },
            scrollBehavior = barScroll,
        )
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier
                    .widthIn(max = Size.contentMax)
                    .fillMaxSize()
                    .nestedScroll(barScroll.nestedScrollConnection)
                    .testTag("my_fridge_screen"),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "header") {
                    Column {
                        if (content?.sample == true) SampleNote(Modifier.padding(side).padding(bottom = 12.dp))
                        SearchField(query, onChange = { query = it }, Modifier.padding(side))
                        Spacer(Modifier.size(8.dp))
                        ChipRow(side) {
                            val group = stringResource(R.string.chips_storage_area)
                            Chip(stringResource(R.string.chip_all), group, area == null, "chip_area_all") { areaId = null }
                            content?.areas?.forEach { Chip(it.name, group, it.id == area?.id, "chip_area_${it.id}") { areaId = it.id } }
                        }
                        ChipRow(side) {
                            val group = stringResource(R.string.chips_category)
                            Chip(stringResource(R.string.chip_all), group, shownCategory == null, "chip_category_all") { category = null }
                            content?.categories?.forEach {
                                Chip(stringResource(it.label()), group, it == shownCategory, "chip_category_${it.key}") { category = it }
                            }
                        }
                    }
                }
                when {
                    state is FridgeUiState.LoadFailed -> item(key = "failed") { LoadFailed(onTryAgain, Modifier.padding(side)) }
                    content == null -> item(key = "loading") { ListMessage(stringResource(R.string.loading), "loading") }
                    content.foods.isEmpty() -> item(key = "none") { EmptyState(stringResource(R.string.no_items), "no_items") }
                    rows.isEmpty() -> item(key = "no_match") { ListMessage(stringResource(R.string.list_no_match), "list_no_match") }
                    else -> items(rows, key = { it.key }) { FoodCard(it, Modifier.padding(side)) }
                }
            }
        }
    }
}

/** The search field, with the shape and the colours of a Material search bar. It filters while the person types. */
@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    val textStyle = MaterialTheme.typography.bodyLarge
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).background(scheme.surfaceContainerHigh, CircleShape).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = scheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.weight(1f).testTag("search_field"),
            textStyle = textStyle.copy(color = scheme.onSurface),
            singleLine = true,
            cursorBrush = SolidColor(scheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            decorationBox = { field ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(stringResource(R.string.search_placeholder), color = scheme.onSurfaceVariant, style = textStyle, maxLines = 1)
                    }
                    field()
                }
            },
        )
        if (value.isEmpty()) {
            Spacer(Modifier.width(12.dp))
        } else {
            IconButton(onClick = { onChange("") }, Modifier.testTag("search_clear")) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.search_clear), tint = scheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * One row of filter chips that scrolls sideways to the edges of the screen, with a fade at an edge where the row
 * continues. One chip of the row is selected.
 */
@Composable
private fun ChipRow(side: PaddingValues, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val scroll = rememberScrollState()
    Row(
        modifier.fadingEdges(scroll, MaterialTheme.colorScheme.surface).horizontalScroll(scroll).padding(side).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

/** A Material filter chip. Screen readers hear the [group] with the label, because the two rows each have a chip "All". */
@Composable
private fun Chip(label: String, group: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    val description = stringResource(R.string.chip_description, group, label)
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = Modifier.semantics { contentDescription = description }.testTag(tag),
        leadingIcon = if (!selected) null else {
            { Icon(painterResource(R.drawable.ic_check), contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) }
        },
    )
}

/**
 * The sort actions of the top app bar: a button that opens the list of sort orders, and a button for the direction.
 * A new order starts in its own first direction.
 */
@Composable
private fun SortActions(sortKey: SortKey, ascending: Boolean, onChange: (SortKey, Boolean) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, Modifier.testTag("sort_key")) {
            Icon(
                painterResource(R.drawable.ic_sort),
                contentDescription = stringResource(R.string.sort_change_key, stringResource(sortKey.label())),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SortKey.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.label())) },
                    onClick = {
                        open = false
                        onChange(option, if (option == sortKey) ascending else option.ascendingFirst)
                    },
                    modifier = Modifier.testTag("sort_option_${option.name.lowercase()}"),
                    trailingIcon = if (option != sortKey) null else {
                        { Icon(painterResource(R.drawable.ic_check), contentDescription = null) }
                    },
                )
            }
        }
    }
    IconButton(onClick = { onChange(sortKey, !ascending) }, Modifier.testTag("sort_order")) {
        Icon(
            painterResource(if (ascending) R.drawable.ic_arrow_up else R.drawable.ic_arrow_down),
            contentDescription = stringResource(if (ascending) R.string.sort_ascending else R.string.sort_descending),
        )
    }
}

/** One line in the place of the list: the list loads, or no food is there for the chips and the search text. */
@Composable
private fun ListMessage(text: String, tag: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.fillMaxWidth().padding(32.dp).semantics { liveRegion = LiveRegionMode.Polite }.testTag(tag),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
    )
}

/**
 * One food, as a Material list item on a card: the icon of its category, its name, its quantity, the days left of the
 * batch to use first in the colour of its freshness, and its category. A food with batches of different expiry dates
 * says how many. Screen readers hear the card as one item.
 */
@Composable
private fun FoodCard(row: FoodRow, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val status = freshnessColor(row.freshness)
    val separator = stringResource(R.string.meta_separator)
    val quantity = quantityText(row.quantity, row.unit)
    val expiry = expiryText(row.daysLeft, row.expiry)
    Card(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
    ) {
        // The list item is one item for screen readers, so the tag of the card is on it.
        ListItem(
            modifier = Modifier.testTag("food_${row.key}"),
            headlineContent = { Text(row.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = {
                Column {
                    Text(
                        // "600 g • Expires in 3d": the quantity, a separator, and the days left in the colour of the freshness.
                        buildAnnotatedString {
                            append("$quantity $separator ")
                            withStyle(SpanStyle(color = status)) { append(expiry) }
                        },
                    )
                    if (row.expiryDateCount > 1) {
                        Text(pluralStringResource(R.plurals.expiry_date_count, row.expiryDateCount, row.expiryDateCount))
                    }
                }
            },
            leadingContent = { IconTile(row.category.icon(), scheme.secondaryContainer, scheme.onSecondaryContainer) },
            trailingContent = { Text(stringResource(row.category.label())) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}
