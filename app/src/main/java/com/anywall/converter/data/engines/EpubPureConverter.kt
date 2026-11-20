package com.anywall.converter.data.engines

import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Lector y Generador 100% Kotlin puro de libros electrónicos `.epub` (EPUB 3 - v0.3).
 * - Extrae capítulos ordenados según el `spine` de `content.opf` hacia `HTML` o `TXT` (y luego `PDF`).
 * - Genera paquetes `.epub` válidos (con entrada `mimetype` sin comprimir al inicio) desde `TXT`, `MD` o `HTML`.
 */
object EpubPureConverter {

    data class EpubBookContent(
        val title: String,
        val author: String,
        val chaptersHtml: List<String>,
        val plainText: String
    )

    fun readEpubStream(input: InputStream): EpubBookContent {
        val entries = LinkedHashMap<String, ByteArray>()
        ZipInputStream(input).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    entries[entry.name.replace('\\', '/')] = zis.readBytes()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val containerXml = entries["META-INF/container.xml"]?.toString(Charsets.UTF_8).orEmpty()
        val opfPath = Regex("full-path=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
            .find(containerXml)?.groupValues?.getOrNull(1)
            ?: entries.keys.firstOrNull { it.endsWith(".opf", ignoreCase = true) }
            ?: ""

        val opfXml = entries[opfPath]?.toString(Charsets.UTF_8).orEmpty()
        val opfDir = if (opfPath.contains('/')) opfPath.substringBeforeLast('/') + "/" else ""

        val title = Regex("<dc:title[^>]*>([^<]+)</dc:title>", RegexOption.IGNORE_CASE)
            .find(opfXml)?.groupValues?.getOrNull(1)?.trim()
            ?.takeIf { it.isNotBlank() } ?: "Libro EPUB"

        val author = Regex("<dc:creator[^>]*>([^<]+)</dc:creator>", RegexOption.IGNORE_CASE)
            .find(opfXml)?.groupValues?.getOrNull(1)?.trim()
            ?.takeIf { it.isNotBlank() } ?: "AnyWall Reader"

        // Mapear manifest id -> href
        val manifestMap = LinkedHashMap<String, String>()
        val itemRegex = Regex("<item\\b([^>]*)/?>", RegexOption.IGNORE_CASE)
        val idRegex = Regex("\\bid=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
        val hrefRegex = Regex("\\bhref=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
        for (match in itemRegex.findAll(opfXml)) {
            val attrs = match.groupValues[1]
            val id = idRegex.find(attrs)?.groupValues?.getOrNull(1)
            val href = hrefRegex.find(attrs)?.groupValues?.getOrNull(1)
            if (!id.isNullOrBlank() && !href.isNullOrBlank()) {
                manifestMap[id] = href
            }
        }

        // Ordenar según itemref idref en spine
        val spineIds = Regex("<itemref\\b[^>]*\\bidref=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
            .findAll(opfXml)
            .map { it.groupValues[1] }
            .toList()

        val orderedChapterPaths = if (spineIds.isNotEmpty()) {
            spineIds.mapNotNull { id ->
                val relHref = manifestMap[id]?.substringBefore('#')
                if (relHref != null) "$opfDir$relHref" else null
            }
        } else {
            entries.keys.filter { key ->
                val lower = key.lowercase(Locale.ROOT)
                (lower.endsWith(".xhtml") || lower.endsWith(".html") || lower.endsWith(".htm")) &&
                    !lower.endsWith("nav.xhtml")
            }
        }

        val chaptersHtml = orderedChapterPaths.mapNotNull { path ->
            val bytes = entries[path]
                ?: entries.entries.firstOrNull { it.key.equals(path, ignoreCase = true) }?.value
            bytes?.toString(Charsets.UTF_8)
        }.ifEmpty {
            entries.entries
                .filter { (k, _) ->
                    val l = k.lowercase(Locale.ROOT)
                    l.endsWith(".xhtml") || l.endsWith(".html") || l.endsWith(".htm")
                }
                .map { it.value.toString(Charsets.UTF_8) }
        }

        val bodyExtracts = chaptersHtml.map { rawHtml ->
            val bodyMatch = Regex("<body\\b[^>]*>([\\s\\S]*?)</body>", RegexOption.IGNORE_CASE).find(rawHtml)
            bodyMatch?.groupValues?.getOrNull(1) ?: rawHtml
        }

        val plainText = bodyExtracts.joinToString("\n\n") { htmlFragment ->
            stripHtmlToPlainText(htmlFragment)
        }.trim()

        return EpubBookContent(
            title = title,
            author = author,
            chaptersHtml = bodyExtracts,
            plainText = plainText
        )
    }

    fun writeEpubFromHtmlBody(
        title: String,
        author: String = "SynthesizerEnd • AnyWall",
        bodyHtml: String,
        output: OutputStream
    ) {
        val bookId = "urn:uuid:${UUID.randomUUID()}"
        val cleanTitle = escapeXml(title.ifBlank { "Documento AnyWall" })
        val cleanAuthor = escapeXml(author)

        ZipOutputStream(output).use { zos ->
            // 1. Entrada obligatoria `mimetype` sin comprimir (STORED) al principio del ZIP
            val mimeBytes = "application/epub+zip".toByteArray(Charsets.US_ASCII)
            val crc = CRC32().apply { update(mimeBytes) }
            val mimeEntry = ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mimeBytes.size.toLong()
                compressedSize = mimeBytes.size.toLong()
                this.crc = crc.value
            }
            zos.putNextEntry(mimeEntry)
            zos.write(mimeBytes)
            zos.closeEntry()

            // 2. META-INF/container.xml
            putUtf8Entry(
                zos,
                "META-INF/container.xml",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
                """.trimIndent()
            )

            // 3. OEBPS/content.opf
            putUtf8Entry(
                zos,
                "OEBPS/content.opf",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="BookId">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:identifier id="BookId">$bookId</dc:identifier>
                    <dc:title>$cleanTitle</dc:title>
                    <dc:creator>$cleanAuthor</dc:creator>
                    <dc:language>es</dc:language>
                  </metadata>
                  <manifest>
                    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                    <item id="chapter1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine>
                    <itemref idref="chapter1"/>
                  </spine>
                </package>
                """.trimIndent()
            )

            // 4. OEBPS/nav.xhtml
            putUtf8Entry(
                zos,
                "OEBPS/nav.xhtml",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE html>
                <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" lang="es">
                <head><meta charset="UTF-8"/><title>$cleanTitle</title></head>
                <body>
                  <nav epub:type="toc" id="toc">
                    <h1>$cleanTitle</h1>
                    <ol><li><a href="chapter1.xhtml">$cleanTitle</a></li></ol>
                  </nav>
                </body>
                </html>
                """.trimIndent()
            )

            // 5. OEBPS/chapter1.xhtml
            putUtf8Entry(
                zos,
                "OEBPS/chapter1.xhtml",
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE html>
                <html xmlns="http://www.w3.org/1999/xhtml" lang="es">
                <head>
                  <meta charset="UTF-8"/>
                  <title>$cleanTitle</title>
                  <style>body{font-family:serif;line-height:1.6;margin:5%;color:#1e293b}h1,h2,h3{font-family:sans-serif;color:#0f172a}pre{background:#f1f5f9;padding:8px;white-space:pre-wrap}</style>
                </head>
                <body>
                  <h1>$cleanTitle</h1>
                  $bodyHtml
                </body>
                </html>
                """.trimIndent()
            )
            zos.flush()
        }
    }

    private fun putUtf8Entry(zos: ZipOutputStream, entryName: String, content: String) {
        val entry = ZipEntry(entryName)
        zos.putNextEntry(entry)
        zos.write(content.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    fun stripHtmlToPlainText(html: String): String {
        return html
            .replace(Regex("<style\\b[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<script\\b[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<(br|p|div|h[1-6]|li|tr)\\b[^>]*>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
            .lines()
            .joinToString("\n") { it.trim() }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun escapeXml(raw: String): String {
        return raw
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
