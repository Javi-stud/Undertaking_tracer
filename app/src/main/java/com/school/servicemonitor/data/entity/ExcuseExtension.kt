package com.school.servicemonitor.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/** Remembers that an excuse moved one sanction's deadline, so a revoke can undo it. */
@Entity(
    tableName = "excuse_extensions",
    foreignKeys = [
        ForeignKey(
            entity = Excuse::class,
            parentColumns = ["id"],
            childColumns = ["excuse_id"],
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
        Index(value = ["excuse_id"]),
        Index(value = ["sanction_id"])
    ]
)
data class ExcuseExtension(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "excuse_id") val excuseId: Long,
    @ColumnInfo(name = "sanction_id") val sanctionId: Long,
    @ColumnInfo(name = "old_end_date") val oldEndDate: LocalDate,
    @ColumnInfo(name = "new_end_date") val newEndDate: LocalDate,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "reverted_at") val revertedAt: Long? = null
)