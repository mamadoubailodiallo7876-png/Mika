package com.wangzan101.mika.core.career

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.zip.Inflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ExtractedText(val text: String, val format: DocumentFormat, val warnings: List<String> = emptyList())

/**
 * Plain, dependency-free DOCX and PDF writers. The DOCX is a minimal OOXML package and the PDF is
 * a single-font text document, which is what ATS parsers handle best. Both are deterministic.
 */
object DocumentExporter {
    fun suggestedFileName(title: String, format: DocumentFormat): String {
        val stem = title.trim().replace(Regex("[^A-Za-z0-9._ -]+"), "-").trim('-', '.', ' ').ifBlank { "career-document" }.take(70)
        return "$stem.${format.name.lowercase()}"
    }

    fun bytes(title: String, content: String, format: DocumentFormat): ByteArray {
        val body = content.ifBlank { title }
        return when (format) {
            DocumentFormat.Txt -> "$body\n".toByteArray(Charsets.UTF_8)
            DocumentFormat.Docx -> docx(body)
            DocumentFormat.Pdf -> pdf(body)
        }
    }

    private fun docx(content: String): ByteArray {
        val paragraphs = content.lines()
        val body = paragraphs.joinToString("") { raw ->
            val text = escapeXml(raw.replace('\t', ' ').trimEnd())
            if (text.isEmpty()) {
                "<w:p><w:pPr><w:spacing w:after=\"0\"/></w:pPr></w:p>"
            } else {
                "<w:p><w:pPr><w:spacing w:after=\"120\" w:line=\"276\" w:lineRule=\"auto\"/></w:pPr>" +
                    "<w:r><w:rPr><w:rFonts w:ascii=\"Calibri\" w:hAnsi=\"Calibri\"/><w:sz w:val=\"22\"/></w:rPr>" +
                    "<w:t xml:space=\"preserve\">$text</w:t></w:r></w:p>"
            }
        }
        val document = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$body<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1134" w:right="1134" w:bottom="1134" w:left="1134"/></w:sectPr></w:body></w:document>"""
        val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>"""
        val rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>"""
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun entry(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry("[Content_Types].xml", contentTypes)
            entry("_rels/.rels", rels)
            entry("word/document.xml", document)
        }
        return out.toByteArray()
    }

    private fun pdf(content: String): ByteArray {
        val lines = content.lines().flatMap { wrap(sanitizePdf(it), 92) }
        val pages = lines.chunked(52).ifEmpty { listOf(emptyList()) }
        val out = ByteArrayOutputStream()
        val offsets = mutableListOf<Int>()
        fun write(text: String) { out.write(text.toByteArray(Charsets.ISO_8859_1)) }
        fun objectAt(number: Int, body: String) {
            while (offsets.size <= number) offsets += 0
            offsets[number] = out.size()
            write("$number 0 obj\n$body\nendobj\n")
        }

        write("%PDF-1.4\n")
        write("%\u00E2\u00E3\u00CF\u00D3\n")
        objectAt(1, "<< /Type /Catalog /Pages 2 0 R >>")
        val kids = pages.indices.joinToString(" ") { "${4 + it * 2} 0 R" }
        objectAt(2, "<< /Type /Pages /Kids [$kids] /Count ${pages.size} >>")
        objectAt(3, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>")
        pages.forEachIndexed { index, page ->
            val pageObj = 4 + index * 2
            val contentObj = pageObj + 1
            val stream = buildString {
                append("BT\n/F1 10 Tf\n14 TL\n50 792 Td\n")
                page.forEach { line ->
                    append('(').append(escapePdf(line)).append(") Tj\nT*\n")
                }
                append("ET\n")
            }
            objectAt(contentObj, "<< /Length ${stream.toByteArray(Charsets.ISO_8859_1).size} >>\nstream\n$stream" + "endstream")
            objectAt(pageObj, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 3 0 R >> >> /Contents $contentObj 0 R >>")
        }
        val count = offsets.size - 1
        val xref = out.size()
        write("xref\n0 ${count + 1}\n")
        write("0000000000 65535 f \n")
        for (i in 1..count) write("%010d 00000 n \n".format(Locale.ROOT, offsets[i]))
        write("trailer\n<< /Size ${count + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        return out.toByteArray()
    }

    private fun wrap(line: String, width: Int): List<String> {
        if (line.isEmpty()) return listOf("")
        val out = mutableListOf<String>()
        var rest = line
        while (rest.length > width) {
            val cut = rest.lastIndexOf(' ', width).takeIf { it > width / 2 } ?: width
            out += rest.take(cut).trimEnd()
            rest = rest.substring(cut).trimStart()
        }
        out += rest
        return out
    }

    private fun sanitizePdf(line: String): String = buildString {
        for (ch in line) {
            append(
                when {
                    ch.code in 32..126 -> ch
                    ch == '—' || ch == '–' -> '-'
                    ch == '’' || ch == '‘' -> '\''
                    ch == '“' || ch == '”' -> '"'
                    ch == '•' -> '-'
                    ch == '\t' -> ' '
                    ch.code in 160..255 -> ch
                    else -> '?'
                },
            )
        }
    }

    private fun escapePdf(text: String): String = text
        .replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")

    private fun escapeXml(text: String): String = buildString {
        for (ch in text) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> if (ch.code >= 32 || ch == '\t') append(ch)
            }
        }
    }
}

/**
 * Text extraction for the formats people actually send: TXT/MD, DOCX and text-based PDF. Scanned
 * PDFs are reported honestly instead of pretending to read them.
 */
object DocumentText {
    fun extract(fileName: String, bytes: ByteArray): ExtractedText {
        val lower = fileName.lowercase()
        val byName = when {
            lower.endsWith(".docx") -> extractDocx(bytes)
            lower.endsWith(".pdf") -> extractPdf(bytes)
            lower.endsWith(".txt") || lower.endsWith(".md") -> plain(bytes)
            else -> null
        }
        if (byName != null) return byName
        // Sniff by magic when the extension is missing or wrong.
        if (bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            val docx = extractDocx(bytes)
            if (docx.text.isNotBlank()) return docx
        }
        if (bytes.size >= 4 && String(bytes.copyOf(4), Charsets.ISO_8859_1) == "%PDF") return extractPdf(bytes)
        return plain(bytes)
    }

    private fun plain(bytes: ByteArray): ExtractedText {
        val utf8 = String(bytes, Charsets.UTF_8)
        val replacementRatio = if (utf8.isEmpty()) 0.0 else utf8.count { it == '\uFFFD' }.toDouble() / utf8.length
        val text = if (replacementRatio > 0.02) String(bytes, Charsets.ISO_8859_1) else utf8
        return ExtractedText(clean(text).take(MAX), DocumentFormat.Txt)
    }

    private fun extractDocx(bytes: ByteArray): ExtractedText {
        var xml: String? = null
        runCatching {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "word/document.xml") {
                        xml = zip.readBytes().toString(Charsets.UTF_8)
                        break
                    }
                    entry = zip.nextEntry
                }
            }
        }
        val source = xml ?: return ExtractedText("", DocumentFormat.Docx, listOf("The DOCX package has no readable document body."))
        val withBreaks = source
            .replace("</w:p>", "\n")
            .replace("<w:tab/>", "\t")
            .replace("<w:br/>", "\n")
            .replace("<w:cr/>", "\n")
            .replace("</w:tc>", " | ")
            .replace("</w:tr>", "\n")
        val stripped = withBreaks.replace(Regex("<[^>]+>"), "")
        val decoded = decodeEntities(stripped)
        val text = clean(decoded)
        return ExtractedText(text.take(MAX), DocumentFormat.Docx,
            if (text.isBlank()) listOf("No text was found in the DOCX file.") else emptyList())
    }

    private fun extractPdf(bytes: ByteArray): ExtractedText {
        val raw = String(bytes, Charsets.ISO_8859_1)
        val text = StringBuilder()
        var cursor = 0
        var streams = 0
        while (true) {
            val start = raw.indexOf("stream", cursor)
            if (start < 0) break
            var dataStart = start + "stream".length
            if (dataStart < raw.length && raw[dataStart] == '\r') dataStart++
            if (dataStart < raw.length && raw[dataStart] == '\n') dataStart++
            val end = raw.indexOf("endstream", dataStart)
            if (end < 0) break
            val dictionary = raw.substring(maxOf(0, start - 500), start)
            var data = raw.substring(dataStart, end).toByteArray(Charsets.ISO_8859_1)
            if (dictionary.contains("/FlateDecode")) data = inflate(data) ?: data
            streams++
            text.append(pdfOperators(String(data, Charsets.ISO_8859_1)))
            cursor = end + "endstream".length
        }
        val merged = clean(text.toString())
        val warnings = when {
            streams == 0 -> listOf("No PDF content streams were found; the file may be corrupted.")
            merged.isBlank() -> listOf("This PDF has no extractable text. Scanned PDFs need OCR before import.")
            else -> emptyList()
        }
        return ExtractedText(merged.take(MAX), DocumentFormat.Pdf, warnings)
    }

    private fun inflate(data: ByteArray): ByteArray? {
        val inflater = Inflater()
        return try {
            inflater.setInput(data)
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var guard = 0
            while (!inflater.finished() && guard < 10_000) {
                val n = inflater.inflate(buffer)
                if (n == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) break
                } else {
                    out.write(buffer, 0, n)
                }
                guard++
            }
            out.toByteArray().takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        } finally {
            inflater.end()
        }
    }

    /** Extracts strings from text operators; other strings in a content stream are ignored best-effort. */
    private fun pdfOperators(content: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < content.length) {
            when {
                content.startsWith("T*", i) -> { out.append('\n'); i += 2 }
                content.startsWith("Td", i) || content.startsWith("TD", i) -> { out.append('\n'); i += 2 }
                content.startsWith("'", i) -> { out.append('\n'); i++ }
                content[i] == '(' -> {
                    val end = closingParen(content, i)
                    if (end < 0) break
                    out.append(decodePdfString(content.substring(i + 1, end)))
                    i = end + 1
                }
                else -> i++
            }
        }
        return out.toString()
    }

    private fun closingParen(s: String, open: Int): Int {
        var depth = 1
        var i = open + 1
        while (i < s.length) {
            val ch = s[i]
            if (ch == '\\') { i += 2; continue }
            if (ch == '(') depth++
            if (ch == ')' && --depth == 0) return i
            i++
        }
        return -1
    }

    private fun decodePdfString(s: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (ch != '\\') { out.append(ch); i++; continue }
            if (i + 1 >= s.length) break
            when (val next = s[i + 1]) {
                'n' -> { out.append('\n'); i += 2 }
                'r' -> { out.append('\r'); i += 2 }
                't' -> { out.append('\t'); i += 2 }
                'b' -> { out.append('\b'); i += 2 }
                'f' -> { out.append('\u000C'); i += 2 }
                '(', ')', '\\' -> { out.append(next); i += 2 }
                '\n' -> i += 2
                '\r' -> i += if (i + 2 < s.length && s[i + 2] == '\n') 3 else 2
                in '0'..'7' -> {
                    var j = i + 1
                    var value = 0
                    var digits = 0
                    while (j < s.length && digits < 3 && s[j] in '0'..'7') {
                        value = value * 8 + (s[j] - '0'); j++; digits++
                    }
                    out.append(value.toChar()); i = j
                }
                else -> { out.append(next); i += 2 }
            }
        }
        return out.toString()
    }

    private fun decodeEntities(text: String): String = text
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'")
        .replace(Regex("&#x([0-9A-Fa-f]{1,6});")) { m -> m.groupValues[1].toInt(16).toChar().toString() }
        .replace(Regex("&#([0-9]{1,7});")) { m -> m.groupValues[1].toInt().toChar().toString() }

    private fun clean(text: String): String = text.replace("\r\n", "\n").replace('\r', '\n')
        .replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), "")
        .replace(Regex("[ \\t]+\\n"), "\n")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()

    private const val MAX = 200_000
}
