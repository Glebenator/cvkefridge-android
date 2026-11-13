package com.cvke.fridge

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.graphics.createBitmap
import androidx.test.runner.AndroidJUnitRunner
import com.cvke.fridge.lookup.HttpResponse
import com.cvke.fridge.lookup.HttpTransport
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Starts the app of the device tests: the real app, with [FakeServer] in the place of the internet. The device tests
 * never send a request to Open Food Facts.
 */
class DeviceTestRunner : AndroidJUnitRunner() {
    override fun newApplication(classLoader: ClassLoader, className: String, context: Context): Application =
        super.newApplication(classLoader, DeviceTestApplication::class.java.name, context)
}

/**
 * The app with [FakeServer] as the connection of the product lookup, [TestPhotoPicker] as the photo picker, and
 * [TestPhotoCamera] as the camera app. All other objects are the real ones.
 */
class DeviceTestApplication : FridgeApplication() {
    override fun httpTransport(): HttpTransport = FakeServer

    override fun photoPicker(): ActivityResultContract<Unit, Uri?> = TestPhotoPicker

    override fun photoCamera(): ActivityResultContract<Uri, Boolean> = TestPhotoCamera
}

/**
 * The camera app in the device tests. It opens no screen: it writes a picture of 1200 x 1600 pixels into the file that
 * the app gives, through the FileProvider of the app, as a camera app does. The app then makes the photo through the
 * production code path.
 */
object TestPhotoCamera : ActivityResultContract<Uri, Boolean>() {
    override fun createIntent(context: Context, input: Uri): Intent = Intent()

    override fun getSynchronousResult(context: Context, input: Uri): SynchronousResult<Boolean> {
        val picture = createBitmap(1200, 1600).apply { eraseColor(Color.rgb(60, 120, 200)) }
        checkNotNull(context.contentResolver.openOutputStream(input)).use { picture.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        picture.recycle()
        return SynchronousResult(true)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean = false
}

/**
 * The source of a picture in the device tests. It opens no screen: it writes a picture of 1600 x 1200 pixels into the
 * cache of the app and gives its address at once. The app then makes the photo through the production code path. The
 * device tests do not drive the photo picker of Android, which is a different app.
 */
object TestPhotoPicker : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit): Intent = Intent()

    override fun getSynchronousResult(context: Context, input: Unit): SynchronousResult<Uri?> {
        val file = File(context.cacheDir, "test-picture.jpg")
        val picture = createBitmap(1600, 1200).apply { eraseColor(Color.rgb(200, 60, 60)) }
        file.outputStream().use { picture.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        picture.recycle()
        return SynchronousResult(Uri.fromFile(file))
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? = null
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
