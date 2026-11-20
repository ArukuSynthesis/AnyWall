package com.anywall.converter.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.anywall.converter.data.engines.ImageVisualTransformHelper
import com.anywall.converter.domain.model.ImageAspectCanvasMode
import com.anywall.converter.domain.model.ImageEditSpec
import com.anywall.converter.domain.model.ImageFilterPreset
import com.anywall.converter.domain.model.InputFileItem
import com.anywall.converter.domain.model.NormalizedRedactionBox
import com.anywall.converter.domain.model.SupportedFormat
import com.caverock.androidsvg.SVG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Editor Visual Interactivo de Imágenes en Vista Previa (v0.3):
 * - Previsualización en vivo de las transformaciones sobre la primera imagen del lote.
 * - Recorte (Crop) por proporciones (`Libre`, `1:1`, `4:3`, `16:9`, `A4`, `80% Central`) y deslizadores finos.
 * - Volteo Horizontal y Vertical.
 * - Filtros rápidos (`Original`, `Escala de grises`, `Escáner B/N Alto Contraste`, `Invertir`).
 * - Lienzo de relación de aspecto con fondo Difuminado (*Blur*) o Blanco.
 * - Censura / Pixelado rápido por zonas y Marca de agua de texto con opacidad regulable.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ImageVisualEditorDialog(
    imageInputs: List<InputFileItem>,
    initialEditSpec: ImageEditSpec,
    onApplyEdits: (ImageEditSpec) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sampleItem = imageInputs.firstOrNull()

    var cropLeft by remember { mutableFloatStateOf(initialEditSpec.cropLeftNorm) }
    var cropTop by remember { mutableFloatStateOf(initialEditSpec.cropTopNorm) }
    var cropRight by remember { mutableFloatStateOf(initialEditSpec.cropRightNorm) }
    var cropBottom by remember { mutableFloatStateOf(initialEditSpec.cropBottomNorm) }
    var flipH by remember { mutableStateOf(initialEditSpec.flipHorizontal) }
    var flipV by remember { mutableStateOf(initialEditSpec.flipVertical) }
    var filterPreset by remember { mutableStateOf(initialEditSpec.filterPreset) }
    var aspectMode by remember { mutableStateOf(initialEditSpec.aspectCanvasMode) }
    var watermarkText by remember { mutableStateOf(initialEditSpec.watermarkText) }
    var watermarkOpacity by remember { mutableIntStateOf(initialEditSpec.watermarkOpacityPercent) }
    val redactionBoxes = remember {
        mutableStateListOf<NormalizedRedactionBox>().apply {
            addAll(initialEditSpec.redactionBoxes)
        }
    }

    var newCensorY by remember { mutableFloatStateOf(0.42f) }
    var newCensorPixelated by remember { mutableStateOf(true) }

    var basePreviewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var transformedPreviewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoadingBase by remember { mutableStateOf(true) }

    val currentSpec = ImageEditSpec(
        cropLeftNorm = cropLeft,
        cropTopNorm = cropTop,
        cropRightNorm = cropRight,
        cropBottomNorm = cropBottom,
        flipHorizontal = flipH,
        flipVertical = flipV,
        filterPreset = filterPreset,
        aspectCanvasMode = aspectMode,
        watermarkText = watermarkText,
        watermarkOpacityPercent = watermarkOpacity,
        redactionBoxes = redactionBoxes.toList()
    )

    LaunchedEffect(sampleItem?.uri) {
        isLoadingBase = true
        basePreviewBitmap = withContext(Dispatchers.IO) {
            if (sampleItem == null) return@withContext null
            try {
                if (sampleItem.format == SupportedFormat.SVG) {
                    context.contentResolver.openInputStream(sampleItem.uri)?.use { stream ->
                        val svg = SVG.getFromInputStream(stream)
                        val bmp = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(bmp)
                        canvas.drawColor(android.graphics.Color.WHITE)
                        svg.renderToCanvas(canvas)
                        bmp
                    }
                } else {
                    val bytes = context.contentResolver.openInputStream(sampleItem.uri)?.use { it.readBytes() }
                        ?: return@withContext null
                    val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
                    val maxSide = max(raw.width, raw.height).coerceAtLeast(1)
                    if (maxSide > 720) {
                        val ratio = 720f / maxSide.toFloat()
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
        isLoadingBase = false
    }

    LaunchedEffect(basePreviewBitmap, currentSpec) {
        val src = basePreviewBitmap ?: return@LaunchedEffect
        transformedPreviewBitmap = withContext(Dispatchers.Default) {
            try {
                ImageVisualTransformHelper.applyVisualEditSpec(src, currentSpec)
            } catch (_: Throwable) {
                src
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = "Editor Visual de Imágenes",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Se aplicará a ${imageInputs.size} ${if (imageInputs.size == 1) "imagen" else "imágenes"} del lote",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                            }
                        },
                        actions = {
                            TextButton(
                                onClick = {
                                    cropLeft = 0f
                                    cropTop = 0f
                                    cropRight = 1f
                                    cropBottom = 1f
                                    flipH = false
                                    flipV = false
                                    filterPreset = ImageFilterPreset.NONE
                                    aspectMode = ImageAspectCanvasMode.ORIGINAL
                                    watermarkText = ""
                                    watermarkOpacity = 45
                                    redactionBoxes.clear()
                                }
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Restablecer")
                            }
                            Button(
                                onClick = { onApplyEdits(currentSpec) },
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Aplicar")
                            }
                        }
                    )
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. Vista Previa en Vivo
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                isLoadingBase -> CircularProgressIndicator()
                                transformedPreviewBitmap != null -> {
                                    Image(
                                        bitmap = transformedPreviewBitmap!!.asImageBitmap(),
                                        contentDescription = "Vista previa de imagen editada",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                                else -> Text("Vista previa no disponible")
                            }
                        }
                    }

                    // 2. Volteo y Recorte Rápido (Crop)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Volteo y Recorte (Crop)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = flipH,
                                    onClick = { flipH = !flipH },
                                    label = { Text("Voltear Horizontal") },
                                    leadingIcon = { Icon(Icons.Filled.Flip, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                )
                                FilterChip(
                                    selected = flipV,
                                    onClick = { flipV = !flipV },
                                    label = { Text("Voltear Vertical") }
                                )
                            }

                            Text(
                                text = "Proporciones rápidas de recorte:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                OutlinedButton(onClick = {
                                    cropLeft = 0f; cropTop = 0f; cropRight = 1f; cropBottom = 1f
                                }) { Text("100% Original") }
                                OutlinedButton(onClick = {
                                    cropLeft = 0.1f; cropTop = 0.1f; cropRight = 0.9f; cropBottom = 0.9f
                                }) { Text("Centro 80%") }
                                OutlinedButton(onClick = {
                                    cropLeft = 0.12f; cropTop = 0.12f; cropRight = 0.88f; cropBottom = 0.88f
                                }) { Text("Recorte Bordes") }
                                OutlinedButton(onClick = {
                                    cropLeft = 0f; cropTop = 0.15f; cropRight = 1f; cropBottom = 0.85f
                                }) { Text("Panorámico 16:9") }
                            }

                            Text(
                                text = "Ajuste fino de márgenes: Izq ${(cropLeft * 100).toInt()}% • Arr ${(cropTop * 100).toInt()}% • Der ${(cropRight * 100).toInt()}% • Abj ${(cropBottom * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Izquierda / Derecha", style = MaterialTheme.typography.labelSmall)
                                    Slider(
                                        value = cropLeft,
                                        onValueChange = { cropLeft = it.coerceAtMost(cropRight - 0.1f) },
                                        valueRange = 0f..0.45f
                                    )
                                    Slider(
                                        value = cropRight,
                                        onValueChange = { cropRight = it.coerceAtLeast(cropLeft + 0.1f) },
                                        valueRange = 0.55f..1f
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Arriba / Abajo", style = MaterialTheme.typography.labelSmall)
                                    Slider(
                                        value = cropTop,
                                        onValueChange = { cropTop = it.coerceAtMost(cropBottom - 0.1f) },
                                        valueRange = 0f..0.45f
                                    )
                                    Slider(
                                        value = cropBottom,
                                        onValueChange = { cropBottom = it.coerceAtLeast(cropTop + 0.1f) },
                                        valueRange = 0.55f..1f
                                    )
                                }
                            }
                        }
                    }

                    // 3. Filtros Rápidos y Fondo Blur de Relación de Aspecto
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Filtros Locales y Modo Escáner",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                ImageFilterPreset.entries.forEach { preset ->
                                    FilterChip(
                                        selected = filterPreset == preset,
                                        onClick = { filterPreset = preset },
                                        label = { Text(preset.labelEs) }
                                    )
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            Text(
                                text = "Encuadre con Fondo Difuminado (Blur) o Sólido",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                ImageAspectCanvasMode.entries.forEach { mode ->
                                    FilterChip(
                                        selected = aspectMode == mode,
                                        onClick = { aspectMode = mode },
                                        label = { Text(mode.labelEs) }
                                    )
                                }
                            }
                        }
                    }

                    // 4. Censura / Pixelado Rápido y Marca de Agua
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Censura / Pixelado de Privacidad (${redactionBoxes.size} zonas)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = newCensorPixelated,
                                    onClick = { newCensorPixelated = true },
                                    label = { Text("Pixelado en bloques") }
                                )
                                FilterChip(
                                    selected = !newCensorPixelated,
                                    onClick = { newCensorPixelated = false },
                                    label = { Text("Franja Negra Opaca") }
                                )
                            }
                            Text(
                                text = "Altura vertical de la franja a añadir: ${(newCensorY * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall
                            )
                            Slider(
                                value = newCensorY,
                                onValueChange = { newCensorY = it },
                                valueRange = 0.08f..0.85f
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        redactionBoxes.add(
                                            NormalizedRedactionBox(
                                                left = 0.12f,
                                                top = newCensorY,
                                                right = 0.88f,
                                                bottom = (newCensorY + 0.14f).coerceAtMost(0.98f),
                                                isPixelated = newCensorPixelated
                                            )
                                        )
                                    }
                                ) {
                                    Icon(Icons.Filled.VisibilityOff, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Añadir franja de censura")
                                }
                                if (redactionBoxes.isNotEmpty()) {
                                    TextButton(onClick = { redactionBoxes.removeAt(redactionBoxes.lastIndex) }) {
                                        Text("Deshacer última")
                                    }
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            Text(
                                text = "Marca de Agua de Texto Local",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            OutlinedTextField(
                                value = watermarkText,
                                onValueChange = { watermarkText = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("Texto de marca de agua (opcional)") },
                                placeholder = { Text("Ej. © SynthesizerEnd • Uso Interno") }
                            )
                            if (watermarkText.isNotBlank()) {
                                Text(
                                    text = "Opacidad de marca de agua: $watermarkOpacity%",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Slider(
                                    value = watermarkOpacity.toFloat(),
                                    onValueChange = { watermarkOpacity = it.roundToInt() },
                                    valueRange = 15f..100f
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
