package com.cvke.fridge.lookup

import com.cvke.fridge.core.Category
import com.cvke.fridge.core.Food
import com.cvke.fridge.core.FoodTable
import com.cvke.fridge.core.MeasureUnit
import com.cvke.fridge.inventory.Names
import java.math.BigDecimal

// The rules that change a product of Open Food Facts into the values of the add form. The shared specification has
// no rules for this yet. They are in the app as the smallest rules that work, and they must go into the
// specification, so that the iOS app fills in the same values. Each rule compares whole words or whole tags, never a
// part of a word (section 2 of the plan in the cvkefridge repository).

/** The name of a looked-up product for the inventory. */
object ProductNames {
    /**
     * The brand and the product name, for example "Ferrero Nutella". The brand is not added when the name already
     * has the first word of one of the brands ("Coca-Cola Original" from "COCA-COLA SERVICES SA/NV"). A text in
     * capital letters only gets a capital letter at the start of each word. The result has not more than
     * [Names.MAX_FOOD_LENGTH] characters: a longer name loses the brand, and then the words at the end.
     */
    fun of(product: LookedUpProduct): String {
        val name = clean(product.name)
        val brands = product.brands.orEmpty().split(',').map(::clean).filter { it.isNotEmpty() }
        val nameWords = FoodMatch.words(name).toSet()
        val brandInName = brands.any { brand -> FoodMatch.words(brand).firstOrNull()?.let { it in nameWords } == true }
        val withBrand = brands.firstOrNull()?.takeUnless { brandInName }?.let { "$it $name" }
        return shorten(withBrand?.takeIf { it.length <= Names.MAX_FOOD_LENGTH } ?: name)
    }

    private val entities = mapOf("&amp;" to "&", "&quot;" to "\"", "&#39;" to "'", "&apos;" to "'", "&lt;" to "<", "&gt;" to ">")

    private fun clean(text: String): String {
        // A tab or a line break between two words is a space.
        var plain = text.map { if (it.isISOControl()) ' ' else it }.joinToString("")
        entities.forEach { (entity, char) -> plain = plain.replace(entity, char) }
        plain = Names.clean(plain)
        val letters = plain.filter(Char::isLetter)
        return if (letters.length > 3 && letters.none(Char::isLowerCase)) capitalizeWords(plain) else plain
    }

    /** "COCA-COLA ZERO" becomes "Coca-Cola Zero". */
    private fun capitalizeWords(text: String): String = buildString {
        text.forEachIndexed { index, char ->
            val startsWord = index == 0 || !text[index - 1].isLetter()
            append(if (startsWord) char.titlecaseChar() else char.lowercaseChar())
        }
    }

    /** The text cut after the last whole word that fits, or after [Names.MAX_FOOD_LENGTH] characters. */
    private fun shorten(text: String): String {
        if (text.length <= Names.MAX_FOOD_LENGTH) return text
        val cut = text.take(Names.MAX_FOOD_LENGTH + 1).lastIndexOf(' ').takeIf { it > 0 } ?: Names.MAX_FOOD_LENGTH
        return text.take(cut).trimEnd()
    }
}

/**
 * The category of a looked-up product from its category tags. A rule matches a whole tag of the taxonomy of Open Food
 * Facts. The tags of a product include the parent categories, so a few general tags are sufficient. The rules apply
 * in this order, and the first category that matches is the result: dairy, meat, beverage, bakery, condiment,
 * produce. A drinking yoghurt is then dairy, and a fruit juice is a beverage.
 */
object ProductCategories {
    private val rules: List<Pair<Category, Set<String>>> = listOf(
        Category.DAIRY to setOf("en:dairies", "en:eggs"),
        Category.MEAT to setOf("en:meats", "en:poultries", "en:fishes", "en:seafood"),
        Category.BEVERAGE to setOf("en:beverages"),
        Category.BAKERY to setOf("en:breads", "en:biscuits-and-cakes", "en:pastries", "en:viennoiseries"),
        Category.CONDIMENT to setOf("en:condiments", "en:sauces", "en:spreads"),
        Category.PRODUCE to setOf("en:fruits", "en:vegetables", "en:fruits-based-foods", "en:vegetables-based-foods"),
    )

    /** The category of the tags, or null when no rule matches. Case has no effect. */
    fun of(tags: List<String>): Category? {
        val all = tags.mapTo(HashSet()) { it.lowercase() }
        return rules.firstOrNull { (_, ruleTags) -> ruleTags.any { it in all } }?.first
    }
}

/**
 * Finds the food of the table of foods that a product name clearly names, for the expiry estimate.
 *
 * 1. A food is a candidate when each word of its name is a word of the product name. A word with an "s" at the end is
 *    the same as the word without it ("Eggs" and "egg").
 * 2. When the category tags give a category, only the foods of that category are candidates. "Milk chocolate" is
 *    then not milk.
 * 3. The candidates with the most words in their name stay ("Peanut butter" before "Butter").
 * 4. When these candidates have different names, the name is not clear, and there is no food.
 * 5. When they have the same name, the food with the most other words of the product name in its subtitle and its
 *    keywords is the result. With equal numbers, the first food in the table, which is the usual form of the food.
 */
object FoodMatch {
    private val notWord = Regex("[^\\p{L}\\p{N}]+")

    /** The words of a text in lower case. */
    fun words(text: String): List<String> = text.lowercase().split(notWord).filter { it.isNotEmpty() }

    private fun stem(word: String): String = if (word.length > 3 && word.endsWith('s') && !word.endsWith("ss")) word.dropLast(1) else word

    private fun stems(text: String): Set<String> = words(text).mapTo(LinkedHashSet(), ::stem)

    fun find(productName: String, category: Category?, foods: FoodTable): Food? {
        val words = stems(productName)
        if (words.isEmpty()) return null
        val candidates = foods.foods.filter { food ->
            val nameWords = stems(food.name)
            (category == null || food.category == category) && nameWords.isNotEmpty() && words.containsAll(nameWords)
        }
        if (candidates.isEmpty()) return null
        val most = candidates.maxOf { stems(it.name).size }
        val best = candidates.filter { stems(it.name).size == most }
        if (best.map { Names.clean(it.name).lowercase() }.distinct().size > 1) return null
        // maxBy gives the first food with the highest number.
        return best.maxBy { food ->
            val other = (food.keywords + listOfNotNull(food.subtitle)).flatMap(::stems).toSet() - stems(food.name)
            other.count { it in words }
        }
    }
}

/** The quantity of a package, from its text on Open Food Facts. */
object PackQuantity {
    private val pattern = Regex("""^(\d{1,5})(?:[.,](\d{1,2}))?\s*(kg|g|gr|ml|cl|dl|l|lt)\.?(?:\s*[e℮])?$""", RegexOption.IGNORE_CASE)

    /**
     * The quantity in thousandths and the unit, when the text is one number and one unit of weight or volume, for
     * example "400 g", "1,5 L", or "70 cl" (700 ml). Other texts are not reliable, and the result is null: more than
     * one package ("6 x 330 ml"), other units ("12 oz"), and numbers with 3 decimals ("1.000 g" can be 1 g or 1000 g).
     */
    fun parse(text: String): Pair<Long, MeasureUnit>? {
        val match = pattern.matchEntire(text.trim()) ?: return null
        val (whole, fraction, unitText) = match.destructured
        val number = BigDecimal(if (fraction.isEmpty()) whole else "$whole.$fraction")
        val (factor, unit) = when (unitText.lowercase()) {
            "kg" -> 1 to MeasureUnit.KG
            "g", "gr" -> 1 to MeasureUnit.G
            "ml" -> 1 to MeasureUnit.ML
            "cl" -> 10 to MeasureUnit.ML
            "dl" -> 100 to MeasureUnit.ML
            else -> 1 to MeasureUnit.L
        }
        val thousandths = number.multiply(BigDecimal(factor)).movePointRight(3)
        if (thousandths.signum() <= 0 || thousandths > MAX_THOUSANDTHS) return null
        return thousandths.longValueExact() to unit
    }

    /** The largest quantity of a form: 99999.999. */
    private val MAX_THOUSANDTHS = BigDecimal(99_999_999)
}
