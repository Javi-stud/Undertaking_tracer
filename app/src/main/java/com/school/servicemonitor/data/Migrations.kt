package com.school.servicemonitor.data

import androidx.room.migration.Migration

/**
 * Every future schema change goes here. From database version 9 on, the app never deletes
 * data when the schema changes: each version step needs a Migration.
 *
 * To change the schema:
 *   1. Change the entity (for example add a column).
 *   2. Raise AppDatabase.VERSION by one.
 *   3. Write a Migration for the step and add it to ALL below.
 *   4. Build. Room writes the new schema file (app/schemas/.../10.json). Keep it.
 *   5. Test it (Stage 19 adds the migration test).
 *
 * Example for a day the schema changes (do NOT add this now):
 *
 *   val MIGRATION_9_10 = object : Migration(9, 10) {
 *       override fun migrate(db: SupportSQLiteDatabase) {
 *           db.execSQL("ALTER TABLE students ADD COLUMN phone TEXT")
 *       }
 *   }
 */
object Migrations {
    val ALL: Array<Migration> = arrayOf()
}