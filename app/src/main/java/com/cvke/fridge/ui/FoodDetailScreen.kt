package com.cvke.fridge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import com.cvke.fridge.ui.theme.IconTile
import com.cvke.fridge.ui.theme.Size

/**
 * The screen of one food: the product, and its batches in the use-first order. Each batch shows its quantity, its
 * days left in the colour of its freshness, its expiry date with "printed" or "estimated", its storage area, and its
 * date added. The days left of a card on My Fridge are short, so the full date is here
 * (`docs/research/competitor-apps.md` in the cvkefridge repository, section 8.2). The person can edit the product and
 * each batch, and add a batch.
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
    onTryAgain: () -> Unit,
    onBack: () -> Unit,
) {
    val ready = state as? FridgeUiState.Ready
    val detail = remember(ready?.inventory, ready?.today, screen.productId) {
        ready?.let { FoodDetail.of(it.inventory, screen.productId, it.today) }
    }
    if (ready != null && detail == null) LaunchedEffect(Unit) { onBack() }

    SubScreen(
        title = detail?.product?.name ?: stringResource(R.string.food_title),
        onBack = onBack,
        snackbar = snackbar,
        tag = "food_screen",
        actions = {
            if (detail != null) {
                IconButton(onEditProduct, Modifier.testTag("edit_food")) {
                    Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.edit_food))
                }
            }
        },
    ) { padding ->
        if (detail == null) {
            NotReady(state, onTryAgain, padding)
            return@SubScreen
        }
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
    Row(modifier.fillMaxWidth().testTag("food_header"), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(product.category.icon(), scheme.secondaryContainer, scheme.onSecondaryContainer, size = 56.dp)
        Column {
            Text(product.name, color = scheme.onSurface, style = MaterialTheme.typography.titleLarge)
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
 * One batch: its quantity, its days left, its expiry date with the source, its storage area, and its date added.
 * The button that edits the batch is at the end of the card.
 */
@Composable
private fun BatchCard(batch: BatchLine, detail: FoodDetail, useFirst: Boolean, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
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
            IconButton(onEdit, Modifier.testTag("edit_batch_${batch.id}")) {
                Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.edit_batch), tint = scheme.onSurfaceVariant)
            }
        }
    }
}
