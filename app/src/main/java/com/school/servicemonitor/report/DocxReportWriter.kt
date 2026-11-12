package com.school.servicemonitor.report

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Builds a Word file by hand: a zip containing three XML parts.
 * No Android code, so it is easy to unit test.
 */
object DocxReportWriter {

    private const val CONTENT_TYPES =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
                """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""" +
                """<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""" +
                """<Default Extension="xml" ContentType="application/xml"/>""" +
                """<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>""" +
                """</Types>"""

    private const val RELS =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
                """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
                """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>""" +
                """</Relationships>"""

    fun write(doc: ReportDocument, out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            fun put(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put("[Content_Types].xml", CONTENT_TYPES)
            put("_rels/.rels", RELS)
            put("word/document.xml", documentXml(doc))
        }
    }

    /** Escapes XML special characters and drops characters XML does not allow. */
    internal fun xmlEscape(text: String): String = buildString {
        for (ch in text) {
            when {
                ch == '&' -> append("&amp;")
                ch == '<' -> append("&lt;")
                ch == '>' -> append("&gt;")
                ch == '"' -> append("&quot;")
                ch == '\'' -> append("&apos;")
                ch == '\t' -> append(' ')
                ch.code < 0x20 -> Unit            // control characters are not valid in XML
                ch.code == 0xFFFE || ch.code == 0xFFFF -> Unit
                else -> append(ch)
            }
        }
    }

    /** Text runs. A newline becomes a line break inside the paragraph. */
    private fun runs(
        text: String,
        size: Int,
        bold: Boolean = false,
        italic: Boolean = false,
        color: String? = null
    ): String {
        val props = buildString {
            if (bold) append("<w:b/>")
            if (italic) append("<w:i/>")
            if (color != null) append("<w:color w:val=\"$color\"/>")
            append("<w:sz w:val=\"$size\"/>")
        }
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        return lines.joinToString("<w:r><w:rPr>$props</w:rPr><w:br/></w:r>") {
            "<w:r><w:rPr>$props</w:rPr><w:t xml:space=\"preserve\">${xmlEscape(it)}</w:t></w:r>"
        }
    }

    private fun paragraph(
        inner: String,
        before: Int = 0,
        after: Int = 80,
        keepNext: Boolean = false
    ): String =
        "<w:p><w:pPr>${if (keepNext) "<w:keepNext/>" else ""}" +
                "<w:spacing w:before=\"$before\" w:after=\"$after\"/></w:pPr>$inner</w:p>"

    private fun cell(text: String, width: Int, header: Boolean): String =
        "<w:tc><w:tcPr><w:tcW w:w=\"$width\" w:type=\"dxa\"/>" +
                (if (header) "<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"E6E6E6\"/>" else "") +
                "</w:tcPr>" +
                paragraph(runs(text.ifEmpty { " " }, 16, bold = header), after = 0) +
                "</w:tc>"

    private fun table(t: ReportBlock.Table, textWidth: Int): String {
        val widths = t.normalizedWeights().map { (it * textWidth).toInt().coerceAtLeast(300) }
        val border = { side: String -> "<w:$side w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"999999\"/>" }
        val sb = StringBuilder()
        sb.append("<w:tbl><w:tblPr><w:tblW w:w=\"${widths.sum()}\" w:type=\"dxa\"/>")
        sb.append("<w:tblBorders>")
        listOf("top", "left", "bottom", "right", "insideH", "insideV").forEach { sb.append(border(it)) }
        sb.append("</w:tblBorders><w:tblLayout w:type=\"fixed\"/>")
        sb.append("<w:tblCellMar><w:left w:w=\"60\" w:type=\"dxa\"/><w:right w:w=\"60\" w:type=\"dxa\"/></w:tblCellMar>")
        sb.append("</w:tblPr><w:tblGrid>")
        widths.forEach { sb.append("<w:gridCol w:w=\"$it\"/>") }
        sb.append("</w:tblGrid>")

        sb.append("<w:tr><w:trPr><w:cantSplit/><w:tblHeader/></w:trPr>")
        t.headers.forEachIndexed { i, h -> sb.append(cell(h, widths[i], header = true)) }
        sb.append("</w:tr>")

        for (row in t.rows) {
            sb.append("<w:tr><w:trPr><w:cantSplit/></w:trPr>")
            row.forEachIndexed { i, c -> sb.append(cell(c, widths[i], header = false)) }
            sb.append("</w:tr>")
        }
        sb.append("</w:tbl>")
        sb.append(paragraph("", after = 120))   // a paragraph after a table keeps Word happy
        return sb.toString()
    }

    private fun documentXml(doc: ReportDocument): String {
        val pageW = if (doc.landscape) 16838 else 11906
        val pageH = if (doc.landscape) 11906 else 16838
        val margin = 1000
        val textWidth = pageW - 2 * margin

        val body = StringBuilder()
        body.append(paragraph(runs(doc.title, 36, bold = true), after = 40))
        body.append(paragraph(runs(doc.subtitle, 22, color = "444444"), after = 40))
        body.append(paragraph(runs("Generated: ${doc.generatedAt}", 16, color = "777777"), after = 200))

        for (block in doc.blocks) {
            when (block) {
                is ReportBlock.Heading ->
                    body.append(paragraph(runs(block.text, 28, bold = true), before = 240, after = 80, keepNext = true))
                is ReportBlock.Paragraph ->
                    body.append(
                        paragraph(
                            runs(block.text, 20, italic = block.muted, color = if (block.muted) "666666" else null)
                        )
                    )
                is ReportBlock.KeyValues ->
                    block.rows.forEach { (key, value) ->
                        body.append(
                            paragraph(runs("$key: ", 20, bold = true) + runs(value, 20), after = 20)
                        )
                    }
                is ReportBlock.Table -> body.append(table(block, textWidth))
            }
        }

        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
                """<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>""" +
                body +
                "<w:sectPr><w:pgSz w:w=\"$pageW\" w:h=\"$pageH\"" +
                (if (doc.landscape) " w:orient=\"landscape\"" else "") + "/>" +
                "<w:pgMar w:top=\"$margin\" w:right=\"$margin\" w:bottom=\"$margin\" w:left=\"$margin\" " +
                "w:header=\"708\" w:footer=\"708\" w:gutter=\"0\"/></w:sectPr>" +
                "</w:body></w:document>"
    }
}