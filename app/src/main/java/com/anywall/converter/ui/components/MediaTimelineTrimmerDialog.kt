package com.anywall.converter.ui.components

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.os.Build
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.anywall.converter.R
import com.anywall.converter.core.ScopedStorageManager
import com.anywall.converter.core.UriFileResolver
import com.anywall.converter.data.engines.PcmWavMp3AudioHelper
import com.anywall.converter.domain.model.InputFileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

private enum class TimelineDragTarget {
    NONE,
    START_HANDLE,
    END_HANDLE,
    PLAYHEAD
}

/**
 * Recortador Visual de Línea de Tiempo para Video y Audio (`MediaTimelineTrimmerDialog` - v0.6):
 * - Reproductor de video real por hardware (`TextureView` + `MediaPlayer.setSurface`) con ajuste
 *   de aspecto exacto dentro del contenedor (`BoxWithConstraints`, sin desbordar en videos 16:9).
 * - Protección de estado `isPlayerPrepared` para evitar errores `-38` en `MediaPlayer` durante `prepareAsync()`.
 * - Dos tiradores arrastrables de Inicio y Fin con seguimiento continuo (`awaitEachGesture` + `rememberUpdatedState`)
 *   y `RangeSlider` sincronizado.
 */
@Composable
fun MediaTimelineTrimmerDialog(
    mediaFile: InputFileItem,
    initialStartMs: Long,
    initialEndMs: Long,
    onApplyTrim: (startMs: Long, endMs: Long) -> Unit,
    onDismiss: () -> Unit,
    initialMuteAudio: Boolean = false,
    onToggleMuteAudio: ((Boolean) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isVideo = mediaFile.format.isVideo

    var totalDurationMs by remember { mutableLongStateOf(0L) }
    var startMs by remember { mutableLongStateOf(initialStartMs.coerceAtLeast(0L)) }
    var endMs by remember { mutableLongStateOf(initialEndMs.coerceAtLeast(0L)) }
    var playheadMs by remember { mutableLongStateOf(initialStartMs.coerceAtLeast(0L)) }
    var videoAspectRatio by remember { mutableFloatStateOf(16f / 9f) }
    var isMuted by remember { mutableStateOf(initialMuteAudio) }

    var isLoading by remember { mutableStateOf(true) }
    val frameStrip = remember { mutableStateListOf<Bitmap>() }
    var waveformPeaks by remember { mutableStateOf(FloatArray(0)) }
    var statusBanner by remember { mutableStateOf<String?>(null) }

    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlayerPrepared by remember { mutableStateOf(false) }
    var videoSurface by remember { mutableStateOf<Surface?>(null) }
    var textureViewRef by remember { mutableStateOf<TextureView?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var lastSeekWallClockMs by remember { mutableLongStateOf(0L) }

    fun seekPlayerSmooth(targetMs: Long, forceExact: Boolean = false) {
        if (!isPlayerPrepared) return
        val mp = mediaPlayer ?: return
        val now = System.currentTimeMillis()
        if (!forceExact && now - lastSeekWallClockMs < 35L) return
        lastSeekWallClockMs = now
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val mode = if (forceExact) MediaPlayer.SEEK_CLOSEST else MediaPlayer.SEEK_PREVIOUS_SYNC
                mp.seekTo(targetMs.coerceAtLeast(0L), mode)
            } else {
                mp.seekTo(targetMs.coerceAtLeast(0L).toInt())
            }
        }
    }

    // Liberar MediaPlayer y Surface al cerrar el diálogo
    DisposableEffect(mediaFile.uri) {
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

    // Cargar metadatos, tira de fotogramas (video) o forma de onda (audio) e inicializar MediaPlayer
    LaunchedEffect(mediaFile.uri) {
        isLoading = true
        isPlayerPrepared = false
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, mediaFile.uri)
                val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val dur = durStr?.toLongOrNull()?.coerceAtLeast(500L) ?: 15_000L
                val vWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toFloatOrNull() ?: 16f
                val vHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toFloatOrNull() ?: 9f
                val vRot = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                val aspect = if (vRot == 90 || vRot == 270) {
                    (vHeight / vWidth.coerceAtLeast(1f)).coerceIn(0.35f, 2.8f)
                } else {
                    (vWidth / vHeight.coerceAtLeast(1f)).coerceIn(0.35f, 2.8f)
                }

                val initStart = initialStartMs.coerceIn(0L, (dur - 250L).coerceAtLeast(0L))
                val initEnd = if (initialEndMs in (initStart + 100L)..dur) initialEndMs else dur

                withContext(Dispatchers.Main) {
                    totalDurationMs = dur
                    videoAspectRatio = aspect
                    startMs = initStart
                    endMs = initEnd
                    playheadMs = initStart
                }

                if (isVideo) {
                    val stripCount = 8
                    val extracted = mutableListOf<Bitmap>()
                    for (i in 0 until stripCount) {
                        val tMs = ((dur * (i + 0.5)) / stripCount).roundToLong()
                        val rawFrame = retriever.getFrameAtTime(
                            tMs * 1000L,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                        )
                        if (rawFrame != null) {
                            val scaled = Bitmap.createScaledBitmap(rawFrame, 120, 80, true)
                            if (scaled !== rawFrame) rawFrame.recycle()
                            extracted.add(scaled)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        frameStrip.clear()
                        frameStrip.addAll(extracted)
                    }
                } else {
                    val resolver = UriFileResolver(context)
                    var sourceTemp: File? = null
                    val pcmTemp = File(context.cacheDir, "trim_wave_${System.nanoTime()}.pcm")
                    try {
                        sourceTemp = resolver.copyUriToTempFile(
                            uri = mediaFile.uri,
                            prefix = "trim_src_",
                            extension = mediaFile.format.extension.ifBlank { "tmp" }
                        )
                        PcmWavMp3AudioHelper.decodeMediaFileToPcm16Le(
                            inputFile = sourceTemp,
                            tempPcmOutputFile = pcmTemp,
                            onProgress = { }
                        )
                        val peaks = PcmWavMp3AudioHelper
                            .extractWaveformPeaksFromPcmFile(pcmFile = pcmTemp, bucketCount = 64)
                            .toFloatArray()
                        withContext(Dispatchers.Main) {
                            waveformPeaks = peaks
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            statusBanner = "No se pudo generar la forma de onda: ${e.localizedMessage ?: e.javaClass.simpleName}"
                        }
                    } finally {
                        sourceTemp?.delete()
                        pcmTemp.delete()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusBanner = "No se pudieron leer los metadatos: ${e.localizedMessage ?: e.javaClass.simpleName}"
                }
            } finally {
                runCatching { retriever.release() }
            }
        }

        // Inicializar MediaPlayer en el hilo principal y enlazar Surface de forma segura
        runCatching {
            val mp = MediaPlayer()
            mp.setDataSource(context, mediaFile.uri)
            videoSurface?.let { mp.setSurface(it) }
            mp.setOnPreparedListener { preparedMp ->
                isPlayerPrepared = true
                val vol = if (isMuted) 0f else 1f
                runCatching { preparedMp.setVolume(vol, vol) }
                videoSurface?.let { runCatching { preparedMp.setSurface(it) } }
                if (totalDurationMs <= 0L && preparedMp.duration > 0) {
                    totalDurationMs = preparedMp.duration.toLong()
                    if (endMs <= startMs) endMs = totalDurationMs
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    preparedMp.seekTo(startMs, MediaPlayer.SEEK_CLOSEST)
                } else {
                    preparedMp.seekTo(startMs.toInt())
                }
            }
            mp.setOnCompletionListener {
                isPlaying = false
                playheadMs = startMs
                seekPlayerSmooth(startMs, forceExact = true)
            }
            mp.prepareAsync()
            mediaPlayer = mp
        }
        isLoading = false
    }

    // Si la Surface de TextureView se crea o el MediaPlayer termina de prepararse, vincular y mostrar fotograma
    LaunchedEffect(videoSurface, mediaPlayer, isPlayerPrepared) {
        val mp = mediaPlayer
        val surf = videoSurface
        if (mp != null && surf != null) {
            runCatching {
                mp.setSurface(surf)
                if (isPlayerPrepared) {
                    seekPlayerSmooth(playheadMs, forceExact = true)
                }
            }
        }
    }

    // Bucle de sincronización de reproducción entre startMs y endMs
    LaunchedEffect(isPlaying, startMs, endMs) {
        while (isPlaying) {
            val mp = mediaPlayer
            if (mp != null && isPlayerPrepared && runCatching { mp.isPlaying }.getOrDefault(false)) {
                val cur = runCatching { mp.currentPosition.toLong() }.getOrDefault(playheadMs)
                playheadMs = cur.coerceIn(startMs, endMs)
                if (cur >= endMs) {
                    runCatching { mp.pause() }
                    seekPlayerSmooth(startMs, forceExact = true)
                    playheadMs = startMs
                    isPlaying = false
                }
            } else {
                isPlaying = false
            }
            delay(35L)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .clip(RoundedCornerShape(22.dp)),
            color = Color(0xFF0E131A),
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Encabezado
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
                            imageVector = if (isVideo) Icons.Default.ContentCut else Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = Color(0xFF6366F1)
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.media_trimmer_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = mediaFile.name,
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

                // Vista previa visual real con ajuste exacto al contenedor (sin desbordar en 16:9 ni 9:16)
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isVideo) 220.dp else 96.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF07090D))
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val safeAspect = videoAspectRatio.coerceIn(0.35f, 2.8f)
                    val fitW = if (maxWidth / safeAspect <= maxHeight) maxWidth else maxHeight * safeAspect
                    val fitH = if (maxWidth / safeAspect <= maxHeight) maxWidth / safeAspect else maxHeight

                    if (isVideo) {
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
                                            runCatching {
                                                mediaPlayer?.setSurface(newSurf)
                                                if (isPlayerPrepared) {
                                                    seekPlayerSmooth(playheadMs, forceExact = true)
                                                }
                                            }
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
                                    textureViewRef = this
                                }
                            },
                            modifier = Modifier.size(fitW, fitH)
                        )
                    } else if (isLoading) {
                        CircularProgressIndicator(color = Color(0xFF6366F1))
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = "Duración seleccionada: ${formatDurationMsPrecise(endMs - startMs)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFFE2E8F0)
                            )
                        }
                    }
                }

                // Tiempos de Inicio / Cabezal / Fin con precisión de milisegundos
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TimeStampBadge(
                        label = "INICIO",
                        timeMs = startMs,
                        accentColor = Color(0xFF10B981)
                    )
                    TimeStampBadge(
                        label = "ACTUAL",
                        timeMs = playheadMs,
                        accentColor = Color(0xFFF59E0B)
                    )
                    TimeStampBadge(
                        label = "FIN",
                        timeMs = endMs,
                        accentColor = Color(0xFF6366F1)
                    )
                }

                // LÍNEA DE TIEMPO TÁCTIL CON SEGUIMIENTO CONTINUO (awaitEachGesture + rememberUpdatedState)
                val safeTotal = totalDurationMs.coerceAtLeast(1000L)
                val currentStartMs by rememberUpdatedState(startMs)
                val currentEndMs by rememberUpdatedState(endMs)
                val currentPlayheadMs by rememberUpdatedState(playheadMs)
                val currentSafeTotal by rememberUpdatedState(safeTotal)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(78.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF141A23))
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                down.consume()
                                val padPx = 16.dp.toPx()
                                val trackW = (size.width.toFloat() - 2f * padPx).coerceAtLeast(1f)
                                val tot = currentSafeTotal.coerceAtLeast(1000L)

                                val startX = padPx + (currentStartMs.toFloat() / tot) * trackW
                                val endX = padPx + (currentEndMs.toFloat() / tot) * trackW
                                val playX = padPx + (currentPlayheadMs.toFloat() / tot) * trackW

                                val distStart = abs(down.position.x - startX)
                                val distEnd = abs(down.position.x - endX)
                                val distPlay = abs(down.position.x - playX)
                                val hitRadiusPx = 38.dp.toPx()

                                val activeTarget = when {
                                    distStart <= hitRadiusPx && distStart <= distEnd -> TimelineDragTarget.START_HANDLE
                                    distEnd <= hitRadiusPx -> TimelineDragTarget.END_HANDLE
                                    distPlay <= 28.dp.toPx() -> TimelineDragTarget.PLAYHEAD
                                    down.position.x < startX -> TimelineDragTarget.START_HANDLE
                                    down.position.x > endX -> TimelineDragTarget.END_HANDLE
                                    else -> {
                                        if (distStart < distEnd && distStart <= 56.dp.toPx()) {
                                            TimelineDragTarget.START_HANDLE
                                        } else if (distEnd <= 56.dp.toPx()) {
                                            TimelineDragTarget.END_HANDLE
                                        } else {
                                            TimelineDragTarget.PLAYHEAD
                                        }
                                    }
                                }

                                if (isPlaying) {
                                    runCatching { mediaPlayer?.pause() }
                                    isPlaying = false
                                }

                                fun updateFromTouchX(touchX: Float, forceExactSeek: Boolean) {
                                    val ratio = ((touchX - padPx) / trackW).coerceIn(0f, 1f)
                                    val targetMs = (ratio * currentSafeTotal).roundToLong()
                                    val minClipGap = 250L
                                    when (activeTarget) {
                                        TimelineDragTarget.START_HANDLE -> {
                                            val nextStart = targetMs.coerceIn(0L, (currentEndMs - minClipGap).coerceAtLeast(0L))
                                            startMs = nextStart
                                            playheadMs = nextStart
                                            seekPlayerSmooth(nextStart, forceExact = forceExactSeek)
                                        }
                                        TimelineDragTarget.END_HANDLE -> {
                                            val nextEnd = targetMs.coerceIn(
                                                (currentStartMs + minClipGap).coerceAtMost(currentSafeTotal),
                                                currentSafeTotal
                                            )
                                            endMs = nextEnd
                                            playheadMs = nextEnd
                                            seekPlayerSmooth(nextEnd, forceExact = forceExactSeek)
                                        }
                                        TimelineDragTarget.PLAYHEAD -> {
                                            val nextPlay = targetMs.coerceIn(currentStartMs, currentEndMs)
                                            playheadMs = nextPlay
                                            seekPlayerSmooth(nextPlay, forceExact = forceExactSeek)
                                        }
                                        TimelineDragTarget.NONE -> Unit
                                    }
                                }

                                updateFromTouchX(down.position.x, forceExactSeek = false)

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                        ?: event.changes.firstOrNull()
                                        ?: break
                                    if (!change.pressed) {
                                        updateFromTouchX(change.position.x, forceExactSeek = true)
                                        break
                                    }
                                    if (change.positionChange() != Offset.Zero) {
                                        change.consume()
                                        updateFromTouchX(change.position.x, forceExactSeek = false)
                                    }
                                }
                            }
                        }
                ) {
                    // Fondo de fotogramas reales (Video) con margen horizontal para que los tiradores nunca se corten
                    if (isVideo && frameStrip.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp)
                                .clip(RoundedCornerShape(8.dp))
                        ) {
                            frameStrip.forEach { thumb ->
                                Image(
                                    bitmap = thumb.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                )
                            }
                        }
                    }

                    // Overlay de Forma de Onda (Audio) + Máscaras + Tiradores de Inicio y Fin
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val padPx = 16.dp.toPx()
                        val trackW = (size.width - 2f * padPx).coerceAtLeast(1f)
                        val h = size.height
                        val startX = (padPx + (startMs.toFloat() / safeTotal) * trackW).coerceIn(padPx, padPx + trackW)
                        val endX = (padPx + (endMs.toFloat() / safeTotal) * trackW).coerceIn(startX, padPx + trackW)
                        val playX = (padPx + (playheadMs.toFloat() / safeTotal) * trackW).coerceIn(startX, endX)

                        // Si es audio, dibujar barras de la forma de onda PCM
                        if (!isVideo && waveformPeaks.isNotEmpty()) {
                            val barCount = waveformPeaks.size
                            val slotW = trackW / barCount
                            val barW = (slotW * 0.62f).coerceAtLeast(2f)
                            for (i in 0 until barCount) {
                                val bx = padPx + i * slotW + (slotW - barW) / 2f
                                val amp = waveformPeaks[i].coerceIn(0.08f, 1f)
                                val bh = (amp * h * 0.80f).coerceAtLeast(4f)
                                val by = (h - bh) / 2f
                                val inSelectedRange = bx in startX..endX
                                drawRoundRect(
                                    color = if (inSelectedRange) Color(0xFF38BDF8) else Color(0xFF334155),
                                    topLeft = Offset(bx, by),
                                    size = Size(barW, bh),
                                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                                )
                            }
                        }

                        // Oscurecer zonas descartadas izquierda y derecha
                        drawRect(
                            color = Color(0xB8000000),
                            topLeft = Offset(0f, 0f),
                            size = Size(startX, h)
                        )
                        drawRect(
                            color = Color(0xB8000000),
                            topLeft = Offset(endX, 0f),
                            size = Size((size.width - endX).coerceAtLeast(0f), h)
                        )

                        // Marco del segmento activo
                        drawRect(
                            color = Color(0xFF6366F1),
                            topLeft = Offset(startX, 2.dp.toPx()),
                            size = Size((endX - startX).coerceAtLeast(2f), h - 4.dp.toPx()),
                            style = Stroke(width = 2.5.dp.toPx())
                        )

                        // Tirador izquierdo (INICIO) con agarre ancho
                        val handleW = 16.dp.toPx()
                        drawRoundRect(
                            color = Color(0xFF10B981),
                            topLeft = Offset(startX - handleW / 2f, 0f),
                            size = Size(handleW, h),
                            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                        )
                        drawLine(
                            color = Color.White,
                            start = Offset(startX, h * 0.28f),
                            end = Offset(startX, h * 0.72f),
                            strokeWidth = 2.dp.toPx()
                        )

                        // Tirador derecho (FIN) con agarre ancho
                        drawRoundRect(
                            color = Color(0xFF6366F1),
                            topLeft = Offset(endX - handleW / 2f, 0f),
                            size = Size(handleW, h),
                            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                        )
                        drawLine(
                            color = Color.White,
                            start = Offset(endX, h * 0.28f),
                            end = Offset(endX, h * 0.72f),
                            strokeWidth = 2.dp.toPx()
                        )

                        // Cabezal de reproducción (Playhead)
                        drawLine(
                            color = Color(0xFFF59E0B),
                            start = Offset(playX, 0f),
                            end = Offset(playX, h),
                            strokeWidth = 2.5.dp.toPx()
                        )
                        drawCircle(
                            color = Color(0xFFF59E0B),
                            radius = 6.dp.toPx(),
                            center = Offset(playX, 8.dp.toPx())
                        )
                    }
                }

                // Control deslizante doble adicional (RangeSlider) sincronizado para ajuste rápido con 2 pulgares
                RangeSlider(
                    value = (startMs.toFloat() / safeTotal).coerceIn(0f, 1f)..(endMs.toFloat() / safeTotal).coerceIn(0f, 1f),
                    onValueChange = { range ->
                        if (isPlaying) {
                            runCatching { mediaPlayer?.pause() }
                            isPlaying = false
                        }
                        val minGap = 250L
                        val newStart = (range.start * safeTotal).roundToLong().coerceIn(0L, (safeTotal - minGap).coerceAtLeast(0L))
                        val newEnd = (range.endInclusive * safeTotal).roundToLong().coerceIn((newStart + minGap).coerceAtMost(safeTotal), safeTotal)
                        val startChanged = abs(newStart - startMs) >= abs(newEnd - endMs)
                        startMs = newStart
                        endMs = newEnd
                        val seekTarget = if (startChanged) newStart else newEnd
                        playheadMs = seekTarget
                        seekPlayerSmooth(seekTarget, forceExact = false)
                    },
                    onValueChangeFinished = {
                        seekPlayerSmooth(playheadMs, forceExact = true)
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF10B981),
                        activeTrackColor = Color(0xFF6366F1),
                        inactiveTrackColor = Color(0xFF1E293B)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                )

                // Fila 1 de Controles: Reproducción, Restablecer Todo y Silenciar Audio (Mute)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = {
                            val mp = mediaPlayer
                            if (mp != null && isPlayerPrepared) {
                                if (isPlaying) {
                                    runCatching { mp.pause() }
                                    isPlaying = false
                                } else {
                                    if (playheadMs < startMs || playheadMs >= endMs - 100L) {
                                        playheadMs = startMs
                                    }
                                    seekPlayerSmooth(playheadMs, forceExact = true)
                                    runCatching { mp.start() }
                                    isPlaying = true
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isPlaying) "Pausar" else "Probar tramo",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            startMs = 0L
                            endMs = totalDurationMs
                            playheadMs = 0L
                            runCatching {
                                mediaPlayer?.pause()
                                seekPlayerSmooth(0L, forceExact = true)
                            }
                            isPlaying = false
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Todo", color = Color.White, maxLines = 1)
                    }

                    if (isVideo && onToggleMuteAudio != null) {
                        FilterChip(
                            selected = isMuted,
                            onClick = {
                                val next = !isMuted
                                isMuted = next
                                val vol = if (next) 0f else 1f
                                if (isPlayerPrepared) {
                                    runCatching { mediaPlayer?.setVolume(vol, vol) }
                                }
                                onToggleMuteAudio(next)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = {
                                Text(if (isMuted) "Sin audio" else "Audio ON", maxLines = 1)
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                labelColor = Color.White,
                                iconColor = Color.White,
                                selectedContainerColor = Color(0xFFEF4444),
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White
                            )
                        )
                    }
                }

                // Fila 2 (solo Video): Botón de ancho completo para "Capturar fotograma exacto"
                if (isVideo) {
                    FilledTonalButton(
                        onClick = {
                            val targetTimeMs = playheadMs
                            val liveTextureBmp = runCatching { textureViewRef?.bitmap }.getOrNull()
                            scope.launch {
                                val savedMsg = withContext(Dispatchers.IO) {
                                    val retriever = MediaMetadataRetriever()
                                    try {
                                        val hiResFrame = runCatching {
                                            retriever.setDataSource(context, mediaFile.uri)
                                            retriever.getFrameAtTime(
                                                targetTimeMs * 1000L,
                                                MediaMetadataRetriever.OPTION_CLOSEST
                                            )
                                        }.getOrNull() ?: liveTextureBmp
                                        if (hiResFrame != null) {
                                            val bos = ByteArrayOutputStream()
                                            hiResFrame.compress(Bitmap.CompressFormat.PNG, 100, bos)
                                            val pngBytes = bos.toByteArray()
                                            val baseName = mediaFile.nameWithoutExtension.ifBlank { "video" }
                                            val fileName = "${baseName}_frame_${targetTimeMs}ms.png"
                                            val saved = ScopedStorageManager(context).saveStreamToDownloads(
                                                inputStream = ByteArrayInputStream(pngBytes),
                                                desiredDisplayName = fileName,
                                                mimeType = "image/png",
                                                knownSizeBytes = pngBytes.size.toLong()
                                            )
                                            "Fotograma guardado en Downloads/AnyWall/${saved.displayName}"
                                        } else {
                                            "No fue posible extraer el fotograma actual."
                                        }
                                    } catch (e: Exception) {
                                        "Error al guardar fotograma: ${e.message}"
                                    } finally {
                                        runCatching { retriever.release() }
                                    }
                                }
                                statusBanner = savedMsg
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.btn_capture_exact_frame),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (statusBanner != null) {
                    Surface(
                        color = Color(0xFF064E3B),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = statusBanner.orEmpty(),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFA7F3D0),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                // Acciones finales
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text(stringResource(R.string.btn_cancel), color = Color.White)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            runCatching { mediaPlayer?.pause() }
                            val finalEnd = if (endMs >= totalDurationMs - 40L && startMs == 0L) 0L else endMs
                            onApplyTrim(startMs, finalEnd)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6366F1),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.btn_apply_trim))
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeStampBadge(
    label: String,
    timeMs: Long,
    accentColor: Color
) {
    Surface(
        color = Color(0xFF1A2230),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = accentColor,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = formatDurationMsPrecise(timeMs),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = Color.White
            )
        }
    }
}

private fun formatDurationMsPrecise(ms: Long): String {
    val safe = ms.coerceAtLeast(0L)
    val totalSec = safe / 1000L
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    val millis = safe % 1000L
    return String.format(Locale.US, "%02d:%02d.%03d", mins, secs, millis)
}
