package com.cvke.fridge.data

import com.cvke.fridge.lookup.HttpResponse
import com.cvke.fridge.lookup.HttpTransport
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URI
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Sends the requests of the product lookup with the HTTPS client of Android. The app needs one GET request for each
 * lookup, so it has no HTTP library. The client of Android does the TLS, the compression, and the proxy of the
 * system. Only HTTPS is possible.
 *
 * @param timeoutMillis The longest time to connect, and the longest time to wait for data after that.
 */
class PlatformHttpTransport(
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val timeoutMillis: Int = 10_000,
) : HttpTransport {
    override suspend fun get(url: String, headers: Map<String, String>): HttpResponse = coroutineScope {
        val target = URI(url).toURL()
        require(target.protocol == "https") { "The product lookup uses only HTTPS" }
        // To open the connection object does not send data. The request starts when the code reads the status.
        val connection = target.openConnection() as HttpsURLConnection
        connection.connectTimeout = timeoutMillis
        connection.readTimeout = timeoutMillis
        connection.useCaches = false
        headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
        val request = async(io) { read(connection) }
        try {
            request.await()
        } catch (cancelled: CancellationException) {
            // A read that waits for the server does not stop for a cancellation. To close the connection stops it.
            connection.disconnect()
            throw cancelled
        }
    }

    private fun read(connection: HttpsURLConnection): HttpResponse = try {
        val status = connection.responseCode
        val stream: InputStream? = if (status >= 400) connection.errorStream else connection.inputStream
        HttpResponse(status, stream?.use(::readLimited).orEmpty())
    } finally {
        connection.disconnect()
    }

    /**
     * The text of the body, with not more than [MAX_BODY_BYTES]. An answer with only the fields of the app is much
     * smaller. A longer answer is cut, so it is not valid JSON, and the lookup gives a server failure.
     */
    private fun readLimited(stream: InputStream): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (out.size() < MAX_BODY_BYTES) {
            val count = stream.read(buffer, 0, minOf(buffer.size, MAX_BODY_BYTES - out.size()))
            if (count < 0) break
            out.write(buffer, 0, count)
        }
        return out.toByteArray().decodeToString()
    }

    private companion object {
        const val MAX_BODY_BYTES = 256 * 1024
    }
}
