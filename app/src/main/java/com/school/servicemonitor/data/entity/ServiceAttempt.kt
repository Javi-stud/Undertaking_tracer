package com.school.servicemonitor.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalTime

enum class AttemptResultType { PASS, FAIL }
enum class AttemptState { RECORDED, VOIDED }

/**
 * One cleaning attempt. Start time, end time, result and credited minutes are never changed
 * after creation. Voiding (Stage 16) only sets record_state, void_reason and voided_at.
 */
@Entity(
    tableName = "service_attempts",
    foreignKeys = [
        ForeignKey(
            entity = DailyServiceRecord::class,
            parentColumns = ["id"],
            childColumns = ["daily_record_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = Student::class,
            parentColumns = ["id"],
            childColumns = ["student_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["daily_record_id", "attempt_no"], unique = true),
        Index(value = ["student_id"])
    ]
)
data class ServiceAttempt(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "daily_record_id") val dailyRecordId: Long,
    @ColumnInfo(name = "student_id") val studentId: Long,
    @ColumnInfo(name = "attempt_no") val attemptNo: Int,

    @ColumnInfo(name = "start_time") val startTime: LocalTime,
    @ColumnInfo(name = "end_time") val endTime: LocalTime,
    @ColumnInfo(name = "duration_minutes") val durationMinutes: Int,

    @ColumnInfo(name = "result") val result: AttemptResultType,
    @ColumnInfo(name = "credited_minutes") val creditedMinutes: Int,
    @ColumnInfo(name = "fail_reason") val failReason: String? = null,

    @ColumnInfo(name = "bulk_batch_id") val bulkBatchId: String? = null,   // used in Stage 13

    @ColumnInfo(name = "record_state") val recordState: AttemptState = AttemptState.RECORDED,
    @ColumnInfo(name = "void_reason") val voidReason: String? = null,
    @ColumnInfo(name = "voided_at") val voidedAt: Long? = null,

    @ColumnInfo(name = "created_at") val createdAt: Long
)