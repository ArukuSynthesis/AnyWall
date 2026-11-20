package com.anywall.converter.data.local

import com.anywall.converter.data.local.dao.ConversionPresetDao
import com.anywall.converter.data.local.entity.ConversionPresetEntity
import com.anywall.converter.domain.model.ArchiveCompressionLevel
import com.anywall.converter.domain.model.ConversionOptions
import com.anywall.converter.domain.model.ConversionPreset
import com.anywall.converter.domain.model.FormatCategory
import com.anywall.converter.domain.model.ImageFilterPreset
import com.anywall.converter.domain.model.ImageEditSpec
import com.anywall.converter.domain.model.PdfPageSize
import com.anywall.converter.domain.model.SupportedFormat
import com.anywall.converter.domain.model.VideoResolution
import com.anywall.converter.domain.repository.ConversionPresetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Implementación del repositorio de presets en Room v3 con perfiles pre-sembrados (v0.3).
 */
class ConversionPresetRepositoryImpl(
    private val dao: ConversionPresetDao
) : ConversionPresetRepository {

    override fun observeAllPresets(): Flow<List<ConversionPreset>> {
        return dao.observeAllPresets().map { list ->
            list.map { it.toDomainModel() }
        }
    }

    override suspend fun ensureBuiltInPresetsSeeded() {
        if (dao.getPresetCount() > 0) return

        val now = System.currentTimeMillis()
        val builtInPresets = listOf(
            ConversionPreset(
                name = "WhatsApp / Web Ligero",
                description = "WEBP 75% • Máx 1280px • Sin EXIF • < 400 KB",
                category = FormatCategory.IMAGE,
                targetFormat = SupportedFormat.WEBP,
                options = ConversionOptions(
                    qualityPercent = 75,
                    maxDimensionPx = 1280,
                    stripExifMetadata = true,
                    targetImageMaxSizeKb = 400
                ),
                isBuiltIn = true,
                createdAtEpochMillis = now - 5000
            ),
            ConversionPreset(
                name = "Escáner Documento B/N",
                description = "PDF A4 • Alto contraste B/N • Numeración de páginas",
                category = FormatCategory.DOCUMENT,
                targetFormat = SupportedFormat.PDF,
                options = ConversionOptions(
                    qualityPercent = 85,
                    pdfPageSize = PdfPageSize.A4,
                    addPdfPageNumbers = true,
                    imageVisualEditSerialized = ImageEditSpec(
                        filterPreset = ImageFilterPreset.DOCUMENT_BW_HIGH_CONTRAST
                    ).toSerializedString()
                ),
                isBuiltIn = true,
                createdAtEpochMillis = now - 4000
            ),
            ConversionPreset(
                name = "Audio Podcast / Voz",
                description = "MP3 192 kbps • Fade In/Out suave (2s)",
                category = FormatCategory.MEDIA,
                targetFormat = SupportedFormat.MP3,
                options = ConversionOptions(
                    audioBitrateKbps = 192,
                    audioFadeInSeconds = 2,
                    audioFadeOutSeconds = 2
                ),
                isBuiltIn = true,
                createdAtEpochMillis = now - 3000
            ),
            ConversionPreset(
                name = "Video Compacto Mensajería",
                description = "MP4 720p • Límite objetivo 15 MB",
                category = FormatCategory.MEDIA,
                targetFormat = SupportedFormat.MP4,
                options = ConversionOptions(
                    videoResolution = VideoResolution.HD_720P,
                    targetVideoMaxSizeMb = 15
                ),
                isBuiltIn = true,
                createdAtEpochMillis = now - 2000
            ),
            ConversionPreset(
                name = "Archivo Comprimido Máximo",
                description = "TAR.XZ • Compresión máxima LZMA2",
                category = FormatCategory.ARCHIVE,
                targetFormat = SupportedFormat.TAR_XZ,
                options = ConversionOptions(
                    archiveCompressionLevel = ArchiveCompressionLevel.MAXIMUM
                ),
                isBuiltIn = true,
                createdAtEpochMillis = now - 1000
            )
        )

        dao.insertPresets(builtInPresets.map { ConversionPresetEntity.fromDomain(it) })
    }

    override suspend fun savePreset(preset: ConversionPreset): Long {
        return dao.insertPreset(ConversionPresetEntity.fromDomain(preset))
    }

    override suspend fun deletePreset(id: Long) {
        dao.deletePresetById(id)
    }
}
