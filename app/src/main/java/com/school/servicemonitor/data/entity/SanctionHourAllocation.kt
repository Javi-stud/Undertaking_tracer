package com.school.servicemonitor.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * Records exactly which sanction received which credited minutes.
 * Rows are never deleted. A voided attempt sets reversed_at instead.
 */
@Entity(
    tableName = "sanction_hour_allocations",
    foreignKeys = [
        ForeignKey(
            entity = Student::class,
            parentColumns = ["id"],
            childColumns = ["student_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = Sanction::class,
            parentColumns = ["id"],
            childColumns = ["sanction_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = ServiceAttempt::class,
            parentColumns = ["id"],
            childColumns = ["attempt_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["sanction_id"]),
        Index(value = ["attempt_id"]),
        Index(value = ["student_id", "allocation_date"])
    ]
)
data class SanctionHourAllocation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "student_id") val studentId: Long,
    @ColumnInfo(name = "sanction_id") val sanctionId: Long,
    @ColumnInfo(name = "attempt_id") val attemptId: Long,

    @ColumnInfo(name = "allocated_minutes") val allocatedMinutes: Int,
    @ColumnInfo(name = "allocation_date") val allocationDate: LocalDate,
    @ColumnInfo(name = "created_at") val createdAt: Long,

    @ColumnInfo(name = "reversed_at") val reversedAt: Long? = null,
    @ColumnInfo(name = "reversal_reason") val reversalReason: String? = null
)