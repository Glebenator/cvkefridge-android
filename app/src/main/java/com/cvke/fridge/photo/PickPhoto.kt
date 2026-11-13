package com.cvke.fridge.photo

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia

/**
 * Opens the photo picker of Android for one picture. The picker gives the app only the picture that the person
 * selects, so the app needs no storage permission. On a phone with no photo picker, Android opens its document picker,
 * which also needs no permission. The result is the address of the picture, or null when the person selected nothing.
 * The app reads the picture one time and keeps a copy (see [PhotoEncoder]).
 */
class PickPhoto : ActivityResultContract<Unit, Uri?>() {
    private val picker = PickVisualMedia()
    private val request = PickVisualMediaRequest.Builder().setMediaType(PickVisualMedia.ImageOnly).build()

    override fun createIntent(context: Context, input: Unit): Intent = picker.createIntent(context, request)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? = picker.parseResult(resultCode, intent)
}
