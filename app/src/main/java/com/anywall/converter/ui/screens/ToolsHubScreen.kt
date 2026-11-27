package com.anywall.converter.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anywall.converter.core.AppLanguageMode
import com.anywall.converter.core.LocalThumbnailLoader
import com.anywall.converter.core.ScopedStorageManager
import com.anywall.converter.core.UriFileResolver
import com.anywall.converter.data.engines.ArchiveEngineImpl
import com.anywall.converter.data.engines.ForensicMetadataHelper
import com.anywall.converter.domain.model.ImageEditSpec
import com.anywall.converter.domain.model.InputFileItem
import com.anywall.converter.domain.model.PdfPageEditItem
import com.anywall.converter.domain.model.SupportedFormat
import com.anywall.converter.domain.model.VideoCropSpec
import com.anywall.converter.ui.components.ArchivePreviewDialog
import com.anywall.converter.ui.components.BatchRenamerDialog
import com.anywall.converter.ui.components.ImageVisualEditorDialog
import com.anywall.converter.ui.components.MediaTimelineTrimmerDialog
import com.anywall.converter.ui.components.PdfVisualEditorDialog
import com.anywall.converter.ui.components.SignaturePadDialog
import com.anywall.converter.ui.components.VideoCropDialog
import com.anywall.converter.ui.theme.CategoryArchiveColor
import com.anywall.converter.ui.theme.CategoryDocumentColor
import com.anywall.converter.ui.theme.CategoryImageColor
import com.anywall.converter.ui.theme.CategoryMediaColor

private enum class StudioLaunchTarget {
    PHOTO_STUDIO,
    PDF_STUDIO,
    MEDIA_TRIMMER,
    VIDEO_CROP,
    ARCHIVE_EXPLORER,
    BATCH_RENAMER
}

/**
 * Pestaña dedicada a los **6 Estudios Táctiles y de Lote** (`ToolsHubScreen` - v0.6 UX):
 * - Estudio de Fotos Táctil
 * - Estudio PDF, Firma y Anotaciones
 * - Línea de Tiempo de Video y Audio (Trim + Captura de fotograma exacto)
 * - Recorte Táctil de Encuadre de Video (Crop)
 * - Explorador `.RAR` / `.ZIP` / `.7Z` y Re-empaquetado en 1 paso
 * - Renombrador Masivo por Lote
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsHubScreen(
    configureState: ConfigureUiState,
    languageMode: AppLanguageMode,
    thumbnailLoader: LocalThumbnailLoader?,
    uriResolver: UriFileResolver,
    storageManager: ScopedStorageManager,
    forensicHelper: ForensicMetadataHelper,
    archiveEngine: ArchiveEngineImpl,
    onLoadFilesIntoQueue: (List<InputFileItem>, Boolean) -> Unit,
    onApplyVisualImageEdits: (ImageEditSpec) -> Unit,
    onApplyVisualPdfEdits: (List<PdfPageEditItem>) -> Unit,
    onUpdatePdfSignaturePngPath: (String) -> Unit,
    onUpdateTrimRangeMillis: (Long, Long) -> Unit,
    onUpdateVideoCropSpec: (VideoCropSpec) -> Unit,
    onUpdateMuteVideoAudio: (Boolean) -> Unit = {},
    onUpdateSelectedArchiveEntries: (Set<String>) -> Unit,
    onUpdateArchivePassword: (String) -> Unit,
    onSelectTargetFormat: (SupportedFormat) -> Unit,
    onStartConversion: () -> Unit,
    onOpenConfigure: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isEn = languageMode.isEnglishActive()

    var pendingStudioTarget by remember { mutableStateOf<StudioLaunchTarget?>(null) }
    var studioLoadedItems by remember { mutableStateOf<List<InputFileItem>>(emptyList()) }
    var activeStudioDialog by remember { mutableStateOf<StudioLaunchTarget?>(null) }
    var showSignaturePad by remember { mutableStateOf(false) }

    val studioFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        val target = pendingStudioTarget
        if (uris.isNotEmpty() && target != null) {
            val resolved = uriResolver.resolveMultipleFiles(uris)
            if (resolved.isNotEmpty()) {
                studioLoadedItems = resolved
                onUpdateTrimRangeMillis(0L, 0L)
                onUpdateVideoCropSpec(VideoCropSpec())
                onLoadFilesIntoQueue(resolved, false)
                activeStudioDialog = target
            }
        }
    }

    fun launchStudioOrPickFiles(
        target: StudioLaunchTarget,
        mimeFilters: Array<String>,
        existingMatchPredicate: (InputFileItem) -> Boolean
    ) {
        val matchingInQueue = configureState.inputFiles.filter(existingMatchPredicate)
        if (matchingInQueue.isNotEmpty()) {
            studioLoadedItems = matchingInQueue
            activeStudioDialog = target
        } else {
            pendingStudioTarget = target
            studioFilePicker.launch(mimeFilters)
        }
    }

    // Diálogos de los Estudios Táctiles lanzados desde el Hub
    when (activeStudioDialog) {
        StudioLaunchTarget.PHOTO_STUDIO -> {
            val imgs = studioLoadedItems.ifEmpty {
                configureState.inputFiles.filter { it.format.category.name == "IMAGE" }
            }
            if (imgs.isNotEmpty()) {
                ImageVisualEditorDialog(
                    imageInputs = imgs,
                    initialEditSpec = configureState.options.imageEditSpec,
                    onApplyEdits = { spec ->
                        onApplyVisualImageEdits(spec)
                        activeStudioDialog = null
                        onOpenConfigure()
                    },
                    onDismiss = { activeStudioDialog = null }
                )
            }
        }

        StudioLaunchTarget.PDF_STUDIO -> {
            val pdfs = studioLoadedItems.ifEmpty {
                configureState.inputFiles.filter { it.format == SupportedFormat.PDF }
            }
            if (pdfs.isNotEmpty()) {
                PdfVisualEditorDialog(
                    pdfInputFiles = pdfs,
                    initialEdits = configureState.visualPdfEditsList,
                    thumbnailLoader = thumbnailLoader,
                    onOpenSignaturePad = { showSignaturePad = true },
                    onApplyEdits = { edits ->
                        onApplyVisualPdfEdits(edits)
                        activeStudioDialog = null
                        onOpenConfigure()
                    },
                    onResetEdits = {
                        onApplyVisualPdfEdits(emptyList())
                        activeStudioDialog = null
                    },
                    onDismiss = { activeStudioDialog = null }
                )
            }
        }

        StudioLaunchTarget.MEDIA_TRIMMER -> {
            val media = studioLoadedItems.firstOrNull()
                ?: configureState.inputFiles.firstOrNull { it.format.isVideo || it.format.isAudio }
            if (media != null) {
                MediaTimelineTrimmerDialog(
                    mediaFile = media,
                    initialStartMs = configureState.options.effectiveTrimStartMillis,
                    initialEndMs = configureState.options.effectiveTrimEndMillis ?: 0L,
                    initialMuteAudio = configureState.options.muteVideoAudio,
                    onToggleMuteAudio = onUpdateMuteVideoAudio,
                    onApplyTrim = { startMs, endMs ->
                        onUpdateTrimRangeMillis(startMs, endMs)
                        activeStudioDialog = null
                        onOpenConfigure()
                    },
                    onDismiss = { activeStudioDialog = null }
                )
            }
        }

        StudioLaunchTarget.VIDEO_CROP -> {
            val video = studioLoadedItems.firstOrNull()
                ?: configureState.inputFiles.firstOrNull { it.format.isVideo }
            if (video != null) {
                VideoCropDialog(
                    videoFile = video,
                    initialCropSpec = configureState.options.videoCropSpec,
                    sampleTimeMs = configureState.options.effectiveTrimStartMillis,
                    onApplyCrop = { spec ->
                        onUpdateVideoCropSpec(spec)
                        activeStudioDialog = null
                        onOpenConfigure()
                    },
                    onDismiss = { activeStudioDialog = null }
                )
            }
        }

        StudioLaunchTarget.ARCHIVE_EXPLORER -> {
            val arch = studioLoadedItems.firstOrNull()
                ?: configureState.inputFiles.firstOrNull { it.format.category.name == "ARCHIVE" }
            if (arch != null) {
                ArchivePreviewDialog(
                    archiveItem = arch,
                    archiveEngine = archiveEngine,
                    initialSelectedEntries = configureState.options.selectedArchiveEntries,
                    initialPassword = configureState.options.archivePassword,
                    onApplySelection = { entries ->
                        onUpdateSelectedArchiveEntries(entries)
                        activeStudioDialog = null
                        onOpenConfigure()
                    },
                    onUpdateArchivePassword = onUpdateArchivePassword,
                    onRepackInOneStep = { targetFmt, entries, pass ->
                        onUpdateSelectedArchiveEntries(entries)
                        onUpdateArchivePassword(pass)
                        onSelectTargetFormat(targetFmt)
                        activeStudioDialog = null
                        onStartConversion()
                        onOpenConfigure()
                    },
                    onDismiss = { activeStudioDialog = null }
                )
            }
        }

        StudioLaunchTarget.BATCH_RENAMER -> {
            val items = studioLoadedItems.ifEmpty { configureState.inputFiles }
            if (items.isNotEmpty()) {
                BatchRenamerDialog(
                    inputFiles = items,
                    storageManager = storageManager,
                    onApplyRenamedItemsToQueue = { renamed ->
                        onLoadFilesIntoQueue(renamed, false)
                        activeStudioDialog = null
                    },
                    onDismiss = { activeStudioDialog = null }
                )
            }
        }

        null -> Unit
    }

    if (showSignaturePad) {
        SignaturePadDialog(
            onSignatureSaved = { path ->
                onUpdatePdfSignaturePngPath(path)
                showSignaturePad = false
            },
            onClearSavedSignature = {
                onUpdatePdfSignaturePngPath("")
                showSignaturePad = false
            },
            onDismiss = { showSignaturePad = false }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isEn) "Touch Studios" else "Estudios Táctiles",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = if (isEn) {
                                "Photo, PDF, Video/Audio timeline, Archive explorer & Batch renamer"
                            } else {
                                "Foto, PDF y firma, Línea de tiempo, Encuadre, RAR/ZIP y Renombrador"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (configureState.inputFiles.isNotEmpty()) {
                item {
                    Surface(
                        onClick = onOpenConfigure,
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.60f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isEn) {
                                    "Active queue: ${configureState.inputFiles.size} file(s) ready for studios"
                                } else {
                                    "Cola activa: ${configureState.inputFiles.size} archivo(s) listos para editar"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = if (isEn) "Open batch" else "Ver lote",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Fila 1 de Bento: Estudio de Fotos + Estudio PDF
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StudioBentoTile(
                        title = if (isEn) "Touch Photo Studio" else "Estudio de Fotos",
                        subtitle = if (isEn) "8-handle crop, 4-corner scanner, 60 FPS adjust, filters & redact" else "Recorte táctil, escáner 4 esquinas, ajustes 60 FPS, filtros y censura",
                        badge = "IMG",
                        icon = Icons.Default.AutoFixHigh,
                        accentColor = CategoryImageColor,
                        onClick = {
                            launchStudioOrPickFiles(
                                target = StudioLaunchTarget.PHOTO_STUDIO,
                                mimeFilters = arrayOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif", "image/avif", "image/bmp"),
                                existingMatchPredicate = { it.format.category.name == "IMAGE" }
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )

                    StudioBentoTile(
                        title = if (isEn) "Touch PDF Studio" else "Estudio PDF y Firma",
                        subtitle = if (isEn) "Reorder pages, handwritten signature, highlighter & redact" else "Reordenar páginas, firma manuscrita, resaltador y censura",
                        badge = "PDF",
                        icon = Icons.Default.PictureAsPdf,
                        accentColor = CategoryDocumentColor,
                        onClick = {
                            launchStudioOrPickFiles(
                                target = StudioLaunchTarget.PDF_STUDIO,
                                mimeFilters = arrayOf("application/pdf"),
                                existingMatchPredicate = { it.format == SupportedFormat.PDF }
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }

            // Fila 2 de Bento: Línea de Tiempo Video/Audio + Recorte de Encuadre de Video
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StudioBentoTile(
                        title = if (isEn) "Timeline Trimmer" else "Línea de Tiempo",
                        subtitle = if (isEn) "Live video player, frame strip, PCM waveform & exact frame capture" else "Reproductor en vivo, fotogramas, onda PCM y captura de frame",
                        badge = "TRIM",
                        icon = Icons.Default.ContentCut,
                        accentColor = CategoryMediaColor,
                        onClick = {
                            launchStudioOrPickFiles(
                                target = StudioLaunchTarget.MEDIA_TRIMMER,
                                mimeFilters = arrayOf("video/mp4", "video/x-matroska", "video/quicktime", "video/webm", "audio/mpeg", "audio/wav", "audio/mp4", "audio/flac", "audio/ogg"),
                                existingMatchPredicate = { it.format.isVideo || it.format.isAudio }
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )

                    StudioBentoTile(
                        title = if (isEn) "Video Frame Crop" else "Encuadre de Video",
                        subtitle = if (isEn) "Live video preview with 8-handle touch spatial crop" else "Video en vivo con recorte espacial táctil y proporciones 1:1, 9:16, 16:9",
                        badge = "CROP",
                        icon = Icons.Default.Crop,
                        accentColor = Color(0xFFEC4899),
                        onClick = {
                            launchStudioOrPickFiles(
                                target = StudioLaunchTarget.VIDEO_CROP,
                                mimeFilters = arrayOf("video/mp4", "video/x-matroska", "video/quicktime", "video/webm"),
                                existingMatchPredicate = { it.format.isVideo }
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }

            // Fila 3 de Bento: Explorador RAR/ZIP + Renombrador Masivo
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StudioBentoTile(
                        title = if (isEn) "RAR / ZIP Explorer" else "Explorador RAR / ZIP",
                        subtitle = if (isEn) "Browse .rar/.rar5/.7z/.zip, selective extract & 1-step repack" else "Explorar .rar/.rar5/.7z/.zip, extracción selectiva y re-empaquetado",
                        badge = "RAR5",
                        icon = Icons.Default.FolderZip,
                        accentColor = CategoryArchiveColor,
                        onClick = {
                            launchStudioOrPickFiles(
                                target = StudioLaunchTarget.ARCHIVE_EXPLORER,
                                mimeFilters = arrayOf("application/vnd.rar", "application/x-rar-compressed", "application/zip", "application/x-7z-compressed", "application/x-tar", "application/gzip", "application/x-xz", "application/x-bzip2", "application/octet-stream"),
                                existingMatchPredicate = {
                                    it.format in setOf(
                                        SupportedFormat.RAR,
                                        SupportedFormat.CBR,
                                        SupportedFormat.ZIP,
                                        SupportedFormat.CBZ,
                                        SupportedFormat.SEVEN_Z,
                                        SupportedFormat.TAR,
                                        SupportedFormat.TAR_GZ,
                                        SupportedFormat.TAR_XZ,
                                        SupportedFormat.TAR_BZ2
                                    )
                                }
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )

                    StudioBentoTile(
                        title = if (isEn) "Batch Renamer" else "Renombrador Masivo",
                        subtitle = if (isEn) "Rename files with templates, find/replace, regex & case modes" else "Renombra lotes con plantillas, buscar/reemplazar, regex y vista previa",
                        badge = "BATCH",
                        icon = Icons.Default.DriveFileRenameOutline,
                        accentColor = Color(0xFF0D9488),
                        onClick = {
                            launchStudioOrPickFiles(
                                target = StudioLaunchTarget.BATCH_RENAMER,
                                mimeFilters = arrayOf("*/*"),
                                existingMatchPredicate = { true }
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun StudioBentoTile(
    title: String,
    subtitle: String,
    badge: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.30f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.14f),
                            Color.Transparent
                        )
                    )
                )
                .padding(15.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
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
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = accentColor.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = accentColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
