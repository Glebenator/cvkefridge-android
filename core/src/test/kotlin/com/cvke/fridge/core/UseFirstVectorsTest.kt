package com.cvke.fridge.core

import com.cvke.fridge.core.SpecFiles.batch
import com.cvke.fridge.core.SpecFiles.json
import com.cvke.fridge.core.SpecFiles.list
import org.junit.Test

class UseFirstVectorsTest {
    private val file = "use-first.json"

    @Test
    fun order() {
        for (case in SpecFiles.cases(file)) {
            val order = UseFirst.order(case.list("batches").map { it.batch() })
            SpecFiles.check(file, case, json("order" to order.map { it.id }))
        }
    }
}
