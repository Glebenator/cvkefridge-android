package com.cvke.fridge.inventory

import com.cvke.fridge.core.Category
import com.cvke.fridge.core.Food
import com.cvke.fridge.core.FoodTable
import com.cvke.fridge.core.Product
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The suggestions of the add form: aliases, common foods, the foods of the person, and a wrong letter. */
class FoodSuggestionsTest {
    /** The real table of foods, `spec/foods.json`. The unit tests run in the folder of the `app` module. */
    private val table = FoodTable.decode(File("../spec/foods.json").readText())

    private fun ids(query: String, products: List<Product> = emptyList(), foods: FoodTable = table): List<String> =
        FoodSuggestions.find(query, products, foods).map {
            when (it) {
                is Suggestion.OfProduct -> it.product.id
                is Suggestion.OfFood -> it.food.id
            }
        }

    @Test
    fun anAliasFindsTheFood() {
        assertEquals("fk-33", ids("yoghurt").first())
        assertEquals("fk-43", ids("mince").first())
        assertEquals("fk-547", ids("courgette").first())
        assertEquals("fk-151", ids("prawns").first())
        assertTrue("fk-146" in ids("salmon"))
    }

    @Test
    fun aCommonFoodComesBeforeTheOtherFoodsAndOnlyTwoFoodsHaveTheSameName() {
        // Plain milk, not evaporated milk.
        assertEquals("fk-27", ids("milk").first())
        // Ham from the deli, not canned ham. The table has 21 kinds of ham.
        val ham = ids("ham")
        assertEquals("fk-515", ham.first())
        assertEquals(2, ham.count { id -> table.food(id)?.name == "Ham" })
        assertEquals("fk-21", ids("eggs").first())
    }

    @Test
    fun aFoodThatThePersonHadComesBeforeACommonFood() {
        // The person had a product that was not cheddar, with the food of soft cheese.
        val brie = Product("p-brie", "Brie de Meaux", Category.DAIRY, null, barcode = null, foodId = "fk-7")
        assertEquals(listOf("fk-7", "fk-3"), ids("chee", listOf(brie)).take(2))
    }

    @Test
    fun aNameWithOneWrongLetterFindsTheFoodAfterTheFoodsWithNoWrongLetter() {
        assertEquals("Chicken", table.food(ids("chiken").first())?.name)
        assertEquals("fk-481", ids("strawbery").first())
        assertTrue(ids("brocoli").contains("fk-276"))
        // Fewer than 4 characters: no wrong letter is permitted.
        assertEquals(emptyList<String>(), ids("xgg"))
    }

    @Test
    fun theChangesCountAddedMissingChangedAndSwappedLetters() {
        assertEquals(1, FoodSuggestions.changes("chiken", "chicken"))
        assertEquals(1, FoodSuggestions.changes("mlik", "milk"))
        assertEquals(1, FoodSuggestions.changes("bread", "breed"))
        assertEquals(2, FoodSuggestions.changes("bread", "brand"))
    }

    @Test
    fun anAliasConnectsATypedNameToTheFood() {
        assertEquals("fk-33", FoodSuggestions.onlyFoodNamed("Yoghurt", table)?.id)
        val yogurt = checkNotNull(table.food("fk-33"))
        assertTrue(FoodSuggestions.fits(yogurt, "Greek yoghurt"))
        assertFalse(FoodSuggestions.fits(yogurt, "Bread"))
    }

    @Test
    fun theOrderOfTheTableStaysWhenNothingElseIsDifferent() {
        val a = Food("a", "Beans", null, Category.OTHER, emptyList(), emptyMap())
        val b = Food("b", "Beans", "dried", Category.OTHER, emptyList(), emptyMap(), common = true)
        val c = Food("c", "Beetroot", null, Category.PRODUCE, emptyList(), emptyMap())
        assertEquals(listOf("b", "a", "c"), ids("be", foods = FoodTable(listOf(a, b, c))))
    }
}
