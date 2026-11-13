package com.cvke.fridge.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.cvke.fridge.R
import kotlinx.coroutines.launch

/**
 * The two sources of a product photo: the camera app of the phone, and the photo picker of Android.
 *
 * @property canTake False when the phone has no camera or no camera app. Then the screens show only "Choose a photo".
 * @property take Takes a picture with the camera app.
 * @property choose Opens the photo picker.
 */
class PhotoSources(val canTake: Boolean, val take: () -> Unit, val choose: () -> Unit)

/** What the camera access asks of the person now. */
private enum class CameraDialog { NONE, EXPLAIN, BLOCKED }

/**
 * The sources of a product photo, with their dialogs. [onPicture] gets the address of the picture.
 *
 * The photo picker needs no permission. The camera app needs the camera permission, because the manifest declares it
 * for the scanner: Android refuses the camera app until the person allows the camera. Before Android asks, a dialog
 * explains why. When Android does not ask again, a dialog says so and opens the settings of the app. The person can
 * always choose a photo instead.
 *
 * The camera app writes the picture into a file of the app (see [com.cvke.fridge.photo.CameraCaptures]). The address
 * of the file stays when Android stops the app while the camera app is open, so the picture is not lost.
 *
 * @param pickPhoto Opens the photo picker. The device tests give a source with no screen.
 * @param takePhoto Opens the camera app with the address of the file for the picture. The device tests give a camera
 *   with no screen.
 * @param newCapture Makes the address of a new file for a picture.
 */
@Composable
fun rememberPhotoSources(
    pickPhoto: ActivityResultContract<Unit, Uri?>,
    takePhoto: ActivityResultContract<Uri, Boolean>,
    newCapture: suspend () -> Uri,
    onPicture: (Uri) -> Unit,
): PhotoSources {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val canTake = remember(context) {
        val manager = context.packageManager
        manager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) &&
            Intent(MediaStore.ACTION_IMAGE_CAPTURE).resolveActivity(manager) != null
    }
    var dialog by rememberSaveable { mutableStateOf(CameraDialog.NONE) }
    var capture by rememberSaveable { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(pickPhoto) { uri -> if (uri != null) onPicture(uri) }
    val camera = rememberLauncherForActivityResult(takePhoto) { taken ->
        val uri = capture?.let(Uri::parse)
        capture = null
        // With no picture, the empty file goes at the next start.
        if (taken && uri != null) onPicture(uri)
    }
    fun startCamera() {
        scope.launch {
            val uri = newCapture()
            capture = uri.toString()
            try {
                camera.launch(uri)
            } catch (_: ActivityNotFoundException) {
                capture = null
            }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        when {
            allowed -> startCamera()
            // Android did not show the question, or the person selected "Don't ask again".
            activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == false -> dialog = CameraDialog.BLOCKED
        }
    }
    fun granted() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    when (dialog) {
        CameraDialog.NONE -> Unit
        CameraDialog.EXPLAIN -> AlertDialog(
            onDismissRequest = { dialog = CameraDialog.NONE },
            modifier = Modifier.testTag("photo_camera_explain"),
            icon = { Icon(painterResource(R.drawable.ic_camera), contentDescription = null) },
            title = { Text(stringResource(R.string.photo_camera_title)) },
            text = { Text(stringResource(R.string.photo_camera_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        dialog = CameraDialog.NONE
                        permission.launch(Manifest.permission.CAMERA)
                    },
                    Modifier.testTag("photo_camera_continue"),
                ) { Text(stringResource(R.string.action_continue)) }
            },
            dismissButton = { TextButton(onClick = { dialog = CameraDialog.NONE }) { Text(stringResource(R.string.action_not_now)) } },
        )
        CameraDialog.BLOCKED -> AlertDialog(
            onDismissRequest = { dialog = CameraDialog.NONE },
            modifier = Modifier.testTag("photo_camera_blocked"),
            icon = { Icon(painterResource(R.drawable.ic_camera_off), contentDescription = null) },
            title = { Text(stringResource(R.string.camera_denied_title)) },
            text = { Text(stringResource(R.string.photo_camera_blocked_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        dialog = CameraDialog.NONE
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                    },
                    Modifier.testTag("photo_camera_settings"),
                ) { Text(stringResource(R.string.camera_open_settings)) }
            },
            dismissButton = { TextButton(onClick = { dialog = CameraDialog.NONE }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    return PhotoSources(
        canTake = canTake,
        take = { if (granted()) startCamera() else dialog = CameraDialog.EXPLAIN },
        choose = { picker.launch(Unit) },
    )
}

/** "Take a photo" and "Choose a photo", for a product with no photo. With no camera, only "Choose a photo". */
@Composable
fun PhotoButtons(sources: PhotoSources, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (sources.canTake) {
            PhotoButton(R.drawable.ic_camera, R.string.photo_take, "photo_take", sources.take)
        }
        PhotoButton(R.drawable.ic_image_plus, R.string.photo_choose, "photo_choose", sources.choose)
    }
}

/** "Change photo", with a menu of the two sources. With no camera, it opens the photo picker at once. */
@Composable
fun ChangePhotoButton(sources: PhotoSources) {
    var open by remember { mutableStateOf(false) }
    Box {
        PhotoButton(R.drawable.ic_image_edit, R.string.photo_change, "photo_change") {
            if (sources.canTake) open = true else sources.choose()
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.photo_take)) },
                onClick = {
                    open = false
                    sources.take()
                },
                leadingIcon = { Icon(painterResource(R.drawable.ic_camera), contentDescription = null) },
                modifier = Modifier.testTag("photo_change_take"),
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.photo_choose)) },
                onClick = {
                    open = false
                    sources.choose()
                },
                leadingIcon = { Icon(painterResource(R.drawable.ic_image_plus), contentDescription = null) },
                modifier = Modifier.testTag("photo_change_choose"),
            )
        }
    }
}

/** A text button of a photo, with its icon. */
@Composable
fun PhotoButton(@DrawableRes icon: Int, @StringRes label: Int, tag: String, onClick: () -> Unit) {
    TextButton(onClick, Modifier.testTag(tag), contentPadding = ButtonDefaults.TextButtonWithIconContentPadding) {
        Icon(painterResource(icon), contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(stringResource(label))
    }
}
