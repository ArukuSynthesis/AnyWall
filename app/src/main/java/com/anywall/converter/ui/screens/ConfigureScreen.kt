package com.anywall.converter.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anywall.converter.domain.model.PdfActionMode
import com.anywall.converter.domain.model.SupportedFormat
import com.anywall.converter.domain.model.VideoResolution
import com.anywall.converter.ui.components.ConversionProgressCard
import com.anywall.converter.ui.components.FormatChipSelector
import com.anywall.converter.ui.components.InputFileCard
import com.anywall.converter.ui.components.OfflinePrivacyBadge
import kotlin.math.roundToInt

/**
 * `ConfigureScreen`:
 * - Lista de archivos cargados en el lote actual mostrando nombre, formato detectado, peso formateado (KB/MB)
 *   y opción para añadir más o quitar archivos.
 * - Selector inteligente de **Formato Destino** que muestra únicamente los formatos válidos para los archivos cargados
 *   (incluyendo acciones por lote como *"Unir en un solo PDF"* o *"Empaquetar en ZIP"* cuando hay múltiples archivos).
 * - Panel dinámico de parámetros según el tipo de archivo:
 *   - Slider de Calidad (10% - 100%) para imágenes/PDF/MP3/ZIP.
 *   - Escala/redimensionado máximo para imágenes.
 *   - Modo de operación PDF y rango de páginas para PDF.
 *   - Selector de Resolución (`Original`, `1080p`, `720p`, `480p`) para video.
 *   - Campo opcional de nombre de salida.
 * - Botón **"Convertir Ahora"** que inicia el `BatchConversionWorker` y muestra el progreso en vivo.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConfigureScreen(
    state: ConfigureUiState,
    onAddMoreFiles: (List<Uri>) -> Unit,
    onRemoveFile: (Uri) -> Unit,
    onClearAllFiles: () -> Unit,
    onSelectTargetFormat: (SupportedFormat) -> Unit,
    onUpdateQuality: (Int) -> Unit,
    onUpdateMaxDimension: (Int?) -> Unit,
    onUpdateVideoResolution: (VideoResolution) -> Unit,
    onUpdateCustomOutputName: (String) -> Unit,
    onUpdateMergeSinglePdf: (Boolean) -> Unit,
    onUpdatePdfActionMode: (PdfActionMode) -> Unit,
    onUpdatePageRangeSpec: (String) -> Unit,
    onStartConversion: () -> Unit,
    onCancelConversion: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val addDocumentsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onAddMoreFiles(uris)
        }
    }

    val isRunning = state.activeJob.isRunning
    val canConvert = state.inputFiles.isNotEmpty() &&
        state.selectedTargetFormat != null &&
        !isRunning

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
                            text = if (targetLabel.isNotEmpty()) {
                                "Convertir Ahora a $targetLabel"
                            } else {
                                "Convertir Ahora"
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
            // Progreso en vivo si el worker está activo o completado
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

            // Encabezado de archivos cargados con botones para Añadir más o Limpiar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Archivos en el Lote (${state.inputFiles.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        OfflinePrivacyBadge(modifier = Modifier.padding(top = 4.dp))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                            Text("Añadir")
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
                                text = "Añade uno o varios archivos para habilitar los formatos de conversión compatibles.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = { addDocumentsLauncher.launch(arrayOf("*/*")) }) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Seleccionar Archivos")
                            }
                        }
                    }
                }
            } else {
                items(
                    items = state.inputFiles,
                    key = { it.uri.toString() }
                ) { fileItem ->
                    InputFileCard(
                        item = fileItem,
                        onRemove = { onRemoveFile(fileItem.uri) },
                        enabled = !isRunning
                    )
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

                // Panel dinámico de parámetros según el tipo de archivo y formato destino
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

                            // 1. Modo de operación PDF avanzada (cuando origen y destino son PDF)
                            if (state.showPdfActionModeSelector) {
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

                            // 2. Opción de unir múltiples imágenes o documentos en un solo PDF
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
                                            text = "Combina los ${state.inputFiles.size} archivos cargados en un único documento PDF continuo",
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

                            // 3. Campo de rango de páginas para extracción/división de PDF
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

                            // 4. Slider de Calidad (10% - 100%) para imágenes, PDF, MP3 y ZIP
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

                            // 5. Selector de escala / redimensionado máximo para imágenes
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

                            // 6. Selector de Resolución para Video (Original, 1080p, 720p, 480p)
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
                            }

                            // 7. Campo opcional de nombre de archivo de salida
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
