package com.cvke.fridge.photo

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decodes the product photos for the screens, off the main thread, at the size that a screen shows, and keeps them in
 * a small cache in memory. A photo is a JPEG of the app of not more than 1024 pixels on the phone, so a decode with a
 * sample size is enough. The app needs no library to load images.
 */
class PhotoLoader(private val io: CoroutineDispatcher = Dispatchers.IO) {
    /** The cache, in kilobytes: an eighth of the memory of the app. */
    private val cache = object : LruCache<String, ImageBitmap>((Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.asAndroidBitmap().allocationByteCount / 1024
    }

    /** The decoded photo for a box of [width] x [height] pixels, when it is in the cache. It does not read the disk. */
    fun cached(photo: ProductPhoto, width: Int, height: Int): ImageBitmap? = cache.get(key(photo, width, height))

    /** The photo for a box of [width] x [height] pixels. The result is null when the file cannot be read. */
    suspend fun load(photo: ProductPhoto, width: Int, height: Int): ImageBitmap? {
        val key = key(photo, width, height)
        cache.get(key)?.let { return it }
        return withContext(io) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(photo.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
            val options = BitmapFactory.Options().apply {
                inSampleSize = PhotoFiles.sampleSize(bounds.outWidth, bounds.outHeight, width, height)
            }
            val bitmap = BitmapFactory.decodeFile(photo.path, options) ?: return@withContext null
            // Sends the bitmap to the graphics memory now, and not during the first frame that draws it.
            bitmap.prepareToDraw()
            bitmap.asImageBitmap().also { cache.put(key, it) }
        }
    }

    private fun key(photo: ProductPhoto, width: Int, height: Int) = "${photo.path}@${width}x$height"
}
