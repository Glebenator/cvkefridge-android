package com.cvke.fridge.inventory

import com.cvke.fridge.core.Batch
import com.cvke.fridge.core.Inventory
import com.cvke.fridge.core.Product
import com.cvke.fridge.history.HistoryLine

/**
 * The start values of the add form for a product that the person had before.
 *
 * @property storageAreaId The storage area of the newest batch of the product, or null when the product has no batch
 *   or that storage area is not there now.
 * @property quantity The usual quantity of a batch of the product, in thousandths, or null when it has no batch.
 */
data class AgainValues(val storageAreaId: String?, val quantity: Long?)

/**
 * "Add again": a new batch of a product that the person had before, with the values of the earlier batches. The core
 * keeps a product when all its batches end, so the new batch goes to the same product, and the app makes no second
 * product. The form does not copy a date of an earlier batch: the date added is today, and the expiry date is the
 * estimated date from today, or no date when the food has no estimate.
 *
 * The shared specification has no rule for this yet. These are the smallest rules that work.
 */
object AddAgain {
    /** The largest number of products in the list of recent foods of the add form. */
    const val RECENT_COUNT = 3

    /** The start values for a new batch of the product. */
    fun values(inventory: Inventory, productId: String): AgainValues {
        val batches = inventory.batches.filter { it.productId == productId }
        if (batches.isEmpty()) return AgainValues(null, null)
        val areas = inventory.storageAreas.mapTo(HashSet()) { it.id }
        val taken = inventory.history.groupBy { it.batchId }.mapValues { (_, entries) -> entries.sumOf { it.quantity } }
        val newestFirst = batches.sortedWith(newest)
        return AgainValues(
            storageAreaId = newestFirst.first().storageAreaId.takeIf { it in areas },
            quantity = usualQuantity(newestFirst.map { it.quantity + (taken[it.id] ?: 0L) }),
        )
    }

    /**
     * The quantity that the most batches had when they were added. With the same number of batches, the quantity of
     * the newer batch wins.
     *
     * @param newestFirst The quantity of each batch when it was added, the newest batch first.
     */
    fun usualQuantity(newestFirst: List<Long>): Long? {
        val counts = newestFirst.groupingBy { it }.eachCount()
        val most = counts.values.maxOrNull() ?: return null
        return newestFirst.first { counts.getValue(it) == most }
    }

    /**
     * The products that the person had before and has none of now, the product that ended last first, not more than
     * [limit]. A product that is in no history entry is not here: the person never used it or threw it away.
     *
     * @param lines The history, the newest first, as [com.cvke.fridge.history.HistoryContent.lines] gives it.
     */
    fun recent(lines: List<HistoryLine>, inventory: Inventory, limit: Int = RECENT_COUNT): List<Product> {
        val inStock = inventory.activeBatches.mapTo(HashSet()) { it.productId }
        val products = inventory.products.associateBy { it.id }
        return lines.asSequence()
            .map { it.productId }
            .distinct()
            .filter { it !in inStock }
            .mapNotNull { products[it] }
            .take(limit)
            .toList()
    }

    /** True when the product has an active batch. */
    fun inStock(inventory: Inventory, productId: String): Boolean = inventory.activeBatches.any { it.productId == productId }

    /** The newest batch first: the later date added, and then the larger identifier, so that the order is fixed. */
    private val newest: Comparator<Batch> = compareByDescending<Batch> { it.dateAdded }.thenByDescending { it.id }
}
