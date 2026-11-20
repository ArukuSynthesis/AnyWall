package com.anywall.converter.data.engines

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.muxer.Muxer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.DefaultMuxer
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.anywall.converter.core.ScopedStorageManager
import com.anywall.converter.core.UriFileResolver
import com.anywall.converter.domain.engine.FileConverterEngine
import com.anywall.converter.domain.model.ConversionJobResult
import com.anywall.converter.domain.model.ConversionOptions
import com.anywall.converter.domain.model.FormatCategory
import com.anywall.converter.domain.model.InputFileItem
import com.anywall.converter.domain.model.OutputNamingTemplateResolver
import com.anywall.converter.domain.model.SupportedFormat
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Motor 3: `MediaEngineImpl` (Video y Audio Avanzado Offline - v0.3)
 * - Conversión de video (`MP4`, `MKV`, `MOV`, `WEBM`) con resolución (`Original`, `1080p`, `720p`, `480p`),
 *   silenciado (`setRemoveAudio(true)`), recorte (`ClippingConfiguration`), rotación (`90°/180°/270°`),
 *   volteo horizontal (`ScaleAndRotateTransformation`), velocidad (`0.5x..2.0x`) y compresión por peso
 *   objetivo en MB (`targetVideoMaxSizeMb`).
 * - Unión/Concatenación de múltiples videos o múltiples audios en un único archivo (`EditedMediaItemSequence`).
 * - Video → GIF animado y extracción de fotogramas (`JPG` / `PNG`).
 * - Extracción y conversión de audio (`MP3`, `AAC`, `M4A`, `WAV`) con soporte de entrada `FLAC`, `OGG` y `OPUS`,
 *   selector de bitrate (`128`/`192`/`320 kbps`), Fade In/Out y exportación como Tono (`Ringtones`).
 */
@OptIn(UnstableApi::class)
class MediaEngineImpl(
    private val context: Context,
    private val uriResolver: UriFileResolver,
    private val storageManager: ScopedStorageManager
) : FileConverterEngine {

    private val videoFormats = setOf(
        SupportedFormat.MP4,
        SupportedFormat.MKV,
        SupportedFormat.MOV,
        SupportedFormat.WEBM
    )

    private val audioOutputFormats = setOf(
        SupportedFormat.MP3,
        SupportedFormat.AAC,
        SupportedFormat.M4A,
        SupportedFormat.WAV
    )

    private val audioInputFormats = audioOutputFormats + setOf(
        SupportedFormat.FLAC,
        SupportedFormat.OGG,
        SupportedFormat.OPUS
    )

    private val videoFrameOutputFormats = setOf(
        SupportedFormat.GIF,
        SupportedFormat.JPG,
        SupportedFormat.PNG
    )

    private val allMediaInputFormats = videoFormats + audioInputFormats

    override fun canHandle(format: SupportedFormat): Boolean {
        return format in allMediaInputFormats
    }

    override fun getSupportedTargetFormats(sourceFormats: List<SupportedFormat>): List<SupportedFormat> {
        if (sourceFormats.isEmpty()) return emptyList()
        val distinct = sourceFormats.distinct()

        if (!distinct.all { it in allMediaInputFormats }) {
            return emptyList()
        }

        val allSourcesAreVideo = distinct.all { it in videoFormats }
        return if (allSourcesAreVideo) {
            listOf(
                SupportedFormat.MP4,
                SupportedFormat.MKV,
                SupportedFormat.MOV,
                SupportedFormat.WEBM,
                SupportedFormat.MP3,
                SupportedFormat.AAC,
                SupportedFormat.M4A,
                SupportedFormat.WAV,
                SupportedFormat.GIF,
                SupportedFormat.JPG,
                SupportedFormat.PNG
            )
        } else {
            listOf(
                SupportedFormat.MP3,
                SupportedFormat.AAC,
                SupportedFormat.M4A,
                SupportedFormat.WAV
            )
        }
    }

    override suspend fun convert(
        inputs: List<InputFileItem>,
        targetFormat: SupportedFormat,
        options: ConversionOptions,
        onProgress: (Float, String) -> Unit
    ): List<ConversionJobResult> = withContext(Dispatchers.IO) {
        require(inputs.isNotEmpty()) { "No se proporcionaron archivos multimedia para convertir." }

        // Caso A: Video -> GIF animado o extracción de fotogramas JPG/PNG
        if (inputs.all { it.format in videoFormats } && targetFormat in videoFrameOutputFormats) {
            return@withContext convertVideosToGifOrFrames(inputs, targetFormat, options, onProgress)
        }

        // Caso B: Unión/Concatenación de múltiples videos en uno solo
        if (inputs.size >= 2 && options.mergeMediaSequence && targetFormat in videoFormats) {
            return@withContext concatenateVideoBatch(inputs, targetFormat, options, onProgress)
        }

        // Caso C: Unión/Concatenación de múltiples audios en uno solo
        if (inputs.size >= 2 && options.mergeMediaSequence && targetFormat in audioOutputFormats) {
            return@withContext concatenateAudioBatch(inputs, targetFormat, options, onProgress)
        }

        val results = mutableListOf<ConversionJobResult>()
        val total = inputs.size

        inputs.forEachIndexed { index, item ->
            coroutineContext.ensureActive()
            val baseFraction = index.toFloat() / total.toFloat()
            val slice = 1f / total.toFloat()

            val reportItemProgress: (Float, String) -> Unit = { itemP, msg ->
                val globalP = (baseFraction + (itemP.coerceIn(0f, 1f) * slice)).coerceIn(0f, 0.99f)
                onProgress(globalP, msg)
            }

            reportItemProgress(0.01f, "Preparando ${item.displayName}…")

            val result = if (targetFormat in videoFormats) {
                convertVideoFile(item, index, total, targetFormat, options, reportItemProgress)
            } else if (targetFormat in audioOutputFormats) {
                convertOrExtractAudioFile(item, index, total, targetFormat, options, reportItemProgress)
            } else {
                throw IllegalArgumentException("Formato multimedia no soportado: $targetFormat")
            }

            results.add(result)
        }

        onProgress(1f, "Procesamiento multimedia finalizado (${results.size}).")
        results
    }

    private fun buildMediaItemWithOptionalTrim(tempInput: File, options: ConversionOptions): MediaItem {
        val builder = MediaItem.Builder().setUri(android.net.Uri.fromFile(tempInput))
        if (options.hasValidTrimRange) {
            val startMs = (options.trimStartSeconds ?: 0L).coerceAtLeast(0L) * 1000L
            val endSec = options.trimEndSeconds
            val clipBuilder = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(startMs)
            if (endSec != null && endSec * 1000L > startMs) {
                clipBuilder.setEndPositionMs(endSec * 1000L)
            }
            builder.setClippingConfiguration(clipBuilder.build())
        }
        return builder.build()
    }

    private fun buildVideoAndAudioEffects(options: ConversionOptions): Effects {
        val videoEffects = mutableListOf<Effect>()
        val audioProcessors = mutableListOf<AudioProcessor>()

        val rot = options.normalizedVideoRotationDegrees
        if (rot != 0 || options.videoFlipHorizontal) {
            val scaleAndRotate = ScaleAndRotateTransformation.Builder().apply {
                if (rot != 0) {
                    setRotationDegrees(rot.toFloat())
                }
                if (options.videoFlipHorizontal) {
                    setScale(-1f, 1f)
                }
            }.build()
            videoEffects.add(scaleAndRotate)
        }

        val targetHeight = options.videoResolution.targetHeightPx
        if (targetHeight != null && targetHeight > 0) {
            videoEffects.add(Presentation.createForHeight(targetHeight))
        }

        val speed = options.videoPlaybackSpeedFactor.coerceIn(0.25f, 4.0f)
        if (abs(speed - 1.0f) > 0.02f) {
            videoEffects.add(SpeedChangeEffect(speed))
            val sonic = SonicAudioProcessor().apply {
                setSpeed(speed)
                setPitch(1.0f)
            }
            audioProcessors.add(sonic)
        }

        return Effects(audioProcessors, videoEffects)
    }

    private fun computeTargetVideoBitrateBps(tempVideoFile: File, options: ConversionOptions): Int? {
        val targetMb = options.targetVideoMaxSizeMb?.takeIf { it > 0 } ?: return null
        val retriever = MediaMetadataRetriever()
        val durationMs = try {
            retriever.setDataSource(tempVideoFile.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 10_000L
        } catch (_: Throwable) {
            10_000L
        } finally {
            try {
                retriever.release()
            } catch (_: Throwable) {
            }
        }

        val effectiveDurationSec = if (options.hasValidTrimRange) {
            val s = (options.trimStartSeconds ?: 0L).coerceAtLeast(0L)
            val e = options.trimEndSeconds ?: (durationMs / 1000L).coerceAtLeast(s + 1L)
            (e - s).coerceAtLeast(1L)
        } else {
            (durationMs / 1000L).coerceAtLeast(1L)
        }

        val totalTargetBits = targetMb.toLong() * 8L * 1024L * 1024L
        val safeTotalBps = ((totalTargetBits * 90L) / (100L * effectiveDurationSec)).toInt()
        val audioBps = if (options.muteVideoAudio) 0 else (options.audioBitrateKbps.coerceIn(64, 320) * 1000)
        return (safeTotalBps - audioBps).coerceIn(250_000, 25_000_000)
    }

    private suspend fun convertVideoFile(
        item: InputFileItem,
        index: Int,
        total: Int,
        targetFormat: SupportedFormat,
        options: ConversionOptions,
        onItemProgress: (Float, String) -> Unit
    ): ConversionJobResult {
        val tempInput = uriResolver.copyUriToTempFile(
            uri = item.uri,
            prefix = "vid_in_",
            extension = item.format.extension
        )
        val tempOutput = uriResolver.createTempWorkingFile(
            prefix = "vid_out_",
            extension = targetFormat.extension
        )

        try {
            val mediaItem = buildMediaItemWithOptionalTrim(tempInput, options)
            val effects = buildVideoAndAudioEffects(options)
            val targetBitrateBps = computeTargetVideoBitrateBps(tempInput, options)

            val editedMediaItem = EditedMediaItem.Builder(mediaItem)
                .setRemoveAudio(options.muteVideoAudio)
                .setEffects(effects)
                .build()

            val useWebmContainer = (targetFormat == SupportedFormat.WEBM || targetFormat == SupportedFormat.MKV)

            val transformSuccess = if (useWebmContainer) {
                try {
                    runMedia3TransformerWithPolling(
                        editedMediaItem = editedMediaItem,
                        outputFile = tempOutput,
                        videoMimeType = MimeTypes.VIDEO_VP8,
                        audioMimeType = if (options.muteVideoAudio) null else MimeTypes.AUDIO_OPUS,
                        muxerFactory = WebmMatroskaMuxerFactory(),
                        targetVideoBitrateBps = targetBitrateBps,
                        statusPrefix = "Convirtiendo video ${item.displayName}",
                        onProgress = onItemProgress
                    )
                    true
                } catch (_: Throwable) {
                    false
                }
            } else {
                false
            }

            if (!transformSuccess) {
                if (tempOutput.exists()) tempOutput.delete()
                runMedia3TransformerWithPolling(
                    editedMediaItem = editedMediaItem,
                    outputFile = tempOutput,
                    videoMimeType = MimeTypes.VIDEO_H264,
                    audioMimeType = if (options.muteVideoAudio) null else MimeTypes.AUDIO_AAC,
                    muxerFactory = DefaultMuxer.Factory(),
                    targetVideoBitrateBps = targetBitrateBps,
                    statusPrefix = "Convirtiendo video ${item.displayName}",
                    onProgress = onItemProgress
                )
            }

            val baseName = OutputNamingTemplateResolver.resolveBaseName(
                templateOrCustomName = options.customOutputBaseName,
                originalBaseName = item.baseNameWithoutExtension,
                batchIndexOneBased = index + 1,
                batchTotalCount = total,
                targetFormat = targetFormat
            )
            val desiredFileName = UriFileResolver.buildOutputFileName(
                baseName = baseName,
                extension = targetFormat.extension
            )

            val saved = storageManager.saveTempFileToDownloads(
                sourceFile = tempOutput,
                desiredDisplayName = desiredFileName,
                mimeType = targetFormat.mimeType,
                subFolder = storageManager.resolveEffectiveSubFolder(
                    relativeFolderPath = item.relativeFolderPath,
                    categorySubfolder = "Videos",
                    preserveTreeOverride = options.preserveSubfolderStructure
                )
            )

            return ConversionJobResult(
                sourceDisplayName = item.displayName,
                sourceFormat = item.format,
                sourceSizeBytes = item.sizeBytes,
                outputDisplayName = saved.displayName,
                outputFormat = targetFormat,
                outputCategory = FormatCategory.MEDIA,
                outputUri = saved.uri,
                outputAbsolutePath = saved.absolutePath,
                outputSizeBytes = saved.sizeBytes,
                outputMimeType = saved.mimeType
            )
        } finally {
            tempInput.delete()
            tempOutput.delete()
        }
    }

    private suspend fun concatenateVideoBatch(
        inputs: List<InputFileItem>,
        targetFormat: SupportedFormat,
        options: ConversionOptions,
        onProgress: (Float, String) -> Unit
    ): List<ConversionJobResult> {
        val tempInputs = mutableListOf<File>()
        val tempOutput = uriResolver.createTempWorkingFile("vid_concat_", targetFormat.extension)
        try {
            val effects = buildVideoAndAudioEffects(options)
            val editedItems = inputs.mapIndexed { idx, item ->
                coroutineContext.ensureActive()
                onProgress((idx.toFloat() / inputs.size) * 0.15f, "Preparando clip ${idx + 1} de ${inputs.size}: ${item.displayName}")
                val tempIn = uriResolver.copyUriToTempFile(item.uri, "concat_in_", item.format.extension)
                tempInputs.add(tempIn)
                val mediaItem = buildMediaItemWithOptionalTrim(tempIn, options)
                EditedMediaItem.Builder(mediaItem)
                    .setRemoveAudio(options.muteVideoAudio)
                    .setEffects(effects)
                    .build()
            }

            val sequence = EditedMediaItemSequence(editedItems)
            val composition = Composition.Builder(listOf(sequence)).build()

            runMedia3CompositionWithPolling(
                composition = composition,
                outputFile = tempOutput,
                videoMimeType = MimeTypes.VIDEO_H264,
                audioMimeType = if (options.muteVideoAudio) null else MimeTypes.AUDIO_AAC,
                muxerFactory = DefaultMuxer.Factory(),
                statusPrefix = "Uniendo ${inputs.size} videos",
                onProgress = { p, msg ->
                    onProgress(0.15f + p * 0.82f, msg)
                }
            )

            val firstInput = inputs.first()
            val baseName = options.customOutputBaseName.ifBlank {
                "${firstInput.baseNameWithoutExtension}_unido_${inputs.size}_clips"
            }
            val saved = storageManager.saveTempFileToDownloads(
                sourceFile = tempOutput,
                desiredDisplayName = UriFileResolver.buildOutputFileName(baseName, targetFormat.extension),
                mimeType = targetFormat.mimeType
            )
            onProgress(1f, "Video unido guardado: ${saved.displayName}")
            return listOf(
                ConversionJobResult(
                    sourceDisplayName = "${firstInput.displayName} (+${inputs.size - 1} clips)",
                    sourceFormat = firstInput.format,
                    sourceSizeBytes = inputs.sumOf { it.sizeBytes },
                    outputDisplayName = saved.displayName,
                    outputFormat = targetFormat,
                    outputCategory = FormatCategory.MEDIA,
                    outputUri = saved.uri,
                    outputAbsolutePath = saved.absolutePath,
                    outputSizeBytes = saved.sizeBytes,
                    outputMimeType = saved.mimeType
                )
            )
        } finally {
            tempInputs.forEach { it.delete() }
            tempOutput.delete()
        }
    }

    private suspend fun concatenateAudioBatch(
        inputs: List<InputFileItem>,
        targetFormat: SupportedFormat,
        options: ConversionOptions,
        onProgress: (Float, String) -> Unit
    ): List<ConversionJobResult> {
        val tempInputs = mutableListOf<File>()
        val combinedPcmFile = uriResolver.createTempWorkingFile("concat_pcm_", "pcm")
        val fadedPcmFile = uriResolver.createTempWorkingFile("faded_pcm_", "pcm")
        val tempFinalOutput = uriResolver.createTempWorkingFile("concat_aud_out_", targetFormat.extension)

        try {
            var targetSampleRate = 44100
            var targetChannels = 2
            var totalBytesWritten = 0L

            FileOutputStream(combinedPcmFile).use { combinedOut ->
                BufferedOutputStream(combinedOut, 64 * 1024).use { bos ->
                    inputs.forEachIndexed { idx, item ->
                        coroutineContext.ensureActive()
                        val tempIn = uriResolver.copyUriToTempFile(item.uri, "aud_cat_in_", item.format.extension)
                        tempInputs.add(tempIn)
                        val singlePcm = uriResolver.createTempWorkingFile("single_cat_", "pcm")
                        try {
                            onProgress(
                                (idx.toFloat() / inputs.size) * 0.55f,
                                "Decodificando pista ${idx + 1} de ${inputs.size}: ${item.displayName}"
                            )
                            val info = PcmWavMp3AudioHelper.decodeMediaFileToPcm16Le(
                                inputFile = tempIn,
                                tempPcmOutputFile = singlePcm,
                                onProgress = {}
                            )
                            if (idx == 0) {
                                targetSampleRate = info.sampleRate
                                targetChannels = info.channelCount
                            }
                            singlePcm.inputStream().use { it.copyTo(bos, 64 * 1024) }
                            totalBytesWritten += info.totalPcmBytes
                        } finally {
                            singlePcm.delete()
                        }
                    }
                    bos.flush()
                }
            }

            var pcmInfo = PcmWavMp3AudioHelper.DecodedPcmInfo(
                pcmFile = combinedPcmFile,
                sampleRate = targetSampleRate,
                channelCount = targetChannels,
                totalPcmBytes = totalBytesWritten
            )

            if (options.audioFadeInSeconds > 0 || options.audioFadeOutSeconds > 0) {
                pcmInfo = PcmWavMp3AudioHelper.applyFadeInOutToPcm16Le(
                    pcmInfo = pcmInfo,
                    fadeInSeconds = options.audioFadeInSeconds,
                    fadeOutSeconds = options.audioFadeOutSeconds,
                    outputPcmFile = fadedPcmFile
                )
            }

            onProgress(0.65f, "Codificando audio unido a ${targetFormat.label}…")
            val effectiveTarget = if (targetFormat == SupportedFormat.WAV) SupportedFormat.WAV else SupportedFormat.MP3
            if (effectiveTarget == SupportedFormat.WAV) {
                PcmWavMp3AudioHelper.writeWavFileFromPcm16Le(pcmInfo, tempFinalOutput)
            } else {
                PcmWavMp3AudioHelper.encodePcm16LeToMp3(
                    pcmInfo = pcmInfo,
                    outputMp3File = tempFinalOutput,
                    qualityPercent = options.clampedQuality,
                    explicitBitrateKbps = options.audioBitrateKbps,
                    onProgress = { subP ->
                        onProgress(0.65f + subP * 0.32f, "Codificando audio unido (${(subP * 100).toInt()}%)…")
                    }
                )
            }

            val firstInput = inputs.first()
            val baseName = options.customOutputBaseName.ifBlank {
                "${firstInput.baseNameWithoutExtension}_unido_${inputs.size}_audios"
            }
            val saved = storageManager.saveTempFileToDownloads(
                sourceFile = tempFinalOutput,
                desiredDisplayName = UriFileResolver.buildOutputFileName(baseName, effectiveTarget.extension),
                mimeType = effectiveTarget.mimeType,
                subFolder = if (options.exportAsRingtone) "Ringtones" else null
            )
            onProgress(1f, "Audio unido guardado: ${saved.displayName}")
            return listOf(
                ConversionJobResult(
                    sourceDisplayName = "${firstInput.displayName} (+${inputs.size - 1} audios)",
                    sourceFormat = firstInput.format,
                    sourceSizeBytes = inputs.sumOf { it.sizeBytes },
                    outputDisplayName = saved.displayName,
                    outputFormat = effectiveTarget,
                    outputCategory = FormatCategory.MEDIA,
                    outputUri = saved.uri,
                    outputAbsolutePath = saved.absolutePath,
                    outputSizeBytes = saved.sizeBytes,
                    outputMimeType = saved.mimeType
                )
            )
        } finally {
            tempInputs.forEach { it.delete() }
            combinedPcmFile.delete()
            fadedPcmFile.delete()
            tempFinalOutput.delete()
        }
    }

    private suspend fun convertVideosToGifOrFrames(
        inputs: List<InputFileItem>,
        targetFormat: SupportedFormat,
        options: ConversionOptions,
        onProgress: (Float, String) -> Unit
    ): List<ConversionJobResult> {
        val results = mutableListOf<ConversionJobResult>()
        val total = inputs.size

        inputs.forEachIndexed { idx, item ->
            coroutineContext.ensureActive()
            val tempVideo = uriResolver.copyUriToTempFile(item.uri, "vframe_in_", item.format.extension)
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(tempVideo.absolutePath)
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()?.coerceAtLeast(500L) ?: 3000L

                val startMs = ((options.trimStartSeconds ?: 0L) * 1000L).coerceIn(0L, (durationMs - 200L).coerceAtLeast(0L))
                val endMs = (options.trimEndSeconds?.let { it * 1000L } ?: durationMs)
                    .coerceIn(startMs + 200L, durationMs)
                val spanMs = (endMs - startMs).coerceAtLeast(200L)

                if (targetFormat == SupportedFormat.GIF) {
                    val fps = options.animatedGifFps.coerceIn(2, 12)
                    val frameCount = ((spanMs / 1000.0) * fps).roundToInt().coerceIn(4, 36)
                    val maxSide = (options.maxDimensionPx ?: 480).coerceIn(160, 720)
                    val frames = ArrayList<AnimatedGifPureEncoder.GifFrame>(frameCount)

                    for (f in 0 until frameCount) {
                        coroutineContext.ensureActive()
                        onProgress(
                            (idx + (f.toFloat() / frameCount) * 0.8f) / total,
                            "Extrayendo fotograma ${f + 1} de $frameCount (${item.displayName})…"
                        )
                        val timeUs = (startMs + (spanMs * f) / max(1, frameCount - 1)) * 1000L
                        val rawBmp = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            ?: retriever.getFrameAtTime(0L)
                            ?: continue
                        val curMax = max(rawBmp.width, rawBmp.height).coerceAtLeast(1)
                        val scaled = if (curMax > maxSide) {
                            val ratio = maxSide.toFloat() / curMax.toFloat()
                            Bitmap.createScaledBitmap(
                                rawBmp,
                                (rawBmp.width * ratio).roundToInt().coerceAtLeast(16),
                                (rawBmp.height * ratio).roundToInt().coerceAtLeast(16),
                                true
                            ).also { if (it !== rawBmp) rawBmp.recycle() }
                        } else {
                            rawBmp
                        }
                        try {
                            val w = scaled.width
                            val h = scaled.height
                            val px = IntArray(w * h)
                            scaled.getPixels(px, 0, w, 0, 0, w, h)
                            frames.add(AnimatedGifPureEncoder.GifFrame(w, h, px))
                        } finally {
                            scaled.recycle()
                        }
                    }

                    if (frames.isEmpty()) {
                        throw IOException("No se pudieron extraer fotogramas del video ${item.displayName}.")
                    }

                    val tempGif = uriResolver.createTempWorkingFile("vid2gif_", "gif")
                    val saved = try {
                        FileOutputStream(tempGif).use { fos ->
                            BufferedOutputStream(fos, 64 * 1024).use { bos ->
                                AnimatedGifPureEncoder.encodeAnimatedGif(frames, fps, true, bos)
                            }
                        }
                        val baseName = OutputNamingTemplateResolver.resolveBaseName(
                            templateOrCustomName = options.customOutputBaseName,
                            originalBaseName = item.baseNameWithoutExtension,
                            batchIndexOneBased = idx + 1,
                            batchTotalCount = total,
                            targetFormat = SupportedFormat.GIF
                        )
                        storageManager.saveTempFileToDownloads(
                            sourceFile = tempGif,
                            desiredDisplayName = UriFileResolver.buildOutputFileName(baseName, "gif"),
                            mimeType = SupportedFormat.GIF.mimeType
                        )
                    } finally {
                        tempGif.delete()
                    }

                    results.add(
                        ConversionJobResult(
                            sourceDisplayName = item.displayName,
                            sourceFormat = item.format,
                            sourceSizeBytes = item.sizeBytes,
                            outputDisplayName = saved.displayName,
                            outputFormat = SupportedFormat.GIF,
                            outputCategory = FormatCategory.IMAGE,
                            outputUri = saved.uri,
                            outputAbsolutePath = saved.absolutePath,
                            outputSizeBytes = saved.sizeBytes,
                            outputMimeType = saved.mimeType
                        )
                    )
                } else {
                    // Extracción de fotogramas clave a JPG / PNG
                    val numSnapshots = 6
                    for (s in 0 until numSnapshots) {
                        coroutineContext.ensureActive()
                        onProgress(
                            (idx + (s.toFloat() / numSnapshots)) / total,
                            "Capturando fotograma ${s + 1} de $numSnapshots (${item.displayName})…"
                        )
                        val timeUs = (startMs + (spanMs * s) / max(1, numSnapshots - 1)) * 1000L
                        val bmp = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: continue
                        val tempImg = uriResolver.createTempWorkingFile("snap_", targetFormat.extension)
                        try {
                            FileOutputStream(tempImg).use { fos ->
                                val fmt = if (targetFormat == SupportedFormat.PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                                bmp.compress(fmt, options.clampedQuality, fos)
                            }
                            val desiredName = UriFileResolver.buildOutputFileName(
                                baseName = item.baseNameWithoutExtension,
                                extension = targetFormat.extension,
                                suffix = "_frame_${String.format("%02d", s + 1)}"
                            )
                            val saved = storageManager.saveTempFileToDownloads(
                                sourceFile = tempImg,
                                desiredDisplayName = desiredName,
                                mimeType = targetFormat.mimeType
                            )
                            results.add(
                                ConversionJobResult(
                                    sourceDisplayName = "${item.displayName} (Frame ${s + 1})",
                                    sourceFormat = item.format,
                                    sourceSizeBytes = item.sizeBytes / numSnapshots,
                                    outputDisplayName = saved.displayName,
                                    outputFormat = targetFormat,
                                    outputCategory = FormatCategory.IMAGE,
                                    outputUri = saved.uri,
                                    outputAbsolutePath = saved.absolutePath,
                                    outputSizeBytes = saved.sizeBytes,
                                    outputMimeType = saved.mimeType
                                )
                            )
                        } finally {
                            bmp.recycle()
                            tempImg.delete()
                        }
                    }
                }
            } finally {
                try {
                    retriever.release()
                } catch (_: Throwable) {
                }
                tempVideo.delete()
            }
        }
        onProgress(1f, "Extracción visual desde video completada.")
        return results
    }

    private suspend fun convertOrExtractAudioFile(
        item: InputFileItem,
        index: Int,
        total: Int,
        targetFormat: SupportedFormat,
        options: ConversionOptions,
        onItemProgress: (Float, String) -> Unit
    ): ConversionJobResult {
        val tempInput = uriResolver.copyUriToTempFile(
            uri = item.uri,
            prefix = "aud_in_",
            extension = item.format.extension
        )
        val tempExtractedM4a = uriResolver.createTempWorkingFile(
            prefix = "aud_m4a_",
            extension = "m4a"
        )
        val tempFinalOutput = uriResolver.createTempWorkingFile(
            prefix = "aud_out_",
            extension = targetFormat.extension
        )

        try {
            val mediaItem = buildMediaItemWithOptionalTrim(tempInput, options)
            val editedMediaItem = EditedMediaItem.Builder(mediaItem)
                .setRemoveVideo(true)
                .build()

            val transformerWeight = when (targetFormat) {
                SupportedFormat.M4A -> 1.0f
                SupportedFormat.AAC -> 0.75f
                SupportedFormat.WAV -> 0.50f
                SupportedFormat.MP3 -> 0.40f
                else -> 1.0f
            }

            var transformerExtractedOk = false
            try {
                val targetFileForTransformer = if (targetFormat == SupportedFormat.M4A) {
                    tempFinalOutput
                } else {
                    tempExtractedM4a
                }
                runMedia3TransformerWithPolling(
                    editedMediaItem = editedMediaItem,
                    outputFile = targetFileForTransformer,
                    videoMimeType = null,
                    audioMimeType = MimeTypes.AUDIO_AAC,
                    muxerFactory = DefaultMuxer.Factory(),
                    targetVideoBitrateBps = null,
                    statusPrefix = "Extrayendo audio de ${item.displayName}",
                    onProgress = { p, msg ->
                        onItemProgress(p * transformerWeight, msg)
                    }
                )
                transformerExtractedOk = targetFileForTransformer.exists() &&
                    targetFileForTransformer.length() > 0L
            } catch (e: Throwable) {
                if (targetFormat == SupportedFormat.M4A || targetFormat == SupportedFormat.AAC) {
                    throw e
                }
                transformerExtractedOk = false
            }

            when (targetFormat) {
                SupportedFormat.M4A -> {
                    // tempFinalOutput ya contiene el M4A generado por Media3 Transformer
                }

                SupportedFormat.AAC -> {
                    onItemProgress(0.78f, "Generando flujo de audio ADTS AAC para ${item.displayName}…")
                    PcmWavMp3AudioHelper.extractAdtsAacFromMp4(
                        mp4File = tempExtractedM4a,
                        outputAacFile = tempFinalOutput,
                        onProgress = { subP ->
                            onItemProgress(
                                0.75f + (subP * 0.25f),
                                "Escribiendo tramas AAC (${(subP * 100).toInt()}%)…"
                            )
                        }
                    )
                }

                SupportedFormat.WAV -> {
                    val sourceForDecode = if (transformerExtractedOk) tempExtractedM4a else tempInput
                    val tempPcm = uriResolver.createTempWorkingFile(prefix = "pcm_", extension = "pcm")
                    val tempFadedPcm = uriResolver.createTempWorkingFile(prefix = "pcm_fade_", extension = "pcm")
                    try {
                        onItemProgress(0.55f, "Decodificando audio a PCM 16-bit para WAV…")
                        var pcmInfo = PcmWavMp3AudioHelper.decodeMediaFileToPcm16Le(
                            inputFile = sourceForDecode,
                            tempPcmOutputFile = tempPcm,
                            onProgress = { subP ->
                                onItemProgress(
                                    0.50f + (subP * 0.35f),
                                    "Sintetizando WAV PCM (${(subP * 100).toInt()}%)…"
                                )
                            }
                        )
                        if (options.audioFadeInSeconds > 0 || options.audioFadeOutSeconds > 0) {
                            pcmInfo = PcmWavMp3AudioHelper.applyFadeInOutToPcm16Le(
                                pcmInfo = pcmInfo,
                                fadeInSeconds = options.audioFadeInSeconds,
                                fadeOutSeconds = options.audioFadeOutSeconds,
                                outputPcmFile = tempFadedPcm
                            )
                        }
                        onItemProgress(0.92f, "Escribiendo cabecera RIFF WAVE…")
                        PcmWavMp3AudioHelper.writeWavFileFromPcm16Le(pcmInfo, tempFinalOutput)
                    } finally {
                        tempPcm.delete()
                        tempFadedPcm.delete()
                    }
                }

                SupportedFormat.MP3 -> {
                    val sourceForDecode = if (transformerExtractedOk) tempExtractedM4a else tempInput
                    val tempPcm = uriResolver.createTempWorkingFile(prefix = "pcm_mp3_", extension = "pcm")
                    val tempFadedPcm = uriResolver.createTempWorkingFile(prefix = "pcm_mp3_fade_", extension = "pcm")
                    try {
                        onItemProgress(0.45f, "Decodificando muestras de audio para MP3…")
                        var pcmInfo = PcmWavMp3AudioHelper.decodeMediaFileToPcm16Le(
                            inputFile = sourceForDecode,
                            tempPcmOutputFile = tempPcm,
                            onProgress = { subP ->
                                onItemProgress(
                                    0.40f + (subP * 0.25f),
                                    "Decodificando PCM (${(subP * 100).toInt()}%)…"
                                )
                            }
                        )
                        if (options.audioFadeInSeconds > 0 || options.audioFadeOutSeconds > 0) {
                            pcmInfo = PcmWavMp3AudioHelper.applyFadeInOutToPcm16Le(
                                pcmInfo = pcmInfo,
                                fadeInSeconds = options.audioFadeInSeconds,
                                fadeOutSeconds = options.audioFadeOutSeconds,
                                outputPcmFile = tempFadedPcm
                            )
                        }
                        onItemProgress(0.70f, "Codificando audio a MP3 (${options.audioBitrateKbps} kbps)…")
                        PcmWavMp3AudioHelper.encodePcm16LeToMp3(
                            pcmInfo = pcmInfo,
                            outputMp3File = tempFinalOutput,
                            qualityPercent = options.clampedQuality,
                            explicitBitrateKbps = options.audioBitrateKbps,
                            onProgress = { subP ->
                                onItemProgress(
                                    0.70f + (subP * 0.29f),
                                    "Codificando MP3 (${(subP * 100).toInt()}%)…"
                                )
                            }
                        )
                    } finally {
                        tempPcm.delete()
                        tempFadedPcm.delete()
                    }
                }

                else -> throw IllegalArgumentException("Formato de audio inesperado: $targetFormat")
            }

            if (!tempFinalOutput.exists() || tempFinalOutput.length() <= 0L) {
                throw IOException("El archivo de audio generado está vacío (${item.displayName}).")
            }

            val baseName = OutputNamingTemplateResolver.resolveBaseName(
                templateOrCustomName = options.customOutputBaseName,
                originalBaseName = if (options.exportAsRingtone) "${item.baseNameWithoutExtension}_ringtone" else item.baseNameWithoutExtension,
                batchIndexOneBased = index + 1,
                batchTotalCount = total,
                targetFormat = targetFormat
            )
            val desiredFileName = UriFileResolver.buildOutputFileName(
                baseName = baseName,
                extension = targetFormat.extension
            )

            val saved = storageManager.saveTempFileToDownloads(
                sourceFile = tempFinalOutput,
                desiredDisplayName = desiredFileName,
                mimeType = targetFormat.mimeType,
                subFolder = if (options.exportAsRingtone) {
                    "Ringtones"
                } else {
                    storageManager.resolveEffectiveSubFolder(
                        relativeFolderPath = item.relativeFolderPath,
                        categorySubfolder = "Audio",
                        preserveTreeOverride = options.preserveSubfolderStructure
                    )
                }
            )

            return ConversionJobResult(
                sourceDisplayName = item.displayName,
                sourceFormat = item.format,
                sourceSizeBytes = item.sizeBytes,
                outputDisplayName = saved.displayName,
                outputFormat = targetFormat,
                outputCategory = FormatCategory.MEDIA,
                outputUri = saved.uri,
                outputAbsolutePath = saved.absolutePath,
                outputSizeBytes = saved.sizeBytes,
                outputMimeType = saved.mimeType
            )
        } finally {
            tempInput.delete()
            tempExtractedM4a.delete()
            tempFinalOutput.delete()
        }
    }

    private suspend fun runMedia3TransformerWithPolling(
        editedMediaItem: EditedMediaItem,
        outputFile: File,
        videoMimeType: String?,
        audioMimeType: String?,
        muxerFactory: Muxer.Factory,
        targetVideoBitrateBps: Int?,
        statusPrefix: String,
        onProgress: (Float, String) -> Unit
    ) {
        val sequence = EditedMediaItemSequence(listOf(editedMediaItem))
        val composition = Composition.Builder(listOf(sequence)).build()
        runMedia3CompositionWithPolling(
            composition = composition,
            outputFile = outputFile,
            videoMimeType = videoMimeType,
            audioMimeType = audioMimeType,
            muxerFactory = muxerFactory,
            targetVideoBitrateBps = targetVideoBitrateBps,
            statusPrefix = statusPrefix,
            onProgress = onProgress
        )
    }

    private suspend fun runMedia3CompositionWithPolling(
        composition: Composition,
        outputFile: File,
        videoMimeType: String?,
        audioMimeType: String?,
        muxerFactory: Muxer.Factory,
        targetVideoBitrateBps: Int? = null,
        statusPrefix: String,
        onProgress: (Float, String) -> Unit
    ) {
        if (outputFile.exists()) {
            outputFile.delete()
        }
        outputFile.parentFile?.mkdirs()

        val completion = CompletableDeferred<ExportResult>()

        val transformer = withContext(Dispatchers.Main) {
            val builder = Transformer.Builder(context.applicationContext)
                .setMuxerFactory(muxerFactory)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        completion.complete(exportResult)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        completion.completeExceptionally(exportException)
                    }
                })

            if (targetVideoBitrateBps != null && targetVideoBitrateBps > 0) {
                val encoderFactory = DefaultEncoderFactory.Builder(context.applicationContext)
                    .setRequestedVideoEncoderSettings(
                        VideoEncoderSettings.Builder()
                            .setBitrate(targetVideoBitrateBps)
                            .build()
                    )
                    .build()
                builder.setEncoderFactory(encoderFactory)
            }

            if (!videoMimeType.isNullOrBlank()) {
                builder.setVideoMimeType(videoMimeType)
            }
            if (!audioMimeType.isNullOrBlank()) {
                builder.setAudioMimeType(audioMimeType)
            }

            val instance = builder.build()
            instance.start(composition, outputFile.absolutePath)
            instance
        }

        val progressHolder = ProgressHolder()
        try {
            while (!completion.isCompleted) {
                coroutineContext.ensureActive()
                val progressFraction = withContext(Dispatchers.Main) {
                    val state = transformer.getProgress(progressHolder)
                    if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                        (progressHolder.progress / 100f).coerceIn(0f, 1f)
                    } else {
                        -1f
                    }
                }
                if (progressFraction >= 0f) {
                    val pct = (progressFraction * 100).toInt()
                    onProgress(progressFraction, "$statusPrefix ($pct%)")
                }
                delay(150L)
            }
            completion.await()
        } catch (t: Throwable) {
            withContext(Dispatchers.Main) {
                try {
                    transformer.cancel()
                } catch (_: Throwable) {
                }
            }
            throw t
        }
    }

    private class WebmMatroskaMuxerFactory : Muxer.Factory {

        override fun create(path: String): Muxer {
            return try {
                val mediaMuxer = MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM)
                WebmMatroskaMuxer(mediaMuxer)
            } catch (e: IOException) {
                throw Muxer.MuxerException("No se pudo crear MediaMuxer para WEBM/MKV", e)
            }
        }

        override fun getSupportedSampleMimeTypes(trackType: @C.TrackType Int): ImmutableList<String> {
            return when (trackType) {
                C.TRACK_TYPE_VIDEO -> ImmutableList.of(
                    MimeTypes.VIDEO_VP8,
                    MimeTypes.VIDEO_VP9
                )
                C.TRACK_TYPE_AUDIO -> ImmutableList.of(
                    MimeTypes.AUDIO_OPUS,
                    MimeTypes.AUDIO_VORBIS
                )
                else -> ImmutableList.of()
            }
        }
    }

    private class WebmMatroskaMuxer(
        private val mediaMuxer: MediaMuxer
    ) : Muxer {

        private data class WebmTrackToken(val index: Int) : Muxer.TrackToken

        private var isStarted = false
        private var isReleased = false

        override fun addTrack(format: Format): Muxer.TrackToken {
            val sampleMime = format.sampleMimeType
                ?: throw Muxer.MuxerException("Formato de pista sin sampleMimeType", IllegalArgumentException())
            val isVideo = MimeTypes.isVideo(sampleMime)

            val mediaFormat = if (isVideo) {
                MediaFormat.createVideoFormat(sampleMime, format.width, format.height).apply {
                    if (format.rotationDegrees != 0) {
                        try {
                            mediaMuxer.setOrientationHint(format.rotationDegrees)
                        } catch (_: Throwable) {
                        }
                    }
                }
            } else {
                MediaFormat.createAudioFormat(sampleMime, format.sampleRate, format.channelCount)
            }

            format.initializationData.forEachIndexed { idx, bytes ->
                mediaFormat.setByteBuffer("csd-$idx", ByteBuffer.wrap(bytes))
            }

            val trackIndex = try {
                mediaMuxer.addTrack(mediaFormat)
            } catch (e: RuntimeException) {
                throw Muxer.MuxerException("Error al añadir pista $sampleMime al muxer WebM/MKV", e)
            }
            return WebmTrackToken(trackIndex)
        }

        override fun writeSampleData(
            trackToken: Muxer.TrackToken,
            byteBuffer: ByteBuffer,
            bufferInfo: MediaCodec.BufferInfo
        ) {
            val token = trackToken as? WebmTrackToken
                ?: throw Muxer.MuxerException("TrackToken inválido", IllegalArgumentException())

            if (!isStarted) {
                try {
                    mediaMuxer.start()
                    isStarted = true
                } catch (e: RuntimeException) {
                    throw Muxer.MuxerException("No se pudo iniciar el MediaMuxer WebM/MKV", e)
                }
            }

            try {
                mediaMuxer.writeSampleData(token.index, byteBuffer, bufferInfo)
            } catch (e: RuntimeException) {
                throw Muxer.MuxerException("Error escribiendo muestra en MediaMuxer WebM/MKV", e)
            }
        }

        override fun addMetadataEntry(metadataEntry: Metadata.Entry) {}

        override fun close() {
            if (isReleased) return
            try {
                if (isStarted) {
                    mediaMuxer.stop()
                    isStarted = false
                }
            } catch (e: RuntimeException) {
                throw Muxer.MuxerException("Error al detener MediaMuxer WebM/MKV", e)
            } finally {
                try {
                    mediaMuxer.release()
                } catch (_: Throwable) {
                }
                isReleased = true
            }
        }
    }
}
