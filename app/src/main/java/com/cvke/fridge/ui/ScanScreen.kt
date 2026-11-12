package com.cvke.fridge.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.viewfinder.compose.MutableCoordinateTransformer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cvke.fridge.R
import com.cvke.fridge.lookup.Barcodes
import com.cvke.fridge.lookup.LookupFailure
import com.cvke.fridge.lookup.ScanSource
import com.cvke.fridge.lookup.Scanned
import com.cvke.fridge.scan.BarcodeCamera
import com.cvke.fridge.scan.CameraStatus
import com.cvke.fridge.scan.ScanDebouncer
import com.cvke.fridge.ui.theme.BrandSpinner
import kotlinx.coroutines.delay

/** What the scanner can do with the camera. */
private enum class CameraAccess {
    /** The device has no camera. */
    NONE,

    /** The app did not ask for the camera yet. The screen explains the permission before Android asks. */
    ASK,

    /** The person did not allow the camera. The app can ask again. */
    DENIED,

    /** The person did not allow the camera, and Android does not ask again. Only the settings of the app can change it. */
    BLOCKED,
    GRANTED,
}

/**
 * The barcode scanner. The camera reads the barcode, and the app looks up the product. The add form then opens with
 * the values of the lookup. The person can also type the barcode, or add the food by hand. These two buttons are
 * always there, so the scanner works with no camera and with no permission.
 *
 * Before Android asks for the camera, the screen explains why the app needs it. When the camera is off, the screen
 * says so, with the action that turns it on. The panel below the camera says what leaves the phone: the barcode
 * number goes to Open Food Facts, and nothing else.
 *
 * A read gives a short vibration, and screen readers hear the lookup. One package gives one scan
 * ([ScanDebouncer]). During a lookup and after a failed lookup the camera does not read.
 *
 * The snackbar is at the bottom of the camera area, above the panel. After "Save and scan next", the message "… added"
 * with "Undo" then does not cover the buttons of the panel, and "Undo" stays available.
 */
@Composable
fun ScanScreen(
    lookup: LookupState,
    snackbar: SnackbarHostState,
    onLookUp: (barcode: String) -> Unit,
    onCancelLookup: () -> Unit,
    onLookupShown: (id: Long) -> Unit,
    onOpenForm: (Scanned, productId: String?) -> Unit,
    onAddByHand: () -> Unit,
    onClose: () -> Unit,
) {
    val (access, requestCamera) = rememberCameraAccess()
    var typing by rememberSaveable { mutableStateOf(false) }
    // The last barcode that the scanner looked up. When the scanner opens again, it ignores this barcode for a short time.
    var lastBarcode by rememberSaveable { mutableStateOf<String?>(null) }
    val idle = lookup is LookupState.Idle && !typing

    LaunchedEffect(lookup) {
        if (lookup is LookupState.Done) {
            onLookupShown(lookup.id)
            onOpenForm(lookup.scanned, lookup.productId)
        }
    }
    fun lookUp(barcode: String) {
        lastBarcode = barcode
        onLookUp(barcode)
    }

    val haptics = LocalHapticFeedback.current
    val debouncer = remember { ScanDebouncer() }
    LaunchedEffect(idle) { if (idle) debouncer.ignore(lastBarcode, SystemClock.elapsedRealtime()) }
    val onRead by rememberUpdatedState { barcode: String ->
        val counted = if (idle) debouncer.read(barcode, SystemClock.elapsedRealtime()) else null
        if (counted != null) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            lookUp(counted)
        }
    }
    val camera = remember { BarcodeCamera { onRead(it) } }
    DisposableEffect(camera) { onDispose { camera.close() } }
    SideEffect { camera.isPaused = !idle }
    val torchAvailable by camera.hasTorch.collectAsStateWithLifecycle()
    val torchOn by camera.torchOn.collectAsStateWithLifecycle()

    if (typing) {
        BarcodeDialog(
            onLookUp = { barcode ->
                typing = false
                lookUp(barcode)
            },
            onDismiss = { typing = false },
        )
    }

    SubScreen(
        title = stringResource(R.string.scan_title),
        onBack = onClose,
        // The snackbar is above the panel, not at the bottom edge of the screen.
        snackbar = null,
        tag = "scan_screen",
        close = true,
        actions = {
            if (access == CameraAccess.GRANTED && torchAvailable) {
                IconButton(onClick = { camera.setTorch(!torchOn) }, Modifier.testTag("torch")) {
                    Icon(
                        painterResource(if (torchOn) R.drawable.ic_flashlight_off else R.drawable.ic_flashlight),
                        contentDescription = stringResource(if (torchOn) R.string.torch_off else R.string.torch_on),
                    )
                }
            }
        },
    ) { padding ->
        // The panel goes to the bottom edge, or to the end edge, and keeps its content clear of the system bars.
        BoxWithConstraints(
            Modifier
                .padding(top = padding.calculateTopPadding())
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        ) {
            val width = maxWidth
            val height = maxHeight
            val wide = width > height
            val cameraArea = @Composable { modifier: Modifier ->
                Box(modifier) {
                    if (access == CameraAccess.GRANTED) {
                        CameraView(camera, idle)
                    } else {
                        AccessMessage(access, requestCamera)
                    }
                    // On a wide screen the camera area goes to the bottom edge, so the snackbar keeps clear of the
                    // navigation bar there.
                    SnackbarHost(
                        snackbar,
                        Modifier
                            .align(Alignment.BottomCenter)
                            .then(if (wide) Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)) else Modifier)
                            .testTag("scan_snackbar"),
                    )
                }
            }
            val panel = @Composable { modifier: Modifier ->
                Panel(
                    lookup = lookup,
                    onTypeBarcode = { typing = true },
                    onAddByHand = onAddByHand,
                    onCancel = onCancelLookup,
                    onTryAgain = { (lookup as? LookupState.Failed)?.let { lookUp(it.barcode) } },
                    onEnterByHand = {
                        (lookup as? LookupState.Failed)?.let { failed ->
                            onCancelLookup()
                            onOpenForm(Scanned(failed.barcode, ScanSource.NOT_LOOKED_UP), null)
                        }
                    },
                    modifier = modifier,
                )
            }
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    cameraArea(Modifier.weight(1f).fillMaxHeight())
                    panel(Modifier.width(minOf(width * 0.45f, 420.dp)).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    cameraArea(Modifier.weight(1f).fillMaxWidth())
                    panel(Modifier.fillMaxWidth().heightIn(max = height * 0.6f))
                }
            }
        }
    }
}

/**
 * The camera access of the scanner, and the function that asks Android for it. The access is read again each time
 * the screen comes back, for example from the settings of the app.
 */
@Composable
private fun rememberCameraAccess(): Pair<CameraAccess, () -> Unit> {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val hasCamera = remember(context) { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    fun isGranted() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    fun mayAskAgain() = activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == true

    var granted by remember { mutableStateOf(isGranted()) }
    // True after the person did not allow the camera: in an earlier session (Android then permits a second
    // question), or in the question of this screen.
    var refused by rememberSaveable { mutableStateOf(false) }
    var blocked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        granted = allowed
        if (!allowed) {
            refused = true
            blocked = !mayAskAgain()
        }
    }
    LifecycleResumeEffect(Unit) {
        granted = isGranted()
        if (granted) blocked = false
        if (mayAskAgain()) refused = true
        onPauseOrDispose {}
    }
    val access = when {
        !hasCamera -> CameraAccess.NONE
        granted -> CameraAccess.GRANTED
        blocked -> CameraAccess.BLOCKED
        refused -> CameraAccess.DENIED
        else -> CameraAccess.ASK
    }
    return access to { launcher.launch(Manifest.permission.CAMERA) }
}

/**
 * The picture of the camera with the scan window. A tap focuses the camera on that point. After some seconds with no
 * barcode, the hint below the window says what helps.
 */
@Composable
private fun CameraView(camera: BarcodeCamera, reading: Boolean) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(camera, owner) { camera.run(context.applicationContext, owner) }
    DisplayRotation(camera::setTargetRotation)
    val request by camera.surfaceRequest.collectAsStateWithLifecycle()
    val status by camera.status.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme

    if (status == CameraStatus.FAILED) {
        Message(R.drawable.ic_camera_off, stringResource(R.string.camera_failed_title), stringResource(R.string.camera_failed_body), "camera_failed")
        return
    }
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(status, reading) {
        slow = false
        if (status == CameraStatus.RUNNING && reading) {
            delay(SLOW_HINT_MILLIS)
            slow = true
        }
    }
    val description = stringResource(R.string.scan_camera_description)
    Box(Modifier.fillMaxSize().background(scheme.scrim).semantics { contentDescription = description }.testTag("camera")) {
        request?.let { surface ->
            val transformer = remember { MutableCoordinateTransformer() }
            CameraXViewfinder(
                surfaceRequest = surface,
                modifier = Modifier.fillMaxSize().pointerInput(camera, transformer) {
                    detectTapGestures { tap ->
                        val point = with(transformer) { tap.transform() }
                        camera.focus(point.x, point.y)
                    }
                },
                coordinateTransformer = transformer,
            )
        }
        ScanWindow()
        Surface(
            Modifier.align(Alignment.BottomCenter).padding(16.dp).widthIn(max = 480.dp),
            shape = MaterialTheme.shapes.large,
            color = scheme.inverseSurface,
            contentColor = scheme.inverseOnSurface,
        ) {
            Text(
                stringResource(if (slow) R.string.scan_hint_slow else R.string.scan_hint),
                Modifier.padding(horizontal = 16.dp, vertical = 10.dp).semantics { liveRegion = LiveRegionMode.Polite }.testTag("scan_hint"),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Gives the rotation of the display to [onRotation] now and after each change, also a turn by 180 degrees. */
@Composable
private fun DisplayRotation(onRotation: (Int) -> Unit) {
    val view = LocalView.current
    val update by rememberUpdatedState(onRotation)
    DisposableEffect(view) {
        val displays = checkNotNull(view.context.getSystemService(DisplayManager::class.java))
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit

            override fun onDisplayRemoved(displayId: Int) = Unit

            override fun onDisplayChanged(displayId: Int) {
                val display = view.display ?: return
                if (display.displayId == displayId) update(display.rotation)
            }
        }
        view.display?.let { update(it.rotation) }
        displays.registerDisplayListener(listener, null)
        onDispose { displays.unregisterDisplayListener(listener) }
    }
}

/** The window in the middle of the camera picture: the picture outside it is darker, and the window has a frame. */
@Composable
private fun ScanWindow() {
    val scheme = MaterialTheme.colorScheme
    Canvas(Modifier.fillMaxSize()) {
        val width = minOf(size.width * 0.8f, 340.dp.toPx())
        val height = minOf(width * 0.55f, size.height * 0.5f)
        val window = RoundRect(
            left = (size.width - width) / 2,
            top = (size.height - height) / 2,
            right = (size.width + width) / 2,
            bottom = (size.height + height) / 2,
            cornerRadius = CornerRadius(16.dp.toPx()),
        )
        val outside = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(Offset.Zero, Size(size.width, size.height)))
            addRoundRect(window)
        }
        drawPath(outside, scheme.scrim.copy(alpha = 0.45f))
        drawPath(Path().apply { addRoundRect(window) }, scheme.primaryContainer, style = Stroke(3.dp.toPx()))
    }
}

/** The explanation of the camera permission, or the reason why the camera is off, with the action that helps. */
@Composable
private fun AccessMessage(access: CameraAccess, onAllow: () -> Unit) {
    val context = LocalContext.current
    when (access) {
        CameraAccess.ASK -> Message(R.drawable.ic_camera, stringResource(R.string.camera_ask_title), stringResource(R.string.camera_ask_body), "camera_ask") {
            Button(onAllow, Modifier.testTag("camera_allow")) { Text(stringResource(R.string.camera_allow)) }
        }
        CameraAccess.DENIED -> Message(R.drawable.ic_camera_off, stringResource(R.string.camera_denied_title), stringResource(R.string.camera_denied_body), "camera_denied") {
            Button(onAllow, Modifier.testTag("camera_allow")) { Text(stringResource(R.string.camera_allow)) }
        }
        CameraAccess.BLOCKED -> Message(R.drawable.ic_camera_off, stringResource(R.string.camera_denied_title), stringResource(R.string.camera_blocked_body), "camera_blocked") {
            Button(
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                },
                Modifier.testTag("camera_settings"),
            ) { Text(stringResource(R.string.camera_open_settings)) }
        }
        CameraAccess.NONE -> Message(R.drawable.ic_camera_off, stringResource(R.string.camera_none_title), stringResource(R.string.camera_none_body), "camera_none")
        CameraAccess.GRANTED -> Unit
    }
}

/** An icon, a title, a text, and an optional action, in the middle of the camera area. */
@Composable
private fun Message(@DrawableRes icon: Int, title: String, body: String, tag: String, action: @Composable () -> Unit = {}) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().background(scheme.surface).verticalScroll(rememberScrollState()).padding(24.dp).testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Icon(painterResource(icon), contentDescription = null, Modifier.size(48.dp), tint = scheme.primary)
        Text(
            title,
            Modifier.semantics { heading() },
            color = scheme.onSurface,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(body, Modifier.widthIn(max = 480.dp), color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        action()
    }
}

/**
 * The panel below the camera: what leaves the phone and the two other ways to add food, the lookup that runs, or a
 * failed lookup with its actions. Screen readers hear each change of the panel.
 */
@Composable
private fun Panel(
    lookup: LookupState,
    onTypeBarcode: () -> Unit,
    onAddByHand: () -> Unit,
    onCancel: () -> Unit,
    onTryAgain: () -> Unit,
    onEnterByHand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .testTag("scan_panel"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (lookup) {
                is LookupState.Looking -> Looking(lookup.barcode, onCancel)
                is LookupState.Failed -> Failed(lookup, onCancel, onTryAgain, onEnterByHand)
                LookupState.Idle, is LookupState.Done -> Idle(onTypeBarcode, onAddByHand)
            }
        }
    }
}

@Composable
private fun Idle(onTypeBarcode: () -> Unit, onAddByHand: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(painterResource(R.drawable.ic_info), contentDescription = null, tint = scheme.onSurfaceVariant)
        Text(
            stringResource(R.string.scan_privacy),
            Modifier.testTag("scan_privacy"),
            color = scheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onTypeBarcode, Modifier.testTag("type_barcode")) {
            Icon(painterResource(R.drawable.ic_keyboard), contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.action_type_barcode))
        }
        OutlinedButton(onAddByHand, Modifier.testTag("scan_add_by_hand")) {
            Icon(painterResource(R.drawable.ic_pencil_plus), contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.action_add_by_hand))
        }
    }
}

@Composable
private fun Looking(barcode: String, onCancel: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        BrandSpinner(size = 24.dp)
        Text(
            stringResource(R.string.lookup_looking, barcode),
            Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }.testTag("lookup_looking"),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyLarge,
        )
        TextButton(onCancel, Modifier.testTag("lookup_cancel")) { Text(stringResource(R.string.action_cancel)) }
    }
}

/** A failed lookup: what failed, and three ways to continue. The barcode stays for "Try again" and "Enter by hand". */
@Composable
private fun Failed(failed: LookupState.Failed, onScanAgain: () -> Unit, onTryAgain: () -> Unit, onEnterByHand: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val (icon, title, body) = when (failed.failure) {
        LookupFailure.NO_CONNECTION -> Triple(R.drawable.ic_wifi_off, R.string.lookup_no_connection_title, R.string.lookup_no_connection_body)
        LookupFailure.TIMEOUT -> Triple(R.drawable.ic_cloud_off, R.string.lookup_timeout_title, R.string.lookup_timeout_body)
        LookupFailure.BUSY -> Triple(R.drawable.ic_cloud_off, R.string.lookup_busy_title, R.string.lookup_busy_body)
        LookupFailure.SERVER -> Triple(R.drawable.ic_cloud_off, R.string.lookup_server_title, R.string.lookup_server_body)
    }
    Row(
        Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }.testTag("lookup_failed"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = scheme.error)
        Column {
            Text(stringResource(title), color = scheme.onSurface, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(body, failed.barcode), color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(onScanAgain, Modifier.testTag("scan_again")) { Text(stringResource(R.string.action_scan_again)) }
        OutlinedButton(onEnterByHand, Modifier.testTag("enter_by_hand")) { Text(stringResource(R.string.action_enter_by_hand)) }
        Button(onTryAgain, Modifier.testTag("lookup_try_again")) { Text(stringResource(R.string.action_try_again)) }
    }
}

/**
 * The dialog for a typed barcode. It accepts the digits with spaces or hyphens. "Look up" shows the problem of a text
 * that is not a barcode.
 */
@Composable
private fun BarcodeDialog(onLookUp: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var checked by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(focus) { focus.requestFocus() }
    val parsed = Barcodes.parseTyped(text)
    val error = (parsed as? Barcodes.Typed.Invalid)?.takeIf { checked }?.let {
        stringResource(
            when (it.problem) {
                Barcodes.Problem.NOT_A_BARCODE -> R.string.error_barcode_not_a_barcode
                Barcodes.Problem.WRONG_CHECK_DIGIT -> R.string.error_barcode_check_digit
            },
        )
    }
    fun confirm() {
        if (parsed is Barcodes.Typed.Valid) onLookUp(parsed.barcode) else checked = true
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(::confirm, Modifier.testTag("barcode_look_up")) { Text(stringResource(R.string.action_look_up)) }
        },
        modifier = Modifier.testTag("barcode_dialog"),
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        title = { Text(stringResource(R.string.action_type_barcode)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    checked = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .semantics { if (error != null) error(error) }
                    .testTag("field_barcode"),
                label = { Text(stringResource(R.string.field_barcode)) },
                supportingText = {
                    if (error != null) Text(error, Modifier.testTag("error_barcode")) else Text(stringResource(R.string.barcode_help))
                },
                isError = error != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { confirm() }),
                singleLine = true,
            )
        },
    )
}

/** The time with no barcode after which the hint says what helps. */
private const val SLOW_HINT_MILLIS = 10_000L
