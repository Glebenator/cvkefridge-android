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
    val productId: String,
    val name: String,
    val category: Category,
    val quantity: Long,
    val unit: MeasureUnit?,
    val expiry: ExpiryDate,
    val daysLeft: Int,
    val freshness: Freshness,
)

/** A storage area, with the number of foods in it. The number is 0 for a storage area with no food. */
data class AreaSummary(val id: String, val name: String, val foodCount: Int)

/**
 * One food in one storage area: all active batches of a product there.
 *
 * @property batchIds The batches, in the use-first order.
 * @property quantity The sum of the batches, in thousandths.
 * @property firstQuantity The quantity of the batch to use first, in thousandths.
 * @property dateAdded The date added of the newest batch.
 * @property expiry The expiry date of the batch to use first, or null.
 * @property freshness The freshness of the batch to use first.
 * @property batchFreshness The freshness of each batch. A food can have an expired batch and an expiring batch.
 * @property expiryDateCount The number of different expiry dates of the batches.
 */
data class FoodRow(
    val key: String,
    val productId: String,
    val batchIds: List<String>,
    val name: String,
    val category: Category,
    val quantity: Long,
    val firstQuantity: Long,
    val unit: MeasureUnit?,
    val areaId: String,
    val areaName: String,
    val dateAdded: CalendarDate,
    val expiry: ExpiryDate?,
    val daysLeft: Int?,
    val freshness: Freshness,
    val batchFreshness: Set<Freshness>,
    val expiryDateCount: Int,
)

/**
 * The filters of My Fridge.
 *
 * @property areaId The storage area, or null for all.
 * @property category The category, or null for all.
 * @property freshness The freshness values to show. A food is shown when one of its batches has one of them. The
 *   empty set shows all food.
 * @property query The search text.
 */
data class FoodFilter(
    val areaId: String? = null,
    val category: Category? = null,
    val freshness: Set<Freshness> = emptySet(),
    val query: String = "",
)

/** A category in the menu of the categories, with the number of foods that its menu item shows. */
data class CategoryCount(val category: Category, val foodCount: Int)

/**
 * What My Fridge shows for a [FoodFilter]. Each count is the number of rows that the list has when the person selects
 * that value and does not change the other filters.
 *
 * @property areaId The storage area of the filter, or null for all. It is also null when the storage area of the
 *   filter is not there.
 * @property category The category of the filter, or null for all. It is also null when the category of the filter has
 *   no food in the storage area.
 * @property areaFoodCount The number of foods in the storage area, with no other filter.
 * @property categories The categories that the menu shows, in the order of [Category]: each category with a count
 *   above 0, and the category of the filter.
 * @property allCategoriesCount The count for "All categories".
 * @property expiringCount The count for "Expiring".
 * @property expiredCount The count for "Expired".
 * @property rows The rows that agree with all filters, in the order of [InventoryContent.foods].
 */
data class FilteredFoods(
    val areaId: String?,
    val category: Category?,
    val areaFoodCount: Int,
    val categories: List<CategoryCount>,
    val allCategoriesCount: Int,
    val expiringCount: Int,
    val expiredCount: Int,
    val rows: List<FoodRow>,
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
 * @property areas All storage areas: Fridge, Freezer, and then the others by name.
 * @property foods One row for each food in each storage area, in the order of [areas] and then by name.
 * @property foodCount The number of products with an active batch.
 * @property sample True when a change in the log comes from the sample of the debug build.
 */
data class InventoryContent(
    val attention: List<AttentionBatch>,
    val expiredCount: Int,
    val expiringCount: Int,
    val areas: List<AreaSummary>,
    val foods: List<FoodRow>,
    val foodCount: Int,
    val sample: Boolean,
) {
    /**
     * The rows and the counts for [filter]. The search text is compared with the name, the category, the unit, and the
     * name of the storage area. Case has no effect.
     */
    fun filtered(filter: FoodFilter): FilteredFoods {
        val area = areas.firstOrNull { it.id == filter.areaId }
        val inArea = if (area == null) foods else foods.filter { it.areaId == area.id }
        val category = filter.category?.takeIf { wanted -> inArea.any { it.category == wanted } }
        val text = filter.query.trim()
        val found = inArea.filter { row ->
            text.isEmpty() || listOfNotNull(row.name, row.category.key, row.unit?.key, row.areaName)
                .any { it.contains(text, ignoreCase = true) }
        }
        // The counts of one filter are for the rows that agree with the search text and with the other filter.
        val withFreshness = found.filter { row -> filter.freshness.isEmpty() || row.batchFreshness.any { it in filter.freshness } }
        val withCategory = found.filter { category == null || it.category == category }
        return FilteredFoods(
            areaId = area?.id,
            category = category,
            areaFoodCount = inArea.size,
            // A category with a count of 0 gives an empty list, so the menu does not have it. The selected category stays.
            categories = Category.entries
                .map { entry -> CategoryCount(entry, withFreshness.count { it.category == entry }) }
                .filter { it.foodCount > 0 || it.category == category },
            allCategoriesCount = withFreshness.size,
            expiringCount = withCategory.count { Freshness.EXPIRING in it.batchFreshness },
            expiredCount = withCategory.count { Freshness.EXPIRED in it.batchFreshness },
            rows = withFreshness.filter { category == null || it.category == category },
        )
    }

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
                AttentionBatch(batch.id, product.id, product.name, product.category, batch.quantity, product.unit, expiry, daysLeft, freshness)
            }

            val areaIndex = areas.withIndex().associate { (index, area) -> area.id to index }
            val foods = active.groupBy { it.productId to it.storageAreaId }.map { (key, batches) ->
                val product = products.getValue(key.first)
                val ordered = UseFirst.order(batches)
                val first = ordered.first()
                val daysLeft = Freshness.daysLeft(today, first.expiry?.date)
                FoodRow(
                    key = "${key.first}/${key.second}",
                    productId = key.first,
                    batchIds = ordered.map(Batch::id),
                    name = product.name,
                    category = product.category,
                    quantity = batches.sumOf(Batch::quantity),
                    firstQuantity = first.quantity,
                    unit = product.unit,
                    areaId = key.second,
                    areaName = areaNames.getValue(key.second),
                    dateAdded = batches.maxOf { it.dateAdded },
                    expiry = first.expiry,
                    daysLeft = daysLeft,
                    freshness = Freshness.of(daysLeft),
                    batchFreshness = batches.mapTo(HashSet()) { Freshness.of(Freshness.daysLeft(today, it.expiry?.date)) },
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
                areas = areas.map { area -> AreaSummary(area.id, area.name, foods.count { it.areaId == area.id }) },
                foods = foods,
                foodCount = active.map { it.productId }.distinct().size,
                sample = sample,
            )
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
