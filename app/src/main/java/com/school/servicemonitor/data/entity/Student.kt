package com.school.servicemonitor.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

enum class StudentStatus { ACTIVE, ARCHIVED }

@Entity(
    tableName = "students",
    indices = [
        Index(value = ["student_number"], unique = true),
        Index(value = ["last_name"])
    ]
)
data class Student(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "student_number") val studentNumber: String,
    @ColumnInfo(name = "first_name") val firstName: String,
    @ColumnInfo(name = "middle_name") val middleName: String? = null,
    @ColumnInfo(name = "last_name") val lastName: String,
    @ColumnInfo(name = "grade_level") val gradeLevel: String,
    @ColumnInfo(name = "section") val section: String,
    @ColumnInfo(name = "is_ssg") val isSsg: Boolean = false,
    @ColumnInfo(name = "status") val status: StudentStatus = StudentStatus.ACTIVE,
    @ColumnInfo(name = "registration_date") val registrationDate: LocalDate,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

/** Display name such as "Dela Cruz, Juan A." */
val Student.fullName: String
    get() {
        val initial = middleName
            ?.takeIf { it.isNotBlank() }
            ?.let { " ${it.trim().first().uppercaseChar()}." }
            ?: ""
        return "$lastName, $firstName$initial"
    }