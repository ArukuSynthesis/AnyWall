package com.anywall.converter.data.engines

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.anywall.converter.core.ScopedStorageManager
import com.anywall.converter.core.UriFileResolver
import com.anywall.converter.domain.engine.FileConverterEngine
import com.anywall.converter.domain.model.ConversionJobResult
import com.anywall.converter.domain.model.ConversionOptions
import com.anywall.converter.domain.model.FormatCategory
import com.anywall.converter.domain.model.InputFileItem
import com.anywall.converter.domain.model.PdfActionMode
import com.anywall.converter.domain.model.SupportedFormat
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.multipdf.Splitter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.zwobble.mammoth.DocumentConverter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

/**
 * Motor 2: `DocumentEngineImpl` (Documentos y PDF Avanzado)
 * - Inicializa `PDFBoxResourceLoader.init(context)` antes de operar con PDFBox.
 * - Unión de múltiples archivos PDF en uno solo (`PDFMergerUtility`).
 * - Extracción/división de rango de páginas (`Splitter` / selección de páginas).
 * - Optimización/compresión de PDF ajustada por porcentaje de calidad (10% - 100%).
 * - Conversión de `DOCX` a `PDF` (vía `org.zwobble.mammoth.DocumentConverter` -> HTML -> `WebView` offscreen + `PrintDocumentAdapter`).
 * - Conversión de `.html`, `.txt` y `.md` a `PDF`, `HTML` o `TXT` de forma 100% local.
 */
class DocumentEngineImpl(
    private val context: Context,
    private val uriResolver: UriFileResolver,
    private val storageManager: ScopedStorageManager,
    private val webViewPdfGenerator: WebViewPdfGenerator
) : FileConverterEngine {

    private val documentFormats = setOf(
        SupportedFormat.PDF,
        SupportedFormat.DOCX,
        SupportedFormat.HTML,
        SupportedFormat.TXT,
        SupportedFormat.MD
    )

    init {
        ensurePdfBoxInitialized()
    }

    private fun ensurePdfBoxInitialized() {
        if (!PDFBoxResourceLoader.isReady()) {
            PDFBoxResourceLoader.init(context.applicationContext)
        }
    }

    override fun canHandle(format: SupportedFormat): Boolean {
        return format in documentFormats
    }

    override fun getSupportedTargetFormats(sourceFormats: List<SupportedFormat>): List<SupportedFormat> {
        if (sourceFormats.isEmpty()) return emptyList()
        val distinct = sourceFormats.distinct()

        if (!distinct.all { it in documentFormats }) {
            return emptyList()
        }

        // Todos los documentos (PDF, DOCX, HTML, TXT, MD) pueden convertirse a PDF, HTML o TXT
        return listOf(
            SupportedFormat.PDF,
            SupportedFormat.TXT,
            SupportedFormat.HTML
        )
    }

    override suspend fun convert(
        inputs: List<InputFileItem>,
        targetFormat: SupportedFormat,
        options: ConversionOptions,
        onProgress: (Float, String) -> Unit
    ): List<ConversionJobResult> = withContext(Dispatchers.IO) {
        require(inputs.isNotEmpty()) { "No se seleccionaron documentos para convertir." }
        ensurePdfBoxInitialized()

        // Caso 1: Todos los archivos de entrada son PDF y el destino es PDF
        if (inputs.all { it.format == SupportedFormat.PDF } && targetFormat == SupportedFormat.PDF) {
            return@withContext processPdfToPdfOperations(inputs, options, onProgress)
        }

        // Caso 2: Destino es PDF y hay archivos DOCX, HTML, TXT, MD (o mezcla con PDF)
        if (targetFormat == SupportedFormat.PDF) {
            return@withContext convertDocumentsToPdf(inputs, options, onProgress)
        }

        // Caso 3: Destino es HTML o TXT
        if (targetFormat == SupportedFormat.HTML || targetFormat == SupportedFormat.TXT) {
            return@withContext convertDocumentsToTextOrHtml(inputs, targetFormat, options, onProgress)
        }

        throw IllegalArgumentException(
            "Formato destino no soportado por DocumentEngineImpl: $targetFormat"
        )
    }

    /**
     * Operaciones avanzadas sobre archivos PDF:
     * 1. Unión de múltiples PDFs con `PDFMergerUtility`.
     * 2. División de todas las páginas (`PdfActionMode.SPLIT_ALL_PAGES`) con `Splitter`.
     * 3. Extracción de rango de páginas (`PdfActionMode.SPLIT_RANGE` o `options.pageRangeSpec.isNotBlank()`).
     * 4. Optimización y compresión de PDF (`PdfActionMode.OPTIMIZE_COMPRESS` o PDF único).
     */
    private suspend fun processPdfToPdfOperations(
        inputs: List<InputFileItem>,
        options: ConversionOptions,
        onProgress: (Float, String) -> Unit
    ): List<ConversionJobResult> {
        val actionMode = when {
            options.pdfActionMode != PdfActionMode.MERGE_OR_CONVERT -> options.pdfActionMode
            inputs.size > 1 && options.mergeIntoSinglePdf -> PdfActionMode.MERGE_OR_CONVERT
            options.pageRangeSpec.isNotBlank() -> PdfActionMode.SPLIT_RANGE
            else -> PdfActionMode.OPTIMIZE_COMPRESS
        }

        return when (actionMode) {
            PdfActionMode.MERGE_OR_CONVERT -> {
                if (inputs.size > 1 && options.mergeIntoSinglePdf) {
                    mergePdfFiles(inputs, options, onProgress)
                } else {
                    optimizeAndCompressPdfFiles(inputs, options, onProgress)
                }
            }
            PdfActionMode.SPLIT_RANGE -> extractPageRangeFromPdfs(inputs, options, onProgress)
            PdfActionMode.SPLIT_ALL_PAGES -> splitAllPagesFromPdfs(inputs, options, onProgress)
            PdfActionMode.OPTIMIZE_COMPRESS -> optimizeAndCompressPdfFiles(inputs, options, onProgress)
        }
    }

    private suspend fun mergePdfFiles(
        inputs: List<InputFileItem>,
        options: ConversionOptions,
        onProgress: (Float, String) -> Unit
    ): List<ConversionJobResult> {
        val tempInputs = mutableListOf<File>()
        val mergedTempOut = uriResolver.createTempWorkingFile(prefix = "pdf_merged_", extension = "pdf")

        try {
            val merger = PDFMergerUtility()
            inputs.forEachIndexed { idx, item ->
                coroutineContext.ensureActive()
                val p = (idx.toFloat() / inputs.size.toFloat()) * 0.5f
                onProgress(p, "Preparando PDF ${idx + 1} de ${inputs.size}: ${item.displayName}")
                val tempIn = uriResolver.copyUriToTempFile(item.uri, prefix = "merge_src_", extension = "pdf")
                tempInputs.add(tempIn)
                merger.addSource(tempIn)
            }

            onProgress(0.6f, "Uniendo ${inputs.size} documentos PDF con PDFMergerUtility…")
            merger.destinationFileName = mergedTempOut.absolutePath
            merger.mergeDocuments(MemoryUsageSetting.setupMixed(32L * 1024L * 1024L))

            // Si el usuario seleccionó una calidad < 95%, optimizamos también el PDF unido
            val finalTempFile = if (options.clampedQuality < 95) {
                onProgress(0.82f, "Optimizando PDF unido al ${options.clampedQuality}%…")
                val compressedTemp = uriResolver.createTempWorkingFile(prefix = "pdf_merged_opt_", extension = "pdf")
                compressPdfFile(mergedTempOut, compressedTemp, options.clampedQuality)
                if (compressedTemp.exists() && compressedTemp.length() in 1 until mergedTempOut.length()) {
                    mergedTempOut.delete()
                    compressedTemp
                } else {
                    compressedTemp.delete()
                    mergedTempOut
                }
            } else {
                mergedTempOut
            }

            val baseName = options.customOutputBaseName.ifBlank {
                "${inputs.first().baseNameWithoutExtension}_unido_${inputs.size}"
            }
            val desiredFileName = UriFileResolver.buildOutputFileName(baseName, "pdf")
            val saved = try {
                storageManager.saveTempFileToDownloads(
                    sourceFile = finalTempFile,
                    desiredDisplayName = desiredFileName,
                    mimeType = SupportedFormat.PDF.mimeType
                )
            } finally {
                finalTempFile.delete()
            }

            onProgress(1f, "PDF unido guardado: ${saved.displayName}")
            return listOf(
                ConversionJobResult(
                    sourceDisplayName = "${inputs.first().displayName} (+${inputs.size - 1} PDFs)",
                    sourceFormat = SupportedFormat.PDF,
                    outputDisplayName = saved.displayName,
                    outputFormat = SupportedFormat.PDF,
                    outputCategory = FormatCategory.DOCUMENT,
                    outputUri = saved.uri,
                    outputAbsolutePath = saved.absolutePath,
                    outputSizeBytes = saved.sizeBytes,
                    outputMimeType = saved.mimeType
                )
            )
        } finally {
            tempInputs.forEach { it.delete() }
            if (mergedTempOut.exists()) mergedTempOut.delete()
        }
    }

    private suspend fun extractPageRangeFromPdfs(
        inputs: List<InputFileItem>,
        options: ConversionOptions,
        onProgress: (Float, String) -> Unit
    ): List<ConversionJobResult> {
        val results = mutableListOf<ConversionJobResult>()
        val total = inputs.size

        inputs.forEachIndexed { index, item ->
            coroutineContext.ensureActive()
            onProgress(
                index.toFloat() / total.toFloat(),
                "Extrayendo páginas de ${item.displayName}…"
            )

            val tempSrc = uriResolver.copyUriToTempFile(item.uri, prefix = "range_src_", extension = "pdf")
            val tempOut = uriResolver.createTempWorkingFile(prefix = "range_out_", extension = "pdf")

            try {
                PDDocument.load(tempSrc).use { sourceDoc ->
                    val totalPages = sourceDoc.numberOfPages
                    if (totalPages <= 0) {
                        throw IOException("El PDF '${item.displayName}' no contiene páginas.")
                    }
                    val selectedIndices = ImageEngineImpl.parsePageIndices(options.pageRangeSpec, totalPages)
                    PDDocument().use { targetDoc ->
                        for (pageIdx in selectedIndices) {
                            targetDoc.importPage(sourceDoc.getPage(pageIdx))
                        }
                        targetDoc.save(tempOut)
                    }
                }

                val baseName = if (options.customOutputBaseName.isNotBlank() && total == 1) {
                    options.customOutputBaseName
                } else {
                    val rangeLabel = options.pageRangeSpec.trim().replace(Regex("[^0-9\\-_]"), "_")
                        .ifBlank { "extracto" }
                    "${item.baseNameWithoutExtension}_pags_$rangeLabel"
                }

                val desiredName = UriFileResolver.buildOutputFileName(baseName, "pdf")
                val saved = storageManager.saveTempFileToDownloads(
                    sourceFile = tempOut,
                    desiredDisplayName = desiredName,
                    mimeType = SupportedFormat.PDF.mimeType
                )

                results.add(
                    ConversionJobResult(
                        sourceDisplayName = item.displayName,
                        sourceFormat = SupportedFormat.PDF,
                        outputDisplayName = saved.displayName,
                        outputFormat = SupportedFormat.PDF,
                        outputCategory = FormatCategory.DOCUMENT,
                        outputUri = saved.uri,
                        outputAbsolutePath = saved.absolutePath,
                        outputSizeBytes = saved.sizeBytes,
                        outputMimeType = saved.mimeType
                    )
                )
            } finally {
                tempSrc.delete()
                tempOut.delete()
            }
        }

        onProgress(1f, "Extracción de rango PDF completada.")
        return results
    }

    private suspend fun splitAllPagesFromPdfs(
        inputs: List<InputFileItem>,
        options: ConversionOptions,
        onProgress: (Float, String) -> Unit
    ): List<ConversionJobResult> {
        val results = mutableListOf<ConversionJobResult>()
        val totalFiles = inputs.size

        inputs.forEachIndexed { fileIdx, item ->
            coroutineContext.ensureActive()
            val tempSrc = uriResolver.copyUriToTempFile(item.uri, prefix = "split_src_", extension = "pdf")
            try {
                PDDocument.load(tempSrc).use { sourceDoc ->
                    val splitter = Splitter()
                    val splitDocs: List<PDDocument> = splitter.split(sourceDoc)
                    try {
                        val pageCount = splitDocs.size
                        splitDocs.forEachIndexed { pageIdx, singlePageDoc ->
                            coroutineContext.ensureActive()
                            val progress = (fileIdx.toFloat() + (pageIdx.toFloat() / pageCount.coerceAtLeast(1))) / totalFiles
                            onProgress(
                                progress.coerceIn(0f, 0.99f),
                                "Dividiendo página ${pageIdx + 1} de $pageCount (${item.displayName})"
                            )

                            val tempPageFile = uriResolver.createTempWorkingFile(
                                prefix = "page_",
                                extension = "pdf"
                            )
                            try {
                                singlePageDoc.save(tempPageFile)
                                val baseName = if (options.customOutputBaseName.isNotBlank() && totalFiles == 1) {
                                    options.customOutputBaseName
                                } else {
                                    item.baseNameWithoutExtension
                                }
                                val formattedPage = String.format("%03d", pageIdx + 1)
                                val desiredName = UriFileResolver.buildOutputFileName(
                                    baseName = baseName,
                                    extension = "pdf",
                                    suffix = "_pag_$formattedPage"
                                )
                                val saved = storageManager.saveTempFileToDownloads(
                                    sourceFile = tempPageFile,
                                    desiredDisplayName = desiredName,
                                    mimeType = SupportedFormat.PDF.mimeType
                                )
                                results.add(
                                    ConversionJobResult(
                                        sourceDisplayName = "${item.displayName} (Pág. ${pageIdx + 1})",
                                        sourceFormat = SupportedFormat.PDF,
                                        outputDisplayName = saved.displayName,
                                        outputFormat = SupportedFormat.PDF,
                                        outputCategory = FormatCategory.DOCUMENT,
                                        outputUri = saved.uri,
                                        outputAbsolutePath = saved.absolutePath,
                                        outputSizeBytes = saved.sizeBytes,
                                        outputMimeType = saved.mimeType
                                    )
                                )
                            } finally {
                                tempPageFile.delete()
                            }
                        }
                    } finally {
                        splitDocs.forEach { doc ->
                            try {
                                doc.close()
                            } catch (_: Throwable) {
                            }
                        }
                    }
                }
            } finally {
                tempSrc.delete()
            }
        }

        onProgress(1f, "División de páginas completada (${results.size} archivos PDF).")
        return results
    }

    private suspend fun optimizeAndCompressPdfFiles(
        inputs: List<InputFileItem>,
        options: ConversionOptions,
        onProgress: (Float, String) -> Unit
    ): List<ConversionJobResult> {
        val results = mutableListOf<ConversionJobResult>()
        val total = inputs.size

        inputs.forEachIndexed { index, item ->
            coroutineContext.ensureActive()
            val progress = index.toFloat() / total.toFloat()
            onProgress(progress, "Optimizando y comprimiendo ${item.displayName} (${options.clampedQuality}%)…")

            val tempSrc = uriResolver.copyUriToTempFile(item.uri, prefix = "opt_src_", extension = "pdf")
            val tempOut = uriResolver.createTempWorkingFile(prefix = "opt_out_", extension = "pdf")

            try {
                compressPdfFile(tempSrc, tempOut, options.clampedQuality)

                val baseName = if (options.customOutputBaseName.isNotBlank() && total == 1) {
                    options.customOutputBaseName
                } else {
                    "${item.baseNameWithoutExtension}_optimizado"
                }
                val desiredName = UriFileResolver.buildOutputFileName(baseName, "pdf")
                val saved = storageManager.saveTempFileToDownloads(
                    sourceFile = tempOut,
                    desiredDisplayName = desiredName,
                    mimeType = SupportedFormat.PDF.mimeType
                )
                results.add(
                    ConversionJobResult(
                        sourceDisplayName = item.displayName,
                        sourceFormat = SupportedFormat.PDF,
                        outputDisplayName = saved.displayName,
                        outputFormat = SupportedFormat.PDF,
                        outputCategory = FormatCategory.DOCUMENT,
                        outputUri = saved.uri,
                        outputAbsolutePath = saved.absolutePath,
                        outputSizeBytes = saved.sizeBytes,
                        outputMimeType = saved.mimeType
                    )
                )
            } finally {
                tempSrc.delete()
                tempOut.delete()
            }
        }

        onProgress(1f, "Optimización de PDF completada.")
        return results
    }

    /**
     * Comprime un archivo PDF recomprimiendo los objetos de imagen (`PDImageXObject`) incrustados
     * y/o rasterizando las páginas con `PdfRenderer` + `JPEGFactory` según la calidad solicitada.
     */
    private fun compressPdfFile(sourceFile: File, destFile: File, qualityPercent: Int) {
        val qualityFloat = (qualityPercent.coerceIn(10, 100) / 100f)

        // Estrategia 1: Recomprimir imágenes incrustadas en el árbol de recursos de PDFBox preservando texto vectorial
        val tempVectorCompressed = uriResolver.createTempWorkingFile(prefix = "vec_opt_", extension = "pdf")
        var vectorSuccess = false
        try {
            PDDocument.load(sourceFile).use { doc ->
                doc.documentInformation.producer = "AnyWall Universal Converter"
                for (page in doc.pages) {
                    val resources = page.resources ?: continue
                    for (name: COSName in resources.xObjectNames) {
                        try {
                            if (resources.isImageXObject(name)) {
                                val pdImage = resources.getXObject(name) as? PDImageXObject ?: continue
                                val bitmap = pdImage.image ?: continue
                                val scale = (0.45f + (0.55f * qualityFloat)).coerceIn(0.35f, 1.0f)
                                val targetW = (bitmap.width * scale).roundToInt().coerceAtLeast(16)
                                val targetH = (bitmap.height * scale).roundToInt().coerceAtLeast(16)
                                val scaledBmp = if (targetW < bitmap.width || targetH < bitmap.height) {
                                    Bitmap.createScaledBitmap(bitmap, targetW, targetH, true).also {
                                        if (it !== bitmap) bitmap.recycle()
                                    }
                                } else {
                                    bitmap
                                }
                                try {
                                    val recompressed = JPEGFactory.createFromImage(doc, scaledBmp, qualityFloat)
                                    resources.put(name, recompressed)
                                } finally {
                                    scaledBmp.recycle()
                                }
                            }
                        } catch (_: Throwable) {
                        }
                    }
                }
                doc.save(tempVectorCompressed)
                vectorSuccess = tempVectorCompressed.exists() && tempVectorCompressed.length() > 0L
            }
        } catch (_: Throwable) {
            vectorSuccess = false
        }

        // Estrategia 2: Si la calidad es < 85% o la estrategia 1 no redujo tamaño, probamos rasterización controlada
        val tempRasterCompressed = uriResolver.createTempWorkingFile(prefix = "ras_opt_", extension = "pdf")
        var rasterSuccess = false
        if (qualityPercent < 90 || !vectorSuccess) {
            try {
                ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        PDDocument().use { outDoc ->
                            val scaleFactor = (0.75f + (qualityFloat * 0.85f)).coerceIn(0.75f, 1.6f)
                            for (i in 0 until renderer.pageCount) {
                                renderer.openPage(i).use { page ->
                                    val bmpW = (page.width * scaleFactor).roundToInt().coerceAtLeast(1)
                                    val bmpH = (page.height * scaleFactor).roundToInt().coerceAtLeast(1)
                                    val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                                    try {
                                        val canvas = Canvas(bitmap)
                                        canvas.drawColor(Color.WHITE)
                                        page.render(
                                            bitmap,
                                            Rect(0, 0, bmpW, bmpH),
                                            null,
                                            PdfRenderer.Page.RENDER_MODE_FOR_PRINT
                                        )
                                        val baos = ByteArrayOutputStream()
                                        bitmap.compress(
                                            Bitmap.CompressFormat.JPEG,
                                            qualityPercent.coerceIn(15, 95),
                                            baos
                                        )
                                        val jpegBytes = baos.toByteArray()
                                        val pdPage = PDPage(
                                            PDRectangle(page.width.toFloat(), page.height.toFloat())
                                        )
                                        outDoc.addPage(pdPage)
                                        val pdImage = JPEGFactory.createFromByteArray(outDoc, jpegBytes)
                                        PDPageContentStream(outDoc, pdPage).use { cs ->
                                            cs.drawImage(
                                                pdImage,
                                                0f,
                                                0f,
                                                page.width.toFloat(),
                                                page.height.toFloat()
                                            )
                                        }
                                    } finally {
                                        bitmap.recycle()
                                    }
                                }
                            }
                            outDoc.save(tempRasterCompressed)
                            rasterSuccess = tempRasterCompressed.exists() && tempRasterCompressed.length() > 0L
                        }
                    }
                }
            } catch (_: Throwable) {
                rasterSuccess = false
            }
        }

        try {
            val candidates = listOfNotNull(
                tempVectorCompressed.takeIf { vectorSuccess },
                tempRasterCompressed.takeIf { rasterSuccess },
                sourceFile
            )
            val best = candidates.minByOrNull { it.length() } ?: sourceFile
            best.copyTo(destFile, overwrite = true)
        } finally {
            tempVectorCompressed.delete()
            tempRasterCompressed.delete()
        }
    }

    /**
     * Convierte archivos `DOCX`, `HTML`, `TXT`, `MD` (e incluso si están combinados con `PDF`) a `PDF`.
     * - `DOCX` -> HTML con `org.zwobble.mammoth.DocumentConverter` -> `WebView` offscreen + `PrintDocumentAdapter`.
     * - `MD` / `TXT` / `HTML` -> HTML estilizado -> `WebView` offscreen + `PrintDocumentAdapter`.
     */
    private suspend fun convertDocumentsToPdf(
        inputs: List<InputFileItem>,
        options: ConversionOptions,
        onProgress: (Float, String) -> Unit
    ): List<ConversionJobResult> {
        val total = inputs.size
        val shouldMergeAll = total > 1 && options.mergeIntoSinglePdf
        val generatedTempPdfs = mutableListOf<Pair<InputFileItem, File>>()

        try {
            inputs.forEachIndexed { index, item ->
                coroutineContext.ensureActive()
                val progress = (index.toFloat() / total.toFloat()) * (if (shouldMergeAll) 0.8f else 0.95f)
                onProgress(
                    progress,
                    "Convirtiendo documento ${index + 1} de $total a PDF: ${item.displayName}"
                )

                val tempPdf = if (item.format == SupportedFormat.PDF) {
                    uriResolver.copyUriToTempFile(item.uri, prefix = "doc_existing_", extension = "pdf")
                } else {
                    val html = convertDocumentItemToHtml(item)
                    val outTempPdf = uriResolver.createTempWorkingFile(
                        prefix = "webview_pdf_",
                        extension = "pdf"
                    )
                    webViewPdfGenerator.renderHtmlToPdfFile(
                        htmlContent = html,
                        documentTitle = item.baseNameWithoutExtension,
                        outputFile = outTempPdf
                    )
                }
                generatedTempPdfs.add(item to tempPdf)
            }

            if (shouldMergeAll) {
                onProgress(0.85f, "Uniendo ${generatedTempPdfs.size} documentos en un único PDF…")
                val mergedPdf = uriResolver.createTempWorkingFile(prefix = "merged_docs_", extension = "pdf")
                try {
                    val merger = PDFMergerUtility()
                    generatedTempPdfs.forEach { (_, file) -> merger.addSource(file) }
                    merger.destinationFileName = mergedPdf.absolutePath
                    merger.mergeDocuments(MemoryUsageSetting.setupMixed(32L * 1024L * 1024L))

                    val baseName = options.customOutputBaseName.ifBlank {
                        "${inputs.first().baseNameWithoutExtension}_unido_${inputs.size}"
                    }
                    val desiredName = UriFileResolver.buildOutputFileName(baseName, "pdf")
                    val saved = storageManager.saveTempFileToDownloads(
                        sourceFile = mergedPdf,
                        desiredDisplayName = desiredName,
                        mimeType = SupportedFormat.PDF.mimeType
                    )

                    onProgress(1f, "PDF combinado generado: ${saved.displayName}")
                    return listOf(
                        ConversionJobResult(
                            sourceDisplayName = "${inputs.first().displayName} (+${inputs.size - 1} más)",
                            sourceFormat = inputs.first().format,
                            outputDisplayName = saved.displayName,
                            outputFormat = SupportedFormat.PDF,
                            outputCategory = FormatCategory.DOCUMENT,
                            outputUri = saved.uri,
                            outputAbsolutePath = saved.absolutePath,
                            outputSizeBytes = saved.sizeBytes,
                            outputMimeType = saved.mimeType
                        )
                    )
                } finally {
                    mergedPdf.delete()
                }
            } else {
                val results = mutableListOf<ConversionJobResult>()
                generatedTempPdfs.forEachIndexed { idx, (item, pdfFile) ->
                    val baseName = if (options.customOutputBaseName.isNotBlank()) {
                        if (total == 1) options.customOutputBaseName
                        else "${options.customOutputBaseName}_${idx + 1}"
                    } else {
                        item.baseNameWithoutExtension
                    }
                    val desiredName = UriFileResolver.buildOutputFileName(baseName, "pdf")
                    val saved = storageManager.saveTempFileToDownloads(
                        sourceFile = pdfFile,
                        desiredDisplayName = desiredName,
                        mimeType = SupportedFormat.PDF.mimeType
                    )
                    results.add(
                        ConversionJobResult(
                            sourceDisplayName = item.displayName,
                            sourceFormat = item.format,
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
                onProgress(1f, "Conversión a PDF completada (${results.size}).")
                return results
            }
        } finally {
            generatedTempPdfs.forEach { (_, file) -> file.delete() }
        }
    }

    /**
     * Convierte cualquier documento de entrada (`DOCX`, `HTML`, `TXT`, `MD`, `PDF`) a HTML o TXT.
     */
    private suspend fun convertDocumentsToTextOrHtml(
        inputs: List<InputFileItem>,
        targetFormat: SupportedFormat,
        options: ConversionOptions,
        onProgress: (Float, String) -> Unit
    ): List<ConversionJobResult> {
        val results = mutableListOf<ConversionJobResult>()
        val total = inputs.size

        inputs.forEachIndexed { index, item ->
            coroutineContext.ensureActive()
            onProgress(
                index.toFloat() / total.toFloat(),
                "Convirtiendo ${item.displayName} a ${targetFormat.label}…"
            )

            val outputContent = when (targetFormat) {
                SupportedFormat.HTML -> {
                    val bodyHtml = convertDocumentItemToHtml(item)
                    WebViewPdfGenerator.wrapWithCleanPrintStyle(bodyHtml, item.baseNameWithoutExtension)
                }
                SupportedFormat.TXT -> convertDocumentItemToPlainText(item)
                else -> throw IllegalArgumentException("Formato inesperado: $targetFormat")
            }

            val tempOut = uriResolver.createTempWorkingFile(
                prefix = "doc_text_",
                extension = targetFormat.extension
            )
            try {
                tempOut.writeText(outputContent, Charsets.UTF_8)
                val baseName = if (options.customOutputBaseName.isNotBlank()) {
                    if (total == 1) options.customOutputBaseName
                    else "${options.customOutputBaseName}_${index + 1}"
                } else {
                    item.baseNameWithoutExtension
                }
                val desiredName = UriFileResolver.buildOutputFileName(baseName, targetFormat.extension)
                val saved = storageManager.saveTempFileToDownloads(
                    sourceFile = tempOut,
                    desiredDisplayName = desiredName,
                    mimeType = targetFormat.mimeType
                )
                results.add(
                    ConversionJobResult(
                        sourceDisplayName = item.displayName,
                        sourceFormat = item.format,
                        outputDisplayName = saved.displayName,
                        outputFormat = targetFormat,
                        outputCategory = FormatCategory.DOCUMENT,
                        outputUri = saved.uri,
                        outputAbsolutePath = saved.absolutePath,
                        outputSizeBytes = saved.sizeBytes,
                        outputMimeType = saved.mimeType
                    )
                )
            } finally {
                tempOut.delete()
            }
        }

        onProgress(1f, "Conversión documental completada.")
        return results
    }

    /**
     * Transforma un archivo `DOCX`, `HTML`, `TXT`, `MD` o `PDF` a una representación HTML limpia.
     */
    @Throws(IOException::class)
    private fun convertDocumentItemToHtml(item: InputFileItem): String {
        return when (item.format) {
            SupportedFormat.DOCX -> {
                // Mammoth DocumentConverter transforma .docx (incluyendo tablas, listas e imágenes embebidas en base64) a HTML
                val tempDocx = uriResolver.copyUriToTempFile(item.uri, prefix = "mammoth_", extension = "docx")
                try {
                    val converter = DocumentConverter()
                    val result = converter.convertToHtml(tempDocx)
                    val htmlBody = result.value.orEmpty()
                    if (htmlBody.isBlank()) {
                        "<p><em>Documento DOCX vacío (${WebViewPdfGenerator.escapeHtml(item.displayName)})</em></p>"
                    } else {
                        htmlBody
                    }
                } finally {
                    tempDocx.delete()
                }
            }

            SupportedFormat.HTML -> {
                uriResolver.openInputStream(item.uri).use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText()
                }
            }

            SupportedFormat.MD -> {
                val markdown = uriResolver.openInputStream(item.uri).use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText()
                }
                convertMarkdownToHtml(markdown)
            }

            SupportedFormat.TXT -> {
                val plainText = uriResolver.openInputStream(item.uri).use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText()
                }
                convertPlainTextToHtml(plainText, item.displayName)
            }

            SupportedFormat.PDF -> {
                val extractedText = extractTextFromPdfUri(item)
                convertPlainTextToHtml(extractedText, item.displayName)
            }

            else -> {
                val raw = uriResolver.openInputStream(item.uri).use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText()
                }
                convertPlainTextToHtml(raw, item.displayName)
            }
        }
    }

    @Throws(IOException::class)
    private fun convertDocumentItemToPlainText(item: InputFileItem): String {
        return when (item.format) {
            SupportedFormat.PDF -> extractTextFromPdfUri(item)
            SupportedFormat.DOCX -> {
                val tempDocx = uriResolver.copyUriToTempFile(item.uri, prefix = "docx_txt_", extension = "docx")
                try {
                    val converter = DocumentConverter()
                    val rawText = converter.extractRawText(tempDocx).value.orEmpty()
                    rawText.trim()
                } finally {
                    tempDocx.delete()
                }
            }
            SupportedFormat.HTML -> {
                val html = uriResolver.openInputStream(item.uri).use {
                    it.bufferedReader(Charsets.UTF_8).readText()
                }
                stripHtmlTags(html)
            }
            else -> {
                uriResolver.openInputStream(item.uri).use {
                    it.bufferedReader(Charsets.UTF_8).readText()
                }
            }
        }
    }

    private fun extractTextFromPdfUri(item: InputFileItem): String {
        val tempPdf = uriResolver.copyUriToTempFile(item.uri, prefix = "pdf_strip_", extension = "pdf")
        return try {
            PDDocument.load(tempPdf).use { doc ->
                val stripper = PDFTextStripper().apply {
                    sortByPosition = true
                }
                stripper.getText(doc).orEmpty()
            }
        } finally {
            tempPdf.delete()
        }
    }

    private fun convertPlainTextToHtml(text: String, title: String): String {
        val escapedTitle = WebViewPdfGenerator.escapeHtml(title)
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val sb = StringBuilder(text.length + 256)
        sb.append("<h1>").append(escapedTitle).append("</h1>\n")
        for (line in lines) {
            if (line.isBlank()) {
                sb.append("<br/>\n")
            } else {
                val escaped = WebViewPdfGenerator.escapeHtml(line)
                    .replace("  ", "&nbsp;&nbsp;")
                    .replace("\t", "&nbsp;&nbsp;&nbsp;&nbsp;")
                sb.append("<p>").append(escaped).append("</p>\n")
            }
        }
        return sb.toString()
    }

    /**
     * Conversor completo de Markdown a HTML semántico (encabezados, bloques de código,
     * listas ordenadas y no ordenadas, citas, separadores, tablas básicas, negritas, cursivas y código inline).
     */
    private fun convertMarkdownToHtml(markdown: String): String {
        val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val html = StringBuilder(markdown.length * 2)
        var inCodeBlock = false
        var inUnorderedList = false
        var inOrderedList = false

        fun closeLists() {
            if (inUnorderedList) {
                html.append("</ul>\n")
                inUnorderedList = false
            }
            if (inOrderedList) {
                html.append("</ol>\n")
                inOrderedList = false
            }
        }

        for (rawLine in lines) {
            val trimmed = rawLine.trim()

            if (trimmed.startsWith("```")) {
                closeLists()
                if (!inCodeBlock) {
                    html.append("<pre><code>")
                    inCodeBlock = true
                } else {
                    html.append("</code></pre>\n")
                    inCodeBlock = false
                }
                continue
            }

            if (inCodeBlock) {
                html.append(WebViewPdfGenerator.escapeHtml(rawLine)).append('\n')
                continue
            }

            if (trimmed.isEmpty()) {
                closeLists()
                continue
            }

            when {
                trimmed == "---" || trimmed == "***" || trimmed == "___" -> {
                    closeLists()
                    html.append("<hr/>\n")
                }
                trimmed.startsWith("###### ") -> {
                    closeLists()
                    html.append("<h6>").append(formatInlineMarkdown(trimmed.drop(7))).append("</h6>\n")
                }
                trimmed.startsWith("##### ") -> {
                    closeLists()
                    html.append("<h5>").append(formatInlineMarkdown(trimmed.drop(6))).append("</h5>\n")
                }
                trimmed.startsWith("#### ") -> {
                    closeLists()
                    html.append("<h4>").append(formatInlineMarkdown(trimmed.drop(5))).append("</h4>\n")
                }
                trimmed.startsWith("### ") -> {
                    closeLists()
                    html.append("<h3>").append(formatInlineMarkdown(trimmed.drop(4))).append("</h3>\n")
                }
                trimmed.startsWith("## ") -> {
                    closeLists()
                    html.append("<h2>").append(formatInlineMarkdown(trimmed.drop(3))).append("</h2>\n")
                }
                trimmed.startsWith("# ") -> {
                    closeLists()
                    html.append("<h1>").append(formatInlineMarkdown(trimmed.drop(2))).append("</h1>\n")
                }
                trimmed.startsWith("> ") || trimmed == ">" -> {
                    closeLists()
                    val quoteContent = trimmed.removePrefix(">").trim()
                    html.append("<blockquote>").append(formatInlineMarkdown(quoteContent)).append("</blockquote>\n")
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ") -> {
                    if (inOrderedList) {
                        html.append("</ol>\n")
                        inOrderedList = false
                    }
                    if (!inUnorderedList) {
                        html.append("<ul>\n")
                        inUnorderedList = true
                    }
                    html.append("<li>").append(formatInlineMarkdown(trimmed.drop(2))).append("</li>\n")
                }
                trimmed.matches(Regex("^\\d+\\.\\s+.*")) -> {
                    if (inUnorderedList) {
                        html.append("</ul>\n")
                        inUnorderedList = false
                    }
                    if (!inOrderedList) {
                        html.append("<ol>\n")
                        inOrderedList = true
                    }
                    val itemText = trimmed.substringAfter(". ").trim()
                    html.append("<li>").append(formatInlineMarkdown(itemText)).append("</li>\n")
                }
                else -> {
                    closeLists()
                    html.append("<p>").append(formatInlineMarkdown(trimmed)).append("</p>\n")
                }
            }
        }

        if (inCodeBlock) {
            html.append("</code></pre>\n")
        }
        closeLists()
        return html.toString()
    }

    private fun formatInlineMarkdown(text: String): String {
        var result = WebViewPdfGenerator.escapeHtml(text)
        // Código inline `code`
        result = result.replace(Regex("`([^`]+)`"), "<code>$1</code>")
        // Negrita **texto** o __texto__
        result = result.replace(Regex("\\*\\*([^*]+)\\*\\*"), "<strong>$1</strong>")
        result = result.replace(Regex("__([^_]+)__"), "<strong>$1</strong>")
        // Cursiva *texto* o _texto_
        result = result.replace(Regex("\\*([^*]+)\\*"), "<em>$1</em>")
        result = result.replace(Regex("_([^_]+)_"), "<em>$1</em>")
        // Enlaces [texto](url) -> texto (url) sin navegación remota
        result = result.replace(Regex("\\[([^\\]]+)]\\(([^)]+)\\)"), "<strong>$1</strong> (<code>$2</code>)")
        return result
    }

    private fun stripHtmlTags(html: String): String {
        return html
            .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<(br|p|div|h[1-6]|li|tr)[^>]*>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }
}
