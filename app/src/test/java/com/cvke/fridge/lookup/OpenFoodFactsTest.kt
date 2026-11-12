package com.cvke.fridge.lookup

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The answers of Open Food Facts and the request of the client. The fixtures in `src/test/resources/openfoodfacts`
 * are answers of the API: `nutella.json` and `coca-cola.json` as the server gave them on 12 November 2026, and the
 * others made in the same format.
 */
class OpenFoodFactsTest {
    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResource("/openfoodfacts/$name")) { "No fixture $name" }.readText()

    /** A transport that records the requests and gives the [answer]. */
    private class FakeTransport(private val answer: () -> HttpResponse) : HttpTransport {
        val requests = mutableListOf<Pair<String, Map<String, String>>>()

        override suspend fun get(url: String, headers: Map<String, String>): HttpResponse {
            requests += url to headers
            return answer()
        }
    }

    private fun lookUp(transport: HttpTransport, barcode: String = "3017620422003", language: String = "en") =
        runBlocking { OpenFoodFactsClient(transport, "CVKEFridge-Android/0.1.0").lookUp(barcode, language) }

    @Test
    fun aFoundProductHasItsNameBrandsQuantityAndTags() {
        val found = lookUp(FakeTransport { HttpResponse(200, fixture("nutella.json")) }) as LookupResult.Found
        assertEquals(
            LookedUpProduct(
                name = "Nutella",
                brands = "Ferrero, Nutella",
                quantity = "400 g e",
                categories = listOf(
                    "en:breakfasts", "en:spreads", "en:sweet-spreads", "fr:pates-a-tartiner", "en:hazelnut-spreads",
                    "en:confectionary-based-spreads", "fr:Nutella",
                ),
            ),
            found.product,
        )
    }

    @Test
    fun theNameInTheLanguageOfTheDeviceComesFirst() {
        val english = OpenFoodFactsJson.decode(fixture("coca-cola.json"), "en") as OpenFoodFactsJson.Decoded.Product
        assertEquals("Coca-Cola", english.product.name)
        // No Spanish field is in the answer, so the main name of the product comes first.
        val spanish = OpenFoodFactsJson.decode(fixture("coca-cola.json"), "es") as OpenFoodFactsJson.Decoded.Product
        assertEquals("Coca-Cola Original", spanish.product.name)
    }

    @Test
    fun emptyFieldsAreSkippedAndUnknownFieldsHaveNoEffect() {
        val messy = OpenFoodFactsJson.decode(fixture("messy.json"), "de") as OpenFoodFactsJson.Decoded.Product
        // product_name is empty, so the English name is next. The decoder trims only the ends.
        assertEquals("GREEK  STYLE\tYOGURT", messy.product.name)
        assertEquals("6 x 150 g", messy.product.quantity)
        assertEquals(",  BIO&amp;GUT  , Hofmolkerei", messy.product.brands)
    }

    @Test
    fun noProductNoNameAndAnInvalidCodeAreNotFound() {
        assertEquals(LookupResult.NotFound, lookUp(FakeTransport { HttpResponse(404, fixture("not-found.json")) }))
        assertEquals(LookupResult.NotFound, lookUp(FakeTransport { HttpResponse(200, fixture("invalid-code.json")) }))
        assertEquals(LookupResult.NotFound, lookUp(FakeTransport { HttpResponse(200, fixture("no-name.json")) }))
        // A 404 with a page that is not JSON is also "not found".
        assertEquals(LookupResult.NotFound, lookUp(FakeTransport { HttpResponse(404, "<html>Not found</html>") }))
    }

    @Test
    fun anAnswerThatIsNotTheApiIsAServerFailure() {
        val server = LookupResult.Failed(LookupFailure.SERVER)
        assertEquals(server, lookUp(FakeTransport { HttpResponse(200, "<html>Maintenance</html>") }))
        assertEquals(server, lookUp(FakeTransport { HttpResponse(200, """{"status":"success"}""") }))
        assertEquals(server, lookUp(FakeTransport { HttpResponse(200, """{"status":1}""") }))
        assertEquals(server, lookUp(FakeTransport { HttpResponse(200, fixture("nutella.json").take(80)) }))
        assertEquals(server, lookUp(FakeTransport { HttpResponse(500, "") }))
        assertEquals(server, lookUp(FakeTransport { HttpResponse(400, "") }))
    }

    @Test
    fun tooManyRequestsIsBusy() {
        assertEquals(LookupResult.Failed(LookupFailure.BUSY), lookUp(FakeTransport { HttpResponse(429, "") }))
        assertEquals(LookupResult.Failed(LookupFailure.BUSY), lookUp(FakeTransport { HttpResponse(503, "") }))
    }

    @Test
    fun aTimeoutIsATimeoutAndEachOtherNetworkFailureIsNoConnection() {
        assertEquals(LookupResult.Failed(LookupFailure.TIMEOUT), lookUp(FakeTransport { throw SocketTimeoutException() }))
        assertEquals(LookupResult.Failed(LookupFailure.NO_CONNECTION), lookUp(FakeTransport { throw UnknownHostException() }))
        assertEquals(LookupResult.Failed(LookupFailure.NO_CONNECTION), lookUp(FakeTransport { throw SSLHandshakeException("") }))
        assertEquals(LookupResult.Failed(LookupFailure.NO_CONNECTION), lookUp(FakeTransport { throw IOException() }))
    }

    @Test
    fun theRequestHasTheBarcodeTheFieldsAndTheNameOfTheAppOnly() {
        val transport = FakeTransport { HttpResponse(200, fixture("nutella.json")) }
        lookUp(transport, language = "de")
        val (url, headers) = transport.requests.single()
        assertEquals(
            "https://world.openfoodfacts.org/api/v2/product/3017620422003?fields=" +
                "product_name_de,product_name,product_name_en,generic_name_de,generic_name,generic_name_en," +
                "brands,quantity,categories_tags",
            url,
        )
        assertEquals(mapOf("User-Agent" to "CVKEFridge-Android/0.1.0", "Accept" to "application/json"), headers)
    }

    @Test
    fun aLanguageThatIsNotACodeAddsNoField() {
        assertEquals(
            listOf("product_name_en", "product_name", "generic_name_en", "generic_name"),
            OpenFoodFactsJson.nameFields("en"),
        )
        assertEquals(listOf("product_name", "product_name_en", "generic_name", "generic_name_en"), OpenFoodFactsJson.nameFields(""))
        assertTrue(OpenFoodFactsJson.nameFields("x,y").none { it.contains(",") })
    }
}
