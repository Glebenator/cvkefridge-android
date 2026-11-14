package com.cvke.fridge.inventory

import com.cvke.fridge.core.Batch
import com.cvke.fridge.core.CalendarDate
import com.cvke.fridge.core.Category
import com.cvke.fridge.core.ExpiryDate
import com.cvke.fridge.core.FoodTable
import com.cvke.fridge.core.MeasureUnit
import com.cvke.fridge.core.Product
import com.cvke.fridge.core.StorageArea

/**
 * What the person typed and selected in the form that edits a product.
 *
 * @property note The text of the note field. An empty text or only spaces saves no note.
 * @property checked True after the person selected "Save" one time. The form then shows the problem of the name.
 */
data class ProductForm(
    val name: String,
    val category: Category,
    val unit: MeasureUnit?,
    val note: String = "",
    val checked: Boolean = false,
) {
    /** The problem of the name. Two products cannot have the same name, because the add form finds a product by its name. */
    fun nameError(product: Product, products: List<Product>): NameError? {
        val cleaned = Names.clean(name)
        return when {
            cleaned.isEmpty() -> NameError.EMPTY
            cleaned.length > Names.MAX_FOOD_LENGTH -> NameError.TOO_LONG
            products.any { it.id != product.id && Names.same(it.name, cleaned) } -> NameError.IN_USE
            else -> null
        }
    }

    /**
     * The action that saves the form, or null when the name has a problem. The product keeps its food of the table
     * of foods while the new name fits that food. If not, a name that only one food has gets that food.
     */
    fun action(product: Product, products: List<Product>, foods: FoodTable): InventoryAction.SaveProduct? {
        if (nameError(product, products) != null) return null
        val cleaned = Names.clean(name)
        val kept = product.foodId?.let(foods::food)?.takeIf { Names.same(product.name, cleaned) || FoodSuggestions.fits(it, cleaned) }
        val foodId = if (kept != null) kept.id else FoodSuggestions.onlyFoodNamed(cleaned, foods)?.id
        return InventoryAction.SaveProduct(
            product.copy(name = cleaned, category = category, unit = unit, foodId = foodId, note = Notes.clean(note)),
        )
    }

    companion object {
        fun of(product: Product) = ProductForm(product.name, product.category, product.unit, product.note.orEmpty())
    }
}

/**
 * What the person typed and selected in the form that edits a batch.
 *
 * @property quantity The text of the quantity field.
 * @property checked True after the person selected "Save" one time. The form then shows the problem of each field.
 */
data class BatchForm(
    val quantity: String,
    val storageAreaId: String,
    val dateAdded: CalendarDate,
    val expiry: ExpiryInput,
    val checked: Boolean = false,
) {
    fun quantityError(): TypedQuantity.Error? = QuantityText.parse(quantity) as? TypedQuantity.Error

    /**
     * The estimated date that the form can use. A change of the storage area or of the date added gives a new
     * estimate (see [ExpiryChoice.suggestionForEdit]).
     */
    fun suggested(batch: Batch, product: Product, areas: List<StorageArea>, foods: FoodTable): ExpiryDate? {
        val kind = areas.firstOrNull { it.id == storageAreaId }?.kind
        val estimate = if (kind == null) null else product.foodId?.let { foods.estimate(it, kind, dateAdded) }
        return ExpiryChoice.suggestionForEdit(batch, storageAreaId, dateAdded, estimate)
    }

    /** The action that saves the form, or null when a field has a problem. */
    fun action(batch: Batch, product: Product, areas: List<StorageArea>, foods: FoodTable): InventoryAction.EditBatch? {
        val typed = QuantityText.parse(quantity) as? TypedQuantity.Value ?: return null
        if (areas.none { it.id == storageAreaId }) return null
        return InventoryAction.EditBatch(
            batchId = batch.id,
            quantity = typed.thousandths,
            storageAreaId = storageAreaId,
            dateAdded = dateAdded,
            expiry = ExpiryChoice.expiry(expiry, suggested(batch, product, areas, foods)),
        )
    }

    companion object {
        /** The form that shows the batch as it is. */
        fun of(batch: Batch, decimalSeparator: Char) = BatchForm(
            quantity = QuantityText.format(batch.quantity, decimalSeparator),
            storageAreaId = batch.storageAreaId,
            dateAdded = batch.dateAdded,
            expiry = ExpiryInput.of(batch.expiry),
        )
    }
}
