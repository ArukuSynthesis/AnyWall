package com.anywall.converter.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anywall.converter.domain.model.FormatCategory
import com.anywall.converter.domain.model.SupportedFormat
import com.anywall.converter.ui.theme.CategoryArchiveColor
import com.anywall.converter.ui.theme.CategoryDocumentColor
import com.anywall.converter.ui.theme.CategoryImageColor
import com.anywall.converter.ui.theme.CategoryMediaColor

/**
 * Especificación de selección de archivos filtrada por un formato de entrada específico (v0.5).
 */
data class FormatSpecificPickerOption(
    val id: String,
    val label: String,
    val extHint: String,
    val mimeTypes: Array<String>,
    val acceptedFormats: Set<SupportedFormat>
)

fun specificFormatOptionsForCategory(category: FormatCategory): List<FormatSpecificPickerOption> {
    return when (category) {
        FormatCategory.IMAGE -> listOf(
            FormatSpecificPickerOption("JPG", "JPG / JPEG", ".jpg, .jpeg", arrayOf("image/jpeg"), setOf(SupportedFormat.JPG)),
            FormatSpecificPickerOption("PNG", "PNG", ".png", arrayOf("image/png"), setOf(SupportedFormat.PNG)),
            FormatSpecificPickerOption("WEBP", "WEBP", ".webp", arrayOf("image/webp"), setOf(SupportedFormat.WEBP)),
            FormatSpecificPickerOption("HEIC", "HEIC / HEIF", ".heic, .heif", arrayOf("image/heic", "image/heif"), setOf(SupportedFormat.HEIC)),
            FormatSpecificPickerOption("AVIF", "AVIF", ".avif", arrayOf("image/avif"), setOf(SupportedFormat.AVIF)),
            FormatSpecificPickerOption("SVG", "SVG Vector", ".svg", arrayOf("image/svg+xml"), setOf(SupportedFormat.SVG)),
            FormatSpecificPickerOption("GIF", "GIF", ".gif", arrayOf("image/gif"), setOf(SupportedFormat.GIF)),
            FormatSpecificPickerOption("BMP", "BMP", ".bmp", arrayOf("image/bmp", "image/x-ms-bmp"), setOf(SupportedFormat.BMP))
        )

        FormatCategory.DOCUMENT -> listOf(
            FormatSpecificPickerOption("PDF", "PDF", ".pdf", arrayOf("application/pdf"), setOf(SupportedFormat.PDF)),
            FormatSpecificPickerOption(
                "DOCX",
                "DOCX (Word)",
                ".docx",
                arrayOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
                setOf(SupportedFormat.DOCX)
            ),
            FormatSpecificPickerOption(
                "XLSX",
                "XLSX (Excel)",
                ".xlsx",
                arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
                setOf(SupportedFormat.XLSX)
            ),
            FormatSpecificPickerOption("EPUB", "EPUB", ".epub", arrayOf("application/epub+zip"), setOf(SupportedFormat.EPUB)),
            FormatSpecificPickerOption(
                "CBZ",
                "CBZ (Cómic)",
                ".cbz",
                arrayOf("application/vnd.comicbook+zip", "application/zip", "application/octet-stream"),
                setOf(SupportedFormat.CBZ)
            ),
            FormatSpecificPickerOption(
                "CBR",
                "CBR (Cómic RAR)",
                ".cbr",
                arrayOf("application/vnd.comicbook-rar", "application/vnd.rar", "application/x-rar-compressed", "application/octet-stream"),
                setOf(SupportedFormat.CBR)
            ),
            FormatSpecificPickerOption("HTML", "HTML", ".html, .htm", arrayOf("text/html"), setOf(SupportedFormat.HTML)),
            FormatSpecificPickerOption("TXT", "TXT", ".txt", arrayOf("text/plain"), setOf(SupportedFormat.TXT)),
            FormatSpecificPickerOption(
                "MD",
                "Markdown",
                ".md",
                arrayOf("text/markdown", "text/plain", "application/octet-stream"),
                setOf(SupportedFormat.MD)
            )
        )

        FormatCategory.MEDIA -> listOf(
            FormatSpecificPickerOption("MP4", "MP4 Video", ".mp4", arrayOf("video/mp4"), setOf(SupportedFormat.MP4)),
            FormatSpecificPickerOption(
                "MKV",
                "MKV Video",
                ".mkv",
                arrayOf("video/x-matroska", "video/matroska", "application/octet-stream"),
                setOf(SupportedFormat.MKV)
            ),
            FormatSpecificPickerOption("MOV", "MOV Video", ".mov", arrayOf("video/quicktime"), setOf(SupportedFormat.MOV)),
            FormatSpecificPickerOption("WEBM", "WEBM Video", ".webm", arrayOf("video/webm"), setOf(SupportedFormat.WEBM)),
            FormatSpecificPickerOption("MP3", "MP3 Audio", ".mp3", arrayOf("audio/mpeg", "audio/mp3"), setOf(SupportedFormat.MP3)),
            FormatSpecificPickerOption("WAV", "WAV Audio", ".wav", arrayOf("audio/wav", "audio/x-wav", "audio/wave"), setOf(SupportedFormat.WAV)),
            FormatSpecificPickerOption(
                "M4A",
                "M4A / AAC",
                ".m4a, .aac",
                arrayOf("audio/mp4", "audio/aac", "audio/x-m4a"),
                setOf(SupportedFormat.M4A, SupportedFormat.AAC)
            ),
            FormatSpecificPickerOption("FLAC", "FLAC Audio", ".flac", arrayOf("audio/flac", "audio/x-flac"), setOf(SupportedFormat.FLAC)),
            FormatSpecificPickerOption(
                "OGG",
                "OGG / OPUS",
                ".ogg, .opus",
                arrayOf("audio/ogg", "audio/opus", "application/ogg"),
                setOf(SupportedFormat.OGG, SupportedFormat.OPUS)
            )
        )

        FormatCategory.ARCHIVE -> listOf(
            FormatSpecificPickerOption(
                "RAR",
                "RAR / RAR5",
                ".rar, .part1.rar",
                arrayOf("application/vnd.rar", "application/x-rar-compressed", "application/octet-stream"),
                setOf(SupportedFormat.RAR)
            ),
            FormatSpecificPickerOption("ZIP", "ZIP", ".zip", arrayOf("application/zip", "application/x-zip-compressed"), setOf(SupportedFormat.ZIP)),
            FormatSpecificPickerOption("7Z", "7-Zip (.7z)", ".7z", arrayOf("application/x-7z-compressed", "application/octet-stream"), setOf(SupportedFormat.SEVEN_Z)),
            FormatSpecificPickerOption(
                "TAR",
                "TAR / GZ / XZ / BZ2",
                ".tar, .tar.gz, .tar.xz, .tar.bz2",
                arrayOf("application/x-tar", "application/gzip", "application/x-xz", "application/x-bzip2", "application/octet-stream"),
                setOf(
                    SupportedFormat.TAR,
                    SupportedFormat.TAR_GZ,
                    SupportedFormat.TAR_XZ,
                    SupportedFormat.TAR_BZ2,
                    SupportedFormat.GZ,
                    SupportedFormat.XZ,
                    SupportedFormat.BZ2
                )
            ),
            FormatSpecificPickerOption(
                "PARTS",
                "Partes (.part001)",
                ".part001, .001",
                arrayOf("application/octet-stream", "*/*"),
                setOf(SupportedFormat.FILE_PARTS)
            )
        )

        FormatCategory.DATA_TOOLS -> listOf(
            FormatSpecificPickerOption(
                "ANYWALL_VAULT",
                "Bóveda AES-256 (.anywall)",
                ".anywall",
                arrayOf("application/octet-stream", "*/*"),
                setOf(SupportedFormat.ANYWALL_VAULT)
            ),
            FormatSpecificPickerOption("JSON", "JSON", ".json", arrayOf("application/json", "text/json", "text/plain"), setOf(SupportedFormat.JSON)),
            FormatSpecificPickerOption("CSV", "CSV", ".csv", arrayOf("text/csv", "text/comma-separated-values", "text/plain"), setOf(SupportedFormat.CSV)),
            FormatSpecificPickerOption("XML", "XML", ".xml", arrayOf("application/xml", "text/xml", "text/plain"), setOf(SupportedFormat.XML)),
            FormatSpecificPickerOption(
                "YAML",
                "YAML",
                ".yaml, .yml",
                arrayOf("application/x-yaml", "text/yaml", "text/plain", "application/octet-stream"),
                setOf(SupportedFormat.YAML)
            ),
            FormatSpecificPickerOption(
                "BASE64",
                "BASE64",
                ".b64, .base64",
                arrayOf("text/plain", "application/octet-stream"),
                setOf(SupportedFormat.BASE64)
            )
        )
    }
}

/**
 * Diálogo selector de formato específico antes de abrir el buscador de archivos del sistema (v0.5).
 * Garantiza que la búsqueda solicite únicamente archivos del formato elegido por el usuario.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpecificFormatPickerDialog(
    category: FormatCategory,
    isEnglish: Boolean = false,
    onSelectFormatOption: (FormatSpecificPickerOption) -> Unit,
    onDismiss: () -> Unit
) {
    val accentColor = categoryAccentColor(category)
    val options = specificFormatOptionsForCategory(category)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (isEnglish) {
                        "Choose format in ${category.localizedName(true)}"
                    } else {
                        "Elegir formato en ${category.localizedName(false)}"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = if (isEnglish) {
                        "The file picker will request files of the selected format"
                    } else {
                        "El buscador solicitará archivos del formato que elijas"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEach { opt ->
                    Surface(
                        onClick = { onSelectFormatOption(opt) },
                        shape = RoundedCornerShape(14.dp),
                        color = accentColor.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.38f))
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = opt.label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = opt.extHint,
                                style = MaterialTheme.typography.labelSmall,
                                color = accentColor
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isEnglish) "Cancel" else "Cancelar")
            }
        }
    )
}

/**
 * Tarjeta Bento Grid moderna por categoría en `HomeScreen` (v0.5),
 * con píldoras de formato específico pulsables directamente o apertura del selector de formato.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryQuickCard(
    category: FormatCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isEnglish: Boolean = false,
    onQuickPickSpecificFormat: ((FormatSpecificPickerOption) -> Unit)? = null
) {
    val accentColor = categoryAccentColor(category)
    val icon = categoryIcon(category)
    val subtitle = categoryShortSubtitle(category, isEnglish)
    val formatOptions = specificFormatOptionsForCategory(category)

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        border = BorderStroke(
            width = 1.dp,
            color = accentColor.copy(alpha = 0.28f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    )
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(accentColor.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = category.localizedName(isEnglish),
                            tint = accentColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = category.localizedName(isEnglish),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(14.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Píldoras directas por formato específico
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                formatOptions.forEach { opt ->
                    Surface(
                        onClick = {
                            if (onQuickPickSpecificFormat != null) {
                                onQuickPickSpecificFormat(opt)
                            } else {
                                onClick()
                            }
                        },
                        shape = RoundedCornerShape(9.dp),
                        color = accentColor.copy(alpha = 0.15f),
                        border = BorderStroke(0.8.dp, accentColor.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = opt.id,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = accentColor,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun categoryShortSubtitle(category: FormatCategory, isEn: Boolean): String {
    return when (category) {
        FormatCategory.IMAGE -> if (isEn) "Tap a format or card to pick images" else "Toca un formato específico para buscar"
        FormatCategory.DOCUMENT -> if (isEn) "PDF, Office documents, eBooks & comics" else "PDF, Office, libros EPUB y cómics"
        FormatCategory.MEDIA -> if (isEn) "Video & audio formats (Trim, Crop, ID3)" else "Formatos de video y audio por separado"
        FormatCategory.ARCHIVE -> if (isEn) "Extract .rar/.rar5, .zip, .7z & repack" else "Extraer .rar/.rar5, .zip, .7z y empaquetar"
        FormatCategory.DATA_TOOLS -> if (isEn) "Structured data & encrypted vault" else "Datos estructurados y bóveda AES-256"
    }
}

fun categoryAccentColor(category: FormatCategory): Color {
    return when (category) {
        FormatCategory.IMAGE -> CategoryImageColor
        FormatCategory.DOCUMENT -> CategoryDocumentColor
        FormatCategory.MEDIA -> CategoryMediaColor
        FormatCategory.ARCHIVE -> CategoryArchiveColor
        FormatCategory.DATA_TOOLS -> Color(0xFF0D9488)
    }
}

fun categoryIcon(category: FormatCategory): ImageVector {
    return when (category) {
        FormatCategory.IMAGE -> Icons.Default.Image
        FormatCategory.DOCUMENT -> Icons.Default.Description
        FormatCategory.MEDIA -> Icons.Default.Movie
        FormatCategory.ARCHIVE -> Icons.Default.FolderZip
        FormatCategory.DATA_TOOLS -> Icons.Default.Security
    }
}
