package com.anywall.converter.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anywall.converter.core.FileShareHelper
import com.anywall.converter.core.LocalThumbnailLoader
import com.anywall.converter.domain.model.ActiveJobState
import com.anywall.converter.domain.model.HistoryRecord
import com.anywall.converter.domain.model.InputFileItem
import kotlin.math.roundToInt

/**
 * Tarjeta reactiva de progreso en vivo y Resultado Post-Conversión Inmediato (v0.6):
 * - Mientras `jobState.isRunning`: muestra porcentaje, barra animada y botón Cancelar.
 * - Cuando `jobState.isCompleted`: muestra la comparativa **Antes → Después (-X%)**,
 *   la lista de archivos recién generados con miniatura y botones directos **"Abrir ahora"**
 *   y **"Compartir"** sin obligar a cambiar a la pestaña Historial.
 */
@Composable
fun ConversionProgressCard(
    jobState: ActiveJobState,
    onCancel: () -> Unit,
    onOpenHistory: () -> Unit,
    latestCompletedRecords: List<HistoryRecord> = emptyList(),
    thumbnailLoader: LocalThumbnailLoader? = null,
    modifier: Modifier = Modifier
) {
    if (!jobState.isRunning && !jobState.isCompleted && !jobState.isFailed) {
        return
    }

    val context = LocalContext.current
    val animatedProgress by animateFloatAsState(
        targetValue = jobState.progress.coerceIn(0f, 1f),
        label = "conversion_progress"
    )
    val percent = (animatedProgress * 100f).roundToInt()

    val containerColor = when {
        jobState.isFailed -> MaterialTheme.colorScheme.errorContainer
        jobState.isCompleted -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f)
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = when {
        jobState.isFailed -> MaterialTheme.colorScheme.onErrorContainer
        jobState.isCompleted -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    val recentOutputs = if (jobState.isCompleted && latestCompletedRecords.isNotEmpty()) {
        val count = jobState.generatedCount.coerceIn(1, 5)
        latestCompletedRecords.take(count)
    } else {
        emptyList()
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val statusIcon = when {
                    jobState.isFailed -> Icons.Default.Error
                    jobState.isCompleted -> Icons.Default.CheckCircle
                    else -> Icons.Default.Sync
                }
                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(24.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            jobState.isFailed -> "Error en la conversión"
                            jobState.isCompleted -> "¡Conversión lista!"
                            else -> "Procesando lote ($percent%)"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = contentColor
                    )
                    Text(
                        text = jobState.errorMessage?.takeIf { jobState.isFailed }
                            ?: jobState.statusMessage.ifBlank { "Guardado en Downloads/AnyWall/" },
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.88f)
                    )
                }
            }

            if (jobState.isRunning) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(50)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = contentColor.copy(alpha = 0.2f)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ejecutándose en segundo plano",
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor.copy(alpha = 0.85f)
                    )
                    OutlinedButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.Default.Cancel,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cancelar")
                    }
                }
            }

            if (jobState.isCompleted) {
                // Resumen Antes -> Después si tenemos los registros recién generados
                if (recentOutputs.isNotEmpty()) {
                    val totalBefore = recentOutputs.sumOf { it.sourceSizeBytes }
                    val totalAfter = recentOutputs.sumOf { it.outputSizeBytes }
                    if (totalBefore > 0L && totalAfter > 0L) {
                        val deltaPct = (((totalAfter.toDouble() - totalBefore.toDouble()) / totalBefore.toDouble()) * 100.0)
                            .roundToInt()
                        val deltaLabel = if (deltaPct <= 0) "$deltaPct% ahorrado" else "+$deltaPct%"

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Antes: ${InputFileItem.formatBytes(totalBefore)} → Después: ${InputFileItem.formatBytes(totalAfter)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                ) {
                                    Text(
                                        text = deltaLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Tarjetas individuales con Abrir / Compartir directo
                    recentOutputs.forEach { rec ->
                        Surface(
                            onClick = { FileShareHelper.openConvertedFile(context, rec) },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ThumbnailPreviewBox(
                                    uri = android.net.Uri.parse(rec.outputUriString),
                                    format = rec.outputFormat,
                                    thumbnailLoader = thumbnailLoader,
                                    modifier = Modifier.size(42.dp),
                                    cornerRadius = 10.dp
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = rec.outputDisplayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${rec.outputFormat.label} • ${InputFileItem.formatBytes(rec.outputSizeBytes)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                FilledTonalButton(
                                    onClick = { FileShareHelper.openConvertedFile(context, rec) },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = "Abrir",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Abrir", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                }
                                IconButton(
                                    onClick = { FileShareHelper.shareConvertedFile(context, rec) },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Compartir",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = contentColor.copy(alpha = 0.15f))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (recentOutputs.size > 1) {
                        FilledTonalButton(
                            onClick = { FileShareHelper.shareMultipleConvertedFiles(context, recentOutputs) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Compartir lote (${recentOutputs.size})")
                        }
                    }
                    Button(
                        onClick = onOpenHistory,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Historial completo")
                    }
                }
            }
        }
    }
}
