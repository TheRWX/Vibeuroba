package com.github.k1rakishou.chan.core.usecase

import android.content.Context
import androidx.room.Room
import com.github.k1rakishou.v2.database.KurobaSettingsDatabase
import java.io.Closeable
import java.io.File
import java.io.IOException

/**
 * A copy of the settings database with every non-backupable setting removed, for putting into a backup.
 *
 * Non-backupable settings include the app lock PIN hash. Deleted rows can linger in free pages and in
 * the WAL file, so after deleting them the copy is vacuumed and its WAL is checkpointed into the main
 * file, which leaves no trace of the removed rows in any of the copy's files.
 *
 * The copy is opened with the same Room configuration (and therefore the same journal mode) as the live
 * database. While it is open, [files] lists the main file plus its `-wal`/`-shm` files when the database
 * uses WAL, in a consistent state (empty WAL). That is the same set of files older backups contained, so
 * importing works the same way it always did. Read [files] before calling [close], which deletes the copy.
 */
class SanitizedSettingsDatabaseCopy private constructor(
  private val tempDir: File,
  private val database: KurobaSettingsDatabase
) : Closeable {

  val files: List<File>
    get() = DATABASE_FILE_SUFFIXES
      .map { suffix -> File(tempDir, KurobaSettingsDatabase.DATABASE_NAME + suffix) }
      .filter { file -> file.exists() }

  override fun close() {
    try {
      database.close()
    } finally {
      tempDir.deleteRecursively()
    }
  }

  companion object {
    private val DATABASE_FILE_SUFFIXES = listOf("", "-wal", "-shm")

    /**
     * [sourceDatabaseFile] must be a complete, checkpointed copy of the settings database (its WAL, if
     * any, already folded into it). [tempDir] is deleted and recreated, and deleted again on [close].
     */
    fun create(
      appContext: Context,
      sourceDatabaseFile: File,
      tempDir: File
    ): SanitizedSettingsDatabaseCopy {
      tempDir.deleteRecursively()
      if (!tempDir.mkdirs()) {
        throw IOException("Failed to create '${tempDir.absolutePath}'")
      }

      val copyFile = File(tempDir, KurobaSettingsDatabase.DATABASE_NAME)

      try {
        sourceDatabaseFile.copyTo(copyFile, overwrite = true)
      } catch (error: Throwable) {
        tempDir.deleteRecursively()
        throw error
      }

      val database = Room.databaseBuilder(appContext, KurobaSettingsDatabase::class.java, copyFile.absolutePath)
        .build()

      val sanitizedCopy = SanitizedSettingsDatabaseCopy(tempDir, database)

      try {
        removeNonBackupableSettings(database)
      } catch (error: Throwable) {
        sanitizedCopy.close()
        throw error
      }

      return sanitizedCopy
    }

    private fun removeNonBackupableSettings(database: KurobaSettingsDatabase) {
      val db = database.openHelper.writableDatabase

      db.execSQL("DELETE FROM kuroba_settings WHERE backupable = 0")
      // Rebuild the file so the deleted rows don't survive in free pages.
      db.execSQL("VACUUM")

      // In WAL mode VACUUM writes the rebuilt pages to the WAL, and the main file still holds the old
      // ones until a checkpoint. TRUNCATE copies everything back and empties the WAL. Without WAL this
      // is a no-op that reports busy = 0.
      db.query("PRAGMA wal_checkpoint(TRUNCATE)").use { cursor ->
        if (cursor.moveToFirst() && cursor.getInt(0) != 0) {
          throw IOException("Settings database checkpoint was blocked, refusing to export it")
        }
      }

      db.query("SELECT COUNT(*) FROM kuroba_settings WHERE backupable = 0").use { cursor ->
        if (!cursor.moveToFirst() || cursor.getLong(0) != 0L) {
          throw IOException("Non-backupable settings are still present in the backup copy")
        }
      }
    }
  }
}
