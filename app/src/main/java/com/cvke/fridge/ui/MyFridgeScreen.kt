package com.cvke.fridge.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.cvke.fridge.R
import com.cvke.fridge.core.Category
import com.cvke.fridge.core.Freshness
import com.cvke.fridge.ui.theme.IconTile
import com.cvke.fridge.ui.theme.Size

/**
 * My Fridge, the screen that lists all food. Its contents start from the My Fridge screen of FridgeFriend
 * (`app/(tabs)/fridge.tsx` in the `basic_ff` repository): a search field, filters for the storage area and the
 * category, a sort control, and one card for each food in each storage area. The storage areas are tabs below the top
 * app bar. The category and the freshness are in one row of chips that does not scroll sideways
 * (`docs/research/competitor-apps.md` in the cvkefridge repository, sections 6 and 6A). The parts are Material 3
 * components. The button that adds food is on the tabs screen, and the empty state has a second one.
 *
 * The screen does not keep the [filter]. It calls [onFilterChange], because Home can also set the filter.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyFridgeScreen(
    state: FridgeUiState,
    filter: FoodFilter,
    onFilterChange: (FoodFilter) -> Unit,
    onAddFood: () -> Unit,
    onTryAgain: () -> Unit,
    insets: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val direction = LocalLayoutDirection.current
    val startInset = insets.calculateStartPadding(direction)
    val endInset = insets.calculateEndPadding(direction)
    val side = PaddingValues(start = startInset + 16.dp, end = endInset + 16.dp)
    var sortKey by rememberSaveable { mutableStateOf(SortKey.EXPIRY) }
    var ascending by rememberSaveable { mutableStateOf(SortKey.EXPIRY.ascendingFirst) }

    val content = (state as? FridgeUiState.Ready)?.content
    val view = remember(content, filter) { content?.filtered(filter) }
    val rows = remember(view, sortKey, ascending) { view?.let { InventoryContent.sort(it.rows, sortKey, ascending) }.orEmpty() }

    // A new filter starts at the top of the list. The position stays when the person comes back to the screen.
    val list = rememberLazyListState()
    var shownFilter by rememberSaveable(stateSaver = FoodFilterSaver) { mutableStateOf(filter) }
    LaunchedEffect(filter) {
        if (filter != shownFilter) {
            shownFilter = filter
            list.scrollToItem(0)
        }
    }

    Column(modifier.fillMaxSize().background(scheme.surface)) {
        TopAppBar(
            title = { Text(stringResource(R.string.tab_my_fridge), Modifier.semantics { heading() }) },
            actions = {
                SortActions(sortKey, ascending) { key, order ->
                    sortKey = key
                    ascending = order
                }
            },
        )
        AreaTabs(
            content?.areas.orEmpty(),
            view?.areaId,
            onSelect = { onFilterChange(filter.copy(areaId = it)) },
            Modifier.padding(start = startInset, end = endInset),
        )
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = Size.contentMax).fillMaxSize().testTag("my_fridge_screen"),
                state = list,
                // The space at the bottom keeps the last card clear of the button that adds food.
                contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "header") {
                    Column {
                        if (content?.sample == true) SampleNote(Modifier.padding(side).padding(bottom = 12.dp))
                        SearchField(filter.query, onChange = { onFilterChange(filter.copy(query = it)) }, Modifier.padding(side))
                        if (view != null) FilterRow(view, filter, onFilterChange, Modifier.padding(side).padding(top = 4.dp))
                    }
                }
                when {
                    state is FridgeUiState.LoadFailed -> item(key = "failed") { LoadFailed(onTryAgain, Modifier.padding(side)) }
                    view == null -> item(key = "loading") { ListMessage(stringResource(R.string.loading), "loading") }
                    // All storage areas have no food, or the storage area of the tab has no food.
                    view.areaFoodCount == 0 -> item(key = "none") {
                        EmptyState(stringResource(R.string.no_items), "no_items") { AddFoodButton(onAddFood) }
                    }
                    rows.isEmpty() -> item(key = "no_match") {
                        ListMessage(stringResource(R.string.list_no_match), "list_no_match") {
                            // The search field has its own button to clear the text.
                            if (view.category != null || filter.freshness.isNotEmpty()) {
                                TextButton(
                                    onClick = { onFilterChange(filter.copy(category = null, freshness = emptySet())) },
                                    Modifier.testTag("filter_clear"),
                                ) { Text(stringResource(R.string.filter_clear)) }
                            }
                        }
                    }
                    else -> items(rows, key = { it.key }) { FoodCard(it, Modifier.padding(side)) }
                }
            }
        }
    }
}

/** Keeps a [FoodFilter] when Android stops the app in the background. */
val FoodFilterSaver: Saver<FoodFilter, Any> = listSaver(
    save = { listOf(it.areaId, it.category?.key, it.freshness.map(Freshness::key), it.query) },
    restore = { saved ->
        FoodFilter(
            areaId = saved[0] as String?,
            category = (saved[1] as String?)?.let(Category::fromKey),
            freshness = (saved[2] as List<*>).mapNotNullTo(HashSet()) { key -> Freshness.fromKey(key as String) },
            query = saved[3] as String,
        )
    },
)

/**
 * The storage areas as Material tabs: "All", and then one tab for each storage area. The tabs have equal widths and
 * fill the row while they fit. With more storage areas, the row scrolls. Screen readers hear "Storage area" with the
 * name, and the selected tab.
 */
@Composable
private fun AreaTabs(areas: List<AreaSummary>, selectedId: String?, onSelect: (String?) -> Unit, modifier: Modifier = Modifier) {
    val group = stringResource(R.string.filter_storage_area)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // The row rounds this width to whole pixels, so calculate it in pixels. Then the tabs are not wider than the row.
        val equalWidth = with(LocalDensity.current) { (constraints.maxWidth / (areas.size + 1)).toDp() }
        PrimaryScrollableTabRow(
            selectedTabIndex = areas.indexOfFirst { it.id == selectedId } + 1,
            edgePadding = 0.dp,
            minTabWidth = max(TabRowDefaults.ScrollableTabRowMinTabWidth, equalWidth),
        ) {
            @Composable
            fun AreaTab(id: String?, name: String, tag: String) {
                val description = stringResource(R.string.filter_description, group, name)
                Tab(
                    selected = id == selectedId,
                    onClick = { onSelect(id) },
                    modifier = Modifier.semantics { contentDescription = description }.testTag(tag),
                    text = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AreaTab(null, stringResource(R.string.area_all), "area_all")
            areas.forEach { AreaTab(it.id, it.name, "area_${it.id}") }
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
 * The row of the category chip and the two freshness chips. A chip goes to the next line when the row is too narrow,
 * for example with large text. The row does not scroll sideways.
 */
@Composable
private fun FilterRow(view: FilteredFoods, filter: FoodFilter, onFilterChange: (FoodFilter) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CategoryChip(view, onSelect = { onFilterChange(filter.copy(category = it)) })
        FreshnessChip(Freshness.EXPIRING, R.string.filter_expiring, view.expiringCount, "filter_expiring", filter, onFilterChange)
        FreshnessChip(Freshness.EXPIRED, R.string.filter_expired, view.expiredCount, "filter_expired", filter, onFilterChange)
    }
}

/**
 * The chip that opens the menu of the categories. It shows "Category", or the selected category in the selected
 * state. The menu has "All categories" and each category with food in the storage area of the tab, each with its
 * number of foods and with a check mark on the selected one.
 */
@Composable
private fun CategoryChip(view: FilteredFoods, onSelect: (Category?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val group = stringResource(R.string.filter_category)
    val all = stringResource(R.string.category_all)
    val selectedLabel = view.category?.let { stringResource(it.label()) }
    val description = stringResource(R.string.filter_description, group, selectedLabel ?: all)
    Box {
        FilterChip(
            selected = view.category != null,
            onClick = { open = true },
            label = { Text(selectedLabel ?: group, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            // The chip opens a list. It is not a check box.
            modifier = Modifier
                .semantics {
                    role = Role.DropdownList
                    contentDescription = description
                }
                .testTag("filter_category"),
            trailingIcon = {
                Icon(painterResource(R.drawable.ic_menu_down), contentDescription = null, Modifier.size(FilterChipDefaults.IconSize))
            },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            @Composable
            fun Option(category: Category?, label: String, count: Int, tag: String) {
                val chosen = category == view.category
                val optionDescription = pluralStringResource(R.plurals.filter_count_description, count, label, count)
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        open = false
                        onSelect(category)
                    },
                    modifier = Modifier
                        .semantics {
                            selected = chosen
                            contentDescription = optionDescription
                        }
                        .testTag(tag),
                    // The space keeps the names in one line when the item has no check mark.
                    leadingIcon = {
                        if (chosen) Icon(painterResource(R.drawable.ic_check), contentDescription = null) else Spacer(Modifier.size(24.dp))
                    },
                    trailingIcon = { Text(numberFormat()(count), style = MaterialTheme.typography.labelLarge) },
                )
            }
            Option(null, all, view.allCategoriesCount, "category_all")
            view.categories.forEach { Option(it.category, stringResource(it.category.label()), it.foodCount, "category_${it.category.key}") }
        }
    }
}

/**
 * A chip that shows or hides the food of one freshness, with the number of foods. The two chips can be on together.
 * Screen readers hear "Freshness" with the label and the number.
 */
@Composable
private fun FreshnessChip(
    freshness: Freshness,
    @StringRes label: Int,
    count: Int,
    tag: String,
    filter: FoodFilter,
    onFilterChange: (FoodFilter) -> Unit,
) {
    val on = freshness in filter.freshness
    val text = stringResource(label)
    val description = pluralStringResource(
        R.plurals.filter_count_description,
        count,
        stringResource(R.string.filter_description, stringResource(R.string.filter_freshness), text),
        count,
    )
    FilterChip(
        selected = on,
        onClick = { onFilterChange(filter.copy(freshness = if (on) filter.freshness - freshness else filter.freshness + freshness)) },
        label = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text, maxLines = 1)
                Text(numberFormat()(count), maxLines = 1)
            }
        },
        modifier = Modifier.semantics { contentDescription = description }.testTag(tag),
        leadingIcon = if (!on) null else {
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

/**
 * One line in the place of the list: the list loads, or no food is there for the filters and the search text. The
 * [action] is below the line. Screen readers hear the line when it comes.
 */
@Composable
private fun ListMessage(text: String, tag: String, modifier: Modifier = Modifier, action: @Composable () -> Unit = {}) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text,
            Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag(tag),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        action()
    }
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
