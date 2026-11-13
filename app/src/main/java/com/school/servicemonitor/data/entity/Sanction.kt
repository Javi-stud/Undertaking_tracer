package com.school.servicemonitor.data.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

enum class SanctionStatus { ACTIVE, COMPLETED, OVERDUE, EXEMPTED }

@Entity(
    tableName = "sanctions",
    foreignKeys = [
        ForeignKey(
            entity = Student::class,
            parentColumns = ["id"],
            childColumns = ["student_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = Violation::class,
            parentColumns = ["id"],
            childColumns = ["violation_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = Sanction::class,
            parentColumns = ["id"],
            childColumns = ["parent_sanction_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["student_id", "status"]),
        Index(value = ["student_id", "end_date", "assigned_date"]),
        Index(value = ["violation_id"]),
        Index(value = ["parent_sanction_id"])
    ]
)
data class Sanction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "student_id") val studentId: Long,
    @ColumnInfo(name = "violation_id") val violationId: Long,

    // Snapshots: these never change, even if the violation or SSG status changes later.
    @ColumnInfo(name = "base_hours") val baseHours: Int,
    @ColumnInfo(name = "multiplier_applied") val multiplierApplied: Int,
    @ColumnInfo(name = "assigned_minutes") val assignedMinutes: Int,

    @ColumnInfo(name = "assigned_date") val assignedDate: LocalDate,
    @ColumnInfo(name = "start_date") val startDate: LocalDate,
    @ColumnInfo(name = "end_date") val endDate: LocalDate,   // the deadline

    @ColumnInfo(name = "deadline_locked") val deadlineLocked: Boolean = false,

    @ColumnInfo(name = "status") val status: SanctionStatus = SanctionStatus.ACTIVE,

    // Used in Stage 15: links a repeated sanction to the one it repeats.
    @ColumnInfo(name = "parent_sanction_id") val parentSanctionId: Long? = null,

    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "completed_at") val completedAt: Long? = null
)

/** A sanction with its violation name and the minutes credited to it so far. */
/** A sanction with its violation name, the minutes credited so far, and the minutes exempted. */
data class SanctionRow(
    @Embedded val sanction: Sanction,
    @ColumnInfo(name = "violation_name") val violationName: String,
    @ColumnInfo(name = "allocated_minutes") val allocatedMinutes: Int,
    @ColumnInfo(name = "exempted_minutes") val exemptedMinutes: Int = 0
)
