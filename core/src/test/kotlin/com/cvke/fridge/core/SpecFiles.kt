package com.cvke.fridge.core

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/** Finds the `spec/` folder of the repository and loads the vector files in it. */
internal object SpecFiles {
    private fun fail(message: String): Nothing = throw AssertionError(message)

    private val dir: File by lazy {
        val path = System.getProperty("spec.dir") ?: error("The system property spec.dir is not set")
        File(path).also { check(it.isDirectory) { "The spec folder is missing: $it" } }
    }

    /** The text of a file in `spec/`, for example `foods.json`. */
    fun text(path: String): String = File(dir, path).also { check(it.isFile) { "The file is missing: $it" } }.readText()

    /** The cases of a vector file. [section] is null for a file with one list `cases`. The list cannot be empty. */
    fun cases(file: String, section: String? = "cases"): List<JsonObject> {
        val root = Json.parseToJsonElement(text("vectors/$file")).jsonObject
        val list = root[section ?: "cases"] as? JsonArray ?: fail("$file has no section $section")
        if (list.isEmpty()) fail("$file section $section has no cases")
        return list.map { it.jsonObject }
    }

    /** Fails when [actual] is not the complete `expect` value of the [case]. */
    fun check(file: String, case: JsonObject, actual: JsonElement) {
        val expected = case.getValue("expect")
        if (expected != actual) {
            val label = case["name"]?.jsonPrimitive?.content ?: JsonObject(case - "expect").toString()
            fail("$file, case $label\n  expected: $expected\n  actual:   $actual")
        }
    }

    fun json(vararg pairs: Pair<String, Any?>): JsonObject = JsonObject(pairs.associate { it.first to element(it.second) })

    fun element(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Enum<*> -> error("Use the key of the enum")
        is List<*> -> JsonArray(value.map(::element))
        else -> error("No JSON for $value")
    }

    fun JsonObject.str(key: String): String = getValue(key).jsonPrimitive.content

    fun JsonObject.strOrNull(key: String): String? = (getValue(key) as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

    fun JsonObject.long(key: String): Long = getValue(key).jsonPrimitive.long

    fun JsonObject.list(key: String): List<JsonObject> = getValue(key).jsonArray.map { it.jsonObject }

    fun dateOf(text: String): CalendarDate = CalendarDate.parse(text) ?: fail("Not a date: $text")

    fun JsonObject.dateAt(key: String): CalendarDate = dateOf(str(key))

    /** A batch in the form of the vector files. */
    fun JsonObject.batch(): Batch {
        val expiryDate = strOrNull("expiryDate")
        val expirySource = strOrNull("expirySource")
        return Batch(
            id = str("id"),
            productId = str("productId"),
            quantity = long("quantity"),
            storageAreaId = str("storageAreaId"),
            dateAdded = dateOf(str("dateAdded")),
            expiry = if (expiryDate == null) null else ExpiryDate(dateOf(expiryDate), ExpirySource.fromKey(expirySource!!)!!),
        )
    }
}
