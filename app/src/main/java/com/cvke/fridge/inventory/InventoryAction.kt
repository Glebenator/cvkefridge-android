package com.cvke.fridge.inventory

import com.cvke.fridge.core.Batch
import com.cvke.fridge.core.CalendarDate
import com.cvke.fridge.core.Category
import com.cvke.fridge.core.ChangeBody
import com.cvke.fridge.core.ExpiryDate
import com.cvke.fridge.core.Inventory
import com.cvke.fridge.core.MeasureUnit
import com.cvke.fridge.core.Outcome
import com.cvke.fridge.core.Product
import com.cvke.fridge.core.RejectionReason
import com.cvke.fridge.core.StorageKind
import com.cvke.fridge.core.UseFirst
import com.cvke.fridge.lookup.Barcodes

/** One thing that the person does to the inventory. [InventoryActions.plan] makes its changes. */
sealed interface InventoryAction {
    /**
     * Adds a batch. With no [productId], it also adds the product.
     *
     * @property quantity In thousandths.
     * @property barcode The barcode of a scan, or null. See [InventoryActions.plan] for the product that gets it.
     */
    data class AddFood(
        val productId: String?,
        val name: String,
        val category: Category,
        val unit: MeasureUnit?,
        val foodId: String?,
        val quantity: Long,
        val storageAreaId: String,
        val expiry: ExpiryDate?,
        val barcode: String? = null,
    ) : InventoryAction

    /** Replaces the values of a product. */
    data class SaveProduct(val product: Product) : InventoryAction

    /** Replaces the values of a batch. */
    data class EditBatch(
        val batchId: String,
        val quantity: Long,
        val storageAreaId: String,
        val dateAdded: CalendarDate,
        val expiry: ExpiryDate?,
    ) : InventoryAction

    /**
     * Records a quantity of a product as used or as thrown away. The quantity comes from the batches with the
     * [batchIds], oldest batch first.
     */
    data class Take(val productId: String, val batchIds: List<String>, val quantity: Long, val outcome: Outcome) : InventoryAction

    /**
     * Deletes batches that were a mistake. When the product has no other batch, active or ended, the product was the
     * mistake, and the action deletes the product.
     */
    data class DeleteBatches(val productId: String, val batchIds: List<String>) : InventoryAction

    /** Deletes a product that was a mistake, with its batches and their history. */
    data class DeleteProduct(val productId: String) : InventoryAction

    /** Adds a storage area, or replaces the name and the storage kind of the storage area with the [id]. */
    data class SaveStorageArea(val id: String?, val name: String, val kind: StorageKind) : InventoryAction

    data class DeleteStorageArea(val id: String) : InventoryAction

    /** Undoes the changes of an earlier action. */
    data class Undo(val changeIds: List<String>) : InventoryAction
}

/** What the app tells the person after an action. The screen makes the text. */
sealed interface NoticeMessage {
    data class Added(val name: String) : NoticeMessage

    /** [quantity] is the quantity that the batches gave, in thousandths. */
    data class Taken(val name: String, val quantity: Long, val unit: MeasureUnit?, val outcome: Outcome) : NoticeMessage

    data class FoodDeleted(val name: String) : NoticeMessage

    /** [count] is the number of batches. */
    data class BatchDeleted(val name: String, val count: Int) : NoticeMessage

    data class StorageAreaDeleted(val name: String) : NoticeMessage

    /** The change log could not be written. */
    data object SaveFailed : NoticeMessage

    /** The inventory changed, and the action is not possible now. */
    data class Refused(val reason: RejectionReason) : NoticeMessage
}

/** The result of [InventoryActions.plan]. */
sealed interface Planned {
    /**
     * The changes of the action, in their order.
     *
     * @property message What to tell the person when the changes are in the log, or null for nothing.
     * @property undoable True when the person can undo the action from the message.
     */
    data class Changes(val bodies: List<ChangeBody>, val message: NoticeMessage? = null, val undoable: Boolean = false) : Planned

    /** The action has nothing to change. */
    data class Refused(val reason: RejectionReason) : Planned
}

/** Makes the changes of an action. The replay of the core decides if the changes can apply. */
object InventoryActions {
    /**
     * The changes for the [action] on the [inventory].
     *
     * @param today The date in the time zone of the device.
     * @param newId Makes a random identifier.
     */
    fun plan(action: InventoryAction, inventory: Inventory, today: CalendarDate, newId: () -> String): Planned = when (action) {
        is InventoryAction.AddFood -> addFood(action, inventory, today, newId)
        is InventoryAction.SaveProduct -> Planned.Changes(listOf(ChangeBody.ProductSaved(action.product)))
        is InventoryAction.EditBatch -> Planned.Changes(
            listOf(ChangeBody.BatchEdited(action.batchId, action.quantity, action.storageAreaId, action.dateAdded, action.expiry)),
        )
        is InventoryAction.Take -> take(action, inventory, today)
        is InventoryAction.DeleteBatches -> deleteBatches(action, inventory)
        is InventoryAction.DeleteProduct -> {
            val name = inventory.products.firstOrNull { it.id == action.productId }?.name.orEmpty()
            Planned.Changes(listOf(ChangeBody.ProductDeleted(action.productId)), NoticeMessage.FoodDeleted(name), undoable = true)
        }
        is InventoryAction.SaveStorageArea ->
            Planned.Changes(listOf(ChangeBody.StorageAreaSaved(action.id ?: newId(), action.name, action.kind)))
        is InventoryAction.DeleteStorageArea -> {
            val name = inventory.storageAreas.firstOrNull { it.id == action.id }?.name.orEmpty()
            Planned.Changes(
                listOf(ChangeBody.StorageAreaDeleted(action.id)),
                NoticeMessage.StorageAreaDeleted(name),
                undoable = true,
            )
        }
        is InventoryAction.Undo ->
            if (action.changeIds.isEmpty()) {
                Planned.Refused(RejectionReason.UNKNOWN_CHANGE)
            } else {
                Planned.Changes(action.changeIds.map(ChangeBody::ChangeUndone))
            }
    }

    /**
     * A barcode belongs to one product. A new product gets the barcode of the action. A product in the inventory with
     * no barcode gets it also, so that the next scan finds it. The barcode is not saved when another product has it:
     * the scan of that barcode then finds the other product, as before.
     */
    private fun addFood(action: InventoryAction.AddFood, inventory: Inventory, today: CalendarDate, newId: () -> String): Planned {
        val productId = action.productId ?: newId()
        val barcode = action.barcode?.takeIf { code ->
            inventory.products.none { it.id != productId && it.barcode?.let(Barcodes::normalize) == code }
        }
        val existing = action.productId?.let { id -> inventory.products.firstOrNull { it.id == id } }
        val saved = when {
            action.productId == null -> Product(productId, action.name, action.category, action.unit, barcode, action.foodId)
            existing != null && existing.barcode == null && barcode != null -> existing.copy(barcode = barcode)
            else -> null
        }
        val batch = Batch(newId(), productId, action.quantity, action.storageAreaId, today, action.expiry)
        return Planned.Changes(
            bodies = listOfNotNull(saved?.let(ChangeBody::ProductSaved), ChangeBody.BatchAdded(batch)),
            message = NoticeMessage.Added(action.name),
            undoable = true,
        )
    }

    private fun take(action: InventoryAction.Take, inventory: Inventory, today: CalendarDate): Planned {
        val product = inventory.products.firstOrNull { it.id == action.productId }
            ?: return Planned.Refused(RejectionReason.UNKNOWN_PRODUCT)
        val first = action.batchIds.firstOrNull() ?: return Planned.Refused(RejectionReason.UNKNOWN_BATCH)
        val plan = UseFirst.plan(inventory.batches.filter { it.id in action.batchIds }, action.productId, action.quantity)
        // With an empty plan, the replay gives the reason: the batch is not there, it has ended, or the quantity is 0.
        val takes = plan.takes.map { it.batchId to it.quantity }.ifEmpty { listOf(first to action.quantity) }
        val bodies = takes.map { (batchId, quantity) ->
            when (action.outcome) {
                Outcome.USED -> ChangeBody.BatchUsed(batchId, quantity, today)
                Outcome.THROWN_AWAY -> ChangeBody.BatchThrownAway(batchId, quantity, today)
            }
        }
        val taken = plan.takes.sumOf { it.quantity }
        return Planned.Changes(bodies, NoticeMessage.Taken(product.name, taken, product.unit, action.outcome), undoable = true)
    }

    private fun deleteBatches(action: InventoryAction.DeleteBatches, inventory: Inventory): Planned {
        if (action.batchIds.isEmpty()) return Planned.Refused(RejectionReason.UNKNOWN_BATCH)
        val name = inventory.products.firstOrNull { it.id == action.productId }?.name.orEmpty()
        val others = inventory.batches.any { it.productId == action.productId && it.id !in action.batchIds }
        return if (others) {
            Planned.Changes(action.batchIds.map(ChangeBody::BatchDeleted), NoticeMessage.BatchDeleted(name, action.batchIds.size), undoable = true)
        } else {
            Planned.Changes(listOf(ChangeBody.ProductDeleted(action.productId)), NoticeMessage.FoodDeleted(name), undoable = true)
        }
    }
}
