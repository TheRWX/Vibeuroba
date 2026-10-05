package com.github.k1rakishou.chan.core.manager.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.github.k1rakishou.chan.core.receiver.ApkInstallStatusReceiver
import com.github.k1rakishou.chan.core.receiver.AppUpdatedReceiver
import com.github.k1rakishou.core_logger.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Vibeuroba: installs a downloaded update through a PackageInstaller session instead of handing the APK to the
 * system installer screen.
 *
 * On Android 12+ the session asks for no user action. Android allows that once Vibeuroba is the installer of
 * record of itself, i.e. after the first update it installed; until then (or when the system decides otherwise)
 * the session reports STATUS_PENDING_USER_ACTION and [ApkInstallStatusReceiver] shows the one-tap confirmation.
 */
object ApkSessionInstaller {
  private const val TAG = "ApkSessionInstaller"

  suspend fun install(context: Context, apkFile: File) {
    val appContext = context.applicationContext
    val packageInstaller = appContext.packageManager.packageInstaller

    val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
      setAppPackageName(appContext.packageName)
      setSize(apkFile.length())

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
      }
    }

    val sessionId = packageInstaller.createSession(params)
    Logger.d(TAG, "install() created session ${sessionId} for ${apkFile.absolutePath} (${apkFile.length()} bytes)")

    try {
      packageInstaller.openSession(sessionId).use { session ->
        withContext(Dispatchers.IO) {
          session.openWrite("base.apk", 0, apkFile.length()).use { output ->
            apkFile.inputStream().use { input -> input.copyTo(output) }
            session.fsync(output)
          }
        }

        // Lets AppUpdatedReceiver tell a self-update apart from installs done by other installers.
        AppUpdatedReceiver.markSelfUpdatePending(appContext)

        val statusIntent = Intent(appContext, ApkInstallStatusReceiver::class.java)
        // The system adds the status extras to this intent, so it must be mutable.
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
          PendingIntent.FLAG_UPDATE_CURRENT
        }

        val statusReceiver = PendingIntent.getBroadcast(appContext, sessionId, statusIntent, pendingIntentFlags)
        session.commit(statusReceiver.intentSender)
      }
    } catch (error: Throwable) {
      packageInstaller.abandonSession(sessionId)
      AppUpdatedReceiver.clearSelfUpdatePending(appContext)
      throw error
    }
  }
}
