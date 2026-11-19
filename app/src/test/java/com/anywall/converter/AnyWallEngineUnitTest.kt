package com.anywall.converter

import com.anywall.converter.data.engines.ImageEngineImpl
import com.anywall.converter.data.engines.PcmWavMp3AudioHelper
import com.anywall.converter.domain.model.InputFileItem
import com.anywall.converter.domain.model.SupportedFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

class AnyWallEngineUnitTest {

    @Test
    fun detectFormats_recognizesAllSupportedExtensionsAndMimeTypes() {
        assertEquals(SupportedFormat.JPG, SupportedFormat.detect("foto.jpeg", "image/jpeg"))
        assertEquals(SupportedFormat.PNG, SupportedFormat.detect("icono.png", "image/png"))
        assertEquals(SupportedFormat.WEBP, SupportedFormat.detect("sticker.webp", "image/webp"))
        assertEquals(SupportedFormat.BMP, SupportedFormat.detect("textura.bmp", "image/bmp"))
        assertEquals(SupportedFormat.SVG, SupportedFormat.detect("vector.svg", "image/svg+xml"))
        assertEquals(SupportedFormat.PDF, SupportedFormat.detect("informe.pdf", "application/pdf"))
        assertEquals(
            SupportedFormat.DOCX,
            SupportedFormat.detect(
                "contrato.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            )
        )
        assertEquals(SupportedFormat.MP4, SupportedFormat.detect("clip.mp4", "video/mp4"))
        assertEquals(SupportedFormat.MKV, SupportedFormat.detect("pelicula.mkv", "video/x-matroska"))
        assertEquals(SupportedFormat.MOV, SupportedFormat.detect("grabacion.mov", "video/quicktime"))
        assertEquals(SupportedFormat.WEBM, SupportedFormat.detect("web.webm", "video/webm"))
        assertEquals(SupportedFormat.MP3, SupportedFormat.detect("cancion.mp3", "audio/mpeg"))
        assertEquals(SupportedFormat.AAC, SupportedFormat.detect("voz.aac", "audio/aac"))
        assertEquals(SupportedFormat.M4A, SupportedFormat.detect("podcast.m4a", "audio/mp4"))
        assertEquals(SupportedFormat.WAV, SupportedFormat.detect("master.wav", "audio/wav"))
        assertEquals(SupportedFormat.ZIP, SupportedFormat.detect("respaldo.zip", "application/zip"))
        assertEquals(SupportedFormat.TAR, SupportedFormat.detect("paquete.tar", "application/x-tar"))
        assertEquals(SupportedFormat.TAR_GZ, SupportedFormat.detect("fuentes.tar.gz", "application/gzip"))
        assertEquals(SupportedFormat.SEVEN_Z, SupportedFormat.detect("datos.7z", "application/x-7z-compressed"))
    }

    @Test
    fun parsePageIndices_parsesRangesAndSinglePagesCorrectly() {
        val indices = ImageEngineImpl.parsePageIndices("1-3, 5, 8-9", totalPages = 10)
        assertEquals(listOf(0, 1, 2, 4, 7, 8), indices)

        val allPages = ImageEngineImpl.parsePageIndices("", totalPages = 4)
        assertEquals(listOf(0, 1, 2, 3), allPages)
    }

    @Test
    fun formatBytes_formatsHumanReadableSizes() {
        assertEquals("512 B", InputFileItem.formatBytes(512))
        assertEquals("2.0 KB", InputFileItem.formatBytes(2048))
        assertEquals("1.50 MB", InputFileItem.formatBytes((1.5 * 1024 * 1024).toLong()))
    }

    @Test
    fun pcmToWavAndMp3_encodesValidAudioFiles() {
        val sampleRate = 22050
        val channels = 1
        val numSamples = sampleRate / 2 // 0.5 segundos de onda sinusoidal a 440 Hz
        val pcmBytes = ByteArray(numSamples * 2)
        val bb = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until numSamples) {
            val sample = (sin(2.0 * Math.PI * 440.0 * i / sampleRate) * 16000).toInt().toShort()
            bb.putShort(sample)
        }

        val tempPcm = File.createTempFile("test_pcm_", ".pcm")
        val tempWav = File.createTempFile("test_wav_", ".wav")
        val tempMp3 = File.createTempFile("test_mp3_", ".mp3")
        try {
            tempPcm.writeBytes(pcmBytes)
            val info = PcmWavMp3AudioHelper.DecodedPcmInfo(
                pcmFile = tempPcm,
                sampleRate = sampleRate,
                channelCount = channels,
                totalPcmBytes = pcmBytes.size.toLong()
            )

            PcmWavMp3AudioHelper.writeWavFileFromPcm16Le(info, tempWav)
            assertTrue(tempWav.exists())
            assertEquals(44L + pcmBytes.size, tempWav.length())

            PcmWavMp3AudioHelper.encodePcm16LeToMp3(
                pcmInfo = info,
                outputMp3File = tempMp3,
                qualityPercent = 80,
                onProgress = {}
            )
            assertTrue(tempMp3.exists())
            assertTrue("El archivo MP3 debe tener contenido codificado", tempMp3.length() > 256L)
        } finally {
            tempPcm.delete()
            tempWav.delete()
            tempMp3.delete()
        }
    }
}
