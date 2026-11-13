package com.cvke.fridge.photo

/**
 * The photo of a product, as a file in the private files of the app.
 *
 * @property path The absolute path of the file. A new photo of the product has a new file, so a path names one picture.
 */
data class ProductPhoto(val path: String)

/**
 * A photo that the person removed. Its file stays, with the end [PhotoFiles.REMOVED], until the message with "Undo"
 * closes. Then [PhotoStore.clean] deletes it.
 */
data class RemovedPhoto(val productId: String, val path: String)

/** A file in the folder of the photos, with the time of its last change in milliseconds. */
data class PhotoFile(val name: String, val modified: Long)

/**
 * The rules for the files of the product photos. The core has no photo yet, so the app keeps the photos in its own
 * folder, with one file for each product: `<product>.<token>.jpg`. The product is the identifier of the product, and the
 * token is random, so that a new photo of a product has a new name. The shared specification must get a rule for the
 * photo of a product before the photos can go into the change log.
 */
object PhotoFiles {
    /** The end of the name of a photo. The app writes each photo as a JPEG. */
    const val EXTENSION = ".jpg"

    /** The end of the name of a photo that the app writes now. The file gets its name when it is complete. */
    const val TEMPORARY = ".tmp"

    /** The end of the name of a photo that the person removed. */
    const val REMOVED = ".removed"

    /** The longest edge of a photo, in pixels. A larger picture is made smaller. A phone screen needs no more. */
    const val MAX_EDGE = 1024

    /** The JPEG quality of a photo, from 0 to 100. */
    const val QUALITY = 85

    /** The name of the file of a photo of the product. Each token is an identifier of the rules of the core. */
    fun name(productId: String, token: String): String {
        require(isIdentifier(productId) && isIdentifier(token)) { "Not an identifier" }
        return "$productId.$token$EXTENSION"
    }

    /** The product of the file with this name, or null when the name is not the name of a photo. */
    fun productOf(name: String): String? {
        if (!name.endsWith(EXTENSION)) return null
        val parts = name.removeSuffix(EXTENSION).split('.')
        if (parts.size != 2 || !parts.all(::isIdentifier)) return null
        return parts[0]
    }

    /**
     * The photo of each product in the folder. When a product has more than one file, for example after a stop of the
     * app during a change of its photo, the newest file is its photo.
     */
    fun current(files: List<PhotoFile>): Map<String, String> = files
        .mapNotNull { file -> productOf(file.name)?.let { it to file } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, group) -> group.maxWith(compareBy<PhotoFile> { it.modified }.thenBy { it.name }).name }

    /**
     * The files that no product needs: a file that is not a photo (a temporary file, a removed photo, or another
     * file), a photo of a product that is not in [productIds], and a file of a product that is not its photo in
     * [current].
     */
    fun unwanted(names: List<String>, current: Map<String, String>, productIds: Set<String>): List<String> = names.filter { name ->
        val product = productOf(name)
        product == null || product !in productIds || current[product] != name
    }

    /**
     * The size of a photo for a picture of [width] x [height] pixels: the same shape, with the longest edge not more
     * than [maxEdge]. A smaller picture keeps its size.
     */
    fun targetSize(width: Int, height: Int, maxEdge: Int = MAX_EDGE): Pair<Int, Int> {
        require(width > 0 && height > 0) { "The picture has no size" }
        val longest = maxOf(width, height)
        if (longest <= maxEdge) return width to height
        val scale = maxEdge.toDouble() / longest
        return maxOf(1, Math.round(width * scale).toInt()) to maxOf(1, Math.round(height * scale).toInt())
    }

    /**
     * The sample size for a decode of a photo of [width] x [height] pixels that fills a box of [boxWidth] x [boxHeight]
     * pixels: the largest power of 2 that keeps each edge as large as the box or larger. The box crops the rest.
     */
    fun sampleSize(width: Int, height: Int, boxWidth: Int, boxHeight: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= boxWidth && height / (sample * 2) >= boxHeight) sample *= 2
        return sample
    }

    private fun isIdentifier(text: String): Boolean =
        text.length in 1..64 && text.all { it in 'a'..'z' || it in '0'..'9' || it == '-' }
}
