package com.github.k1rakishou.chan.core.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.core_logger.Logger

/**
 * Receives the result of a Vibeuroba self-update session started by
 * [com.github.k1rakishou.chan.core.manager.update.ApkSessionInstaller].
 */
class ApkInstallStatusReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
    val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
    Logger.d(TAG, "onReceive() status=${status}, message=${message}")

    when (status) {
      PackageInstaller.STATUS_PENDING_USER_ACTION -> {
        // Android wants the user to confirm (first self-update, or the system requires it). Show its dialog now
        // instead of leaving the user to find a notification.
        val confirmIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
          @Suppress("DEPRECATION")
          intent.getParcelableExtra(Intent.EXTRA_INTENT)
        }

        if (confirmIntent == null) {
          Logger.e(TAG, "onReceive() STATUS_PENDING_USER_ACTION without a confirmation intent")
          AppUpdatedReceiver.clearSelfUpdatePending(context)
          return
        }

        confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(confirmIntent)
      }
      PackageInstaller.STATUS_SUCCESS -> {
        // The app process is replaced; AppUpdatedReceiver posts the "updated" notification.
      }
      else -> {
        AppUpdatedReceiver.clearSelfUpdatePending(context)

        if (status == PackageInstaller.STATUS_FAILURE_ABORTED) {
          // The user cancelled the confirmation dialog.
          return
        }

        val reason = message ?: "status ${status}"
        Toast.makeText(context, context.getString(R.string.update_install_failed_toast, reason), Toast.LENGTH_LONG)
          .show()
      }
    }
  }

  companion object {
    private const val TAG = "ApkInstallStatusReceiver"
  }
}
