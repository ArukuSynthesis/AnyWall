package com.anywall.converter.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Rotate90DegreesCcw
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.anywall.converter.R
import com.anywall.converter.data.engines.ImageVisualTransformHelper
import com.anywall.converter.domain.model.CensorEffectMode
import com.anywall.converter.domain.model.CropAspectRatioPreset
import com.anywall.converter.domain.model.ImageAspectCanvasMode
import com.anywall.converter.domain.model.ImageEditSpec
import com.anywall.converter.domain.model.ImageFilterPreset
import com.anywall.converter.domain.model.InputFileItem
import com.anywall.converter.domain.model.NormalizedRedactionBox
import com.anywall.converter.domain.model.NormalizedRedactionStroke
import com.anywall.converter.domain.model.PerspectiveQuadSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class PhotoStudioTab {
    CROP_ROTATE,
    SCANNER_4CORNERS,
    LIVE_ADJUST,
    FILTERS,
    CENSOR_TOUCH,
    TEXT_WATERMARK
}

private enum class CropDragHandle {
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

private enum class AdjustmentControl(val labelEs: String, val minVal: Float, val maxVal: Float) {
    EXPOSURE("Exposición", -100f, 100f),
    BRIGHTNESS("Brillo", -100f, 100f),
    CONTRAST("Contraste", -100f, 100f),
    SATURATION("Saturación", -100f, 100f),
    TEMPERATURE("Temperatura", -100f, 100f),
    SHARPNESS("Nitidez", 0f, 100f),
    VIGNETTE("Viñeta", 0f, 100f)
}

private enum class CensorToolSubMode {
    FREEHAND_BRUSH,
    DRAGGABLE_RECT
}

/**
 * Estudio de Fotos 100% Táctil Full-Screen (`#0B0D10`) — AnyWall v0.4:
 * - Cero sliders de recorte: 8 tiradores táctiles (4 esquinas + 4 bordes), cuadrícula 3×3 al arrastrar,
 *   pinch-to-zoom + pan, dial táctil horizontal de enderezado (-45°..+45°), rotación 90°, volteo H/V y proporciones.
 * - Escáner / Perspectiva de 4 esquinas independientes con lupa de aumento 2.5× y `Matrix.setPolyToPoly`.
 * - Ajustes en vivo a 60 FPS (Exposición, Brillo, Contraste, Saturación, Temperatura, Nitidez, Viñeta).
 * - Filtros con miniaturas reales de la foto actual + intensidad regulable (0–100%).
 * - Censura táctil: pincel libre con el dedo + rectángulos arrastrables (`Pixelado`, `Blur`, `Negro sólido`).
 * - Texto / Marca de agua interactiva: arrastrar con 1 dedo y escalar/rotar con 2 dedos sobre la foto.
 * - Undo / Redo, Hold to Compare (Antes/Después) y "Aplicar a todo el lote".
 */
@Composable
fun ImageVisualEditorDialog(
    imageInputs: List<InputFileItem>,
    initialEditSpec: ImageEditSpec,
    onApplyEdits: (ImageEditSpec) -> Unit,
    onDismiss: () -> Unit
) {
    val first = imageInputs.firstOrNull()
    if (first == null) {
        onDismiss()
        return
    }
    ImageVisualEditorDialog(
        imageFile = first,
        initialSpec = initialEditSpec,
        batchCount = imageInputs.size,
        onApply = onApplyEdits,
        onDismiss = onDismiss
    )
}

@Composable
fun ImageVisualEditorDialog(
    imageFile: InputFileItem,
    initialSpec: ImageEditSpec,
    batchCount: Int = 1,
    onApply: (ImageEditSpec) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val studioBg = Color(0xFF0B0D10)
    val studioPanelBg = Color(0xFF14181F)
    val studioAccent = Color(0xFF6366F1)

    var activeTab by remember { mutableStateOf(PhotoStudioTab.CROP_ROTATE) }
    var spec by remember(initialSpec) { mutableStateOf(initialSpec) }

    // Undo / Redo history stack
    val undoStack = remember { mutableStateListOf<ImageEditSpec>() }
    val redoStack = remember { mutableStateListOf<ImageEditSpec>() }

    fun pushUndoState(newSpec: ImageEditSpec) {
        if (newSpec != spec) {
            undoStack.add(spec)
            if (undoStack.size > 40) undoStack.removeAt(0)
            redoStack.clear()
            spec = newSpec
        }
    }

    var isHoldingCompare by remember { mutableStateOf(false) }
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var filterThumbnails by remember { mutableStateOf<Map<ImageFilterPreset, Bitmap>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }

    // Zoom & Pan state on canvas
    var canvasZoom by remember { mutableFloatStateOf(1f) }
    var canvasPan by remember { mutableStateOf(Offset.Zero) }

    // Crop interaction state
    var activeCropHandle by remember { mutableStateOf(CropDragHandle.NONE) }
    var isDraggingCrop by remember { mutableStateOf(false) }

    // 4-Corner Scanner state (0 = TL, 1 = TR, 2 = BR, 3 = BL, -1 = none)
    var activeScannerCorner by remember { mutableIntStateOf(-1) }
    var activeLoupeNormPos by remember { mutableStateOf<Offset?>(null) }

    // Live Adjustments active control
    var selectedAdjustment by remember { mutableStateOf(AdjustmentControl.EXPOSURE) }

    // Censor Touch state
    var censorSubMode by remember { mutableStateOf(CensorToolSubMode.FREEHAND_BRUSH) }
    var censorEffectMode by remember { mutableStateOf(CensorEffectMode.PIXELATE) }
    var censorBrushThicknessNorm by remember { mutableFloatStateOf(0.045f) }
    val activeBrushPoints = remember { mutableStateListOf<Pair<Float, Float>>() }
    var draggingCensorBoxIndex by remember { mutableIntStateOf(-1) }
    var resizingCensorBoxCorner by remember { mutableStateOf(false) }

    // Decodificar foto base y generar miniaturas reales de filtros
    LaunchedEffect(imageFile.uri) {
        isLoading = true
        val decoded = withContext(Dispatchers.IO) {
            runCatching {
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(imageFile.uri)?.use {
                    BitmapFactory.decodeStream(it, null, boundsOpts)
                }
                var sample = 1
                val maxDim = max(boundsOpts.outWidth, boundsOpts.outHeight)
                while (maxDim / sample > 1280) {
                    sample *= 2
                }
                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                context.contentResolver.openInputStream(imageFile.uri)?.use {
                    BitmapFactory.decodeStream(it, null, decodeOpts)
                }
            }.getOrNull()
        }
        sourceBitmap = decoded

        if (decoded != null) {
            val thumbs = withContext(Dispatchers.Default) {
                val thumbSize = 110
                val ratio = decoded.width.toFloat() / decoded.height.toFloat().coerceAtLeast(1f)
                val tw = if (ratio >= 1f) thumbSize else (thumbSize * ratio).roundToInt().coerceAtLeast(48)
                val th = if (ratio >= 1f) (thumbSize / ratio).roundToInt().coerceAtLeast(48) else thumbSize
                val baseThumb = Bitmap.createScaledBitmap(decoded, tw, th, true)
                val map = mutableMapOf<ImageFilterPreset, Bitmap>()
                for (preset in ImageFilterPreset.entries) {
                    val previewSpec = ImageEditSpec(
                        filterPreset = preset,
                        filterIntensityPercent = 100
                    )
                    map[preset] = if (preset == ImageFilterPreset.NONE) {
                        baseThumb
                    } else {
                        ImageVisualTransformHelper.applyColorAdjustmentsAndFilter(baseThumb, previewSpec)
                    }
                }
                map
            }
            filterThumbnails = thumbs
        }
        isLoading = false
    }

    // Bitmap renderizado en vivo (para pestaña de recorte mostramos la imagen sin recortar para que el usuario mueva el marco sobre toda la foto; en las demás pestañas mostramos el resultado completo)
    var renderedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(sourceBitmap, spec, activeTab, isHoldingCompare) {
        val src = sourceBitmap ?: return@LaunchedEffect
        val preview = withContext(Dispatchers.Default) {
            if (isHoldingCompare) {
                src
            } else {
                when (activeTab) {
                    PhotoStudioTab.CROP_ROTATE -> {
                        // En modo recorte aplicamos perspectiva + rotación/enderezado/flip + color/filtros, pero dejamos el marco de recorte interactivo encima
                        val uncroppedSpec = spec.copy(
                            cropLeftNorm = 0f,
                            cropTopNorm = 0f,
                            cropRightNorm = 1f,
                            cropBottomNorm = 1f
                        )
                        ImageVisualTransformHelper.applyVisualEditSpec(src, uncroppedSpec)
                    }
                    PhotoStudioTab.SCANNER_4CORNERS -> {
                        // En modo escáner mostramos la foto original para colocar las 4 esquinas con la lupa 2.5x
                        src
                    }
                    PhotoStudioTab.TEXT_WATERMARK -> {
                        // Renderizamos sin el texto para dibujar el texto interactivo en el overlay y permitir arrastrar/rotar a 60fps
                        ImageVisualTransformHelper.applyVisualEditSpec(src, spec.copy(watermarkText = ""))
                    }
                    else -> {
                        ImageVisualTransformHelper.applyVisualEditSpec(src, spec)
                    }
                }
            }
        }
        renderedBitmap = preview
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = studioBg,
            contentColor = Color.White
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 1. TOP BAR DEL ESTUDIO
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(studioPanelBg)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.btn_cancel),
                                tint = Color.White
                            )
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.photo_studio_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = imageFile.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF94A3B8),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.width(130.dp)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // Undo
                        IconButton(
                            onClick = {
                                if (undoStack.isNotEmpty()) {
                                    val prev = undoStack.removeAt(undoStack.lastIndex)
                                    redoStack.add(spec)
                                    spec = prev
                                }
                            },
                            enabled = undoStack.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Undo,
                                contentDescription = stringResource(R.string.btn_undo),
                                tint = if (undoStack.isNotEmpty()) Color.White else Color(0xFF475569)
                            )
                        }
                        // Redo
                        IconButton(
                            onClick = {
                                if (redoStack.isNotEmpty()) {
                                    val next = redoStack.removeAt(redoStack.lastIndex)
                                    undoStack.add(spec)
                                    spec = next
                                }
                            },
                            enabled = redoStack.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Redo,
                                contentDescription = stringResource(R.string.btn_redo),
                                tint = if (redoStack.isNotEmpty()) Color.White else Color(0xFF475569)
                            )
                        }
                        // Hold to Compare (Antes / Después)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isHoldingCompare) studioAccent else Color(0xFF1E293B))
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onPress = {
                                            isHoldingCompare = true
                                            tryAwaitRelease()
                                            isHoldingCompare = false
                                        }
                                    )
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Compare,
                                    contentDescription = stringResource(R.string.btn_hold_to_compare),
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (isHoldingCompare) "ANTES" else "Comparar",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                        }
                        // Reset
                        IconButton(
                            onClick = {
                                pushUndoState(ImageEditSpec(applyToAllBatch = spec.applyToAllBatch))
                                canvasZoom = 1f
                                canvasPan = Offset.Zero
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = stringResource(R.string.btn_reset_edits),
                                tint = Color(0xFFF87171)
                            )
                        }
                        // Guardar / Aplicar
                        Button(
                            onClick = { onApply(spec) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = studioAccent,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.btn_apply_visual_edits),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // 2. LIENZO INTERACTIVO CENTRAL (60 FPS + GESTOS TÁCTILES)
                var viewportSize by remember { mutableStateOf(IntSize.Zero) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(studioBg)
                        .onSizeChanged { viewportSize = it }
                        .pointerInput(activeTab) {
                            // En pestañas de Ajustes y Filtros permitimos Pinch-to-Zoom y Pan libre sobre la foto
                            if (activeTab == PhotoStudioTab.LIVE_ADJUST || activeTab == PhotoStudioTab.FILTERS) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val newZoom = (canvasZoom * zoom).coerceIn(1f, 4f)
                                    canvasZoom = newZoom
                                    canvasPan = if (newZoom <= 1.02f) {
                                        Offset.Zero
                                    } else {
                                        Offset(
                                            x = (canvasPan.x + pan.x).coerceIn(-600f, 600f),
                                            y = (canvasPan.y + pan.y).coerceIn(-600f, 600f)
                                        )
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = renderedBitmap
                    if (isLoading || bmp == null) {
                        CircularProgressIndicator(color = studioAccent)
                    } else {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp)
                                // Gestos de Recorte con 8 tiradores táctiles
                                .pointerInput(activeTab, bmp, viewportSize) {
                                    if (activeTab != PhotoStudioTab.CROP_ROTATE) return@pointerInput
                                    detectDragGestures(
                                        onDragStart = { touch ->
                                            val imgRect = computeImageBoundsInCanvas(
                                                canvasW = size.width.toFloat(),
                                                canvasH = size.height.toFloat(),
                                                bmpW = bmp.width.toFloat(),
                                                bmpH = bmp.height.toFloat()
                                            )
                                            activeCropHandle = hitTestCropHandle(
                                                touch = touch,
                                                imgRect = imgRect,
                                                spec = spec
                                            )
                                            if (activeCropHandle != CropDragHandle.NONE) {
                                                undoStack.add(spec)
                                                if (undoStack.size > 40) undoStack.removeAt(0)
                                                redoStack.clear()
                                                isDraggingCrop = true
                                            }
                                        },
                                        onDragEnd = {
                                            isDraggingCrop = false
                                            activeCropHandle = CropDragHandle.NONE
                                        },
                                        onDragCancel = {
                                            isDraggingCrop = false
                                            activeCropHandle = CropDragHandle.NONE
                                        },
                                        onDrag = { change, dragAmount ->
                                            if (activeCropHandle == CropDragHandle.NONE) return@detectDragGestures
                                            change.consume()
                                            val imgRect = computeImageBoundsInCanvas(
                                                canvasW = size.width.toFloat(),
                                                canvasH = size.height.toFloat(),
                                                bmpW = bmp.width.toFloat(),
                                                bmpH = bmp.height.toFloat()
                                            )
                                            val dxNorm = dragAmount.x / imgRect.width.coerceAtLeast(1f)
                                            val dyNorm = dragAmount.y / imgRect.height.coerceAtLeast(1f)
                                            val sourceAspect = bmp.width.toFloat() / bmp.height.toFloat().coerceAtLeast(1f)
                                            spec = updateCropWithHandleDrag(
                                                current = spec,
                                                handle = activeCropHandle,
                                                dxNorm = dxNorm,
                                                dyNorm = dyNorm,
                                                imageAspectRatio = sourceAspect
                                            )
                                        }
                                    )
                                }
                                // Gestos de Escáner de 4 Esquinas con Lupa 2.5x
                                .pointerInput(activeTab, bmp, viewportSize) {
                                    if (activeTab != PhotoStudioTab.SCANNER_4CORNERS) return@pointerInput
                                    detectDragGestures(
                                        onDragStart = { touch ->
                                            val imgRect = computeImageBoundsInCanvas(
                                                canvasW = size.width.toFloat(),
                                                canvasH = size.height.toFloat(),
                                                bmpW = bmp.width.toFloat(),
                                                bmpH = bmp.height.toFloat()
                                            )
                                            val q = spec.perspectiveQuad
                                            val corners = listOf(
                                                Offset(imgRect.left + q.tlX * imgRect.width, imgRect.top + q.tlY * imgRect.height),
                                                Offset(imgRect.left + q.trX * imgRect.width, imgRect.top + q.trY * imgRect.height),
                                                Offset(imgRect.left + q.brX * imgRect.width, imgRect.top + q.brY * imgRect.height),
                                                Offset(imgRect.left + q.blX * imgRect.width, imgRect.top + q.blY * imgRect.height)
                                            )
                                            val hitIdx = corners.indexOfFirst { hypot((it.x - touch.x).toDouble(), (it.y - touch.y).toDouble()) <= 56.0 }
                                            activeScannerCorner = hitIdx
                                            if (hitIdx >= 0) {
                                                undoStack.add(spec)
                                                if (undoStack.size > 40) undoStack.removeAt(0)
                                                redoStack.clear()
                                                val normX = ((touch.x - imgRect.left) / imgRect.width).coerceIn(0f, 1f)
                                                val normY = ((touch.y - imgRect.top) / imgRect.height).coerceIn(0f, 1f)
                                                activeLoupeNormPos = Offset(normX, normY)
                                            }
                                        },
                                        onDragEnd = {
                                            activeScannerCorner = -1
                                            activeLoupeNormPos = null
                                        },
                                        onDragCancel = {
                                            activeScannerCorner = -1
                                            activeLoupeNormPos = null
                                        },
                                        onDrag = { change, _ ->
                                            if (activeScannerCorner !in 0..3) return@detectDragGestures
                                            change.consume()
                                            val imgRect = computeImageBoundsInCanvas(
                                                canvasW = size.width.toFloat(),
                                                canvasH = size.height.toFloat(),
                                                bmpW = bmp.width.toFloat(),
                                                bmpH = bmp.height.toFloat()
                                            )
                                            val normX = ((change.position.x - imgRect.left) / imgRect.width.coerceAtLeast(1f)).coerceIn(0f, 1f)
                                            val normY = ((change.position.y - imgRect.top) / imgRect.height.coerceAtLeast(1f)).coerceIn(0f, 1f)
                                            activeLoupeNormPos = Offset(normX, normY)
                                            val q = spec.perspectiveQuad
                                            val updatedQuad = when (activeScannerCorner) {
                                                0 -> q.copy(tlX = normX, tlY = normY)
                                                1 -> q.copy(trX = normX, trY = normY)
                                                2 -> q.copy(brX = normX, brY = normY)
                                                else -> q.copy(blX = normX, blY = normY)
                                            }
                                            spec = spec.copy(perspectiveQuad = updatedQuad)
                                        }
                                    )
                                }
                                // Gestos de Censura Táctil (Pincel libre + Rectángulos arrastrables)
                                .pointerInput(activeTab, censorSubMode, censorEffectMode, censorBrushThicknessNorm, bmp) {
                                    if (activeTab != PhotoStudioTab.CENSOR_TOUCH) return@pointerInput
                                    detectDragGestures(
                                        onDragStart = { touch ->
                                            val imgRect = computeImageBoundsInCanvas(
                                                canvasW = size.width.toFloat(),
                                                canvasH = size.height.toFloat(),
                                                bmpW = bmp.width.toFloat(),
                                                bmpH = bmp.height.toFloat()
                                            )
                                            val nx = ((touch.x - imgRect.left) / imgRect.width.coerceAtLeast(1f)).coerceIn(0f, 1f)
                                            val ny = ((touch.y - imgRect.top) / imgRect.height.coerceAtLeast(1f)).coerceIn(0f, 1f)
                                            if (censorSubMode == CensorToolSubMode.FREEHAND_BRUSH) {
                                                activeBrushPoints.clear()
                                                activeBrushPoints.add(nx to ny)
                                            } else {
                                                // Buscar si tocó una caja existente (esquina inferior derecha para redimensionar o interior para mover)
                                                var foundIdx = -1
                                                var isCorner = false
                                                spec.redactionBoxes.forEachIndexed { idx, box ->
                                                    val bx1 = imgRect.left + box.clampedLeft * imgRect.width
                                                    val by1 = imgRect.top + box.clampedTop * imgRect.height
                                                    val bx2 = imgRect.left + box.clampedRight * imgRect.width
                                                    val by2 = imgRect.top + box.clampedBottom * imgRect.height
                                                    if (hypot((touch.x - bx2).toDouble(), (touch.y - by2).toDouble()) <= 42.0) {
                                                        foundIdx = idx
                                                        isCorner = true
                                                    } else if (touch.x in bx1..bx2 && touch.y in by1..by2) {
                                                        foundIdx = idx
                                                        isCorner = false
                                                    }
                                                }
                                                if (foundIdx >= 0) {
                                                    undoStack.add(spec)
                                                    redoStack.clear()
                                                    draggingCensorBoxIndex = foundIdx
                                                    resizingCensorBoxCorner = isCorner
                                                } else {
                                                    // Crear nueva caja arrastrando desde el toque
                                                    undoStack.add(spec)
                                                    redoStack.clear()
                                                    val newBox = NormalizedRedactionBox(
                                                        left = (nx - 0.08f).coerceIn(0f, 0.90f),
                                                        top = (ny - 0.05f).coerceIn(0f, 0.90f),
                                                        right = (nx + 0.08f).coerceIn(0.10f, 1f),
                                                        bottom = (ny + 0.05f).coerceIn(0.10f, 1f),
                                                        isPixelated = censorEffectMode == CensorEffectMode.PIXELATE,
                                                        effectMode = censorEffectMode
                                                    )
                                                    val updated = spec.redactionBoxes + newBox
                                                    spec = spec.copy(redactionBoxes = updated)
                                                    draggingCensorBoxIndex = updated.lastIndex
                                                    resizingCensorBoxCorner = true
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            if (censorSubMode == CensorToolSubMode.FREEHAND_BRUSH && activeBrushPoints.isNotEmpty()) {
                                                val stroke = NormalizedRedactionStroke(
                                                    points = activeBrushPoints.toList(),
                                                    strokeWidthNorm = censorBrushThicknessNorm,
                                                    effectMode = censorEffectMode
                                                )
                                                activeBrushPoints.clear()
                                                pushUndoState(spec.copy(redactionStrokes = spec.redactionStrokes + stroke))
                                            }
                                            draggingCensorBoxIndex = -1
                                            resizingCensorBoxCorner = false
                                        },
                                        onDragCancel = {
                                            activeBrushPoints.clear()
                                            draggingCensorBoxIndex = -1
                                            resizingCensorBoxCorner = false
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            val imgRect = computeImageBoundsInCanvas(
                                                canvasW = size.width.toFloat(),
                                                canvasH = size.height.toFloat(),
                                                bmpW = bmp.width.toFloat(),
                                                bmpH = bmp.height.toFloat()
                                            )
                                            if (censorSubMode == CensorToolSubMode.FREEHAND_BRUSH) {
                                                val nx = ((change.position.x - imgRect.left) / imgRect.width.coerceAtLeast(1f)).coerceIn(0f, 1f)
                                                val ny = ((change.position.y - imgRect.top) / imgRect.height.coerceAtLeast(1f)).coerceIn(0f, 1f)
                                                activeBrushPoints.add(nx to ny)
                                            } else if (draggingCensorBoxIndex in spec.redactionBoxes.indices) {
                                                val dx = dragAmount.x / imgRect.width.coerceAtLeast(1f)
                                                val dy = dragAmount.y / imgRect.height.coerceAtLeast(1f)
                                                val list = spec.redactionBoxes.toMutableList()
                                                val old = list[draggingCensorBoxIndex]
                                                list[draggingCensorBoxIndex] = if (resizingCensorBoxCorner) {
                                                    old.copy(
                                                        right = (old.clampedRight + dx).coerceIn(old.clampedLeft + 0.04f, 1f),
                                                        bottom = (old.clampedBottom + dy).coerceIn(old.clampedTop + 0.04f, 1f)
                                                    )
                                                } else {
                                                    val w = old.clampedRight - old.clampedLeft
                                                    val h = old.clampedBottom - old.clampedTop
                                                    val nl = (old.clampedLeft + dx).coerceIn(0f, 1f - w)
                                                    val nt = (old.clampedTop + dy).coerceIn(0f, 1f - h)
                                                    old.copy(
                                                        left = nl,
                                                        top = nt,
                                                        right = nl + w,
                                                        bottom = nt + h
                                                    )
                                                }
                                                spec = spec.copy(redactionBoxes = list)
                                            }
                                        }
                                    )
                                }
                                // Gestos de Texto / Marca de Agua (1 dedo arrastra, 2 dedos escalan y rotan)
                                .pointerInput(activeTab, bmp) {
                                    if (activeTab != PhotoStudioTab.TEXT_WATERMARK) return@pointerInput
                                    detectTransformGestures { _, pan, zoom, rotation ->
                                        val imgRect = computeImageBoundsInCanvas(
                                            canvasW = size.width.toFloat(),
                                            canvasH = size.height.toFloat(),
                                            bmpW = bmp.width.toFloat(),
                                            bmpH = bmp.height.toFloat()
                                        )
                                        val dxNorm = pan.x / imgRect.width.coerceAtLeast(1f)
                                        val dyNorm = pan.y / imgRect.height.coerceAtLeast(1f)
                                        spec = spec.copy(
                                            watermarkPosXNorm = (spec.watermarkPosXNorm + dxNorm).coerceIn(0.03f, 0.97f),
                                            watermarkPosYNorm = (spec.watermarkPosYNorm + dyNorm).coerceIn(0.03f, 0.97f),
                                            watermarkScale = (spec.watermarkScale * zoom).coerceIn(0.35f, 3.5f),
                                            watermarkRotationDegrees = normalizeAngle180(spec.watermarkRotationDegrees + rotation)
                                        )
                                    }
                                }
                        ) {
                            val imgRect = computeImageBoundsInCanvas(
                                canvasW = size.width,
                                canvasH = size.height,
                                bmpW = bmp.width.toFloat(),
                                bmpH = bmp.height.toFloat()
                            )

                            // Dibujar Bitmap principal
                            drawIntoCanvas { canvas ->
                                canvas.save()
                                if (activeTab == PhotoStudioTab.LIVE_ADJUST || activeTab == PhotoStudioTab.FILTERS) {
                                    canvas.translate(canvasPan.x, canvasPan.y)
                                    canvas.translate(size.width / 2f, size.height / 2f)
                                    canvas.scale(canvasZoom, canvasZoom)
                                    canvas.translate(-(size.width / 2f), -(size.height / 2f))
                                }
                                val dstRect = android.graphics.RectF(
                                    imgRect.left,
                                    imgRect.top,
                                    imgRect.right,
                                    imgRect.bottom
                                )
                                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                                canvas.nativeCanvas.drawBitmap(bmp, null, dstRect, paint)
                                canvas.restore()
                            }

                            if (!isHoldingCompare) {
                                when (activeTab) {
                                    PhotoStudioTab.CROP_ROTATE -> {
                                        // Marco de Recorte con 8 tiradores táctiles y cuadrícula 3x3
                                        val cropLeft = imgRect.left + spec.cropLeftNorm * imgRect.width
                                        val cropTop = imgRect.top + spec.cropTopNorm * imgRect.height
                                        val cropRight = imgRect.left + spec.cropRightNorm * imgRect.width
                                        val cropBottom = imgRect.top + spec.cropBottomNorm * imgRect.height
                                        val cropW = (cropRight - cropLeft).coerceAtLeast(1f)
                                        val cropH = (cropBottom - cropTop).coerceAtLeast(1f)

                                        // Oscurecer exterior del área recortada
                                        val maskColor = Color(0xAA000000)
                                        drawRect(maskColor, Offset(imgRect.left, imgRect.top), Size(imgRect.width, cropTop - imgRect.top))
                                        drawRect(maskColor, Offset(imgRect.left, cropBottom), Size(imgRect.width, imgRect.bottom - cropBottom))
                                        drawRect(maskColor, Offset(imgRect.left, cropTop), Size(cropLeft - imgRect.left, cropH))
                                        drawRect(maskColor, Offset(cropRight, cropTop), Size(imgRect.right - cropRight, cropH))

                                        // Cuadrícula 3x3 (Rule of Thirds) visible al arrastrar o enderezar
                                        val gridAlpha = if (isDraggingCrop || abs(spec.straightenDegrees) > 0.1f) 0.78f else 0.35f
                                        for (i in 1..2) {
                                            val gx = cropLeft + (cropW * i / 3f)
                                            val gy = cropTop + (cropH * i / 3f)
                                            drawLine(
                                                color = Color.White.copy(alpha = gridAlpha),
                                                start = Offset(gx, cropTop),
                                                end = Offset(gx, cropBottom),
                                                strokeWidth = 1.dp.toPx()
                                            )
                                            drawLine(
                                                color = Color.White.copy(alpha = gridAlpha),
                                                start = Offset(cropLeft, gy),
                                                end = Offset(cropRight, gy),
                                                strokeWidth = 1.dp.toPx()
                                            )
                                        }

                                        // Borde blanco del recorte
                                        drawRect(
                                            color = Color.White,
                                            topLeft = Offset(cropLeft, cropTop),
                                            size = Size(cropW, cropH),
                                            style = Stroke(width = 2.dp.toPx())
                                        )

                                        // 8 Tiradores táctiles (4 esquinas + 4 centros de borde)
                                        val handlePoints = listOf(
                                            Offset(cropLeft, cropTop),
                                            Offset(cropRight, cropTop),
                                            Offset(cropLeft, cropBottom),
                                            Offset(cropRight, cropBottom),
                                            Offset((cropLeft + cropRight) / 2f, cropTop),
                                            Offset((cropLeft + cropRight) / 2f, cropBottom),
                                            Offset(cropLeft, (cropTop + cropBottom) / 2f),
                                            Offset(cropRight, (cropTop + cropBottom) / 2f)
                                        )
                                        handlePoints.forEachIndexed { idx, pt ->
                                            val radius = if (idx < 4) 8.dp.toPx() else 6.dp.toPx()
                                            drawCircle(color = studioAccent, radius = radius + 2.dp.toPx(), center = pt)
                                            drawCircle(color = Color.White, radius = radius, center = pt)
                                        }
                                    }

                                    PhotoStudioTab.SCANNER_4CORNERS -> {
                                        // Polígono de 4 esquinas independientes + Lupa 2.5x al arrastrar
                                        val q = spec.perspectiveQuad
                                        val tl = Offset(imgRect.left + q.tlX * imgRect.width, imgRect.top + q.tlY * imgRect.height)
                                        val tr = Offset(imgRect.left + q.trX * imgRect.width, imgRect.top + q.trY * imgRect.height)
                                        val br = Offset(imgRect.left + q.brX * imgRect.width, imgRect.top + q.brY * imgRect.height)
                                        val bl = Offset(imgRect.left + q.blX * imgRect.width, imgRect.top + q.blY * imgRect.height)

                                        val path = Path().apply {
                                            moveTo(tl.x, tl.y)
                                            lineTo(tr.x, tr.y)
                                            lineTo(br.x, br.y)
                                            lineTo(bl.x, bl.y)
                                            close()
                                        }
                                        drawPath(
                                            path = path,
                                            color = Color(0x3310B981)
                                        )
                                        drawPath(
                                            path = path,
                                            color = Color(0xFF10B981),
                                            style = Stroke(width = 2.5.dp.toPx())
                                        )

                                        listOf(tl, tr, br, bl).forEachIndexed { idx, corner ->
                                            val isSelected = idx == activeScannerCorner
                                            drawCircle(
                                                color = if (isSelected) Color(0xFFF59E0B) else Color(0xFF10B981),
                                                radius = if (isSelected) 12.dp.toPx() else 9.dp.toPx(),
                                                center = corner
                                            )
                                            drawCircle(
                                                color = Color.White,
                                                radius = 4.dp.toPx(),
                                                center = corner
                                            )
                                        }

                                        // Lupa de aumento 2.5x en tiempo real
                                        val loupeNorm = activeLoupeNormPos
                                        if (loupeNorm != null) {
                                            val loupeRadius = 56.dp.toPx()
                                            // Si el dedo está arriba a la izquierda, ponemos la lupa arriba a la derecha
                                            val loupeCenter = if (loupeNorm.x < 0.45f && loupeNorm.y < 0.45f) {
                                                Offset(size.width - loupeRadius - 16.dp.toPx(), loupeRadius + 16.dp.toPx())
                                            } else {
                                                Offset(loupeRadius + 16.dp.toPx(), loupeRadius + 16.dp.toPx())
                                            }

                                            drawIntoCanvas { canvas ->
                                                val zoomFactor = 2.5f
                                                val srcX = loupeNorm.x * bmp.width
                                                val srcY = loupeNorm.y * bmp.height
                                                val shaderMatrix = Matrix().apply {
                                                    postTranslate(-srcX, -srcY)
                                                    postScale(zoomFactor, zoomFactor)
                                                    postTranslate(loupeCenter.x, loupeCenter.y)
                                                }
                                                val shader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                                                    setLocalMatrix(shaderMatrix)
                                                }
                                                val shaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                                    this.shader = shader
                                                }
                                                canvas.nativeCanvas.drawCircle(
                                                    loupeCenter.x,
                                                    loupeCenter.y,
                                                    loupeRadius,
                                                    shaderPaint
                                                )
                                            }
                                            drawCircle(
                                                color = Color(0xFF10B981),
                                                radius = loupeRadius,
                                                center = loupeCenter,
                                                style = Stroke(width = 3.dp.toPx())
                                            )
                                            // Retícula de precisión 2.5x en el centro de la lupa
                                            drawLine(
                                                color = Color(0xFFF59E0B),
                                                start = Offset(loupeCenter.x - 14.dp.toPx(), loupeCenter.y),
                                                end = Offset(loupeCenter.x + 14.dp.toPx(), loupeCenter.y),
                                                strokeWidth = 1.5.dp.toPx()
                                            )
                                            drawLine(
                                                color = Color(0xFFF59E0B),
                                                start = Offset(loupeCenter.x, loupeCenter.y - 14.dp.toPx()),
                                                end = Offset(loupeCenter.x, loupeCenter.y + 14.dp.toPx()),
                                                strokeWidth = 1.5.dp.toPx()
                                            )
                                        }
                                    }

                                    PhotoStudioTab.CENSOR_TOUCH -> {
                                        // Mostrar trazo activo mientras el usuario pinta con el dedo
                                        if (activeBrushPoints.size >= 2) {
                                            val strokePath = Path()
                                            activeBrushPoints.forEachIndexed { idx, (nx, ny) ->
                                                val px = imgRect.left + nx * imgRect.width
                                                val py = imgRect.top + ny * imgRect.height
                                                if (idx == 0) strokePath.moveTo(px, py) else strokePath.lineTo(px, py)
                                            }
                                            val minSide = min(imgRect.width, imgRect.height)
                                            drawPath(
                                                path = strokePath,
                                                color = when (censorEffectMode) {
                                                    CensorEffectMode.SOLID_BLACK -> Color.Black
                                                    CensorEffectMode.PIXELATE -> Color(0xCC6366F1)
                                                    CensorEffectMode.BLUR -> Color(0xCC06B6D4)
                                                },
                                                style = Stroke(
                                                    width = (censorBrushThicknessNorm * minSide).coerceAtLeast(8f),
                                                    cap = StrokeCap.Round,
                                                    join = StrokeJoin.Round
                                                )
                                            )
                                        }

                                        // Mostrar guías de rectángulos arrastrables
                                        spec.redactionBoxes.forEachIndexed { idx, box ->
                                            val bx1 = imgRect.left + box.clampedLeft * imgRect.width
                                            val by1 = imgRect.top + box.clampedTop * imgRect.height
                                            val bx2 = imgRect.left + box.clampedRight * imgRect.width
                                            val by2 = imgRect.top + box.clampedBottom * imgRect.height
                                            val isSelected = idx == draggingCensorBoxIndex
                                            drawRect(
                                                color = if (isSelected) Color(0xFFF59E0B) else Color.White.copy(alpha = 0.7f),
                                                topLeft = Offset(bx1, by1),
                                                size = Size(bx2 - bx1, by2 - by1),
                                                style = Stroke(width = 1.8.dp.toPx())
                                            )
                                            drawCircle(
                                                color = Color(0xFFF59E0B),
                                                radius = 6.dp.toPx(),
                                                center = Offset(bx2, by2)
                                            )
                                        }
                                    }

                                    PhotoStudioTab.TEXT_WATERMARK -> {
                                        if (spec.watermarkText.isNotBlank()) {
                                            drawIntoCanvas { canvas ->
                                                val cx = imgRect.left + spec.watermarkPosXNorm * imgRect.width
                                                val cy = imgRect.top + spec.watermarkPosYNorm * imgRect.height
                                                val baseSize = (min(imgRect.width, imgRect.height) * 0.055f * spec.watermarkScale)
                                                    .coerceIn(14f, 180f)
                                                val alpha = ((spec.watermarkOpacityPercent.coerceIn(10, 100) / 100f) * 255f)
                                                    .roundToInt().coerceIn(25, 255)
                                                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                                    color = spec.watermarkColorArgb
                                                    this.alpha = alpha
                                                    textSize = baseSize
                                                    textAlign = Paint.Align.CENTER
                                                    typeface = android.graphics.Typeface.create(
                                                        android.graphics.Typeface.DEFAULT,
                                                        android.graphics.Typeface.BOLD
                                                    )
                                                    setShadowLayer(6f, 2f, 2f, android.graphics.Color.argb(180, 0, 0, 0))
                                                }
                                                canvas.nativeCanvas.save()
                                                canvas.nativeCanvas.translate(cx, cy)
                                                canvas.nativeCanvas.rotate(spec.watermarkRotationDegrees)
                                                canvas.nativeCanvas.drawText(spec.watermarkText, 0f, baseSize * 0.35f, textPaint)
                                                canvas.nativeCanvas.restore()
                                            }
                                        }
                                    }

                                    else -> Unit
                                }
                            }
                        }
                    }
                }

                // 3. PANEL DE CONTROLES SEGÚN LA HERRAMIENTA ACTIVA
                Surface(
                    color = studioPanelBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        when (activeTab) {
                            PhotoStudioTab.CROP_ROTATE -> {
                                // Dial táctil horizontal de enderezado (-45° .. +45°)
                                TouchStraightenDial(
                                    degrees = spec.straightenDegrees,
                                    onDegreesChange = { newDeg ->
                                        spec = spec.copy(straightenDegrees = newDeg)
                                    },
                                    onResetDegrees = {
                                        pushUndoState(spec.copy(straightenDegrees = 0f))
                                    }
                                )

                                // Botones rápidos de Rotación 90° y Volteo H/V
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        OutlinedButton(
                                            onClick = {
                                                pushUndoState(
                                                    spec.copy(rotation90Degrees = (spec.normalizedRotation90 + 270) % 360)
                                                )
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Default.Rotate90DegreesCcw, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("-90°", style = MaterialTheme.typography.labelSmall, color = Color.White)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                pushUndoState(
                                                    spec.copy(rotation90Degrees = (spec.normalizedRotation90 + 90) % 360)
                                                )
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Default.RotateRight, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("+90°", style = MaterialTheme.typography.labelSmall, color = Color.White)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                pushUndoState(spec.copy(flipHorizontal = !spec.flipHorizontal))
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Default.Flip, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Flip H", style = MaterialTheme.typography.labelSmall, color = Color.White)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                pushUndoState(spec.copy(flipVertical = !spec.flipVertical))
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text("Flip V", style = MaterialTheme.typography.labelSmall, color = Color.White)
                                        }
                                    }
                                }

                                // Chips de proporción de aspecto (Libre, Original, 1:1, 4:5, 3:4, 16:9, 9:16)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val srcRatio = (sourceBitmap?.width ?: 1).toFloat() /
                                        (sourceBitmap?.height ?: 1).toFloat().coerceAtLeast(1f)
                                    CropAspectRatioPreset.entries.forEach { preset ->
                                        FilterChip(
                                            selected = spec.cropAspectRatioPreset == preset,
                                            onClick = {
                                                pushUndoState(
                                                    applyAspectRatioPresetToCrop(
                                                        current = spec,
                                                        preset = preset,
                                                        imageAspectRatio = srcRatio
                                                    )
                                                )
                                            },
                                            label = {
                                                Text(
                                                    text = preset.labelEs,
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = studioAccent,
                                                selectedLabelColor = Color.White,
                                                labelColor = Color(0xFFCBD5E1)
                                            )
                                        )
                                    }
                                }
                            }

                            PhotoStudioTab.SCANNER_4CORNERS -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Escáner de Documentos / Perspectiva 4 Esquinas",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Arrastra cualquiera de las 4 esquinas verdes para activar la lupa 2.5× y rectificar la perspectiva.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        OutlinedButton(
                                            onClick = {
                                                // Auto-encuadre de documento con margen interior del 8%
                                                pushUndoState(
                                                    spec.copy(
                                                        perspectiveQuad = PerspectiveQuadSpec(
                                                            tlX = 0.08f, tlY = 0.08f,
                                                            trX = 0.92f, trY = 0.08f,
                                                            brX = 0.92f, brY = 0.92f,
                                                            blX = 0.08f, blY = 0.92f
                                                        )
                                                    )
                                                )
                                            }
                                        ) {
                                            Text("Auto-guía", style = MaterialTheme.typography.labelSmall, color = Color.White)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                pushUndoState(spec.copy(perspectiveQuad = PerspectiveQuadSpec()))
                                            }
                                        ) {
                                            Text("Restablecer", style = MaterialTheme.typography.labelSmall, color = Color.White)
                                        }
                                    }
                                }
                            }

                            PhotoStudioTab.LIVE_ADJUST -> {
                                val currentValue = when (selectedAdjustment) {
                                    AdjustmentControl.EXPOSURE -> spec.exposure
                                    AdjustmentControl.BRIGHTNESS -> spec.brightness
                                    AdjustmentControl.CONTRAST -> spec.contrast
                                    AdjustmentControl.SATURATION -> spec.saturation
                                    AdjustmentControl.TEMPERATURE -> spec.temperature
                                    AdjustmentControl.SHARPNESS -> spec.sharpness
                                    AdjustmentControl.VIGNETTE -> spec.vignette
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${selectedAdjustment.labelEs}: ${currentValue.roundToInt()}",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Toca dos veces o pulsa 0 para centrar",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF94A3B8),
                                        modifier = Modifier.clickable {
                                            pushUndoState(updateAdjustmentValue(spec, selectedAdjustment, 0f))
                                        }
                                    )
                                }

                                Slider(
                                    value = currentValue,
                                    onValueChange = { v ->
                                        spec = updateAdjustmentValue(spec, selectedAdjustment, v)
                                    },
                                    valueRange = selectedAdjustment.minVal..selectedAdjustment.maxVal,
                                    colors = SliderDefaults.colors(
                                        thumbColor = studioAccent,
                                        activeTrackColor = studioAccent
                                    )
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    AdjustmentControl.entries.forEach { ctrl ->
                                        val ctrlVal = when (ctrl) {
                                            AdjustmentControl.EXPOSURE -> spec.exposure
                                            AdjustmentControl.BRIGHTNESS -> spec.brightness
                                            AdjustmentControl.CONTRAST -> spec.contrast
                                            AdjustmentControl.SATURATION -> spec.saturation
                                            AdjustmentControl.TEMPERATURE -> spec.temperature
                                            AdjustmentControl.SHARPNESS -> spec.sharpness
                                            AdjustmentControl.VIGNETTE -> spec.vignette
                                        }.roundToInt()
                                        FilterChip(
                                            selected = selectedAdjustment == ctrl,
                                            onClick = { selectedAdjustment = ctrl },
                                            label = {
                                                Text(
                                                    text = if (ctrlVal != 0) "${ctrl.labelEs} ($ctrlVal)" else ctrl.labelEs,
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = studioAccent,
                                                selectedLabelColor = Color.White,
                                                labelColor = Color(0xFFCBD5E1)
                                            )
                                        )
                                    }
                                }
                            }

                            PhotoStudioTab.FILTERS -> {
                                if (spec.filterPreset != ImageFilterPreset.NONE) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = "Intensidad: ${spec.filterIntensityPercent}%",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color.White,
                                            modifier = Modifier.width(110.dp)
                                        )
                                        Slider(
                                            value = spec.filterIntensityPercent.toFloat(),
                                            onValueChange = {
                                                spec = spec.copy(filterIntensityPercent = it.roundToInt().coerceIn(0, 100))
                                            },
                                            valueRange = 0f..100f,
                                            modifier = Modifier.weight(1f),
                                            colors = SliderDefaults.colors(
                                                thumbColor = studioAccent,
                                                activeTrackColor = studioAccent
                                            )
                                        )
                                    }
                                }

                                LazyRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(ImageFilterPreset.entries) { preset ->
                                        val isSelected = spec.filterPreset == preset
                                        val thumb = filterThumbnails[preset]
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable {
                                                    pushUndoState(
                                                        spec.copy(
                                                            filterPreset = preset,
                                                            filterIntensityPercent = if (spec.filterIntensityPercent == 0) 100 else spec.filterIntensityPercent
                                                        )
                                                    )
                                                }
                                                .padding(4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .border(
                                                        width = if (isSelected) 2.5.dp else 1.dp,
                                                        color = if (isSelected) studioAccent else Color(0xFF334155),
                                                        shape = RoundedCornerShape(8.dp)
                                                    )
                                                    .background(Color(0xFF1E293B)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (thumb != null) {
                                                    Image(
                                                        bitmap = thumb.asImageBitmap(),
                                                        contentDescription = preset.labelEs,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = preset.labelEs,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            }

                            PhotoStudioTab.CENSOR_TOUCH -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        FilterChip(
                                            selected = censorSubMode == CensorToolSubMode.FREEHAND_BRUSH,
                                            onClick = { censorSubMode = CensorToolSubMode.FREEHAND_BRUSH },
                                            label = { Text("Pincel Libre", style = MaterialTheme.typography.labelSmall) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = studioAccent,
                                                selectedLabelColor = Color.White,
                                                labelColor = Color(0xFFCBD5E1)
                                            )
                                        )
                                        FilterChip(
                                            selected = censorSubMode == CensorToolSubMode.DRAGGABLE_RECT,
                                            onClick = { censorSubMode = CensorToolSubMode.DRAGGABLE_RECT },
                                            label = { Text("Rectángulo", style = MaterialTheme.typography.labelSmall) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = studioAccent,
                                                selectedLabelColor = Color.White,
                                                labelColor = Color(0xFFCBD5E1)
                                            )
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            pushUndoState(
                                                spec.copy(
                                                    redactionBoxes = emptyList(),
                                                    redactionStrokes = emptyList()
                                                )
                                            )
                                        },
                                        enabled = spec.redactionBoxes.isNotEmpty() || spec.redactionStrokes.isNotEmpty()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteSweep,
                                            contentDescription = "Limpiar censuras",
                                            tint = Color(0xFFF87171)
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CensorEffectMode.entries.forEach { effect ->
                                        FilterChip(
                                            selected = censorEffectMode == effect,
                                            onClick = { censorEffectMode = effect },
                                            label = { Text(effect.labelEs, style = MaterialTheme.typography.labelSmall) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = Color(0xFF0EA5E9),
                                                selectedLabelColor = Color.White,
                                                labelColor = Color(0xFFCBD5E1)
                                            )
                                        )
                                    }
                                    if (censorSubMode == CensorToolSubMode.FREEHAND_BRUSH) {
                                        Slider(
                                            value = censorBrushThicknessNorm,
                                            onValueChange = { censorBrushThicknessNorm = it },
                                            valueRange = 0.015f..0.10f,
                                            modifier = Modifier.weight(1f),
                                            colors = SliderDefaults.colors(
                                                thumbColor = studioAccent,
                                                activeTrackColor = studioAccent
                                            )
                                        )
                                    }
                                }
                            }

                            PhotoStudioTab.TEXT_WATERMARK -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = spec.watermarkText,
                                        onValueChange = { spec = spec.copy(watermarkText = it) },
                                        label = { Text("Texto / Marca de agua (1 dedo mueve, 2 dedos escalan/rotan)") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedBorderColor = studioAccent,
                                            unfocusedBorderColor = Color(0xFF475569),
                                            focusedLabelColor = Color(0xFFCBD5E1),
                                            unfocusedLabelColor = Color(0xFF94A3B8)
                                        )
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Paleta de colores rápidos para el texto
                                    val watermarkColors = listOf(
                                        0xFFFFFFFF.toInt(),
                                        0xFF000000.toInt(),
                                        0xFFF59E0B.toInt(),
                                        0xFF10B981.toInt(),
                                        0xFF38BDF8.toInt(),
                                        0xFFF43F5E.toInt()
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        watermarkColors.forEach { argb ->
                                            val isSelected = spec.watermarkColorArgb == argb
                                            Box(
                                                modifier = Modifier
                                                    .size(26.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(argb))
                                                    .border(
                                                        width = if (isSelected) 2.5.dp else 1.dp,
                                                        color = if (isSelected) studioAccent else Color(0xFF64748B),
                                                        shape = CircleShape
                                                    )
                                                    .clickable {
                                                        pushUndoState(spec.copy(watermarkColorArgb = argb))
                                                    }
                                            )
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.width(170.dp)
                                    ) {
                                        Text(
                                            text = "${spec.watermarkOpacityPercent}%",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White
                                        )
                                        Slider(
                                            value = spec.watermarkOpacityPercent.toFloat(),
                                            onValueChange = {
                                                spec = spec.copy(watermarkOpacityPercent = it.roundToInt().coerceIn(10, 100))
                                            },
                                            valueRange = 10f..100f,
                                            colors = SliderDefaults.colors(
                                                thumbColor = studioAccent,
                                                activeTrackColor = studioAccent
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        // Switch de Aplicar a todo el lote si hay múltiples imágenes
                        if (batchCount > 1) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1E293B))
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.photo_studio_apply_all_batch, batchCount),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFE2E8F0)
                                )
                                Switch(
                                    checked = spec.applyToAllBatch,
                                    onCheckedChange = { spec = spec.copy(applyToAllBatch = it) }
                                )
                            }
                        }
                    }
                }

                // 4. BARRA INFERIOR DE HERRAMIENTAS DEL ESTUDIO
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F1319))
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StudioBottomTabButton(
                        selected = activeTab == PhotoStudioTab.CROP_ROTATE,
                        icon = Icons.Default.Crop,
                        label = stringResource(R.string.photo_studio_tab_crop),
                        accent = studioAccent,
                        onClick = { activeTab = PhotoStudioTab.CROP_ROTATE }
                    )
                    StudioBottomTabButton(
                        selected = activeTab == PhotoStudioTab.SCANNER_4CORNERS,
                        icon = Icons.Default.DocumentScanner,
                        label = stringResource(R.string.photo_studio_tab_scanner),
                        accent = studioAccent,
                        onClick = { activeTab = PhotoStudioTab.SCANNER_4CORNERS }
                    )
                    StudioBottomTabButton(
                        selected = activeTab == PhotoStudioTab.LIVE_ADJUST,
                        icon = Icons.Default.Tune,
                        label = stringResource(R.string.photo_studio_tab_adjust),
                        accent = studioAccent,
                        onClick = { activeTab = PhotoStudioTab.LIVE_ADJUST }
                    )
                    StudioBottomTabButton(
                        selected = activeTab == PhotoStudioTab.FILTERS,
                        icon = Icons.Default.AutoFixHigh,
                        label = stringResource(R.string.photo_studio_tab_filters),
                        accent = studioAccent,
                        onClick = { activeTab = PhotoStudioTab.FILTERS }
                    )
                    StudioBottomTabButton(
                        selected = activeTab == PhotoStudioTab.CENSOR_TOUCH,
                        icon = Icons.Default.BlurOn,
                        label = stringResource(R.string.photo_studio_tab_censor),
                        accent = studioAccent,
                        onClick = { activeTab = PhotoStudioTab.CENSOR_TOUCH }
                    )
                    StudioBottomTabButton(
                        selected = activeTab == PhotoStudioTab.TEXT_WATERMARK,
                        icon = Icons.Default.TextFields,
                        label = stringResource(R.string.photo_studio_tab_text),
                        accent = studioAccent,
                        onClick = { activeTab = PhotoStudioTab.TEXT_WATERMARK }
                    )
                }
            }
        }
    }
}

@Composable
private fun StudioBottomTabButton(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) accent.copy(alpha = 0.22f) else Color.Transparent,
        border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, accent) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) Color.White else Color(0xFF94A3B8),
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) Color.White else Color(0xFF94A3B8)
            )
        }
    }
}

/**
 * Dial táctil horizontal graduado de enderezado (`-45°` a `+45°`) con auto-zoom sin bordes negros.
 */
@Composable
private fun TouchStraightenDial(
    degrees: Float,
    onDegreesChange: (Float) -> Unit,
    onResetDegrees: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = String.format(Locale.US, "%+.1f°", degrees),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (abs(degrees) > 0.1f) Color(0xFFF59E0B) else Color(0xFF94A3B8)
            )
            if (abs(degrees) > 0.1f) {
                Text(
                    text = "Restablecer 0°",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF38BDF8),
                    modifier = Modifier.clickable { onResetDegrees() }
                )
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .pointerInput(degrees) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val deltaDeg = -(dragAmount.x / size.width.coerceAtLeast(1)) * 55f
                        onDegreesChange((degrees + deltaDeg).coerceIn(-45f, 45f))
                    }
                }
        ) {
            val midX = size.width / 2f
            val cy = size.height / 2f
            val pxPerDegree = (size.width * 0.8f) / 90f

            for (deg in -45..45 step 3) {
                val tickX = midX + (deg - degrees) * pxPerDegree
                if (tickX in 8f..(size.width - 8f)) {
                    val isMajor = deg % 15 == 0
                    val tickH = if (isMajor) 14.dp.toPx() else 7.dp.toPx()
                    val distRatio = (1f - abs(tickX - midX) / (size.width / 2f)).coerceIn(0.15f, 1f)
                    drawLine(
                        color = if (deg == 0) Color(0xFFF59E0B) else Color.White.copy(alpha = distRatio),
                        start = Offset(tickX, cy - tickH / 2f),
                        end = Offset(tickX, cy + tickH / 2f),
                        strokeWidth = if (isMajor) 2.dp.toPx() else 1.dp.toPx()
                    )
                }
            }

            // Indicador central fijo
            drawLine(
                color = Color(0xFF6366F1),
                start = Offset(midX, 2.dp.toPx()),
                end = Offset(midX, size.height - 2.dp.toPx()),
                strokeWidth = 2.5.dp.toPx()
            )
        }
    }
}

private fun computeImageBoundsInCanvas(
    canvasW: Float,
    canvasH: Float,
    bmpW: Float,
    bmpH: Float
): Rect {
    if (canvasW <= 1f || canvasH <= 1f || bmpW <= 1f || bmpH <= 1f) {
        return Rect(0f, 0f, canvasW.coerceAtLeast(1f), canvasH.coerceAtLeast(1f))
    }
    val scale = min(canvasW / bmpW, canvasH / bmpH)
    val drawW = bmpW * scale
    val drawH = bmpH * scale
    val left = (canvasW - drawW) / 2f
    val top = (canvasH - drawH) / 2f
    return Rect(left, top, left + drawW, top + drawH)
}

private fun hitTestCropHandle(
    touch: Offset,
    imgRect: Rect,
    spec: ImageEditSpec
): CropDragHandle {
    val cropLeft = imgRect.left + spec.cropLeftNorm * imgRect.width
    val cropTop = imgRect.top + spec.cropTopNorm * imgRect.height
    val cropRight = imgRect.left + spec.cropRightNorm * imgRect.width
    val cropBottom = imgRect.top + spec.cropBottomNorm * imgRect.height
    val midX = (cropLeft + cropRight) / 2f
    val midY = (cropTop + cropBottom) / 2f
    val hitRadius = 38.0

    fun near(pt: Offset): Boolean =
        hypot((touch.x - pt.x).toDouble(), (touch.y - pt.y).toDouble()) <= hitRadius

    return when {
        near(Offset(cropLeft, cropTop)) -> CropDragHandle.TOP_LEFT
        near(Offset(cropRight, cropTop)) -> CropDragHandle.TOP_RIGHT
        near(Offset(cropLeft, cropBottom)) -> CropDragHandle.BOTTOM_LEFT
        near(Offset(cropRight, cropBottom)) -> CropDragHandle.BOTTOM_RIGHT
        near(Offset(midX, cropTop)) || (touch.x in cropLeft..cropRight && abs(touch.y - cropTop) <= 24f) -> CropDragHandle.TOP_EDGE
        near(Offset(midX, cropBottom)) || (touch.x in cropLeft..cropRight && abs(touch.y - cropBottom) <= 24f) -> CropDragHandle.BOTTOM_EDGE
        near(Offset(cropLeft, midY)) || (touch.y in cropTop..cropBottom && abs(touch.x - cropLeft) <= 24f) -> CropDragHandle.LEFT_EDGE
        near(Offset(cropRight, midY)) || (touch.y in cropTop..cropBottom && abs(touch.x - cropRight) <= 24f) -> CropDragHandle.RIGHT_EDGE
        touch.x in cropLeft..cropRight && touch.y in cropTop..cropBottom -> CropDragHandle.INSIDE_MOVE
        else -> CropDragHandle.NONE
    }
}

private fun updateCropWithHandleDrag(
    current: ImageEditSpec,
    handle: CropDragHandle,
    dxNorm: Float,
    dyNorm: Float,
    imageAspectRatio: Float
): ImageEditSpec {
    val minSpan = 0.08f
    var l = current.cropLeftNorm
    var t = current.cropTopNorm
    var r = current.cropRightNorm
    var b = current.cropBottomNorm

    when (handle) {
        CropDragHandle.TOP_LEFT -> {
            l = (l + dxNorm).coerceIn(0f, r - minSpan)
            t = (t + dyNorm).coerceIn(0f, b - minSpan)
        }
        CropDragHandle.TOP_RIGHT -> {
            r = (r + dxNorm).coerceIn(l + minSpan, 1f)
            t = (t + dyNorm).coerceIn(0f, b - minSpan)
        }
        CropDragHandle.BOTTOM_LEFT -> {
            l = (l + dxNorm).coerceIn(0f, r - minSpan)
            b = (b + dyNorm).coerceIn(t + minSpan, 1f)
        }
        CropDragHandle.BOTTOM_RIGHT -> {
            r = (r + dxNorm).coerceIn(l + minSpan, 1f)
            b = (b + dyNorm).coerceIn(t + minSpan, 1f)
        }
        CropDragHandle.TOP_EDGE -> {
            t = (t + dyNorm).coerceIn(0f, b - minSpan)
        }
        CropDragHandle.BOTTOM_EDGE -> {
            b = (b + dyNorm).coerceIn(t + minSpan, 1f)
        }
        CropDragHandle.LEFT_EDGE -> {
            l = (l + dxNorm).coerceIn(0f, r - minSpan)
        }
        CropDragHandle.RIGHT_EDGE -> {
            r = (r + dxNorm).coerceIn(l + minSpan, 1f)
        }
        CropDragHandle.INSIDE_MOVE -> {
            val w = r - l
            val h = b - t
            val nl = (l + dxNorm).coerceIn(0f, 1f - w)
            val nt = (t + dyNorm).coerceIn(0f, 1f - h)
            l = nl
            t = nt
            r = nl + w
            b = nt + h
        }
        CropDragHandle.NONE -> return current
    }

    val updated = current.copy(
        cropLeftNorm = l,
        cropTopNorm = t,
        cropRightNorm = r,
        cropBottomNorm = b
    )

    // Si hay una proporción fija seleccionada y no estamos solo moviendo el interior, ajustamos relación de aspecto
    val targetRatio = when (current.cropAspectRatioPreset) {
        CropAspectRatioPreset.FREE -> null
        CropAspectRatioPreset.ORIGINAL -> imageAspectRatio
        else -> current.cropAspectRatioPreset.ratioWidthToHeight
    }

    return if (targetRatio != null && handle != CropDragHandle.INSIDE_MOVE) {
        val normRatio = (targetRatio / imageAspectRatio.coerceAtLeast(0.1f)).coerceIn(0.15f, 6f)
        val curW = updated.cropRightNorm - updated.cropLeftNorm
        val desiredH = (curW / normRatio).coerceIn(minSpan, 1f)
        val centerY = ((updated.cropTopNorm + updated.cropBottomNorm) / 2f).coerceIn(desiredH / 2f, 1f - desiredH / 2f)
        updated.copy(
            cropTopNorm = (centerY - desiredH / 2f).coerceIn(0f, 0.92f),
            cropBottomNorm = (centerY + desiredH / 2f).coerceIn(0.08f, 1f)
        )
    } else {
        updated
    }
}

private fun applyAspectRatioPresetToCrop(
    current: ImageEditSpec,
    preset: CropAspectRatioPreset,
    imageAspectRatio: Float
): ImageEditSpec {
    if (preset == CropAspectRatioPreset.FREE) {
        return current.copy(cropAspectRatioPreset = preset)
    }
    if (preset == CropAspectRatioPreset.ORIGINAL) {
        return current.copy(
            cropAspectRatioPreset = preset,
            cropLeftNorm = 0f,
            cropTopNorm = 0f,
            cropRightNorm = 1f,
            cropBottomNorm = 1f
        )
    }
    val targetRatio = preset.ratioWidthToHeight ?: return current.copy(cropAspectRatioPreset = preset)
    val normRatio = (targetRatio / imageAspectRatio.coerceAtLeast(0.1f)).coerceIn(0.15f, 6f)
    val boxW: Float
    val boxH: Float
    if (normRatio >= 1f) {
        boxW = 0.88f
        boxH = (boxW / normRatio).coerceIn(0.12f, 0.88f)
    } else {
        boxH = 0.88f
        boxW = (boxH * normRatio).coerceIn(0.12f, 0.88f)
    }
    return current.copy(
        cropAspectRatioPreset = preset,
        cropLeftNorm = (0.5f - boxW / 2f).coerceIn(0f, 0.9f),
        cropTopNorm = (0.5f - boxH / 2f).coerceIn(0f, 0.9f),
        cropRightNorm = (0.5f + boxW / 2f).coerceIn(0.1f, 1f),
        cropBottomNorm = (0.5f + boxH / 2f).coerceIn(0.1f, 1f)
    )
}

private fun updateAdjustmentValue(
    spec: ImageEditSpec,
    control: AdjustmentControl,
    value: Float
): ImageEditSpec = when (control) {
    AdjustmentControl.EXPOSURE -> spec.copy(exposure = value.coerceIn(-100f, 100f))
    AdjustmentControl.BRIGHTNESS -> spec.copy(brightness = value.coerceIn(-100f, 100f))
    AdjustmentControl.CONTRAST -> spec.copy(contrast = value.coerceIn(-100f, 100f))
    AdjustmentControl.SATURATION -> spec.copy(saturation = value.coerceIn(-100f, 100f))
    AdjustmentControl.TEMPERATURE -> spec.copy(temperature = value.coerceIn(-100f, 100f))
    AdjustmentControl.SHARPNESS -> spec.copy(sharpness = value.coerceIn(0f, 100f))
    AdjustmentControl.VIGNETTE -> spec.copy(vignette = value.coerceIn(0f, 100f))
}

private fun normalizeAngle180(angle: Float): Float {
    var a = angle % 360f
    if (a > 180f) a -= 360f
    if (a < -180f) a += 360f
    return a
}
