package com.anywall.converter.data.engines

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
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
import com.anywall.converter.domain.model.NormalizedRedactionBox
import com.anywall.converter.domain.model.OutputNamingTemplateResolver
import com.anywall.converter.domain.model.PdfActionMode
import com.anywall.converter.domain.model.PdfPageEditItem
import com.anywall.converter.domain.model.SupportedFormat
import com.caverock.androidsvg.SVG
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.multipdf.Splitter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.util.Matrix as PdfMatrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.zwobble.mammoth.DocumentConverter
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Motor 2: `DocumentEngineImpl` (Documentos, PDF Profesional, Firma, Censura, XLSX y EPUB - v0.3)
 * - Ejecución del Modificador Visual de PDF (`PdfPageEditItem`): reordenamiento, rotación, exclusión,
 *   páginas separadoras en blanco, censura sobre páginas y estampado de firma manuscrita.
 * - Marca de agua diagonal en PDF, numeración automática `"Página X de Y"`, estampado QR opcional.
 * - Nuevos modos PDF: división cada N páginas (`SPLIT_BY_CHUNK`), extractor de imágenes originales embebidas
 *   (`EXTRACT_EMBEDDED_IMAGES`), y modo Folleto/Cuadrícula (`BOOKLET_N_UP_2`, `BOOKLET_N_UP_4`).
 * - Soporte 100% Kotlin puro de hojas de cálculo `.xlsx` (`XlsxPureParser`) y libros `.epub` (`EpubPureConverter`).
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
        SupportedFormat.XLSX,
        SupportedFormat.EPUB,
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
        return format in documentFormats || format.category == FormatCategory.IMAGE
    }

    override fun getSupportedTargetFormats(sourceFormats: List<SupportedFormat>): List<SupportedFormat> {
        if (sourceFormats.isEmpty()) return emptyList()
        val distinct = sourceFormats.distinct()

        if (!distinct.all { it in documentFormats }) {
            return emptyList()
        }

        val targets = mutableListOf(
            SupportedFormat.PDF,
            SupportedFormat.EPUB,
            SupportedFormat.HTML,
            SupportedFormat.TXT
        )
        if (distinct.all { it == SupportedFormat.XLSX }) {
            targets.add(1, SupportedFormat.CSV)
            targets.add(2, SupportedFormat.JSON)
        }
        return targets
    }

    override suspend fun convert(
        inputs: List<InputFileItem>,
        targetFormat: SupportedFormat,
        options: ConversionOptions,
        onProgress: (Float, String) -> Unit
    ): List<ConversionJobResult> = withContext(Dispatchers.IO) {
        require(inputs.isNotEmpty()) { "No se seleccionaron documentos para convertir." }
        ensurePdfBoxInitialized()

        // Caso 0: Modificador Visual de PDF en Vista Previa
        if (targetFormat == SupportedFormat.PDF &&
            options.hasVisualPdfEdits &&
            inputs.all { it.format == SupportedFormat.PDF }
        ) {
            return@withContext applyVisualPdfPageEdits(inputs, options, onProgress)
        }

        // Caso 1: Operaciones de PDF sobre entradas PDF (incluyendo extracción de imágenes embebidas)
        if (inputs.all { it.format == SupportedFormat.PDF } &&
            (targetFormat == SupportedFormat.PDF || options.pdfActionMode == PdfActionMode.EXTRACT_EMBEDDED_IMAGES)
        ) {
            return@withContext processPdfToPdfOperations(inputs, options, onProgress)
        }

        // Caso 2: Exportación a EPUB 3
        if (targetFormat == SupportedFormat.EPUB) {
            return@withContext convertDocumentsToEpub(inputs, options, onProgress)
        }

        // Caso 3: XLSX -> CSV o JSON
        if (inputs.all { it.format == SupportedFormat.XLSX } &&
            (targetFormat == SupportedFormat.CSV || targetFormat == SupportedFormat.JSON)
        ) {
            return@withContext convertXlsxToCsvOrJson(inputs, targetFormat, options, onProgress)
        }

        // Caso 4: Destino es PDF (DOCX, XLSX, EPUB, HTML, TXT, MD o lotes mixtos con Imágenes/PDF)
        if (targetFormat == SupportedFormat.PDF) {
            return@withContext convertDocumentsToPdf(inputs, options, onProgress)
        }

        // Caso 5: Destino es HTML o TXT
        if (targetFormat == SupportedFormat.HTML || targetFormat == SupportedFormat.TXT) {
            return@withContext convertDocumentsToTextOrHtml(inputs, targetFormat, options, onProgress)
        }

        throw IllegalArgumentException(
            "Formato destino no soportado por DocumentEngineImpl: $targetFormat"
        )
    }

    /**
     * Aplica las modificaciones configuradas en el Editor Visual de Páginas PDF (v0.3):
     * reordenamiento, rotación, exclusión, páginas separadoras en blanco, censura y firma manuscrita.
     */
    private suspend fun applyVisualPdfPageEdits(
        inputs: List<InputFileItem>,
        options: ConversionOptions,
        onProgress: (Float, String) -> Unit
    ): List<ConversionJobResult> {
        val allEdits = PdfPageEditItem.fromJsonArrayString(options.pdfVisualPageEditsJson)
        val activeEdits = allEdits.filter { it.included }
        require(activeEdits.isNotEmpty()) {
            "Debes mantener al menos 1 página seleccionada en el Editor Visual de PDF."
        }

        val inputByUri = inputs.associateBy { it.uri.toString() }
        val loadedDocs = linkedMapOf<String, Pair<File, PDDocument>>()
        val tempOut = uriResolver.createTempWorkingFile(prefix = "pdf_visual_edit_", extension = "pdf")

        try {
            onProgress(0.08f, "Cargando documentos PDF de origen…")
            for (edit in activeEdits) {
                if (!edit.isBlankSeparator &&
                    edit.sourceUriString.isNotBlank() &&
                    !loadedDocs.containsKey(edit.sourceUriString)
                ) {
                    val inputItem = inputByUri[edit.sourceUriString] ?: inputs.first()
                    val tempSrc = uriResolver.copyUriToTempFile(
                        uri = inputItem.uri,
                        prefix = "vis_src_",
                        extension = "pdf"
                    )
                    val doc = if (options.pdfPassword.isNotBlank()) {
                        try {
                            PDDocument.load(tempSrc, options.pdfPassword)
                        } catch (_: Throwable) {
                            PDDocument.load(tempSrc)
                        }
                    } else {
                        PDDocument.load(tempSrc)
                    }
                    loadedDocs[edit.sourceUriString] = tempSrc to doc
                }
            }

            val signatureBitmap = loadSignatureBitmapOrNull(options.pdfSignaturePngPath)
            try {
                PDDocument().use { targetDoc ->
                    targetDoc.documentInformation.producer = "AnyWall Universal Converter v0.3"
                    val totalActive = activeEdits.size
                    activeEdits.forEachIndexed { idx, edit ->
                        coroutineContext.ensureActive()
                        val p = 0.1f + (idx.toFloat() / totalActive.toFloat()) * 0.70f
                        onProgress(
                            p,
                            "Aplicando página ${idx + 1} de $totalActive…"
                        )

                        val targetPage: PDPage? = if (edit.isBlankSeparator) {
                            val blank = PDPage(PDRectangle.A4)
                            targetDoc.addPage(blank)
                            blank
                        } else {
                            val sourceDoc = loadedDocs[edit.sourceUriString]?.second
                                ?: loadedDocs.values.firstOrNull()?.second
                                ?: throw IOException("No se pudo acceder al PDF de origen: ${edit.sourceDisplayName}")

                            if (edit.originalPageIndex in 0 until sourceDoc.numberOfPages) {
                                val importedPage = targetDoc.importPage(sourceDoc.getPage(edit.originalPageIndex))
                                if (edit.normalizedRotation != 0) {
                                    val currentRot = importedPage.rotation
                                    importedPage.rotation = (currentRot + edit.normalizedRotation) % 360
                                }
                                importedPage
                            } else {
                                null
                            }
                        }

                        if (targetPage != null) {
                            if (edit.redactionBoxes.isNotEmpty()) {
                                stampPageRedactionBoxes(targetDoc, targetPage, edit.redactionBoxes)
                            }
                            if (edit.hasSignatureStamp && signatureBitmap != null) {
                                stampSignatureOnPage(targetDoc, targetPage, signatureBitmap)
                            }
                        }
                    }

                    onProgress(0.84f, "Guardando documento PDF modificado…")
                    targetDoc.save(tempOut)
                }
            } finally {
                signatureBitmap?.recycle()
            }

            val finalTempFile = if (options.clampedQuality < 95) {
                onProgress(0.89f, "Optimizando peso del PDF modificado (${options.clampedQuality}%)…")
                val compressedTemp = uriResolver.createTempWorkingFile(prefix = "vis_opt_", extension = "pdf")
                compressPdfFile(tempOut, compressedTemp, options.clampedQuality)
                if (compressedTemp.exists() && compressedTemp.length() in 1 until tempOut.length()) {
                    tempOut.delete()
                    compressedTemp
                } else {
                    compressedTemp.delete()
                    tempOut
                }
            } else {
                tempOut
            }

            val anyPageHadExplicitSig = activeEdits.any { it.hasSignatureStamp }
            applyPdfDecorationsAndSecurityInPlace(
                pdfFile = finalTempFile,
                options = if (anyPageHadExplicitSig) options.copy(pdfSignaturePngPath = "") else options
            )

            val firstInput = inputs.first()
            val baseName = when {
                options.customOutputBaseName.isNotBlank() -> OutputNamingTemplateResolver.resolveBaseName(
                    templateOrCustomName = options.customOutputBaseName,
                    originalBaseName = firstInput.baseNameWithoutExtension,
                    batchIndexOneBased = 1,
                    batchTotalCount = 1,
                    targetFormat = SupportedFormat.PDF
                )
                inputs.size == 1 -> "${firstInput.baseNameWithoutExtension}_editado"
                else -> "${firstInput.baseNameWithoutExtension}_editado_${activeEdits.size}_pags"
            }
            val desiredName = UriFileResolver.buildOutputFileName(baseName, "pdf")
            val saved = try {
                storageManager.saveTempFileToDownloads(
                    sourceFile = finalTempFile,
                    desiredDisplayName = desiredName,
                    mimeType = SupportedFormat.PDF.mimeType,
                    subFolder = storageManager.resolveEffectiveSubFolder(
                        relativeFolderPath = firstInput.relativeFolderPath,
                        categorySubfolder = "Documentos",
                        preserveTreeOverride = options.preserveSubfolderStructure
                    )
                )
            } finally {
                finalTempFile.delete()
            }

            val totalSourceBytes = inputs.sumOf { it.sizeBytes }
            onProgress(1f, "PDF editado guardado: ${saved.displayName}")
            val sourceLabel = if (inputs.size == 1) {
                "${firstInput.displayName} (${activeEdits.size} págs.)"
            } else {
                "${firstInput.displayName} (+${inputs.size - 1} PDFs • ${activeEdits.size} págs.)"
            }

            return listOf(
                ConversionJobResult(
                    sourceDisplayName = sourceLabel,
                    sourceFormat = SupportedFormat.PDF,
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
        } finally {
            loadedDocs.values.forEach { (file, doc) ->
                try {
                    doc.close()
                } catch (_: Throwable) {
                }
                file.delete()
            }
            if (tempOut.exists()) tempOut.delete()
        }
    }

    private fun stampPageRedactionBoxes(
        doc: PDDocument,
        page: PDPage,
        boxes: List<NormalizedRedactionBox>
    ) {
        if (boxes.isEmpty()) return
        val box = page.cropBox ?: page.mediaBox ?: PDRectangle.A4
        val pageW = box.width
        val pageH = box.height

        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
            cs.setNonStrokingColor(0f, 0f, 0f)
            for (r in boxes) {
                val x = box.lowerLeftX + (r.clampedLeft * pageW)
                val w = ((r.clampedRight - r.clampedLeft) * pageW).coerceAtLeast(2f)
                // Coordenadas PDF crecen de abajo hacia arriba
                val yTopFromBottom = (1f - r.clampedTop) * pageH
                val yBottomFromBottom = (1f - r.clampedBottom) * pageH
                val y = box.lowerLeftY + yBottomFromBottom
                val h = (yTopFromBottom - yBottomFromBottom).coerceAtLeast(2f)
                cs.addRect(x, y, w, h)
                cs.fill()
            }
        }
    }

    private fun stampSignatureOnPage(
        doc: PDDocument,
        page: PDPage,
        signatureBitmap: Bitmap
    ) {
        val box = page.cropBox ?: page.mediaBox ?: PDRectangle.A4
        val pageW = box.width
        val pageH = box.height
        val sigImg = LosslessFactory.createFromImage(doc, signatureBitmap)
        val targetW = (pageW * 0.24f).coerceIn(72f, 180f)
        val aspect = signatureBitmap.height.toFloat() / signatureBitmap.width.coerceAtLeast(1).toFloat()
        val targetH = (targetW * aspect).coerceIn(24f, 110f)
        val x = box.lowerLeftX + pageW - targetW - 28f
        val y = box.lowerLeftY + 28f

        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
            cs.drawImage(sigImg, x, y, targetW, targetH)
        }
    }

    private fun loadSignatureBitmapOrNull(path: String): Bitmap? {
        if (path.isBlank()) return null
        val file = File(path)
        if (!file.exists() || !file.isFile) return null
        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Aplica marca de agua diagonal, numeración de páginas (`"Página X de Y"`),
     * firma manuscrita en la última página, sello QR opcional, limpieza de metadatos y cifrado PDF.
     */
    private fun applyPdfDecorationsAndSecurityInPlace(pdfFile: File, options: ConversionOptions) {
        val hasWatermark = options.pdfWatermarkText.isNotBlank()
        val hasPageNums = options.addPdfPageNumbers
        val hasSig = options.pdfSignaturePngPath.isNotBlank()
        val hasQr = options.pdfStampQrContent.isNotBlank()
        val hasPass = options.pdfPassword.isNotBlank() && options.pdfActionMode != PdfActionMode.UNLOCK_PASSWORD
        val stripMeta = options.stripExifMetadata

        if (!hasWatermark && !hasPageNums && !hasSig && !hasQr && !hasPass && !stripMeta) return

        val sigBmp = loadSignatureBitmapOrNull(options.pdfSignaturePngPath)
        val qrBmp = if (hasQr) QrCodeHelper.generateQrBitmap(options.pdfStampQrContent.trim(), 240) else null

        try {
            PDDocument.load(pdfFile).use { doc ->
                if (stripMeta) {
                    doc.documentInformation.apply {
                        author = ""
                        creator = "AnyWall Universal Converter v0.3"
                        producer = "AnyWall Universal Converter v0.3"
                        subject = ""
                        keywords = ""
                    }
                }

                val totalPages = doc.numberOfPages
                val wmText = options.pdfWatermarkText.trim()

                for (pageIdx in 0 until totalPages) {
                    val page = doc.getPage(pageIdx)
                    val box = page.cropBox ?: page.mediaBox ?: PDRectangle.A4
                    val w = box.width
                    val h = box.height

                    if (hasWatermark || hasPageNums || (pageIdx == 0 && qrBmp != null) || (pageIdx == totalPages - 1 && sigBmp != null)) {
                        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                            if (hasWatermark) {
                                val gs = PDExtendedGraphicsState().apply {
                                    nonStrokingAlphaConstant = 0.20f
                                }
                                cs.setGraphicsStateParameters(gs)
                                cs.setNonStrokingColor(0.35f, 0.35f, 0.35f)
                                val fontSize = (minOf(w, h) * 0.08f).coerceIn(24f, 64f)
                                cs.beginText()
                                cs.setFont(PDType1Font.HELVETICA_BOLD, fontSize)
                                val angle = atan2(h.toDouble(), w.toDouble()).toFloat()
                                cs.setTextMatrix(
                                    PdfMatrix.getRotateInstance(
                                        angle.toDouble(),
                                        box.lowerLeftX + w * 0.22f,
                                        box.lowerLeftY + h * 0.30f
                                    )
                                )
                                cs.showText(sanitizePdfWinAnsiText(wmText))
                                cs.endText()
                            }

                            if (hasPageNums) {
                                val gsNormal = PDExtendedGraphicsState().apply {
                                    nonStrokingAlphaConstant = 0.85f
                                }
                                cs.setGraphicsStateParameters(gsNormal)
                                cs.setNonStrokingColor(0.24f, 0.24f, 0.24f)
                                val label = "Pagina ${pageIdx + 1} de $totalPages"
                                val fontSize = 9.5f
                                val approxWidth = label.length * fontSize * 0.52f
                                cs.beginText()
                                cs.setFont(PDType1Font.HELVETICA, fontSize)
                                cs.newLineAtOffset(
                                    box.lowerLeftX + (w - approxWidth) / 2f,
                                    box.lowerLeftY + 14f
                                )
                                cs.showText(label)
                                cs.endText()
                            }

                            if (pageIdx == 0 && qrBmp != null) {
                                val qrImg = LosslessFactory.createFromImage(doc, qrBmp)
                                val qrSide = 60f
                                cs.drawImage(
                                    qrImg,
                                    box.lowerLeftX + 20f,
                                    box.lowerLeftY + 20f,
                                    qrSide,
                                    qrSide
                                )
                            }
                        }
                    }

                    if (pageIdx == totalPages - 1 && sigBmp != null) {
                        stampSignatureOnPage(doc, page, sigBmp)
                    }
                }

                if (hasPass) {
                    val cleanPass = options.pdfPassword.trim()
                    val accessPermission = AccessPermission()
                    val policy = StandardProtectionPolicy(cleanPass, cleanPass, accessPermission).apply {
                        encryptionKeyLength = 128
                        permissions = accessPermission
                    }
                    doc.protect(policy)
                }

                doc.save(pdfFile)
            }
        } finally {
            sigBmp?.recycle()
            qrBmp?.recycle()
        }
    }

    private fun sanitizePdfWinAnsiText(raw: String): String {
        return raw.map { ch ->
            if (ch.code in 32..255) ch else '?'
        }.joinToString("")
    }

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
            PdfActionMode.SPLIT_BY_CHUNK -> splitPdfsByChunkSize(inputs, options, onProgress)
            PdfActionMode.OPTIMIZE_COMPRESS -> optimizeAndCompressPdfFiles(inputs, options, onProgress)
            PdfActionMode.PROTECT_PASSWORD -> protectPdfFilesWithPassword(inputs, options, onProgress)
            PdfActionMode.UNLOCK_PASSWORD -> unlockPdfFilesWithPassword(inputs, options, onProgress)
            PdfActionMode.EXTRACT_EMBEDDED_IMAGES -> extractEmbeddedImagesFromPdfs(inputs, options, onProgress)
            PdfActionMode.BOOKLET_N_UP_2 -> createNUpBookletFromPdfs(inputs, pagesPerSheet = 2, options = options, onProgress = onProgress)
            PdfActionMode.BOOKLET_N_UP_4 -> createNUpBookletFromPdfs(inputs, pagesPerSheet = 4, options = options, onProgress = onProgress)
        }
    }

    private suspend fun splitPdfsByChunkSize(
        inputs: List<InputFileItem>,
        options: ConversionOptions,
        onProgress: (Float, String) -> Unit
    ): List<ConversionJobResult> {
        val results = mutableListOf<ConversionJobResult>()
        val chunkSize = options.pdfSplitChunkPages.coerceAtLeast(1)
        val totalFiles = inputs.size

        inputs.forEachIndexed { fileIdx, item ->
            coroutineContext.ensureActive()
            val tempSrc = uriResolver.copyUriToTempFile(item.uri, prefix = "chunk_src_", extension = "pdf")
            try {
                PDDocument.load(tempSrc, options.pdfPassword).use { sourceDoc ->
                    val splitter = Splitter().apply {
                        setSplitAtPage(chunkSize)
                    }
                    val chunks = splitter.split(sourceDoc)
                    try {
                        val totalChunks = chunks.size.coerceAtLeast(1)
                        val approxBytes = (item.sizeBytes / totalChunks).coerceAtLeast(1L)
                        chunks.forEachIndexed { chunkIdx, chunkDoc ->
                            coroutineContext.ensureActive()
                            val p = (fileIdx.toFloat() + (chunkIdx.toFloat() / totalChunks)) / totalFiles
                            onProgress(p.coerceIn(0f, 0.99f), "Guardando bloque ${chunkIdx + 1} de $totalChunks (${item.displayName})…")

                            val tempChunk = uriResolver.createTempWorkingFile("chunk_", "pdf")
                            try {
                                chunkDoc.save(tempChunk)
                                applyPdfDecorationsAndSecurityInPlace(tempChunk, options)

                                val baseName = OutputNamingTemplateResolver.resolveBaseName(
                                    templateOrCustomName = options.customOutputBaseName,
                                    originalBaseName = item.baseNameWithoutExtension,
                                    batchIndexOneBased = chunkIdx + 1,
                                    batchTotalCount = totalChunks,
                                    targetFormat = SupportedFormat.PDF
                                )
                                val desiredName = UriFileResolver.buildOutputFileName(
                                    baseName = baseName,
                                    extension = "pdf",
                                    suffix = "_bloque_${String.format("%02d", chunkIdx + 1)}"
                                )
                                val saved = storageManager.saveTempFileToDownloads(
                                    sourceFile = tempChunk,
                                    desiredDisplayName = desiredName,
                                    mimeType = SupportedFormat.PDF.mimeType
                                )
                                results.add(
                                    ConversionJobResult(
                                        sourceDisplayName = "${item.displayName} (Bloque ${chunkIdx + 1})",
                                        sourceFormat = SupportedFormat.PDF,
                                        sourceSizeBytes = approxBytes,
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
                                tempChunk.delete()
                            }
                        }
                    } finally {
                        chunks.forEach {
                            try {
                                it.close()
                            } catch (_: Throwable) {
                            }
                        }
                    }
                }
            } finally {
                tempSrc.delete()
            }
        }
        onProgress(1f, "División por bloques completada (${results.size} PDFs).")
        return results
    }

    private suspend fun extractEmbeddedImagesFromPdfs(
        inputs: List<InputFileItem>,
        options: ConversionOptions,
        onProgress: (Float, String) -> Unit
    ): List<ConversionJobResult> {
        val results = mutableListOf<ConversionJobResult>()
        val totalFiles = inputs.size

        inputs.forEachIndexed { fileIdx, item ->
            coroutineContext.ensureActive()
            onProgress(fileIdx.toFloat() / totalFiles.toFloat(), "Buscando imágenes incrustadas en ${item.displayName}…")
            val tempSrc = uriResolver.copyUriToTempFile(item.uri, "emb_src_", "pdf")
            try {
                PDDocument.load(tempSrc, options.pdfPassword).use { doc ->
                    var imageCounter = 0
                    for ((pageIdx, page) in doc.pages.withIndex()) {
                        val resources = page.resources ?: continue
                        for (name: COSName in resources.xObjectNames) {
                            coroutineContext.ensureActive()
                            if (resources.isImageXObject(name)) {
                                val pdImage = resources.getXObject(name) as? PDImageXObject ?: continue
                                val bitmap = pdImage.image ?: continue
                                imageCounter++
                                val tempImg = uriResolver.createTempWorkingFile("emb_img_", "png")
                                try {
                                    FileOutputStream(tempImg).use { fos ->
                                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                                    }
                                    val desiredName = UriFileResolver.buildOutputFileName(
                                        baseName = item.baseNameWithoutExtension,
                                        extension = "png",
                                        suffix = "_p${pageIdx + 1}_img${String.format("%03d", imageCounter)}"
                                    )
                                    val saved = storageManager.saveTempFileToDownloads(
                                        sourceFile = tempImg,
                                        desiredDisplayName = desiredName,
                                        mimeType = SupportedFormat.PNG.mimeType
                                    )
                                    results.add(
                                        ConversionJobResult(
                                            sourceDisplayName = "${item.displayName} (Img $imageCounter)",
                                            sourceFormat = SupportedFormat.PDF,
                                            sourceSizeBytes = saved.sizeBytes,
                                            outputDisplayName = saved.displayName,
                                            outputFormat = SupportedFormat.PNG,
                                            outputCategory = FormatCategory.IMAGE,
                                            outputUri = saved.uri,
                                            outputAbsolutePath = saved.absolutePath,
                                            outputSizeBytes = saved.sizeBytes,
                                            outputMimeType = saved.mimeType
                                        )
                                    )
                                } finally {
                                    bitmap.recycle()
                                    tempImg.delete()
                                }
                            }
                        }
                    }
                }
            } finally {
                tempSrc.delete()
            }
        }

        if (results.isEmpty()) {
            throw IOException("No se encontraron imágenes incrustadas dentro del PDF seleccionado.")
        }
        onProgress(1f, "Se extrajeron ${results.size} imágenes originales del PDF.")
        return results
    }

    private suspend fun createNUpBookletFromPdfs(
        inputs: List<InputFileItem>,
        pagesPerSheet: Int,
        options: ConversionOptions,
        onProgress: (Float, String) -> Unit
    ): List<ConversionJobResult> {
        val results = mutableListOf<ConversionJobResult>()
        val totalFiles = inputs.size
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        inputs.forEachIndexed { fileIdx, item ->
            coroutineContext.ensureActive()
            onProgress(fileIdx.toFloat() / totalFiles.toFloat(), "Generando modo ${pagesPerSheet} páginas por hoja para ${item.displayName}…")
            val tempSrc = uriResolver.copyUriToTempFile(item.uri, "nup_src_", "pdf")
            val tempOut = uriResolver.createTempWorkingFile("nup_out_", "pdf")

            try {
                ParcelFileDescriptor.open(tempSrc, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        val totalPages = renderer.pageCount
                        if (totalPages <= 0) throw IOException("El PDF no contiene páginas.")

                        PDDocument().use { outDoc ->
                            val is2Up = pagesPerSheet <= 2
                            val sheetW = if (is2Up) PDRectangle.A4.height else PDRectangle.A4.width
                            val sheetH = if (is2Up) PDRectangle.A4.width else PDRectangle.A4.height
                            val cols = 2
                            val rows = if (is2Up) 1 else 2

                            val sheetPixelW = (sheetW * 2.0f).roundToInt()
                            val sheetPixelH = (sheetH * 2.0f).roundToInt()
                            val cellW = sheetPixelW / cols
                            val cellH = sheetPixelH / rows

                            var pageIndex = 0
                            while (pageIndex < totalPages) {
                                coroutineContext.ensureActive()
                                val sheetBitmap = Bitmap.createBitmap(sheetPixelW, sheetPixelH, Bitmap.Config.ARGB_8888)
                                try {
                                    val sheetCanvas = Canvas(sheetBitmap)
                                    sheetCanvas.drawColor(Color.WHITE)

                                    for (slot in 0 until pagesPerSheet) {
                                        val srcPageIdx = pageIndex + slot
                                        if (srcPageIdx >= totalPages) break
                                        val col = slot % cols
                                        val row = slot / cols
                                        val dstLeft = col * cellW + 16
                                        val dstTop = row * cellH + 16
                                        val dstRight = (col + 1) * cellW - 16
                                        val dstBottom = (row + 1) * cellH - 16

                                        renderer.openPage(srcPageIdx).use { srcPage ->
                                            val pageBmp = Bitmap.createBitmap(
                                                (dstRight - dstLeft).coerceAtLeast(1),
                                                (dstBottom - dstTop).coerceAtLeast(1),
                                                Bitmap.Config.ARGB_8888
                                            )
                                            try {
                                                val pCanvas = Canvas(pageBmp)
                                                pCanvas.drawColor(Color.WHITE)
                                                srcPage.render(
                                                    pageBmp,
                                                    Rect(0, 0, pageBmp.width, pageBmp.height),
                                                    null,
                                                    PdfRenderer.Page.RENDER_MODE_FOR_PRINT
                                                )
                                                sheetCanvas.drawBitmap(pageBmp, dstLeft.toFloat(), dstTop.toFloat(), paint)
                                            } finally {
                                                pageBmp.recycle()
                                            }
                                        }
                                    }

                                    val pdPage = PDPage(PDRectangle(sheetW, sheetH))
                                    outDoc.addPage(pdPage)
                                    val pdImg = JPEGFactory.createFromImage(
                                        outDoc,
                                        sheetBitmap,
                                        (options.clampedQuality / 100f).coerceIn(0.4f, 0.95f)
                                    )
                                    PDPageContentStream(outDoc, pdPage).use { cs ->
                                        cs.drawImage(pdImg, 0f, 0f, sheetW, sheetH)
                                    }
                                } finally {
                                    sheetBitmap.recycle()
                                }
                                pageIndex += pagesPerSheet
                            }

                            outDoc.save(tempOut)
                        }
                    }
                }

                applyPdfDecorationsAndSecurityInPlace(tempOut, options)
                val desiredName = UriFileResolver.buildOutputFileName(
                    baseName = "${item.baseNameWithoutExtension}_${pagesPerSheet}en1",
                    extension = "pdf"
                )
                val saved = storageManager.saveTempFileToDownloads(
                    sourceFile = tempOut,
                    desiredDisplayName = desiredName,
                    mimeType = SupportedFormat.PDF.mimeType
                )
                results.add(
                    ConversionJobResult(
                        sourceDisplayName = item.displayName,
                        sourceFormat = SupportedFormat.PDF,
                        sourceSizeBytes = item.sizeBytes,
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

        onProgress(1f, "Documento ${pagesPerSheet}-en-1 generado.")
        return results
    }

    private suspend fun protectPdfFilesWithPassword(
        inputs: List<InputFileItem>,
        options: ConversionOptions,
        onProgress: (Float, String) -> Unit
    ): List<ConversionJobResult> {
        val password = options.pdfPassword.trim()
        require(password.isNotEmpty()) {
            "Debes especificar una contraseña en los parámetros de conversión para proteger el PDF."
        }

        val results = mutableListOf<ConversionJobResult>()
        val total = inputs.size

        inputs.forEachIndexed { index, item ->
            coroutineContext.ensureActive()
            onProgress(
                index.toFloat() / total.toFloat(),
                "Cifrando con contraseña ${item.displayName}…"
            )

            val tempSrc = uriResolver.copyUriToTempFile(item.uri, prefix = "enc_src_", extension = "pdf")
            val tempOut = uriResolver.createTempWorkingFile(prefix = "enc_out_", extension = "pdf")

            try {
                tempSrc.copyTo(tempOut, overwrite = true)
                applyPdfDecorationsAndSecurityInPlace(tempOut, options)

                val baseName = if (options.customOutputBaseName.isNotBlank() && total == 1) {
                    options.customOutputBaseName
                } else {
                    "${item.baseNameWithoutExtension}_protegido"
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
                        sourceSizeBytes = item.sizeBytes,
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

        onProgress(1f, "Protección de PDF completada.")
        return results
    }

    private suspend fun unlockPdfFilesWithPassword(
        inputs: List<InputFileItem>,
        options: ConversionOptions,
        onProgress: (Float, String) -> Unit
    ): List<ConversionJobResult> {
        val password = options.pdfPassword.trim()
        val results = mutableListOf<ConversionJobResult>()
        val total = inputs.size

        inputs.forEachIndexed { index, item ->
            coroutineContext.ensureActive()
            onProgress(
                index.toFloat() / total.toFloat(),
                "Desbloqueando ${item.displayName}…"
            )

            val tempSrc = uriResolver.copyUriToTempFile(item.uri, prefix = "unlk_src_", extension = "pdf")
            val tempOut = uriResolver.createTempWorkingFile(prefix = "unlk_out_", extension = "pdf")

            try {
                PDDocument.load(tempSrc, password).use { doc ->
                    doc.isAllSecurityToBeRemoved = true
                    doc.save(tempOut)
                }

                val baseName = if (options.customOutputBaseName.isNotBlank() && total == 1) {
                    options.customOutputBaseName
                } else {
                    "${item.baseNameWithoutExtension}_desbloqueado"
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
                        sourceSizeBytes = item.sizeBytes,
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

        onProgress(1f, "Desbloqueo de PDF completado.")
        return results
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

            applyPdfDecorationsAndSecurityInPlace(finalTempFile, options)

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

            val totalSourceBytes = inputs.sumOf { it.sizeBytes }
            onProgress(1f, "PDF unido guardado: ${saved.displayName}")
            return listOf(
                ConversionJobResult(
                    sourceDisplayName = "${inputs.first().displayName} (+${inputs.size - 1} PDFs)",
                    sourceFormat = SupportedFormat.PDF,
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
                PDDocument.load(tempSrc, options.pdfPassword).use { sourceDoc ->
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

                applyPdfDecorationsAndSecurityInPlace(tempOut, options)

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
                        sourceSizeBytes = item.sizeBytes,
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
                PDDocument.load(tempSrc, options.pdfPassword).use { sourceDoc ->
                    val splitter = Splitter()
                    val splitDocs: List<PDDocument> = splitter.split(sourceDoc)
                    try {
                        val pageCount = splitDocs.size.coerceAtLeast(1)
                        val approxSourcePerPage = (item.sizeBytes / pageCount).coerceAtLeast(1L)
                        splitDocs.forEachIndexed { pageIdx, singlePageDoc ->
                            coroutineContext.ensureActive()
                            val progress = (fileIdx.toFloat() + (pageIdx.toFloat() / pageCount)) / totalFiles
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
                                applyPdfDecorationsAndSecurityInPlace(tempPageFile, options)

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
                                        sourceSizeBytes = approxSourcePerPage,
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
                applyPdfDecorationsAndSecurityInPlace(tempOut, options)

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
                        sourceSizeBytes = item.sizeBytes,
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

    private fun compressPdfFile(sourceFile: File, destFile: File, qualityPercent: Int) {
        val qualityFloat = (qualityPercent.coerceIn(10, 100) / 100f)

        val tempVectorCompressed = uriResolver.createTempWorkingFile(prefix = "vec_opt_", extension = "pdf")
        var vectorSuccess = false
        try {
            PDDocument.load(sourceFile).use { doc ->
                doc.documentInformation.producer = "AnyWall Universal Converter v0.3"
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

    private suspend fun convertDocumentsToEpub(
        inputs: List<InputFileItem>,
        options: ConversionOptions,
        onProgress: (Float, String) -> Unit
    ): List<ConversionJobResult> {
        val results = mutableListOf<ConversionJobResult>()
        val total = inputs.size

        inputs.forEachIndexed { idx, item ->
            coroutineContext.ensureActive()
            onProgress(idx.toFloat() / total.toFloat(), "Generando eBook EPUB para ${item.displayName}…")
            val bodyHtml = convertDocumentItemToHtml(item)
            val tempEpub = uriResolver.createTempWorkingFile("ebook_", "epub")
            try {
                FileOutputStream(tempEpub).use { fos ->
                    BufferedOutputStream(fos, 64 * 1024).use { bos ->
                        EpubPureConverter.writeEpubFromHtmlBody(
                            title = item.baseNameWithoutExtension,
                            bodyHtml = bodyHtml,
                            output = bos
                        )
                    }
                }
                val baseName = OutputNamingTemplateResolver.resolveBaseName(
                    templateOrCustomName = options.customOutputBaseName,
                    originalBaseName = item.baseNameWithoutExtension,
                    batchIndexOneBased = idx + 1,
                    batchTotalCount = total,
                    targetFormat = SupportedFormat.EPUB
                )
                val saved = storageManager.saveTempFileToDownloads(
                    sourceFile = tempEpub,
                    desiredDisplayName = UriFileResolver.buildOutputFileName(baseName, "epub"),
                    mimeType = SupportedFormat.EPUB.mimeType
                )
                results.add(
                    ConversionJobResult(
                        sourceDisplayName = item.displayName,
                        sourceFormat = item.format,
                        sourceSizeBytes = item.sizeBytes,
                        outputDisplayName = saved.displayName,
                        outputFormat = SupportedFormat.EPUB,
                        outputCategory = FormatCategory.DOCUMENT,
                        outputUri = saved.uri,
                        outputAbsolutePath = saved.absolutePath,
                        outputSizeBytes = saved.sizeBytes,
                        outputMimeType = saved.mimeType
                    )
                )
            } finally {
                tempEpub.delete()
            }
        }
        onProgress(1f, "EPUB generado.")
        return results
    }

    private suspend fun convertXlsxToCsvOrJson(
        inputs: List<InputFileItem>,
        targetFormat: SupportedFormat,
        options: ConversionOptions,
        onProgress: (Float, String) -> Unit
    ): List<ConversionJobResult> {
        val results = mutableListOf<ConversionJobResult>()
        val total = inputs.size

        inputs.forEachIndexed { idx, item ->
            coroutineContext.ensureActive()
            onProgress(idx.toFloat() / total.toFloat(), "Leyendo hoja de cálculo ${item.displayName}…")
            val sheets = uriResolver.openInputStream(item.uri).use { XlsxPureParser.parseXlsxStream(it) }
            val firstRows = sheets.firstOrNull()?.rows.orEmpty()
            val csvText = StructuredDataConverter.formatRowsToCsv(firstRows)
            val finalContent = if (targetFormat == SupportedFormat.CSV) {
                csvText
            } else {
                StructuredDataConverter.convertStructuredText(
                    sourceText = csvText,
                    sourceFormat = SupportedFormat.CSV,
                    targetFormat = SupportedFormat.JSON,
                    prettyPrint = options.prettyPrintStructuredData
                )
            }

            val tempOut = uriResolver.createTempWorkingFile("xlsx_out_", targetFormat.extension)
            try {
                tempOut.writeText(finalContent, Charsets.UTF_8)
                val baseName = OutputNamingTemplateResolver.resolveBaseName(
                    templateOrCustomName = options.customOutputBaseName,
                    originalBaseName = item.baseNameWithoutExtension,
                    batchIndexOneBased = idx + 1,
                    batchTotalCount = total,
                    targetFormat = targetFormat
                )
                val saved = storageManager.saveTempFileToDownloads(
                    sourceFile = tempOut,
                    desiredDisplayName = UriFileResolver.buildOutputFileName(baseName, targetFormat.extension),
                    mimeType = targetFormat.mimeType
                )
                results.add(
                    ConversionJobResult(
                        sourceDisplayName = item.displayName,
                        sourceFormat = item.format,
                        sourceSizeBytes = item.sizeBytes,
                        outputDisplayName = saved.displayName,
                        outputFormat = targetFormat,
                        outputCategory = targetFormat.category,
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
        onProgress(1f, "Conversión de hoja de cálculo completada.")
        return results
    }

    /**
     * Convierte documentos (`DOCX`, `XLSX`, `EPUB`, `HTML`, `TXT`, `MD`) o lotes mixtos con imágenes y PDFs
     * en un único documento PDF continuo (o PDFs individuales si `mergeIntoSinglePdf == false`).
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
                    "Convirtiendo archivo ${index + 1} de $total a PDF: ${item.displayName}"
                )

                val tempPdf = when {
                    item.format == SupportedFormat.PDF -> {
                        uriResolver.copyUriToTempFile(item.uri, prefix = "doc_existing_", extension = "pdf")
                    }
                    item.format.category == FormatCategory.IMAGE -> {
                        convertSingleImageItemToTempPdf(item, options)
                    }
                    else -> {
                        val html = convertDocumentItemToHtml(item)
                        val outTempPdf = uriResolver.createTempWorkingFile(
                            prefix = "webview_pdf_",
                            extension = "pdf"
                        )
                        webViewPdfGenerator.renderHtmlToPdfFile(
                            htmlContent = html,
                            documentTitle = item.baseNameWithoutExtension,
                            outputFile = outTempPdf,
                            pageSize = options.pdfPageSize,
                            pageOrientation = options.pdfPageOrientation
                        )
                    }
                }
                generatedTempPdfs.add(item to tempPdf)
            }

            if (shouldMergeAll) {
                onProgress(0.85f, "Uniendo ${generatedTempPdfs.size} elementos en un único PDF…")
                val mergedPdf = uriResolver.createTempWorkingFile(prefix = "merged_docs_", extension = "pdf")
                try {
                    val merger = PDFMergerUtility()
                    generatedTempPdfs.forEach { (_, file) -> merger.addSource(file) }
                    merger.destinationFileName = mergedPdf.absolutePath
                    merger.mergeDocuments(MemoryUsageSetting.setupMixed(32L * 1024L * 1024L))
                    applyPdfDecorationsAndSecurityInPlace(mergedPdf, options)

                    val baseName = options.customOutputBaseName.ifBlank {
                        "${inputs.first().baseNameWithoutExtension}_unido_${inputs.size}"
                    }
                    val desiredName = UriFileResolver.buildOutputFileName(baseName, "pdf")
                    val saved = storageManager.saveTempFileToDownloads(
                        sourceFile = mergedPdf,
                        desiredDisplayName = desiredName,
                        mimeType = SupportedFormat.PDF.mimeType
                    )

                    val totalSourceBytes = inputs.sumOf { it.sizeBytes }
                    onProgress(1f, "PDF combinado generado: ${saved.displayName}")
                    return listOf(
                        ConversionJobResult(
                            sourceDisplayName = "${inputs.first().displayName} (+${inputs.size - 1} más)",
                            sourceFormat = inputs.first().format,
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
                } finally {
                    mergedPdf.delete()
                }
            } else {
                val results = mutableListOf<ConversionJobResult>()
                generatedTempPdfs.forEachIndexed { idx, (item, pdfFile) ->
                    applyPdfDecorationsAndSecurityInPlace(pdfFile, options)
                    val baseName = OutputNamingTemplateResolver.resolveBaseName(
                        templateOrCustomName = options.customOutputBaseName,
                        originalBaseName = item.baseNameWithoutExtension,
                        batchIndexOneBased = idx + 1,
                        batchTotalCount = total,
                        targetFormat = SupportedFormat.PDF
                    )
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
                            sourceSizeBytes = item.sizeBytes,
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

    private fun convertSingleImageItemToTempPdf(
        item: InputFileItem,
        options: ConversionOptions
    ): File {
        val tempPdf = uriResolver.createTempWorkingFile(prefix = "mix_img_", extension = "pdf")
        val rawBitmap = if (item.format == SupportedFormat.SVG) {
            val svg = uriResolver.openInputStream(item.uri).use { SVG.getFromInputStream(it) }
            val w = (svg.documentWidth.takeIf { it > 0f } ?: 1080f).roundToInt().coerceIn(64, 2480)
            val h = (svg.documentHeight.takeIf { it > 0f } ?: 1080f).roundToInt().coerceIn(64, 2480)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(Color.WHITE)
            svg.renderToCanvas(canvas)
            bmp
        } else {
            val bytes = uriResolver.openInputStream(item.uri).use { it.readBytes() }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: throw IOException("No se pudo decodificar la imagen '${item.displayName}' en el lote mixto.")
        }

        val maxDim = options.maxDimensionPx ?: 2048
        val currentMax = max(rawBitmap.width, rawBitmap.height).coerceAtLeast(1)
        val scaled = if (currentMax > maxDim) {
            val ratio = maxDim.toFloat() / currentMax.toFloat()
            val newW = (rawBitmap.width * ratio).roundToInt().coerceAtLeast(1)
            val newH = (rawBitmap.height * ratio).roundToInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(rawBitmap, newW, newH, true).also {
                if (it !== rawBitmap) rawBitmap.recycle()
            }
        } else {
            rawBitmap
        }

        val rotated = if (options.normalizedRotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(options.normalizedRotationDegrees.toFloat()) }
            Bitmap.createBitmap(scaled, 0, 0, scaled.width, scaled.height, matrix, true).also {
                if (it !== scaled) scaled.recycle()
            }
        } else {
            scaled
        }

        val edited = ImageVisualTransformHelper.applyVisualEditSpec(rotated, options.imageEditSpec)
        if (edited !== rotated) rotated.recycle()

        try {
            PDDocument().use { doc ->
                val page = PDPage(PDRectangle(edited.width.toFloat(), edited.height.toFloat()))
                doc.addPage(page)
                val pdImage = JPEGFactory.createFromImage(
                    doc,
                    edited,
                    (options.clampedQuality / 100f).coerceIn(0.15f, 1.0f)
                )
                PDPageContentStream(doc, page).use { cs ->
                    cs.drawImage(pdImage, 0f, 0f, edited.width.toFloat(), edited.height.toFloat())
                }
                doc.save(tempPdf)
            }
        } finally {
            edited.recycle()
        }
        return tempPdf
    }

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

            val normalizedContent = StructuredDataConverter.normalizeLineEndings(
                outputContent,
                options.lineEndingOption
            )

            val tempOut = uriResolver.createTempWorkingFile(
                prefix = "doc_text_",
                extension = targetFormat.extension
            )
            try {
                tempOut.writeText(normalizedContent, Charsets.UTF_8)
                val baseName = OutputNamingTemplateResolver.resolveBaseName(
                    templateOrCustomName = options.customOutputBaseName,
                    originalBaseName = item.baseNameWithoutExtension,
                    batchIndexOneBased = index + 1,
                    batchTotalCount = total,
                    targetFormat = targetFormat
                )
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
                        sourceSizeBytes = item.sizeBytes,
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

    @Throws(IOException::class)
    private fun convertDocumentItemToHtml(item: InputFileItem): String {
        return when (item.format) {
            SupportedFormat.DOCX -> {
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

            SupportedFormat.XLSX -> {
                val sheets = uriResolver.openInputStream(item.uri).use { XlsxPureParser.parseXlsxStream(it) }
                if (sheets.isEmpty()) {
                    "<p><em>Hoja de cálculo XLSX vacía (${WebViewPdfGenerator.escapeHtml(item.displayName)})</em></p>"
                } else {
                    buildString {
                        for (sheet in sheets) {
                            append("<h2>${WebViewPdfGenerator.escapeHtml(sheet.sheetName)}</h2>\n")
                            if (sheet.rows.isNotEmpty()) {
                                val headers = sheet.rows.first()
                                val bodyRows = sheet.rows.drop(1)
                                append("<table style=\"border-collapse:collapse;width:100%;margin-bottom:18px;font-size:11px;\">")
                                append("<thead><tr>")
                                for (h in headers) {
                                    append("<th style=\"border:1px solid #cbd5e1;padding:6px;background:#f1f5f9;\">${WebViewPdfGenerator.escapeHtml(h)}</th>")
                                }
                                append("</tr></thead><tbody>")
                                for (row in bodyRows) {
                                    append("<tr>")
                                    for (i in headers.indices) {
                                        append("<td style=\"border:1px solid #cbd5e1;padding:6px;\">${WebViewPdfGenerator.escapeHtml(row.getOrNull(i).orEmpty())}</td>")
                                    }
                                    append("</tr>")
                                }
                                append("</tbody></table>\n")
                            }
                        }
                    }
                }
            }

            SupportedFormat.EPUB -> {
                val epub = uriResolver.openInputStream(item.uri).use { EpubPureConverter.readEpubStream(it) }
                buildString {
                    append("<h1>${WebViewPdfGenerator.escapeHtml(epub.title)}</h1>\n")
                    append("<p><em>Autor: ${WebViewPdfGenerator.escapeHtml(epub.author)}</em></p><hr/>\n")
                    for (chapter in epub.chaptersHtml) {
                        append(chapter).append("\n<hr/>\n")
                    }
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
            SupportedFormat.XLSX -> {
                val sheets = uriResolver.openInputStream(item.uri).use { XlsxPureParser.parseXlsxStream(it) }
                sheets.joinToString("\n\n") { sheet ->
                    "=== ${sheet.sheetName} ===\n" + StructuredDataConverter.formatRowsToCsv(sheet.rows)
                }
            }
            SupportedFormat.EPUB -> {
                val epub = uriResolver.openInputStream(item.uri).use { EpubPureConverter.readEpubStream(it) }
                "${epub.title} (${epub.author})\n\n${epub.plainText}"
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

    companion object {
        fun convertMarkdownToHtml(markdown: String): String {
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
            result = result.replace(Regex("`([^`]+)`"), "<code>$1</code>")
            result = result.replace(Regex("\\*\\*([^*]+)\\*\\*"), "<strong>$1</strong>")
            result = result.replace(Regex("__([^_]+)__"), "<strong>$1</strong>")
            result = result.replace(Regex("\\*([^*]+)\\*"), "<em>$1</em>")
            result = result.replace(Regex("_([^_]+)_"), "<em>$1</em>")
            result = result.replace(Regex("\\[([^\\]]+)]\\(([^)]+)\\)"), "<strong>$1</strong> (<code>$2</code>)")
            return result
        }

        fun stripHtmlTags(html: String): String {
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
}
