package com.cvke.fridge.inventory

import com.cvke.fridge.core.Batch
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The forms that edit a product and a batch. */
class EditFormsTest {
    private val areas = listOf(StorageArea.fridge, StorageArea.freezer)
    private val butterFood = Food("f-butter", "Butter", null, Category.DAIRY, listOf("butter"), mapOf(StorageKind.FRIDGE to 30, StorageKind.FREEZER to 180))
    private val foods = FoodTable(listOf(butterFood))
    private val butter = Product("p-butter", "Butter", Category.DAIRY, MeasureUnit.G, "4000000000001", "f-butter")
    private val milk = Product("p-milk", "Whole milk", Category.DAIRY, MeasureUnit.L, null, null)
    private val products = listOf(butter, milk)

    private fun date(text: String) = checkNotNull(CalendarDate.parse(text))

    private fun batch(expiry: ExpiryDate?) = Batch("b1", "p-butter", 250_000, "fridge", date("2026-11-01"), expiry)

    @Test
    fun aProductKeepsItsOtherValuesAndItsFoodWhileTheNameFits() {
        val saved = checkNotNull(ProductForm(" Salted  butter ", Category.OTHER, MeasureUnit.PACK).action(butter, products, foods)).product
        assertEquals(butter.copy(name = "Salted butter", category = Category.OTHER, unit = MeasureUnit.PACK), saved)
        assertNull(ProductForm("Margarine", Category.DAIRY, MeasureUnit.G).action(butter, products, foods)?.product?.foodId)
        assertEquals("f-butter", ProductForm("butter", Category.DAIRY, null).action(milk, listOf(milk), foods)?.product?.foodId)
    }

    @Test
    fun aProductCannotGetTheNameOfAnotherProduct() {
        assertEquals(NameError.IN_USE, ProductForm("whole MILK", Category.DAIRY, null).nameError(butter, products))
        assertNull(ProductForm("BUTTER", Category.DAIRY, null).nameError(butter, products))
        assertEquals(NameError.EMPTY, ProductForm(" ", Category.DAIRY, null).nameError(butter, products))
        assertNull(ProductForm("Whole milk", Category.DAIRY, null).action(butter, products, foods))
    }

    @Test
    fun anEstimatedDateFollowsTheStorageAreaAndTheDateAdded() {
        // The estimated date of the batch is not the date of the table. It stays while the two values stay.
        val original = batch(ExpiryDate(date("2026-11-20"), ExpirySource.ESTIMATED))
        val form = BatchForm.of(original, '.')
        assertEquals("250", form.quantity)
        assertEquals(original.expiry, form.copy(quantity = "100").action(original, butter, areas, foods)?.expiry)

        val frozen = form.copy(storageAreaId = "freezer").action(original, butter, areas, foods)
        assertEquals(ExpiryDate(date("2027-04-30"), ExpirySource.ESTIMATED), frozen?.expiry)
        val later = form.copy(dateAdded = date("2026-11-05")).action(original, butter, areas, foods)
        assertEquals(ExpiryDate(date("2026-12-05"), ExpirySource.ESTIMATED), later?.expiry)
        // The product has no food, so there is no new estimate. The estimated date of the batch stays.
        assertEquals(original.expiry, form.copy(storageAreaId = "freezer").action(original, milk, areas, foods)?.expiry)
    }

    @Test
    fun aPrintedDateAndNoExpiryDateStay() {
        val printed = batch(ExpiryDate(date("2026-11-20"), ExpirySource.PRINTED))
        assertEquals(printed.expiry, BatchForm.of(printed, '.').copy(storageAreaId = "freezer").action(printed, butter, areas, foods)?.expiry)

        val none = batch(null)
        val form = BatchForm.of(none, '.')
        assertNull(form.copy(storageAreaId = "freezer").action(none, butter, areas, foods)?.expiry)
        // The person can select the estimated date for a batch that has no expiry date.
        assertEquals(
            ExpiryDate(date("2026-12-01"), ExpirySource.ESTIMATED),
            form.copy(expiry = ExpiryInput.Suggested).action(none, butter, areas, foods)?.expiry,
        )
    }

    @Test
    fun aBatchFormWithAProblemSavesNothing() {
        val original = batch(null)
        assertNull(BatchForm.of(original, ',').copy(quantity = "").action(original, butter, areas, foods))
        assertNull(BatchForm.of(original, ',').copy(storageAreaId = "gone").action(original, butter, areas, foods))
        assertEquals(1_500L, BatchForm.of(original, ',').copy(quantity = "1,5").action(original, butter, areas, foods)?.quantity)
    }
}
