package com.anywall.converter.data.engines

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.EnumMap

/**
 * Generador y Lector 100% offline de códigos QR (`PNG`, `SVG` vectorial y decodificación desde imagen)
 * impulsado por `com.google.zxing:core:3.5.3` (`Apache-2.0` - v0.3).
 */
object QrCodeHelper {

    fun encodeQrMatrix(content: String, sizePx: Int = 512, marginModules: Int = 2): BitMatrix {
        val safeContent = content.ifBlank { "AnyWall Offline Converter" }
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
            put(EncodeHintType.CHARACTER_SET, "UTF-8")
            put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
            put(EncodeHintType.MARGIN, marginModules)
        }
        return QRCodeWriter().encode(safeContent, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    }

    fun generateQrSvg(content: String, moduleSizePx: Int = 10): String {
        val matrix = encodeQrMatrix(content, sizePx = 1, marginModules = 2)
        val width = matrix.width
        val height = matrix.height
        val svgWidth = width * moduleSizePx
        val svgHeight = height * moduleSizePx

        return buildString {
            appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            appendLine("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 $svgWidth $svgHeight\" width=\"$svgWidth\" height=\"$svgHeight\" shape-rendering=\"crispEdges\">")
            appendLine("  <rect width=\"100%\" height=\"100%\" fill=\"#FFFFFF\"/>")
            append("  <path fill=\"#000000\" d=\"")
            for (y in 0 until height) {
                for (x in 0 until width) {
                    if (matrix.get(x, y)) {
                        val px = x * moduleSizePx
                        val py = y * moduleSizePx
                        append("M$px,${py}h${moduleSizePx}v${moduleSizePx}h-${moduleSizePx}z")
                    }
                }
            }
            appendLine("\"/>")
            append("</svg>")
        }
    }

    fun generateQrBitmap(content: String, sizePx: Int = 512): Bitmap {
        val matrix = encodeQrMatrix(content, sizePx = sizePx, marginModules = 2)
        val w = matrix.width
        val h = matrix.height
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            val offset = y * w
            for (x in 0 until w) {
                pixels[offset + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, w, 0, 0, w, h)
        }
    }

    fun decodeQrFromArgbPixels(width: Int, height: Int, argbPixels: IntArray): String? {
        return try {
            val source = RGBLuminanceSource(width, height, argbPixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
                put(DecodeHintType.CHARACTER_SET, "UTF-8")
                put(DecodeHintType.TRY_HARDER, true)
            }
            QRCodeReader().decode(binaryBitmap, hints).text
        } catch (_: Throwable) {
            null
        }
    }

    fun decodeQrFromBitmap(bitmap: Bitmap): String? {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return null
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        return decodeQrFromArgbPixels(w, h, pixels)
    }
}
