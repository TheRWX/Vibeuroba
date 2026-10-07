package com.github.k1rakishou.chan.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseHelpersTest {

    @Test
    fun testReleaseVersionCalculation() {
        assertEquals(0, ReleaseHelpers.calculateReleaseVersionCode("123"))
        assertEquals(10332, ReleaseHelpers.calculateReleaseVersionCode("v1.3.32-release"))
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

    @Test
    fun testSelectApkIndex() {
        val assets = listOf(
            "Vibeuroba-all.apk",
            "Vibeuroba-arm64-v8a.apk",
            "Vibeuroba-armeabi-v7a.apk",
            "Vibeuroba-x86.apk",
            "Vibeuroba-x86_64.apk"
        )

        assertEquals(1, ReleaseHelpers.selectApkIndex(assets, listOf("arm64-v8a", "armeabi-v7a", "armeabi")))
        assertEquals(2, ReleaseHelpers.selectApkIndex(assets, listOf("armeabi-v7a", "armeabi")))
        assertEquals(4, ReleaseHelpers.selectApkIndex(assets, listOf("x86_64", "x86")))
        // x86 must not pick the x86_64 APK, whatever the asset order
        assertEquals(1, ReleaseHelpers.selectApkIndex(assets.reversed(), listOf("x86")))
        // Unknown ABI: the universal APK, wherever it is in the list
        assertEquals(0, ReleaseHelpers.selectApkIndex(assets, listOf("riscv64")))
        assertEquals(4, ReleaseHelpers.selectApkIndex(assets.reversed(), listOf("riscv64")))

        // Releases made before the universal APK was renamed
        val oldAssets = listOf(
            "Vibeuroba-arm64-v8a.apk",
            "Vibeuroba-armeabi-v7a.apk",
            "Vibeuroba-x86.apk",
            "Vibeuroba-x86_64.apk",
            "Vibeuroba.apk"
        )
        assertEquals(4, ReleaseHelpers.selectApkIndex(oldAssets, listOf("riscv64")))
        assertEquals(0, ReleaseHelpers.selectApkIndex(listOf("Vibeuroba.apk"), listOf("arm64-v8a")))

        assertEquals(null, ReleaseHelpers.selectApkIndex(emptyList(), listOf("arm64-v8a")))
    }

    private fun assertBetaVersionEquals(versionCode: Long, buildNumber: Long, other: ReleaseHelpers.BetaVersionCode) {
        assertEquals(versionCode, other.versionCode)
        assertEquals(buildNumber, other.buildNumber)
    }

}