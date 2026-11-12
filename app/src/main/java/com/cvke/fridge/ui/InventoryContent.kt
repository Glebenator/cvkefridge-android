package com.cvke.fridge.ui

import com.cvke.fridge.core.Batch
import com.cvke.fridge.core.CalendarDate
import com.cvke.fridge.core.Category
import com.cvke.fridge.core.ExpiryDate
import com.cvke.fridge.core.Freshness
import com.cvke.fridge.core.Inventory
import com.cvke.fridge.core.MeasureUnit
import com.cvke.fridge.core.StorageArea
import com.cvke.fridge.core.UseFirst

/**
 * One expired or expiring batch on Home.
 *
 * @property quantity A whole number of thousandths.
 */
data class AttentionBatch(
    val id: String,
    val name: String,
    val category: Category,
    val quantity: Long,
    val unit: MeasureUnit?,
    val expiry: ExpiryDate,
    val daysLeft: Int,
    val freshness: Freshness,
)

/** A storage area that has food, with the number of foods in it. */
data class AreaSummary(val id: String, val name: String, val foodCount: Int)

/**
 * One food in one storage area: all active batches of a product there.
 *
 * @property quantity The sum of the batches, in thousandths.
 * @property dateAdded The date added of the newest batch.
 * @property expiry The expiry date of the batch to use first, or null.
 * @property expiryDateCount The number of different expiry dates of the batches.
 */
data class FoodRow(
    val key: String,
    val name: String,
    val category: Category,
    val quantity: Long,
    val unit: MeasureUnit?,
    val areaId: String,
    val areaName: String,
    val dateAdded: CalendarDate,
    val expiry: ExpiryDate?,
    val daysLeft: Int?,
    val freshness: Freshness,
    val expiryDateCount: Int,
)

/**
 * The sort orders of My Fridge. They are the sort orders of FridgeFriend.
 *
 * @property ascendingFirst The direction that the order starts with.
 */
enum class SortKey(val ascendingFirst: Boolean) {
    NAME(true),
    EXPIRY(true),
    DATE_ADDED(false),
    QUANTITY(false),
    CATEGORY(true),
}

/**
 * What the two screens show, calculated from the inventory and the date of today.
 *
 * @property attention The first expired and expiring batches, in the use-first order.
 * @property expiredCount The number of all expired batches.
 * @property expiringCount The number of all expiring batches.
 * @property areas The storage areas with food: Fridge, Freezer, and then the others by name.
 * @property categories The categories with food, in the order of [Category].
 * @property foods One row for each food in each storage area, in the order of [areas] and then by name.
 * @property foodCount The number of products with an active batch.
 * @property sample True when a change in the log comes from the sample of the debug build.
 */
data class InventoryContent(
    val attention: List<AttentionBatch>,
    val expiredCount: Int,
    val expiringCount: Int,
    val areas: List<AreaSummary>,
    val categories: List<Category>,
    val foods: List<FoodRow>,
    val foodCount: Int,
    val sample: Boolean,
) {
    companion object {
        /** The largest number of batches that Home shows. */
        const val ATTENTION_COUNT = 5

        fun of(inventory: Inventory, today: CalendarDate, sample: Boolean = false): InventoryContent {
            val products = inventory.products.associateBy { it.id }
            val areas = inventory.storageAreas.sortedWith(areaOrder)
            val areaNames = areas.associate { it.id to it.name }
            // A batch is on a screen only with its product and its storage area.
            val active = inventory.activeBatches.filter { it.productId in products && it.storageAreaId in areaNames }

            val useFirst = UseFirst.order(active).mapNotNull { batch ->
                val expiry = batch.expiry ?: return@mapNotNull null
                val product = products.getValue(batch.productId)
                val daysLeft = today.daysUntil(expiry.date)
                val freshness = Freshness.of(daysLeft)
                if (freshness != Freshness.EXPIRED && freshness != Freshness.EXPIRING) return@mapNotNull null
                AttentionBatch(batch.id, product.name, product.category, batch.quantity, product.unit, expiry, daysLeft, freshness)
            }

            val areaIndex = areas.withIndex().associate { (index, area) -> area.id to index }
            val foods = active.groupBy { it.productId to it.storageAreaId }.map { (key, batches) ->
                val product = products.getValue(key.first)
                val first = UseFirst.order(batches).first()
                val daysLeft = Freshness.daysLeft(today, first.expiry?.date)
                FoodRow(
                    key = "${key.first}/${key.second}",
                    name = product.name,
                    category = product.category,
                    quantity = batches.sumOf(Batch::quantity),
                    unit = product.unit,
                    areaId = key.second,
                    areaName = areaNames.getValue(key.second),
                    dateAdded = batches.maxOf { it.dateAdded },
                    expiry = first.expiry,
                    daysLeft = daysLeft,
                    freshness = Freshness.of(daysLeft),
                    expiryDateCount = batches.mapNotNull { it.expiry?.date }.distinct().size,
                )
            }.sortedWith(
                compareBy<FoodRow> { areaIndex.getValue(it.areaId) }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                    .thenBy { it.key },
            )

            return InventoryContent(
                attention = useFirst.take(ATTENTION_COUNT),
                expiredCount = useFirst.count { it.freshness == Freshness.EXPIRED },
                expiringCount = useFirst.count { it.freshness == Freshness.EXPIRING },
                areas = areas.mapNotNull { area ->
                    val count = foods.count { it.areaId == area.id }
                    if (count == 0) null else AreaSummary(area.id, area.name, count)
                },
                categories = Category.entries.filter { category -> foods.any { it.category == category } },
                foods = foods,
                foodCount = active.map { it.productId }.distinct().size,
                sample = sample,
            )
        }

        /**
         * The rows of one storage area and one category, or of all for null, that have [query] in the name, the
         * category, the unit, or the name of the storage area. Case has no effect.
         */
        fun filter(foods: List<FoodRow>, areaId: String?, category: Category?, query: String): List<FoodRow> {
            val text = query.trim()
            return foods.filter { row ->
                (areaId == null || row.areaId == areaId) &&
                    (category == null || row.category == category) &&
                    (text.isEmpty() || listOfNotNull(row.name, row.category.key, row.unit?.key, row.areaName)
                        .any { it.contains(text, ignoreCase = true) })
            }
        }

        /**
         * The rows in a sort order. Rows that are equal keep their order. A row with no expiry date is last in both
         * directions of [SortKey.EXPIRY].
         */
        fun sort(foods: List<FoodRow>, key: SortKey, ascending: Boolean): List<FoodRow> {
            fun <T : Comparable<T>> by(value: (FoodRow) -> T): Comparator<FoodRow> =
                if (ascending) compareBy(value) else compareByDescending(value)
            val order: Comparator<FoodRow> = when (key) {
                SortKey.NAME -> String.CASE_INSENSITIVE_ORDER.let { names ->
                    Comparator { a, b -> names.compare(a.name, b.name).let { if (ascending) it else -it } }
                }
                SortKey.CATEGORY -> by { it.category.key }
                SortKey.QUANTITY -> by { it.quantity }
                SortKey.DATE_ADDED -> by { it.dateAdded }
                SortKey.EXPIRY -> Comparator { a, b ->
                    val first = a.expiry?.date
                    val second = b.expiry?.date
                    when {
                        first == null && second == null -> 0
                        first == null -> 1
                        second == null -> -1
                        ascending -> first.compareTo(second)
                        else -> second.compareTo(first)
                    }
                }
            }
            return foods.sortedWith(order)
        }

        private val areaOrder: Comparator<StorageArea> = compareBy<StorageArea> {
            when (it.id) {
                StorageArea.fridge.id -> 0
                StorageArea.freezer.id -> 1
                else -> 2
            }
        }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.id }
    }
}
