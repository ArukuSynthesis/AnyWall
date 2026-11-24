package com.anywall.converter.ui.components

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anywall.converter.data.engines.ArchiveEngineImpl
import com.anywall.converter.data.engines.ArchiveEntryPreview
import com.anywall.converter.data.engines.LibArchiveRarBridge
import com.anywall.converter.domain.model.InputFileItem
import com.anywall.converter.domain.model.SupportedFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Explorador Previo de Archivos Comprimidos y Re-empaquetador en 1 Paso (`ArchivePreviewDialog` - v0.4):
 * - Soporta `.rar` / `.rar5` / `.cbr` (vía `libarchive` `BSD-2-Clause`), `.zip`, `.cbz`, `.7z` y `.tar*`.
 * - Permite introducir contraseña para archivos `.rar`/`.rar5` protegidos.
 * - Permite seleccionar entradas específicas para extracción selectiva O **re-empaquetar en 1 paso**
 *   hacia `.zip`, `.7z`, `.tar.gz`, `.tar.xz`, `.tar.bz2` o `.cbz`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ArchivePreviewDialog(
    archiveItem: InputFileItem,
    archiveEngine: ArchiveEngineImpl,
    initialSelectedEntries: Set<String>,
    initialPassword: String = "",
    onApplySelection: (Set<String>) -> Unit,
    onUpdateArchivePassword: (String) -> Unit = {},
    onRepackInOneStep: ((SupportedFormat, Set<String>, String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var entries by remember { mutableStateOf<List<ArchiveEntryPreview>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var filterQuery by remember { mutableStateOf("") }
    var password by remember { mutableStateOf(initialPassword) }
    var reloadTrigger by remember { mutableIntStateOf(0) }
    val selectedPaths = remember { mutableStateListOf<String>() }

    val isRarOrCbr = archiveItem.format == SupportedFormat.RAR || archiveItem.format == SupportedFormat.CBR

    LaunchedEffect(archiveItem.uri, reloadTrigger) {
        isLoading = true
        val loaded = withContext(Dispatchers.IO) {
            archiveEngine.inspectArchiveEntries(
                item = archiveItem,
                maxEntries = 500,
                passphrase = password
            )
        }
        entries = loaded
        selectedPaths.clear()
        if (initialSelectedEntries.isNotEmpty()) {
            selectedPaths.addAll(loaded.map { it.entryPath }.filter { it in initialSelectedEntries })
        } else {
            selectedPaths.addAll(loaded.map { it.entryPath })
        }
        isLoading = false
    }

    val filteredEntries = remember(entries, filterQuery) {
        if (filterQuery.isBlank()) entries
        else entries.filter { it.entryPath.contains(filterQuery.trim(), ignoreCase = true) }
    }

    val repackTargets = remember {
        listOf(
            SupportedFormat.ZIP,
            SupportedFormat.SEVEN_Z,
            SupportedFormat.TAR_GZ,
            SupportedFormat.TAR_XZ,
            SupportedFormat.TAR_BZ2
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.FolderZip,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Column {
                Text(
                    text = if (isRarOrCbr) "Explorador RAR / RAR5 (${LibArchiveRarBridge.getEngineVersionLabel()})" else "Explorador de Comprimido",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${archiveItem.displayName} • ${selectedPaths.size} de ${entries.size} seleccionados",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isRarOrCbr || entries.any { it.isEncrypted }) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            onUpdateArchivePassword(it)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            IconButton(onClick = { reloadTrigger++ }) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Recargar con contraseña")
                            }
                        },
                        label = { Text("Contraseña del archivo RAR (opcional)") }
                    )
                }

                if (isLoading) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Inspeccionando contenido del archivo…")
                    }
                } else if (entries.isEmpty()) {
                    Text(
                        if (isRarOrCbr) {
                            "No se pudieron listar entradas. Si el archivo RAR tiene cabeceras cifradas, introduce la contraseña arriba y pulsa recargar."
                        } else {
                            "No se pudieron listar entradas individuales en este archivo."
                        }
                    )
                } else {
                    OutlinedTextField(
                        value = filterQuery,
                        onValueChange = { filterQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Filtrar archivos internos") }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = {
                                selectedPaths.clear()
                                selectedPaths.addAll(entries.map { it.entryPath })
                            }
                        ) {
                            Text("Seleccionar todos (${entries.size})")
                        }
                        TextButton(
                            onClick = { selectedPaths.clear() }
                        ) {
                            Text("Ninguno")
                        }
                    }

                    HorizontalDivider()

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                    ) {
                        items(filteredEntries, key = { it.entryPath }) { entry ->
                            val isChecked = entry.entryPath in selectedPaths
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isChecked) selectedPaths.remove(entry.entryPath)
                                        else selectedPaths.add(entry.entryPath)
                                    }
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            if (entry.entryPath !in selectedPaths) selectedPaths.add(entry.entryPath)
                                        } else {
                                            selectedPaths.remove(entry.entryPath)
                                        }
                                    }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (entry.isEncrypted) "🔒 ${entry.entryPath}" else entry.entryPath,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (entry.sizeBytes > 0L) {
                                        Text(
                                            text = InputFileItem.formatBytes(entry.sizeBytes),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (onRepackInOneStep != null) {
                        HorizontalDivider()
                        Text(
                            text = "Re-empaquetar seleccionados en 1 paso hacia:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            repackTargets.forEach { targetFmt ->
                                AssistChip(
                                    onClick = {
                                        val finalSet = if (selectedPaths.size == entries.size) emptySet() else selectedPaths.toSet()
                                        onRepackInOneStep(targetFmt, finalSet, password)
                                    },
                                    enabled = selectedPaths.isNotEmpty(),
                                    leadingIcon = {
                                        Icon(Icons.Filled.Archive, contentDescription = null, modifier = Modifier.size(14.dp))
                                    },
                                    label = { Text(targetFmt.label, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isLoading && selectedPaths.isNotEmpty(),
                onClick = {
                    val finalSet = if (selectedPaths.size == entries.size) {
                        emptySet()
                    } else {
                        selectedPaths.toSet()
                    }
                    onUpdateArchivePassword(password)
                    onApplySelection(finalSet)
                }
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Extraer Selección")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}
