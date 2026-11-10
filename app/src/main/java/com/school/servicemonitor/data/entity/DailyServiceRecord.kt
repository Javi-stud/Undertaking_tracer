package com.school.servicemonitor.data.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

enum class DailyRecordStatus { PENDING, FAILED_REPEAT, PASSED, EXCUSED }

/** One row per student per date. Its status is a cache recomputed from the attempts. */
@Entity(
    tableName = "daily_service_records",
    foreignKeys = [
        ForeignKey(
            entity = Student::class,
            parentColumns = ["id"],
            childColumns = ["student_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["student_id", "service_date"], unique = true),   // no duplicate daily records
        Index(value = ["service_date"])
    ]
)
data class DailyServiceRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "student_id") val studentId: Long,
    @ColumnInfo(name = "service_date") val serviceDate: LocalDate,
    @ColumnInfo(name = "status") val status: DailyRecordStatus = DailyRecordStatus.PENDING,
    @ColumnInfo(name = "credited_minutes") val creditedMinutes: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

/** A daily record plus how many non-voided attempts it has. */
data class DailyRecordRow(
    @Embedded val record: DailyServiceRecord,
    @ColumnInfo(name = "attempt_count") val attemptCount: Int
)