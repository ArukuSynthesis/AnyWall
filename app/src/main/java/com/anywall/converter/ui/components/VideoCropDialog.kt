package com.anywall.converter.ui.components

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.anywall.converter.R
import com.anywall.converter.domain.model.CropAspectRatioPreset
import com.anywall.converter.domain.model.InputFileItem
import com.anywall.converter.domain.model.VideoCropSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

private enum class VideoCropHandle {
    NONE,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    TOP_EDGE,
    BOTTOM_EDGE,
    LEFT_EDGE,
    RIGHT_EDGE,
    INSIDE_MOVE
}

/**
 * Recorte Táctil de Encuadre de Video (`VideoCropDialog` - v0.4):
 * - Extrae un fotograma real del video y permite arrastrar 8 tiradores táctiles (4 esquinas + 4 bordes)
 *   o elegir proporciones estándar (`Libre`, `Original`, `1:1`, `4:5`, `3:4`, `16:9`, `9:16`).
 * - El resultado `VideoCropSpec` se aplica en `MediaEngineImpl` mediante `androidx.media3.effect.Crop`.
 */
@Composable
fun VideoCropDialog(
    videoFile: InputFileItem,
    initialCropSpec: VideoCropSpec,
    sampleTimeMs: Long = 0L,
    onApplyCrop: (VideoCropSpec) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var cropSpec by remember(initialCropSpec) { mutableStateOf(initialCropSpec) }
    var frameBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var activeHandle by remember { mutableStateOf(VideoCropHandle.NONE) }

    LaunchedEffect(videoFile.uri, sampleTimeMs) {
        isLoading = true
        val extracted = withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, videoFile.uri)
                val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 4000L
                val probeUs = if (sampleTimeMs > 0L) sampleTimeMs * 1000L else (dur / 4L) * 1000L
                retriever.getFrameAtTime(probeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.getFrameAtTime(0L)
            } catch (_: Exception) {
                null
            } finally {
                runCatching { retriever.release() }
            }
        }
        frameBitmap = extracted
        isLoading = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .clip(RoundedCornerShape(20.dp)),
            color = Color(0xFF0B0D10),
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Crop,
                            contentDescription = null,
                            tint = Color(0xFF6366F1)
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.video_crop_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = videoFile.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF94A3B8),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
                    }
                }

                // Lienzo táctil de recorte con 8 tiradores sobre el fotograma del video
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(310.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF131720)),
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = frameBitmap
                    if (isLoading) {
                        CircularProgressIndicator(color = Color(0xFF6366F1))
                    } else {
                        val bmpW = (bmp?.width ?: 1280).toFloat()
                        val bmpH = (bmp?.height ?: 720).toFloat()

                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                                .pointerInput(bmpW, bmpH) {
                                    detectDragGestures(
                                        onDragStart = { touch ->
                                            val rect = computeVideoFrameRect(size.width.toFloat(), size.height.toFloat(), bmpW, bmpH)
                                            activeHandle = hitTestVideoCropHandle(touch, rect, cropSpec)
                                        },
                                        onDragEnd = { activeHandle = VideoCropHandle.NONE },
                                        onDragCancel = { activeHandle = VideoCropHandle.NONE },
                                        onDrag = { change, dragAmount ->
                                            if (activeHandle == VideoCropHandle.NONE) return@detectDragGestures
                                            change.consume()
                                            val rect = computeVideoFrameRect(size.width.toFloat(), size.height.toFloat(), bmpW, bmpH)
                                            val dx = dragAmount.x / rect.width.coerceAtLeast(1f)
                                            val dy = dragAmount.y / rect.height.coerceAtLeast(1f)
                                            cropSpec = dragVideoCropSpec(
                                                current = cropSpec,
                                                handle = activeHandle,
                                                dx = dx,
                                                dy = dy,
                                                videoAspect = bmpW / bmpH.coerceAtLeast(1f)
                                            )
                                        }
                                    )
                                }
                        ) {
                            val rect = computeVideoFrameRect(size.width, size.height, bmpW, bmpH)
                            if (bmp != null) {
                                drawIntoCanvas { canvas ->
                                    val dst = android.graphics.RectF(rect.left, rect.top, rect.right, rect.bottom)
                                    val paint = android.graphics.Paint(
                                        android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG
                                    )
                                    canvas.nativeCanvas.drawBitmap(bmp, null, dst, paint)
                                }
                            } else {
                                drawRect(
                                    color = Color(0xFF1E293B),
                                    topLeft = Offset(rect.left, rect.top),
                                    size = Size(rect.width, rect.height)
                                )
                            }

                            val cl = rect.left + cropSpec.leftNorm * rect.width
                            val ct = rect.top + cropSpec.topNorm * rect.height
                            val cr = rect.left + cropSpec.rightNorm * rect.width
                            val cb = rect.top + cropSpec.bottomNorm * rect.height
                            val cw = (cr - cl).coerceAtLeast(1f)
                            val ch = (cb - ct).coerceAtLeast(1f)

                            // Máscara oscura exterior
                            val mask = Color(0xAA000000)
                            drawRect(mask, Offset(rect.left, rect.top), Size(rect.width, ct - rect.top))
                            drawRect(mask, Offset(rect.left, cb), Size(rect.width, rect.bottom - cb))
                            drawRect(mask, Offset(rect.left, ct), Size(cl - rect.left, ch))
                            drawRect(mask, Offset(cr, ct), Size(rect.right - cr, ch))

                            // Cuadrícula 3x3
                            for (i in 1..2) {
                                val gx = cl + cw * i / 3f
                                val gy = ct + ch * i / 3f
                                drawLine(Color.White.copy(alpha = 0.55f), Offset(gx, ct), Offset(gx, cb), 1.dp.toPx())
                                drawLine(Color.White.copy(alpha = 0.55f), Offset(cl, gy), Offset(cr, gy), 1.dp.toPx())
                            }

                            // Borde
                            drawRect(
                                color = Color.White,
                                topLeft = Offset(cl, ct),
                                size = Size(cw, ch),
                                style = Stroke(width = 2.dp.toPx())
                            )

                            // 8 Tiradores táctiles
                            val handles = listOf(
                                Offset(cl, ct),
                                Offset(cr, ct),
                                Offset(cl, cb),
                                Offset(cr, cb),
                                Offset((cl + cr) / 2f, ct),
                                Offset((cl + cr) / 2f, cb),
                                Offset(cl, (ct + cb) / 2f),
                                Offset(cr, (ct + cb) / 2f)
                            )
                            handles.forEachIndexed { idx, pt ->
                                val r = if (idx < 4) 7.5.dp.toPx() else 5.5.dp.toPx()
                                drawCircle(Color(0xFF6366F1), r + 2.dp.toPx(), pt)
                                drawCircle(Color.White, r, pt)
                            }
                        }
                    }
                }

                // Chips de proporción de aspecto
                val videoAspect = ((frameBitmap?.width ?: 1280).toFloat() / (frameBitmap?.height ?: 720).toFloat().coerceAtLeast(1f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CropAspectRatioPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = cropSpec.aspectPreset == preset,
                            onClick = {
                                cropSpec = applyVideoAspectPreset(cropSpec, preset, videoAspect)
                            },
                            label = {
                                Text(preset.labelEs, style = MaterialTheme.typography.labelSmall)
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF6366F1),
                                selectedLabelColor = Color.White,
                                labelColor = Color(0xFFCBD5E1)
                            )
                        )
                    }
                }

                // Acciones
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { cropSpec = VideoCropSpec() }
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Original", color = Color.White)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onDismiss) {
                            Text(stringResource(R.string.btn_cancel), color = Color.White)
                        }
                        Button(
                            onClick = { onApplyCrop(cropSpec) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF6366F1),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.btn_apply_visual_edits))
                        }
                    }
                }
            }
        }
    }
}

private fun computeVideoFrameRect(canvasW: Float, canvasH: Float, bmpW: Float, bmpH: Float): Rect {
    val scale = min(canvasW / bmpW.coerceAtLeast(1f), canvasH / bmpH.coerceAtLeast(1f))
    val dw = bmpW * scale
    val dh = bmpH * scale
    val l = (canvasW - dw) / 2f
    val t = (canvasH - dh) / 2f
    return Rect(l, t, l + dw, t + dh)
}

private fun hitTestVideoCropHandle(touch: Offset, rect: Rect, spec: VideoCropSpec): VideoCropHandle {
    val cl = rect.left + spec.leftNorm * rect.width
    val ct = rect.top + spec.topNorm * rect.height
    val cr = rect.left + spec.rightNorm * rect.width
    val cb = rect.top + spec.bottomNorm * rect.height
    val mx = (cl + cr) / 2f
    val my = (ct + cb) / 2f
    val hitR = 36.0
    fun near(pt: Offset) = hypot((touch.x - pt.x).toDouble(), (touch.y - pt.y).toDouble()) <= hitR

    return when {
        near(Offset(cl, ct)) -> VideoCropHandle.TOP_LEFT
        near(Offset(cr, ct)) -> VideoCropHandle.TOP_RIGHT
        near(Offset(cl, cb)) -> VideoCropHandle.BOTTOM_LEFT
        near(Offset(cr, cb)) -> VideoCropHandle.BOTTOM_RIGHT
        near(Offset(mx, ct)) || (touch.x in cl..cr && abs(touch.y - ct) <= 22f) -> VideoCropHandle.TOP_EDGE
        near(Offset(mx, cb)) || (touch.x in cl..cr && abs(touch.y - cb) <= 22f) -> VideoCropHandle.BOTTOM_EDGE
        near(Offset(cl, my)) || (touch.y in ct..cb && abs(touch.x - cl) <= 22f) -> VideoCropHandle.LEFT_EDGE
        near(Offset(cr, my)) || (touch.y in ct..cb && abs(touch.x - cr) <= 22f) -> VideoCropHandle.RIGHT_EDGE
        touch.x in cl..cr && touch.y in ct..cb -> VideoCropHandle.INSIDE_MOVE
        else -> VideoCropHandle.NONE
    }
}

private fun dragVideoCropSpec(
    current: VideoCropSpec,
    handle: VideoCropHandle,
    dx: Float,
    dy: Float,
    videoAspect: Float
): VideoCropSpec {
    val minSpan = 0.10f
    var l = current.leftNorm
    var t = current.topNorm
    var r = current.rightNorm
    var b = current.bottomNorm

    when (handle) {
        VideoCropHandle.TOP_LEFT -> {
            l = (l + dx).coerceIn(0f, r - minSpan)
            t = (t + dy).coerceIn(0f, b - minSpan)
        }
        VideoCropHandle.TOP_RIGHT -> {
            r = (r + dx).coerceIn(l + minSpan, 1f)
            t = (t + dy).coerceIn(0f, b - minSpan)
        }
        VideoCropHandle.BOTTOM_LEFT -> {
            l = (l + dx).coerceIn(0f, r - minSpan)
            b = (b + dy).coerceIn(t + minSpan, 1f)
        }
        VideoCropHandle.BOTTOM_RIGHT -> {
            r = (r + dx).coerceIn(l + minSpan, 1f)
            b = (b + dy).coerceIn(t + minSpan, 1f)
        }
        VideoCropHandle.TOP_EDGE -> t = (t + dy).coerceIn(0f, b - minSpan)
        VideoCropHandle.BOTTOM_EDGE -> b = (b + dy).coerceIn(t + minSpan, 1f)
        VideoCropHandle.LEFT_EDGE -> l = (l + dx).coerceIn(0f, r - minSpan)
        VideoCropHandle.RIGHT_EDGE -> r = (r + dx).coerceIn(l + minSpan, 1f)
        VideoCropHandle.INSIDE_MOVE -> {
            val w = r - l
            val h = b - t
            val nl = (l + dx).coerceIn(0f, 1f - w)
            val nt = (t + dy).coerceIn(0f, 1f - h)
            l = nl
            t = nt
            r = nl + w
            b = nt + h
        }
        VideoCropHandle.NONE -> return current
    }

    val updated = current.copy(leftNorm = l, topNorm = t, rightNorm = r, bottomNorm = b)
    val targetRatio = when (current.aspectPreset) {
        CropAspectRatioPreset.FREE -> null
        CropAspectRatioPreset.ORIGINAL -> videoAspect
        else -> current.aspectPreset.ratioWidthToHeight
    }
    return if (targetRatio != null && handle != VideoCropHandle.INSIDE_MOVE) {
        val normRatio = (targetRatio / videoAspect.coerceAtLeast(0.1f)).coerceIn(0.15f, 6f)
        val curW = updated.rightNorm - updated.leftNorm
        val desiredH = (curW / normRatio).coerceIn(minSpan, 1f)
        val cy = ((updated.topNorm + updated.bottomNorm) / 2f).coerceIn(desiredH / 2f, 1f - desiredH / 2f)
        updated.copy(
            topNorm = (cy - desiredH / 2f).coerceIn(0f, 0.90f),
            bottomNorm = (cy + desiredH / 2f).coerceIn(0.10f, 1f)
        )
    } else {
        updated
    }
}

private fun applyVideoAspectPreset(
    current: VideoCropSpec,
    preset: CropAspectRatioPreset,
    videoAspect: Float
): VideoCropSpec {
    if (preset == CropAspectRatioPreset.FREE) {
        return current.copy(aspectPreset = preset)
    }
    if (preset == CropAspectRatioPreset.ORIGINAL) {
        return VideoCropSpec(aspectPreset = preset)
    }
    val ratio = preset.ratioWidthToHeight ?: return current.copy(aspectPreset = preset)
    val normRatio = (ratio / videoAspect.coerceAtLeast(0.1f)).coerceIn(0.15f, 6f)
    val bw: Float
    val bh: Float
    if (normRatio >= 1f) {
        bw = 0.88f
        bh = (bw / normRatio).coerceIn(0.12f, 0.88f)
    } else {
        bh = 0.88f
        bw = (bh * normRatio).coerceIn(0.12f, 0.88f)
    }
    return current.copy(
        aspectPreset = preset,
        leftNorm = (0.5f - bw / 2f).coerceIn(0f, 0.9f),
        topNorm = (0.5f - bh / 2f).coerceIn(0f, 0.9f),
        rightNorm = (0.5f + bw / 2f).coerceIn(0.1f, 1f),
        bottomNorm = (0.5f + bh / 2f).coerceIn(0.1f, 1f)
    )
}
