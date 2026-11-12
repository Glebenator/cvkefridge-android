package com.cvke.fridge.data

import android.util.AtomicFile
import com.cvke.fridge.core.Change
import com.cvke.fridge.core.ChangeCodec
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The change log on the phone: one file with one change on each line, as JSON from [ChangeCodec].
 *
 * The inventory is the replay of this log. Each write makes a new file and then replaces the old file, so a write that
 * stops in the middle leaves the old log.
 */
class ChangeLogStore(file: File, private val io: CoroutineDispatcher = Dispatchers.IO) {
    private val file = AtomicFile(file)
    private val mutex = Mutex()

    /**
     * All changes, in the order of the file. A log that does not exist is empty.
     *
     * @throws IOException when the file cannot be read.
     * @throws IllegalArgumentException when a line is not a change.
     */
    suspend fun read(): List<Change> = mutex.withLock { withContext(io) { readLines() } }

    /** Adds the changes to the end of the log. */
    suspend fun append(changes: List<Change>) {
        if (changes.isEmpty()) return
        mutex.withLock { withContext(io) { write(readLines() + changes) } }
    }

    /** Replaces the log with the changes. An empty list removes the log. */
    suspend fun replace(changes: List<Change>) = mutex.withLock {
        withContext(io) { if (changes.isEmpty()) file.delete() else write(changes) }
    }

    private fun readLines(): List<Change> {
        val text = try {
            file.readFully().decodeToString()
        } catch (_: FileNotFoundException) {
            return emptyList()
        }
        return text.lineSequence().filter { it.isNotBlank() }.map(ChangeCodec::decode).toList()
    }

    private fun write(changes: List<Change>) {
        val stream = file.startWrite()
        try {
            stream.write(changes.joinToString(separator = "") { ChangeCodec.encode(it) + "\n" }.encodeToByteArray())
            file.finishWrite(stream)
        } catch (failure: IOException) {
            file.failWrite(stream)
            throw failure
        }
    }
}
