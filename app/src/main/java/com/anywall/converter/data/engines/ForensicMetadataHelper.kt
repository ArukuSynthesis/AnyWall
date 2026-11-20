package com.anywall.converter.data.engines

import android.content.Context
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import com.anywall.converter.core.UriFileResolver
import com.anywall.converter.domain.model.InputFileItem
import com.anywall.converter.domain.model.SupportedFormat
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Inspector y limpiador forense 100% offline de metadatos (EXIF/GPS, PDF, Multimedia)
 * y verificador de integridad (`SHA-256` y `MD5`) (v0.3).
 */
class ForensicMetadataHelper(
    private val context: Context,
    private val uriResolver: UriFileResolver
) {

    data class MetadataEntry(
        val section: String,
        val key: String,
        val value: String,
        val isSensitivePrivacy: Boolean = false
    )

    data class FileForensicReport(
        val fileName: String,
        val formatLabel: String,
        val sizeBytes: Long,
        val sha256Hex: String,
        val md5Hex: String,
        val entries: List<MetadataEntry>,
        val hasSensitiveMetadata: Boolean
    )

    fun inspectInputFile(item: InputFileItem): FileForensicReport {
        val tempExt = item.format.extension.ifBlank { "tmp" }
        val tempFile = uriResolver.copyUriToTempFile(item.uri, "inspect_", tempExt)
        try {
            val sha256 = AesVaultCrypto.computeSha256Hex(tempFile)
            val md5 = AesVaultCrypto.computeMd5Hex(tempFile)
            val entries = mutableListOf<MetadataEntry>()

            when {
                item.format in setOf(
                    SupportedFormat.JPG,
                    SupportedFormat.PNG,
                    SupportedFormat.WEBP,
                    SupportedFormat.HEIC,
                    SupportedFormat.AVIF
                ) -> {
                    extractExifMetadata(tempFile, entries)
                }

                item.format == SupportedFormat.PDF -> {
                    extractPdfMetadata(tempFile, entries)
                }

                item.format.isVideo || item.format.isAudio -> {
                    extractMediaMetadata(tempFile, entries)
                }
            }

            if (entries.isEmpty()) {
                entries.add(
                    MetadataEntry(
                        section = "General",
                        key = "Estado de metadatos",
                        value = "Sin etiquetas EXIF/GPS/Autor detectadas en la cabecera",
                        isSensitivePrivacy = false
                    )
                )
            }

            return FileForensicReport(
                fileName = item.displayName,
                formatLabel = item.format.label,
                sizeBytes = tempFile.length(),
                sha256Hex = sha256,
                md5Hex = md5,
                entries = entries,
                hasSensitiveMetadata = entries.any { it.isSensitivePrivacy }
            )
        } finally {
            tempFile.delete()
        }
    }

    private fun extractExifMetadata(file: File, out: MutableList<MetadataEntry>) {
        try {
            val exif = ExifInterface(file.absolutePath)
            val tagPairs = listOf(
                Triple(ExifInterface.TAG_MAKE, "Fabricante de cámara", true),
                Triple(ExifInterface.TAG_MODEL, "Modelo de dispositivo", true),
                Triple(ExifInterface.TAG_SOFTWARE, "Software / Editor", false),
                Triple(ExifInterface.TAG_DATETIME, "Fecha y hora captura", true),
                Triple(ExifInterface.TAG_DATETIME_ORIGINAL, "Fecha original", true),
                Triple(ExifInterface.TAG_ARTIST, "Artista / Autor", true),
                Triple(ExifInterface.TAG_COPYRIGHT, "Copyright", false),
                Triple(ExifInterface.TAG_USER_COMMENT, "Comentario de usuario", true),
                Triple(ExifInterface.TAG_GPS_LATITUDE, "GPS Latitud", true),
                Triple(ExifInterface.TAG_GPS_LONGITUDE, "GPS Longitud", true),
                Triple(ExifInterface.TAG_GPS_ALTITUDE, "GPS Altitud", true),
                Triple(ExifInterface.TAG_GPS_DATESTAMP, "GPS Fecha", true),
                Triple(ExifInterface.TAG_IMAGE_WIDTH, "Ancho EXIF (px)", false),
                Triple(ExifInterface.TAG_IMAGE_LENGTH, "Alto EXIF (px)", false),
                Triple(ExifInterface.TAG_ORIENTATION, "Orientación EXIF", false),
                Triple(ExifInterface.TAG_FOCAL_LENGTH, "Distancia focal", false),
                Triple(ExifInterface.TAG_EXPOSURE_TIME, "Tiempo de exposición", false),
                Triple(ExifInterface.TAG_F_NUMBER, "Apertura (f/)", false)
            )
            for ((tag, label, sensitive) in tagPairs) {
                val value = exif.getAttribute(tag)?.trim()
                if (!value.isNullOrBlank()) {
                    out.add(
                        MetadataEntry(
                            section = if (sensitive) "Privacidad / EXIF & GPS" else "Imagen Técnica",
                            key = label,
                            value = value,
                            isSensitivePrivacy = sensitive
                        )
                    )
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun extractPdfMetadata(file: File, out: MutableList<MetadataEntry>) {
        try {
            PDDocument.load(file).use { doc ->
                out.add(
                    MetadataEntry(
                        section = "Documento PDF",
                        key = "Total de páginas",
                        value = doc.numberOfPages.toString(),
                        isSensitivePrivacy = false
                    )
                )
                out.add(
                    MetadataEntry(
                        section = "Documento PDF",
                        key = "Cifrado",
                        value = if (doc.isEncrypted) "Sí" else "No",
                        isSensitivePrivacy = false
                    )
                )
                val info = doc.documentInformation
                val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                info.title?.takeIf { it.isNotBlank() }?.let {
                    out.add(MetadataEntry("Metadatos PDF", "Título", it, false))
                }
                info.author?.takeIf { it.isNotBlank() }?.let {
                    out.add(MetadataEntry("Privacidad PDF", "Autor", it, true))
                }
                info.subject?.takeIf { it.isNotBlank() }?.let {
                    out.add(MetadataEntry("Metadatos PDF", "Asunto", it, false))
                }
                info.keywords?.takeIf { it.isNotBlank() }?.let {
                    out.add(MetadataEntry("Metadatos PDF", "Palabras clave", it, false))
                }
                info.creator?.takeIf { it.isNotBlank() }?.let {
                    out.add(MetadataEntry("Privacidad PDF", "Aplicación creadora", it, true))
                }
                info.producer?.takeIf { it.isNotBlank() }?.let {
                    out.add(MetadataEntry("Privacidad PDF", "Productor PDF", it, true))
                }
                info.creationDate?.time?.let { d: Date ->
                    out.add(MetadataEntry("Privacidad PDF", "Fecha de creación", fmt.format(d), true))
                }
                info.modificationDate?.time?.let { d: Date ->
                    out.add(MetadataEntry("Privacidad PDF", "Fecha de modificación", fmt.format(d), true))
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun extractMediaMetadata(file: File, out: MutableList<MetadataEntry>) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            if (durationMs != null && durationMs > 0L) {
                val secs = durationMs / 1000
                out.add(
                    MetadataEntry(
                        section = "Multimedia",
                        key = "Duración",
                        value = String.format(Locale.US, "%02d:%02d (%d s)", secs / 60, secs % 60, secs),
                        isSensitivePrivacy = false
                    )
                )
            }
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            if (!w.isNullOrBlank() && !h.isNullOrBlank()) {
                out.add(MetadataEntry("Multimedia", "Resolución", "${w}×${h} px", false))
            }
            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()
            if (bitrate != null && bitrate > 0L) {
                out.add(MetadataEntry("Multimedia", "Bitrate", "${bitrate / 1000} kbps", false))
            }
            val loc = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
            if (!loc.isNullOrBlank()) {
                out.add(MetadataEntry("Privacidad Multimedia", "Coordenadas GPS", loc, true))
            }
            val date = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
            if (!date.isNullOrBlank()) {
                out.add(MetadataEntry("Privacidad Multimedia", "Fecha de grabación", date, true))
            }
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            if (!artist.isNullOrBlank()) {
                out.add(MetadataEntry("Privacidad Multimedia", "Artista / Autor", artist, true))
            }
        } catch (_: Throwable) {
        } finally {
            try {
                retriever.release()
            } catch (_: Throwable) {
            }
        }
    }
}
