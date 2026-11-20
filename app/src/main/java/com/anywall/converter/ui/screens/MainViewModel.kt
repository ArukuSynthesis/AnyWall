package com.anywall.converter.ui.screens

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.anywall.converter.core.AppContainer
import com.anywall.converter.core.AppPreferences
import com.anywall.converter.core.AppThemeMode
import com.anywall.converter.core.LocalThumbnailLoader
import com.anywall.converter.core.UriFileResolver
import com.anywall.converter.domain.engine.ConverterEngineRegistry
import com.anywall.converter.domain.model.ActiveJobState
import com.anywall.converter.domain.model.ConversionOptions
import com.anywall.converter.domain.model.FormatCategory
import com.anywall.converter.domain.model.HistoryRecord
import com.anywall.converter.domain.model.InputFileItem
import com.anywall.converter.domain.model.PdfActionMode
import com.anywall.converter.domain.model.PdfPageEditItem
import com.anywall.converter.domain.model.PdfPageOrientation
import com.anywall.converter.domain.model.PdfPageSize
import com.anywall.converter.domain.model.SupportedFormat
import com.anywall.converter.domain.model.VideoResolution
import com.anywall.converter.domain.repository.ConversionHistoryRepository
import com.anywall.converter.workers.BatchConversionWorker
import com.anywall.converter.workers.ConversionWorkScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.Locale
import java.util.UUID

data class ConfigureUiState(
    val inputFiles: List<InputFileItem> = emptyList(),
    val availableTargetFormats: List<SupportedFormat> = emptyList(),
    val selectedTargetFormat: SupportedFormat? = null,
    val options: ConversionOptions = ConversionOptions(),
    val trimStartText: String = "",
    val trimEndText: String = "",
    val isResolvingFiles: Boolean = false,
    val activeJob: ActiveJobState = ActiveJobState()
) {
    val totalInputBytes: Long
        get() = inputFiles.sumOf { it.sizeBytes }

    val formattedTotalInputSize: String
        get() = InputFileItem.formatBytes(totalInputBytes)

    val visualPdfEditsList: List<PdfPageEditItem>
        get() = PdfPageEditItem.fromJsonArrayString(options.pdfVisualPageEditsJson)

    val showVisualPdfEditorButton: Boolean
        get() {
            val target = selectedTargetFormat ?: return false
            val allPdfs = inputFiles.isNotEmpty() && inputFiles.all { it.format == SupportedFormat.PDF }
            return allPdfs && (target == SupportedFormat.PDF || target == SupportedFormat.JPG || target == SupportedFormat.PNG)
        }

    val showQualitySlider: Boolean
        get() {
            val target = selectedTargetFormat ?: return false
            if (target == SupportedFormat.PDF &&
                (options.pdfActionMode == PdfActionMode.PROTECT_PASSWORD ||
                    options.pdfActionMode == PdfActionMode.UNLOCK_PASSWORD)
            ) {
                return false
            }
            return target in setOf(
                SupportedFormat.JPG,
                SupportedFormat.WEBP,
                SupportedFormat.PDF,
                SupportedFormat.MP3,
                SupportedFormat.ZIP
            )
        }

    val showImageResizeOption: Boolean
        get() {
            val target = selectedTargetFormat ?: return false
            val hasImageOrPdfInput = inputFiles.any {
                it.format.category == FormatCategory.IMAGE || it.format == SupportedFormat.PDF
            }
            return hasImageOrPdfInput && (target.category == FormatCategory.IMAGE || target == SupportedFormat.PDF)
        }

    val showImageRotationAndExifOptions: Boolean
        get() {
            val target = selectedTargetFormat ?: return false
            val hasImageOrPdfInput = inputFiles.any {
                it.format.category == FormatCategory.IMAGE || it.format == SupportedFormat.PDF
            }
            return hasImageOrPdfInput && (target.category == FormatCategory.IMAGE || target == SupportedFormat.PDF)
        }

    val showVideoResolutionSelector: Boolean
        get() = selectedTargetFormat?.isVideo == true

    val showMediaTrimAndMuteOptions: Boolean
        get() {
            val target = selectedTargetFormat ?: return false
            return target.isVideo || target.isAudio
        }

    val showMergePdfToggle: Boolean
        get() {
            val target = selectedTargetFormat ?: return false
            if (target != SupportedFormat.PDF || inputFiles.size <= 1) return false
            if (options.hasVisualPdfEdits) return false
            return options.pdfActionMode == PdfActionMode.MERGE_OR_CONVERT
        }

    val showPdfActionModeSelector: Boolean
        get() {
            val target = selectedTargetFormat ?: return false
            return inputFiles.isNotEmpty() &&
                inputFiles.all { it.format == SupportedFormat.PDF } &&
                target == SupportedFormat.PDF
        }

    val showPageRangeField: Boolean
        get() {
            if (options.hasVisualPdfEdits) return false
            val target = selectedTargetFormat ?: return false
            val allPdfInputs = inputFiles.isNotEmpty() && inputFiles.all { it.format == SupportedFormat.PDF }
            if (!allPdfInputs) return false
            return (target == SupportedFormat.PDF && options.pdfActionMode == PdfActionMode.SPLIT_RANGE) ||
                (target == SupportedFormat.JPG || target == SupportedFormat.PNG)
        }

    val showPdfPasswordField: Boolean
        get() {
            val target = selectedTargetFormat ?: return false
            return target == SupportedFormat.PDF
        }

    val showPdfPageSetupSelector: Boolean
        get() {
            val target = selectedTargetFormat ?: return false
            if (target != SupportedFormat.PDF) return false
            return inputFiles.any {
                it.format in setOf(
                    SupportedFormat.DOCX,
                    SupportedFormat.HTML,
                    SupportedFormat.TXT,
                    SupportedFormat.MD
                )
            }
        }
}

/**
 * ViewModel principal de AnyWall (v0.2) con arquitectura MVVM pura e inyección desde `AppContainer`.
 */
class MainViewModel(
    private val appPreferences: AppPreferences,
    private val uriFileResolver: UriFileResolver,
    val thumbnailLoader: LocalThumbnailLoader,
    private val engineRegistry: ConverterEngineRegistry,
    private val historyRepository: ConversionHistoryRepository,
    private val workScheduler: ConversionWorkScheduler
) : ViewModel() {

    private val _configureState = MutableStateFlow(
        ConfigureUiState(
            options = ConversionOptions(
                stripExifMetadata = appPreferences.defaultStripExif.value
            )
        )
    )
    val configureState: StateFlow<ConfigureUiState> = _configureState.asStateFlow()

    val themeMode: StateFlow<AppThemeMode> = appPreferences.themeMode
    val autoCleanTempCache: StateFlow<Boolean> = appPreferences.autoCleanTempCache
    val defaultStripExif: StateFlow<Boolean> = appPreferences.defaultStripExif

    private val _tempCacheSizeBytes = MutableStateFlow(0L)
    val tempCacheSizeBytes: StateFlow<Long> = _tempCacheSizeBytes.asStateFlow()

    private val _selectedHistoryCategory = MutableStateFlow<FormatCategory?>(null)
    val selectedHistoryCategory: StateFlow<FormatCategory?> = _selectedHistoryCategory.asStateFlow()

    private val _historySearchQuery = MutableStateFlow("")
    val historySearchQuery: StateFlow<String> = _historySearchQuery.asStateFlow()

    private val _navigateToConfigureChannel = Channel<Unit>(Channel.BUFFERED)
    val navigateToConfigureFlow = _navigateToConfigureChannel.receiveAsFlow()

    val allHistoryRecords: StateFlow<List<HistoryRecord>> = historyRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val filteredHistoryRecords: StateFlow<List<HistoryRecord>> = combine(
        allHistoryRecords,
        _selectedHistoryCategory,
        _historySearchQuery
    ) { records, category, query ->
        val cleanQuery = query.trim().lowercase()
        records.filter { record ->
            val matchesCategory = (category == null || record.category == category)
            val matchesQuery = cleanQuery.isEmpty() ||
                record.outputDisplayName.lowercase().contains(cleanQuery) ||
                record.sourceDisplayName.lowercase().contains(cleanQuery) ||
                record.outputFormat.label.lowercase().contains(cleanQuery)
            matchesCategory && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var activeWorkObserverJob: Job? = null

    init {
        performStartupCacheMaintenance()
    }

    private fun performStartupCacheMaintenance() {
        viewModelScope.launch(Dispatchers.IO) {
            if (appPreferences.autoCleanTempCache.value && !workScheduler.hasActiveConversionWork()) {
                uriFileResolver.cleanupTempFiles()
            }
            _tempCacheSizeBytes.value = uriFileResolver.getTempCacheSizeBytes()
        }
    }

    fun refreshTempCacheSize() {
        viewModelScope.launch(Dispatchers.IO) {
            _tempCacheSizeBytes.value = uriFileResolver.getTempCacheSizeBytes()
        }
    }

    fun clearTempCacheNow() {
        viewModelScope.launch(Dispatchers.IO) {
            if (!workScheduler.hasActiveConversionWork()) {
                uriFileResolver.cleanupTempFiles()
            }
            thumbnailLoader.clearCache()
            _tempCacheSizeBytes.value = uriFileResolver.getTempCacheSizeBytes()
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        appPreferences.setThemeMode(mode)
    }

    fun setAutoCleanTempCache(enabled: Boolean) {
        appPreferences.setAutoCleanTempCache(enabled)
    }

    fun setDefaultStripExif(enabled: Boolean) {
        appPreferences.setDefaultStripExif(enabled)
        _configureState.update { current ->
            current.copy(options = current.options.copy(stripExifMetadata = enabled))
        }
    }

    fun addInputUris(
        uris: List<Uri>,
        replaceExisting: Boolean = false,
        navigateToConfigure: Boolean = true
    ) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _configureState.update { it.copy(isResolvingFiles = true) }

            val resolvedItems = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri -> uriFileResolver.resolveInputFile(uri) }
            }

            _configureState.update { current ->
                val baseList = if (replaceExisting) emptyList() else current.inputFiles
                val existingUris = baseList.map { it.uri }.toSet()
                val mergedInputs = baseList + resolvedItems.filter { it.uri !in existingUris }
                recalculateAvailableFormats(
                    current.copy(
                        inputFiles = mergedInputs,
                        options = current.options.copy(pdfVisualPageEditsJson = ""),
                        isResolvingFiles = false,
                        activeJob = if (replaceExisting && !current.activeJob.isRunning) {
                            ActiveJobState()
                        } else {
                            current.activeJob
                        }
                    )
                )
            }

            if (navigateToConfigure && resolvedItems.isNotEmpty()) {
                _navigateToConfigureChannel.trySend(Unit)
            }
        }
    }

    /**
     * Importa todos los archivos de una carpeta seleccionada con `OpenDocumentTree`.
     */
    fun addInputFolderTree(
        treeUri: Uri,
        replaceExisting: Boolean = false,
        navigateToConfigure: Boolean = true
    ) {
        viewModelScope.launch {
            _configureState.update { it.copy(isResolvingFiles = true) }
            val folderUris = withContext(Dispatchers.IO) {
                uriFileResolver.resolveFolderTreeUris(treeUri)
            }
            if (folderUris.isEmpty()) {
                _configureState.update { it.copy(isResolvingFiles = false) }
                return@launch
            }
            addInputUris(
                uris = folderUris,
                replaceExisting = replaceExisting,
                navigateToConfigure = navigateToConfigure
            )
        }
    }

    fun onFilesReceivedFromShareSheet(uris: List<Uri>) {
        addInputUris(
            uris = uris,
            replaceExisting = true,
            navigateToConfigure = true
        )
    }

    fun moveInputFileUp(index: Int) {
        _configureState.update { current ->
            if (index <= 0 || index >= current.inputFiles.size) return@update current
            val mutable = current.inputFiles.toMutableList()
            Collections.swap(mutable, index, index - 1)
            current.copy(
                inputFiles = mutable,
                options = current.options.copy(pdfVisualPageEditsJson = "")
            )
        }
    }

    fun moveInputFileDown(index: Int) {
        _configureState.update { current ->
            if (index < 0 || index >= current.inputFiles.size - 1) return@update current
            val mutable = current.inputFiles.toMutableList()
            Collections.swap(mutable, index, index + 1)
            current.copy(
                inputFiles = mutable,
                options = current.options.copy(pdfVisualPageEditsJson = "")
            )
        }
    }

    fun sortInputFilesByName() {
        _configureState.update { current ->
            if (current.inputFiles.size <= 1) return@update current
            val sortedAsc = current.inputFiles.sortedBy { it.displayName.lowercase(Locale.ROOT) }
            val finalOrder = if (sortedAsc == current.inputFiles) sortedAsc.reversed() else sortedAsc
            current.copy(
                inputFiles = finalOrder,
                options = current.options.copy(pdfVisualPageEditsJson = "")
            )
        }
    }

    fun sortInputFilesBySize() {
        _configureState.update { current ->
            if (current.inputFiles.size <= 1) return@update current
            val sortedAsc = current.inputFiles.sortedBy { it.sizeBytes }
            val finalOrder = if (sortedAsc == current.inputFiles) sortedAsc.reversed() else sortedAsc
            current.copy(
                inputFiles = finalOrder,
                options = current.options.copy(pdfVisualPageEditsJson = "")
            )
        }
    }

    fun reverseInputFilesOrder() {
        _configureState.update { current ->
            if (current.inputFiles.size <= 1) return@update current
            current.copy(
                inputFiles = current.inputFiles.reversed(),
                options = current.options.copy(pdfVisualPageEditsJson = "")
            )
        }
    }

    fun applyVisualPdfPageEdits(edits: List<PdfPageEditItem>) {
        val json = PdfPageEditItem.toJsonArrayString(edits)
        _configureState.update { current ->
            current.copy(
                options = current.options.copy(pdfVisualPageEditsJson = json)
            )
        }
    }

    fun clearVisualPdfPageEdits() {
        _configureState.update { current ->
            current.copy(
                options = current.options.copy(pdfVisualPageEditsJson = "")
            )
        }
    }

    fun removeInputFile(uri: Uri) {
        _configureState.update { current ->
            val updatedList = current.inputFiles.filterNot { it.uri == uri }
            recalculateAvailableFormats(
                current.copy(
                    inputFiles = updatedList,
                    options = current.options.copy(pdfVisualPageEditsJson = "")
                )
            )
        }
    }

    fun clearInputFiles() {
        _configureState.update { current ->
            recalculateAvailableFormats(
                current.copy(
                    inputFiles = emptyList(),
                    options = ConversionOptions(
                        stripExifMetadata = appPreferences.defaultStripExif.value
                    ),
                    trimStartText = "",
                    trimEndText = "",
                    activeJob = if (current.activeJob.isRunning) current.activeJob else ActiveJobState()
                )
            )
        }
    }

    fun selectTargetFormat(format: SupportedFormat) {
        _configureState.update { current ->
            current.copy(selectedTargetFormat = format)
        }
    }

    fun updateQualityPercent(quality: Int) {
        _configureState.update { current ->
            current.copy(
                options = current.options.copy(qualityPercent = quality.coerceIn(10, 100))
            )
        }
    }

    fun updateMaxDimensionPx(maxDimensionPx: Int?) {
        _configureState.update { current ->
            current.copy(
                options = current.options.copy(maxDimensionPx = maxDimensionPx)
            )
        }
    }

    fun updateImageRotationDegrees(degrees: Int) {
        _configureState.update { current ->
            current.copy(
                options = current.options.copy(imageRotationDegrees = degrees)
            )
        }
    }

    fun updateStripExifMetadata(strip: Boolean) {
        _configureState.update { current ->
            current.copy(
                options = current.options.copy(stripExifMetadata = strip)
            )
        }
    }

    fun updateVideoResolution(resolution: VideoResolution) {
        _configureState.update { current ->
            current.copy(
                options = current.options.copy(videoResolution = resolution)
            )
        }
    }

    fun updateMuteVideoAudio(mute: Boolean) {
        _configureState.update { current ->
            current.copy(
                options = current.options.copy(muteVideoAudio = mute)
            )
        }
    }

    fun updateTrimStartText(rawText: String) {
        val clean = rawText.filter { it.isDigit() }.take(6)
        val seconds = clean.toLongOrNull()
        _configureState.update { current ->
            current.copy(
                trimStartText = clean,
                options = current.options.copy(trimStartSeconds = seconds)
            )
        }
    }

    fun updateTrimEndText(rawText: String) {
        val clean = rawText.filter { it.isDigit() }.take(6)
        val seconds = clean.toLongOrNull()
        _configureState.update { current ->
            current.copy(
                trimEndText = clean,
                options = current.options.copy(trimEndSeconds = seconds)
            )
        }
    }

    fun updateCustomOutputBaseName(name: String) {
        _configureState.update { current ->
            current.copy(
                options = current.options.copy(customOutputBaseName = name)
            )
        }
    }

    fun updateMergeIntoSinglePdf(merge: Boolean) {
        _configureState.update { current ->
            current.copy(
                options = current.options.copy(mergeIntoSinglePdf = merge)
            )
        }
    }

    fun updatePdfActionMode(mode: PdfActionMode) {
        _configureState.update { current ->
            current.copy(
                options = current.options.copy(pdfActionMode = mode)
            )
        }
    }

    fun updatePageRangeSpec(rangeSpec: String) {
        _configureState.update { current ->
            current.copy(
                options = current.options.copy(pageRangeSpec = rangeSpec)
            )
        }
    }

    fun updatePdfPassword(password: String) {
        _configureState.update { current ->
            current.copy(
                options = current.options.copy(pdfPassword = password)
            )
        }
    }

    fun updatePdfPageSize(pageSize: PdfPageSize) {
        _configureState.update { current ->
            current.copy(
                options = current.options.copy(pdfPageSize = pageSize)
            )
        }
    }

    fun updatePdfPageOrientation(orientation: PdfPageOrientation) {
        _configureState.update { current ->
            current.copy(
                options = current.options.copy(pdfPageOrientation = orientation)
            )
        }
    }

    fun startBatchConversion() {
        val snapshot = _configureState.value
        val inputs = snapshot.inputFiles
        val targetFormat = snapshot.selectedTargetFormat ?: return
        if (inputs.isEmpty() || snapshot.activeJob.isRunning) return

        val workId = workScheduler.enqueueBatchConversion(
            inputs = inputs,
            targetFormat = targetFormat,
            options = snapshot.options
        )

        _configureState.update {
            it.copy(
                activeJob = ActiveJobState(
                    workId = workId,
                    isRunning = true,
                    progress = 0.02f,
                    statusMessage = "Iniciando motor de conversión local…"
                )
            )
        }

        observeWorkExecution(workId)
    }

    fun cancelActiveConversion() {
        val workId = _configureState.value.activeJob.workId
        workScheduler.cancelActiveConversion(workId)
        _configureState.update {
            it.copy(
                activeJob = ActiveJobState(
                    workId = workId,
                    isRunning = false,
                    isFailed = true,
                    errorMessage = "Conversión cancelada por el usuario."
                )
            )
        }
    }

    private fun observeWorkExecution(workId: UUID) {
        activeWorkObserverJob?.cancel()
        activeWorkObserverJob = viewModelScope.launch {
            workScheduler.observeWorkInfo(workId).collect { info ->
                if (info == null) return@collect
                when (info.state) {
                    WorkInfo.State.ENQUEUED,
                    WorkInfo.State.BLOCKED -> {
                        _configureState.update { state ->
                            state.copy(
                                activeJob = state.activeJob.copy(
                                    workId = workId,
                                    isRunning = true,
                                    statusMessage = "Preparando lote de conversión…"
                                )
                            )
                        }
                    }

                    WorkInfo.State.RUNNING -> {
                        val p = info.progress.getFloat(BatchConversionWorker.KEY_PROGRESS, 0f)
                        val status = info.progress.getString(BatchConversionWorker.KEY_STATUS)
                            ?: "Procesando archivos localmente…"
                        _configureState.update { state ->
                            state.copy(
                                activeJob = state.activeJob.copy(
                                    workId = workId,
                                    isRunning = true,
                                    progress = p.coerceIn(0f, 1f),
                                    statusMessage = status,
                                    isCompleted = false,
                                    isFailed = false
                                )
                            )
                        }
                    }

                    WorkInfo.State.SUCCEEDED -> {
                        val count = info.outputData.getInt(BatchConversionWorker.KEY_GENERATED_COUNT, 1)
                        val status = info.outputData.getString(BatchConversionWorker.KEY_STATUS)
                            ?: "Conversión completada ($count)"
                        _configureState.update { state ->
                            state.copy(
                                activeJob = ActiveJobState(
                                    workId = workId,
                                    isRunning = false,
                                    progress = 1f,
                                    statusMessage = status,
                                    isCompleted = true,
                                    isFailed = false,
                                    generatedCount = count
                                )
                            )
                        }
                        refreshTempCacheSize()
                    }

                    WorkInfo.State.FAILED -> {
                        val err = info.outputData.getString(BatchConversionWorker.KEY_ERROR)
                            ?: "No se pudo completar la conversión."
                        _configureState.update { state ->
                            state.copy(
                                activeJob = ActiveJobState(
                                    workId = workId,
                                    isRunning = false,
                                    progress = 0f,
                                    statusMessage = err,
                                    isCompleted = false,
                                    isFailed = true,
                                    errorMessage = err
                                )
                            )
                        }
                        refreshTempCacheSize()
                    }

                    WorkInfo.State.CANCELLED -> {
                        _configureState.update { state ->
                            state.copy(
                                activeJob = ActiveJobState(
                                    workId = workId,
                                    isRunning = false,
                                    progress = 0f,
                                    statusMessage = "Conversión cancelada.",
                                    isCompleted = false,
                                    isFailed = true,
                                    errorMessage = "Conversión cancelada."
                                )
                            )
                        }
                        refreshTempCacheSize()
                    }
                }
            }
        }
    }

    fun selectHistoryCategoryFilter(category: FormatCategory?) {
        _selectedHistoryCategory.value = category
    }

    fun updateHistorySearchQuery(query: String) {
        _historySearchQuery.value = query
    }

    fun deleteHistoryRecord(record: HistoryRecord, deletePhysicalFile: Boolean = true) {
        viewModelScope.launch {
            historyRepository.deleteRecord(record, deletePhysicalFile)
        }
    }

    fun deleteHistoryRecords(records: List<HistoryRecord>, deletePhysicalFiles: Boolean = true) {
        viewModelScope.launch {
            historyRepository.deleteRecords(records, deletePhysicalFiles)
        }
    }

    fun clearAllHistory(deletePhysicalFiles: Boolean = true) {
        viewModelScope.launch {
            historyRepository.clearAll(deletePhysicalFiles)
        }
    }

    private fun recalculateAvailableFormats(state: ConfigureUiState): ConfigureUiState {
        val sourceFormats = state.inputFiles.map { it.format }
        val available = engineRegistry.getAvailableTargetFormats(sourceFormats)
        if (available.isEmpty()) {
            return state.copy(
                availableTargetFormats = emptyList(),
                selectedTargetFormat = null
            )
        }

        val currentSelected = state.selectedTargetFormat
        val smartDefault = when {
            currentSelected != null && currentSelected in available -> currentSelected
            SupportedFormat.EXTRACT_ARCHIVE in available -> SupportedFormat.EXTRACT_ARCHIVE
            sourceFormats.size == 1 -> {
                val singleSource = sourceFormats.first()
                available.firstOrNull { it != singleSource && !it.isArchiveOutput }
                    ?: available.first()
            }
            else -> available.first()
        }

        return state.copy(
            availableTargetFormats = available,
            selectedTargetFormat = smartDefault
        )
    }

    companion object {
        fun provideFactory(container: AppContainer): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MainViewModel(
                        appPreferences = container.appPreferences,
                        uriFileResolver = container.uriFileResolver,
                        thumbnailLoader = container.thumbnailLoader,
                        engineRegistry = container.converterEngineRegistry,
                        historyRepository = container.historyRepository,
                        workScheduler = container.workScheduler
                    ) as T
                }
            }
        }
    }
}
