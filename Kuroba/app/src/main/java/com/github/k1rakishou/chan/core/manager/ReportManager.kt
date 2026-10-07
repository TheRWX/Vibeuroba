package com.github.k1rakishou.chan.core.manager

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.github.k1rakishou.chan.BuildConfig
import com.github.k1rakishou.chan.Chan
import com.github.k1rakishou.chan.core.concurrency.SerializedCoroutineExecutor
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.common.isNotNullNorEmpty
import com.github.k1rakishou.v2.KurobaSettings
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ReportManager(
  private val kurobaSettings: KurobaSettings,
  private val appScope: CoroutineScope,
  private val appContext: Context,
  private val appConstants: AppConstants
) {
  private val activityManager: ActivityManager?
    get() = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager

  private val serializedCoroutineExecutor = SerializedCoroutineExecutor(
    scope = appScope,
    dispatcher = Dispatchers.Default
  )

  fun sendComment(
    issueNumber: Int,
    description: String,
    logs: String?,
    onReportSendResult: (ModularResult<Unit>) -> Unit
  ) {
    serializedCoroutineExecutor.post {
      val body = buildString(8192) {
        appendLine(description)

        if (logs.isNotNullNorEmpty()) {
          append("```")
          append(logs)
          append("```")
        }
      }

      val request = ReportRequest.comment(
        body = body
      )

      val result = sendInternal(
        reportRequest = request,
        issueNumber = issueNumber
      )

      withContext(Dispatchers.Main) { onReportSendResult.invoke(result) }
    }
  }

  fun sendCrashlog(
    title: String,
    body: String,
    onReportSendResult: (ModularResult<Unit>) -> Unit
  ) {
    serializedCoroutineExecutor.post {
      val request = ReportRequest.crashLog(
        title = title,
        body = body
      )

      val result = sendInternal(request)
      withContext(Dispatchers.Main) { onReportSendResult.invoke(result) }
    }
  }

  fun sendReport(
    title: String,
    description: String,
    logs: String?,
    onReportSendResult: (ModularResult<Unit>) -> Unit
  ) {
    require(title.isNotEmpty()) { "title is empty" }
    require(description.isNotEmpty() || logs != null) { "description is empty" }

    serializedCoroutineExecutor.post {
      val body = buildString(8192) {
        appendLine(description)

        if (logs.isNotNullNorEmpty()) {
          append("```")
          append(logs)
          append("```")
        }
      }

      val request = ReportRequest.report(
        title = title,
        body = body
      )

      val result = sendInternal(request)
      withContext(Dispatchers.Main) { onReportSendResult.invoke(result) }
    }
  }

  fun getReportFooter(context: Context): String {
    val appRunningTime = (((appContext as? Chan)?.appRunningTime) ?: -1L).toString()

    return getReportFooter(context, appRunningTime, appConstants.userAgentMightBeOverridden)
  }

  @Suppress("MaxLineLength")
  fun getReportFooter(context: Context, appRunningTime: String, userAgent: String): String {
    return buildString(capacity = 2048) {
      appendLine("------------------------------")
      appendLine("Android API Level: " + Build.VERSION.SDK_INT)
      appendLine("App Version: " + BuildConfig.VERSION_NAME)
      appendLine("Phone Model: " + Build.MANUFACTURER + " " + Build.MODEL)

      appendLine("usePrereleaseBuilds: " + kurobaSettings.application.usePrereleaseBuilds.readBlocking())
      appendLine("isLowRamDevice: ${kurobaSettings.application.isLowRamDeviceBlocking()}, " +
        "isLowRamDeviceForced: ${kurobaSettings.application.isLowRamDeviceForced.readBlocking()}")
      appendLine("MemoryClass: ${activityManager?.memoryClass}")
      appendLine("App running time: ${appRunningTime}")
      appendLine("System animations state: ${systemAnimationsState(context)}")
      appendLine("------------------------------")
      appendLine("Current layout mode: ${kurobaSettings.application.getCurrentLayoutModeBlocking().name}")
      appendLine("Board view mode: ${kurobaSettings.application.boardPostViewMode.readBlocking()}")
      appendLine("Prefetching enabled: ${kurobaSettings.application.prefetchMedia.readBlocking()}")
      appendLine("Hi-res thumbnails enabled: ${kurobaSettings.application.highResCells.readBlocking()}")
      appendLine("mediaViewerMaxOffscreenPages: ${kurobaSettings.application.mediaViewerMaxOffscreenPages.readBlocking()}")
      appendLine("useMpvVideoPlayer: ${kurobaSettings.application.useMpvVideoPlayer.readBlocking()}")
      appendLine("userAgent: ${userAgent}")
      appendLine("customUserAgent: ${appConstants.kurobaExCustomUserAgent}")

      appendLine("maxPostsCountInPostsCache: ${appConstants.maxPostsCountInPostsCache}")
      appendLine("maxAmountOfPostsInDatabase: ${appConstants.maxAmountOfPostsInDatabase}")
      appendLine("maxAmountOfThreadsInDatabase: ${appConstants.maxAmountOfThreadsInDatabase}")

      appendLine("diskCacheSizeMegabytes: ${kurobaSettings.application.diskCacheSizeMegabytes.readBlocking()}")
      appendLine("prefetchDiskCacheSizeMegabytes: ${kurobaSettings.application.prefetchDiskCacheSizeMegabytes.readBlocking()}")
      appendLine("diskCacheCleanupRemovePercent: ${kurobaSettings.application.diskCacheCleanupRemovePercent.readBlocking()}")

      appendLine("ImageSaver root directory: ${kurobaSettings.internal.imageSaverV2PersistedOptions.readBlocking().rootDirectoryUri}")
      appendLine("OkHttp IPv6 support enabled: ${kurobaSettings.application.okHttpAllowIpv6.readBlocking()}")

      appendLine("Foreground watcher enabled: ${kurobaSettings.application.watchEnabled.readBlocking()}")
      if (kurobaSettings.application.watchEnabled.readBlocking()) {
        appendLine("Watch foreground interval: ${kurobaSettings.application.watchForegroundInterval.readBlocking()}")
      }

      appendLine("Background watcher enabled: ${kurobaSettings.application.watchBackground.readBlocking()}")
      if (kurobaSettings.application.watchBackground.readBlocking()) {
        appendLine("Watch background interval: ${kurobaSettings.application.watchBackgroundInterval.readBlocking()}")
      }

      appendLine("Filter watch enabled: ${kurobaSettings.application.filterWatchEnabled.readBlocking()}")
      if (kurobaSettings.application.filterWatchEnabled.readBlocking()) {
        appendLine("Filter watch interval: ${kurobaSettings.application.filterWatchInterval.readBlocking()}")
      }

      appendLine("Thread downloader interval: ${kurobaSettings.application.threadDownloaderUpdateInterval.readBlocking()}")
      appendLine("Thread downloader download media on metered network: " +
        "${kurobaSettings.application.threadDownloaderDownloadMediaOnMeteredNetwork.readBlocking()}")

      appendLine("------------------------------")
    }
  }

  private fun systemAnimationsState(context: Context): String {
    val duration = Settings.Global.getFloat(
      context.contentResolver,
      Settings.Global.ANIMATOR_DURATION_SCALE, 0f
    )

    val transition = Settings.Global.getFloat(
      context.contentResolver,
      Settings.Global.TRANSITION_ANIMATION_SCALE, 0f
    )

    val window = Settings.Global.getFloat(
      context.contentResolver,
      Settings.Global.WINDOW_ANIMATION_SCALE, 0f
    )

    return "duration: ${duration}, transition: ${transition}, window: ${window}"
  }

  /**
   * Vibeuroba is a private fork: reports are never uploaded. Upstream posted them to the public
   * kurobaexreports/reports GitHub repo; instead, hand the report to the Android share sheet so the
   * user decides where it goes (notes, email, a private issue, ...).
   */
  private suspend fun sendInternal(reportRequest: ReportRequest, issueNumber: Int? = null): ModularResult<Unit> {
    return ModularResult.Try {
      val appName = AndroidUtils.applicationLabel
      val subject = when {
        issueNumber != null -> "$appName report comment (issue #$issueNumber)"
        reportRequest.title.isNullOrEmpty() -> "$appName report"
        else -> reportRequest.title
      }

      val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, reportRequest.body)
      }

      val chooserIntent = Intent.createChooser(sendIntent, subject).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }

      withContext(Dispatchers.Main) { appContext.startActivity(chooserIntent) }
    }
  }

  data class ReportRequest(
    @SerializedName("title")
    val title: String?,
    @SerializedName("body")
    val body: String,
    @SerializedName("labels")
    val labels: List<String>
  ) {

    companion object {

      fun crashLog(title: String, body: String): ReportRequest {
        return ReportRequest(
          title = title,
          body = body,
          labels = listOf("Vibeuroba", "New", "Crash")
        )
      }

      fun report(title: String, body: String): ReportRequest {
        return ReportRequest(
          title = title,
          body = body,
          labels = listOf("Vibeuroba", "New", "Report")
        )
      }

      fun comment(body: String): ReportRequest {
        return ReportRequest(
          title = null,
          body = body,
          labels = emptyList()
        )
      }

    }

  }

  data class ReportResponse(
    @SerializedName("message")
    val errorMessage: String?,
  )

  companion object {
    const val MAX_TITLE_LENGTH = 512
    const val MAX_DESCRIPTION_LENGTH = 8192
    const val MAX_LOGS_LENGTH = 65535
  }

}