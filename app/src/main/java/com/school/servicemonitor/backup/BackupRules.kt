package com.school.servicemonitor.backup

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object BackupRules {

    /** Databases older than this were development-only and cannot be restored. */
    const val MIN_SUPPORTED_VERSION = 9

    /** Every table that must exist in a backup (all of them exist from version 9 on). */
    val REQUIRED_TABLES: Set<String> = setOf(
        "app_settings", "students", "violations", "sanctions",
        "daily_service_records", "service_attempts", "sanction_hour_allocations",
        "excuses", "sanction_exemptions", "deadline_repeat_events",
        "sanction_history", "excuse_extensions", "activity_logs"
    )

    // "SQLite format 3" followed by one zero byte: 16 bytes.
    private val MAGIC = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    fun isSqliteHeader(bytes: ByteArray): Boolean =
        bytes.size >= MAGIC.size && MAGIC.indices.all { bytes[it] == MAGIC[it] }

    /** An error message if a backup of [fileVersion] cannot be restored by this app, else null. */
    fun versionProblem(fileVersion: Int, currentVersion: Int): String? = when {
        fileVersion <= 0 ->
            "This file is a database, but not one made by this app."
        fileVersion > currentVersion ->
            "This backup was made by a newer version of the app (database version $fileVersion). " +
                    "Update the app first."
        fileVersion < MIN_SUPPORTED_VERSION ->
            "This backup is too old to restore (database version $fileVersion). " +
                    "Only version $MIN_SUPPORTED_VERSION or newer can be restored."
        else -> null
    }

    fun missingTables(found: Set<String>): List<String> =
        REQUIRED_TABLES.filter { it !in found }.sorted()

    fun suggestedFileName(now: LocalDateTime): String =
        "service_monitor_backup_" +
                now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm", Locale.US)) + ".db"

    fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes bytes"
        bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    }
}