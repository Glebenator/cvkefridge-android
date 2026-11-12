package com.cvke.fridge.inventory

/**
 * The rules for the names that the person types. The core does not check names (ADR 0005 in the cvkefridge
 * repository), so the forms do.
 */
object Names {
    /** The largest number of characters in the name of a food. */
    const val MAX_FOOD_LENGTH = 80

    /** The largest number of characters in the name of a storage area. The name must fit on a tab. */
    const val MAX_AREA_LENGTH = 30

    private val spaces = Regex("\\s+")

    /** The name as the app keeps it: no space at the start or the end, and one space between words. */
    fun clean(text: String): String = text.trim().replace(spaces, " ")

    /** True when the two texts are the same name. Case and extra spaces have no effect. */
    fun same(first: String, second: String): Boolean = clean(first).equals(clean(second), ignoreCase = true)
}
