package com.anywall.converter.workers

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.anywall.converter.AnyWallApplication
import com.anywall.converter.core.ScopedStorageManager
import com.anywall.converter.core.UriFileResolver
import com.anywall.converter.data.engines.AesVaultCrypto
import com.anywall.converter.domain.engine.ConverterEngineRegistry
import com.anywall.converter.domain.model.ConversionJobResult
import com.anywall.converter.domain.model.ConversionOptions
import com.anywall.converter.domain.model.FormatCategory
import com.anywall.converter.domain.model.InputFileItem
import com.anywall.converter.domain.model.SecondaryPipelineAction
import com.anywall.converter.domain.model.SupportedFormat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CancellationException

/**
 * `CoroutineWorker` de WorkManager que ejecuta todas las conversiones de AnyWall en segundo plano (v0.3),
 * incluyendo soporte de encadenamiento de 2 pasos (`SecondaryPipelineAction`: empaquetar en ZIP/TAR.XZ,
 * cifrar en Bóveda AES-256 o generar manifiesto SHA-256), notificación de progreso y registro en Room.
 */
class BatchConversionWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? AnyWallApplication
            ?: return Result.failure(workDataOf(KEY_ERROR to "Contexto de aplicación AnyWall no inicializado."))
        val container = app.container
        val notificationHelper = container.notificationHelper
        val uriResolver = container.uriFileResolver
        val storageManager = container.scopedStorageManager
        val engineRegistry = container.converterEngineRegistry
        val historyRepository = container.historyRepository

        try {
            setForeground(notificationHelper.createForegroundInfo(0f, "Preparando archivos…"))
        } catch (_: Throwable) {
        }

        val payloadFile = inputData.getString(KEY_PAYLOAD_FILE_PATH)?.let(::File)
        return try {
            val (inputs, targetFormat, options) = parseWorkInput(payloadFile, uriResolver)

            if (inputs.isEmpty()) {
                return Result.failure(
                    workDataOf(KEY_ERROR to "No se pudieron leer los archivos seleccionados.")
                )
            }

            val hasStep2 = options.secondaryPipelineAction != SecondaryPipelineAction.NONE
            val step1Weight = if (hasStep2) 0.78f else 1.0f

            var lastNotificationUpdateMs = 0L
            fun reportWorkerProgress(progress: Float, statusText: String) {
                val clamped = progress.coerceIn(0f, 1f)
                setProgressAsync(
                    workDataOf(
                        KEY_PROGRESS to clamped,
                        KEY_STATUS to statusText
                    )
                )

                val now = System.currentTimeMillis()
                if (now - lastNotificationUpdateMs >= 220L || clamped >= 0.99f) {
                    lastNotificationUpdateMs = now
                    try {
                        setForegroundAsync(
                            notificationHelper.createForegroundInfo(clamped, statusText)
                        )
                    } catch (_: Throwable) {
                    }
                }
            }

            val step1Results = engineRegistry.executeConversion(
                inputs = inputs,
                targetFormat = targetFormat,
                options = options
            ) { p, msg ->
                reportWorkerProgress(p * step1Weight, msg)
            }

            val finalResults = if (hasStep2 && step1Results.isNotEmpty()) {
                executeSecondaryPipelineStep(
                    step1Results = step1Results,
                    options = options,
                    uriResolver = uriResolver,
                    storageManager = storageManager,
                    engineRegistry = engineRegistry,
                    onProgress = { subP, msg ->
                        reportWorkerProgress(step1Weight + subP * (1f - step1Weight), msg)
                    }
                )
            } else {
                step1Results
            }

            historyRepository.recordConversions(finalResults)

            val doneMsg = "Conversión completada (${finalResults.size} ${if (finalResults.size == 1) "archivo" else "archivos"})"
            setProgress(
                workDataOf(
                    KEY_PROGRESS to 1f,
                    KEY_STATUS to doneMsg,
                    KEY_GENERATED_COUNT to finalResults.size
                )
            )
            notificationHelper.notifyBatchFinished(finalResults.size, null)

            Result.success(
                workDataOf(
                    KEY_PROGRESS to 1f,
                    KEY_STATUS to doneMsg,
                    KEY_GENERATED_COUNT to finalResults.size
                )
            )
        } catch (ce: CancellationException) {
            notificationHelper.notifyBatchFinished(0, "Conversión cancelada por el usuario.")
            throw ce
        } catch (t: Throwable) {
            val errorMsg = t.localizedMessage?.takeIf { it.isNotBlank() }
                ?: "Error inesperado durante la conversión local."
            notificationHelper.notifyBatchFinished(0, errorMsg)
            Result.failure(
                workDataOf(
                    KEY_ERROR to errorMsg,
                    KEY_STATUS to errorMsg
                )
            )
        } finally {
            try {
                payloadFile?.delete()
            } catch (_: Throwable) {
            }
        }
    }

    private suspend fun executeSecondaryPipelineStep(
        step1Results: List<ConversionJobResult>,
        options: ConversionOptions,
        uriResolver: UriFileResolver,
        storageManager: ScopedStorageManager,
        engineRegistry: ConverterEngineRegistry,
        onProgress: (Float, String) -> Unit
    ): List<ConversionJobResult> {
        val action = options.secondaryPipelineAction
        val step2Inputs = step1Results.map { res ->
            InputFileItem(
                uri = res.outputUri,
                displayName = res.outputDisplayName,
                sizeBytes = res.outputSizeBytes,
                mimeType = res.outputMimeType,
                format = res.outputFormat
            )
        }

        return when (action) {
            SecondaryPipelineAction.NONE -> step1Results

            SecondaryPipelineAction.PACK_ZIP -> {
                onProgress(0.1f, "Paso 2: Empaquetando salidas en archivo ZIP…")
                val zipResults = engineRegistry.archiveEngine.convert(
                    inputs = step2Inputs,
                    targetFormat = SupportedFormat.ZIP,
                    options = options.copy(secondaryPipelineAction = SecondaryPipelineAction.NONE),
                    onProgress = onProgress
                )
                step1Results + zipResults
            }

            SecondaryPipelineAction.PACK_TAR_XZ -> {
                onProgress(0.1f, "Paso 2: Empaquetando salidas en archivo TAR.XZ…")
                val tarResults = engineRegistry.archiveEngine.convert(
                    inputs = step2Inputs,
                    targetFormat = SupportedFormat.TAR_XZ,
                    options = options.copy(secondaryPipelineAction = SecondaryPipelineAction.NONE),
                    onProgress = onProgress
                )
                step1Results + tarResults
            }

            SecondaryPipelineAction.ENCRYPT_AES256 -> {
                val dataEngine = engineRegistry.dataAndSecurityEngine ?: return step1Results
                val effectiveOptions = if (options.vaultPassword.isNotBlank()) {
                    options
                } else if (options.pdfPassword.isNotBlank()) {
                    options.copy(vaultPassword = options.pdfPassword)
                } else {
                    return step1Results
                }
                onProgress(0.1f, "Paso 2: Cifrando salidas en Bóveda AES-256 (.anywall)…")
                val vaultResults = dataEngine.convert(
                    inputs = step2Inputs,
                    targetFormat = SupportedFormat.ANYWALL_VAULT,
                    options = effectiveOptions.copy(secondaryPipelineAction = SecondaryPipelineAction.NONE),
                    onProgress = onProgress
                )
                step1Results + vaultResults
            }

            SecondaryPipelineAction.COMPUTE_SHA256_MANIFEST -> {
                onProgress(0.2f, "Paso 2: Calculando hashes SHA-256 y MD5 de los archivos generados…")
                val manifestText = buildString {
                    appendLine("# AnyWall Universal Converter v0.3 — Manifiesto de Integridad SHA-256 / MD5")
                    appendLine("# Generado 100% Offline")
                    appendLine()
                    for (res in step1Results) {
                        val sha256 = try {
                            uriResolver.openInputStream(res.outputUri).use {
                                AesVaultCrypto.computeDigestHex(it, "SHA-256")
                            }
                        } catch (_: Throwable) {
                            "no-disponible"
                        }
                        val md5 = try {
                            uriResolver.openInputStream(res.outputUri).use {
                                AesVaultCrypto.computeDigestHex(it, "MD5")
                            }
                        } catch (_: Throwable) {
                            "no-disponible"
                        }
                        appendLine("$sha256  ${res.outputDisplayName}  (MD5: $md5, ${res.outputSizeBytes} bytes)")
                    }
                }
                val tempManifest = uriResolver.createTempWorkingFile("sha256sums_", "txt")
                try {
                    tempManifest.writeText(manifestText, Charsets.UTF_8)
                    val firstBase = step1Results.first().outputDisplayName.substringBeforeLast('.')
                    val saved = storageManager.saveTempFileToDownloads(
                        sourceFile = tempManifest,
                        desiredDisplayName = "${firstBase}_SHA256SUMS.txt",
                        mimeType = "text/plain"
                    )
                    val manifestResult = ConversionJobResult(
                        sourceDisplayName = "Manifiesto de lote (${step1Results.size} archivos)",
                        sourceFormat = step1Results.first().outputFormat,
                        sourceSizeBytes = saved.sizeBytes,
                        outputDisplayName = saved.displayName,
                        outputFormat = SupportedFormat.TXT,
                        outputCategory = FormatCategory.DATA_TOOLS,
                        outputUri = saved.uri,
                        outputAbsolutePath = saved.absolutePath,
                        outputSizeBytes = saved.sizeBytes,
                        outputMimeType = saved.mimeType
                    )
                    step1Results + manifestResult
                } finally {
                    tempManifest.delete()
                }
            }
        }
    }

    private fun parseWorkInput(
        payloadFile: File?,
        uriResolver: UriFileResolver
    ): Triple<List<InputFileItem>, SupportedFormat, ConversionOptions> {
        if (payloadFile != null && payloadFile.exists()) {
            val json = JSONObject(payloadFile.readText(Charsets.UTF_8))
            val targetFormat = SupportedFormat.fromNameOrNull(json.optString("targetFormat"))
                ?: throw IllegalArgumentException("Formato destino inválido en el lote.")

            val optionsSerialized = json.optString("optionsSerialized", "")
            val options = if (optionsSerialized.isNotBlank()) {
                ConversionOptions.deserializeFromString(optionsSerialized)
            } else {
                ConversionOptions()
            }

            val itemsArray = json.optJSONArray("inputs") ?: JSONArray()
            val inputs = mutableListOf<InputFileItem>()
            for (i in 0 until itemsArray.length()) {
                val obj = itemsArray.getJSONObject(i)
                val uri = Uri.parse(obj.getString("uri"))
                val format = SupportedFormat.fromNameOrNull(obj.optString("format"))
                val displayName = obj.optString("displayName")
                val sizeBytes = obj.optLong("sizeBytes", 0L)
                val mimeType = obj.optString("mimeType")
                val relativeFolderPath = obj.optString("relativeFolderPath", "")

                if (format != null && displayName.isNotBlank()) {
                    inputs.add(
                        InputFileItem(
                            uri = uri,
                            displayName = displayName,
                            sizeBytes = sizeBytes,
                            mimeType = mimeType.ifBlank { format.mimeType },
                            format = format,
                            relativeFolderPath = relativeFolderPath
                        )
                    )
                } else {
                    uriResolver.resolveInputFile(uri, relativeFolderPath)?.let(inputs::add)
                }
            }
            return Triple(inputs, targetFormat, options)
        }

        val targetFormatName = inputData.getString(KEY_TARGET_FORMAT)
        val targetFormat = SupportedFormat.fromNameOrNull(targetFormatName)
            ?: throw IllegalArgumentException("Formato destino no especificado.")
        val uriStrings = inputData.getStringArray(KEY_INPUT_URIS).orEmpty()
        val inputs = uriStrings.mapNotNull { raw ->
            uriResolver.resolveInputFile(Uri.parse(raw))
        }
        return Triple(inputs, targetFormat, ConversionOptions())
    }

    companion object {
        const val WORK_TAG = "anywall_batch_conversion"
        const val UNIQUE_WORK_NAME = "anywall_active_batch_conversion"

        const val KEY_PAYLOAD_FILE_PATH = "payload_file_path"
        const val KEY_INPUT_URIS = "input_uris"
        const val KEY_TARGET_FORMAT = "target_format"

        const val KEY_PROGRESS = "progress"
        const val KEY_STATUS = "status"
        const val KEY_ERROR = "error"
        const val KEY_GENERATED_COUNT = "generated_count"
    }
}
