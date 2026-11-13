package com.cvke.fridge.photo

import android.net.Uri
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The files into which the camera app writes a picture for a product photo. Each picture gets a new file in a
 * [directory] of the cache of the app, and the camera app gets its address through the FileProvider of the app
 * ([authority]). The app reads the picture one time and makes the photo from it (see [PhotoEncoder]). Then the file goes.
 *
 * A file can stay when the app stops while the camera app is open and the person does not come back. [clean] deletes
 * such files at the next start.
 *
 * @param uriOf Gives the address of a file through the FileProvider.
 */
class CameraCaptures(
    private val directory: File,
    private val authority: String,
    private val uriOf: (File) -> Uri,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** Makes the address of a new file for a picture. The camera app makes the file. */
    suspend fun newCapture(): Uri = withContext(io) {
        directory.mkdirs()
        uriOf(File(directory, "${UUID.randomUUID()}$EXTENSION"))
    }

    /** Deletes the file of a picture of the camera. The address of a picture from another source changes nothing. */
    suspend fun discard(uri: Uri) {
        if (uri.authority != authority) return
        val name = uri.lastPathSegment ?: return
        if (!name.endsWith(EXTENSION) || '/' in name) return
        withContext(io) { File(directory, name).delete() }
    }

    /** Deletes the pictures that are older than one day. A picture of now can still come back from the camera app. */
    suspend fun clean() = withContext(io) {
        val limit = now() - MAX_AGE_MILLIS
        directory.listFiles()?.filter { it.lastModified() < limit }?.forEach { it.delete() }
    }

    private companion object {
        const val EXTENSION = ".jpg"
        const val MAX_AGE_MILLIS = 24L * 60 * 60 * 1000
    }
}
