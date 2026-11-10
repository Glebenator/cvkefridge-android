package com.cvke.fridge.core

/** A part of a use: the quantity to take from one batch. */
data class Take(val batchId: String, val quantity: Long)

/**
 * The plan of a use.
 *
 * @property takes The parts, in the use-first order.
 * @property shortfall The quantity that the batches could not give. It is 0 when they had enough.
 */
data class UsePlan(val takes: List<Take>, val shortfall: Long)

/**
 * The use-first order and the plan of a use.
 *
 * The iOS file `CVKEFridgeCore/Sources/CVKEFridgeCore/UseFirst.swift` must give the same results for the same input.
 */
object UseFirst {
    /**
     * The active batches in the use-first order. A batch with an expiry date is first. Then the earlier expiry date,
     * the earlier date added, and the smaller identifier are first. The source of the expiry date has no effect.
     */
    fun order(batches: List<Batch>): List<Batch> = batches.filter { it.isActive }.sortedWith(
        compareBy<Batch> { it.expiry == null }
            .thenBy(nullsLast()) { it.expiry?.date }
            .thenBy { it.dateAdded }
            .thenBy { it.id },
    )

    /**
     * The plan to take [quantity] of a product from its active batches. A quantity of 0 or less gives an empty plan
     * with a shortfall of 0.
     */
    fun plan(batches: List<Batch>, productId: String, quantity: Long): UsePlan {
        val takes = mutableListOf<Take>()
        var left = maxOf(quantity, 0L)
        for (batch in order(batches.filter { it.productId == productId })) {
            if (left == 0L) break
            val taken = minOf(left, batch.quantity)
            takes += Take(batch.id, taken)
            left -= taken
        }
        return UsePlan(takes, left)
    }
}
