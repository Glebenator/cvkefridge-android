package com.cvke.fridge.core

/**
 * Where an expiry date comes from.
 *
 * The iOS file `CVKEFridgeCore/Sources/CVKEFridgeCore/Batch.swift` must give the same results for the same input.
 *
 * @property key The text of the value in the shared specification.
 */
enum class ExpirySource(val key: String) {
    /** The date on the package, which the person entered. */
    PRINTED("printed"),

    /** The date from the table of foods. */
    ESTIMATED("estimated"),

    /** A later date that the person selected because the food is still good. */
    CHOSEN("chosen");

    companion object {
        fun fromKey(key: String): ExpirySource? = entries.firstOrNull { it.key == key }
    }
}

/** An expiry date with its source. A batch has both or none. */
data class ExpiryDate(val date: CalendarDate, val source: ExpirySource)

/**
 * An amount of a product that the user bought at one time.
 *
 * @property quantity A whole number of thousandths. 1500 is 1.5. A batch with quantity 0 has ended.
 * @property expiry The expiry date, or null.
 */
data class Batch(
    val id: String,
    val productId: String,
    val quantity: Long,
    val storageAreaId: String,
    val dateAdded: CalendarDate,
    val expiry: ExpiryDate?,
) {
    /** True when the quantity is above 0. */
    val isActive: Boolean
        get() = quantity > 0
}
