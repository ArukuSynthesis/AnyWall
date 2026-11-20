package com.anywall.converter.ui.screens

import android.app.Activity
import android.app.KeyguardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.anywall.converter.core.FileShareHelper
import com.anywall.converter.core.LocalThumbnailLoader
import com.anywall.converter.domain.model.FormatCategory
import com.anywall.converter.domain.model.HistoryRecord
import com.anywall.converter.domain.model.InputFileItem
import com.anywall.converter.ui.components.HistoryItemCard

/**
 * `HistoryScreen` (v0.3):
 * - Tarjeta superior de **Estadísticas Locales de Ahorro**: total de archivos convertidos offline,
 *   volumen total procesado y espacio total ahorrado (`MB/GB`).
 * - Puerta de **Bloqueo Biométrico / Credencial del Dispositivo / PIN Local** opcional para privacidad.
 * - Lista persistente conectada a Room con miniaturas locales, insignia de ahorro de peso,
 *   filtro por categoría, búsqueda y modo de **Selección Múltiple** (`ACTION_SEND_MULTIPLE`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    records: List<HistoryRecord>,
    allRecordsForStats: List<HistoryRecord> = records,
    thumbnailLoader: LocalThumbnailLoader?,
    selectedCategory: FormatCategory?,
    searchQuery: String,
    biometricLockEnabled: Boolean = false,
    expectedPinCode: String = "",
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

    var isUnlocked by remember(biometricLockEnabled) { mutableStateOf(!biometricLockEnabled) }
    var typedPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    val deviceCredentialLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            isUnlocked = true
            pinError = false
        }
    }

    val selectedRecords = remember(records, selectedIds) {
        records.filter { it.id in selectedIds }
    }

    val totalSavedBytes = remember(allRecordsForStats) {
        allRecordsForStats.sumOf { rec ->
            if (rec.sourceSizeBytes > rec.outputSizeBytes && rec.outputSizeBytes > 0L) {
                rec.sourceSizeBytes - rec.outputSizeBytes
            } else {
                0L
            }
        }
    }
    val totalGeneratedBytes = remember(allRecordsForStats) {
        allRecordsForStats.sumOf { it.outputSizeBytes }
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
            if (isSelectionMode && isUnlocked) {
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
                        if (records.isNotEmpty() && isUnlocked) {
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
        if (!isUnlocked) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Historial Protegido",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Desbloquea con la credencial/huella de tu dispositivo o introduce tu PIN local configurado en Ajustes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = {
                        val km = context.getSystemService(KeyguardManager::class.java)
                        @Suppress("DEPRECATION")
                        val intent = km?.createConfirmDeviceCredentialIntent(
                            "Desbloquear Historial de AnyWall",
                            "Confirma tu huella, patrón o PIN del dispositivo"
                        )
                        if (intent != null) {
                            deviceCredentialLauncher.launch(intent)
                        } else if (expectedPinCode.isBlank()) {
                            isUnlocked = true
                        }
                    }
                ) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Desbloquear con Huella / Dispositivo")
                }

                if (expectedPinCode.isNotBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = typedPin,
                        onValueChange = {
                            typedPin = it
                            pinError = false
                        },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        label = { Text("PIN Local de AnyWall") },
                        isError = pinError
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            if (typedPin.trim() == expectedPinCode.trim()) {
                                isUnlocked = true
                                pinError = false
                            } else {
                                pinError = true
                            }
                        }
                    ) {
                        Text("Verificar PIN Local")
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Tarjeta de Estadísticas Locales de Ahorro (v0.3)
                if (allRecordsForStats.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Savings,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Estadísticas Offline: ${allRecordsForStats.size} conversiones",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Espacio ahorrado: ${InputFileItem.formatBytes(totalSavedBytes)} • Generado: ${InputFileItem.formatBytes(totalGeneratedBytes)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    placeholder = { Text("Buscar por nombre o formato (ej. PDF, MP4, TAR.XZ, AES)…") },
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
}
