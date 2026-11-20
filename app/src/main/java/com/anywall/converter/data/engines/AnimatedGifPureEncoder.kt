package com.anywall.converter.data.engines

import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * Codificador GIF89a animado 100% Kotlin puro con cuantización de paleta de 256 colores
 * (cubo RGB 6x6x6 + rampa de grises) y compresión LZW estándar (v0.3).
 * Funciona tanto en Android Runtime como en pruebas unitarias JVM sin dependencias nativas.
 */
object AnimatedGifPureEncoder {

    data class GifFrame(
        val width: Int,
        val height: Int,
        val argbPixels: IntArray
    )

    private val PALETTE_256: IntArray by lazy { buildStandard256Palette() }

    private fun buildStandard256Palette(): IntArray {
        val pal = IntArray(256)
        var idx = 0
        val levels = intArrayOf(0, 51, 102, 153, 204, 255)
        for (r in levels) {
            for (g in levels) {
                for (b in levels) {
                    pal[idx++] = (r shl 16) or (g shl 8) or b
                }
            }
        }
        // 216..255: 40 niveles adicionales de escala de grises
        for (i in 0 until 40) {
            val g = ((i + 1) * 255) / 41
            pal[idx++] = (g shl 16) or (g shl 8) or g
        }
        return pal
    }

    private fun mapArgbToPaletteIndex(argb: Int): Int {
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        val rIdx = (r * 5 + 127) / 255
        val gIdx = (g * 5 + 127) / 255
        val bIdx = (b * 5 + 127) / 255
        return (rIdx * 36 + gIdx * 6 + bIdx).coerceIn(0, 215)
    }

    fun encodeAnimatedGifToBytes(
        frames: List<GifFrame>,
        fps: Int = 4,
        loopForever: Boolean = true
    ): ByteArray {
        val out = ByteArrayOutputStream()
        encodeAnimatedGif(frames, fps, loopForever, out)
        return out.toByteArray()
    }

    fun encodeAnimatedGif(
        frames: List<GifFrame>,
        fps: Int = 4,
        loopForever: Boolean = true,
        output: OutputStream
    ) {
        require(frames.isNotEmpty()) { "Se requiere al menos un fotograma para codificar un GIF." }
        val canvasWidth = frames[0].width.coerceIn(1, 4096)
        val canvasHeight = frames[0].height.coerceIn(1, 4096)
        val delayCentiseconds = (100 / fps.coerceIn(1, 30)).coerceAtLeast(2)

        // 1. Cabecera mágica GIF89a
        output.write("GIF89a".toByteArray(Charsets.US_ASCII))

        // 2. Logical Screen Descriptor
        writeShortLE(output, canvasWidth)
        writeShortLE(output, canvasHeight)
        // Global Color Table Flag = 1 (bit 7), Color Resolution = 7 (bits 4..6), Sort = 0, Size of GCT = 7 (2^(7+1) = 256)
        output.write(0xF7)
        output.write(0) // Background color index
        output.write(0) // Pixel aspect ratio

        // 3. Global Color Table (256 * 3 = 768 bytes)
        for (rgb in PALETTE_256) {
            output.write((rgb ushr 16) and 0xFF)
            output.write((rgb ushr 8) and 0xFF)
            output.write(rgb and 0xFF)
        }

        // 4. Netscape 2.0 Application Extension para bucle infinito
        if (loopForever && frames.size > 1) {
            output.write(0x21) // Extension Introducer
            output.write(0xFF) // Application Extension Label
            output.write(11)   // Block Size
            output.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
            output.write(3)    // Sub-block data size
            output.write(1)    // Sub-block ID
            writeShortLE(output, 0) // Loop count 0 = infinite
            output.write(0)    // Block terminator
        }

        // 5. Cada fotograma
        for (frame in frames) {
            // Graphic Control Extension
            output.write(0x21)
            output.write(0xF9)
            output.write(4)
            output.write(0x04) // Disposal method: Do not dispose, no transparency
            writeShortLE(output, delayCentiseconds)
            output.write(0)    // Transparent color index
            output.write(0)    // Block terminator

            // Image Descriptor
            output.write(0x2C)
            writeShortLE(output, 0) // Left
            writeShortLE(output, 0) // Top
            writeShortLE(output, canvasWidth)
            writeShortLE(output, canvasHeight)
            output.write(0) // No Local Color Table, not interlaced

            // Cuantizar píxeles al tamaño del lienzo
            val indexedPixels = ByteArray(canvasWidth * canvasHeight)
            for (y in 0 until canvasHeight) {
                val srcY = (y * frame.height) / canvasHeight
                for (x in 0 until canvasWidth) {
                    val srcX = (x * frame.width) / canvasWidth
                    val pixel = frame.argbPixels[srcY * frame.width + srcX]
                    indexedPixels[y * canvasWidth + x] = mapArgbToPaletteIndex(pixel).toByte()
                }
            }

            // LZW Minimum Code Size = 8
            lzwEncodeFrame(indexedPixels, output)
        }

        // 6. GIF Trailer
        output.write(0x3B)
        output.flush()
    }

    private fun lzwEncodeFrame(indexedPixels: ByteArray, output: OutputStream) {
        val minCodeSize = 8
        output.write(minCodeSize)

        val clearCode = 1 shl minCodeSize // 256
        val endCode = clearCode + 1       // 257

        val subBlockBuffer = ByteArrayOutputStream(256)
        var bitAccumulator = 0
        var bitCount = 0

        fun flushSubBlockIfFull(force: Boolean = false) {
            val bytes = subBlockBuffer.toByteArray()
            var offset = 0
            while (bytes.size - offset >= 255) {
                output.write(255)
                output.write(bytes, offset, 255)
                offset += 255
            }
            subBlockBuffer.reset()
            if (offset < bytes.size) {
                if (force) {
                    val rem = bytes.size - offset
                    output.write(rem)
                    output.write(bytes, offset, rem)
                } else {
                    subBlockBuffer.write(bytes, offset, bytes.size - offset)
                }
            }
        }

        fun emitCode(code: Int, codeSize: Int) {
            bitAccumulator = bitAccumulator or (code shl bitCount)
            bitCount += codeSize
            while (bitCount >= 8) {
                subBlockBuffer.write(bitAccumulator and 0xFF)
                bitAccumulator = bitAccumulator ushr 8
                bitCount -= 8
                if (subBlockBuffer.size() >= 255) {
                    flushSubBlockIfFull(force = false)
                }
            }
        }

        var codeSize = minCodeSize + 1 // 9
        var nextCode = endCode + 1     // 258
        val dictionary = HashMap<Int, Int>(4096)

        emitCode(clearCode, codeSize)

        var prefixCode = indexedPixels[0].toInt() and 0xFF
        for (i in 1 until indexedPixels.size) {
            val k = indexedPixels[i].toInt() and 0xFF
            val key = (prefixCode shl 8) or k
            val existing = dictionary[key]
            if (existing != null) {
                prefixCode = existing
            } else {
                emitCode(prefixCode, codeSize)
                if (nextCode < 4096) {
                    dictionary[key] = nextCode
                    if (nextCode == (1 shl codeSize) && codeSize < 12) {
                        codeSize++
                    }
                    nextCode++
                } else {
                    emitCode(clearCode, codeSize)
                    dictionary.clear()
                    codeSize = minCodeSize + 1
                    nextCode = endCode + 1
                }
                prefixCode = k
            }
        }

        emitCode(prefixCode, codeSize)
        emitCode(endCode, codeSize)

        if (bitCount > 0) {
            subBlockBuffer.write(bitAccumulator and 0xFF)
        }
        flushSubBlockIfFull(force = true)
        output.write(0) // Block terminator
    }

    private fun writeShortLE(output: OutputStream, value: Int) {
        output.write(value and 0xFF)
        output.write((value ushr 8) and 0xFF)
    }
}
