package com.cvke.fridge.scan

/**
 * Decides which reads of the camera count. The camera reads a barcode in many pictures each second, and a bad
 * picture can give a wrong number with a correct check digit. A barcode counts when the camera reads the same number
 * [confirmations] times within [windowMillis]. A barcode that counted does not count again until [ignoreMillis] after
 * it counted, so one package gives one scan.
 */
class ScanDebouncer(
    private val confirmations: Int = 2,
    private val windowMillis: Long = 1_000,
    private val ignoreMillis: Long = 3_000,
) {
    private var candidate: String? = null
    private var count = 0
    private var firstRead = 0L
    private var ignored: String? = null
    private var ignoredUntil = Long.MIN_VALUE

    /** One read of the camera at the instant [now], in milliseconds. The result is the barcode when it counts, else null. */
    fun read(barcode: String, now: Long): String? {
        if (barcode == ignored && now < ignoredUntil) return null
        if (barcode != candidate || now - firstRead > windowMillis) {
            candidate = barcode
            count = 0
            firstRead = now
        }
        count++
        if (count < confirmations) return null
        candidate = null
        count = 0
        ignore(barcode, now)
        return barcode
    }

    /**
     * Ignores the [barcode] until [ignoreMillis] after [now]. The scanner calls it when it starts to read again after
     * a scan, so that the package that is still in front of the camera does not give the same scan again.
     */
    fun ignore(barcode: String?, now: Long) {
        ignored = barcode
        ignoredUntil = now + ignoreMillis
    }
}
