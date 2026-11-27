package com.anywall.converter.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
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
import com.anywall.converter.data.engines.DuplicateFilesDetector
import com.anywall.converter.data.engines.ForensicMetadataHelper
import com.anywall.converter.data.engines.ImageVisualTransformHelper
import com.anywall.converter.data.engines.LineDiffEngine
import com.anywall.converter.data.engines.QrCodeHelper
import com.anywall.converter.domain.model.FormatCategory
import com.anywall.converter.domain.model.ImageEditSpec
import com.anywall.converter.domain.model.InputFileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Suite de Herramientas Técnicas (`OfflineToolsSheetDialog` - v0.6):
 * 1. Inspector Forense de Metadatos (EXIF/GPS, PDF, Multimedia, Paleta de 6 Colores Dominantes `#HEX`) y Verificador de Hashes `SHA-256` / `MD5`.
 * 2. Generador y Escáner de Códigos QR y Códigos de Barras (`PNG`, `SVG` vectorial, Asistente Wi-Fi y decodificación desde imagen).
 * 3. Comparador de Diferencias Línea por Línea (`LineDiffEngine` LCS) con exportación `.diff.txt`.
 * 4. Comparador Visual «Antes / Después» con cortina deslizable interactiva y cálculo de ahorro en vivo.
 * 5. Detector y Limpiador de Duplicados en 2 fases (tamaño exacto + `SHA-256` en streaming).
 */
@Composable
fun ToolsUtilityInlinePanel(
    selectedTab: Int,
    loadedInputs: List<InputFileItem>,
    currentImageEditSpec: ImageEditSpec = ImageEditSpec(),
    currentQualityPercent: Int = 85,
    uriResolver: UriFileResolver,
    storageManager: ScopedStorageManager,
    forensicHelper: ForensicMetadataHelper,
    onDeduplicateQueue: ((List<InputFileItem>) -> Unit)? = null
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
        4 -> DuplicateFilesDetectorTab(
            loadedInputs = loadedInputs,
            uriResolver = uriResolver,
            onDeduplicateQueue = onDeduplicateQueue
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ForensicMetadataAndHashTab(
    loadedInputs: List<InputFileItem>,
    uriResolver: UriFileResolver,
    forensicHelper: ForensicMetadataHelper
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var targetItem by remember { mutableStateOf(loadedInputs.firstOrNull()) }
    var report by remember { mutableStateOf<ForensicMetadataHelper.FileForensicReport?>(null) }
    var dominantPalette by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
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
        val (loadedReport, loadedPalette) = withContext(Dispatchers.IO) {
            val rep = try {
                forensicHelper.inspectInputFile(item)
            } catch (_: Throwable) {
                null
            }
            val pal = if (item.format.category == FormatCategory.IMAGE) {
                try {
                    uriResolver.openInputStream(item.uri).use { stream ->
                        val bmp = BitmapFactory.decodeStream(stream)
                        val extracted = bmp?.let { extractDominantColorsFromBitmap(it, maxColors = 6) }.orEmpty()
                        bmp?.recycle()
                        extracted
                    }
                } catch (_: Throwable) {
                    emptyList()
                }
            } else {
                emptyList()
            }
            rep to pal
        }
        report = loadedReport
        dominantPalette = loadedPalette
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
                    text = "Selecciona cualquier archivo para analizar sus metadatos ocultos (EXIF, GPS, cámara, autor PDF, códecs), extraer su paleta de colores dominantes (#HEX) y calcular sus hashes criptográficos SHA-256 y MD5.",
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
                        IconButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(r.sha256Hex))
                                Toast.makeText(context, "SHA-256 copiado", Toast.LENGTH_SHORT).show()
                            }
                        ) {
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
                        IconButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(r.md5Hex))
                                Toast.makeText(context, "MD5 copiado", Toast.LENGTH_SHORT).show()
                            }
                        ) {
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

            // Paleta de Colores Dominantes (#HEX) si el archivo es una imagen
            if (dominantPalette.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Paleta de Colores Dominantes (toca para copiar #HEX)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            dominantPalette.forEach { (hexCode, argbColor) ->
                                Surface(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(hexCode))
                                        Toast.makeText(context, "Color $hexCode copiado al portapapeles", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(Color(argbColor))
                                        )
                                        Text(
                                            text = hexCode,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                            }
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

    // 0 = Generar QR (PNG/SVG), 1 = Escanear QR / Código de Barras desde Imagen
    var qrSubMode by remember { mutableIntStateOf(0) }
    // Plantilla en Generador: 0 = Texto / Enlace, 1 = Red Wi-Fi
    var generatorTemplate by remember { mutableIntStateOf(0) }

    var qrText by remember { mutableStateOf("https://github.com/ArukuSynthesis/AnyWall") }
    var wifiSsid by remember { mutableStateOf("") }
    var wifiPassword by remember { mutableStateOf("") }

    val effectiveQrPayload = remember(generatorTemplate, qrText, wifiSsid, wifiPassword) {
        if (generatorTemplate == 1) {
            val cleanSsid = wifiSsid.trim().ifEmpty { "MiRedWiFi" }
            val cleanPass = wifiPassword.trim()
            val sec = if (cleanPass.isEmpty()) "nopass" else "WPA"
            "WIFI:T:$sec;S:$cleanSsid;P:$cleanPass;;"
        } else {
            qrText
        }
    }

    var qrPreviewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var scannedImagePreview by remember { mutableStateOf<Bitmap?>(null) }
    var decodedInfo by remember { mutableStateOf<QrCodeHelper.DecodedBarcodeInfo?>(null) }
    var scanErrorMessage by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val qrImageScannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                var thumbBmp: Bitmap? = null
                val info = try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val bmp = BitmapFactory.decodeStream(stream)
                        if (bmp != null) {
                            val maxEdge = max(bmp.width, bmp.height).coerceAtLeast(1)
                            val scale = (360f / maxEdge.toFloat()).coerceAtMost(1f)
                            thumbBmp = Bitmap.createScaledBitmap(
                                bmp,
                                (bmp.width * scale).roundToInt().coerceAtLeast(1),
                                (bmp.height * scale).roundToInt().coerceAtLeast(1),
                                true
                            )
                            val res = QrCodeHelper.decodeBarcodeInfoFromBitmap(bmp)
                            if (thumbBmp !== bmp) bmp.recycle()
                            res
                        } else {
                            null
                        }
                    }
                } catch (_: Throwable) {
                    null
                }
                withContext(Dispatchers.Main) {
                    scannedImagePreview = thumbBmp
                    decodedInfo = info
                    scanErrorMessage = if (info == null) {
                        "No se detectó ningún código QR o código de barras válido en la imagen seleccionada."
                    } else {
                        null
                    }
                }
            }
        }
    }

    LaunchedEffect(effectiveQrPayload) {
        if (effectiveQrPayload.isBlank()) {
            qrPreviewBitmap = null
        } else {
            qrPreviewBitmap = withContext(Dispatchers.Default) {
                try {
                    QrCodeHelper.generateQrBitmap(effectiveQrPayload, sizePx = 512)
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
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Selector Segmentado Superior: Generar QR vs Leer QR de Imagen
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    onClick = { qrSubMode = 0 },
                    shape = RoundedCornerShape(10.dp),
                    color = if (qrSubMode == 0) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Generar QR (PNG/SVG)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (qrSubMode == 0) FontWeight.ExtraBold else FontWeight.Medium
                        )
                    }
                }
                Surface(
                    onClick = { qrSubMode = 1 },
                    shape = RoundedCornerShape(10.dp),
                    color = if (qrSubMode == 1) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Leer QR de Imagen",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (qrSubMode == 1) FontWeight.ExtraBold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        if (qrSubMode == 0) {
            // MODO GENERADOR QR
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = generatorTemplate == 0,
                            onClick = { generatorTemplate = 0 },
                            leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(15.dp)) },
                            label = { Text("Texto / URL") }
                        )
                        FilterChip(
                            selected = generatorTemplate == 1,
                            onClick = { generatorTemplate = 1 },
                            leadingIcon = { Icon(Icons.Filled.Wifi, contentDescription = null, modifier = Modifier.size(15.dp)) },
                            label = { Text("Red Wi-Fi") }
                        )
                    }

                    if (generatorTemplate == 0) {
                        OutlinedTextField(
                            value = qrText,
                            onValueChange = { qrText = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Texto, enlace o contacto vCard") },
                            minLines = 2,
                            maxLines = 4
                        )
                    } else {
                        OutlinedTextField(
                            value = wifiSsid,
                            onValueChange = { wifiSsid = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Nombre de la red Wi-Fi (SSID)") }
                        )
                        OutlinedTextField(
                            value = wifiPassword,
                            onValueChange = { wifiPassword = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Contraseña Wi-Fi (WPA/WPA2/WPA3)") }
                        )
                    }

                    val bmp = qrPreviewBitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Código QR generado",
                            modifier = Modifier
                                .size(200.dp)
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
                            enabled = effectiveQrPayload.isNotBlank(),
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    val svg = QrCodeHelper.generateQrSvg(effectiveQrPayload)
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
        } else {
            // MODO ESCÁNER QR / CÓDIGO DE BARRAS DESDE IMAGEN
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Decodificar QR o Código de Barras desde Imagen",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Selecciona cualquier captura de pantalla o foto que contenga un código QR (normal o modo oscuro), DataMatrix, Aztec o Código de Barras.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = { qrImageScannerLauncher.launch(arrayOf("image/*")) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Seleccionar captura o foto con QR", fontWeight = FontWeight.Bold)
                    }

                    val previewBmp = scannedImagePreview
                    if (previewBmp != null) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = previewBmp.asImageBitmap(),
                                contentDescription = "Imagen escaneada",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .height(140.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        }
                    }

                    if (scanErrorMessage != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = scanErrorMessage!!,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }

                    val info = decodedInfo
                    if (info != null) {
                        // Si es un QR de red Wi-Fi, mostrar tarjeta dedicada con SSID y Contraseña
                        if (info.wifiSsid != null) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Wifi,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = "Red Wi-Fi Detectada: ${info.wifiSsid}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    if (!info.wifiPassword.isNullOrBlank()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Clave: ${info.wifiPassword}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            FilledTonalButton(
                                                onClick = {
                                                    clipboard.setText(AnnotatedString(info.wifiPassword))
                                                    Toast.makeText(context, "Contraseña Wi-Fi copiada", Toast.LENGTH_SHORT).show()
                                                }
                                            ) {
                                                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Copiar clave Wi-Fi")
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Formato detectado: ${info.formatName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = info.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilledTonalButton(
                                        onClick = {
                                            clipboard.setText(AnnotatedString(info.text))
                                            Toast.makeText(context, "Contenido copiado al portapapeles", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Copiar texto")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            qrText = info.text
                                            generatorTemplate = 0
                                            qrSubMode = 0
                                        }
                                    ) {
                                        Icon(Icons.Filled.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Editar en Generador")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun extractDominantColorsFromBitmap(bitmap: Bitmap, maxColors: Int = 6): List<Pair<String, Int>> {
    if (bitmap.width <= 0 || bitmap.height <= 0) return emptyList()
    val scaled = Bitmap.createScaledBitmap(bitmap, 48, 48, true)
    val bucketCounts = HashMap<Int, Int>()
    val bucketSumR = HashMap<Int, Long>()
    val bucketSumG = HashMap<Int, Long>()
    val bucketSumB = HashMap<Int, Long>()

    val pixels = IntArray(48 * 48)
    scaled.getPixels(pixels, 0, 48, 0, 0, 48, 48)
    if (scaled !== bitmap) scaled.recycle()

    for (p in pixels) {
        val a = (p ushr 24) and 0xFF
        if (a < 128) continue
        val r = (p ushr 16) and 0xFF
        val g = (p ushr 8) and 0xFF
        val b = p and 0xFF
        // Cuantización en cubos de 32 niveles por canal (8x8x8 = 512 cubos)
        val bucketKey = ((r / 32) shl 6) or ((g / 32) shl 3) or (b / 32)
        bucketCounts[bucketKey] = (bucketCounts[bucketKey] ?: 0) + 1
        bucketSumR[bucketKey] = (bucketSumR[bucketKey] ?: 0L) + r
        bucketSumG[bucketKey] = (bucketSumG[bucketKey] ?: 0L) + g
        bucketSumB[bucketKey] = (bucketSumB[bucketKey] ?: 0L) + b
    }

    val sortedBuckets = bucketCounts.entries.sortedByDescending { it.value }
    val selected = mutableListOf<Pair<String, Int>>()

    for ((key, count) in sortedBuckets) {
        if (selected.size >= maxColors) break
        val avgR = ((bucketSumR[key] ?: 0L) / count).toInt().coerceIn(0, 255)
        val avgG = ((bucketSumG[key] ?: 0L) / count).toInt().coerceIn(0, 255)
        val avgB = ((bucketSumB[key] ?: 0L) / count).toInt().coerceIn(0, 255)

        val tooSimilar = selected.any { (_, existingArgb) ->
            val er = (existingArgb ushr 16) and 0xFF
            val eg = (existingArgb ushr 8) and 0xFF
            val eb = existingArgb and 0xFF
            (abs(avgR - er) + abs(avgG - eg) + abs(avgB - eb)) < 55
        }
        if (!tooSimilar) {
            val argb = (0xFF shl 24) or (avgR shl 16) or (avgG shl 8) or avgB
            val hex = String.format(Locale.US, "#%02X%02X%02X", avgR, avgG, avgB)
            selected.add(hex to argb)
        }
    }
    return selected
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

@Composable
private fun DuplicateFilesDetectorTab(
    loadedInputs: List<InputFileItem>,
    uriResolver: UriFileResolver,
    onDeduplicateQueue: ((List<InputFileItem>) -> Unit)?
) {
    val scope = rememberCoroutineScope()
    var scanCandidates by remember(loadedInputs) { mutableStateOf(loadedInputs) }
    var scanReport by remember { mutableStateOf<DuplicateFilesDetector.DuplicateScanReport<InputFileItem>?>(null) }
    var isScanning by remember { mutableStateOf(false) }
    var statusBanner by remember { mutableStateOf<String?>(null) }

    val multiFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val resolved = uriResolver.resolveMultipleFiles(uris)
            scanCandidates = scanCandidates + resolved
        }
    }

    LaunchedEffect(scanCandidates) {
        if (scanCandidates.isEmpty()) {
            scanReport = null
            return@LaunchedEffect
        }
        isScanning = true
        val report = withContext(Dispatchers.IO) {
            DuplicateFilesDetector.scanInputItemsForDuplicates(
                items = scanCandidates,
                uriResolver = uriResolver
            )
        }
        scanReport = report
        isScanning = false
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Detector y Limpiador de Duplicados (SHA-256)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Agrupa por tamaño exacto en bytes y calcula hash SHA-256 en streaming solo cuando hay coincidencia.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            FilledTonalButton(onClick = { multiFilePicker.launch(arrayOf("*/*")) }) {
                Icon(Icons.Filled.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("+ Archivos")
            }
        }

        if (isScanning) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Analizando tamaños y calculando SHA-256 en streaming…")
                }
            }
        } else {
            val report = scanReport
            if (report == null || report.totalScannedCount == 0) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Carga varios archivos en la cola o pulsa «+ Archivos» para detectar copias idénticas por tamaño y hash criptográfico SHA-256.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (report.hasDuplicates) {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (report.hasDuplicates) {
                                "Se detectaron ${report.totalRedundantFilesCount} duplicados exactos en ${report.duplicateGroups.size} grupos"
                            } else {
                                "✓ 0 duplicados en ${report.totalScannedCount} archivos analizados"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (report.hasDuplicates) {
                            Text(
                                text = "Espacio redundante recuperable: ${InputFileItem.formatBytes(report.totalReclaimableBytes)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (onDeduplicateQueue != null) {
                                Button(
                                    onClick = {
                                        onDeduplicateQueue(report.deduplicatedList)
                                        scanCandidates = report.deduplicatedList
                                        statusBanner = "Se limpiaron ${report.totalRedundantFilesCount} archivos duplicados de la cola."
                                    }
                                ) {
                                    Text("Limpiar duplicados de la cola en 1 toque")
                                }
                            }
                        }
                    }
                }

                if (statusBanner != null) {
                    Text(
                        text = statusBanner.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                report.duplicateGroups.forEachIndexed { idx, group ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Grupo #${idx + 1} • ${group.items.size} copias (${InputFileItem.formatBytes(group.fileSizeBytes)} c/u)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "SHA-256: ${group.sha256Hex}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            HorizontalDivider()
                            group.items.forEachIndexed { copyIdx, item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (copyIdx == 0) "★ Conservar: ${item.displayName}" else "• Duplicado: ${item.displayName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (copyIdx == 0) FontWeight.Bold else FontWeight.Normal,
                                        color = if (copyIdx == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

