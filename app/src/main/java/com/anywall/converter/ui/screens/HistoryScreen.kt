package com.anywall.converter.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anywall.converter.core.FileShareHelper
import com.anywall.converter.core.LocalThumbnailLoader
import com.anywall.converter.domain.model.FormatCategory
import com.anywall.converter.domain.model.HistoryRecord
import com.anywall.converter.ui.components.HistoryItemCard

/**
 * `HistoryScreen` (v0.2):
 * - Lista persistente conectada a Room con miniaturas locales, insignia de ahorro de peso (`Antes → Después, -62%`),
 *   filtro por categoría, búsqueda por nombre/formato y modo de **Selección Múltiple** para compartir
 *   varios archivos a la vez (`ACTION_SEND_MULTIPLE`) o eliminarlos en lote.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    records: List<HistoryRecord>,
    thumbnailLoader: LocalThumbnailLoader?,
    selectedCategory: FormatCategory?,
    searchQuery: String,
    onSelectCategory: (FormatCategory?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onDeleteRecord: (HistoryRecord) -> Unit,
    onDeleteSelectedRecords: (List<HistoryRecord>) -> Unit,
    onClearAllHistory: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showClearAllDialog by remember { mutableStateOf(false) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }

    val selectedRecords = remember(records, selectedIds) {
        records.filter { it.id in selectedIds }
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Vaciar Historial y Archivos") },
            text = {
                Text(
                    "¿Deseas eliminar todos los registros del historial y borrar los archivos generados en Downloads/AnyWall/?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearAllDialog = false
                        isSelectionMode = false
                        selectedIds = emptySet()
                        onClearAllHistory()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Eliminar Todo")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = {
                        Text(
                            text = "${selectedIds.size} seleccionados",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                isSelectionMode = false
                                selectedIds = emptySet()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Salir de selección múltiple"
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                selectedIds = if (selectedIds.size == records.size) {
                                    emptySet()
                                } else {
                                    records.map { it.id }.toSet()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.SelectAll,
                                contentDescription = "Seleccionar todos"
                            )
                        }
                        IconButton(
                            onClick = {
                                if (selectedRecords.isNotEmpty()) {
                                    FileShareHelper.shareMultipleConvertedFiles(context, selectedRecords)
                                }
                            },
                            enabled = selectedRecords.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Compartir seleccionados"
                            )
                        }
                        IconButton(
                            onClick = {
                                if (selectedRecords.isNotEmpty()) {
                                    onDeleteSelectedRecords(selectedRecords)
                                    selectedIds = emptySet()
                                    isSelectionMode = false
                                }
                            },
                            enabled = selectedRecords.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Eliminar seleccionados",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Historial de Conversiones",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Carpeta: Downloads/AnyWall/ • ${records.size} ${if (records.size == 1) "archivo" else "archivos"}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                        if (records.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    isSelectionMode = true
                                    selectedIds = emptySet()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Checklist,
                                    contentDescription = "Selección múltiple"
                                )
                            }
                            IconButton(onClick = { showClearAllDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = "Vaciar todo el historial",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                placeholder = { Text("Buscar por nombre o formato (ej. PDF, MP4, TAR.XZ)…") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Limpiar búsqueda")
                        }
                    }
                }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { onSelectCategory(null) },
                    label = { Text("Todos") }
                )
                FormatCategory.entries.forEach { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = {
                            onSelectCategory(if (selectedCategory == category) null else category)
                        },
                        label = { Text(category.displayNameEs) }
                    )
                }
            }

            if (records.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Sin archivos en esta vista",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Todos los archivos que conviertas con AnyWall se almacenarán localmente en Downloads/AnyWall/.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = records,
                        key = { it.id }
                    ) { record ->
                        val isSelected = record.id in selectedIds
                        HistoryItemCard(
                            record = record,
                            thumbnailLoader = thumbnailLoader,
                            isSelectionMode = isSelectionMode,
                            isSelected = isSelected,
                            onToggleSelect = {
                                selectedIds = if (isSelected) {
                                    selectedIds - record.id
                                } else {
                                    selectedIds + record.id
                                }
                            },
                            onOpen = { FileShareHelper.openConvertedFile(context, record) },
                            onShare = { FileShareHelper.shareConvertedFile(context, record) },
                            onDelete = { onDeleteRecord(record) }
                        )
                    }
                }
            }
        }
    }
}
