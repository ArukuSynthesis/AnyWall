package com.anywall.converter.domain.model

import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Estimador heurístico inteligente de tamaño de salida (`OutputSizeEstimator` - v0.4).
 * Calcula en tiempo real el peso estimado final y el porcentaje de ahorro/variación antes de iniciar
 * la conversión en `ConfigureScreen`.
 */
typealias OutputSizeEstimate = OutputSizeEstimator.SizeEstimateResult

object OutputSizeEstimator {

    data class SizeEstimateResult(
        val sourceTotalBytes: Long,
        val estimatedBytes: Long,
        val minBytes: Long,
        val maxBytes: Long,
        val deltaPercent: Int,
        val formattedEstimate: String,
        val badgeTextEs: String,
        val badgeTextEn: String
    ) {
        val formattedRange: String
            get() = "~${InputFileItem.formatBytes(estimatedBytes)}"
        val formattedSavingsBadge: String
            get() = if (deltaPercent > 0) "+$deltaPercent%" else "$deltaPercent%"
    }

    fun estimateBatchOutputSize(
        inputs: List<InputFileItem>,
        targetFormat: SupportedFormat?,
        options: ConversionOptions
    ): SizeEstimateResult? = estimateOutputSize(inputs, targetFormat, options)

    fun estimateOutputSize(
        inputs: List<InputFileItem>,
        targetFormat: SupportedFormat?,
        options: ConversionOptions
    ): SizeEstimateResult? {
        if (inputs.isEmpty() || targetFormat == null) return null
        val totalSourceBytes = inputs.sumOf { it.sizeBytes.coerceAtLeast(1024L) }
        if (totalSourceBytes <= 0L) return null

        val qualityFactor = (options.clampedQuality / 100.0).coerceIn(0.10, 1.0)
        val editSpec = options.imageEditSpec
        val cropAreaRatio = if (editSpec.hasCrop) {
            val w = (editSpec.cropRightNorm - editSpec.cropLeftNorm).coerceIn(0.1f, 1f)
            val h = (editSpec.cropBottomNorm - editSpec.cropTopNorm).coerceIn(0.1f, 1f)
            (w * h).toDouble().coerceIn(0.05, 1.0)
        } else {
            1.0
        }

        val scaleRatio = when (val maxDim = options.maxDimensionPx) {
            null -> 1.0
            720 -> 0.35
            1080 -> 0.55
            1440 -> 0.72
            2048 -> 0.88
            else -> (maxDim.toDouble() / 2400.0).coerceIn(0.25, 1.0)
        }

        val rawEstimated: Long = when {
            // 1. Imágenes con límite explícito en KB
            targetFormat in setOf(SupportedFormat.JPG, SupportedFormat.WEBP) &&
                (options.targetImageMaxSizeKb ?: 0) > 0 -> {
                val maxPerImageBytes = options.targetImageMaxSizeKb!!.toLong() * 1024L
                val count = if (options.imageBatchLayoutMode == ImageBatchLayoutMode.INDIVIDUAL) inputs.size else 1
                minOf(totalSourceBytes, maxPerImageBytes * count)
            }

            // 2. Salida a WEBP / JPG / PNG / BMP / ICO / GIF
            targetFormat == SupportedFormat.WEBP -> {
                val baseRatio = 0.22 + 0.50 * (qualityFactor * qualityFactor)
                (totalSourceBytes * baseRatio * cropAreaRatio * scaleRatio).roundToLong()
            }
            targetFormat == SupportedFormat.JPG -> {
                val baseRatio = 0.28 + 0.62 * (qualityFactor * qualityFactor)
                (totalSourceBytes * baseRatio * cropAreaRatio * scaleRatio).roundToLong()
            }
            targetFormat == SupportedFormat.PNG -> {
                val fromLossy = inputs.any { it.format in setOf(SupportedFormat.JPG, SupportedFormat.WEBP, SupportedFormat.HEIC, SupportedFormat.AVIF) }
                val baseRatio = if (fromLossy) 1.55 else 0.92
                (totalSourceBytes * baseRatio * cropAreaRatio * scaleRatio).roundToLong()
            }
            targetFormat == SupportedFormat.BMP -> {
                (totalSourceBytes * 2.8 * cropAreaRatio * scaleRatio).roundToLong()
            }
            targetFormat == SupportedFormat.ICO -> {
                (inputs.size * 48L * 1024L)
            }
            targetFormat == SupportedFormat.ICON_PACK_ZIP -> {
                (inputs.size * 145L * 1024L)
            }
            targetFormat == SupportedFormat.GIF -> {
                if (inputs.any { it.format.isVideo }) {
                    val durSec = computeTrimmedDurationSec(options, defaultSec = 5L)
                    (durSec * options.animatedGifFps.coerceIn(2, 12) * 38L * 1024L)
                } else {
                    (totalSourceBytes * 0.75 * scaleRatio).roundToLong()
                }
            }

            // 3. Salida a PDF o CBZ
            targetFormat == SupportedFormat.PDF -> {
                val activePdfPages = if (options.hasVisualPdfEdits) {
                    val all = PdfPageEditItem.fromJsonArrayString(options.pdfVisualPageEditsJson)
                    val active = all.count { it.included }
                    if (all.isNotEmpty()) active.toDouble() / all.size.toDouble() else 1.0
                } else {
                    1.0
                }
                val compressionRatio = 0.35 + 0.65 * qualityFactor
                (totalSourceBytes * activePdfPages * compressionRatio * scaleRatio).roundToLong()
            }
            targetFormat == SupportedFormat.CBZ -> {
                (totalSourceBytes * 0.88).roundToLong()
            }
            targetFormat == SupportedFormat.EPUB -> {
                (totalSourceBytes * 0.65).roundToLong()
            }

            // 4. Salida de Video (MP4, MKV, MOV, WEBM)
            targetFormat.isVideo -> {
                if ((options.targetVideoMaxSizeMb ?: 0) > 0) {
                    val count = if (options.mergeMediaSequence) 1 else inputs.size
                    minOf(totalSourceBytes, options.targetVideoMaxSizeMb!!.toLong() * 1024L * 1024L * count)
                } else {
                    val resFactor = when (options.videoResolution) {
                        VideoResolution.ORIGINAL -> 0.92
                        VideoResolution.FHD_1080P -> 0.78
                        VideoResolution.HD_720P -> 0.48
                        VideoResolution.SD_480P -> 0.28
                    }
                    val muteFactor = if (options.muteVideoAudio) 0.90 else 1.0
                    val cropVideoSpec = options.videoCropSpec
                    val videoCropFactor = if (cropVideoSpec.isActive) {
                        val cw = (cropVideoSpec.rightNorm - cropVideoSpec.leftNorm).coerceIn(0.2f, 1f)
                        val ch = (cropVideoSpec.bottomNorm - cropVideoSpec.topNorm).coerceIn(0.2f, 1f)
                        0.4 + 0.6 * (cw * ch)
                    } else {
                        1.0
                    }
                    val speedFactor = (1.0 / options.videoPlaybackSpeedFactor.coerceIn(0.25f, 4.0f).toDouble())
                    (totalSourceBytes * resFactor * muteFactor * videoCropFactor * speedFactor).roundToLong()
                }
            }

            // 5. Salida de Audio (MP3, AAC, M4A, WAV)
            targetFormat.isAudio -> {
                val allVideos = inputs.all { it.format.isVideo }
                val baseAudioBytes = if (allVideos) {
                    (totalSourceBytes * 0.14).roundToLong()
                } else {
                    totalSourceBytes
                }
                val bitrateRatio = (options.audioBitrateKbps.coerceIn(64, 320) / 192.0)
                when (targetFormat) {
                    SupportedFormat.WAV -> (baseAudioBytes * 4.2).roundToLong()
                    SupportedFormat.MP3, SupportedFormat.AAC, SupportedFormat.M4A -> {
                        val fromLossless = inputs.any { it.format in setOf(SupportedFormat.WAV, SupportedFormat.FLAC) }
                        val mult = if (fromLossless) 0.22 * bitrateRatio else 0.85 * bitrateRatio
                        (baseAudioBytes * mult).roundToLong()
                    }
                    else -> baseAudioBytes
                }
            }

            // 6. Archivos comprimidos y extracción
            targetFormat in setOf(
                SupportedFormat.ZIP,
                SupportedFormat.SEVEN_Z,
                SupportedFormat.TAR,
                SupportedFormat.TAR_GZ,
                SupportedFormat.TAR_XZ,
                SupportedFormat.TAR_BZ2
            ) -> {
                val levelFactor = when (options.archiveCompressionLevel) {
                    ArchiveCompressionLevel.FAST -> 0.78
                    ArchiveCompressionLevel.BALANCED -> 0.66
                    ArchiveCompressionLevel.MAXIMUM -> 0.54
                }
                val fmtFactor = when (targetFormat) {
                    SupportedFormat.TAR -> 1.02
                    SupportedFormat.ZIP -> levelFactor
                    SupportedFormat.TAR_GZ -> levelFactor * 0.95
                    SupportedFormat.TAR_BZ2 -> levelFactor * 0.88
                    SupportedFormat.TAR_XZ, SupportedFormat.SEVEN_Z -> levelFactor * 0.80
                    else -> levelFactor
                }
                (totalSourceBytes * fmtFactor).roundToLong()
            }

            targetFormat == SupportedFormat.EXTRACT_ARCHIVE -> {
                (totalSourceBytes * 1.35).roundToLong()
            }

            targetFormat == SupportedFormat.ANYWALL_VAULT -> {
                totalSourceBytes + 128L * inputs.size
            }

            else -> (totalSourceBytes * 0.90).roundToLong()
        }.coerceAtLeast(256L)

        val minBytes = (rawEstimated * 0.82).roundToLong().coerceAtLeast(128L)
        val maxBytes = (rawEstimated * 1.18).roundToLong().coerceAtLeast(minBytes)
        val deltaPct = (((rawEstimated.toDouble() - totalSourceBytes.toDouble()) / totalSourceBytes.toDouble()) * 100.0)
            .roundToInt()
            .coerceIn(-98, 500)

        val signPrefix = if (deltaPct > 0) "+$deltaPct%" else "$deltaPct%"
        val formattedEst = "~${InputFileItem.formatBytes(rawEstimated)} ($signPrefix)"

        return SizeEstimateResult(
            sourceTotalBytes = totalSourceBytes,
            estimatedBytes = rawEstimated,
            minBytes = minBytes,
            maxBytes = maxBytes,
            deltaPercent = deltaPct,
            formattedEstimate = formattedEst,
            badgeTextEs = "Salida estimada: $formattedEst",
            badgeTextEn = "Estimated output: $formattedEst"
        )
    }

    private fun computeTrimmedDurationSec(options: ConversionOptions, defaultSec: Long): Long {
        val start = (options.trimStartSeconds ?: 0L).coerceAtLeast(0L)
        val end = options.trimEndSeconds
        return if (end != null && end > start) {
            (end - start).coerceAtLeast(1L)
        } else {
            defaultSec
        }
    }
}
