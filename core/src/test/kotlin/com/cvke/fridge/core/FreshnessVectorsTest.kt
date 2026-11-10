package com.cvke.fridge.core

import com.cvke.fridge.core.SpecFiles.dateOf
import com.cvke.fridge.core.SpecFiles.json
import com.cvke.fridge.core.SpecFiles.str
import com.cvke.fridge.core.SpecFiles.strOrNull
import org.junit.Test

class FreshnessVectorsTest {
    private val file = "freshness.json"

    @Test
    fun daysLeftAndFreshness() {
        for (case in SpecFiles.cases(file)) {
            val expiry = case.strOrNull("expiryDate")?.let(SpecFiles::dateOf)
            val left = Freshness.daysLeft(dateOf(case.str("today")), expiry)
            SpecFiles.check(file, case, json("daysLeft" to left, "freshness" to Freshness.of(left).key))
        }
    }
}
