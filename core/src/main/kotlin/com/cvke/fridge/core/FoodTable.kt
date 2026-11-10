package com.cvke.fridge.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * A food of the table of foods.
 *
 * @property subtitle More text for the name, or null.
 * @property days The days that the food keeps, for each storage kind where it can be kept.
 */
data class Food(
    val id: String,
    val name: String,
    val subtitle: String?,
    val category: Category,
    val keywords: List<String>,
    val days: Map<StorageKind, Int>,
)

/**
 * The table of foods, and the expiry estimate from it.
 *
 * The iOS file `CVKEFridgeCore/Sources/CVKEFridgeCore/FoodTable.swift` must give the same results for the same input.
 */
data class FoodTable(val foods: List<Food>) {
    private val byId: Map<String, Food> = buildMap { foods.forEach { putIfAbsent(it.id, it) } }

    /** The food with this identifier. If two foods have it, the first one. */
    fun food(id: String): Food? = byId[id]

    /**
     * The estimated expiry date: [dateAdded] plus the days of the food for [kind]. It is null when the food is not in
     * the table, or has no days for [kind]. The category has no effect.
     */
    fun estimate(foodId: String, kind: StorageKind, dateAdded: CalendarDate): ExpiryDate? {
        val days = food(foodId)?.days?.get(kind) ?: return null
        return dateAdded.plusDays(days)?.let { ExpiryDate(it, ExpirySource.ESTIMATED) }
    }

    companion object {
        /**
         * Reads the format of `spec/foods.json`: an object with a `foods` array. Other top-level keys have no effect.
         * `subtitle` and `keywords` can be absent. A `days` key that is not a storage kind is an error.
         *
         * @throws IllegalArgumentException when the text is not in this format.
         */
        fun decode(json: String): FoodTable {
            val root = Json.parseToJsonElement(json) as? JsonObject ?: fail("The table is not an object")
            val list = root["foods"] as? JsonArray ?: fail("The table has no foods array")
            return FoodTable(list.map(::decodeFood))
        }

        private fun decodeFood(element: JsonElement): Food {
            val food = element as? JsonObject ?: fail("A food is not an object")
            val days = food["days"] as? JsonObject ?: fail("A food has no days object")
            val subtitle = food["subtitle"]
            val keywords = food["keywords"]
            return Food(
                id = text(food["id"], "id"),
                name = text(food["name"], "name"),
                subtitle = if (subtitle == null || subtitle is JsonNull) null else text(subtitle, "subtitle"),
                category = Category.fromKey(text(food["category"], "category")) ?: fail("Unknown category"),
                keywords = when (keywords) {
                    null -> emptyList()
                    is JsonArray -> keywords.map { text(it, "keyword") }
                    else -> fail("keywords is not an array")
                },
                days = days.entries.associate { (key, value) ->
                    val kind = StorageKind.fromKey(key) ?: fail("Unknown storage kind: $key")
                    val count = (value as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
                        ?: fail("The days for $key are not a whole number")
                    kind to count
                },
            )
        }

        private fun text(element: JsonElement?, name: String): String {
            val primitive = element as? JsonPrimitive
            if (primitive == null || !primitive.isString) fail("$name is not text")
            return primitive.content
        }

        private fun fail(message: String): Nothing = throw IllegalArgumentException(message)
    }
}
