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
 * Planificador de lotes de conversión sobre `WorkManager`.
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
            BatchConversionWorker.KEY_VIDEO_RESOLUTION to options.videoResolution.name,
            BatchConversionWorker.KEY_CUSTOM_OUTPUT_NAME to options.customOutputBaseName,
            BatchConversionWorker.KEY_MERGE_SINGLE_PDF to options.mergeIntoSinglePdf,
            BatchConversionWorker.KEY_PDF_ACTION_MODE to options.pdfActionMode.name,
            BatchConversionWorker.KEY_PAGE_RANGE_SPEC to options.pageRangeSpec
        )

        val request = OneTimeWorkRequestBuilder<BatchConversionWorker>()
            .setInputData(data)
            .addTag(BatchConversionWorker.WORK_TAG)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        workManager.enqueueUniqueWork(
            BatchConversionWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
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
            put("videoResolution", options.videoResolution.name)
            put("customOutputBaseName", options.customOutputBaseName)
            put("mergeIntoSinglePdf", options.mergeIntoSinglePdf)
            put("pdfActionMode", options.pdfActionMode.name)
            put("pageRangeSpec", options.pageRangeSpec)

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
