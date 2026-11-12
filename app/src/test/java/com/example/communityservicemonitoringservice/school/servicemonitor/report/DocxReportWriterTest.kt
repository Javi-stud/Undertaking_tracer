package com.school.servicemonitor.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class DocxReportWriterTest {

    private val doc = ReportDocument(
        title = "Test & <Report>",
        subtitle = "Line one\nLine two",
        generatedAt = "now",
        blocks = listOf(
            ReportBlock.Heading("Section"),
            ReportBlock.Paragraph("Quote \" and apostrophe ' and control \u0001 char"),
            ReportBlock.KeyValues(listOf("Key" to "Value")),
            ReportBlock.Table(
                headers = listOf("A", "B"),
                rows = listOf(listOf("Alcohol & Drugs", "5 hours"), listOf("", "x")),
                weights = listOf(2f, 1f)
            )
        ),
        landscape = true
    )

    private fun parts(d: ReportDocument): Map<String, String> {
        val out = ByteArrayOutputStream()
        DocxReportWriter.write(d, out)
        val map = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                map[e.name] = zip.readBytes().toString(Charsets.UTF_8)
                e = zip.nextEntry
            }
        }
        return map
    }

    @Test
    fun theZipHasTheThreeRequiredParts_contentTypesFirst() {
        val p = parts(doc)
        assertEquals(listOf("[Content_Types].xml", "_rels/.rels", "word/document.xml"), p.keys.toList())
    }

    @Test
    fun everyPartIsWellFormedXml_evenWithSpecialAndControlCharacters() {
        parts(doc).values.forEach { xml ->
            DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
        }
    }

    @Test
    fun specialCharactersAreEscaped_andControlCharactersDropped() {
        val xml = parts(doc).getValue("word/document.xml")
        assertTrue(xml.contains("Test &amp; &lt;Report&gt;"))
        assertTrue(xml.contains("Alcohol &amp; Drugs"))
        assertFalse(xml.contains('\u0001'))
    }

    @Test
    fun newlineBecomesALineBreak() {
        assertTrue(parts(doc).getValue("word/document.xml").contains("<w:br/>"))
    }

    @Test
    fun tableHasAHeaderRow_andAllRows() {
        val xml = parts(doc).getValue("word/document.xml")
        assertTrue(xml.contains("<w:tblHeader/>"))
        assertEquals(3, Regex("<w:tr>").findAll(xml).count())   // header + 2 rows
    }

    @Test
    fun landscapeAndPortraitSetThePageSize() {
        assertTrue(parts(doc).getValue("word/document.xml").contains("w:orient=\"landscape\""))
        val portrait = parts(doc.copy(landscape = false)).getValue("word/document.xml")
        assertFalse(portrait.contains("w:orient"))
        assertTrue(portrait.contains("w:w=\"11906\""))
    }
}