package com.cvke.fridge.inventory

import com.cvke.fridge.core.CalendarDate
import com.cvke.fridge.core.Category
import com.cvke.fridge.core.ExpiryDate
import com.cvke.fridge.core.ExpirySource
import com.cvke.fridge.core.Food
import com.cvke.fridge.core.FoodTable
import com.cvke.fridge.core.MeasureUnit
import com.cvke.fridge.core.Product
import com.cvke.fridge.core.StorageArea
import com.cvke.fridge.core.StorageKind
import com.cvke.fridge.lookup.ScanSource
import com.cvke.fridge.lookup.Scanned
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The form that adds food: the food of a name, the estimated date, and what the form saves. */
class AddFoodFormTest {
    private val today = date("2026-11-12")
    private val pantry = StorageArea("pantry-1", "Pantry", StorageKind.PANTRY, fixed = false)
    private val areas = listOf(StorageArea.fridge, StorageArea.freezer, pantry)

    private val butter = Food("f-butter", "Butter", null, Category.DAIRY, listOf("butter"), mapOf(StorageKind.FRIDGE to 30, StorageKind.FREEZER to 180), StorageKind.FRIDGE)
    private val hardCheese = Food("f-hard", "Cheese", "hard such as cheddar", Category.DAIRY, listOf("cheese", "cheddar"), mapOf(StorageKind.FRIDGE to 180))
    private val softCheese = Food("f-soft", "Cheese", "soft such as brie", Category.DAIRY, listOf("cheese", "brie"), mapOf(StorageKind.FRIDGE to 7))
    private val peanutButter = Food("f-peanut", "Peanut butter", null, Category.CONDIMENT, listOf("peanut butter"), mapOf(StorageKind.PANTRY to 180), StorageKind.PANTRY)
    private val foods = FoodTable(listOf(butter, hardCheese, softCheese, peanutButter))

    private val milk = Product("p-milk", "Whole milk", Category.DAIRY, MeasureUnit.L, null, null)
    private val products = listOf(milk)

    private fun date(text: String) = checkNotNull(CalendarDate.parse(text))

    private fun estimated(text: String) = ExpiryDate(date(text), ExpirySource.ESTIMATED)

    @Test
    fun aNameOfOneFoodGetsThatFoodAndItsCategory() {
        val form = AddFoodForm().withName("butter", foods, areas)
        assertEquals("f-butter", form.foodId)
        assertEquals(Category.DAIRY, form.category)
        // The date is the estimate for the Fridge. It is not today.
        assertEquals(estimated("2026-12-12"), form.estimate(products, areas, foods, today))
    }

    @Test
    fun aNameOfMoreThanOneFoodGetsAFoodOnlyFromASuggestion() {
        val typed = AddFoodForm().withName("Cheese", foods, areas)
        assertNull(typed.foodId)
        assertNull(typed.estimate(products, areas, foods, today))
        assertEquals(listOf("f-hard", "f-soft"), typed.suggestions(products, foods).map { (it as Suggestion.OfFood).food.id })

        val selected = typed.withSuggestion(Suggestion.OfFood(softCheese), foods, areas)
        assertEquals("Cheese", selected.name)
        assertEquals(estimated("2026-11-19"), selected.estimate(products, areas, foods, today))
        // The name is complete, so the list of suggestions closes.
        assertEquals(emptyList<Suggestion>(), selected.suggestions(products, foods))
    }

    @Test
    fun theFoodStaysWhileTheNameFitsIt() {
        val selected = AddFoodForm().withSuggestion(Suggestion.OfFood(hardCheese), foods, areas)
        assertEquals("f-hard", selected.withName("Cheddar", foods, areas).foodId)
        assertNull(selected.withName("Bread", foods, areas).foodId)
        assertNull(selected.withName("", foods, areas).foodId)
    }

    @Test
    fun aCategoryThatThePersonSelectedStays() {
        val form = AddFoodForm(category = Category.LEFTOVERS, categoryChosen = true).withName("Butter", foods, areas)
        assertEquals(Category.LEFTOVERS, form.category)
    }

    @Test
    fun aChangeOfTheStorageAreaGivesANewEstimateAndKeepsAPrintedDate() {
        val form = AddFoodForm().withName("Butter", foods, areas)
        val frozen = form.copy(storageAreaId = "freezer")
        assertEquals(estimated("2027-05-11"), ExpiryChoice.expiry(frozen.expiry, frozen.estimate(products, areas, foods, today)))
        // The table has no days for butter in a pantry.
        assertNull(form.copy(storageAreaId = "pantry-1").estimate(products, areas, foods, today))

        val printed = form.copy(expiry = ExpiryInput.Printed(date("2026-11-20"))).copy(storageAreaId = "freezer")
        assertEquals(
            ExpiryDate(date("2026-11-20"), ExpirySource.PRINTED),
            printed.action(products, areas, foods, today)?.expiry,
        )
    }

    @Test
    fun theNameOfAProductInTheInventoryAddsABatchToIt() {
        val form = AddFoodForm(quantity = "1,5", unit = MeasureUnit.PCS, category = Category.OTHER).withName("  whole   MILK ", foods, areas)
        val action = checkNotNull(form.action(products, areas, foods, today))
        assertEquals("p-milk", action.productId)
        assertEquals("Whole milk", action.name)
        assertEquals(MeasureUnit.L, action.unit)
        assertEquals(Category.DAIRY, action.category)
        assertEquals(1_500L, action.quantity)
        assertEquals(emptyList<Suggestion>(), form.suggestions(products, foods))
    }

    @Test
    fun aNewFoodSavesTheCleanNameAndTheValuesOfTheForm() {
        val form = AddFoodForm(quantity = "250", unit = MeasureUnit.G, storageAreaId = "pantry-1").withName(" Peanut  butter ", foods, areas)
        val action = checkNotNull(form.action(products, areas, foods, today))
        assertNull(action.productId)
        assertEquals("Peanut butter", action.name)
        assertEquals("f-peanut", action.foodId)
        assertEquals(Category.CONDIMENT, action.category)
        assertEquals(estimated("2027-05-11"), action.expiry)
        assertNull(form.copy(expiry = ExpiryInput.None).action(products, areas, foods, today)?.expiry)
    }

    @Test
    fun aFormWithAProblemSavesNothing() {
        assertEquals(NameError.EMPTY, AddFoodForm(name = "  ").nameError())
        assertEquals(NameError.TOO_LONG, AddFoodForm(name = "a".repeat(81)).nameError())
        assertNull(AddFoodForm(name = " ").action(products, areas, foods, today))
        assertNull(AddFoodForm(name = "Rice", quantity = "0").action(products, areas, foods, today))
        assertNull(AddFoodForm(name = "Rice", storageAreaId = "gone").action(products, areas, foods, today))
    }

    @Test
    fun theNextFormKeepsOnlyTheStorageArea() {
        val form = AddFoodForm(quantity = "3", checked = true).withName("Butter", foods, areas).withArea("freezer")
        assertEquals(AddFoodForm(storageAreaId = "freezer"), form.next())
    }

    @Test
    fun theFirstStorageAreaIsTheLastOneUsedThenTheOpenTabThenTheFridge() {
        assertEquals("pantry-1", AddFoodForm.startArea("pantry-1", "freezer", areas))
        assertEquals("freezer", AddFoodForm.startArea(null, "freezer", areas))
        assertEquals("freezer", AddFoodForm.startArea("deleted", "freezer", areas))
        assertEquals("fridge", AddFoodForm.startArea(null, null, areas))
    }

    @Test
    fun aFoodSelectsAnAreaOfItsUsualStorageKindAndANameWithNoFoodGoesBackToTheStartArea() {
        // The last batch went to the freezer. Butter goes to the fridge, and peanut butter to the pantry.
        val start = AddFoodForm(storageAreaId = "freezer")
        val withButter = start.withName("Butter", foods, areas)
        assertEquals("fridge", withButter.storageAreaId)
        assertEquals("pantry-1", start.withSuggestion(Suggestion.OfFood(peanutButter), foods, areas).storageAreaId)
        assertEquals("freezer", withButter.withName("Bread", foods, areas).storageAreaId)
        // Cheese has no usual storage kind.
        assertEquals("freezer", start.withSuggestion(Suggestion.OfFood(softCheese), foods, areas).storageAreaId)
    }

    @Test
    fun theStartAreaStaysWhenItHasTheUsualKindAndAKindWithNoAreaKeepsTheStartArea() {
        val garage = StorageArea("garage", "Garage fridge", StorageKind.FRIDGE, fixed = false)
        val withGarage = areas + garage
        assertEquals("garage", AddFoodForm(storageAreaId = "garage").withName("Butter", foods, withGarage).storageAreaId)
        assertEquals("fridge", AddFoodForm(storageAreaId = "freezer").withName("Butter", foods, withGarage).storageAreaId)
        // The fixed area of a kind comes before an area that the person made.
        assertEquals("fridge", AddFoodForm.areaOfKind(StorageKind.FRIDGE, "pantry-1", listOf(garage, StorageArea.fridge)))
        // No pantry: peanut butter stays in the start area.
        val noPantry = listOf(StorageArea.fridge, StorageArea.freezer)
        assertEquals("freezer", AddFoodForm(storageAreaId = "freezer").withName("Peanut butter", foods, noPantry).storageAreaId)
    }

    @Test
    fun anAreaThatThePersonSelectedStaysWhenTheFoodChanges() {
        val form = AddFoodForm().withArea("freezer").withName("Peanut butter", foods, areas)
        assertEquals("freezer", form.storageAreaId)
        assertEquals("freezer", form.withName("Butter", foods, areas).storageAreaId)
    }

    @Test
    fun aScanWithAFoodGetsTheAreaOfItsUsualStorageKind() {
        val scan = Scanned("1", ScanSource.FOUND, "Skippy", "condiment", "f-peanut")
        assertEquals("pantry-1", AddFoodForm.scanned(scan, "fridge", '.').withUsualArea(foods, areas).storageAreaId)
    }

    @Test
    fun aScanFillsInTheFormAndTheBarcodeGoesIntoTheAction() {
        val scan = Scanned("3017620422003", ScanSource.FOUND, "Nutella", "condiment", null, 400_000, "g")
        val form = AddFoodForm.scanned(scan, "pantry-1", ',')
        assertEquals(
            AddFoodForm(
                name = "Nutella",
                category = Category.CONDIMENT,
                quantity = "400",
                unit = MeasureUnit.G,
                storageAreaId = "pantry-1",
                barcode = "3017620422003",
            ),
            form,
        )
        assertEquals("3017620422003", form.action(products, areas, foods, today)?.barcode)
        // The quantity has the decimal sign of the locale.
        assertEquals("1,5", AddFoodForm.scanned(scan.copy(quantity = 1_500, unit = "l"), "fridge", ',').quantity)
        // The next form has no barcode.
        assertNull(form.next().barcode)
    }

    @Test
    fun aScanWithNoValuesKeepsTheStartValuesAndTheBarcode() {
        val notFound = AddFoodForm.scanned(Scanned("5449000000996", ScanSource.NOT_FOUND), "fridge", '.')
        assertEquals(AddFoodForm(storageAreaId = "fridge", barcode = "5449000000996"), notFound)
        // A quantity with no unit is not reliable.
        assertEquals("1", AddFoodForm.scanned(Scanned("1", ScanSource.FOUND, "Rice", quantity = 500_000), "fridge", '.').quantity)
    }

    @Test
    fun theFoodOfAScanGivesTheEstimatedDate() {
        val scanned = AddFoodForm.scanned(Scanned("1", ScanSource.FOUND, "Président Butter", "dairy", "f-butter"), "fridge", '.')
        assertEquals(estimated("2026-12-12"), scanned.estimate(products, areas, foods, today))
        assertEquals(estimated("2026-12-12"), scanned.action(products, areas, foods, today)?.expiry)
    }

    @Test
    fun suggestionsHaveProductsFirstAndThenFoodsByHowWellTheNameAgrees() {
        val found = FoodSuggestions.find("bu", listOf(Product("p-buns", "Burger buns", Category.BAKERY, null, null, null)), foods)
        assertEquals(
            listOf("Burger buns", "Butter", "Peanut butter"),
            found.map { if (it is Suggestion.OfProduct) it.product.name else (it as Suggestion.OfFood).food.name },
        )
        assertEquals(emptyList<Suggestion>(), FoodSuggestions.find("b", products, foods))
    }
}
