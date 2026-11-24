package com.anywall.converter.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
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
import com.anywall.converter.core.AppAccentPalette
import com.anywall.converter.core.AppLanguageMode
import com.anywall.converter.core.AppThemeMode
import com.anywall.converter.core.FileShareHelper
import com.anywall.converter.core.LocalThumbnailLoader
import com.anywall.converter.core.OutputSubfolderMode
import com.anywall.converter.core.ScopedStorageManager
import com.anywall.converter.core.UriFileResolver
import com.anywall.converter.data.engines.ForensicMetadataHelper
import com.anywall.converter.domain.model.ConversionPreset
import com.anywall.converter.domain.model.FormatCategory
import com.anywall.converter.domain.model.HistoryRecord
import com.anywall.converter.domain.model.InputFileItem
import com.anywall.converter.ui.components.AboutCreditsDialog
import com.anywall.converter.ui.components.GitHubUpdaterDialog
import com.anywall.converter.ui.components.AnyWallCreditsCatalog
import com.anywall.converter.ui.components.CategoryQuickCard
import com.anywall.converter.ui.components.ConversionProgressCard
import com.anywall.converter.ui.components.HistoryItemCard
import com.anywall.converter.ui.components.OfflineToolsSheetDialog
import com.anywall.converter.ui.components.SettingsDialog

/**
 * `HomeScreen` (v0.4):
 * - Encabezado con el nombre **AnyWall** (`Universal Converter v0.4 • SynthesizerEnd`), soporte bilingüe (`ES`/`EN`).
 * - Selección de uno o múltiples archivos (`OpenMultipleDocuments`), importación de carpeta completa (`OpenDocumentTree`),
 *   captura rápida desde Cámara / Escáner (`TakePicture`) y acceso directo a la suite de **Herramientas**
 *   (Inspector Forense & SHA-256, Duplicados SHA-256, Códigos QR, Comparador Diff y Antes/Después).
 * - Barra de **Presets / Perfiles Rápidos** (Room v3).
 * - Tarjetas de acceso rápido para las 5 categorías (`Imágenes`, `Documentos / PDF`, `Audio / Video`, `Comprimidos`, `Datos y Seguridad`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    configureState: ConfigureUiState,
    recentHistory: List<HistoryRecord>,
    presets: List<ConversionPreset>,
    thumbnailLoader: LocalThumbnailLoader?,
    uriResolver: UriFileResolver,
    storageManager: ScopedStorageManager,
    forensicHelper: ForensicMetadataHelper,
    themeMode: AppThemeMode,
    useDynamicColor: Boolean = false,
    accentPalette: AppAccentPalette = AppAccentPalette.INDIGO_STUDIO,
    languageMode: AppLanguageMode,
    outputSubfolderMode: OutputSubfolderMode,
    customSubfolderName: String,
    biometricHistoryLockEnabled: Boolean,
    historyPinCode: String,
    autoCleanTempCache: Boolean,
    defaultStripExif: Boolean,
    tempCacheSizeBytes: Long,
    initialOpenToolsDialog: Boolean = false,
    onSelectThemeMode: (AppThemeMode) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit = {},
    onSelectAccentPalette: (AppAccentPalette) -> Unit = {},
    onSelectLanguageMode: (AppLanguageMode) -> Unit,
    onSelectOutputSubfolderMode: (OutputSubfolderMode) -> Unit,
    onUpdateCustomSubfolderName: (String) -> Unit,
    onToggleBiometricHistoryLock: (Boolean) -> Unit,
    onUpdateHistoryPinCode: (String) -> Unit,
    onToggleAutoCleanCache: (Boolean) -> Unit,
    onToggleDefaultStripExif: (Boolean) -> Unit,
    onRefreshTempCacheSize: () -> Unit,
    onClearTempCacheNow: () -> Unit,
    onApplyPreset: (ConversionPreset) -> Unit,
    onCreateCameraUri: () -> Uri,
    onFilesSelected: (List<Uri>) -> Unit,
    onFolderSelected: (Uri) -> Unit,
    onOpenConfigure: () -> Unit,
    onOpenHistory: () -> Unit,
    onCancelActiveJob: () -> Unit,
    onDeleteHistoryRecord: (HistoryRecord) -> Unit,
    onDeduplicateQueue: ((List<InputFileItem>) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isEn = languageMode.isEnglishActive()

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showCreditsDialog by remember { mutableStateOf(false) }
    var showGitHubUpdaterDialog by remember { mutableStateOf(false) }
    var showToolsDialog by remember { mutableStateOf(initialOpenToolsDialog) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

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

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val captured = pendingCameraUri
        if (success && captured != null) {
            onFilesSelected(listOf(captured))
        }
    }

    if (showSettingsDialog) {
        SettingsDialog(
            currentThemeMode = themeMode,
            useDynamicColor = useDynamicColor,
            accentPalette = accentPalette,
            currentLanguageMode = languageMode,
            outputSubfolderMode = outputSubfolderMode,
            customSubfolderName = customSubfolderName,
            biometricHistoryLockEnabled = biometricHistoryLockEnabled,
            historyPinCode = historyPinCode,
            autoCleanTempCache = autoCleanTempCache,
            defaultStripExif = defaultStripExif,
            tempCacheSizeBytes = tempCacheSizeBytes,
            onSelectThemeMode = onSelectThemeMode,
            onToggleDynamicColor = onToggleDynamicColor,
            onSelectAccentPalette = onSelectAccentPalette,
            onSelectLanguageMode = onSelectLanguageMode,
            onSelectOutputSubfolderMode = onSelectOutputSubfolderMode,
            onUpdateCustomSubfolderName = onUpdateCustomSubfolderName,
            onToggleBiometricHistoryLock = onToggleBiometricHistoryLock,
            onUpdateHistoryPinCode = onUpdateHistoryPinCode,
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

    if (showGitHubUpdaterDialog) {
        GitHubUpdaterDialog(
            isEn = isEn,
            onDismiss = { showGitHubUpdaterDialog = false }
        )
    }

    if (showToolsDialog) {
        OfflineToolsSheetDialog(
            initialTabIndex = 0,
            loadedInputs = configureState.inputFiles,
            currentImageEditSpec = configureState.options.imageEditSpec,
            currentQualityPercent = configureState.options.clampedQuality,
            uriResolver = uriResolver,
            storageManager = storageManager,
            forensicHelper = forensicHelper,
            onDeduplicateQueue = onDeduplicateQueue,
            onDismiss = { showToolsDialog = false }
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
                                text = "Universal Converter v0.4 • ${AnyWallCreditsCatalog.DEVELOPER_NAME}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showToolsDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = if (isEn) "Tools" else "Herramientas"
                        )
                    }
                    IconButton(onClick = onOpenHistory) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = if (isEn) "History" else "Abrir Historial"
                        )
                    }
                    IconButton(onClick = { showGitHubUpdaterDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = if (isEn) "Check for Updates (GitHub)" else "Buscar actualizaciones en GitHub"
                        )
                    }
                    IconButton(onClick = { showCreditsDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = if (isEn) "Credits" else "Acerca de AnyWall y Créditos"
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
                            contentDescription = if (isEn) "Settings" else "Ajustes"
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
                        Text(
                            text = if (isEn) "Universal File Converter" else "Conversor Universal de Archivos",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (isEn) {
                                "Convert files or entire folders, edit photos & PDF pages with touch gestures, extract .rar/.rar5 & comics (.cbz/.cbr), sign documents, encrypt AES-256 vaults, generate QR codes and process audio/video."
                            } else {
                                "Convierte archivos o carpetas enteras, edita fotos y páginas PDF con gestos táctiles, extrae .rar/.rar5 y cómics (.cbz/.cbr), firma documentos, cifra en Bóveda AES-256, genera QR y procesa audio/video."
                            },
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
                                text = if (isEn) "Select One or Multiple Files" else "Seleccionar Uno o Múltiples Archivos",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { folderTreeLauncher.launch(null) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CreateNewFolder,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isEn) "Folder" else "Carpeta",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            FilledTonalButton(
                                onClick = {
                                    val uri = onCreateCameraUri()
                                    pendingCameraUri = uri
                                    cameraLauncher.launch(uri)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isEn) "Camera Scan" else "Cámara / Escáner",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = { showToolsDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Build,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isEn) {
                                    "Tools: Metadata, SHA-256, Duplicates, QR, Diff & Slider"
                                } else {
                                    "Herramientas: Metadatos, SHA-256, Duplicados, QR, Diff y Slider"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            if (presets.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = if (isEn) "Quick Presets (1-Tap Profiles)" else "Perfiles Rápidos (Presets en 1 Toque)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            presets.forEach { preset ->
                                AssistChip(
                                    onClick = {
                                        onApplyPreset(preset)
                                        if (configureState.inputFiles.isNotEmpty()) {
                                            onOpenConfigure()
                                        } else {
                                            multiDocumentLauncher.launch(preset.category.mimeFilters)
                                        }
                                    },
                                    label = {
                                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                            Text(preset.name, fontWeight = FontWeight.Bold)
                                            Text(
                                                preset.description,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }
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
                                        text = if (isEn) {
                                            "Active Batch (${configureState.inputFiles.size} files)"
                                        } else {
                                            "Lote actual (${configureState.inputFiles.size} ${if (configureState.inputFiles.size == 1) "archivo" else "archivos"})"
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (isEn) {
                                            "Total size: ${configureState.formattedTotalInputSize} • Tap to configure"
                                        } else {
                                            "Peso total: ${configureState.formattedTotalInputSize} • Toca para configurar"
                                        },
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
                        text = if (isEn) "Quick Access by Category" else "Acceso Rápido por Categoría",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CategoryQuickCard(
                            category = FormatCategory.IMAGE,
                            isEnglish = isEn,
                            onClick = {
                                multiDocumentLauncher.launch(FormatCategory.IMAGE.mimeFilters)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        CategoryQuickCard(
                            category = FormatCategory.DOCUMENT,
                            isEnglish = isEn,
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
                            isEnglish = isEn,
                            onClick = {
                                multiDocumentLauncher.launch(FormatCategory.MEDIA.mimeFilters)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        CategoryQuickCard(
                            category = FormatCategory.ARCHIVE,
                            isEnglish = isEn,
                            onClick = {
                                multiDocumentLauncher.launch(FormatCategory.ARCHIVE.mimeFilters)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    CategoryQuickCard(
                        category = FormatCategory.DATA_TOOLS,
                        isEnglish = isEn,
                        onClick = {
                            multiDocumentLauncher.launch(FormatCategory.DATA_TOOLS.mimeFilters)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEn) "Conversion History" else "Historial de Conversiones",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = onOpenHistory) {
                        Text(if (isEn) "View all (${recentHistory.size})" else "Ver todo (${recentHistory.size})")
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
                                text = if (isEn) "No converted files yet" else "Aún no hay archivos convertidos",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isEn) {
                                    "Your converted files will be saved in Downloads/AnyWall/ and listed here."
                                } else {
                                    "Tus archivos convertidos se guardarán en Downloads/AnyWall/ y aparecerán aquí."
                                },
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
                                Text(if (isEn) "Open History" else "Abrir Historial")
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
                            text = if (isEn) "Developed by ${AnyWallCreditsCatalog.DEVELOPER_NAME}" else "Desarrollado por ${AnyWallCreditsCatalog.DEVELOPER_NAME}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "AnyWall: Universal Converter v0.4 • Licencia AGPL-3.0",
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
                            Text(if (isEn) "Credits & Open Source Licenses" else "Créditos y Licencias de Código Abierto")
                        }
                    }
                }
            }
        }
    }
}
