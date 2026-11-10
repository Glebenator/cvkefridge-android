package com.cvke.fridge.core

import com.cvke.fridge.core.SpecFiles.batch
import com.cvke.fridge.core.SpecFiles.json
import com.cvke.fridge.core.SpecFiles.list
import com.cvke.fridge.core.SpecFiles.long
import com.cvke.fridge.core.SpecFiles.str
import org.junit.Test

class PlanUseVectorsTest {
    private val file = "plan-use.json"

    @Test
    fun plan() {
        for (case in SpecFiles.cases(file)) {
            val batches = case.list("batches").map { it.batch() }
            val plan = UseFirst.plan(batches, case.str("productId"), case.long("quantity"))
            val takes = plan.takes.map { json("batchId" to it.batchId, "quantity" to it.quantity) }
            SpecFiles.check(file, case, json("takes" to takes, "shortfall" to plan.shortfall))
        }
    }
}
