package com.anywall.converter.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anywall.converter.domain.model.SupportedFormat

/**
 * Selector inteligente de Formato Destino (v0.2) que presenta únicamente los formatos válidos
 * para los archivos cargados, resaltando acciones por lote como "Unir en un solo PDF"
 * o "Empaquetar en ZIP / TAR.XZ / TAR.BZ2".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FormatChipSelector(
    availableFormats: List<SupportedFormat>,
    selectedFormat: SupportedFormat?,
    inputCount: Int,
    mergeIntoSinglePdf: Boolean,
    onSelectFormat: (SupportedFormat) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val primaryTargets = availableFormats.filter { !it.isArchiveOutput && !it.isExtractionTarget }
    val archiveTargets = availableFormats.filter { it.isArchiveOutput || it.isExtractionTarget }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (primaryTargets.isNotEmpty()) {
            Text(
                text = "Conversión directa",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                primaryTargets.forEach { format ->
                    val isSelected = selectedFormat == format
                    val chipText = when {
                        format == SupportedFormat.PDF && inputCount > 1 && mergeIntoSinglePdf ->
                            "PDF (Unir en un solo PDF)"
                        else -> format.label
                    }
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectFormat(format) },
                        enabled = enabled,
                        label = {
                            Text(
                                text = chipText,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        leadingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                                )
                            }
                        } else if (format == SupportedFormat.PDF && inputCount > 1) {
                            {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                                )
                            }
                        } else null
                    )
                }
            }
        }

        if (archiveTargets.isNotEmpty()) {
            Text(
                text = "Empaquetado y Archivos Comprimidos",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                archiveTargets.forEach { format ->
                    val isSelected = selectedFormat == format
                    val chipText = when {
                        format == SupportedFormat.EXTRACT_ARCHIVE -> "Extraer Contenido"
                        inputCount > 1 -> "Empaquetar en ${format.label}"
                        else -> "Empaquetar (${format.label})"
                    }
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectFormat(format) },
                        enabled = enabled,
                        label = {
                            Text(
                                text = chipText,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        leadingIcon = {
                            val icon = when {
                                isSelected -> Icons.Default.Check
                                format == SupportedFormat.EXTRACT_ARCHIVE -> Icons.Default.Unarchive
                                else -> Icons.Default.Archive
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    )
                }
            }
        }
    }
}
