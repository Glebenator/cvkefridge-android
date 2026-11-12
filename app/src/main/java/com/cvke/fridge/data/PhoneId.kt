package com.cvke.fridge.data

import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The identifier of this phone: the origin of the changes that it makes. It is a random identifier that the app makes
 * one time and keeps in a file in its private files. It does not come from the hardware or from an account.
 */
class PhoneId(file: File, private val io: CoroutineDispatcher = Dispatchers.IO) {
    private val file = AtomicFile(file)
    private val mutex = Mutex()
    private var known: String? = null

    /**
     * The identifier. The first call makes it.
     *
     * @throws IOException when the file cannot be read or written.
     */
    suspend fun get(): String = mutex.withLock {
        known ?: withContext(io) { read() ?: make() }.also { known = it }
    }

    private fun read(): String? = try {
        file.readFully().decodeToString().trim().takeIf(::isIdentifier)
    } catch (_: FileNotFoundException) {
        null
    }

    private fun make(): String {
        val id = UUID.randomUUID().toString()
        val stream = file.startWrite()
        try {
            stream.write(id.encodeToByteArray())
            file.finishWrite(stream)
        } catch (failure: IOException) {
            file.failWrite(stream)
            throw failure
        }
        return id
    }

    /** An identifier has 1 to 64 characters from `a` to `z`, `0` to `9`, and `-` (rule 1 of `spec/rules.md`). */
    private fun isIdentifier(text: String) = text.length in 1..64 && text.all { it in 'a'..'z' || it in '0'..'9' || it == '-' }
}
