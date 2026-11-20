package com.anywall.converter.workers

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.anywall.converter.domain.model.ConversionOptions
import com.anywall.converter.domain.model.InputFileItem
import com.anywall.converter.domain.model.SupportedFormat
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Planificador de lotes de conversión sobre `WorkManager` (v0.2).
 * Utiliza `ExistingWorkPolicy.APPEND_OR_REPLACE` para encolar trabajos de forma segura
 * sin cancelar conversiones activas en curso.
 */
class ConversionWorkScheduler(private val context: Context) {

    private val workManager: WorkManager = WorkManager.getInstance(context)

    fun enqueueBatchConversion(
        inputs: List<InputFileItem>,
        targetFormat: SupportedFormat,
        options: ConversionOptions
    ): UUID {
        val payloadFile = writeBatchPayloadFile(inputs, targetFormat, options)

        val data = workDataOf(
            BatchConversionWorker.KEY_PAYLOAD_FILE_PATH to payloadFile.absolutePath,
            BatchConversionWorker.KEY_TARGET_FORMAT to targetFormat.name,
            BatchConversionWorker.KEY_QUALITY_PERCENT to options.clampedQuality,
            BatchConversionWorker.KEY_MAX_DIMENSION_PX to (options.maxDimensionPx ?: -1),
            BatchConversionWorker.KEY_IMAGE_ROTATION to options.normalizedRotationDegrees,
            BatchConversionWorker.KEY_STRIP_EXIF to options.stripExifMetadata,
            BatchConversionWorker.KEY_VIDEO_RESOLUTION to options.videoResolution.name,
            BatchConversionWorker.KEY_MUTE_VIDEO_AUDIO to options.muteVideoAudio,
            BatchConversionWorker.KEY_TRIM_START_SECONDS to (options.trimStartSeconds ?: -1L),
            BatchConversionWorker.KEY_TRIM_END_SECONDS to (options.trimEndSeconds ?: -1L),
            BatchConversionWorker.KEY_CUSTOM_OUTPUT_NAME to options.customOutputBaseName,
            BatchConversionWorker.KEY_MERGE_SINGLE_PDF to options.mergeIntoSinglePdf,
            BatchConversionWorker.KEY_PDF_ACTION_MODE to options.pdfActionMode.name,
            BatchConversionWorker.KEY_PAGE_RANGE_SPEC to options.pageRangeSpec,
            BatchConversionWorker.KEY_PDF_PASSWORD to options.pdfPassword,
            BatchConversionWorker.KEY_PDF_PAGE_SIZE to options.pdfPageSize.name,
            BatchConversionWorker.KEY_PDF_PAGE_ORIENTATION to options.pdfPageOrientation.name
        )

        val request = OneTimeWorkRequestBuilder<BatchConversionWorker>()
            .setInputData(data)
            .addTag(BatchConversionWorker.WORK_TAG)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        workManager.enqueueUniqueWork(
            BatchConversionWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )

        return request.id
    }

    fun observeWorkInfo(workId: UUID): Flow<WorkInfo?> {
        return workManager.getWorkInfoByIdFlow(workId)
    }

    fun cancelActiveConversion(workId: UUID?) {
        if (workId != null) {
            workManager.cancelWorkById(workId)
        } else {
            workManager.cancelUniqueWork(BatchConversionWorker.UNIQUE_WORK_NAME)
        }
    }

    fun hasActiveConversionWork(): Boolean {
        return try {
            val infos = workManager.getWorkInfosForUniqueWork(BatchConversionWorker.UNIQUE_WORK_NAME).get()
            infos.any { info ->
                info.state == WorkInfo.State.RUNNING ||
                    info.state == WorkInfo.State.ENQUEUED ||
                    info.state == WorkInfo.State.BLOCKED
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun writeBatchPayloadFile(
        inputs: List<InputFileItem>,
        targetFormat: SupportedFormat,
        options: ConversionOptions
    ): File {
        val dir = File(context.cacheDir, "anywall_payloads")
        if (!dir.exists()) dir.mkdirs()

        val payloadFile = File(dir, "batch_${UUID.randomUUID()}.json")
        val root = JSONObject().apply {
            put("targetFormat", targetFormat.name)
            put("qualityPercent", options.clampedQuality)
            put("maxDimensionPx", options.maxDimensionPx ?: -1)
            put("imageRotationDegrees", options.normalizedRotationDegrees)
            put("stripExifMetadata", options.stripExifMetadata)
            put("videoResolution", options.videoResolution.name)
            put("muteVideoAudio", options.muteVideoAudio)
            put("trimStartSeconds", options.trimStartSeconds ?: -1L)
            put("trimEndSeconds", options.trimEndSeconds ?: -1L)
            put("customOutputBaseName", options.customOutputBaseName)
            put("mergeIntoSinglePdf", options.mergeIntoSinglePdf)
            put("pdfActionMode", options.pdfActionMode.name)
            put("pageRangeSpec", options.pageRangeSpec)
            put("pdfPassword", options.pdfPassword)
            put("pdfPageSize", options.pdfPageSize.name)
            put("pdfPageOrientation", options.pdfPageOrientation.name)
            put("pdfVisualPageEditsJson", options.pdfVisualPageEditsJson)

            val arr = JSONArray()
            for (item in inputs) {
                val obj = JSONObject().apply {
                    put("uri", item.uri.toString())
                    put("displayName", item.displayName)
                    put("sizeBytes", item.sizeBytes)
                    put("mimeType", item.mimeType)
                    put("format", item.format.name)
                }
                arr.put(obj)
            }
            put("inputs", arr)
        }
        payloadFile.writeText(root.toString(), Charsets.UTF_8)
        return payloadFile
    }
}
