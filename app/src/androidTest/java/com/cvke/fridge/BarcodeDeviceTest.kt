package com.cvke.fridge

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.cvke.fridge.core.Category
import com.cvke.fridge.core.MeasureUnit
import com.cvke.fridge.lookup.HttpResponse
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * From a barcode to a saved food, through the production code path: the scanner, the product lookup with its cache,
 * the add form, and the change log. The test types the barcode, which is the second way into the same lookup: the
 * emulator has no camera picture with a barcode. [FakeServer] answers in the place of Open Food Facts.
 */
class BarcodeDeviceTest : DeviceTest() {
    private fun openScanner() {
        // The scanner opens from the large button at the top of the add form.
        rule.onNodeWithTag("add_food").performClick()
        waitFor(hasTestTag("name_scan"))
        rule.onNodeWithTag("name_scan").performClick()
        waitFor(hasTestTag("scan_screen"))
        // The scanner says what leaves the phone.
        rule.onNodeWithTag("scan_privacy").assertTextContains("Only the barcode number goes to Open Food Facts", substring = true)
    }

    private fun typeBarcode(barcode: String) {
        // The snackbar of the last save is above the panel, so a tap on the button reaches it.
        rule.onNodeWithTag("type_barcode").performClick()
        waitFor(hasTestTag("field_barcode"))
        rule.onNodeWithTag("field_barcode").performTextInput(barcode)
        rule.onNodeWithTag("barcode_look_up").performClick()
    }

    private fun waitForRequests(count: Int) = rule.waitUntil(10_000) { FakeServer.requests.size == count }

    @Test
    fun aFoundProductFillsInTheFormAndTheNextScanFindsItInTheInventory() {
        FakeServer.answer = { HttpResponse(200, NUTELLA) }
        start(emptyList())
        openScanner()
        typeBarcode("3017620422003")

        waitFor(hasTestTag("add_food_screen"))
        rule.onNodeWithTag("scan_note").assertTextContains("From Open Food Facts", substring = true)
        rule.onNodeWithTag("field_name").assertTextContains("Nutella")
        rule.onNodeWithTag("field_category").assertTextContains("Condiment")
        rule.onNodeWithTag("field_quantity").assertTextContains("400")
        rule.onNodeWithTag("field_unit").assertTextContains("g")
        rule.onNodeWithTag("save").performClick()

        // "Save" closes the form and the scanner.
        waitFor(hasTestTag("total_items") and hasText("1"))
        val (url, headers) = FakeServer.requests.single()
        assertTrue(url.startsWith("https://world.openfoodfacts.org/api/v2/product/3017620422003?fields="))
        assertTrue(headers.getValue("User-Agent").startsWith("CVKEFridge-Android/"))
        val product = inventory().products.single()
        assertEquals(listOf("Nutella", "3017620422003"), listOf(product.name, product.barcode))
        assertEquals(Category.CONDIMENT to MeasureUnit.G, product.category to product.unit)
        assertEquals(400_000L, inventory().batches.single().quantity)

        // The second scan of the barcode needs no network. The form adds a batch to the product.
        openScanner()
        typeBarcode("3017620422003")
        waitFor(hasTestTag("add_food_screen"))
        rule.onNodeWithTag("field_name").assertTextContains("Nutella")
        rule.onNodeWithTag("name_existing", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("save_scan_next").performClick()
        waitFor(hasTestTag("scan_screen"))
        waitFor(hasText("Nutella added"))
        // The snackbar is above the panel: "Undo" is there, and the buttons of the panel are not under the snackbar.
        val undo = rule.onNodeWithText("Undo").assertIsDisplayed().getUnclippedBoundsInRoot()
        val panel = rule.onNodeWithTag("scan_panel").getUnclippedBoundsInRoot()
        assertTrue("The snackbar covers the panel", undo.bottom <= panel.top)
        assertEquals(1, FakeServer.requests.size)
        assertEquals(2, inventory().batches.count { it.productId == product.id })
    }

    @Test
    fun aBarcodeThatOpenFoodFactsDoesNotHaveKeepsTheBarcodeAndAsksForTheName() {
        FakeServer.answer = { HttpResponse(404, """{"code":"4099999999996","status":0,"status_verbose":"product not found"}""") }
        start(emptyList())
        openScanner()
        typeBarcode("4099999999996")

        waitFor(hasTestTag("add_food_screen"))
        rule.onNodeWithTag("scan_note").assertTextContains("Open Food Facts has no product", substring = true)
        rule.onNodeWithTag("field_name").assertIsFocused()
        rule.onNodeWithTag("field_name").performTextInput("Oat drink")
        rule.onNodeWithTag("save_scan_next").performClick()

        // "Save and scan next" shows the scanner again.
        waitFor(hasTestTag("scan_screen"))
        waitFor(hasText("Oat drink added"))
        assertEquals("4099999999996", inventory().products.single().barcode)
    }

    @Test
    fun withNoConnectionTheScannerSaysSoAndKeepsTheBarcodeForEnterByHand() {
        FakeServer.answer = { throw UnknownHostException() }
        start(emptyList())
        openScanner()

        // A typed number with a wrong check digit is not looked up.
        typeBarcode("5449000000997")
        rule.onNodeWithTag("error_barcode", useUnmergedTree = true).assertTextContains("A digit is not correct", substring = true)
        rule.onNodeWithText("Cancel").performClick()
        waitUntilGone(hasTestTag("barcode_dialog"))
        assertEquals(0, FakeServer.requests.size)

        typeBarcode("5449000000996")
        waitFor(hasTestTag("lookup_failed"))
        rule.onNodeWithTag("lookup_failed").assertTextContains("No connection", substring = true)

        // "Try again" sends one request. The app never tries again by itself.
        rule.onNodeWithTag("lookup_try_again").performClick()
        waitForRequests(2)
        waitFor(hasTestTag("lookup_failed"))

        rule.onNodeWithTag("enter_by_hand").performClick()
        waitFor(hasTestTag("add_food_screen"))
        rule.onNodeWithTag("scan_note").assertTextContains("5449000000996", substring = true)
        rule.onNodeWithTag("field_name").performTextInput("Cola")
        rule.onNodeWithTag("save").performClick()

        waitFor(hasTestTag("total_items") and hasText("1"))
        assertEquals("5449000000996", inventory().products.single().barcode)
        assertEquals(2, FakeServer.requests.size)
        rule.onNodeWithTag("add_food").assertIsDisplayed()
    }

    private companion object {
        /** The answer of Open Food Facts for the barcode 3017620422003 on 12 November 2026. */
        const val NUTELLA = """{"code":"3017620422003","product":{"brands":"Ferrero, Nutella","categories_tags":["en:breakfasts","en:spreads","en:sweet-spreads","fr:pates-a-tartiner","en:hazelnut-spreads","en:confectionary-based-spreads","fr:Nutella"],"generic_name":"Hazelnut And Cocoa Spread","product_name":"Nutella","product_name_en":"Nutella","quantity":"400 g e"},"status":1,"status_verbose":"product found"}"""
    }
}
