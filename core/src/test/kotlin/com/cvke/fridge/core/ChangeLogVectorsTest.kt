package com.cvke.fridge.core

import com.cvke.fridge.core.SpecFiles.json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.Test

class ChangeLogVectorsTest {
    private val file = "change-log.json"

    @Test
    fun replay() {
        for (case in SpecFiles.cases(file)) {
            val changes = (case.getValue("changes") as JsonArray).map { ChangeCodec.decode(it.toString()) }
            SpecFiles.check(file, case, inventoryJson(Inventory.replay(changes)))
        }
    }

    private fun inventoryJson(inventory: Inventory): JsonObject = json(
        "products" to inventory.products.map {
            json(
                "id" to it.id, "name" to it.name, "category" to it.category.key, "unit" to it.unit?.key,
                "barcode" to it.barcode, "foodId" to it.foodId, "note" to it.note,
            )
        },
        "storageAreas" to inventory.storageAreas.map {
            json("id" to it.id, "name" to it.name, "kind" to it.kind.key, "fixed" to it.fixed)
        },
        "batches" to inventory.batches.map {
            json(
                "id" to it.id, "productId" to it.productId, "quantity" to it.quantity,
                "storageAreaId" to it.storageAreaId, "dateAdded" to it.dateAdded.text,
                "expiryDate" to it.expiry?.date?.text, "expirySource" to it.expiry?.source?.key,
            )
        },
        "history" to inventory.history.map {
            json(
                "changeId" to it.changeId, "batchId" to it.batchId, "productId" to it.productId,
                "quantity" to it.quantity, "outcome" to it.outcome.key, "date" to it.date.text,
            )
        },
        "rejected" to inventory.rejected.map { json("changeId" to it.changeId, "reason" to it.reason.key) },
    )
}
