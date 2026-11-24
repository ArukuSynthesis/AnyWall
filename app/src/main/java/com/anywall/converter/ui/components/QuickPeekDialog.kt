package com.anywall.converter.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.anywall.converter.core.LocalThumbnailLoader
import com.anywall.converter.core.UriFileResolver
import com.anywall.converter.data.engines.ArchiveEngineImpl
import com.anywall.converter.data.engines.ArchiveEntryPreview
import com.anywall.converter.domain.model.FormatCategory
import com.anywall.converter.domain.model.InputFileItem
import com.anywall.converter.domain.model.SupportedFormat
import com.caverock.androidsvg.SVG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Vista Previa Rápida Universal (`QuickPeekDialog` - Fase 1 v0.4):
 * - Imágenes: zoom interactivo (*pinch-to-zoom* + *pan*) e información de dimensiones.
 * - Video y Audio: mini reproductor integrado con Play/Pause, barra de progreso y tiempo.
 * - PDF: visor de páginas con navegación `<` / `>` y zoom táctil.
 * - Texto / Código / Datos: visor monoespaciado con numeración de líneas.
 * - Comprimidos (`ZIP`, `RAR`, `7Z`, `TAR*`, `CBZ`, `CBR`): listado de entradas y peso descomprimido.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickPeekDialog(
    item: InputFileItem,
    uriResolver: UriFileResolver,
    thumbnailLoader: LocalThumbnailLoader?,
    archiveEngine: ArchiveEngineImpl,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = item.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${item.format.label} • ${item.formattedSize}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Cerrar vista previa")
                        }
                    }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {
                when {
                    item.format.category == FormatCategory.IMAGE -> {
                        QuickPeekImageViewer(item = item, uriResolver = uriResolver)
                    }
                    item.format == SupportedFormat.PDF -> {
                        QuickPeekPdfViewer(item = item, thumbnailLoader = thumbnailLoader)
                    }
                    item.format.isVideo || item.format.isAudio -> {
                        QuickPeekMediaPlayer(item = item, uriResolver = uriResolver)
                    }
                    item.format.category == FormatCategory.ARCHIVE ||
                        item.format == SupportedFormat.CBZ ||
                        item.format == SupportedFormat.CBR -> {
                        QuickPeekArchiveViewer(item = item, archiveEngine = archiveEngine)
                    }
                    else -> {
                        QuickPeekTextViewer(item = item, uriResolver = uriResolver)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickPeekImageViewer(
    item: InputFileItem,
    uriResolver: UriFileResolver
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(item.uri) {
        isLoading = true
        bitmap = withContext(Dispatchers.IO) {
            try {
                if (item.format == SupportedFormat.SVG) {
                    uriResolver.openInputStream(item.uri).use { stream ->
                        val svg = SVG.getFromInputStream(stream)
                        val bmp = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(bmp)
                        canvas.drawColor(android.graphics.Color.WHITE)
                        svg.renderToCanvas(canvas)
                        bmp
                    }
                } else {
                    val bytes = uriResolver.openInputStream(item.uri).use { it.readBytes() }
                    val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
                    val maxSide = max(raw.width, raw.height).coerceAtLeast(1)
                    if (maxSide > 1600) {
                        val ratio = 1600f / maxSide.toFloat()
                        Bitmap.createScaledBitmap(
                            raw,
                            (raw.width * ratio).roundToInt().coerceAtLeast(1),
                            (raw.height * ratio).roundToInt().coerceAtLeast(1),
                            true
                        )
                    } else {
                        raw
                    }
                }
            } catch (_: Throwable) {
                null
            }
        }
        isLoading = false
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val bmp = bitmap
            Text(
                text = if (bmp != null) "${bmp.width} × ${bmp.height} px • Zoom ${(scale * 100).roundToInt()}%" else "Cargando imagen…",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (scale != 1f || offset != Offset.Zero) {
                TextButton(
                    onClick = {
                        scale = 1f
                        offset = Offset.Zero
                    }
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("100%")
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0D10))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(1f, 5f)
                            scale = newScale
                            offset = if (newScale > 1.01f) {
                                offset + pan
                            } else {
                                Offset.Zero
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                when {
                    isLoading -> CircularProgressIndicator()
                    bitmap != null -> {
                        Image(
                            bitmap = bitmap!!.asImageBitmap(),
                            contentDescription = item.displayName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offset.x,
                                    translationY = offset.y
                                )
                        )
                    }
                    else -> Text("No se pudo decodificar la vista previa.", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun QuickPeekPdfViewer(
    item: InputFileItem,
    thumbnailLoader: LocalThumbnailLoader?
) {
    var totalPages by remember { mutableIntStateOf(1) }
    var currentPage by remember { mutableIntStateOf(0) }
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(item.uri) {
        totalPages = (thumbnailLoader?.getPdfPageCount(item.uri) ?: 1).coerceAtLeast(1)
        currentPage = 0
    }

    LaunchedEffect(item.uri, currentPage) {
        isLoading = true
        scale = 1f
        offset = Offset.Zero
        pageBitmap = thumbnailLoader?.loadPdfPagePreview(
            uri = item.uri,
            pageIndex = currentPage,
            targetSizePx = 1080,
            rotationDegrees = 0
        )
        isLoading = false
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { if (currentPage > 0) currentPage-- },
                enabled = currentPage > 0
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Página anterior")
            }

            Text(
                text = "Página ${currentPage + 1} de $totalPages",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            IconButton(
                onClick = { if (currentPage < totalPages - 1) currentPage++ },
                enabled = currentPage < totalPages - 1
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Página siguiente")
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(currentPage) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(1f, 4.5f)
                            scale = newScale
                            offset = if (newScale > 1.01f) offset + pan else Offset.Zero
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                when {
                    isLoading -> CircularProgressIndicator()
                    pageBitmap != null -> {
                        Image(
                            bitmap = pageBitmap!!.asImageBitmap(),
                            contentDescription = "Página ${currentPage + 1}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offset.x,
                                    translationY = offset.y
                                )
                        )
                    }
                    else -> Text("No se pudo renderizar la página PDF.", color = Color.Black)
                }
            }
        }
    }
}

@Composable
private fun QuickPeekMediaPlayer(
    item: InputFileItem,
    uriResolver: UriFileResolver
) {
    val context = LocalContext.current
    var durationMs by remember { mutableLongStateOf(0L) }
    var currentPosMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var videoFramePreview by remember { mutableStateOf<Bitmap?>(null) }
    var mediaPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }

    DisposableEffect(item.uri) {
        onDispose {
            try {
                mediaPlayerRef?.stop()
            } catch (_: Throwable) {
            }
            try {
                mediaPlayerRef?.release()
            } catch (_: Throwable) {
            }
            try {
                videoViewRef?.stopPlayback()
            } catch (_: Throwable) {
            }
        }
    }

    LaunchedEffect(item.uri) {
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, item.uri)
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                if (item.format.isVideo) {
                    videoFramePreview = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }
            } catch (_: Throwable) {
            } finally {
                try {
                    retriever.release()
                } catch (_: Throwable) {
                }
            }
        }

        if (item.format.isAudio) {
            try {
                val mp = MediaPlayer().apply {
                    setDataSource(context, item.uri)
                    prepare()
                    durationMs = duration.toLong().coerceAtLeast(durationMs)
                    setOnCompletionListener {
                        isPlaying = false
                        currentPosMs = 0L
                    }
                }
                mediaPlayerRef = mp
            } catch (_: Throwable) {
            }
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosMs = if (item.format.isVideo) {
                videoViewRef?.currentPosition?.toLong() ?: currentPosMs
            } else {
                mediaPlayerRef?.currentPosition?.toLong() ?: currentPosMs
            }
            if (durationMs > 0 && currentPosMs >= durationMs) {
                isPlaying = false
            }
            delay(150L)
        }
    }

    fun formatMs(ms: Long): String {
        val totalSec = (ms / 1000L).coerceAtLeast(0L)
        val m = totalSec / 60L
        val s = totalSec % 60L
        return "%02d:%02d".format(m, s)
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0D10))
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (item.format.isVideo) {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                videoViewRef = this
                                setVideoURI(item.uri)
                                setOnPreparedListener { mp ->
                                    durationMs = mp.duration.toLong().coerceAtLeast(durationMs)
                                }
                                setOnCompletionListener {
                                    isPlaying = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Visibility,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(56.dp)
                        )
                        Text(
                            text = item.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${item.format.label} • Duración ${formatMs(durationMs)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.LightGray
                        )
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (item.format.isVideo) {
                                val vv = videoViewRef
                                if (vv != null) {
                                    if (isPlaying) {
                                        vv.pause()
                                        isPlaying = false
                                    } else {
                                        vv.start()
                                        isPlaying = true
                                    }
                                }
                            } else {
                                val mp = mediaPlayerRef
                                if (mp != null) {
                                    if (isPlaying) {
                                        mp.pause()
                                        isPlaying = false
                                    } else {
                                        mp.start()
                                        isPlaying = true
                                    }
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Text(
                        text = "${formatMs(currentPosMs)} / ${formatMs(durationMs)}",
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Slider(
                    value = if (durationMs > 0L) (currentPosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f,
                    onValueChange = { frac ->
                        val target = (frac * durationMs).toLong()
                        currentPosMs = target
                        if (item.format.isVideo) {
                            videoViewRef?.seekTo(target.toInt())
                        } else {
                            mediaPlayerRef?.seekTo(target.toInt())
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun QuickPeekArchiveViewer(
    item: InputFileItem,
    archiveEngine: ArchiveEngineImpl
) {
    var entries by remember { mutableStateOf<List<ArchiveEntryPreview>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(item.uri) {
        isLoading = true
        entries = withContext(Dispatchers.IO) {
            archiveEngine.inspectArchiveEntries(item, maxEntries = 300)
        }
        isLoading = false
    }

    val totalUnpackedBytes = remember(entries) { entries.sumOf { it.sizeBytes } }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        ) {
            Text(
                text = "${entries.size} archivos internos • Peso descomprimido: ${InputFileItem.formatBytes(totalUnpackedBytes)}",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (entries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No se encontraron entradas visibles en el archivo.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(entries, key = { it.entryPath }) { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = entry.entryPath,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = InputFileItem.formatBytes(entry.sizeBytes),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickPeekTextViewer(
    item: InputFileItem,
    uriResolver: UriFileResolver
) {
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(item.uri) {
        isLoading = true
        lines = withContext(Dispatchers.IO) {
            try {
                uriResolver.openInputStream(item.uri).use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText().take(48_000).lines().take(600)
                }
            } catch (_: Throwable) {
                listOf("No se pudo leer el contenido de texto de ${item.displayName}.")
            }
        }
        isLoading = false
    }

    Card(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(16.dp)
    ) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(lines) { idx, line ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "%4d ".format(idx + 1),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}
