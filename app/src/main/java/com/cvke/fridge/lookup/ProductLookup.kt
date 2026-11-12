package com.cvke.fridge.lookup

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The product lookup: first the cache on the phone, then Open Food Facts. A found product goes into the cache. A
 * product that is not found and a failed lookup do not, so the next scan asks Open Food Facts again.
 *
 * The app never sends a lookup again by itself. Only the person can do that. As a second protection for the server,
 * the app sends not more than [MAX_REQUESTS] requests in [WINDOW_MILLIS]. A person who scans does not get to this
 * number. Open Food Facts permits more.
 *
 * @param language The language code of the device, for example "en".
 * @param now The instant of now, in milliseconds.
 */
class ProductLookup(
    private val client: OpenFoodFactsClient,
    private val cache: ProductLookupCache,
    private val language: () -> String,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()

    /** The instants of the requests in the last [WINDOW_MILLIS], oldest first. */
    private val sent = ArrayDeque<Long>()

    /** Looks up the product of the [barcode], in the form of [Barcodes]. */
    suspend fun find(barcode: String): LookupResult {
        cache.get(barcode)?.let { return LookupResult.Found(it) }
        if (!mayRequest()) return LookupResult.Failed(LookupFailure.BUSY)
        val result = client.lookUp(barcode, language())
        if (result is LookupResult.Found) cache.put(barcode, result.product)
        return result
    }

    private suspend fun mayRequest(): Boolean = mutex.withLock {
        val time = now()
        while (sent.isNotEmpty() && time - sent.first() >= WINDOW_MILLIS) sent.removeFirst()
        if (sent.size >= MAX_REQUESTS) return@withLock false
        sent.addLast(time)
        true
    }

    companion object {
        const val MAX_REQUESTS = 30
        const val WINDOW_MILLIS = 60_000L
    }
}
