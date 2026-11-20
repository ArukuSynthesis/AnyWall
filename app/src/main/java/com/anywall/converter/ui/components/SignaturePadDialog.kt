package com.anywall.converter.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.io.File
import java.io.FileOutputStream

/**
 * Lienzo táctil 100% local para dibujar y guardar una Firma Manuscrita transparente (`.png`)
 * que luego puede estamparse en cualquier página PDF desde `PdfVisualEditorDialog` o `ConfigureScreen` (v0.3).
 */
@Composable
fun SignaturePadDialog(
    onSignatureSaved: (String) -> Unit,
    onClearSavedSignature: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    val currentStroke = remember { mutableStateListOf<Offset>() }
    var canvasSize by remember { mutableIntStateOf(0) }
    var canvasWidthPx by remember { mutableIntStateOf(600) }
    var canvasHeightPx by remember { mutableIntStateOf(260) }
    var useBlueInk by remember { mutableIntStateOf(0) } // 0 = Negro, 1 = Azul

    val strokeColorCompose = if (useBlueInk == 1) {
        androidx.compose.ui.graphics.Color(0xFF1E3A8A)
    } else {
        androidx.compose.ui.graphics.Color(0xFF0F172A)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.Draw,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Firma Manuscrita Local",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Dibuja tu firma con el dedo en el recuadro. Se guardará de forma privada en el almacenamiento interno de AnyWall con fondo transparente.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = useBlueInk == 0,
                        onClick = { useBlueInk = 0 },
                        label = { Text("Tinta Negra") }
                    )
                    FilterChip(
                        selected = useBlueInk == 1,
                        onClick = { useBlueInk = 1 },
                        label = { Text("Tinta Azul") }
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(androidx.compose.ui.graphics.Color.White)
                        .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                        .onSizeChanged { sz: IntSize ->
                            if (sz.width > 0 && sz.height > 0) {
                                canvasWidthPx = sz.width
                                canvasHeightPx = sz.height
                                canvasSize = sz.width + sz.height
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    currentStroke.clear()
                                    currentStroke.add(offset)
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    currentStroke.add(change.position)
                                },
                                onDragEnd = {
                                    if (currentStroke.size > 1) {
                                        strokes.add(currentStroke.toList())
                                    }
                                    currentStroke.clear()
                                }
                            )
                        }
                ) {
                    ComposeCanvas(modifier = Modifier.fillMaxSize()) {
                        val allStrokes = if (currentStroke.size > 1) {
                            strokes + listOf(currentStroke.toList())
                        } else {
                            strokes.toList()
                        }
                        for (pts in allStrokes) {
                            if (pts.size < 2) continue
                            val path = Path().apply {
                                moveTo(pts[0].x, pts[0].y)
                                for (i in 1 until pts.size) {
                                    lineTo(pts[i].x, pts[i].y)
                                }
                            }
                            drawPath(
                                path = path,
                                color = strokeColorCompose,
                                style = Stroke(
                                    width = 6.5f,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }

                    if (strokes.isEmpty() && currentStroke.isEmpty()) {
                        Text(
                            text = "Firma aquí",
                            modifier = Modifier.align(Alignment.Center),
                            style = MaterialTheme.typography.bodyMedium,
                            color = androidx.compose.ui.graphics.Color.LightGray
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = {
                            strokes.clear()
                            currentStroke.clear()
                        }
                    ) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Limpiar lienzo")
                    }
                    TextButton(
                        onClick = {
                            strokes.clear()
                            currentStroke.clear()
                            onClearSavedSignature()
                        }
                    ) {
                        Text("Quitar firma del PDF")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = strokes.isNotEmpty(),
                onClick = {
                    val outW = canvasWidthPx.coerceAtLeast(320)
                    val outH = canvasHeightPx.coerceAtLeast(140)
                    val bmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
                    val androidCanvas = Canvas(bmp)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = if (useBlueInk == 1) Color.rgb(30, 58, 138) else Color.rgb(15, 23, 42)
                        style = Paint.Style.STROKE
                        strokeWidth = 7f
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                    }
                    for (pts in strokes) {
                        if (pts.size < 2) continue
                        val p = android.graphics.Path()
                        p.moveTo(pts[0].x, pts[0].y)
                        for (i in 1 until pts.size) {
                            p.lineTo(pts[i].x, pts[i].y)
                        }
                        androidCanvas.drawPath(p, paint)
                    }
                    val sigFile = File(context.filesDir, "anywall_signature.png")
                    FileOutputStream(sigFile).use { fos ->
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
                    }
                    bmp.recycle()
                    onSignatureSaved(sigFile.absolutePath)
                }
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Guardar y Usar Firma")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
