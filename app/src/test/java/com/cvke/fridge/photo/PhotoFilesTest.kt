package com.cvke.fridge.photo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The names of the photo files, the files that no product needs, and the sizes of a photo. */
class PhotoFilesTest {
    @Test
    fun theNameOfAPhotoHasTheProductAndARandomToken() {
        assertEquals("sample-milk.3f2a-77.jpg", PhotoFiles.name("sample-milk", "3f2a-77"))
        assertEquals("sample-milk", PhotoFiles.productOf("sample-milk.3f2a-77.jpg"))
        // A temporary file, a removed photo, and other files are not photos.
        listOf(
            "sample-milk.jpg",
            "sample-milk.a.b.jpg",
            "Milk.a.jpg",
            "milk..jpg",
            "milk.a.jpg.tmp",
            "milk.a.jpg.removed",
            "milk.a.png",
            "notes.txt",
        ).forEach { assertNull(it, PhotoFiles.productOf(it)) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun aNameNeedsAnIdentifier() {
        PhotoFiles.name("milk/../x", "a")
    }

    @Test
    fun theNewestFileOfAProductIsItsPhoto() {
        val files = listOf(
            PhotoFile("milk.old.jpg", modified = 10),
            PhotoFile("milk.new.jpg", modified = 20),
            PhotoFile("eggs.a.jpg", modified = 5),
            PhotoFile("eggs.b.jpg.tmp", modified = 30),
        )
        assertEquals(mapOf("milk" to "milk.new.jpg", "eggs" to "eggs.a.jpg"), PhotoFiles.current(files))
    }

    @Test
    fun theFilesThatNoProductNeedsAreTheOrphans() {
        val names = listOf(
            "milk.new.jpg",
            "milk.old.jpg",
            "eggs.a.jpg",
            "gone.a.jpg",
            "milk.x.jpg.removed",
            "milk.y.jpg.tmp",
            "notes.txt",
        )
        val current = mapOf("milk" to "milk.new.jpg", "eggs" to "eggs.a.jpg")
        assertEquals(
            listOf("milk.old.jpg", "gone.a.jpg", "milk.x.jpg.removed", "milk.y.jpg.tmp", "notes.txt"),
            PhotoFiles.unwanted(names, current, productIds = setOf("milk", "eggs")),
        )
        // A product that is not in the inventory loses its photo.
        assertEquals(listOf("eggs.a.jpg"), PhotoFiles.unwanted(listOf("milk.new.jpg", "eggs.a.jpg"), current, setOf("milk")))
    }

    @Test
    fun aLargePictureGetsALongestEdgeOf1024AndKeepsItsShape() {
        assertEquals(1024 to 768, PhotoFiles.targetSize(4000, 3000))
        assertEquals(768 to 1024, PhotoFiles.targetSize(3000, 4000))
        assertEquals(1024 to 1, PhotoFiles.targetSize(12_000, 10))
        // A small picture keeps its size.
        assertEquals(800 to 600, PhotoFiles.targetSize(800, 600))
    }

    @Test
    fun theSampleSizeKeepsEachEdgeAsLargeAsTheBox() {
        // A thumbnail of 126 px: 1024 x 768 / 4 is 256 x 192. Divided by 8, the short edge is too small.
        assertEquals(4, PhotoFiles.sampleSize(1024, 768, 126, 126))
        assertEquals(1, PhotoFiles.sampleSize(1024, 768, 945, 709))
        assertEquals(16, PhotoFiles.sampleSize(4000, 3000, 100, 100))
        assertEquals(1, PhotoFiles.sampleSize(50, 50, 100, 100))
    }
}
