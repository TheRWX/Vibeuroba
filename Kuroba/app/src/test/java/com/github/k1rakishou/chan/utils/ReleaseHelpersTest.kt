package com.github.k1rakishou.chan.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseHelpersTest {

    @Test
    fun testReleaseVersionCalculation() {
        assertEquals(0, ReleaseHelpers.calculateReleaseVersionCode("123"))
        assertEquals(10332, ReleaseHelpers.calculateReleaseVersionCode("v1.3.32-release"))

        // Vibeuroba tags (v1.3.47 - v1.3.50 were published without the "-release" suffix)
        assertEquals(10350, ReleaseHelpers.calculateReleaseVersionCode("v1.3.50"))
        assertEquals(10351, ReleaseHelpers.calculateReleaseVersionCode("v1.3.51-release"))
        assertEquals(0, ReleaseHelpers.calculateReleaseVersionCode("v1.3.51-beta"))
        assertEquals(0, ReleaseHelpers.calculateReleaseVersionCode("v1.3.51.1-beta"))
    }

    @Test
    fun testBetaVersionCalculation() {
        assertBetaVersionEquals(0, 0, ReleaseHelpers.calculateBetaVersionCode("123"))

        // Old format, not supported anymore
        assertBetaVersionEquals(10332, 0, ReleaseHelpers.calculateBetaVersionCode("v1.3.32-beta"))

        // New format
        assertBetaVersionEquals(10332, 0, ReleaseHelpers.calculateBetaVersionCode("v1.3.32.0-beta"))
        assertBetaVersionEquals(10332, 1, ReleaseHelpers.calculateBetaVersionCode("v1.3.32.1-beta"))
        assertBetaVersionEquals(10332, 999999, ReleaseHelpers.calculateBetaVersionCode("v1.3.32.999999-beta"))
        assertBetaVersionEquals(10332, 10000000000000L, ReleaseHelpers.calculateBetaVersionCode("v1.3.32.10000000000000-beta"))
    }

    private fun assertBetaVersionEquals(versionCode: Long, buildNumber: Long, other: ReleaseHelpers.BetaVersionCode) {
        assertEquals(versionCode, other.versionCode)
        assertEquals(buildNumber, other.buildNumber)
    }

}