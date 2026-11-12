package com.cvke.fridge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cvke.fridge.R
import com.cvke.fridge.core.Inventory
import com.cvke.fridge.core.Outcome
import com.cvke.fridge.inventory.InventoryAction
import com.cvke.fridge.inventory.QuantityText
import com.cvke.fridge.inventory.TakeCheck
import com.cvke.fridge.inventory.TakeRequest
import com.cvke.fridge.inventory.TakeSource
import java.text.DecimalFormatSymbols

/**
 * The dialog that records a quantity of a food as used or as thrown away. The field starts with all of the oldest
 * batch, and the person can type a different quantity. A quantity that is more than the oldest batch has comes from
 * the next batches also. The dialog closes when the batches of the [request] are not in the inventory.
 */
@Composable
fun TakeDialog(request: TakeRequest, inventory: Inventory, onPerform: (InventoryAction) -> Unit, onDismiss: () -> Unit) {
    val source = remember(request, inventory) { TakeSource.of(inventory, request) }
    if (source == null) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    val locale = LocalConfiguration.current.locales[0]
    var text by rememberSaveable(request) {
        mutableStateOf(QuantityText.format(source.firstQuantity, DecimalFormatSymbols.getInstance(locale).decimalSeparator))
    }
    var checked by rememberSaveable(request) { mutableStateOf(false) }
    val check = source.check(text)
    val used = request.outcome == Outcome.USED
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("take_dialog"),
        title = { Text(stringResource(if (used) R.string.take_used_title else R.string.take_thrown_away_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.take_available, source.name, quantityText(source.available, source.unit)))
                QuantityField(
                    value = text,
                    onChange = { text = it },
                    error = (check as? TakeCheck.Problem)?.error?.takeIf { checked },
                    unit = source.unit?.let { stringResource(unitName(it)) },
                    errorText = when {
                        !checked -> null
                        check is TakeCheck.Problem -> stringResource(check.error.message())
                        check is TakeCheck.TooMuch -> stringResource(R.string.error_take_too_much, quantityText(source.available, source.unit))
                        else -> null
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (check is TakeCheck.Quantity) {
                        onPerform(InventoryAction.Take(request.productId, request.batchIds, check.thousandths, request.outcome))
                        onDismiss()
                    } else {
                        checked = true
                    }
                },
                Modifier.testTag("take_confirm"),
            ) { Text(stringResource(if (used) R.string.action_used else R.string.action_thrown_away)) }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Keeps the request of an open [TakeDialog] when Android stops the app in the background. */
val TakeRequestSaver: Saver<TakeRequest?, Any> = listSaver(
    save = { request -> if (request == null) emptyList() else listOf(request.productId, request.outcome.key) + request.batchIds },
    restore = { saved ->
        val outcome = saved.getOrNull(1)?.let(Outcome::fromKey)
        if (outcome == null) null else TakeRequest(saved[0], saved.drop(2), outcome)
    },
)
