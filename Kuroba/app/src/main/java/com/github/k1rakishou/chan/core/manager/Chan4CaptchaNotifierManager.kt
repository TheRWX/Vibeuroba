package com.github.k1rakishou.chan.core.manager

import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.compose.AsyncUiData
import com.github.k1rakishou.chan.core.helper.KurobaSystemNotifications
import com.github.k1rakishou.chan.ui.captcha.chan4.Chan4CaptchaLayoutViewModel
import com.github.k1rakishou.chan.ui.captcha.chan4.Chan4CaptchaLayoutViewModel.CaptchaGenericRateLimitError
import com.github.k1rakishou.chan.ui.captcha.chan4.Chan4CaptchaLayoutViewModel.CaptchaPostRateLimitError
import com.github.k1rakishou.chan.ui.captcha.chan4.Chan4CaptchaLayoutViewModel.CaptchaThreadRateLimitError
import com.github.k1rakishou.chan.ui.helper.AppResources
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.model.data.descriptor.ChanDescriptor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class Chan4CaptchaNotifierManager(
  private val appScope: CoroutineScope,
  private val appResources: AppResources,
  private val kurobaSystemNotifications: KurobaSystemNotifications,
  private val siteManager: SiteManager,
  private val chanThreadManager: ChanThreadManager,
  private val applicationVisibilityManager: ApplicationVisibilityManager
) {
  private var _waitJob: Job? = null
  private var _waiter = CompletableDeferred<Unit>()
  private var _captchaViewShown = false
  private var _captchaViewModelCallbacks: CaptchaViewModelCallbacks? = null

  // Written from the app scope (Default dispatcher), read from the main thread
  @Volatile
  private var _activeCooldown: ActiveCooldown? = null

  /**
   * Returns the cooldown error with the remaining time if there is a cooldown that is still running for the
   * [chanDescriptor]. Used to restore the countdown when the captcha screen is closed and reopened.
   * */
  fun activeCooldownError(chanDescriptor: ChanDescriptor): Chan4CaptchaLayoutViewModel.CaptchaCooldownError? {
    val activeCooldown = _activeCooldown
      ?: return null

    if (activeCooldown.chanDescriptor != chanDescriptor) {
      return null
    }

    val remainingMs = activeCooldown.error.cooldownEndTimeMs - System.currentTimeMillis()
    if (remainingMs <= 0L) {
      _activeCooldown = null
      return null
    }

    return when (activeCooldown.error) {
      is CaptchaGenericRateLimitError -> CaptchaGenericRateLimitError(activeCooldown.error.cooldownEndTimeMs, remainingMs)
      is CaptchaThreadRateLimitError -> CaptchaThreadRateLimitError(activeCooldown.error.cooldownEndTimeMs, remainingMs)
      is CaptchaPostRateLimitError -> CaptchaPostRateLimitError(activeCooldown.error.cooldownEndTimeMs, remainingMs)
      else -> null
    }
  }

  fun onCaptchaViewInitialized(callbacks: CaptchaViewModelCallbacks) {
    _captchaViewShown = true
    _captchaViewModelCallbacks = callbacks
    Logger.debug(TAG) { "onCaptchaViewInitialized()" }
  }

  fun onCaptchaViewDestroyed() {
    _captchaViewShown = false
    _captchaViewModelCallbacks = null
    Logger.debug(TAG) { "onCaptchaViewDestroyed()" }
  }

  fun start(
    waitDescriptor: ChanDescriptor,
    cooldownEndTimeMs: Long,
    cooldownError: Chan4CaptchaLayoutViewModel.CaptchaCooldownError? = null
  ) {
    if (waitDescriptor is ChanDescriptor.CompositeCatalogDescriptor) {
      // Shouldn't be possible
      return
    }

    Logger.debug(TAG) { "start() waitDescriptor: ${waitDescriptor}, cooldownEndTimeMs: ${cooldownEndTimeMs}" }

    val thisCooldown = cooldownError?.let { error -> ActiveCooldown(waitDescriptor, error) }
    _activeCooldown = thisCooldown

    _waitJob?.cancel()
    _waiter.cancel()
    _waiter = CompletableDeferred()

    _waitJob = appScope.launch {
      var currentTime: Long
      val oneSecond = 1000L

      try {
        while (isActive) {
          delay(1000L)

          currentTime = System.currentTimeMillis()
          if (currentTime >= cooldownEndTimeMs) {
            break
          }

          val callbacks = _captchaViewModelCallbacks
            ?: continue

          val currentCaptchaInfo = callbacks.readCurrentCaptchaInfo()
          if (currentCaptchaInfo is AsyncUiData.NotInitialized) {
            continue
          }

          val previousError = (currentCaptchaInfo as? AsyncUiData.Error)?.throwable
            ?: break

          val updatedError = when (previousError) {
            is CaptchaGenericRateLimitError -> {
              CaptchaGenericRateLimitError(
                cooldownEndTimeMs = previousError.cooldownEndTimeMs,
                cooldownMs = previousError.cooldownMs - oneSecond
              )
            }
            is CaptchaThreadRateLimitError -> {
              CaptchaThreadRateLimitError(
                cooldownEndTimeMs = previousError.cooldownEndTimeMs,
                cooldownMs = previousError.cooldownMs - oneSecond
              )
            }
            is CaptchaPostRateLimitError -> {
              CaptchaPostRateLimitError(
                cooldownEndTimeMs = previousError.cooldownEndTimeMs,
                cooldownMs = previousError.cooldownMs - oneSecond
              )
            }
            else -> {
              break
            }
          }

          callbacks.updateCurrentCaptchaInfo(AsyncUiData.Error(updatedError))
        }

        // The wait is over (or was interrupted), so don't restore this cooldown anymore, unless a newer wait has
        // already replaced it.
        if (_activeCooldown === thisCooldown) {
          _activeCooldown = null
        }

        if (!_captchaViewShown || applicationVisibilityManager.isAppInBackground()) {
          val chanDescriptorReadable = waitDescriptor.userReadableString()
          val largeIconUrl = when (waitDescriptor) {
            is ChanDescriptor.CatalogDescriptor -> {
              siteManager.bySiteDescriptorAndActive(waitDescriptor.siteDescriptor())
                ?.configuration
                ?.icon
                ?.url
            }
            is ChanDescriptor.ThreadDescriptor -> {
              chanThreadManager.getChanThread(waitDescriptor)?.getOriginalPostSafe()?.firstImage()?.actualThumbnailUrl
            }
          }

          kurobaSystemNotifications.showNotification(
            KurobaSystemNotifications.NotificationData(
              id = chanDescriptorReadable,
              style = KurobaSystemNotifications.NotificationData.Style.Default(
                title = appResources.string(R.string.captcha_layout_captcha_is_ready_title),
                content = appResources.string(
                  R.string.captcha_layout_captcha_is_ready_description,
                  chanDescriptorReadable
                )
              ),
              priority = KurobaSystemNotifications.NotificationData.Priority.High,
              largeIcon = largeIconUrl
                ?.let { url -> KurobaSystemNotifications.NotificationData.LargeIcon.RemoteUrl(url) }
            )
          )
        }

        _waiter.complete(Unit)
      } catch (error: Throwable) {
        _waiter.cancel()
        throw error
      }
    }
  }

  suspend fun wait(): Boolean {
    try {
      _waiter.await()
      return true
    } catch (ignored: Throwable) {
      return false
    }
  }

  private class ActiveCooldown(
    val chanDescriptor: ChanDescriptor,
    val error: Chan4CaptchaLayoutViewModel.CaptchaCooldownError
  )

  interface CaptchaViewModelCallbacks {
    fun readCurrentCaptchaInfo(): AsyncUiData<Chan4CaptchaLayoutViewModel.CaptchaInfo>
    fun updateCurrentCaptchaInfo(captchaInfo: AsyncUiData<Chan4CaptchaLayoutViewModel.CaptchaInfo>)
  }

  companion object {
    private const val TAG = "Chan4CaptchaNotifierManager"
  }

}