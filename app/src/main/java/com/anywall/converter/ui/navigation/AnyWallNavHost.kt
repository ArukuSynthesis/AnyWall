package com.anywall.converter.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.anywall.converter.core.AnyWallQuickTileService
import com.anywall.converter.ui.screens.ConfigureScreen
import com.anywall.converter.ui.screens.HistoryScreen
import com.anywall.converter.ui.screens.HomeScreen
import com.anywall.converter.ui.screens.MainViewModel
import com.anywall.converter.ui.screens.SecurityAndDataScreen
import com.anywall.converter.ui.screens.SettingsScreen
import com.anywall.converter.ui.screens.ToolsHubScreen

/**
 * Grafo de navegación principal de AnyWall (v0.6 UX) con Barra de Navegación Inferior de 5 pestañas:
 * 1. **Convertir** (`HOME`: Selección rápida por categoría y formato específico, más `CONFIGURE`)
 * 2. **Estudios** (`TOOLS` -> `ToolsHubScreen` con los 6 Estudios Táctiles y Renombrador Masivo)
 * 3. **Seguridad** (`SECURITY_DATA` -> `SecurityAndDataScreen` con Bóveda `.anywall`, Datos, QR, Forense, Duplicados y Diff)
 * 4. **Historial** (`HISTORY` -> `HistoryScreen`)
 * 5. **Ajustes** (`SETTINGS` -> `SettingsScreen`)
 */
@Composable
fun AnyWallNavHost(
    viewModel: MainViewModel,
    initialQuickAction: String? = null,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val configureState by viewModel.configureState.collectAsStateWithLifecycle()
    val allHistory by viewModel.allHistoryRecords.collectAsStateWithLifecycle()
    val filteredHistory by viewModel.filteredHistoryRecords.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedHistoryCategory.collectAsStateWithLifecycle()
    val searchQuery by viewModel.historySearchQuery.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val useDynamicColor by viewModel.useDynamicColor.collectAsStateWithLifecycle()
    val accentPalette by viewModel.accentPalette.collectAsStateWithLifecycle()
    val languageMode by viewModel.languageMode.collectAsStateWithLifecycle()
    val outputSubfolderMode by viewModel.outputSubfolderMode.collectAsStateWithLifecycle()
    val customSubfolderName by viewModel.customSubfolderName.collectAsStateWithLifecycle()
    val biometricHistoryLockEnabled by viewModel.biometricHistoryLockEnabled.collectAsStateWithLifecycle()
    val historyPinCode by viewModel.historyPinCode.collectAsStateWithLifecycle()
    val autoCleanTempCache by viewModel.autoCleanTempCache.collectAsStateWithLifecycle()
    val defaultStripExif by viewModel.defaultStripExif.collectAsStateWithLifecycle()
    val tempCacheSizeBytes by viewModel.tempCacheSizeBytes.collectAsStateWithLifecycle()
    val pendingVaultFiles by viewModel.pendingVaultFiles.collectAsStateWithLifecycle()

    val isEn = languageMode.isEnglishActive()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: AnyWallDestination.HOME.route

    val showBottomBar = currentRoute in setOf(
        AnyWallDestination.HOME.route,
        AnyWallDestination.TOOLS.route,
        AnyWallDestination.SECURITY_DATA.route,
        AnyWallDestination.HISTORY.route,
        AnyWallDestination.SETTINGS.route
    )

    fun navigateToBottomTab(destination: AnyWallDestination) {
        if (destination == AnyWallDestination.SETTINGS) {
            viewModel.refreshTempCacheSize()
        }
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    LaunchedEffect(initialQuickAction) {
        when (initialQuickAction) {
            AnyWallQuickTileService.ACTION_OPEN_HISTORY -> {
                navigateToBottomTab(AnyWallDestination.HISTORY)
            }
            AnyWallQuickTileService.ACTION_OPEN_TOOLS -> {
                navigateToBottomTab(AnyWallDestination.SECURITY_DATA)
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.navigateToConfigureFlow.collect {
            val activeRoute = navController.currentDestination?.route
            if (activeRoute != AnyWallDestination.CONFIGURE.route) {
                navController.navigate(AnyWallDestination.CONFIGURE.route) {
                    launchSingleTop = true
                }
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.navigateToSecurityVaultFlow.collect {
            navigateToBottomTab(AnyWallDestination.SECURITY_DATA)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                Column {
                    HorizontalDivider(
                        thickness = 0.6.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp
                    ) {
                        val bottomTabs = listOf(
                            AnyWallDestination.HOME,
                            AnyWallDestination.TOOLS,
                            AnyWallDestination.SECURITY_DATA,
                            AnyWallDestination.HISTORY,
                            AnyWallDestination.SETTINGS
                        )
                        bottomTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            val tabIcon = when (tab) {
                                AnyWallDestination.HOME -> Icons.Default.Bolt
                                AnyWallDestination.TOOLS -> Icons.Default.AutoFixHigh
                                AnyWallDestination.SECURITY_DATA -> Icons.Default.Security
                                AnyWallDestination.HISTORY -> Icons.Default.History
                                AnyWallDestination.SETTINGS -> Icons.Default.Settings
                                AnyWallDestination.CONFIGURE -> Icons.Default.Bolt
                            }

                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToBottomTab(tab) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                icon = {
                                    if (tab == AnyWallDestination.HOME && configureState.inputFiles.isNotEmpty()) {
                                        BadgedBox(
                                            badge = {
                                                Badge {
                                                    Text(configureState.inputFiles.size.toString())
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = tabIcon,
                                                contentDescription = tab.localizedTitle(isEn)
                                            )
                                        }
                                    } else {
                                        Icon(
                                            imageVector = tabIcon,
                                            contentDescription = tab.localizedTitle(isEn)
                                        )
                                    }
                                },
                                label = {
                                    Text(
                                        text = tab.localizedTitle(isEn),
                                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { outerPadding ->
        NavHost(
            navController = navController,
            startDestination = AnyWallDestination.HOME.route,
            enterTransition = { fadeIn(animationSpec = tween(180)) },
            exitTransition = { fadeOut(animationSpec = tween(140)) },
            popEnterTransition = { fadeIn(animationSpec = tween(180)) },
            popExitTransition = { fadeOut(animationSpec = tween(140)) },
            modifier = Modifier
                .fillMaxSize()
                .padding(outerPadding)
        ) {
            composable(AnyWallDestination.HOME.route) {
                HomeScreen(
                    configureState = configureState,
                    recentHistory = allHistory,
                    thumbnailLoader = viewModel.thumbnailLoader,
                    uriResolver = viewModel.uriFileResolver,
                    storageManager = viewModel.scopedStorageManager,
                    forensicHelper = viewModel.forensicMetadataHelper,
                    themeMode = themeMode,
                    useDynamicColor = useDynamicColor,
                    accentPalette = accentPalette,
                    languageMode = languageMode,
                    outputSubfolderMode = outputSubfolderMode,
                    customSubfolderName = customSubfolderName,
                    biometricHistoryLockEnabled = biometricHistoryLockEnabled,
                    historyPinCode = historyPinCode,
                    autoCleanTempCache = autoCleanTempCache,
                    defaultStripExif = defaultStripExif,
                    tempCacheSizeBytes = tempCacheSizeBytes,
                    initialOpenToolsDialog = false,
                    onSelectThemeMode = viewModel::setThemeMode,
                    onToggleDynamicColor = viewModel::setUseDynamicColor,
                    onSelectAccentPalette = viewModel::setAccentPalette,
                    onSelectLanguageMode = viewModel::setLanguageMode,
                    onSelectOutputSubfolderMode = viewModel::setOutputSubfolderMode,
                    onUpdateCustomSubfolderName = viewModel::setCustomSubfolderName,
                    onToggleBiometricHistoryLock = viewModel::setBiometricHistoryLockEnabled,
                    onUpdateHistoryPinCode = viewModel::setHistoryPinCode,
                    onToggleAutoCleanCache = viewModel::setAutoCleanTempCache,
                    onToggleDefaultStripExif = viewModel::setDefaultStripExif,
                    onRefreshTempCacheSize = viewModel::refreshTempCacheSize,
                    onClearTempCacheNow = viewModel::clearTempCacheNow,
                    onCreateCameraUri = viewModel::createCameraCaptureUri,
                    onFilesSelected = { uris ->
                        viewModel.addInputUris(
                            uris = uris,
                            replaceExisting = true,
                            navigateToConfigure = true
                        )
                    },
                    onFolderSelected = { treeUri ->
                        viewModel.addInputFolderTree(
                            treeUri = treeUri,
                            replaceExisting = true,
                            navigateToConfigure = true
                        )
                    },
                    onOpenConfigure = {
                        navController.navigate(AnyWallDestination.CONFIGURE.route) {
                            launchSingleTop = true
                        }
                    },
                    onOpenHistory = {
                        navigateToBottomTab(AnyWallDestination.HISTORY)
                    },
                    onOpenToolsTab = {
                        navigateToBottomTab(AnyWallDestination.TOOLS)
                    },
                    onCancelActiveJob = viewModel::cancelActiveConversion,
                    onDeleteHistoryRecord = { record ->
                        viewModel.deleteHistoryRecord(record, deletePhysicalFile = true)
                    },
                    onDeduplicateQueue = viewModel::replaceInputFilesList
                )
            }

            composable(AnyWallDestination.TOOLS.route) {
                ToolsHubScreen(
                    configureState = configureState,
                    languageMode = languageMode,
                    thumbnailLoader = viewModel.thumbnailLoader,
                    uriResolver = viewModel.uriFileResolver,
                    storageManager = viewModel.scopedStorageManager,
                    forensicHelper = viewModel.forensicMetadataHelper,
                    archiveEngine = viewModel.archiveEngineImpl,
                    onLoadFilesIntoQueue = { items, navigate ->
                        viewModel.replaceInputFilesList(items)
                        if (navigate) {
                            navController.navigate(AnyWallDestination.CONFIGURE.route) {
                                launchSingleTop = true
                            }
                        }
                    },
                    onApplyVisualImageEdits = viewModel::applyVisualImageEdits,
                    onApplyVisualPdfEdits = viewModel::applyVisualPdfPageEdits,
                    onUpdatePdfSignaturePngPath = viewModel::updatePdfSignaturePngPath,
                    onUpdateTrimRangeMillis = viewModel::updateTrimRangeMillis,
                    onUpdateVideoCropSpec = viewModel::updateVideoCropSpec,
                    onUpdateMuteVideoAudio = viewModel::updateMuteVideoAudio,
                    onUpdateSelectedArchiveEntries = viewModel::updateSelectedArchiveEntries,
                    onUpdateArchivePassword = viewModel::updateArchivePassword,
                    onSelectTargetFormat = viewModel::selectTargetFormat,
                    onStartConversion = viewModel::startBatchConversion,
                    onOpenConfigure = {
                        navController.navigate(AnyWallDestination.CONFIGURE.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(AnyWallDestination.SECURITY_DATA.route) {
                SecurityAndDataScreen(
                    configureState = configureState,
                    languageMode = languageMode,
                    uriResolver = viewModel.uriFileResolver,
                    storageManager = viewModel.scopedStorageManager,
                    forensicHelper = viewModel.forensicMetadataHelper,
                    pendingVaultFiles = pendingVaultFiles,
                    onConsumePendingVaultFiles = viewModel::consumePendingVaultFiles,
                    onFilesSelectedForConversion = { uris, forcedTarget ->
                        viewModel.addInputUris(
                            uris = uris,
                            replaceExisting = true,
                            navigateToConfigure = true
                        )
                        if (forcedTarget != null) {
                            viewModel.selectTargetFormat(forcedTarget)
                        }
                    },
                    onDeduplicateQueue = viewModel::replaceInputFilesList,
                    onRecordDirectConversions = viewModel::recordDirectConversionResults
                )
            }

            composable(AnyWallDestination.CONFIGURE.route) {
                ConfigureScreen(
                    state = configureState,
                    recentHistory = allHistory,
                    thumbnailLoader = viewModel.thumbnailLoader,
                    uriResolver = viewModel.uriFileResolver,
                    storageManager = viewModel.scopedStorageManager,
                    forensicHelper = viewModel.forensicMetadataHelper,
                    archiveEngine = viewModel.archiveEngineImpl,
                    onCreateCameraUri = viewModel::createCameraCaptureUri,
                    onAddMoreFiles = { uris ->
                        viewModel.addInputUris(
                            uris = uris,
                            replaceExisting = false,
                            navigateToConfigure = false
                        )
                    },
                    onAddFolderTree = { treeUri ->
                        viewModel.addInputFolderTree(
                            treeUri = treeUri,
                            replaceExisting = false,
                            navigateToConfigure = false
                        )
                    },
                    onMoveFileUp = viewModel::moveInputFileUp,
                    onMoveFileDown = viewModel::moveInputFileDown,
                    onMoveFile = viewModel::moveInputFile,
                    onReplaceInputFilesList = viewModel::replaceInputFilesList,
                    onDeduplicateQueue = viewModel::deduplicateCurrentInputFiles,
                    onDismissSmartShareSheet = viewModel::dismissSmartShareSheet,
                    onSortByName = viewModel::sortInputFilesByName,
                    onSortBySize = viewModel::sortInputFilesBySize,
                    onReverseOrder = viewModel::reverseInputFilesOrder,
                    onApplyVisualPdfEdits = viewModel::applyVisualPdfPageEdits,
                    onClearVisualPdfEdits = viewModel::clearVisualPdfPageEdits,
                    onApplyVisualImageEdits = viewModel::applyVisualImageEdits,
                    onClearVisualImageEdits = viewModel::clearVisualImageEdits,
                    onRemoveFile = viewModel::removeInputFile,
                    onClearAllFiles = viewModel::clearInputFiles,
                    onSelectTargetFormat = viewModel::selectTargetFormat,
                    onUpdateQuality = viewModel::updateQualityPercent,
                    onUpdateMaxDimension = viewModel::updateMaxDimensionPx,
                    onUpdateImageRotation = viewModel::updateImageRotationDegrees,
                    onUpdateStripExif = viewModel::updateStripExifMetadata,
                    onUpdateTargetImageMaxKb = viewModel::updateTargetImageMaxSizeKb,
                    onUpdateImageBatchLayoutMode = viewModel::updateImageBatchLayoutMode,
                    onUpdateAnimatedGifFps = viewModel::updateAnimatedGifFps,
                    onUpdateVideoResolution = viewModel::updateVideoResolution,
                    onUpdateMuteVideoAudio = viewModel::updateMuteVideoAudio,
                    onUpdateTrimStartText = viewModel::updateTrimStartText,
                    onUpdateTrimEndText = viewModel::updateTrimEndText,
                    onUpdateTrimRangeMillis = viewModel::updateTrimRangeMillis,
                    onUpdateVideoCropSpec = viewModel::updateVideoCropSpec,
                    onUpdateVideoPlaybackSpeed = viewModel::updateVideoPlaybackSpeed,
                    onUpdateVideoRotation = viewModel::updateVideoRotationDegrees,
                    onUpdateVideoFlipHorizontal = viewModel::updateVideoFlipHorizontal,
                    onUpdateTargetVideoMaxMb = viewModel::updateTargetVideoMaxSizeMb,
                    onUpdateMergeMediaSequence = viewModel::updateMergeMediaSequence,
                    onUpdateAudioBitrateKbps = viewModel::updateAudioBitrateKbps,
                    onUpdateAudioFadeSeconds = viewModel::updateAudioFadeSeconds,
                    onUpdateExportAsRingtone = viewModel::updateExportAsRingtone,
                    onUpdateAudioNormalizeAutoGain = viewModel::updateAudioNormalizeAutoGain,
                    onUpdateAudioMetadataSpec = viewModel::updateAudioMetadataSpec,
                    onUpdateCustomOutputName = viewModel::updateCustomOutputBaseName,
                    onUpdateMergeSinglePdf = viewModel::updateMergeIntoSinglePdf,
                    onUpdatePdfActionMode = viewModel::updatePdfActionMode,
                    onUpdatePageRangeSpec = viewModel::updatePageRangeSpec,
                    onUpdatePdfSplitChunkPages = viewModel::updatePdfSplitChunkPages,
                    onUpdatePdfPassword = viewModel::updatePdfPassword,
                    onUpdatePdfPageSize = viewModel::updatePdfPageSize,
                    onUpdatePdfPageOrientation = viewModel::updatePdfPageOrientation,
                    onUpdatePdfWatermarkText = viewModel::updatePdfWatermarkText,
                    onUpdateAddPdfPageNumbers = viewModel::updateAddPdfPageNumbers,
                    onUpdatePdfSignaturePngPath = viewModel::updatePdfSignaturePngPath,
                    onUpdatePdfStampQrContent = viewModel::updatePdfStampQrContent,
                    onUpdateArchiveCompressionLevel = viewModel::updateArchiveCompressionLevel,
                    onUpdateSelectedArchiveEntries = viewModel::updateSelectedArchiveEntries,
                    onUpdateArchivePassword = viewModel::updateArchivePassword,
                    onUpdateFileSplitPartSizeMb = viewModel::updateFileSplitPartSizeMb,
                    onUpdatePreserveSubfolderStructure = viewModel::updatePreserveSubfolderStructure,
                    onUpdatePrettyPrintStructuredData = viewModel::updatePrettyPrintStructuredData,
                    onUpdateTextEncoding = viewModel::updateTextEncoding,
                    onUpdateLineEndingOption = viewModel::updateLineEndingOption,
                    onUpdateVaultPassword = viewModel::updateVaultPassword,
                    onUpdateQrCustomText = viewModel::updateQrCustomText,
                    onUpdateSecondaryPipelineAction = viewModel::updateSecondaryPipelineAction,
                    onStartConversion = viewModel::startBatchConversion,
                    onCancelConversion = viewModel::cancelActiveConversion,
                    onNavigateBack = {
                        if (!navController.popBackStack()) {
                            navigateToBottomTab(AnyWallDestination.HOME)
                        }
                    },
                    onNavigateToHistory = {
                        navigateToBottomTab(AnyWallDestination.HISTORY)
                    }
                )
            }

            composable(AnyWallDestination.HISTORY.route) {
                HistoryScreen(
                    records = filteredHistory,
                    allRecordsForStats = allHistory,
                    thumbnailLoader = viewModel.thumbnailLoader,
                    selectedCategory = selectedCategory,
                    searchQuery = searchQuery,
                    biometricLockEnabled = biometricHistoryLockEnabled,
                    expectedPinCode = historyPinCode,
                    onSelectCategory = viewModel::selectHistoryCategoryFilter,
                    onSearchQueryChange = viewModel::updateHistorySearchQuery,
                    onDeleteRecord = { record ->
                        viewModel.deleteHistoryRecord(record, deletePhysicalFile = true)
                    },
                    onDeleteSelectedRecords = { selected ->
                        viewModel.deleteHistoryRecords(selected, deletePhysicalFiles = true)
                    },
                    onClearAllHistory = {
                        viewModel.clearAllHistory(deletePhysicalFiles = true)
                    },
                    onNavigateBack = {
                        navigateToBottomTab(AnyWallDestination.HOME)
                    }
                )
            }

            composable(AnyWallDestination.SETTINGS.route) {
                SettingsScreen(
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
                    onSelectThemeMode = viewModel::setThemeMode,
                    onToggleDynamicColor = viewModel::setUseDynamicColor,
                    onSelectAccentPalette = viewModel::setAccentPalette,
                    onSelectLanguageMode = viewModel::setLanguageMode,
                    onSelectOutputSubfolderMode = viewModel::setOutputSubfolderMode,
                    onUpdateCustomSubfolderName = viewModel::setCustomSubfolderName,
                    onToggleBiometricHistoryLock = viewModel::setBiometricHistoryLockEnabled,
                    onUpdateHistoryPinCode = viewModel::setHistoryPinCode,
                    onToggleAutoCleanCache = viewModel::setAutoCleanTempCache,
                    onToggleDefaultStripExif = viewModel::setDefaultStripExif,
                    onClearTempCacheNow = viewModel::clearTempCacheNow
                )
            }
        }
    }
}
