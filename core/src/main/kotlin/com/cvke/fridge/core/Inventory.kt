package com.cvke.fridge.core

/**
 * What a use did to a batch.
 *
 * @property key The text of the value in the shared specification.
 */
enum class Outcome(val key: String) {
    USED("used"),
    THROWN_AWAY("thrownAway");

    companion object {
        fun fromKey(key: String): Outcome? = entries.firstOrNull { it.key == key }
    }
}

/** One use or throw away of a batch. [quantity] is the quantity that was taken. */
data class HistoryEntry(
    val changeId: String,
    val batchId: String,
    val productId: String,
    val quantity: Long,
    val outcome: Outcome,
    val date: CalendarDate,
)

/**
 * Why a change cannot apply.
 *
 * @property key The text of the value in the shared specification.
 */
enum class RejectionReason(val key: String) {
    UNKNOWN_PRODUCT("unknownProduct"),
    UNKNOWN_STORAGE_AREA("unknownStorageArea"),
    UNKNOWN_BATCH("unknownBatch"),
    UNKNOWN_CHANGE("unknownChange"),
    FIXED_STORAGE_AREA("fixedStorageArea"),
    STORAGE_AREA_IN_USE("storageAreaInUse"),
    INVALID_QUANTITY("invalidQuantity"),
    DUPLICATE_BATCH("duplicateBatch"),
    BATCH_ENDED("batchEnded"),
    CANNOT_UNDO("cannotUndo");

    companion object {
        fun fromKey(key: String): RejectionReason? = entries.firstOrNull { it.key == key }
    }
}

/** A change that could not apply, and the reason. */
data class Rejection(val changeId: String, val reason: RejectionReason)

/**
 * The inventory: the result of the replay of a change log.
 *
 * The iOS file `CVKEFridgeCore/Sources/CVKEFridgeCore/Inventory.swift` must give the same results for the same input.
 *
 * @property products The products, sorted by identifier.
 * @property storageAreas The storage areas, sorted by identifier.
 * @property batches All batches, active and ended, sorted by identifier.
 * @property history The uses and throw aways, in the order of the replay.
 * @property rejected The changes that could not apply, in the order of the replay.
 */
data class Inventory(
    val products: List<Product>,
    val storageAreas: List<StorageArea>,
    val batches: List<Batch>,
    val history: List<HistoryEntry>,
    val rejected: List<Rejection>,
) {
    /** The batches with a quantity above 0. */
    val activeBatches: List<Batch>
        get() = batches.filter { it.isActive }

    companion object {
        /**
         * The inventory that results from the changes (rule 7 of `spec/rules.md`). The changes can be in any order
         * and can contain the same change more than once.
         */
        fun replay(changes: List<Change>): Inventory {
            // 1. and 2. Sort by instant and then identifier. For an identifier that is more than once, keep the first.
            val seen = HashSet<String>()
            val ordered = changes.sortedWith(compareBy<Change> { it.at }.thenBy { it.id }).filter { seen.add(it.id) }
            val byId = ordered.associateBy { it.id }

            // 3. Find the undone changes. The undo changes that cannot apply are rejected when they are reached.
            val undone = HashSet<String>()
            val undoRejections = HashMap<String, RejectionReason>()
            for (change in ordered) {
                val body = change.body as? ChangeBody.ChangeUndone ?: continue
                val target = byId[body.changeId]
                when {
                    target == null -> undoRejections[change.id] = RejectionReason.UNKNOWN_CHANGE
                    target.body is ChangeBody.ChangeUndone -> undoRejections[change.id] = RejectionReason.CANNOT_UNDO
                    else -> undone += target.id
                }
            }

            // 4. and 5. Apply the changes.
            val state = State()
            for (change in ordered) {
                if (change.id in undone) continue
                val reason = state.apply(change, undoRejections[change.id])
                if (reason != null) state.rejected += Rejection(change.id, reason)
            }
            return Inventory(
                products = state.products.sortedByKey { it.id },
                storageAreas = state.areas.sortedByKey { it.id },
                batches = state.batches.sortedByKey { it.id },
                history = state.history,
                rejected = state.rejected,
            )
        }

        private fun <T> Map<String, T>.sortedByKey(id: (T) -> String): List<T> =
            values.sortedWith(compareBy(id))
    }

    private class State {
        val products = HashMap<String, Product>()
        val areas = hashMapOf(
            StorageArea.fridge.id to StorageArea.fridge,
            StorageArea.freezer.id to StorageArea.freezer,
        )
        val batches = HashMap<String, Batch>()
        val history = mutableListOf<HistoryEntry>()
        val rejected = mutableListOf<Rejection>()

        private fun isFixed(id: String) = id == StorageArea.fridge.id || id == StorageArea.freezer.id

        /** Applies one change. The result is the reason when the change cannot apply, or null. */
        fun apply(change: Change, undoRejection: RejectionReason?): RejectionReason? {
            when (val body = change.body) {
                is ChangeBody.ProductSaved -> products[body.product.id] = body.product
                is ChangeBody.ProductDeleted -> {
                    if (products.remove(body.productId) == null) return RejectionReason.UNKNOWN_PRODUCT
                    batches.values.removeAll { it.productId == body.productId }
                    history.removeAll { it.productId == body.productId }
                }
                is ChangeBody.StorageAreaSaved -> {
                    if (isFixed(body.id)) return RejectionReason.FIXED_STORAGE_AREA
                    areas[body.id] = StorageArea(body.id, body.name, body.kind, fixed = false)
                }
                is ChangeBody.StorageAreaDeleted -> {
                    if (isFixed(body.storageAreaId)) return RejectionReason.FIXED_STORAGE_AREA
                    if (body.storageAreaId !in areas) return RejectionReason.UNKNOWN_STORAGE_AREA
                    if (batches.values.any { it.storageAreaId == body.storageAreaId && it.isActive }) {
                        return RejectionReason.STORAGE_AREA_IN_USE
                    }
                    areas.remove(body.storageAreaId)
                }
                is ChangeBody.BatchAdded -> {
                    val batch = body.batch
                    if (batch.id in batches) return RejectionReason.DUPLICATE_BATCH
                    if (batch.productId !in products) return RejectionReason.UNKNOWN_PRODUCT
                    if (batch.storageAreaId !in areas) return RejectionReason.UNKNOWN_STORAGE_AREA
                    if (batch.quantity <= 0) return RejectionReason.INVALID_QUANTITY
                    batches[batch.id] = batch
                }
                is ChangeBody.BatchEdited -> {
                    val batch = batches[body.batchId] ?: return RejectionReason.UNKNOWN_BATCH
                    if (body.storageAreaId !in areas) return RejectionReason.UNKNOWN_STORAGE_AREA
                    if (body.quantity <= 0) return RejectionReason.INVALID_QUANTITY
                    batches[batch.id] = batch.copy(
                        quantity = body.quantity,
                        storageAreaId = body.storageAreaId,
                        dateAdded = body.dateAdded,
                        expiry = body.expiry,
                    )
                }
                is ChangeBody.BatchUsed -> return take(change.id, body.batchId, body.quantity, Outcome.USED, body.date)
                is ChangeBody.BatchThrownAway ->
                    return take(change.id, body.batchId, body.quantity, Outcome.THROWN_AWAY, body.date)
                is ChangeBody.BatchDeleted -> {
                    if (batches.remove(body.batchId) == null) return RejectionReason.UNKNOWN_BATCH
                    history.removeAll { it.batchId == body.batchId }
                }
                is ChangeBody.ChangeUndone -> return undoRejection
            }
            return null
        }

        private fun take(changeId: String, batchId: String, quantity: Long, outcome: Outcome, date: CalendarDate): RejectionReason? {
            val batch = batches[batchId] ?: return RejectionReason.UNKNOWN_BATCH
            if (quantity <= 0) return RejectionReason.INVALID_QUANTITY
            if (batch.quantity == 0L) return RejectionReason.BATCH_ENDED
            val taken = minOf(quantity, batch.quantity)
            batches[batchId] = batch.copy(quantity = batch.quantity - taken)
            history += HistoryEntry(changeId, batchId, batch.productId, taken, outcome, date)
            return null
        }
    }
}
