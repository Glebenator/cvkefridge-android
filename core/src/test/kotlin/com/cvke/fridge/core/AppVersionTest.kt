package com.cvke.fridge.core

import org.junit.Assert.assertEquals
import org.junit.Test

class AppVersionTest {
    @Test
    fun labelShowsNameAndBuild() {
        assertEquals("0.1.0 (1)", AppVersion("0.1.0", 1).label)
        assertEquals("1.2 (34)", AppVersion("1.2", 34).label)
    }
}
