package com.cvke.fridge.scan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The reader of the scanner with the model in the app, on the device, with no camera: the test draws barcodes in a
 * picture and reads them as the camera pictures are read. The emulator in CI has no camera picture with a barcode.
 */
class BarcodeReaderDeviceTest {
    private val reader = retailBarcodeReader()

    @After
    fun close() = reader.close()

    private fun read(bitmap: Bitmap): List<String> =
        Tasks.await(reader.process(InputImage.fromBitmap(bitmap, 0)), 20, TimeUnit.SECONDS).mapNotNull { it.retailBarcode() }

    @Test
    fun theReaderGivesRetailBarcodesInTheFormOfTheApp() {
        assertEquals(listOf("3017620422003"), read(draw(ean13("3017620422003"))))
        // A UPC-A is an EAN-13 that starts with 0. The app keeps it with the 0.
        assertEquals(listOf("0036000291452"), read(draw(ean13("0036000291452"))))
        assertEquals(listOf("96385074"), read(draw(ean8("96385074"))))
    }

    @Test
    fun aPictureWithNoBarcodeGivesNothing() {
        assertEquals(emptyList<String>(), read(Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }))
    }

    /** The modules of a barcode as black bars on white, with a quiet zone at each side. */
    private fun draw(modules: String, module: Int = 6, height: Int = 300): Bitmap {
        val quiet = 12
        val bitmap = Bitmap.createBitmap((modules.length + 2 * quiet) * module, height + 40, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val black = Paint().apply { color = Color.BLACK }
        modules.forEachIndexed { index, bit ->
            if (bit == '1') {
                val left = ((quiet + index) * module).toFloat()
                canvas.drawRect(left, 20f, left + module, 20f + height, black)
            }
        }
        return bitmap
    }

    private fun ean13(digits: String): String {
        val first = digits[0].digitToInt()
        val left = digits.substring(1, 7).mapIndexed { index, char ->
            if (PARITY[first][index] == 'L') L[char.digitToInt()] else G[char.digitToInt()]
        }.joinToString("")
        val right = digits.substring(7).map { R[it.digitToInt()] }.joinToString("")
        return "101$left" + "01010$right" + "101"
    }

    private fun ean8(digits: String): String {
        val left = digits.substring(0, 4).map { L[it.digitToInt()] }.joinToString("")
        val right = digits.substring(4).map { R[it.digitToInt()] }.joinToString("")
        return "101$left" + "01010$right" + "101"
    }

    private companion object {
        val L = listOf("0001101", "0011001", "0010011", "0111101", "0100011", "0110001", "0101111", "0111011", "0110111", "0001011")
        val G = listOf("0100111", "0110011", "0011011", "0100001", "0011101", "0111001", "0000101", "0010001", "0001001", "0010111")
        val R = listOf("1110010", "1100110", "1101100", "1000010", "1011100", "1001110", "1010000", "1000100", "1001000", "1110100")

        /** The codes of the first group of an EAN-13, for each first digit. */
        val PARITY = listOf("LLLLLL", "LLGLGG", "LLGGLG", "LLGGGL", "LGLLGG", "LGGLLG", "LGGGLL", "LGLGLG", "LGLGGL", "LGGLGL")
    }
}
