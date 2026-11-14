package com.cvke.fridge

import com.cvke.fridge.core.Batch
import com.cvke.fridge.core.CalendarDate
import com.cvke.fridge.core.Category
import com.cvke.fridge.core.Change
import com.cvke.fridge.core.ChangeBody
import com.cvke.fridge.core.ExpiryDate
import com.cvke.fridge.core.ExpirySource
import com.cvke.fridge.core.MeasureUnit
import com.cvke.fridge.core.Outcome
import com.cvke.fridge.core.Product
import com.cvke.fridge.core.StorageArea
import com.cvke.fridge.core.StorageKind
import com.cvke.fridge.ui.FridgeViewModel

/**
 * The sample of the debug build: a change log with food in each freshness, and a history of food that was used and
 * thrown away in the last five weeks, for work on the screens. It is not in the release build. Each date is a number
 * of days from today, so the days left and the days of the history come from the same calculation as for real food.
 * The changes have the origin [FridgeViewModel.SAMPLE_ORIGIN], and the screens then show that the food is a sample.
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
        Product("sample-milk", "Whole milk", Category.DAIRY, MeasureUnit.L, null, null, note = "Opened on Tuesday"),
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
        // Food that the person had before and has none of now. It is only in the history.
        Product("sample-strawberries", "Strawberries", Category.PRODUCE, MeasureUnit.G, null, "fk-481"),
        Product("sample-bananas", "Bananas", Category.PRODUCE, MeasureUnit.PCS, null, "fk-251"),
        Product("sample-salmon", "Salmon fillet", Category.MEAT, MeasureUnit.G, null, null),
        Product("sample-curry", "Leftover curry", Category.LEFTOVERS, null, null, null, note = "Made on Sunday. Spicy, so not for the children."),
        Product("sample-croissants", "Croissants", Category.BAKERY, MeasureUnit.PCS, null, null),
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

    /** A use or a throw away of a batch of the sample. [quantity] is in thousandths, [daysAgo] in days before today. */
    private class Take(val outcome: Outcome, val quantity: Long, val daysAgo: Int)

    private fun used(quantity: Long, daysAgo: Int) = Take(Outcome.USED, quantity, daysAgo)

    private fun thrownAway(quantity: Long, daysAgo: Int) = Take(Outcome.THROWN_AWAY, quantity, daysAgo)

    /** A batch of the sample that ended, or partly ended, before today, with its uses and throw aways. */
    private class Past(val entry: Entry, val takes: List<Take>)

    private val past = listOf(
        Past(Entry("sample-croissants", 4_000, StorageArea.fridge.id, added = 35, expires = -32), listOf(used(2_000, 34), thrownAway(2_000, 31))),
        Past(Entry("sample-milk", 1_000, StorageArea.fridge.id, added = 27, expires = -20), listOf(used(1_000, 22))),
        Past(
            Entry("sample-strawberries", 400_000, StorageArea.fridge.id, added = 30, expires = -28, source = ExpirySource.ESTIMATED),
            listOf(thrownAway(400_000, 25)),
        ),
        Past(Entry("sample-curry", 1_000, StorageArea.fridge.id, added = 20, expires = -16, source = ExpirySource.ESTIMATED), listOf(thrownAway(1_000, 15))),
        Past(Entry("sample-salmon", 300_000, StorageArea.fridge.id, added = 16, expires = -14), listOf(used(300_000, 14))),
        Past(Entry("sample-spinach", 200_000, StorageArea.fridge.id, added = 18, expires = -12), listOf(used(50_000, 14), thrownAway(150_000, 12))),
        Past(Entry("sample-milk", 1_000, StorageArea.fridge.id, added = 13, expires = -6), listOf(used(1_000, 8))),
        Past(Entry("sample-bananas", 6_000, StorageArea.fridge.id, added = 9, expires = null), listOf(used(3_000, 7), used(3_000, 4))),
        Past(
            Entry("sample-strawberries", 400_000, StorageArea.fridge.id, added = 12, expires = -10, source = ExpirySource.ESTIMATED),
            listOf(thrownAway(400_000, 6)),
        ),
        Past(Entry("sample-yoghurt", 2_000, StorageArea.fridge.id, added = 8, expires = -1), listOf(thrownAway(2_000, 1))),
        Past(Entry("sample-juice", 1_000, StorageArea.fridge.id, added = 9, expires = 3), listOf(used(1_000, 0))),
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
            past.forEachIndexed { index, batch ->
                val entry = batch.entry
                val expiry = entry.expires?.let { ExpiryDate(date(it), entry.source) }
                add(ChangeBody.BatchAdded(Batch("sample-past-${index + 1}", entry.product, entry.quantity, entry.area, date(-entry.added), expiry)))
            }
            // The uses and throw aways in the order of their dates, as a person makes them.
            past.withIndex()
                .flatMap { (index, batch) -> batch.takes.map { "sample-past-${index + 1}" to it } }
                .sortedByDescending { it.second.daysAgo }
                .forEach { (batchId, take) ->
                    add(
                        when (take.outcome) {
                            Outcome.USED -> ChangeBody.BatchUsed(batchId, take.quantity, date(-take.daysAgo))
                            Outcome.THROWN_AWAY -> ChangeBody.BatchThrownAway(batchId, take.quantity, date(-take.daysAgo))
                        },
                    )
                }
        }
        return bodies.mapIndexed { index, body ->
            Change("sample-change-${index + 1}", at = index.toLong(), origin = FridgeViewModel.SAMPLE_ORIGIN, body = body)
        }
    }
}
