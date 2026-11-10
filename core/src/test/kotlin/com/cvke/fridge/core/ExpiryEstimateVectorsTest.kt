package com.cvke.fridge.core

import com.cvke.fridge.core.SpecFiles.dateOf
import com.cvke.fridge.core.SpecFiles.json
import com.cvke.fridge.core.SpecFiles.str
import org.junit.Test

class ExpiryEstimateVectorsTest {
    private val file = "expiry-estimate.json"

    @Test
    fun estimate() {
        // The vector file has its own table of foods, in the format of foods.json.
        val table = FoodTable.decode(SpecFiles.text("vectors/$file"))
        for (case in SpecFiles.cases(file)) {
            val kind = StorageKind.fromKey(case.str("storageKind")) ?: throw AssertionError("Unknown storage kind in $case")
            val result = table.estimate(case.str("foodId"), kind, dateOf(case.str("dateAdded")))
            SpecFiles.check(file, case, json("expiryDate" to result?.date?.text, "expirySource" to result?.source?.key))
        }
    }
}
