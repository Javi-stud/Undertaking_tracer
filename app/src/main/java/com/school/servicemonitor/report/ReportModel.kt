package com.school.servicemonitor.report

sealed interface ReportBlock {
    data class Heading(val text: String) : ReportBlock
    data class Paragraph(val text: String, val muted: Boolean = false) : ReportBlock
    data class KeyValues(val rows: List<Pair<String, String>>) : ReportBlock

    /** Every row must have exactly as many cells as there are headers. */
    data class Table(
        val headers: List<String>,
        val rows: List<List<String>>,
        val weights: List<Float> = emptyList()
    ) : ReportBlock {
        /** Column widths as fractions that add up to 1. Equal widths if no weights were given. */
        fun normalizedWeights(): List<Float> {
            val w = if (weights.size == headers.size) weights else List(headers.size) { 1f }
            val sum = w.sum()
            return w.map { it / sum }
        }
    }
}

data class ReportDocument(
    val title: String,
    val subtitle: String,
    val generatedAt: String,
    val blocks: List<ReportBlock>,
    val landscape: Boolean = true
)

enum class ReportFormat(val extension: String, val mimeType: String) {
    PDF("pdf", "application/pdf"),
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
}