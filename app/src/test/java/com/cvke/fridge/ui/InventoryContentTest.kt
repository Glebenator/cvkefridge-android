package com.cvke.fridge.ui

import com.cvke.fridge.core.Batch
import com.cvke.fridge.core.CalendarDate
import com.cvke.fridge.core.Category
import com.cvke.fridge.core.Change
import com.cvke.fridge.core.ChangeBody
import com.cvke.fridge.core.ExpiryDate
import com.cvke.fridge.core.ExpirySource
import com.cvke.fridge.core.Freshness
import com.cvke.fridge.core.Inventory
import com.cvke.fridge.core.MeasureUnit
import com.cvke.fridge.core.Product
import com.cvke.fridge.core.StorageKind
import org.junit.Assert.assertEquals
import org.junit.Test

/** The content of the screens for a given inventory and date. The freshness rules have their own tests in `:core`. */
class InventoryContentTest {
    private val today = date("2026-11-11")

    private fun date(text: String) = checkNotNull(CalendarDate.parse(text))

    private fun product(id: String, name: String, category: Category = Category.OTHER) =
        ChangeBody.ProductSaved(Product(id, name, category, MeasureUnit.PCS, null, null))

    private fun batch(id: String, product: String, area: String, added: String, expires: String?, quantity: Long = 1_000) =
        ChangeBody.BatchAdded(
            Batch(id, product, quantity, area, date(added), expires?.let { ExpiryDate(date(it), ExpirySource.PRINTED) }),
        )

    private fun content(vararg bodies: ChangeBody): InventoryContent {
        val changes = bodies.mapIndexed { index, body -> Change("c$index", index.toLong(), "test", body) }
        return InventoryContent.of(Inventory.replay(changes), today)
    }

    private val sample = content(
        ChangeBody.StorageAreaSaved("pantry", "Pantry", StorageKind.PANTRY),
        product("milk", "milk", Category.DAIRY),
        product("eggs", "Eggs"),
        product("rice", "Rice"),
        product("ham", "Ham"),
        product("jam", "Jam"),
        product("tea", "Tea"),
        batch("b1", "milk", "fridge", "2026-11-05", "2026-11-12"),
        batch("b2", "milk", "fridge", "2026-11-10", "2026-11-20", quantity = 2_000),
        batch("b3", "eggs", "fridge", "2026-11-01", "2026-11-10"),
        batch("b4", "rice", "pantry", "2026-10-01", null),
        batch("b5", "ham", "fridge", "2026-11-09", "2026-11-14"),
        batch("b6", "jam", "pantry", "2026-11-01", "2026-11-30"),
        batch("b7", "tea", "pantry", "2026-11-01", "2026-12-30"),
        batch("b8", "milk", "freezer", "2026-11-01", "2027-02-01"),
    )

    @Test
    fun attentionHasTheExpiredAndTheExpiringBatchesInTheUseFirstOrder() {
        assertEquals(listOf("b3", "b1"), sample.attention.map { it.id })
        assertEquals(listOf(Freshness.EXPIRED, Freshness.EXPIRING), sample.attention.map { it.freshness })
        assertEquals(listOf(-1, 1), sample.attention.map { it.daysLeft })
        assertEquals(1, sample.expiredCount)
        assertEquals(1, sample.expiringCount)
    }

    @Test
    fun attentionStopsAtFiveBatchesAndTheCountsAreForAllBatches() {
        val many = content(
            product("milk", "Milk"),
            *(1..7).map { batch("b$it", "milk", "fridge", "2026-11-01", if (it <= 3) "2026-11-0$it" else "2026-11-11") }.toTypedArray(),
        )
        assertEquals(InventoryContent.ATTENTION_COUNT, many.attention.size)
        assertEquals(listOf("b1", "b2", "b3", "b4", "b5"), many.attention.map { it.id })
        assertEquals(3, many.expiredCount)
        assertEquals(4, many.expiringCount)
    }

    @Test
    fun foodsHaveOneRowForEachProductInEachStorageArea() {
        assertEquals(
            listOf("eggs/fridge", "ham/fridge", "milk/fridge", "milk/freezer", "jam/pantry", "rice/pantry", "tea/pantry"),
            sample.foods.map { it.key },
        )
        val milk = sample.foods.first { it.key == "milk/fridge" }
        assertEquals(3_000L, milk.quantity)
        assertEquals(2, milk.expiryDateCount)
        assertEquals(1, milk.daysLeft)
        assertEquals(date("2026-11-10"), milk.dateAdded)
        assertEquals(Freshness.NO_EXPIRY_DATE, sample.foods.first { it.key == "rice/pantry" }.freshness)
    }

    @Test
    fun countsAreForProductsWithAnActiveBatch() {
        assertEquals(6, sample.foodCount)
        assertEquals(listOf("fridge" to 3, "freezer" to 1, "pantry" to 3), sample.areas.map { it.id to it.foodCount })
    }

    @Test
    fun aBatchThatWasUsedCompletelyIsNotOnAScreen() {
        val used = content(
            product("milk", "Milk"),
            batch("b1", "milk", "fridge", "2026-11-05", "2026-11-12"),
            ChangeBody.BatchUsed("b1", 1_000, today),
        )
        assertEquals(0, used.foodCount)
        assertEquals(emptyList<AttentionBatch>(), used.attention)
        assertEquals(emptyList<FoodRow>(), used.foods)
        // A storage area with no food stays in the list, so that its tab does not go away.
        assertEquals(listOf("fridge" to 0, "freezer" to 0), used.areas.map { it.id to it.foodCount })
    }

    @Test
    fun filterIsForOneStorageAreaOneCategoryAndTheSearchText() {
        fun keys(areaId: String?, category: Category?, query: String) =
            sample.filtered(FoodFilter(areaId, category, query = query)).rows.map { it.key }
        assertEquals(listOf("milk/fridge", "milk/freezer"), keys(null, null, " MIL "))
        assertEquals(listOf("milk/freezer"), keys("freezer", null, ""))
        assertEquals(emptyList<String>(), keys("freezer", null, "jam"))
        assertEquals(listOf("milk/fridge", "milk/freezer"), keys(null, Category.DAIRY, ""))
        assertEquals(listOf("milk/fridge"), keys("fridge", Category.DAIRY, ""))
        // The search text is also in the category, the unit, and the name of the storage area.
        assertEquals(listOf("milk/fridge", "milk/freezer"), keys(null, null, "dairy"))
        assertEquals(listOf("jam/pantry", "rice/pantry", "tea/pantry"), keys(null, null, "pantry"))
    }

    @Test
    fun filterIsForTheFreshnessOfEachBatchOfAFood() {
        fun keys(vararg freshness: Freshness, areaId: String? = null) =
            sample.filtered(FoodFilter(areaId, freshness = freshness.toSet())).rows.map { it.key }
        assertEquals(listOf("milk/fridge"), keys(Freshness.EXPIRING))
        assertEquals(listOf("eggs/fridge"), keys(Freshness.EXPIRED))
        // The two values together show the food that has one of them.
        assertEquals(listOf("eggs/fridge", "milk/fridge"), keys(Freshness.EXPIRING, Freshness.EXPIRED))
        assertEquals(emptyList<String>(), keys(Freshness.EXPIRING, areaId = "pantry"))

        // A food with an expired batch and an expiring batch is one row, and each of the two filters shows it.
        val mixed = content(
            product("milk", "Milk"),
            batch("b1", "milk", "fridge", "2026-11-01", "2026-11-10"),
            batch("b2", "milk", "fridge", "2026-11-05", "2026-11-12"),
            batch("b3", "milk", "fridge", "2026-11-10", "2026-11-30"),
        )
        assertEquals(Freshness.EXPIRED, mixed.foods.single().freshness)
        assertEquals(setOf(Freshness.EXPIRED, Freshness.EXPIRING, Freshness.FRESH), mixed.foods.single().batchFreshness)
        assertEquals(1, mixed.filtered(FoodFilter(freshness = setOf(Freshness.EXPIRING))).rows.size)
        assertEquals(1, mixed.filtered(FoodFilter(freshness = setOf(Freshness.EXPIRED))).rows.size)
    }

    @Test
    fun aFilterValueThatIsNotThereShowsAll() {
        // No storage area has this identifier.
        val gone = sample.filtered(FoodFilter(areaId = "cellar"))
        assertEquals(null, gone.areaId)
        assertEquals(7, gone.rows.size)
        // The Pantry has no dairy.
        val noDairy = sample.filtered(FoodFilter(areaId = "pantry", category = Category.DAIRY))
        assertEquals("pantry", noDairy.areaId)
        assertEquals(null, noDairy.category)
        assertEquals(listOf("jam/pantry", "rice/pantry", "tea/pantry"), noDairy.rows.map { it.key })
        // A storage area with no food is a storage area. It does not show all.
        val empty = content(product("milk", "Milk"), batch("b1", "milk", "fridge", "2026-11-05", null))
            .filtered(FoodFilter(areaId = "freezer"))
        assertEquals("freezer", empty.areaId)
        assertEquals(0, empty.areaFoodCount)
        assertEquals(emptyList<FoodRow>(), empty.rows)
    }

    @Test
    fun countsAreForTheStorageAreaTheSearchTextAndTheOtherFilter() {
        fun categories(view: FilteredFoods) = view.categories.map { it.category to it.foodCount }

        val all = sample.filtered(FoodFilter())
        assertEquals(7, all.areaFoodCount)
        assertEquals(7, all.allCategoriesCount)
        assertEquals(listOf(Category.DAIRY to 2, Category.OTHER to 5), categories(all))
        assertEquals(1, all.expiringCount)
        assertEquals(1, all.expiredCount)

        // The menu has the categories with food in the storage area.
        val freezer = sample.filtered(FoodFilter(areaId = "freezer"))
        assertEquals(1, freezer.areaFoodCount)
        assertEquals(listOf(Category.DAIRY to 1), categories(freezer))
        assertEquals(0, freezer.expiringCount)

        // A category count is for the selected freshness. A category with no such food stays in the menu.
        val expired = sample.filtered(FoodFilter(freshness = setOf(Freshness.EXPIRED)))
        assertEquals(1, expired.allCategoriesCount)
        assertEquals(listOf(Category.DAIRY to 0, Category.OTHER to 1), categories(expired))
        // The count of a freshness chip does not change when the chip is on.
        assertEquals(1, expired.expiringCount)
        assertEquals(1, expired.expiredCount)

        // A freshness count is for the selected category.
        val dairy = sample.filtered(FoodFilter(category = Category.DAIRY))
        assertEquals(1, dairy.expiringCount)
        assertEquals(0, dairy.expiredCount)
        assertEquals(7, dairy.allCategoriesCount)

        // All counts are for the search text. The number of foods in the storage area is not.
        val search = sample.filtered(FoodFilter(query = "eggs"))
        assertEquals(7, search.areaFoodCount)
        assertEquals(1, search.allCategoriesCount)
        assertEquals(listOf(Category.DAIRY to 0, Category.OTHER to 1), categories(search))
        assertEquals(0, search.expiringCount)
        assertEquals(1, search.expiredCount)
    }

    @Test
    fun sortKeepsFoodWithNoExpiryDateLastInBothDirections() {
        fun keys(key: SortKey, ascending: Boolean) = InventoryContent.sort(sample.foods, key, ascending).map { it.key }
        assertEquals(
            listOf("eggs/fridge", "milk/fridge", "ham/fridge", "jam/pantry", "tea/pantry", "milk/freezer", "rice/pantry"),
            keys(SortKey.EXPIRY, true),
        )
        assertEquals(
            listOf("milk/freezer", "tea/pantry", "jam/pantry", "ham/fridge", "milk/fridge", "eggs/fridge", "rice/pantry"),
            keys(SortKey.EXPIRY, false),
        )
    }

    @Test
    fun sortHasTheOtherOrdersInBothDirections() {
        fun keys(key: SortKey, ascending: Boolean) = InventoryContent.sort(sample.foods, key, ascending).map { it.key }
        // Rows that are equal keep the order of the storage areas.
        assertEquals(
            listOf("eggs/fridge", "ham/fridge", "jam/pantry", "milk/fridge", "milk/freezer", "rice/pantry", "tea/pantry"),
            keys(SortKey.NAME, true),
        )
        assertEquals("tea/pantry", keys(SortKey.NAME, false).first())
        assertEquals("milk/fridge", keys(SortKey.QUANTITY, false).first())
        assertEquals(listOf("milk/fridge", "ham/fridge"), keys(SortKey.DATE_ADDED, false).take(2))
        assertEquals("rice/pantry", keys(SortKey.DATE_ADDED, true).first())
        assertEquals(listOf("milk/fridge", "milk/freezer"), keys(SortKey.CATEGORY, true).take(2))
    }
}
