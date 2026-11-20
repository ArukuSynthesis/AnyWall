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
import com.anywall.converter.ui.screens.ConfigureScreen
import com.anywall.converter.ui.screens.HistoryScreen
import com.anywall.converter.ui.screens.HomeScreen
import com.anywall.converter.ui.screens.MainViewModel

/**
 * Grafo de navegación principal de AnyWall (v0.2) en Jetpack Compose.
 */
@Composable
fun AnyWallNavHost(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val configureState by viewModel.configureState.collectAsStateWithLifecycle()
    val allHistory by viewModel.allHistoryRecords.collectAsStateWithLifecycle()
    val filteredHistory by viewModel.filteredHistoryRecords.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedHistoryCategory.collectAsStateWithLifecycle()
    val searchQuery by viewModel.historySearchQuery.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val autoCleanTempCache by viewModel.autoCleanTempCache.collectAsStateWithLifecycle()
    val defaultStripExif by viewModel.defaultStripExif.collectAsStateWithLifecycle()
    val tempCacheSizeBytes by viewModel.tempCacheSizeBytes.collectAsStateWithLifecycle()

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
                thumbnailLoader = viewModel.thumbnailLoader,
                themeMode = themeMode,
                autoCleanTempCache = autoCleanTempCache,
                defaultStripExif = defaultStripExif,
                tempCacheSizeBytes = tempCacheSizeBytes,
                onSelectThemeMode = viewModel::setThemeMode,
                onToggleAutoCleanCache = viewModel::setAutoCleanTempCache,
                onToggleDefaultStripExif = viewModel::setDefaultStripExif,
                onRefreshTempCacheSize = viewModel::refreshTempCacheSize,
                onClearTempCacheNow = viewModel::clearTempCacheNow,
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
                thumbnailLoader = viewModel.thumbnailLoader,
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
                onApplyVisualPdfEdits = viewModel::applyVisualPdfPageEdits,
                onClearVisualPdfEdits = viewModel::clearVisualPdfPageEdits,
                onRemoveFile = viewModel::removeInputFile,
                onClearAllFiles = viewModel::clearInputFiles,
                onSelectTargetFormat = viewModel::selectTargetFormat,
                onUpdateQuality = viewModel::updateQualityPercent,
                onUpdateMaxDimension = viewModel::updateMaxDimensionPx,
                onUpdateImageRotation = viewModel::updateImageRotationDegrees,
                onUpdateStripExif = viewModel::updateStripExifMetadata,
                onUpdateVideoResolution = viewModel::updateVideoResolution,
                onUpdateMuteVideoAudio = viewModel::updateMuteVideoAudio,
                onUpdateTrimStartText = viewModel::updateTrimStartText,
                onUpdateTrimEndText = viewModel::updateTrimEndText,
                onUpdateCustomOutputName = viewModel::updateCustomOutputBaseName,
                onUpdateMergeSinglePdf = viewModel::updateMergeIntoSinglePdf,
                onUpdatePdfActionMode = viewModel::updatePdfActionMode,
                onUpdatePageRangeSpec = viewModel::updatePageRangeSpec,
                onUpdatePdfPassword = viewModel::updatePdfPassword,
                onUpdatePdfPageSize = viewModel::updatePdfPageSize,
                onUpdatePdfPageOrientation = viewModel::updatePdfPageOrientation,
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
                thumbnailLoader = viewModel.thumbnailLoader,
                selectedCategory = selectedCategory,
                searchQuery = searchQuery,
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
