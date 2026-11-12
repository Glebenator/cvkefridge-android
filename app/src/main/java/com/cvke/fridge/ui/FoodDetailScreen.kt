package com.cvke.fridge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cvke.fridge.R
import com.cvke.fridge.core.ExpirySource
import com.cvke.fridge.core.Outcome
import com.cvke.fridge.inventory.InventoryAction
import com.cvke.fridge.inventory.TakeRequest
import com.cvke.fridge.ui.theme.IconTile
import com.cvke.fridge.ui.theme.Size

/**
 * The screen of one food: the product, and its batches in the use-first order. Each batch shows its quantity, its
 * days left in the colour of its freshness, its expiry date with "printed" or "estimated", its storage area, and its
 * date added. The days left of a card on My Fridge are short, so the full date is here
 * (`docs/research/competitor-apps.md` in the cvkefridge repository, section 8.2).
 *
 * "Used" and "Thrown away" are two buttons with space between them, below the product. They take from the oldest
 * batch first. The menu of a batch has the same two actions for that batch, "Edit", and "Delete batch". "Delete food"
 * is only in the menu of the top app bar, because a delete is for a mistake. The person can undo each of these
 * actions from the snackbar.
 *
 * When the product is not in the inventory, for example after a delete, the screen closes.
 */
@Composable
fun FoodDetailScreen(
    screen: Screen.Food,
    state: FridgeUiState,
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
                item(key = "product") { ProductHeader(detail, Modifier.padding(bottom = 8.dp)) }
                if (detail.batches.isEmpty()) {
                    item(key = "none") { EmptyState(stringResource(R.string.food_no_batches), "no_batches") }
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
                }
                item(key = "add") {
                    OutlinedButton(onAddBatch, Modifier.padding(top = 8.dp).fillMaxWidth().testTag("add_batch")) {
                        Icon(painterResource(R.drawable.ic_add), contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.food_add_batch))
                    }
                }
            }
        }
    }
}

/** The product: the icon of its category, its name in full, and its category with the sum of its batches. */
@Composable
private fun ProductHeader(detail: FoodDetail, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val product = detail.product
    Row(
        modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag("food_header"),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(product.category.icon(), scheme.secondaryContainer, scheme.onSecondaryContainer, size = 56.dp)
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
