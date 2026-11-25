package com.anywall.converter.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.anywall.converter.ui.components.AnyWallCreditsCatalog
import com.anywall.converter.ui.components.CategoryQuickCard
import com.anywall.converter.ui.components.ConversionProgressCard
import com.anywall.converter.ui.components.FormatSpecificPickerOption
import com.anywall.converter.ui.components.GitHubUpdaterDialog
import com.anywall.converter.ui.components.HistoryItemCard
import com.anywall.converter.ui.components.SpecificFormatPickerDialog

/**
 * `HomeScreen` (v0.5):
 * - Arranca directamente con la **Selección Rápida por Categoría y Formato Específico** (`Imágenes`, `Documentos / PDF`, `Audio / Video`, `Comprimidos`),
 *   sin tarjeta redundante de presentación ni barra de presets.
 * - Al pulsar cualquier chip de formato (`JPG`, `PNG`, `PDF`, `DOCX`, `MP4`, `MP3`, `RAR`, `ZIP`, etc.) o elegirlo en el selector de la categoría,
 *   solicita al sistema únicamente archivos de ese formato específico.
 * - La categoría de **Datos y Seguridad** vive ahora en su propia pestaña de la barra inferior.
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
    onOpenToolsTab: (() -> Unit)? = null,
    onCancelActiveJob: () -> Unit,
    onDeleteHistoryRecord: (HistoryRecord) -> Unit,
    onDeduplicateQueue: ((List<InputFileItem>) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isEn = languageMode.isEnglishActive()

    var showGitHubUpdaterDialog by remember { mutableStateOf(false) }
    var activeCategoryForFormatDialog by remember { mutableStateOf<FormatCategory?>(null) }
    var pendingFormatOption by remember { mutableStateOf<FormatSpecificPickerOption?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val multiDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val expectedOpt = pendingFormatOption
            if (expectedOpt != null) {
                val matchingUris = uris.filter { uri ->
                    val item = uriResolver.resolveInputFile(uri)
                    item != null && item.format in expectedOpt.acceptedFormats
                }
                if (matchingUris.isNotEmpty()) {
                    if (matchingUris.size < uris.size) {
                        val skipped = uris.size - matchingUris.size
                        Toast.makeText(
                            context,
                            if (isEn) "Ignored $skipped file(s) not matching ${expectedOpt.label}"
                            else "Se omitieron $skipped archivo(s) que no son ${expectedOpt.label}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    onFilesSelected(matchingUris)
                } else {
                    Toast.makeText(
                        context,
                        if (isEn) "Please select ${expectedOpt.label} (${expectedOpt.extHint}) files"
                        else "Selecciona archivos de formato ${expectedOpt.label} (${expectedOpt.extHint})",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                onFilesSelected(uris)
            }
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

    fun launchPickerForSpecificFormat(option: FormatSpecificPickerOption) {
        pendingFormatOption = option
        activeCategoryForFormatDialog = null
        multiDocumentLauncher.launch(option.mimeTypes)
    }

    if (showGitHubUpdaterDialog) {
        GitHubUpdaterDialog(
            isEn = isEn,
            onDismiss = { showGitHubUpdaterDialog = false }
        )
    }

    val dialogCat = activeCategoryForFormatDialog
    if (dialogCat != null) {
        SpecificFormatPickerDialog(
            category = dialogCat,
            isEnglish = isEn,
            onSelectFormatOption = { opt -> launchPickerForSpecificFormat(opt) },
            onDismiss = { activeCategoryForFormatDialog = null }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "AnyWall",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        text = "v0.5",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = AnyWallCreditsCatalog.DEVELOPER_NAME,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showGitHubUpdaterDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = if (isEn) "Check for Updates (GitHub)" else "Buscar actualizaciones en GitHub",
                            tint = MaterialTheme.colorScheme.primary
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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Progreso de conversión en curso (si hay un trabajo activo)
            if (configureState.activeJob.isRunning) {
                item {
                    ConversionProgressCard(
                        jobState = configureState.activeJob,
                        onCancel = onCancelActiveJob,
                        onOpenHistory = onOpenHistory
                    )
                }
            }

            // 2. Banner de Lote Activo (si ya hay archivos en la cola)
            if (configureState.inputFiles.isNotEmpty()) {
                item {
                    Surface(
                        onClick = onOpenConfigure,
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = if (isEn) {
                                        "Active Batch (${configureState.inputFiles.size} files • ${configureState.formattedTotalInputSize})"
                                    } else {
                                        "Lote activo (${configureState.inputFiles.size} archivos • ${configureState.formattedTotalInputSize})"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = if (isEn) {
                                        "Tap to resume conversion or open touch studio"
                                    } else {
                                        "Toca para continuar configurando o editar en el estudio"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 3. Selección Rápida por Categoría y Formato Específico (Lo primero que se ve)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = if (isEn) "Select by Category & Format" else "Selección Rápida por Categoría",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = if (isEn) {
                            "Tap a format badge directly or tap a card to choose the exact input format"
                        } else {
                            "Toca directamente el formato que buscas o abre la categoría para elegirlo"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                CategoryQuickCard(
                    category = FormatCategory.IMAGE,
                    isEnglish = isEn,
                    onClick = { activeCategoryForFormatDialog = FormatCategory.IMAGE },
                    onQuickPickSpecificFormat = { opt -> launchPickerForSpecificFormat(opt) }
                )
            }

            item {
                CategoryQuickCard(
                    category = FormatCategory.DOCUMENT,
                    isEnglish = isEn,
                    onClick = { activeCategoryForFormatDialog = FormatCategory.DOCUMENT },
                    onQuickPickSpecificFormat = { opt -> launchPickerForSpecificFormat(opt) }
                )
            }

            item {
                CategoryQuickCard(
                    category = FormatCategory.MEDIA,
                    isEnglish = isEn,
                    onClick = { activeCategoryForFormatDialog = FormatCategory.MEDIA },
                    onQuickPickSpecificFormat = { opt -> launchPickerForSpecificFormat(opt) }
                )
            }

            item {
                CategoryQuickCard(
                    category = FormatCategory.ARCHIVE,
                    isEnglish = isEn,
                    onClick = { activeCategoryForFormatDialog = FormatCategory.ARCHIVE },
                    onQuickPickSpecificFormat = { opt -> launchPickerForSpecificFormat(opt) }
                )
            }

            // 4. Acciones secundarias compactas: Importar Carpeta o Capturar con Cámara
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = { folderTreeLauncher.launch(null) },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CreateNewFolder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isEn) "Import Folder" else "Importar Carpeta",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
                            .height(44.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isEn) "Camera" else "Cámara",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 5. Conversiones recientes (máximo 3)
            if (recentHistory.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isEn) "Recent Conversions" else "Conversiones Recientes",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        TextButton(onClick = onOpenHistory) {
                            Text(
                                text = if (isEn) "View all (${recentHistory.size})" else "Ver todo (${recentHistory.size})",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

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
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}
