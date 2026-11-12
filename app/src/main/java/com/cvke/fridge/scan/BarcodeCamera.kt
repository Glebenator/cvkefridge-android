package com.cvke.fridge.scan

import android.content.Context
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.cvke.fridge.lookup.Barcodes
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import android.util.Size as PixelSize
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the camera of the scanner does. */
enum class CameraStatus {
    /** The camera opens, or it is closed while the app is not on the screen. */
    STARTING,

    /** The preview shows the picture of the camera, and the reader looks for barcodes. */
    RUNNING,

    /** The camera cannot open, for example because another app uses it. */
    FAILED,
}

/**
 * The camera of the scanner: the preview, and the barcode reader of ML Kit with the model in the app. The model works
 * with no connection and with no Google Play services. The pictures stay in memory for the time of one read. The app
 * does not keep them, and does not send them.
 *
 * The reader reads only the barcodes of retail packages: EAN-13, EAN-8, UPC-A, and UPC-E. It gives each barcode with
 * a correct check digit to [onRead], on the main thread, in the form of [Barcodes]. [ScanDebouncer] decides which
 * reads count.
 *
 * Call [run] in a coroutine while the scanner is on the screen, and [close] when the scanner goes away.
 */
class BarcodeCamera(private val onRead: (String) -> Unit) {
    private val preview = Preview.Builder().build()

    // A picture of about 1280 x 720 pixels is sufficient for a barcode, and the reader stays fast.
    private val analysis = ImageAnalysis.Builder()
        .setResolutionSelector(
            ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(PixelSize(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                )
                .build(),
        )
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build()

    private val reader = retailBarcodeReader()
    private val analysisThread: ExecutorService = Executors.newSingleThreadExecutor()
    private val paused = AtomicBoolean(false)

    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)

    /** The request of the preview for a surface. The viewfinder shows the preview on it. */
    val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest.asStateFlow()

    private val _status = MutableStateFlow(CameraStatus.STARTING)
    val status: StateFlow<CameraStatus> = _status.asStateFlow()

    private val _hasTorch = MutableStateFlow(false)

    /** True when the camera has a flash that can stay on as a light. */
    val hasTorch: StateFlow<Boolean> = _hasTorch.asStateFlow()

    private val _torchOn = MutableStateFlow(false)
    val torchOn: StateFlow<Boolean> = _torchOn.asStateFlow()

    private var camera: Camera? = null
    private var meteringPoints: SurfaceOrientedMeteringPointFactory? = null

    init {
        preview.setSurfaceProvider { request ->
            _surfaceRequest.value = request
            meteringPoints = SurfaceOrientedMeteringPointFactory(request.resolution.width.toFloat(), request.resolution.height.toFloat())
        }
        analysis.setAnalyzer(analysisThread, ::analyze)
    }

    /** True while the scanner does not want reads, for example during a lookup. The reader then does no work. */
    var isPaused: Boolean
        get() = paused.get()
        set(value) = paused.set(value)

    /**
     * Opens the back camera for the [owner], or the front camera when the device has no back camera, and keeps it
     * open until the coroutine is cancelled. The camera closes by itself while the [owner] is stopped.
     */
    suspend fun run(context: Context, owner: LifecycleOwner) {
        val provider = try {
            ProcessCameraProvider.awaitInstance(context)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            _status.value = CameraStatus.FAILED
            return
        }
        val bound = try {
            val selector = listOf(CameraSelector.DEFAULT_BACK_CAMERA, CameraSelector.DEFAULT_FRONT_CAMERA).first(provider::hasCamera)
            provider.bindToLifecycle(owner, selector, preview, analysis)
        } catch (_: Exception) {
            // No camera that CameraX can use, or the camera cannot be bound.
            _status.value = CameraStatus.FAILED
            return
        }
        camera = bound
        _hasTorch.value = bound.cameraInfo.hasFlashUnit()
        val observer = Observer<CameraState> { state ->
            _status.value = when {
                state.error != null -> CameraStatus.FAILED
                state.type == CameraState.Type.OPEN -> CameraStatus.RUNNING
                else -> CameraStatus.STARTING
            }
        }
        bound.cameraInfo.cameraState.observe(owner, observer)
        try {
            awaitCancellation()
        } finally {
            bound.cameraInfo.cameraState.removeObserver(observer)
            provider.unbind(preview, analysis)
            camera = null
            _surfaceRequest.value = null
            _torchOn.value = false
            _status.value = CameraStatus.STARTING
        }
    }

    /**
     * The rotation of the display, a [android.view.Surface] rotation value. A turn of the display by 180 degrees does
     * not make the activity again, so the screen gives the new rotation to the preview and to the reader.
     */
    fun setTargetRotation(rotation: Int) {
        preview.targetRotation = rotation
        analysis.targetRotation = rotation
    }

    /** Turns the light of the camera on or off. */
    fun setTorch(on: Boolean) {
        val control = camera?.cameraControl ?: return
        if (!_hasTorch.value) return
        control.enableTorch(on)
        _torchOn.value = on
    }

    /** Focuses the camera on a point of the preview surface, in the pixels of [SurfaceRequest.getResolution]. */
    fun focus(x: Float, y: Float) {
        val control = camera?.cameraControl ?: return
        val point = meteringPoints?.createPoint(x, y) ?: return
        control.startFocusAndMetering(FocusMeteringAction.Builder(point).setAutoCancelDuration(3, TimeUnit.SECONDS).build())
    }

    /** Stops the reader. Call it one time, when the scanner goes away. */
    fun close() {
        analysis.clearAnalyzer()
        reader.close()
        analysisThread.shutdown()
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private fun analyze(proxy: ImageProxy) {
        val image = proxy.image
        if (paused.get() || image == null) {
            proxy.close()
            return
        }
        reader.process(InputImage.fromMediaImage(image, proxy.imageInfo.rotationDegrees))
            .addOnSuccessListener { barcodes ->
                if (paused.get()) return@addOnSuccessListener
                barcodes.forEach { barcode -> barcode.retailBarcode()?.let(onRead) }
            }
            // The next picture comes when this one is closed.
            .addOnCompleteListener { proxy.close() }
    }

}

/** The barcode reader of ML Kit for the formats of retail packages: EAN-13, EAN-8, UPC-A, and UPC-E. */
internal fun retailBarcodeReader(): BarcodeScanner = BarcodeScanning.getClient(
    BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
        .build(),
)

/** The barcode of a read in the form of [Barcodes], or null for another format or a wrong check digit. */
internal fun Barcode.retailBarcode(): String? {
    val format = when (format) {
        Barcode.FORMAT_EAN_13 -> Barcodes.Format.EAN_13
        Barcode.FORMAT_EAN_8 -> Barcodes.Format.EAN_8
        Barcode.FORMAT_UPC_A -> Barcodes.Format.UPC_A
        Barcode.FORMAT_UPC_E -> Barcodes.Format.UPC_E
        else -> return null
    }
    return rawValue?.let { Barcodes.fromScan(format, it) }
}
