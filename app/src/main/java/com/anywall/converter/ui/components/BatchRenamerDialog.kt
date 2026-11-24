package com.anywall.converter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.anywall.converter.R
import com.anywall.converter.core.ScopedStorageManager
import com.anywall.converter.data.engines.BatchFileRenamer
import com.anywall.converter.domain.model.InputFileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Renombrador Masivo de Archivos sin reconvertir (`BatchRenamerDialog` - v0.4):
 * - Plantillas (`{nombre}`, `{indice}`, `{fecha}`, `{formato}`), prefijo/sufijo,
 *   buscar/reemplazar (literal o Regex) y modos de capitalización.
 * - Vista previa Antes → Después en tiempo real libre de colisiones.
 * - Permite aplicar los nuevos nombres a la cola actual o guardar copias renombradas directamente en `Downloads/AnyWall/`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BatchRenamerDialog(
    inputFiles: List<InputFileItem>,
    storageManager: ScopedStorageManager,
    onApplyRenamedItemsToQueue: (List<InputFileItem>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var template by remember { mutableStateOf("{nombre}_{indice}") }
    var prefix by remember { mutableStateOf("") }
    var suffix by remember { mutableStateOf("") }
    var findText by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }
    var useRegex by remember { mutableStateOf(false) }
    var caseMode by remember { mutableStateOf(BatchFileRenamer.RenameCaseMode.KEEP) }
    var startIndex by remember { mutableIntStateOf(1) }
    var paddingDigits by remember { mutableIntStateOf(2) }

    var isExportingCopies by remember { mutableStateOf(false) }
    var exportStatusMessage by remember { mutableStateOf<String?>(null) }

    val renameSpec = remember(
        template,
        prefix,
        suffix,
        findText,
        replaceText,
        useRegex,
        caseMode,
        startIndex,
        paddingDigits
    ) {
        BatchFileRenamer.BatchRenameSpec(
            template = template,
            prefix = prefix,
            suffix = suffix,
            findText = findText,
            replaceText = replaceText,
            useRegex = useRegex,
            caseMode = caseMode,
            startIndex = startIndex,
            paddingDigits = paddingDigits
        )
    }

    val previewItems = remember(inputFiles, renameSpec) {
        BatchFileRenamer.generatePreview(
            originalFileNames = inputFiles.map { it.displayName },
            spec = renameSpec
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(22.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DriveFileRenameOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.batch_renamer_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${inputFiles.size} archivos en lote • Vista previa Antes → Después",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                }

                // Plantilla y tokens rápidos
                OutlinedTextField(
                    value = template,
                    onValueChange = { template = it },
                    singleLine = true,
                    label = { Text("Plantilla de nombre") },
                    modifier = Modifier.fillMaxWidth()
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("{nombre}", "{indice}", "{fecha}", "{formato}").forEach { token ->
                        AssistChip(
                            onClick = { template = "$template$token" },
                            label = { Text("+ $token", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                    AssistChip(
                        onClick = { template = "{nombre}" },
                        label = { Text("Solo {nombre}", style = MaterialTheme.typography.labelSmall) }
                    )
                }

                // Prefijo y Sufijo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = prefix,
                        onValueChange = { prefix = it },
                        singleLine = true,
                        label = { Text("Prefijo") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = suffix,
                        onValueChange = { suffix = it },
                        singleLine = true,
                        label = { Text("Sufijo") },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Buscar y Reemplazar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = findText,
                        onValueChange = { findText = it },
                        singleLine = true,
                        label = { Text("Buscar") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = replaceText,
                        onValueChange = { replaceText = it },
                        singleLine = true,
                        label = { Text("Reemplazar") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = useRegex,
                            onCheckedChange = { useRegex = it }
                        )
                        Text("Regex", style = MaterialTheme.typography.labelMedium)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AssistChip(
                            onClick = { startIndex = if (startIndex == 1) 0 else 1 },
                            label = { Text("Inicio: $startIndex") }
                        )
                        AssistChip(
                            onClick = { paddingDigits = if (paddingDigits >= 4) 1 else paddingDigits + 1 },
                            label = { Text("Dígitos: $paddingDigits") }
                        )
                    }
                }

                // Modos de capitalización
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BatchFileRenamer.RenameCaseMode.entries.forEach { mode ->
                        FilterChip(
                            selected = caseMode == mode,
                            onClick = { caseMode = mode },
                            label = { Text(mode.labelEs, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                HorizontalDivider()

                // Vista Previa Antes -> Después
                Text(
                    text = "Vista previa (Antes → Después):",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(165.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(previewItems) { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = item.originalFileName,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = item.newFileName,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = if (item.isModified) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                if (exportStatusMessage != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = exportStatusMessage.orEmpty(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                // Acciones
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Exportar copias renombradas directamente sin reconvertir
                    FilledTonalButton(
                        enabled = !isExportingCopies && inputFiles.isNotEmpty(),
                        onClick = {
                            isExportingCopies = true
                            scope.launch {
                                val count = withContext(Dispatchers.IO) {
                                    var savedCount = 0
                                    inputFiles.forEachIndexed { idx, fileItem ->
                                        val targetName = previewItems.getOrNull(idx)?.newFileName ?: fileItem.displayName
                                        runCatching {
                                            context.contentResolver.openInputStream(fileItem.uri)?.use { stream ->
                                                storageManager.saveStreamToDownloads(
                                                    inputStream = stream,
                                                    desiredDisplayName = targetName,
                                                    mimeType = fileItem.mimeType.ifBlank { "application/octet-stream" },
                                                    knownSizeBytes = fileItem.sizeBytes
                                                )
                                                savedCount++
                                            }
                                        }
                                    }
                                    savedCount
                                }
                                isExportingCopies = false
                                exportStatusMessage = "Se guardaron $count archivos renombrados en Downloads/AnyWall/"
                            }
                        }
                    ) {
                        if (isExportingCopies) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Guardar copias ya", style = MaterialTheme.typography.labelMedium)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onDismiss) {
                            Text(stringResource(R.string.btn_cancel))
                        }
                        Button(
                            enabled = inputFiles.isNotEmpty(),
                            onClick = {
                                val renamedList = inputFiles.mapIndexed { idx, item ->
                                    val newName = previewItems.getOrNull(idx)?.newFileName ?: item.displayName
                                    item.copy(displayName = newName)
                                }
                                onApplyRenamedItemsToQueue(renamedList)
                                onDismiss()
                            }
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Aplicar a cola")
                        }
                    }
                }
            }
        }
    }
}
