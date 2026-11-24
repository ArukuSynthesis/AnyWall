package com.anywall.converter.data.engines

import com.anywall.converter.core.UriFileResolver
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Motor de Renombrado Masivo sin reconvertir (`BatchFileRenamer` - v0.4).
 * Soporta plantillas (`{nombre}`, `{indice}`, `{fecha}`, `{formato}`), prefijo/sufijo,
 * buscar/reemplazar (literal o Regex), capitalización y vista previa Antes → Después libre de colisiones.
 */
object BatchFileRenamer {

    enum class RenameCaseMode(val labelEs: String, val labelEn: String) {
        KEEP("Original", "Original"),
        LOWERCASE("minúsculas", "lowercase"),
        UPPERCASE("MAYÚSCULAS", "UPPERCASE"),
        TITLE_CASE("Tipo Título", "Title Case"),
        SNAKE_CASE("snake_case", "snake_case");
    }

    data class BatchRenameSpec(
        val template: String = "{nombre}",
        val prefix: String = "",
        val suffix: String = "",
        val findText: String = "",
        val replaceText: String = "",
        val useRegex: Boolean = false,
        val ignoreCaseInFind: Boolean = true,
        val caseMode: RenameCaseMode = RenameCaseMode.KEEP,
        val startIndex: Int = 1,
        val paddingDigits: Int = 2
    )

    data class RenamePreviewItem(
        val index: Int,
        val originalFileName: String,
        val newFileName: String
    ) {
        val isModified: Boolean
            get() = originalFileName != newFileName
    }

    fun generatePreview(
        originalFileNames: List<String>,
        spec: BatchRenameSpec,
        fixedDateString: String? = null
    ): List<RenamePreviewItem> {
        if (originalFileNames.isEmpty()) return emptyList()
        val dateStr = fixedDateString ?: SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val padWidth = spec.paddingDigits.coerceIn(1, 6)
        val usedFinalNames = mutableSetOf<String>()

        return originalFileNames.mapIndexed { idx, rawOriginal ->
            val cleanOriginal = rawOriginal.trim().ifBlank { "archivo_${idx + 1}" }
            val (origBase, origExt) = splitBaseAndExtension(cleanOriginal)
            val seqNumber = (spec.startIndex.coerceAtLeast(0) + idx)
            val indexStr = seqNumber.toString().padStart(padWidth, '0')
            val extLower = origExt.lowercase(Locale.ROOT)

            // 1. Buscar y reemplazar sobre el nombre base original
            var workingBase = origBase
            if (spec.findText.isNotEmpty()) {
                workingBase = try {
                    if (spec.useRegex) {
                        val opts = if (spec.ignoreCaseInFind) setOf(RegexOption.IGNORE_CASE) else emptySet()
                        workingBase.replace(Regex(spec.findText, opts), spec.replaceText)
                    } else {
                        workingBase.replace(spec.findText, spec.replaceText, ignoreCase = spec.ignoreCaseInFind)
                    }
                } catch (_: Throwable) {
                    workingBase
                }
            }

            // 2. Resolver plantilla ({nombre}, {indice}, {fecha}, {formato}, {ext})
            val tpl = spec.template.trim().ifBlank { "{nombre}" }
            var resolvedBase = tpl
                .replace(Regex("\\{nombre\\}", RegexOption.IGNORE_CASE), workingBase)
                .replace(Regex("\\{name\\}", RegexOption.IGNORE_CASE), workingBase)
                .replace(Regex("\\{indice\\}", RegexOption.IGNORE_CASE), indexStr)
                .replace(Regex("\\{index\\}", RegexOption.IGNORE_CASE), indexStr)
                .replace(Regex("\\{fecha\\}", RegexOption.IGNORE_CASE), dateStr)
                .replace(Regex("\\{date\\}", RegexOption.IGNORE_CASE), dateStr)
                .replace(Regex("\\{formato\\}", RegexOption.IGNORE_CASE), extLower)
                .replace(Regex("\\{format\\}", RegexOption.IGNORE_CASE), extLower)
                .replace(Regex("\\{ext\\}", RegexOption.IGNORE_CASE), extLower)

            // 3. Añadir prefijo y sufijo
            resolvedBase = "${spec.prefix}$resolvedBase${spec.suffix}".trim()
            if (resolvedBase.isEmpty()) {
                resolvedBase = "archivo_$indexStr"
            }

            // 4. Aplicar modo de capitalización
            resolvedBase = applyCaseTransformation(resolvedBase, spec.caseMode)
            resolvedBase = UriFileResolver.sanitizeFileName(resolvedBase)

            // 5. Garantizar unicidad en el lote para evitar colisiones
            var candidateFull = if (origExt.isNotEmpty()) "$resolvedBase.$origExt" else resolvedBase
            if (!usedFinalNames.add(candidateFull.lowercase(Locale.ROOT))) {
                var collisionCounter = 2
                while (true) {
                    val numbered = if (origExt.isNotEmpty()) {
                        "${resolvedBase}_$collisionCounter.$origExt"
                    } else {
                        "${resolvedBase}_$collisionCounter"
                    }
                    if (usedFinalNames.add(numbered.lowercase(Locale.ROOT))) {
                        candidateFull = numbered
                        break
                    }
                    collisionCounter++
                }
            }

            RenamePreviewItem(
                index = idx,
                originalFileName = cleanOriginal,
                newFileName = candidateFull
            )
        }
    }

    fun applyCaseTransformation(text: String, mode: RenameCaseMode): String {
        return when (mode) {
            RenameCaseMode.KEEP -> text
            RenameCaseMode.LOWERCASE -> text.lowercase(Locale.ROOT)
            RenameCaseMode.UPPERCASE -> text.uppercase(Locale.ROOT)
            RenameCaseMode.TITLE_CASE -> {
                text.split(Regex("[\\s_\\-]+"))
                    .filter { it.isNotEmpty() }
                    .joinToString(" ") { word ->
                        word.lowercase(Locale.ROOT).replaceFirstChar { ch ->
                            if (ch.isLowerCase()) ch.titlecase(Locale.ROOT) else ch.toString()
                        }
                    }
                    .ifBlank { text }
            }
            RenameCaseMode.SNAKE_CASE -> {
                text.trim()
                    .replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
                    .replace(Regex("[\\s\\-]+"), "_")
                    .replace(Regex("_+"), "_")
                    .lowercase(Locale.ROOT)
                    .trim('_')
                    .ifBlank { text.lowercase(Locale.ROOT) }
            }
        }
    }

    private fun splitBaseAndExtension(fileName: String): Pair<String, String> {
        val lower = fileName.lowercase(Locale.ROOT)
        for (compound in listOf(".tar.gz", ".tar.xz", ".tar.bz2")) {
            if (lower.endsWith(compound) && fileName.length > compound.length) {
                val base = fileName.dropLast(compound.length)
                val ext = fileName.takeLast(compound.length - 1)
                return base to ext
            }
        }
        val dotIdx = fileName.lastIndexOf('.')
        return if (dotIdx > 0 && dotIdx < fileName.length - 1) {
            fileName.substring(0, dotIdx) to fileName.substring(dotIdx + 1)
        } else {
            fileName to ""
        }
    }
}
