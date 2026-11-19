package com.anywall.converter.data.engines

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import com.anywall.converter.core.ScopedStorageManager
import com.anywall.converter.core.UriFileResolver
import com.anywall.converter.domain.engine.FileConverterEngine
import com.anywall.converter.domain.model.ConversionJobResult
import com.anywall.converter.domain.model.ConversionOptions
import com.anywall.converter.domain.model.FormatCategory
import com.anywall.converter.domain.model.InputFileItem
import com.anywall.converter.domain.model.SupportedFormat
import com.caverock.androidsvg.SVG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Motor 1: `ImageEngineImpl` (Imágenes y PDF básico)
 * - Conversión bidireccional entre JPG, PNG, WEBP y BMP respetando calidad (10% - 100%) y redimensionado máximo.
 * - Renderizado de SVG a PNG, JPG, WEBP, BMP o PDF usando `com.caverock.androidsvg.SVG`.
 * - Conversión de una o múltiples imágenes a un único archivo PDF mediante `android.graphics.pdf.PdfDocument`.
 * - Extracción de páginas de un PDF a imágenes individuales (JPG o PNG) mediante `android.graphics.pdf.PdfRenderer`.
 */
class ImageEngineImpl(
    private val context: Context,
    private val uriResolver: UriFileResolver,
    private val storageManager: ScopedStorageManager
) : FileConverterEngine {

    private val rasterFormats = setOf(
        SupportedFormat.JPG,
        SupportedFormat.PNG,
        SupportedFormat.WEBP,
        SupportedFormat.BMP
    )

    private val imageFormats = rasterFormats + SupportedFormat.SVG

    override fun canHandle(format: SupportedFormat): Boolean {
        return format in imageFormats || format == SupportedFormat.PDF
    }

    override fun getSupportedTargetFormats(sourceFormats: List<SupportedFormat>): List<SupportedFormat> {
        if (sourceFormats.isEmpty()) return emptyList()
        val distinct = sourceFormats.distinct()

        // Caso 1: Todos los archivos son imágenes (JPG, PNG, WEBP, BMP, SVG)
        if (distinct.all { it in imageFormats }) {
            val targets = linkedSetOf(
                SupportedFormat.JPG,
                SupportedFormat.PNG,
                SupportedFormat.WEBP,
                SupportedFormat.BMP,
                SupportedFormat.PDF
            )
            // Si sólo hay un formato de entrada y un único archivo, aún permitimos recomprimir al mismo formato
            return targets.toList()
        }

        // Caso 2: Todos los archivos son PDF -> permite extraer páginas a JPG o PNG
        if (distinct.size == 1 && distinct.first() == SupportedFormat.PDF) {
            return listOf(
                SupportedFormat.JPG,
                SupportedFormat.PNG
            )
        }

        return emptyList()
    }

    override suspend fun convert(
        inputs: List<InputFileItem>,
        targetFormat: SupportedFormat,
        options: ConversionOptions,
        onProgress: (Float, String) -> Unit
    ): List<ConversionJobResult> = withContext(Dispatchers.IO) {
        require(inputs.isNotEmpty()) { "La lista de imágenes de entrada está vacía." }

        // Caso A: Extracción de páginas de PDF a imágenes individuales (JPG / PNG)
        if (inputs.all { it.format == SupportedFormat.PDF } && targetFormat in rasterFormats) {
            return@withContext extractPdfPagesToImages(inputs, targetFormat, options, onProgress)
        }

        // Caso B: Una o múltiples imágenes a un único PDF (o PDFs separados si mergeIntoSinglePdf == false)
        if (targetFormat == SupportedFormat.PDF) {
            return@withContext convertImagesToPdf(inputs, options, onProgress)
        }

        // Caso C: Conversión de imagen (JPG, PNG, WEBP, BMP, SVG) a formato raster (JPG, PNG, WEBP, BMP)
        if (targetFormat in rasterFormats) {
            return@withContext convertImagesToRaster(inputs, targetFormat, options, onProgress)
        }

        throw IllegalArgumentException(
            "Combinación no soportada por ImageEngineImpl: ${inputs.map { it.format }} -> $targetFormat"
        )
    }

    private suspend fun convertImagesToRaster(
        inputs: List<InputFileItem>,
        targetFormat: SupportedFormat,
        options: ConversionOptions,
        onProgress: (Float, String) -> Unit
    ): List<ConversionJobResult> {
        val results = mutableListOf<ConversionJobResult>()
        val total = inputs.size

        inputs.forEachIndexed { index, item ->
            coroutineContext.ensureActive()
            val stepProgress = index.toFloat() / total.toFloat()
            onProgress(stepProgress, "Procesando imagen ${index + 1} de $total: ${item.displayName}")

            val decoded = decodeInputToBitmap(
                item = item,
                maxDimensionPx = options.maxDimensionPx,
                opaqueWhiteBackground = (targetFormat == SupportedFormat.JPG || targetFormat == SupportedFormat.BMP)
            )

            val tempOut = uriResolver.createTempWorkingFile(
                prefix = "img_",
                extension = targetFormat.extension
            )

            try {
                FileOutputStream(tempOut).use { fos ->
                    BufferedOutputStream(fos, 64 * 1024).use { bos ->
                        encodeBitmapToStream(
                            bitmap = decoded,
                            targetFormat = targetFormat,
                            quality = options.clampedQuality,
                            output = bos
                        )
                    }
                }
            } finally {
                decoded.recycle()
            }

            val baseName = if (options.customOutputBaseName.isNotBlank()) {
                if (total == 1) options.customOutputBaseName
                else "${options.customOutputBaseName}_${index + 1}"
            } else {
                item.baseNameWithoutExtension
            }
            val desiredFileName = UriFileResolver.buildOutputFileName(
                baseName = baseName,
                extension = targetFormat.extension
            )

            val saved = try {
                storageManager.saveTempFileToDownloads(
                    sourceFile = tempOut,
                    desiredDisplayName = desiredFileName,
                    mimeType = targetFormat.mimeType
                )
            } finally {
                tempOut.delete()
            }

            results.add(
                ConversionJobResult(
                    sourceDisplayName = item.displayName,
                    sourceFormat = item.format,
                    outputDisplayName = saved.displayName,
                    outputFormat = targetFormat,
                    outputCategory = FormatCategory.IMAGE,
                    outputUri = saved.uri,
                    outputAbsolutePath = saved.absolutePath,
                    outputSizeBytes = saved.sizeBytes,
                    outputMimeType = saved.mimeType
                )
            )

            val doneProgress = (index + 1).toFloat() / total.toFloat()
            onProgress(doneProgress, "Convertida ${saved.displayName}")
        }

        return results
    }

    private suspend fun convertImagesToPdf(
        inputs: List<InputFileItem>,
        options: ConversionOptions,
        onProgress: (Float, String) -> Unit
    ): List<ConversionJobResult> {
        val total = inputs.size
        val mergeSingle = options.mergeIntoSinglePdf || total == 1

        if (!mergeSingle) {
            // Un PDF individual por cada imagen
            val results = mutableListOf<ConversionJobResult>()
            inputs.forEachIndexed { index, item ->
                coroutineContext.ensureActive()
                val subOptions = options.copy(
                    mergeIntoSinglePdf = true,
                    customOutputBaseName = if (options.customOutputBaseName.isNotBlank()) {
                        "${options.customOutputBaseName}_${index + 1}"
                    } else {
                        item.baseNameWithoutExtension
                    }
                )
                val singleResult = convertImagesToPdf(
                    inputs = listOf(item),
                    options = subOptions,
                    onProgress = { subP, msg ->
                        val globalP = (index + subP) / total.toFloat()
                        onProgress(globalP, msg)
                    }
                )
                results.addAll(singleResult)
            }
            return results
        }

        val tempPdfFile = uriResolver.createTempWorkingFile(prefix = "img2pdf_", extension = "pdf")
        val pdfDocument = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        try {
            inputs.forEachIndexed { index, item ->
                coroutineContext.ensureActive()
                val progress = (index.toFloat() / total.toFloat()) * 0.9f
                onProgress(progress, "Añadiendo imagen ${index + 1} de $total al PDF: ${item.displayName}")

                val rawBitmap = decodeInputToBitmap(
                    item = item,
                    maxDimensionPx = options.maxDimensionPx ?: 2480,
                    opaqueWhiteBackground = true
                )

                // Si la calidad es < 100, recomprimimos en memoria a JPEG con el porcentaje indicado
                // para reducir efectivamente el peso del PDF generado.
                val pageBitmap = if (options.clampedQuality < 98) {
                    val baos = ByteArrayOutputStream()
                    rawBitmap.compress(Bitmap.CompressFormat.JPEG, options.clampedQuality, baos)
                    rawBitmap.recycle()
                    val bytes = baos.toByteArray()
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: throw IOException(
                        "No se pudo recomprimir la imagen para el PDF: ${item.displayName}"
                    )
                } else {
                    rawBitmap
                }

                try {
                    val pageWidth = pageBitmap.width.coerceAtLeast(1)
                    val pageHeight = pageBitmap.height.coerceAtLeast(1)
                    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    page.canvas.drawColor(Color.WHITE)
                    page.canvas.drawBitmap(pageBitmap, 0f, 0f, paint)
                    pdfDocument.finishPage(page)
                } finally {
                    pageBitmap.recycle()
                }
            }

            onProgress(0.93f, "Escribiendo documento PDF final…")
            FileOutputStream(tempPdfFile).use { fos ->
                BufferedOutputStream(fos, 64 * 1024).use { bos ->
                    pdfDocument.writeTo(bos)
                    bos.flush()
                }
            }
        } finally {
            pdfDocument.close()
        }

        val baseName = when {
            options.customOutputBaseName.isNotBlank() -> options.customOutputBaseName
            inputs.size == 1 -> inputs.first().baseNameWithoutExtension
            else -> "${inputs.first().baseNameWithoutExtension}_unido_${inputs.size}_imgs"
        }

        val desiredName = UriFileResolver.buildOutputFileName(baseName, "pdf")
        val saved = try {
            storageManager.saveTempFileToDownloads(
                sourceFile = tempPdfFile,
                desiredDisplayName = desiredName,
                mimeType = SupportedFormat.PDF.mimeType
            )
        } finally {
            tempPdfFile.delete()
        }

        onProgress(1f, "PDF generado: ${saved.displayName}")
        val firstInput = inputs.first()
        val sourceDesc = if (inputs.size == 1) {
            firstInput.displayName
        } else {
            "${firstInput.displayName} (+${inputs.size - 1} más)"
        }

        return listOf(
            ConversionJobResult(
                sourceDisplayName = sourceDesc,
                sourceFormat = firstInput.format,
                outputDisplayName = saved.displayName,
                outputFormat = SupportedFormat.PDF,
                outputCategory = FormatCategory.DOCUMENT,
                outputUri = saved.uri,
                outputAbsolutePath = saved.absolutePath,
                outputSizeBytes = saved.sizeBytes,
                outputMimeType = saved.mimeType
            )
        )
    }

    private suspend fun extractPdfPagesToImages(
        inputs: List<InputFileItem>,
        targetFormat: SupportedFormat,
        options: ConversionOptions,
        onProgress: (Float, String) -> Unit
    ): List<ConversionJobResult> {
        val results = mutableListOf<ConversionJobResult>()
        val totalFiles = inputs.size

        inputs.forEachIndexed { fileIdx, item ->
            coroutineContext.ensureActive()
            val tempPdf = uriResolver.copyUriToTempFile(item.uri, prefix = "pdf_render_", extension = "pdf")
            try {
                ParcelFileDescriptor.open(tempPdf, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        val pageCount = renderer.pageCount
                        if (pageCount <= 0) {
                            throw IOException("El archivo PDF '${item.displayName}' no contiene páginas.")
                        }

                        val selectedPages = parsePageIndices(options.pageRangeSpec, pageCount)
                        val totalPagesToRender = selectedPages.size

                        selectedPages.forEachIndexed { idx, pageIndex ->
                            coroutineContext.ensureActive()
                            val fileBaseProgress = fileIdx.toFloat() / totalFiles.toFloat()
                            val pageFraction = idx.toFloat() / totalPagesToRender.toFloat()
                            val overallProgress = fileBaseProgress + (pageFraction / totalFiles.toFloat())
                            onProgress(
                                overallProgress.coerceIn(0f, 0.99f),
                                "Renderizando página ${pageIndex + 1} de $pageCount (${item.displayName})"
                            )

                            renderer.openPage(pageIndex).use { page ->
                                // Escala de alta definición (2x = ~144 DPI) respetando maxDimensionPx si se configuró
                                val defaultScale = 2.0f
                                var targetW = (page.width * defaultScale).roundToInt().coerceAtLeast(1)
                                var targetH = (page.height * defaultScale).roundToInt().coerceAtLeast(1)

                                val maxDim = options.maxDimensionPx
                                if (maxDim != null && maxDim > 0 && max(targetW, targetH) > maxDim) {
                                    val ratio = maxDim.toFloat() / max(targetW, targetH).toFloat()
                                    targetW = (targetW * ratio).roundToInt().coerceAtLeast(1)
                                    targetH = (targetH * ratio).roundToInt().coerceAtLeast(1)
                                }

                                val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                                try {
                                    val canvas = Canvas(bitmap)
                                    canvas.drawColor(Color.WHITE)
                                    page.render(
                                        bitmap,
                                        Rect(0, 0, targetW, targetH),
                                        null,
                                        PdfRenderer.Page.RENDER_MODE_FOR_PRINT
                                    )

                                    val tempImg = uriResolver.createTempWorkingFile(
                                        prefix = "pdf_page_",
                                        extension = targetFormat.extension
                                    )
                                    try {
                                        FileOutputStream(tempImg).use { fos ->
                                            BufferedOutputStream(fos, 64 * 1024).use { bos ->
                                                encodeBitmapToStream(
                                                    bitmap = bitmap,
                                                    targetFormat = targetFormat,
                                                    quality = options.clampedQuality,
                                                    output = bos
                                                )
                                            }
                                        }

                                        val baseName = if (options.customOutputBaseName.isNotBlank() && totalFiles == 1) {
                                            options.customOutputBaseName
                                        } else {
                                            item.baseNameWithoutExtension
                                        }
                                        val pageNumberFormatted = String.format("%03d", pageIndex + 1)
                                        val outFileName = UriFileResolver.buildOutputFileName(
                                            baseName = baseName,
                                            extension = targetFormat.extension,
                                            suffix = "_pag_$pageNumberFormatted"
                                        )

                                        val saved = storageManager.saveTempFileToDownloads(
                                            sourceFile = tempImg,
                                            desiredDisplayName = outFileName,
                                            mimeType = targetFormat.mimeType
                                        )

                                        results.add(
                                            ConversionJobResult(
                                                sourceDisplayName = "${item.displayName} (Pág. ${pageIndex + 1})",
                                                sourceFormat = item.format,
                                                outputDisplayName = saved.displayName,
                                                outputFormat = targetFormat,
                                                outputCategory = FormatCategory.IMAGE,
                                                outputUri = saved.uri,
                                                outputAbsolutePath = saved.absolutePath,
                                                outputSizeBytes = saved.sizeBytes,
                                                outputMimeType = saved.mimeType
                                            )
                                        )
                                    } finally {
                                        tempImg.delete()
                                    }
                                } finally {
                                    bitmap.recycle()
                                }
                            }
                        }
                    }
                }
            } finally {
                tempPdf.delete()
            }
        }

        onProgress(1f, "Extracción de páginas completada (${results.size} imágenes)")
        return results
    }

    /**
     * Decodifica una imagen raster (JPG, PNG, WEBP, BMP) o vectorial (SVG) a un `Bitmap` en memoria,
     * aplicando redimensionado máximo si se solicita y aplanando el canal alfa sobre blanco cuando
     * el formato destino no admite transparencia (JPG, BMP, PDF).
     */
    @Throws(IOException::class)
    private fun decodeInputToBitmap(
        item: InputFileItem,
        maxDimensionPx: Int?,
        opaqueWhiteBackground: Boolean
    ): Bitmap {
        val rawBitmap = if (item.format == SupportedFormat.SVG) {
            renderSvgToBitmap(item.uri, maxDimensionPx, opaqueWhiteBackground)
        } else {
            decodeRasterUriToBitmap(item.uri, maxDimensionPx)
        }

        val scaledBitmap = if (maxDimensionPx != null && maxDimensionPx > 0) {
            scaleDownIfNeeded(rawBitmap, maxDimensionPx)
        } else {
            rawBitmap
        }

        return if (opaqueWhiteBackground) {
            flattenAlphaToWhite(scaledBitmap)
        } else {
            ensureSoftwareBitmap(scaledBitmap)
        }
    }

    @Throws(IOException::class)
    private fun renderSvgToBitmap(
        uri: Uri,
        maxDimensionPx: Int?,
        opaqueWhiteBackground: Boolean
    ): Bitmap {
        val svg = uriResolver.openInputStream(uri).use { stream ->
            try {
                SVG.getFromInputStream(stream)
            } catch (e: Exception) {
                throw IOException("Archivo SVG inválido o corrupto: ${e.message}", e)
            }
        }

        val viewBox = svg.documentViewBox
        var docWidth = svg.documentWidth
        var docHeight = svg.documentHeight

        if (docWidth <= 0f || docHeight <= 0f) {
            if (viewBox != null && viewBox.width() > 0f && viewBox.height() > 0f) {
                docWidth = viewBox.width()
                docHeight = viewBox.height()
            } else {
                docWidth = 1080f
                docHeight = 1080f
            }
        }

        val targetMax = (maxDimensionPx?.takeIf { it > 0 } ?: 2048).toFloat()
        val maxSide = max(docWidth, docHeight).coerceAtLeast(1f)
        val scale = if (maxSide > targetMax || maxSide < 256f) {
            targetMax / maxSide
        } else {
            1f
        }

        val widthPx = (docWidth * scale).roundToInt().coerceIn(1, 4096)
        val heightPx = (docHeight * scale).roundToInt().coerceIn(1, 4096)

        if (viewBox == null) {
            svg.setDocumentViewBox(0f, 0f, docWidth, docHeight)
        }
        svg.setDocumentWidth(widthPx.toFloat())
        svg.setDocumentHeight(heightPx.toFloat())

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (opaqueWhiteBackground) {
            canvas.drawColor(Color.WHITE)
        }
        svg.renderToCanvas(canvas)
        return bitmap
    }

    @Throws(IOException::class)
    private fun decodeRasterUriToBitmap(uri: Uri, maxDimensionPx: Int?): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                    val maxDim = maxDimensionPx
                    if (maxDim != null && maxDim > 0) {
                        val size = info.size
                        val currentMax = max(size.width, size.height)
                        if (currentMax > maxDim) {
                            val ratio = maxDim.toFloat() / currentMax.toFloat()
                            val newW = (size.width * ratio).roundToInt().coerceAtLeast(1)
                            val newH = (size.height * ratio).roundToInt().coerceAtLeast(1)
                            decoder.setTargetSize(newW, newH)
                        }
                    }
                }
            } catch (_: Throwable) {
                // Fallback a BitmapFactory si ImageDecoder falla en algún archivo específico
            }
        }

        val bytes = uriResolver.openInputStream(uri).use { it.readBytes() }
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)

        var sampleSize = 1
        if (maxDimensionPx != null && maxDimensionPx > 0) {
            val halfW = boundsOptions.outWidth / 2
            val halfH = boundsOptions.outHeight / 2
            while ((halfW / sampleSize) >= maxDimensionPx && (halfH / sampleSize) >= maxDimensionPx) {
                sampleSize *= 2
            }
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
            ?: throw IOException("No se pudo decodificar el archivo de imagen.")
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDimensionPx: Int): Bitmap {
        val currentMax = max(bitmap.width, bitmap.height)
        if (currentMax <= maxDimensionPx) return bitmap

        val ratio = maxDimensionPx.toFloat() / currentMax.toFloat()
        val newW = (bitmap.width * ratio).roundToInt().coerceAtLeast(1)
        val newH = (bitmap.height * ratio).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, newW, newH, true)
        if (scaled !== bitmap) {
            bitmap.recycle()
        }
        return scaled
    }

    private fun ensureSoftwareBitmap(bitmap: Bitmap): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE) {
            val copy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
            bitmap.recycle()
            return copy
        }
        return bitmap
    }

    private fun flattenAlphaToWhite(source: Bitmap): Bitmap {
        val sw = ensureSoftwareBitmap(source)
        val output = Bitmap.createBitmap(sw.width, sw.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(sw, 0f, 0f, null)
        if (output !== sw) {
            sw.recycle()
        }
        return output
    }

    @Throws(IOException::class)
    private fun encodeBitmapToStream(
        bitmap: Bitmap,
        targetFormat: SupportedFormat,
        quality: Int,
        output: OutputStream
    ) {
        val clampedQ = quality.coerceIn(10, 100)
        when (targetFormat) {
            SupportedFormat.JPG -> {
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, clampedQ, output)) {
                    throw IOException("Error al comprimir Bitmap a JPEG.")
                }
            }
            SupportedFormat.PNG -> {
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    throw IOException("Error al comprimir Bitmap a PNG.")
                }
            }
            SupportedFormat.WEBP -> {
                val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (clampedQ == 100) {
                        Bitmap.CompressFormat.WEBP_LOSSLESS
                    } else {
                        Bitmap.CompressFormat.WEBP_LOSSY
                    }
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
                if (!bitmap.compress(format, clampedQ, output)) {
                    throw IOException("Error al comprimir Bitmap a WEBP.")
                }
            }
            SupportedFormat.BMP -> {
                writeBitmapAs24BitBmp(bitmap, output)
            }
            else -> throw IllegalArgumentException("Formato de imagen no soportado: $targetFormat")
        }
        output.flush()
    }

    /**
     * Codificador nativo de formato Windows Bitmap (BMP 24-bit BGR sin compresión).
     * Garantiza soporte real para `.bmp` en todas las versiones de Android sin depender de librerías externas.
     */
    @Throws(IOException::class)
    private fun writeBitmapAs24BitBmp(bitmap: Bitmap, output: OutputStream) {
        val width = bitmap.width
        val height = bitmap.height
        val rowBytesWithoutPadding = width * 3
        val rowPadding = (4 - (rowBytesWithoutPadding % 4)) % 4
        val rowStride = rowBytesWithoutPadding + rowPadding
        val pixelDataSize = rowStride * height
        val fileSize = 54 + pixelDataSize

        val header = ByteBuffer.allocate(54).order(ByteOrder.LITTLE_ENDIAN)
        // BITMAPFILEHEADER (14 bytes)
        header.put(0x42.toByte()) // 'B'
        header.put(0x4D.toByte()) // 'M'
        header.putInt(fileSize)
        header.putShort(0)
        header.putShort(0)
        header.putInt(54) // Offset a datos de píxeles

        // BITMAPINFOHEADER (40 bytes)
        header.putInt(40) // Tamaño del header DIB
        header.putInt(width)
        header.putInt(height) // Positivo = orden de filas bottom-up estándar
        header.putShort(1) // Planes
        header.putShort(24) // 24 bits por píxel (BGR)
        header.putInt(0) // BI_RGB (sin compresión)
        header.putInt(pixelDataSize)
        header.putInt(2835) // ~72 DPI horizontal
        header.putInt(2835) // ~72 DPI vertical
        header.putInt(0)
        header.putInt(0)

        output.write(header.array())

        val pixels = IntArray(width)
        val rowBuffer = ByteArray(rowStride)
        // BMP almacena las filas desde la inferior (height - 1) hasta la superior (0)
        for (y in (height - 1) downTo 0) {
            bitmap.getPixels(pixels, 0, width, 0, y, width, 1)
            var offset = 0
            for (x in 0 until width) {
                val argb = pixels[x]
                rowBuffer[offset++] = (argb and 0xFF).toByte() // Blue
                rowBuffer[offset++] = ((argb shr 8) and 0xFF).toByte() // Green
                rowBuffer[offset++] = ((argb shr 16) and 0xFF).toByte() // Red
            }
            for (p in 0 until rowPadding) {
                rowBuffer[offset++] = 0
            }
            output.write(rowBuffer)
        }
    }

    companion object {
        /**
         * Parsea una especificación de páginas en base 1 (por ejemplo: "1-3, 5, 7-9")
         * y devuelve la lista ordenada de índices en base 0 dentro de `0 until totalPages`.
         */
        fun parsePageIndices(rangeSpec: String, totalPages: Int): List<Int> {
            if (totalPages <= 0) return emptyList()
            val clean = rangeSpec.trim()
            if (clean.isEmpty()) return (0 until totalPages).toList()

            val indices = sortedSetOf<Int>()
            val tokens = clean.split(',', ';')
            for (rawToken in tokens) {
                val token = rawToken.trim()
                if (token.isEmpty()) continue
                if (token.contains('-')) {
                    val parts = token.split('-', limit = 2)
                    val start = parts[0].trim().toIntOrNull() ?: continue
                    val end = parts[1].trim().toIntOrNull() ?: totalPages
                    val low = start.coerceIn(1, totalPages)
                    val high = end.coerceIn(low, totalPages)
                    for (p in low..high) {
                        indices.add(p - 1)
                    }
                } else {
                    val single = token.toIntOrNull() ?: continue
                    if (single in 1..totalPages) {
                        indices.add(single - 1)
                    }
                }
            }
            return if (indices.isEmpty()) (0 until totalPages).toList() else indices.toList()
        }
    }
}
