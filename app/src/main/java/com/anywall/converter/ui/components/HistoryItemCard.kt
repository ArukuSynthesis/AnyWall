package com.anywall.converter.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anywall.converter.core.LocalThumbnailLoader
import com.anywall.converter.domain.model.HistoryRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tarjeta de registro individual en `HistoryScreen` (v0.2) con miniatura local,
 * insignia de ahorro de peso (`Antes → Después, -62%`), soporte de selección múltiple
 * y botones directos para Abrir (`ACTION_VIEW`), Compartir (`ACTION_SEND`) y Eliminar.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryItemCard(
    record: HistoryRecord,
    thumbnailLoader: LocalThumbnailLoader? = null,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = categoryAccentColor(record.category)
    val icon = categoryIcon(record.category)
    val formattedDate = remember(record.createdAtMillis) {
        val sdf = SimpleDateFormat("dd MMM yyyy • HH:mm", Locale("es", "ES"))
        sdf.format(Date(record.createdAtMillis))
    }

    Surface(
        onClick = {
            if (isSelectionMode) {
                onToggleSelect()
            } else {
                onOpen()
            }
        },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            }
        ),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSelectionMode) {
                    IconButton(
                        onClick = onToggleSelect,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = if (isSelected) {
                                Icons.Default.CheckCircle
                            } else {
                                Icons.Default.RadioButtonUnchecked
                            },
                            contentDescription = "Seleccionar ${record.outputDisplayName}",
                            tint = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }

                ThumbnailPreviewBox(
                    uri = record.outputUri,
                    format = record.outputFormat,
                    thumbnailLoader = thumbnailLoader,
                    modifier = Modifier.size(44.dp),
                    cornerRadius = 11.dp,
                    fallbackContent = {
                        Icon(
                            imageVector = icon,
                            contentDescription = record.category.displayNameEs,
                            tint = accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = record.outputDisplayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Origen: ${record.sourceDisplayName} • $formattedDate",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!isSelectionMode) {
                    IconButton(
                        onClick = onOpen,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Abrir ${record.outputDisplayName}",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onShare,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Compartir ${record.outputDisplayName}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Eliminar del historial y almacenamiento",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(7.dp),
                    color = accent.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = "${record.sourceFormat.label} → ${record.outputFormat.label}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = accent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                val comparisonText = record.sizeComparisonBadgeText
                val delta = record.sizeChangePercent
                if (comparisonText != null && delta != null) {
                    val isSavings = delta <= 0
                    val badgeBg = if (isSavings) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    }
                    val badgeFg = if (isSavings) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    }
                    Surface(
                        shape = RoundedCornerShape(7.dp),
                        color = badgeBg
                    ) {
                        Text(
                            text = comparisonText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = badgeFg,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(7.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Text(
                            text = record.formattedSize,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}
