package com.cvke.fridge.core

/**
 * One entry of the change log. The inventory is the result of the log (see [Inventory.replay]).
 *
 * The iOS file `CVKEFridgeCore/Sources/CVKEFridgeCore/Change.swift` must give the same results for the same input.
 *
 * @property id The identifier of the change.
 * @property at The instant of the change, in milliseconds from 1970-01-01T00:00:00Z.
 * @property origin The identifier of the phone that made the change.
 */
data class Change(val id: String, val at: Long, val origin: String, val body: ChangeBody)

/** What a change does. The set of types is closed. */
sealed interface ChangeBody {
    /** Adds a product, or replaces the product with the same identifier. */
    data class ProductSaved(val product: Product) : ChangeBody

    /** Removes a product, its batches, and the history of those batches. */
    data class ProductDeleted(val productId: String) : ChangeBody

    /** Adds a storage area, or replaces the one with the same identifier. */
    data class StorageAreaSaved(val id: String, val name: String, val kind: StorageKind) : ChangeBody

    /** Removes a storage area. */
    data class StorageAreaDeleted(val storageAreaId: String) : ChangeBody

    /** Adds a batch. */
    data class BatchAdded(val batch: Batch) : ChangeBody

    /** Replaces the values of a batch. It makes no history entry. */
    data class BatchEdited(
        val batchId: String,
        val quantity: Long,
        val storageAreaId: String,
        val dateAdded: CalendarDate,
        val expiry: ExpiryDate?,
    ) : ChangeBody

    /** Takes a quantity from a batch, as used. */
    data class BatchUsed(val batchId: String, val quantity: Long, val date: CalendarDate) : ChangeBody

    /** Takes a quantity from a batch, as thrown away. */
    data class BatchThrownAway(val batchId: String, val quantity: Long, val date: CalendarDate) : ChangeBody

    /** Removes a batch and its history. It is for a mistake. */
    data class BatchDeleted(val batchId: String) : ChangeBody

    /** Undoes another change. */
    data class ChangeUndone(val changeId: String) : ChangeBody
}
