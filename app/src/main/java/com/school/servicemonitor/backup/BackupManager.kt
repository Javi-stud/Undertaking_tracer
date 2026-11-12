package com.school.servicemonitor.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.DocumentsContract
import androidx.room.withTransaction
import com.school.servicemonitor.data.AppDatabase
import com.school.servicemonitor.data.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class BackupSummary(
    val version: Int,
    val sizeBytes: Long,
    val students: Int,
    val sanctions: Int,
    val attempts: Int
)

sealed interface InspectResult {
    data class Valid(val summary: BackupSummary) : InspectResult
    data class Invalid(val message: String) : InspectResult
}

sealed interface BackupOutcome {
    data class Success(val sizeBytes: Long) : BackupOutcome
    data class Failed(val message: String) : BackupOutcome
}

class BackupManager(
    private val context: Context,
    private val database: AppDatabase,
    private val settings: SettingsRepository
) {

    companion object {
        const val PENDING_NAME = "pending_restore.db"
        private const val MAX_BYTES = 200L * 1024 * 1024
        private const val CHECK_DB_NAME = "restore_check.db"
    }

    private val staging get() = File(context.cacheDir, "restore_staging.db")
    private val pending get() = File(context.filesDir, PENDING_NAME)

    // ------------------------------------------------------------------ backup

    /** Folds the write-ahead log into the main file so the main file is complete. */
    private fun checkpoint() {
        database.openHelper.writableDatabase
            .query("PRAGMA wal_checkpoint(TRUNCATE)")
            .use { it.moveToFirst() }
    }

    private fun walLength(dbFile: File): Long = File(dbFile.path + "-wal").let { if (it.exists()) it.length() else 0L }

    suspend fun export(uri: Uri): BackupOutcome = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val snapshot = File(context.cacheDir, "backup_snapshot.db")
        val verify = File(context.cacheDir, "backup_verify.db")
        try {
            val dbFile = context.getDatabasePath(AppDatabase.DB_NAME)

            // Take a consistent copy. While the transaction is open nobody else can write.
            var copied = false
            for (attempt in 1..3) {
                if (copied) break
                checkpoint()
                database.withTransaction {
                    if (walLength(dbFile) == 0L) {
                        dbFile.copyTo(snapshot, overwrite = true)
                        copied = true
                    }
                }
            }
            if (!copied) {
                return@withContext BackupOutcome.Failed("The database was busy. Please try again.")
            }

            val out = resolver.openOutputStream(uri, "wt")
                ?: return@withContext BackupOutcome.Failed("The chosen location could not be opened.")
            out.use { stream -> snapshot.inputStream().use { it.copyTo(stream) } }

            // Read the saved file back and check it, so a bad backup is never trusted.
            val input = resolver.openInputStream(uri)
                ?: return@withContext BackupOutcome.Failed("The saved backup could not be read back.")
            input.use { stream -> verify.outputStream().use { stream.copyTo(it) } }
            when (val check = inspect(verify, deep = false)) {
                is InspectResult.Invalid -> {
                    runCatching { DocumentsContract.deleteDocument(resolver, uri) }
                    return@withContext BackupOutcome.Failed(
                        "The backup failed its check and was removed: ${check.message}"
                    )
                }
                is InspectResult.Valid -> Unit
            }

            settings.setValue(SettingsRepository.KEY_LAST_BACKUP_AT, System.currentTimeMillis().toString())
            BackupOutcome.Success(snapshot.length())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, uri) }
            BackupOutcome.Failed("The backup could not be created: ${e.message ?: "unknown error"}")
        } finally {
            snapshot.delete()
            verify.delete()
        }
    }

    // ----------------------------------------------------------------- restore

    /** Copies the chosen file to a temporary place and checks it. Nothing is replaced yet. */
    suspend fun stage(uri: Uri): InspectResult = withContext(Dispatchers.IO) {
        try {
            staging.delete()
            val input = context.contentResolver.openInputStream(uri)
                ?: return@withContext InspectResult.Invalid("The file could not be opened.")
            var total = 0L
            input.use { stream ->
                staging.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = stream.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > MAX_BYTES) {
                            staging.delete()
                            return@withContext InspectResult.Invalid("This file is too large to be a backup.")
                        }
                        out.write(buffer, 0, n)
                    }
                }
            }
            val result = inspect(staging, deep = true)
            if (result is InspectResult.Invalid) staging.delete()
            result
        } catch (e: CancellationException) {
            staging.delete()
            throw e
        } catch (e: Exception) {
            staging.delete()
            InspectResult.Invalid("The file could not be read: ${e.message ?: "unknown error"}")
        }
    }

    /**
     * The operator confirmed. Moves the checked file to where the next app start will pick it up.
     * Returns an error message, or null when the app may restart now.
     */
    suspend fun commitStaged(): String? = withContext(Dispatchers.IO) {
        try {
            if (!staging.exists()) {
                return@withContext "The selected backup is no longer available. Choose the file again."
            }
            checkpoint()   // the current database file is complete before it is replaced
            val tmp = File(pending.path + ".tmp")
            staging.copyTo(tmp, overwrite = true)
            if (!tmp.renameTo(pending)) {
                tmp.copyTo(pending, overwrite = true)
                tmp.delete()
            }
            staging.delete()
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            pending.delete()
            "The restore could not be prepared: ${e.message ?: "unknown error"}. Nothing was changed."
        }
    }

    fun discardStaged() { staging.delete() }

    /** The message left by the last restore attempt, shown once. */
    fun takeRestoreResult(): String? = RestoreApplier.takeResult(context)

    // -------------------------------------------------------------- the checks

    private fun inspect(file: File, deep: Boolean): InspectResult {
        val header = ByteArray(16)
        val read = file.inputStream().use { it.read(header) }
        if (read < 16 || !BackupRules.isSqliteHeader(header)) {
            return InspectResult.Invalid("This is not a database backup file.")
        }

        var sqlite: SQLiteDatabase? = null
        val version: Int
        val students: Int
        val sanctions: Int
        val attempts: Int
        try {
            sqlite = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE)

            val integrity = sqlite.rawQuery("PRAGMA integrity_check", null)
                .use { if (it.moveToFirst()) it.getString(0) else null }
            if (integrity != "ok") {
                return InspectResult.Invalid("The file is damaged (its integrity check failed).")
            }

            version = sqlite.version
            BackupRules.versionProblem(version, AppDatabase.VERSION)?.let {
                return InspectResult.Invalid(it)
            }

            val tables = mutableSetOf<String>()
            sqlite.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null).use {
                while (it.moveToNext()) tables += it.getString(0)
            }
            val missing = BackupRules.missingTables(tables)
            if (missing.isNotEmpty()) {
                return InspectResult.Invalid("The backup is incomplete. Missing: ${missing.joinToString(", ")}.")
            }

            val brokenLinks = sqlite.rawQuery("PRAGMA foreign_key_check", null).use { it.count }
            if (brokenLinks > 0) {
                return InspectResult.Invalid("The backup contains records that point to missing records.")
            }

            students = count(sqlite, "students")
            sanctions = count(sqlite, "sanctions")
            attempts = count(sqlite, "service_attempts")
        } catch (e: Exception) {
            return InspectResult.Invalid("The file could not be read as a database: ${e.message ?: "unknown error"}")
        } finally {
            sqlite?.close()
            File(file.path + "-wal").delete()
            File(file.path + "-shm").delete()
            File(file.path + "-journal").delete()
        }

        if (deep) roomCheck(file)?.let { return InspectResult.Invalid(it) }
        return InspectResult.Valid(BackupSummary(version, file.length(), students, sanctions, attempts))
    }

    private fun count(db: SQLiteDatabase, table: String): Int =
        db.rawQuery("SELECT COUNT(*) FROM $table", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    /**
     * Opens a COPY of the file the way the app itself would: with every migration, and with
     * Room's own schema check. If this passes, the app will be able to use the backup.
     */
    private fun roomCheck(file: File): String? {
        context.deleteDatabase(CHECK_DB_NAME)
        val check = AppDatabase.builder(context, CHECK_DB_NAME).createFromFile(file).build()
        return try {
            check.openHelper.writableDatabase
            null
        } catch (e: Exception) {
            "This backup does not match this version of the app: ${e.message ?: "schema mismatch"}"
        } finally {
            runCatching { check.close() }
            context.deleteDatabase(CHECK_DB_NAME)
        }
    }
}