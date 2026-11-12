package com.cvke.fridge

import com.cvke.fridge.core.Batch
import com.cvke.fridge.core.CalendarDate
import com.cvke.fridge.core.Category
import com.cvke.fridge.core.Change
import com.cvke.fridge.core.ChangeBody
import com.cvke.fridge.core.ExpiryDate
import com.cvke.fridge.core.ExpirySource
import com.cvke.fridge.core.MeasureUnit
import com.cvke.fridge.core.Product
import com.cvke.fridge.core.StorageArea
import com.cvke.fridge.core.StorageKind
import com.cvke.fridge.ui.FridgeViewModel

/**
 * The sample of the debug build: a change log with food in each freshness, for work on the screens. It is not in the
 * release build. Each date is a number of days from today, so the days left on the screens come from the same
 * calculation as for real food. The changes have the origin [FridgeViewModel.SAMPLE_ORIGIN], and the screens then
 * show that the food is a sample.
 */
object SampleInventory {
    private const val PANTRY = "sample-pantry"

    /**
     * One batch of the sample.
     *
     * @property quantity In thousandths.
     * @property added The date added, in days before today.
     * @property expires The expiry date in days from today, or null for no expiry date.
     */
    private class Entry(
        val product: String,
        val quantity: Long,
        val area: String,
        val added: Int,
        val expires: Int?,
        val source: ExpirySource = ExpirySource.PRINTED,
    )

    private val products = listOf(
        Product("sample-milk", "Whole milk", Category.DAIRY, MeasureUnit.L, null, null),
        Product("sample-chicken", "Chicken thighs", Category.MEAT, MeasureUnit.G, null, null),
        Product("sample-spinach", "Baby spinach", Category.PRODUCE, MeasureUnit.G, null, null),
        Product("sample-yoghurt", "Greek yoghurt", Category.DAIRY, MeasureUnit.PCS, null, null),
        Product("sample-lasagne", "Leftover lasagne with roasted vegetables and béchamel sauce", Category.LEFTOVERS, null, null, null),
        Product("sample-juice", "Orange juice", Category.BEVERAGE, MeasureUnit.BOTTLE, null, null),
        Product("sample-cheddar", "Cheddar", Category.DAIRY, MeasureUnit.G, null, null),
        Product("sample-eggs", "Eggs", Category.OTHER, MeasureUnit.PCS, null, null),
        Product("sample-bread", "Sourdough bread", Category.BAKERY, MeasureUnit.SLICE, null, null),
        Product("sample-peas", "Garden peas", Category.PRODUCE, MeasureUnit.KG, null, null),
        Product("sample-ketchup", "Tomato ketchup", Category.CONDIMENT, MeasureUnit.BOTTLE, null, null),
        Product("sample-rice", "Basmati rice", Category.OTHER, MeasureUnit.PACK, null, null),
    )

    private val entries = listOf(
        Entry("sample-chicken", 600_000, StorageArea.fridge.id, added = 3, expires = -1, source = ExpirySource.ESTIMATED),
        Entry("sample-spinach", 200_000, StorageArea.fridge.id, added = 4, expires = 0, source = ExpirySource.ESTIMATED),
        Entry("sample-milk", 1_000, StorageArea.fridge.id, added = 6, expires = 1),
        Entry("sample-milk", 2_000, StorageArea.fridge.id, added = 1, expires = 8),
        Entry("sample-yoghurt", 4_000, StorageArea.fridge.id, added = 5, expires = 2),
        Entry("sample-lasagne", 2_000, StorageArea.fridge.id, added = 1, expires = 3, source = ExpirySource.ESTIMATED),
        Entry("sample-juice", 1_000, StorageArea.fridge.id, added = 2, expires = 5),
        Entry("sample-eggs", 10_000, StorageArea.fridge.id, added = 7, expires = 14),
        Entry("sample-cheddar", 350_000, StorageArea.fridge.id, added = 10, expires = 21),
        Entry("sample-bread", 8_000, StorageArea.freezer.id, added = 20, expires = 70, source = ExpirySource.ESTIMATED),
        Entry("sample-peas", 1_500, StorageArea.freezer.id, added = 40, expires = null),
        Entry("sample-ketchup", 1_000, PANTRY, added = 60, expires = 200),
        Entry("sample-rice", 2_000, PANTRY, added = 30, expires = null),
    )

    /** The change log of the sample for the date [today]. */
    fun changes(today: CalendarDate): List<Change> {
        fun date(days: Int) = checkNotNull(today.plusDays(days)) { "The sample date is out of range" }
        val bodies = buildList {
            add(ChangeBody.StorageAreaSaved(PANTRY, "Pantry", StorageKind.PANTRY))
            products.forEach { add(ChangeBody.ProductSaved(it)) }
            entries.forEachIndexed { index, entry ->
                val expiry = entry.expires?.let { ExpiryDate(date(it), entry.source) }
                add(
                    ChangeBody.BatchAdded(
                        Batch("sample-batch-${index + 1}", entry.product, entry.quantity, entry.area, date(-entry.added), expiry),
                    ),
                )
            }
        }
        return bodies.mapIndexed { index, body ->
            Change("sample-change-${index + 1}", at = index.toLong(), origin = FridgeViewModel.SAMPLE_ORIGIN, body = body)
        }
    }
}
