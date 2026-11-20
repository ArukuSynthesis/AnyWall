package com.anywall.converter.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.anywall.converter.domain.model.FormatCategory
import com.anywall.converter.domain.model.SupportedFormat
import com.caverock.androidsvg.SVG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Generador 100% local de miniaturas y vistas previas de páginas PDF con caché LRU en memoria.
 */
class LocalThumbnailLoader(
    private val context: Context,
    private val uriResolver: UriFileResolver
) {
    private val memoryCache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.allocationByteCount
        }
    }

    fun getCachedThumbnail(cacheKey: String): Bitmap? {
        return memoryCache.get(cacheKey)?.takeIf { !it.isRecycled }
    }

    suspend fun loadThumbnail(
        uri: Uri,
        format: SupportedFormat,
        targetSizePx: Int = 96
    ): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = "${uri}_${format.name}_$targetSizePx"
        getCachedThumbnail(cacheKey)?.let { return@withContext it }

        val generated: Bitmap? = try {
            when {
                format == SupportedFormat.SVG -> decodeSvgThumbnail(uri, targetSizePx)
                format.category == FormatCategory.IMAGE -> decodeImageThumbnail(uri, targetSizePx)
                format == SupportedFormat.PDF -> decodePdfPageBitmap(uri, pageIndex = 0, targetSizePx = targetSizePx, rotationDegrees = 0)
                format.isVideo -> decodeVideoFrameThumbnail(uri, targetSizePx)
                else -> null
            }
        } catch (_: Throwable) {
            null
        }

        if (generated != null && !generated.isRecycled) {
            memoryCache.put(cacheKey, generated)
        }
        generated
    }

    /**
     * Devuelve el número total de páginas de un archivo PDF local.
     */
    suspend fun getPdfPageCount(uri: Uri): Int = withContext(Dispatchers.IO) {
        var tempCopy: File? = null
        val pfd: ParcelFileDescriptor? = try {
            context.contentResolver.openFileDescriptor(uri, "r")
        } catch (_: Throwable) {
            try {
                tempCopy = uriResolver.copyUriToTempFile(uri, prefix = "cnt_pdf_", extension = "pdf")
                ParcelFileDescriptor.open(tempCopy, ParcelFileDescriptor.MODE_READ_ONLY)
            } catch (_: Throwable) {
                null
            }
        }

        try {
            pfd?.use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    renderer.pageCount
                }
            } ?: 0
        } catch (_: Throwable) {
            0
        } finally {
            tempCopy?.delete()
        }
    }

    /**
     * Renderiza cualquier página (`pageIndex` en base 0) de un PDF a la resolución solicitada
     * y le aplica la rotación visual indicada (`0`, `90`, `180`, `270`).
     */
    suspend fun loadPdfPagePreview(
        uri: Uri,
        pageIndex: Int,
        targetSizePx: Int = 260,
        rotationDegrees: Int = 0
    ): Bitmap? = withContext(Dispatchers.IO) {
        val normRot = ((rotationDegrees % 360) + 360) % 360
        val cacheKey = "pdf_page_${uri}_p${pageIndex}_s${targetSizePx}_r$normRot"
        getCachedThumbnail(cacheKey)?.let { return@withContext it }

        val rendered = try {
            decodePdfPageBitmap(
                uri = uri,
                pageIndex = pageIndex,
                targetSizePx = targetSizePx,
                rotationDegrees = normRot
            )
        } catch (_: Throwable) {
            null
        }

        if (rendered != null && !rendered.isRecycled) {
            memoryCache.put(cacheKey, rendered)
        }
        rendered
    }

    fun clearCache() {
        memoryCache.evictAll()
    }

    private fun decodeImageThumbnail(uri: Uri, targetSizePx: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val w = info.size.width.coerceAtLeast(1)
                    val h = info.size.height.coerceAtLeast(1)
                    val maxSide = max(w, h)
                    if (maxSide > targetSizePx) {
                        val ratio = targetSizePx.toFloat() / maxSide.toFloat()
                        decoder.setTargetSize(
                            (w * ratio).roundToInt().coerceAtLeast(1),
                            (h * ratio).roundToInt().coerceAtLeast(1)
                        )
                    }
                }
            } catch (_: Throwable) {
            }
        }

        val bytes = uriResolver.openInputStream(uri).use { it.readBytes() }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while ((bounds.outWidth / sampleSize) > targetSizePx * 2 ||
            (bounds.outHeight / sampleSize) > targetSizePx * 2
        ) {
            sampleSize *= 2
        }

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
        return scaleDownBitmap(decoded, targetSizePx)
    }

    private fun decodeSvgThumbnail(uri: Uri, targetSizePx: Int): Bitmap? {
        val svg = uriResolver.openInputStream(uri).use { SVG.getFromInputStream(it) }
        val bmp = Bitmap.createBitmap(targetSizePx, targetSizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        svg.setDocumentWidth(targetSizePx.toFloat())
        svg.setDocumentHeight(targetSizePx.toFloat())
        svg.renderToCanvas(canvas)
        return bmp
    }

    private fun decodePdfPageBitmap(
        uri: Uri,
        pageIndex: Int,
        targetSizePx: Int,
        rotationDegrees: Int
    ): Bitmap? {
        var tempCopy: File? = null
        val pfd: ParcelFileDescriptor? = try {
            context.contentResolver.openFileDescriptor(uri, "r")
        } catch (_: Throwable) {
            tempCopy = uriResolver.copyUriToTempFile(uri, prefix = "thumb_pdf_", extension = "pdf")
            ParcelFileDescriptor.open(tempCopy, ParcelFileDescriptor.MODE_READ_ONLY)
        }

        return try {
            pfd?.use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    if (pageIndex < 0 || pageIndex >= renderer.pageCount) return null
                    renderer.openPage(pageIndex).use { page ->
                        val maxSide = max(page.width, page.height).coerceAtLeast(1)
                        val scale = targetSizePx.toFloat() / maxSide.toFloat()
                        val w = (page.width * scale).roundToInt().coerceAtLeast(1)
                        val h = (page.height * scale).roundToInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bmp)
                        canvas.drawColor(Color.WHITE)
                        page.render(
                            bmp,
                            Rect(0, 0, w, h),
                            null,
                            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                        )
                        val norm = ((rotationDegrees % 360) + 360) % 360
                        if (norm == 0) {
                            bmp
                        } else {
                            val matrix = Matrix().apply { postRotate(norm.toFloat()) }
                            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
                            if (rotated !== bmp) bmp.recycle()
                            rotated
                        }
                    }
                }
            }
        } finally {
            tempCopy?.delete()
        }
    }

    private fun decodeVideoFrameThumbnail(uri: Uri, targetSizePx: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val rawFrame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
                ?: return null
            scaleDownBitmap(rawFrame, targetSizePx)
        } finally {
            try {
                retriever.release()
            } catch (_: Throwable) {
            }
        }
    }

    private fun scaleDownBitmap(source: Bitmap, targetSizePx: Int): Bitmap {
        val maxSide = max(source.width, source.height).coerceAtLeast(1)
        if (maxSide <= targetSizePx) return source
        val ratio = targetSizePx.toFloat() / maxSide.toFloat()
        val w = (source.width * ratio).roundToInt().coerceAtLeast(1)
        val h = (source.height * ratio).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(source, w, h, true)
        if (scaled !== source) {
            source.recycle()
        }
        return scaled
    }
}
