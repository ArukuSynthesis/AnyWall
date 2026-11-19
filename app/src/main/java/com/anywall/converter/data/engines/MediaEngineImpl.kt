package com.anywall.converter.data.engines

import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.muxer.Muxer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultMuxer
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.anywall.converter.core.ScopedStorageManager
import com.anywall.converter.core.UriFileResolver
import com.anywall.converter.domain.engine.FileConverterEngine
import com.anywall.converter.domain.model.ConversionJobResult
import com.anywall.converter.domain.model.ConversionOptions
import com.anywall.converter.domain.model.FormatCategory
import com.anywall.converter.domain.model.InputFileItem
import com.anywall.converter.domain.model.SupportedFormat
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.coroutines.coroutineContext

/**
 * Motor 3: `MediaEngineImpl` (Video y Audio Offline)
 * - Conversión de video (`MP4`, `MKV`, `MOV`, `WEBM`) con selector de resolución
 *   (`Original`, `1080p`, `720p`, `480p`) aplicando `Presentation.createForHeight(...)` en `Media3 Transformer`.
 * - Extracción y conversión de audio (`MP3`, `AAC`, `M4A`, `WAV`) configurando
 *   `EditedMediaItem.Builder(mediaItem).setRemoveVideo(true).build()`.
 * - Polling en vivo del progreso con `Transformer.getProgress(progressHolder)` para reportar
 *   avance continuo (`0.0` a `1.0`) al `BatchConversionWorker`.
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

    private val audioFormats = setOf(
        SupportedFormat.MP3,
        SupportedFormat.AAC,
        SupportedFormat.M4A,
        SupportedFormat.WAV
    )

    private val allMediaFormats = videoFormats + audioFormats

    override fun canHandle(format: SupportedFormat): Boolean {
        return format in allMediaFormats
    }

    override fun getSupportedTargetFormats(sourceFormats: List<SupportedFormat>): List<SupportedFormat> {
        if (sourceFormats.isEmpty()) return emptyList()
        val distinct = sourceFormats.distinct()

        if (!distinct.all { it in allMediaFormats }) {
            return emptyList()
        }

        val allSourcesAreVideo = distinct.all { it in videoFormats }
        return if (allSourcesAreVideo) {
            // Si el origen es video, permite convertir a cualquier formato de video o extraer audio
            listOf(
                SupportedFormat.MP4,
                SupportedFormat.MKV,
                SupportedFormat.MOV,
                SupportedFormat.WEBM,
                SupportedFormat.MP3,
                SupportedFormat.AAC,
                SupportedFormat.M4A,
                SupportedFormat.WAV
            )
        } else {
            // Si hay archivos de solo audio, permite convertir/extraer a todos los formatos de audio
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
            } else if (targetFormat in audioFormats) {
                convertOrExtractAudioFile(item, index, total, targetFormat, options, reportItemProgress)
            } else {
                throw IllegalArgumentException("Formato multimedia no soportado: $targetFormat")
            }

            results.add(result)
        }

        onProgress(1f, "Procesamiento multimedia finalizado (${results.size}).")
        results
    }

    /**
     * Conversión de video (`MP4`, `MKV`, `MOV`, `WEBM`) con `Media3 Transformer`
     * aplicando `Presentation.createForHeight(...)` cuando se selecciona `1080p`, `720p` o `480p`.
     */
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
            val mediaItem = MediaItem.fromUri(android.net.Uri.fromFile(tempInput))
            val videoEffects = mutableListOf<Effect>()
            val targetHeight = options.videoResolution.targetHeightPx
            if (targetHeight != null && targetHeight > 0) {
                videoEffects.add(Presentation.createForHeight(targetHeight))
            }

            val editedMediaItem = EditedMediaItem.Builder(mediaItem)
                .setEffects(Effects(emptyList<AudioProcessor>(), videoEffects))
                .build()

            val useWebmContainer = (targetFormat == SupportedFormat.WEBM || targetFormat == SupportedFormat.MKV)

            val transformSuccess = if (useWebmContainer) {
                // Intentar primero con codificación VP8/VP9 + Opus sobre MediaMuxer MUXER_OUTPUT_WEBM
                try {
                    runMedia3TransformerWithPolling(
                        editedMediaItem = editedMediaItem,
                        outputFile = tempOutput,
                        videoMimeType = MimeTypes.VIDEO_VP8,
                        audioMimeType = MimeTypes.AUDIO_OPUS,
                        muxerFactory = WebmMatroskaMuxerFactory(),
                        statusPrefix = "Convirtiendo video ${item.displayName} (${options.videoResolution.labelEs})",
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
                    audioMimeType = MimeTypes.AUDIO_AAC,
                    muxerFactory = DefaultMuxer.Factory(),
                    statusPrefix = "Convirtiendo video ${item.displayName} (${options.videoResolution.labelEs})",
                    onProgress = onItemProgress
                )
            }

            val baseName = if (options.customOutputBaseName.isNotBlank()) {
                if (total == 1) options.customOutputBaseName
                else "${options.customOutputBaseName}_${index + 1}"
            } else {
                val resSuffix = if (options.videoResolution.targetHeightPx != null) {
                    "_${options.videoResolution.labelEs}"
                } else {
                    ""
                }
                "${item.baseNameWithoutExtension}$resSuffix"
            }

            val desiredFileName = UriFileResolver.buildOutputFileName(
                baseName = baseName,
                extension = targetFormat.extension
            )

            val saved = storageManager.saveTempFileToDownloads(
                sourceFile = tempOutput,
                desiredDisplayName = desiredFileName,
                mimeType = targetFormat.mimeType
            )

            return ConversionJobResult(
                sourceDisplayName = item.displayName,
                sourceFormat = item.format,
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

    /**
     * Extracción y conversión de audio (`MP3`, `AAC`, `M4A`, `WAV`) configurando
     * `EditedMediaItem.Builder(mediaItem).setRemoveVideo(true).build()` en `Media3 Transformer`
     * con polling continuo de `Transformer.getProgress(progressHolder)`.
     */
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
            val mediaItem = MediaItem.fromUri(android.net.Uri.fromFile(tempInput))
            val editedMediaItem = EditedMediaItem.Builder(mediaItem)
                .setRemoveVideo(true)
                .build()

            // Fase 1: Ejecutar Media3 Transformer con setRemoveVideo(true) y polling de getProgress(progressHolder)
            val transformerWeight = when (targetFormat) {
                SupportedFormat.M4A -> 1.0f
                SupportedFormat.AAC -> 0.75f
                SupportedFormat.WAV -> 0.55f
                SupportedFormat.MP3 -> 0.45f
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
                // Si el archivo de entrada era un WAV/MP3 especial y el muxer intermedio falló,
                // la Fase 2 decodificará directamente desde tempInput para WAV o MP3.
                transformerExtractedOk = false
            }

            // Fase 2: Finalizar codificación según el formato de audio destino
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
                    try {
                        onItemProgress(0.58f, "Decodificando audio a PCM 16-bit para WAV…")
                        val pcmInfo = PcmWavMp3AudioHelper.decodeMediaFileToPcm16Le(
                            inputFile = sourceForDecode,
                            tempPcmOutputFile = tempPcm,
                            onProgress = { subP ->
                                onItemProgress(
                                    0.55f + (subP * 0.35f),
                                    "Sintetizando WAV PCM (${(subP * 100).toInt()}%)…"
                                )
                            }
                        )
                        onItemProgress(0.92f, "Escribiendo cabecera RIFF WAVE…")
                        PcmWavMp3AudioHelper.writeWavFileFromPcm16Le(pcmInfo, tempFinalOutput)
                    } finally {
                        tempPcm.delete()
                    }
                }

                SupportedFormat.MP3 -> {
                    val sourceForDecode = if (transformerExtractedOk) tempExtractedM4a else tempInput
                    val tempPcm = uriResolver.createTempWorkingFile(prefix = "pcm_mp3_", extension = "pcm")
                    try {
                        onItemProgress(0.46f, "Decodificando muestras de audio para MP3…")
                        val pcmInfo = PcmWavMp3AudioHelper.decodeMediaFileToPcm16Le(
                            inputFile = sourceForDecode,
                            tempPcmOutputFile = tempPcm,
                            onProgress = { subP ->
                                onItemProgress(
                                    0.45f + (subP * 0.25f),
                                    "Decodificando PCM (${(subP * 100).toInt()}%)…"
                                )
                            }
                        )
                        onItemProgress(0.72f, "Codificando audio a MP3 con motor LAME…")
                        PcmWavMp3AudioHelper.encodePcm16LeToMp3(
                            pcmInfo = pcmInfo,
                            outputMp3File = tempFinalOutput,
                            qualityPercent = options.clampedQuality,
                            onProgress = { subP ->
                                onItemProgress(
                                    0.70f + (subP * 0.29f),
                                    "Codificando MP3 (${(subP * 100).toInt()}%)…"
                                )
                            }
                        )
                    } finally {
                        tempPcm.delete()
                    }
                }

                else -> throw IllegalArgumentException("Formato de audio inesperado: $targetFormat")
            }

            if (!tempFinalOutput.exists() || tempFinalOutput.length() <= 0L) {
                throw IOException("El archivo de audio generado está vacío (${item.displayName}).")
            }

            val baseName = if (options.customOutputBaseName.isNotBlank()) {
                if (total == 1) options.customOutputBaseName
                else "${options.customOutputBaseName}_${index + 1}"
            } else {
                item.baseNameWithoutExtension
            }
            val desiredFileName = UriFileResolver.buildOutputFileName(
                baseName = baseName,
                extension = targetFormat.extension
            )

            val saved = storageManager.saveTempFileToDownloads(
                sourceFile = tempFinalOutput,
                desiredDisplayName = desiredFileName,
                mimeType = targetFormat.mimeType
            )

            return ConversionJobResult(
                sourceDisplayName = item.displayName,
                sourceFormat = item.format,
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

    /**
     * Ejecuta `Media3 Transformer` en el hilo principal (`Dispatchers.Main`) y realiza polling
     * del progreso cada 150 ms usando `Transformer.getProgress(progressHolder)`.
     */
    private suspend fun runMedia3TransformerWithPolling(
        editedMediaItem: EditedMediaItem,
        outputFile: File,
        videoMimeType: String?,
        audioMimeType: String?,
        muxerFactory: Muxer.Factory,
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

            if (!videoMimeType.isNullOrBlank()) {
                builder.setVideoMimeType(videoMimeType)
            }
            if (!audioMimeType.isNullOrBlank()) {
                builder.setAudioMimeType(audioMimeType)
            }

            val instance = builder.build()
            instance.start(editedMediaItem, outputFile.absolutePath)
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

    /**
     * `Muxer.Factory` nativo basado en `android.media.MediaMuxer(path, MUXER_OUTPUT_WEBM)`
     * para contenedores `WEBM` y `MKV` (Matroska) en Media3 Transformer.
     */
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

        override fun addMetadataEntry(metadataEntry: Metadata.Entry) {
            // El contenedor WebM de MediaMuxer no requiere metadatos de ubicación MP4
        }

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
