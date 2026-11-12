package com.cvke.fridge.lookup

import com.cvke.fridge.lookup.Barcodes.Format
import com.cvke.fridge.lookup.Barcodes.Problem
import com.cvke.fridge.lookup.Barcodes.Typed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The check digit and the one form of the app for each kind of barcode. */
class BarcodesTest {
    private fun typed(text: String) = (Barcodes.parseTyped(text) as? Typed.Valid)?.barcode

    @Test
    fun theCheckDigitIsTheGs1CheckDigit() {
        assertTrue(Barcodes.hasValidCheckDigit("3017620422003"))
        assertTrue(Barcodes.hasValidCheckDigit("96385074"))
        assertTrue(Barcodes.hasValidCheckDigit("036000291452"))
        assertFalse(Barcodes.hasValidCheckDigit("3017620422004"))
        assertFalse(Barcodes.hasValidCheckDigit("3"))
        assertFalse(Barcodes.hasValidCheckDigit("30176204220a3"))
    }

    @Test
    fun aTypedBarcodeGetsTheFormOfTheApp() {
        assertEquals("3017620422003", typed("3017620422003"))
        // Packages print the digits in groups.
        assertEquals("3017620422003", typed(" 3 017620 422003 "))
        assertEquals("5449000000996", typed("5449000-000996"))
        assertEquals("96385074", typed("96385074"))
        // UPC-A is the EAN-13 with a 0 at the start.
        assertEquals("0036000291452", typed("036000291452"))
        // A GTIN-14 with a 0 at the start is the EAN-13. Other GTIN-14 stay as they are.
        assertEquals("0012345678905", typed("00012345678905"))
        assertEquals("10012345678902", typed("10012345678902"))
    }

    @Test
    fun eightDigitsAreAnEan8WhenTheyCanBeAndElseAUpcE() {
        // 01234565 is a valid EAN-8 and also a valid UPC-E. Typed, it is the EAN-8.
        assertEquals("01234565", typed("01234565"))
        // 04252614 is not a valid EAN-8. Its UPC-A is 042100005264.
        assertEquals("0042100005264", typed("04252614"))
    }

    @Test
    fun aTextThatIsNotABarcodeSaysWhy() {
        assertEquals(Typed.Invalid(Problem.WRONG_CHECK_DIGIT), Barcodes.parseTyped("3017620422004"))
        assertEquals(Typed.Invalid(Problem.WRONG_CHECK_DIGIT), Barcodes.parseTyped("04252615"))
        assertEquals(Typed.Invalid(Problem.NOT_A_BARCODE), Barcodes.parseTyped("3"))
        assertEquals(Typed.Invalid(Problem.NOT_A_BARCODE), Barcodes.parseTyped("30176204220"))
        assertEquals(Typed.Invalid(Problem.NOT_A_BARCODE), Barcodes.parseTyped("30176204220O3"))
        assertEquals(Typed.Invalid(Problem.NOT_A_BARCODE), Barcodes.parseTyped("Nutella"))
        assertEquals(Typed.Invalid(Problem.NOT_A_BARCODE), Barcodes.parseTyped(""))
        // Digits that are not ASCII are not digits of a barcode.
        assertEquals(Typed.Invalid(Problem.NOT_A_BARCODE), Barcodes.parseTyped("٣٠١٧٦٢٠٤٢٢٠٠٣"))
    }

    @Test
    fun theFormatOfTheCameraDecidesForEightDigits() {
        assertEquals("01234565", Barcodes.fromScan(Format.EAN_8, "01234565"))
        assertEquals("0012345000065", Barcodes.fromScan(Format.UPC_E, "01234565"))
        // A reader that gives only the 6 digits of a UPC-E.
        assertEquals("0012345000065", Barcodes.fromScan(Format.UPC_E, "123456"))
        assertEquals("0036000291452", Barcodes.fromScan(Format.UPC_A, "036000291452"))
        assertEquals("0036000291452", Barcodes.fromScan(Format.EAN_13, "0036000291452"))
    }

    @Test
    fun aReadWithAWrongCheckDigitOrLengthIsNoBarcode() {
        assertNull(Barcodes.fromScan(Format.EAN_13, "3017620422004"))
        assertNull(Barcodes.fromScan(Format.EAN_8, "3017620422003"))
        assertNull(Barcodes.fromScan(Format.UPC_E, "21234565"))
        assertNull(Barcodes.fromScan(Format.UPC_E, "01234566"))
        assertNull(Barcodes.fromScan(Format.EAN_13, ""))
    }

    @Test
    fun aUpcEExpandsToItsUpcA() {
        assertEquals("012345000065", Barcodes.expandUpcE("01234565"))
        assertEquals("042100005264", Barcodes.expandUpcE("04252614"))
        assertEquals("012000003455", Barcodes.expandUpcE("01234505"))
        assertEquals("012300000451", Barcodes.expandUpcE("01234531"))
        assertEquals("012340000053", Barcodes.expandUpcE("01234543"))
        // The number system is 0 or 1.
        assertNull(Barcodes.expandUpcE("21234565"))
    }

    @Test
    fun aStoredBarcodeIsComparedInTheFormOfTheApp() {
        assertEquals("0036000291452", Barcodes.normalize("036000291452"))
        assertEquals("5449000000996", Barcodes.normalize("5449000000996"))
        assertNull(Barcodes.normalize("12"))
    }
}
