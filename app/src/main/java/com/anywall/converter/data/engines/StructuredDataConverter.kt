package com.anywall.converter.data.engines

import com.anywall.converter.domain.model.LineEndingOption
import com.anywall.converter.domain.model.SupportedFormat
import java.nio.charset.Charset
import java.util.Base64

/**
 * Conversor y formateador 100% Kotlin puro entre `CSV`, `JSON`, `XML`, `YAML`, `BASE64`, `HTML` y `TXT`,
 * incluyendo reparación de codificación de caracteres y normalización de saltos de línea (`LF` ↔ `CRLF`) (v0.3).
 * No utiliza `org.json.*` para garantizar compatibilidad idéntica en Android Runtime y pruebas JVM.
 */
object StructuredDataConverter {

    fun normalizeLineEndings(text: String, option: LineEndingOption): String {
        if (option == LineEndingOption.KEEP) return text
        val unifiedLf = text.replace("\r\n", "\n").replace("\r", "\n")
        return when (option) {
            LineEndingOption.LF -> unifiedLf
            LineEndingOption.CRLF -> unifiedLf.replace("\n", "\r\n")
            LineEndingOption.KEEP -> text
        }
    }

    fun transcodeBytes(
        rawBytes: ByteArray,
        sourceCharset: Charset,
        targetCharset: Charset,
        lineEndingOption: LineEndingOption
    ): ByteArray {
        val decoded = String(rawBytes, sourceCharset)
        val normalized = normalizeLineEndings(decoded, lineEndingOption)
        return normalized.toByteArray(targetCharset)
    }

    /**
     * Convierte un documento tabular o estructurado entre `CSV`, `JSON`, `XML`, `YAML`, `HTML`, `TXT` y `BASE64`.
     */
    fun convertStructuredText(
        sourceText: String,
        sourceFormat: SupportedFormat,
        targetFormat: SupportedFormat,
        prettyPrint: Boolean = true,
        lineEndingOption: LineEndingOption = LineEndingOption.KEEP,
        title: String = "Datos AnyWall"
    ): String {
        if (targetFormat == SupportedFormat.BASE64) {
            val encoded = Base64.getEncoder().encodeToString(sourceText.toByteArray(Charsets.UTF_8))
            return normalizeLineEndings(encoded, lineEndingOption)
        }
        if (sourceFormat == SupportedFormat.BASE64) {
            val cleanB64 = sourceText.trim().replace(Regex("\\s+"), "")
            val decodedBytes = try {
                Base64.getDecoder().decode(cleanB64)
            } catch (_: Throwable) {
                sourceText.toByteArray(Charsets.UTF_8)
            }
            val decodedText = String(decodedBytes, Charsets.UTF_8)
            if (targetFormat == SupportedFormat.TXT) {
                return normalizeLineEndings(decodedText, lineEndingOption)
            }
            return convertStructuredText(
                sourceText = decodedText,
                sourceFormat = sniffTextStructure(decodedText),
                targetFormat = targetFormat,
                prettyPrint = prettyPrint,
                lineEndingOption = lineEndingOption,
                title = title
            )
        }

        val records: List<Map<String, String>> = when (sourceFormat) {
            SupportedFormat.CSV -> parseCsvToRecords(sourceText)
            SupportedFormat.JSON -> parseJsonToRecords(sourceText)
            SupportedFormat.XML -> parseXmlToRecords(sourceText)
            SupportedFormat.YAML -> parseYamlToRecords(sourceText)
            else -> parseFallbackLinesToRecords(sourceText)
        }

        val result = when (targetFormat) {
            SupportedFormat.CSV -> formatRecordsToCsv(records)
            SupportedFormat.JSON -> formatRecordsToJson(records, prettyPrint)
            SupportedFormat.XML -> formatRecordsToXml(records, prettyPrint)
            SupportedFormat.YAML -> formatRecordsToYaml(records)
            SupportedFormat.HTML, SupportedFormat.PDF -> formatRecordsToHtmlTable(records, title, prettyPrint)
            else -> formatRecordsToPlainText(records)
        }

        return normalizeLineEndings(result, lineEndingOption)
    }

    private fun sniffTextStructure(text: String): SupportedFormat {
        val trimmed = text.trimStart()
        return when {
            trimmed.startsWith("{") || trimmed.startsWith("[") -> SupportedFormat.JSON
            trimmed.startsWith("<") -> SupportedFormat.XML
            trimmed.contains(",") && trimmed.lines().size >= 2 -> SupportedFormat.CSV
            trimmed.lines().any { it.matches(Regex("^\\s*[a-zA-Z0-9_-]+:\\s+.*")) } -> SupportedFormat.YAML
            else -> SupportedFormat.TXT
        }
    }

    // =========================================================================
    // CSV Parser & Formatter (RFC 4180 compatible)
    // =========================================================================

    fun parseCsvRows(csvText: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var currentRow = mutableListOf<String>()
        val currentField = StringBuilder()
        var inQuotes = false
        var i = 0
        val clean = csvText.replace("\r\n", "\n").replace("\r", "\n")

        while (i < clean.length) {
            val c = clean[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < clean.length && clean[i + 1] == '"') {
                        currentField.append('"')
                        i += 2
                        continue
                    } else {
                        inQuotes = false
                    }
                } else {
                    currentField.append(c)
                }
            } else {
                when (c) {
                    '"' -> inQuotes = true
                    ',' -> {
                        currentRow.add(currentField.toString())
                        currentField.clear()
                    }
                    '\n' -> {
                        currentRow.add(currentField.toString())
                        currentField.clear()
                        if (currentRow.any { it.isNotBlank() }) {
                            rows.add(currentRow)
                        }
                        currentRow = mutableListOf()
                    }
                    else -> currentField.append(c)
                }
            }
            i++
        }
        if (currentField.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(currentField.toString())
            if (currentRow.any { it.isNotBlank() }) {
                rows.add(currentRow)
            }
        }
        return rows
    }

    fun parseCsvToRecords(csvText: String): List<Map<String, String>> {
        val rows = parseCsvRows(csvText)
        if (rows.isEmpty()) return emptyList()
        val headers = rows.first().mapIndexed { idx, h ->
            h.trim().ifBlank { "col_${idx + 1}" }
        }
        if (rows.size == 1) {
            return listOf(headers.mapIndexed { idx, v -> "col_${idx + 1}" to v }.toMap())
        }
        return rows.drop(1).map { row ->
            val map = LinkedHashMap<String, String>()
            for (idx in headers.indices) {
                map[headers[idx]] = row.getOrNull(idx)?.trim().orEmpty()
            }
            map
        }
    }

    fun formatRecordsToCsv(records: List<Map<String, String>>): String {
        if (records.isEmpty()) return ""
        val headers = records.flatMap { it.keys }.distinct()
        val sb = StringBuilder()
        sb.appendLine(headers.joinToString(",") { escapeCsvField(it) })
        for (record in records) {
            sb.appendLine(headers.joinToString(",") { key -> escapeCsvField(record[key].orEmpty()) })
        }
        return sb.toString().trimEnd()
    }

    fun formatRowsToCsv(rows: List<List<String>>): String {
        if (rows.isEmpty()) return ""
        return rows.joinToString("\n") { row ->
            row.joinToString(",") { escapeCsvField(it) }
        }
    }

    private fun escapeCsvField(value: String): String {
        return if (value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    // =========================================================================
    // JSON Parser & Formatter (Pure Kotlin)
    // =========================================================================

    fun parseJsonToRecords(jsonText: String): List<Map<String, String>> {
        val trimmed = jsonText.trim()
        if (trimmed.isEmpty()) return emptyList()

        val objects = mutableListOf<String>()
        var depth = 0
        var objStart = -1
        var inString = false
        var escape = false

        for (i in trimmed.indices) {
            val c = trimmed[i]
            if (escape) {
                escape = false
                continue
            }
            if (c == '\\') {
                escape = true
                continue
            }
            if (c == '"') {
                inString = !inString
                continue
            }
            if (!inString) {
                if (c == '{') {
                    if (depth == 0) objStart = i
                    depth++
                } else if (c == '}') {
                    depth--
                    if (depth == 0 && objStart >= 0) {
                        objects.add(trimmed.substring(objStart + 1, i))
                        objStart = -1
                    }
                }
            }
        }

        if (objects.isEmpty()) {
            return parseFallbackLinesToRecords(trimmed)
        }

        val pairRegex = Regex("\"([^\"]+)\"\\s*:\\s*(\"((?:[^\"\\\\]|\\\\.)*)\"|[^,}]+)")
        return objects.mapNotNull { objBody ->
            val map = LinkedHashMap<String, String>()
            for (match in pairRegex.findAll(objBody)) {
                val key = unescapeJsonString(match.groupValues[1])
                val rawVal = match.groupValues[2].trim()
                val value = if (rawVal.startsWith("\"") && rawVal.endsWith("\"") && rawVal.length >= 2) {
                    unescapeJsonString(rawVal.substring(1, rawVal.length - 1))
                } else {
                    rawVal
                }
                map[key] = value
            }
            map.takeIf { it.isNotEmpty() }
        }
    }

    fun formatRecordsToJson(records: List<Map<String, String>>, prettyPrint: Boolean): String {
        if (records.isEmpty()) return "[]"
        return if (prettyPrint) {
            buildString {
                appendLine("[")
                records.forEachIndexed { rIdx, record ->
                    appendLine("  {")
                    val entries = record.entries.toList()
                    entries.forEachIndexed { eIdx, (k, v) ->
                        val comma = if (eIdx < entries.lastIndex) "," else ""
                        appendLine("    \"${escapeJsonString(k)}\": ${formatJsonScalar(v)}$comma")
                    }
                    val objComma = if (rIdx < records.lastIndex) "," else ""
                    appendLine("  }$objComma")
                }
                append("]")
            }
        } else {
            buildString {
                append("[")
                records.forEachIndexed { rIdx, record ->
                    if (rIdx > 0) append(",")
                    append("{")
                    val entries = record.entries.toList()
                    entries.forEachIndexed { eIdx, (k, v) ->
                        if (eIdx > 0) append(",")
                        append("\"${escapeJsonString(k)}\":${formatJsonScalar(v)}")
                    }
                    append("}")
                }
                append("]")
            }
        }
    }

    private fun formatJsonScalar(value: String): String {
        return when {
            value.equals("true", ignoreCase = true) || value.equals("false", ignoreCase = true) -> value.lowercase()
            value.equals("null", ignoreCase = true) -> "null"
            value.matches(Regex("^-?(?:0|[1-9]\\d*)(?:\\.\\d+)?$")) -> value
            else -> "\"${escapeJsonString(value)}\""
        }
    }

    private fun escapeJsonString(raw: String): String {
        return raw
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun unescapeJsonString(raw: String): String {
        return raw
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\\", "\\")
    }

    // =========================================================================
    // XML Parser & Formatter
    // =========================================================================

    fun parseXmlToRecords(xmlText: String): List<Map<String, String>> {
        val itemRegex = Regex("<(item|row|record|entry)\\b[^>]*>([\\s\\S]*?)</\\1>", RegexOption.IGNORE_CASE)
        val fieldRegex = Regex("<([a-zA-Z0-9_:-]+)\\b[^>]*>([^<]*)</\\1>")
        val itemMatches = itemRegex.findAll(xmlText).toList()

        if (itemMatches.isNotEmpty()) {
            return itemMatches.mapNotNull { itemMatch ->
                val body = itemMatch.groupValues[2]
                val map = LinkedHashMap<String, String>()
                for (fieldMatch in fieldRegex.findAll(body)) {
                    map[fieldMatch.groupValues[1]] = unescapeXml(fieldMatch.groupValues[2].trim())
                }
                map.takeIf { it.isNotEmpty() }
            }
        }

        val singleMap = LinkedHashMap<String, String>()
        for (fieldMatch in fieldRegex.findAll(xmlText)) {
            singleMap[fieldMatch.groupValues[1]] = unescapeXml(fieldMatch.groupValues[2].trim())
        }
        return if (singleMap.isNotEmpty()) listOf(singleMap) else parseFallbackLinesToRecords(xmlText)
    }

    fun formatRecordsToXml(records: List<Map<String, String>>, prettyPrint: Boolean): String {
        if (prettyPrint) {
            return buildString {
                appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                appendLine("<records>")
                for (record in records) {
                    appendLine("  <item>")
                    for ((k, v) in record) {
                        val tag = sanitizeXmlTag(k)
                        appendLine("    <$tag>${escapeXml(v)}</$tag>")
                    }
                    appendLine("  </item>")
                }
                append("</records>")
            }
        } else {
            return buildString {
                append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><records>")
                for (record in records) {
                    append("<item>")
                    for ((k, v) in record) {
                        val tag = sanitizeXmlTag(k)
                        append("<$tag>${escapeXml(v)}</$tag>")
                    }
                    append("</item>")
                }
                append("</records>")
            }
        }
    }

    private fun sanitizeXmlTag(raw: String): String {
        val cleaned = raw.trim().replace(Regex("[^a-zA-Z0-9_.-]"), "_")
        return if (cleaned.isEmpty() || !cleaned[0].isLetter()) "field_$cleaned" else cleaned
    }

    private fun escapeXml(raw: String): String {
        return raw
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun unescapeXml(raw: String): String {
        return raw
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
    }

    // =========================================================================
    // YAML Parser & Formatter
    // =========================================================================

    fun parseYamlToRecords(yamlText: String): List<Map<String, String>> {
        val records = mutableListOf<LinkedHashMap<String, String>>()
        var current = LinkedHashMap<String, String>()

        for (rawLine in yamlText.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#") || line == "---") continue

            if (line.startsWith("- ")) {
                if (current.isNotEmpty()) {
                    records.add(current)
                    current = LinkedHashMap()
                }
                val afterDash = line.removePrefix("- ").trim()
                if (afterDash.contains(':')) {
                    val key = afterDash.substringBefore(':').trim()
                    val value = unquoteYamlValue(afterDash.substringAfter(':').trim())
                    if (key.isNotEmpty()) current[key] = value
                }
            } else if (line.contains(':')) {
                val key = line.substringBefore(':').trim()
                val value = unquoteYamlValue(line.substringAfter(':').trim())
                if (key.isNotEmpty()) current[key] = value
            }
        }
        if (current.isNotEmpty()) {
            records.add(current)
        }
        return if (records.isNotEmpty()) records else parseFallbackLinesToRecords(yamlText)
    }

    fun formatRecordsToYaml(records: List<Map<String, String>>): String {
        if (records.isEmpty()) return "[]"
        return buildString {
            appendLine("---")
            for (record in records) {
                val entries = record.entries.toList()
                entries.forEachIndexed { idx, (k, v) ->
                    val prefix = if (idx == 0) "- " else "  "
                    appendLine("$prefix$k: ${formatYamlScalar(v)}")
                }
            }
        }.trimEnd()
    }

    private fun formatYamlScalar(value: String): String {
        return if (value.isEmpty() || value.contains(':') || value.contains('#') || value.contains('\n')) {
            "\"${escapeJsonString(value)}\""
        } else {
            value
        }
    }

    private fun unquoteYamlValue(raw: String): String {
        return when {
            raw.length >= 2 && raw.startsWith("\"") && raw.endsWith("\"") ->
                unescapeJsonString(raw.substring(1, raw.length - 1))
            raw.length >= 2 && raw.startsWith("'") && raw.endsWith("'") ->
                raw.substring(1, raw.length - 1)
            else -> raw
        }
    }

    // =========================================================================
    // HTML Table & Plain Text Formatters
    // =========================================================================

    fun formatRecordsToHtmlTable(
        records: List<Map<String, String>>,
        title: String,
        prettyPrint: Boolean = true
    ): String {
        val headers = records.flatMap { it.keys }.distinct()
        val rows = records.map { rec -> headers.map { h -> rec[h].orEmpty() } }
        return formatTableToHtml(
            headers = headers,
            rows = rows,
            title = title,
            prettyPrint = prettyPrint
        )
    }

    fun formatTableToHtml(
        headers: List<String>,
        rows: List<List<String>>,
        title: String,
        prettyPrint: Boolean = true
    ): String {
        val nl = if (prettyPrint) "\n" else ""
        return buildString {
            append("<!DOCTYPE html>$nl<html lang=\"es\">$nl<head>$nl")
            append("<meta charset=\"UTF-8\"/>$nl")
            append("<title>${escapeXml(title)}</title>$nl")
            append("<style>body{font-family:sans-serif;margin:20px;color:#1e293b}h2{color:#0f172a}table{border-collapse:collapse;width:100%;font-size:12px}th,td{border:1px solid #cbd5e1;padding:8px;text-align:left}th{background:#f1f5f9;font-weight:bold}tr:nth-child(even){background:#f8fafc}</style>$nl")
            append("</head>$nl<body>$nl")
            append("<h2>${escapeXml(title)}</h2>$nl")
            append("<table>$nl<thead><tr>")
            for (h in headers) {
                append("<th>${escapeXml(h)}</th>")
            }
            append("</tr></thead>$nl<tbody>$nl")
            for (row in rows) {
                append("<tr>")
                for (i in headers.indices) {
                    append("<td>${escapeXml(row.getOrNull(i).orEmpty())}</td>")
                }
                append("</tr>$nl")
            }
            append("</tbody></table>$nl</body>$nl</html>")
        }
    }

    private fun formatRecordsToPlainText(records: List<Map<String, String>>): String {
        return records.mapIndexed { idx, rec ->
            buildString {
                appendLine("=== Registro ${idx + 1} ===")
                for ((k, v) in rec) {
                    appendLine("$k: $v")
                }
            }.trimEnd()
        }.joinToString("\n\n")
    }

    private fun parseFallbackLinesToRecords(text: String): List<Map<String, String>> {
        val nonEmptyLines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        return nonEmptyLines.mapIndexed { idx, line ->
            if (line.contains('=') && !line.startsWith('=')) {
                mapOf(
                    "clave" to line.substringBefore('=').trim(),
                    "valor" to line.substringAfter('=').trim()
                )
            } else {
                mapOf(
                    "linea" to (idx + 1).toString(),
                    "contenido" to line
                )
            }
        }
    }
}
