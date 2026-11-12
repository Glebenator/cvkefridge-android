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
        assertEquals(listOf(Category.DAIRY, Category.OTHER), sample.categories)
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
        assertEquals(emptyList<AreaSummary>(), used.areas)
    }

    @Test
    fun filterIsForOneStorageAreaOneCategoryAndTheSearchText() {
        fun keys(areaId: String?, category: Category?, query: String) =
            InventoryContent.filter(sample.foods, areaId, category, query).map { it.key }
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
