package com.anywall.converter.data.engines

import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * Lector 100% Kotlin puro de hojas de cálculo `.xlsx` (Office Open XML Spreadsheet - v0.3).
 * Extrae `xl/sharedStrings.xml` y las hojas `xl/worksheets/sheet*.xml` mediante `ZipInputStream`
 * sin requerir Apache POI (~15 MB), manteniendo el APK ligero y 100% offline.
 */
object XlsxPureParser {

    data class XlsxSheetData(
        val sheetName: String,
        val rows: List<List<String>>
    )

    fun parseXlsxStream(input: InputStream): List<XlsxSheetData> {
        val entries = LinkedHashMap<String, String>()
        ZipInputStream(input).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                if (!entry.isDirectory &&
                    (name.equals("xl/sharedStrings.xml", ignoreCase = true) ||
                        name.equals("xl/workbook.xml", ignoreCase = true) ||
                        name.startsWith("xl/worksheets/sheet", ignoreCase = true) && name.endsWith(".xml", ignoreCase = true))
                ) {
                    val xmlText = zis.readBytes().toString(Charsets.UTF_8)
                    entries[name] = xmlText
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val sharedStringsXml = entries.entries.firstOrNull {
            it.key.equals("xl/sharedStrings.xml", ignoreCase = true)
        }?.value.orEmpty()
        val sharedStrings = parseSharedStrings(sharedStringsXml)

        val workbookXml = entries.entries.firstOrNull {
            it.key.equals("xl/workbook.xml", ignoreCase = true)
        }?.value.orEmpty()
        val sheetNames = parseWorkbookSheetNames(workbookXml)

        val sheetEntries = entries.entries
            .filter { it.key.startsWith("xl/worksheets/sheet", ignoreCase = true) }
            .sortedBy { it.key.lowercase(Locale.ROOT) }

        return sheetEntries.mapIndexed { idx, (_, sheetXml) ->
            val name = sheetNames.getOrNull(idx) ?: "Hoja ${idx + 1}"
            val rows = parseWorksheetRows(sheetXml, sharedStrings)
            XlsxSheetData(sheetName = name, rows = rows)
        }
    }

    private fun parseSharedStrings(xml: String): List<String> {
        if (xml.isBlank()) return emptyList()
        val siRegex = Regex("<si\\b[^>]*>([\\s\\S]*?)</si>", RegexOption.IGNORE_CASE)
        val tRegex = Regex("<t\\b[^>]*>([\\s\\S]*?)</t>", RegexOption.IGNORE_CASE)
        return siRegex.findAll(xml).map { siMatch ->
            val inner = siMatch.groupValues[1]
            tRegex.findAll(inner).joinToString("") { tMatch ->
                unescapeXmlEntities(tMatch.groupValues[1])
            }
        }.toList()
    }

    private fun parseWorkbookSheetNames(xml: String): List<String> {
        if (xml.isBlank()) return emptyList()
        val sheetRegex = Regex("<sheet\\b[^>]*\\bname=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
        return sheetRegex.findAll(xml).map { unescapeXmlEntities(it.groupValues[1]) }.toList()
    }

    private fun parseWorksheetRows(sheetXml: String, sharedStrings: List<String>): List<List<String>> {
        val rowRegex = Regex("<row\\b[^>]*>([\\s\\S]*?)</row>", RegexOption.IGNORE_CASE)
        val cellRegex = Regex("<c\\b([^>]*)>([\\s\\S]*?)</c>", RegexOption.IGNORE_CASE)
        val attrRRegex = Regex("\\br=\"([A-Z]+)\\d+\"", RegexOption.IGNORE_CASE)
        val attrTRegex = Regex("\\bt=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
        val vRegex = Regex("<v\\b[^>]*>([\\s\\S]*?)</v>", RegexOption.IGNORE_CASE)
        val isTRegex = Regex("<t\\b[^>]*>([\\s\\S]*?)</t>", RegexOption.IGNORE_CASE)

        val parsedRows = mutableListOf<List<String>>()
        for (rowMatch in rowRegex.findAll(sheetXml)) {
            val rowBody = rowMatch.groupValues[1]
            val colMap = LinkedHashMap<Int, String>()
            var nextColIndex = 0
            var maxColIndex = -1

            for (cellMatch in cellRegex.findAll(rowBody)) {
                val attrs = cellMatch.groupValues[1]
                val cellBody = cellMatch.groupValues[2]
                val colLetters = attrRRegex.find(attrs)?.groupValues?.getOrNull(1)
                val colIndex = if (!colLetters.isNullOrBlank()) {
                    columnLettersToZeroBasedIndex(colLetters)
                } else {
                    nextColIndex
                }
                nextColIndex = colIndex + 1
                if (colIndex > maxColIndex) maxColIndex = colIndex

                val cellType = attrTRegex.find(attrs)?.groupValues?.getOrNull(1).orEmpty()
                val rawV = vRegex.find(cellBody)?.groupValues?.getOrNull(1)?.trim().orEmpty()

                val resolvedValue = when (cellType) {
                    "s" -> {
                        val sIdx = rawV.toIntOrNull()
                        if (sIdx != null && sIdx in sharedStrings.indices) {
                            sharedStrings[sIdx]
                        } else {
                            rawV
                        }
                    }
                    "inlineStr", "str" -> {
                        val inlineText = isTRegex.findAll(cellBody).joinToString("") {
                            unescapeXmlEntities(it.groupValues[1])
                        }
                        inlineText.ifEmpty { unescapeXmlEntities(rawV) }
                    }
                    "b" -> if (rawV == "1" || rawV.equals("true", ignoreCase = true)) "TRUE" else "FALSE"
                    else -> unescapeXmlEntities(rawV)
                }
                colMap[colIndex] = resolvedValue
            }

            if (maxColIndex >= 0) {
                val rowList = (0..maxColIndex).map { idx -> colMap[idx].orEmpty() }
                if (rowList.any { it.isNotBlank() }) {
                    parsedRows.add(rowList)
                }
            }
        }
        return parsedRows
    }

    fun columnLettersToZeroBasedIndex(letters: String): Int {
        var result = 0
        for (ch in letters.uppercase(Locale.ROOT)) {
            if (ch in 'A'..'Z') {
                result = result * 26 + (ch - 'A' + 1)
            }
        }
        return (result - 1).coerceAtLeast(0)
    }

    private fun unescapeXmlEntities(raw: String): String {
        return raw
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
    }
}
