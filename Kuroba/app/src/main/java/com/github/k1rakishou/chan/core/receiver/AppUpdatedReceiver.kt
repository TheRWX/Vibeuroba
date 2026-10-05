package com.github.k1rakishou.chan.core.receiver

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.github.k1rakishou.chan.BuildConfig
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils
import com.github.k1rakishou.core_logger.Logger

/**
 * Android kills the app when it updates itself and doesn't let it reopen itself afterwards, so after a
 * self-update (ACTION_MY_PACKAGE_REPLACED) post a notification that opens the updated app.
 */
class AppUpdatedReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) {
      return
    }

    if (!consumeSelfUpdatePending(context)) {
      // Updated by another installer (adb, file manager, ...): nothing to announce.
      return
    }

    Logger.d(TAG, "onReceive() self-update installed, versionName=${BuildConfig.VERSION_NAME}")
    showUpdatedNotification(context)
  }

  @SuppressLint("MissingPermission")
  private fun showUpdatedNotification(context: Context) {
    if (!AppModuleAndroidUtils.hasPostNotificationsPermission(context)) {
      return
    }

    val notificationManager = NotificationManagerCompat.from(context)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        context.getString(R.string.update_installed_channel_name),
        NotificationManager.IMPORTANCE_DEFAULT
      )
      notificationManager.createNotificationChannel(channel)
    }

    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
      ?: return

    val contentIntent = PendingIntent.getActivity(
      context,
      0,
      launchIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_stat_notify_alert)
      .setContentTitle(context.getString(R.string.update_installed_title, BuildConfig.VERSION_NAME))
      .setContentText(context.getString(R.string.update_installed_text))
      .setContentIntent(contentIntent)
      .setAutoCancel(true)
      .build()

    notificationManager.notify(NOTIFICATION_ID, notification)
  }

  companion object {
    private const val TAG = "AppUpdatedReceiver"
    private const val CHANNEL_ID = "vibeuroba_app_updates"
    private const val NOTIFICATION_ID = 0x5EB0
    private const val PREFS_NAME = "vibeuroba_self_update"
    private const val KEY_PENDING = "self_update_pending"

    // Plain SharedPreferences: this runs in a fresh process right after the update, before the app's settings
    // database and dependency graph exist.
    fun markSelfUpdatePending(context: Context) {
      prefs(context).edit().putBoolean(KEY_PENDING, true).commit()
    }

    fun clearSelfUpdatePending(context: Context) {
      prefs(context).edit().remove(KEY_PENDING).apply()
    }

    private fun consumeSelfUpdatePending(context: Context): Boolean {
      val pending = prefs(context).getBoolean(KEY_PENDING, false)
      if (pending) {
        prefs(context).edit().remove(KEY_PENDING).commit()
      }

      return pending
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  }
}
