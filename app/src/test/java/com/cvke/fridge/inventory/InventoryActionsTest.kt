package com.cvke.fridge.inventory

import com.cvke.fridge.core.Batch
import com.cvke.fridge.core.CalendarDate
import com.cvke.fridge.core.Category
import com.cvke.fridge.core.Change
import com.cvke.fridge.core.ChangeBody
import com.cvke.fridge.core.ExpiryDate
import com.cvke.fridge.core.ExpirySource
import com.cvke.fridge.core.Inventory
import com.cvke.fridge.core.MeasureUnit
import com.cvke.fridge.core.Outcome
import com.cvke.fridge.core.Product
import com.cvke.fridge.core.RejectionReason
import com.cvke.fridge.core.StorageKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The changes of each action, and their result in the replay of the core. */
class InventoryActionsTest {
    private val today = date("2026-11-12")

    private fun date(text: String) = checkNotNull(CalendarDate.parse(text))

    private fun batch(id: String, product: String, quantity: Long, area: String, expires: String?) =
        ChangeBody.BatchAdded(Batch(id, product, quantity, area, date("2026-11-01"), expires?.let { ExpiryDate(date(it), ExpirySource.PRINTED) }))

    private val log = listOf(
        ChangeBody.StorageAreaSaved("pantry", "Pantry", StorageKind.PANTRY),
        ChangeBody.ProductSaved(Product("milk", "Milk", Category.DAIRY, MeasureUnit.L, null, null)),
        ChangeBody.ProductSaved(Product("rice", "Rice", Category.OTHER, MeasureUnit.KG, null, null)),
        batch("m-new", "milk", 2_000, "fridge", "2026-11-20"),
        batch("m-old", "milk", 1_000, "fridge", "2026-11-13"),
        batch("m-frozen", "milk", 1_000, "freezer", "2027-01-01"),
        batch("r1", "rice", 1_000, "pantry", null),
    ).mapIndexed { index, body -> Change("c$index", index.toLong(), "test", body) }
    private val inventory = Inventory.replay(log)

    private var count = 0
    private val newId: () -> String = { "new-${count++}" }

    /** The changes of the action, and the inventory after them. A change that the core rejects fails the test. */
    private fun apply(action: InventoryAction, from: List<Change> = log): Pair<Planned.Changes, List<Change>> {
        val before = Inventory.replay(from)
        val planned = InventoryActions.plan(action, before, today, newId) as Planned.Changes
        val changes = from + NewChanges.stamp(planned.bodies, from, 1_000, "phone", newId)
        assertNull(NewChanges.rejection(before, Inventory.replay(changes)))
        return planned to changes
    }

    private fun quantities(changes: List<Change>) = Inventory.replay(changes).batches.associate { it.id to it.quantity }

    @Test
    fun aNewFoodIsAProductAndABatchThatIsAddedToday() {
        val action = InventoryAction.AddFood(null, "Butter", Category.DAIRY, MeasureUnit.G, "fk-1", 250_000, "fridge", null)
        val (planned, changes) = apply(action)
        assertEquals(2, planned.bodies.size)
        assertEquals(NoticeMessage.Added("Butter"), planned.message)
        val after = Inventory.replay(changes)
        val product = after.products.single { it.name == "Butter" }
        assertEquals(Batch("new-1", product.id, 250_000, "fridge", today, null), after.batches.single { it.productId == product.id })

        // A batch for a product in the inventory does not save the product again.
        val (again, _) = apply(action.copy(productId = "milk", name = "Milk"))
        assertEquals(1, again.bodies.size)
    }

    @Test
    fun aUseTakesFromTheOldestBatchFirst() {
        val fridge = listOf("m-new", "m-old")
        val (planned, changes) = apply(InventoryAction.Take("milk", fridge, 1_500, Outcome.USED))
        assertEquals(mapOf("m-old" to 0L, "m-new" to 1_500L, "m-frozen" to 1_000L, "r1" to 1_000L), quantities(changes))
        assertEquals(NoticeMessage.Taken("Milk", 1_500, MeasureUnit.L, Outcome.USED), planned.message)
        assertEquals(listOf(Outcome.USED, Outcome.USED), Inventory.replay(changes).history.map { it.outcome })

        // The undo of the action undoes each of its changes.
        val added = changes.drop(log.size).map { it.id }
        val (_, undone) = apply(InventoryAction.Undo(added), changes)
        assertEquals(quantities(log), quantities(undone))
        assertEquals(emptyList<Any>(), Inventory.replay(undone).history)
    }

    @Test
    fun aThrowAwayOfOneBatchDoesNotTakeFromTheOthers() {
        val (planned, changes) = apply(InventoryAction.Take("milk", listOf("m-new"), 2_000, Outcome.THROWN_AWAY))
        assertEquals(0L, quantities(changes)["m-new"])
        assertEquals(1_000L, quantities(changes)["m-old"])
        assertEquals(Outcome.THROWN_AWAY, Inventory.replay(changes).history.single().outcome)
        assertTrue(planned.undoable)
    }

    @Test
    fun theCoreGivesTheReasonWhenABatchHasNothingToTake() {
        val (_, ended) = apply(InventoryAction.Take("rice", listOf("r1"), 1_000, Outcome.USED))
        val before = Inventory.replay(ended)
        val planned = InventoryActions.plan(InventoryAction.Take("rice", listOf("r1"), 1_000, Outcome.USED), before, today, newId) as Planned.Changes
        val after = Inventory.replay(ended + NewChanges.stamp(planned.bodies, ended, 2_000, "phone", newId))
        assertEquals(RejectionReason.BATCH_ENDED, NewChanges.rejection(before, after))
        assertEquals(
            Planned.Refused(RejectionReason.UNKNOWN_BATCH),
            InventoryActions.plan(InventoryAction.Take("rice", emptyList(), 1_000, Outcome.USED), inventory, today, newId),
        )
    }

    @Test
    fun aDeleteOfSomeBatchesKeepsTheProductAndADeleteOfAllBatchesDeletesIt() {
        val (some, afterSome) = apply(InventoryAction.DeleteBatches("milk", listOf("m-new", "m-old")))
        assertEquals(NoticeMessage.BatchDeleted("Milk", 2), some.message)
        assertEquals(setOf("m-frozen", "r1"), quantities(afterSome).keys)
        assertTrue(Inventory.replay(afterSome).products.any { it.id == "milk" })

        val (all, afterAll) = apply(InventoryAction.DeleteBatches("rice", listOf("r1")))
        assertEquals(listOf(ChangeBody.ProductDeleted("rice")), all.bodies)
        assertEquals(NoticeMessage.FoodDeleted("Rice"), all.message)
        assertEquals(listOf("milk"), Inventory.replay(afterAll).products.map { it.id })
    }

    @Test
    fun aStorageAreaWithFoodCannotBeDeleted() {
        val planned = InventoryActions.plan(InventoryAction.DeleteStorageArea("pantry"), inventory, today, newId) as Planned.Changes
        val after = Inventory.replay(log + NewChanges.stamp(planned.bodies, log, 1_000, "phone", newId))
        assertEquals(RejectionReason.STORAGE_AREA_IN_USE, NewChanges.rejection(inventory, after))

        // After the food is used, the delete applies, and the person can undo it.
        val (_, used) = apply(InventoryAction.Take("rice", listOf("r1"), 1_000, Outcome.USED))
        val (deleted, changes) = apply(InventoryAction.DeleteStorageArea("pantry"), used)
        assertEquals(NoticeMessage.StorageAreaDeleted("Pantry"), deleted.message)
        assertEquals(listOf("freezer", "fridge"), Inventory.replay(changes).storageAreas.map { it.id })
    }

    @Test
    fun aNewStorageAreaGetsARandomIdentifierAndAnEditKeepsIt() {
        val (_, added) = apply(InventoryAction.SaveStorageArea(null, "Cellar", StorageKind.PANTRY))
        assertEquals("Cellar", Inventory.replay(added).storageAreas.single { it.id == "new-0" }.name)
        val (_, renamed) = apply(InventoryAction.SaveStorageArea("pantry", "Larder", StorageKind.FRIDGE), added)
        val pantry = Inventory.replay(renamed).storageAreas.single { it.id == "pantry" }
        assertEquals("Larder" to StorageKind.FRIDGE, pantry.name to pantry.kind)
    }

    @Test
    fun theSourceOfATakeStartsWithTheOldestBatch() {
        val source = checkNotNull(TakeSource.of(inventory, TakeRequest("milk", listOf("m-new", "m-old"), Outcome.USED)))
        assertEquals(TakeSource("Milk", MeasureUnit.L, firstQuantity = 1_000, available = 3_000), source)
        assertEquals(TakeCheck.Quantity(3_000), source.check("3"))
        assertEquals(TakeCheck.TooMuch, source.check("3.001"))
        assertEquals(TakeCheck.Problem(TypedQuantity.Error.NOT_POSITIVE), source.check("0"))
        assertNull(TakeSource.of(inventory, TakeRequest("milk", listOf("gone"), Outcome.USED)))
    }
}
