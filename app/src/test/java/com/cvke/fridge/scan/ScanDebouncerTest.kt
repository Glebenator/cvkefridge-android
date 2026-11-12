package com.cvke.fridge.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** One package gives one scan, and a single wrong read gives none. */
class ScanDebouncerTest {
    @Test
    fun aBarcodeCountsAfterTwoReadsWithinOneSecond() {
        val debouncer = ScanDebouncer()
        assertNull(debouncer.read("3017620422003", 0))
        assertEquals("3017620422003", debouncer.read("3017620422003", 100))
    }

    @Test
    fun aDifferentReadOrALateReadStartsAgain() {
        val debouncer = ScanDebouncer()
        assertNull(debouncer.read("3017620422003", 0))
        assertNull(debouncer.read("5449000000996", 50))
        assertNull(debouncer.read("3017620422003", 100))
        assertNull(debouncer.read("3017620422003", 1_200))
        assertEquals("3017620422003", debouncer.read("3017620422003", 1_300))
    }

    @Test
    fun theSamePackageDoesNotCountAgainForThreeSeconds() {
        val debouncer = ScanDebouncer()
        debouncer.read("3017620422003", 0)
        debouncer.read("3017620422003", 100)
        repeat(10) { assertNull(debouncer.read("3017620422003", 200L + it * 250)) }
        // Another package counts at once.
        debouncer.read("5449000000996", 2_800)
        assertEquals("5449000000996", debouncer.read("5449000000996", 2_900))
        assertNull(debouncer.read("5449000000996", 3_000))
    }

    @Test
    fun anIgnoredBarcodeCountsAgainAfterTheTime() {
        val debouncer = ScanDebouncer()
        debouncer.ignore("3017620422003", 10_000)
        assertNull(debouncer.read("3017620422003", 10_000))
        assertNull(debouncer.read("3017620422003", 12_999))
        assertNull(debouncer.read("3017620422003", 13_000))
        assertEquals("3017620422003", debouncer.read("3017620422003", 13_100))
    }
}
