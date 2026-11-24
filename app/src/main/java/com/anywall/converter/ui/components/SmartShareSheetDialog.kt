package com.anywall.converter.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anywall.converter.domain.model.FormatCategory
import com.anywall.converter.domain.model.InputFileItem
import com.anywall.converter.domain.model.PdfActionMode
import com.anywall.converter.domain.model.SupportedFormat
import com.anywall.converter.domain.model.VideoResolution

/**
 * Acción directa en 1 toque ofrecida por `SmartShareSheetDialog` al recibir archivos desde
 * Android Share Sheet (`ACTION_SEND` / `ACTION_SEND_MULTIPLE` - Fase 1 v0.4).
 */
enum class ShareQuickActionType {
    CONVERT_WEBP_LIGHT,
    CONVERT_JPG_CLEAN_EXIF,
    MERGE_TO_PDF,
    OPEN_PHOTO_STUDIO,
    OPEN_PDF_EDITOR,
    COMPRESS_PDF,
    PDF_TO_IMAGES,
    EXTRACT_AUDIO_MP3,
    COMPRESS_VIDEO_720P,
    VIDEO_TO_GIF,
    EXTRACT_ARCHIVE_NOW,
    REPACK_TO_ZIP,
    COMIC_TO_PDF,
    ENCRYPT_AES256_VAULT,
    OPEN_FULL_CONFIGURE
}

/**
 * Diálogo inteligente al recibir `ACTION_SEND` / `ACTION_SEND_MULTIPLE` (`SmartShareSheetDialog` - v0.4):
 * Analiza las categorías y formatos MIME de los archivos compartidos y ofrece acciones directas en 1 toque.
 */
@Composable
fun SmartShareSheetDialog(
    sharedItems: List<InputFileItem>,
    isEnglish: Boolean = false,
    onExecuteQuickAction: (ShareQuickActionType) -> Unit,
    onDismissToConfigure: () -> Unit
) {
    if (sharedItems.isEmpty()) return

    val allImages = sharedItems.all { it.format.category == FormatCategory.IMAGE }
    val allPdfs = sharedItems.all { it.format == SupportedFormat.PDF }
    val allVideos = sharedItems.all { it.format.isVideo }
    val allAudios = sharedItems.all { it.format.isAudio }
    val allArchives = sharedItems.all { it.format.category == FormatCategory.ARCHIVE }
    val allComics = sharedItems.all { it.format == SupportedFormat.CBZ || it.format == SupportedFormat.CBR }
    val totalSizeFormatted = InputFileItem.formatBytes(sharedItems.sumOf { it.sizeBytes })

    data class QuickActionCardModel(
        val type: ShareQuickActionType,
        val icon: ImageVector,
        val labelEs: String,
        val labelEn: String
    )

    val actions = buildList {
        when {
            allImages -> {
                add(
                    QuickActionCardModel(
                        ShareQuickActionType.OPEN_PHOTO_STUDIO,
                        Icons.Filled.Crop,
                        "Abrir en Estudio de Fotos Táctil",
                        "Open in Touch Photo Studio"
                    )
                )
                add(
                    QuickActionCardModel(
                        ShareQuickActionType.CONVERT_WEBP_LIGHT,
                        Icons.Filled.Compress,
                        "Convertir ahora a WEBP Ligero (78%)",
                        "Convert now to Lightweight WEBP (78%)"
                    )
                )
                add(
                    QuickActionCardModel(
                        ShareQuickActionType.CONVERT_JPG_CLEAN_EXIF,
                        Icons.Filled.Image,
                        "Convertir a JPG sin metadatos EXIF/GPS",
                        "Convert to JPG & Strip EXIF/GPS"
                    )
                )
                add(
                    QuickActionCardModel(
                        ShareQuickActionType.MERGE_TO_PDF,
                        Icons.Filled.PictureAsPdf,
                        if (sharedItems.size > 1) "Unir ${sharedItems.size} imágenes en 1 PDF" else "Convertir imagen a PDF",
                        if (sharedItems.size > 1) "Merge ${sharedItems.size} images into 1 PDF" else "Convert image to PDF"
                    )
                )
            }
            allPdfs -> {
                add(
                    QuickActionCardModel(
                        ShareQuickActionType.OPEN_PDF_EDITOR,
                        Icons.Filled.EditNote,
                        "Abrir Editor PDF Táctil (Firmar / Anotar)",
                        "Open Touch PDF Editor (Sign / Annotate)"
                    )
                )
                add(
                    QuickActionCardModel(
                        ShareQuickActionType.COMPRESS_PDF,
                        Icons.Filled.Compress,
                        if (sharedItems.size > 1) "Unir y Optimizar PDFs" else "Comprimir y Optimizar PDF",
                        if (sharedItems.size > 1) "Merge & Optimize PDFs" else "Compress & Optimize PDF"
                    )
                )
                add(
                    QuickActionCardModel(
                        ShareQuickActionType.PDF_TO_IMAGES,
                        Icons.Filled.Image,
                        "Extraer páginas del PDF a JPG",
                        "Extract PDF pages to JPG"
                    )
                )
            }
            allVideos -> {
                add(
                    QuickActionCardModel(
                        ShareQuickActionType.EXTRACT_AUDIO_MP3,
                        Icons.Filled.AudioFile,
                        "Extraer Audio a MP3 (192 kbps + Auto-Gain)",
                        "Extract Audio to MP3 (192 kbps + Auto-Gain)"
                    )
                )
                add(
                    QuickActionCardModel(
                        ShareQuickActionType.COMPRESS_VIDEO_720P,
                        Icons.Filled.VideoFile,
                        "Comprimir Video a MP4 HD 720p",
                        "Compress Video to MP4 HD 720p"
                    )
                )
                add(
                    QuickActionCardModel(
                        ShareQuickActionType.VIDEO_TO_GIF,
                        Icons.Filled.AutoAwesome,
                        "Convertir Video a GIF Animado",
                        "Convert Video to Animated GIF"
                    )
                )
            }
            allAudios -> {
                add(
                    QuickActionCardModel(
                        ShareQuickActionType.EXTRACT_AUDIO_MP3,
                        Icons.Filled.AudioFile,
                        "Convertir a MP3 normalizado (-1.0 dBFS)",
                        "Convert to Normalized MP3 (-1.0 dBFS)"
                    )
                )
            }
            allComics -> {
                add(
                    QuickActionCardModel(
                        ShareQuickActionType.COMIC_TO_PDF,
                        Icons.Filled.PictureAsPdf,
                        "Convertir Cómic (${sharedItems.first().format.label}) a PDF",
                        "Convert Comic (${sharedItems.first().format.label}) to PDF"
                    )
                )
                add(
                    QuickActionCardModel(
                        ShareQuickActionType.PDF_TO_IMAGES,
                        Icons.Filled.Image,
                        "Extraer páginas del cómic a JPG",
                        "Extract comic pages to JPG"
                    )
                )
            }
            allArchives -> {
                add(
                    QuickActionCardModel(
                        ShareQuickActionType.EXTRACT_ARCHIVE_NOW,
                        Icons.Filled.FolderZip,
                        "Extraer Contenido en Downloads/AnyWall/",
                        "Extract Contents to Downloads/AnyWall/"
                    )
                )
                add(
                    QuickActionCardModel(
                        ShareQuickActionType.REPACK_TO_ZIP,
                        Icons.Filled.Archive,
                        "Re-empaquetar en 1 paso hacia .ZIP",
                        "1-Step Repackage to .ZIP"
                    )
                )
            }
            else -> {
                add(
                    QuickActionCardModel(
                        ShareQuickActionType.MERGE_TO_PDF,
                        Icons.Filled.PictureAsPdf,
                        "Convertir a Documento PDF",
                        "Convert to PDF Document"
                    )
                )
                add(
                    QuickActionCardModel(
                        ShareQuickActionType.REPACK_TO_ZIP,
                        Icons.Filled.Archive,
                        "Empaquetar lote en archivo .ZIP",
                        "Package batch into .ZIP archive"
                    )
                )
            }
        }
        add(
            QuickActionCardModel(
                ShareQuickActionType.ENCRYPT_AES256_VAULT,
                Icons.Filled.Lock,
                "Proteger en Bóveda AES-256 (.anywall)",
                "Encrypt in AES-256 Vault (.anywall)"
            )
        )
    }

    AlertDialog(
        onDismissRequest = onDismissToConfigure,
        icon = {
            Icon(
                imageVector = Icons.Filled.Share,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isEnglish) "Smart Share Actions" else "Acciones Rápidas de Compartición",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${sharedItems.size} ${if (sharedItems.size == 1) "archivo" else "archivos"} • $totalSizeFormatted",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                actions.forEach { act ->
                    FilledTonalButton(
                        onClick = { onExecuteQuickAction(act.type) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = act.icon,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (isEnglish) act.labelEn else act.labelEs,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                OutlinedButton(
                    onClick = { onExecuteQuickAction(ShareQuickActionType.OPEN_FULL_CONFIGURE) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isEnglish) "Advanced Studio Configuration" else "Configuración Avanzada en Estudio",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissToConfigure) {
                Text(if (isEnglish) "Close" else "Cerrar")
            }
        }
    )
}
