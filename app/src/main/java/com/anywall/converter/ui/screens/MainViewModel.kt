package com.anywall.converter.ui.screens

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.anywall.converter.core.AppContainer
import com.anywall.converter.core.AppLanguageMode
import com.anywall.converter.core.AppPreferences
import com.anywall.converter.core.AppThemeMode
import com.anywall.converter.core.LocalThumbnailLoader
import com.anywall.converter.core.OutputSubfolderMode
import com.anywall.converter.core.ScopedStorageManager
import com.anywall.converter.core.UriFileResolver
import com.anywall.converter.data.engines.ArchiveEngineImpl
import com.anywall.converter.data.engines.ForensicMetadataHelper
import com.anywall.converter.domain.engine.ConverterEngineRegistry
import com.anywall.converter.domain.model.ActiveJobState
import com.anywall.converter.domain.model.ArchiveCompressionLevel
import com.anywall.converter.domain.model.ConversionOptions
import com.anywall.converter.domain.model.ConversionPreset
import com.anywall.converter.domain.model.FormatCategory
import com.anywall.converter.domain.model.HistoryRecord
import com.anywall.converter.domain.model.ImageBatchLayoutMode
import com.anywall.converter.domain.model.ImageEditSpec
import com.anywall.converter.domain.model.InputFileItem
import com.anywall.converter.domain.model.LineEndingOption
import com.anywall.converter.domain.model.PdfActionMode
import com.anywall.converter.domain.model.PdfPageEditItem
import com.anywall.converter.domain.model.PdfPageOrientation
import com.anywall.converter.domain.model.PdfPageSize
import com.anywall.converter.domain.model.SecondaryPipelineAction
import com.anywall.converter.domain.model.SupportedFormat
import com.anywall.converter.domain.model.TextEncodingOption
import com.anywall.converter.domain.model.VideoResolution
import com.anywall.converter.domain.repository.ConversionHistoryRepository
import com.anywall.converter.domain.repository.ConversionPresetRepository
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
    val targetImageMaxKbText: String = "",
    val targetVideoMaxMbText: String = "",
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
            return allPdfs && (target == SupportedFormat.PDF || target == SupportedFormat.JPG || target == SupportedFormat.PNG || target == SupportedFormat.WEBP)
        }

    val showVisualImageEditorButton: Boolean
        get() {
            val target = selectedTargetFormat ?: return false
            val hasImages = inputFiles.any { it.format.category == FormatCategory.IMAGE }
            return hasImages && (target.category == FormatCategory.IMAGE || target == SupportedFormat.PDF)
        }

    val showArchivePreviewButton: Boolean
        get() {
            val target = selectedTargetFormat ?: return false
            return target == SupportedFormat.EXTRACT_ARCHIVE && inputFiles.isNotEmpty()
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
                SupportedFormat.MP3
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

    val showImageAdvancedV03Options: Boolean
        get() {
            val target = selectedTargetFormat ?: return false
            val allImages = inputFiles.isNotEmpty() && inputFiles.all { it.format.category == FormatCategory.IMAGE }
            return allImages && (target.category == FormatCategory.IMAGE || target == SupportedFormat.PDF)
        }

    val showVideoResolutionSelector: Boolean
        get() = selectedTargetFormat?.isVideo == true

    val showMediaTrimAndMuteOptions: Boolean
        get() {
            val target = selectedTargetFormat ?: return false
            return target.isVideo || target.isAudio ||
                (inputFiles.any { it.format.isVideo } && target == SupportedFormat.GIF)
        }

    val showAudioV03Options: Boolean
        get() = selectedTargetFormat?.isAudio == true

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
                (target == SupportedFormat.JPG || target == SupportedFormat.PNG || target == SupportedFormat.WEBP)
        }

    val showPdfPasswordField: Boolean
        get() {
            val target = selectedTargetFormat ?: return false
            return target == SupportedFormat.PDF
        }

    val showPdfDecorationsSection: Boolean
        get() {
            val target = selectedTargetFormat ?: return false
            return target == SupportedFormat.PDF &&
                options.pdfActionMode != PdfActionMode.UNLOCK_PASSWORD &&
                options.pdfActionMode != PdfActionMode.EXTRACT_EMBEDDED_IMAGES
        }

    val showPdfPageSetupSelector: Boolean
        get() {
            val target = selectedTargetFormat ?: return false
            if (target != SupportedFormat.PDF) return false
            return inputFiles.any {
                it.format in setOf(
                    SupportedFormat.DOCX,
                    SupportedFormat.XLSX,
                    SupportedFormat.EPUB,
                    SupportedFormat.HTML,
                    SupportedFormat.TXT,
                    SupportedFormat.MD,
                    SupportedFormat.CSV,
                    SupportedFormat.JSON
                )
            }
        }

    val showArchiveV03Options: Boolean
        get() {
            val target = selectedTargetFormat ?: return false
            return target.isArchiveOutput || target == SupportedFormat.FILE_PARTS
        }

    val showStructuredDataV03Options: Boolean
        get() {
            val target = selectedTargetFormat ?: return false
            return target in setOf(
                SupportedFormat.CSV,
                SupportedFormat.JSON,
                SupportedFormat.XML,
                SupportedFormat.YAML,
                SupportedFormat.HTML,
                SupportedFormat.TXT,
                SupportedFormat.BASE64
            ) && inputFiles.any {
                it.format.category == FormatCategory.DATA_TOOLS ||
                    it.format in setOf(SupportedFormat.TXT, SupportedFormat.MD, SupportedFormat.HTML, SupportedFormat.XLSX)
            }
        }

    val showVaultPasswordField: Boolean
        get() {
            val target = selectedTargetFormat ?: return false
            return target == SupportedFormat.ANYWALL_VAULT ||
                inputFiles.any { it.format == SupportedFormat.ANYWALL_VAULT } ||
                options.secondaryPipelineAction == SecondaryPipelineAction.ENCRYPT_AES256
        }

    val showQrCustomTextField: Boolean
        get() {
            val target = selectedTargetFormat ?: return false
            return target == SupportedFormat.QR_PNG || target == SupportedFormat.QR_SVG
        }
}

/**
 * ViewModel principal de AnyWall (v0.3) con arquitectura MVVM pura e inyección desde `AppContainer`.
 */
class MainViewModel(
    private val appPreferences: AppPreferences,
    val uriFileResolver: UriFileResolver,
    val thumbnailLoader: LocalThumbnailLoader,
    val scopedStorageManager: ScopedStorageManager,
    val forensicMetadataHelper: ForensicMetadataHelper,
    val archiveEngineImpl: ArchiveEngineImpl,
    private val engineRegistry: ConverterEngineRegistry,
    private val historyRepository: ConversionHistoryRepository,
    private val presetRepository: ConversionPresetRepository,
    private val workScheduler: ConversionWorkScheduler
) : ViewModel() {

    private val _configureState = MutableStateFlow(
        ConfigureUiState(
            options = ConversionOptions(
                stripExifMetadata = appPreferences.defaultStripExif.value,
                pdfSignaturePngPath = appPreferences.savedSignaturePngPath.value
            )
        )
    )
    val configureState: StateFlow<ConfigureUiState> = _configureState.asStateFlow()

    val themeMode: StateFlow<AppThemeMode> = appPreferences.themeMode
    val languageMode: StateFlow<AppLanguageMode> = appPreferences.languageMode
    val outputSubfolderMode: StateFlow<OutputSubfolderMode> = appPreferences.outputSubfolderMode
    val customSubfolderName: StateFlow<String> = appPreferences.customSubfolderName
    val autoCleanTempCache: StateFlow<Boolean> = appPreferences.autoCleanTempCache
    val defaultStripExif: StateFlow<Boolean> = appPreferences.defaultStripExif
    val biometricHistoryLockEnabled: StateFlow<Boolean> = appPreferences.biometricHistoryLockEnabled
    val historyPinCode: StateFlow<String> = appPreferences.historyPinCode
    val savedSignaturePngPath: StateFlow<String> = appPreferences.savedSignaturePngPath

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

    val allPresets: StateFlow<List<ConversionPreset>> = presetRepository.observeAllPresets()
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
        viewModelScope.launch(Dispatchers.IO) {
            presetRepository.ensureBuiltInPresetsSeeded()
        }
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

    fun setLanguageMode(mode: AppLanguageMode) {
        appPreferences.setLanguageMode(mode)
    }

    fun setOutputSubfolderMode(mode: OutputSubfolderMode) {
        appPreferences.setOutputSubfolderMode(mode)
    }

    fun setCustomSubfolderName(name: String) {
        appPreferences.setCustomSubfolderName(name)
    }

    fun setBiometricHistoryLockEnabled(enabled: Boolean) {
        appPreferences.setBiometricHistoryLockEnabled(enabled)
    }

    fun setHistoryPinCode(pin: String) {
        appPreferences.setHistoryPinCode(pin)
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

    fun createCameraCaptureUri(): Uri {
        return uriFileResolver.createCameraCaptureOutputUri()
    }

    fun applyConversionPreset(preset: ConversionPreset) {
        _configureState.update { current ->
            val targetAllowed = if (current.availableTargetFormats.isEmpty() ||
                preset.targetFormat in current.availableTargetFormats
            ) {
                preset.targetFormat
            } else {
                current.selectedTargetFormat
            }
            current.copy(
                selectedTargetFormat = targetAllowed,
                options = preset.options.copy(
                    pdfVisualPageEditsJson = current.options.pdfVisualPageEditsJson
                ),
                targetImageMaxKbText = preset.options.targetImageMaxSizeKb?.toString().orEmpty(),
                targetVideoMaxMbText = preset.options.targetVideoMaxSizeMb?.toString().orEmpty(),
                trimStartText = preset.options.trimStartSeconds?.toString().orEmpty(),
                trimEndText = preset.options.trimEndSeconds?.toString().orEmpty()
            )
        }
    }

    fun saveCurrentAsPreset(name: String, description: String) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        val snap = _configureState.value
        val target = snap.selectedTargetFormat ?: SupportedFormat.PDF
        viewModelScope.launch(Dispatchers.IO) {
            presetRepository.savePreset(
                ConversionPreset(
                    name = cleanName,
                    description = description.trim().ifBlank { "${target.label} • ${snap.options.clampedQuality}%" },
                    category = target.category,
                    targetFormat = target,
                    options = snap.options.copy(
                        pdfVisualPageEditsJson = "",
                        selectedArchiveEntriesSerialized = ""
                    ),
                    isBuiltIn = false
                )
            )
        }
    }

    fun deleteConversionPreset(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            presetRepository.deletePreset(id)
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
                        options = current.options.copy(
                            pdfVisualPageEditsJson = "",
                            selectedArchiveEntriesSerialized = ""
                        ),
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
     * Importa todos los archivos de una carpeta seleccionada con `OpenDocumentTree`
     * preservando sus rutas de subcarpeta relativas (`relativeFolderPath`).
     */
    fun addInputFolderTree(
        treeUri: Uri,
        replaceExisting: Boolean = false,
        navigateToConfigure: Boolean = true
    ) {
        viewModelScope.launch {
            _configureState.update { it.copy(isResolvingFiles = true) }
            val resolvedItems = withContext(Dispatchers.IO) {
                val entries = uriFileResolver.resolveFolderTreeEntries(treeUri)
                entries.mapNotNull { entry ->
                    uriFileResolver.resolveInputFile(entry.uri, entry.relativeFolderPath)
                }
            }
            if (resolvedItems.isEmpty()) {
                _configureState.update { it.copy(isResolvingFiles = false) }
                return@launch
            }
            _configureState.update { current ->
                val baseList = if (replaceExisting) emptyList() else current.inputFiles
                val existingUris = baseList.map { it.uri }.toSet()
                val mergedInputs = baseList + resolvedItems.filter { it.uri !in existingUris }
                recalculateAvailableFormats(
                    current.copy(
                        inputFiles = mergedInputs,
                        options = current.options.copy(
                            pdfVisualPageEditsJson = "",
                            selectedArchiveEntriesSerialized = ""
                        ),
                        isResolvingFiles = false
                    )
                )
            }
            if (navigateToConfigure) {
                _navigateToConfigureChannel.trySend(Unit)
            }
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

    fun applyVisualImageEdits(spec: ImageEditSpec) {
        _configureState.update { current ->
            current.copy(
                options = current.options.copy(imageVisualEditSerialized = spec.toSerializedString())
            )
        }
    }

    fun clearVisualImageEdits() {
        _configureState.update { current ->
            current.copy(
                options = current.options.copy(imageVisualEditSerialized = "")
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
                        stripExifMetadata = appPreferences.defaultStripExif.value,
                        pdfSignaturePngPath = appPreferences.savedSignaturePngPath.value
                    ),
                    trimStartText = "",
                    trimEndText = "",
                    targetImageMaxKbText = "",
                    targetVideoMaxMbText = "",
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

    fun updateTargetImageMaxSizeKb(rawText: String) {
        val clean = rawText.filter { it.isDigit() }.take(6)
        val kb = clean.toIntOrNull()?.takeIf { it > 0 }
        _configureState.update { current ->
            current.copy(
                targetImageMaxKbText = clean,
                options = current.options.copy(targetImageMaxSizeKb = kb)
            )
        }
    }

    fun updateImageBatchLayoutMode(mode: ImageBatchLayoutMode) {
        _configureState.update { current ->
            current.copy(options = current.options.copy(imageBatchLayoutMode = mode))
        }
    }

    fun updateAnimatedGifFps(fps: Int) {
        _configureState.update { current ->
            current.copy(options = current.options.copy(animatedGifFps = fps.coerceIn(1, 24)))
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

    fun updateVideoPlaybackSpeed(factor: Float) {
        _configureState.update { current ->
            current.copy(options = current.options.copy(videoPlaybackSpeedFactor = factor))
        }
    }

    fun updateVideoRotationDegrees(degrees: Int) {
        _configureState.update { current ->
            current.copy(options = current.options.copy(videoRotationDegrees = degrees))
        }
    }

    fun updateVideoFlipHorizontal(flip: Boolean) {
        _configureState.update { current ->
            current.copy(options = current.options.copy(videoFlipHorizontal = flip))
        }
    }

    fun updateTargetVideoMaxSizeMb(rawText: String) {
        val clean = rawText.filter { it.isDigit() }.take(5)
        val mb = clean.toIntOrNull()?.takeIf { it > 0 }
        _configureState.update { current ->
            current.copy(
                targetVideoMaxMbText = clean,
                options = current.options.copy(targetVideoMaxSizeMb = mb)
            )
        }
    }

    fun updateMergeMediaSequence(merge: Boolean) {
        _configureState.update { current ->
            current.copy(options = current.options.copy(mergeMediaSequence = merge))
        }
    }

    fun updateAudioBitrateKbps(kbps: Int) {
        _configureState.update { current ->
            current.copy(options = current.options.copy(audioBitrateKbps = kbps))
        }
    }

    fun updateAudioFadeSeconds(fadeIn: Int, fadeOut: Int) {
        _configureState.update { current ->
            current.copy(
                options = current.options.copy(
                    audioFadeInSeconds = fadeIn.coerceIn(0, 15),
                    audioFadeOutSeconds = fadeOut.coerceIn(0, 15)
                )
            )
        }
    }

    fun updateExportAsRingtone(ringtone: Boolean) {
        _configureState.update { current ->
            current.copy(options = current.options.copy(exportAsRingtone = ringtone))
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

    fun updatePdfSplitChunkPages(pages: Int) {
        _configureState.update { current ->
            current.copy(
                options = current.options.copy(pdfSplitChunkPages = pages.coerceIn(1, 500))
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

    fun updatePdfWatermarkText(text: String) {
        _configureState.update { current ->
            current.copy(options = current.options.copy(pdfWatermarkText = text))
        }
    }

    fun updateAddPdfPageNumbers(enabled: Boolean) {
        _configureState.update { current ->
            current.copy(options = current.options.copy(addPdfPageNumbers = enabled))
        }
    }

    fun updatePdfSignaturePngPath(path: String) {
        appPreferences.setSavedSignaturePngPath(path)
        _configureState.update { current ->
            current.copy(options = current.options.copy(pdfSignaturePngPath = path))
        }
    }

    fun updatePdfStampQrContent(content: String) {
        _configureState.update { current ->
            current.copy(options = current.options.copy(pdfStampQrContent = content))
        }
    }

    fun updateArchiveCompressionLevel(level: ArchiveCompressionLevel) {
        _configureState.update { current ->
            current.copy(options = current.options.copy(archiveCompressionLevel = level))
        }
    }

    fun updateSelectedArchiveEntries(entries: Set<String>) {
        _configureState.update { current ->
            current.copy(
                options = current.options.copy(
                    selectedArchiveEntriesSerialized = entries.joinToString("\n")
                )
            )
        }
    }

    fun updateFileSplitPartSizeMb(sizeMb: Int) {
        _configureState.update { current ->
            current.copy(options = current.options.copy(fileSplitPartSizeMb = sizeMb.coerceIn(1, 500)))
        }
    }

    fun updatePreserveSubfolderStructure(preserve: Boolean) {
        _configureState.update { current ->
            current.copy(options = current.options.copy(preserveSubfolderStructure = preserve))
        }
    }

    fun updatePrettyPrintStructuredData(pretty: Boolean) {
        _configureState.update { current ->
            current.copy(options = current.options.copy(prettyPrintStructuredData = pretty))
        }
    }

    fun updateTextEncoding(encoding: TextEncodingOption) {
        _configureState.update { current ->
            current.copy(options = current.options.copy(textEncoding = encoding))
        }
    }

    fun updateLineEndingOption(option: LineEndingOption) {
        _configureState.update { current ->
            current.copy(options = current.options.copy(lineEndingOption = option))
        }
    }

    fun updateVaultPassword(password: String) {
        _configureState.update { current ->
            current.copy(options = current.options.copy(vaultPassword = password))
        }
    }

    fun updateQrCustomText(text: String) {
        _configureState.update { current ->
            current.copy(options = current.options.copy(qrCustomText = text))
        }
    }

    fun updateSecondaryPipelineAction(action: SecondaryPipelineAction) {
        _configureState.update { current ->
            current.copy(options = current.options.copy(secondaryPipelineAction = action))
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
            SupportedFormat.JOIN_PARTS in available && sourceFormats.any { it == SupportedFormat.FILE_PARTS } ->
                SupportedFormat.JOIN_PARTS
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
                        scopedStorageManager = container.scopedStorageManager,
                        forensicMetadataHelper = container.forensicMetadataHelper,
                        archiveEngineImpl = container.archiveEngineImpl,
                        engineRegistry = container.converterEngineRegistry,
                        historyRepository = container.historyRepository,
                        presetRepository = container.presetRepository,
                        workScheduler = container.workScheduler
                    ) as T
                }
            }
        }
    }
}
