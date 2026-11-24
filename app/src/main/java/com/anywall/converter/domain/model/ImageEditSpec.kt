package com.anywall.converter.domain.model

import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.abs

/**
 * Especificación determinista del Estudio de Fotos 100% Táctil (`ImageVisualEditorDialog` - v0.4).
 * Incluye:
 * - Recorte gestual (`cropLeftNorm..cropRightNorm`, `cropTopNorm..cropBottomNorm`) + proporción (`CropAspectRatioPreset`)
 * - Enderezado por dial horizontal (`straightenDegrees` en `-45°..+45°`), rotación `90°` y volteo H/V
 * - Escáner / Perspectiva de 4 esquinas (`perspectiveQuad` con `Matrix.setPolyToPoly`)
 * - Ajustes en vivo a 60 FPS: Exposición, Brillo, Contraste, Saturación, Temperatura, Nitidez y Viñeta
 * - Filtros con intensidad regulable (`0..100%`)
 * - Censura táctil: pincel libre con el dedo (`redactionStrokes`) + rectángulos arrastrables (`redactionBoxes`) en `Pixelado`, `Blur` o `Negro sólido`
 * - Texto / Marca de agua interactiva arrastrable con 1 dedo y escalable/rotable con 2 dedos
 */
data class ImageEditSpec(
    val cropLeftNorm: Float = 0f,
    val cropTopNorm: Float = 0f,
    val cropRightNorm: Float = 1f,
    val cropBottomNorm: Float = 1f,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val filterPreset: ImageFilterPreset = ImageFilterPreset.NONE,
    val aspectCanvasMode: ImageAspectCanvasMode = ImageAspectCanvasMode.ORIGINAL,
    val watermarkText: String = "",
    val watermarkOpacityPercent: Int = 75,
    val redactionBoxes: List<NormalizedRedactionBox> = emptyList(),
    // Nuevos campos v0.4 Estudio Táctil:
    val straightenDegrees: Float = 0f,
    val rotation90Degrees: Int = 0,
    val cropAspectRatioPreset: CropAspectRatioPreset = CropAspectRatioPreset.FREE,
    val perspectiveQuad: PerspectiveQuadSpec = PerspectiveQuadSpec(),
    val exposure: Float = 0f,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val temperature: Float = 0f,
    val sharpness: Float = 0f,
    val vignette: Float = 0f,
    val filterIntensityPercent: Int = 100,
    val redactionStrokes: List<NormalizedRedactionStroke> = emptyList(),
    val watermarkPosXNorm: Float = 0.50f,
    val watermarkPosYNorm: Float = 0.86f,
    val watermarkScale: Float = 1.0f,
    val watermarkRotationDegrees: Float = 0f,
    val watermarkColorArgb: Int = 0xFFFFFFFF.toInt(),
    val applyToAllBatch: Boolean = true
) {
    val hasCrop: Boolean
        get() = cropLeftNorm > 0.005f || cropTopNorm > 0.005f ||
            cropRightNorm < 0.995f || cropBottomNorm < 0.995f

    val hasColorAdjustments: Boolean
        get() = abs(exposure) > 0.5f ||
            abs(brightness) > 0.5f ||
            abs(contrast) > 0.5f ||
            abs(saturation) > 0.5f ||
            abs(temperature) > 0.5f ||
            sharpness > 0.5f ||
            vignette > 0.5f

    val normalizedRotation90: Int
        get() = ((rotation90Degrees % 360) + 360) % 360

    val isDefault: Boolean
        get() = !hasCrop &&
            !flipHorizontal &&
            !flipVertical &&
            abs(straightenDegrees) < 0.1f &&
            normalizedRotation90 == 0 &&
            !perspectiveQuad.isModified &&
            !hasColorAdjustments &&
            (filterPreset == ImageFilterPreset.NONE || filterIntensityPercent <= 0) &&
            aspectCanvasMode == ImageAspectCanvasMode.ORIGINAL &&
            watermarkText.isBlank() &&
            redactionBoxes.isEmpty() &&
            redactionStrokes.isEmpty()

    fun toSerializedString(): String {
        if (isDefault) return ""
        return listOf(
            String.format(Locale.US, "%.4f", cropLeftNorm.coerceIn(0f, 0.95f)),
            String.format(Locale.US, "%.4f", cropTopNorm.coerceIn(0f, 0.95f)),
            String.format(Locale.US, "%.4f", cropRightNorm.coerceIn(0.05f, 1f)),
            String.format(Locale.US, "%.4f", cropBottomNorm.coerceIn(0.05f, 1f)),
            if (flipHorizontal) "1" else "0",
            if (flipVertical) "1" else "0",
            filterPreset.name,
            aspectCanvasMode.name,
            URLEncoder.encode(watermarkText, Charsets.UTF_8.name()),
            watermarkOpacityPercent.coerceIn(10, 100).toString(),
            URLEncoder.encode(NormalizedRedactionBox.serializeList(redactionBoxes), Charsets.UTF_8.name()),
            String.format(Locale.US, "%.2f", straightenDegrees.coerceIn(-45f, 45f)),
            normalizedRotation90.toString(),
            cropAspectRatioPreset.name,
            URLEncoder.encode(perspectiveQuad.toToken(), Charsets.UTF_8.name()),
            String.format(Locale.US, "%.1f", exposure.coerceIn(-100f, 100f)),
            String.format(Locale.US, "%.1f", brightness.coerceIn(-100f, 100f)),
            String.format(Locale.US, "%.1f", contrast.coerceIn(-100f, 100f)),
            String.format(Locale.US, "%.1f", saturation.coerceIn(-100f, 100f)),
            String.format(Locale.US, "%.1f", temperature.coerceIn(-100f, 100f)),
            String.format(Locale.US, "%.1f", sharpness.coerceIn(0f, 100f)),
            String.format(Locale.US, "%.1f", vignette.coerceIn(0f, 100f)),
            filterIntensityPercent.coerceIn(0, 100).toString(),
            URLEncoder.encode(NormalizedRedactionStroke.serializeList(redactionStrokes), Charsets.UTF_8.name()),
            String.format(Locale.US, "%.4f", watermarkPosXNorm.coerceIn(0f, 1f)),
            String.format(Locale.US, "%.4f", watermarkPosYNorm.coerceIn(0f, 1f)),
            String.format(Locale.US, "%.3f", watermarkScale.coerceIn(0.25f, 4.0f)),
            String.format(Locale.US, "%.2f", watermarkRotationDegrees.coerceIn(-180f, 180f)),
            watermarkColorArgb.toString(),
            if (applyToAllBatch) "1" else "0"
        ).joinToString(separator = "\t")
    }

    companion object {
        fun fromSerializedString(raw: String?): ImageEditSpec {
            if (raw.isNullOrBlank()) return ImageEditSpec()
            val parts = raw.split('\t')
            if (parts.size < 11) return ImageEditSpec()
            val l = parts[0].toFloatOrNull()?.coerceIn(0f, 0.95f) ?: 0f
            val t = parts[1].toFloatOrNull()?.coerceIn(0f, 0.95f) ?: 0f
            val r = (parts[2].toFloatOrNull() ?: 1f).coerceIn(l + 0.05f, 1f)
            val b = (parts[3].toFloatOrNull() ?: 1f).coerceIn(t + 0.05f, 1f)
            val flipH = parts[4] == "1"
            val flipV = parts[5] == "1"
            val filter = ImageFilterPreset.fromNameOrDefault(parts[6])
            val aspect = ImageAspectCanvasMode.fromNameOrDefault(parts[7])
            val wmText = URLDecoder.decode(parts[8], Charsets.UTF_8.name())
            val wmOpacity = parts[9].toIntOrNull()?.coerceIn(10, 100) ?: 75
            val boxes = NormalizedRedactionBox.deserializeList(
                URLDecoder.decode(parts[10], Charsets.UTF_8.name())
            )
            val straighten = parts.getOrNull(11)?.toFloatOrNull()?.coerceIn(-45f, 45f) ?: 0f
            val rot90 = parts.getOrNull(12)?.toIntOrNull() ?: 0
            val cropRatio = CropAspectRatioPreset.fromNameOrDefault(parts.getOrNull(13))
            val quad = PerspectiveQuadSpec.fromToken(
                parts.getOrNull(14)?.let { URLDecoder.decode(it, Charsets.UTF_8.name()) }
            )
            val exp = parts.getOrNull(15)?.toFloatOrNull()?.coerceIn(-100f, 100f) ?: 0f
            val bri = parts.getOrNull(16)?.toFloatOrNull()?.coerceIn(-100f, 100f) ?: 0f
            val con = parts.getOrNull(17)?.toFloatOrNull()?.coerceIn(-100f, 100f) ?: 0f
            val sat = parts.getOrNull(18)?.toFloatOrNull()?.coerceIn(-100f, 100f) ?: 0f
            val tmp = parts.getOrNull(19)?.toFloatOrNull()?.coerceIn(-100f, 100f) ?: 0f
            val shp = parts.getOrNull(20)?.toFloatOrNull()?.coerceIn(0f, 100f) ?: 0f
            val vig = parts.getOrNull(21)?.toFloatOrNull()?.coerceIn(0f, 100f) ?: 0f
            val filterInt = parts.getOrNull(22)?.toIntOrNull()?.coerceIn(0, 100) ?: 100
            val strokes = NormalizedRedactionStroke.deserializeList(
                parts.getOrNull(23)?.let { URLDecoder.decode(it, Charsets.UTF_8.name()) }
            )
            val wmX = parts.getOrNull(24)?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.50f
            val wmY = parts.getOrNull(25)?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.86f
            val wmScale = parts.getOrNull(26)?.toFloatOrNull()?.coerceIn(0.25f, 4.0f) ?: 1.0f
            val wmRot = parts.getOrNull(27)?.toFloatOrNull()?.coerceIn(-180f, 180f) ?: 0f
            val wmColor = parts.getOrNull(28)?.toIntOrNull() ?: 0xFFFFFFFF.toInt()
            val applyAll = parts.getOrNull(29)?.let { it == "1" } ?: true

            return ImageEditSpec(
                cropLeftNorm = l,
                cropTopNorm = t,
                cropRightNorm = r,
                cropBottomNorm = b,
                flipHorizontal = flipH,
                flipVertical = flipV,
                filterPreset = filter,
                aspectCanvasMode = aspect,
                watermarkText = wmText,
                watermarkOpacityPercent = wmOpacity,
                redactionBoxes = boxes,
                straightenDegrees = straighten,
                rotation90Degrees = rot90,
                cropAspectRatioPreset = cropRatio,
                perspectiveQuad = quad,
                exposure = exp,
                brightness = bri,
                contrast = con,
                saturation = sat,
                temperature = tmp,
                sharpness = shp,
                vignette = vig,
                filterIntensityPercent = filterInt,
                redactionStrokes = strokes,
                watermarkPosXNorm = wmX,
                watermarkPosYNorm = wmY,
                watermarkScale = wmScale,
                watermarkRotationDegrees = wmRot,
                watermarkColorArgb = wmColor,
                applyToAllBatch = applyAll
            )
        }
    }
}
