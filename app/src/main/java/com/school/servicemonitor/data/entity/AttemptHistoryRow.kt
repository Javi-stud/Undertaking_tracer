package com.school.servicemonitor.data.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import java.time.LocalDate

data class AttemptHistoryRow(
    @Embedded val attempt: ServiceAttempt,
    @ColumnInfo(name = "service_date") val serviceDate: LocalDate,
    @ColumnInfo(name = "student_number") val studentNumber: String,
    @ColumnInfo(name = "first_name") val firstName: String,
    @ColumnInfo(name = "middle_name") val middleName: String?,
    @ColumnInfo(name = "last_name") val lastName: String
)