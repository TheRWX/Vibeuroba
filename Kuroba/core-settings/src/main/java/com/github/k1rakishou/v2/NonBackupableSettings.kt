package com.github.k1rakishou.v2

import com.github.k1rakishou.v2.database.KurobaSettingsDatabase

class NonBackupableSettings(
  database: KurobaSettingsDatabase,
  private val nonBackupableSettingsParameters: NonBackupableSettingsParameters,
  override val initialSettingsState: KurobaInitialSettingsState
) : BaseSettings(database) {
  override val backupable: Boolean = false

  val applicationMigrationVersion by lazy {
    createIntSetting(
      key = KurobaSettingKey.NonBackupable.ApplicationMigrationVersion,
      default = nonBackupableSettingsParameters.applicationMigrationVersion
    )
  }

  val settingMigrationPerformed by lazy {
    createBooleanSetting(
      key = KurobaSettingKey.NonBackupable.SettingMigrationPerformed,
      default = false
    )
  }

  // Vibeuroba app lock
  val appLockEnabled by lazy {
    createBooleanSetting(KurobaSettingKey.NonBackupable.VibeurobaAppLockEnabled, false)
  }
  // Salted PBKDF2 hash of the user's PIN ("v1:<iterations>:<salt>:<hash>"), empty when no PIN is set.
  val appLockPinHash by lazy {
    createStringSetting(KurobaSettingKey.NonBackupable.VibeurobaAppLockPinHash, "")
  }
  val appLockFailedAttempts by lazy {
    createIntSetting(KurobaSettingKey.NonBackupable.VibeurobaAppLockFailedAttempts, 0)
  }
  val appLockLockoutUntil by lazy {
    createLongSetting(KurobaSettingKey.NonBackupable.VibeurobaAppLockLockoutUntil, 0L)
  }
}