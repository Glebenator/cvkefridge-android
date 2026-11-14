package com.cvke.fridge.inventory

import com.cvke.fridge.core.CalendarDate
import com.cvke.fridge.core.Category
import com.cvke.fridge.core.ExpiryDate
import com.cvke.fridge.core.Food
import com.cvke.fridge.core.FoodTable
import com.cvke.fridge.core.MeasureUnit
import com.cvke.fridge.core.Product
import com.cvke.fridge.core.StorageArea
import com.cvke.fridge.core.StorageKind
import com.cvke.fridge.lookup.Scanned

/** Why a name in a form is not good. */
enum class NameError { EMPTY, TOO_LONG, IN_USE }

/**
 * What the person typed and selected in the form that adds food. The form is one value, and each change of a field
 * gives a new value.
 *
 * @property foodId The food of the table of foods that gives the expiry estimate, or null.
 * @property categoryChosen True after the person selected the category. A food then does not change it.
 * @property quantity The text of the quantity field.
 * @property areaKept True when a food does not change the storage area: the person selected it, or it is the storage
 *   area of the newest batch of the product.
 * @property startAreaId The storage area of the form when the food has no usual storage kind.
 * @property checked True after the person selected "Save" one time. The form then shows the problem of each field.
 * @property barcode The barcode of a scan, or null. The form shows it but does not let the person change it.
 * @property note The text of the note field. A product that the person selected from the suggestions, or with "Add
 *   again", puts its note in the field.
 * @property noteEdited True after the person typed in the note field. The form then does not change the note.
 */
data class AddFoodForm(
    val name: String = "",
    val foodId: String? = null,
    val category: Category = Category.OTHER,
    val categoryChosen: Boolean = false,
    val quantity: String = DEFAULT_QUANTITY,
    val unit: MeasureUnit? = MeasureUnit.PCS,
    val storageAreaId: String = StorageArea.fridge.id,
    val areaKept: Boolean = false,
    val startAreaId: String = storageAreaId,
    val expiry: ExpiryInput = ExpiryInput.Suggested,
    val checked: Boolean = false,
    val barcode: String? = null,
    val note: String = "",
    val noteEdited: Boolean = false,
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
     * A change of the food gives the storage area of the food ([withUsualArea]).
     */
    fun withName(text: String, foods: FoodTable, areas: List<StorageArea>): AddFoodForm {
        val kept = foodId?.let(foods::food)?.takeIf { FoodSuggestions.fits(it, text) }
        val food = kept ?: FoodSuggestions.onlyFoodNamed(text, foods)
        // A note that came from a selected product goes away with its name.
        return copy(
            name = text,
            foodId = food?.id,
            category = if (categoryChosen || food == null) category else food.category,
            note = if (noteEdited) note else "",
        ).afterFoodOf(this, foods, areas)
    }

    /** The form after the person selected a suggestion. A change of the food gives the storage area of the food. */
    fun withSuggestion(suggestion: Suggestion, foods: FoodTable, areas: List<StorageArea>): AddFoodForm = when (suggestion) {
        is Suggestion.OfProduct -> copy(
            name = suggestion.product.name,
            foodId = suggestion.product.foodId,
            note = if (noteEdited) note else suggestion.product.note.orEmpty(),
        )
        is Suggestion.OfFood -> copy(
            name = suggestion.food.name,
            foodId = suggestion.food.id,
            category = if (categoryChosen) category else suggestion.food.category,
        )
    }.afterFoodOf(this, foods, areas)

    private fun afterFoodOf(before: AddFoodForm, foods: FoodTable, areas: List<StorageArea>): AddFoodForm =
        if (foodId == before.foodId) this else withUsualArea(foods, areas)

    /**
     * The form with the storage area for its food: an area of the usual storage kind of the food ([areaOfKind]). With
     * no such area, or no usual storage kind, it is the start area. When [areaKept] is true, the form does not change.
     */
    fun withUsualArea(foods: FoodTable, areas: List<StorageArea>): AddFoodForm {
        if (areaKept) return this
        val kind = foodId?.let(foods::food)?.usualKind
        return copy(storageAreaId = kind?.let { areaOfKind(it, startAreaId, areas) } ?: startAreaId)
    }

    /** The form after the person typed in the note field. */
    fun withNote(text: String): AddFoodForm = copy(note = text, noteEdited = true)

    /** The form after the person selected a storage area. A food then does not change it. */
    fun withArea(id: String): AddFoodForm = copy(storageAreaId = id, areaKept = true)

    /**
     * The suggestions for the name. A name that is complete has none: the name of a product, or the name or an alias of
     * the food of the form.
     */
    fun suggestions(products: List<Product>, foods: FoodTable): List<Suggestion> {
        if (product(products) != null) return emptyList()
        val food = foodId?.let(foods::food)
        if (food != null && (Names.same(food.name, name) || Names.clean(name).lowercase() in food.aliases)) return emptyList()
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

    /**
     * The action that saves the form, or null when a field has a problem. The note of a new product is the cleaned
     * text of the note field ([Notes.clean]). For a product in the inventory, a note replaces its note, and an empty
     * note field keeps its note: the add form does not remove a note.
     */
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
            barcode = barcode,
            note = Notes.clean(note),
        )
    }

    /**
     * The form for a product that the person had before: the storage area of its newest batch and its usual quantity,
     * when they are known, and the estimated date. A date of an earlier batch is never used.
     *
     * @param decimalSeparator The decimal sign of the locale, for the quantity text.
     */
    fun again(values: AgainValues, decimalSeparator: Char): AddFoodForm = copy(
        storageAreaId = values.storageAreaId ?: storageAreaId,
        areaKept = areaKept || values.storageAreaId != null,
        quantity = values.quantity?.let { QuantityText.format(it, decimalSeparator) } ?: quantity,
        expiry = ExpiryInput.Suggested,
    )

    /** The empty form for the next food. It keeps the storage area. */
    fun next(): AddFoodForm = AddFoodForm(storageAreaId = storageAreaId)

    companion object {
        const val DEFAULT_QUANTITY = "1"

        /**
         * The form after a scan, with the values of the scan. A value that the scan does not have keeps the start
         * value of the form. The quantity of a package needs its unit. Everything stays editable, and the expiry date
         * starts as the estimated date.
         *
         * @param decimalSeparator The decimal sign of the locale, for the quantity text.
         */
        fun scanned(scan: Scanned, storageAreaId: String, decimalSeparator: Char): AddFoodForm {
            val unit = scan.unit?.let(MeasureUnit::fromKey)
            val pack = scan.quantity?.takeIf { unit != null }
            return AddFoodForm(
                name = scan.name,
                foodId = scan.foodId,
                category = scan.category?.let(Category::fromKey) ?: Category.OTHER,
                quantity = pack?.let { QuantityText.format(it, decimalSeparator) } ?: DEFAULT_QUANTITY,
                unit = if (pack != null) unit else MeasureUnit.PCS,
                storageAreaId = storageAreaId,
                barcode = scan.barcode,
            )
        }

        /**
         * The form that adds a batch to a product that the person had before ("Add again"). [startArea] is the storage
         * area when the product has no known storage area.
         */
        fun again(product: Product, values: AgainValues, startArea: String, decimalSeparator: Char): AddFoodForm =
            AddFoodForm(
                name = product.name,
                foodId = product.foodId,
                category = product.category,
                unit = product.unit,
                storageAreaId = startArea,
                note = product.note.orEmpty(),
            ).again(values, decimalSeparator)

        /**
         * The storage area of a new form: the storage area of the last batch that the person added, then the storage
         * area of the tab that was open, and then the Fridge. A storage area that is not there has no effect.
         */
        fun startArea(lastUsedId: String?, openTabId: String?, areas: List<StorageArea>): String =
            listOfNotNull(lastUsedId, openTabId).firstOrNull { id -> areas.any { it.id == id } } ?: StorageArea.fridge.id

        /**
         * The storage area for a food of the usual storage kind [kind]: the start area if it has that kind, then the
         * fixed area of that kind, and then the first area of that kind. It is null when no area has that kind.
         */
        fun areaOfKind(kind: StorageKind, startAreaId: String, areas: List<StorageArea>): String? {
            val ofKind = areas.filter { it.kind == kind }
            return (ofKind.firstOrNull { it.id == startAreaId } ?: ofKind.firstOrNull { it.fixed } ?: ofKind.firstOrNull())?.id
        }
    }
}
