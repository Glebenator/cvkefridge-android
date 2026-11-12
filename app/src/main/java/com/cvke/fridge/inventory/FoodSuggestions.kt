package com.cvke.fridge.inventory

import com.cvke.fridge.core.Food
import com.cvke.fridge.core.FoodTable
import com.cvke.fridge.core.Product

/** A name that the add form suggests while the person types. */
sealed interface Suggestion {
    /** A product that is in the inventory. The form then adds a batch to it. */
    data class OfProduct(val product: Product) : Suggestion

    /** A food of the table of foods. The form then has an expiry estimate. */
    data class OfFood(val food: Food) : Suggestion
}

/** Finds products and foods for the text in the name field, and connects a name to a food. */
object FoodSuggestions {
    /** The largest number of products in one list of suggestions. */
    private const val MAX_PRODUCTS = 2

    private val notWord = Regex("[^\\p{L}\\p{N}]+")

    private fun words(text: String): List<String> = text.lowercase().split(notWord).filter { it.isNotEmpty() }

    /**
     * The suggestions for the [query]: first the products, then the foods of the table. A name that starts with the
     * text comes before a name with a word that starts with it, and that comes before a food with such a keyword. The
     * result is empty for a text of fewer than 2 characters.
     */
    fun find(query: String, products: List<Product>, foods: FoodTable, limit: Int = 4): List<Suggestion> {
        val text = Names.clean(query).lowercase()
        if (text.length < 2) return emptyList()

        fun rank(name: String, keywords: List<String>): Int? {
            val lower = name.lowercase()
            return when {
                lower == text -> 0
                lower.startsWith(text) -> 1
                words(lower).any { it.startsWith(text) } -> 2
                keywords.any { it.lowercase().startsWith(text) } -> 3
                else -> null
            }
        }

        val foundProducts = products
            .mapNotNull { product -> rank(product.name, emptyList())?.let { it to product } }
            .sortedWith(
                compareBy<Pair<Int, Product>> { it.first }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.second.name }
                    .thenBy { it.second.id },
            )
            .take(MAX_PRODUCTS)
            .map { Suggestion.OfProduct(it.second) }
        // A food that a suggested product already has is not in the list a second time.
        val taken = foundProducts.mapNotNullTo(HashSet()) { it.product.foodId }
        val foundFoods = foods.foods
            .filter { it.id !in taken }
            .mapNotNull { food -> rank(food.name, food.keywords)?.let { it to food } }
            .sortedBy { it.first }
            .take(limit - foundProducts.size)
            .map { Suggestion.OfFood(it.second) }
        return foundProducts + foundFoods
    }

    /** The food with this name, when exactly one food of the table has it. Case has no effect. */
    fun onlyFoodNamed(name: String, foods: FoodTable): Food? =
        foods.foods.filter { Names.same(it.name, name) }.singleOrNull()

    /**
     * True when the [name] can be a name for the [food]: a word of the name is a word of the name of the food, or one
     * of its keywords. "Cheddar" fits the food "Cheese" with the keyword "cheddar". "Bread" does not fit "Milk".
     */
    fun fits(food: Food, name: String): Boolean {
        val known = (words(food.name) + food.keywords.flatMap(::words)).toSet()
        return words(name).any { it in known }
    }
}
