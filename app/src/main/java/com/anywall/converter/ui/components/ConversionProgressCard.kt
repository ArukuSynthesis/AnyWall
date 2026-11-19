package com.anywall.converter.ui.components

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anywall.converter.domain.model.ActiveJobState
import kotlin.math.roundToInt

/**
 * Tarjeta reactiva de progreso en vivo conectada al `BatchConversionWorker` de WorkManager.
 */
@Composable
fun ConversionProgressCard(
    jobState: ActiveJobState,
    onCancel: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!jobState.isRunning && !jobState.isCompleted && !jobState.isFailed) {
        return
    }

    val animatedProgress by animateFloatAsState(
        targetValue = jobState.progress.coerceIn(0f, 1f),
        label = "conversion_progress"
    )
    val percent = (animatedProgress * 100f).roundToInt()

    val containerColor = when {
        jobState.isFailed -> MaterialTheme.colorScheme.errorContainer
        jobState.isCompleted -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = when {
        jobState.isFailed -> MaterialTheme.colorScheme.onErrorContainer
        jobState.isCompleted -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
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
                Text(
                    text = when {
                        jobState.isFailed -> "Error en la conversión"
                        jobState.isCompleted -> "¡Conversión finalizada con éxito!"
                        else -> "Procesando en segundo plano ($percent%)"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = jobState.errorMessage?.takeIf { jobState.isFailed }
                    ?: jobState.statusMessage.ifBlank { "Guardado en Downloads/AnyWall/" },
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor
            )

            if (jobState.isRunning) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(50)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = contentColor.copy(alpha = 0.2f)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Puedes minimizar la app sin interrupciones",
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
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onOpenHistory,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ver archivos en Historial (Downloads/AnyWall/)")
                }
            }
        }
    }
}
