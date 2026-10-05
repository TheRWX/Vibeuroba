package com.github.k1rakishou.chan.features.settings.screen

import android.content.Context
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.core.helper.AppLockPin
import com.github.k1rakishou.chan.core.helper.DialogFactory
import com.github.k1rakishou.chan.core.helper.ProxyStorage
import com.github.k1rakishou.chan.core.manager.AppPrivacyManager
import com.github.k1rakishou.chan.features.proxies.ProxySetupController
import com.github.k1rakishou.chan.features.settings.SettingsScreen
import com.github.k1rakishou.chan.features.settings.setting.SettingUiElement
import com.github.k1rakishou.chan.ui.controller.dialog.KurobaComposeDialogController
import com.github.k1rakishou.chan.ui.helper.AppResources
import com.github.k1rakishou.v2.KurobaSettings

class SecuritySettingsScreenBuilder(
  private val appResources: AppResources,
  private val proxyStorage: ProxyStorage,
  private val kurobaSettings: KurobaSettings,
  private val dialogFactory: DialogFactory,
  private val appLockPin: AppLockPin
) : SettingsScreenBuilder {

  override suspend fun build(
    context: Context,
    settingActions: SettingActions,
    settingsScreen: SettingsScreen
  ) {
    with(settingsScreen) {
      buildMainGroup(context, settingActions)
      buildPrivacyGroup(context, settingActions)
    }
  }

  private suspend fun SettingsScreen.buildMainGroup(
    context: Context,
    settingActions: SettingActions
  ) {
    addGroup(
      key = "main",
      title = appResources.string(R.string.settings_screen_security_main_group),
    ) {
      addSetting(
        SettingUiElement.Link(
          composeKey = "Proxy",
          title = { appResources.string(R.string.settings_screen_security_proxy) },
          description = {
            val proxiesCount = proxyStorage.getCount()
            return@Link appResources.string(R.string.settings_screen_security_proxy_description, proxiesCount)
          },
          callback = { settingActions.pushController(ProxySetupController(context)) }
        )
      )
    }
  }

  private suspend fun SettingsScreen.buildPrivacyGroup(
    context: Context,
    settingActions: SettingActions
  ) {
    addGroup(
      key = "vibeuroba_privacy",
      title = appResources.string(R.string.settings_group_vibeuroba_privacy),
    ) {
      // The settings screen doesn't refresh a Link's texts after its callback, so this entry describes what it
      // does and the dependent entries below (enabled only while app lock is on) show the current state.
      addSetting(
        SettingUiElement.Link(
          // Same key as the appLockEnabled setting so dependent entries show "Requires: App lock".
          composeKey = "VibeurobaAppLockEnabled",
          title = { appResources.string(R.string.setting_app_lock) },
          description = { appResources.string(R.string.setting_app_lock_description) },
          callback = {
            if (AppPrivacyManager.isAppLockConfigured(kurobaSettings, appLockPin)) {
              manageAppLock(context, settingActions)
            } else {
              turnOnAppLock(context, settingActions)
            }
          }
        )
      )

      addSetting(
        SettingUiElement.Bool(
          title = { appResources.string(R.string.setting_app_lock_biometrics) },
          description = {
            if (AppPrivacyManager.canUseBiometrics(context)) {
              appResources.string(R.string.setting_app_lock_biometrics_description)
            } else {
              appResources.string(R.string.setting_app_lock_biometrics_unavailable)
            }
          },
          // No `dependencies` here: the settings widget switches a Bool off whenever its dependency turns off,
          // which would silently disable biometrics every time app lock is turned off and on again.
          setting = kurobaSettings.application.appLockBiometrics
        )
      )

      addSetting(
        SettingUiElement.Items<Long>(
          title = { appResources.string(R.string.setting_app_lock_timeout) },
          setting = kurobaSettings.application.appLockTimeoutSeconds,
          dependencies = listOf(kurobaSettings.nonBackupable.appLockEnabled),
          items = APP_LOCK_TIMEOUTS_SECONDS,
          itemNameMapper = { seconds ->
            when (seconds) {
              0L -> appResources.string(R.string.setting_app_lock_timeout_immediately)
              60L -> appResources.string(R.string.setting_app_lock_timeout_one_minute)
              300L -> appResources.string(R.string.setting_app_lock_timeout_five_minutes)
              else -> appResources.string(R.string.setting_app_lock_timeout_fifteen_minutes)
            }
          }
        )
      )

      addSetting(
        SettingUiElement.Bool(
          title = { appResources.string(R.string.setting_hide_from_screenshots) },
          description = { appResources.string(R.string.setting_hide_from_screenshots_description) },
          setting = kurobaSettings.application.hideFromScreenshots
        )
      )
    }
  }

  private suspend fun turnOnAppLock(context: Context, settingActions: SettingActions) {
    val newPin = askForNewPin(context, settingActions) ?: return

    appLockPin.saveAsync(newPin)
    kurobaSettings.nonBackupable.appLockEnabled.write(true)
    settingActions.showToast(appResources.string(R.string.app_lock_enabled_toast))
  }

  /** App lock is on: change the PIN (positive) or turn app lock off (neutral). Both need the current PIN. */
  private suspend fun manageAppLock(context: Context, settingActions: SettingActions) {
    val params = KurobaComposeDialogController.Params(
      title = KurobaComposeDialogController.Text.Id(R.string.setting_app_lock),
      description = KurobaComposeDialogController.Text.Id(R.string.setting_app_lock_manage_description),
      negativeButton = KurobaComposeDialogController.DialogButton(buttonText = R.string.cancel),
      neutralButton = KurobaComposeDialogController.DialogButton(buttonText = R.string.app_lock_turn_off),
      positiveButton = KurobaComposeDialogController.PositiveDialogButton(buttonText = R.string.setting_app_lock_change_pin)
    )

    dialogFactory.showDialog(context = context, params = params)
      ?: return

    when (params.awaitButtonClick()) {
      KurobaComposeDialogController.Params.ClickedButton.Positive -> changePin(context, settingActions)
      KurobaComposeDialogController.Params.ClickedButton.Neutral -> turnOffAppLock(context, settingActions)
      KurobaComposeDialogController.Params.ClickedButton.Negative,
      null -> return
    }
  }

  private suspend fun turnOffAppLock(context: Context, settingActions: SettingActions) {
    if (!askForCurrentPin(context, settingActions)) {
      return
    }

    kurobaSettings.nonBackupable.appLockEnabled.write(false)
    appLockPin.clear()
    settingActions.showToast(appResources.string(R.string.app_lock_disabled_toast))
  }

  private suspend fun changePin(context: Context, settingActions: SettingActions) {
    if (!askForCurrentPin(context, settingActions)) {
      return
    }

    val newPin = askForNewPin(context, settingActions) ?: return
    appLockPin.saveAsync(newPin)
    settingActions.showToast(appResources.string(R.string.app_lock_pin_changed_toast))
  }

  /** Asks for the current PIN; returns true only if it matches. */
  private suspend fun askForCurrentPin(context: Context, settingActions: SettingActions): Boolean {
    val pin = askForPin(context, R.string.app_lock_enter_current_pin, null)
      ?: return false

    if (!appLockPin.verifyAsync(pin)) {
      settingActions.showToast(appResources.string(R.string.app_lock_wrong_pin))
      return false
    }

    return true
  }

  /** Asks the user to create a PIN and confirm it; returns null if cancelled or invalid. */
  private suspend fun askForNewPin(context: Context, settingActions: SettingActions): String? {
    val pin = askForPin(context, R.string.app_lock_create_pin, R.string.app_lock_create_pin_description)
      ?: return null

    if (!AppLockPin.isValidFormat(pin)) {
      settingActions.showToast(
        appResources.string(R.string.app_lock_pin_invalid, AppLockPin.MIN_LENGTH, AppLockPin.MAX_LENGTH)
      )
      return null
    }

    val confirmation = askForPin(context, R.string.app_lock_confirm_pin, null)
      ?: return null

    if (pin != confirmation) {
      settingActions.showToast(appResources.string(R.string.app_lock_pin_mismatch))
      return null
    }

    return pin
  }

  private suspend fun askForPin(context: Context, titleId: Int, descriptionId: Int?): String? {
    val params = KurobaComposeDialogController.Params(
      title = KurobaComposeDialogController.Text.Id(titleId),
      description = descriptionId?.let { id -> KurobaComposeDialogController.Text.Id(id) },
      inputs = listOf(
        KurobaComposeDialogController.Input.Pin(
          hint = KurobaComposeDialogController.Text.Id(R.string.app_lock_pin_hint)
        )
      ),
      negativeButton = KurobaComposeDialogController.DialogButton(buttonText = R.string.cancel),
      positiveButton = KurobaComposeDialogController.PositiveDialogButton(buttonText = R.string.ok)
    )

    dialogFactory.showDialog(context = context, params = params)
      ?: return null

    return params.awaitInputResult().valueOrNull()
  }

  companion object {
    private val APP_LOCK_TIMEOUTS_SECONDS = listOf(0L, 60L, 300L, 900L)
  }
}
