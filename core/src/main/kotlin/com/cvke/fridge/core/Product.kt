package com.cvke.fridge.core

/**
 * The category of a product.
 *
 * The iOS file `CVKEFridgeCore/Sources/CVKEFridgeCore/Product.swift` must give the same results for the same input.
 *
 * @property key The text of the value in the shared specification.
 */
enum class Category(val key: String) {
    PRODUCE("produce"),
    DAIRY("dairy"),
    MEAT("meat"),
    CONDIMENT("condiment"),
    BEVERAGE("beverage"),
    BAKERY("bakery"),
    LEFTOVERS("leftovers"),
    OTHER("other");

    companion object {
        fun fromKey(key: String): Category? = entries.firstOrNull { it.key == key }
    }
}

/**
 * The unit of a product. The glossary calls it "unit". The code name is different to avoid the Kotlin type `Unit`.
 *
 * @property key The text of the value in the shared specification.
 */
enum class MeasureUnit(val key: String) {
    PCS("pcs"),
    G("g"),
    KG("kg"),
    ML("ml"),
    L("l"),
    PACK("pack"),
    BOTTLE("bottle"),
    SLICE("slice"),
    CUP("cup");

    companion object {
        fun fromKey(key: String): MeasureUnit? = entries.firstOrNull { it.key == key }
    }
}

/**
 * A product. All batches of a product have the same unit.
 *
 * @property unit The unit, or null for none.
 * @property barcode The barcode text, or null.
 * @property foodId The identifier of the food in the table of foods, or null.
 */
data class Product(
    val id: String,
    val name: String,
    val category: Category,
    val unit: MeasureUnit?,
    val barcode: String?,
    val foodId: String?,
)
