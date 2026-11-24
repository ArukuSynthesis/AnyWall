package com.anywall.converter.data.engines

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.anywall.converter.domain.model.AudioMetadataSpec
import de.sciss.jump3r.mp3.BitStream
import de.sciss.jump3r.mp3.GainAnalysis
import de.sciss.jump3r.mp3.GetAudio
import de.sciss.jump3r.mp3.ID3Tag
import de.sciss.jump3r.mp3.Lame
import de.sciss.jump3r.mp3.LameGlobalFlags
import de.sciss.jump3r.mp3.MPEGMode
import de.sciss.jump3r.mp3.Parse
import de.sciss.jump3r.mp3.Presets
import de.sciss.jump3r.mp3.Quantize
import de.sciss.jump3r.mp3.QuantizePVT
import de.sciss.jump3r.mp3.Reservoir
import de.sciss.jump3r.mp3.Takehiro
import de.sciss.jump3r.mp3.VBRTag
import de.sciss.jump3r.mp3.VbrMode
import de.sciss.jump3r.mp3.Version
import de.sciss.jump3r.mpg.Common
import de.sciss.jump3r.mpg.Interface
import de.sciss.jump3r.mpg.MPGLib
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Procesador de audio de bajo nivel (v0.4) para:
 * 1. Extracción de flujo ADTS `.aac` desde contenedor MP4/M4A generado por Media3 Transformer.
 * 2. Decodificación de audio a PCM 16-bit Little-Endian mediante `MediaExtractor` + `MediaCodec`.
 * 3. Normalización automática de volumen (`Auto-Gain` a `-1.0 dBFS`) sobre muestras PCM 16-bit LE.
 * 4. Extracción de picos de forma de onda (`extractWaveformPeaksFromPcmFile`) para `MediaTimelineTrimmerDialog`.
 * 5. Escritura de archivos `.wav` (RIFF WAVE PCM 16-bit).
 * 6. Codificación a `.mp3` en Java puro usando LAME (`de.sciss.jump3r.mp3.Lame`).
 * 7. Escritura y lectura binaria de etiquetas **ID3v2.3** (`TIT2`, `TPE1`, `TALB`, `TYER`, `TCON` + carátula `APIC`)
 *    para `MP3` y átomos de metadatos para `M4A`.
 */
object PcmWavMp3AudioHelper {

    data class DecodedPcmInfo(
        val pcmFile: File,
        val sampleRate: Int,
        val channelCount: Int,
        val totalPcmBytes: Long
    )

    /**
     * Extrae la pista AAC de un contenedor MP4/M4A y añade cabeceras ADTS de 7 bytes a cada trama
     * para producir un archivo `.aac` estándar reproducible directamente.
     */
    @Throws(IOException::class)
    fun extractAdtsAacFromMp4(
        mp4File: File,
        outputAacFile: File,
        onProgress: (Float) -> Unit
    ) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(mp4File.absolutePath)
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex < 0 || audioFormat == null) {
                throw IOException("El archivo intermedio no contiene una pista de audio AAC válida.")
            }

            extractor.selectTrack(audioTrackIndex)
            val sampleRate = if (audioFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else {
                44100
            }
            val channelCount = if (audioFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else {
                2
            }
            val durationUs = if (audioFormat.containsKey(MediaFormat.KEY_DURATION)) {
                audioFormat.getLong(MediaFormat.KEY_DURATION).coerceAtLeast(1L)
            } else {
                1L
            }

            val freqIdx = sampleRateToAdtsFrequencyIndex(sampleRate)
            val chanCfg = channelCount.coerceIn(1, 7)
            val sampleBuffer = ByteBuffer.allocate(256 * 1024)
            val adtsHeader = ByteArray(7)

            FileOutputStream(outputAacFile).use { fos ->
                BufferedOutputStream(fos, 64 * 1024).use { bos ->
                    while (true) {
                        sampleBuffer.clear()
                        val sampleSize = extractor.readSampleData(sampleBuffer, 0)
                        if (sampleSize < 0) break

                        val frameLength = sampleSize + 7
                        val profile = 1
                        adtsHeader[0] = 0xFF.toByte()
                        adtsHeader[1] = 0xF9.toByte()
                        adtsHeader[2] = (((profile and 0x03) shl 6) or
                            ((freqIdx and 0x0F) shl 2) or
                            ((chanCfg shr 2) and 0x01)).toByte()
                        adtsHeader[3] = (((chanCfg and 0x03) shl 6) or
                            ((frameLength shr 11) and 0x03)).toByte()
                        adtsHeader[4] = ((frameLength shr 3) and 0xFF).toByte()
                        adtsHeader[5] = (((frameLength and 0x07) shl 5) or 0x1F).toByte()
                        adtsHeader[6] = 0xFC.toByte()

                        bos.write(adtsHeader)
                        val chunk = ByteArray(sampleSize)
                        sampleBuffer.get(chunk)
                        bos.write(chunk)

                        val sampleTimeUs = extractor.sampleTime
                        if (durationUs > 1L && sampleTimeUs > 0L) {
                            onProgress((sampleTimeUs.toFloat() / durationUs.toFloat()).coerceIn(0f, 1f))
                        }

                        if (!extractor.advance()) break
                    }
                    bos.flush()
                }
            }
        } finally {
            extractor.release()
        }
    }

    /**
     * Decodifica cualquier archivo de audio/video local a un archivo temporal de muestras PCM 16-bit LE.
     */
    @Throws(IOException::class)
    fun decodeMediaFileToPcm16Le(
        inputFile: File,
        tempPcmOutputFile: File,
        onProgress: (Float) -> Unit
    ): DecodedPcmInfo {
        parseDirectWavPcm16IfApplicable(inputFile, tempPcmOutputFile, onProgress)?.let {
            return it
        }

        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(inputFile.absolutePath)
            var audioTrackIndex = -1
            var inputFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    inputFormat = format
                    break
                }
            }

            if (audioTrackIndex < 0 || inputFormat == null) {
                throw IOException("El archivo no contiene ninguna pista de audio decodificable.")
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: throw IOException("Tipo MIME de audio desconocido.")
            var sampleRate = if (inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else {
                44100
            }
            var channelCount = if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else {
                2
            }
            val durationUs = if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) {
                inputFormat.getLong(MediaFormat.KEY_DURATION).coerceAtLeast(1L)
            } else {
                1L
            }

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var sawInputEOS = false
            var sawOutputEOS = false
            var totalWrittenBytes = 0L

            FileOutputStream(tempPcmOutputFile).use { fos ->
                BufferedOutputStream(fos, 64 * 1024).use { bos ->
                    while (!sawOutputEOS) {
                        if (!sawInputEOS) {
                            val inputBufferIndex = codec.dequeueInputBuffer(10_000L)
                            if (inputBufferIndex >= 0) {
                                val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                                if (inputBuffer != null) {
                                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                                    if (sampleSize < 0) {
                                        codec.queueInputBuffer(
                                            inputBufferIndex,
                                            0,
                                            0,
                                            0L,
                                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                        )
                                        sawInputEOS = true
                                    } else {
                                        val presentationTimeUs = extractor.sampleTime.coerceAtLeast(0L)
                                        codec.queueInputBuffer(
                                            inputBufferIndex,
                                            0,
                                            sampleSize,
                                            presentationTimeUs,
                                            0
                                        )
                                        extractor.advance()
                                    }
                                }
                            }
                        }

                        val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
                        when {
                            outputBufferIndex >= 0 -> {
                                val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                                if (outputBuffer != null && bufferInfo.size > 0) {
                                    outputBuffer.position(bufferInfo.offset)
                                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                    val chunk = ByteArray(bufferInfo.size)
                                    outputBuffer.get(chunk)
                                    bos.write(chunk)
                                    totalWrittenBytes += chunk.size
                                }
                                codec.releaseOutputBuffer(outputBufferIndex, false)

                                if (durationUs > 1L && bufferInfo.presentationTimeUs > 0L) {
                                    val p = (bufferInfo.presentationTimeUs.toFloat() / durationUs.toFloat())
                                        .coerceIn(0f, 1f)
                                    onProgress(p)
                                }

                                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                    sawOutputEOS = true
                                }
                            }
                            outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                val newFormat = codec.outputFormat
                                if (newFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                                    sampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                                }
                                if (newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                                    channelCount = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                                }
                            }
                        }
                    }
                    bos.flush()
                }
            }

            return DecodedPcmInfo(
                pcmFile = tempPcmOutputFile,
                sampleRate = sampleRate.coerceAtLeast(8000),
                channelCount = channelCount.coerceIn(1, 2),
                totalPcmBytes = totalWrittenBytes
            )
        } finally {
            try {
                codec?.stop()
            } catch (_: Throwable) {
            }
            try {
                codec?.release()
            } catch (_: Throwable) {
            }
            try {
                extractor.release()
            } catch (_: Throwable) {
            }
        }
    }

    private fun parseDirectWavPcm16IfApplicable(
        inputFile: File,
        tempPcmOutputFile: File,
        onProgress: (Float) -> Unit
    ): DecodedPcmInfo? {
        if (inputFile.length() < 44L) return null
        return try {
            RandomAccessFile(inputFile, "r").use { raf ->
                val header = ByteArray(44)
                raf.readFully(header)
                val riff = String(header, 0, 4, Charsets.US_ASCII)
                val wave = String(header, 8, 4, Charsets.US_ASCII)
                if (riff != "RIFF" || wave != "WAVE") return null

                val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                val audioFormat = bb.getShort(20).toInt() and 0xFFFF
                val channels = bb.getShort(22).toInt() and 0xFFFF
                val sampleRate = bb.getInt(24)
                val bitsPerSample = bb.getShort(34).toInt() and 0xFFFF

                if (audioFormat != 1 || bitsPerSample != 16 || channels !in 1..2) {
                    return null
                }

                var offset = 12L
                var dataOffset = 44L
                var dataSize = inputFile.length() - 44L
                val chunkHead = ByteArray(8)
                while (offset + 8L <= inputFile.length()) {
                    raf.seek(offset)
                    raf.readFully(chunkHead)
                    val chunkId = String(chunkHead, 0, 4, Charsets.US_ASCII)
                    val chunkSize = ByteBuffer.wrap(chunkHead, 4, 4)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .int
                        .toLong() and 0xFFFFFFFFL
                    if (chunkId == "data") {
                        dataOffset = offset + 8L
                        dataSize = chunkSize.coerceAtMost(inputFile.length() - dataOffset)
                        break
                    }
                    offset += 8L + chunkSize + (chunkSize % 2L)
                }

                raf.seek(dataOffset)
                var remaining = dataSize
                val buf = ByteArray(64 * 1024)
                var written = 0L
                FileOutputStream(tempPcmOutputFile).use { fos ->
                    while (remaining > 0L) {
                        val toRead = minOf(buf.size.toLong(), remaining).toInt()
                        val read = raf.read(buf, 0, toRead)
                        if (read <= 0) break
                        fos.write(buf, 0, read)
                        remaining -= read
                        written += read
                        if (dataSize > 0L) {
                            onProgress((written.toFloat() / dataSize.toFloat()).coerceIn(0f, 1f))
                        }
                    }
                    fos.flush()
                }

                DecodedPcmInfo(
                    pcmFile = tempPcmOutputFile,
                    sampleRate = sampleRate.coerceAtLeast(8000),
                    channelCount = channels,
                    totalPcmBytes = written
                )
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Escribe un archivo `.wav` estándar (cabecera RIFF WAVE de 44 bytes + PCM 16-bit Little-Endian).
     */
    @Throws(IOException::class)
    fun writeWavFileFromPcm16Le(
        pcmInfo: DecodedPcmInfo,
        outputWavFile: File
    ) {
        val dataSize = pcmInfo.totalPcmBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val totalFileSizeMinus8 = 36 + dataSize
        val channels = pcmInfo.channelCount.coerceIn(1, 2)
        val sampleRate = pcmInfo.sampleRate
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * (bitsPerSample / 8)
        val blockAlign = channels * (bitsPerSample / 8)

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(totalFileSizeMinus8)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1)
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(blockAlign.toShort())
        header.putShort(bitsPerSample.toShort())
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(dataSize)

        FileOutputStream(outputWavFile).use { fos ->
            BufferedOutputStream(fos, 64 * 1024).use { bos ->
                bos.write(header.array())
                FileInputStream(pcmInfo.pcmFile).use { fis ->
                    fis.copyTo(bos, bufferSize = 64 * 1024)
                }
                bos.flush()
            }
        }
    }

    /**
     * Normalización automática de volumen (`Auto-Gain` a `-1.0 dBFS` por defecto - v0.4).
     * Escanea el pico máximo absoluto en streaming y escala todas las muestras PCM 16-bit LE
     * para alcanzar exactamente `targetDbFs` (`-1.0 dBFS` ≈ amplitud `29204` de `32767`).
     */
    @Throws(IOException::class)
    fun normalizePcm16LeToTargetDbFs(
        pcmInfo: DecodedPcmInfo,
        targetDbFs: Double = -1.0,
        outputPcmFile: File
    ): DecodedPcmInfo {
        val maxPeak = computePeakAmplitudePcm16Le(pcmInfo.pcmFile)
        if (maxPeak <= 0) {
            pcmInfo.pcmFile.copyTo(outputPcmFile, overwrite = true)
            return pcmInfo.copy(pcmFile = outputPcmFile)
        }

        val targetPeak = (32767.0 * 10.0.pow(targetDbFs.coerceIn(-24.0, 0.0) / 20.0))
            .roundToInt()
            .coerceIn(1, 32767)
        val gain = (targetPeak.toDouble() / maxPeak.toDouble()).coerceIn(0.05, 16.0)

        FileInputStream(pcmInfo.pcmFile).use { fis ->
            FileOutputStream(outputPcmFile).use { fos ->
                BufferedOutputStream(fos, 64 * 1024).use { bos ->
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val read = readFullFrames(fis, buf, 2)
                        if (read <= 0) break
                        var i = 0
                        while (i + 1 < read) {
                            val lo = buf[i].toInt() and 0xFF
                            val hi = buf[i + 1].toInt() // sign-extended
                            val sample = (hi shl 8) or lo
                            val scaled = (sample * gain).roundToInt()
                                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                            buf[i] = (scaled and 0xFF).toByte()
                            buf[i + 1] = ((scaled shr 8) and 0xFF).toByte()
                            i += 2
                        }
                        bos.write(buf, 0, read)
                    }
                    bos.flush()
                }
            }
        }
        return pcmInfo.copy(pcmFile = outputPcmFile)
    }

    /**
     * Calcula el pico máximo absoluto (`0..32767`) de un archivo de muestras PCM 16-bit LE.
     */
    fun computePeakAmplitudePcm16Le(pcmFile: File): Int {
        var maxPeak = 0
        FileInputStream(pcmFile).use { fis ->
            val buf = ByteArray(16 * 1024)
            while (true) {
                val read = readFullFrames(fis, buf, 2)
                if (read <= 0) break
                var i = 0
                while (i + 1 < read) {
                    val lo = buf[i].toInt() and 0xFF
                    val hi = buf[i + 1].toInt()
                    val sample = (hi shl 8) or lo
                    val absVal = if (sample == Short.MIN_VALUE.toInt()) 32767 else abs(sample)
                    if (absVal > maxPeak) {
                        maxPeak = absVal
                    }
                    i += 2
                }
            }
        }
        return maxPeak
    }

    /**
     * Extrae `bucketCount` picos normalizados (`0.06f..1.0f`) de un archivo PCM 16-bit LE
     * para dibujar la forma de onda interactiva en `MediaTimelineTrimmerDialog` (v0.4).
     */
    fun extractWaveformPeaksFromPcmFile(pcmFile: File, bucketCount: Int = 72): List<Float> {
        val count = bucketCount.coerceIn(16, 240)
        val totalLen = pcmFile.length()
        if (totalLen < 4L) return List(count) { 0.25f }

        val peaks = IntArray(count)
        var globalMax = 1
        val bytesPerBucket = (totalLen / count).coerceAtLeast(2L)

        RandomAccessFile(pcmFile, "r").use { raf ->
            val sampleBuf = ByteArray(4096)
            for (b in 0 until count) {
                val startOffset = (b * bytesPerBucket) and -2L // alinear a 2 bytes
                if (startOffset >= totalLen) break
                raf.seek(startOffset)
                val toRead = minOf(sampleBuf.size.toLong(), bytesPerBucket, totalLen - startOffset).toInt() and -2
                if (toRead <= 0) continue
                val read = raf.read(sampleBuf, 0, toRead)
                var bucketPeak = 0
                var i = 0
                while (i + 1 < read) {
                    val lo = sampleBuf[i].toInt() and 0xFF
                    val hi = sampleBuf[i + 1].toInt()
                    val sample = (hi shl 8) or lo
                    val absVal = if (sample == Short.MIN_VALUE.toInt()) 32767 else abs(sample)
                    if (absVal > bucketPeak) bucketPeak = absVal
                    i += 2
                }
                peaks[b] = bucketPeak
                if (bucketPeak > globalMax) globalMax = bucketPeak
            }
        }

        return peaks.map { p ->
            (p.toFloat() / globalMax.toFloat()).coerceIn(0.06f, 1.0f)
        }
    }

    /**
     * Aplica una envolvente suave de entrada (Fade In) y salida (Fade Out) sobre muestras PCM 16-bit LE.
     */
    @Throws(IOException::class)
    fun applyFadeInOutToPcm16Le(
        pcmInfo: DecodedPcmInfo,
        fadeInSeconds: Int,
        fadeOutSeconds: Int,
        outputPcmFile: File
    ): DecodedPcmInfo {
        if (fadeInSeconds <= 0 && fadeOutSeconds <= 0) {
            return pcmInfo
        }
        val channels = pcmInfo.channelCount.coerceIn(1, 2)
        val bytesPerFrame = 2 * channels
        val totalFrames = (pcmInfo.totalPcmBytes / bytesPerFrame).coerceAtLeast(1L)
        val fadeInFrames = (fadeInSeconds.coerceAtLeast(0).toLong() * pcmInfo.sampleRate).coerceAtMost(totalFrames / 2)
        val fadeOutFrames = (fadeOutSeconds.coerceAtLeast(0).toLong() * pcmInfo.sampleRate).coerceAtMost(totalFrames / 2)

        FileInputStream(pcmInfo.pcmFile).use { fis ->
            FileOutputStream(outputPcmFile).use { fos ->
                BufferedOutputStream(fos, 64 * 1024).use { bos ->
                    val chunkFrames = 4096
                    val buf = ByteArray(chunkFrames * bytesPerFrame)
                    var currentFrame = 0L

                    while (true) {
                        val read = readFullFrames(fis, buf, bytesPerFrame)
                        if (read <= 0) break
                        val framesInChunk = read / bytesPerFrame
                        var byteOffset = 0
                        for (f in 0 until framesInChunk) {
                            val globalFrame = currentFrame + f
                            var gain = 1.0f
                            if (fadeInFrames > 0L && globalFrame < fadeInFrames) {
                                gain = minOf(gain, globalFrame.toFloat() / fadeInFrames.toFloat())
                            }
                            val framesFromEnd = (totalFrames - 1L - globalFrame).coerceAtLeast(0L)
                            if (fadeOutFrames > 0L && framesFromEnd < fadeOutFrames) {
                                gain = minOf(gain, framesFromEnd.toFloat() / fadeOutFrames.toFloat())
                            }
                            if (gain < 0.999f) {
                                for (c in 0 until channels) {
                                    val idx = byteOffset + c * 2
                                    val lo = buf[idx].toInt() and 0xFF
                                    val hi = buf[idx + 1].toInt()
                                    val sample = (hi shl 8) or lo
                                    val scaled = (sample * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                                    buf[idx] = (scaled and 0xFF).toByte()
                                    buf[idx + 1] = ((scaled shr 8) and 0xFF).toByte()
                                }
                            }
                            byteOffset += bytesPerFrame
                        }
                        bos.write(buf, 0, read)
                        currentFrame += framesInChunk
                    }
                    bos.flush()
                }
            }
        }
        return pcmInfo.copy(pcmFile = outputPcmFile)
    }

    /**
     * Codifica muestras PCM 16-bit Little-Endian a un archivo `.mp3` real usando LAME 3.98.4 (`de.sciss.jump3r.mp3.Lame`).
     */
    @Throws(IOException::class)
    fun encodePcm16LeToMp3(
        pcmInfo: DecodedPcmInfo,
        outputMp3File: File,
        qualityPercent: Int,
        explicitBitrateKbps: Int? = null,
        onProgress: (Float) -> Unit
    ) {
        val lame = Lame()
        val gaud = GetAudio()
        val ga = GainAnalysis()
        val bs = BitStream()
        val presets = Presets()
        val qupvt = QuantizePVT()
        val qu = Quantize()
        val vbr = VBRTag()
        val ver = Version()
        val id3 = ID3Tag()
        val rv = Reservoir()
        val tak = Takehiro()
        val parse = Parse()
        val mpg = MPGLib()
        val intf = Interface()
        val common = Common()

        lame.setModules(ga, bs, presets, qupvt, qu, vbr, ver, id3, mpg)
        bs.setModules(ga, mpg, ver, vbr)
        id3.setModules(bs, ver)
        presets.setModules(lame)
        qu.setModules(bs, rv, qupvt, tak)
        qupvt.setModules(tak, rv, lame.enc.psy)
        rv.setModules(bs)
        tak.setModules(qupvt)
        vbr.setModules(lame, bs, ver)
        gaud.setModules(parse, mpg)
        parse.setModules(ver, id3, presets)
        mpg.setModules(intf, common)
        intf.setModules(vbr, common)

        val channels = pcmInfo.channelCount.coerceIn(1, 2)
        val sampleRate = pcmInfo.sampleRate.coerceIn(8000, 48000)
        val bitrateKbps = explicitBitrateKbps?.takeIf { it in 64..320 } ?: when {
            qualityPercent >= 90 -> 256
            qualityPercent >= 75 -> 192
            qualityPercent >= 50 -> 160
            qualityPercent >= 30 -> 128
            else -> 96
        }

        val gfp: LameGlobalFlags = lame.lame_init()
        gfp.num_channels = channels
        gfp.in_samplerate = sampleRate
        gfp.out_samplerate = sampleRate
        gfp.mode = if (channels == 1) MPEGMode.MONO else MPEGMode.JOINT_STEREO
        gfp.VBR = VbrMode.vbr_off
        gfp.brate = bitrateKbps
        gfp.quality = 5
        id3.id3tag_init(gfp)
        gfp.write_id3tag_automatic = false
        gfp.findReplayGain = false

        val initRc = lame.lame_init_params(gfp)
        if (initRc < 0) {
            throw IOException("Error al inicializar los parámetros del codificador LAME MP3 (código $initRc)")
        }

        val framesPerChunk = 1152 * 4
        val bytesPerSample = 2
        val bytesPerFrame = bytesPerSample * channels
        val pcmChunkBuffer = ByteArray(framesPerChunk * bytesPerFrame)
        val leftChannel = IntArray(framesPerChunk)
        val rightChannel = IntArray(framesPerChunk)
        val mp3Buffer = ByteArray((1.25 * framesPerChunk + 7200).toInt())

        val totalBytes = pcmInfo.totalPcmBytes.coerceAtLeast(1L)
        var processedBytes = 0L

        try {
            FileInputStream(pcmInfo.pcmFile).use { fis ->
                FileOutputStream(outputMp3File).use { fos ->
                    BufferedOutputStream(fos, 64 * 1024).use { bos ->
                        while (true) {
                            val bytesRead = readFullFrames(fis, pcmChunkBuffer, bytesPerFrame)
                            if (bytesRead <= 0) break

                            val sampleFrames = bytesRead / bytesPerFrame
                            var byteIdx = 0
                            if (channels == 2) {
                                for (i in 0 until sampleFrames) {
                                    val loL = pcmChunkBuffer[byteIdx++].toInt() and 0xFF
                                    val hiL = pcmChunkBuffer[byteIdx++].toInt()
                                    val sampleL = (hiL shl 8) or loL
                                    leftChannel[i] = sampleL shl 16

                                    val loR = pcmChunkBuffer[byteIdx++].toInt() and 0xFF
                                    val hiR = pcmChunkBuffer[byteIdx++].toInt()
                                    val sampleR = (hiR shl 8) or loR
                                    rightChannel[i] = sampleR shl 16
                                }
                            } else {
                                for (i in 0 until sampleFrames) {
                                    val lo = pcmChunkBuffer[byteIdx++].toInt() and 0xFF
                                    val hi = pcmChunkBuffer[byteIdx++].toInt()
                                    val sample = (hi shl 8) or lo
                                    val scaled = sample shl 16
                                    leftChannel[i] = scaled
                                    rightChannel[i] = 0
                                }
                            }

                            val encodedBytes = lame.lame_encode_buffer_int(
                                gfp,
                                leftChannel,
                                rightChannel,
                                sampleFrames,
                                mp3Buffer,
                                0,
                                mp3Buffer.size
                            )
                            if (encodedBytes < 0) {
                                throw IOException("Error durante la codificación de tramas MP3 ($encodedBytes)")
                            }
                            if (encodedBytes > 0) {
                                bos.write(mp3Buffer, 0, encodedBytes)
                            }

                            processedBytes += bytesRead
                            onProgress((processedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f))
                        }

                        val flushedBytes = lame.lame_encode_flush(gfp, mp3Buffer, 0, mp3Buffer.size)
                        if (flushedBytes > 0) {
                            bos.write(mp3Buffer, 0, flushedBytes)
                        }
                        bos.flush()
                    }
                }
            }
        } finally {
            try {
                lame.lame_close(gfp)
            } catch (_: Throwable) {
            }
        }
    }

    // =========================================================================================
    // Editor Binario de Etiquetas ID3v2.3 + Carátula (APIC) para MP3 y Átomos para M4A (v0.4)
    // =========================================================================================

    /**
     * Construye el bloque binario completo ID3v2.3 (`ID3\x03\x00\x00` + frames `TIT2`, `TPE1`,
     * `TALB`, `TYER`, `TCON` en UTF-16 con BOM y frame `APIC` para la carátula).
     */
    fun buildId3v23TagBytes(
        metadata: AudioMetadataSpec,
        coverArtBytes: ByteArray? = null,
        coverMimeType: String = "image/jpeg"
    ): ByteArray {
        val framesOut = ByteArrayOutputStream()

        fun writeTextFrame(frameId: String, value: String) {
            val clean = value.trim()
            if (clean.isEmpty() || frameId.length != 4) return
            val utf16LeBytes = clean.toByteArray(Charsets.UTF_16LE)
            val payload = ByteArray(1 + 2 + utf16LeBytes.size)
            payload[0] = 0x01 // UTF-16 con BOM
            payload[1] = 0xFF.toByte()
            payload[2] = 0xFE.toByte()
            System.arraycopy(utf16LeBytes, 0, payload, 3, utf16LeBytes.size)

            framesOut.write(frameId.toByteArray(Charsets.US_ASCII))
            framesOut.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(payload.size).array())
            framesOut.write(byteArrayOf(0x00, 0x00)) // Frame flags
            framesOut.write(payload)
        }

        writeTextFrame("TIT2", metadata.title)
        writeTextFrame("TPE1", metadata.artist)
        writeTextFrame("TALB", metadata.album)
        writeTextFrame("TYER", metadata.year)
        writeTextFrame("TCON", metadata.genre)

        if (coverArtBytes != null && coverArtBytes.isNotEmpty()) {
            val mimeAscii = coverMimeType.ifBlank { "image/jpeg" }.toByteArray(Charsets.ISO_8859_1)
            val apicPayload = ByteArrayOutputStream().apply {
                write(0x00) // Text encoding ISO-8859-1
                write(mimeAscii)
                write(0x00) // Null terminator de MIME
                write(0x03) // Picture type: 0x03 = Cover (front)
                write(0x00) // Descripción vacía terminada en null
                write(coverArtBytes)
            }.toByteArray()

            framesOut.write("APIC".toByteArray(Charsets.US_ASCII))
            framesOut.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(apicPayload.size).array())
            framesOut.write(byteArrayOf(0x00, 0x00))
            framesOut.write(apicPayload)
        }

        val allFrames = framesOut.toByteArray()
        if (allFrames.isEmpty()) return ByteArray(0)

        val header = ByteArray(10)
        header[0] = 'I'.code.toByte()
        header[1] = 'D'.code.toByte()
        header[2] = '3'.code.toByte()
        header[3] = 0x03 // ID3v2.3
        header[4] = 0x00 // Revision 0
        header[5] = 0x00 // Flags
        // Synchsafe 28-bit integer
        val size = allFrames.size
        header[6] = ((size shr 21) and 0x7F).toByte()
        header[7] = ((size shr 14) and 0x7F).toByte()
        header[8] = ((size shr 7) and 0x7F).toByte()
        header[9] = (size and 0x7F).toByte()

        val tagOut = ByteArrayOutputStream(10 + allFrames.size)
        tagOut.write(header)
        tagOut.write(allFrames)
        return tagOut.toByteArray()
    }

    /**
     * Inyecta o reemplaza la cabecera ID3v2.3 y carátula `APIC` al inicio de un archivo `.mp3`.
     */
    @Throws(IOException::class)
    fun writeId3v23TagsToMp3File(
        mp3File: File,
        metadata: AudioMetadataSpec,
        coverArtBytes: ByteArray? = null,
        coverMimeType: String = "image/jpeg"
    ) {
        val tagBytes = buildId3v23TagBytes(metadata, coverArtBytes, coverMimeType)
        if (tagBytes.isEmpty() || !mp3File.exists()) return

        val tempTagged = File(mp3File.parentFile, "${mp3File.name}.id3tmp")
        try {
            FileInputStream(mp3File).use { fis ->
                val head = ByteArray(10)
                val r = fis.read(head)
                val skipExistingTagBytes = if (r == 10 &&
                    head[0] == 'I'.code.toByte() &&
                    head[1] == 'D'.code.toByte() &&
                    head[2] == '3'.code.toByte()
                ) {
                    val existingSize = ((head[6].toInt() and 0x7F) shl 21) or
                        ((head[7].toInt() and 0x7F) shl 14) or
                        ((head[8].toInt() and 0x7F) shl 7) or
                        (head[9].toInt() and 0x7F)
                    existingSize.toLong()
                } else {
                    -1L
                }

                FileOutputStream(tempTagged).use { fos ->
                    BufferedOutputStream(fos, 64 * 1024).use { bos ->
                        bos.write(tagBytes)
                        if (skipExistingTagBytes >= 0L) {
                            var toSkip = skipExistingTagBytes
                            while (toSkip > 0L) {
                                val skipped = fis.skip(toSkip)
                                if (skipped <= 0L) break
                                toSkip -= skipped
                            }
                        } else if (r > 0) {
                            bos.write(head, 0, r)
                        }
                        fis.copyTo(bos, 64 * 1024)
                        bos.flush()
                    }
                }
            }
            tempTagged.copyTo(mp3File, overwrite = true)
        } finally {
            tempTagged.delete()
        }
    }

    /**
     * Lee y decodifica los frames ID3v2.3 (`TIT2`, `TPE1`, `TALB`, `TYER`, `TCON`, `APIC_SIZE`)
     * de un archivo/array `.mp3`.
     */
    fun parseId3v23TagsFromBytes(mp3Bytes: ByteArray): Map<String, String> {
        if (mp3Bytes.size < 10) return emptyMap()
        if (mp3Bytes[0] != 'I'.code.toByte() ||
            mp3Bytes[1] != 'D'.code.toByte() ||
            mp3Bytes[2] != '3'.code.toByte()
        ) {
            return emptyMap()
        }

        val tagSize = ((mp3Bytes[6].toInt() and 0x7F) shl 21) or
            ((mp3Bytes[7].toInt() and 0x7F) shl 14) or
            ((mp3Bytes[8].toInt() and 0x7F) shl 7) or
            (mp3Bytes[9].toInt() and 0x7F)

        val endPos = minOf(10 + tagSize, mp3Bytes.size)
        var pos = 10
        val result = linkedMapOf<String, String>()

        while (pos + 10 <= endPos) {
            val frameId = String(mp3Bytes, pos, 4, Charsets.US_ASCII)
            if (frameId.any { it.code !in 0x30..0x5A }) break
            val frameSize = ByteBuffer.wrap(mp3Bytes, pos + 4, 4).order(ByteOrder.BIG_ENDIAN).int
            pos += 10
            if (frameSize <= 0 || pos + frameSize > endPos) break

            if (frameId == "APIC") {
                result["APIC_BYTES"] = frameSize.toString()
            } else if (frameId.startsWith("T")) {
                val enc = mp3Bytes[pos].toInt() and 0xFF
                val textBytes = mp3Bytes.copyOfRange(pos + 1, pos + frameSize)
                val decoded = when (enc) {
                    1 -> {
                        if (textBytes.size >= 2 &&
                            textBytes[0] == 0xFF.toByte() &&
                            textBytes[1] == 0xFE.toByte()
                        ) {
                            String(textBytes, 2, textBytes.size - 2, Charsets.UTF_16LE)
                        } else if (textBytes.size >= 2 &&
                            textBytes[0] == 0xFE.toByte() &&
                            textBytes[1] == 0xFF.toByte()
                        ) {
                            String(textBytes, 2, textBytes.size - 2, Charsets.UTF_16BE)
                        } else {
                            String(textBytes, Charsets.UTF_16)
                        }
                    }
                    3 -> String(textBytes, Charsets.UTF_8)
                    else -> String(textBytes, Charsets.ISO_8859_1)
                }.trimEnd('\u0000')
                result[frameId] = decoded
            }
            pos += frameSize
        }

        return result
    }

    /**
     * Escribe un átomo de metadatos ISO-BMFF (`udta` / `ID32`) al final de un contenedor `.m4a`
     * para almacenar título, artista, álbum, año, género y carátula.
     */
    @Throws(IOException::class)
    fun writeMetadataAtomsToM4aFile(
        m4aFile: File,
        metadata: AudioMetadataSpec,
        coverArtBytes: ByteArray? = null
    ) {
        if (metadata.isEmpty && (coverArtBytes == null || coverArtBytes.isEmpty())) return
        val id3Payload = buildId3v23TagBytes(metadata, coverArtBytes, "image/jpeg")
        if (id3Payload.isEmpty() || !m4aFile.exists()) return

        // Construir caja MP4 'udta' compatible que envuelve el bloque ID3v2.3
        val boxSize = 8 + id3Payload.size
        val boxHeader = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(boxSize)
            put("udta".toByteArray(Charsets.US_ASCII))
        }.array()

        FileOutputStream(m4aFile, true).use { fos ->
            fos.write(boxHeader)
            fos.write(id3Payload)
            fos.flush()
        }
    }

    private fun readFullFrames(fis: FileInputStream, buffer: ByteArray, frameSize: Int): Int {
        var total = 0
        while (total < buffer.size) {
            val read = fis.read(buffer, total, buffer.size - total)
            if (read < 0) break
            total += read
        }
        val remainder = total % frameSize
        return total - remainder
    }

    private fun sampleRateToAdtsFrequencyIndex(sampleRate: Int): Int {
        return when (sampleRate) {
            96000 -> 0
            88200 -> 1
            64000 -> 2
            48000 -> 3
            44100 -> 4
            32000 -> 5
            24000 -> 6
            22050 -> 7
            16000 -> 8
            12000 -> 9
            11025 -> 10
            8000 -> 11
            7350 -> 12
            else -> 4
        }
    }
}
