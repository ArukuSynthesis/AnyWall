package com.anywall.converter.data.engines

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
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
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Procesador local de bajo nivel para:
 * 1. Extracción de flujo ADTS `.aac` desde contenedor MP4/M4A generado por Media3 Transformer.
 * 2. Decodificación de audio a PCM 16-bit Little-Endian mediante `MediaExtractor` + `MediaCodec`.
 * 3. Escritura de archivos `.wav` (RIFF WAVE PCM 16-bit).
 * 4. Codificación a `.mp3` 100% en Java puro usando el motor LAME (`de.sciss.jump3r.mp3.Lame`)
 *    sin depender de `javax.sound.sampled` (ausente en Android).
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
                        // Perfil AAC LC = 2 (índice ADTS = profile - 1 = 1)
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
        // Si el archivo de entrada ya es un WAV PCM 16-bit LE sin compresión, podemos extraer su PCM directo
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

    /**
     * Si el archivo de entrada ya es un archivo RIFF WAVE PCM 16-bit sin comprimir,
     * extrae sus muestras PCM directamente de forma ultrarrápida.
     */
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

                // Buscar el subchunk "data"
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
        header.putInt(16) // Tamaño subchunk PCM
        header.putShort(1) // AudioFormat = 1 (PCM lineal)
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
     * Codifica muestras PCM 16-bit Little-Endian a un archivo `.mp3` real usando los módulos internos
     * de `de.sciss.jump3r.mp3.Lame` (LAME 3.98.4 en Java puro, compatible al 100% con Android Runtime).
     */
    @Throws(IOException::class)
    fun encodePcm16LeToMp3(
        pcmInfo: DecodedPcmInfo,
        outputMp3File: File,
        qualityPercent: Int,
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

        // Enlazar todos los submódulos internos del motor LAME exactamente como lo requiere jump3r
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
        val bitrateKbps = when {
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
        val bytesPerSample = 2 // 16-bit PCM
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
                                    val hiL = pcmChunkBuffer[byteIdx++].toInt() // sign-extended
                                    val sampleL = (hiL shl 8) or loL
                                    // Escalar muestra de 16-bit a 32-bit con signo como espera lame_encode_buffer_int
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
            else -> 4 // 44100 Hz por defecto
        }
    }
}
