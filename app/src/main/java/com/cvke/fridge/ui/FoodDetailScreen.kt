package com.cvke.fridge.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.cvke.fridge.R
import com.cvke.fridge.core.CalendarDate
import com.cvke.fridge.core.ExpirySource
import com.cvke.fridge.core.Outcome
import com.cvke.fridge.history.DayName
import com.cvke.fridge.history.HistoryContent
import com.cvke.fridge.history.HistoryLine
import com.cvke.fridge.inventory.InventoryAction
import com.cvke.fridge.inventory.TakeRequest
import com.cvke.fridge.photo.ProductPhoto
import com.cvke.fridge.ui.theme.BrandSpinner
import com.cvke.fridge.ui.theme.IconTile
import com.cvke.fridge.ui.theme.Size

/**
 * The screen of one food: the product, and its batches in the use-first order. Each batch shows its quantity, its
 * days left in the colour of its freshness, its expiry date with "printed" or "estimated", its storage area, and its
 * date added. The days left of a card on My Fridge are short, so the full date is here
 * (`docs/research/competitor-apps.md` in the cvkefridge repository, section 8.2).
 *
 * A food with no active batch, for example after the person used all of it, says so and has "Add again". "Add again"
 * and "Add a batch" open the add form with the storage area of the newest batch and the usual quantity.
 *
 * "Used" and "Thrown away" are two buttons with space between them, below the product. They take from the oldest
 * batch first. The menu of a batch has the same two actions for that batch, "Edit", and "Delete batch". "Delete food"
 * is only in the menu of the top app bar, because a delete is for a mistake. The person can undo each of these
 * actions from the snackbar.
 *
 * Below the batches, the history of the food shows what was used and what was thrown away, the newest first, with the
 * totals. It shows the first entries, and "Show all" shows the others.
 *
 * The product can have a photo. "Add a photo" opens the photo picker of Android, which needs no permission. The photo
 * is at the top of the screen, with "Change photo" and "Remove photo". The person can undo a remove. The photo is
 * decorative, because the name of the food is below it.
 *
 * When the product is not in the inventory, for example after a delete, the screen closes.
 *
 * @param photo The photo of the product, or null.
 * @param addingPhoto True while the app makes the photo from the picture that the person selected.
 * @param pickPhoto Opens the source of a picture.
 */
@Composable
fun FoodDetailScreen(
    screen: Screen.Food,
    state: FridgeUiState,
    photo: ProductPhoto?,
    addingPhoto: Boolean,
    pickPhoto: ActivityResultContract<Unit, Uri?>,
    onPhotoPicked: (Uri) -> Unit,
    onRemovePhoto: () -> Unit,
    snackbar: SnackbarHostState,
    onEditProduct: () -> Unit,
    onEditBatch: (batchId: String) -> Unit,
    onAddBatch: () -> Unit,
    onPerform: (InventoryAction) -> Unit,
    onTryAgain: () -> Unit,
    onBack: () -> Unit,
) {
    val ready = state as? FridgeUiState.Ready
    val detail = remember(ready?.inventory, ready?.today, screen.productId) {
        ready?.let { FoodDetail.of(it.inventory, screen.productId, it.today) }
    }
    if (ready != null && detail == null) LaunchedEffect(Unit) { onBack() }

    // The history of the product, the newest first. The history of the app is calculated off the main thread.
    val history = remember(ready?.history, screen.productId) {
        ready?.history?.lines?.filter { it.productId == screen.productId }.orEmpty()
    }
    val today = ready?.today
    var allHistory by rememberSaveable { mutableStateOf(false) }

    // The picker gives its result also after Android stopped the app while the picker was open.
    val picker = rememberLauncherForActivityResult(pickPhoto) { uri -> if (uri != null) onPhotoPicked(uri) }
    val choosePhoto = { picker.launch(Unit) }

    var taking by rememberSaveable(stateSaver = TakeRequestSaver) { mutableStateOf(null) }
    taking?.let { request -> if (ready != null) TakeDialog(request, ready.inventory, onPerform, onDismiss = { taking = null }) }

    SubScreen(
        title = stringResource(R.string.food_title),
        onBack = onBack,
        snackbar = snackbar,
        tag = "food_screen",
        actions = {
            if (detail != null) {
                IconButton(onEditProduct, Modifier.testTag("edit_food")) {
                    Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.edit_food))
                }
                FoodMenu(onDelete = { onPerform(InventoryAction.DeleteProduct(screen.productId)) })
            }
        },
    ) { padding ->
        if (detail == null) {
            NotReady(state, onTryAgain, padding)
            return@SubScreen
        }
        val allBatches = detail.batches.map { it.id }
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = Size.contentMax).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val hasPhoto = photo != null || addingPhoto
                if (hasPhoto) item(key = "photo") { FoodPhoto(photo, addingPhoto, onChange = choosePhoto, onRemove = onRemovePhoto) }
                item(key = "product") {
                    ProductHeader(
                        detail,
                        showIcon = !hasPhoto,
                        onAddPhoto = if (hasPhoto) null else choosePhoto,
                        Modifier.padding(bottom = 8.dp),
                    )
                }
                if (detail.batches.isEmpty()) {
                    // The core keeps a product when its batches end. "Add again" adds a batch to it.
                    item(key = "none") {
                        EmptyState(stringResource(R.string.food_no_batches), "no_batches") {
                            Button(onAddBatch, Modifier.padding(top = 16.dp).testTag("add_again")) {
                                Icon(painterResource(R.drawable.ic_add), contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                Text(stringResource(R.string.action_add_again))
                            }
                        }
                    }
                } else {
                    item(key = "take") {
                        TakeButtons(
                            onUsed = { taking = TakeRequest(screen.productId, allBatches, Outcome.USED) },
                            onThrownAway = { taking = TakeRequest(screen.productId, allBatches, Outcome.THROWN_AWAY) },
                        )
                    }
                    item(key = "batches") {
                        Text(
                            pluralStringResource(R.plurals.food_batches, detail.batches.size, detail.batches.size),
                            Modifier.padding(top = 8.dp).semantics { heading() },
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    items(detail.batches, key = { it.id }) { batch ->
                        BatchCard(
                            batch = batch,
                            detail = detail,
                            // The first batch is the one that a use takes from.
                            useFirst = detail.batches.size > 1 && batch.id == detail.batches.first().id,
                            onEdit = { onEditBatch(batch.id) },
                            onTake = { taking = TakeRequest(screen.productId, listOf(batch.id), it) },
                            onDelete = { onPerform(InventoryAction.DeleteBatches(screen.productId, listOf(batch.id))) },
                        )
                    }
                    item(key = "add") {
                        OutlinedButton(onAddBatch, Modifier.padding(top = 8.dp).fillMaxWidth().testTag("add_batch")) {
                            Icon(painterResource(R.drawable.ic_add), contentDescription = null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.food_add_batch))
                        }
                    }
                }
                if (history.isNotEmpty()) {
                    item(key = "history") { FoodHistoryHeader(history, Modifier.padding(top = 16.dp)) }
                    val shown = if (allHistory) history else history.take(FOOD_HISTORY_COUNT)
                    items(shown, key = { "history_${it.changeId}" }) { line -> FoodHistoryLine(line, today) }
                    if (shown.size < history.size) {
                        item(key = "history_all") {
                            TextButton(onClick = { allHistory = true }, Modifier.testTag("food_history_all")) {
                                Text(pluralStringResource(R.plurals.food_history_show_all, history.size, history.size))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The product: the icon of its category, its name in full, and its category with the sum of its batches. A product
 * with a photo shows the photo above, in the place of the icon. A product with no photo has "Add a photo" below.
 */
@Composable
private fun ProductHeader(detail: FoodDetail, showIcon: Boolean, onAddPhoto: (() -> Unit)?, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val product = detail.product
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag("food_header"),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showIcon) IconTile(product.category.icon(), scheme.secondaryContainer, scheme.onSecondaryContainer, size = 56.dp)
            ProductNames(detail)
        }
        if (onAddPhoto != null) {
            TextButton(onAddPhoto, Modifier.padding(top = 8.dp).testTag("photo_add"), contentPadding = ButtonDefaults.TextButtonWithIconContentPadding) {
                Icon(painterResource(R.drawable.ic_image_plus), contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.photo_add))
            }
        }
    }
}

/**
 * The photo of the product, cropped to the width of the screen and not higher than 280 dp or 40% of the window, with
 * "Change photo" and "Remove photo". While the app makes a new photo, the screen says so, and screen readers hear it.
 */
@Composable
private fun FoodPhoto(photo: ProductPhoto?, adding: Boolean, onChange: () -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // On a phone on its side, the photo leaves space for the name and the batches.
            val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
            val height = min(min(maxWidth * 0.75f, 280.dp), windowHeight * 0.4f)
            Box(Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
                if (photo != null) {
                    ProductPhotoImage(photo, MaterialTheme.shapes.large, scheme.surfaceContainerHigh, Modifier.fillMaxSize().testTag("food_photo"))
                } else {
                    Box(Modifier.fillMaxSize().background(scheme.surfaceContainerHigh, MaterialTheme.shapes.large))
                }
                if (adding) {
                    Surface(shape = MaterialTheme.shapes.large, color = scheme.surfaceContainerHighest) {
                        Row(
                            Modifier
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
                                .testTag("photo_adding"),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BrandSpinner()
                            Text(stringResource(R.string.photo_adding), color = scheme.onSurface, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
        if (!adding) {
            FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onChange, Modifier.testTag("photo_change"), contentPadding = ButtonDefaults.TextButtonWithIconContentPadding) {
                    Icon(painterResource(R.drawable.ic_image_edit), contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.photo_change))
                }
                TextButton(onRemove, Modifier.testTag("photo_remove"), contentPadding = ButtonDefaults.TextButtonWithIconContentPadding) {
                    Icon(painterResource(R.drawable.ic_image_remove), contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.photo_remove))
                }
            }
        }
    }
}

/** The name of the product in full, and its category with the sum of its batches. */
@Composable
private fun ProductNames(detail: FoodDetail) {
    val scheme = MaterialTheme.colorScheme
    val product = detail.product
    Column {
        Text(product.name, Modifier.semantics { heading() }, color = scheme.onSurface, style = MaterialTheme.typography.titleLarge)
        val category = stringResource(product.category.label())
        Text(
            if (detail.batches.isEmpty()) {
                category
            } else {
                "$category ${stringResource(R.string.meta_separator)} ${quantityText(detail.total, product.unit)}"
            },
            color = scheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/**
 * The two ends of a batch as two buttons with different looks, one at each side of the screen. A person then does
 * not select the wrong one (`docs/research/competitor-apps.md` in the cvkefridge repository, section 8.2, item 10).
 * Each button opens the dialog for the quantity.
 */
@Composable
private fun TakeButtons(onUsed: () -> Unit, onThrownAway: () -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onUsed, Modifier.testTag("food_used")) {
            Icon(painterResource(R.drawable.ic_check), contentDescription = null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.action_used))
        }
        OutlinedButton(onThrownAway, Modifier.testTag("food_thrown_away")) {
            Icon(painterResource(R.drawable.ic_trash), contentDescription = null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.action_thrown_away))
        }
    }
}

/** The number of entries that the history of a food shows before the person selects "Show all". */
private const val FOOD_HISTORY_COUNT = 5

/**
 * The start of the history of the food: the heading, and for each outcome the quantity and the number of batches, for
 * example "Used: 3 l from 2 batches".
 */
@Composable
private fun FoodHistoryHeader(lines: List<HistoryLine>, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val used = remember(lines) { HistoryContent.total(lines, Outcome.USED) }
    val thrownAway = remember(lines) { HistoryContent.total(lines, Outcome.THROWN_AWAY) }
    Column(modifier.fillMaxWidth().testTag("food_history"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            stringResource(R.string.food_history),
            Modifier.semantics { heading() },
            color = scheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
        )
        if (used.batchCount > 0) {
            Text(
                pluralStringResource(R.plurals.food_history_used, used.batchCount, totalsText(used.quantities), used.batchCount),
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (thrownAway.batchCount > 0) {
            Text(
                pluralStringResource(
                    R.plurals.food_history_thrown_away,
                    thrownAway.batchCount,
                    totalsText(thrownAway.quantities),
                    thrownAway.batchCount,
                ),
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** One entry of the history of the food: the outcome in words with its icon, the quantity, and the day. */
@Composable
private fun FoodHistoryLine(line: HistoryLine, today: CalendarDate?, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val outcome = stringResource(if (line.outcome == Outcome.USED) R.string.action_used else R.string.action_thrown_away)
    val name = today?.let { HistoryContent.dayName(line.date, it) } ?: DayName.DATE
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics(mergeDescendants = true) {}
            .testTag("food_history_${line.changeId}"),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(line.outcome.icon()), contentDescription = null, Modifier.padding(start = 8.dp), tint = outcomeColor(line.outcome))
        Column {
            Text(
                "$outcome ${stringResource(R.string.meta_separator)} ${quantityText(line.quantity, line.unit)}",
                color = scheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                if (name == DayName.DATE) dateText(line.date) else dayText(line.date, name),
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** The menu of the top app bar. It has the delete of the food, which is for a mistake. */
@Composable
private fun FoodMenu(onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, Modifier.testTag("food_menu")) {
            Icon(painterResource(R.drawable.ic_more), contentDescription = stringResource(R.string.more_actions))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.food_delete)) },
                onClick = {
                    open = false
                    onDelete()
                },
                modifier = Modifier.testTag("menu_delete_food"),
            )
        }
    }
}

/**
 * One batch: its quantity, its days left, its expiry date with the source, its storage area, and its date added.
 * The menu at the end of the card has the actions for this batch.
 */
@Composable
private fun BatchCard(
    batch: BatchLine,
    detail: FoodDetail,
    useFirst: Boolean,
    onEdit: () -> Unit,
    onTake: (Outcome) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    Card(
        modifier.fillMaxWidth().testTag("batch_${batch.id}"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (useFirst) {
                    Text(stringResource(R.string.food_use_first), color = scheme.primary, style = MaterialTheme.typography.labelLarge)
                }
                Text(quantityText(batch.quantity, detail.product.unit), color = scheme.onSurface, style = MaterialTheme.typography.titleMedium)
                Text(
                    expiryText(batch.daysLeft, batch.expiry),
                    color = freshnessColor(batch.freshness),
                    style = MaterialTheme.typography.bodyLarge,
                )
                val expiry = batch.expiry
                if (expiry != null) {
                    Text(
                        stringResource(
                            if (expiry.source == ExpirySource.PRINTED) R.string.batch_printed_date else R.string.batch_estimated_date,
                            dateText(expiry.date),
                        ),
                        color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(
                    "${batch.areaName} ${stringResource(R.string.meta_separator)} " +
                        stringResource(R.string.batch_date_added, dateText(batch.dateAdded)),
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Box {
                IconButton(onClick = { open = true }, Modifier.testTag("batch_menu_${batch.id}")) {
                    Icon(
                        painterResource(R.drawable.ic_more),
                        contentDescription = stringResource(R.string.batch_more_actions),
                        tint = scheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.edit_batch)) },
                        onClick = {
                            open = false
                            onEdit()
                        },
                        modifier = Modifier.testTag("menu_edit_batch"),
                        leadingIcon = { Icon(painterResource(R.drawable.ic_edit), contentDescription = null) },
                    )
                    TakeMenuItems(
                        onUsed = {
                            open = false
                            onTake(Outcome.USED)
                        },
                        onThrownAway = {
                            open = false
                            onTake(Outcome.THROWN_AWAY)
                        },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.batch_delete)) },
                        onClick = {
                            open = false
                            onDelete()
                        },
                        modifier = Modifier.testTag("menu_delete_batch"),
                    )
                }
            }
        }
    }
}
