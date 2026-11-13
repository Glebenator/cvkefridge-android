package com.cvke.fridge.history

import com.cvke.fridge.core.Batch
import com.cvke.fridge.core.CalendarDate
import com.cvke.fridge.core.Category
import com.cvke.fridge.core.Change
import com.cvke.fridge.core.ChangeBody
import com.cvke.fridge.core.Inventory
import com.cvke.fridge.core.MeasureUnit
import com.cvke.fridge.core.Outcome
import com.cvke.fridge.core.Product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The list and the summary of the history for a given change log and date. */
class HistoryContentTest {
    private val today = date("2026-11-12")

    private fun date(text: String) = checkNotNull(CalendarDate.parse(text))

    private fun product(id: String, name: String, unit: MeasureUnit?, category: Category = Category.OTHER) =
        ChangeBody.ProductSaved(Product(id, name, category, unit, null, null))

    private fun batch(id: String, product: String, quantity: Long) =
        ChangeBody.BatchAdded(Batch(id, product, quantity, "fridge", date("2026-09-01"), null))

    private fun used(batch: String, quantity: Long, on: String) = ChangeBody.BatchUsed(batch, quantity, date(on))

    private fun thrown(batch: String, quantity: Long, on: String) = ChangeBody.BatchThrownAway(batch, quantity, date(on))

    /** The replay of the bodies, with the changes `c0`, `c1`, and so on, in this order. */
    private fun inventory(vararg bodies: ChangeBody): Inventory =
        Inventory.replay(bodies.mapIndexed { index, body -> Change("c$index", index.toLong(), "test", body) })

    private val log = arrayOf(
        product("milk", "Milk", MeasureUnit.L, Category.DAIRY),
        product("spinach", "Spinach", MeasureUnit.G, Category.PRODUCE),
        product("soup", "Soup", null, Category.LEFTOVERS),
        batch("m1", "milk", 2_000),
        batch("m2", "milk", 1_000),
        batch("s1", "spinach", 200_000),
        batch("s2", "spinach", 200_000),
        batch("p1", "soup", 3_000),
        used("m1", 500, "2026-10-20"), // c8
        thrown("s1", 200_000, "2026-10-31"), // c9
        used("m1", 1_500, "2026-11-11"), // c10
        thrown("m2", 250, "2026-11-12"), // c11
        used("m2", 750, "2026-11-12"), // c12
        thrown("s2", 50_000, "2026-11-12"), // c13
        thrown("p1", 1_000, "2026-11-03"), // c14
    )

    private val content = HistoryContent.of(inventory(*log), today)

    @Test
    fun theEntriesAreNewestFirstWithTheirProducts() {
        // The later date first. On one date, the later change first.
        assertEquals(listOf("c13", "c12", "c11", "c10", "c14", "c9", "c8"), content.lines.map { it.changeId })
        val first = content.lines.first()
        assertEquals(
            HistoryLine("c13", "s2", "spinach", "Spinach", Category.PRODUCE, MeasureUnit.G, 50_000, Outcome.THROWN_AWAY, today),
            first,
        )
    }

    @Test
    fun aDeleteAndAnUndoRemoveTheirEntries() {
        val changes = log.toList() + listOf(
            ChangeBody.BatchDeleted("s1"),
            ChangeBody.ProductDeleted("soup"),
            ChangeBody.ChangeUndone("c13"),
        )
        val lines = HistoryContent.lines(inventory(*changes.toTypedArray()))
        assertEquals(listOf("c12", "c11", "c10", "c8"), lines.map { it.changeId })
    }

    @Test
    fun eachDayHasOneHeaderWithTodayAndYesterday() {
        val rows = content.items(HistoryFilter.ALL)
        assertEquals(
            listOf("day-2026-11-12", "c13", "c12", "c11", "day-2026-11-11", "c10", "day-2026-11-03", "c14", "day-2026-10-31", "c9", "day-2026-10-20", "c8"),
            rows.map { it.key },
        )
        val days = rows.filterIsInstance<HistoryItem.Day>().map { it.name }
        assertEquals(listOf(DayName.TODAY, DayName.YESTERDAY, DayName.DATE, DayName.DATE, DayName.DATE), days)
        // The entries know their day, for screen readers.
        assertEquals(DayName.YESTERDAY, rows.filterIsInstance<HistoryItem.Entry>().single { it.line.changeId == "c10" }.day)
    }

    @Test
    fun theFilterKeepsOneOutcomeAndOnlyTheDaysWithAnEntry() {
        assertEquals(
            listOf("day-2026-11-12", "c12", "day-2026-11-11", "c10", "day-2026-10-20", "c8"),
            content.items(HistoryFilter.USED).map { it.key },
        )
        assertEquals(
            listOf("day-2026-11-12", "c13", "c11", "day-2026-11-03", "c14", "day-2026-10-31", "c9"),
            content.items(HistoryFilter.THROWN_AWAY).map { it.key },
        )
    }

    @Test
    fun aDateAfterTodayIsADate() {
        assertEquals(DayName.DATE, HistoryContent.dayName(date("2026-11-13"), today))
        assertEquals(DayName.DATE, HistoryContent.dayName(date("2026-11-10"), today))
    }

    @Test
    fun thePeriodsAreCalendarMonths() {
        assertTrue(HistoryPeriod.THIS_MONTH.contains(date("2026-11-01"), today))
        assertTrue(HistoryPeriod.THIS_MONTH.contains(date("2026-11-30"), today))
        assertFalse(HistoryPeriod.THIS_MONTH.contains(date("2026-10-31"), today))
        assertFalse(HistoryPeriod.THIS_MONTH.contains(date("2025-11-12"), today))
        assertTrue(HistoryPeriod.LAST_MONTH.contains(date("2026-10-01"), today))
        assertFalse(HistoryPeriod.LAST_MONTH.contains(date("2026-09-30"), today))
        // In January, the last month is December of the year before.
        val january = date("2027-01-05")
        assertTrue(HistoryPeriod.LAST_MONTH.contains(date("2026-12-31"), january))
        assertFalse(HistoryPeriod.LAST_MONTH.contains(date("2027-12-31"), january))
        assertTrue(HistoryPeriod.ALL.contains(date("2001-01-01"), today))
    }

    @Test
    fun theSummaryCountsBatchesAndAddsQuantitiesForEachUnit() {
        val month = content.summary(HistoryPeriod.THIS_MONTH)
        // Milk m1 was used and m2 was partly used: two batches. M2 was also partly thrown away, so it counts there too.
        assertEquals(OutcomeTotal(2, listOf(QuantityTotal(MeasureUnit.L, 2_250))), month.used)
        // The litres, the grams, and the soup with no unit are three sums. The sum with no unit is last.
        assertEquals(
            OutcomeTotal(
                3,
                listOf(QuantityTotal(MeasureUnit.G, 50_000), QuantityTotal(MeasureUnit.L, 250), QuantityTotal(null, 1_000)),
            ),
            month.thrownAway,
        )

        val last = content.summary(HistoryPeriod.LAST_MONTH)
        assertEquals(OutcomeTotal(1, listOf(QuantityTotal(MeasureUnit.L, 500))), last.used)
        assertEquals(OutcomeTotal(1, listOf(QuantityTotal(MeasureUnit.G, 200_000))), last.thrownAway)

        val all = content.summary(HistoryPeriod.ALL)
        assertEquals(OutcomeTotal(2, listOf(QuantityTotal(MeasureUnit.L, 2_750))), all.used)
        assertEquals(4, all.thrownAway.batchCount)
    }

    @Test
    fun theProductsThrownAwayMostOftenComeFirst() {
        // Spinach has two batches thrown away. Soup and milk have one each: milk was thrown away last.
        assertEquals(
            listOf(
                ThrownAwayProduct("spinach", "Spinach", Category.PRODUCE, 2),
                ThrownAwayProduct("milk", "Milk", Category.DAIRY, 1),
                ThrownAwayProduct("soup", "Soup", Category.LEFTOVERS, 1),
            ),
            content.summary(HistoryPeriod.ALL).mostThrownAway,
        )
        // In the last month, only the first batch of spinach was thrown away.
        assertEquals(listOf("spinach"), content.summary(HistoryPeriod.LAST_MONTH).mostThrownAway.map { it.productId })

        // Two thrown-away entries of one batch count as one batch. With the same number and date, the name decides.
        val lines = HistoryContent.lines(
            inventory(
                product("b", "banana", MeasureUnit.PCS),
                product("a", "Apple", MeasureUnit.PCS),
                product("c", "Cherry", MeasureUnit.PCS),
                product("d", "Date", MeasureUnit.PCS),
                batch("b1", "b", 5_000),
                batch("a1", "a", 5_000),
                batch("c1", "c", 5_000),
                batch("d1", "d", 5_000),
                thrown("b1", 1_000, "2026-11-01"),
                thrown("b1", 1_000, "2026-11-01"),
                thrown("a1", 1_000, "2026-11-01"),
                thrown("c1", 1_000, "2026-11-01"),
                thrown("d1", 1_000, "2026-11-01"),
            ),
        )
        assertEquals(listOf("Apple", "banana", "Cherry"), HistoryContent.mostThrownAway(lines, 3).map { it.name })
    }

    @Test
    fun anEmptyHistoryHasNoRowsAndAnEmptySummary() {
        val empty = HistoryContent.of(inventory(product("milk", "Milk", MeasureUnit.L), batch("m1", "milk", 1_000)), today)
        assertEquals(emptyList<HistoryItem>(), empty.items(HistoryFilter.ALL))
        HistoryPeriod.entries.forEach { period ->
            val summary = empty.summary(period)
            assertTrue(summary.isEmpty)
            assertEquals(OutcomeTotal(0, emptyList()), summary.used)
            assertEquals(emptyList<ThrownAwayProduct>(), summary.mostThrownAway)
        }
        // A period with no entry is empty also when the history has entries.
        assertTrue(HistoryContent.summary(content.lines, HistoryPeriod.THIS_MONTH, date("2027-03-01")).isEmpty)
    }
}
