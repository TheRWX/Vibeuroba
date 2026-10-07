package com.github.k1rakishou.chan.core.usecase

import android.content.Context
import com.github.k1rakishou.chan.features.settings.delegate.ExportBackupOptions
import com.github.k1rakishou.chan.utils.BackgroundUtils
import com.github.k1rakishou.common.AppConstants
import com.github.k1rakishou.common.ModularResult
import com.github.k1rakishou.core_logger.LOGGER_DATABASE_NAME
import com.github.k1rakishou.core_logger.Logger
import com.github.k1rakishou.core_themes.ThemeParser
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.file.ExternalFile
import com.github.k1rakishou.model.KurobaMainDatabase
import com.github.k1rakishou.model.repository.DatabaseMetaRepository
import com.github.k1rakishou.v2.database.KurobaSettingsDatabase
import kotlinx.coroutines.delay
import okhttp3.internal.closeQuietly
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.time.measureTime

class ExportBackupFileUseCase(
  private val appContext: Context,
  private val appConstants: AppConstants,
  private val databaseMetaRepository: DatabaseMetaRepository,
  private val kurobaSettingsDatabase: KurobaSettingsDatabase,
  private val fileManager: FileManager
) : ISuspendUseCase<ExportBackupFileUseCase.Params, ModularResult<Unit>> {

  override suspend fun execute(parameter: Params): ModularResult<Unit> {
    BackgroundUtils.ensureBackgroundThread()

    val outputFile = parameter.externalFile
    val exportBackupOptions = parameter.exportBackupOptions

    return ModularResult.Try { doExportInternal(outputFile, exportBackupOptions) }
  }

  private suspend fun doExportInternal(outputFile: ExternalFile, exportBackupOptions: ExportBackupOptions) {
    Logger.d(TAG, "Export start")

    val databases = appContext.databaseList()
    val filesToExport = mutableListOf<File>()

    val lightThemeFile = File(appContext.filesDir, ThemeParser.LIGHT_THEME_FILE_NAME)
    if (lightThemeFile.exists() && lightThemeFile.length() > 0) {
      filesToExport += lightThemeFile
    }

    val darkThemeFile = File(appContext.filesDir, ThemeParser.DARK_THEME_FILE_NAME)
    if (darkThemeFile.exists() && darkThemeFile.length() > 0) {
      filesToExport += darkThemeFile
    }

    val mpvConfFile = File(File(appContext.filesDir, AppConstants.MPV_CONF_DIR), AppConstants.MPV_CONF_FILE)
    if (mpvConfFile.exists() && mpvConfFile.length() > 0) {
      filesToExport += mpvConfFile
    }

    filesToExport += databases.mapNotNull { databaseName ->
      // The settings database is exported separately, from a copy without non-backupable settings.
      if (databaseName.contains(KurobaSettingsDatabase.DATABASE_NAME, ignoreCase = true)) {
        return@mapNotNull null
      }

      val isKurobaAppDatabase =
        databaseName.contains(KurobaMainDatabase.DATABASE_NAME, ignoreCase = true) ||
        (exportBackupOptions.exportLogsDatabase && databaseName.contains(LOGGER_DATABASE_NAME, ignoreCase = true))

      if (!isKurobaAppDatabase) {
        Logger.debug(TAG) { "Skipping database '${databaseName}'" }
        return@mapNotNull null
      }

      return@mapNotNull appContext.getDatabasePath(databaseName)
    }

    if (exportBackupOptions.exportDownloadedThreadsMedia) {
      filesToExport += appConstants.threadDownloaderCacheDir
    }

    filesToExport.forEach { fileToExport ->
      Logger.d(TAG, "File to export: '${fileToExport.absolutePath}'")
    }

    Logger.d(TAG, "Executing checkpoint command...")

    val time = measureTime {
      databaseMetaRepository.checkpoint()
        .unwrap()
    }

    Logger.d(TAG, "Executing checkpoint command... done! took ${time}")

    checkpointLiveSettingsDatabase()
    val sanitizedSettingsDatabase = createSanitizedSettingsDatabaseCopy()

    try {
      sanitizedSettingsDatabase.files.forEach { settingsFile ->
        Logger.d(TAG, "Settings file to export: '${settingsFile.absolutePath}'")
      }

      writeBackupZip(outputFile, filesToExport + sanitizedSettingsDatabase.files)
    } finally {
      sanitizedSettingsDatabase.close()
    }
  }

  /**
   * The settings database holds non-backupable settings such as the app lock PIN hash, which must not
   * end up in a backup file. Export a copy with those rows removed instead of the live database file.
   */
  private fun createSanitizedSettingsDatabaseCopy(): SanitizedSettingsDatabaseCopy {
    return SanitizedSettingsDatabaseCopy.create(
      appContext = appContext,
      sourceDatabaseFile = appContext.getDatabasePath(KurobaSettingsDatabase.DATABASE_NAME),
      tempDir = File(appContext.cacheDir, SETTINGS_EXPORT_TEMP_DIR)
    )
  }

  /**
   * Folds the live WAL into the main file so that the copy of the main file has every current setting. The
   * checkpoint can be blocked for a moment by a concurrent settings write, so try a few times. If it is still
   * blocked the export goes ahead (a settings change made in the last moments may be missing from the backup,
   * which is better than refusing to make a backup at all).
   */
  private suspend fun checkpointLiveSettingsDatabase() {
    for (attempt in 1..SETTINGS_CHECKPOINT_ATTEMPTS) {
      val blocked = kurobaSettingsDatabase.openHelper.writableDatabase
        .query("PRAGMA wal_checkpoint(FULL)")
        .use { cursor -> cursor.moveToFirst() && cursor.getInt(0) != 0 }

      if (!blocked) {
        return
      }

      Logger.w(TAG, "Settings database checkpoint was blocked (attempt ${attempt}/${SETTINGS_CHECKPOINT_ATTEMPTS})")

      if (attempt < SETTINGS_CHECKPOINT_ATTEMPTS) {
        delay(SETTINGS_CHECKPOINT_RETRY_DELAY_MS)
      }
    }

    Logger.w(TAG, "Settings database checkpoint is still blocked, the backup may miss the latest settings changes")
  }

  private fun writeBackupZip(outputFile: ExternalFile, filesToExport: List<File>) {
    val outputStream = fileManager.getOutputStream(outputFile)
      ?: throw IOException("Failed to open output stream for file '${outputFile.getFullPath()}'")
    val zipOutputStream = ZipOutputStream(outputStream)

    Logger.d(TAG, "Output zip file: '${outputFile.getFullPath()}'")

    try {
      // Put the backup version as the first entry
      run {
        zipOutputStream.putNextEntry(ZipEntry(BACKUP_VERSION_ENTRY_NAME))

        val bytes = with(ByteBuffer.allocate(Int.SIZE_BYTES)) {
          putInt(CURRENT_BACKUP_VERSION)
          array()
        }

        zipOutputStream.write(bytes)
        zipOutputStream.closeEntry()
      }

      zipFiles(null, filesToExport, zipOutputStream) { directory, fileToExport ->
        val fileName = when {
          fileToExport == appConstants.threadDownloaderCacheDir -> THREAD_DOWNLOADS_CACHE_DIR
          else -> fileToExport.name
        }

        if (directory == null) {
          return@zipFiles fileName
        }

        return@zipFiles directory + fileName
      }

      Logger.d(TAG, "Export success!")
    } catch (error: Throwable) {
      Logger.e(TAG, "Export error", error)
      throw error
    } finally {
      zipOutputStream.finish()
      zipOutputStream.flush()
      outputStream.closeQuietly()
      zipOutputStream.closeQuietly()
    }
  }

  private fun zipFiles(
    directory: String?,
    filesToExport: List<File>,
    zipOutputStream: ZipOutputStream,
    selectFileName: (String?, File) -> String
  ) {
    for (fileToExport in filesToExport) {
      if (fileToExport.isDirectory) {
        val innerFiles = fileToExport.listFiles()?.toList() ?: emptyList()
        val newDirectory = if (directory == null) {
          selectFileName(null, fileToExport) + "/"
        } else {
          selectFileName(directory, fileToExport) + "/"
        }

        zipFiles(newDirectory, innerFiles, zipOutputStream, selectFileName)

        continue
      }

      val fileInputStream = FileInputStream(fileToExport)
      val bufferedInputStream = BufferedInputStream(fileInputStream, BUFFER_SIZE)

      try {
        val zipEntryName = selectFileName(directory, fileToExport)
        zipOutputStream.putNextEntry(ZipEntry(zipEntryName))
        bufferedInputStream.copyTo(zipOutputStream, BUFFER_SIZE)

        Logger.d(TAG, "Writing file (zipEntryName='${zipEntryName}') '${fileToExport.absolutePath}' success!")
      } catch (error: Throwable) {
        Logger.e(TAG, "Writing file '${fileToExport.absolutePath}' error", error)
        throw error
      } finally {
        zipOutputStream.closeEntry()
        fileInputStream.closeQuietly()
        bufferedInputStream.closeQuietly()
      }
    }
  }

  data class Params(
    val externalFile: ExternalFile,
    val exportBackupOptions: ExportBackupOptions
  )

  companion object {
    private const val TAG = "ExportBackupFileUseCase"

    const val BACKUP_VERSION_ENTRY_NAME = "backup_version"
    const val CURRENT_BACKUP_VERSION = 1

    const val THREAD_DOWNLOADS_CACHE_DIR = "thread_downloads_cache_dir"
    private const val SETTINGS_EXPORT_TEMP_DIR = "settings_backup_export"
    private const val SETTINGS_CHECKPOINT_ATTEMPTS = 3
    private const val SETTINGS_CHECKPOINT_RETRY_DELAY_MS = 200L
    const val BUFFER_SIZE = 8192

    /**
     * An export that was interrupted (for example the app got killed) leaves behind the copy of the settings
     * database that was not cleaned yet and still contains the non-backupable settings. A later export would
     * delete it, but it should not sit in the cache until then, so this is called when the app starts.
     */
    fun deleteLeftoverTempFiles(appContext: Context) {
      File(appContext.cacheDir, SETTINGS_EXPORT_TEMP_DIR).deleteRecursively()
    }
  }
}