package com.anywall.converter.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anywall.converter.core.AppThemeMode
import com.anywall.converter.core.FileShareHelper
import com.anywall.converter.core.LocalThumbnailLoader
import com.anywall.converter.domain.model.FormatCategory
import com.anywall.converter.domain.model.HistoryRecord
import com.anywall.converter.ui.components.AboutCreditsDialog
import com.anywall.converter.ui.components.AnyWallCreditsCatalog
import com.anywall.converter.ui.components.CategoryQuickCard
import com.anywall.converter.ui.components.ConversionProgressCard
import com.anywall.converter.ui.components.HistoryItemCard
import com.anywall.converter.ui.components.OfflinePrivacyBadge
import com.anywall.converter.ui.components.SettingsDialog

/**
 * `HomeScreen` (v0.2):
 * - Encabezado con el nombre **AnyWall** (`Universal Converter v0.2 • SynthesizerEnd`) y la insignia `"100% Offline • Privado"`.
 * - Botón principal para seleccionar uno o múltiples archivos (`OpenMultipleDocuments`) y botón secundario para importar una carpeta completa (`OpenDocumentTree`).
 * - Tarjetas de acceso rápido por categoría (`Imágenes`, `Documentos / PDF`, `Audio / Video`, `Comprimidos`).
 * - Acceso directo al Historial de archivos convertidos, Ajustes locales y Créditos/Licencias Open Source.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    configureState: ConfigureUiState,
    recentHistory: List<HistoryRecord>,
    thumbnailLoader: LocalThumbnailLoader?,
    themeMode: AppThemeMode,
    autoCleanTempCache: Boolean,
    defaultStripExif: Boolean,
    tempCacheSizeBytes: Long,
    onSelectThemeMode: (AppThemeMode) -> Unit,
    onToggleAutoCleanCache: (Boolean) -> Unit,
    onToggleDefaultStripExif: (Boolean) -> Unit,
    onRefreshTempCacheSize: () -> Unit,
    onClearTempCacheNow: () -> Unit,
    onFilesSelected: (List<Uri>) -> Unit,
    onFolderSelected: (Uri) -> Unit,
    onOpenConfigure: () -> Unit,
    onOpenHistory: () -> Unit,
    onCancelActiveJob: () -> Unit,
    onDeleteHistoryRecord: (HistoryRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showCreditsDialog by remember { mutableStateOf(false) }

    val multiDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onFilesSelected(uris)
        }
    }

    val folderTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            onFolderSelected(treeUri)
        }
    }

    if (showSettingsDialog) {
        SettingsDialog(
            currentThemeMode = themeMode,
            autoCleanTempCache = autoCleanTempCache,
            defaultStripExif = defaultStripExif,
            tempCacheSizeBytes = tempCacheSizeBytes,
            onSelectThemeMode = onSelectThemeMode,
            onToggleAutoCleanCache = onToggleAutoCleanCache,
            onToggleDefaultStripExif = onToggleDefaultStripExif,
            onClearTempCacheNow = onClearTempCacheNow,
            onOpenCredits = {
                showSettingsDialog = false
                showCreditsDialog = true
            },
            onDismiss = { showSettingsDialog = false }
        )
    }

    if (showCreditsDialog) {
        AboutCreditsDialog(
            onDismiss = { showCreditsDialog = false }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "AnyWall",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Universal Converter v0.2 • ${AnyWallCreditsCatalog.DEVELOPER_NAME}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Abrir Historial de Conversiones"
                        )
                    }
                    IconButton(onClick = { showCreditsDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Acerca de AnyWall y Créditos Open Source"
                        )
                    }
                    IconButton(
                        onClick = {
                            onRefreshTempCacheSize()
                            showSettingsDialog = true
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Ajustes Locales"
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
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                                    )
                                )
                            )
                            .padding(20.dp)
                    ) {
                        OfflinePrivacyBadge()

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Conversor Universal en tu Dispositivo",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Convierte múltiples archivos o carpetas enteras, edita páginas de PDF en vista previa, procesa imágenes, video, audio y comprimidos 100% sin conexión a Internet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = {
                                multiDocumentLauncher.launch(arrayOf("*/*"))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(16.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddCircleOutline,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Seleccionar Uno o Múltiples Archivos",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        FilledTonalButton(
                            onClick = {
                                folderTreeLauncher.launch(null)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CreateNewFolder,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Importar Carpeta Completa al Lote",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            if (configureState.activeJob.isRunning ||
                configureState.activeJob.isCompleted ||
                configureState.activeJob.isFailed
            ) {
                item {
                    ConversionProgressCard(
                        jobState = configureState.activeJob,
                        onCancel = onCancelActiveJob,
                        onOpenHistory = onOpenHistory
                    )
                }
            }

            if (configureState.inputFiles.isNotEmpty()) {
                item {
                    OutlinedCard(
                        onClick = onOpenConfigure,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(26.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Lote actual (${configureState.inputFiles.size} ${if (configureState.inputFiles.size == 1) "archivo" else "archivos"})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Peso total: ${configureState.formattedTotalInputSize} • Toca para configurar",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Continuar configuración",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Acceso Rápido por Categoría",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CategoryQuickCard(
                            category = FormatCategory.IMAGE,
                            onClick = {
                                multiDocumentLauncher.launch(FormatCategory.IMAGE.mimeFilters)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        CategoryQuickCard(
                            category = FormatCategory.DOCUMENT,
                            onClick = {
                                multiDocumentLauncher.launch(FormatCategory.DOCUMENT.mimeFilters)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CategoryQuickCard(
                            category = FormatCategory.MEDIA,
                            onClick = {
                                multiDocumentLauncher.launch(FormatCategory.MEDIA.mimeFilters)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        CategoryQuickCard(
                            category = FormatCategory.ARCHIVE,
                            onClick = {
                                multiDocumentLauncher.launch(FormatCategory.ARCHIVE.mimeFilters)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Historial de Conversiones",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = onOpenHistory) {
                        Text("Ver todo (${recentHistory.size})")
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (recentHistory.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Aún no hay archivos convertidos",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tus archivos convertidos se guardarán en Downloads/AnyWall/ y aparecerán aquí.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            FilledTonalButton(onClick = onOpenHistory) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Abrir Historial")
                            }
                        }
                    }
                }
            } else {
                items(
                    items = recentHistory.take(3),
                    key = { it.id }
                ) { record ->
                    HistoryItemCard(
                        record = record,
                        thumbnailLoader = thumbnailLoader,
                        onOpen = { FileShareHelper.openConvertedFile(context, record) },
                        onShare = { FileShareHelper.shareConvertedFile(context, record) },
                        onDelete = { onDeleteHistoryRecord(record) }
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Desarrollado por ${AnyWallCreditsCatalog.DEVELOPER_NAME}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "AnyWall: Universal Converter v0.2 • Licencia AGPL-3.0",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        OutlinedButton(onClick = { showCreditsDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Créditos y Licencias de Código Abierto")
                        }
                    }
                }
            }
        }
    }
}
