package com.anywall.converter.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.anywall.converter.core.LocalThumbnailLoader
import com.anywall.converter.core.ScopedStorageManager
import com.anywall.converter.core.UriFileResolver
import com.anywall.converter.data.engines.ArchiveEngineImpl
import com.anywall.converter.data.engines.ForensicMetadataHelper
import com.anywall.converter.domain.model.ArchiveCompressionLevel
import com.anywall.converter.domain.model.AudioBitrateKbps
import com.anywall.converter.domain.model.ConversionPreset
import com.anywall.converter.domain.model.ImageBatchLayoutMode
import com.anywall.converter.domain.model.ImageEditSpec
import com.anywall.converter.domain.model.LineEndingOption
import com.anywall.converter.domain.model.PdfActionMode
import com.anywall.converter.domain.model.PdfPageEditItem
import com.anywall.converter.domain.model.PdfPageOrientation
import com.anywall.converter.domain.model.PdfPageSize
import com.anywall.converter.domain.model.SecondaryPipelineAction
import com.anywall.converter.domain.model.SupportedFormat
import com.anywall.converter.domain.model.TextEncodingOption
import com.anywall.converter.domain.model.VideoPlaybackSpeed
import com.anywall.converter.domain.model.VideoResolution
import com.anywall.converter.ui.components.ArchivePreviewDialog
import com.anywall.converter.ui.components.ConversionProgressCard
import com.anywall.converter.ui.components.FormatChipSelector
import com.anywall.converter.ui.components.ImageVisualEditorDialog
import com.anywall.converter.ui.components.InputFileCard
import com.anywall.converter.ui.components.OfflinePrivacyBadge
import com.anywall.converter.ui.components.OfflineToolsSheetDialog
import com.anywall.converter.ui.components.PdfVisualEditorDialog
import com.anywall.converter.ui.components.SignaturePadDialog
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * `ConfigureScreen` (v0.3):
 * - Gestión de lotes múltiples con miniaturas, ordenamiento (`Nombre A–Z`, `Tamaño`, `Invertir`),
 *   importación de carpetas y captura directa desde Cámara / Escáner.
 * - Barra de **Presets / Perfiles Rápidos** (Room v3) con opción de guardar nuevos presets personalizados.
 * - Editores Visuales en Vista Previa:
 *   1. `PdfVisualEditorDialog` + `SignaturePadDialog` (páginas PDF, rotación, páginas en blanco, censura y firma manuscrita).
 *   2. `ImageVisualEditorDialog` (recorte, volteo, filtros B/N, relación de aspecto con Blur, censura/pixelado y marca de agua).
 *   3. `ArchivePreviewDialog` (explorador previo de `.zip`/`.tar*`/`.7z` con extracción selectiva).
 *   4. `OfflineToolsSheetDialog` (inspector forense & SHA-256, QR offline, Diff y comparador Antes/Después).
 * - Panel completo de opciones v0.3 incluyendo plantillas de renombrado (`{nombre}`, `{indice}`, `{fecha}`, `{formato}`)
 *   y encadenamiento / Pipelines de 2 pasos (`SecondaryPipelineAction`).
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
    onUpdateVideoPlaybackSpeed: (Float) -> Unit,
    onUpdateVideoRotation: (Int) -> Unit,
    onUpdateVideoFlipHorizontal: (Boolean) -> Unit,
    onUpdateTargetVideoMaxMb: (String) -> Unit,
    onUpdateMergeMediaSequence: (Boolean) -> Unit,
    onUpdateAudioBitrateKbps: (Int) -> Unit,
    onUpdateAudioFadeSeconds: (Int, Int) -> Unit,
    onUpdateExportAsRingtone: (Boolean) -> Unit,
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
    var showVisualPdfEditor by remember { mutableStateOf(false) }
    var showVisualImageEditor by remember { mutableStateOf(false) }
    var showSignaturePad by remember { mutableStateOf(false) }
    var showArchivePreview by remember { mutableStateOf(false) }
    var showOfflineTools by remember { mutableStateOf(false) }
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var newPresetName by remember { mutableStateOf("") }
    var newPresetDesc by remember { mutableStateOf("") }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val addDocumentsLauncher = rememberLauncherForActivityResult(
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
    ) { ok ->
        val uri = pendingCameraUri
        if (ok && uri != null) {
            onAddMoreFiles(listOf(uri))
        }
    }

    if (showVisualPdfEditor) {
        PdfVisualEditorDialog(
            pdfInputFiles = state.inputFiles,
            initialEdits = state.visualPdfEditsList,
            thumbnailLoader = thumbnailLoader,
            onOpenSignaturePad = { showSignaturePad = true },
            onApplyEdits = onApplyVisualPdfEdits,
            onResetEdits = onClearVisualPdfEdits,
            onDismiss = { showVisualPdfEditor = false }
        )
    }

    if (showVisualImageEditor) {
        ImageVisualEditorDialog(
            imageInputs = state.inputFiles.filter { it.format.category == com.anywall.converter.domain.model.FormatCategory.IMAGE },
            initialEditSpec = state.options.imageEditSpec,
            onApplyEdits = { spec ->
                onApplyVisualImageEdits(spec)
                showVisualImageEditor = false
            },
            onDismiss = { showVisualImageEditor = false }
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

    if (showArchivePreview && state.inputFiles.isNotEmpty()) {
        ArchivePreviewDialog(
            archiveItem = state.inputFiles.first(),
            archiveEngine = archiveEngine,
            initialSelectedEntries = state.options.selectedArchiveEntries,
            onApplySelection = { entries ->
                onUpdateSelectedArchiveEntries(entries)
                showArchivePreview = false
            },
            onDismiss = { showArchivePreview = false }
        )
    }

    if (showOfflineTools) {
        OfflineToolsSheetDialog(
            initialTabIndex = 0,
            loadedInputs = state.inputFiles,
            currentImageEditSpec = state.options.imageEditSpec,
            currentQualityPercent = state.options.clampedQuality,
            uriResolver = uriResolver,
            storageManager = storageManager,
            forensicHelper = forensicHelper,
            onDismiss = { showOfflineTools = false }
        )
    }

    if (showSavePresetDialog) {
        AlertDialog(
            onDismissRequest = { showSavePresetDialog = false },
            title = { Text("Guardar Perfil / Preset Rápido", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newPresetName,
                        onValueChange = { newPresetName = it },
                        singleLine = true,
                        label = { Text("Nombre del preset") },
                        placeholder = { Text("Ej. Mi PDF Firmado") }
                    )
                    OutlinedTextField(
                        value = newPresetDesc,
                        onValueChange = { newPresetDesc = it },
                        singleLine = true,
                        label = { Text("Descripción corta (opcional)") }
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = newPresetName.isNotBlank(),
                    onClick = {
                        onSaveNewPreset(newPresetName, newPresetDesc)
                        newPresetName = ""
                        newPresetDesc = ""
                        showSavePresetDialog = false
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

    val isRunning = state.activeJob.isRunning
    val requiresPdfPassword = state.selectedTargetFormat == SupportedFormat.PDF &&
        state.options.pdfActionMode == PdfActionMode.PROTECT_PASSWORD &&
        state.options.pdfPassword.isBlank()
    val requiresVaultPassword = state.showVaultPasswordField &&
        state.options.vaultPassword.isBlank() &&
        state.options.pdfPassword.isBlank()
    val canConvert = state.inputFiles.isNotEmpty() &&
        state.selectedTargetFormat != null &&
        !isRunning &&
        !requiresPdfPassword &&
        !requiresVaultPassword

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Configurar Conversión",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${state.inputFiles.size} ${if (state.inputFiles.size == 1) "archivo" else "archivos"} • ${state.formattedTotalInputSize}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver a Inicio"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showOfflineTools = true }) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = "Herramientas Offline (Metadatos, QR, Diff, Slider)"
                        )
                    }
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Ver Historial"
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 6.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onStartConversion,
                        enabled = canConvert,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        val targetLabel = state.selectedTargetFormat?.label ?: ""
                        Text(
                            text = when {
                                requiresPdfPassword -> "Introduce una contraseña para proteger el PDF"
                                requiresVaultPassword -> "Introduce la contraseña de la Bóveda AES-256"
                                targetLabel.isNotEmpty() -> "Convertir Ahora a $targetLabel"
                                else -> "Convertir Ahora"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (state.activeJob.isRunning ||
                state.activeJob.isCompleted ||
                state.activeJob.isFailed
            ) {
                item {
                    ConversionProgressCard(
                        jobState = state.activeJob,
                        onCancel = onCancelConversion,
                        onOpenHistory = onNavigateToHistory
                    )
                }
            }

            // Barra de Perfiles / Presets Rápidos (v0.3)
            if (presets.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AssistChip(
                            onClick = { showSavePresetDialog = true },
                            enabled = !isRunning,
                            label = { Text("+ Guardar Preset", fontWeight = FontWeight.Bold) },
                            leadingIcon = {
                                Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )
                        presets.forEach { preset ->
                            AssistChip(
                                onClick = { onApplyPreset(preset) },
                                enabled = !isRunning,
                                label = { Text(preset.name) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Archivos en el Lote (${state.inputFiles.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            OfflinePrivacyBadge(modifier = Modifier.padding(top = 4.dp))
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            OutlinedButton(
                                onClick = { addDocumentsLauncher.launch(arrayOf("*/*")) },
                                enabled = !isRunning
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Archivos")
                            }

                            IconButton(
                                onClick = { addFolderLauncher.launch(null) },
                                enabled = !isRunning
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CreateNewFolder,
                                    contentDescription = "Añadir carpeta completa",
                                    tint = MaterialTheme.colorScheme.primary
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
                                    contentDescription = "Capturar desde cámara",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            if (state.inputFiles.isNotEmpty()) {
                                IconButton(
                                    onClick = onClearAllFiles,
                                    enabled = !isRunning
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteSweep,
                                        contentDescription = "Quitar todos los archivos",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }

                    if (state.inputFiles.size > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AssistChip(
                                onClick = onSortByName,
                                enabled = !isRunning,
                                label = { Text("Ordenar Nombre (A–Z)") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.SortByAlpha,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                            AssistChip(
                                onClick = onSortBySize,
                                enabled = !isRunning,
                                label = { Text("Ordenar por Tamaño") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Storage,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                            AssistChip(
                                onClick = onReverseOrder,
                                enabled = !isRunning,
                                label = { Text("Invertir Orden") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.SwapVert,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }

            if (state.inputFiles.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No hay archivos cargados en el lote",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Añade archivos, importa una carpeta o captura un documento con la cámara.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = { addDocumentsLauncher.launch(arrayOf("*/*")) }) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Archivos")
                                }
                                OutlinedButton(onClick = { addFolderLauncher.launch(null) }) {
                                    Icon(imageVector = Icons.Default.CreateNewFolder, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Carpeta")
                                }
                            }
                        }
                    }
                }
            } else {
                itemsIndexed(
                    items = state.inputFiles,
                    key = { _, item -> item.uri.toString() }
                ) { index, fileItem ->
                    InputFileCard(
                        item = fileItem,
                        index = index,
                        totalCount = state.inputFiles.size,
                        thumbnailLoader = thumbnailLoader,
                        onMoveUp = { onMoveFileUp(index) },
                        onMoveDown = { onMoveFileDown(index) },
                        onRemove = { onRemoveFile(fileItem.uri) },
                        enabled = !isRunning
                    )
                }

                // 1. Tarjeta destacada: Modificador Visual de PDF en Vista Previa
                if (state.showVisualPdfEditorButton) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                val activeVisualEdits = state.visualPdfEditsList
                                val activeCount = activeVisualEdits.count { it.included }
                                Text(
                                    text = "Modificador Visual de PDF en Vista Previa",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = if (activeVisualEdits.isNotEmpty()) {
                                        "Edición visual activa: $activeCount de ${activeVisualEdits.size} páginas seleccionadas (orden, rotación, censura y firma)."
                                    } else {
                                        "Previsualiza todas las páginas en cuadrícula para reordenarlas, rotarlas (+90°), duplicarlas, insertar páginas en blanco, aplicar censura o estampar tu firma manuscrita."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    FilledTonalButton(
                                        onClick = { showVisualPdfEditor = true },
                                        enabled = !isRunning,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.EditNote,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (activeVisualEdits.isNotEmpty()) {
                                                "Editar Vista Previa PDF ($activeCount págs.)"
                                            } else {
                                                "Abrir Vista Previa y Editor PDF"
                                            },
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    if (activeVisualEdits.isNotEmpty()) {
                                        OutlinedButton(
                                            onClick = onClearVisualPdfEdits,
                                            enabled = !isRunning
                                        ) {
                                            Text("Restablecer")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Tarjeta destacada: Editor Visual de Imágenes en Vista Previa (v0.3)
                if (state.showVisualImageEditorButton) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Editor Visual de Imágenes (Crop, Filtros, Blur y Censura)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = if (state.options.hasVisualImageEdits) {
                                        "Edición visual activa (recorte, filtros B/N, fondo Blur, censura o marca de agua)."
                                    } else {
                                        "Recorta proporciones, aplica filtro escáner B/N, añade fondo difuminado (Blur), censura/pixela zonas sensibles o estampa marca de agua."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.9f)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    FilledTonalButton(
                                        onClick = { showVisualImageEditor = true },
                                        enabled = !isRunning,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (state.options.hasVisualImageEdits) "Modificar Edición Visual" else "Abrir Editor Visual de Imagen",
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    if (state.options.hasVisualImageEdits) {
                                        OutlinedButton(
                                            onClick = onClearVisualImageEdits,
                                            enabled = !isRunning
                                        ) {
                                            Text("Restablecer")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Tarjeta destacada: Explorador Previo de Archivos Comprimidos (v0.3)
                if (state.showArchivePreviewButton) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val selectedCount = state.options.selectedArchiveEntries.size
                                Text(
                                    text = "Explorador Previo de Archivos Comprimidos",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = if (selectedCount > 0) {
                                        "Extracción selectiva activa: se extraerán únicamente $selectedCount archivos seleccionados."
                                    } else {
                                        "Inspecciona el contenido del archivo comprimido antes de extraer y elige qué elementos descomprimir."
                                    },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                FilledTonalButton(
                                    onClick = { showArchivePreview = true },
                                    enabled = !isRunning
                                ) {
                                    Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Inspeccionar Contenido y Seleccionar")
                                }
                            }
                        }
                    }
                }

                // Selector inteligente de Formato Destino
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Formato Destino",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            FormatChipSelector(
                                availableFormats = state.availableTargetFormats,
                                selectedFormat = state.selectedTargetFormat,
                                inputCount = state.inputFiles.size,
                                mergeIntoSinglePdf = state.options.mergeIntoSinglePdf,
                                onSelectFormat = onSelectTargetFormat,
                                enabled = !isRunning
                            )
                        }
                    }
                }

                // Panel dinámico de parámetros (v0.2 + v0.3)
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Parámetros de Conversión",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            if (state.showPdfActionModeSelector && !state.options.hasVisualPdfEdits) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "Modo de operación PDF",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        PdfActionMode.entries.forEach { mode ->
                                            val selected = state.options.pdfActionMode == mode
                                            FilterChip(
                                                selected = selected,
                                                onClick = { onUpdatePdfActionMode(mode) },
                                                enabled = !isRunning,
                                                label = { Text(mode.labelEs) }
                                            )
                                        }
                                    }
                                    Text(
                                        text = state.options.pdfActionMode.descriptionEs,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    if (state.options.pdfActionMode == PdfActionMode.SPLIT_BY_CHUNK) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            listOf(2, 3, 5, 10).forEach { chunk ->
                                                FilterChip(
                                                    selected = state.options.pdfSplitChunkPages == chunk,
                                                    onClick = { onUpdatePdfSplitChunkPages(chunk) },
                                                    enabled = !isRunning,
                                                    label = { Text("Cada $chunk págs.") }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (state.showMergePdfToggle) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Unir en un solo archivo PDF",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Combina los ${state.inputFiles.size} archivos en el orden actual (#1 → #${state.inputFiles.size})",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Switch(
                                        checked = state.options.mergeIntoSinglePdf,
                                        onCheckedChange = onUpdateMergeSinglePdf,
                                        enabled = !isRunning
                                    )
                                }
                            }

                            if (state.showPdfPageSetupSelector) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "Tamaño y orientación de página PDF",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        PdfPageSize.entries.forEach { size ->
                                            FilterChip(
                                                selected = state.options.pdfPageSize == size,
                                                onClick = { onUpdatePdfPageSize(size) },
                                                enabled = !isRunning,
                                                label = { Text(size.labelEs) }
                                            )
                                        }
                                        PdfPageOrientation.entries.forEach { orientation ->
                                            FilterChip(
                                                selected = state.options.pdfPageOrientation == orientation,
                                                onClick = { onUpdatePdfPageOrientation(orientation) },
                                                enabled = !isRunning,
                                                label = { Text(orientation.labelEs) }
                                            )
                                        }
                                    }
                                }
                            }

                            if (state.showPageRangeField) {
                                OutlinedTextField(
                                    value = state.options.pageRangeSpec,
                                    onValueChange = onUpdatePageRangeSpec,
                                    enabled = !isRunning,
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = { Text("Rango de páginas (opcional)") },
                                    placeholder = { Text("Ej: 1-3, 5, 8-10 (vacío = todas)") }
                                )
                            }

                            if (state.showPdfPasswordField) {
                                val passLabel = when (state.options.pdfActionMode) {
                                    PdfActionMode.PROTECT_PASSWORD -> "Contraseña para cifrar el PDF (obligatoria)"
                                    PdfActionMode.UNLOCK_PASSWORD -> "Contraseña actual del PDF a desbloquear"
                                    else -> "Contraseña para proteger el PDF generado (opcional)"
                                }
                                OutlinedTextField(
                                    value = state.options.pdfPassword,
                                    onValueChange = onUpdatePdfPassword,
                                    enabled = !isRunning,
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    leadingIcon = {
                                        Icon(imageVector = Icons.Default.Lock, contentDescription = null)
                                    },
                                    label = { Text(passLabel) },
                                    placeholder = { Text("Cifrado local estándar PDFBox") },
                                    trailingIcon = {
                                        if (state.options.pdfPassword.isNotEmpty()) {
                                            TextButton(onClick = { onUpdatePdfPassword("") }) {
                                                Text("Limpiar")
                                            }
                                        }
                                    }
                                )
                            }

                            // Decoraciones PDF v0.3: Marca de agua, Numeración, Firma Manuscrita y Sello QR
                            if (state.showPdfDecorationsSection) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        text = "Marca de Agua, Numeración, Firma y Sello QR en PDF",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    OutlinedTextField(
                                        value = state.options.pdfWatermarkText,
                                        onValueChange = onUpdatePdfWatermarkText,
                                        enabled = !isRunning,
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        label = { Text("Marca de agua diagonal en PDF (opcional)") },
                                        placeholder = { Text("Ej: CONFIDENCIAL • BORRADOR") }
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Numerar páginas automáticamente (Página X de Y)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Switch(
                                            checked = state.options.addPdfPageNumbers,
                                            onCheckedChange = onUpdateAddPdfPageNumbers,
                                            enabled = !isRunning
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedButton(
                                            onClick = { showSignaturePad = true },
                                            enabled = !isRunning,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.Draw, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                if (state.options.pdfSignaturePngPath.isNotBlank()) "✓ Firma Manuscrita Lista"
                                                else "Dibujar Firma Manuscrita"
                                            )
                                        }
                                    }

                                    OutlinedTextField(
                                        value = state.options.pdfStampQrContent,
                                        onValueChange = onUpdatePdfStampQrContent,
                                        enabled = !isRunning,
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        label = { Text("Estampar código QR en pág. 1 del PDF (opcional)") },
                                        placeholder = { Text("Texto o URL para el sello QR") }
                                    )
                                }
                            }

                            if (state.showQualitySlider) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Calidad / Nivel de compresión",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "${state.options.clampedQuality}%",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Slider(
                                        value = state.options.clampedQuality.toFloat(),
                                        onValueChange = { onUpdateQuality(it.roundToInt()) },
                                        valueRange = 10f..100f,
                                        steps = 17,
                                        enabled = !isRunning
                                    )
                                }
                            }

                            if (state.showImageResizeOption) {
                                val dimensionOptions = listOf(
                                    null to "Original",
                                    2048 to "2048 px",
                                    1440 to "1440 px",
                                    1080 to "1080 px",
                                    720 to "720 px"
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Escala / Tamaño máximo de imagen",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        dimensionOptions.forEach { (px, label) ->
                                            FilterChip(
                                                selected = state.options.maxDimensionPx == px,
                                                onClick = { onUpdateMaxDimension(px) },
                                                enabled = !isRunning,
                                                label = { Text(label) }
                                            )
                                        }
                                    }
                                }
                            }

                            if (state.showImageAdvancedV03Options) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (state.selectedTargetFormat in setOf(SupportedFormat.JPG, SupportedFormat.WEBP)) {
                                        OutlinedTextField(
                                            value = state.targetImageMaxKbText,
                                            onValueChange = onUpdateTargetImageMaxKb,
                                            enabled = !isRunning,
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            label = { Text("Límite de peso máximo por imagen en KB (opcional)") },
                                            placeholder = { Text("Ej: 400 (comprime hasta quedar < 400 KB)") }
                                        )
                                    }

                                    if (state.inputFiles.size >= 2) {
                                        Text(
                                            text = "Modo Collage o Tira Continua",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            ImageBatchLayoutMode.entries.forEach { layout ->
                                                FilterChip(
                                                    selected = state.options.imageBatchLayoutMode == layout,
                                                    onClick = { onUpdateImageBatchLayoutMode(layout) },
                                                    enabled = !isRunning,
                                                    label = { Text(layout.labelEs) }
                                                )
                                            }
                                        }
                                    }

                                    if (state.selectedTargetFormat == SupportedFormat.GIF) {
                                        Text(
                                            text = "Velocidad de GIF animado: ${state.options.animatedGifFps} FPS",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            listOf(2, 4, 8, 12).forEach { fps ->
                                                FilterChip(
                                                    selected = state.options.animatedGifFps == fps,
                                                    onClick = { onUpdateAnimatedGifFps(fps) },
                                                    enabled = !isRunning,
                                                    label = { Text("$fps FPS") }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (state.showImageRotationAndExifOptions) {
                                val rotationOptions = listOf(0, 90, 180, 270)
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.RotateRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Rotación global de imagen",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        rotationOptions.forEach { deg ->
                                            FilterChip(
                                                selected = state.options.normalizedRotationDegrees == deg,
                                                onClick = { onUpdateImageRotation(deg) },
                                                enabled = !isRunning,
                                                label = { Text(if (deg == 0) "0° (Original)" else "$deg°") }
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Eliminar metadatos EXIF y ubicación GPS",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Garantiza privacidad total limpiando etiquetas de cámara y coordenadas",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Switch(
                                        checked = state.options.stripExifMetadata,
                                        onCheckedChange = onUpdateStripExif,
                                        enabled = !isRunning
                                    )
                                }
                            }

                            if (state.showVideoResolutionSelector) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "Resolución de Video (Media3 Transformer)",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        VideoResolution.entries.forEach { resolution ->
                                            val isSelected = state.options.videoResolution == resolution
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = { onUpdateVideoResolution(resolution) },
                                                enabled = !isRunning,
                                                label = {
                                                    Text(
                                                        text = resolution.labelEs,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                }
                                            )
                                        }
                                    }

                                    Text(
                                        text = "Velocidad, Rotación y Volteo de Video",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        VideoPlaybackSpeed.entries.forEach { spd ->
                                            FilterChip(
                                                selected = abs(state.options.videoPlaybackSpeedFactor - spd.factor) < 0.03f,
                                                onClick = { onUpdateVideoPlaybackSpeed(spd.factor) },
                                                enabled = !isRunning,
                                                label = { Text(spd.label) }
                                            )
                                        }
                                    }
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        listOf(0, 90, 180, 270).forEach { deg ->
                                            FilterChip(
                                                selected = state.options.normalizedVideoRotationDegrees == deg,
                                                onClick = { onUpdateVideoRotation(deg) },
                                                enabled = !isRunning,
                                                label = { Text("Rotar $deg°") }
                                            )
                                        }
                                        FilterChip(
                                            selected = state.options.videoFlipHorizontal,
                                            onClick = { onUpdateVideoFlipHorizontal(!state.options.videoFlipHorizontal) },
                                            enabled = !isRunning,
                                            label = { Text("Espejo Horizontal") }
                                        )
                                    }

                                    OutlinedTextField(
                                        value = state.targetVideoMaxMbText,
                                        onValueChange = onUpdateTargetVideoMaxMb,
                                        enabled = !isRunning,
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        label = { Text("Peso máximo objetivo del video en MB (opcional)") },
                                        placeholder = { Text("Ej: 15 (calcula bitrate automático para WhatsApp/Discord)") }
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Silenciar video (quitar pista de audio)",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Exporta el video sin sonido para reducir peso y proteger privacidad",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Switch(
                                        checked = state.options.muteVideoAudio,
                                        onCheckedChange = onUpdateMuteVideoAudio,
                                        enabled = !isRunning
                                    )
                                }
                            }

                            if (state.showAudioV03Options) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "Bitrate de Audio y Modo Ringtone",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        AudioBitrateKbps.entries.forEach { br ->
                                            FilterChip(
                                                selected = state.options.audioBitrateKbps == br.kbps,
                                                onClick = { onUpdateAudioBitrateKbps(br.kbps) },
                                                enabled = !isRunning,
                                                label = { Text(br.label) }
                                            )
                                        }
                                    }
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf(0 to "Sin Fade", 2 to "Fade In/Out 2s", 4 to "Fade In/Out 4s").forEach { (sec, label) ->
                                            FilterChip(
                                                selected = state.options.audioFadeInSeconds == sec,
                                                onClick = { onUpdateAudioFadeSeconds(sec, sec) },
                                                enabled = !isRunning,
                                                label = { Text(label) }
                                            )
                                        }
                                        FilterChip(
                                            selected = state.options.exportAsRingtone,
                                            onClick = { onUpdateExportAsRingtone(!state.options.exportAsRingtone) },
                                            enabled = !isRunning,
                                            label = { Text("Guardar en Ringtones") }
                                        )
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
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "Une los ${state.inputFiles.size} archivos multimedia continuamente en el orden actual",
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

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Recorte de tiempo (Trim en segundos)",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = state.trimStartText,
                                            onValueChange = onUpdateTrimStartText,
                                            enabled = !isRunning,
                                            modifier = Modifier.weight(1f),
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            label = { Text("Inicio (seg)") },
                                            placeholder = { Text("0") }
                                        )
                                        OutlinedTextField(
                                            value = state.trimEndText,
                                            onValueChange = onUpdateTrimEndText,
                                            enabled = !isRunning,
                                            modifier = Modifier.weight(1f),
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            label = { Text("Fin (seg)") },
                                            placeholder = { Text("Hasta el final") }
                                        )
                                    }
                                }
                            }

                            if (state.showArchiveV03Options) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (state.selectedTargetFormat == SupportedFormat.FILE_PARTS) {
                                        Text(
                                            text = "Tamaño de cada parte (.part001, .part002…)",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            listOf(5, 10, 25, 50, 100).forEach { mb ->
                                                FilterChip(
                                                    selected = state.options.fileSplitPartSizeMb == mb,
                                                    onClick = { onUpdateFileSplitPartSizeMb(mb) },
                                                    enabled = !isRunning,
                                                    label = { Text("$mb MB") }
                                                )
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = "Nivel de compresión de archivo",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            ArchiveCompressionLevel.entries.forEach { lvl ->
                                                FilterChip(
                                                    selected = state.options.archiveCompressionLevel == lvl,
                                                    onClick = { onUpdateArchiveCompressionLevel(lvl) },
                                                    enabled = !isRunning,
                                                    label = { Text(lvl.labelEs) }
                                                )
                                            }
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Conservar jerarquía de subcarpetas de origen",
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Switch(
                                                checked = state.options.preserveSubfolderStructure,
                                                onCheckedChange = onUpdatePreserveSubfolderStructure,
                                                enabled = !isRunning
                                            )
                                        }
                                    }
                                }
                            }

                            if (state.showStructuredDataV03Options) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Formatear con sangría limpia (Pretty Print)",
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
                                        text = "Codificación de caracteres y saltos de línea",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextEncodingOption.entries.forEach { enc ->
                                            FilterChip(
                                                selected = state.options.textEncoding == enc,
                                                onClick = { onUpdateTextEncoding(enc) },
                                                enabled = !isRunning,
                                                label = { Text(enc.label) }
                                            )
                                        }
                                    }
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        LineEndingOption.entries.forEach { eol ->
                                            FilterChip(
                                                selected = state.options.lineEndingOption == eol,
                                                onClick = { onUpdateLineEndingOption(eol) },
                                                enabled = !isRunning,
                                                label = { Text(eol.label) }
                                            )
                                        }
                                    }
                                }
                            }

                            if (state.showVaultPasswordField) {
                                OutlinedTextField(
                                    value = state.options.vaultPassword,
                                    onValueChange = onUpdateVaultPassword,
                                    enabled = !isRunning,
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                    label = { Text("Contraseña de Bóveda Universal AES-256-GCM (.anywall)") },
                                    placeholder = { Text("Derivación PBKDF2-HMAC-SHA256 (120 000 iter.)") }
                                )
                            }

                            if (state.showQrCustomTextField) {
                                OutlinedTextField(
                                    value = state.options.qrCustomText,
                                    onValueChange = onUpdateQrCustomText,
                                    enabled = !isRunning,
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Texto/URL personalizado para el QR (vacío = usar contenido del archivo)") }
                                )
                            }

                            HorizontalDivider()

                            // Nombre y Plantillas Dinámicas + Pipeline de 2 pasos (v0.3)
                            OutlinedTextField(
                                value = state.options.customOutputBaseName,
                                onValueChange = onUpdateCustomOutputName,
                                enabled = !isRunning,
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("Nombre o plantilla de salida (opcional)") },
                                placeholder = { Text("Ej: {nombre}_{indice}_{fecha}") },
                                trailingIcon = {
                                    if (state.options.customOutputBaseName.isNotEmpty()) {
                                        TextButton(onClick = { onUpdateCustomOutputName("") }) {
                                            Text("Limpiar")
                                        }
                                    }
                                }
                            )

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(
                                    "{nombre}_{indice}",
                                    "{nombre}_{fecha}",
                                    "Doc_{indice}_{fecha}",
                                    "{nombre}_{formato}"
                                ).forEach { tpl ->
                                    AssistChip(
                                        onClick = { onUpdateCustomOutputName(tpl) },
                                        enabled = !isRunning,
                                        label = { Text(tpl, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Encadenar Paso 2 Automático (Pipeline de 2 pasos)",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
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
                            }
                        }
                    }
                }
            }
        }
    }
}
