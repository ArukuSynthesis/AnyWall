package com.anywall.converter.ui.components

import android.graphics.SurfaceTexture
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.os.Build
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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
 * Recorte Táctil de Encuadre de Video (`VideoCropDialog` - v0.6):
 * - Reproduce el video real por hardware (`TextureView` + `MediaPlayer.setSurface`) directamente
 *   detrás de la rejilla interactiva de 8 tiradores táctiles, ajustado con `BoxWithConstraints`
 *   para que el 100% del fotograma y los 8 tiradores entren siempre en pantalla (tanto en 16:9 como en 9:16).
 * - Arrastre continuo a 60 FPS (`awaitEachGesture` + `rememberUpdatedState`) sin cancelaciones.
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
    var videoAspect by remember { mutableFloatStateOf(16f / 9f) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlayerPrepared by remember { mutableStateOf(false) }
    var videoSurface by remember { mutableStateOf<Surface?>(null) }
    var isPlaying by remember { mutableStateOf(false) }

    DisposableEffect(videoFile.uri) {
        onDispose {
            isPlayerPrepared = false
            runCatching {
                mediaPlayer?.setSurface(null)
                mediaPlayer?.stop()
                mediaPlayer?.release()
            }
            runCatching { videoSurface?.release() }
            mediaPlayer = null
            videoSurface = null
        }
    }

    LaunchedEffect(videoFile.uri, sampleTimeMs) {
        isPlayerPrepared = false
        val aspect = withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, videoFile.uri)
                val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toFloatOrNull() ?: 1280f
                val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toFloatOrNull() ?: 720f
                val rot = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                if (rot == 90 || rot == 270) {
                    (h / w.coerceAtLeast(1f)).coerceIn(0.35f, 2.8f)
                } else {
                    (w / h.coerceAtLeast(1f)).coerceIn(0.35f, 2.8f)
                }
            } catch (_: Exception) {
                16f / 9f
            } finally {
                runCatching { retriever.release() }
            }
        }
        videoAspect = aspect

        runCatching {
            val mp = MediaPlayer()
            mp.setDataSource(context, videoFile.uri)
            mp.isLooping = true
            videoSurface?.let { mp.setSurface(it) }
            mp.setOnPreparedListener { prepared ->
                isPlayerPrepared = true
                videoSurface?.let { runCatching { prepared.setSurface(it) } }
                val startSeek = sampleTimeMs.coerceAtLeast(0L)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    prepared.seekTo(startSeek, MediaPlayer.SEEK_CLOSEST)
                } else {
                    prepared.seekTo(startSeek.toInt())
                }
                prepared.start()
                isPlaying = true
            }
            mp.prepareAsync()
            mediaPlayer = mp
        }
    }

    LaunchedEffect(videoSurface, mediaPlayer, isPlayerPrepared) {
        val mp = mediaPlayer
        val surf = videoSurface
        if (mp != null && surf != null) {
            runCatching { mp.setSurface(surf) }
        }
    }

    val currentCropSpec by rememberUpdatedState(cropSpec)
    val currentVideoAspect by rememberUpdatedState(videoAspect)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .clip(RoundedCornerShape(22.dp)),
            color = Color(0xFF0B0D10),
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
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

                // Contenedor con BoxWithConstraints para que el video 16:9 o 9:16 y sus 8 tiradores entren siempre al 100%
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(290.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF07090D))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val safeAspect = videoAspect.coerceIn(0.35f, 2.8f)
                    val fitW = if (maxWidth / safeAspect <= maxHeight) maxWidth else maxHeight * safeAspect
                    val fitH = if (maxWidth / safeAspect <= maxHeight) maxWidth / safeAspect else maxHeight

                    Box(
                        modifier = Modifier.size(fitW, fitH)
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                TextureView(ctx).apply {
                                    isOpaque = false
                                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                        override fun onSurfaceTextureAvailable(
                                            surface: SurfaceTexture,
                                            width: Int,
                                            height: Int
                                        ) {
                                            val newSurf = Surface(surface)
                                            videoSurface?.release()
                                            videoSurface = newSurf
                                            runCatching { mediaPlayer?.setSurface(newSurf) }
                                        }

                                        override fun onSurfaceTextureSizeChanged(
                                            surface: SurfaceTexture,
                                            width: Int,
                                            height: Int
                                        ) = Unit

                                        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                                            runCatching { mediaPlayer?.setSurface(null) }
                                            runCatching { videoSurface?.release() }
                                            videoSurface = null
                                            return true
                                        }

                                        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Overlay Canvas de Recorte con 8 tiradores táctiles sobre el área exacta del video
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        val w = size.width.toFloat().coerceAtLeast(1f)
                                        val h = size.height.toFloat().coerceAtLeast(1f)
                                        val hitR = 34.dp.toPx().toDouble()
                                        val edgeTol = 26.dp.toPx()

                                        val handle = hitTestVideoCropHandleOnBox(
                                            touch = down.position,
                                            boxW = w,
                                            boxH = h,
                                            spec = currentCropSpec,
                                            hitRadiusPx = hitR,
                                            edgeTolerancePx = edgeTol
                                        )
                                        if (handle == VideoCropHandle.NONE) return@awaitEachGesture
                                        down.consume()

                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id }
                                                ?: event.changes.firstOrNull()
                                                ?: break
                                            if (!change.pressed) break
                                            val delta = change.positionChange()
                                            if (delta != Offset.Zero) {
                                                change.consume()
                                                val dx = delta.x / w
                                                val dy = delta.y / h
                                                cropSpec = dragVideoCropSpec(
                                                    current = currentCropSpec,
                                                    handle = handle,
                                                    dx = dx,
                                                    dy = dy,
                                                    videoAspect = currentVideoAspect
                                                )
                                            }
                                        }
                                    }
                                }
                        ) {
                            val w = size.width
                            val h = size.height
                            val cl = cropSpec.leftNorm * w
                            val ct = cropSpec.topNorm * h
                            val cr = cropSpec.rightNorm * w
                            val cb = cropSpec.bottomNorm * h
                            val cw = (cr - cl).coerceAtLeast(1f)
                            val ch = (cb - ct).coerceAtLeast(1f)

                            // Máscara oscura exterior
                            val mask = Color(0xAA000000)
                            drawRect(mask, Offset(0f, 0f), Size(w, ct))
                            drawRect(mask, Offset(0f, cb), Size(w, (h - cb).coerceAtLeast(0f)))
                            drawRect(mask, Offset(0f, ct), Size(cl, ch))
                            drawRect(mask, Offset(cr, ct), Size((w - cr).coerceAtLeast(0f), ch))

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

                            // 8 Tiradores táctiles grandes y visibles
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
                                val r = if (idx < 4) 8.5.dp.toPx() else 6.5.dp.toPx()
                                drawCircle(Color(0xFF6366F1), r + 2.5.dp.toPx(), pt)
                                drawCircle(Color.White, r, pt)
                            }
                        }
                    }
                }

                // Chips de proporción de aspecto
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

                // Acciones + Botón Reproducir/Pausar video
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilledTonalButton(
                            onClick = {
                                val mp = mediaPlayer ?: return@FilledTonalButton
                                if (isPlaying) {
                                    runCatching { mp.pause() }
                                    isPlaying = false
                                } else {
                                    runCatching { mp.start() }
                                    isPlaying = true
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isPlaying) "Pausar" else "Video")
                        }

                        OutlinedButton(
                            onClick = { cropSpec = VideoCropSpec() }
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Original", color = Color.White)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onDismiss) {
                            Text(stringResource(R.string.btn_cancel), color = Color.White)
                        }
                        Button(
                            onClick = {
                                runCatching { mediaPlayer?.pause() }
                                onApplyCrop(cropSpec)
                            },
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

private fun hitTestVideoCropHandleOnBox(
    touch: Offset,
    boxW: Float,
    boxH: Float,
    spec: VideoCropSpec,
    hitRadiusPx: Double,
    edgeTolerancePx: Float
): VideoCropHandle {
    val cl = spec.leftNorm * boxW
    val ct = spec.topNorm * boxH
    val cr = spec.rightNorm * boxW
    val cb = spec.bottomNorm * boxH
    val mx = (cl + cr) / 2f
    val my = (ct + cb) / 2f
    fun near(pt: Offset) = hypot((touch.x - pt.x).toDouble(), (touch.y - pt.y).toDouble()) <= hitRadiusPx

    return when {
        near(Offset(cl, ct)) -> VideoCropHandle.TOP_LEFT
        near(Offset(cr, ct)) -> VideoCropHandle.TOP_RIGHT
        near(Offset(cl, cb)) -> VideoCropHandle.BOTTOM_LEFT
        near(Offset(cr, cb)) -> VideoCropHandle.BOTTOM_RIGHT
        near(Offset(mx, ct)) || (touch.x in cl..cr && abs(touch.y - ct) <= edgeTolerancePx) -> VideoCropHandle.TOP_EDGE
        near(Offset(mx, cb)) || (touch.x in cl..cr && abs(touch.y - cb) <= edgeTolerancePx) -> VideoCropHandle.BOTTOM_EDGE
        near(Offset(cl, my)) || (touch.y in ct..cb && abs(touch.x - cl) <= edgeTolerancePx) -> VideoCropHandle.LEFT_EDGE
        near(Offset(cr, my)) || (touch.y in ct..cb && abs(touch.x - cr) <= edgeTolerancePx) -> VideoCropHandle.RIGHT_EDGE
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

    val isSingleEdgeHandle = handle in setOf(
        VideoCropHandle.TOP_EDGE,
        VideoCropHandle.BOTTOM_EDGE,
        VideoCropHandle.LEFT_EDGE,
        VideoCropHandle.RIGHT_EDGE
    )

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

    // Al arrastrar un borde individual (Superior/Inferior/Izquierdo/Derecho), pasar a modo Libre
    // para que el borde siga 1:1 al dedo sin bloquearse por la proporción anterior.
    if (isSingleEdgeHandle) {
        return current.copy(
            leftNorm = l,
            topNorm = t,
            rightNorm = r,
            bottomNorm = b,
            aspectPreset = CropAspectRatioPreset.FREE
        )
    }

    val updated = current.copy(leftNorm = l, topNorm = t, rightNorm = r, bottomNorm = b)
    val targetRatio = when (current.aspectPreset) {
        CropAspectRatioPreset.FREE -> null
        CropAspectRatioPreset.ORIGINAL -> videoAspect
        else -> current.aspectPreset.ratioWidthToHeight
    }

    if (targetRatio != null && handle != VideoCropHandle.INSIDE_MOVE) {
        val normRatio = (targetRatio / videoAspect.coerceAtLeast(0.1f)).coerceIn(0.15f, 6f)
        val anchorRight = handle == VideoCropHandle.TOP_LEFT || handle == VideoCropHandle.BOTTOM_LEFT
        val anchorBottom = handle == VideoCropHandle.TOP_LEFT || handle == VideoCropHandle.TOP_RIGHT

        val maxAllowedW = if (anchorRight) updated.rightNorm else (1f - updated.leftNorm)
        val maxAllowedH = if (anchorBottom) updated.bottomNorm else (1f - updated.topNorm)

        var newW = (updated.rightNorm - updated.leftNorm).coerceIn(minSpan, maxAllowedW)
        var newH = newW / normRatio
        if (newH > maxAllowedH) {
            newH = maxAllowedH.coerceAtLeast(minSpan)
            newW = (newH * normRatio).coerceIn(minSpan, maxAllowedW)
        } else if (newH < minSpan) {
            newH = minSpan
            newW = (newH * normRatio).coerceIn(minSpan, maxAllowedW)
        }

        val finalL = if (anchorRight) (updated.rightNorm - newW).coerceIn(0f, 1f - minSpan) else updated.leftNorm
        val finalR = if (anchorRight) updated.rightNorm else (updated.leftNorm + newW).coerceIn(minSpan, 1f)
        val finalT = if (anchorBottom) (updated.bottomNorm - newH).coerceIn(0f, 1f - minSpan) else updated.topNorm
        val finalB = if (anchorBottom) updated.bottomNorm else (updated.topNorm + newH).coerceIn(minSpan, 1f)

        return updated.copy(
            leftNorm = finalL,
            topNorm = finalT,
            rightNorm = finalR,
            bottomNorm = finalB
        )
    }
    return updated
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
