package com.anywall.converter.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
 * Hoja inferior moderna (`ModalBottomSheet`) para elegir un formato específico o todos los formatos
 * de la categoría antes de abrir el selector de archivos del sistema (v0.6 UX).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SpecificFormatPickerDialog(
    category: FormatCategory,
    isEnglish: Boolean = false,
    onSelectFormatOption: (FormatSpecificPickerOption) -> Unit,
    onSelectAllCategoryFormats: ((FormatCategory, Array<String>) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val accentColor = categoryAccentColor(category)
    val icon = categoryIcon(category)
    val options = specificFormatOptionsForCategory(category)
    val allCategoryMimes = remember(category) {
        options.flatMap { it.mimeTypes.toList() }.distinct().toTypedArray()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(accentColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column {
                    Text(
                        text = category.localizedName(isEnglish),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = if (isEnglish) {
                            "Pick a specific format or browse all files in this category"
                        } else {
                            "Elige un formato específico o busca cualquier archivo de esta categoría"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Botón destacado: Todos los formatos de esta categoría
            if (onSelectAllCategoryFormats != null) {
                Surface(
                    onClick = { onSelectAllCategoryFormats(category, allCategoryMimes) },
                    shape = RoundedCornerShape(16.dp),
                    color = accentColor.copy(alpha = 0.16f),
                    border = BorderStroke(1.2.dp, accentColor.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = if (isEnglish) {
                                    "All ${category.localizedName(true)} formats"
                                } else {
                                    "Todos los formatos de ${category.localizedName(false)}"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = accentColor
                            )
                            Text(
                                text = options.joinToString(", ") { it.id },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Text(
                text = if (isEnglish) "Filter by specific format:" else "O filtrar por un formato exacto:",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Cuadrícula de 2 columnas para los formatos específicos
            val chunkedOptions = remember(options) { options.chunked(2) }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                chunkedOptions.forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowItems.forEach { opt ->
                            Surface(
                                onClick = { onSelectFormatOption(opt) },
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.28f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = opt.label,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = opt.extHint,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = accentColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                        if (rowItems.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tarjeta Bento moderna por categoría en `HomeScreen` (v0.6 UX),
 * con tira horizontal deslizable de píldoras táctiles grandes (`Todos` + formatos específicos).
 */
@Composable
fun CategoryQuickCard(
    category: FormatCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isEnglish: Boolean = false,
    onQuickPickAllCategory: ((FormatCategory, Array<String>) -> Unit)? = null,
    onQuickPickSpecificFormat: ((FormatSpecificPickerOption) -> Unit)? = null
) {
    val accentColor = categoryAccentColor(category)
    val icon = categoryIcon(category)
    val subtitle = categoryShortSubtitle(category, isEnglish)
    val formatOptions = remember(category) { specificFormatOptionsForCategory(category) }
    val allCategoryMimes = remember(category) {
        formatOptions.flatMap { it.mimeTypes.toList() }.distinct().toTypedArray()
    }

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f),
        border = BorderStroke(
            width = 1.dp,
            color = accentColor.copy(alpha = 0.26f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.11f),
                            Color.Transparent
                        )
                    )
                )
                .padding(horizontal = 15.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
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
                            .size(42.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(accentColor.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = category.localizedName(isEnglish),
                            tint = accentColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
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

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = accentColor.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (isEnglish) "Formats" else "Ver todos",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = accentColor
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
            }

            // Tira horizontal táctil con píldoras cómodas para el pulgar ("Todos" + cada formato)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onQuickPickAllCategory != null) {
                    Surface(
                        onClick = { onQuickPickAllCategory(category, allCategoryMimes) },
                        shape = RoundedCornerShape(10.dp),
                        color = accentColor,
                    ) {
                        Text(
                            text = if (isEnglish) "All" else "Todos",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                formatOptions.forEach { opt ->
                    Surface(
                        onClick = {
                            if (onQuickPickSpecificFormat != null) {
                                onQuickPickSpecificFormat(opt)
                            } else {
                                onClick()
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = opt.id,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = accentColor,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun categoryShortSubtitle(category: FormatCategory, isEn: Boolean): String {
    return when (category) {
        FormatCategory.IMAGE -> if (isEn) "Convert, compress, stitch & edit photos" else "Convertir, comprimir, unir en tira y editar"
        FormatCategory.DOCUMENT -> if (isEn) "PDF, Word, Excel, eBooks & comic books" else "PDF, Word, Excel, libros EPUB y cómics"
        FormatCategory.MEDIA -> if (isEn) "Video & audio conversion, trim & crop" else "Video y audio, recorte, encuadre y MP3"
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
