package com.cvke.fridge.photo

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The photos of the products: one JPEG file for each product in a [directory] of the private files of the app (see
 * [PhotoFiles]). Backup is off, so the photos stay on the phone. The store keeps no reference to the picture that the
 * person selected: [save] writes a copy, and the copy is the photo.
 *
 * One change runs at a time. Each file operation is off the main thread.
 *
 * @param newToken Makes the random part of the name of a new photo.
 */
class PhotoStore(
    private val directory: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val newToken: () -> String = { UUID.randomUUID().toString() },
) {
    private val mutex = Mutex()
    private var read = false
    private val _photos = MutableStateFlow<Map<String, ProductPhoto>>(emptyMap())

    /** The photo of each product that has one, by the identifier of the product. It is empty before [read]. */
    val photos: StateFlow<Map<String, ProductPhoto>> = _photos.asStateFlow()

    /** Reads the folder, one time. */
    suspend fun read() = mutex.withLock { readOnce() }

    /**
     * Writes a new photo of the product, and deletes the photo that it had. [encode] writes the JPEG. A write that
     * fails leaves the photo that the product had.
     *
     * @throws IOException when the file cannot be written, and each exception of [encode].
     */
    suspend fun save(productId: String, encode: (OutputStream) -> Unit): ProductPhoto = mutex.withLock {
        readOnce()
        val photo = withContext(io) {
            directory.mkdirs()
            val name = PhotoFiles.name(productId, newToken())
            val target = File(directory, name)
            val temporary = File(directory, name + PhotoFiles.TEMPORARY)
            try {
                FileOutputStream(temporary).use { stream ->
                    stream.buffered().let { buffered ->
                        encode(buffered)
                        buffered.flush()
                    }
                    stream.fd.sync()
                }
                if (!temporary.renameTo(target)) throw IOException("The photo could not get its name")
            } finally {
                temporary.delete()
            }
            _photos.value[productId]?.let { File(it.path).delete() }
            ProductPhoto(target.path)
        }
        _photos.value += productId to photo
        photo
    }

    /**
     * Removes the photo of the product. The file stays as a removed photo, so that [restore] can bring it back, until
     * [clean] deletes it. The result is null when the product has no photo.
     *
     * @throws IOException when the file cannot be renamed.
     */
    suspend fun remove(productId: String): RemovedPhoto? = mutex.withLock {
        readOnce()
        val photo = _photos.value[productId] ?: return@withLock null
        val removed = withContext(io) {
            val removed = File(photo.path + PhotoFiles.REMOVED)
            if (!File(photo.path).renameTo(removed)) throw IOException("The photo could not be removed")
            removed
        }
        _photos.value -= productId
        RemovedPhoto(productId, removed.path)
    }

    /**
     * Brings back a removed photo. A product that got a new photo after the remove keeps the new photo. The result is
     * true when the photo came back.
     */
    suspend fun restore(removed: RemovedPhoto): Boolean = mutex.withLock {
        readOnce()
        if (removed.productId in _photos.value) return@withLock false
        val target = File(removed.path.removeSuffix(PhotoFiles.REMOVED))
        val back = withContext(io) { File(removed.path).renameTo(target) }
        if (back) _photos.value += removed.productId to ProductPhoto(target.path)
        back
    }

    /**
     * Deletes each file that no product needs: the photos of the products that are not in [productIds], the removed
     * photos, the files of a write that stopped, and the older photos of a product. Call it only when no message with
     * "Undo" can bring back a product or a photo.
     */
    suspend fun clean(productIds: Set<String>) = mutex.withLock {
        readOnce()
        val kept = _photos.value.filterKeys { it in productIds }
        withContext(io) {
            val names = directory.list()?.toList().orEmpty()
            val current = kept.mapValues { File(it.value.path).name }
            PhotoFiles.unwanted(names, current, productIds).forEach { File(directory, it).delete() }
        }
        _photos.value = kept
    }

    private suspend fun readOnce() {
        if (read) return
        val found = withContext(io) {
            val files = directory.listFiles()?.map { PhotoFile(it.name, it.lastModified()) }.orEmpty()
            PhotoFiles.current(files).mapValues { ProductPhoto(File(directory, it.value).path) }
        }
        _photos.value = found
        read = true
    }
}
