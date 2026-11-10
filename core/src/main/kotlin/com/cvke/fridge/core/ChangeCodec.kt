package com.cvke.fridge.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Reads and writes a [Change] as JSON, in the shape of the `changes` in `spec/vectors/change-log.json`.
 *
 * The iOS file `CVKEFridgeCore/Sources/CVKEFridgeCore/ChangeCodec.swift` must read the same JSON. The two apps do
 * not need to write the same bytes.
 *
 * A key with the value null must be in the JSON. A batch and a `batchEdited` body have `expiryDate` and
 * `expirySource`: both are null, or both have a value.
 */
object ChangeCodec {
    /**
     * The change in the JSON text.
     *
     * @throws IllegalArgumentException for an unknown type, a bad date, an unknown value of an enum, only one of the
     * two expiry fields, a missing key, a value of the wrong JSON type, or text that is not JSON.
     */
    fun decode(json: String): Change {
        val root = Json.parseToJsonElement(json).asObject("The change")
        return Change(
            id = root.text("id"),
            at = root.long("at"),
            origin = root.text("origin"),
            body = decodeBody(root.obj("body")),
        )
    }

    /** The JSON text of the change. [decode] gives the same change back. */
    fun encode(change: Change): String = buildJsonObject {
        put("id", change.id)
        put("at", change.at)
        put("origin", change.origin)
        put("body", encodeBody(change.body))
    }.toString()

    private fun decodeBody(body: JsonObject): ChangeBody = when (val type = body.text("type")) {
        "productSaved" -> ChangeBody.ProductSaved(decodeProduct(body.obj("product")))
        "productDeleted" -> ChangeBody.ProductDeleted(body.text("productId"))
        "storageAreaSaved" -> body.obj("area").let {
            ChangeBody.StorageAreaSaved(it.text("id"), it.text("name"), it.enum("kind", StorageKind::fromKey))
        }
        "storageAreaDeleted" -> ChangeBody.StorageAreaDeleted(body.text("storageAreaId"))
        "batchAdded" -> ChangeBody.BatchAdded(decodeBatch(body.obj("batch")))
        "batchEdited" -> ChangeBody.BatchEdited(
            batchId = body.text("batchId"),
            quantity = body.long("quantity"),
            storageAreaId = body.text("storageAreaId"),
            dateAdded = body.date("dateAdded"),
            expiry = body.expiry(),
        )
        "batchUsed" -> ChangeBody.BatchUsed(body.text("batchId"), body.long("quantity"), body.date("date"))
        "batchThrownAway" -> ChangeBody.BatchThrownAway(body.text("batchId"), body.long("quantity"), body.date("date"))
        "batchDeleted" -> ChangeBody.BatchDeleted(body.text("batchId"))
        "changeUndone" -> ChangeBody.ChangeUndone(body.text("changeId"))
        else -> fail("Unknown change type: $type")
    }

    private fun decodeProduct(product: JsonObject) = Product(
        id = product.text("id"),
        name = product.text("name"),
        category = product.enum("category", Category::fromKey),
        unit = product.enumOrNull("unit", MeasureUnit::fromKey),
        barcode = product.textOrNull("barcode"),
        foodId = product.textOrNull("foodId"),
    )

    private fun decodeBatch(batch: JsonObject) = Batch(
        id = batch.text("id"),
        productId = batch.text("productId"),
        quantity = batch.long("quantity"),
        storageAreaId = batch.text("storageAreaId"),
        dateAdded = batch.date("dateAdded"),
        expiry = batch.expiry(),
    )

    private fun encodeBody(body: ChangeBody): JsonObject = buildJsonObject {
        when (body) {
            is ChangeBody.ProductSaved -> {
                put("type", "productSaved")
                put("product", buildJsonObject {
                    put("id", body.product.id)
                    put("name", body.product.name)
                    put("category", body.product.category.key)
                    put("unit", body.product.unit?.key)
                    put("barcode", body.product.barcode)
                    put("foodId", body.product.foodId)
                })
            }
            is ChangeBody.ProductDeleted -> {
                put("type", "productDeleted")
                put("productId", body.productId)
            }
            is ChangeBody.StorageAreaSaved -> {
                put("type", "storageAreaSaved")
                put("area", buildJsonObject {
                    put("id", body.id)
                    put("name", body.name)
                    put("kind", body.kind.key)
                })
            }
            is ChangeBody.StorageAreaDeleted -> {
                put("type", "storageAreaDeleted")
                put("storageAreaId", body.storageAreaId)
            }
            is ChangeBody.BatchAdded -> {
                put("type", "batchAdded")
                put("batch", buildJsonObject {
                    put("id", body.batch.id)
                    put("productId", body.batch.productId)
                    put("quantity", body.batch.quantity)
                    put("storageAreaId", body.batch.storageAreaId)
                    put("dateAdded", body.batch.dateAdded.text)
                    put("expiryDate", body.batch.expiry?.date?.text)
                    put("expirySource", body.batch.expiry?.source?.key)
                })
            }
            is ChangeBody.BatchEdited -> {
                put("type", "batchEdited")
                put("batchId", body.batchId)
                put("quantity", body.quantity)
                put("storageAreaId", body.storageAreaId)
                put("dateAdded", body.dateAdded.text)
                put("expiryDate", body.expiry?.date?.text)
                put("expirySource", body.expiry?.source?.key)
            }
            is ChangeBody.BatchUsed -> {
                put("type", "batchUsed")
                put("batchId", body.batchId)
                put("quantity", body.quantity)
                put("date", body.date.text)
            }
            is ChangeBody.BatchThrownAway -> {
                put("type", "batchThrownAway")
                put("batchId", body.batchId)
                put("quantity", body.quantity)
                put("date", body.date.text)
            }
            is ChangeBody.BatchDeleted -> {
                put("type", "batchDeleted")
                put("batchId", body.batchId)
            }
            is ChangeBody.ChangeUndone -> {
                put("type", "changeUndone")
                put("changeId", body.changeId)
            }
        }
    }

    private fun fail(message: String): Nothing = throw IllegalArgumentException(message)

    private fun JsonElement.asObject(name: String): JsonObject = this as? JsonObject ?: fail("$name is not an object")

    private fun JsonObject.value(key: String): JsonElement = this[key] ?: fail("The key $key is missing")

    private fun JsonObject.obj(key: String): JsonObject = value(key).asObject(key)

    private fun JsonObject.text(key: String): String = textOrNull(key) ?: fail("$key is null")

    private fun JsonObject.textOrNull(key: String): String? = when (val element = value(key)) {
        JsonNull -> null
        is JsonPrimitive -> if (element.isString) element.content else fail("$key is not text")
        else -> fail("$key is not text")
    }

    private fun JsonObject.long(key: String): Long {
        val element = value(key) as? JsonPrimitive
        return element?.takeUnless { it.isString }?.longOrNull ?: fail("$key is not a whole number")
    }

    private fun JsonObject.date(key: String): CalendarDate = dateOrNull(key) ?: fail("$key is null")

    private fun JsonObject.dateOrNull(key: String): CalendarDate? =
        textOrNull(key)?.let { CalendarDate.parse(it) ?: fail("$key is not a date: $it") }

    private fun <T> JsonObject.enum(key: String, fromKey: (String) -> T?): T = enumOrNull(key, fromKey) ?: fail("$key is null")

    private fun <T> JsonObject.enumOrNull(key: String, fromKey: (String) -> T?): T? =
        textOrNull(key)?.let { fromKey(it) ?: fail("Unknown value of $key: $it") }

    private fun JsonObject.expiry(): ExpiryDate? {
        val date = dateOrNull("expiryDate")
        val source = enumOrNull("expirySource", ExpirySource::fromKey)
        if (date == null && source == null) return null
        if (date == null || source == null) fail("expiryDate and expirySource must both have a value, or both be null")
        return ExpiryDate(date, source)
    }
}
