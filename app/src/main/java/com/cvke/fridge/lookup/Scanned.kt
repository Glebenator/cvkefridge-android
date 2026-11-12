package com.cvke.fridge.lookup

import com.cvke.fridge.core.FoodTable
import com.cvke.fridge.core.Product
import kotlinx.serialization.Serializable

/** Where the values of a scan come from. */
@Serializable
enum class ScanSource {
    /** Open Food Facts had the product. */
    FOUND,

    /** Open Food Facts has no product with a name for the barcode. */
    NOT_FOUND,

    /** The lookup failed, and the person adds the food by hand. The barcode stays. */
    NOT_LOOKED_UP,

    /** A product in the inventory has the barcode. The app did not use the network. */
    IN_INVENTORY,
}

/**
 * What a scan gives the add form. The back stack keeps it, so it has only text and numbers.
 *
 * @property barcode The barcode in the form of [Barcodes].
 * @property name The name for the inventory, or empty.
 * @property category The key of the category, or null for the start value of the form.
 * @property foodId The food of the table of foods that gives the expiry estimate, or null.
 * @property quantity The quantity of the package in thousandths, or null for the start value of the form.
 * @property unit The key of the unit of [quantity], or null.
 */
@Serializable
data class Scanned(
    val barcode: String,
    val source: ScanSource,
    val name: String = "",
    val category: String? = null,
    val foodId: String? = null,
    val quantity: Long? = null,
    val unit: String? = null,
)

/** Makes the values of the add form from a scan. */
object ScannedFoods {
    /**
     * The values for a product that Open Food Facts found: the name ([ProductNames]), the category of the tags
     * ([ProductCategories]), the food that the name clearly names ([FoodMatch]), and the quantity of the package
     * when it is reliable ([PackQuantity]). With no category from the tags, the category of the food, and then none.
     */
    fun found(barcode: String, product: LookedUpProduct, foods: FoodTable): Scanned {
        val tagCategory = ProductCategories.of(product.categories)
        val food = FoodMatch.find(product.name, tagCategory, foods)
        val pack = product.quantity?.let(PackQuantity::parse)
        return Scanned(
            barcode = barcode,
            source = ScanSource.FOUND,
            name = ProductNames.of(product),
            category = (tagCategory ?: food?.category)?.key,
            foodId = food?.id,
            quantity = pack?.first,
            unit = pack?.second?.key,
        )
    }

    /**
     * The product in the inventory with the barcode, or null. A product with an ended batch is also in the inventory:
     * the person can add a new batch to it. A barcode in storage that is not in the form of [Barcodes] is compared in
     * that form.
     */
    fun productWithBarcode(products: List<Product>, barcode: String): Product? =
        products.firstOrNull { product -> product.barcode?.let { (Barcodes.normalize(it) ?: it) == barcode } == true }
}
