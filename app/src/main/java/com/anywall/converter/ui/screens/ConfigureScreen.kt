package com.anywall.converter.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anywall.converter.core.LocalThumbnailLoader
import com.anywall.converter.core.ScopedStorageManager
import com.anywall.converter.core.UriFileResolver
import com.anywall.converter.data.engines.ArchiveEngineImpl
import com.anywall.converter.data.engines.ForensicMetadataHelper
import com.anywall.converter.domain.model.ArchiveCompressionLevel
import com.anywall.converter.domain.model.AudioBitrateKbps
import com.anywall.converter.domain.model.AudioMetadataSpec
import com.anywall.converter.domain.model.ConversionPreset
import com.anywall.converter.domain.model.FormatCategory
import com.anywall.converter.domain.model.ImageBatchLayoutMode
import com.anywall.converter.domain.model.ImageEditSpec
import com.anywall.converter.domain.model.InputFileItem
import com.anywall.converter.domain.model.LineEndingOption
import com.anywall.converter.domain.model.PdfActionMode
import com.anywall.converter.domain.model.PdfPageEditItem
import com.anywall.converter.domain.model.PdfPageOrientation
import com.anywall.converter.domain.model.PdfPageSize
import com.anywall.converter.domain.model.SecondaryPipelineAction
import com.anywall.converter.domain.model.SupportedFormat
import com.anywall.converter.domain.model.TextEncodingOption
import com.anywall.converter.domain.model.VideoCropSpec
import com.anywall.converter.domain.model.VideoPlaybackSpeed
import com.anywall.converter.domain.model.VideoResolution
import com.anywall.converter.ui.components.ArchivePreviewDialog
import com.anywall.converter.ui.components.BatchRenamerDialog
import com.anywall.converter.ui.components.ConversionProgressCard
import com.anywall.converter.ui.components.FormatChipSelector
import com.anywall.converter.ui.components.ImageVisualEditorDialog
import com.anywall.converter.ui.components.InputFileCard
import com.anywall.converter.ui.components.MediaTimelineTrimmerDialog
import com.anywall.converter.ui.components.OfflineToolsSheetDialog
import com.anywall.converter.ui.components.PdfVisualEditorDialog
import com.anywall.converter.ui.components.QuickPeekDialog
import com.anywall.converter.ui.components.ShareQuickActionType
import com.anywall.converter.ui.components.SignaturePadDialog
import com.anywall.converter.ui.components.SmartShareSheetDialog
import com.anywall.converter.ui.components.VideoCropDialog
import com.anywall.converter.ui.theme.CategoryArchiveColor
import com.anywall.converter.ui.theme.CategoryDocumentColor
import com.anywall.converter.ui.theme.CategoryImageColor
import com.anywall.converter.ui.theme.CategoryMediaColor
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * `ConfigureScreen` rediseñada en v0.5 con un flujo limpio de 3 pasos visibles:
 * - **Paso 1 — Archivos en Cola:** Drag & Drop táctil, Quick Peek, orden rápido, renombrador masivo y deduplicación.
 * - **Estudios Táctiles Contextuales Destacados:** aparecen tarjetas de acceso directo solo para los formatos presentes en el lote.
 * - **Paso 2 — Formato Destino y Ajuste Esencial:** selector segmentado de formato, estimación de tamaño en vivo y control primario.
 * - **Acordeones Colapsables de "Opciones Avanzadas":** cerrados por defecto para mantener la pantalla limpia, con contador de ajustes activos.
 * - **Paso 3 — Barra Inferior Fija (*Sticky Bottom CTA*):** botón principal de conversión siempre accesible sin hacer scroll interminable.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConfigureScreen(
    state: ConfigureUiState,
    presets: List<ConversionPreset>,
    thumbnailLoader: LocalThumbnailLoader?,
    uriResolver: UriFileResolver,
    storageManager: ScopedStorageManager,
    forensicHelper: ForensicMetadataHelper,
    archiveEngine: ArchiveEngineImpl,
    onCreateCameraUri: () -> Uri,
    onAddMoreFiles: (List<Uri>) -> Unit,
    onAddFolderTree: (Uri) -> Unit,
    onMoveFileUp: (Int) -> Unit,
    onMoveFileDown: (Int) -> Unit,
    onMoveFile: (Int, Int) -> Unit = { _, _ -> },
    onReplaceInputFilesList: (List<InputFileItem>) -> Unit = {},
    onDeduplicateQueue: ((Int) -> Unit) -> Unit = {},
    onDismissSmartShareSheet: () -> Unit = {},
    onSortByName: () -> Unit,
    onSortBySize: () -> Unit,
    onReverseOrder: () -> Unit,
    onApplyPreset: (ConversionPreset) -> Unit,
    onSaveNewPreset: (String, String) -> Unit,
    onApplyVisualPdfEdits: (List<PdfPageEditItem>) -> Unit,
    onClearVisualPdfEdits: () -> Unit,
    onApplyVisualImageEdits: (ImageEditSpec) -> Unit,
    onClearVisualImageEdits: () -> Unit,
    onRemoveFile: (Uri) -> Unit,
    onClearAllFiles: () -> Unit,
    onSelectTargetFormat: (SupportedFormat) -> Unit,
    onUpdateQuality: (Int) -> Unit,
    onUpdateMaxDimension: (Int?) -> Unit,
    onUpdateImageRotation: (Int) -> Unit,
    onUpdateStripExif: (Boolean) -> Unit,
    onUpdateTargetImageMaxKb: (String) -> Unit,
    onUpdateImageBatchLayoutMode: (ImageBatchLayoutMode) -> Unit,
    onUpdateAnimatedGifFps: (Int) -> Unit,
    onUpdateVideoResolution: (VideoResolution) -> Unit,
    onUpdateMuteVideoAudio: (Boolean) -> Unit,
    onUpdateTrimStartText: (String) -> Unit,
    onUpdateTrimEndText: (String) -> Unit,
    onUpdateTrimRangeMillis: (Long, Long) -> Unit = { _, _ -> },
    onUpdateVideoCropSpec: (VideoCropSpec) -> Unit = {},
    onUpdateVideoPlaybackSpeed: (Float) -> Unit,
    onUpdateVideoRotation: (Int) -> Unit,
    onUpdateVideoFlipHorizontal: (Boolean) -> Unit,
    onUpdateTargetVideoMaxMb: (String) -> Unit,
    onUpdateMergeMediaSequence: (Boolean) -> Unit,
    onUpdateAudioBitrateKbps: (Int) -> Unit,
    onUpdateAudioFadeSeconds: (Int, Int) -> Unit,
    onUpdateExportAsRingtone: (Boolean) -> Unit,
    onUpdateAudioNormalizeAutoGain: (Boolean) -> Unit = {},
    onUpdateAudioMetadataSpec: (AudioMetadataSpec) -> Unit = {},
    onUpdateCustomOutputName: (String) -> Unit,
    onUpdateMergeSinglePdf: (Boolean) -> Unit,
    onUpdatePdfActionMode: (PdfActionMode) -> Unit,
    onUpdatePageRangeSpec: (String) -> Unit,
    onUpdatePdfSplitChunkPages: (Int) -> Unit,
    onUpdatePdfPassword: (String) -> Unit,
    onUpdatePdfPageSize: (PdfPageSize) -> Unit,
    onUpdatePdfPageOrientation: (PdfPageOrientation) -> Unit,
    onUpdatePdfWatermarkText: (String) -> Unit,
    onUpdateAddPdfPageNumbers: (Boolean) -> Unit,
    onUpdatePdfSignaturePngPath: (String) -> Unit,
    onUpdatePdfStampQrContent: (String) -> Unit,
    onUpdateArchiveCompressionLevel: (ArchiveCompressionLevel) -> Unit,
    onUpdateSelectedArchiveEntries: (Set<String>) -> Unit,
    onUpdateArchivePassword: (String) -> Unit = {},
    onUpdateFileSplitPartSizeMb: (Int) -> Unit,
    onUpdatePreserveSubfolderStructure: (Boolean) -> Unit,
    onUpdatePrettyPrintStructuredData: (Boolean) -> Unit,
    onUpdateTextEncoding: (TextEncodingOption) -> Unit,
    onUpdateLineEndingOption: (LineEndingOption) -> Unit,
    onUpdateVaultPassword: (String) -> Unit,
    onUpdateQrCustomText: (String) -> Unit,
    onUpdateSecondaryPipelineAction: (SecondaryPipelineAction) -> Unit,
    onStartConversion: () -> Unit,
    onCancelConversion: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRunning = state.activeJob.isRunning
    var showVisualPdfEditor by remember { mutableStateOf(false) }
    var showSignaturePad by remember { mutableStateOf(false) }
    var showVisualImageEditor by remember { mutableStateOf(false) }
    var showArchivePreview by remember { mutableStateOf(false) }
    var showToolsDialog by remember { mutableStateOf(false) }
    var toolsInitialTab by remember { mutableIntStateOf(0) }
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var newPresetName by remember { mutableStateOf("") }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    var quickPeekItem by remember { mutableStateOf<InputFileItem?>(null) }
    var showBatchRenamer by remember { mutableStateOf(false) }
    var showMediaTimelineTrimmer by remember { mutableStateOf(false) }
    var showVideoCropDialog by remember { mutableStateOf(false) }
    var duplicateRemovedMessage by remember { mutableStateOf<String?>(null) }

    // Estado de acordeones colapsables de opciones avanzadas (cerrados por defecto en v0.5)
    var expandFormatAdvanced by remember { mutableStateOf(false) }
    var expandOutputAndPipeline by remember { mutableStateOf(false) }
    var expandAudioId3Tags by remember { mutableStateOf(false) }

    // Estado Drag & Drop de la cola de archivos
    var draggedIndex by remember { mutableIntStateOf(-1) }
    var draggedAccumulatedY by remember { mutableFloatStateOf(0f) }

    val addFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onAddMoreFiles(uris)
        }
    }

    val addFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            onAddFolderTree(treeUri)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            onAddMoreFiles(listOf(uri))
        }
    }

    val coverArtPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { coverUri ->
        if (coverUri != null) {
            val currentMeta = state.options.audioMetadataSpec
            onUpdateAudioMetadataSpec(currentMeta.copy(coverArtPathOrUri = coverUri.toString()))
        }
    }

    // ==================== DIÁLOGOS Y ESTUDIOS TÁCTILES ====================

    if (state.showSmartShareSheet && state.inputFiles.isNotEmpty()) {
        SmartShareSheetDialog(
            sharedItems = state.inputFiles,
            onExecuteQuickAction = { action ->
                onDismissSmartShareSheet()
                when (action) {
                    ShareQuickActionType.CONVERT_WEBP_LIGHT -> {
                        onSelectTargetFormat(SupportedFormat.WEBP)
                        onUpdateQuality(80)
                        onUpdateStripExif(true)
                        onStartConversion()
                    }
                    ShareQuickActionType.CONVERT_JPG_CLEAN_EXIF -> {
                        onSelectTargetFormat(SupportedFormat.JPG)
                        onUpdateQuality(88)
                        onUpdateStripExif(true)
                        onStartConversion()
                    }
                    ShareQuickActionType.MERGE_TO_PDF -> {
                        onSelectTargetFormat(SupportedFormat.PDF)
                        onUpdateMergeSinglePdf(true)
                        onStartConversion()
                    }
                    ShareQuickActionType.OPEN_PHOTO_STUDIO -> {
                        showVisualImageEditor = true
                    }
                    ShareQuickActionType.OPEN_PDF_EDITOR -> {
                        showVisualPdfEditor = true
                    }
                    ShareQuickActionType.COMPRESS_PDF -> {
                        onSelectTargetFormat(SupportedFormat.PDF)
                        onUpdatePdfActionMode(PdfActionMode.OPTIMIZE_COMPRESS)
                        onUpdateQuality(65)
                        onStartConversion()
                    }
                    ShareQuickActionType.PDF_TO_IMAGES -> {
                        onSelectTargetFormat(SupportedFormat.PNG)
                        onStartConversion()
                    }
                    ShareQuickActionType.EXTRACT_AUDIO_MP3 -> {
                        onSelectTargetFormat(SupportedFormat.MP3)
                        onStartConversion()
                    }
                    ShareQuickActionType.COMPRESS_VIDEO_720P -> {
                        onSelectTargetFormat(SupportedFormat.MP4)
                        onUpdateVideoResolution(VideoResolution.HD_720P)
                        onStartConversion()
                    }
                    ShareQuickActionType.VIDEO_TO_GIF -> {
                        onSelectTargetFormat(SupportedFormat.GIF)
                        onStartConversion()
                    }
                    ShareQuickActionType.EXTRACT_ARCHIVE_NOW -> {
                        onSelectTargetFormat(SupportedFormat.EXTRACT_ARCHIVE)
                        onStartConversion()
                    }
                    ShareQuickActionType.REPACK_TO_ZIP -> {
                        onSelectTargetFormat(SupportedFormat.ZIP)
                        onStartConversion()
                    }
                    ShareQuickActionType.COMIC_TO_PDF -> {
                        onSelectTargetFormat(SupportedFormat.PDF)
                        onStartConversion()
                    }
                    ShareQuickActionType.ENCRYPT_AES256_VAULT -> {
                        onSelectTargetFormat(SupportedFormat.ANYWALL_VAULT)
                    }
                    ShareQuickActionType.OPEN_FULL_CONFIGURE -> Unit
                }
            },
            onDismissToConfigure = onDismissSmartShareSheet
        )
    }

    val peekTarget = quickPeekItem
    if (peekTarget != null) {
        QuickPeekDialog(
            item = peekTarget,
            uriResolver = uriResolver,
            thumbnailLoader = thumbnailLoader,
            archiveEngine = archiveEngine,
            onDismiss = { quickPeekItem = null }
        )
    }

    if (showBatchRenamer && state.inputFiles.isNotEmpty()) {
        BatchRenamerDialog(
            inputFiles = state.inputFiles,
            storageManager = storageManager,
            onApplyRenamedItemsToQueue = { renamedList ->
                onReplaceInputFilesList(renamedList)
                showBatchRenamer = false
            },
            onDismiss = { showBatchRenamer = false }
        )
    }

    val firstMediaFile = state.inputFiles.firstOrNull {
        it.format.isVideo || it.format.isAudio
    }
    if (showMediaTimelineTrimmer && firstMediaFile != null) {
        MediaTimelineTrimmerDialog(
            mediaFile = firstMediaFile,
            initialStartMs = state.options.effectiveTrimStartMillis,
            initialEndMs = state.options.effectiveTrimEndMillis ?: 0L,
            onApplyTrim = { startMs, endMs ->
                onUpdateTrimRangeMillis(startMs, endMs)
                showMediaTimelineTrimmer = false
            },
            onDismiss = { showMediaTimelineTrimmer = false }
        )
    }

    val firstVideoFile = state.inputFiles.firstOrNull { it.format.isVideo }
    if (showVideoCropDialog && firstVideoFile != null) {
        VideoCropDialog(
            videoFile = firstVideoFile,
            initialCropSpec = state.options.videoCropSpec,
            sampleTimeMs = state.options.effectiveTrimStartMillis,
            onApplyCrop = { spec ->
                onUpdateVideoCropSpec(spec)
                showVideoCropDialog = false
            },
            onDismiss = { showVideoCropDialog = false }
        )
    }

    if (showVisualPdfEditor) {
        val pdfFiles = state.inputFiles.filter { it.format == SupportedFormat.PDF }
        PdfVisualEditorDialog(
            pdfInputFiles = pdfFiles,
            initialEdits = state.visualPdfEditsList,
            thumbnailLoader = thumbnailLoader,
            onOpenSignaturePad = { showSignaturePad = true },
            onApplyEdits = { edits ->
                onApplyVisualPdfEdits(edits)
                showVisualPdfEditor = false
            },
            onResetEdits = {
                onClearVisualPdfEdits()
                showVisualPdfEditor = false
            },
            onDismiss = { showVisualPdfEditor = false }
        )
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

    if (showVisualImageEditor) {
        val imageFiles = state.inputFiles.filter { it.format.category == FormatCategory.IMAGE }
        ImageVisualEditorDialog(
            imageInputs = imageFiles,
            initialEditSpec = state.options.imageEditSpec,
            onApplyEdits = { spec ->
                onApplyVisualImageEdits(spec)
                showVisualImageEditor = false
            },
            onDismiss = { showVisualImageEditor = false }
        )
    }

    val firstArchive = state.inputFiles.firstOrNull {
        it.format.category == FormatCategory.ARCHIVE && it.format != SupportedFormat.FILE_PARTS
    }
    if (showArchivePreview && firstArchive != null) {
        ArchivePreviewDialog(
            archiveItem = firstArchive,
            archiveEngine = archiveEngine,
            initialSelectedEntries = state.options.selectedArchiveEntries,
            initialPassword = state.options.archivePassword,
            onApplySelection = { entries ->
                onUpdateSelectedArchiveEntries(entries)
                showArchivePreview = false
            },
            onUpdateArchivePassword = onUpdateArchivePassword,
            onRepackInOneStep = { targetRepackFormat, entries, password ->
                onUpdateSelectedArchiveEntries(entries)
                onUpdateArchivePassword(password)
                onSelectTargetFormat(targetRepackFormat)
                showArchivePreview = false
                onStartConversion()
            },
            onDismiss = { showArchivePreview = false }
        )
    }

    if (showToolsDialog) {
        OfflineToolsSheetDialog(
            initialTabIndex = toolsInitialTab,
            loadedInputs = state.inputFiles,
            currentImageEditSpec = state.options.imageEditSpec,
            currentQualityPercent = state.options.clampedQuality,
            uriResolver = uriResolver,
            storageManager = storageManager,
            forensicHelper = forensicHelper,
            onDeduplicateQueue = onReplaceInputFilesList,
            onDismiss = { showToolsDialog = false }
        )
    }

    if (showSavePresetDialog) {
        AlertDialog(
            onDismissRequest = { showSavePresetDialog = false },
            title = { Text("Guardar Perfil Rápido (Preset)", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Guarda el formato destino (${state.selectedTargetFormat?.label ?: ""}) y las opciones actuales para reutilizarlos con un toque:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = newPresetName,
                        onValueChange = { newPresetName = it },
                        singleLine = true,
                        label = { Text("Nombre del perfil (ej. WebP 80% sin EXIF)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPresetName.isNotBlank()) {
                            onSaveNewPreset(
                                newPresetName.trim(),
                                "Destino: ${state.selectedTargetFormat?.label ?: ""}"
                            )
                            newPresetName = ""
                            showSavePresetDialog = false
                        }
                    }
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSavePresetDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // ==================== ESTRUCTURA PRINCIPAL v0.5 ====================

    val allVaultsInQueue = state.inputFiles.isNotEmpty() && state.inputFiles.all { it.format == SupportedFormat.ANYWALL_VAULT }
    val hasImagesInQueue = state.inputFiles.any { it.format.category == FormatCategory.IMAGE }
    val hasPdfsInQueue = state.inputFiles.any { it.format == SupportedFormat.PDF }
    val hasVideosInQueue = state.inputFiles.any { it.format.isVideo }
    val hasMediaInQueue = state.inputFiles.any { it.format.isVideo || it.format.isAudio }
    val hasArchivesInQueue = firstArchive != null

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Configurar Lote",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                        if (state.inputFiles.isNotEmpty()) {
                            Text(
                                text = "${state.inputFiles.size} archivo(s) • ${state.formattedTotalInputSize}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { addFilesLauncher.launch(arrayOf("*/*")) },
                        enabled = !isRunning
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Añadir archivos"
                        )
                    }
                    IconButton(
                        onClick = { addFolderLauncher.launch(null) },
                        enabled = !isRunning
                    ) {
                        Icon(
                            imageVector = Icons.Default.CreateNewFolder,
                            contentDescription = "Añadir carpeta"
                        )
                    }
                    IconButton(
                        onClick = {
                            val uri = onCreateCameraUri()
                            pendingCameraUri = uri
                            cameraLauncher.launch(uri)
                        },
                        enabled = !isRunning
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Capturar foto"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            if (state.inputFiles.isNotEmpty() && !isRunning) {
                StickyConversionBottomBar(
                    inputCount = state.inputFiles.size,
                    selectedFormat = state.selectedTargetFormat,
                    isDecryptingVault = allVaultsInQueue,
                    estimateRange = state.outputSizeEstimate?.formattedRange,
                    estimateSavingsBadge = state.outputSizeEstimate?.formattedSavingsBadge,
                    onSavePreset = { showSavePresetDialog = true },
                    onStartConversion = onStartConversion
                )
            }
        }
    ) { innerPadding ->
        if (state.inputFiles.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "No hay archivos en el lote actual",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { addFilesLauncher.launch(arrayOf("*/*")) },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Seleccionar Archivos")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Progreso activo si se está ejecutando una tarea
                if (state.activeJob.isRunning) {
                    item {
                        ConversionProgressCard(
                            jobState = state.activeJob,
                            onCancel = onCancelConversion,
                            onOpenHistory = onNavigateToHistory
                        )
                    }
                }

                // ==================== PASO 1: COLA DE ARCHIVOS ====================
                item {
                    StepHeaderRow(
                        stepNumber = "1",
                        title = "Archivos en Cola (${state.inputFiles.size})",
                        trailingContent = {
                            TextButton(
                                onClick = onClearAllFiles,
                                enabled = !isRunning
                            ) {
                                Text("Vaciar")
                            }
                        }
                    )
                }

                // Barra horizontal compacta de herramientas de cola
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (state.inputFiles.size > 1) {
                            AssistChip(
                                onClick = onSortByName,
                                enabled = !isRunning,
                                shape = RoundedCornerShape(10.dp),
                                label = { Text("A–Z") },
                                leadingIcon = {
                                    Icon(Icons.Default.SortByAlpha, contentDescription = null, modifier = Modifier.size(15.dp))
                                }
                            )
                            AssistChip(
                                onClick = onSortBySize,
                                enabled = !isRunning,
                                shape = RoundedCornerShape(10.dp),
                                label = { Text("Tamaño") },
                                leadingIcon = {
                                    Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(15.dp))
                                }
                            )
                            AssistChip(
                                onClick = onReverseOrder,
                                enabled = !isRunning,
                                shape = RoundedCornerShape(10.dp),
                                label = { Text("Invertir") },
                                leadingIcon = {
                                    Icon(Icons.Default.SwapVert, contentDescription = null, modifier = Modifier.size(15.dp))
                                }
                            )
                            AssistChip(
                                onClick = {
                                    onDeduplicateQueue { removedCount ->
                                        duplicateRemovedMessage = if (removedCount > 0) {
                                            "Se eliminaron $removedCount duplicado(s) por SHA-256."
                                        } else {
                                            "No se encontraron archivos duplicados."
                                        }
                                    }
                                },
                                enabled = !isRunning,
                                shape = RoundedCornerShape(10.dp),
                                label = { Text("Sin duplicados") },
                                leadingIcon = {
                                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(15.dp))
                                }
                            )
                        }
                        AssistChip(
                            onClick = { showBatchRenamer = true },
                            enabled = !isRunning,
                            shape = RoundedCornerShape(10.dp),
                            label = { Text("Renombrar lote") },
                            leadingIcon = {
                                Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null, modifier = Modifier.size(15.dp))
                            }
                        )
                    }
                }

                if (duplicateRemovedMessage != null) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = duplicateRemovedMessage.orEmpty(),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { duplicateRemovedMessage = null }) {
                                    Text("OK")
                                }
                            }
                        }
                    }
                }

                // Lista de tarjetas de archivos con Drag & Drop
                itemsIndexed(
                    items = state.inputFiles,
                    key = { index, item -> "${item.uri}_$index" }
                ) { index, fileItem ->
                    InputFileCard(
                        item = fileItem,
                        index = index,
                        totalCount = state.inputFiles.size,
                        thumbnailLoader = thumbnailLoader,
                        onMoveUp = { onMoveFileUp(index) },
                        onMoveDown = { onMoveFileDown(index) },
                        onRemove = { onRemoveFile(fileItem.uri) },
                        onQuickPeek = { quickPeekItem = fileItem },
                        onDragDeltaY = { dy ->
                            if (draggedIndex == -1) {
                                draggedIndex = index
                                draggedAccumulatedY = 0f
                            }
                            draggedAccumulatedY += dy
                            val thresholdPx = 95f
                            val curIdx = if (draggedIndex in state.inputFiles.indices) draggedIndex else index
                            if (draggedAccumulatedY > thresholdPx && curIdx < state.inputFiles.lastIndex) {
                                onMoveFile(curIdx, curIdx + 1)
                                draggedIndex = curIdx + 1
                                draggedAccumulatedY = 0f
                            } else if (draggedAccumulatedY < -thresholdPx && curIdx > 0) {
                                onMoveFile(curIdx, curIdx - 1)
                                draggedIndex = curIdx - 1
                                draggedAccumulatedY = 0f
                            }
                        },
                        onDragEnd = {
                            draggedIndex = -1
                            draggedAccumulatedY = 0f
                        },
                        isBeingDragged = (draggedIndex == index),
                        enabled = !isRunning
                    )
                }

                // ==================== ESTUDIOS TÁCTILES CONTEXTUALES DESTACADOS ====================
                if (hasImagesInQueue || hasPdfsInQueue || hasMediaInQueue || hasArchivesInQueue) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Estudios Táctiles para tu Lote",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            if (hasImagesInQueue) {
                                val hasEdits = state.options.hasVisualImageEdits
                                ContextualStudioBannerCard(
                                    title = if (hasEdits) "✓ Estudio de Fotos Táctil Aplicado" else "Abrir Estudio de Fotos Táctil",
                                    subtitle = "Recorte 8 tiradores, escáner 4 esquinas, ajustes 60 FPS, filtros, censura y marca de agua",
                                    icon = Icons.Default.AutoFixHigh,
                                    accentColor = CategoryImageColor,
                                    isActive = hasEdits,
                                    onClick = { showVisualImageEditor = true },
                                    onReset = if (hasEdits) onClearVisualImageEdits else null,
                                    enabled = !isRunning
                                )
                            }

                            if (hasPdfsInQueue) {
                                val hasPdfEdits = state.options.hasVisualPdfEdits
                                ContextualStudioBannerCard(
                                    title = if (hasPdfEdits) {
                                        "✓ Estudio PDF Aplicado (${state.activeVisualPdfPagesCount} págs.)"
                                    } else {
                                        "Abrir Estudio PDF, Firma y Anotaciones"
                                    },
                                    subtitle = "Reordenar páginas, firma manuscrita, resaltador, sello de fecha, censura y escáner",
                                    icon = Icons.Default.EditNote,
                                    accentColor = CategoryDocumentColor,
                                    isActive = hasPdfEdits,
                                    onClick = { showVisualPdfEditor = true },
                                    onReset = if (hasPdfEdits) onClearVisualPdfEdits else null,
                                    enabled = !isRunning
                                )
                            }

                            if (hasMediaInQueue) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val hasTrim = state.options.trimStartMillis > 0L || state.options.trimEndMillis > 0L
                                    ContextualStudioBannerCard(
                                        title = if (hasTrim) "✓ Tramo Recortado" else "Línea de Tiempo (Trim)",
                                        subtitle = "Fotogramas / onda PCM",
                                        icon = Icons.Default.ContentCut,
                                        accentColor = CategoryMediaColor,
                                        isActive = hasTrim,
                                        onClick = { showMediaTimelineTrimmer = true },
                                        onReset = if (hasTrim) ({ onUpdateTrimRangeMillis(0L, 0L) }) else null,
                                        enabled = !isRunning,
                                        modifier = Modifier.weight(1f)
                                    )

                                    if (hasVideosInQueue) {
                                        val hasCrop = state.options.videoCropSpec.hasCrop
                                        ContextualStudioBannerCard(
                                            title = if (hasCrop) "✓ Encuadre Recortado" else "Encuadre (Crop)",
                                            subtitle = "Proporciones 1:1, 9:16, 16:9",
                                            icon = Icons.Default.Crop,
                                            accentColor = Color(0xFFEC4899),
                                            isActive = hasCrop,
                                            onClick = { showVideoCropDialog = true },
                                            onReset = if (hasCrop) ({ onUpdateVideoCropSpec(VideoCropSpec()) }) else null,
                                            enabled = !isRunning,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }

                            if (hasArchivesInQueue) {
                                val selCount = state.options.selectedArchiveEntries.size
                                ContextualStudioBannerCard(
                                    title = if (selCount > 0) {
                                        "✓ Explorador ($selCount archivo(s) seleccionados)"
                                    } else {
                                        "Explorar Comprimido y Re-empaquetar en 1 Paso"
                                    },
                                    subtitle = "Inspeccionar árbol interno .rar/.rar5/.zip/.7z, contraseñas y extracción selectiva",
                                    icon = Icons.Default.FolderZip,
                                    accentColor = CategoryArchiveColor,
                                    isActive = selCount > 0,
                                    onClick = { showArchivePreview = true },
                                    onReset = if (selCount > 0) ({ onUpdateSelectedArchiveEntries(emptySet()) }) else null,
                                    enabled = !isRunning
                                )
                            }
                        }
                    }
                }

                // ==================== PASO 2: FORMATO DESTINO Y CONTROL ESENCIAL ====================
                item {
                    StepHeaderRow(
                        stepNumber = "2",
                        title = "Formato Destino",
                        trailingContent = {
                            val estimate = state.outputSizeEstimate
                            if (estimate != null) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = "${estimate.formattedRange} (${estimate.formattedSavingsBadge})",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    )
                }



                // Tarjeta limpia de Selección de Formato + Control Primario Esencial
                item {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            FormatChipSelector(
                                availableFormats = state.availableTargetFormats,
                                selectedFormat = state.selectedTargetFormat,
                                inputCount = state.inputFiles.size,
                                mergeIntoSinglePdf = state.options.mergeIntoSinglePdf,
                                onSelectFormat = onSelectTargetFormat,
                                enabled = !isRunning
                            )

                            // Control Primario Esencial (solo el ajuste más importante del formato seleccionado)
                            if (state.showImageQualityOptions) {
                                HorizontalDivider()
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Calidad de compresión",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "${state.options.clampedQuality}%",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Slider(
                                        value = state.options.clampedQuality.toFloat(),
                                        onValueChange = { onUpdateQuality(it.roundToInt()) },
                                        valueRange = 10f..100f,
                                        enabled = !isRunning
                                    )
                                }
                            }

                            if (state.showImageTransformOptions) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Limpiar metadatos EXIF y GPS",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Elimina ubicación GPS y datos de cámara",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = state.options.stripExifMetadata,
                                        onCheckedChange = onUpdateStripExif,
                                        enabled = !isRunning
                                    )
                                }
                            }

                            if (state.showMultiFileMergePdfToggle) {
                                HorizontalDivider()
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Unir todos en un único PDF",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Combina los ${state.inputFiles.size} archivos siguiendo el orden actual",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = state.options.mergeIntoSinglePdf,
                                        onCheckedChange = onUpdateMergeSinglePdf,
                                        enabled = !isRunning
                                    )
                                }
                            }

                            if (state.showSinglePdfActionOptions) {
                                HorizontalDivider()
                                Text(
                                    text = "Modo de operación PDF",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    PdfActionMode.entries.forEach { mode ->
                                        FilterChip(
                                            selected = state.options.pdfActionMode == mode,
                                            onClick = { onUpdatePdfActionMode(mode) },
                                            enabled = !isRunning,
                                            shape = RoundedCornerShape(10.dp),
                                            label = { Text(mode.labelEs) }
                                        )
                                    }
                                }

                                if (state.options.pdfActionMode == PdfActionMode.SPLIT_RANGE) {
                                    OutlinedTextField(
                                        value = state.options.pageRangeSpec,
                                        onValueChange = onUpdatePageRangeSpec,
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = !isRunning,
                                        singleLine = true,
                                        label = { Text("Rango de páginas (ej. 1-3, 5, 8-10)") }
                                    )
                                }

                                if (state.options.pdfActionMode == PdfActionMode.SPLIT_BY_CHUNK) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf(1, 2, 5, 10).forEach { chunk ->
                                            FilterChip(
                                                selected = state.options.pdfSplitChunkPages == chunk,
                                                onClick = { onUpdatePdfSplitChunkPages(chunk) },
                                                enabled = !isRunning,
                                                label = { Text("Cada $chunk pág.") }
                                            )
                                        }
                                    }
                                }
                            }

                            if (state.showPdfPasswordInput) {
                                OutlinedTextField(
                                    value = state.options.pdfPassword,
                                    onValueChange = onUpdatePdfPassword,
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !isRunning,
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    label = {
                                        Text(
                                            if (state.options.pdfActionMode == PdfActionMode.UNLOCK_PASSWORD) {
                                                "Contraseña actual del PDF"
                                            } else {
                                                "Contraseña para cifrar el PDF (opcional)"
                                            }
                                        )
                                    }
                                )
                            }

                            if (state.showVideoOptions) {
                                HorizontalDivider()
                                Text(
                                    text = "Resolución de Video",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    VideoResolution.entries.forEach { res ->
                                        FilterChip(
                                            selected = state.options.videoResolution == res,
                                            onClick = { onUpdateVideoResolution(res) },
                                            enabled = !isRunning,
                                            label = { Text(res.labelEs) }
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Silenciar pista de audio",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Switch(
                                        checked = state.options.muteVideoAudio,
                                        onCheckedChange = onUpdateMuteVideoAudio,
                                        enabled = !isRunning
                                    )
                                }
                            }

                            if (state.showAudioAdvancedOptions) {
                                HorizontalDivider()
                                Text(
                                    text = "Calidad / Bitrate de Audio",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    AudioBitrateKbps.entries.forEach { br ->
                                        FilterChip(
                                            selected = state.options.audioBitrateKbps == br.kbps,
                                            onClick = { onUpdateAudioBitrateKbps(br.kbps) },
                                            enabled = !isRunning,
                                            label = { Text(br.label) }
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Normalizar volumen (Auto-Gain -1.0 dBFS)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Nivela el volumen máximo sin saturación",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = state.options.audioNormalizeAutoGain,
                                        onCheckedChange = onUpdateAudioNormalizeAutoGain,
                                        enabled = !isRunning
                                    )
                                }
                            }

                            if (state.showArchiveLevelOptions) {
                                HorizontalDivider()
                                Text(
                                    text = "Nivel de compresión",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    ArchiveCompressionLevel.entries.forEach { lvl ->
                                        FilterChip(
                                            selected = state.options.archiveCompressionLevel == lvl,
                                            onClick = { onUpdateArchiveCompressionLevel(lvl) },
                                            enabled = !isRunning,
                                            label = { Text(lvl.labelEs) }
                                        )
                                    }
                                }
                            }

                            if (state.inputFiles.any { it.format == SupportedFormat.RAR || it.format == SupportedFormat.CBR }) {
                                OutlinedTextField(
                                    value = state.options.archivePassword,
                                    onValueChange = onUpdateArchivePassword,
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !isRunning,
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    label = { Text("Contraseña del archivo .RAR / .RAR5 (si está cifrado)") }
                                )
                            }

                            if (state.showVaultSecurityOptions) {
                                HorizontalDivider()
                                if (allVaultsInQueue) {
                                    Text(
                                        text = "Modo Desencriptación: se restaurará el archivo original con su nombre y extensión originales en Downloads/AnyWall/",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                OutlinedTextField(
                                    value = state.options.vaultPassword,
                                    onValueChange = onUpdateVaultPassword,
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !isRunning,
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    label = {
                                        Text(
                                            if (allVaultsInQueue) "Contraseña para desencriptar el archivo .anywall"
                                            else "Contraseña para cifrar la Bóveda AES-256-GCM (.anywall)"
                                        )
                                    }
                                )
                            }

                            if (state.showQrGeneratorOptions) {
                                HorizontalDivider()
                                OutlinedTextField(
                                    value = state.options.qrCustomText,
                                    onValueChange = onUpdateQrCustomText,
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !isRunning,
                                    label = { Text("Texto o URL para el código QR (vacío = usar archivo)") }
                                )
                            }
                        }
                    }
                }

                // ==================== ACORDEÓN 1: OPCIONES AVANZADAS DEL FORMATO ====================
                val hasAnyFormatAdvancedSection = state.showImageTransformOptions ||
                    state.showAnimatedGifOptions ||
                    state.showDocumentPageLayoutOptions ||
                    state.showPdfBrandingAndStampOptions ||
                    state.showVideoOptions ||
                    state.showAudioAdvancedOptions ||
                    state.showMediaTrimAndMuteOptions ||
                    state.showArchiveLevelOptions ||
                    state.showFileSplitOptions ||
                    state.showStructuredDataAndTextOptions

                if (hasAnyFormatAdvancedSection) {
                    item {
                        CollapsibleSettingsCard(
                            title = "Opciones Avanzadas del Formato",
                            subtitle = "Dimensiones, collages, marca de agua PDF, velocidad, etiquetas ID3 y codificación",
                            icon = Icons.Default.Tune,
                            expanded = expandFormatAdvanced,
                            onToggleExpand = { expandFormatAdvanced = !expandFormatAdvanced }
                        ) {
                            // A. Opciones avanzadas de Imagen
                            if (state.showImageTransformOptions) {
                                Text(
                                    text = "Redimensionado máximo (px)",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                val dimOptions = listOf(null to "Original", 3840 to "4K", 1920 to "1920px", 1280 to "1280px", 800 to "800px")
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    dimOptions.forEach { (px, label) ->
                                        FilterChip(
                                            selected = state.options.maxDimensionPx == px,
                                            onClick = { onUpdateMaxDimension(px) },
                                            enabled = !isRunning,
                                            label = { Text(label) }
                                        )
                                    }
                                }

                                Text(
                                    text = "Rotación rápida",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(0, 90, 180, 270).forEach { deg ->
                                        FilterChip(
                                            selected = state.options.normalizedRotationDegrees == deg,
                                            onClick = { onUpdateImageRotation(deg) },
                                            enabled = !isRunning,
                                            label = { Text("${deg}°") }
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = state.targetImageMaxKbText,
                                    onValueChange = onUpdateTargetImageMaxKb,
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !isRunning,
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    label = { Text("Peso máximo objetivo en KB (opcional, ej. 200)") }
                                )

                                if (state.inputFiles.size >= 2) {
                                    Text(
                                        text = "Composición por lote (Collage / Tira)",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        ImageBatchLayoutMode.entries.forEach { mode ->
                                            FilterChip(
                                                selected = state.options.imageBatchLayoutMode == mode,
                                                onClick = { onUpdateImageBatchLayoutMode(mode) },
                                                enabled = !isRunning,
                                                label = { Text(mode.labelEs) }
                                            )
                                        }
                                    }
                                }
                            }

                            if (state.showAnimatedGifOptions) {
                                Text(
                                    text = "Fotogramas por segundo (GIF Animado): ${state.options.animatedGifFps} FPS",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Slider(
                                    value = state.options.animatedGifFps.toFloat(),
                                    onValueChange = { onUpdateAnimatedGifFps(it.roundToInt()) },
                                    valueRange = 1f..15f,
                                    steps = 13,
                                    enabled = !isRunning
                                )
                            }

                            // B. Opciones avanzadas de PDF y Documentos
                            if (state.showDocumentPageLayoutOptions) {
                                Text(
                                    text = "Tamaño y orientación de hoja PDF",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PdfPageSize.entries.forEach { size ->
                                        FilterChip(
                                            selected = state.options.pdfPageSize == size,
                                            onClick = { onUpdatePdfPageSize(size) },
                                            enabled = !isRunning,
                                            label = { Text(size.labelEs) }
                                        )
                                    }
                                    PdfPageOrientation.entries.forEach { orient ->
                                        FilterChip(
                                            selected = state.options.pdfPageOrientation == orient,
                                            onClick = { onUpdatePdfPageOrientation(orient) },
                                            enabled = !isRunning,
                                            label = { Text(orient.labelEs) }
                                        )
                                    }
                                }
                            }

                            if (state.showPdfBrandingAndStampOptions) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Numerar páginas automáticamente (\"Página X de Y\")",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Switch(
                                        checked = state.options.addPdfPageNumbers,
                                        onCheckedChange = onUpdateAddPdfPageNumbers,
                                        enabled = !isRunning
                                    )
                                }

                                OutlinedTextField(
                                    value = state.options.pdfWatermarkText,
                                    onValueChange = onUpdatePdfWatermarkText,
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !isRunning,
                                    singleLine = true,
                                    label = { Text("Marca de agua diagonal en PDF (opcional)") }
                                )

                                OutlinedTextField(
                                    value = state.options.pdfStampQrContent,
                                    onValueChange = onUpdatePdfStampQrContent,
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !isRunning,
                                    singleLine = true,
                                    label = { Text("Estampar sello QR en PDF (texto o URL opcional)") }
                                )

                                OutlinedButton(
                                    onClick = { showSignaturePad = true },
                                    enabled = !isRunning,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Draw, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        if (state.options.pdfSignaturePngPath.isNotBlank()) "✓ Firma Manuscrita Lista (Editar)"
                                        else "Dibujar Firma Manuscrita"
                                    )
                                }
                            }

                            // C. Opciones avanzadas de Video y Audio
                            if (state.showVideoOptions) {
                                Text(
                                    text = "Velocidad de reproducción",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    VideoPlaybackSpeed.entries.forEach { spd ->
                                        val isSel = abs(state.options.videoPlaybackSpeedFactor - spd.factor) < 0.05f
                                        FilterChip(
                                            selected = isSel,
                                            onClick = { onUpdateVideoPlaybackSpeed(spd.factor) },
                                            enabled = !isRunning,
                                            label = { Text(spd.label) }
                                        )
                                    }
                                }

                                Text(
                                    text = "Rotación y Espejo de Video",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(0, 90, 180, 270).forEach { deg ->
                                        FilterChip(
                                            selected = state.options.normalizedVideoRotationDegrees == deg,
                                            onClick = { onUpdateVideoRotation(deg) },
                                            enabled = !isRunning,
                                            label = { Text("${deg}°") }
                                        )
                                    }
                                    FilterChip(
                                        selected = state.options.videoFlipHorizontal,
                                        onClick = { onUpdateVideoFlipHorizontal(!state.options.videoFlipHorizontal) },
                                        enabled = !isRunning,
                                        label = { Text("Espejo H") }
                                    )
                                }

                                OutlinedTextField(
                                    value = state.targetVideoMaxMbText,
                                    onValueChange = onUpdateTargetVideoMaxMb,
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !isRunning,
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    label = { Text("Tamaño máximo objetivo de video en MB (ej. 15)") }
                                )
                            }

                            if (state.showAudioAdvancedOptions) {
                                Text(
                                    text = "Fundido de entrada / salida (Fade In / Out)",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(0 to "Sin Fade", 2 to "2 seg", 4 to "4 seg", 6 to "6 seg").forEach { (sec, label) ->
                                        val isSel = state.options.audioFadeInSeconds == sec && state.options.audioFadeOutSeconds == sec
                                        FilterChip(
                                            selected = isSel,
                                            onClick = { onUpdateAudioFadeSeconds(sec, sec) },
                                            enabled = !isRunning,
                                            label = { Text(label) }
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Modo Tono de Llamada (Ringtone máx. 30 s)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Switch(
                                        checked = state.options.exportAsRingtone,
                                        onCheckedChange = onUpdateExportAsRingtone,
                                        enabled = !isRunning
                                    )
                                }

                                // Sub-sección colapsable para Etiquetas ID3v2.3 + Carátula
                                val audioMeta = state.options.audioMetadataSpec
                                OutlinedButton(
                                    onClick = { expandAudioId3Tags = !expandAudioId3Tags },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        if (!audioMeta.isEmpty) "✓ Etiquetas ID3v2.3 y Carátula Configuradas"
                                        else "Editar Etiquetas ID3v2.3 y Carátula (MP3 / M4A)"
                                    )
                                }

                                AnimatedVisibility(visible = expandAudioId3Tags) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedTextField(
                                                value = audioMeta.title,
                                                onValueChange = { onUpdateAudioMetadataSpec(audioMeta.copy(title = it)) },
                                                modifier = Modifier.weight(1f),
                                                enabled = !isRunning,
                                                singleLine = true,
                                                label = { Text("Título") }
                                            )
                                            OutlinedTextField(
                                                value = audioMeta.artist,
                                                onValueChange = { onUpdateAudioMetadataSpec(audioMeta.copy(artist = it)) },
                                                modifier = Modifier.weight(1f),
                                                enabled = !isRunning,
                                                singleLine = true,
                                                label = { Text("Artista") }
                                            )
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedTextField(
                                                value = audioMeta.album,
                                                onValueChange = { onUpdateAudioMetadataSpec(audioMeta.copy(album = it)) },
                                                modifier = Modifier.weight(1f),
                                                enabled = !isRunning,
                                                singleLine = true,
                                                label = { Text("Álbum") }
                                            )
                                            OutlinedTextField(
                                                value = audioMeta.year,
                                                onValueChange = { onUpdateAudioMetadataSpec(audioMeta.copy(year = it.take(8))) },
                                                modifier = Modifier.weight(0.55f),
                                                enabled = !isRunning,
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                label = { Text("Año") }
                                            )
                                            OutlinedTextField(
                                                value = audioMeta.genre,
                                                onValueChange = { onUpdateAudioMetadataSpec(audioMeta.copy(genre = it)) },
                                                modifier = Modifier.weight(0.85f),
                                                enabled = !isRunning,
                                                singleLine = true,
                                                label = { Text("Género") }
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            FilledTonalButton(
                                                onClick = { coverArtPickerLauncher.launch(arrayOf("image/*")) },
                                                enabled = !isRunning
                                            ) {
                                                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    if (audioMeta.coverArtUriString.isNotBlank()) "✓ Carátula APIC Lista"
                                                    else "Elegir Carátula"
                                                )
                                            }
                                            if (!audioMeta.isEmpty) {
                                                TextButton(
                                                    onClick = { onUpdateAudioMetadataSpec(AudioMetadataSpec()) },
                                                    enabled = !isRunning
                                                ) {
                                                    Text("Limpiar")
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (state.showMediaTrimAndMuteOptions) {
                                if (state.inputFiles.size >= 2 && (state.selectedTargetFormat?.isVideo == true || state.selectedTargetFormat?.isAudio == true)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Unir (Concatenar) clips en un solo archivo",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "Une los ${state.inputFiles.size} archivos continuamente",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Switch(
                                            checked = state.options.mergeMediaSequence,
                                            onCheckedChange = onUpdateMergeMediaSequence,
                                            enabled = !isRunning
                                        )
                                    }
                                }
                            }

                            // D. Opciones avanzadas de Comprimidos, Particionado y Datos/Texto
                            if (state.showArchiveLevelOptions) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Conservar jerarquía de subcarpetas",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Switch(
                                        checked = state.options.preserveSubfolderStructure,
                                        onCheckedChange = onUpdatePreserveSubfolderStructure,
                                        enabled = !isRunning
                                    )
                                }
                            }

                            if (state.showFileSplitOptions) {
                                Text(
                                    text = "Tamaño de cada parte (.part001)",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(5, 10, 25, 50, 100).forEach { mb ->
                                        FilterChip(
                                            selected = state.options.fileSplitPartSizeMb == mb,
                                            onClick = { onUpdateFileSplitPartSizeMb(mb) },
                                            enabled = !isRunning,
                                            label = { Text("$mb MB") }
                                        )
                                    }
                                }
                            }

                            if (state.showStructuredDataAndTextOptions) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Formatear y embellecer (Pretty Print)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Switch(
                                        checked = state.options.prettyPrintStructuredData,
                                        onCheckedChange = onUpdatePrettyPrintStructuredData,
                                        enabled = !isRunning
                                    )
                                }

                                Text(
                                    text = "Codificación de caracteres",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    TextEncodingOption.entries.forEach { enc ->
                                        FilterChip(
                                            selected = state.options.textEncoding == enc,
                                            onClick = { onUpdateTextEncoding(enc) },
                                            enabled = !isRunning,
                                            label = { Text(enc.label) }
                                        )
                                    }
                                }

                                Text(
                                    text = "Saltos de línea",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    LineEndingOption.entries.forEach { le ->
                                        FilterChip(
                                            selected = state.options.lineEndingOption == le,
                                            onClick = { onUpdateLineEndingOption(le) },
                                            enabled = !isRunning,
                                            label = { Text(le.label) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ==================== ACORDEÓN 2: NOMBRE DE SALIDA Y PIPELINE DE 2 PASOS ====================
                item {
                    val hasOutputCustomization = state.options.customOutputBaseName.isNotBlank() ||
                        state.options.secondaryPipelineAction != SecondaryPipelineAction.NONE
                    CollapsibleSettingsCard(
                        title = if (hasOutputCustomization) "✓ Nombre de Salida y Pipeline (Activo)" else "Nombre de Salida y Pipeline de 2 Pasos",
                        subtitle = "Plantillas {nombre}_{fecha}_{indice} y empaquetado/cifrado posterior",
                        icon = Icons.Default.FilterNone,
                        expanded = expandOutputAndPipeline,
                        onToggleExpand = { expandOutputAndPipeline = !expandOutputAndPipeline }
                    ) {
                        OutlinedTextField(
                            value = state.options.customOutputBaseName,
                            onValueChange = onUpdateCustomOutputName,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isRunning,
                            singleLine = true,
                            label = { Text("Nombre o plantilla (ej. {nombre}_{fecha}_{indice})") }
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("{nombre}", "{indice}", "{fecha}", "{formato}").forEach { token ->
                                AssistChip(
                                    onClick = {
                                        val cur = state.options.customOutputBaseName
                                        val next = if (cur.isBlank()) "{nombre}_$token" else "${cur}_$token"
                                        onUpdateCustomOutputName(next)
                                    },
                                    enabled = !isRunning,
                                    label = { Text("+ $token") }
                                )
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        Text(
                            text = "Acción secundaria tras convertir (Pipeline de 2 pasos)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SecondaryPipelineAction.entries.forEach { action ->
                                FilterChip(
                                    selected = state.options.secondaryPipelineAction == action,
                                    onClick = { onUpdateSecondaryPipelineAction(action) },
                                    enabled = !isRunning,
                                    label = { Text(action.labelEs) }
                                )
                            }
                        }

                        if (state.options.secondaryPipelineAction == SecondaryPipelineAction.ENCRYPT_AES256 &&
                            !state.showVaultSecurityOptions
                        ) {
                            OutlinedTextField(
                                value = state.options.vaultPassword,
                                onValueChange = onUpdateVaultPassword,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isRunning,
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                label = { Text("Contraseña para la Bóveda .anywall del Paso 2") }
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun StepHeaderRow(
    stepNumber: String,
    title: String,
    trailingContent: @Composable () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stepNumber,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )
        }
        trailingContent()
    }
}

@Composable
private fun ContextualStudioBannerCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    isActive: Boolean,
    onClick: () -> Unit,
    onReset: (() -> Unit)? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = if (isActive) {
            accentColor.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        },
        border = BorderStroke(
            width = if (isActive) 1.5.dp else 1.dp,
            color = accentColor.copy(alpha = if (isActive) 0.55f else 0.30f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    )
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
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
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (onReset != null) {
                TextButton(
                    onClick = onReset,
                    enabled = enabled
                ) {
                    Text("Quitar", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun CollapsibleSettingsCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HorizontalDivider()
                    content()
                }
            }
        }
    }
}

@Composable
private fun StickyConversionBottomBar(
    inputCount: Int,
    selectedFormat: SupportedFormat?,
    isDecryptingVault: Boolean = false,
    estimateRange: String?,
    estimateSavingsBadge: String?,
    onSavePreset: () -> Unit,
    onStartConversion: () -> Unit
) {
    Surface(
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (estimateRange != null && selectedFormat != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$inputCount archivo(s) → ${selectedFormat.label}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Salida est.: $estimateRange (${estimateSavingsBadge.orEmpty()})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onStartConversion,
                    enabled = selectedFormat != null && inputCount > 0,
                    shape = RoundedCornerShape(14.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            isDecryptingVault -> "Desencriptar y Restaurar Original"
                            selectedFormat != null -> "Convertir a ${selectedFormat.label}"
                            else -> "Elige un formato destino"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}
