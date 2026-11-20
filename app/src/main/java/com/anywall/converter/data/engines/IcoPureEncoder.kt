package com.anywall.converter.data.engines

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Codificador 100% Kotlin puro de archivos de icono `.ico` (Windows Icon Format - v0.3).
 * Soporta empaquetar una o múltiples imágenes PNG (ej. 48x48, 96x96, 256x256) dentro de un contenedor ICO estándar.
 */
object IcoPureEncoder {

    data class IcoImageEntry(
        val width: Int,
        val height: Int,
        val pngBytes: ByteArray
    )

    fun encodeSinglePngToIco(width: Int, height: Int, pngBytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(pngBytes.size + 32)
        writeIco(listOf(IcoImageEntry(width, height, pngBytes)), out)
        return out.toByteArray()
    }

    fun writeIco(entries: List<IcoImageEntry>, output: OutputStream) {
        require(entries.isNotEmpty()) { "Se requiere al menos una imagen para generar un archivo ICO." }

        val count = entries.size
        val headerSize = 6 + (16 * count)
        val headerBuf = ByteBuffer.allocate(headerSize).order(ByteOrder.LITTLE_ENDIAN)

        // ICONDIR Header: Reserved (0), Type (1 = ICO), Count
        headerBuf.putShort(0)
        headerBuf.putShort(1)
        headerBuf.putShort(count.toShort())

        var currentOffset = headerSize
        for (entry in entries) {
            val wByte = if (entry.width >= 256) 0.toByte() else entry.width.toByte()
            val hByte = if (entry.height >= 256) 0.toByte() else entry.height.toByte()
            headerBuf.put(wByte)
            headerBuf.put(hByte)
            headerBuf.put(0) // ColorCount (0 for >=8bpp)
            headerBuf.put(0) // Reserved
            headerBuf.putShort(1) // ColorPlanes
            headerBuf.putShort(32) // BitsPerPixel (32-bit ARGB PNG)
            headerBuf.putInt(entry.pngBytes.size) // SizeInBytes
            headerBuf.putInt(currentOffset) // ImageOffset
            currentOffset += entry.pngBytes.size
        }

        output.write(headerBuf.array())
        for (entry in entries) {
            output.write(entry.pngBytes)
        }
        output.flush()
    }
}
