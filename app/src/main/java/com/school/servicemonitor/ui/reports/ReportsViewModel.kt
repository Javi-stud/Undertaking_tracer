package com.school.servicemonitor.ui.reports

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.school.servicemonitor.data.entity.Student
import com.school.servicemonitor.data.repository.ReportRepository
import com.school.servicemonitor.report.DocxReportWriter
import com.school.servicemonitor.report.PdfReportWriter
import com.school.servicemonitor.report.ReportDocument
import com.school.servicemonitor.report.ReportFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.time.LocalDate

enum class ReportKind { STUDENT, DAILY, SANCTION }

private data class ExportRequest(
    val kind: ReportKind,
    val format: ReportFormat,
    val studentId: Long?,
    val date: LocalDate,
    val onlyOutstanding: Boolean
)

data class ReportsUiState(
    val student: Student? = null,
    val date: LocalDate = LocalDate.now(),
    val onlyOutstanding: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModel(private val repository: ReportRepository) : ViewModel() {

    private val _state = MutableStateFlow(ReportsUiState())
    val state: StateFlow<ReportsUiState> = _state.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val results: StateFlow<List<Student>> = _query
        .mapLatest { repository.searchStudents(it.trim()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // The report waits here while the person chooses where to save the file.
    private var pending: ExportRequest? = null

    fun onQuery(value: String) { _query.value = value }
    fun pickStudent(student: Student) = _state.update { it.copy(student = student) }
    fun onDate(date: LocalDate) = _state.update { it.copy(date = date) }
    fun onOnlyOutstanding(value: Boolean) = _state.update { it.copy(onlyOutstanding = value) }
    fun consumeMessage() = _state.update { it.copy(message = null) }

    /** Returns the suggested file name, or null if the report cannot be started yet. */
    fun prepare(kind: ReportKind, format: ReportFormat): String? {
        val s = _state.value
        if (s.busy) return null
        if (kind == ReportKind.STUDENT && s.student == null) {
            _state.update { it.copy(message = "Choose a student first.") }
            return null
        }
        pending = ExportRequest(kind, format, s.student?.id, s.date, s.onlyOutstanding)

        fun safe(text: String) = text.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_')
        val base = when (kind) {
            ReportKind.STUDENT ->
                "Student_Report_${safe(s.student?.lastName.orEmpty())}_${safe(s.student?.firstName.orEmpty())}_${LocalDate.now()}"
            ReportKind.DAILY -> "Daily_Attendance_${s.date}"
            ReportKind.SANCTION -> "Sanction_Report_${LocalDate.now()}"
        }
        return "$base.${format.extension}"
    }

    fun cancelPending() { pending = null }

    /** Builds the report and writes it to the place the person chose. */
    fun export(uri: Uri, resolver: ContentResolver) {
        val request = pending ?: return
        pending = null
        _state.update { it.copy(busy = true) }

        viewModelScope.launch {
            val message = withContext(Dispatchers.IO) {
                try {
                    val doc = build(request)
                    val stream = resolver.openOutputStream(uri, "wt")
                        ?: error("The chosen location could not be opened.")
                    BufferedOutputStream(stream).use { out ->
                        when (request.format) {
                            ReportFormat.PDF -> PdfReportWriter().write(doc, out)
                            ReportFormat.DOCX -> DocxReportWriter.write(doc, out)
                        }
                    }
                    "Saved: ${doc.title} (${request.format.extension.uppercase()})."
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Do not leave an empty or half-written file behind.
                    runCatching { DocumentsContract.deleteDocument(resolver, uri) }
                    "Could not create the report: ${e.message ?: "unknown error"}"
                }
            }
            _state.update { it.copy(busy = false, message = message) }
        }
    }

    private suspend fun build(request: ExportRequest): ReportDocument = when (request.kind) {
        ReportKind.STUDENT ->
            repository.studentReport(request.studentId ?: error("No student was chosen."))
                ?: error("The student could not be found.")
        ReportKind.DAILY -> repository.dailyReport(request.date)
        ReportKind.SANCTION -> repository.sanctionReport(request.onlyOutstanding)
    }
}