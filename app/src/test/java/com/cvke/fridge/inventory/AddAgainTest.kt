package com.cvke.fridge.inventory

import com.cvke.fridge.core.Batch
import com.cvke.fridge.core.CalendarDate
import com.cvke.fridge.core.Category
import com.cvke.fridge.core.Change
import com.cvke.fridge.core.ChangeBody
import com.cvke.fridge.core.ExpiryDate
import com.cvke.fridge.core.ExpirySource
import com.cvke.fridge.core.Food
import com.cvke.fridge.core.FoodTable
import com.cvke.fridge.core.Inventory
import com.cvke.fridge.core.MeasureUnit
import com.cvke.fridge.core.Product
import com.cvke.fridge.core.StorageKind
import com.cvke.fridge.history.HistoryContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The values of "Add again" and the list of recent foods, from a change log. */
class AddAgainTest {
    private val today = date("2026-11-12")

    private fun date(text: String) = checkNotNull(CalendarDate.parse(text))

    private fun product(id: String, name: String, unit: MeasureUnit?, foodId: String? = null) =
        ChangeBody.ProductSaved(Product(id, name, Category.DAIRY, unit, null, foodId))

    private fun batch(id: String, product: String, quantity: Long, area: String, added: String) =
        ChangeBody.BatchAdded(Batch(id, product, quantity, area, date(added), ExpiryDate(date(added), ExpirySource.PRINTED)))

    private val inventory = Inventory.replay(
        listOf(
            ChangeBody.StorageAreaSaved("cellar", "Cellar", StorageKind.PANTRY),
            product("milk", "Milk", MeasureUnit.L, "f-milk"),
            product("jam", "Jam", null),
            product("soup", "Soup", MeasureUnit.ML),
            product("tea", "Tea", MeasureUnit.PACK),
            product("salt", "Salt", MeasureUnit.G),
            batch("m1", "milk", 1_000, "fridge", "2026-10-01"),
            batch("m2", "milk", 2_000, "fridge", "2026-10-10"),
            batch("m3", "milk", 1_000, "freezer", "2026-11-01"),
            batch("j1", "jam", 500, "cellar", "2026-10-20"),
            batch("s1", "soup", 1_000, "fridge", "2026-11-05"),
            batch("t1", "tea", 1_000, "cellar", "2026-10-01"),
            batch("t2", "tea", 2_000, "fridge", "2026-10-02"),
            ChangeBody.BatchUsed("m1", 400, date("2026-10-05")),
            ChangeBody.BatchThrownAway("m1", 600, date("2026-10-06")),
            ChangeBody.BatchUsed("m2", 2_000, date("2026-10-15")),
            ChangeBody.BatchThrownAway("j1", 500, date("2026-10-25")),
            ChangeBody.BatchUsed("t1", 1_000, date("2026-10-08")),
            ChangeBody.BatchUsed("t2", 2_000, date("2026-10-09")),
            // The storage area can go when no active batch is in it.
            ChangeBody.StorageAreaDeleted("cellar"),
            ChangeBody.BatchUsed("s1", 400, date("2026-11-06")),
            ChangeBody.BatchUsed("m3", 1_000, date("2026-11-03")),
        ).mapIndexed { index, body -> Change("c$index", index.toLong(), "test", body) },
    )

    private val foods = FoodTable(
        listOf(Food("f-milk", "Milk", null, Category.DAIRY, emptyList(), mapOf(StorageKind.FRIDGE to 7, StorageKind.FREEZER to 90), StorageKind.FRIDGE)),
    )

    @Test
    fun theNewestBatchGivesTheStorageAreaAndTheMostCommonQuantityWins() {
        // The batches of milk had 1 l, 2 l, and 1 l when they were added. A use and a throw away do not change that.
        assertEquals(AgainValues("freezer", 1_000), AddAgain.values(inventory, "milk"))
        // A batch that is still active counts with the quantity that it had at the start.
        assertEquals(AgainValues("fridge", 1_000), AddAgain.values(inventory, "soup"))
    }

    @Test
    fun withTheSameNumberOfBatchesTheNewerQuantityWins() {
        assertEquals(AgainValues("fridge", 2_000), AddAgain.values(inventory, "tea"))
        assertEquals(3_000L, AddAgain.usualQuantity(listOf(3_000, 1_000, 1_000, 3_000)))
    }

    @Test
    fun aStorageAreaThatIsNotThereAndAProductWithNoBatchGiveNoValue() {
        assertEquals(AgainValues(null, 500), AddAgain.values(inventory, "jam"))
        assertEquals(AgainValues(null, null), AddAgain.values(inventory, "salt"))
        assertEquals(null, AddAgain.usualQuantity(emptyList()))
    }

    @Test
    fun theRecentFoodsAreTheProductsWithNoActiveBatchThatEndedLastFirst() {
        val lines = HistoryContent.lines(inventory)
        // Soup has an active batch, and salt is in no history entry.
        assertEquals(listOf("milk", "jam", "tea"), AddAgain.recent(lines, inventory, limit = 5).map { it.id })
        assertEquals(listOf("milk"), AddAgain.recent(lines, inventory, limit = 1).map { it.id })
        assertTrue(AddAgain.inStock(inventory, "soup"))
        assertFalse(AddAgain.inStock(inventory, "milk"))
    }

    @Test
    fun theFormOfAddAgainHasTheValuesAndAnEstimateFromToday() {
        val milk = inventory.products.single { it.id == "milk" }
        val form = AddFoodForm.again(milk, AddAgain.values(inventory, "milk"), startArea = "fridge", decimalSeparator = ',')
        assertEquals("Milk", form.name)
        assertEquals("freezer", form.storageAreaId)
        assertEquals("1", form.quantity)
        assertEquals(ExpiryInput.Suggested, form.expiry)

        // The new batch goes to the same product. Its expiry date is the estimate for the freezer from today.
        val action = checkNotNull(form.action(inventory.products, inventory.storageAreas, foods, today))
        assertEquals("milk", action.productId)
        assertEquals(1_000L, action.quantity)
        assertEquals(ExpiryDate(date("2027-02-10"), ExpirySource.ESTIMATED), action.expiry)

        // A product with no storage area and a fraction keeps the start storage area and the decimal sign.
        val jam = inventory.products.single { it.id == "jam" }
        val jamForm = AddFoodForm.again(jam, AgainValues(null, 1_500), startArea = "fridge", decimalSeparator = ',')
        assertEquals("fridge" to "1,5", jamForm.storageAreaId to jamForm.quantity)
        // No food of the table: no estimate, so no expiry date. The form never takes an earlier date.
        assertEquals(null, jamForm.action(inventory.products, inventory.storageAreas, foods, today)?.expiry)
    }

    @Test
    fun aRecentFoodThatThePersonSelectsGetsItsValues() {
        val form = AddFoodForm(storageAreaId = "fridge", quantity = "3", expiry = ExpiryInput.None)
            .withSuggestion(Suggestion.OfProduct(inventory.products.single { it.id == "milk" }), foods, inventory.storageAreas)
            .again(AddAgain.values(inventory, "milk"), '.')
        assertEquals(Triple("Milk", "freezer", "1"), Triple(form.name, form.storageAreaId, form.quantity))
        assertEquals(ExpiryInput.Suggested, form.expiry)
    }
}
