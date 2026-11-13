package com.cvke.fridge.photo

import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The files of the photo store in a folder on the computer: save, remove, undo, read again, and clean. */
class PhotoStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val directory: File get() = File(folder.root, "photos")
    private var tokens = 0

    private fun store() = PhotoStore(directory, newToken = { "t${++tokens}" })

    private fun names() = directory.list()?.sorted().orEmpty()

    private fun bytes(text: String): (java.io.OutputStream) -> Unit = { it.write(text.encodeToByteArray()) }

    @Test
    fun aNewPhotoReplacesTheOldOneAndANewStoreReadsIt() = runBlocking {
        val store = store()
        store.read()
        val first = store.save("milk", bytes("first"))
        assertEquals(File(directory, "milk.t1.jpg").path, first.path)
        val second = store.save("milk", bytes("second"))
        assertEquals(listOf("milk.t2.jpg"), names())
        assertEquals("second", File(second.path).readText())
        assertEquals(mapOf("milk" to second), store.photos.value)

        // After a restart of the app, a new store finds the photo.
        val again = store()
        assertEquals(emptyMap<String, ProductPhoto>(), again.photos.value)
        again.read()
        assertEquals(mapOf("milk" to second), again.photos.value)
    }

    @Test
    fun aWriteThatFailsKeepsThePhotoAndLeavesNoFile() = runBlocking {
        val store = store()
        val photo = store.save("milk", bytes("good"))
        try {
            store.save("milk") { throw IOException("The picture cannot be read") }
        } catch (_: IOException) {
            // The failure goes to the caller.
        }
        assertEquals(listOf("milk.t1.jpg"), names())
        assertEquals(mapOf("milk" to photo), store.photos.value)
    }

    @Test
    fun aRemovedPhotoComesBackWithUndoAndTheCleanupDeletesIt() = runBlocking {
        val store = store()
        val photo = store.save("milk", bytes("photo"))
        val removed = checkNotNull(store.remove("milk"))
        assertEquals(emptyMap<String, ProductPhoto>(), store.photos.value)
        assertEquals(listOf("milk.t1.jpg.removed"), names())
        assertNull(store.remove("milk"))

        assertTrue(store.restore(removed))
        assertEquals(mapOf("milk" to photo), store.photos.value)

        // When the message with "Undo" closed, the cleanup deletes the removed file.
        val again = checkNotNull(store.remove("milk"))
        store.clean(setOf("milk"))
        assertEquals(emptyList<String>(), names())
        assertFalse(store.restore(again))
    }

    @Test
    fun anUndoDoesNotReplaceANewerPhoto() = runBlocking {
        val store = store()
        store.save("milk", bytes("old"))
        val removed = checkNotNull(store.remove("milk"))
        val newer = store.save("milk", bytes("new"))
        assertFalse(store.restore(removed))
        assertEquals(mapOf("milk" to newer), store.photos.value)
    }

    @Test
    fun theCleanupDeletesTheFilesThatNoProductNeeds() = runBlocking {
        val store = store()
        store.save("milk", bytes("milk"))
        store.save("eggs", bytes("eggs"))
        // Files of a write that stopped, of an older photo, and of another program.
        File(directory, "milk.t9.jpg.tmp").writeText("half")
        File(directory, "milk.old.jpg").apply {
            writeText("old")
            setLastModified(0)
        }
        File(directory, "notes.txt").writeText("?")

        // Eggs was deleted from the inventory.
        store.clean(setOf("milk"))
        assertEquals(listOf("milk.t1.jpg"), names())
        assertEquals(setOf("milk"), store.photos.value.keys)
    }
}
