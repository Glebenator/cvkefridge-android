package com.cvke.fridge.core

import com.cvke.fridge.core.SpecFiles.json
import com.cvke.fridge.core.SpecFiles.str
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodTableVectorsTest {
    private val file = "food-table.json"

    @Test
    fun theRealTableHasTheFactsOfTheVectorFile() {
        val table = FoodTable.decode(SpecFiles.text("foods.json"))
        val expect = Json.parseToJsonElement(SpecFiles.text("vectors/$file")).jsonObject.getValue("expect").jsonObject

        assertEquals(expect.getValue("count"), SpecFiles.element(table.foods.size))
        val withDays = StorageKind.entries.associate { kind -> kind.key to table.foods.count { kind in it.days } }
        assertEquals(expect.getValue("withDays"), SpecFiles.element(JsonObject(withDays.mapValues { SpecFiles.element(it.value) })))
        val usual = StorageKind.entries.associate { kind -> kind.key to table.foods.count { it.usualKind == kind } }
        assertEquals(expect.getValue("withUsualKind"), SpecFiles.element(JsonObject(usual.mapValues { SpecFiles.element(it.value) })))
        assertEquals(expect.getValue("common"), SpecFiles.element(table.foods.count { it.common }))
        assertEquals(expect.getValue("aliases"), SpecFiles.element(table.foods.sumOf { it.aliases.size }))

        val samples = expect.getValue("samples").jsonArray.map { it.jsonObject }
        assertTrue("$file has no samples", samples.isNotEmpty())
        for (sample in samples) {
            val food = table.food(sample.str("foodId"))
            assertNotNull("$file: the food ${sample.str("foodId")} is missing", food)
            val days = JsonObject(food!!.days.entries.associate { it.key.key to SpecFiles.element(it.value) })
            val actual = json(
                "foodId" to food.id,
                "name" to food.name,
                "category" to food.category.key,
                "days" to days,
                "usualKind" to food.usualKind?.key,
                "common" to food.common,
                "aliases" to JsonArray(food.aliases.map { SpecFiles.element(it) }),
            )
            assertEquals("$file, sample ${food.id}", sample, actual)
        }
    }

    @Test
    fun aDaysKeyThatIsNotAStorageKindIsAnError() {
        val text = "{\"foods\":[{\"id\":\"a\",\"name\":\"A\",\"category\":\"dairy\",\"days\":{\"shelf\":3}}]}"
        assertThrows(IllegalArgumentException::class.java) { FoodTable.decode(text) }
    }

    @Test
    fun aUsualKindThatIsNotAStorageKindIsAnError() {
        val text = "{\"foods\":[{\"id\":\"a\",\"name\":\"A\",\"category\":\"dairy\",\"days\":{},\"usualKind\":\"shelf\"}]}"
        assertThrows(IllegalArgumentException::class.java) { FoodTable.decode(text) }
    }

    @Test
    fun subtitleAndKeywordsCanBeAbsent() {
        val text = "{\"other\":1,\"foods\":[{\"id\":\"a\",\"name\":\"A\",\"category\":\"dairy\",\"days\":{\"fridge\":3}}]}"
        val food = FoodTable.decode(text).food("a")
        assertNotNull(food)
        assertNull(food!!.subtitle)
        assertEquals(emptyList<String>(), food.keywords)
        assertNull(food.usualKind)
        assertEquals(false, food.common)
        assertEquals(emptyList<String>(), food.aliases)
    }
}
