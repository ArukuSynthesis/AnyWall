package com.anywall.converter.domain.model

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Parámetros configurables para cualquier lote de conversión en AnyWall (v0.4).
 * Incluye serialización determinista pura en Kotlin (`serializeToString` / `deserializeFromString`)
 * para almacenamiento en Room v3 (`conversion_presets`), payloads de WorkManager y pruebas JUnit en JVM.
 */
data class ConversionOptions(
    val qualityPercent: Int = 85,
    val maxDimensionPx: Int? = null,
    val imageRotationDegrees: Int = 0,
    val stripExifMetadata: Boolean = true,
    val targetImageMaxSizeKb: Int? = null,
    val imageBatchLayoutMode: ImageBatchLayoutMode = ImageBatchLayoutMode.INDIVIDUAL,
    val imageVisualEditSerialized: String = "",
    val animatedGifFps: Int = 4,
    val videoResolution: VideoResolution = VideoResolution.ORIGINAL,
    val muteVideoAudio: Boolean = false,
    val trimStartSeconds: Long? = null,
    val trimEndSeconds: Long? = null,
    val trimStartMillis: Long = 0L,
    val trimEndMillis: Long = 0L,
    val videoPlaybackSpeedFactor: Float = 1.0f,
    val videoRotationDegrees: Int = 0,
    val videoFlipHorizontal: Boolean = false,
    val targetVideoMaxSizeMb: Int? = null,
    val videoCropSerialized: String = "",
    val mergeMediaSequence: Boolean = false,
    val audioBitrateKbps: Int = 192,
    val audioFadeInSeconds: Int = 0,
    val audioFadeOutSeconds: Int = 0,
    val audioNormalizeAutoGain: Boolean = false,
    val audioMetadataSerialized: String = "",
    val exportAsRingtone: Boolean = false,
    val customOutputBaseName: String = "",
    val mergeIntoSinglePdf: Boolean = true,
    val pdfActionMode: PdfActionMode = PdfActionMode.MERGE_OR_CONVERT,
    val pageRangeSpec: String = "",
    val pdfSplitChunkPages: Int = 2,
    val pdfPassword: String = "",
    val pdfPageSize: PdfPageSize = PdfPageSize.A4,
    val pdfPageOrientation: PdfPageOrientation = PdfPageOrientation.PORTRAIT,
    val pdfVisualPageEditsJson: String = "",
    val pdfWatermarkText: String = "",
    val addPdfPageNumbers: Boolean = false,
    val pdfSignaturePngPath: String = "",
    val pdfStampQrContent: String = "",
    val archiveCompressionLevel: ArchiveCompressionLevel = ArchiveCompressionLevel.BALANCED,
    val selectedArchiveEntriesSerialized: String = "",
    val archivePassword: String = "",
    val fileSplitPartSizeMb: Int = 25,
    val preserveSubfolderStructure: Boolean = false,
    val prettyPrintStructuredData: Boolean = true,
    val textEncoding: TextEncodingOption = TextEncodingOption.UTF_8,
    val lineEndingOption: LineEndingOption = LineEndingOption.KEEP,
    val vaultPassword: String = "",
    val qrCustomText: String = "",
    val secondaryPipelineAction: SecondaryPipelineAction = SecondaryPipelineAction.NONE
) {
    val clampedQuality: Int
        get() = qualityPercent.coerceIn(10, 100)

    val normalizedRotationDegrees: Int
        get() = ((imageRotationDegrees % 360) + 360) % 360

    val normalizedVideoRotationDegrees: Int
        get() = ((videoRotationDegrees % 360) + 360) % 360

    val effectiveTrimStartMillis: Long
        get() = if (trimStartMillis > 0L) trimStartMillis else ((trimStartSeconds ?: 0L).coerceAtLeast(0L) * 1000L)

    val effectiveTrimEndMillis: Long?
        get() = when {
            trimEndMillis > effectiveTrimStartMillis -> trimEndMillis
            trimEndSeconds != null && trimEndSeconds * 1000L > effectiveTrimStartMillis -> trimEndSeconds * 1000L
            else -> null
        }

    val hasValidTrimRange: Boolean
        get() {
            val startMs = effectiveTrimStartMillis
            val endMs = effectiveTrimEndMillis
            return (startMs > 0L && (endMs == null || endMs > startMs)) || (endMs != null && endMs > startMs && endMs > 0L)
        }

    val hasVisualPdfEdits: Boolean
        get() = pdfVisualPageEditsJson.isNotBlank()

    val hasVisualImageEdits: Boolean
        get() = imageVisualEditSerialized.isNotBlank()

    val imageEditSpec: ImageEditSpec
        get() = ImageEditSpec.fromSerializedString(imageVisualEditSerialized)

    val videoCropSpec: VideoCropSpec
        get() = VideoCropSpec.deserialize(videoCropSerialized)

    val audioMetadataSpec: AudioMetadataSpec
        get() = AudioMetadataSpec.deserialize(audioMetadataSerialized)

    val selectedArchiveEntries: Set<String>
        get() {
            if (selectedArchiveEntriesSerialized.isBlank()) return emptySet()
            return selectedArchiveEntriesSerialized
                .split('\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
        }

    fun serializeToString(): String {
        val map = linkedMapOf(
            "qualityPercent" to qualityPercent.toString(),
            "maxDimensionPx" to (maxDimensionPx?.toString() ?: ""),
            "imageRotationDegrees" to imageRotationDegrees.toString(),
            "stripExifMetadata" to stripExifMetadata.toString(),
            "targetImageMaxSizeKb" to (targetImageMaxSizeKb?.toString() ?: ""),
            "imageBatchLayoutMode" to imageBatchLayoutMode.name,
            "imageVisualEditSerialized" to imageVisualEditSerialized,
            "animatedGifFps" to animatedGifFps.toString(),
            "videoResolution" to videoResolution.name,
            "muteVideoAudio" to muteVideoAudio.toString(),
            "trimStartSeconds" to (trimStartSeconds?.toString() ?: ""),
            "trimEndSeconds" to (trimEndSeconds?.toString() ?: ""),
            "trimStartMillis" to trimStartMillis.toString(),
            "trimEndMillis" to trimEndMillis.toString(),
            "videoPlaybackSpeedFactor" to videoPlaybackSpeedFactor.toString(),
            "videoRotationDegrees" to videoRotationDegrees.toString(),
            "videoFlipHorizontal" to videoFlipHorizontal.toString(),
            "targetVideoMaxSizeMb" to (targetVideoMaxSizeMb?.toString() ?: ""),
            "videoCropSerialized" to videoCropSerialized,
            "mergeMediaSequence" to mergeMediaSequence.toString(),
            "audioBitrateKbps" to audioBitrateKbps.toString(),
            "audioFadeInSeconds" to audioFadeInSeconds.toString(),
            "audioFadeOutSeconds" to audioFadeOutSeconds.toString(),
            "audioNormalizeAutoGain" to audioNormalizeAutoGain.toString(),
            "audioMetadataSerialized" to audioMetadataSerialized,
            "exportAsRingtone" to exportAsRingtone.toString(),
            "customOutputBaseName" to customOutputBaseName,
            "mergeIntoSinglePdf" to mergeIntoSinglePdf.toString(),
            "pdfActionMode" to pdfActionMode.name,
            "pageRangeSpec" to pageRangeSpec,
            "pdfSplitChunkPages" to pdfSplitChunkPages.toString(),
            "pdfPassword" to pdfPassword,
            "pdfPageSize" to pdfPageSize.name,
            "pdfPageOrientation" to pdfPageOrientation.name,
            "pdfVisualPageEditsJson" to pdfVisualPageEditsJson,
            "pdfWatermarkText" to pdfWatermarkText,
            "addPdfPageNumbers" to addPdfPageNumbers.toString(),
            "pdfSignaturePngPath" to pdfSignaturePngPath,
            "pdfStampQrContent" to pdfStampQrContent,
            "archiveCompressionLevel" to archiveCompressionLevel.name,
            "selectedArchiveEntriesSerialized" to selectedArchiveEntriesSerialized,
            "archivePassword" to archivePassword,
            "fileSplitPartSizeMb" to fileSplitPartSizeMb.toString(),
            "preserveSubfolderStructure" to preserveSubfolderStructure.toString(),
            "prettyPrintStructuredData" to prettyPrintStructuredData.toString(),
            "textEncoding" to textEncoding.name,
            "lineEndingOption" to lineEndingOption.name,
            "vaultPassword" to vaultPassword,
            "qrCustomText" to qrCustomText,
            "secondaryPipelineAction" to secondaryPipelineAction.name
        )
        return map.entries.joinToString(separator = "\n") { (k, v) ->
            "$k=${URLEncoder.encode(v, Charsets.UTF_8.name())}"
        }
    }

    companion object {
        fun deserializeFromString(raw: String?): ConversionOptions {
            if (raw.isNullOrBlank()) return ConversionOptions()
            val map = HashMap<String, String>()
            for (line in raw.lines()) {
                val idx = line.indexOf('=')
                if (idx <= 0) continue
                val key = line.substring(0, idx).trim()
                val value = URLDecoder.decode(line.substring(idx + 1), Charsets.UTF_8.name())
                map[key] = value
            }
            return ConversionOptions(
                qualityPercent = map["qualityPercent"]?.toIntOrNull() ?: 85,
                maxDimensionPx = map["maxDimensionPx"]?.toIntOrNull()?.takeIf { it > 0 },
                imageRotationDegrees = map["imageRotationDegrees"]?.toIntOrNull() ?: 0,
                stripExifMetadata = map["stripExifMetadata"]?.toBooleanStrictOrNull() ?: true,
                targetImageMaxSizeKb = map["targetImageMaxSizeKb"]?.toIntOrNull()?.takeIf { it > 0 },
                imageBatchLayoutMode = ImageBatchLayoutMode.fromNameOrDefault(map["imageBatchLayoutMode"]),
                imageVisualEditSerialized = map["imageVisualEditSerialized"].orEmpty(),
                animatedGifFps = map["animatedGifFps"]?.toIntOrNull()?.coerceIn(1, 30) ?: 4,
                videoResolution = VideoResolution.fromNameOrDefault(map["videoResolution"]),
                muteVideoAudio = map["muteVideoAudio"]?.toBooleanStrictOrNull() ?: false,
                trimStartSeconds = map["trimStartSeconds"]?.toLongOrNull()?.takeIf { it >= 0L },
                trimEndSeconds = map["trimEndSeconds"]?.toLongOrNull()?.takeIf { it > 0L },
                trimStartMillis = map["trimStartMillis"]?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
                trimEndMillis = map["trimEndMillis"]?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
                videoPlaybackSpeedFactor = map["videoPlaybackSpeedFactor"]?.toFloatOrNull()?.coerceIn(0.25f, 4.0f) ?: 1.0f,
                videoRotationDegrees = map["videoRotationDegrees"]?.toIntOrNull() ?: 0,
                videoFlipHorizontal = map["videoFlipHorizontal"]?.toBooleanStrictOrNull() ?: false,
                targetVideoMaxSizeMb = map["targetVideoMaxSizeMb"]?.toIntOrNull()?.takeIf { it > 0 },
                videoCropSerialized = map["videoCropSerialized"].orEmpty(),
                mergeMediaSequence = map["mergeMediaSequence"]?.toBooleanStrictOrNull() ?: false,
                audioBitrateKbps = map["audioBitrateKbps"]?.toIntOrNull() ?: 192,
                audioFadeInSeconds = map["audioFadeInSeconds"]?.toIntOrNull()?.coerceIn(0, 30) ?: 0,
                audioFadeOutSeconds = map["audioFadeOutSeconds"]?.toIntOrNull()?.coerceIn(0, 30) ?: 0,
                audioNormalizeAutoGain = map["audioNormalizeAutoGain"]?.toBooleanStrictOrNull() ?: false,
                audioMetadataSerialized = map["audioMetadataSerialized"].orEmpty(),
                exportAsRingtone = map["exportAsRingtone"]?.toBooleanStrictOrNull() ?: false,
                customOutputBaseName = map["customOutputBaseName"].orEmpty(),
                mergeIntoSinglePdf = map["mergeIntoSinglePdf"]?.toBooleanStrictOrNull() ?: true,
                pdfActionMode = PdfActionMode.fromNameOrDefault(map["pdfActionMode"]),
                pageRangeSpec = map["pageRangeSpec"].orEmpty(),
                pdfSplitChunkPages = map["pdfSplitChunkPages"]?.toIntOrNull()?.coerceAtLeast(1) ?: 2,
                pdfPassword = map["pdfPassword"].orEmpty(),
                pdfPageSize = PdfPageSize.fromNameOrDefault(map["pdfPageSize"]),
                pdfPageOrientation = PdfPageOrientation.fromNameOrDefault(map["pdfPageOrientation"]),
                pdfVisualPageEditsJson = map["pdfVisualPageEditsJson"].orEmpty(),
                pdfWatermarkText = map["pdfWatermarkText"].orEmpty(),
                addPdfPageNumbers = map["addPdfPageNumbers"]?.toBooleanStrictOrNull() ?: false,
                pdfSignaturePngPath = map["pdfSignaturePngPath"].orEmpty(),
                pdfStampQrContent = map["pdfStampQrContent"].orEmpty(),
                archiveCompressionLevel = ArchiveCompressionLevel.fromNameOrDefault(map["archiveCompressionLevel"]),
                selectedArchiveEntriesSerialized = map["selectedArchiveEntriesSerialized"].orEmpty(),
                archivePassword = map["archivePassword"].orEmpty(),
                fileSplitPartSizeMb = map["fileSplitPartSizeMb"]?.toIntOrNull()?.coerceAtLeast(1) ?: 25,
                preserveSubfolderStructure = map["preserveSubfolderStructure"]?.toBooleanStrictOrNull() ?: false,
                prettyPrintStructuredData = map["prettyPrintStructuredData"]?.toBooleanStrictOrNull() ?: true,
                textEncoding = TextEncodingOption.fromNameOrDefault(map["textEncoding"]),
                lineEndingOption = LineEndingOption.fromNameOrDefault(map["lineEndingOption"]),
                vaultPassword = map["vaultPassword"].orEmpty(),
                qrCustomText = map["qrCustomText"].orEmpty(),
                secondaryPipelineAction = SecondaryPipelineAction.fromNameOrDefault(map["secondaryPipelineAction"])
            )
        }
    }
}
