package com.cvke.fridge.ui

import androidx.annotation.DrawableRes
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cvke.fridge.R
import com.cvke.fridge.photo.ProductPhoto
import com.cvke.fridge.core.CalendarDate
import com.cvke.fridge.core.Outcome
import com.cvke.fridge.history.DayName
import com.cvke.fridge.history.HistoryFilter
import com.cvke.fridge.history.HistoryItem
import com.cvke.fridge.history.HistoryPeriod
import com.cvke.fridge.history.HistorySummary
import com.cvke.fridge.history.OutcomeTotal
import com.cvke.fridge.history.QuantityTotal
import com.cvke.fridge.ui.theme.BrandSpinner
import com.cvke.fridge.ui.theme.Size

/**
 * History, the third tab: what was used and what was thrown away, the newest first, with a header for each day. Each
 * entry shows the food, the quantity, and the outcome in words and with an icon. A tap on an entry opens the screen of
 * the food. The button at the end of an entry adds the food again: it opens the add form with the values of the
 * product (`docs/research/competitor-apps.md` in the cvkefridge repository, section 8.3, item 6).
 *
 * At the top, a summary counts the batches that were used and thrown away in this month, the last month, or all of
 * the history, with the quantities for each unit, and names the foods that were thrown away most often
 * (`docs/research/competitor-apps.md` in the cvkefridge repository, section 8.3, item 7). It has counts and
 * quantities only. Below the summary, three segmented buttons show all entries, or one outcome. The buttons stay at
 * the top of the screen while the list scrolls.
 *
 * The list and the summary are calculated off the main thread, before the screen opens (see [HistoryContent]). The
 * list is lazy, so a long history scrolls smoothly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    state: FridgeUiState,
    photos: Map<String, ProductPhoto>,
    onOpenFood: (productId: String) -> Unit,
    onAddAgain: (productId: String) -> Unit,
    onTryAgain: () -> Unit,
    insets: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val direction = LocalLayoutDirection.current
    val edges = PaddingValues(start = insets.calculateStartPadding(direction), end = insets.calculateEndPadding(direction))
    val side = PaddingValues(start = insets.calculateStartPadding(direction) + 16.dp, end = insets.calculateEndPadding(direction) + 16.dp)
    var filter by rememberSaveable { mutableStateOf(HistoryFilter.ALL) }
    var period by rememberSaveable { mutableStateOf(HistoryPeriod.THIS_MONTH) }
    val ready = state as? FridgeUiState.Ready
    val history = ready?.history

    Column(modifier.fillMaxSize().background(scheme.surface)) {
        TopAppBar(title = { Text(stringResource(R.string.history_title), Modifier.semantics { heading() }) })
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = Size.contentMax).fillMaxSize().testTag("history_screen"),
                contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
            ) {
                when {
                    state is FridgeUiState.LoadFailed -> item(key = "failed") { LoadFailed(onTryAgain, Modifier.padding(side)) }
                    history == null -> item(key = "loading") { HistoryLoading(Modifier.padding(side)) }
                    history.lines.isEmpty() -> item(key = "empty") {
                        Column(Modifier.padding(side)) {
                            if (ready?.content?.sample == true) SampleNote()
                            EmptyState(stringResource(R.string.history_empty), "history_empty") {
                                Text(
                                    stringResource(R.string.history_empty_body),
                                    Modifier.padding(top = 8.dp).widthIn(max = 320.dp),
                                    color = scheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                    else -> {
                        item(key = "summary") {
                            Column(Modifier.padding(side)) {
                                if (ready?.content?.sample == true) SampleNote(Modifier.padding(bottom = 12.dp))
                                SummaryCard(history.summary(period), period, photos, onPeriodChange = { period = it })
                            }
                        }
                        stickyHeader(key = "filter") { FilterButtons(filter, onSelect = { filter = it }, Modifier.padding(side)) }
                        val rows = history.items(filter)
                        if (rows.isEmpty()) {
                            item(key = "no_match") {
                                Text(
                                    stringResource(
                                        if (filter == HistoryFilter.USED) R.string.history_none_used else R.string.history_none_thrown_away,
                                    ),
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp)
                                        .semantics { liveRegion = LiveRegionMode.Polite }
                                        .testTag("history_no_match"),
                                    color = scheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                        items(rows, key = { it.key }, contentType = { it is HistoryItem.Day }) { row ->
                            when (row) {
                                is HistoryItem.Day -> DayHeader(row, Modifier.padding(side))
                                is HistoryItem.Entry -> HistoryEntry(
                                    row,
                                    photos[row.line.productId],
                                    onOpen = { onOpenFood(row.line.productId) },
                                    onAddAgain = { onAddAgain(row.line.productId) },
                                    Modifier.padding(edges),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The name of a day in the history: "Today", "Yesterday", or the date in the full format of the locale. */
@Composable
fun dayText(date: CalendarDate, name: DayName): String = when (name) {
    DayName.TODAY -> stringResource(R.string.history_today)
    DayName.YESTERDAY -> stringResource(R.string.history_yesterday)
    DayName.DATE -> longDateText(date)
}

/** Sums in more than one unit, for example "3.5 l • 12 pcs • 2 with no unit". */
@Composable
fun totalsText(totals: List<QuantityTotal>): String {
    val separator = stringResource(R.string.meta_separator)
    return totals.map { total ->
        if (total.unit == null) {
            stringResource(R.string.history_total_no_unit, quantityText(total.quantity, null))
        } else {
            quantityText(total.quantity, total.unit)
        }
    }.joinToString(" $separator ")
}

/** The icon of an outcome, the same as on the buttons and the swipe of a card. */
@DrawableRes
fun Outcome.icon(): Int = when (this) {
    Outcome.USED -> R.drawable.ic_check
    Outcome.THROWN_AWAY -> R.drawable.ic_trash
}

/** The colour of the icon of an outcome. The words of the outcome are always next to it. */
@Composable
fun outcomeColor(outcome: Outcome): Color = when (outcome) {
    Outcome.USED -> MaterialTheme.colorScheme.primary
    Outcome.THROWN_AWAY -> MaterialTheme.colorScheme.tertiary
}

@Composable
private fun HistoryLoading(modifier: Modifier = Modifier) {
    Row(
        modifier.padding(top = 32.dp).testTag("loading"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandSpinner()
        Text(stringResource(R.string.loading), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * The summary of a period: the number of batches that were used and that were thrown away, each with its quantities,
 * and the foods that were thrown away most often. The button at the top changes the period. The two numbers are side
 * by side. With large text they do not fit, and the second is below the first.
 */
@Composable
private fun SummaryCard(
    summary: HistorySummary,
    period: HistoryPeriod,
    photos: Map<String, ProductPhoto>,
    onPeriodChange: (HistoryPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier.fillMaxWidth().testTag("history_summary"),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.history_summary_title),
                    Modifier.weight(1f).semantics { heading() },
                    color = scheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                )
                PeriodButton(period, onPeriodChange)
            }
            Spacer(Modifier.height(8.dp))
            if (summary.isEmpty) {
                Text(
                    stringResource(R.string.history_summary_none),
                    Modifier.padding(end = 8.dp).testTag("summary_none"),
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
                return@Column
            }
            BoxWithConstraints(Modifier.padding(end = 8.dp)) {
                val sideBySide = maxWidth / LocalDensity.current.fontScale >= 264.dp
                val used = @Composable { item: Modifier ->
                    OutcomeNumber(Outcome.USED, summary.used, R.plurals.history_batches_used, item.testTag("summary_used"))
                }
                val thrownAway = @Composable { item: Modifier ->
                    OutcomeNumber(Outcome.THROWN_AWAY, summary.thrownAway, R.plurals.history_batches_thrown_away, item.testTag("summary_thrown_away"))
                }
                if (sideBySide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        used(Modifier.weight(1f))
                        thrownAway(Modifier.weight(1f))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        used(Modifier.fillMaxWidth())
                        thrownAway(Modifier.fillMaxWidth())
                    }
                }
            }
            if (summary.mostThrownAway.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(end = 8.dp, top = 16.dp, bottom = 12.dp), color = scheme.outlineVariant)
                Text(
                    stringResource(R.string.history_most_thrown_away),
                    Modifier.semantics { heading() },
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                )
                summary.mostThrownAway.forEach { product ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, end = 8.dp)
                            .semantics(mergeDescendants = true) {}
                            .testTag("most_thrown_away_${product.productId}"),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProductTile(
                            photos[product.productId],
                            product.category,
                            scheme.secondaryContainer,
                            scheme.onSecondaryContainer,
                            size = 32.dp,
                        )
                        Text(
                            product.name,
                            Modifier.weight(1f),
                            color = scheme.onSurface,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            pluralStringResource(R.plurals.history_batch_count, product.batchCount, product.batchCount),
                            color = scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

/** One number of the summary: the icon of the outcome, the number of batches, its words, and the quantities. */
@Composable
private fun OutcomeNumber(outcome: Outcome, total: OutcomeTotal, @PluralsRes label: Int, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(outcome.icon()), contentDescription = null, Modifier.size(24.dp), tint = outcomeColor(outcome))
            Text(
                numberFormat()(total.batchCount),
                color = scheme.onSurface,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            )
        }
        Text(pluralStringResource(label, total.batchCount), color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        if (total.quantities.isNotEmpty()) {
            Text(totalsText(total.quantities), color = scheme.onSurface, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * The button that changes the period of the summary. It shows the period, and it opens a list of the periods. The
 * selected period has a check mark.
 */
@Composable
private fun PeriodButton(period: HistoryPeriod, onChange: (HistoryPeriod) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = stringResource(period.label())
    val description = stringResource(R.string.history_period_description, label)
    Box {
        TextButton(
            onClick = { open = true },
            modifier = Modifier
                .semantics {
                    role = Role.DropdownList
                    contentDescription = description
                }
                .testTag("history_period"),
        ) {
            Text(label)
            Icon(painterResource(R.drawable.ic_menu_down), contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            HistoryPeriod.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.label())) },
                    onClick = {
                        open = false
                        onChange(option)
                    },
                    modifier = Modifier.testTag("period_${option.name.lowercase()}"),
                    trailingIcon = if (option != period) null else {
                        { Icon(painterResource(R.drawable.ic_check), contentDescription = null) }
                    },
                )
            }
        }
    }
}

/**
 * All entries, the used food, or the thrown-away food, as Material segmented buttons with one selection. The row has
 * the colour of the screen, because the list scrolls below it.
 */
@Composable
private fun FilterButtons(filter: HistoryFilter, onSelect: (HistoryFilter) -> Unit, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp).testTag("history_filter")) {
            HistoryFilter.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == filter,
                    onClick = { onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, HistoryFilter.entries.size),
                    modifier = Modifier.testTag("history_filter_${option.name.lowercase()}"),
                    label = { Text(stringResource(option.label()), textAlign = TextAlign.Center) },
                )
            }
        }
    }
}

/** The header of a day: "Today", "Yesterday", or the date. Screen readers can go from one day to the next. */
@Composable
private fun DayHeader(day: HistoryItem.Day, modifier: Modifier = Modifier) {
    Text(
        dayText(day.date, day.name),
        modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp).semantics { heading() }.testTag("history_day_${day.date.text}"),
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleSmall,
    )
}

/**
 * One entry: the photo of the product or the icon of its category, the name of the food, the quantity, the outcome in
 * words with its icon, and the button that adds the food again. Screen readers hear the entry as one item with its
 * day, for example "Whole milk, 1 l, Used, Today", and they have "Add again" as an action of the item also.
 */
@Composable
private fun HistoryEntry(
    entry: HistoryItem.Entry,
    photo: ProductPhoto?,
    onOpen: () -> Unit,
    onAddAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val line = entry.line
    val quantity = quantityText(line.quantity, line.unit)
    val outcome = stringResource(if (line.outcome == Outcome.USED) R.string.action_used else R.string.action_thrown_away)
    val description = stringResource(R.string.history_entry_description, line.name, quantity, outcome, dayText(line.date, entry.day))
    val separator = stringResource(R.string.meta_separator)
    val again = stringResource(R.string.action_add_again)
    ListItem(
        modifier = modifier
            .clickable(onClick = onOpen)
            .semantics {
                contentDescription = description
                customActions = listOf(
                    CustomAccessibilityAction(again) {
                        onAddAgain()
                        true
                    },
                )
            }
            .testTag("history_${line.changeId}"),
        headlineContent = { Text(line.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(line.outcome.icon()), contentDescription = null, Modifier.size(16.dp), tint = outcomeColor(line.outcome))
                Text("$quantity $separator $outcome")
            }
        },
        leadingContent = { ProductTile(photo, line.category, scheme.secondaryContainer, scheme.onSecondaryContainer) },
        trailingContent = {
            IconButton(onClick = onAddAgain, Modifier.testTag("add_again_${line.changeId}")) {
                Icon(
                    painterResource(R.drawable.ic_add),
                    contentDescription = stringResource(R.string.add_again_description, line.name),
                    tint = scheme.onSurfaceVariant,
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@StringRes
private fun HistoryFilter.label(): Int = when (this) {
    HistoryFilter.ALL -> R.string.history_filter_all
    HistoryFilter.USED -> R.string.history_filter_used
    HistoryFilter.THROWN_AWAY -> R.string.history_filter_thrown_away
}

@StringRes
private fun HistoryPeriod.label(): Int = when (this) {
    HistoryPeriod.THIS_MONTH -> R.string.history_period_this_month
    HistoryPeriod.LAST_MONTH -> R.string.history_period_last_month
    HistoryPeriod.ALL -> R.string.history_period_all
}
