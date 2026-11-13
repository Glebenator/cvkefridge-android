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
 * The add form can have a photo before the product exists. [prepare] writes such a photo into [preparedDirectory], and
 * [adopt] makes it the photo of the product after the save.
 *
 * One change runs at a time. Each file operation is off the main thread.
 *
 * @param newToken Makes the random part of the name of a new photo.
 */
class PhotoStore(
    private val directory: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val newToken: () -> String = { UUID.randomUUID().toString() },
    private val preparedDirectory: File = File(directory.parentFile, directory.name + "-prepared"),
    private val now: () -> Long = System::currentTimeMillis,
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

    /**
     * Writes a photo for a product that does not exist yet: the photo of the add form. [encode] writes the JPEG. The
     * result is the file of the photo.
     *
     * @throws IOException when the file cannot be written, and each exception of [encode].
     */
    suspend fun prepare(encode: (OutputStream) -> Unit): PreparedPhoto = withContext(io) {
        preparedDirectory.mkdirs()
        val target = File(preparedDirectory, newToken() + PhotoFiles.EXTENSION)
        try {
            target.outputStream().use { stream ->
                stream.buffered().let { buffered ->
                    encode(buffered)
                    buffered.flush()
                }
            }
        } catch (failure: Exception) {
            target.delete()
            throw failure
        }
        PreparedPhoto(target.path)
    }

    /**
     * Makes a prepared photo the photo of the product, and deletes the prepared file.
     *
     * @throws IOException when the prepared file cannot be read or the photo cannot be written.
     */
    suspend fun adopt(productId: String, prepared: PreparedPhoto): ProductPhoto {
        val file = prepared.file(preparedDirectory) ?: throw IOException("Not a prepared photo")
        val photo = save(productId) { out -> file.inputStream().use { it.copyTo(out) } }
        discard(prepared)
        return photo
    }

    /** Deletes a prepared photo that no product got, for example after "Remove" in the add form. */
    suspend fun discard(prepared: PreparedPhoto) {
        withContext(io) { prepared.file(preparedDirectory)?.delete() }
    }

    /** Deletes the prepared photos that are older than one day: the photos of forms that closed with no save. */
    suspend fun cleanPrepared() = withContext(io) {
        val limit = now() - PREPARED_MAX_AGE_MILLIS
        preparedDirectory.listFiles()?.filter { it.lastModified() < limit }?.forEach { it.delete() }
    }

    /** The file of a prepared photo, only when it is in the folder of the prepared photos. */
    private fun PreparedPhoto.file(folder: File): File? = File(path).takeIf { it.parentFile?.canonicalPath == folder.canonicalPath }

    private suspend fun readOnce() {
        if (read) return
        val found = withContext(io) {
            val files = directory.listFiles()?.map { PhotoFile(it.name, it.lastModified()) }.orEmpty()
            PhotoFiles.current(files).mapValues { ProductPhoto(File(directory, it.value).path) }
        }
        _photos.value = found
        read = true
    }

    private companion object {
        const val PREPARED_MAX_AGE_MILLIS = 24L * 60 * 60 * 1000
    }
}
