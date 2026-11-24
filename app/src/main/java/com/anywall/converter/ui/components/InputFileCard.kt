package com.anywall.converter.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anywall.converter.core.LocalThumbnailLoader
import com.anywall.converter.domain.model.InputFileItem

/**
 * Tarjeta que muestra un archivo cargado en el lote actual en `ConfigureScreen` (v0.4),
 * con reordenamiento táctil *Drag & Drop*, botón de Vista Previa Rápida (`QuickPeekDialog`),
 * miniatura local y controles para reordenar o quitar del lote.
 */
@Composable
fun InputFileCard(
    item: InputFileItem,
    index: Int,
    totalCount: Int,
    thumbnailLoader: LocalThumbnailLoader?,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onQuickPeek: (() -> Unit)? = null,
    onDragDeltaY: ((Float) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
    isBeingDragged: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border = if (isBeingDragged) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        colors = CardDefaults.cardColors(
            containerColor = if (isBeingDragged) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tirador táctil Drag & Drop cuando hay más de 1 archivo
            if (totalCount > 1 && onDragDeltaY != null) {
                Box(
                    modifier = Modifier
                        .size(width = 26.dp, height = 44.dp)
                        .pointerInput(index, totalCount, enabled) {
                            if (!enabled) return@pointerInput
                            detectDragGestures(
                                onDragEnd = { onDragEnd?.invoke() },
                                onDragCancel = { onDragEnd?.invoke() },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    onDragDeltaY(dragAmount.y)
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DragIndicator,
                        contentDescription = "Arrastrar para reordenar ${item.displayName}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }

            Box(
                modifier = if (onQuickPeek != null) {
                    Modifier.clickable { onQuickPeek() }
                } else {
                    Modifier
                }
            ) {
                ThumbnailPreviewBox(
                    uri = item.uri,
                    format = item.format,
                    thumbnailLoader = thumbnailLoader,
                    modifier = Modifier.size(width = 52.dp, height = 44.dp),
                    cornerRadius = 10.dp
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (onQuickPeek != null) {
                            Modifier.clickable { onQuickPeek() }
                        } else {
                            Modifier
                        }
                    ),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "#${index + 1} • ${item.format.category.displayNameEs} • ${item.format.label} • ${item.formattedSize}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (onQuickPeek != null) {
                IconButton(
                    onClick = onQuickPeek,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = "Vista previa rápida de ${item.displayName}",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (totalCount > 1) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = enabled && index > 0,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Subir ${item.displayName}",
                        tint = if (index > 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        }
                    )
                }

                IconButton(
                    onClick = onMoveDown,
                    enabled = enabled && index < totalCount - 1,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Bajar ${item.displayName}",
                        tint = if (index < totalCount - 1) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        }
                    )
                }
            }

            IconButton(
                onClick = onRemove,
                enabled = enabled,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Quitar ${item.displayName}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
