package com.school.servicemonitor.data.repository

import com.school.servicemonitor.data.entity.Student
import com.school.servicemonitor.domain.DashboardBuilder
import com.school.servicemonitor.domain.DayRecordInfo
import com.school.servicemonitor.domain.ScheduleCalculator
import com.school.servicemonitor.report.ReportContent
import com.school.servicemonitor.report.ReportDocument
import com.school.servicemonitor.ui.components.formatTimestamp
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** Reads the data a report needs once, then hands it to [ReportContent]. Nothing is changed. */
class ReportRepository(
    private val students: StudentRepository,
    private val sanctions: SanctionRepository,
    private val attempts: AttemptRepository,
    private val excuses: ExcuseRepository,
    private val exemptions: ExemptionRepository,
    private val deadlines: DeadlineRepository,
    private val settings: SettingsRepository
) {

    private fun now() = formatTimestamp(System.currentTimeMillis())

    /** Includes archived students, so their records can still be reported. */
    suspend fun searchStudents(query: String): List<Student> = students.search(query, true).first()

    suspend fun studentReport(studentId: Long): ReportDocument? {
        val student = students.get(studentId) ?: return null
        return ReportContent.studentReport(
            student = student,
            sanctionRows = sanctions.observeForStudent(studentId).first(),
            attempts = attempts.observeHistoryForStudent(studentId).first(),
            excuses = excuses.observeForStudent(studentId).first(),
            exemptions = exemptions.observeForStudent(studentId).first(),
            events = deadlines.observeForStudent(studentId).first(),
            generatedAt = now()
        )
    }

    suspend fun dailyReport(date: LocalDate): ReportDocument {
        val serviceDays = ScheduleCalculator.parseServiceDays(
            settings.getString(SettingsRepository.KEY_SERVICE_DAYS)
        )
        val isServiceDay = ScheduleCalculator.isServiceDay(date, serviceDays)

        val dayRecords = attempts.observeDayRecords(date).first().associate {
            it.record.studentId to DayRecordInfo(
                status = it.record.status,
                creditedMinutes = it.record.creditedMinutes,
                attemptCount = it.attemptCount
            )
        }
        // The same builder the Dashboard uses, so the report always agrees with the screen.
        val entries = DashboardBuilder.build(
            students = students.observeActive().first(),
            sanctionRows = sanctions.observeAllForActiveStudents().first(),
            dayRecords = dayRecords,
            date = date,
            isServiceDay = isServiceDay
        )
        return ReportContent.dailyReport(
            date = date,
            isServiceDay = isServiceDay,
            isPastDate = date.isBefore(LocalDate.now()),
            entries = entries,
            attempts = attempts.observeHistoryForDate(date).first(),
            excuses = excuses.observeHistoryForDate(date).first(),
            generatedAt = now()
        )
    }

    suspend fun sanctionReport(onlyOutstanding: Boolean): ReportDocument {
        val studentMap = students.observeActive().first().associateBy { it.id }
        return ReportContent.sanctionReport(
            rows = sanctions.observeAllForActiveStudents().first(),
            students = studentMap,
            onlyOutstanding = onlyOutstanding,
            generatedAt = now()
        )
    }
}