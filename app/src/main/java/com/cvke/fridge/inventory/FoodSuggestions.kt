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

    /** The largest number of foods with the same name in one list of suggestions, for example two kinds of ham. */
    private const val MAX_SAME_NAME = 2

    /** The shortest text that can agree with a word that has one wrong letter. */
    private const val MIN_TYPO_LENGTH = 4

    private val notWord = Regex("[^\\p{L}\\p{N}]+")

    private fun words(text: String): List<String> = text.lowercase().split(notWord).filter { it.isNotEmpty() }

    /**
     * How well the [text] agrees with a name, its aliases, and its keywords. A smaller number is better, and null is no
     * agreement:
     * 0. the name or an alias is the text;
     * 1. the name or an alias starts with the text;
     * 2. a word of the name or of an alias starts with the text;
     * 3. a keyword starts with the text;
     * 4. a word of the name, of an alias, or of a keyword is the text, or starts with it, with one letter that is wrong,
     *    added, missing, or in the wrong order. Only for a text of 4 or more characters.
     */
    private fun rank(text: String, name: String, aliases: List<String>, keywords: List<String>): Int? {
        val names = listOf(name.lowercase()) + aliases
        val nameWords = names.flatMap(::words)
        return when {
            names.any { it == text } -> 0
            names.any { it.startsWith(text) } -> 1
            nameWords.any { it.startsWith(text) } -> 2
            keywords.any { it.lowercase().startsWith(text) } -> 3
            text.length >= MIN_TYPO_LENGTH && ' ' !in text &&
                (nameWords + keywords.flatMap(::words)).any { closeTo(text, it) } -> 4
            else -> null
        }
    }

    /** True when the [word], or its start, has one change from the [text]. */
    private fun closeTo(text: String, word: String): Boolean =
        changes(text, word) <= 1 || (word.length > text.length && changes(text, word.take(text.length)) <= 1)

    /** The number of letters to add, remove, change, or swap with the next letter to make [b] from [a]. */
    internal fun changes(a: String, b: String): Int {
        if (kotlin.math.abs(a.length - b.length) > 1) return 2
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
            }
        }
        return d[a.length][b.length]
    }

    /**
     * The suggestions for the [query]: first the products, then the foods of the table. A better agreement ([rank])
     * comes first. With the same agreement, a food of a product that the person had comes first, then a common food,
     * and then the order of the table. Not more than 2 foods have the same name. The result is empty for a text of
     * fewer than 2 characters.
     */
    fun find(query: String, products: List<Product>, foods: FoodTable, limit: Int = 4): List<Suggestion> {
        val text = Names.clean(query).lowercase()
        if (text.length < 2) return emptyList()

        val foundProducts = products
            .mapNotNull { product -> rank(text, product.name, emptyList(), emptyList())?.let { it to product } }
            .sortedWith(
                compareBy<Pair<Int, Product>> { it.first }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.second.name }
                    .thenBy { it.second.id },
            )
            .take(MAX_PRODUCTS)
            .map { Suggestion.OfProduct(it.second) }
        // A food that a suggested product already has is not in the list a second time.
        val taken = foundProducts.mapNotNullTo(HashSet()) { it.product.foodId }
        val known = products.mapNotNullTo(HashSet()) { it.foodId }
        val perName = HashMap<String, Int>()
        val foundFoods = foods.foods
            .filter { it.id !in taken }
            .mapNotNull { food -> rank(text, food.name, food.aliases, food.keywords)?.let { it to food } }
            .sortedWith(compareBy({ it.first }, { it.second.id !in known }, { !it.second.common }))
            .filter { (_, food) ->
                val key = food.name.lowercase()
                val count = perName.getOrDefault(key, 0)
                perName[key] = count + 1
                count < MAX_SAME_NAME
            }
            .take(limit - foundProducts.size)
            .map { Suggestion.OfFood(it.second) }
        return foundProducts + foundFoods
    }

    /**
     * The food with this name, when exactly one food of the table has it as its name or as an alias. Case has no
     * effect. "Yoghurt" gets the food "Yogurt".
     */
    fun onlyFoodNamed(name: String, foods: FoodTable): Food? {
        val text = Names.clean(name).lowercase()
        return foods.foods.filter { Names.same(it.name, name) || text in it.aliases }.singleOrNull()
    }

    /**
     * True when the [name] can be a name for the [food]: a word of the name is a word of the name of the food, of an
     * alias, or of a keyword. "Cheddar" fits the food "Cheese" with the keyword "cheddar". "Bread" does not fit "Milk".
     */
    fun fits(food: Food, name: String): Boolean {
        val known = (words(food.name) + food.aliases.flatMap(::words) + food.keywords.flatMap(::words)).toSet()
        return words(name).any { it in known }
    }
}
