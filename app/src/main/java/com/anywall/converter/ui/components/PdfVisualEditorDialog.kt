package com.anywall.converter.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.anywall.converter.core.LocalThumbnailLoader
import com.anywall.converter.domain.model.CensorEffectMode
import com.anywall.converter.domain.model.InputFileItem
import com.anywall.converter.domain.model.NormalizedRedactionBox
import com.anywall.converter.domain.model.PdfAnnotationType
import com.anywall.converter.domain.model.PdfPageAnnotation
import com.anywall.converter.domain.model.PdfPageEditItem
import com.anywall.converter.domain.model.PerspectiveQuadSpec
import com.anywall.converter.domain.model.SupportedFormat
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

private enum class PdfTouchToolMode {
    ZOOM_PAN,
    SIGNATURE_DRAG,
    DRAW_REDACTION,
    HIGHLIGHTER,
    TEXT_STAMP,
    SCANNER_4CORNERS
}

/**
 * Editor PDF Táctil y Anotaciones en Vista Previa (`PdfVisualEditorDialog` - v0.4):
 * - Reordenamiento táctil *Drag & Drop* de páginas en la cuadrícula.
 * - Estudio táctil de página individual con:
 *   - Pinch-to-Zoom + Pan.
 *   - Firma manuscrita arrastrable y redimensionable libremente (`signatureXNorm`, `signatureYNorm`, `signatureWidthNorm`).
 *   - Cajas de censura dibujadas directamente con el dedo (`redactionBoxes`).
 *   - Anotaciones rápidas: Texto libre, Sello de fecha y Resaltador amarillo translúcido (`PDExtendedGraphicsState`).
 *   - Escáner / Perspectiva de 4 esquinas con lupa 2.5× (`PerspectiveQuadSpec`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfVisualEditorDialog(
    pdfInputFiles: List<InputFileItem>,
    initialEdits: List<PdfPageEditItem>,
    thumbnailLoader: LocalThumbnailLoader?,
    onOpenSignaturePad: (() -> Unit)? = null,
    onApplyEdits: (List<PdfPageEditItem>) -> Unit,
    onResetEdits: () -> Unit,
    onDismiss: () -> Unit
) {
    var isLoadingPages by remember { mutableStateOf(initialEdits.isEmpty()) }
    var pageItems by remember { mutableStateOf(initialEdits) }
    var zoomedItemIndex by remember { mutableStateOf<Int?>(null) }
    var draggingGridIndex by remember { mutableIntStateOf(-1) }
    var accumulatedDragX by remember { mutableFloatStateOf(0f) }
    var accumulatedDragY by remember { mutableFloatStateOf(0f) }

    suspend fun buildDefaultPageItems(): List<PdfPageEditItem> {
        val built = mutableListOf<PdfPageEditItem>()
        for (fileItem in pdfInputFiles.filter { it.format == SupportedFormat.PDF }) {
            val count = (thumbnailLoader?.getPdfPageCount(fileItem.uri) ?: 0).coerceAtLeast(1)
            for (pageIdx in 0 until count) {
                built.add(
                    PdfPageEditItem(
                        id = "${fileItem.uri}#$pageIdx",
                        sourceUriString = fileItem.uri.toString(),
                        sourceDisplayName = fileItem.displayName,
                        originalPageIndex = pageIdx,
                        totalPagesInSource = count,
                        rotationDegrees = 0,
                        included = true
                    )
                )
            }
        }
        return built
    }

    LaunchedEffect(pdfInputFiles, initialEdits) {
        if (initialEdits.isNotEmpty()) {
            pageItems = initialEdits
            isLoadingPages = false
        } else {
            isLoadingPages = true
            pageItems = buildDefaultPageItems()
            isLoadingPages = false
        }
    }

    val activePagesCount = remember(pageItems) { pageItems.count { it.included } }
    val showSourceFileName = remember(pdfInputFiles) { pdfInputFiles.count { it.format == SupportedFormat.PDF } > 1 }

    val currentZoomIdx = zoomedItemIndex
    if (currentZoomIdx != null && currentZoomIdx in pageItems.indices) {
        val zoomedItem = pageItems[currentZoomIdx]
        PdfSinglePageTouchStudioDialog(
            item = zoomedItem,
            positionNumber = currentZoomIdx + 1,
            totalItems = pageItems.size,
            thumbnailLoader = thumbnailLoader,
            onOpenSignaturePad = onOpenSignaturePad,
            onUpdateItem = { updatedPage ->
                val updated = pageItems.toMutableList()
                updated[currentZoomIdx] = updatedPage
                pageItems = updated
            },
            onPreviousPage = {
                if (currentZoomIdx > 0) zoomedItemIndex = currentZoomIdx - 1
            },
            onNextPage = {
                if (currentZoomIdx < pageItems.size - 1) zoomedItemIndex = currentZoomIdx + 1
            },
            onDismiss = { zoomedItemIndex = null }
        )
    }

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
                                text = "Editor Visual Táctil de PDF",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "$activePagesCount de ${pageItems.size} páginas • Arrastra para reordenar o toca para anotar",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar editor visual"
                            )
                        }
                    }
                )
            },
            bottomBar = {
                Surface(
                    tonalElevation = 8.dp,
                    shadowElevation = 10.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                onResetEdits()
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Restablecer")
                        }

                        Button(
                            onClick = {
                                onApplyEdits(pageItems)
                                onDismiss()
                            },
                            enabled = activePagesCount > 0 && !isLoadingPages,
                            modifier = Modifier.weight(1.4f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Aplicar ($activePagesCount págs.)",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Barra de herramientas rápidas por lote de páginas
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = {
                            val blankItem = PdfPageEditItem(
                                id = "blank_${UUID.randomUUID()}",
                                sourceUriString = "",
                                sourceDisplayName = "Separador en blanco",
                                originalPageIndex = 0,
                                totalPagesInSource = 1,
                                rotationDegrees = 0,
                                included = true,
                                isBlankSeparator = true
                            )
                            pageItems = pageItems + blankItem
                        },
                        label = { Text("+ Pág. en blanco") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.AddBox,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )

                    if (onOpenSignaturePad != null) {
                        AssistChip(
                            onClick = onOpenSignaturePad,
                            label = { Text("Dibujar Firma") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Draw,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }

                    AssistChip(
                        onClick = {
                            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                            pageItems = pageItems.map { page ->
                                if (page.included) {
                                    val stamp = PdfPageAnnotation(
                                        type = PdfAnnotationType.DATE_STAMP,
                                        xNorm = 0.70f,
                                        yNorm = 0.08f,
                                        endXNorm = 0.94f,
                                        endYNorm = 0.13f,
                                        text = today,
                                        colorArgb = 0xFF1E3A8A.toInt(),
                                        fontSizePt = 12f
                                    )
                                    page.copy(annotations = page.annotations + stamp)
                                } else {
                                    page
                                }
                            }
                        },
                        label = { Text("+ Sello Fecha") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )

                    AssistChip(
                        onClick = {
                            val allIncluded = pageItems.all { it.included }
                            pageItems = pageItems.map { it.copy(included = !allIncluded) }
                        },
                        label = {
                            Text(
                                if (pageItems.all { it.included }) "Desmarcar todas"
                                else "Incluir todas"
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.SelectAll,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )

                    AssistChip(
                        onClick = {
                            pageItems = pageItems.map { it.copy(included = !it.included) }
                        },
                        label = { Text("Invertir selección") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.FlipCameraAndroid,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )

                    AssistChip(
                        onClick = {
                            pageItems = pageItems.map { item ->
                                if (item.included) {
                                    item.copy(rotationDegrees = (item.normalizedRotation + 90) % 360)
                                } else {
                                    item
                                }
                            }
                        },
                        label = { Text("Rotar activas +90°") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.RotateRight,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )

                    AssistChip(
                        onClick = {
                            pageItems = pageItems.reversed()
                        },
                        label = { Text("Invertir orden") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.SwapVert,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }

                if (isLoadingPages) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = "Renderizando vista previa de páginas PDF…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(
                            items = pageItems,
                            key = { _, item -> item.id }
                        ) { index, item ->
                            PdfPageEditorGridCard(
                                item = item,
                                index = index,
                                totalCount = pageItems.size,
                                isBeingDragged = draggingGridIndex == index,
                                showSourceFileName = showSourceFileName,
                                thumbnailLoader = thumbnailLoader,
                                onDragReorderDelta = { dx, dy ->
                                    if (draggingGridIndex != index) {
                                        draggingGridIndex = index
                                        accumulatedDragX = 0f
                                        accumulatedDragY = 0f
                                    }
                                    accumulatedDragX += dx
                                    accumulatedDragY += dy
                                    val stepX = 165f
                                    val stepY = 230f
                                    var targetIndex = index
                                    if (accumulatedDragX > stepX && index + 1 < pageItems.size) {
                                        targetIndex = index + 1
                                        accumulatedDragX = 0f
                                    } else if (accumulatedDragX < -stepX && index - 1 >= 0) {
                                        targetIndex = index - 1
                                        accumulatedDragX = 0f
                                    } else if (accumulatedDragY > stepY && index + 2 < pageItems.size) {
                                        targetIndex = index + 2
                                        accumulatedDragY = 0f
                                    } else if (accumulatedDragY < -stepY && index - 2 >= 0) {
                                        targetIndex = index - 2
                                        accumulatedDragY = 0f
                                    }
                                    if (targetIndex != index) {
                                        val mutable = pageItems.toMutableList()
                                        val moved = mutable.removeAt(index)
                                        mutable.add(targetIndex, moved)
                                        pageItems = mutable
                                        draggingGridIndex = targetIndex
                                    }
                                },
                                onDragReorderEnd = {
                                    draggingGridIndex = -1
                                    accumulatedDragX = 0f
                                    accumulatedDragY = 0f
                                },
                                onToggleIncluded = {
                                    val mutable = pageItems.toMutableList()
                                    mutable[index] = item.copy(included = !item.included)
                                    pageItems = mutable
                                },
                                onRotate90 = {
                                    val mutable = pageItems.toMutableList()
                                    mutable[index] = item.copy(
                                        rotationDegrees = (item.normalizedRotation + 90) % 360
                                    )
                                    pageItems = mutable
                                },
                                onDuplicatePage = {
                                    val mutable = pageItems.toMutableList()
                                    mutable.add(
                                        index + 1,
                                        item.copy(id = "${item.id}_dup_${UUID.randomUUID()}")
                                    )
                                    pageItems = mutable
                                },
                                onToggleSignature = {
                                    val mutable = pageItems.toMutableList()
                                    mutable[index] = item.copy(hasSignatureStamp = !item.hasSignatureStamp)
                                    pageItems = mutable
                                },
                                onOpenTouchStudio = {
                                    if (!item.isBlankSeparator) {
                                        zoomedItemIndex = index
                                    }
                                },
                                onMoveEarlier = {
                                    if (index > 0) {
                                        val mutable = pageItems.toMutableList()
                                        Collections.swap(mutable, index, index - 1)
                                        pageItems = mutable
                                    }
                                },
                                onMoveLater = {
                                    if (index < pageItems.size - 1) {
                                        val mutable = pageItems.toMutableList()
                                        Collections.swap(mutable, index, index + 1)
                                        pageItems = mutable
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPageEditorGridCard(
    item: PdfPageEditItem,
    index: Int,
    totalCount: Int,
    isBeingDragged: Boolean,
    showSourceFileName: Boolean,
    thumbnailLoader: LocalThumbnailLoader?,
    onDragReorderDelta: (dx: Float, dy: Float) -> Unit,
    onDragReorderEnd: () -> Unit,
    onToggleIncluded: () -> Unit,
    onRotate90: () -> Unit,
    onDuplicatePage: () -> Unit,
    onToggleSignature: () -> Unit,
    onOpenTouchStudio: () -> Unit,
    onMoveEarlier: () -> Unit,
    onMoveLater: () -> Unit
) {
    val uri = remember(item.sourceUriString) {
        if (item.sourceUriString.isBlank()) null else Uri.parse(item.sourceUriString)
    }
    val previewBitmap by produceState<Bitmap?>(
        initialValue = null,
        key1 = uri,
        key2 = item.originalPageIndex,
        key3 = item.normalizedRotation
    ) {
        value = if (uri != null && !item.isBlankSeparator) {
            thumbnailLoader?.loadPdfPagePreview(
                uri = uri,
                pageIndex = item.originalPageIndex,
                targetSizePx = 280,
                rotationDegrees = item.normalizedRotation
            )
        } else {
            null
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (item.included) 1f else 0.52f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = if (isBeingDragged) 2.5.dp else if (item.included) 1.5.dp else 1.dp,
            color = if (isBeingDragged) {
                MaterialTheme.colorScheme.tertiary
            } else if (item.included) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tirador táctil Drag & Drop de página
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (item.included) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    modifier = Modifier.pointerInput(index) {
                        detectDragGestures(
                            onDragEnd = onDragReorderEnd,
                            onDragCancel = onDragReorderEnd,
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDragReorderDelta(dragAmount.x, dragAmount.y)
                            }
                        )
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.DragIndicator,
                            contentDescription = "Arrastrar página",
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = if (item.isBlankSeparator) {
                                "#${index + 1} • Blanco"
                            } else {
                                "#${index + 1} • Pág. ${item.displayPageNumber}"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (item.included) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onDuplicatePage,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Duplicar página",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onToggleIncluded,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (item.included) {
                                Icons.Default.CheckCircle
                            } else {
                                Icons.Default.RadioButtonUnchecked
                            },
                            contentDescription = if (item.included) "Excluir página" else "Incluir página",
                            tint = if (item.included) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            if (showSourceFileName) {
                Text(
                    text = item.sourceDisplayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.74f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White)
                    .clickable(onClick = onOpenTouchStudio),
                contentAlignment = Alignment.Center
            ) {
                val bmp = previewBitmap
                if (item.isBlankSeparator) {
                    Text(
                        text = "Página en Blanco",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.DarkGray
                    )
                } else if (bmp != null && !bmp.isRecycled) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Vista previa página ${item.displayPageNumber}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(36.dp)
                    )
                }

                if (item.redactionBoxes.isNotEmpty() || item.annotations.isNotEmpty() || item.perspectiveQuad.isModified) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Black.copy(alpha = 0.85f)
                    ) {
                        val badgeParts = mutableListOf<String>()
                        if (item.redactionBoxes.isNotEmpty()) badgeParts.add("Cens:${item.redactionBoxes.size}")
                        if (item.annotations.isNotEmpty()) badgeParts.add("Anot:${item.annotations.size}")
                        if (item.perspectiveQuad.isModified) badgeParts.add("4-Esq")
                        Text(
                            text = badgeParts.joinToString(" • "),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                if (item.hasSignatureStamp) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f)
                    ) {
                        Text(
                            text = "✓ Firma",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                if (item.normalizedRotation != 0) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.92f)
                    ) {
                        Text(
                            text = "${item.normalizedRotation}°",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                if (!item.included) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f)
                    ) {
                        Text(
                            text = "EXCLUIDA",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Chips rápidos de Firma y Estudio Táctil de Página
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilterChip(
                    selected = item.hasSignatureStamp,
                    onClick = onToggleSignature,
                    modifier = Modifier.weight(1f),
                    label = { Text("Firma", style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = {
                        Icon(Icons.Default.Draw, contentDescription = null, modifier = Modifier.size(12.dp))
                    }
                )
                FilterChip(
                    selected = item.redactionBoxes.isNotEmpty() || item.annotations.isNotEmpty(),
                    onClick = onOpenTouchStudio,
                    modifier = Modifier.weight(1f),
                    label = {
                        Text(
                            text = "Anotar / Cens",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.BorderColor, contentDescription = null, modifier = Modifier.size(12.dp))
                    }
                )
            }

            // Barra de acciones rápidas por página: Mover <- | Rotar +90° | Estudio Táctil | Mover ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onMoveEarlier,
                    enabled = index > 0,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Mover antes",
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onRotate90,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.RotateRight,
                        contentDescription = "Rotar página 90 grados",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onOpenTouchStudio,
                    enabled = !item.isBlankSeparator,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomIn,
                        contentDescription = "Abrir estudio táctil de página",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onMoveLater,
                    enabled = index < totalCount - 1,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Mover después",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Estudio Táctil de Página Individual PDF (v0.4):
 * - Pinch-to-Zoom + Pan.
 * - Posicionar y redimensionar libremente la firma manuscrita arrastrándola sobre la página.
 * - Dibujar cajas de censura con el dedo (`redactionBoxes`).
 * - Añadir anotaciones: Texto, Sello de fecha y Resaltador amarillo translúcido.
 * - Escáner de 4 esquinas con lupa 2.5× (`PerspectiveQuadSpec`).
 */
@Composable
private fun PdfSinglePageTouchStudioDialog(
    item: PdfPageEditItem,
    positionNumber: Int,
    totalItems: Int,
    thumbnailLoader: LocalThumbnailLoader?,
    onOpenSignaturePad: (() -> Unit)?,
    onUpdateItem: (PdfPageEditItem) -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onDismiss: () -> Unit
) {
    val uri = remember(item.sourceUriString) { Uri.parse(item.sourceUriString) }
    val highResBitmap by produceState<Bitmap?>(
        initialValue = null,
        key1 = uri,
        key2 = item.originalPageIndex,
        key3 = item.normalizedRotation
    ) {
        value = thumbnailLoader?.loadPdfPagePreview(
            uri = uri,
            pageIndex = item.originalPageIndex,
            targetSizePx = 1080,
            rotationDegrees = item.normalizedRotation
        )
    }

    var toolMode by remember { mutableStateOf(PdfTouchToolMode.ZOOM_PAN) }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    // Estado para dibujar cajas de censura o trazos de resaltador con el dedo
    var dragStartNorm by remember { mutableStateOf<Offset?>(null) }
    var dragCurrentNorm by remember { mutableStateOf<Offset?>(null) }

    // Estado para texto de anotación
    var annotationDraftText by remember { mutableStateOf("Revisado") }

    // Estado para escáner de 4 esquinas con lupa 2.5x
    var activeScannerCorner by remember { mutableIntStateOf(-1) }
    var activeLoupeNorm by remember { mutableStateOf<Offset?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF0B0D10),
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Página #$positionNumber de $totalItems (Original ${item.displayPageNumber})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = item.sourceDisplayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF94A3B8),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row {
                        IconButton(
                            onClick = {
                                onUpdateItem(
                                    item.copy(
                                        redactionBoxes = emptyList(),
                                        annotations = emptyList(),
                                        perspectiveQuad = PerspectiveQuadSpec()
                                    )
                                )
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Limpiar anotaciones de página",
                                tint = Color(0xFFF87171)
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                        }
                    }
                }

                // Selector de herramienta táctil sobre la página PDF
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = toolMode == PdfTouchToolMode.ZOOM_PAN,
                        onClick = { toolMode = PdfTouchToolMode.ZOOM_PAN },
                        label = { Text("Zoom / Pan") },
                        leadingIcon = { Icon(Icons.Default.PanTool, contentDescription = null, modifier = Modifier.size(15.dp)) }
                    )
                    FilterChip(
                        selected = toolMode == PdfTouchToolMode.SIGNATURE_DRAG,
                        onClick = {
                            if (!item.hasSignatureStamp) {
                                onUpdateItem(item.copy(hasSignatureStamp = true))
                            }
                            toolMode = PdfTouchToolMode.SIGNATURE_DRAG
                        },
                        label = { Text("Mover Firma") },
                        leadingIcon = { Icon(Icons.Default.Draw, contentDescription = null, modifier = Modifier.size(15.dp)) }
                    )
                    FilterChip(
                        selected = toolMode == PdfTouchToolMode.DRAW_REDACTION,
                        onClick = {
                            zoomScale = 1f
                            panOffset = Offset.Zero
                            toolMode = PdfTouchToolMode.DRAW_REDACTION
                        },
                        label = { Text("Censurar (${item.redactionBoxes.size})") },
                        leadingIcon = { Icon(Icons.Default.VisibilityOff, contentDescription = null, modifier = Modifier.size(15.dp)) }
                    )
                    FilterChip(
                        selected = toolMode == PdfTouchToolMode.HIGHLIGHTER,
                        onClick = {
                            zoomScale = 1f
                            panOffset = Offset.Zero
                            toolMode = PdfTouchToolMode.HIGHLIGHTER
                        },
                        label = { Text("Resaltador") },
                        leadingIcon = { Icon(Icons.Default.BorderColor, contentDescription = null, modifier = Modifier.size(15.dp)) }
                    )
                    FilterChip(
                        selected = toolMode == PdfTouchToolMode.TEXT_STAMP,
                        onClick = {
                            zoomScale = 1f
                            panOffset = Offset.Zero
                            toolMode = PdfTouchToolMode.TEXT_STAMP
                        },
                        label = { Text("Texto / Fecha") },
                        leadingIcon = { Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(15.dp)) }
                    )
                    FilterChip(
                        selected = toolMode == PdfTouchToolMode.SCANNER_4CORNERS,
                        onClick = {
                            zoomScale = 1f
                            panOffset = Offset.Zero
                            toolMode = PdfTouchToolMode.SCANNER_4CORNERS
                        },
                        label = { Text("Escáner 4-Esq") },
                        leadingIcon = { Icon(Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(15.dp)) }
                    )
                }

                // Lienzo interactivo de la página PDF
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF141820)),
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = highResBitmap
                    if (bmp != null && !bmp.isRecycled) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp)
                                // Modo Zoom / Pan con 2 dedos
                                .pointerInput(toolMode) {
                                    if (toolMode != PdfTouchToolMode.ZOOM_PAN) return@pointerInput
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        val nextZ = (zoomScale * zoom).coerceIn(1f, 4.5f)
                                        zoomScale = nextZ
                                        panOffset = if (nextZ <= 1.02f) {
                                            Offset.Zero
                                        } else {
                                            Offset(
                                                x = (panOffset.x + pan.x).coerceIn(-700f, 700f),
                                                y = (panOffset.y + pan.y).coerceIn(-700f, 700f)
                                            )
                                        }
                                    }
                                }
                                // Modo Mover / Redimensionar Firma manuscrita
                                .pointerInput(toolMode, item, bmp) {
                                    if (toolMode != PdfTouchToolMode.SIGNATURE_DRAG) return@pointerInput
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val pageRect = computePdfPageRect(
                                            size.width.toFloat(),
                                            size.height.toFloat(),
                                            bmp.width.toFloat(),
                                            bmp.height.toFloat()
                                        )
                                        val dx = dragAmount.x / pageRect.width.coerceAtLeast(1f)
                                        val dy = dragAmount.y / pageRect.height.coerceAtLeast(1f)
                                        val maxX = (1f - item.signatureWidthNorm).coerceAtLeast(0.05f)
                                        onUpdateItem(
                                            item.copy(
                                                hasSignatureStamp = true,
                                                signatureXNorm = (item.signatureXNorm + dx).coerceIn(0.02f, maxX),
                                                signatureYNorm = (item.signatureYNorm + dy).coerceIn(0.04f, 0.93f)
                                            )
                                        )
                                    }
                                }
                                // Modo Dibujar Caja de Censura o Resaltador amarillo con el dedo
                                .pointerInput(toolMode, item, bmp) {
                                    if (toolMode != PdfTouchToolMode.DRAW_REDACTION && toolMode != PdfTouchToolMode.HIGHLIGHTER) {
                                        return@pointerInput
                                    }
                                    detectDragGestures(
                                        onDragStart = { touch ->
                                            val pageRect = computePdfPageRect(
                                                size.width.toFloat(),
                                                size.height.toFloat(),
                                                bmp.width.toFloat(),
                                                bmp.height.toFloat()
                                            )
                                            val nx = ((touch.x - pageRect.left) / pageRect.width.coerceAtLeast(1f)).coerceIn(0f, 1f)
                                            val ny = ((touch.y - pageRect.top) / pageRect.height.coerceAtLeast(1f)).coerceIn(0f, 1f)
                                            dragStartNorm = Offset(nx, ny)
                                            dragCurrentNorm = Offset(nx, ny)
                                        },
                                        onDragEnd = {
                                            val s = dragStartNorm
                                            val e = dragCurrentNorm
                                            dragStartNorm = null
                                            dragCurrentNorm = null
                                            if (s != null && e != null) {
                                                val l = minOf(s.x, e.x).coerceIn(0f, 0.96f)
                                                val t = minOf(s.y, e.y).coerceIn(0f, 0.96f)
                                                val r = maxOf(s.x, e.x).coerceIn(l + 0.03f, 1f)
                                                val b = maxOf(s.y, e.y).coerceIn(t + 0.02f, 1f)
                                                if (toolMode == PdfTouchToolMode.DRAW_REDACTION) {
                                                    val box = NormalizedRedactionBox(
                                                        left = l,
                                                        top = t,
                                                        right = r,
                                                        bottom = b,
                                                        isPixelated = false,
                                                        effectMode = CensorEffectMode.SOLID_BLACK
                                                    )
                                                    onUpdateItem(item.copy(redactionBoxes = item.redactionBoxes + box))
                                                } else {
                                                    val highlight = PdfPageAnnotation(
                                                        type = PdfAnnotationType.HIGHLIGHTER,
                                                        xNorm = l,
                                                        yNorm = t,
                                                        endXNorm = r,
                                                        endYNorm = b,
                                                        text = "",
                                                        colorArgb = 0xFFFFEB3B.toInt()
                                                    )
                                                    onUpdateItem(item.copy(annotations = item.annotations + highlight))
                                                }
                                            }
                                        },
                                        onDragCancel = {
                                            dragStartNorm = null
                                            dragCurrentNorm = null
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            val pageRect = computePdfPageRect(
                                                size.width.toFloat(),
                                                size.height.toFloat(),
                                                bmp.width.toFloat(),
                                                bmp.height.toFloat()
                                            )
                                            val nx = ((change.position.x - pageRect.left) / pageRect.width.coerceAtLeast(1f)).coerceIn(0f, 1f)
                                            val ny = ((change.position.y - pageRect.top) / pageRect.height.coerceAtLeast(1f)).coerceIn(0f, 1f)
                                            dragCurrentNorm = Offset(nx, ny)
                                        }
                                    )
                                }
                                // Modo Texto / Sello de Fecha arrastrable
                                .pointerInput(toolMode, item, annotationDraftText, bmp) {
                                    if (toolMode != PdfTouchToolMode.TEXT_STAMP) return@pointerInput
                                    detectDragGestures(
                                        onDragStart = { touch ->
                                            val pageRect = computePdfPageRect(
                                                size.width.toFloat(),
                                                size.height.toFloat(),
                                                bmp.width.toFloat(),
                                                bmp.height.toFloat()
                                            )
                                            val nx = ((touch.x - pageRect.left) / pageRect.width.coerceAtLeast(1f)).coerceIn(0.05f, 0.90f)
                                            val ny = ((touch.y - pageRect.top) / pageRect.height.coerceAtLeast(1f)).coerceIn(0.05f, 0.94f)
                                            val list = item.annotations.toMutableList()
                                            val textIdx = list.indexOfLast { it.type != PdfAnnotationType.HIGHLIGHTER }
                                            if (textIdx >= 0) {
                                                list[textIdx] = list[textIdx].copy(xNorm = nx, yNorm = ny)
                                            } else {
                                                list.add(
                                                    PdfPageAnnotation(
                                                        type = PdfAnnotationType.TEXT,
                                                        xNorm = nx,
                                                        yNorm = ny,
                                                        text = annotationDraftText.ifBlank { "Nota" },
                                                        colorArgb = 0xFF1E3A8A.toInt(),
                                                        fontSizePt = 14f
                                                    )
                                                )
                                            }
                                            onUpdateItem(item.copy(annotations = list))
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            val pageRect = computePdfPageRect(
                                                size.width.toFloat(),
                                                size.height.toFloat(),
                                                bmp.width.toFloat(),
                                                bmp.height.toFloat()
                                            )
                                            val nx = ((change.position.x - pageRect.left) / pageRect.width.coerceAtLeast(1f)).coerceIn(0.05f, 0.90f)
                                            val ny = ((change.position.y - pageRect.top) / pageRect.height.coerceAtLeast(1f)).coerceIn(0.05f, 0.94f)
                                            val list = item.annotations.toMutableList()
                                            val textIdx = list.indexOfLast { it.type != PdfAnnotationType.HIGHLIGHTER }
                                            if (textIdx >= 0) {
                                                list[textIdx] = list[textIdx].copy(xNorm = nx, yNorm = ny)
                                                onUpdateItem(item.copy(annotations = list))
                                            }
                                        }
                                    )
                                }
                                // Modo Escáner de 4 Esquinas con Lupa 2.5x
                                .pointerInput(toolMode, item, bmp) {
                                    if (toolMode != PdfTouchToolMode.SCANNER_4CORNERS) return@pointerInput
                                    detectDragGestures(
                                        onDragStart = { touch ->
                                            val pageRect = computePdfPageRect(
                                                size.width.toFloat(),
                                                size.height.toFloat(),
                                                bmp.width.toFloat(),
                                                bmp.height.toFloat()
                                            )
                                            val q = item.perspectiveQuad
                                            val pts = listOf(
                                                Offset(pageRect.left + q.tlX * pageRect.width, pageRect.top + q.tlY * pageRect.height),
                                                Offset(pageRect.left + q.trX * pageRect.width, pageRect.top + q.trY * pageRect.height),
                                                Offset(pageRect.left + q.brX * pageRect.width, pageRect.top + q.brY * pageRect.height),
                                                Offset(pageRect.left + q.blX * pageRect.width, pageRect.top + q.blY * pageRect.height)
                                            )
                                            activeScannerCorner = pts.indexOfFirst {
                                                hypot((it.x - touch.x).toDouble(), (it.y - touch.y).toDouble()) <= 54.0
                                            }
                                        },
                                        onDragEnd = {
                                            activeScannerCorner = -1
                                            activeLoupeNorm = null
                                        },
                                        onDragCancel = {
                                            activeScannerCorner = -1
                                            activeLoupeNorm = null
                                        },
                                        onDrag = { change, _ ->
                                            if (activeScannerCorner !in 0..3) return@detectDragGestures
                                            change.consume()
                                            val pageRect = computePdfPageRect(
                                                size.width.toFloat(),
                                                size.height.toFloat(),
                                                bmp.width.toFloat(),
                                                bmp.height.toFloat()
                                            )
                                            val nx = ((change.position.x - pageRect.left) / pageRect.width.coerceAtLeast(1f)).coerceIn(0f, 1f)
                                            val ny = ((change.position.y - pageRect.top) / pageRect.height.coerceAtLeast(1f)).coerceIn(0f, 1f)
                                            activeLoupeNorm = Offset(nx, ny)
                                            val q = item.perspectiveQuad
                                            val nextQuad = when (activeScannerCorner) {
                                                0 -> q.copy(tlX = nx, tlY = ny)
                                                1 -> q.copy(trX = nx, trY = ny)
                                                2 -> q.copy(brX = nx, brY = ny)
                                                else -> q.copy(blX = nx, blY = ny)
                                            }
                                            onUpdateItem(item.copy(perspectiveQuad = nextQuad))
                                        }
                                    )
                                }
                        ) {
                            val pageRect = computePdfPageRect(
                                size.width,
                                size.height,
                                bmp.width.toFloat(),
                                bmp.height.toFloat()
                            )

                            drawIntoCanvas { canvas ->
                                canvas.save()
                                if (toolMode == PdfTouchToolMode.ZOOM_PAN) {
                                    canvas.translate(panOffset.x, panOffset.y)
                                    canvas.translate(size.width / 2f, size.height / 2f)
                                    canvas.scale(zoomScale, zoomScale)
                                    canvas.translate(-(size.width / 2f), -(size.height / 2f))
                                }
                                val dst = android.graphics.RectF(pageRect.left, pageRect.top, pageRect.right, pageRect.bottom)
                                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                                canvas.nativeCanvas.drawBitmap(bmp, null, dst, paint)
                                canvas.restore()
                            }

                            // 1. Dibujar Cajas de Censura existentes
                            item.redactionBoxes.forEach { box ->
                                val bx = pageRect.left + box.clampedLeft * pageRect.width
                                val by = pageRect.top + box.clampedTop * pageRect.height
                                val bw = (box.clampedRight - box.clampedLeft) * pageRect.width
                                val bh = (box.clampedBottom - box.clampedTop) * pageRect.height
                                drawRect(
                                    color = Color.Black,
                                    topLeft = Offset(bx, by),
                                    size = Size(bw, bh)
                                )
                            }

                            // 2. Dibujar Anotaciones existentes (Resaltador amarillo translúcido, Texto, Sello de fecha)
                            item.annotations.forEach { ann ->
                                when (ann.type) {
                                    PdfAnnotationType.HIGHLIGHTER -> {
                                        val hx = pageRect.left + minOf(ann.xNorm, ann.endXNorm) * pageRect.width
                                        val hy = pageRect.top + minOf(ann.yNorm, ann.endYNorm) * pageRect.height
                                        val hw = abs(ann.endXNorm - ann.xNorm).coerceAtLeast(0.03f) * pageRect.width
                                        val hh = abs(ann.endYNorm - ann.yNorm).coerceAtLeast(0.025f) * pageRect.height
                                        drawRect(
                                            color = Color(0x66FFEB3B),
                                            topLeft = Offset(hx, hy),
                                            size = Size(hw, hh)
                                        )
                                    }
                                    PdfAnnotationType.TEXT, PdfAnnotationType.DATE_STAMP -> {
                                        val tx = pageRect.left + ann.xNorm * pageRect.width
                                        val ty = pageRect.top + ann.yNorm * pageRect.height
                                        drawIntoCanvas { canvas ->
                                            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                                color = ann.colorArgb
                                                textSize = 32f
                                                typeface = android.graphics.Typeface.create(
                                                    android.graphics.Typeface.DEFAULT,
                                                    android.graphics.Typeface.BOLD
                                                )
                                            }
                                            val label = if (ann.type == PdfAnnotationType.DATE_STAMP) {
                                                "[${ann.text}]"
                                            } else {
                                                ann.text
                                            }
                                            canvas.nativeCanvas.drawText(label, tx, ty, tp)
                                        }
                                    }
                                }
                            }

                            // 3. Dibujar Caja/Resaltador en progreso mientras el usuario arrastra el dedo
                            val sNorm = dragStartNorm
                            val eNorm = dragCurrentNorm
                            if (sNorm != null && eNorm != null) {
                                val lx = pageRect.left + minOf(sNorm.x, eNorm.x) * pageRect.width
                                val ty = pageRect.top + minOf(sNorm.y, eNorm.y) * pageRect.height
                                val rw = abs(eNorm.x - sNorm.x) * pageRect.width
                                val bh = abs(eNorm.y - sNorm.y) * pageRect.height
                                drawRect(
                                    color = if (toolMode == PdfTouchToolMode.HIGHLIGHTER) Color(0x77FFEB3B) else Color(0xBB000000),
                                    topLeft = Offset(lx, ty),
                                    size = Size(rw, bh)
                                )
                            }

                            // 4. Dibujar Marco Interactivo de Firma Manuscrita
                            if (item.hasSignatureStamp) {
                                val sx = pageRect.left + item.signatureXNorm * pageRect.width
                                val sy = pageRect.top + item.signatureYNorm * pageRect.height
                                val sw = (item.signatureWidthNorm * pageRect.width).coerceAtLeast(48f)
                                val sh = sw * 0.42f
                                drawRect(
                                    color = Color(0x266366F1),
                                    topLeft = Offset(sx, sy),
                                    size = Size(sw, sh)
                                )
                                drawRect(
                                    color = Color(0xFF6366F1),
                                    topLeft = Offset(sx, sy),
                                    size = Size(sw, sh),
                                    style = Stroke(width = 2.dp.toPx())
                                )
                                drawIntoCanvas { canvas ->
                                    val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                        color = android.graphics.Color.rgb(67, 56, 202)
                                        textSize = 24f
                                        typeface = android.graphics.Typeface.create(
                                            android.graphics.Typeface.SERIF,
                                            android.graphics.Typeface.BOLD_ITALIC
                                        )
                                    }
                                    canvas.nativeCanvas.drawText("✍ Firma Manuscrita", sx + 10f, sy + sh * 0.62f, sp)
                                }
                            }

                            // 5. Dibujar Polígono de Escáner de 4 Esquinas + Lupa 2.5x
                            if (toolMode == PdfTouchToolMode.SCANNER_4CORNERS) {
                                val q = item.perspectiveQuad
                                val tl = Offset(pageRect.left + q.tlX * pageRect.width, pageRect.top + q.tlY * pageRect.height)
                                val tr = Offset(pageRect.left + q.trX * pageRect.width, pageRect.top + q.trY * pageRect.height)
                                val br = Offset(pageRect.left + q.brX * pageRect.width, pageRect.top + q.brY * pageRect.height)
                                val bl = Offset(pageRect.left + q.blX * pageRect.width, pageRect.top + q.blY * pageRect.height)
                                val path = Path().apply {
                                    moveTo(tl.x, tl.y)
                                    lineTo(tr.x, tr.y)
                                    lineTo(br.x, br.y)
                                    lineTo(bl.x, bl.y)
                                    close()
                                }
                                drawPath(path, Color(0x2E10B981))
                                drawPath(path, Color(0xFF10B981), style = Stroke(width = 2.5.dp.toPx()))
                                listOf(tl, tr, br, bl).forEachIndexed { idx, pt ->
                                    drawCircle(
                                        color = if (idx == activeScannerCorner) Color(0xFFF59E0B) else Color(0xFF10B981),
                                        radius = 10.dp.toPx(),
                                        center = pt
                                    )
                                    drawCircle(Color.White, 4.dp.toPx(), pt)
                                }

                                val loupe = activeLoupeNorm
                                if (loupe != null) {
                                    val lr = 52.dp.toPx()
                                    val lc = if (loupe.x < 0.45f && loupe.y < 0.45f) {
                                        Offset(size.width - lr - 16.dp.toPx(), lr + 16.dp.toPx())
                                    } else {
                                        Offset(lr + 16.dp.toPx(), lr + 16.dp.toPx())
                                    }
                                    drawIntoCanvas { canvas ->
                                        val m = Matrix().apply {
                                            postTranslate(-loupe.x * bmp.width, -loupe.y * bmp.height)
                                            postScale(2.5f, 2.5f)
                                            postTranslate(lc.x, lc.y)
                                        }
                                        val shader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                                            setLocalMatrix(m)
                                        }
                                        val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
                                        canvas.nativeCanvas.drawCircle(lc.x, lc.y, lr, sp)
                                    }
                                    drawCircle(Color(0xFF10B981), lr, lc, style = Stroke(width = 3.dp.toPx()))
                                }
                            }
                        }
                    } else {
                        CircularProgressIndicator()
                    }
                }

                // Controles contextuales inferiores según la herramienta activa
                when (toolMode) {
                    PdfTouchToolMode.SIGNATURE_DRAG -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Tamaño Firma:", style = MaterialTheme.typography.labelMedium, color = Color.White)
                            Slider(
                                value = item.signatureWidthNorm,
                                onValueChange = { w ->
                                    onUpdateItem(item.copy(signatureWidthNorm = w.coerceIn(0.10f, 0.70f)))
                                },
                                valueRange = 0.10f..0.70f,
                                modifier = Modifier.weight(1f)
                            )
                            if (onOpenSignaturePad != null) {
                                OutlinedButton(onClick = onOpenSignaturePad) {
                                    Text("Redibujar", color = Color.White)
                                }
                            }
                        }
                    }

                    PdfTouchToolMode.TEXT_STAMP -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = annotationDraftText,
                                onValueChange = { txt ->
                                    annotationDraftText = txt
                                    val list = item.annotations.toMutableList()
                                    val lastIdx = list.indexOfLast { it.type == PdfAnnotationType.TEXT }
                                    if (lastIdx >= 0) {
                                        list[lastIdx] = list[lastIdx].copy(text = txt)
                                        onUpdateItem(item.copy(annotations = list))
                                    }
                                },
                                singleLine = true,
                                label = { Text("Texto de anotación (arrastra en la página)") },
                                modifier = Modifier.weight(1f)
                            )
                            FilledTonalButton(
                                onClick = {
                                    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                                    val stamp = PdfPageAnnotation(
                                        type = PdfAnnotationType.DATE_STAMP,
                                        xNorm = 0.68f,
                                        yNorm = 0.08f,
                                        text = today,
                                        colorArgb = 0xFF1E3A8A.toInt(),
                                        fontSizePt = 12f
                                    )
                                    onUpdateItem(item.copy(annotations = item.annotations + stamp))
                                }
                            ) {
                                Text("+ Fecha")
                            }
                        }
                    }

                    else -> Unit
                }

                // Barra de navegación inferior entre páginas
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onPreviousPage,
                        enabled = positionNumber > 1
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Anterior", tint = Color.White)
                    }

                    FilledTonalButton(
                        onClick = {
                            onUpdateItem(item.copy(rotationDegrees = (item.normalizedRotation + 90) % 360))
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.RotateRight,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Rotar +90°")
                    }

                    OutlinedButton(
                        onClick = {
                            onUpdateItem(item.copy(included = !item.included))
                        }
                    ) {
                        Text(if (item.included) "Excluir página" else "Incluir página", color = Color.White)
                    }

                    IconButton(
                        onClick = onNextPage,
                        enabled = positionNumber < totalItems
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Siguiente", tint = Color.White)
                    }
                }
            }
        }
    }
}

private fun computePdfPageRect(
    canvasW: Float,
    canvasH: Float,
    bmpW: Float,
    bmpH: Float
): Rect {
    val scale = min(canvasW / bmpW.coerceAtLeast(1f), canvasH / bmpH.coerceAtLeast(1f))
    val dw = bmpW * scale
    val dh = bmpH * scale
    val l = (canvasW - dw) / 2f
    val t = (canvasH - dh) / 2f
    return Rect(l, t, l + dw, t + dh)
}
