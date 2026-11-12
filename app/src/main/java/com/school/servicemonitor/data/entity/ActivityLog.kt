package com.school.servicemonitor.data.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One audit line. Never edited or deleted. No foreign keys, so logging can never block a save. */
@Entity(
    tableName = "activity_logs",
    indices = [
        Index(value = ["created_at"]),
        Index(value = ["student_id"]),
        Index(value = ["entity_type", "entity_id"])
    ]
)
data class ActivityLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "entity_type") val entityType: String,
    @ColumnInfo(name = "entity_id") val entityId: Long,
    @ColumnInfo(name = "student_id") val studentId: Long? = null,
    @ColumnInfo(name = "action") val action: String,
    @ColumnInfo(name = "details") val details: String,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

/** A log line with the student's name (if it concerns one). */
data class ActivityLogRow(
    @Embedded val log: ActivityLog,
    @ColumnInfo(name = "first_name") val firstName: String?,
    @ColumnInfo(name = "middle_name") val middleName: String?,
    @ColumnInfo(name = "last_name") val lastName: String?
)