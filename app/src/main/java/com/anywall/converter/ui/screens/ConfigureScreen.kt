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
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
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
import com.anywall.converter.domain.model.PdfActionMode
import com.anywall.converter.domain.model.PdfPageEditItem
import com.anywall.converter.domain.model.PdfPageOrientation
import com.anywall.converter.domain.model.PdfPageSize
import com.anywall.converter.domain.model.SupportedFormat
import com.anywall.converter.domain.model.VideoResolution
import com.anywall.converter.ui.components.ConversionProgressCard
import com.anywall.converter.ui.components.FormatChipSelector
import com.anywall.converter.ui.components.InputFileCard
import com.anywall.converter.ui.components.OfflinePrivacyBadge
import com.anywall.converter.ui.components.PdfVisualEditorDialog
import kotlin.math.roundToInt

/**
 * `ConfigureScreen` (v0.2):
 * - Lista de múltiples archivos cargados con miniatura local, reordenamiento ↑/↓, ordenación rápida
 *   (`Nombre A–Z`, `Tamaño`, `Invertir orden`) y botones para añadir archivos o importar una carpeta entera.
 * - **Modificador Visual de PDF en Vista Previa (`PdfVisualEditorDialog`)**: permite ver todas las páginas
 *   de los PDFs cargados, reordenarlas, rotar páginas individuales (`+90°`), excluir/incluir páginas
 *   y previsualizar cualquier página a pantalla completa.
 * - Selector inteligente de Formato Destino (incluyendo unión de lotes mixtos Imágenes + Documentos/PDF).
 * - Panel dinámico de parámetros v0.2.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConfigureScreen(
    state: ConfigureUiState,
    thumbnailLoader: LocalThumbnailLoader?,
    onAddMoreFiles: (List<Uri>) -> Unit,
    onAddFolderTree: (Uri) -> Unit,
    onMoveFileUp: (Int) -> Unit,
    onMoveFileDown: (Int) -> Unit,
    onSortByName: () -> Unit,
    onSortBySize: () -> Unit,
    onReverseOrder: () -> Unit,
    onApplyVisualPdfEdits: (List<PdfPageEditItem>) -> Unit,
    onClearVisualPdfEdits: () -> Unit,
    onRemoveFile: (Uri) -> Unit,
    onClearAllFiles: () -> Unit,
    onSelectTargetFormat: (SupportedFormat) -> Unit,
    onUpdateQuality: (Int) -> Unit,
    onUpdateMaxDimension: (Int?) -> Unit,
    onUpdateImageRotation: (Int) -> Unit,
    onUpdateStripExif: (Boolean) -> Unit,
    onUpdateVideoResolution: (VideoResolution) -> Unit,
    onUpdateMuteVideoAudio: (Boolean) -> Unit,
    onUpdateTrimStartText: (String) -> Unit,
    onUpdateTrimEndText: (String) -> Unit,
    onUpdateCustomOutputName: (String) -> Unit,
    onUpdateMergeSinglePdf: (Boolean) -> Unit,
    onUpdatePdfActionMode: (PdfActionMode) -> Unit,
    onUpdatePageRangeSpec: (String) -> Unit,
    onUpdatePdfPassword: (String) -> Unit,
    onUpdatePdfPageSize: (PdfPageSize) -> Unit,
    onUpdatePdfPageOrientation: (PdfPageOrientation) -> Unit,
    onStartConversion: () -> Unit,
    onCancelConversion: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showVisualPdfEditor by remember { mutableStateOf(false) }

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

    if (showVisualPdfEditor) {
        PdfVisualEditorDialog(
            pdfInputFiles = state.inputFiles,
            initialEdits = state.visualPdfEditsList,
            thumbnailLoader = thumbnailLoader,
            onApplyEdits = onApplyVisualPdfEdits,
            onResetEdits = onClearVisualPdfEdits,
            onDismiss = { showVisualPdfEditor = false }
        )
    }

    val isRunning = state.activeJob.isRunning
    val requiresPassword = state.selectedTargetFormat == SupportedFormat.PDF &&
        state.options.pdfActionMode == PdfActionMode.PROTECT_PASSWORD &&
        state.options.pdfPassword.isBlank()
    val canConvert = state.inputFiles.isNotEmpty() &&
        state.selectedTargetFormat != null &&
        !isRunning &&
        !requiresPassword

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
                                requiresPassword -> "Introduce una contraseña para proteger el PDF"
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

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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

                    // Barra de ordenación rápida cuando hay múltiples archivos en el lote
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
                                text = "Añade uno o varios archivos o importa una carpeta entera para habilitar los formatos compatibles.",
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

                // Tarjeta destacada: Modificador Visual de PDF en Vista Previa
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
                                        "Edición visual activa: $activeCount de ${activeVisualEdits.size} páginas seleccionadas con orden y rotación personalizados."
                                    } else {
                                        "Previsualiza todas las páginas de tus PDFs en cuadrícula para reordenarlas, rotarlas individualmente (+90°) o excluir páginas antes de convertir."
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

                // Panel dinámico de parámetros
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
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "10% (Menor peso)",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "100% (Máxima calidad)",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
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
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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

                            if (state.showMediaTrimAndMuteOptions) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Recorte de tiempo (opcional, en segundos)",
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

                            OutlinedTextField(
                                value = state.options.customOutputBaseName,
                                onValueChange = onUpdateCustomOutputName,
                                enabled = !isRunning,
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("Nombre de salida (opcional)") },
                                placeholder = { Text("Se guardará en Downloads/AnyWall/") },
                                trailingIcon = {
                                    if (state.options.customOutputBaseName.isNotEmpty()) {
                                        TextButton(onClick = { onUpdateCustomOutputName("") }) {
                                            Text("Limpiar")
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
