package com.cvke.fridge

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import com.cvke.fridge.lookup.HttpResponse
import com.cvke.fridge.lookup.HttpTransport
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Starts the app of the device tests: the real app, with [FakeServer] in the place of the internet. The device tests
 * never send a request to Open Food Facts.
 */
class DeviceTestRunner : AndroidJUnitRunner() {
    override fun newApplication(classLoader: ClassLoader, className: String, context: Context): Application =
        super.newApplication(classLoader, DeviceTestApplication::class.java.name, context)
}

/** The app with [FakeServer] as the connection of the product lookup. All other objects are the real ones. */
class DeviceTestApplication : FridgeApplication() {
    override fun httpTransport(): HttpTransport = FakeServer
}

/** The server of the device tests. A test sets the [answer], and reads the [requests]. */
object FakeServer : HttpTransport {
    /** The URLs and the headers of the requests, in their order. */
    val requests = CopyOnWriteArrayList<Pair<String, Map<String, String>>>()

    /** Gives the answer for a URL, or throws an [java.io.IOException] for a failed connection. */
    @Volatile
    var answer: (url: String) -> HttpResponse = { throw java.net.UnknownHostException("No answer in this test") }

    override suspend fun get(url: String, headers: Map<String, String>): HttpResponse {
        requests += url to headers
        return answer(url)
    }

    fun reset() {
        requests.clear()
        answer = { throw java.net.UnknownHostException("No answer in this test") }
    }
}
