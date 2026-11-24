package com.anywall.converter.domain.model

import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.abs

/**
 * Rectángulo normalizado `[0.0 .. 1.0]` de censura aplicable sobre una página PDF o una imagen (v0.4).
 * Soporta `PIXELATE`, `BLUR` y `SOLID_BLACK`.
 */
data class NormalizedRedactionBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val isPixelated: Boolean = false,
    val effectMode: CensorEffectMode = if (isPixelated) CensorEffectMode.PIXELATE else CensorEffectMode.SOLID_BLACK
) {
    val clampedLeft: Float get() = minOf(left, right).coerceIn(0f, 1f)
    val clampedTop: Float get() = minOf(top, bottom).coerceIn(0f, 1f)
    val clampedRight: Float get() = maxOf(left, right).coerceIn(0f, 1f)
    val clampedBottom: Float get() = maxOf(top, bottom).coerceIn(0f, 1f)

    fun toToken(): String {
        return String.format(
            Locale.US,
            "%.4f,%.4f,%.4f,%.4f,%d,%s",
            clampedLeft,
            clampedTop,
            clampedRight,
            clampedBottom,
            if (isPixelated || effectMode == CensorEffectMode.PIXELATE) 1 else 0,
            effectMode.name
        )
    }

    companion object {
        fun fromToken(token: String): NormalizedRedactionBox? {
            val parts = token.split(',')
            if (parts.size < 4) return null
            val l = parts[0].toFloatOrNull() ?: return null
            val t = parts[1].toFloatOrNull() ?: return null
            val r = parts[2].toFloatOrNull() ?: return null
            val b = parts[3].toFloatOrNull() ?: return null
            val pix = parts.getOrNull(4) == "1"
            val mode = parts.getOrNull(5)?.let { CensorEffectMode.fromNameOrDefault(it) }
                ?: if (pix) CensorEffectMode.PIXELATE else CensorEffectMode.SOLID_BLACK
            return NormalizedRedactionBox(
                left = l,
                top = t,
                right = r,
                bottom = b,
                isPixelated = (mode == CensorEffectMode.PIXELATE || pix),
                effectMode = mode
            )
        }

        fun serializeList(boxes: List<NormalizedRedactionBox>): String {
            if (boxes.isEmpty()) return ""
            return boxes.joinToString(separator = ";") { it.toToken() }
        }

        fun deserializeList(raw: String?): List<NormalizedRedactionBox> {
            if (raw.isNullOrBlank()) return emptyList()
            return raw.split(';').mapNotNull { fromToken(it.trim()) }
        }
    }
}

/**
 * Trazo libre de censura táctil dibujado con el dedo sobre la imagen (`ImageVisualEditorDialog` - v0.4).
 */
data class NormalizedRedactionStroke(
    val points: List<Pair<Float, Float>>,
    val strokeWidthNorm: Float = 0.05f,
    val effectMode: CensorEffectMode = CensorEffectMode.PIXELATE
) {
    fun toToken(): String {
        if (points.isEmpty()) return ""
        val ptsStr = points.joinToString("|") { (x, y) ->
            String.format(Locale.US, "%.4f:%.4f", x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
        }
        return String.format(
            Locale.US,
            "%s~%.4f~%s",
            effectMode.name,
            strokeWidthNorm.coerceIn(0.01f, 0.25f),
            ptsStr
        )
    }

    companion object {
        fun fromToken(token: String): NormalizedRedactionStroke? {
            if (token.isBlank()) return null
            val parts = token.split('~')
            if (parts.size < 3) return null
            val mode = CensorEffectMode.fromNameOrDefault(parts[0])
            val width = parts[1].toFloatOrNull()?.coerceIn(0.01f, 0.25f) ?: 0.05f
            val pts = parts[2].split('|').mapNotNull { pairStr ->
                val xy = pairStr.split(':')
                if (xy.size < 2) null
                else {
                    val x = xy[0].toFloatOrNull() ?: return@mapNotNull null
                    val y = xy[1].toFloatOrNull() ?: return@mapNotNull null
                    x.coerceIn(0f, 1f) to y.coerceIn(0f, 1f)
                }
            }
            if (pts.isEmpty()) return null
            return NormalizedRedactionStroke(pts, width, mode)
        }

        fun serializeList(strokes: List<NormalizedRedactionStroke>): String {
            if (strokes.isEmpty()) return ""
            return strokes.mapNotNull { it.toToken().takeIf { t -> t.isNotEmpty() } }
                .joinToString(separator = ";")
        }

        fun deserializeList(raw: String?): List<NormalizedRedactionStroke> {
            if (raw.isNullOrBlank()) return emptyList()
            return raw.split(';').mapNotNull { fromToken(it.trim()) }
        }
    }
}

/**
 * Cuadrilátero de 4 esquinas independientes para corrección de perspectiva / escáner con `Matrix.setPolyToPoly` (v0.4).
 */
data class PerspectiveQuadSpec(
    val tlX: Float = 0f,
    val tlY: Float = 0f,
    val trX: Float = 1f,
    val trY: Float = 0f,
    val brX: Float = 1f,
    val brY: Float = 1f,
    val blX: Float = 0f,
    val blY: Float = 1f
) {
    val isModified: Boolean
        get() = abs(tlX - 0f) > 0.004f || abs(tlY - 0f) > 0.004f ||
            abs(trX - 1f) > 0.004f || abs(trY - 0f) > 0.004f ||
            abs(brX - 1f) > 0.004f || abs(brY - 1f) > 0.004f ||
            abs(blX - 0f) > 0.004f || abs(blY - 1f) > 0.004f

    fun toToken(): String {
        return String.format(
            Locale.US,
            "%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f",
            tlX.coerceIn(0f, 1f),
            tlY.coerceIn(0f, 1f),
            trX.coerceIn(0f, 1f),
            trY.coerceIn(0f, 1f),
            brX.coerceIn(0f, 1f),
            brY.coerceIn(0f, 1f),
            blX.coerceIn(0f, 1f),
            blY.coerceIn(0f, 1f)
        )
    }

    companion object {
        fun fromToken(raw: String?): PerspectiveQuadSpec {
            if (raw.isNullOrBlank()) return PerspectiveQuadSpec()
            val p = raw.split(',')
            if (p.size < 8) return PerspectiveQuadSpec()
            return PerspectiveQuadSpec(
                tlX = p[0].toFloatOrNull()?.coerceIn(0f, 1f) ?: 0f,
                tlY = p[1].toFloatOrNull()?.coerceIn(0f, 1f) ?: 0f,
                trX = p[2].toFloatOrNull()?.coerceIn(0f, 1f) ?: 1f,
                trY = p[3].toFloatOrNull()?.coerceIn(0f, 1f) ?: 0f,
                brX = p[4].toFloatOrNull()?.coerceIn(0f, 1f) ?: 1f,
                brY = p[5].toFloatOrNull()?.coerceIn(0f, 1f) ?: 1f,
                blX = p[6].toFloatOrNull()?.coerceIn(0f, 1f) ?: 0f,
                blY = p[7].toFloatOrNull()?.coerceIn(0f, 1f) ?: 1f
            )
        }
    }
}

/**
 * Tipos de anotaciones interactivas en el Editor PDF Táctil (v0.4).
 */
enum class PdfAnnotationType(val labelEs: String, val labelEn: String) {
    TEXT("Texto", "Text"),
    DATE_STAMP("Sello de fecha", "Date Stamp"),
    HIGHLIGHTER("Resaltador", "Highlighter");

    companion object {
        fun fromNameOrDefault(name: String?): PdfAnnotationType {
            if (name.isNullOrBlank()) return TEXT
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: TEXT
        }
    }
}

/**
 * Anotación individual sobre una página PDF (Texto libre, Sello de fecha o Resaltador translúcido - v0.4).
 */
data class PdfPageAnnotation(
    val type: PdfAnnotationType = PdfAnnotationType.TEXT,
    val xNorm: Float = 0.15f,
    val yNorm: Float = 0.15f,
    val endXNorm: Float = 0.65f,
    val endYNorm: Float = 0.20f,
    val text: String = "",
    val colorArgb: Int = 0xFFFFEB3B.toInt(),
    val fontSizePt: Float = 13f
) {
    fun toToken(): String {
        return listOf(
            type.name,
            String.format(Locale.US, "%.4f", xNorm.coerceIn(0f, 1f)),
            String.format(Locale.US, "%.4f", yNorm.coerceIn(0f, 1f)),
            String.format(Locale.US, "%.4f", endXNorm.coerceIn(0f, 1f)),
            String.format(Locale.US, "%.4f", endYNorm.coerceIn(0f, 1f)),
            URLEncoder.encode(text, Charsets.UTF_8.name()),
            colorArgb.toString(),
            String.format(Locale.US, "%.1f", fontSizePt.coerceIn(8f, 48f))
        ).joinToString("|")
    }

    companion object {
        fun fromToken(token: String): PdfPageAnnotation? {
            if (token.isBlank()) return null
            val p = token.split('|')
            if (p.size < 8) return null
            return PdfPageAnnotation(
                type = PdfAnnotationType.fromNameOrDefault(p[0]),
                xNorm = p[1].toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.15f,
                yNorm = p[2].toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.15f,
                endXNorm = p[3].toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.65f,
                endYNorm = p[4].toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.20f,
                text = URLDecoder.decode(p[5], Charsets.UTF_8.name()),
                colorArgb = p[6].toIntOrNull() ?: 0xFFFFEB3B.toInt(),
                fontSizePt = p[7].toFloatOrNull()?.coerceIn(8f, 48f) ?: 13f
            )
        }

        fun serializeList(list: List<PdfPageAnnotation>): String {
            if (list.isEmpty()) return ""
            return list.joinToString(";") { it.toToken() }
        }

        fun deserializeList(raw: String?): List<PdfPageAnnotation> {
            if (raw.isNullOrBlank()) return emptyList()
            return raw.split(';').mapNotNull { fromToken(it.trim()) }
        }
    }
}

/**
 * Especificación individual de edición visual de una página de PDF en vista previa (v0.4).
 * Soporta Drag & Drop entre PDFs, rotación, exclusión/inclusión, páginas en blanco,
 * firma manuscrita arrastrable/redimensionable libremente, cajas de censura trazadas con el dedo,
 * anotaciones (texto, sello de fecha y resaltador translúcido) y escáner de perspectiva de 4 esquinas.
 */
data class PdfPageEditItem(
    val id: String,
    val sourceUriString: String,
    val sourceDisplayName: String,
    val originalPageIndex: Int,
    val totalPagesInSource: Int,
    val rotationDegrees: Int = 0,
    val included: Boolean = true,
    val isBlankSeparator: Boolean = false,
    val hasSignatureStamp: Boolean = false,
    val redactionBoxes: List<NormalizedRedactionBox> = emptyList(),
    val signatureXNorm: Float = 0.65f,
    val signatureYNorm: Float = 0.82f,
    val signatureWidthNorm: Float = 0.26f,
    val annotations: List<PdfPageAnnotation> = emptyList(),
    val perspectiveQuad: PerspectiveQuadSpec = PerspectiveQuadSpec()
) {
    val normalizedRotation: Int
        get() = ((rotationDegrees % 360) + 360) % 360

    val displayPageNumber: Int
        get() = if (isBlankSeparator) 0 else (originalPageIndex + 1)

    companion object {
        private fun enc(value: String): String =
            URLEncoder.encode(value, Charsets.UTF_8.name())

        private fun dec(value: String): String =
            URLDecoder.decode(value, Charsets.UTF_8.name())

        /**
         * Serializa la lista de ediciones de páginas en un formato determinista 100% Kotlin puro.
         */
        fun toJsonArrayString(items: List<PdfPageEditItem>): String {
            if (items.isEmpty()) return ""
            return items.joinToString(separator = "\n") { item ->
                listOf(
                    enc(item.id),
                    enc(item.sourceUriString),
                    enc(item.sourceDisplayName),
                    item.originalPageIndex.toString(),
                    item.totalPagesInSource.toString(),
                    item.normalizedRotation.toString(),
                    if (item.included) "1" else "0",
                    if (item.isBlankSeparator) "1" else "0",
                    if (item.hasSignatureStamp) "1" else "0",
                    enc(NormalizedRedactionBox.serializeList(item.redactionBoxes)),
                    String.format(Locale.US, "%.4f", item.signatureXNorm.coerceIn(0f, 0.92f)),
                    String.format(Locale.US, "%.4f", item.signatureYNorm.coerceIn(0f, 0.92f)),
                    String.format(Locale.US, "%.4f", item.signatureWidthNorm.coerceIn(0.08f, 0.85f)),
                    enc(PdfPageAnnotation.serializeList(item.annotations)),
                    enc(item.perspectiveQuad.toToken())
                ).joinToString(separator = "\t")
            }
        }

        fun fromJsonArrayString(rawSerialized: String?): List<PdfPageEditItem> {
            if (rawSerialized.isNullOrBlank()) return emptyList()
            val lines = rawSerialized.lines().filter { it.isNotBlank() }
            val result = ArrayList<PdfPageEditItem>(lines.size)
            for ((idx, line) in lines.withIndex()) {
                val parts = line.split('\t')
                if (parts.size < 7) continue
                val id = dec(parts[0]).ifBlank { "page_$idx" }
                val sourceUri = dec(parts[1])
                val sourceName = dec(parts[2]).ifBlank { "PDF" }
                val pageIndex = parts[3].toIntOrNull() ?: continue
                val totalPages = parts[4].toIntOrNull() ?: 1
                val rotation = parts[5].toIntOrNull() ?: 0
                val included = parts[6] == "1" || parts[6].equals("true", ignoreCase = true)
                val isBlank = parts.getOrNull(7) == "1"
                val hasSig = parts.getOrNull(8) == "1"
                val boxes = NormalizedRedactionBox.deserializeList(
                    parts.getOrNull(9)?.let { dec(it) }
                )
                val sigX = parts.getOrNull(10)?.toFloatOrNull()?.coerceIn(0f, 0.92f) ?: 0.65f
                val sigY = parts.getOrNull(11)?.toFloatOrNull()?.coerceIn(0f, 0.92f) ?: 0.82f
                val sigW = parts.getOrNull(12)?.toFloatOrNull()?.coerceIn(0.08f, 0.85f) ?: 0.26f
                val annotations = PdfPageAnnotation.deserializeList(
                    parts.getOrNull(13)?.let { dec(it) }
                )
                val quad = PerspectiveQuadSpec.fromToken(
                    parts.getOrNull(14)?.let { dec(it) }
                )

                result.add(
                    PdfPageEditItem(
                        id = id,
                        sourceUriString = sourceUri,
                        sourceDisplayName = sourceName,
                        originalPageIndex = pageIndex,
                        totalPagesInSource = totalPages,
                        rotationDegrees = rotation,
                        included = included,
                        isBlankSeparator = isBlank,
                        hasSignatureStamp = hasSig,
                        redactionBoxes = boxes,
                        signatureXNorm = sigX,
                        signatureYNorm = sigY,
                        signatureWidthNorm = sigW,
                        annotations = annotations,
                        perspectiveQuad = quad
                    )
                )
            }
            return result
        }
    }
}
