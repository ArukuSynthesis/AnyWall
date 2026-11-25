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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anywall.converter.core.LocalThumbnailLoader
import com.anywall.converter.domain.model.InputFileItem

/**
 * Tarjeta limpia de archivo en cola (`InputFileCard` - v0.5).
 * Elimina el exceso de flechas repetitivas cuando hay Drag & Drop táctil, mostrando:
 * - Tirador táctil de arrastre
 * - Miniatura con toque directo para Vista Rápida (`QuickPeekDialog`)
 * - Nombre del archivo + insignia coloreada de formato y peso
 * - Botón rápido de vista previa y botón de quitar
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
    val catColor = categoryAccentColor(item.format.category)
    val currentOnDragDeltaY by rememberUpdatedState(onDragDeltaY)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = if (isBeingDragged) 2.dp else 1.dp,
            color = if (isBeingDragged) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f)
            }
        ),
        color = if (isBeingDragged) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tirador táctil Drag & Drop cuando hay más de 1 archivo
            if (totalCount > 1 && onDragDeltaY != null) {
                Box(
                    modifier = Modifier
                        .size(width = 24.dp, height = 42.dp)
                        .pointerInput(item.uri, enabled) {
                            if (!enabled) return@pointerInput
                            detectDragGestures(
                                onDragEnd = { currentOnDragEnd?.invoke() },
                                onDragCancel = { currentOnDragEnd?.invoke() },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    currentOnDragDeltaY?.invoke(dragAmount.y)
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DragIndicator,
                        contentDescription = "Arrastrar para reordenar ${item.displayName}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
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
                    modifier = Modifier.size(width = 48.dp, height = 44.dp),
                    cornerRadius = 11.dp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

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
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = catColor.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = item.format.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = catColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                    Text(
                        text = "${item.formattedSize}${if (totalCount > 1) " • #${index + 1}" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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

            IconButton(
                onClick = onRemove,
                enabled = enabled,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Quitar ${item.displayName}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
