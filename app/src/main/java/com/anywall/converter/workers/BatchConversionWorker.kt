package com.anywall.converter.workers

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.anywall.converter.AnyWallApplication
import com.anywall.converter.domain.model.ConversionOptions
import com.anywall.converter.domain.model.InputFileItem
import com.anywall.converter.domain.model.PdfActionMode
import com.anywall.converter.domain.model.SupportedFormat
import com.anywall.converter.domain.model.VideoResolution
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CancellationException

/**
 * `CoroutineWorker` de WorkManager que ejecuta todas las conversiones de AnyWall en segundo plano,
 * manteniendo una notificación persistente con barra de progreso real y registrando los resultados
 * en la base de datos local Room.
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
        val engineRegistry = container.converterEngineRegistry
        val historyRepository = container.historyRepository

        // Activar servicio en primer plano con notificación persistente y barra de progreso real
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

            var lastNotificationUpdateMs = 0L
            val results = engineRegistry.executeConversion(
                inputs = inputs,
                targetFormat = targetFormat,
                options = options
            ) { progress, statusText ->
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

            // Registrar cada archivo generado en la base de datos Room
            historyRepository.recordConversions(results)

            val doneMsg = "Conversión completada (${results.size} ${if (results.size == 1) "archivo" else "archivos"})"
            setProgress(
                workDataOf(
                    KEY_PROGRESS to 1f,
                    KEY_STATUS to doneMsg,
                    KEY_GENERATED_COUNT to results.size
                )
            )
            notificationHelper.notifyBatchFinished(results.size, null)

            Result.success(
                workDataOf(
                    KEY_PROGRESS to 1f,
                    KEY_STATUS to doneMsg,
                    KEY_GENERATED_COUNT to results.size
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

    private fun parseWorkInput(
        payloadFile: File?,
        uriResolver: com.anywall.converter.core.UriFileResolver
    ): Triple<List<InputFileItem>, SupportedFormat, ConversionOptions> {
        if (payloadFile != null && payloadFile.exists()) {
            val json = JSONObject(payloadFile.readText(Charsets.UTF_8))
            val targetFormat = SupportedFormat.fromNameOrNull(json.optString("targetFormat"))
                ?: throw IllegalArgumentException("Formato destino inválido en el lote.")

            val options = ConversionOptions(
                qualityPercent = json.optInt("qualityPercent", 85),
                maxDimensionPx = json.optInt("maxDimensionPx", -1).takeIf { it > 0 },
                videoResolution = VideoResolution.fromNameOrDefault(json.optString("videoResolution")),
                customOutputBaseName = json.optString("customOutputBaseName", ""),
                mergeIntoSinglePdf = json.optBoolean("mergeIntoSinglePdf", true),
                pdfActionMode = PdfActionMode.fromNameOrDefault(json.optString("pdfActionMode")),
                pageRangeSpec = json.optString("pageRangeSpec", "")
            )

            val itemsArray = json.optJSONArray("inputs") ?: JSONArray()
            val inputs = mutableListOf<InputFileItem>()
            for (i in 0 until itemsArray.length()) {
                val obj = itemsArray.getJSONObject(i)
                val uri = Uri.parse(obj.getString("uri"))
                val format = SupportedFormat.fromNameOrNull(obj.optString("format"))
                val displayName = obj.optString("displayName")
                val sizeBytes = obj.optLong("sizeBytes", 0L)
                val mimeType = obj.optString("mimeType")

                if (format != null && displayName.isNotBlank()) {
                    inputs.add(
                        InputFileItem(
                            uri = uri,
                            displayName = displayName,
                            sizeBytes = sizeBytes,
                            mimeType = mimeType.ifBlank { format.mimeType },
                            format = format
                        )
                    )
                } else {
                    uriResolver.resolveInputFile(uri)?.let(inputs::add)
                }
            }
            return Triple(inputs, targetFormat, options)
        }

        // Fallback desde WorkManager Data directo
        val targetFormatName = inputData.getString(KEY_TARGET_FORMAT)
        val targetFormat = SupportedFormat.fromNameOrNull(targetFormatName)
            ?: throw IllegalArgumentException("Formato destino no especificado.")
        val uriStrings = inputData.getStringArray(KEY_INPUT_URIS).orEmpty()
        val inputs = uriStrings.mapNotNull { raw ->
            uriResolver.resolveInputFile(Uri.parse(raw))
        }
        val options = ConversionOptions(
            qualityPercent = inputData.getInt(KEY_QUALITY_PERCENT, 85),
            maxDimensionPx = inputData.getInt(KEY_MAX_DIMENSION_PX, -1).takeIf { it > 0 },
            videoResolution = VideoResolution.fromNameOrDefault(inputData.getString(KEY_VIDEO_RESOLUTION)),
            customOutputBaseName = inputData.getString(KEY_CUSTOM_OUTPUT_NAME).orEmpty(),
            mergeIntoSinglePdf = inputData.getBoolean(KEY_MERGE_SINGLE_PDF, true),
            pdfActionMode = PdfActionMode.fromNameOrDefault(inputData.getString(KEY_PDF_ACTION_MODE)),
            pageRangeSpec = inputData.getString(KEY_PAGE_RANGE_SPEC).orEmpty()
        )
        return Triple(inputs, targetFormat, options)
    }

    companion object {
        const val WORK_TAG = "anywall_batch_conversion"
        const val UNIQUE_WORK_NAME = "anywall_active_batch_conversion"

        const val KEY_PAYLOAD_FILE_PATH = "payload_file_path"
        const val KEY_INPUT_URIS = "input_uris"
        const val KEY_TARGET_FORMAT = "target_format"
        const val KEY_QUALITY_PERCENT = "quality_percent"
        const val KEY_MAX_DIMENSION_PX = "max_dimension_px"
        const val KEY_VIDEO_RESOLUTION = "video_resolution"
        const val KEY_CUSTOM_OUTPUT_NAME = "custom_output_name"
        const val KEY_MERGE_SINGLE_PDF = "merge_single_pdf"
        const val KEY_PDF_ACTION_MODE = "pdf_action_mode"
        const val KEY_PAGE_RANGE_SPEC = "page_range_spec"

        const val KEY_PROGRESS = "progress"
        const val KEY_STATUS = "status"
        const val KEY_ERROR = "error"
        const val KEY_GENERATED_COUNT = "generated_count"
    }
}
