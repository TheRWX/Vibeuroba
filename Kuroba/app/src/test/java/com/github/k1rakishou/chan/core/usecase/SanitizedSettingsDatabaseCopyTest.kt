package com.github.k1rakishou.chan.core.usecase

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.github.k1rakishou.v2.database.KurobaSettingsDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class SanitizedSettingsDatabaseCopyTest {
  private lateinit var context: Context
  private lateinit var workDir: File

  @Before
  fun setUp() {
    context = RuntimeEnvironment.getApplication()
    workDir = File(context.cacheDir, "sanitized_settings_test")
    workDir.deleteRecursively()
    workDir.mkdirs()
  }

  @After
  fun tearDown() {
    workDir.deleteRecursively()
  }

  @Test
  fun `copy keeps backupable settings and leaves no trace of non-backupable ones`() {
    val sourceFile = createSourceDatabase()
    val tempDir = File(workDir, "export")

    SanitizedSettingsDatabaseCopy.create(context, sourceFile, tempDir).use { copy ->
      val files = copy.files
      assertTrue(files.isNotEmpty())
      assertEquals(KurobaSettingsDatabase.DATABASE_NAME, files.first().name)

      val bytes = files.map { file -> String(file.readBytes(), Charsets.ISO_8859_1) }
      assertTrue(bytes.none { content -> content.contains(SECRET_MARKER) })
      assertTrue(bytes.any { content -> content.contains(KEPT_MARKER) })

      val exportedMainFile = File(workDir, "exported.db")
      files.first().copyTo(exportedMainFile)
      assertEquals(listOf("kept_setting"), readKeys(exportedMainFile))
    }

    assertFalse(tempDir.exists())
    // The source is never modified.
    assertEquals(listOf("kept_setting", "secret_setting"), readKeys(sourceFile).sorted())
  }

  private fun createSourceDatabase(): File {
    val sourceFile = File(workDir, KurobaSettingsDatabase.DATABASE_NAME)
    val database = Room.databaseBuilder(context, KurobaSettingsDatabase::class.java, sourceFile.absolutePath)
      .allowMainThreadQueries()
      .build()

    val db = database.openHelper.writableDatabase
    db.insert("kuroba_settings", SQLiteDatabase.CONFLICT_ABORT, setting("kept_setting", KEPT_MARKER, true))
    db.insert("kuroba_settings", SQLiteDatabase.CONFLICT_ABORT, setting("secret_setting", SECRET_MARKER, false))
    database.close()

    return sourceFile
  }

  private fun setting(key: String, value: String, backupable: Boolean): ContentValues {
    return ContentValues().apply {
      put("setting_key", key)
      put("setting_value", value.toByteArray())
      put("backupable", if (backupable) 1 else 0)
      put("created_on", 0L)
      put("last_accessed_on", 0L)
    }
  }

  private fun readKeys(databaseFile: File): List<String> {
    val db = SQLiteDatabase.openDatabase(databaseFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)

    return db.use {
      db.rawQuery("SELECT setting_key FROM kuroba_settings", null).use { cursor ->
        buildList {
          while (cursor.moveToNext()) {
            add(cursor.getString(0))
          }
        }
      }
    }
  }

  companion object {
    private const val SECRET_MARKER = "pin-hash-0123456789abcdef"
    private const val KEPT_MARKER = "kept-value-fedcba9876543210"
  }
}
