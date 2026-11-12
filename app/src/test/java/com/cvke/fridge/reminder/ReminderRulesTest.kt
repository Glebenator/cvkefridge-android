package com.cvke.fridge.reminder

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
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which batches go into the reminder of a date (see [ReminderRules]). */
class ReminderRulesTest {
    private val today = date("2026-11-12")

    private fun date(text: String) = checkNotNull(CalendarDate.parse(text))

    private fun product(id: String, name: String = id) = ChangeBody.ProductSaved(Product(id, name, Category.OTHER, MeasureUnit.PCS, null, null))

    private fun batch(
        id: String,
        product: String,
        expires: String?,
        added: String = "2026-11-01",
        source: ExpirySource = ExpirySource.PRINTED,
        area: String = "fridge",
    ) = ChangeBody.BatchAdded(Batch(id, product, 1_000, area, date(added), expires?.let { ExpiryDate(date(it), source) }))

    private fun inventory(vararg bodies: ChangeBody): Inventory =
        Inventory.replay(bodies.mapIndexed { index, body -> Change("c$index", index.toLong(), "test", body) })

    private fun foods(inventory: Inventory, leadDays: Int = ReminderRules.DEFAULT_LEAD_DAYS) = ReminderRules.foods(inventory, today, leadDays)

    @Test
    fun theDefaultLeadTimeIsTheExpiringRuleOfTheCore() {
        assertEquals(Freshness.expiringWithinDays, ReminderRules.DEFAULT_LEAD_DAYS)
        assertEquals(0..Freshness.expiringWithinDays, ReminderRules.leadDayChoices)
    }

    @Test
    fun aBatchIsInTheReminderFromTheDayAfterItsExpiryDateToTheLeadTime() {
        val dates = listOf("2026-11-09", "2026-11-10", "2026-11-11", "2026-11-12", "2026-11-13", "2026-11-14", "2026-11-15")
        val all = inventory(
            *dates.map { product("p$it") }.toTypedArray(),
            *dates.map { batch("b$it", "p$it", it) }.toTypedArray(),
        )
        // Expired yesterday (-1), today (0), tomorrow (1), and in 2 days. Not 2 or 3 days ago, not in 3 days.
        assertEquals(listOf(-1, 0, 1, 2), foods(all).map { it.daysLeft })
        assertEquals(listOf("p2026-11-11", "p2026-11-12", "p2026-11-13", "p2026-11-14"), foods(all).map { it.productId })
        // A shorter lead time stops earlier. The batch that expired yesterday stays.
        assertEquals(listOf(-1, 0, 1), foods(all, leadDays = 1).map { it.daysLeft })
        assertEquals(listOf(-1, 0), foods(all, leadDays = 0).map { it.daysLeft })
    }

    @Test
    fun anExpiredBatchIsOnlyInTheReminderOfTheFirstDayAfterItsExpiryDate() {
        val milk = inventory(product("milk"), batch("b1", "milk", "2026-11-11"))
        assertEquals(listOf(-1), ReminderRules.foods(milk, date("2026-11-12"), 2).map { it.daysLeft })
        assertEquals(emptyList<ReminderFood>(), ReminderRules.foods(milk, date("2026-11-13"), 2))
        assertEquals(emptyList<ReminderFood>(), ReminderRules.foods(milk, date("2026-12-01"), 2))
    }

    @Test
    fun aBatchWithNoExpiryDateOrNoQuantityIsNotInTheReminder() {
        val inventory = inventory(
            product("rice"),
            product("milk"),
            batch("b1", "rice", null),
            batch("b2", "milk", "2026-11-12"),
            ChangeBody.BatchUsed("b2", 1_000, today),
        )
        assertEquals(emptyList<ReminderFood>(), foods(inventory))
    }

    @Test
    fun aProductWithSeveralBatchesIsOneFoodWithItsFirstBatch() {
        val inventory = inventory(
            product("milk", "Whole milk"),
            batch("b1", "milk", "2026-11-14"),
            batch("b2", "milk", "2026-11-12", source = ExpirySource.ESTIMATED),
            batch("b3", "milk", "2026-11-11", area = "freezer"),
            batch("b4", "milk", "2026-11-30"),
        )
        assertEquals(listOf(ReminderFood("milk", "Whole milk", -1, estimated = false)), foods(inventory))
        // After the expired batch is used, the batch that expires today is first. Its date is an estimate.
        val used = inventory(
            product("milk", "Whole milk"),
            batch("b1", "milk", "2026-11-14"),
            batch("b2", "milk", "2026-11-12", source = ExpirySource.ESTIMATED),
        )
        assertEquals(listOf(ReminderFood("milk", "Whole milk", 0, estimated = true)), foods(used))
    }

    @Test
    fun theFoodsAreInTheOrderOfTheirDaysLeftAndThenOfTheirNames() {
        val inventory = inventory(
            product("a", "yoghurt"),
            product("b", "Apples"),
            product("c", "bread"),
            product("d", "Cheese"),
            batch("b1", "a", "2026-11-13"),
            batch("b2", "b", "2026-11-13"),
            batch("b3", "c", "2026-11-12"),
            batch("b4", "d", "2026-11-11"),
        )
        assertEquals(listOf("Cheese", "bread", "Apples", "yoghurt"), foods(inventory).map { it.name })
    }

    @Test
    fun aLeadTimeOutOfRangeUsesTheNearestChoice() {
        val inventory = inventory(product("milk"), batch("b1", "milk", "2026-11-15"))
        assertEquals(emptyList<ReminderFood>(), foods(inventory, leadDays = 10))
        assertEquals(emptyList<ReminderFood>(), foods(inventory, leadDays = -3))
    }
}
