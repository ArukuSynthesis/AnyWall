package com.anywall.converter.core

import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.anywall.converter.domain.model.HistoryRecord
import java.io.File

/**
 * Utilidad para abrir (`Intent.ACTION_VIEW`) y compartir uno (`Intent.ACTION_SEND`)
 * o múltiples (`Intent.ACTION_SEND_MULTIPLE`) archivos convertidos usando URIs seguras
 * de `MediaStore` o `FileProvider`.
 */
object FileShareHelper {

    fun resolveShareableUri(context: Context, record: HistoryRecord): Uri? {
        if (record.outputUriString.isNotBlank()) {
            val parsed = Uri.parse(record.outputUriString)
            if (parsed.scheme == ContentResolver.SCHEME_CONTENT) {
                val accessible = try {
                    context.contentResolver.openAssetFileDescriptor(parsed, "r")?.use { true } ?: false
                } catch (_: Throwable) {
                    false
                }
                if (accessible) return parsed
            }
        }

        if (record.outputAbsolutePath.isNotBlank()) {
            val file = File(record.outputAbsolutePath)
            if (file.exists()) {
                return try {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                } catch (_: Throwable) {
                    null
                }
            }
        }
        return null
    }

    fun openConvertedFile(context: Context, record: HistoryRecord) {
        val uri = resolveShareableUri(context, record)
        if (uri == null) {
            Toast.makeText(
                context,
                "El archivo ya no se encuentra disponible en el almacenamiento.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val mime = if (record.outputMimeType.isBlank() || record.outputMimeType == "resource/folder") {
            "*/*"
        } else {
            record.outputMimeType
        }

        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(Intent.createChooser(viewIntent, "Abrir ${record.outputDisplayName}").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                context,
                "No se encontró una aplicación instalada para abrir este formato.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun shareConvertedFile(context: Context, record: HistoryRecord) {
        val uri = resolveShareableUri(context, record)
        if (uri == null) {
            Toast.makeText(
                context,
                "El archivo ya no se encuentra disponible para compartir.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val mime = if (record.outputMimeType.isBlank() || record.outputMimeType == "resource/folder") {
            "*/*"
        } else {
            record.outputMimeType
        }

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, record.outputDisplayName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(sendIntent, "Compartir ${record.outputDisplayName}").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    /**
     * Comparte múltiples archivos seleccionados del historial en una sola acción (`ACTION_SEND_MULTIPLE`).
     */
    fun shareMultipleConvertedFiles(context: Context, records: List<HistoryRecord>) {
        if (records.isEmpty()) return
        if (records.size == 1) {
            shareConvertedFile(context, records.first())
            return
        }

        val uris = ArrayList<Uri>()
        for (record in records) {
            resolveShareableUri(context, record)?.let(uris::add)
        }

        if (uris.isEmpty()) {
            Toast.makeText(
                context,
                "Los archivos seleccionados ya no están disponibles en el almacenamiento.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val distinctMimes = records.map { it.outputMimeType }.filter { it.isNotBlank() && it != "resource/folder" }.distinct()
        val commonMime = if (distinctMimes.size == 1) distinctMimes.first() else "*/*"

        val sendMultipleIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = commonMime
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(
            sendMultipleIntent,
            "Compartir ${uris.size} archivos convertidos"
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
