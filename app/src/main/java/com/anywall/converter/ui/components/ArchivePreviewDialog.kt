package com.anywall.converter.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anywall.converter.data.engines.ArchiveEngineImpl
import com.anywall.converter.data.engines.ArchiveEntryPreview
import com.anywall.converter.domain.model.InputFileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Explorador Previo de Archivos Comprimidos (`ArchivePreviewDialog` - v0.3):
 * Permite inspeccionar las entradas dentro de un `.zip`, `.tar*` o `.7z` antes de extraer,
 * filtrar por nombre y seleccionar qué archivos específicos descomprimir.
 */
@Composable
fun ArchivePreviewDialog(
    archiveItem: InputFileItem,
    archiveEngine: ArchiveEngineImpl,
    initialSelectedEntries: Set<String>,
    onApplySelection: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var entries by remember { mutableStateOf<List<ArchiveEntryPreview>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var filterQuery by remember { mutableStateOf("") }
    val selectedPaths = remember { mutableStateListOf<String>() }

    LaunchedEffect(archiveItem.uri) {
        isLoading = true
        val loaded = withContext(Dispatchers.IO) {
            archiveEngine.inspectArchiveEntries(archiveItem)
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
                    text = "Explorador de Comprimido",
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
                    Text("No se pudieron listar entradas individuales en este archivo.")
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
                            .height(260.dp)
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
                                    .padding(vertical = 4.dp),
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
                                        text = entry.entryPath,
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
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isLoading && selectedPaths.isNotEmpty(),
                onClick = {
                    val finalSet = if (selectedPaths.size == entries.size) {
                        emptySet() // vacío = extraer todo
                    } else {
                        selectedPaths.toSet()
                    }
                    onApplySelection(finalSet)
                }
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Aplicar Selección")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}
