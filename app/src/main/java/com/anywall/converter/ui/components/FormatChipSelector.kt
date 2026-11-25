package com.anywall.converter.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anywall.converter.domain.model.SupportedFormat

/**
 * Selector segmentado de Formato Destino (v0.5).
 * Cuando existen tanto formatos de conversión directa como formatos de empaquetado/comprimidos,
 * los separa en un selector segmentado de 2 pestañas ("Convertir formato" vs "Empaquetar / Comprimir")
 * para evitar mostrar una pared saturada de chips simultáneos.
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
    val hasBothGroups = primaryTargets.isNotEmpty() && archiveTargets.isNotEmpty()

    var activeGroupIndex by remember {
        mutableIntStateOf(
            if (selectedFormat != null && (selectedFormat.isArchiveOutput || selectedFormat.isExtractionTarget) && primaryTargets.isNotEmpty()) 1 else 0
        )
    }

    LaunchedEffect(selectedFormat, hasBothGroups) {
        if (hasBothGroups && selectedFormat != null) {
            activeGroupIndex = if (selectedFormat.isArchiveOutput || selectedFormat.isExtractionTarget) 1 else 0
        } else if (primaryTargets.isEmpty() && archiveTargets.isNotEmpty()) {
            activeGroupIndex = 1
        } else {
            activeGroupIndex = 0
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (hasBothGroups) {
            // Barra segmentada moderna de 2 modos
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SegmentedModeButton(
                        title = "Convertir (${primaryTargets.size})",
                        icon = Icons.Default.SwapHoriz,
                        selected = activeGroupIndex == 0,
                        onClick = {
                            activeGroupIndex = 0
                            if (selectedFormat == null || selectedFormat !in primaryTargets) {
                                primaryTargets.firstOrNull()?.let(onSelectFormat)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    SegmentedModeButton(
                        title = "Comprimir / Extraer (${archiveTargets.size})",
                        icon = Icons.Default.Archive,
                        selected = activeGroupIndex == 1,
                        onClick = {
                            activeGroupIndex = 1
                            if (selectedFormat == null || selectedFormat !in archiveTargets) {
                                archiveTargets.firstOrNull()?.let(onSelectFormat)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        val displayedFormats = when {
            !hasBothGroups -> availableFormats
            activeGroupIndex == 1 -> archiveTargets
            else -> primaryTargets
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            displayedFormats.forEach { format ->
                val isSelected = selectedFormat == format
                val isArchiveOrExtract = format.isArchiveOutput || format.isExtractionTarget
                val chipText = when {
                    format == SupportedFormat.PDF && inputCount > 1 && mergeIntoSinglePdf ->
                        "PDF (Unir lote)"
                    format == SupportedFormat.EXTRACT_ARCHIVE ->
                        "Extraer contenido"
                    isArchiveOrExtract && inputCount > 1 ->
                        "Lote → ${format.label}"
                    else -> format.label
                }

                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectFormat(format) },
                    enabled = enabled,
                    shape = RoundedCornerShape(12.dp),
                    label = {
                        Text(
                            text = chipText,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                        )
                    },
                    leadingIcon = when {
                        isSelected -> {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                                )
                            }
                        }
                        format == SupportedFormat.PDF && inputCount > 1 -> {
                            {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                                )
                            }
                        }
                        format == SupportedFormat.EXTRACT_ARCHIVE -> {
                            {
                                Icon(
                                    imageVector = Icons.Default.Unarchive,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                                )
                            }
                        }
                        isArchiveOrExtract -> {
                            {
                                Icon(
                                    imageVector = Icons.Default.Archive,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                                )
                            }
                        }
                        else -> null
                    }
                )
            }
        }
    }
}

@Composable
private fun SegmentedModeButton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            androidx.compose.ui.graphics.Color.Transparent
        },
        border = if (selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
        } else {
            null
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}
