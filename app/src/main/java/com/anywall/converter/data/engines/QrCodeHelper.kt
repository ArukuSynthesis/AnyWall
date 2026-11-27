package com.anywall.converter.data.engines

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.EnumMap
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Generador y Lector de códigos QR y códigos de barras (`PNG`, `SVG` vectorial y decodificación desde imagen)
 * impulsado por `com.google.zxing:core:3.5.3` (`Apache-2.0` - v0.6).
 */
object QrCodeHelper {

    data class DecodedBarcodeInfo(
        val text: String,
        val formatName: String,
        val wifiSsid: String? = null,
        val wifiPassword: String? = null,
        val wifiSecurity: String? = null
    )

    fun encodeQrMatrix(content: String, sizePx: Int = 512, marginModules: Int = 2): BitMatrix {
        val safeContent = content.ifBlank { "AnyWall Universal Converter" }
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

    fun decodeBarcodeInfoFromArgbPixels(width: Int, height: Int, argbPixels: IntArray): DecodedBarcodeInfo? {
        val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
            put(DecodeHintType.CHARACTER_SET, "UTF-8")
            put(DecodeHintType.TRY_HARDER, true)
        }
        val reader = MultiFormatReader().apply { setHints(hints) }
        val source = RGBLuminanceSource(width, height, argbPixels)

        val candidates = listOf(
            { BinaryBitmap(HybridBinarizer(source)) },
            { BinaryBitmap(GlobalHistogramBinarizer(source)) },
            {
                val invertedPixels = IntArray(argbPixels.size) { idx ->
                    val p = argbPixels[idx]
                    val r = 255 - ((p shr 16) and 0xFF)
                    val g = 255 - ((p shr 8) and 0xFF)
                    val b = 255 - (p and 0xFF)
                    (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
                BinaryBitmap(HybridBinarizer(RGBLuminanceSource(width, height, invertedPixels)))
            }
        )

        for (candidateBuilder in candidates) {
            try {
                reader.reset()
                val res = reader.decodeWithState(candidateBuilder())
                val rawText = res.text ?: continue
                return parseDecodedText(rawText, res.barcodeFormat?.name ?: "QR_CODE")
            } catch (_: Throwable) {
                // Continuar con el siguiente binarizador/inversión
            }
        }
        return null
    }

    fun decodeQrFromArgbPixels(width: Int, height: Int, argbPixels: IntArray): String? {
        return decodeBarcodeInfoFromArgbPixels(width, height, argbPixels)?.text
    }

    fun decodeBarcodeInfoFromBitmap(bitmap: Bitmap): DecodedBarcodeInfo? {
        if (bitmap.width <= 0 || bitmap.height <= 0) return null
        val maxEdge = max(bitmap.width, bitmap.height)
        val workingBitmap = if (maxEdge > 1600) {
            val scale = 1600f / maxEdge.toFloat()
            val targetW = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
            val targetH = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        } else {
            bitmap
        }
        return try {
            val w = workingBitmap.width
            val h = workingBitmap.height
            val pixels = IntArray(w * h)
            workingBitmap.getPixels(pixels, 0, w, 0, 0, w, h)
            decodeBarcodeInfoFromArgbPixels(w, h, pixels)
        } finally {
            if (workingBitmap !== bitmap) {
                workingBitmap.recycle()
            }
        }
    }

    fun decodeQrFromBitmap(bitmap: Bitmap): String? {
        return decodeBarcodeInfoFromBitmap(bitmap)?.text
    }

    private fun parseDecodedText(rawText: String, formatName: String): DecodedBarcodeInfo {
        if (rawText.startsWith("WIFI:", ignoreCase = true)) {
            val body = rawText.substringAfter(":", "")
            var ssid: String? = null
            var password: String? = null
            var security: String? = null
            body.split(";").forEach { token ->
                when {
                    token.startsWith("S:", ignoreCase = true) -> ssid = token.substring(2).ifBlank { null }
                    token.startsWith("P:", ignoreCase = true) -> password = token.substring(2).ifBlank { null }
                    token.startsWith("T:", ignoreCase = true) -> security = token.substring(2).ifBlank { null }
                }
            }
            return DecodedBarcodeInfo(
                text = rawText,
                formatName = formatName,
                wifiSsid = ssid,
                wifiPassword = password,
                wifiSecurity = security
            )
        }
        return DecodedBarcodeInfo(text = rawText, formatName = formatName)
    }
}

