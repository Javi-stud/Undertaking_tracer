package com.school.servicemonitor.data.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/** A PERMANENT exemption: cancels part or all of the remaining minutes of one sanction. */
@Entity(
    tableName = "sanction_exemptions",
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
        )
    ],
    indices = [
        Index(value = ["student_id"]),
        Index(value = ["sanction_id"])
    ]
)
data class SanctionExemption(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "student_id") val studentId: Long,
    @ColumnInfo(name = "sanction_id") val sanctionId: Long,
    @ColumnInfo(name = "exempted_minutes") val exemptedMinutes: Int,
    @ColumnInfo(name = "reason") val reason: String,
    @ColumnInfo(name = "approved_by") val approvedBy: String,
    @ColumnInfo(name = "approval_date") val approvalDate: LocalDate,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

/** An exemption with the name of the violation it applies to. */
data class ExemptionRow(
    @Embedded val exemption: SanctionExemption,
    @ColumnInfo(name = "violation_name") val violationName: String
)