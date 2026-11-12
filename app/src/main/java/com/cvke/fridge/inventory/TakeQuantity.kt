package com.cvke.fridge.inventory

import com.cvke.fridge.core.Inventory
import com.cvke.fridge.core.MeasureUnit
import com.cvke.fridge.core.Outcome
import com.cvke.fridge.core.UseFirst

/**
 * What the person wants to record as used or as thrown away: a product, and the batches that can give the quantity.
 * A card of My Fridge gives the batches of the product in one storage area. The screen of a food gives all batches of
 * the product, or one batch.
 */
data class TakeRequest(val productId: String, val batchIds: List<String>, val outcome: Outcome)

/**
 * The batches of a [TakeRequest] as they are in the inventory now.
 *
 * @property firstQuantity The quantity of the oldest batch, in thousandths. A form starts with it.
 * @property available The sum of the batches, in thousandths. The person cannot take more.
 */
data class TakeSource(val name: String, val unit: MeasureUnit?, val firstQuantity: Long, val available: Long) {
    /** The quantity in the [text], a problem of the text, or [TooMuch]. */
    fun check(text: String): TakeCheck = when (val typed = QuantityText.parse(text)) {
        is TypedQuantity.Error -> TakeCheck.Problem(typed)
        is TypedQuantity.Value -> if (typed.thousandths > available) TakeCheck.TooMuch else TakeCheck.Quantity(typed.thousandths)
    }

    companion object {
        /** The source for the request, or null when the product or all of the batches are not there. */
        fun of(inventory: Inventory, request: TakeRequest): TakeSource? {
            val product = inventory.products.firstOrNull { it.id == request.productId } ?: return null
            val batches = UseFirst.order(inventory.batches.filter { it.productId == product.id && it.id in request.batchIds })
            val first = batches.firstOrNull() ?: return null
            return TakeSource(product.name, product.unit, first.quantity, batches.sumOf { it.quantity })
        }
    }
}

/** The result of [TakeSource.check]. */
sealed interface TakeCheck {
    data class Quantity(val thousandths: Long) : TakeCheck

    data class Problem(val error: TypedQuantity.Error) : TakeCheck

    /** The quantity is more than the batches have. */
    data object TooMuch : TakeCheck
}
