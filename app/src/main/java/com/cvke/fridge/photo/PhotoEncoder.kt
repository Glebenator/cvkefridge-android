package com.cvke.fridge.photo

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.core.graphics.createBitmap
import java.io.IOException
import java.io.OutputStream

/**
 * Makes the JPEG of a product photo from a picture that the person selected. [ImageDecoder] reads the picture and
 * turns it as its EXIF orientation says, and it decodes it at the size of the photo, so a very large picture needs
 * little memory. The JPEG has no metadata: the location, the time, and the camera of the picture do not go into it.
 */
class PhotoEncoder(private val resolver: ContentResolver) {
    /**
     * Writes the photo of the picture at [uri] to [out]. The app reads the picture only during this call.
     *
     * @throws IOException when the picture cannot be read or is not a picture.
     * @throws SecurityException when the app cannot read the picture any more.
     */
    fun encode(uri: Uri, out: OutputStream) {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            val (width, height) = PhotoFiles.targetSize(info.size.width, info.size.height)
            decoder.setTargetSize(width, height)
            // A bitmap in memory, not in the graphics memory, so that the app can encode it.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
        }
        // JPEG has no transparency. A transparent part becomes white, not black.
        val opaque = if (bitmap.hasAlpha()) onWhite(bitmap) else bitmap
        try {
            if (!opaque.compress(Bitmap.CompressFormat.JPEG, PhotoFiles.QUALITY, out)) throw IOException("The photo could not be encoded")
        } finally {
            if (opaque !== bitmap) opaque.recycle()
            bitmap.recycle()
        }
    }

    private fun onWhite(bitmap: Bitmap): Bitmap {
        val result = createBitmap(bitmap.width, bitmap.height)
        Canvas(result).apply {
            drawColor(Color.WHITE)
            drawBitmap(bitmap, 0f, 0f, null)
        }
        return result
    }
}
