package com.anywall.converter.data.engines

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.anywall.converter.domain.model.CensorEffectMode
import com.anywall.converter.domain.model.ImageAspectCanvasMode
import com.anywall.converter.domain.model.ImageBatchLayoutMode
import com.anywall.converter.domain.model.ImageEditSpec
import com.anywall.converter.domain.model.ImageFilterPreset
import com.anywall.converter.domain.model.NormalizedRedactionBox
import com.anywall.converter.domain.model.NormalizedRedactionStroke
import com.anywall.converter.domain.model.PerspectiveQuadSpec
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Procesador visual de imágenes de estudio para `ImageEngineImpl`, `ImageVisualEditorDialog` y `PdfVisualEditorDialog` (v0.4):
 * - Escáner / corrección de perspectiva de 4 esquinas con `Matrix.setPolyToPoly`
 * - Rotación `90°`, enderezado continuo (`-45°..+45°`), volteo H/V y recorte gestual
 * - Ajustes fotográficos en vivo a 60 FPS (Exposición, Brillo, Contraste, Saturación, Temperatura, Nitidez y Viñeta)
 * - Filtros con intensidad regulable (`0..100%`)
 * - Censura táctil por pincel libre con el dedo y rectángulos arrastrables (`Pixelado`, `Blur`, `Negro sólido`)
 * - Texto / Marca de agua interactiva posicionable, escalable y rotable con gestos multitáctiles
 * - Composición de Collages (`2x2`, `3x3`) y Tiras (`Vertical` / `Horizontal`)
 */
object ImageVisualTransformHelper {

    fun applyVisualEditSpec(source: Bitmap, spec: ImageEditSpec): Bitmap {
        if (spec.isDefault) return source
        var current = source

        // 1. Escáner / Perspectiva de 4 esquinas con Matrix.setPolyToPoly
        if (spec.perspectiveQuad.isModified) {
            val warped = applyPerspectiveQuad(current, spec.perspectiveQuad)
            if (warped !== current && current !== source) {
                current.recycle()
            }
            current = warped
        }

        // 2. Rotación de 90°, Enderezado (-45°..+45°) y Volteo H/V
        val rot90 = spec.normalizedRotation90
        val straighten = spec.straightenDegrees.coerceIn(-45f, 45f)
        if (rot90 != 0 || abs(straighten) >= 0.1f || spec.flipHorizontal || spec.flipVertical) {
            val transformed = applyRotationStraightenAndFlip(
                source = current,
                rotation90 = rot90,
                straightenDegrees = straighten,
                flipH = spec.flipHorizontal,
                flipV = spec.flipVertical
            )
            if (transformed !== current && current !== source) {
                current.recycle()
            }
            current = transformed
        }

        // 3. Recorte (Crop)
        if (spec.hasCrop) {
            val w = current.width
            val h = current.height
            val left = (spec.cropLeftNorm * w).roundToInt().coerceIn(0, (w - 2).coerceAtLeast(0))
            val top = (spec.cropTopNorm * h).roundToInt().coerceIn(0, (h - 2).coerceAtLeast(0))
            val right = (spec.cropRightNorm * w).roundToInt().coerceIn(left + 1, w)
            val bottom = (spec.cropBottomNorm * h).roundToInt().coerceIn(top + 1, h)
            val cropW = (right - left).coerceAtLeast(1)
            val cropH = (bottom - top).coerceAtLeast(1)
            val cropped = Bitmap.createBitmap(current, left, top, cropW, cropH)
            if (cropped !== current && current !== source) {
                current.recycle()
            }
            current = cropped
        }

        // 4. Ajustes en vivo (Exposición, Brillo, Contraste, Saturación, Temperatura) + Filtro con intensidad
        if (spec.hasColorAdjustments || (spec.filterPreset != ImageFilterPreset.NONE && spec.filterIntensityPercent > 0)) {
            val colorAdjusted = applyColorAdjustmentsAndFilter(current, spec)
            if (colorAdjusted !== current && current !== source) {
                current.recycle()
            }
            current = colorAdjusted
        }

        // 5. Censura táctil: rectángulos arrastrables y trazos de pincel libre (Pixelado, Blur, Negro sólido)
        if (spec.redactionBoxes.isNotEmpty() || spec.redactionStrokes.isNotEmpty()) {
            val redacted = applyRedactionsAndStrokes(current, spec.redactionBoxes, spec.redactionStrokes)
            if (redacted !== current && current !== source) {
                current.recycle()
            }
            current = redacted
        }

        // 6. Ajuste de relación de aspecto con fondo Blur o Sólido
        if (spec.aspectCanvasMode != ImageAspectCanvasMode.ORIGINAL) {
            val framed = applyAspectCanvas(current, spec.aspectCanvasMode)
            if (framed !== current && current !== source) {
                current.recycle()
            }
            current = framed
        }

        // 7. Texto / Marca de agua interactiva (posición, escala, rotación, color y opacidad)
        if (spec.watermarkText.isNotBlank()) {
            val watermarked = applyInteractiveTextWatermark(
                source = current,
                text = spec.watermarkText.trim(),
                posXNorm = spec.watermarkPosXNorm,
                posYNorm = spec.watermarkPosYNorm,
                scale = spec.watermarkScale,
                rotationDegrees = spec.watermarkRotationDegrees,
                colorArgb = spec.watermarkColorArgb,
                opacityPercent = spec.watermarkOpacityPercent
            )
            if (watermarked !== current && current !== source) {
                current.recycle()
            }
            current = watermarked
        }

        return current
    }

    /**
     * Aplica corrección de perspectiva de 4 esquinas usando `Matrix.setPolyToPoly`.
     */
    fun applyPerspectiveQuad(source: Bitmap, quad: PerspectiveQuadSpec): Bitmap {
        if (!quad.isModified) return source
        val w = source.width.toFloat().coerceAtLeast(1f)
        val h = source.height.toFloat().coerceAtLeast(1f)

        val srcPts = floatArrayOf(
            quad.tlX * w, quad.tlY * h,
            quad.trX * w, quad.trY * h,
            quad.brX * w, quad.brY * h,
            quad.blX * w, quad.blY * h
        )

        val topWidth = hypot((srcPts[2] - srcPts[0]).toDouble(), (srcPts[3] - srcPts[1]).toDouble())
        val bottomWidth = hypot((srcPts[4] - srcPts[6]).toDouble(), (srcPts[5] - srcPts[7]).toDouble())
        val leftHeight = hypot((srcPts[6] - srcPts[0]).toDouble(), (srcPts[7] - srcPts[1]).toDouble())
        val rightHeight = hypot((srcPts[4] - srcPts[2]).toDouble(), (srcPts[5] - srcPts[3]).toDouble())

        val dstW = max(topWidth, bottomWidth).roundToInt().coerceIn(32, source.width * 2)
        val dstH = max(leftHeight, rightHeight).roundToInt().coerceIn(32, source.height * 2)

        val dstPts = floatArrayOf(
            0f, 0f,
            dstW.toFloat(), 0f,
            dstW.toFloat(), dstH.toFloat(),
            0f, dstH.toFloat()
        )

        val matrix = Matrix()
        val success = matrix.setPolyToPoly(srcPts, 0, dstPts, 0, 4)
        if (!success) return source

        val out = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, matrix, paint)
        return out
    }

    private fun applyRotationStraightenAndFlip(
        source: Bitmap,
        rotation90: Int,
        straightenDegrees: Float,
        flipH: Boolean,
        flipV: Boolean
    ): Bitmap {
        var current = source
        if (rotation90 != 0 || flipH || flipV) {
            val m90 = Matrix().apply {
                if (flipH || flipV) {
                    postScale(
                        if (flipH) -1f else 1f,
                        if (flipV) -1f else 1f,
                        current.width / 2f,
                        current.height / 2f
                    )
                }
                if (rotation90 != 0) {
                    postRotate(rotation90.toFloat())
                }
            }
            val rotated = Bitmap.createBitmap(current, 0, 0, current.width, current.height, m90, true)
            if (rotated !== current && current !== source) {
                current.recycle()
            }
            current = rotated
        }

        if (abs(straightenDegrees) >= 0.1f) {
            val w = current.width.toFloat()
            val h = current.height.toFloat()
            val rad = Math.toRadians(abs(straightenDegrees).toDouble())
            val aspect = max(w / h.coerceAtLeast(1f), h / w.coerceAtLeast(1f)).toDouble()
            val zoom = (cos(rad) + sin(rad) * aspect).toFloat().coerceIn(1.0f, 2.2f)

            val out = Bitmap.createBitmap(current.width, current.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val m = Matrix().apply {
                postTranslate(-w / 2f, -h / 2f)
                postRotate(straightenDegrees)
                postScale(zoom, zoom)
                postTranslate(w / 2f, h / 2f)
            }
            canvas.drawBitmap(current, m, paint)
            if (current !== source) {
                current.recycle()
            }
            current = out
        }

        return current
    }

    fun applyColorAdjustmentsAndFilter(source: Bitmap, spec: ImageEditSpec): Bitmap {
        val w = source.width
        val h = source.height
        var working = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(working)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val combinedMatrix = buildCombinedColorMatrix(
            exposure = spec.exposure,
            brightness = spec.brightness,
            contrast = spec.contrast,
            saturation = spec.saturation,
            temperature = spec.temperature,
            filterPreset = spec.filterPreset,
            filterIntensityPercent = spec.filterIntensityPercent
        )
        paint.colorFilter = ColorMatrixColorFilter(combinedMatrix)
        canvas.drawBitmap(source, 0f, 0f, paint)

        // Nitidez (Sharpness 0..100)
        if (spec.sharpness > 1f) {
            val sharpened = applyFastSharpen(working, (spec.sharpness / 100f).coerceIn(0f, 1f))
            if (sharpened !== working) {
                working.recycle()
                working = sharpened
            }
        }

        // Viñeta (Vignette 0..100)
        if (spec.vignette > 1f) {
            val vigCanvas = Canvas(working)
            val cx = w / 2f
            val cy = h / 2f
            val radius = hypot(cx.toDouble(), cy.toDouble()).toFloat().coerceAtLeast(1f)
            val maxAlpha = ((spec.vignette.coerceIn(0f, 100f) / 100f) * 215f).roundToInt().coerceIn(0, 230)
            val radial = RadialGradient(
                cx,
                cy,
                radius,
                intArrayOf(
                    Color.TRANSPARENT,
                    Color.argb((maxAlpha * 0.35f).roundToInt(), 0, 0, 0),
                    Color.argb(maxAlpha, 0, 0, 0)
                ),
                floatArrayOf(0.35f, 0.72f, 1.0f),
                Shader.TileMode.CLAMP
            )
            val vigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = radial
            }
            vigCanvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), vigPaint)
        }

        return working
    }

    fun buildCombinedColorMatrix(
        exposure: Float,
        brightness: Float,
        contrast: Float,
        saturation: Float,
        temperature: Float,
        filterPreset: ImageFilterPreset,
        filterIntensityPercent: Int
    ): ColorMatrix {
        val master = ColorMatrix()

        // 1. Exposición (-100..+100 -> factor 2^(exp/65))
        if (abs(exposure) > 0.5f) {
            val expGain = 2.0.pow((exposure.coerceIn(-100f, 100f) / 65.0)).toFloat()
            master.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        expGain, 0f, 0f, 0f, 0f,
                        0f, expGain, 0f, 0f, 0f,
                        0f, 0f, expGain, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }

        // 2. Brillo (-100..+100) y Contraste (-100..+100)
        if (abs(brightness) > 0.5f || abs(contrast) > 0.5f) {
            val c = (1f + (contrast.coerceIn(-100f, 100f) / 100f) * 0.85f).coerceAtLeast(0.15f)
            val bOffset = brightness.coerceIn(-100f, 100f) * 1.15f + 128f * (1f - c)
            master.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        c, 0f, 0f, 0f, bOffset,
                        0f, c, 0f, 0f, bOffset,
                        0f, 0f, c, 0f, bOffset,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }

        // 3. Saturación (-100..+100 -> 0.0..2.0)
        if (abs(saturation) > 0.5f) {
            val satValue = (1f + saturation.coerceIn(-100f, 100f) / 100f).coerceIn(0f, 2.5f)
            val satMatrix = ColorMatrix().apply { setSaturation(satValue) }
            master.postConcat(satMatrix)
        }

        // 4. Temperatura (-100 frío .. +100 cálido)
        if (abs(temperature) > 0.5f) {
            val t = temperature.coerceIn(-100f, 100f) / 100f
            val rBoost = t * 28f
            val gBoost = t * 6f
            val bBoost = -t * 28f
            master.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        1f, 0f, 0f, 0f, rBoost,
                        0f, 1f, 0f, 0f, gBoost,
                        0f, 0f, 1f, 0f, bBoost,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }

        // 5. Filtro con intensidad (0..100%)
        val intensity = (filterIntensityPercent.coerceIn(0, 100) / 100f)
        if (filterPreset != ImageFilterPreset.NONE && intensity > 0.005f) {
            val targetFilterMatrix = buildPresetMatrix(filterPreset)
            val blendedFilter = blendColorMatrixWithIdentity(targetFilterMatrix, intensity)
            master.postConcat(blendedFilter)
        }

        return master
    }

    private fun buildPresetMatrix(filter: ImageFilterPreset): ColorMatrix {
        return when (filter) {
            ImageFilterPreset.GRAYSCALE -> ColorMatrix().apply { setSaturation(0f) }
            ImageFilterPreset.DOCUMENT_BW_HIGH_CONTRAST -> {
                val gray = ColorMatrix().apply { setSaturation(0f) }
                val contrast = 2.15f
                val translate = -165f
                val hc = ColorMatrix(
                    floatArrayOf(
                        contrast, 0f, 0f, 0f, translate,
                        0f, contrast, 0f, 0f, translate,
                        0f, 0f, contrast, 0f, translate,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                gray.postConcat(hc)
                gray
            }
            ImageFilterPreset.WARM_VINTAGE -> ColorMatrix(
                floatArrayOf(
                    1.12f, 0.06f, 0f, 0f, 12f,
                    0.02f, 1.04f, 0f, 0f, 6f,
                    0f, 0f, 0.88f, 0f, -10f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            ImageFilterPreset.COOL_CINEMA -> ColorMatrix(
                floatArrayOf(
                    0.92f, 0f, 0.04f, 0f, -6f,
                    0f, 1.02f, 0.04f, 0f, 2f,
                    0.04f, 0.06f, 1.16f, 0f, 14f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            ImageFilterPreset.SEPIA -> ColorMatrix(
                floatArrayOf(
                    0.393f, 0.769f, 0.189f, 0f, 0f,
                    0.349f, 0.686f, 0.168f, 0f, 0f,
                    0.272f, 0.534f, 0.131f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            ImageFilterPreset.VIVID -> {
                val sat = ColorMatrix().apply { setSaturation(1.42f) }
                val c = 1.14f
                val t = 128f * (1f - c)
                sat.postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            c, 0f, 0f, 0f, t,
                            0f, c, 0f, 0f, t,
                            0f, 0f, c, 0f, t,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
                sat
            }
            ImageFilterPreset.INVERT -> ColorMatrix(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            ImageFilterPreset.NONE -> ColorMatrix()
        }
    }

    private fun blendColorMatrixWithIdentity(target: ColorMatrix, amount: Float): ColorMatrix {
        val a = amount.coerceIn(0f, 1f)
        if (a >= 0.999f) return target
        val identity = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
        val tArr = target.array
        val out = FloatArray(20) { idx ->
            identity[idx] * (1f - a) + tArr[idx] * a
        }
        return ColorMatrix(out)
    }

    fun applyFilterPreset(source: Bitmap, filter: ImageFilterPreset, intensityPercent: Int = 100): Bitmap {
        if (filter == ImageFilterPreset.NONE || intensityPercent <= 0) return source
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cm = blendColorMatrixWithIdentity(buildPresetMatrix(filter), intensityPercent / 100f)
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return out
    }

    private fun applyFastSharpen(source: Bitmap, strength: Float): Bitmap {
        val w = source.width
        val h = source.height
        if (w < 4 || h < 4) return source
        val blurred = createQuickDownscaledBlur(source, downscaleFactor = 3)
        val srcPx = IntArray(w * h)
        val blurPx = IntArray(w * h)
        source.getPixels(srcPx, 0, w, 0, 0, w, h)
        blurred.getPixels(blurPx, 0, w, 0, 0, w, h)
        blurred.recycle()

        val amount = (strength * 1.35f).coerceIn(0f, 1.5f)
        for (i in srcPx.indices) {
            val sc = srcPx[i]
            val bc = blurPx[i]
            val a = sc and 0xFF000000.toInt()
            val sr = (sc shr 16) and 0xFF
            val sg = (sc shr 8) and 0xFF
            val sb = sc and 0xFF

            val br = (bc shr 16) and 0xFF
            val bg = (bc shr 8) and 0xFF
            val bb = bc and 0xFF

            val nr = (sr + ((sr - br) * amount).roundToInt()).coerceIn(0, 255)
            val ng = (sg + ((sg - bg) * amount).roundToInt()).coerceIn(0, 255)
            val nb = (sb + ((sb - bb) * amount).roundToInt()).coerceIn(0, 255)
            srcPx[i] = a or (nr shl 16) or (ng shl 8) or nb
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(srcPx, 0, w, 0, 0, w, h)
        }
    }

    private fun createQuickDownscaledBlur(source: Bitmap, downscaleFactor: Int): Bitmap {
        val w = source.width
        val h = source.height
        val sw = (w / downscaleFactor).coerceAtLeast(8)
        val sh = (h / downscaleFactor).coerceAtLeast(8)
        val small = Bitmap.createScaledBitmap(source, sw, sh, true)
        val blurredSmall = fastBoxBlur(small, radius = 2)
        if (blurredSmall !== small) small.recycle()
        val upscaled = Bitmap.createScaledBitmap(blurredSmall, w, h, true)
        if (upscaled !== blurredSmall) blurredSmall.recycle()
        return upscaled
    }

    private fun createMosaicBitmap(source: Bitmap, blockSize: Int): Bitmap {
        val w = source.width
        val h = source.height
        val step = blockSize.coerceAtLeast(6)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val p = Paint()
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val sx = min(x + step / 2, w - 1)
                val sy = min(y + step / 2, h - 1)
                p.color = source.getPixel(sx, sy)
                canvas.drawRect(
                    x.toFloat(),
                    y.toFloat(),
                    min(x + step, w).toFloat(),
                    min(y + step, h).toFloat(),
                    p
                )
                x += step
            }
            y += step
        }
        return out
    }

    fun applyRedactionsAndStrokes(
        source: Bitmap,
        boxes: List<NormalizedRedactionBox>,
        strokes: List<NormalizedRedactionStroke>
    ): Bitmap {
        if (boxes.isEmpty() && strokes.isEmpty()) return source
        val mutable = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutable)
        val w = mutable.width
        val h = mutable.height
        val blockSize = (max(w, h) / 34).coerceAtLeast(8)

        val needMosaic = boxes.any { it.effectMode == CensorEffectMode.PIXELATE } ||
            strokes.any { it.effectMode == CensorEffectMode.PIXELATE }
        val needBlur = boxes.any { it.effectMode == CensorEffectMode.BLUR } ||
            strokes.any { it.effectMode == CensorEffectMode.BLUR }

        val mosaicBmp = if (needMosaic) createMosaicBitmap(source, blockSize) else null
        val blurredBmp = if (needBlur) createQuickDownscaledBlur(source, downscaleFactor = 10) else null

        val blackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }

        try {
            // 1. Rectángulos de censura
            for (box in boxes) {
                val left = (box.clampedLeft * w).coerceIn(0f, (w - 1).toFloat())
                val top = (box.clampedTop * h).coerceIn(0f, (h - 1).toFloat())
                val right = (box.clampedRight * w).coerceIn(left + 1f, w.toFloat())
                val bottom = (box.clampedBottom * h).coerceIn(top + 1f, h.toFloat())
                val rect = RectF(left, top, right, bottom)

                when (box.effectMode) {
                    CensorEffectMode.SOLID_BLACK -> {
                        canvas.drawRect(rect, blackPaint)
                    }
                    CensorEffectMode.PIXELATE -> {
                        if (mosaicBmp != null) {
                            val save = canvas.save()
                            canvas.clipRect(rect)
                            canvas.drawBitmap(mosaicBmp, 0f, 0f, null)
                            canvas.restoreToCount(save)
                        }
                    }
                    CensorEffectMode.BLUR -> {
                        if (blurredBmp != null) {
                            val save = canvas.save()
                            canvas.clipRect(rect)
                            canvas.drawBitmap(blurredBmp, 0f, 0f, null)
                            canvas.restoreToCount(save)
                        }
                    }
                }
            }

            // 2. Trazos de pincel libre con el dedo
            for (stroke in strokes) {
                if (stroke.points.isEmpty()) continue
                val strokePx = (stroke.strokeWidthNorm * min(w, h)).coerceAtLeast(10f)
                val path = Path()
                stroke.points.forEachIndexed { idx, (nx, ny) ->
                    val px = nx * w
                    val py = ny * h
                    if (idx == 0) {
                        path.moveTo(px, py)
                    } else {
                        path.lineTo(px, py)
                    }
                }
                if (stroke.points.size == 1) {
                    val (nx, ny) = stroke.points.first()
                    path.addCircle(nx * w, ny * h, strokePx / 2f, Path.Direction.CW)
                }

                when (stroke.effectMode) {
                    CensorEffectMode.SOLID_BLACK -> {
                        val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.BLACK
                            style = Paint.Style.STROKE
                            strokeWidth = strokePx
                            strokeCap = Paint.Cap.ROUND
                            strokeJoin = Paint.Join.ROUND
                        }
                        canvas.drawPath(path, sp)
                    }
                    CensorEffectMode.PIXELATE -> {
                        if (mosaicBmp != null) {
                            drawEffectAlongStroke(canvas, mosaicBmp, stroke, w, h, strokePx)
                        }
                    }
                    CensorEffectMode.BLUR -> {
                        if (blurredBmp != null) {
                            drawEffectAlongStroke(canvas, blurredBmp, stroke, w, h, strokePx)
                        }
                    }
                }
            }
        } finally {
            mosaicBmp?.recycle()
            blurredBmp?.recycle()
        }

        return mutable
    }

    private fun drawEffectAlongStroke(
        canvas: Canvas,
        effectBitmap: Bitmap,
        stroke: NormalizedRedactionStroke,
        w: Int,
        h: Int,
        strokePx: Float
    ) {
        val radius = strokePx / 2f
        val clipPath = Path()
        for (i in stroke.points.indices) {
            val (nx, ny) = stroke.points[i]
            val px = nx * w
            val py = ny * h
            clipPath.addCircle(px, py, radius, Path.Direction.CW)
            if (i > 0) {
                val (prevX, prevY) = stroke.points[i - 1]
                val ppx = prevX * w
                val ppy = prevY * h
                val steps = (hypot((px - ppx).toDouble(), (py - ppy).toDouble()) / (radius * 0.5f))
                    .roundToInt().coerceIn(1, 32)
                for (s in 1 until steps) {
                    val f = s.toFloat() / steps.toFloat()
                    clipPath.addCircle(ppx + (px - ppx) * f, ppy + (py - ppy) * f, radius, Path.Direction.CW)
                }
            }
        }
        val save = canvas.save()
        canvas.clipPath(clipPath)
        canvas.drawBitmap(effectBitmap, 0f, 0f, null)
        canvas.restoreToCount(save)
    }

    fun applyRedactionBoxes(source: Bitmap, boxes: List<NormalizedRedactionBox>): Bitmap {
        return applyRedactionsAndStrokes(source, boxes, emptyList())
    }

    fun applyAspectCanvas(source: Bitmap, mode: ImageAspectCanvasMode): Bitmap {
        if (mode == ImageAspectCanvasMode.ORIGINAL || mode.ratioWidth <= 0 || mode.ratioHeight <= 0) {
            return source
        }
        val srcW = source.width.coerceAtLeast(1)
        val srcH = source.height.coerceAtLeast(1)
        val targetRatio = mode.ratioWidth.toFloat() / mode.ratioHeight.toFloat()
        val srcRatio = srcW.toFloat() / srcH.toFloat()

        val canvasW: Int
        val canvasH: Int
        if (srcRatio > targetRatio) {
            canvasW = srcW
            canvasH = (srcW / targetRatio).roundToInt().coerceAtLeast(1)
        } else {
            canvasH = srcH
            canvasW = (srcH * targetRatio).roundToInt().coerceAtLeast(1)
        }

        val out = Bitmap.createBitmap(canvasW, canvasH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        if (mode.useBlur) {
            val smallW = (canvasW / 16).coerceIn(16, 128)
            val smallH = (canvasH / 16).coerceIn(16, 128)
            val small = Bitmap.createScaledBitmap(source, smallW, smallH, true)
            val blurredSmall = fastBoxBlur(small, radius = 3)
            if (blurredSmall !== small) small.recycle()
            canvas.drawBitmap(
                blurredSmall,
                Rect(0, 0, blurredSmall.width, blurredSmall.height),
                Rect(0, 0, canvasW, canvasH),
                paint
            )
            blurredSmall.recycle()
            canvas.drawColor(Color.argb(45, 0, 0, 0))
        } else {
            canvas.drawColor(Color.WHITE)
        }

        val left = (canvasW - srcW) / 2f
        val top = (canvasH - srcH) / 2f
        canvas.drawBitmap(source, left, top, paint)
        return out
    }

    fun applyInteractiveTextWatermark(
        source: Bitmap,
        text: String,
        posXNorm: Float,
        posYNorm: Float,
        scale: Float,
        rotationDegrees: Float,
        colorArgb: Int,
        opacityPercent: Int
    ): Bitmap {
        if (text.isBlank()) return source
        val mutable = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutable)
        val w = mutable.width.toFloat()
        val h = mutable.height.toFloat()
        val alpha = ((opacityPercent.coerceIn(10, 100) * 255) / 100).coerceIn(25, 255)
        val baseSizePx = (min(w, h) * 0.055f * scale.coerceIn(0.25f, 4.0f)).coerceIn(14f, 240f)

        val r = (colorArgb shr 16) and 0xFF
        val g = (colorArgb shr 8) and 0xFF
        val b = colorArgb and 0xFF

        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((alpha * 0.78f).roundToInt(), 0, 0, 0)
            textSize = baseSizePx
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(alpha, r, g, b)
            textSize = baseSizePx
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val cx = (posXNorm.coerceIn(0.02f, 0.98f) * w)
        val cy = (posYNorm.coerceIn(0.04f, 0.96f) * h)

        val save = canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(rotationDegrees)
        canvas.drawText(text, 2f, 2f, shadowPaint)
        canvas.drawText(text, 0f, 0f, textPaint)
        canvas.restoreToCount(save)
        return mutable
    }

    fun composeBatchCollageOrStitch(
        bitmaps: List<Bitmap>,
        layoutMode: ImageBatchLayoutMode,
        maxCanvasSidePx: Int = 2400
    ): Bitmap {
        require(bitmaps.isNotEmpty()) { "Se requiere al menos una imagen para componer un collage o tira." }
        if (bitmaps.size == 1) {
            return bitmaps.first().copy(Bitmap.Config.ARGB_8888, false)
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        return when (layoutMode) {
            ImageBatchLayoutMode.COLLAGE_GRID_2X2,
            ImageBatchLayoutMode.COLLAGE_GRID_3X3 -> {
                val cols = if (layoutMode == ImageBatchLayoutMode.COLLAGE_GRID_3X3) 3 else 2
                val rows = cols
                val canvasSide = maxCanvasSidePx.coerceIn(600, 3200)
                val gap = (canvasSide * 0.012f).roundToInt().coerceAtLeast(8)
                val cellSize = (canvasSide - gap * (cols + 1)) / cols
                val out = Bitmap.createBitmap(canvasSide, canvasSide, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(out)
                canvas.drawColor(Color.WHITE)

                val maxCells = cols * rows
                bitmaps.take(maxCells).forEachIndexed { index, bmp ->
                    val r = index / cols
                    val c = index % cols
                    val dstLeft = gap + c * (cellSize + gap)
                    val dstTop = gap + r * (cellSize + gap)
                    val dstRect = RectF(
                        dstLeft.toFloat(),
                        dstTop.toFloat(),
                        (dstLeft + cellSize).toFloat(),
                        (dstTop + cellSize).toFloat()
                    )
                    drawCenterCropBitmap(canvas, bmp, dstRect, paint)
                }
                out
            }

            ImageBatchLayoutMode.STITCH_VERTICAL -> {
                val targetWidth = bitmaps.maxOf { it.width }.coerceIn(256, maxCanvasSidePx)
                val gap = 12
                val scaledHeights = bitmaps.map { bmp ->
                    ((bmp.height.toFloat() * targetWidth.toFloat()) / bmp.width.coerceAtLeast(1).toFloat())
                        .roundToInt().coerceAtLeast(1)
                }
                val totalHeight = (scaledHeights.sum() + gap * (bitmaps.size - 1)).coerceAtMost(8192)
                val heightScale = if (scaledHeights.sum() + gap * (bitmaps.size - 1) > 8192) {
                    8192f / (scaledHeights.sum() + gap * (bitmaps.size - 1)).toFloat()
                } else {
                    1f
                }
                val finalWidth = (targetWidth * heightScale).roundToInt().coerceAtLeast(1)
                val out = Bitmap.createBitmap(finalWidth, totalHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(out)
                canvas.drawColor(Color.WHITE)

                var currentY = 0
                bitmaps.forEachIndexed { idx, bmp ->
                    val drawH = (scaledHeights[idx] * heightScale).roundToInt().coerceAtLeast(1)
                    val dstRect = Rect(0, currentY, finalWidth, (currentY + drawH).coerceAtMost(totalHeight))
                    canvas.drawBitmap(bmp, Rect(0, 0, bmp.width, bmp.height), dstRect, paint)
                    currentY += drawH + (gap * heightScale).roundToInt()
                }
                out
            }

            ImageBatchLayoutMode.STITCH_HORIZONTAL -> {
                val targetHeight = bitmaps.maxOf { it.height }.coerceIn(256, maxCanvasSidePx)
                val gap = 12
                val scaledWidths = bitmaps.map { bmp ->
                    ((bmp.width.toFloat() * targetHeight.toFloat()) / bmp.height.coerceAtLeast(1).toFloat())
                        .roundToInt().coerceAtLeast(1)
                }
                val totalWidth = (scaledWidths.sum() + gap * (bitmaps.size - 1)).coerceAtMost(8192)
                val widthScale = if (scaledWidths.sum() + gap * (bitmaps.size - 1) > 8192) {
                    8192f / (scaledWidths.sum() + gap * (bitmaps.size - 1)).toFloat()
                } else {
                    1f
                }
                val finalHeight = (targetHeight * widthScale).roundToInt().coerceAtLeast(1)
                val out = Bitmap.createBitmap(totalWidth, finalHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(out)
                canvas.drawColor(Color.WHITE)

                var currentX = 0
                bitmaps.forEachIndexed { idx, bmp ->
                    val drawW = (scaledWidths[idx] * widthScale).roundToInt().coerceAtLeast(1)
                    val dstRect = Rect(currentX, 0, (currentX + drawW).coerceAtMost(totalWidth), finalHeight)
                    canvas.drawBitmap(bmp, Rect(0, 0, bmp.width, bmp.height), dstRect, paint)
                    currentX += drawW + (gap * widthScale).roundToInt()
                }
                out
            }

            ImageBatchLayoutMode.INDIVIDUAL -> bitmaps.first().copy(Bitmap.Config.ARGB_8888, false)
        }
    }

    private fun drawCenterCropBitmap(canvas: Canvas, bmp: Bitmap, dst: RectF, paint: Paint) {
        val srcW = bmp.width.toFloat()
        val srcH = bmp.height.toFloat()
        val dstW = dst.width()
        val dstH = dst.height()
        val scale = max(dstW / srcW, dstH / srcH)
        val scaledW = srcW * scale
        val scaledH = srcH * scale
        val dx = dst.left + (dstW - scaledW) / 2f
        val dy = dst.top + (dstH - scaledH) / 2f

        val saveCount = canvas.save()
        canvas.clipRect(dst)
        canvas.drawBitmap(
            bmp,
            Rect(0, 0, bmp.width, bmp.height),
            RectF(dx, dy, dx + scaledW, dy + scaledH),
            paint
        )
        canvas.restoreToCount(saveCount)
    }

    private fun fastBoxBlur(source: Bitmap, radius: Int): Bitmap {
        val w = source.width
        val h = source.height
        val srcPixels = IntArray(w * h)
        val dstPixels = IntArray(w * h)
        source.getPixels(srcPixels, 0, w, 0, 0, w, h)

        for (y in 0 until h) {
            for (x in 0 until w) {
                var rSum = 0
                var gSum = 0
                var bSum = 0
                var count = 0
                for (dy in -radius..radius) {
                    val ny = (y + dy).coerceIn(0, h - 1)
                    for (dx in -radius..radius) {
                        val nx = (x + dx).coerceIn(0, w - 1)
                        val c = srcPixels[ny * w + nx]
                        rSum += (c shr 16) and 0xFF
                        gSum += (c shr 8) and 0xFF
                        bSum += c and 0xFF
                        count++
                    }
                }
                dstPixels[y * w + x] = (0xFF shl 24) or
                    ((rSum / count) shl 16) or
                    ((gSum / count) shl 8) or
                    (bSum / count)
            }
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(dstPixels, 0, w, 0, 0, w, h)
        }
    }
}
