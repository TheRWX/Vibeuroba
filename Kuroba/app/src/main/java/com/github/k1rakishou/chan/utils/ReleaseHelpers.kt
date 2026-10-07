package com.github.k1rakishou.chan.utils

import com.github.k1rakishou.common.groupOrNull
import java.util.regex.Pattern

object ReleaseHelpers {
    private val RELEASE_VERSION_CODE_PATTERN = Pattern.compile("v(\\d+?)\\.(\\d{1,2})\\.(\\d{1,2})-release$")
    private val BETA_VERSION_CODE_PATTERN = Pattern.compile("v(\\d+?)\\.(\\d{1,2})\\.(\\d{1,2})(?:\\.(\\d+))?-beta\$")

    @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    fun calculateReleaseVersionCode(versionCodeString: String?): Long {
        if (versionCodeString.isNullOrBlank()) {
            return 0
        }

        val versionMatcher = RELEASE_VERSION_CODE_PATTERN.matcher(versionCodeString)
        if (!versionMatcher.find()) {
            return 0
        }

        return versionMatcher.group(3).toLong() +
                versionMatcher.group(2).toLong() * 100L +
                versionMatcher.group(1).toLong() * 10000L
    }

    @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    fun calculateBetaVersionCode(versionCodeString: String?): BetaVersionCode {
        if (versionCodeString.isNullOrBlank()) {
            return BetaVersionCode()
        }

        val versionMatcher = BETA_VERSION_CODE_PATTERN.matcher(versionCodeString)
        if (!versionMatcher.find()) {
            return BetaVersionCode()
        }

        val versionCode = versionMatcher.group(3).toLong() +
                versionMatcher.group(2).toLong() * 100L +
                versionMatcher.group(1).toLong() * 10000L

        val buildNumber = versionMatcher.groupOrNull(4)
            ?.toLongOrNull()
            ?: 0L

        return BetaVersionCode(
            versionCode = versionCode,
            buildNumber = buildNumber
        )
    }

    /**
     * Picks the APK for this device from a release's asset file names: the one built for the first
     * supported ABI (in the device's order of preference), otherwise the universal APK. Returns the index
     * into [apkFileNames], or null when the list is empty.
     *
     * Per-ABI APKs end with `-<abi>.apk` (for example `Vibeuroba-arm64-v8a.apk`); the universal APK has no
     * ABI suffix. The ABI is matched exactly at the end of the name, so `x86` doesn't pick the
     * `-x86_64` APK.
     */
    fun selectApkIndex(apkFileNames: List<String>, supportedAbis: List<String>): Int? {
        if (apkFileNames.isEmpty()) {
            return null
        }

        for (abi in supportedAbis) {
            val index = apkFileNames.indexOfFirst { fileName -> apkAbi(fileName).equals(abi, ignoreCase = true) }
            if (index >= 0) {
                return index
            }
        }

        val universalIndex = apkFileNames.indexOfFirst { fileName -> apkAbi(fileName) == null }
        if (universalIndex >= 0) {
            return universalIndex
        }

        return apkFileNames.lastIndex
    }

    private fun apkAbi(apkFileName: String): String? {
        val baseName = apkFileName.removeSuffix(".apk")

        return KNOWN_APK_ABIS.firstOrNull { abi -> baseName.endsWith("-$abi", ignoreCase = true) }
    }

    private val KNOWN_APK_ABIS = listOf("armeabi-v7a", "arm64-v8a", "x86_64", "x86")

    data class BetaVersionCode(
        val versionCode: Long = 0,
        val buildNumber: Long = 0
    )

}