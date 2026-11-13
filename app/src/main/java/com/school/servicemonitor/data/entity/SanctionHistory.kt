package com.school.servicemonitor.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class SanctionHistoryType {
    EXPIRED, REPEAT_PROPOSED, REPEAT_CONFIRMED, REPEAT_DISMISSED, REPEAT_CREATED,
    DEADLINE_EXTENDED, DEADLINE_EXTENSION_REVERTED, DEADLINE_SKIPPED_LOCKED
}

/** An audit line about a sanction. Rows are never changed or deleted. */
@Entity(
    tableName = "sanction_history",
    foreignKeys = [
        ForeignKey(
            entity = Sanction::class,
            parentColumns = ["id"],
            childColumns = ["sanction_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = Sanction::class,
            parentColumns = ["id"],
            childColumns = ["related_sanction_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["sanction_id"]),
        Index(value = ["related_sanction_id"])
    ]
)
data class SanctionHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "sanction_id") val sanctionId: Long,
    @ColumnInfo(name = "related_sanction_id") val relatedSanctionId: Long? = null,
    @ColumnInfo(name = "event_type") val eventType: SanctionHistoryType,
    @ColumnInfo(name = "details") val details: String,
    @ColumnInfo(name = "created_at") val createdAt: Long
)
