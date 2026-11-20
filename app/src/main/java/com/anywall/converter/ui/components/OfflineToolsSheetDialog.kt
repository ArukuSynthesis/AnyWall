package com.anywall.converter.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.anywall.converter.core.ScopedStorageManager
import com.anywall.converter.core.UriFileResolver
import com.anywall.converter.data.engines.ForensicMetadataHelper
import com.anywall.converter.data.engines.ImageVisualTransformHelper
import com.anywall.converter.data.engines.LineDiffEngine
import com.anywall.converter.data.engines.QrCodeHelper
import com.anywall.converter.domain.model.ImageEditSpec
import com.anywall.converter.domain.model.InputFileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Suite de Herramientas Técnicas y de Privacidad 100% Offline (`OfflineToolsSheetDialog` - v0.3):
 * 1. Inspector Forense de Metadatos (EXIF/GPS, PDF, Multimedia) y Verificador de Hashes `SHA-256` / `MD5`.
 * 2. Generador y Escáner de Códigos QR Offline (`PNG`, `SVG` vectorial y decodificación desde imagen).
 * 3. Comparador Local de Diferencias Línea por Línea (`LineDiffEngine` LCS) con exportación `.diff.txt`.
 * 4. Comparador Visual «Antes / Después» con cortina deslizable interactiva y cálculo de ahorro en vivo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineToolsSheetDialog(
    initialTabIndex: Int = 0,
    loadedInputs: List<InputFileItem>,
    currentImageEditSpec: ImageEditSpec = ImageEditSpec(),
    currentQualityPercent: Int = 85,
    uriResolver: UriFileResolver,
    storageManager: ScopedStorageManager,
    forensicHelper: ForensicMetadataHelper,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(initialTabIndex.coerceIn(0, 3)) }
    val tabTitles = listOf(
        "Metadatos & SHA-256",
        "Códigos QR Offline",
        "Comparador Diff",
        "Antes / Después"
    )

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
                    Column {
                        TopAppBar(
                            title = {
                                Column {
                                    Text(
                                        text = "Herramientas Offline • AnyWall v0.3",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "100% Local • Sin red • SynthesizerEnd",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = onDismiss) {
                                    Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                                }
                            }
                        )
                        ScrollableTabRow(
                            selectedTabIndex = selectedTab,
                            edgePadding = 12.dp
                        ) {
                            tabTitles.forEachIndexed { idx, title ->
                                Tab(
                                    selected = selectedTab == idx,
                                    onClick = { selectedTab = idx },
                                    text = { Text(title, fontWeight = if (selectedTab == idx) FontWeight.Bold else FontWeight.Normal) }
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (selectedTab) {
                        0 -> ForensicMetadataAndHashTab(
                            loadedInputs = loadedInputs,
                            uriResolver = uriResolver,
                            forensicHelper = forensicHelper
                        )
                        1 -> OfflineQrToolTab(
                            uriResolver = uriResolver,
                            storageManager = storageManager
                        )
                        2 -> LocalDiffToolTab(
                            loadedInputs = loadedInputs,
                            uriResolver = uriResolver,
                            storageManager = storageManager
                        )
                        3 -> BeforeAfterComparisonTab(
                            loadedInputs = loadedInputs,
                            imageEditSpec = currentImageEditSpec,
                            qualityPercent = currentQualityPercent,
                            uriResolver = uriResolver
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ForensicMetadataAndHashTab(
    loadedInputs: List<InputFileItem>,
    uriResolver: UriFileResolver,
    forensicHelper: ForensicMetadataHelper
) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var targetItem by remember { mutableStateOf(loadedInputs.firstOrNull()) }
    var report by remember { mutableStateOf<ForensicMetadataHelper.FileForensicReport?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var expectedHashInput by remember { mutableStateOf("") }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            targetItem = uriResolver.resolveInputFile(uri)
        }
    }

    LaunchedEffect(targetItem?.uri) {
        val item = targetItem ?: return@LaunchedEffect
        isLoading = true
        report = withContext(Dispatchers.IO) {
            try {
                forensicHelper.inspectInputFile(item)
            } catch (_: Throwable) {
                null
            }
        }
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Inspector Forense & Verificador SHA-256",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            FilledTonalButton(onClick = { filePicker.launch(arrayOf("*/*")) }) {
                Icon(Icons.Filled.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Elegir archivo")
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (report == null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Selecciona cualquier archivo para analizar sus metadatos ocultos (EXIF, GPS, cámara, autor PDF, códecs) y calcular sus hashes criptográficos SHA-256 y MD5.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            val r = report!!
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${r.fileName} (${r.formatLabel} • ${InputFileItem.formatBytes(r.sizeBytes)})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("SHA-256:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text(
                                text = r.sha256Hex,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        IconButton(onClick = { clipboard.setText(AnnotatedString(r.sha256Hex)) }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copiar SHA-256", modifier = Modifier.size(18.dp))
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("MD5:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text(
                                text = r.md5Hex,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        IconButton(onClick = { clipboard.setText(AnnotatedString(r.md5Hex)) }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copiar MD5", modifier = Modifier.size(18.dp))
                        }
                    }

                    OutlinedTextField(
                        value = expectedHashInput,
                        onValueChange = { expectedHashInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Pegar hash esperado para verificar (SHA-256 o MD5)") }
                    )

                    if (expectedHashInput.isNotBlank()) {
                        val cleanExp = expectedHashInput.trim()
                        val matches = cleanExp.equals(r.sha256Hex, ignoreCase = true) ||
                            cleanExp.equals(r.md5Hex, ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (matches) Color(0xFF14532D) else MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = if (matches) "✓ Hash verificado: Coincide exactamente" else "✗ Alerta: El hash NO coincide",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (matches) Color(0xFFBBF7D0) else MaterialTheme.colorScheme.onErrorContainer
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
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (r.hasSensitiveMetadata) Icons.Filled.Warning else Icons.Filled.VerifiedUser,
                            contentDescription = null,
                            tint = if (r.hasSensitiveMetadata) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (r.hasSensitiveMetadata) {
                                "Se detectaron metadatos sensibles de privacidad (EXIF/GPS/Autor)"
                            } else {
                                "Archivo limpio de metadatos sensibles"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    HorizontalDivider()

                    r.entries.forEach { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${entry.key}:",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (entry.isSensitivePrivacy) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = entry.value,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OfflineQrToolTab(
    uriResolver: UriFileResolver,
    storageManager: ScopedStorageManager
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var qrText by remember { mutableStateOf("https://github.com/ArukuSynthesis/AnyWall") }
    var qrPreviewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var decodedQrResult by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val qrImageScannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val decoded = try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val bmp = BitmapFactory.decodeStream(stream)
                        val text = bmp?.let { QrCodeHelper.decodeQrFromBitmap(it) }
                        bmp?.recycle()
                        text
                    }
                } catch (_: Throwable) {
                    null
                }
                withContext(Dispatchers.Main) {
                    decodedQrResult = decoded ?: "No se detectó ningún código QR válido en la imagen seleccionada."
                }
            }
        }
    }

    LaunchedEffect(qrText) {
        if (qrText.isBlank()) {
            qrPreviewBitmap = null
        } else {
            qrPreviewBitmap = withContext(Dispatchers.Default) {
                try {
                    QrCodeHelper.generateQrBitmap(qrText, sizePx = 512)
                } catch (_: Throwable) {
                    null
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Generador de Código QR 100% Offline",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = qrText,
                    onValueChange = { qrText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Texto, enlace, Wi-Fi o contacto vCard") },
                    minLines = 2,
                    maxLines = 4
                )

                val bmp = qrPreviewBitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Código QR generado",
                        modifier = Modifier
                            .size(210.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .padding(8.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        enabled = qrPreviewBitmap != null,
                        onClick = {
                            val currentBmp = qrPreviewBitmap ?: return@Button
                            scope.launch(Dispatchers.IO) {
                                val tempPng = uriResolver.createTempWorkingFile("qr_", "png")
                                try {
                                    FileOutputStream(tempPng).use { fos ->
                                        currentBmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
                                    }
                                    val saved = storageManager.saveTempFileToDownloads(
                                        sourceFile = tempPng,
                                        desiredDisplayName = "AnyWall_QR_${System.currentTimeMillis() % 10000}.png",
                                        mimeType = "image/png"
                                    )
                                    withContext(Dispatchers.Main) {
                                        statusMessage = "Guardado en Downloads/AnyWall/${saved.displayName}"
                                    }
                                } finally {
                                    tempPng.delete()
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Filled.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Guardar PNG")
                    }

                    OutlinedButton(
                        enabled = qrText.isNotBlank(),
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                val svg = QrCodeHelper.generateQrSvg(qrText)
                                val tempSvg = uriResolver.createTempWorkingFile("qr_", "svg")
                                try {
                                    tempSvg.writeText(svg, Charsets.UTF_8)
                                    val saved = storageManager.saveTempFileToDownloads(
                                        sourceFile = tempSvg,
                                        desiredDisplayName = "AnyWall_QR_${System.currentTimeMillis() % 10000}.svg",
                                        mimeType = "image/svg+xml"
                                    )
                                    withContext(Dispatchers.Main) {
                                        statusMessage = "Guardado en Downloads/AnyWall/${saved.displayName}"
                                    }
                                } finally {
                                    tempSvg.delete()
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Filled.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Guardar SVG")
                    }
                }

                if (!statusMessage.isNullOrBlank()) {
                    Text(
                        text = statusMessage!!,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Escáner QR desde Imagen Local",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    FilledTonalButton(onClick = { qrImageScannerLauncher.launch(arrayOf("image/*")) }) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Escanear imagen")
                    }
                }

                if (!decodedQrResult.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = decodedQrResult!!,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                        IconButton(onClick = { clipboard.setText(AnnotatedString(decodedQrResult!!)) }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copiar resultado QR")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalDiffToolTab(
    loadedInputs: List<InputFileItem>,
    uriResolver: UriFileResolver,
    storageManager: ScopedStorageManager
) {
    val scope = rememberCoroutineScope()
    var leftText by remember { mutableStateOf("") }
    var rightText by remember { mutableStateOf("") }
    var leftName by remember { mutableStateOf("Archivo A") }
    var rightName by remember { mutableStateOf("Archivo B") }
    var savedMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(loadedInputs) {
        if (loadedInputs.size >= 2 && leftText.isEmpty() && rightText.isEmpty()) {
            withContext(Dispatchers.IO) {
                try {
                    leftName = loadedInputs[0].displayName
                    rightName = loadedInputs[1].displayName
                    leftText = uriResolver.openInputStream(loadedInputs[0].uri).use {
                        it.bufferedReader(Charsets.UTF_8).readText().take(20_000)
                    }
                    rightText = uriResolver.openInputStream(loadedInputs[1].uri).use {
                        it.bufferedReader(Charsets.UTF_8).readText().take(20_000)
                    }
                } catch (_: Throwable) {
                }
            }
        }
    }

    val diffReport = remember(leftText, rightText) {
        LineDiffEngine.computeDiff(leftText, rightText)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Comparador Local de Diferencias (Diff LCS)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = leftText,
                onValueChange = { leftText = it },
                modifier = Modifier.weight(1f),
                label = { Text(leftName) },
                minLines = 4,
                maxLines = 6
            )
            OutlinedTextField(
                value = rightText,
                onValueChange = { rightText = it },
                modifier = Modifier.weight(1f),
                label = { Text(rightName) },
                minLines = 4,
                maxLines = 6
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "+${diffReport.addedCount} añadidas • -${diffReport.removedCount} eliminadas • =${diffReport.unchangedCount} iguales",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            OutlinedButton(
                enabled = leftText.isNotEmpty() || rightText.isNotEmpty(),
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        val tempDiff = uriResolver.createTempWorkingFile("diff_", "txt")
                        try {
                            tempDiff.writeText(diffReport.toUnifiedDiffText(leftName, rightName), Charsets.UTF_8)
                            val saved = storageManager.saveTempFileToDownloads(
                                sourceFile = tempDiff,
                                desiredDisplayName = "AnyWall_Diff_${System.currentTimeMillis() % 10000}.diff.txt",
                                mimeType = "text/plain"
                            )
                            withContext(Dispatchers.Main) {
                                savedMsg = "Reporte guardado: ${saved.displayName}"
                            }
                        } finally {
                            tempDiff.delete()
                        }
                    }
                }
            ) {
                Icon(Icons.Filled.Difference, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Exportar .diff")
            }
        }

        if (!savedMsg.isNullOrBlank()) {
            Text(
                text = savedMsg!!,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                diffReport.lines.take(200).forEach { line ->
                    val bgColor = when (line.type) {
                        LineDiffEngine.DiffType.ADDED -> Color(0xFF14532D).copy(alpha = 0.25f)
                        LineDiffEngine.DiffType.REMOVED -> Color(0xFF7F1D1D).copy(alpha = 0.25f)
                        LineDiffEngine.DiffType.EQUAL -> Color.Transparent
                    }
                    Text(
                        text = "${line.type.prefixSymbol} ${line.text}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bgColor)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun BeforeAfterComparisonTab(
    loadedInputs: List<InputFileItem>,
    imageEditSpec: ImageEditSpec,
    qualityPercent: Int,
    uriResolver: UriFileResolver
) {
    val context = LocalContext.current
    val firstImage = loadedInputs.firstOrNull { it.format.category == com.anywall.converter.domain.model.FormatCategory.IMAGE }
    var splitFraction by remember { mutableFloatStateOf(0.5f) }
    var beforeBmp by remember { mutableStateOf<Bitmap?>(null) }
    var afterBmp by remember { mutableStateOf<Bitmap?>(null) }
    var beforeBytesCount by remember { mutableStateOf(0L) }
    var afterBytesCount by remember { mutableStateOf(0L) }

    LaunchedEffect(firstImage?.uri, imageEditSpec, qualityPercent) {
        if (firstImage == null) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val rawBytes = context.contentResolver.openInputStream(firstImage.uri)?.use { it.readBytes() }
                    ?: return@withContext
                beforeBytesCount = rawBytes.size.toLong()
                val raw = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size) ?: return@withContext
                val maxSide = max(raw.width, raw.height).coerceAtLeast(1)
                val scaled = if (maxSide > 800) {
                    val ratio = 800f / maxSide.toFloat()
                    Bitmap.createScaledBitmap(
                        raw,
                        (raw.width * ratio).roundToInt().coerceAtLeast(1),
                        (raw.height * ratio).roundToInt().coerceAtLeast(1),
                        true
                    )
                } else {
                    raw
                }
                val edited = ImageVisualTransformHelper.applyVisualEditSpec(scaled, imageEditSpec)
                val compressedBytes = ByteArrayOutputStream().use { baos ->
                    edited.compress(Bitmap.CompressFormat.WEBP, qualityPercent.coerceIn(15, 100), baos)
                    baos.toByteArray()
                }
                afterBytesCount = compressedBytes.size.toLong()
                val decodedAfter = BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.size) ?: edited
                beforeBmp = scaled
                afterBmp = decodedAfter
            } catch (_: Throwable) {
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Comparador Visual «Antes / Después»",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        if (firstImage == null || beforeBmp == null || afterBmp == null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Carga al menos una imagen en el lote para comparar visualmente el archivo original contra la versión optimizada/editada deslizando la cortina interactiva.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            val savingPct = if (beforeBytesCount > 0L) {
                (((beforeBytesCount - afterBytesCount) * 100L) / beforeBytesCount).toInt()
            } else {
                0
            }
            Text(
                text = "Original: ${InputFileItem.formatBytes(beforeBytesCount)} → Optimizada (${qualityPercent}%): ${InputFileItem.formatBytes(afterBytesCount)} (${if (savingPct >= 0) "-$savingPct%" else "+${-savingPct}%"})",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(290.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                // Imagen Después (fondo completo)
                Image(
                    bitmap = afterBmp!!.asImageBitmap(),
                    contentDescription = "Después",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )

                // Imagen Antes (recortada hasta splitFraction)
                Image(
                    bitmap = beforeBmp!!.asImageBitmap(),
                    contentDescription = "Antes",
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            clipRect(right = size.width * splitFraction) {
                                this@drawWithContent.drawContent()
                            }
                        },
                    contentScale = ContentScale.Fit
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("← ANTES (Original)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("Cortina: ${(splitFraction * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                Text("DESPUÉS (Editada) →", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }

            Slider(
                value = splitFraction,
                onValueChange = { splitFraction = it },
                valueRange = 0.05f..0.95f
            )
        }
    }
}
