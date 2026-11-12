package com.cvke.fridge.lookup

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The products that a lookup found, on the phone, so that the second scan of a barcode needs no connection. Only a
 * product that was found goes into the cache. "Not found" and a failed lookup do not go into it.
 *
 * The cache is one JSON file in the private files of the app. Backup is off, so it stays on the phone. A write makes a
 * new file and then replaces the old file. The cache is not necessary for the inventory: when the file cannot be read
 * or written, the app continues without the cache.
 *
 * @param maxSize The largest number of products. A new product then replaces the product that was saved first.
 */
class ProductLookupCache(
    private val file: File,
    private val now: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val maxSize: Int = MAX_SIZE,
) {
    private val mutex = Mutex()

    /** The products of the file, in the order in which they were saved. Null until the first read. */
    private var entries: LinkedHashMap<String, CachedProduct>? = null

    /** The product of the barcode, or null when the cache does not have it. */
    suspend fun get(barcode: String): LookedUpProduct? = mutex.withLock { load()[barcode]?.product() }

    /** Adds or replaces the product of the barcode. */
    suspend fun put(barcode: String, product: LookedUpProduct) = mutex.withLock {
        val all = load()
        all.remove(barcode)
        all[barcode] = CachedProduct(barcode, product.name, product.brands, product.quantity, product.categories, now())
        while (all.size > maxSize) all.remove(all.keys.first())
        withContext(io) { write(all.values.toList()) }
    }

    private suspend fun load(): LinkedHashMap<String, CachedProduct> = entries ?: withContext(io) { read() }.also { entries = it }

    private fun read(): LinkedHashMap<String, CachedProduct> {
        val products = try {
            if (file.exists()) json.decodeFromString(CacheFile.serializer(), file.readText()).products else emptyList()
        } catch (_: IOException) {
            emptyList()
        } catch (_: SerializationException) {
            emptyList()
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
        return products.associateByTo(LinkedHashMap<String, CachedProduct>()) { it.barcode }
    }

    private fun write(products: List<CachedProduct>) {
        val temporary = File(file.parentFile, file.name + ".new")
        try {
            temporary.writeText(json.encodeToString(CacheFile.serializer(), CacheFile(products)))
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: IOException) {
            // The products stay in memory while the app runs. The next write tries again.
            temporary.delete()
        }
    }

    @Serializable
    private class CacheFile(val products: List<CachedProduct>)

    /** @property savedAt The instant of the lookup, in milliseconds from 1970-01-01T00:00:00Z. */
    @Serializable
    private class CachedProduct(
        val barcode: String,
        val name: String,
        val brands: String? = null,
        val quantity: String? = null,
        val categories: List<String> = emptyList(),
        val savedAt: Long,
    ) {
        fun product() = LookedUpProduct(name, brands, quantity, categories)
    }

    private companion object {
        const val MAX_SIZE = 1000
        val json = Json { ignoreUnknownKeys = true }
    }
}
