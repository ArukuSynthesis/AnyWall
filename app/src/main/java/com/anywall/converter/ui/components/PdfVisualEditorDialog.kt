package com.anywall.converter.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SwapVert
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.anywall.converter.core.LocalThumbnailLoader
import com.anywall.converter.domain.model.InputFileItem
import com.anywall.converter.domain.model.NormalizedRedactionBox
import com.anywall.converter.domain.model.PdfPageEditItem
import com.anywall.converter.domain.model.SupportedFormat
import java.util.Collections
import java.util.UUID

/**
 * Modificador Visual de PDF en Vista Previa (100% Offline - v0.3):
 * - Renderiza cada página de uno o varios PDFs cargados en el lote usando `PdfRenderer`.
 * - Permite reordenar páginas (`←` / `→`), rotar páginas (`+90°`), excluir/incluir páginas,
 *   duplicar páginas, insertar páginas separadoras en blanco, estampar firma manuscrita por página,
 *   aplicar franjas de censura/redacción y abrir vista previa ampliada en alta resolución.
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
        PdfSinglePageZoomDialog(
            item = zoomedItem,
            positionNumber = currentZoomIdx + 1,
            totalItems = pageItems.size,
            thumbnailLoader = thumbnailLoader,
            onRotate90 = {
                val updated = pageItems.toMutableList()
                updated[currentZoomIdx] = zoomedItem.copy(
                    rotationDegrees = (zoomedItem.normalizedRotation + 90) % 360
                )
                pageItems = updated
            },
            onToggleIncluded = {
                val updated = pageItems.toMutableList()
                updated[currentZoomIdx] = zoomedItem.copy(included = !zoomedItem.included)
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
                                text = "Editor Visual de Páginas PDF",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "$activePagesCount de ${pageItems.size} páginas activas • Toca para ampliar",
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
                                text = "Renderizando vista previa local de páginas PDF…",
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
                                showSourceFileName = showSourceFileName,
                                thumbnailLoader = thumbnailLoader,
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
                                onCycleRedaction = {
                                    val mutable = pageItems.toMutableList()
                                    val nextBoxes = when (item.redactionBoxes.size) {
                                        0 -> listOf(NormalizedRedactionBox(0.10f, 0.20f, 0.90f, 0.34f))
                                        1 -> item.redactionBoxes + NormalizedRedactionBox(0.10f, 0.55f, 0.90f, 0.69f)
                                        else -> emptyList()
                                    }
                                    mutable[index] = item.copy(redactionBoxes = nextBoxes)
                                    pageItems = mutable
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
                                },
                                onOpenZoom = {
                                    if (!item.isBlankSeparator) {
                                        zoomedItemIndex = index
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
    showSourceFileName: Boolean,
    thumbnailLoader: LocalThumbnailLoader?,
    onToggleIncluded: () -> Unit,
    onRotate90: () -> Unit,
    onDuplicatePage: () -> Unit,
    onToggleSignature: () -> Unit,
    onCycleRedaction: () -> Unit,
    onMoveEarlier: () -> Unit,
    onMoveLater: () -> Unit,
    onOpenZoom: () -> Unit
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
            width = if (item.included) 1.5.dp else 1.dp,
            color = if (item.included) {
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
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (item.included) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                ) {
                    Text(
                        text = if (item.isBlankSeparator) {
                            "#${index + 1} • En blanco"
                        } else {
                            "#${index + 1} • Pág. ${item.displayPageNumber}"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (item.included) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
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
                    .clickable(onClick = onOpenZoom),
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

                if (item.redactionBoxes.isNotEmpty()) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Black.copy(alpha = 0.85f)
                    ) {
                        Text(
                            text = "Censura (${item.redactionBoxes.size})",
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

            // Chips rápidos de Firma y Censura por página
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
                    selected = item.redactionBoxes.isNotEmpty(),
                    onClick = onCycleRedaction,
                    modifier = Modifier.weight(1f),
                    label = {
                        Text(
                            if (item.redactionBoxes.isEmpty()) "Censura" else "Cens (${item.redactionBoxes.size})",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.VisibilityOff, contentDescription = null, modifier = Modifier.size(12.dp))
                    }
                )
            }

            // Barra de acciones rápidas por página: Mover <- | Rotar +90° | Zoom | Mover ->
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
                    onClick = onOpenZoom,
                    enabled = !item.isBlankSeparator,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ZoomIn,
                        contentDescription = "Ampliar página",
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

@Composable
private fun PdfSinglePageZoomDialog(
    item: PdfPageEditItem,
    positionNumber: Int,
    totalItems: Int,
    thumbnailLoader: LocalThumbnailLoader?,
    onRotate90: () -> Unit,
    onToggleIncluded: () -> Unit,
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
            targetSizePx = 960,
            rotationDegrees = item.normalizedRotation
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Posición #$positionNumber de $totalItems (Pág. original ${item.displayPageNumber})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${item.sourceDisplayName} • Rotación: ${item.normalizedRotation}°",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Cerrar vista ampliada")
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = highResBitmap
                    if (bmp != null && !bmp.isRecycled) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Página ${item.displayPageNumber}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                        )
                    } else {
                        CircularProgressIndicator()
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onPreviousPage,
                        enabled = positionNumber > 1
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Anterior")
                    }

                    FilledTonalButton(onClick = onRotate90) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.RotateRight,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Rotar +90°")
                    }

                    OutlinedButton(onClick = onToggleIncluded) {
                        Text(if (item.included) "Excluir página" else "Incluir página")
                    }

                    IconButton(
                        onClick = onNextPage,
                        enabled = positionNumber < totalItems
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Siguiente")
                    }
                }
            }
        }
    }
}
