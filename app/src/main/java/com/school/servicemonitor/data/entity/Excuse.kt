package com.school.servicemonitor.data.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * A DAILY excuse. It never reduces hours and never cancels a sanction.
 * Revoking only stamps revoked_at and revoke_reason. Rows are never deleted.
 */
@Entity(
    tableName = "excuses",
    foreignKeys = [
        ForeignKey(
            entity = Student::class,
            parentColumns = ["id"],
            childColumns = ["student_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["student_id", "excuse_date"]),
        Index(value = ["excuse_date"]),
        Index(value = ["bulk_batch_id"])
    ]
)
data class Excuse(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "student_id") val studentId: Long,
    @ColumnInfo(name = "excuse_date") val excuseDate: LocalDate,
    @ColumnInfo(name = "reason") val reason: String,
    @ColumnInfo(name = "bulk_batch_id") val bulkBatchId: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "revoked_at") val revokedAt: Long? = null,
    @ColumnInfo(name = "revoke_reason") val revokeReason: String? = null
)

/** An excuse with the student's name, for the History tab. */
data class ExcuseHistoryRow(
    @Embedded val excuse: Excuse,
    @ColumnInfo(name = "student_number") val studentNumber: String,
    @ColumnInfo(name = "first_name") val firstName: String,
    @ColumnInfo(name = "middle_name") val middleName: String?,
    @ColumnInfo(name = "last_name") val lastName: String
)