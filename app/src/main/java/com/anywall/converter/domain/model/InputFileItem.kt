package com.anywall.converter.domain.model

import android.net.Uri
import java.util.Locale

/**
 * Representa un archivo de entrada cargado por el usuario para convertir.
 */
data class InputFileItem(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String,
    val format: SupportedFormat
) {
    val baseNameWithoutExtension: String
        get() {
            val lower = displayName.lowercase(Locale.ROOT)
            return when {
                lower.endsWith(".tar.gz") -> displayName.dropLast(7)
                lower.endsWith(".tar.xz") -> displayName.dropLast(7)
                lower.endsWith(".tar.bz2") -> displayName.dropLast(8)
                lower.contains('.') -> displayName.substringBeforeLast('.')
                else -> displayName
            }.ifBlank { "archivo" }
        }

    val formattedSize: String
        get() = formatBytes(sizeBytes)

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0L) return "0 B"
            val kb = bytes / 1024.0
            if (kb < 1.0) return "$bytes B"
            val mb = kb / 1024.0
            if (mb < 1.0) return String.format(Locale.US, "%.1f KB", kb)
            val gb = mb / 1024.0
            return if (gb < 1.0) {
                String.format(Locale.US, "%.2f MB", mb)
            } else {
                String.format(Locale.US, "%.2f GB", gb)
            }
        }
    }
}
