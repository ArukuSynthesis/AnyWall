package com.anywall.converter.ui.screens

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.anywall.converter.core.AppContainer
import com.anywall.converter.core.UriFileResolver
import com.anywall.converter.domain.engine.ConverterEngineRegistry
import com.anywall.converter.domain.model.ActiveJobState
import com.anywall.converter.domain.model.ConversionOptions
import com.anywall.converter.domain.model.FormatCategory
import com.anywall.converter.domain.model.HistoryRecord
import com.anywall.converter.domain.model.InputFileItem
import com.anywall.converter.domain.model.PdfActionMode
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
import java.util.UUID

data class ConfigureUiState(
    val inputFiles: List<InputFileItem> = emptyList(),
    val availableTargetFormats: List<SupportedFormat> = emptyList(),
    val selectedTargetFormat: SupportedFormat? = null,
    val options: ConversionOptions = ConversionOptions(),
    val isResolvingFiles: Boolean = false,
    val activeJob: ActiveJobState = ActiveJobState()
) {
    val totalInputBytes: Long
        get() = inputFiles.sumOf { it.sizeBytes }

    val formattedTotalInputSize: String
        get() = InputFileItem.formatBytes(totalInputBytes)

    val showQualitySlider: Boolean
        get() {
            val target = selectedTargetFormat ?: return false
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

    val showVideoResolutionSelector: Boolean
        get() = selectedTargetFormat?.isVideo == true

    val showMergePdfToggle: Boolean
        get() {
            val target = selectedTargetFormat ?: return false
            if (target != SupportedFormat.PDF || inputFiles.size <= 1) return false
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
            val target = selectedTargetFormat ?: return false
            val allPdfInputs = inputFiles.isNotEmpty() && inputFiles.all { it.format == SupportedFormat.PDF }
            if (!allPdfInputs) return false
            return (target == SupportedFormat.PDF && options.pdfActionMode == PdfActionMode.SPLIT_RANGE) ||
                (target == SupportedFormat.JPG || target == SupportedFormat.PNG)
        }
}

/**
 * ViewModel principal de AnyWall con arquitectura MVVM pura e inyección desde `AppContainer`.
 */
class MainViewModel(
    private val uriFileResolver: UriFileResolver,
    private val engineRegistry: ConverterEngineRegistry,
    private val historyRepository: ConversionHistoryRepository,
    private val workScheduler: ConversionWorkScheduler
) : ViewModel() {

    private val _configureState = MutableStateFlow(ConfigureUiState())
    val configureState: StateFlow<ConfigureUiState> = _configureState.asStateFlow()

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

    /**
     * Carga archivos seleccionados desde el selector de documentos o desde Android Share Sheet.
     */
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
     * Recibe URIs entrantes desde `Intent.ACTION_SEND` o `Intent.ACTION_SEND_MULTIPLE` (Share Sheet)
     * y abre directamente la pantalla `ConfigureScreen`.
     */
    fun onFilesReceivedFromShareSheet(uris: List<Uri>) {
        addInputUris(
            uris = uris,
            replaceExisting = true,
            navigateToConfigure = true
        )
    }

    fun removeInputFile(uri: Uri) {
        _configureState.update { current ->
            val updatedList = current.inputFiles.filterNot { it.uri == uri }
            recalculateAvailableFormats(current.copy(inputFiles = updatedList))
        }
    }

    fun clearInputFiles() {
        _configureState.update { current ->
            recalculateAvailableFormats(
                current.copy(
                    inputFiles = emptyList(),
                    options = ConversionOptions(),
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

    fun updateVideoResolution(resolution: VideoResolution) {
        _configureState.update { current ->
            current.copy(
                options = current.options.copy(videoResolution = resolution)
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

    /**
     * Inicia la conversión en segundo plano mediante `BatchConversionWorker` (WorkManager).
     */
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

        // Mantener el formato seleccionado si sigue siendo válido, o elegir el formato destino más inteligente
        val currentSelected = state.selectedTargetFormat
        val smartDefault = when {
            currentSelected != null && currentSelected in available -> currentSelected
            // Si todos son comprimidos, sugerir Extraer por defecto
            SupportedFormat.EXTRACT_ARCHIVE in available -> SupportedFormat.EXTRACT_ARCHIVE
            // Si son varias imágenes, sugerir PNG, JPG o PDF
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
                        uriFileResolver = container.uriFileResolver,
                        engineRegistry = container.converterEngineRegistry,
                        historyRepository = container.historyRepository,
                        workScheduler = container.workScheduler
                    ) as T
                }
            }
        }
    }
}
