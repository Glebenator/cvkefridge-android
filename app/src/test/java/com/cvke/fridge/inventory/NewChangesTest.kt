package com.cvke.fridge.inventory

import com.cvke.fridge.core.Batch
import com.cvke.fridge.core.CalendarDate
import com.cvke.fridge.core.Category
import com.cvke.fridge.core.Change
import com.cvke.fridge.core.ChangeBody
import com.cvke.fridge.core.Inventory
import com.cvke.fridge.core.Product
import com.cvke.fridge.core.RejectionReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The changes that the phone adds to the change log. */
class NewChangesTest {
    private val today = checkNotNull(CalendarDate.parse("2026-11-12"))
    private val product = ChangeBody.ProductSaved(Product("p1", "Rice", Category.OTHER, null, null, null))
    private val batch = ChangeBody.BatchAdded(Batch("b1", "p1", 1_000, "fridge", today, null))

    private fun ids(): () -> String {
        var count = 0
        // The second identifier is smaller than the first, as a random identifier can be.
        return { "id-${9 - count++}" }
    }

    @Test
    fun theChangesOfOneActionStayInTheirOrderInTheReplay() {
        val changes = NewChanges.stamp(listOf(product, batch), emptyList(), now = 500, origin = "phone", newId = ids())
        assertEquals(listOf(500L, 501L), changes.map { it.at })
        assertEquals(listOf("phone", "phone"), changes.map { it.origin })
        val inventory = Inventory.replay(changes)
        assertEquals(emptyList<Any>(), inventory.rejected)
        assertEquals(listOf("b1"), inventory.batches.map { it.id })
    }

    @Test
    fun aChangeIsAfterTheLogWhenTheClockWentBack() {
        val log = listOf(Change("c1", at = 900, origin = "phone", body = product))
        val changes = NewChanges.stamp(listOf(batch), log, now = 100, origin = "phone", newId = ids())
        assertEquals(listOf(901L), changes.map { it.at })
        assertEquals(emptyList<Any>(), Inventory.replay(log + changes).rejected)
    }

    @Test
    fun theReasonIsThatOfTheNewChangeThatTheReplayRejects() {
        val log = listOf(
            Change("c1", 1, "phone", product),
            Change("c0", 2, "phone", ChangeBody.BatchDeleted("gone")),
        )
        val before = Inventory.replay(log)
        val good = NewChanges.stamp(listOf(batch), log, 10, "phone", ids())
        assertNull(NewChanges.rejection(before, Inventory.replay(log + good)))

        val bad = NewChanges.stamp(listOf(ChangeBody.BatchUsed("b1", 1_000, today)), log, 10, "phone", ids())
        assertEquals(RejectionReason.UNKNOWN_BATCH, NewChanges.rejection(before, Inventory.replay(log + bad)))
    }
}
