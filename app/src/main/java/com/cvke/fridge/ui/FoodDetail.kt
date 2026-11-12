package com.cvke.fridge.ui

import com.cvke.fridge.core.CalendarDate
import com.cvke.fridge.core.ExpiryDate
import com.cvke.fridge.core.Freshness
import com.cvke.fridge.core.Inventory
import com.cvke.fridge.core.Product
import com.cvke.fridge.core.UseFirst

/**
 * One active batch on the screen of a food.
 *
 * @property quantity In thousandths.
 */
data class BatchLine(
    val id: String,
    val quantity: Long,
    val areaName: String,
    val dateAdded: CalendarDate,
    val expiry: ExpiryDate?,
    val daysLeft: Int?,
    val freshness: Freshness,
)

/**
 * What the screen of a food shows: the product, and its active batches in the use-first order.
 *
 * @property total The sum of the batches, in thousandths.
 */
data class FoodDetail(val product: Product, val batches: List<BatchLine>, val total: Long) {
    companion object {
        /** The detail of the product for the date [today], or null when the product is not in the inventory. */
        fun of(inventory: Inventory, productId: String, today: CalendarDate): FoodDetail? {
            val product = inventory.products.firstOrNull { it.id == productId } ?: return null
            val areaNames = inventory.storageAreas.associate { it.id to it.name }
            // A batch is on a screen only with its storage area, as on My Fridge.
            val batches = UseFirst.order(inventory.batches.filter { it.productId == productId && it.storageAreaId in areaNames })
            return FoodDetail(
                product = product,
                batches = batches.map { batch ->
                    val daysLeft = Freshness.daysLeft(today, batch.expiry?.date)
                    BatchLine(
                        id = batch.id,
                        quantity = batch.quantity,
                        areaName = areaNames.getValue(batch.storageAreaId),
                        dateAdded = batch.dateAdded,
                        expiry = batch.expiry,
                        daysLeft = daysLeft,
                        freshness = Freshness.of(daysLeft),
                    )
                },
                total = batches.sumOf { it.quantity },
            )
        }
    }
}
