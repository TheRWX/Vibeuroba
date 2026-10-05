package com.github.k1rakishou.chan.core.helper

import android.util.Base64
import com.github.k1rakishou.v2.NonBackupableSettings
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * The user-chosen Vibeuroba app lock PIN. Only a salted PBKDF2 hash is stored
 * (NonBackupableSettings.appLockPinHash, format "v1:<iterations>:<salt>:<hash>"); the PIN itself never is.
 */
class AppLockPin(
  private val settings: NonBackupableSettings
) {
  fun isSet(): Boolean = settings.appLockPinHash.readBlocking().isNotEmpty()

  fun save(pin: String) {
    require(isValidFormat(pin)) { "Invalid PIN format" }

    val salt = ByteArray(SALT_BYTES).also { bytes -> SecureRandom().nextBytes(bytes) }
    val hash = derive(pin, salt, ITERATIONS)

    settings.appLockPinHash.writeBlocking(
      listOf(VERSION, ITERATIONS.toString(), encode(salt), encode(hash)).joinToString(":")
    )
    resetFailedAttempts()
  }

  fun clear() {
    settings.appLockPinHash.writeBlocking("")
    resetFailedAttempts()
  }

  fun verify(pin: String): Boolean {
    val parts = settings.appLockPinHash.readBlocking().split(":")
    if (parts.size != 4 || parts[0] != VERSION) {
      return false
    }

    val iterations = parts[1].toIntOrNull() ?: return false
    val salt = decode(parts[2]) ?: return false
    val expected = decode(parts[3]) ?: return false

    return MessageDigest.isEqual(derive(pin, salt, iterations), expected)
  }

  /** Remaining lockout in ms after too many wrong attempts, or 0 when the PIN can be entered. */
  fun remainingLockoutMs(): Long {
    val remaining = settings.appLockLockoutUntil.readBlocking() - System.currentTimeMillis()
    // Clamp so a clock moved backwards can't extend the lockout beyond one period.
    return remaining.coerceIn(0L, LOCKOUT_MS)
  }

  fun onWrongAttempt() {
    val attempts = settings.appLockFailedAttempts.readBlocking() + 1
    settings.appLockFailedAttempts.writeBlocking(attempts)

    if (attempts % MAX_ATTEMPTS_BEFORE_LOCKOUT == 0) {
      settings.appLockLockoutUntil.writeBlocking(System.currentTimeMillis() + LOCKOUT_MS)
    }
  }

  fun resetFailedAttempts() {
    settings.appLockFailedAttempts.writeBlocking(0)
    settings.appLockLockoutUntil.writeBlocking(0L)
  }

  private fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray {
    val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS)
    try {
      return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
    } finally {
      spec.clearPassword()
    }
  }

  private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

  private fun decode(value: String): ByteArray? {
    return try {
      Base64.decode(value, Base64.NO_WRAP)
    } catch (error: IllegalArgumentException) {
      null
    }
  }

  companion object {
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 12

    // Wrong attempts allowed before a lockout, and how long each lockout lasts.
    const val MAX_ATTEMPTS_BEFORE_LOCKOUT = 5
    const val LOCKOUT_MS = 30_000L

    private const val VERSION = "v1"
    private const val ITERATIONS = 20_000
    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256

    // PBKDF2WithHmacSHA256 needs API 26; minSdk is 23.
    private const val ALGORITHM = "PBKDF2WithHmacSHA1"

    fun isValidFormat(pin: String): Boolean {
      return pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { ch -> ch in '0'..'9' }
    }
  }
}
