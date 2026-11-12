package com.school.servicemonitor.backup

import android.content.Context
import com.school.servicemonitor.data.AppDatabase
import java.io.File

/**
 * Runs at app start, BEFORE the database is opened. If a checked backup is waiting, it replaces
 * the database file. The old data is copied aside first and put back if anything goes wrong.
 */
object RestoreApplier {

    private const val PREFS = "restore_state"
    private const val KEY_RESULT = "result"

    fun applyPendingIfAny(context: Context) {
        val pending = File(context.filesDir, BackupManager.PENDING_NAME)
        if (!pending.exists()) return

        val dbFile = context.getDatabasePath(AppDatabase.DB_NAME)
        val safetyDir = File(context.filesDir, "pre_restore_safety")
        try {
            dbFile.parentFile?.mkdirs()

            // 1. Keep the old data (main file and any unmerged log) so it can be put back.
            safetyDir.deleteRecursively()
            safetyDir.mkdirs()
            listOf("", "-wal").forEach { suffix ->
                val f = File(dbFile.path + suffix)
                if (f.exists()) f.copyTo(File(safetyDir, f.name), overwrite = true)
            }

            // 2. Put the backup next to the database, then swap it in.
            val incoming = File(dbFile.path + ".incoming")
            pending.copyTo(incoming, overwrite = true)
            removeSideFiles(dbFile)
            if (!incoming.renameTo(dbFile)) {
                incoming.copyTo(dbFile, overwrite = true)
                incoming.delete()
            }

            pending.delete()
            record(context, "Restore completed. The data from the backup is now in use.")
        } catch (e: Exception) {
            // Put the old data back.
            runCatching {
                removeSideFiles(dbFile)
                listOf("", "-wal").forEach { suffix ->
                    val saved = File(safetyDir, dbFile.name + suffix)
                    if (saved.exists()) saved.copyTo(File(dbFile.path + suffix), overwrite = true)
                }
            }
            pending.delete()
            record(
                context,
                "The restore failed and your previous data was put back: ${e.message ?: "unknown error"}"
            )
        }
    }

    private fun removeSideFiles(dbFile: File) {
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-shm").delete()
        File(dbFile.path + "-journal").delete()
    }

    private fun record(context: Context, message: String) {
        // commit() (not apply()) so it is written before anything else can happen.
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_RESULT, message).commit()
    }

    fun takeResult(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val message = prefs.getString(KEY_RESULT, null)
        if (message != null) prefs.edit().remove(KEY_RESULT).commit()
        return message
    }
}