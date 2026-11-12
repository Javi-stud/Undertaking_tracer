package com.school.servicemonitor.data.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

enum class RepeatEventStatus { DETECTED, CONFIRMED, DISMISSED }

/**
 * "This overdue deadline has been seen." The unique pair (original sanction, deadline)
 * guarantees one deadline cycle is processed only once. Rows are never deleted.
 */
@Entity(
    tableName = "deadline_repeat_events",
    foreignKeys = [
        ForeignKey(
            entity = Sanction::class,
            parentColumns = ["id"],
            childColumns = ["original_sanction_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = Sanction::class,
            parentColumns = ["id"],
            childColumns = ["proposed_sanction_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["original_sanction_id", "triggering_deadline"], unique = true),
        Index(value = ["proposed_sanction_id"]),
        Index(value = ["status"])
    ]
)
data class DeadlineRepeatEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "original_sanction_id") val originalSanctionId: Long,
    @ColumnInfo(name = "triggering_deadline") val triggeringDeadline: LocalDate,
    @ColumnInfo(name = "proposed_sanction_id") val proposedSanctionId: Long? = null,
    @ColumnInfo(name = "status") val status: RepeatEventStatus = RepeatEventStatus.DETECTED,
    @ColumnInfo(name = "detected_at") val detectedAt: Long,
    @ColumnInfo(name = "confirmed_at") val confirmedAt: Long? = null,
    @ColumnInfo(name = "dismissed_at") val dismissedAt: Long? = null,
    @ColumnInfo(name = "dismiss_reason") val dismissReason: String? = null
)

/** An event with the student, the violation and the original sanction's hours, for display. */
data class DeadlineEventRow(
    @Embedded val event: DeadlineRepeatEvent,
    @ColumnInfo(name = "student_id") val studentId: Long,
    @ColumnInfo(name = "student_number") val studentNumber: String,
    @ColumnInfo(name = "first_name") val firstName: String,
    @ColumnInfo(name = "middle_name") val middleName: String?,
    @ColumnInfo(name = "last_name") val lastName: String,
    @ColumnInfo(name = "violation_name") val violationName: String,
    @ColumnInfo(name = "assigned_minutes") val assignedMinutes: Int,
    @ColumnInfo(name = "allocated_minutes") val allocatedMinutes: Int,
    @ColumnInfo(name = "exempted_minutes") val exemptedMinutes: Int
) {
    val remainingMinutes: Int
        get() = (assignedMinutes - allocatedMinutes - exemptedMinutes).coerceAtLeast(0)
}