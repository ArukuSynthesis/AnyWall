package com.anywall.converter.data.engines

import android.graphics.Bitmap
import com.anywall.converter.core.ScopedStorageManager
import com.anywall.converter.core.UriFileResolver
import com.anywall.converter.domain.engine.FileConverterEngine
import com.anywall.converter.domain.model.ConversionJobResult
import com.anywall.converter.domain.model.ConversionOptions
import com.anywall.converter.domain.model.FormatCategory
import com.anywall.converter.domain.model.InputFileItem
import com.anywall.converter.domain.model.OutputNamingTemplateResolver
import com.anywall.converter.domain.model.SupportedFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.Charset
import kotlin.coroutines.coroutineContext

/**
 * Motor 5: `DataAndSecurityEngineImpl` (Datos Estructurados, Bóveda AES-256-GCM y Códigos QR - v0.3)
 * - Conversión bidireccional entre `CSV`, `JSON`, `XML`, `YAML`, `BASE64`, `HTML`, `TXT` y `PDF`.
 * - Formateo (`Pretty Print` / `Minificar`), conversión de codificación (`UTF-8`, `ISO-8859-1`, `Windows-1252`, `UTF-16`)
 *   y normalización de saltos de línea (`LF` ↔ `CRLF`).
 * - Bóveda Universal Criptográfica `AES-256-GCM` (`ANYWALL_VAULT` `.anywall`) para cifrar cualquier archivo
 *   con clave derivada vía `PBKDF2WithHmacSHA256` (120 000 iteraciones) y restaurar `.anywall` a su archivo original.
 * - Generación de códigos QR offline (`QR_PNG`, `QR_SVG`) a partir del contenido del archivo o texto personalizado.
 */
class DataAndSecurityEngineImpl(
    private val uriResolver: UriFileResolver,
    private val storageManager: ScopedStorageManager,
    private val webViewPdfGenerator: WebViewPdfGenerator
) : FileConverterEngine {

    private val structuredDataFormats = setOf(
        SupportedFormat.CSV,
        SupportedFormat.JSON,
        SupportedFormat.XML,
        SupportedFormat.YAML,
        SupportedFormat.BASE64
    )

    override fun canHandle(format: SupportedFormat): Boolean {
        return format in structuredDataFormats || format == SupportedFormat.ANYWALL_VAULT
    }

    override fun getSupportedTargetFormats(sourceFormats: List<SupportedFormat>): List<SupportedFormat> {
        if (sourceFormats.isEmpty()) return emptyList()
        val distinct = sourceFormats.distinct()

        // Si todos los archivos son bóvedas .anywall -> se descifran a su formato original
        if (distinct.all { it == SupportedFormat.ANYWALL_VAULT }) {
            return listOf(SupportedFormat.ANYWALL_VAULT)
        }

        // Si son datos estructurados (CSV, JSON, XML, YAML, BASE64)
        if (distinct.all { it in structuredDataFormats }) {
            return listOf(
                SupportedFormat.JSON,
                SupportedFormat.CSV,
                SupportedFormat.XML,
                SupportedFormat.YAML,
                SupportedFormat.HTML,
                SupportedFormat.PDF,
                SupportedFormat.TXT,
                SupportedFormat.BASE64,
                SupportedFormat.QR_PNG,
                SupportedFormat.QR_SVG,
                SupportedFormat.ANYWALL_VAULT
            )
        }

        return listOf(SupportedFormat.ANYWALL_VAULT)
    }

    override suspend fun convert(
        inputs: List<InputFileItem>,
        targetFormat: SupportedFormat,
        options: ConversionOptions,
        onProgress: (Float, String) -> Unit
    ): List<ConversionJobResult> = withContext(Dispatchers.IO) {
        require(inputs.isNotEmpty()) { "No se proporcionaron archivos para DataAndSecurityEngineImpl." }

        // Caso A: Descifrar archivos .anywall existentes
        if (inputs.all { it.format == SupportedFormat.ANYWALL_VAULT }) {
            return@withContext decryptAnyWallVaults(inputs, options, onProgress)
        }

        // Caso B: Cifrar cualquier archivo en una Bóveda AES-256-GCM (.anywall)
        if (targetFormat == SupportedFormat.ANYWALL_VAULT) {
            return@withContext encryptFilesToAnyWallVault(inputs, options, onProgress)
        }

        // Caso C: Generar código QR (PNG o SVG) desde el contenido o texto configurado
        if (targetFormat == SupportedFormat.QR_PNG || targetFormat == SupportedFormat.QR_SVG) {
            return@withContext generateQrFromFiles(inputs, targetFormat, options, onProgress)
        }

        // Caso D: Conversión entre formatos estructurados (CSV, JSON, XML, YAML, BASE64, HTML, TXT, PDF)
        return@withContext convertStructuredFiles(inputs, targetFormat, options, onProgress)
    }

    private suspend fun encryptFilesToAnyWallVault(
        inputs: List<InputFileItem>,
        options: ConversionOptions,
        onProgress: (Float, String) -> Unit
    ): List<ConversionJobResult> {
        val password = options.vaultPassword.trim().ifBlank { options.pdfPassword.trim() }
        require(password.isNotEmpty()) {
            "Debes introducir una contraseña en la sección de Bóveda AES-256 para cifrar los archivos."
        }

        val results = mutableListOf<ConversionJobResult>()
        val total = inputs.size

        inputs.forEachIndexed { idx, item ->
            coroutineContext.ensureActive()
            onProgress(idx.toFloat() / total.toFloat(), "Cifrando con AES-256-GCM: ${item.displayName}…")
            val tempVault = uriResolver.createTempWorkingFile("vault_", "anywall")
            try {
                uriResolver.openInputStream(item.uri).use { inputStream ->
                    FileOutputStream(tempVault).use { fos ->
                        BufferedOutputStream(fos, 64 * 1024).use { bos ->
                            AesVaultCrypto.encryptStreamToVault(
                                input = inputStream,
                                originalFileName = item.displayName,
                                password = password,
                                output = bos
                            )
                        }
                    }
                }

                val baseName = OutputNamingTemplateResolver.resolveBaseName(
                    templateOrCustomName = options.customOutputBaseName,
                    originalBaseName = item.baseNameWithoutExtension,
                    batchIndexOneBased = idx + 1,
                    batchTotalCount = total,
                    targetFormat = SupportedFormat.ANYWALL_VAULT
                )
                val saved = storageManager.saveTempFileToDownloads(
                    sourceFile = tempVault,
                    desiredDisplayName = UriFileResolver.buildOutputFileName(baseName, "anywall"),
                    mimeType = SupportedFormat.ANYWALL_VAULT.mimeType,
                    subFolder = storageManager.resolveEffectiveSubFolder(
                        relativeFolderPath = item.relativeFolderPath,
                        categorySubfolder = "Boveda_AES",
                        preserveTreeOverride = options.preserveSubfolderStructure
                    )
                )
                results.add(
                    ConversionJobResult(
                        sourceDisplayName = item.displayName,
                        sourceFormat = item.format,
                        sourceSizeBytes = item.sizeBytes,
                        outputDisplayName = saved.displayName,
                        outputFormat = SupportedFormat.ANYWALL_VAULT,
                        outputCategory = FormatCategory.DATA_TOOLS,
                        outputUri = saved.uri,
                        outputAbsolutePath = saved.absolutePath,
                        outputSizeBytes = saved.sizeBytes,
                        outputMimeType = saved.mimeType
                    )
                )
            } finally {
                tempVault.delete()
            }
        }
        onProgress(1f, "Cifrado AES-256-GCM completado.")
        return results
    }

    private suspend fun decryptAnyWallVaults(
        inputs: List<InputFileItem>,
        options: ConversionOptions,
        onProgress: (Float, String) -> Unit
    ): List<ConversionJobResult> {
        val password = options.vaultPassword.trim().ifBlank { options.pdfPassword.trim() }
        require(password.isNotEmpty()) {
            "Debes introducir la contraseña de la Bóveda AES-256 (.anywall) para restaurar el archivo original."
        }

        val results = mutableListOf<ConversionJobResult>()
        val total = inputs.size

        inputs.forEachIndexed { idx, item ->
            coroutineContext.ensureActive()
            onProgress(idx.toFloat() / total.toFloat(), "Descifrando bóveda AES-256: ${item.displayName}…")
            val tempPlain = uriResolver.createTempWorkingFile("unvault_", "bin")
            try {
                val originalFileName = uriResolver.openInputStream(item.uri).use { inputStream ->
                    FileOutputStream(tempPlain).use { fos ->
                        BufferedOutputStream(fos, 64 * 1024).use { bos ->
                            AesVaultCrypto.decryptVaultStream(
                                input = inputStream,
                                password = password,
                                output = bos
                            )
                        }
                    }
                }

                val detectedFormat = SupportedFormat.detect(originalFileName, null) ?: SupportedFormat.TXT
                val saved = storageManager.saveTempFileToDownloads(
                    sourceFile = tempPlain,
                    desiredDisplayName = UriFileResolver.sanitizeFileName(originalFileName),
                    mimeType = detectedFormat.mimeType
                )
                results.add(
                    ConversionJobResult(
                        sourceDisplayName = item.displayName,
                        sourceFormat = SupportedFormat.ANYWALL_VAULT,
                        sourceSizeBytes = item.sizeBytes,
                        outputDisplayName = saved.displayName,
                        outputFormat = detectedFormat,
                        outputCategory = detectedFormat.category,
                        outputUri = saved.uri,
                        outputAbsolutePath = saved.absolutePath,
                        outputSizeBytes = saved.sizeBytes,
                        outputMimeType = saved.mimeType
                    )
                )
            } finally {
                tempPlain.delete()
            }
        }
        onProgress(1f, "Bóveda(s) AES-256 restauradas con éxito.")
        return results
    }

    private suspend fun generateQrFromFiles(
        inputs: List<InputFileItem>,
        targetFormat: SupportedFormat,
        options: ConversionOptions,
        onProgress: (Float, String) -> Unit
    ): List<ConversionJobResult> {
        val results = mutableListOf<ConversionJobResult>()
        val total = inputs.size

        inputs.forEachIndexed { idx, item ->
            coroutineContext.ensureActive()
            onProgress(idx.toFloat() / total.toFloat(), "Generando código QR desde ${item.displayName}…")
            val content = options.qrCustomText.trim().ifBlank {
                uriResolver.openInputStream(item.uri).use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText().take(2200)
                }
            }.ifBlank { item.displayName }

            val ext = if (targetFormat == SupportedFormat.QR_SVG) "svg" else "png"
            val tempQr = uriResolver.createTempWorkingFile("qr_", ext)
            try {
                if (targetFormat == SupportedFormat.QR_SVG) {
                    val svgText = QrCodeHelper.generateQrSvg(content)
                    tempQr.writeText(svgText, Charsets.UTF_8)
                } else {
                    val qrBmp = QrCodeHelper.generateQrBitmap(content, sizePx = (options.maxDimensionPx ?: 768).coerceIn(256, 2048))
                    try {
                        FileOutputStream(tempQr).use { fos ->
                            qrBmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
                        }
                    } finally {
                        qrBmp.recycle()
                    }
                }

                val baseName = OutputNamingTemplateResolver.resolveBaseName(
                    templateOrCustomName = options.customOutputBaseName,
                    originalBaseName = "${item.baseNameWithoutExtension}_qr",
                    batchIndexOneBased = idx + 1,
                    batchTotalCount = total,
                    targetFormat = targetFormat
                )
                val saved = storageManager.saveTempFileToDownloads(
                    sourceFile = tempQr,
                    desiredDisplayName = UriFileResolver.buildOutputFileName(baseName, ext),
                    mimeType = targetFormat.mimeType
                )
                results.add(
                    ConversionJobResult(
                        sourceDisplayName = item.displayName,
                        sourceFormat = item.format,
                        sourceSizeBytes = item.sizeBytes,
                        outputDisplayName = saved.displayName,
                        outputFormat = targetFormat,
                        outputCategory = FormatCategory.DATA_TOOLS,
                        outputUri = saved.uri,
                        outputAbsolutePath = saved.absolutePath,
                        outputSizeBytes = saved.sizeBytes,
                        outputMimeType = saved.mimeType
                    )
                )
            } finally {
                tempQr.delete()
            }
        }
        onProgress(1f, "Código(s) QR generados.")
        return results
    }

    private suspend fun convertStructuredFiles(
        inputs: List<InputFileItem>,
        targetFormat: SupportedFormat,
        options: ConversionOptions,
        onProgress: (Float, String) -> Unit
    ): List<ConversionJobResult> {
        val results = mutableListOf<ConversionJobResult>()
        val total = inputs.size
        val targetCharset = try {
            Charset.forName(options.textEncoding.charsetName)
        } catch (_: Throwable) {
            Charsets.UTF_8
        }

        inputs.forEachIndexed { idx, item ->
            coroutineContext.ensureActive()
            onProgress(idx.toFloat() / total.toFloat(), "Convirtiendo ${item.displayName} a ${targetFormat.label}…")
            val rawBytes = uriResolver.openInputStream(item.uri).use { it.readBytes() }
            val sourceText = decodeBestEffortText(rawBytes)

            val convertedText = StructuredDataConverter.convertStructuredText(
                sourceText = sourceText,
                sourceFormat = item.format,
                targetFormat = targetFormat,
                prettyPrint = options.prettyPrintStructuredData,
                lineEndingOption = options.lineEndingOption,
                title = item.baseNameWithoutExtension
            )

            val baseName = OutputNamingTemplateResolver.resolveBaseName(
                templateOrCustomName = options.customOutputBaseName,
                originalBaseName = item.baseNameWithoutExtension,
                batchIndexOneBased = idx + 1,
                batchTotalCount = total,
                targetFormat = targetFormat
            )

            if (targetFormat == SupportedFormat.PDF) {
                val tempPdf = uriResolver.createTempWorkingFile("data_pdf_", "pdf")
                try {
                    webViewPdfGenerator.renderHtmlToPdfFile(
                        htmlContent = convertedText,
                        documentTitle = item.baseNameWithoutExtension,
                        outputFile = tempPdf,
                        pageSize = options.pdfPageSize,
                        pageOrientation = options.pdfPageOrientation
                    )
                    val saved = storageManager.saveTempFileToDownloads(
                        sourceFile = tempPdf,
                        desiredDisplayName = UriFileResolver.buildOutputFileName(baseName, "pdf"),
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
                } finally {
                    tempPdf.delete()
                }
            } else {
                val tempOut = uriResolver.createTempWorkingFile("data_out_", targetFormat.extension)
                try {
                    tempOut.writeBytes(convertedText.toByteArray(targetCharset))
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
        }

        onProgress(1f, "Conversión de datos estructurados completada.")
        return results
    }

    private fun decodeBestEffortText(bytes: ByteArray): String {
        val utf8 = String(bytes, Charsets.UTF_8)
        return if (utf8.contains('\uFFFD')) {
            String(bytes, Charsets.ISO_8859_1)
        } else {
            utf8
        }
    }
}
