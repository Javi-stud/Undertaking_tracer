package com.school.servicemonitor.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class BackupRulesTest {

    @Test
    fun sqliteHeader_isRecognised() {
        val bytes = ("SQLite format 3\u0000" + "rest of the file").toByteArray(Charsets.US_ASCII)
        assertTrue(BackupRules.isSqliteHeader(bytes))
    }

    @Test
    fun otherFiles_areNotSqlite() {
        assertFalse(BackupRules.isSqliteHeader("%PDF-1.4 something long enough".toByteArray()))
        assertFalse(BackupRules.isSqliteHeader(ByteArray(0)))
        assertFalse(BackupRules.isSqliteHeader("SQLite format 3".toByteArray()))   // missing the zero byte
    }

    @Test
    fun versionRules() {
        assertNull(BackupRules.versionProblem(9, 9))
        assertNull(BackupRules.versionProblem(9, 12))
        assertNull(BackupRules.versionProblem(11, 12))
        assertNotNull(BackupRules.versionProblem(13, 12))   // newer than this app
        assertNotNull(BackupRules.versionProblem(8, 12))    // development-only schema
        assertNotNull(BackupRules.versionProblem(0, 9))     // not an app database
    }

    @Test
    fun newerBackup_tellsTheOperatorToUpdate() =
        assertTrue(BackupRules.versionProblem(10, 9)!!.contains("Update"))

    @Test
    fun allTablesPresent_nothingMissing() =
        assertTrue(BackupRules.missingTables(BackupRules.REQUIRED_TABLES + "room_master_table").isEmpty())

    @Test
    fun missingTables_areListed_inOrder() {
        val found = BackupRules.REQUIRED_TABLES - setOf("sanctions", "excuses")
        assertEquals(listOf("excuses", "sanctions"), BackupRules.missingTables(found))
    }

    @Test
    fun thirteenTablesAreRequired() = assertEquals(13, BackupRules.REQUIRED_TABLES.size)

    @Test
    fun suggestedFileName_hasDateAndTime() =
        assertEquals(
            "service_monitor_backup_2026-11-12_1505.db",
            BackupRules.suggestedFileName(LocalDateTime.of(2026, 11, 12, 15, 5))
        )

    @Test
    fun formatSize() {
        assertEquals("500 bytes", BackupRules.formatSize(500))
        assertEquals("1.5 KB", BackupRules.formatSize(1536))
        assertEquals("2.0 MB", BackupRules.formatSize(2L * 1024 * 1024))
    }
}