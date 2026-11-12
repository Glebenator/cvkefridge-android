package com.cvke.fridge.lookup

import java.io.File
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The cache on the phone, and the product lookup that uses it before the network. */
class ProductLookupTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val nutella = LookedUpProduct("Nutella", "Ferrero, Nutella", "400 g", listOf("en:spreads"))
    private val cola = LookedUpProduct("Coca-Cola", null, "330 ml", listOf("en:beverages"))
    private var clock = 1_000L

    private fun cache(file: File = File(folder.root, "lookups.json"), maxSize: Int = 1000) =
        ProductLookupCache(file, now = { clock }, io = Dispatchers.Unconfined, maxSize = maxSize)

    /** A server that answers with [answers] in turn, and counts the requests. */
    private class FakeServer(vararg answers: () -> HttpResponse) : HttpTransport {
        private val queue = ArrayDeque(answers.toList())
        var requests = 0

        override suspend fun get(url: String, headers: Map<String, String>): HttpResponse {
            requests++
            return queue.removeFirst()()
        }
    }

    private fun found(name: String) = { HttpResponse(200, """{"status":1,"product":{"product_name":"$name"}}""") }

    private val notFound = { HttpResponse(404, """{"status":0}""") }

    private fun lookup(server: FakeServer, cache: ProductLookupCache = cache()) =
        ProductLookup(OpenFoodFactsClient(server, "test"), cache, language = { "en" }, now = { clock })

    @Test
    fun theCacheKeepsAProductAfterTheAppStopped() = runBlocking {
        val file = File(folder.root, "lookups.json")
        cache(file).put("3017620422003", nutella)
        assertEquals(nutella, cache(file).get("3017620422003"))
        assertNull(cache(file).get("5449000000996"))
    }

    @Test
    fun aNewProductReplacesTheProductThatWasSavedFirst() = runBlocking {
        val file = File(folder.root, "lookups.json")
        val small = cache(file, maxSize = 2)
        small.put("1", nutella)
        small.put("2", cola)
        small.put("1", nutella.copy(name = "Nutella 2"))
        small.put("3", cola)
        // "2" is now the oldest, because "1" was saved again after it.
        val reopened = cache(file, maxSize = 2)
        assertNull(reopened.get("2"))
        assertEquals("Nutella 2", reopened.get("1")?.name)
        assertEquals(cola, reopened.get("3"))
    }

    @Test
    fun aFileThatCannotBeReadIsAnEmptyCache() = runBlocking {
        val file = File(folder.root, "lookups.json").apply { writeText("{not json") }
        val broken = cache(file)
        assertNull(broken.get("3017620422003"))
        broken.put("3017620422003", nutella)
        assertEquals(nutella, cache(file).get("3017620422003"))
    }

    @Test
    fun aSecondLookupOfAFoundProductNeedsNoNetwork() = runBlocking {
        val server = FakeServer(found("Nutella"))
        val lookup = lookup(server)
        assertEquals("Nutella", (lookup.find("3017620422003") as LookupResult.Found).product.name)
        assertEquals("Nutella", (lookup.find("3017620422003") as LookupResult.Found).product.name)
        assertEquals(1, server.requests)
    }

    @Test
    fun aProductThatIsNotFoundOrAFailedLookupIsNotKept() = runBlocking {
        val file = File(folder.root, "lookups.json")
        val server = FakeServer(notFound, { throw UnknownHostException() }, found("Water"))
        val lookup = lookup(server, cache(file))
        assertEquals(LookupResult.NotFound, lookup.find("5449000000996"))
        assertEquals(LookupResult.Failed(LookupFailure.NO_CONNECTION), lookup.find("5449000000996"))
        // The third lookup asks the server again, and then the product is in the cache.
        assertTrue(lookup.find("5449000000996") is LookupResult.Found)
        assertEquals(3, server.requests)
        assertEquals("Water", cache(file).get("5449000000996")?.name)
        assertFalse(File(folder.root, "lookups.json.new").exists())
    }

    @Test
    fun theAppSendsNotMoreThanThirtyRequestsInOneMinute() = runBlocking {
        val server = FakeServer(*Array(ProductLookup.MAX_REQUESTS + 1) { notFound })
        val lookup = lookup(server)
        repeat(ProductLookup.MAX_REQUESTS) { assertEquals(LookupResult.NotFound, lookup.find("5449000000996")) }
        assertEquals(LookupResult.Failed(LookupFailure.BUSY), lookup.find("5449000000996"))
        assertEquals(ProductLookup.MAX_REQUESTS, server.requests)
        // One minute after the first request, the app can send again.
        clock += ProductLookup.WINDOW_MILLIS
        assertEquals(LookupResult.NotFound, lookup.find("5449000000996"))
    }
}
