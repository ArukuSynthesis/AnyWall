package com.anywall.converter.ui.components

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

private enum class TimelineDragTarget {
    NONE,
    START_HANDLE,
    END_HANDLE,
    PLAYHEAD
}

/**
 * Recortador Visual de Línea de Tiempo para Video y Audio (`MediaTimelineTrimmerDialog` - v0.4):
 * - Tira de fotogramas reales para Video o Forma de Onda PCM para Audio.
 * - Dos tiradores arrastrables de Inicio y Fin con precisión de milisegundos.
 * - Reproductor integrado para escuchar/previsualizar el tramo exacto.
 * - Botón directo en Video: "Capturar fotograma exacto" guardado en `Downloads/AnyWall/`.
 */
@Composable
fun MediaTimelineTrimmerDialog(
    mediaFile: InputFileItem,
    initialStartMs: Long,
    initialEndMs: Long,
    onApplyTrim: (startMs: Long, endMs: Long) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isVideo = mediaFile.format.isVideo

    var totalDurationMs by remember { mutableLongStateOf(0L) }
    var startMs by remember { mutableLongStateOf(initialStartMs.coerceAtLeast(0L)) }
    var endMs by remember { mutableLongStateOf(initialEndMs.coerceAtLeast(0L)) }
    var playheadMs by remember { mutableLongStateOf(initialStartMs.coerceAtLeast(0L)) }

    var isLoading by remember { mutableStateOf(true) }
    val frameStrip = remember { mutableStateListOf<Bitmap>() }
    var currentVideoFrame by remember { mutableStateOf<Bitmap?>(null) }
    var waveformPeaks by remember { mutableStateOf<FloatArray>(FloatArray(0)) }
    var statusBanner by remember { mutableStateOf<String?>(null) }

    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var dragTarget by remember { mutableStateOf(TimelineDragTarget.NONE) }

    // Liberar MediaPlayer al cerrar el diálogo
    DisposableEffect(mediaFile.uri) {
        onDispose {
            runCatching {
                mediaPlayer?.stop()
                mediaPlayer?.release()
            }
            mediaPlayer = null
        }
    }

    // Cargar metadatos, tira de fotogramas (video) o forma de onda (audio)
    LaunchedEffect(mediaFile.uri) {
        isLoading = true
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, mediaFile.uri)
                val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val dur = durStr?.toLongOrNull()?.coerceAtLeast(500L) ?: 15_000L
                totalDurationMs = dur
                val initStart = initialStartMs.coerceIn(0L, (dur - 250L).coerceAtLeast(0L))
                val initEnd = if (initialEndMs in (initStart + 100L)..dur) initialEndMs else dur
                startMs = initStart
                endMs = initEnd
                playheadMs = initStart

                if (isVideo) {
                    // Extraer fotograma inicial de vista previa
                    val firstBmp = retriever.getFrameAtTime(
                        initStart * 1000L,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    )
                    if (firstBmp != null) {
                        currentVideoFrame = firstBmp
                    }
                    // Extraer 8 miniaturas a lo largo del video para la tira de tiempo
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
                    // Extraer forma de onda real: decodificar el audio a PCM 16-bit LE y calcular picos
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
                        waveformPeaks = PcmWavMp3AudioHelper
                            .extractWaveformPeaksFromPcmFile(pcmFile = pcmTemp, bucketCount = 64)
                            .toFloatArray()
                    } catch (e: Exception) {
                        statusBanner = "No se pudo generar la forma de onda: ${e.localizedMessage ?: e.javaClass.simpleName}"
                    } finally {
                        sourceTemp?.delete()
                        pcmTemp.delete()
                    }
                }
            } catch (e: Exception) {
                statusBanner = "No se pudieron leer los metadatos: ${e.localizedMessage ?: e.javaClass.simpleName}"
            } finally {
                runCatching { retriever.release() }
            }
        }

        // Inicializar MediaPlayer para preescucha del tramo
        runCatching {
            val mp = MediaPlayer()
            mp.setDataSource(context, mediaFile.uri)
            mp.prepare()
            mp.setOnCompletionListener {
                isPlaying = false
                playheadMs = startMs
            }
            mediaPlayer = mp
        }
        isLoading = false
    }

    // Actualizar fotograma de video cuando se mueve el cabezal
    LaunchedEffect(playheadMs, isVideo) {
        if (!isVideo || isLoading) return@LaunchedEffect
        val frame = withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, mediaFile.uri)
                retriever.getFrameAtTime(
                    playheadMs * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST
                )
            } catch (_: Exception) {
                null
            } finally {
                runCatching { retriever.release() }
            }
        }
        if (frame != null) {
            currentVideoFrame = frame
        }
    }

    // Bucle de sincronización de reproducción entre startMs y endMs
    LaunchedEffect(isPlaying, startMs, endMs) {
        while (isPlaying) {
            val mp = mediaPlayer
            if (mp != null && mp.isPlaying) {
                val cur = mp.currentPosition.toLong()
                playheadMs = cur.coerceIn(startMs, endMs)
                if (cur >= endMs) {
                    mp.pause()
                    mp.seekTo(startMs.toInt())
                    playheadMs = startMs
                    isPlaying = false
                }
            } else {
                isPlaying = false
            }
            delay(45L)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .clip(RoundedCornerShape(20.dp)),
            color = Color(0xFF0E131A),
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
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

                // Vista previa visual (Fotograma actual para Video o Analizador de Onda para Audio)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isVideo) 220.dp else 110.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF07090D))
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color(0xFF6366F1))
                    } else if (isVideo) {
                        val bmp = currentVideoFrame
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = mediaFile.name,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text("Vista previa de video", color = Color(0xFF94A3B8))
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(34.dp)
                            )
                            Text(
                                text = "Preescucha de tramo: ${formatDurationMsPrecise(endMs - startMs)}",
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

                // LÍNEA DE TIEMPO TÁCTIL CON TIRA DE FOTOGRAMAS / FORMA DE ONDA Y 2 TIRADORES ARRASTRABLES
                val safeTotal = totalDurationMs.coerceAtLeast(1000L)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(74.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF141A23))
                        .pointerInput(safeTotal) {
                            detectTapGestures { tapOffset ->
                                val ratio = (tapOffset.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                                val tappedMs = (ratio * safeTotal).roundToLong()
                                playheadMs = tappedMs.coerceIn(startMs, endMs)
                                runCatching { mediaPlayer?.seekTo(playheadMs.toInt()) }
                            }
                        }
                        .pointerInput(safeTotal) {
                            detectDragGestures(
                                onDragStart = { touch ->
                                    val w = size.width.toFloat().coerceAtLeast(1f)
                                    val startX = (startMs.toFloat() / safeTotal) * w
                                    val endX = (endMs.toFloat() / safeTotal) * w
                                    val playX = (playheadMs.toFloat() / safeTotal) * w

                                    val distStart = abs(touch.x - startX)
                                    val distEnd = abs(touch.x - endX)
                                    val distPlay = abs(touch.x - playX)

                                    dragTarget = when {
                                        distStart <= 36f && distStart <= distEnd -> TimelineDragTarget.START_HANDLE
                                        distEnd <= 36f -> TimelineDragTarget.END_HANDLE
                                        distPlay <= 32f -> TimelineDragTarget.PLAYHEAD
                                        touch.x < startX -> TimelineDragTarget.START_HANDLE
                                        touch.x > endX -> TimelineDragTarget.END_HANDLE
                                        else -> TimelineDragTarget.PLAYHEAD
                                    }
                                },
                                onDragEnd = { dragTarget = TimelineDragTarget.NONE },
                                onDragCancel = { dragTarget = TimelineDragTarget.NONE },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val w = size.width.toFloat().coerceAtLeast(1f)
                                    val ratio = (change.position.x / w).coerceIn(0f, 1f)
                                    val targetMs = (ratio * safeTotal).roundToLong()
                                    val minClipGap = 200L

                                    when (dragTarget) {
                                        TimelineDragTarget.START_HANDLE -> {
                                            startMs = targetMs.coerceIn(0L, (endMs - minClipGap).coerceAtLeast(0L))
                                            playheadMs = startMs
                                            runCatching { mediaPlayer?.seekTo(startMs.toInt()) }
                                        }
                                        TimelineDragTarget.END_HANDLE -> {
                                            endMs = targetMs.coerceIn((startMs + minClipGap).coerceAtMost(safeTotal), safeTotal)
                                            playheadMs = endMs
                                            runCatching { mediaPlayer?.seekTo(endMs.toInt()) }
                                        }
                                        TimelineDragTarget.PLAYHEAD -> {
                                            playheadMs = targetMs.coerceIn(startMs, endMs)
                                            runCatching { mediaPlayer?.seekTo(playheadMs.toInt()) }
                                        }
                                        TimelineDragTarget.NONE -> Unit
                                    }
                                }
                            )
                        }
                ) {
                    // Fondo de fotogramas reales (Video)
                    if (isVideo && frameStrip.isNotEmpty()) {
                        Row(modifier = Modifier.fillMaxSize()) {
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
                        val w = size.width
                        val h = size.height
                        val startX = ((startMs.toFloat() / safeTotal) * w).coerceIn(0f, w)
                        val endX = ((endMs.toFloat() / safeTotal) * w).coerceIn(startX, w)
                        val playX = ((playheadMs.toFloat() / safeTotal) * w).coerceIn(startX, endX)

                        // Si es audio, dibujar barras de la forma de onda PCM
                        if (!isVideo && waveformPeaks.isNotEmpty()) {
                            val barCount = waveformPeaks.size
                            val slotW = w / barCount
                            val barW = (slotW * 0.62f).coerceAtLeast(2f)
                            for (i in 0 until barCount) {
                                val bx = i * slotW + (slotW - barW) / 2f
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
                            color = Color(0xB3000000),
                            topLeft = Offset(0f, 0f),
                            size = Size(startX, h)
                        )
                        drawRect(
                            color = Color(0xB3000000),
                            topLeft = Offset(endX, 0f),
                            size = Size((w - endX).coerceAtLeast(0f), h)
                        )

                        // Marco del segmento activo
                        drawRect(
                            color = Color(0xFF6366F1),
                            topLeft = Offset(startX, 0f),
                            size = Size((endX - startX).coerceAtLeast(2f), h),
                            style = Stroke(width = 2.5.dp.toPx())
                        )

                        // Tirador izquierdo (INICIO)
                        val handleW = 12.dp.toPx()
                        drawRoundRect(
                            color = Color(0xFF10B981),
                            topLeft = Offset((startX - handleW / 2f).coerceIn(0f, w - handleW), 0f),
                            size = Size(handleW, h),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                        // Tirador derecho (FIN)
                        drawRoundRect(
                            color = Color(0xFF6366F1),
                            topLeft = Offset((endX - handleW / 2f).coerceIn(0f, w - handleW), 0f),
                            size = Size(handleW, h),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
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

                // Controles de Reproducción y "Capturar fotograma exacto"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = {
                                val mp = mediaPlayer
                                if (mp != null) {
                                    if (isPlaying) {
                                        mp.pause()
                                        isPlaying = false
                                    } else {
                                        if (playheadMs < startMs || playheadMs >= endMs - 100L) {
                                            playheadMs = startMs
                                        }
                                        mp.seekTo(playheadMs.toInt())
                                        mp.start()
                                        isPlaying = true
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isPlaying) "Pausar" else "Probar tramo")
                        }

                        OutlinedButton(
                            onClick = {
                                startMs = 0L
                                endMs = totalDurationMs
                                playheadMs = 0L
                                runCatching {
                                    mediaPlayer?.pause()
                                    mediaPlayer?.seekTo(0)
                                }
                                isPlaying = false
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Todo", color = Color.White)
                        }
                    }

                    // Botón directo: "Capturar fotograma exacto" para videos
                    if (isVideo) {
                        FilledTonalButton(
                            onClick = {
                                val targetTimeMs = playheadMs
                                scope.launch {
                                    val savedMsg = withContext(Dispatchers.IO) {
                                        val retriever = MediaMetadataRetriever()
                                        try {
                                            retriever.setDataSource(context, mediaFile.uri)
                                            val hiResFrame = retriever.getFrameAtTime(
                                                targetTimeMs * 1000L,
                                                MediaMetadataRetriever.OPTION_CLOSEST
                                            ) ?: currentVideoFrame
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
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.btn_capture_exact_frame),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
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
        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.55f))
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
