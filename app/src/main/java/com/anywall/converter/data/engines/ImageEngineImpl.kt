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
import com.anywall.converter.domain.model.ImageBatchLayoutMode
import com.anywall.converter.domain.model.InputFileItem
import com.anywall.converter.domain.model.OutputNamingTemplateResolver
import com.anywall.converter.domain.model.PdfPageEditItem
import com.anywall.converter.domain.model.SupportedFormat
import com.caverock.androidsvg.SVG
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Motor 1: `ImageEngineImpl` (Imágenes, Editor Visual, Collages, GIF, ICO, IconPack y PDF - v0.3)
 * - Conversión entre JPG, PNG, WEBP, BMP, ICO, GIF animado y Paquete de Íconos ZIP (`ICON_PACK_ZIP`),
 *   más soporte de entrada HEIC/HEIF, AVIF, GIF y SVG.
 * - Aplica ediciones visuales (`ImageEditSpec`): recorte interactivo, volteo H/V, filtros (Grises, Escáner B/N, Invertir),
 *   censura/pixelado, relación de aspecto con fondo Blur/sólido y marca de agua de texto.
 * - Compresión inteligente por peso máximo objetivo (`targetImageMaxSizeKb`) mediante búsqueda binaria local.
 * - Modo Collage (`2x2`, `3x3`) y Tira continua (`Vertical`, `Horizontal`).
 * - Conversión de imágenes a PDF (con soporte de cifrado) y extracción de páginas PDF a imágenes.
 */
class ImageEngineImpl(
    private val context: Context,
    private val uriResolver: UriFileResolver,
    private val storageManager: ScopedStorageManager
) : FileConverterEngine {

    private val rasterOutputFormats = setOf(
        SupportedFormat.JPG,
        SupportedFormat.PNG,
        SupportedFormat.WEBP,
        SupportedFormat.BMP,
        SupportedFormat.ICO
    )

    private val imageInputFormats = rasterOutputFormats + setOf(
        SupportedFormat.SVG,
        SupportedFormat.HEIC,
        SupportedFormat.AVIF,
        SupportedFormat.GIF
    )

    override fun canHandle(format: SupportedFormat): Boolean {
        return format in imageInputFormats || format == SupportedFormat.PDF
    }

    override fun getSupportedTargetFormats(sourceFormats: List<SupportedFormat>): List<SupportedFormat> {
        if (sourceFormats.isEmpty()) return emptyList()
        val distinct = sourceFormats.distinct()

        // Caso 1: Todos los archivos son imágenes (JPG, PNG, WEBP, BMP, SVG, HEIC, AVIF, GIF, ICO)
        if (distinct.all { it in imageInputFormats }) {
            return listOf(
                SupportedFormat.JPG,
                SupportedFormat.PNG,
                SupportedFormat.WEBP,
                SupportedFormat.BMP,
                SupportedFormat.GIF,
                SupportedFormat.ICO,
                SupportedFormat.ICON_PACK_ZIP,
                SupportedFormat.PDF
            )
        }

        // Caso 2: Todos los archivos son PDF -> permite extraer páginas a JPG, PNG o WEBP
        if (distinct.size == 1 && distinct.first() == SupportedFormat.PDF) {
            return listOf(
                SupportedFormat.JPG,
                SupportedFormat.PNG,
                SupportedFormat.WEBP
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

        // Caso A: Extracción de páginas de PDF a imágenes individuales
        if (inputs.all { it.format == SupportedFormat.PDF } && targetFormat in rasterOutputFormats) {
            return@withContext extractPdfPagesToImages(inputs, targetFormat, options, onProgress)
        }

        // Caso B: Generación de GIF animado desde 1 o múltiples imágenes
        if (targetFormat == SupportedFormat.GIF) {
            return@withContext convertImagesToAnimatedGif(inputs, options, onProgress)
        }

        // Caso C: Generación de Paquete de Íconos (.zip con 48, 96, 192, 512px + favicon.ico)
        if (targetFormat == SupportedFormat.ICON_PACK_ZIP) {
            return@withContext convertImagesToIconPacks(inputs, options, onProgress)
        }

        // Caso D: Modo Collage o Tira continua cuando hay >= 2 imágenes y layout != INDIVIDUAL
        if (inputs.size >= 2 &&
            options.imageBatchLayoutMode != ImageBatchLayoutMode.INDIVIDUAL &&
            (targetFormat in rasterOutputFormats || targetFormat == SupportedFormat.PDF)
        ) {
            return@withContext convertImagesToCollageOrStitch(inputs, targetFormat, options, onProgress)
        }

        // Caso E: Una o múltiples imágenes a PDF
        if (targetFormat == SupportedFormat.PDF) {
            return@withContext convertImagesToPdf(inputs, options, onProgress)
        }

        // Caso F: Conversión de imagen a formato raster / ICO
        if (targetFormat in rasterOutputFormats) {
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
        val editSpec = options.imageEditSpec

        inputs.forEachIndexed { index, item ->
            coroutineContext.ensureActive()
            val stepProgress = index.toFloat() / total.toFloat()
            onProgress(stepProgress, "Procesando imagen ${index + 1} de $total: ${item.displayName}")

            val decoded = decodeInputToBitmap(
                item = item,
                maxDimensionPx = if (targetFormat == SupportedFormat.ICO) 256 else options.maxDimensionPx,
                rotationDegrees = options.normalizedRotationDegrees,
                opaqueWhiteBackground = (targetFormat == SupportedFormat.JPG || targetFormat == SupportedFormat.BMP)
            )

            val edited = try {
                ImageVisualTransformHelper.applyVisualEditSpec(decoded, editSpec)
            } finally {
                // Si applyVisualEditSpec creó un nuevo bitmap, reciclar el original
            }
            if (edited !== decoded) {
                decoded.recycle()
            }

            val tempOut = uriResolver.createTempWorkingFile(
                prefix = "img_",
                extension = targetFormat.extension
            )

            try {
                encodeBitmapWithTargetSizeConstraint(
                    bitmap = edited,
                    targetFormat = targetFormat,
                    initialQuality = options.clampedQuality,
                    targetMaxSizeKb = options.targetImageMaxSizeKb,
                    outputFile = tempOut
                )
            } finally {
                edited.recycle()
            }

            val baseName = OutputNamingTemplateResolver.resolveBaseName(
                templateOrCustomName = options.customOutputBaseName,
                originalBaseName = item.baseNameWithoutExtension,
                batchIndexOneBased = index + 1,
                batchTotalCount = total,
                targetFormat = targetFormat
            )
            val desiredFileName = UriFileResolver.buildOutputFileName(
                baseName = baseName,
                extension = targetFormat.extension
            )
            val subFolder = storageManager.resolveEffectiveSubFolder(
                relativeFolderPath = item.relativeFolderPath,
                categorySubfolder = "Imagenes",
                preserveTreeOverride = options.preserveSubfolderStructure
            )

            val saved = try {
                storageManager.saveTempFileToDownloads(
                    sourceFile = tempOut,
                    desiredDisplayName = desiredFileName,
                    mimeType = targetFormat.mimeType,
                    subFolder = subFolder
                )
            } finally {
                tempOut.delete()
            }

            results.add(
                ConversionJobResult(
                    sourceDisplayName = item.displayName,
                    sourceFormat = item.format,
                    sourceSizeBytes = item.sizeBytes,
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

    private suspend fun convertImagesToAnimatedGif(
        inputs: List<InputFileItem>,
        options: ConversionOptions,
        onProgress: (Float, String) -> Unit
    ): List<ConversionJobResult> {
        val total = inputs.size
        val editSpec = options.imageEditSpec
        val maxSide = (options.maxDimensionPx ?: 720).coerceIn(128, 1280)
        val frames = ArrayList<AnimatedGifPureEncoder.GifFrame>(total)

        inputs.forEachIndexed { idx, item ->
            coroutineContext.ensureActive()
            onProgress(
                (idx.toFloat() / total.toFloat()) * 0.8f,
                "Preparando fotograma GIF ${idx + 1} de $total: ${item.displayName}"
            )
            val raw = decodeInputToBitmap(
                item = item,
                maxDimensionPx = maxSide,
                rotationDegrees = options.normalizedRotationDegrees,
                opaqueWhiteBackground = true
            )
            val edited = ImageVisualTransformHelper.applyVisualEditSpec(raw, editSpec)
            if (edited !== raw) raw.recycle()
            try {
                val w = edited.width
                val h = edited.height
                val pixels = IntArray(w * h)
                edited.getPixels(pixels, 0, w, 0, 0, w, h)
                frames.add(AnimatedGifPureEncoder.GifFrame(w, h, pixels))
            } finally {
                edited.recycle()
            }
        }

        onProgress(0.86f, "Codificando GIF animado (${frames.size} fotogramas @ ${options.animatedGifFps} FPS)…")
        val tempGif = uriResolver.createTempWorkingFile(prefix = "anim_", extension = "gif")
        val firstInput = inputs.first()
        val baseName = OutputNamingTemplateResolver.resolveBaseName(
            templateOrCustomName = options.customOutputBaseName,
            originalBaseName = if (inputs.size > 1) "${firstInput.baseNameWithoutExtension}_anim" else firstInput.baseNameWithoutExtension,
            batchIndexOneBased = 1,
            batchTotalCount = 1,
            targetFormat = SupportedFormat.GIF
        )
        val desiredName = UriFileResolver.buildOutputFileName(baseName, "gif")

        val saved = try {
            FileOutputStream(tempGif).use { fos ->
                BufferedOutputStream(fos, 64 * 1024).use { bos ->
                    AnimatedGifPureEncoder.encodeAnimatedGif(
                        frames = frames,
                        fps = options.animatedGifFps,
                        loopForever = true,
                        output = bos
                    )
                }
            }
            storageManager.saveTempFileToDownloads(
                sourceFile = tempGif,
                desiredDisplayName = desiredName,
                mimeType = SupportedFormat.GIF.mimeType,
                subFolder = storageManager.resolveEffectiveSubFolder(
                    relativeFolderPath = firstInput.relativeFolderPath,
                    categorySubfolder = "Imagenes",
                    preserveTreeOverride = options.preserveSubfolderStructure
                )
            )
        } finally {
            tempGif.delete()
        }

        onProgress(1f, "GIF animado generado: ${saved.displayName}")
        return listOf(
            ConversionJobResult(
                sourceDisplayName = if (inputs.size == 1) firstInput.displayName else "${firstInput.displayName} (+${inputs.size - 1} más)",
                sourceFormat = firstInput.format,
                sourceSizeBytes = inputs.sumOf { it.sizeBytes },
                outputDisplayName = saved.displayName,
                outputFormat = SupportedFormat.GIF,
                outputCategory = FormatCategory.IMAGE,
                outputUri = saved.uri,
                outputAbsolutePath = saved.absolutePath,
                outputSizeBytes = saved.sizeBytes,
                outputMimeType = saved.mimeType
            )
        )
    }

    private suspend fun convertImagesToIconPacks(
        inputs: List<InputFileItem>,
        options: ConversionOptions,
        onProgress: (Float, String) -> Unit
    ): List<ConversionJobResult> {
        val results = mutableListOf<ConversionJobResult>()
        val sizes = listOf(48, 96, 192, 512)
        val total = inputs.size
        val editSpec = options.imageEditSpec

        inputs.forEachIndexed { idx, item ->
            coroutineContext.ensureActive()
            onProgress(
                idx.toFloat() / total.toFloat(),
                "Generando paquete de íconos para ${item.displayName}…"
            )
            val raw = decodeInputToBitmap(
                item = item,
                maxDimensionPx = 1024,
                rotationDegrees = options.normalizedRotationDegrees,
                opaqueWhiteBackground = false
            )
            val edited = ImageVisualTransformHelper.applyVisualEditSpec(raw, editSpec)
            if (edited !== raw) raw.recycle()

            val tempZip = uriResolver.createTempWorkingFile(prefix = "iconpack_", extension = "zip")
            try {
                FileOutputStream(tempZip).use { fos ->
                    ZipOutputStream(BufferedOutputStream(fos, 64 * 1024)).use { zos ->
                        val icoEntries = mutableListOf<IcoPureEncoder.IcoImageEntry>()
                        for (sz in sizes) {
                            val scaled = Bitmap.createScaledBitmap(edited, sz, sz, true)
                            val pngBytes = ByteArrayOutputStream().use { baos ->
                                scaled.compress(Bitmap.CompressFormat.PNG, 100, baos)
                                baos.toByteArray()
                            }
                            if (scaled !== edited) scaled.recycle()

                            val entryName = "icon_${sz}x${sz}.png"
                            zos.putNextEntry(ZipEntry(entryName))
                            zos.write(pngBytes)
                            zos.closeEntry()

                            if (sz <= 192) {
                                icoEntries.add(IcoPureEncoder.IcoImageEntry(sz, sz, pngBytes))
                            }
                        }
                        val icoOut = ByteArrayOutputStream()
                        IcoPureEncoder.writeIco(icoEntries, icoOut)
                        zos.putNextEntry(ZipEntry("favicon.ico"))
                        zos.write(icoOut.toByteArray())
                        zos.closeEntry()
                        zos.flush()
                    }
                }
            } finally {
                edited.recycle()
            }

            val baseName = OutputNamingTemplateResolver.resolveBaseName(
                templateOrCustomName = options.customOutputBaseName,
                originalBaseName = "${item.baseNameWithoutExtension}_iconpack",
                batchIndexOneBased = idx + 1,
                batchTotalCount = total,
                targetFormat = SupportedFormat.ZIP
            )
            val desiredName = UriFileResolver.buildOutputFileName(baseName, "zip")
            val saved = try {
                storageManager.saveTempFileToDownloads(
                    sourceFile = tempZip,
                    desiredDisplayName = desiredName,
                    mimeType = SupportedFormat.ZIP.mimeType,
                    subFolder = storageManager.resolveEffectiveSubFolder(
                        relativeFolderPath = item.relativeFolderPath,
                        categorySubfolder = "Imagenes",
                        preserveTreeOverride = options.preserveSubfolderStructure
                    )
                )
            } finally {
                tempZip.delete()
            }

            results.add(
                ConversionJobResult(
                    sourceDisplayName = item.displayName,
                    sourceFormat = item.format,
                    sourceSizeBytes = item.sizeBytes,
                    outputDisplayName = saved.displayName,
                    outputFormat = SupportedFormat.ICON_PACK_ZIP,
                    outputCategory = FormatCategory.IMAGE,
                    outputUri = saved.uri,
                    outputAbsolutePath = saved.absolutePath,
                    outputSizeBytes = saved.sizeBytes,
                    outputMimeType = saved.mimeType
                )
            )
        }
        onProgress(1f, "Paquete(s) de íconos generados")
        return results
    }

    private suspend fun convertImagesToCollageOrStitch(
        inputs: List<InputFileItem>,
        targetFormat: SupportedFormat,
        options: ConversionOptions,
        onProgress: (Float, String) -> Unit
    ): List<ConversionJobResult> {
        val editSpec = options.imageEditSpec
        val bitmaps = ArrayList<Bitmap>(inputs.size)
        try {
            inputs.forEachIndexed { idx, item ->
                coroutineContext.ensureActive()
                onProgress(
                    (idx.toFloat() / inputs.size.toFloat()) * 0.7f,
                    "Cargando imagen ${idx + 1} de ${inputs.size} para ${options.imageBatchLayoutMode.labelEs}…"
                )
                val raw = decodeInputToBitmap(
                    item = item,
                    maxDimensionPx = options.maxDimensionPx ?: 1600,
                    rotationDegrees = options.normalizedRotationDegrees,
                    opaqueWhiteBackground = true
                )
                val edited = ImageVisualTransformHelper.applyVisualEditSpec(raw, editSpec)
                if (edited !== raw) raw.recycle()
                bitmaps.add(edited)
            }

            onProgress(0.78f, "Componiendo ${options.imageBatchLayoutMode.labelEs}…")
            val composed = ImageVisualTransformHelper.composeBatchCollageOrStitch(
                bitmaps = bitmaps,
                layoutMode = options.imageBatchLayoutMode,
                maxCanvasSidePx = options.maxDimensionPx ?: 2400
            )

            try {
                val firstInput = inputs.first()
                val suffixTag = when (options.imageBatchLayoutMode) {
                    ImageBatchLayoutMode.COLLAGE_GRID_2X2 -> "_collage2x2"
                    ImageBatchLayoutMode.COLLAGE_GRID_3X3 -> "_collage3x3"
                    ImageBatchLayoutMode.STITCH_VERTICAL -> "_tira_v"
                    ImageBatchLayoutMode.STITCH_HORIZONTAL -> "_tira_h"
                    ImageBatchLayoutMode.INDIVIDUAL -> ""
                }
                val baseName = OutputNamingTemplateResolver.resolveBaseName(
                    templateOrCustomName = options.customOutputBaseName,
                    originalBaseName = "${firstInput.baseNameWithoutExtension}$suffixTag",
                    batchIndexOneBased = 1,
                    batchTotalCount = 1,
                    targetFormat = targetFormat
                )

                if (targetFormat == SupportedFormat.PDF) {
                    val tempPdf = uriResolver.createTempWorkingFile("collage_", "pdf")
                    val pdfDoc = PdfDocument()
                    try {
                        val pageInfo = PdfDocument.PageInfo.Builder(composed.width, composed.height, 1).create()
                        val page = pdfDoc.startPage(pageInfo)
                        page.canvas.drawColor(Color.WHITE)
                        page.canvas.drawBitmap(composed, 0f, 0f, null)
                        pdfDoc.finishPage(page)
                        FileOutputStream(tempPdf).use { fos ->
                            BufferedOutputStream(fos, 64 * 1024).use { pdfDoc.writeTo(it) }
                        }
                    } finally {
                        pdfDoc.close()
                    }
                    if (options.pdfPassword.isNotBlank()) {
                        encryptPdfFileInPlace(tempPdf, options.pdfPassword)
                    }
                    val saved = try {
                        storageManager.saveTempFileToDownloads(
                            sourceFile = tempPdf,
                            desiredDisplayName = UriFileResolver.buildOutputFileName(baseName, "pdf"),
                            mimeType = SupportedFormat.PDF.mimeType
                        )
                    } finally {
                        tempPdf.delete()
                    }
                    onProgress(1f, "Collage PDF generado: ${saved.displayName}")
                    return listOf(
                        ConversionJobResult(
                            sourceDisplayName = "${firstInput.displayName} (+${inputs.size - 1} más)",
                            sourceFormat = firstInput.format,
                            sourceSizeBytes = inputs.sumOf { it.sizeBytes },
                            outputDisplayName = saved.displayName,
                            outputFormat = SupportedFormat.PDF,
                            outputCategory = FormatCategory.DOCUMENT,
                            outputUri = saved.uri,
                            outputAbsolutePath = saved.absolutePath,
                            outputSizeBytes = saved.sizeBytes,
                            outputMimeType = saved.mimeType
                        )
                    )
                } else {
                    val tempImg = uriResolver.createTempWorkingFile("collage_", targetFormat.extension)
                    val saved = try {
                        encodeBitmapWithTargetSizeConstraint(
                            bitmap = composed,
                            targetFormat = targetFormat,
                            initialQuality = options.clampedQuality,
                            targetMaxSizeKb = options.targetImageMaxSizeKb,
                            outputFile = tempImg
                        )
                        storageManager.saveTempFileToDownloads(
                            sourceFile = tempImg,
                            desiredDisplayName = UriFileResolver.buildOutputFileName(baseName, targetFormat.extension),
                            mimeType = targetFormat.mimeType
                        )
                    } finally {
                        tempImg.delete()
                    }
                    onProgress(1f, "${options.imageBatchLayoutMode.labelEs} generado: ${saved.displayName}")
                    return listOf(
                        ConversionJobResult(
                            sourceDisplayName = "${firstInput.displayName} (+${inputs.size - 1} más)",
                            sourceFormat = firstInput.format,
                            sourceSizeBytes = inputs.sumOf { it.sizeBytes },
                            outputDisplayName = saved.displayName,
                            outputFormat = targetFormat,
                            outputCategory = FormatCategory.IMAGE,
                            outputUri = saved.uri,
                            outputAbsolutePath = saved.absolutePath,
                            outputSizeBytes = saved.sizeBytes,
                            outputMimeType = saved.mimeType
                        )
                    )
                }
            } finally {
                composed.recycle()
            }
        } finally {
            bitmaps.forEach { if (!it.isRecycled) it.recycle() }
        }
    }

    /**
     * Codifica un `Bitmap` al archivo destino aplicando búsqueda binaria local de calidad
     * (y escalado progresivo si es necesario) cuando el usuario define `targetMaxSizeKb`.
     */
    @Throws(IOException::class)
    private fun encodeBitmapWithTargetSizeConstraint(
        bitmap: Bitmap,
        targetFormat: SupportedFormat,
        initialQuality: Int,
        targetMaxSizeKb: Int?,
        outputFile: File
    ) {
        val targetBytes = targetMaxSizeKb?.takeIf { it > 0 }?.let { it.toLong() * 1024L }
        if (targetBytes == null || targetFormat !in setOf(SupportedFormat.JPG, SupportedFormat.WEBP)) {
            FileOutputStream(outputFile).use { fos ->
                BufferedOutputStream(fos, 64 * 1024).use { bos ->
                    encodeBitmapToStream(bitmap, targetFormat, initialQuality, bos)
                }
            }
            return
        }

        // Búsqueda binaria de calidad en [12 .. initialQuality]
        var bestBytes: ByteArray? = null
        var low = 12
        var high = initialQuality.coerceIn(12, 100)

        while (low <= high) {
            val mid = (low + high) / 2
            val candidate = ByteArrayOutputStream().use { baos ->
                encodeBitmapToStream(bitmap, targetFormat, mid, baos)
                baos.toByteArray()
            }
            if (candidate.size <= targetBytes) {
                bestBytes = candidate
                low = mid + 1
            } else {
                if (bestBytes == null) bestBytes = candidate
                high = mid - 1
            }
        }

        // Si aún a calidad 12 supera el peso objetivo, reducir resolución progresivamente
        var currentBytes = bestBytes ?: ByteArray(0)
        var scaleFactor = 0.85f
        var attempt = 0
        while (currentBytes.size > targetBytes && attempt < 4) {
            val newW = (bitmap.width * scaleFactor).roundToInt().coerceAtLeast(64)
            val newH = (bitmap.height * scaleFactor).roundToInt().coerceAtLeast(64)
            val scaled = Bitmap.createScaledBitmap(bitmap, newW, newH, true)
            try {
                currentBytes = ByteArrayOutputStream().use { baos ->
                    encodeBitmapToStream(scaled, targetFormat, 45, baos)
                    baos.toByteArray()
                }
            } finally {
                if (scaled !== bitmap) scaled.recycle()
            }
            scaleFactor *= 0.8f
            attempt++
        }

        FileOutputStream(outputFile).use { it.write(currentBytes) }
    }

    private suspend fun convertImagesToPdf(
        inputs: List<InputFileItem>,
        options: ConversionOptions,
        onProgress: (Float, String) -> Unit
    ): List<ConversionJobResult> {
        val total = inputs.size
        val mergeSingle = options.mergeIntoSinglePdf || total == 1
        val editSpec = options.imageEditSpec

        if (!mergeSingle) {
            val results = mutableListOf<ConversionJobResult>()
            inputs.forEachIndexed { index, item ->
                coroutineContext.ensureActive()
                val resolvedName = OutputNamingTemplateResolver.resolveBaseName(
                    templateOrCustomName = options.customOutputBaseName,
                    originalBaseName = item.baseNameWithoutExtension,
                    batchIndexOneBased = index + 1,
                    batchTotalCount = total,
                    targetFormat = SupportedFormat.PDF
                )
                val subOptions = options.copy(
                    mergeIntoSinglePdf = true,
                    customOutputBaseName = resolvedName
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
                val progress = (index.toFloat() / total.toFloat()) * 0.88f
                onProgress(progress, "Añadiendo imagen ${index + 1} de $total al PDF: ${item.displayName}")

                val rawBitmap = decodeInputToBitmap(
                    item = item,
                    maxDimensionPx = options.maxDimensionPx ?: 2480,
                    rotationDegrees = options.normalizedRotationDegrees,
                    opaqueWhiteBackground = true
                )
                val editedBitmap = ImageVisualTransformHelper.applyVisualEditSpec(rawBitmap, editSpec)
                if (editedBitmap !== rawBitmap) rawBitmap.recycle()

                val pageBitmap = if (options.clampedQuality < 98) {
                    val baos = ByteArrayOutputStream()
                    editedBitmap.compress(Bitmap.CompressFormat.JPEG, options.clampedQuality, baos)
                    editedBitmap.recycle()
                    val bytes = baos.toByteArray()
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: throw IOException(
                        "No se pudo recomprimir la imagen para el PDF: ${item.displayName}"
                    )
                } else {
                    editedBitmap
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

            onProgress(0.92f, "Escribiendo documento PDF final…")
            FileOutputStream(tempPdfFile).use { fos ->
                BufferedOutputStream(fos, 64 * 1024).use { bos ->
                    pdfDocument.writeTo(bos)
                    bos.flush()
                }
            }
        } finally {
            pdfDocument.close()
        }

        if (options.pdfPassword.isNotBlank()) {
            onProgress(0.96f, "Cifrando PDF con contraseña…")
            encryptPdfFileInPlace(tempPdfFile, options.pdfPassword)
        }

        val firstInput = inputs.first()
        val baseName = when {
            options.customOutputBaseName.isNotBlank() -> OutputNamingTemplateResolver.resolveBaseName(
                templateOrCustomName = options.customOutputBaseName,
                originalBaseName = firstInput.baseNameWithoutExtension,
                batchIndexOneBased = 1,
                batchTotalCount = 1,
                targetFormat = SupportedFormat.PDF
            )
            inputs.size == 1 -> firstInput.baseNameWithoutExtension
            else -> "${firstInput.baseNameWithoutExtension}_unido_${inputs.size}_imgs"
        }

        val desiredName = UriFileResolver.buildOutputFileName(baseName, "pdf")
        val saved = try {
            storageManager.saveTempFileToDownloads(
                sourceFile = tempPdfFile,
                desiredDisplayName = desiredName,
                mimeType = SupportedFormat.PDF.mimeType,
                subFolder = storageManager.resolveEffectiveSubFolder(
                    relativeFolderPath = firstInput.relativeFolderPath,
                    categorySubfolder = "Documentos",
                    preserveTreeOverride = options.preserveSubfolderStructure
                )
            )
        } finally {
            tempPdfFile.delete()
        }

        onProgress(1f, "PDF generado: ${saved.displayName}")
        val totalSourceBytes = inputs.sumOf { it.sizeBytes }
        val sourceDesc = if (inputs.size == 1) {
            firstInput.displayName
        } else {
            "${firstInput.displayName} (+${inputs.size - 1} más)"
        }

        return listOf(
            ConversionJobResult(
                sourceDisplayName = sourceDesc,
                sourceFormat = firstInput.format,
                sourceSizeBytes = totalSourceBytes,
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

    private fun encryptPdfFileInPlace(pdfFile: File, password: String) {
        val cleanPass = password.trim()
        if (cleanPass.isEmpty()) return
        PDDocument.load(pdfFile).use { doc ->
            val accessPermission = AccessPermission()
            val policy = StandardProtectionPolicy(cleanPass, cleanPass, accessPermission).apply {
                encryptionKeyLength = 128
                permissions = accessPermission
            }
            doc.protect(policy)
            doc.save(pdfFile)
        }
    }

    private suspend fun extractPdfPagesToImages(
        inputs: List<InputFileItem>,
        targetFormat: SupportedFormat,
        options: ConversionOptions,
        onProgress: (Float, String) -> Unit
    ): List<ConversionJobResult> {
        val results = mutableListOf<ConversionJobResult>()
        val totalFiles = inputs.size
        val editSpec = options.imageEditSpec

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

                        val visualEditsForFile = if (options.hasVisualPdfEdits) {
                            PdfPageEditItem.fromJsonArrayString(options.pdfVisualPageEditsJson)
                                .filter { it.included && !it.isBlankSeparator && it.sourceUriString == item.uri.toString() }
                        } else {
                            emptyList()
                        }

                        val pageIndexAndRotationList: List<Pair<Int, Int>> = if (visualEditsForFile.isNotEmpty()) {
                            visualEditsForFile
                                .filter { it.originalPageIndex in 0 until pageCount }
                                .map { it.originalPageIndex to ((it.normalizedRotation + options.normalizedRotationDegrees) % 360) }
                        } else if (options.hasVisualPdfEdits) {
                            emptyList()
                        } else {
                            parsePageIndices(options.pageRangeSpec, pageCount)
                                .map { it to options.normalizedRotationDegrees }
                        }

                        val totalPagesToRender = pageIndexAndRotationList.size.coerceAtLeast(1)
                        val approxSourceBytesPerPage = (item.sizeBytes / pageCount.coerceAtLeast(1)).coerceAtLeast(1L)

                        pageIndexAndRotationList.forEachIndexed { idx, (pageIndex, combinedRotation) ->
                            coroutineContext.ensureActive()
                            val fileBaseProgress = fileIdx.toFloat() / totalFiles.toFloat()
                            val pageFraction = idx.toFloat() / totalPagesToRender.toFloat()
                            val overallProgress = fileBaseProgress + (pageFraction / totalFiles.toFloat())
                            onProgress(
                                overallProgress.coerceIn(0f, 0.99f),
                                "Renderizando página ${pageIndex + 1} de $pageCount (${item.displayName})"
                            )

                            renderer.openPage(pageIndex).use { page ->
                                val defaultScale = 2.0f
                                var targetW = (page.width * defaultScale).roundToInt().coerceAtLeast(1)
                                var targetH = (page.height * defaultScale).roundToInt().coerceAtLeast(1)

                                val maxDim = options.maxDimensionPx
                                if (maxDim != null && maxDim > 0 && max(targetW, targetH) > maxDim) {
                                    val ratio = maxDim.toFloat() / max(targetW, targetH).toFloat()
                                    targetW = (targetW * ratio).roundToInt().coerceAtLeast(1)
                                    targetH = (targetH * ratio).roundToInt().coerceAtLeast(1)
                                }

                                val rawBitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                                val finalBitmap: Bitmap
                                try {
                                    val canvas = Canvas(rawBitmap)
                                    canvas.drawColor(Color.WHITE)
                                    page.render(
                                        rawBitmap,
                                        Rect(0, 0, targetW, targetH),
                                        null,
                                        PdfRenderer.Page.RENDER_MODE_FOR_PRINT
                                    )
                                    val rotated = rotateBitmapIfNeeded(rawBitmap, combinedRotation)
                                    finalBitmap = ImageVisualTransformHelper.applyVisualEditSpec(rotated, editSpec)
                                    if (finalBitmap !== rotated) rotated.recycle()
                                } catch (t: Throwable) {
                                    rawBitmap.recycle()
                                    throw t
                                }

                                try {
                                    val tempImg = uriResolver.createTempWorkingFile(
                                        prefix = "pdf_page_",
                                        extension = targetFormat.extension
                                    )
                                    try {
                                        encodeBitmapWithTargetSizeConstraint(
                                            bitmap = finalBitmap,
                                            targetFormat = targetFormat,
                                            initialQuality = options.clampedQuality,
                                            targetMaxSizeKb = options.targetImageMaxSizeKb,
                                            outputFile = tempImg
                                        )

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
                                            mimeType = targetFormat.mimeType,
                                            subFolder = storageManager.resolveEffectiveSubFolder(
                                                relativeFolderPath = item.relativeFolderPath,
                                                categorySubfolder = "Imagenes",
                                                preserveTreeOverride = options.preserveSubfolderStructure
                                            )
                                        )

                                        results.add(
                                            ConversionJobResult(
                                                sourceDisplayName = "${item.displayName} (Pág. ${pageIndex + 1})",
                                                sourceFormat = item.format,
                                                sourceSizeBytes = approxSourceBytesPerPage,
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
                                    finalBitmap.recycle()
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

    @Throws(IOException::class)
    private fun decodeInputToBitmap(
        item: InputFileItem,
        maxDimensionPx: Int?,
        rotationDegrees: Int,
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

        val rotatedBitmap = rotateBitmapIfNeeded(scaledBitmap, rotationDegrees)

        return if (opaqueWhiteBackground) {
            flattenAlphaToWhite(rotatedBitmap)
        } else {
            ensureSoftwareBitmap(rotatedBitmap)
        }
    }

    private fun rotateBitmapIfNeeded(source: Bitmap, rotationDegrees: Int): Bitmap {
        val norm = ((rotationDegrees % 360) + 360) % 360
        if (norm == 0) return source
        val sw = ensureSoftwareBitmap(source)
        val matrix = Matrix().apply {
            postRotate(norm.toFloat())
        }
        val rotated = Bitmap.createBitmap(sw, 0, 0, sw.width, sw.height, matrix, true)
        if (rotated !== sw) {
            sw.recycle()
        }
        return rotated
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
            SupportedFormat.ICO -> {
                val maxIcoSide = 256
                val icoBmp = if (bitmap.width > maxIcoSide || bitmap.height > maxIcoSide) {
                    scaleDownIfNeeded(bitmap.copy(Bitmap.Config.ARGB_8888, false), maxIcoSide)
                } else {
                    bitmap
                }
                val pngBytes = ByteArrayOutputStream().use { baos ->
                    icoBmp.compress(Bitmap.CompressFormat.PNG, 100, baos)
                    baos.toByteArray()
                }
                val w = icoBmp.width
                val h = icoBmp.height
                if (icoBmp !== bitmap) icoBmp.recycle()
                IcoPureEncoder.writeIco(
                    listOf(IcoPureEncoder.IcoImageEntry(w, h, pngBytes)),
                    output
                )
            }
            else -> throw IllegalArgumentException("Formato de imagen no soportado: $targetFormat")
        }
        output.flush()
    }

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
        header.put(0x42.toByte()) // 'B'
        header.put(0x4D.toByte()) // 'M'
        header.putInt(fileSize)
        header.putShort(0)
        header.putShort(0)
        header.putInt(54)

        header.putInt(40)
        header.putInt(width)
        header.putInt(height)
        header.putShort(1)
        header.putShort(24)
        header.putInt(0)
        header.putInt(pixelDataSize)
        header.putInt(2835)
        header.putInt(2835)
        header.putInt(0)
        header.putInt(0)

        output.write(header.array())

        val pixels = IntArray(width)
        val rowBuffer = ByteArray(rowStride)
        for (y in (height - 1) downTo 0) {
            bitmap.getPixels(pixels, 0, width, 0, y, width, 1)
            var offset = 0
            for (x in 0 until width) {
                val argb = pixels[x]
                rowBuffer[offset++] = (argb and 0xFF).toByte()
                rowBuffer[offset++] = ((argb shr 8) and 0xFF).toByte()
                rowBuffer[offset++] = ((argb shr 16) and 0xFF).toByte()
            }
            for (p in 0 until rowPadding) {
                rowBuffer[offset++] = 0
            }
            output.write(rowBuffer)
        }
    }

    companion object {
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
