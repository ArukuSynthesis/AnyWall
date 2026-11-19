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
 * Utilidad para abrir (`Intent.ACTION_VIEW`) y compartir (`Intent.ACTION_SEND`)
 * archivos convertidos usando URIs seguras de `MediaStore` o `FileProvider`.
 */
object FileShareHelper {

    fun resolveShareableUri(context: Context, record: HistoryRecord): Uri? {
        if (record.outputUriString.isNotBlank()) {
            val parsed = Uri.parse(record.outputUriString)
            if (parsed.scheme == ContentResolver.SCHEME_CONTENT) {
                // Verificar si el descriptor sigue siendo accesible
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
}
