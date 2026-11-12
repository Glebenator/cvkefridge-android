package com.cvke.fridge.lookup

/**
 * The barcodes of retail packages: EAN-13, EAN-8, UPC-A, UPC-E, and GTIN-14. The app keeps a barcode in one form, so
 * that one package always gives the same text, typed or scanned:
 *
 * - EAN-13 and EAN-8 stay as they are.
 * - UPC-A (12 digits) gets a 0 at the start. It is then the EAN-13 of the same package. Open Food Facts uses this form.
 * - UPC-E (8 digits) becomes its UPC-A, and then gets a 0 at the start.
 * - GTIN-14 loses a 0 at the start. A GTIN-14 that does not start with 0 stays as it is.
 *
 * Each barcode has a check digit at the end. A text with a wrong check digit is not a barcode.
 *
 * The shared specification has no rule for barcodes yet. These rules must go into it, so that the iOS app keeps the
 * same text for the same package.
 */
object Barcodes {
    /** The format that the camera read. The format tells EAN-8 from UPC-E, which both have 8 digits. */
    enum class Format { EAN_13, EAN_8, UPC_A, UPC_E }

    /** Why a typed text is not a barcode. */
    enum class Problem {
        /** The text has a character that is not a digit, a space, or a hyphen, or it has a wrong number of digits. */
        NOT_A_BARCODE,

        /** The number of digits is correct, but the last digit is not the check digit. */
        WRONG_CHECK_DIGIT,
    }

    /** The result of [parseTyped]. */
    sealed interface Typed {
        data class Valid(val barcode: String) : Typed

        data class Invalid(val problem: Problem) : Typed
    }

    /**
     * The barcode in a text that the person typed. Spaces and hyphens have no effect, because packages print the
     * digits in groups. A text of 8 digits is an EAN-8 when its check digit is correct for EAN-8, and else a UPC-E.
     */
    fun parseTyped(text: String): Typed {
        if (text.any { !isDigit(it) && it != ' ' && it != '-' }) return Typed.Invalid(Problem.NOT_A_BARCODE)
        val digits = text.filter(::isDigit)
        if (digits.length !in LENGTHS) return Typed.Invalid(Problem.NOT_A_BARCODE)
        val barcode = when (digits.length) {
            8 -> canonical(digits) ?: upcE(digits)
            else -> canonical(digits)
        }
        return barcode?.let(Typed::Valid) ?: Typed.Invalid(Problem.WRONG_CHECK_DIGIT)
    }

    /** The barcode that the camera read, in the form of the app, or null when its digits are not a valid barcode. */
    fun fromScan(format: Format, raw: String): String? {
        if (raw.isEmpty() || !raw.all(::isDigit)) return null
        return when (format) {
            Format.UPC_E -> when (raw.length) {
                8 -> upcE(raw)
                // Some readers give only the 6 digits of the code. The number system is then 0.
                6 -> upcE("0" + raw + upcACheckDigitOfUpcE("0$raw"))
                else -> null
            }
            Format.EAN_8 -> raw.takeIf { it.length == 8 }?.let(::canonical)
            Format.EAN_13, Format.UPC_A -> raw.takeIf { it.length == 12 || it.length == 13 }?.let(::canonical)
        }
    }

    /**
     * The barcode in the form of the app, or null when [text] is not a valid barcode. A text from storage that is
     * not in this form, for example a UPC-A, gets the same result as the scan of the same package.
     */
    fun normalize(text: String): String? = (parseTyped(text) as? Typed.Valid)?.barcode

    /** True when the last digit is the GS1 check digit of the other digits. */
    fun hasValidCheckDigit(digits: String): Boolean =
        digits.length >= 2 && digits.all(::isDigit) && checkDigit(digits.dropLast(1)) == digits.last().digitToInt()

    /**
     * The UPC-A of a UPC-E: 8 digits with the number system (0 or 1) first and the check digit last. The result is
     * null when the text is not a UPC-E or its check digit is wrong.
     */
    fun expandUpcE(digits: String): String? {
        if (digits.length != 8 || !digits.all(::isDigit) || digits[0] !in "01") return null
        val upcA = upcABody(digits) + digits[7]
        return upcA.takeIf(::hasValidCheckDigit)
    }

    /** The GS1 check digit for the digits: weights 3 and 1 from the right, and then the step to the next 10. */
    private fun checkDigit(body: String): Int {
        val sum = body.reversed().withIndex().sumOf { (index, char) -> char.digitToInt() * if (index % 2 == 0) 3 else 1 }
        return (10 - sum % 10) % 10
    }

    /** The 11 digits of the UPC-A of a UPC-E, with no check digit. [digits] has the number system first. */
    private fun upcABody(digits: String): String {
        val d = digits
        return d[0] + when (d[6]) {
            '0', '1', '2' -> "${d[1]}${d[2]}${d[6]}0000${d[3]}${d[4]}${d[5]}"
            '3' -> "${d[1]}${d[2]}${d[3]}00000${d[4]}${d[5]}"
            '4' -> "${d[1]}${d[2]}${d[3]}${d[4]}00000${d[5]}"
            else -> "${d[1]}${d[2]}${d[3]}${d[4]}${d[5]}0000${d[6]}"
        }
    }

    private fun upcACheckDigitOfUpcE(sevenDigits: String): Int = checkDigit(upcABody(sevenDigits))

    /** The form of the app for a UPC-E, or null. */
    private fun upcE(digits: String): String? = expandUpcE(digits)?.let { "0$it" }

    /** The form of the app for an EAN-8, a UPC-A, an EAN-13, or a GTIN-14, or null when the check digit is wrong. */
    private fun canonical(digits: String): String? {
        if (!hasValidCheckDigit(digits)) return null
        return when (digits.length) {
            12 -> "0$digits"
            14 -> if (digits[0] == '0') digits.drop(1) else digits
            else -> digits
        }
    }

    private fun isDigit(char: Char) = char in '0'..'9'

    /** The numbers of digits of a barcode (glossary: "Barcode"). */
    private val LENGTHS = setOf(8, 12, 13, 14)
}
