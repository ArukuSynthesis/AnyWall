package com.anywall.converter.data.engines

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import com.anywall.converter.domain.model.ImageAspectCanvasMode
import com.anywall.converter.domain.model.ImageBatchLayoutMode
import com.anywall.converter.domain.model.ImageEditSpec
import com.anywall.converter.domain.model.ImageFilterPreset
import com.anywall.converter.domain.model.NormalizedRedactionBox
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Procesador visual 100% local de imágenes para `ImageEngineImpl` y `ImageVisualEditorDialog` (v0.3):
 * - Recorte normalizado (`cropLeftNorm..cropRightNorm`, `cropTopNorm..cropBottomNorm`)
 * - Volteo horizontal / vertical
 * - Filtros de color (`GRAYSCALE`, `DOCUMENT_BW_HIGH_CONTRAST`, `INVERT`)
 * - Censura / Pixelado por bloques sobre rectángulos normalizados
 * - Lienzo de relación de aspecto con fondo difuminado (*Blur*) o sólido
 * - Marca de agua de texto con opacidad configurable
 * - Composición de Collages (`2x2`, `3x3`) y Tiras (`Vertical` / `Horizontal`)
 */
object ImageVisualTransformHelper {

    fun applyVisualEditSpec(source: Bitmap, spec: ImageEditSpec): Bitmap {
        if (spec.isDefault) return source
        var current = source

        // 1. Recorte (Crop)
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

        // 2. Volteo horizontal / vertical
        if (spec.flipHorizontal || spec.flipVertical) {
            val matrix = Matrix().apply {
                postScale(
                    if (spec.flipHorizontal) -1f else 1f,
                    if (spec.flipVertical) -1f else 1f,
                    current.width / 2f,
                    current.height / 2f
                )
            }
            val flipped = Bitmap.createBitmap(current, 0, 0, current.width, current.height, matrix, true)
            if (flipped !== current && current !== source) {
                current.recycle()
            }
            current = flipped
        }

        // 3. Filtros de color
        if (spec.filterPreset != ImageFilterPreset.NONE) {
            val filtered = applyFilterPreset(current, spec.filterPreset)
            if (filtered !== current && current !== source) {
                current.recycle()
            }
            current = filtered
        }

        // 4. Rectángulos de censura / pixelado
        if (spec.redactionBoxes.isNotEmpty()) {
            val redacted = applyRedactionBoxes(current, spec.redactionBoxes)
            if (redacted !== current && current !== source) {
                current.recycle()
            }
            current = redacted
        }

        // 5. Ajuste de relación de aspecto con fondo Blur o Sólido
        if (spec.aspectCanvasMode != ImageAspectCanvasMode.ORIGINAL) {
            val framed = applyAspectCanvas(current, spec.aspectCanvasMode)
            if (framed !== current && current !== source) {
                current.recycle()
            }
            current = framed
        }

        // 6. Marca de agua de texto
        if (spec.watermarkText.isNotBlank()) {
            val watermarked = applyTextWatermark(
                source = current,
                text = spec.watermarkText.trim(),
                opacityPercent = spec.watermarkOpacityPercent
            )
            if (watermarked !== current && current !== source) {
                current.recycle()
            }
            current = watermarked
        }

        return current
    }

    fun applyFilterPreset(source: Bitmap, filter: ImageFilterPreset): Bitmap {
        if (filter == ImageFilterPreset.NONE) return source
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val colorMatrix = when (filter) {
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

        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return out
    }

    fun applyRedactionBoxes(source: Bitmap, boxes: List<NormalizedRedactionBox>): Bitmap {
        if (boxes.isEmpty()) return source
        val mutable = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutable)
        val blackPaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }
        val pixelPaint = Paint()
        val w = mutable.width
        val h = mutable.height

        for (box in boxes) {
            val left = (box.clampedLeft * w).roundToInt().coerceIn(0, w - 1)
            val top = (box.clampedTop * h).roundToInt().coerceIn(0, h - 1)
            val right = (box.clampedRight * w).roundToInt().coerceIn(left + 1, w)
            val bottom = (box.clampedBottom * h).roundToInt().coerceIn(top + 1, h)

            if (!box.isPixelated) {
                canvas.drawRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), blackPaint)
            } else {
                val blockSize = (max(w, h) / 36).coerceAtLeast(8)
                var y = top
                while (y < bottom) {
                    var x = left
                    while (x < right) {
                        val sampleX = min(x + blockSize / 2, right - 1)
                        val sampleY = min(y + blockSize / 2, bottom - 1)
                        pixelPaint.color = mutable.getPixel(sampleX, sampleY)
                        canvas.drawRect(
                            x.toFloat(),
                            y.toFloat(),
                            min(x + blockSize, right).toFloat(),
                            min(y + blockSize, bottom).toFloat(),
                            pixelPaint
                        )
                        x += blockSize
                    }
                    y += blockSize
                }
            }
        }
        return mutable
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
            // Crear fondo difuminado rápido (downscale -> box blur -> upscale)
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
            // Velo oscuro sutil para resaltar la imagen central
            canvas.drawColor(Color.argb(45, 0, 0, 0))
        } else {
            canvas.drawColor(Color.WHITE)
        }

        val left = (canvasW - srcW) / 2f
        val top = (canvasH - srcH) / 2f
        canvas.drawBitmap(source, left, top, paint)
        return out
    }

    fun applyTextWatermark(source: Bitmap, text: String, opacityPercent: Int): Bitmap {
        if (text.isBlank()) return source
        val mutable = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutable)
        val w = mutable.width.toFloat()
        val h = mutable.height.toFloat()
        val alpha = ((opacityPercent.coerceIn(10, 100) * 255) / 100).coerceIn(25, 255)
        val textSizePx = (min(w, h) * 0.055f).coerceIn(18f, 120f)

        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((alpha * 0.75f).roundToInt(), 0, 0, 0)
            textSize = textSizePx
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(alpha, 255, 255, 255)
            textSize = textSizePx
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }

        val margin = (min(w, h) * 0.035f).coerceAtLeast(12f)
        val x = w - margin
        val y = h - margin
        canvas.drawText(text, x + 2f, y + 2f, shadowPaint)
        canvas.drawText(text, x, y, textPaint)
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
