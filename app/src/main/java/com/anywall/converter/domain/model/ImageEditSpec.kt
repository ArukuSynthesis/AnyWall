package com.anywall.converter.domain.model

import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

/**
 * Especificación determinista de edición visual de imágenes (`ImageVisualEditorDialog` - v0.3).
 * Incluye recorte normalizado (`[0..1]`), volteo horizontal/vertical, filtro de color,
 * lienzo de relación de aspecto con fondo blur/sólido, marca de agua de texto y rectángulos de censura.
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
    val watermarkOpacityPercent: Int = 45,
    val redactionBoxes: List<NormalizedRedactionBox> = emptyList()
) {
    val hasCrop: Boolean
        get() = cropLeftNorm > 0.005f || cropTopNorm > 0.005f ||
            cropRightNorm < 0.995f || cropBottomNorm < 0.995f

    val isDefault: Boolean
        get() = !hasCrop &&
            !flipHorizontal &&
            !flipVertical &&
            filterPreset == ImageFilterPreset.NONE &&
            aspectCanvasMode == ImageAspectCanvasMode.ORIGINAL &&
            watermarkText.isBlank() &&
            redactionBoxes.isEmpty()

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
            URLEncoder.encode(NormalizedRedactionBox.serializeList(redactionBoxes), Charsets.UTF_8.name())
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
            val wmOpacity = parts[9].toIntOrNull()?.coerceIn(10, 100) ?: 45
            val boxes = NormalizedRedactionBox.deserializeList(
                URLDecoder.decode(parts[10], Charsets.UTF_8.name())
            )
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
                redactionBoxes = boxes
            )
        }
    }
}
