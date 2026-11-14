package com.cvke.fridge.core

import kotlinx.serialization.json.JsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangeCodecTest {
    /** A `batchAdded` change. Each argument is JSON text. */
    private fun batchAdded(
        id: String = "\"c-1\"",
        at: String = "1",
        type: String = "\"batchAdded\"",
        quantity: String = "1000",
        dateAdded: String = "\"2026-11-10\"",
        expiryDate: String = "\"2026-11-12\"",
        expirySource: String = "\"printed\"",
    ) = "{\"id\":$id,\"at\":$at,\"origin\":\"phone-a\",\"body\":{\"type\":$type,\"batch\":{\"id\":\"b-1\"," +
        "\"productId\":\"p-1\",\"quantity\":$quantity,\"storageAreaId\":\"fridge\",\"dateAdded\":$dateAdded," +
        "\"expiryDate\":$expiryDate,\"expirySource\":$expirySource}}}"

    @Test
    fun everyChangeOfTheVectorsSurvivesEncodeAndDecode() {
        var count = 0
        for (case in SpecFiles.cases("change-log.json")) {
            for (element in case.getValue("changes") as JsonArray) {
                val change = ChangeCodec.decode(element.toString())
                val again = ChangeCodec.decode(ChangeCodec.encode(change))
                assertEquals("change ${change.id} of ${case["name"]}", change, again)
                count++
            }
        }
        assertTrue("The vector file has no changes", count > 0)
    }

    @Test
    fun theTextIsTheShapeOfTheVectors() {
        val text = batchAdded()
        assertEquals(text, ChangeCodec.encode(ChangeCodec.decode(text)))
    }

    @Test
    fun invalidInputThrows() {
        val bad = listOf(
            "not json",
            "[]",
            batchAdded(type = "\"batchMoved\""),
            batchAdded(dateAdded = "\"2026-02-30\""),
            batchAdded(dateAdded = "\"２０２６-11-10\""),
            batchAdded(expirySource = "\"guessed\""),
            batchAdded(expirySource = "null"),
            batchAdded(expiryDate = "null"),
            batchAdded(quantity = "\"1000\""),
            batchAdded(quantity = "1.5"),
            batchAdded(id = "1"),
            batchAdded(at = "null"),
        )
        for (text in bad) {
            assertThrows(text, IllegalArgumentException::class.java) { ChangeCodec.decode(text) }
        }
        val noteNotText = "{\"id\":\"c-1\",\"at\":1,\"origin\":\"phone-a\",\"body\":{\"type\":\"productSaved\"," +
            "\"product\":{\"id\":\"p-1\",\"name\":\"Jam\",\"category\":\"condiment\",\"unit\":null," +
            "\"barcode\":null,\"foodId\":null,\"note\":5}}}"
        assertThrows(IllegalArgumentException::class.java) { ChangeCodec.decode(noteNotText) }
        val withoutExpiryKeys = batchAdded().replace(",\"expiryDate\":\"2026-11-12\",\"expirySource\":\"printed\"", "")
        assertThrows(IllegalArgumentException::class.java) { ChangeCodec.decode(withoutExpiryKeys) }
    }
}
