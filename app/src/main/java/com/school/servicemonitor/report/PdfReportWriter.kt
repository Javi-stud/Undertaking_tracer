package com.school.servicemonitor.report

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.StaticLayout
import android.text.TextPaint
import java.io.OutputStream

/** Draws a [ReportDocument] onto A4 pages using Android's built-in PdfDocument. */
class PdfReportWriter {

    fun write(doc: ReportDocument, out: OutputStream) {
        val renderer = Renderer(doc)
        try {
            renderer.render()
            renderer.pdf.writeTo(out)
        } finally {
            renderer.pdf.close()
        }
    }

    private class Renderer(private val doc: ReportDocument) {
        val pdf = PdfDocument()

        private val pageW = if (doc.landscape) 842 else 595
        private val pageH = if (doc.landscape) 595 else 842
        private val margin = 36f
        private val contentW = pageW - 2 * margin
        private val bottom = pageH - margin - 8f

        private lateinit var page: PdfDocument.Page
        private lateinit var canvas: Canvas
        private var started = false
        private var pageNo = 0
        private var y = 0f

        private fun textPaint(size: Float, bold: Boolean = false, color: Int = Color.BLACK) =
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size
                this.color = color
                typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }

        private val titlePaint = textPaint(18f, bold = true)
        private val headingPaint = textPaint(13f, bold = true)
        private val bodyPaint = textPaint(10f)
        private val mutedPaint = textPaint(10f, color = Color.DKGRAY)
        private val keyPaint = textPaint(10f, bold = true)
        private val cellPaint = textPaint(8.5f)
        private val headerCellPaint = textPaint(8.5f, bold = true)
        private val footerPaint = textPaint(8f, color = Color.GRAY)

        private val headerFill = Paint().apply { style = Paint.Style.FILL; color = 0xFFE6E6E6.toInt() }
        private val border = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.5f
            color = Color.GRAY
        }

        fun render() {
            startPage()
            paragraph(doc.title, titlePaint, 2f)
            paragraph(doc.subtitle, mutedPaint, 2f)
            paragraph("Generated: ${doc.generatedAt}", footerPaint, 10f)

            for (block in doc.blocks) {
                when (block) {
                    is ReportBlock.Heading -> {
                        y += 8f
                        paragraph(block.text, headingPaint, 4f, keepWithNext = 50f)
                    }
                    is ReportBlock.Paragraph ->
                        paragraph(block.text, if (block.muted) mutedPaint else bodyPaint, 4f)
                    is ReportBlock.KeyValues -> keyValues(block)
                    is ReportBlock.Table -> table(block)
                }
            }
            finishPage()
        }

        private fun startPage() {
            if (started) finishPage()
            pageNo++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
            canvas = page.canvas
            started = true
            y = margin
        }

        private fun finishPage() {
            canvas.drawText("${doc.title}   |   Page $pageNo", margin, pageH - 18f, footerPaint)
            pdf.finishPage(page)
        }

        private fun layout(text: String, paint: TextPaint, width: Float): StaticLayout =
            StaticLayout.Builder
                .obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(1))
                .build()

        private fun ensure(height: Float) {
            if (y + height > bottom) startPage()
        }

        private fun draw(layout: StaticLayout, x: Float, top: Float) {
            canvas.save()
            canvas.translate(x, top)
            layout.draw(canvas)
            canvas.restore()
        }

        private fun paragraph(text: String, paint: TextPaint, after: Float, keepWithNext: Float = 0f) {
            val l = layout(text, paint, contentW)
            ensure(l.height + keepWithNext)
            draw(l, margin, y)
            y += l.height + after
        }

        private fun keyValues(block: ReportBlock.KeyValues) {
            val keyW = 210f
            for ((key, value) in block.rows) {
                val k = layout(key, keyPaint, keyW - 6f)
                val v = layout(value, bodyPaint, contentW - keyW)
                val h = maxOf(k.height, v.height).toFloat()
                ensure(h)
                draw(k, margin, y)
                draw(v, margin + keyW, y)
                y += h + 2f
            }
            y += 6f
        }

        private fun table(t: ReportBlock.Table) {
            val widths = t.normalizedWeights().map { it * contentW }
            val pad = 3f

            fun layouts(cells: List<String>, paint: TextPaint) =
                cells.mapIndexed { i, c -> layout(c.ifEmpty { " " }, paint, widths[i] - 2 * pad) }

            fun drawRow(ls: List<StaticLayout>, header: Boolean) {
                val h = ls.maxOf { it.height } + 2 * pad
                var x = margin
                ls.forEachIndexed { i, l ->
                    if (header) canvas.drawRect(x, y, x + widths[i], y + h, headerFill)
                    canvas.drawRect(x, y, x + widths[i], y + h, border)
                    draw(l, x + pad, y + pad)
                    x += widths[i]
                }
                y += h
            }

            val headerLayouts = layouts(t.headers, headerCellPaint)
            ensure(70f)   // keep the header with at least a row or two
            drawRow(headerLayouts, header = true)

            for (row in t.rows) {
                val ls = layouts(row, cellPaint)
                val h = ls.maxOf { it.height } + 2 * pad
                if (y + h > bottom) {
                    startPage()
                    drawRow(headerLayouts, header = true)   // repeat the header on every page
                }
                drawRow(ls, header = false)
            }
            y += 10f
        }
    }
}