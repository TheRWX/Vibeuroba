package com.github.k1rakishou.chan.core.manager

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.FragmentActivity
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.helper.AppLockPin
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.dp
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeEngine
import com.github.k1rakishou.v2.KurobaSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.util.WeakHashMap
import java.util.concurrent.TimeUnit

/**
 * Vibeuroba privacy features that apply to every activity of the app:
 * - App lock: covers the UI and asks for the user's Vibeuroba PIN (optionally fingerprint/face) on cold
 *   start and when returning after the configured timeout. The PIN is created by the user when enabling
 *   app lock (see [AppLockPin]); there is no default PIN.
 * - Screenshot protection: sets FLAG_SECURE so the app is blank in recents, screenshots and recordings.
 */
class AppPrivacyManager(
  private val application: Application,
  private val themeEngine: ThemeEngine,
  private val kurobaSettings: KurobaSettings,
  private val appLockPin: AppLockPin,
  private val scope: CoroutineScope
) : Application.ActivityLifecycleCallbacks {
  private val startedActivities = LinkedHashSet<Activity>()
  private val lockCovers = WeakHashMap<Activity, LockCover>()

  private var locked = true

  // Incremented whenever a new lock session starts, so a PIN check still running from an earlier session can't
  // unlock a later one.
  private var lockGeneration = 0
  private var promptShowing = false
  private var lastBackgroundTime = 0L
  fun initialize() {
    application.registerActivityLifecycleCallbacks(this)

    scope.launch {
      kurobaSettings.application.hideFromScreenshots.listen()
        .drop(1)
        .collect { startedActivities.forEach { activity -> applySecureFlag(activity) } }
    }
  }

  override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
    applySecureFlag(activity)
  }

  override fun onActivityStarted(activity: Activity) {
    val wasInBackground = startedActivities.isEmpty()
    startedActivities += activity
    applySecureFlag(activity)

    if (!isAppLockConfigured(kurobaSettings, appLockPin)) {
      locked = false
      return
    }

    if (wasInBackground && !promptShowing && lastBackgroundTime > 0L) {
      val timeoutMs = TimeUnit.SECONDS.toMillis(kurobaSettings.application.appLockTimeoutSeconds.readBlocking())
      if (SystemClock.elapsedRealtime() - lastBackgroundTime >= timeoutMs) {
        if (!locked) {
          locked = true
          lockGeneration++
        }
      }

      // Consume the timestamp so a later activity recreation isn't mistaken for another return.
      lastBackgroundTime = 0L
    }

    if (locked) {
      lockActivity(activity)
    }
  }

  override fun onActivityStopped(activity: Activity) {
    startedActivities -= activity

    if (startedActivities.isEmpty() && !activity.isChangingConfigurations && !promptShowing) {
      lastBackgroundTime = SystemClock.elapsedRealtime()
    }
  }

  override fun onActivityDestroyed(activity: Activity) {
    startedActivities -= activity
    lockCovers.remove(activity)?.backCallback?.remove()
  }

  override fun onActivityResumed(activity: Activity) {}
  override fun onActivityPaused(activity: Activity) {}
  override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

  private fun applySecureFlag(activity: Activity) {
    val window = activity.window
    val wantSecure = kurobaSettings.application.hideFromScreenshots.readBlocking()
    val isSecure = (window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0

    // Only touch the window when the flag actually changes; every flag update relayouts the window.
    if (wantSecure == isSecure) {
      return
    }

    if (wantSecure) {
      window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    } else {
      window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
  }

  private fun lockActivity(activity: Activity) {
    if (activity !is FragmentActivity) {
      return
    }

    // Each (re)created activity gets its own cover and BiometricPrompt: constructing the prompt also
    // re-attaches the callback to an authentication still running from before an activity recreation.
    val lockCover = lockCovers.getOrPut(activity) { createCover(activity) }

    // Start unlocking once the activity has finished starting.
    lockCover.view.post {
      if (lockCover.prompt != null && kurobaSettings.application.appLockBiometrics.readBlocking()) {
        showBiometricPrompt(activity)
      } else {
        focusPinInput(activity, lockCover)
      }
    }
  }

  private fun createCover(activity: FragmentActivity): LockCover {
    val chanTheme = themeEngine.chanTheme

    val title = TextView(activity).apply {
      text = activity.getString(R.string.app_lock_locked_message)
      setTextColor(chanTheme.textColorPrimary)
      textSize = 18f
      gravity = Gravity.CENTER
      compoundDrawablePadding = dp(8f)
      setCompoundDrawablesWithIntrinsicBounds(
        null,
        ContextCompat.getDrawable(activity, R.drawable.ic_baseline_lock_24)
          ?.let { drawable -> themeEngine.tintDrawable(drawable, chanTheme.textColorPrimary) },
        null,
        null
      )
    }

    val pinInput = EditText(activity).apply {
      hint = activity.getString(R.string.app_lock_pin_hint)
      inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
      filters = arrayOf(InputFilter.LengthFilter(AppLockPin.MAX_LENGTH))
      imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_FULLSCREEN
      isSingleLine = true
      gravity = Gravity.CENTER
      textSize = 22f
      setTextColor(chanTheme.textColorPrimary)
      setHintTextColor(chanTheme.textColorHint)
    }

    val errorText = TextView(activity).apply {
      setTextColor(chanTheme.errorColor)
      gravity = Gravity.CENTER
      visibility = View.GONE
    }

    val unlockButton = createTextButton(activity, R.string.app_lock_unlock)

    val biometricsAvailable = kurobaSettings.application.appLockBiometrics.readBlocking() && canUseBiometrics(activity)
    val biometricButton = if (biometricsAvailable) {
      createTextButton(activity, R.string.app_lock_use_biometrics)
    } else {
      null
    }

    val content = LinearLayout(activity).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER_HORIZONTAL
      setPadding(dp(32f), 0, dp(32f), 0)
      addView(title, LinearLayout.LayoutParams(WRAP, WRAP))
      addView(pinInput, LinearLayout.LayoutParams(dp(220f), WRAP).apply { topMargin = dp(24f) })
      addView(errorText, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8f) })
      addView(unlockButton, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(16f) })
      biometricButton?.let { button -> addView(button, LinearLayout.LayoutParams(WRAP, WRAP)) }
    }

    val cover = FrameLayout(activity).apply {
      setBackgroundColor(chanTheme.backColor)
      isClickable = true
      isFocusable = true
      // Stay above elevated app views (toolbars, FABs) that are drawn in the same decor view.
      elevation = COVER_ELEVATION
      addView(content, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER))
    }

    val prompt = if (biometricsAvailable) createBiometricPrompt(activity) else null
    val lockCover = LockCover(cover, pinInput, errorText, prompt, createBackCallback(activity))

    fun submit() = submitPin(activity, lockCover)
    unlockButton.setOnClickListener { submit() }
    pinInput.setOnEditorActionListener { _, actionId, _ ->
      if (actionId == EditorInfo.IME_ACTION_DONE) {
        submit()
        true
      } else {
        false
      }
    }
    biometricButton?.setOnClickListener { showBiometricPrompt(activity) }

    (activity.window.decorView as ViewGroup).addView(cover, ViewGroup.LayoutParams(MATCH, MATCH))
    (activity as ComponentActivity).onBackPressedDispatcher.addCallback(lockCover.backCallback)
    uiLocked = true

    showLockoutIfNeeded(lockCover)
    return lockCover
  }

  private fun createTextButton(activity: Activity, textId: Int): TextView {
    return TextView(activity).apply {
      text = activity.getString(textId)
      setTextColor(themeEngine.chanTheme.accentColor)
      textSize = 16f
      gravity = Gravity.CENTER
      setPadding(dp(16f), dp(12f), dp(16f), dp(12f))
      isClickable = true
      isFocusable = true
    }
  }

  // While locked, back leaves the app instead of navigating the hidden UI.
  private fun createBackCallback(activity: Activity): OnBackPressedCallback {
    return object : OnBackPressedCallback(true) {
      override fun handleOnBackPressed() {
        activity.moveTaskToBack(true)
      }
    }
  }

  private fun submitPin(activity: Activity, lockCover: LockCover) {
    if (showLockoutIfNeeded(lockCover)) {
      return
    }

    if (lockCover.verifying) {
      return
    }

    val pin = lockCover.pinInput.text?.toString().orEmpty()
    lockCover.pinInput.setText("")
    lockCover.verifying = true
    val generation = lockGeneration

    scope.launch {
      val correct = try {
        appLockPin.verifyAsync(pin)
      } finally {
        lockCover.verifying = false
      }

      if (correct) {
        if (generation != lockGeneration || !locked) {
          // The app was left and re-locked (or already unlocked) while this PIN was being checked.
          return@launch
        }

        appLockPin.resetFailedAttempts()
        hideKeyboard(activity, lockCover)
        unlock()
        return@launch
      }

      appLockPin.onWrongAttempt()

      if (!showLockoutIfNeeded(lockCover)) {
        showError(lockCover, activity.getString(R.string.app_lock_wrong_pin))
      }
    }
  }

  /** Disables PIN entry while a lockout after too many wrong attempts is running. Returns true if locked out. */
  private fun showLockoutIfNeeded(lockCover: LockCover): Boolean {
    val remainingMs = appLockPin.remainingLockoutMs()
    if (remainingMs <= 0L) {
      lockCover.pinInput.isEnabled = true
      return false
    }

    val seconds = TimeUnit.MILLISECONDS.toSeconds(remainingMs) + 1
    lockCover.pinInput.isEnabled = false
    showError(lockCover, lockCover.view.context.getString(R.string.app_lock_too_many_attempts, seconds))

    lockCover.view.postDelayed({
      if (lockCover.view.isAttachedToWindow && !showLockoutIfNeeded(lockCover)) {
        lockCover.errorText.visibility = View.GONE
      }
    }, 1000L)

    return true
  }

  private fun showError(lockCover: LockCover, message: String) {
    lockCover.errorText.text = message
    lockCover.errorText.visibility = View.VISIBLE
  }

  private fun focusPinInput(activity: Activity, lockCover: LockCover) {
    if (!locked || !lockCover.pinInput.isEnabled) {
      return
    }

    lockCover.pinInput.requestFocus()
    // showSoftInput(SHOW_IMPLICIT) is ignored for a view added over the decor, so ask the window to show the IME.
    WindowCompat.getInsetsController(activity.window, lockCover.pinInput).show(WindowInsetsCompat.Type.ime())
  }

  private fun hideKeyboard(activity: Activity, lockCover: LockCover) {
    val inputMethodManager = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    inputMethodManager?.hideSoftInputFromWindow(lockCover.pinInput.windowToken, 0)
  }

  private fun createBiometricPrompt(activity: FragmentActivity): BiometricPrompt {
    return BiometricPrompt(
      activity,
      ContextCompat.getMainExecutor(activity),
      object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
          promptShowing = false
          appLockPin.resetFailedAttempts()
          unlock()
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
          // Cancelled, "Use PIN" or failed: fall back to the PIN field on the cover.
          Logger.d(TAG, "onAuthenticationError() errorCode=$errorCode, errString=$errString")
          promptShowing = false
          lockCovers[activity]?.let { lockCover -> focusPinInput(activity, lockCover) }
        }
      }
    )
  }

  private fun showBiometricPrompt(activity: FragmentActivity) {
    if (!locked || promptShowing || activity.isFinishing || activity.isDestroyed) {
      return
    }

    val prompt = lockCovers[activity]?.prompt
      ?: return

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
      .setTitle(activity.getString(R.string.app_lock_prompt_title))
      .setNegativeButtonText(activity.getString(R.string.app_lock_use_pin))
      .setAllowedAuthenticators(BIOMETRIC_WEAK)
      .build()

    promptShowing = true
    prompt.authenticate(promptInfo)
  }

  private fun unlock() {
    locked = false
    uiLocked = false

    lockCovers.values.forEach { lockCover ->
      (lockCover.view.parent as? ViewGroup)?.removeView(lockCover.view)
      lockCover.backCallback.remove()
    }
    lockCovers.clear()
  }

  private class LockCover(
    val view: View,
    val pinInput: EditText,
    val errorText: TextView,
    val prompt: BiometricPrompt?,
    val backCallback: OnBackPressedCallback
  ) {
    // True while a submitted PIN is being checked off the main thread.
    var verifying = false
  }

  companion object {
    private const val TAG = "AppPrivacyManager"
    private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private const val COVER_ELEVATION = 1000f

    /**
     * True while the lock cover is shown. Activities that hand touch/key events to controllers before the
     * view hierarchy check this so gestures can't act on the hidden UI.
     */
    @Volatile
    var uiLocked: Boolean = false
      private set

    /** App lock is only active once the user has enabled it and created a PIN. */
    fun isAppLockConfigured(kurobaSettings: KurobaSettings, appLockPin: AppLockPin): Boolean {
      return kurobaSettings.nonBackupable.appLockEnabled.readBlocking() && appLockPin.isSet()
    }

    /** True when the device has a fingerprint/face sensor with something enrolled. */
    fun canUseBiometrics(context: Context): Boolean {
      return BiometricManager.from(context).canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }
  }
}
