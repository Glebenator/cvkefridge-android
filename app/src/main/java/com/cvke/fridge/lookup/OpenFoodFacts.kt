package com.cvke.fridge.lookup

import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * A product from Open Food Facts, with only the fields that the app uses.
 *
 * @property name The name of the product, with no brand. It is not empty.
 * @property brands The brands as Open Food Facts gives them: names with a comma between them. Null when there are none.
 * @property quantity The quantity of the package as text, for example "400 g". Null when there is none.
 * @property categories The category tags of Open Food Facts, for example "en:sweet-spreads".
 */
data class LookedUpProduct(val name: String, val brands: String?, val quantity: String?, val categories: List<String>)

/** Why a product lookup did not give an answer. The app keeps nothing for a failed lookup. */
enum class LookupFailure {
    /** The phone has no connection, or the server cannot be found. */
    NO_CONNECTION,

    /** The server did not answer in time. */
    TIMEOUT,

    /** Too many lookups in a short time: the server refused, or the app stopped them (see [ProductLookup]). */
    BUSY,

    /** The server gave an error, or an answer that the app cannot read. */
    SERVER,
}

/** The result of a product lookup. */
sealed interface LookupResult {
    data class Found(val product: LookedUpProduct) : LookupResult

    /** Open Food Facts has no product with a name for the barcode. */
    data object NotFound : LookupResult

    data class Failed(val failure: LookupFailure) : LookupResult
}

/** An answer of the server: the HTTP status code and the text of the body. */
data class HttpResponse(val status: Int, val body: String)

/** Sends a request to the internet. The product lookup is the only part of the app that uses it. */
fun interface HttpTransport {
    /**
     * Sends a GET request and gives the answer. Cancellation of the coroutine stops the request.
     *
     * @throws IOException when no answer arrives, for example with no connection or after the timeout.
     */
    suspend fun get(url: String, headers: Map<String, String>): HttpResponse
}

/**
 * The client of the product API of Open Food Facts (API version 2). It sends the barcode and nothing else, asks only
 * for the fields that the app uses, and does not try again by itself. The request uses HTTPS.
 *
 * @param userAgent The name and the version of the app. Open Food Facts asks each app to send them. It has no data
 *   about the person.
 */
class OpenFoodFactsClient(private val transport: HttpTransport, private val userAgent: String) {
    /**
     * Looks up the product of the [barcode]. [language] is the language code of the device, for example "en". The
     * name in that language comes first when the product has one.
     */
    suspend fun lookUp(barcode: String, language: String): LookupResult {
        require(barcode.isNotEmpty() && barcode.all { it in '0'..'9' }) { "A barcode has only digits" }
        val response = try {
            transport.get(url(barcode, language), mapOf("User-Agent" to userAgent, "Accept" to "application/json"))
        } catch (failure: IOException) {
            return LookupResult.Failed(failureOf(failure))
        }
        return when (response.status) {
            200, 404 -> when (val decoded = OpenFoodFactsJson.decode(response.body, language)) {
                is OpenFoodFactsJson.Decoded.Product -> LookupResult.Found(decoded.product)
                OpenFoodFactsJson.Decoded.NoProduct -> LookupResult.NotFound
                // A 404 with a body that is not JSON is still a "not found".
                OpenFoodFactsJson.Decoded.Unreadable ->
                    if (response.status == 404) LookupResult.NotFound else LookupResult.Failed(LookupFailure.SERVER)
            }
            429, 503 -> LookupResult.Failed(LookupFailure.BUSY)
            else -> LookupResult.Failed(LookupFailure.SERVER)
        }
    }

    companion object {
        private const val BASE = "https://world.openfoodfacts.org/api/v2/product/"

        /** The URL of the lookup. It has the barcode and the names of the fields, and no other data. */
        fun url(barcode: String, language: String): String {
            val names = OpenFoodFactsJson.nameFields(language)
            val fields = (names + listOf("brands", "quantity", "categories_tags")).joinToString(",")
            return "$BASE$barcode?fields=$fields"
        }

        /**
         * A timeout is [LookupFailure.TIMEOUT]. Each other failure of the connection is
         * [LookupFailure.NO_CONNECTION], for example no network, a server name that cannot be found, or a network
         * that blocks the request.
         */
        private fun failureOf(failure: IOException): LookupFailure =
            if (failure is SocketTimeoutException) LookupFailure.TIMEOUT else LookupFailure.NO_CONNECTION
    }
}

/** Reads the answers of the product API of Open Food Facts. */
object OpenFoodFactsJson {
    /** What an answer contains. */
    sealed interface Decoded {
        data class Product(val product: LookedUpProduct) : Decoded

        /** The answer says that there is no product for the barcode, or the product has no name. */
        data object NoProduct : Decoded

        /** The text is not an answer of the API. */
        data object Unreadable : Decoded
    }

    /**
     * The fields with a name, in the order that the app uses them: the product name in the language of the device,
     * the product name in the main language of the product, the product name in English, and then the same for the
     * generic name.
     */
    fun nameFields(language: String): List<String> {
        val code = language.lowercase().takeIf { it.length in 2..3 && it.all { char -> char in 'a'..'z' } }
        return listOf("product_name", "generic_name").flatMap { field ->
            listOfNotNull(code?.let { "${field}_$it" }, field, "${field}_en")
        }.distinct()
    }

    /**
     * Reads one answer. A product is there when `status` is 1 and the product has a name. `status` 0 means that
     * Open Food Facts has no product for the barcode, or that the barcode is not valid.
     */
    fun decode(body: String, language: String): Decoded {
        val root = try {
            Json.parseToJsonElement(body) as? JsonObject
        } catch (_: SerializationException) {
            null
        } ?: return Decoded.Unreadable
        val status = (root["status"] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull ?: return Decoded.Unreadable
        if (status == 0) return Decoded.NoProduct
        if (status != 1) return Decoded.Unreadable
        val product = root["product"] as? JsonObject ?: return Decoded.Unreadable
        val name = nameFields(language).firstNotNullOfOrNull { text(product[it]) } ?: return Decoded.NoProduct
        val categories = (product["categories_tags"] as? JsonArray).orEmpty().mapNotNull(::text)
        return Decoded.Product(LookedUpProduct(name, text(product["brands"]), text(product["quantity"]), categories))
    }

    /** The text of a field with no spaces at the ends, or null when it is not text or is empty. */
    private fun text(element: JsonElement?): String? =
        (element as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.takeIf { it.isNotEmpty() }
}
