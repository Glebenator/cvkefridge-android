package com.cvke.fridge.core

import com.cvke.fridge.core.SpecFiles.dateAt
import com.cvke.fridge.core.SpecFiles.dateOf
import com.cvke.fridge.core.SpecFiles.json
import com.cvke.fridge.core.SpecFiles.long
import com.cvke.fridge.core.SpecFiles.str
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarDateVectorsTest {
    private val file = "calendar-date.json"

    @Test
    fun parse() {
        for (case in SpecFiles.cases(file, "parse")) {
            val date = CalendarDate.parse(case.str("text"))
            val actual = if (date == null) json("valid" to false) else
                json("valid" to true, "year" to date.year, "month" to date.month, "day" to date.day)
            SpecFiles.check(file, case, actual)
            if (date != null) assertEquals(case.str("text"), date.text)
        }
    }

    @Test
    fun plusDays() {
        for (case in SpecFiles.cases(file, "plusDays")) {
            val result = case.dateAt("date").plusDays(case.long("days").toInt())
            SpecFiles.check(file, case, json("date" to result?.text))
        }
    }

    @Test
    fun daysUntil() {
        for (case in SpecFiles.cases(file, "daysUntil")) {
            SpecFiles.check(file, case, json("days" to case.dateAt("from").daysUntil(case.dateAt("to"))))
        }
    }

    @Test
    fun order() {
        for (case in SpecFiles.cases(file, "order")) {
            val dates = case.getValue("dates").jsonArray.map { dateOf(it.jsonPrimitive.content) }
            SpecFiles.check(file, case, json("sorted" to dates.sorted().map { it.text }))
        }
    }

    @Test
    fun dateOfInstant() {
        for (case in SpecFiles.cases(file, "dateOfInstant")) {
            val result = CalendarDate.ofInstant(case.long("epochMillis"), case.long("utcOffsetSeconds").toInt())
            SpecFiles.check(file, case, json("date" to result?.text))
        }
    }
}
