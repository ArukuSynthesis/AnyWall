package com.anywall.converter.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.anywall.converter.core.AnyWallQuickTileService
import com.anywall.converter.ui.screens.ConfigureScreen
import com.anywall.converter.ui.screens.HistoryScreen
import com.anywall.converter.ui.screens.HomeScreen
import com.anywall.converter.ui.screens.MainViewModel

/**
 * Grafo de navegación principal de AnyWall (v0.3) en Jetpack Compose.
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
    val presets by viewModel.allPresets.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedHistoryCategory.collectAsStateWithLifecycle()
    val searchQuery by viewModel.historySearchQuery.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val languageMode by viewModel.languageMode.collectAsStateWithLifecycle()
    val outputSubfolderMode by viewModel.outputSubfolderMode.collectAsStateWithLifecycle()
    val customSubfolderName by viewModel.customSubfolderName.collectAsStateWithLifecycle()
    val biometricHistoryLockEnabled by viewModel.biometricHistoryLockEnabled.collectAsStateWithLifecycle()
    val historyPinCode by viewModel.historyPinCode.collectAsStateWithLifecycle()
    val autoCleanTempCache by viewModel.autoCleanTempCache.collectAsStateWithLifecycle()
    val defaultStripExif by viewModel.defaultStripExif.collectAsStateWithLifecycle()
    val tempCacheSizeBytes by viewModel.tempCacheSizeBytes.collectAsStateWithLifecycle()

    LaunchedEffect(initialQuickAction) {
        if (initialQuickAction == AnyWallQuickTileService.ACTION_OPEN_HISTORY) {
            navController.navigate(AnyWallDestination.HISTORY.route) {
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.navigateToConfigureFlow.collect {
            val currentRoute = navController.currentDestination?.route
            if (currentRoute != AnyWallDestination.CONFIGURE.route) {
                navController.navigate(AnyWallDestination.CONFIGURE.route) {
                    launchSingleTop = true
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = AnyWallDestination.HOME.route,
        modifier = modifier
    ) {
        composable(AnyWallDestination.HOME.route) {
            HomeScreen(
                configureState = configureState,
                recentHistory = allHistory,
                presets = presets,
                thumbnailLoader = viewModel.thumbnailLoader,
                uriResolver = viewModel.uriFileResolver,
                storageManager = viewModel.scopedStorageManager,
                forensicHelper = viewModel.forensicMetadataHelper,
                themeMode = themeMode,
                languageMode = languageMode,
                outputSubfolderMode = outputSubfolderMode,
                customSubfolderName = customSubfolderName,
                biometricHistoryLockEnabled = biometricHistoryLockEnabled,
                historyPinCode = historyPinCode,
                autoCleanTempCache = autoCleanTempCache,
                defaultStripExif = defaultStripExif,
                tempCacheSizeBytes = tempCacheSizeBytes,
                initialOpenToolsDialog = (initialQuickAction == AnyWallQuickTileService.ACTION_OPEN_TOOLS),
                onSelectThemeMode = viewModel::setThemeMode,
                onSelectLanguageMode = viewModel::setLanguageMode,
                onSelectOutputSubfolderMode = viewModel::setOutputSubfolderMode,
                onUpdateCustomSubfolderName = viewModel::setCustomSubfolderName,
                onToggleBiometricHistoryLock = viewModel::setBiometricHistoryLockEnabled,
                onUpdateHistoryPinCode = viewModel::setHistoryPinCode,
                onToggleAutoCleanCache = viewModel::setAutoCleanTempCache,
                onToggleDefaultStripExif = viewModel::setDefaultStripExif,
                onRefreshTempCacheSize = viewModel::refreshTempCacheSize,
                onClearTempCacheNow = viewModel::clearTempCacheNow,
                onApplyPreset = viewModel::applyConversionPreset,
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
                    navController.navigate(AnyWallDestination.HISTORY.route) {
                        launchSingleTop = true
                    }
                },
                onCancelActiveJob = viewModel::cancelActiveConversion,
                onDeleteHistoryRecord = { record ->
                    viewModel.deleteHistoryRecord(record, deletePhysicalFile = true)
                }
            )
        }

        composable(AnyWallDestination.CONFIGURE.route) {
            ConfigureScreen(
                state = configureState,
                presets = presets,
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
                onSortByName = viewModel::sortInputFilesByName,
                onSortBySize = viewModel::sortInputFilesBySize,
                onReverseOrder = viewModel::reverseInputFilesOrder,
                onApplyPreset = viewModel::applyConversionPreset,
                onSaveNewPreset = viewModel::saveCurrentAsPreset,
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
                onUpdateVideoPlaybackSpeed = viewModel::updateVideoPlaybackSpeed,
                onUpdateVideoRotation = viewModel::updateVideoRotationDegrees,
                onUpdateVideoFlipHorizontal = viewModel::updateVideoFlipHorizontal,
                onUpdateTargetVideoMaxMb = viewModel::updateTargetVideoMaxSizeMb,
                onUpdateMergeMediaSequence = viewModel::updateMergeMediaSequence,
                onUpdateAudioBitrateKbps = viewModel::updateAudioBitrateKbps,
                onUpdateAudioFadeSeconds = viewModel::updateAudioFadeSeconds,
                onUpdateExportAsRingtone = viewModel::updateExportAsRingtone,
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
                        navController.navigate(AnyWallDestination.HOME.route)
                    }
                },
                onNavigateToHistory = {
                    navController.navigate(AnyWallDestination.HISTORY.route) {
                        launchSingleTop = true
                    }
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
                    if (!navController.popBackStack()) {
                        navController.navigate(AnyWallDestination.HOME.route)
                    }
                }
            )
        }
    }
}
