package com.cvke.fridge.inventory

import com.cvke.fridge.core.CalendarDate
import com.cvke.fridge.core.Category
import com.cvke.fridge.core.ExpiryDate
import com.cvke.fridge.core.Food
import com.cvke.fridge.core.FoodTable
import com.cvke.fridge.core.MeasureUnit
import com.cvke.fridge.core.Product
import com.cvke.fridge.core.StorageArea

/** Why a name in a form is not good. */
enum class NameError { EMPTY, TOO_LONG, IN_USE }

/**
 * What the person typed and selected in the form that adds food. The form is one value, and each change of a field
 * gives a new value.
 *
 * @property foodId The food of the table of foods that gives the expiry estimate, or null.
 * @property categoryChosen True after the person selected the category. A food then does not change it.
 * @property quantity The text of the quantity field.
 * @property checked True after the person selected "Save" one time. The form then shows the problem of each field.
 */
data class AddFoodForm(
    val name: String = "",
    val foodId: String? = null,
    val category: Category = Category.OTHER,
    val categoryChosen: Boolean = false,
    val quantity: String = DEFAULT_QUANTITY,
    val unit: MeasureUnit? = MeasureUnit.PCS,
    val storageAreaId: String = StorageArea.fridge.id,
    val expiry: ExpiryInput = ExpiryInput.Suggested,
    val checked: Boolean = false,
) {
    /** The product in the inventory with this name. The form then adds a batch to it, and makes no second product. */
    fun product(products: List<Product>): Product? {
        val cleaned = Names.clean(name)
        return if (cleaned.isEmpty()) null else products.firstOrNull { Names.same(it.name, cleaned) }
    }

    /** The food that gives the expiry estimate: the food of the product in the inventory, or the food of the form. */
    fun food(products: List<Product>, foods: FoodTable): Food? = (product(products)?.let { it.foodId } ?: foodId)?.let(foods::food)

    /**
     * The form after the person typed in the name field. The food of the form stays while the name fits it. A name
     * that only one food of the table has gets that food. A food gives its category, if the person did not select one.
     */
    fun withName(text: String, foods: FoodTable): AddFoodForm {
        val kept = foodId?.let(foods::food)?.takeIf { FoodSuggestions.fits(it, text) }
        val food = kept ?: FoodSuggestions.onlyFoodNamed(text, foods)
        return copy(name = text, foodId = food?.id, category = if (categoryChosen || food == null) category else food.category)
    }

    /** The form after the person selected a suggestion. */
    fun withSuggestion(suggestion: Suggestion): AddFoodForm = when (suggestion) {
        is Suggestion.OfProduct -> copy(name = suggestion.product.name, foodId = suggestion.product.foodId)
        is Suggestion.OfFood -> copy(
            name = suggestion.food.name,
            foodId = suggestion.food.id,
            category = if (categoryChosen) category else suggestion.food.category,
        )
    }

    /** The suggestions for the name. A name that is complete has none: the name of a product, or of the food of the form. */
    fun suggestions(products: List<Product>, foods: FoodTable): List<Suggestion> {
        if (product(products) != null) return emptyList()
        if (foodId?.let(foods::food)?.let { Names.same(it.name, name) } == true) return emptyList()
        return FoodSuggestions.find(name, products, foods)
    }

    /** The estimated date for the food in the storage area, for a batch that is added [today]. */
    fun estimate(products: List<Product>, areas: List<StorageArea>, foods: FoodTable, today: CalendarDate): ExpiryDate? {
        val food = food(products, foods) ?: return null
        val area = areas.firstOrNull { it.id == storageAreaId } ?: return null
        return foods.estimate(food.id, area.kind, today)
    }

    fun nameError(): NameError? = when {
        Names.clean(name).isEmpty() -> NameError.EMPTY
        Names.clean(name).length > Names.MAX_FOOD_LENGTH -> NameError.TOO_LONG
        else -> null
    }

    fun quantityError(): TypedQuantity.Error? = QuantityText.parse(quantity) as? TypedQuantity.Error

    /** The action that saves the form, or null when a field has a problem. */
    fun action(products: List<Product>, areas: List<StorageArea>, foods: FoodTable, today: CalendarDate): InventoryAction.AddFood? {
        val typed = QuantityText.parse(quantity) as? TypedQuantity.Value ?: return null
        if (nameError() != null || areas.none { it.id == storageAreaId }) return null
        val existing = product(products)
        return InventoryAction.AddFood(
            productId = existing?.id,
            name = existing?.name ?: Names.clean(name),
            category = existing?.category ?: category,
            unit = if (existing != null) existing.unit else unit,
            foodId = if (existing != null) existing.foodId else foodId,
            quantity = typed.thousandths,
            storageAreaId = storageAreaId,
            expiry = ExpiryChoice.expiry(expiry, estimate(products, areas, foods, today)),
        )
    }

    /** The empty form for the next food. It keeps the storage area. */
    fun next(): AddFoodForm = AddFoodForm(storageAreaId = storageAreaId)

    companion object {
        const val DEFAULT_QUANTITY = "1"

        /**
         * The storage area of a new form: the storage area of the last batch that the person added, then the storage
         * area of the tab that was open, and then the Fridge. A storage area that is not there has no effect.
         */
        fun startArea(lastUsedId: String?, openTabId: String?, areas: List<StorageArea>): String =
            listOfNotNull(lastUsedId, openTabId).firstOrNull { id -> areas.any { it.id == id } } ?: StorageArea.fridge.id
    }
}
