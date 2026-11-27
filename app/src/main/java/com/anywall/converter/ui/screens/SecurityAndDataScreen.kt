package com.anywall.converter.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anywall.converter.core.AppLanguageMode
import com.anywall.converter.core.FileShareHelper
import com.anywall.converter.core.ScopedStorageManager
import com.anywall.converter.core.UriFileResolver
import com.anywall.converter.data.engines.AesVaultCrypto
import com.anywall.converter.data.engines.ForensicMetadataHelper
import com.anywall.converter.domain.model.ConversionJobResult
import com.anywall.converter.domain.model.FormatCategory
import com.anywall.converter.domain.model.HistoryRecord
import com.anywall.converter.domain.model.InputFileItem
import com.anywall.converter.domain.model.SupportedFormat
import com.anywall.converter.ui.components.FormatSpecificPickerOption
import com.anywall.converter.ui.components.ToolsUtilityInlinePanel
import com.anywall.converter.ui.components.specificFormatOptionsForCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.security.SecureRandom

private const val TAB_VAULT_MANAGER = 99
private const val TAB_STRUCTURED_DATA = 98

/**
 * Pestaña dedicada en la barra de navegación inferior para **Datos, Seguridad e Inspección** (`SecurityAndDataScreen` - v0.6):
 * - **Gestor Directo de Bóveda Criptográfica `AES-256-GCM` (`.anywall`)**: permite cifrar cualquier archivo hacia `.anywall`
 *   o desencriptar archivos `.anywall` existentes restaurando su nombre y extensión originales **directamente en esta pantalla**
 *   (sin enviar a `ConfigureScreen`), con generador de claves `SecureRandom`, medidor de fortaleza, recepción directa desde
 *   `"Abrir con AnyWall"` (`ACTION_VIEW`) y tarjeta de resultado inmediata con botones **"Abrir"** y **"Compartir"**.
 * - **Conversión rápida de Datos Estructurados** por formato específico (`JSON`, `CSV`, `XML`, `YAML`, `BASE64`).
 * - **Suite de Utilidades de Inspección y Seguridad (en pantalla completa)**:
 *   - Limpiador de Duplicados `SHA-256`
 *   - Inspector Forense de Metadatos (EXIF/GPS/PDF/Media + Paleta de Colores `#HEX`) y Verificador `SHA-256` / `MD5`
 *   - Generador y Escáner de Códigos QR (`PNG` / `SVG` / Decodificador desde Imagen)
 *   - Comparador de Texto `Diff` (LCS)
 *   - Comparador Visual `Antes / Después`
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SecurityAndDataScreen(
    configureState: ConfigureUiState,
    languageMode: AppLanguageMode,
    uriResolver: UriFileResolver,
    storageManager: ScopedStorageManager,
    forensicHelper: ForensicMetadataHelper,
    pendingVaultFiles: List<InputFileItem> = emptyList(),
    onConsumePendingVaultFiles: () -> List<InputFileItem> = { emptyList() },
    onFilesSelectedForConversion: (List<Uri>, SupportedFormat?) -> Unit,
    onDeduplicateQueue: (List<InputFileItem>) -> Unit,
    onRecordDirectConversions: (List<ConversionJobResult>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isEn = languageMode.isEnglishActive()
    val accentTeal = Color(0xFF0D9488)

    var activeUtilityTab by remember { mutableIntStateOf(TAB_VAULT_MANAGER) }
    var pendingFormatOption by remember { mutableStateOf<FormatSpecificPickerOption?>(null) }

    LaunchedEffect(pendingVaultFiles) {
        if (pendingVaultFiles.isNotEmpty()) {
            activeUtilityTab = TAB_VAULT_MANAGER
        }
    }

    val structuredFormatOptions = remember {
        specificFormatOptionsForCategory(FormatCategory.DATA_TOOLS).filter { it.id != "ANYWALL_VAULT" }
    }

    val structuredFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val opt = pendingFormatOption
            if (opt != null) {
                val matched = uris.filter { uri ->
                    val item = uriResolver.resolveInputFile(uri)
                    item != null && item.format in opt.acceptedFormats
                }
                if (matched.isNotEmpty()) {
                    onFilesSelectedForConversion(matched, null)
                } else {
                    Toast.makeText(
                        context,
                        if (isEn) "Please select ${opt.label} (${opt.extHint}) files"
                        else "Selecciona archivos de formato ${opt.label} (${opt.extHint})",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                onFilesSelectedForConversion(uris, null)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isEn) "Vault, Data & Security" else "Bóveda, Datos y Seguridad",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = if (isEn) {
                                "Direct AES-256 Vault, JSON/CSV/XML/YAML, SHA-256 Duplicates & QR"
                            } else {
                                "Bóveda AES-256 directa, JSON/CSV/XML/YAML, Duplicados SHA-256 y QR"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 1. Barra unificada de selección de herramienta (Bóveda .anywall, Datos, QR, Forense, Duplicados, Diff)
            val utilityTabs = if (isEn) {
                listOf(
                    Triple(TAB_VAULT_MANAGER, "AES-256 Vault (.anywall)", Icons.Default.Lock),
                    Triple(TAB_STRUCTURED_DATA, "Data (JSON/CSV/XML)", Icons.Default.FileOpen),
                    Triple(1, "QR Codes & Scanner", Icons.Default.QrCode2),
                    Triple(0, "Forensic & SHA-256", Icons.Default.VerifiedUser),
                    Triple(4, "SHA-256 Duplicates", Icons.Default.FileCopy),
                    Triple(2, "Text Diff (LCS)", Icons.Default.Difference),
                    Triple(3, "Before / After", Icons.Default.Compare)
                )
            } else {
                listOf(
                    Triple(TAB_VAULT_MANAGER, "Bóveda .anywall", Icons.Default.Lock),
                    Triple(TAB_STRUCTURED_DATA, "Datos (JSON/CSV/XML)", Icons.Default.FileOpen),
                    Triple(1, "Códigos QR", Icons.Default.QrCode2),
                    Triple(0, "Metadatos & SHA-256", Icons.Default.VerifiedUser),
                    Triple(4, "Duplicados SHA-256", Icons.Default.FileCopy),
                    Triple(2, "Comparador Diff", Icons.Default.Difference),
                    Triple(3, "Antes / Después", Icons.Default.Compare)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                utilityTabs.forEach { (tabIdx, label, icon) ->
                    val selected = activeUtilityTab == tabIdx
                    FilterChip(
                        selected = selected,
                        onClick = { activeUtilityTab = tabIdx },
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = {
                            Text(
                                text = label,
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium
                            )
                        }
                    )
                }
            }

            // 2. Espacio de trabajo interactivo en pantalla completa
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                when (activeUtilityTab) {
                    TAB_VAULT_MANAGER -> {
                        DirectAesVaultManagerPanel(
                            isEn = isEn,
                            uriResolver = uriResolver,
                            storageManager = storageManager,
                            pendingVaultFiles = pendingVaultFiles,
                            onConsumePendingVaultFiles = onConsumePendingVaultFiles,
                            onRecordDirectConversions = onRecordDirectConversions
                        )
                    }
                    TAB_STRUCTURED_DATA -> {
                        StructuredDataWorkspacePanel(
                            isEn = isEn,
                            accentTeal = accentTeal,
                            structuredFormatOptions = structuredFormatOptions,
                            onPickSpecificOption = { opt ->
                                pendingFormatOption = opt
                                structuredFilePickerLauncher.launch(opt.mimeTypes)
                            }
                        )
                    }
                    else -> {
                        ToolsUtilityInlinePanel(
                            selectedTab = activeUtilityTab,
                            loadedInputs = configureState.inputFiles,
                            currentImageEditSpec = configureState.options.imageEditSpec,
                            currentQualityPercent = configureState.options.clampedQuality,
                            uriResolver = uriResolver,
                            storageManager = storageManager,
                            forensicHelper = forensicHelper,
                            onDeduplicateQueue = onDeduplicateQueue
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StructuredDataWorkspacePanel(
    isEn: Boolean,
    accentTeal: Color,
    structuredFormatOptions: List<FormatSpecificPickerOption>,
    onPickSpecificOption: (FormatSpecificPickerOption) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                border = BorderStroke(1.dp, accentTeal.copy(alpha = 0.32f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (isEn) "Structured Data Converter" else "Conversor de Datos Estructurados",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = if (isEn) {
                            "Select a format below to convert between JSON, CSV, XML, YAML, HTML, TXT and BASE64 with automatic schema formatting."
                        } else {
                            "Elige el formato de origen para convertir entre JSON, CSV, XML, YAML, HTML, TXT y BASE64 con formato limpio (Pretty-Print) y codificación UTF-8."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        val rows = structuredFormatOptions.chunked(2)
        items(rows) { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowItems.forEach { opt ->
                    Surface(
                        onClick = { onPickSpecificOption(opt) },
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, accentTeal.copy(alpha = 0.35f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = accentTeal.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = opt.id,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = accentTeal,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            Text(
                                text = opt.label,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = opt.extHint,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Gestor Directo de Bóveda Criptográfica `AES-256-GCM` (`.anywall`) dentro de la pestaña Seguridad (v0.6).
 * Permite Cifrar cualquier archivo hacia `.anywall` o Desencriptar un `.anywall` existente para restaurar
 * su archivo original en 1 paso sin pasar por `ConfigureScreen`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DirectAesVaultManagerPanel(
    isEn: Boolean,
    uriResolver: UriFileResolver,
    storageManager: ScopedStorageManager,
    pendingVaultFiles: List<InputFileItem>,
    onConsumePendingVaultFiles: () -> List<InputFileItem>,
    onRecordDirectConversions: (List<ConversionJobResult>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accentTeal = Color(0xFF0D9488)

    // 0 = Cifrar hacia .anywall, 1 = Desencriptar .anywall
    var vaultMode by remember { mutableIntStateOf(0) }
    var selectedFiles by remember { mutableStateOf<List<InputFileItem>>(emptyList()) }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isErrorStatus by remember { mutableStateOf(false) }
    var generatedRecords by remember { mutableStateOf<List<HistoryRecord>>(emptyList()) }

    // Si el usuario abrió un archivo .anywall con "Abrir con AnyWall" (ACTION_VIEW) o Share Sheet,
    // cambia automáticamente a Modo Desencriptar con el archivo cargado.
    LaunchedEffect(pendingVaultFiles) {
        if (pendingVaultFiles.isNotEmpty()) {
            val incoming = onConsumePendingVaultFiles()
            if (incoming.isNotEmpty()) {
                vaultMode = 1
                selectedFiles = incoming
                isErrorStatus = false
                statusMessage = if (isEn) {
                    "Loaded ${incoming.size} .anywall file(s). Enter your password below to decrypt and restore."
                } else {
                    "Se cargó ${incoming.size} archivo(s) .anywall automáticamente. Introduce la contraseña para desencriptar."
                }
            }
        }
    }

    fun copyKeyToClipboard(keyText: String, toastMessage: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("AnyWall AES-256 Key", keyText))
        Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val resolved = uriResolver.resolveMultipleFiles(uris)
            if (vaultMode == 1) {
                val validVaults = resolved.filter {
                    it.format == SupportedFormat.ANYWALL_VAULT ||
                        it.displayName.lowercase().endsWith(".anywall") ||
                        it.displayName.lowercase().endsWith(".awv")
                }
                if (validVaults.isNotEmpty()) {
                    selectedFiles = validVaults
                    statusMessage = null
                    isErrorStatus = false
                } else {
                    statusMessage = if (isEn) {
                        "Please select valid .anywall encrypted vault files to decrypt."
                    } else {
                        "Selecciona archivos con extensión .anywall para desencriptarlos."
                    }
                    isErrorStatus = true
                }
            } else {
                selectedFiles = resolved
                statusMessage = null
                isErrorStatus = false
            }
        }
    }

    val passwordStrength = remember(password) { computePasswordStrengthScore(password) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Selector Segmentado: Cifrar vs Desencriptar
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    VaultModePill(
                        title = if (isEn) "Encrypt → .anywall" else "Cifrar → .anywall",
                        icon = Icons.Default.Lock,
                        selected = vaultMode == 0,
                        onClick = {
                            if (vaultMode != 0) {
                                vaultMode = 0
                                selectedFiles = emptyList()
                                statusMessage = null
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    VaultModePill(
                        title = if (isEn) "Decrypt .anywall" else "Desencriptar .anywall",
                        icon = Icons.Default.LockOpen,
                        selected = vaultMode == 1,
                        onClick = {
                            if (vaultMode != 1) {
                                vaultMode = 1
                                selectedFiles = emptyList()
                                statusMessage = null
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 2. Tarjeta principal de selección de archivo(s) y contraseña AES-256-GCM
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                border = BorderStroke(1.dp, accentTeal.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(accentTeal.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (vaultMode == 0) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = null,
                                tint = accentTeal,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (vaultMode == 0) {
                                    if (isEn) "Encrypt Any File (AES-256-GCM)" else "Cifrar Archivos en Bóveda AES-256-GCM"
                                } else {
                                    if (isEn) "Decrypt .anywall & Restore Original" else "Desencriptar .anywall y Restaurar Original"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = if (vaultMode == 0) {
                                    if (isEn) "PBKDF2-HMAC-SHA256 (120,000 iterations) + 128-bit GCM authentication tag"
                                    else "Derivación PBKDF2-HMAC-SHA256 (120 000 iteraciones) + etiqueta GCM de 128 bits"
                                } else {
                                    if (isEn) "Automatically restores the original filename and extension into Downloads/AnyWall/"
                                    else "Restaura automáticamente el nombre y la extensión original en Downloads/AnyWall/"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Botón para seleccionar archivos
                    FilledTonalButton(
                        onClick = {
                            filePicker.launch(arrayOf("*/*"))
                        },
                        enabled = !isProcessing,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (vaultMode == 0) {
                                if (isEn) "Select File(s) to Encrypt" else "Seleccionar archivo(s) a cifrar"
                            } else {
                                if (isEn) "Select .anywall File(s) to Decrypt" else "Seleccionar archivo(s) .anywall a desencriptar"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Lista compacta de archivos seleccionados
                    if (selectedFiles.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            selectedFiles.forEach { item ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.displayName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${item.format.label} • ${item.formattedSize}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                selectedFiles = selectedFiles.filterNot { it.uri == item.uri }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Campo de Contraseña con botón Mostrar/Ocultar
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        enabled = !isProcessing,
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        visualTransformation = if (showPassword) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (password.isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            copyKeyToClipboard(
                                                password,
                                                if (isEn) "Key copied to clipboard" else "Clave copiada al portapapeles"
                                            )
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = if (isEn) "Copy key" else "Copiar clave",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (showPassword) "Ocultar" else "Mostrar"
                                    )
                                }
                            }
                        },
                        label = {
                            Text(
                                if (vaultMode == 0) {
                                    if (isEn) "Encryption Password" else "Contraseña para cifrar"
                                } else {
                                    if (isEn) "Vault Password to Decrypt" else "Contraseña con la que cifraste el .anywall"
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Generador Criptográfico en 1 toque (solo en Modo Cifrar)
                    if (vaultMode == 0) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            AssistChip(
                                onClick = {
                                    val generated = generateSecureRandomKey(20)
                                    password = generated
                                    showPassword = true
                                    copyKeyToClipboard(
                                        generated,
                                        if (isEn) "Generated 20-char key & copied to clipboard"
                                        else "Clave segura de 20 caracteres generada y copiada"
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Key,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                        tint = accentTeal
                                    )
                                },
                                label = {
                                    Text(
                                        text = if (isEn) "Generate Strong Key (20c)" else "Generar clave segura (20c)",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            )
                            AssistChip(
                                onClick = {
                                    val generated = generateSecurePassphrase()
                                    password = generated
                                    showPassword = true
                                    copyKeyToClipboard(
                                        generated,
                                        if (isEn) "Passphrase generated & copied to clipboard"
                                        else "Frase clave generada y copiada al portapapeles"
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                        tint = accentTeal
                                    )
                                },
                                label = {
                                    Text(
                                        text = if (isEn) "Memorable Passphrase" else "Generar frase clave",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            )
                        }
                    }

                    // Medidor de fortaleza de contraseña al cifrar
                    if (vaultMode == 0 && password.isNotEmpty()) {
                        val (strengthLabel, strengthColor, strengthFraction) = when (passwordStrength) {
                            0, 1 -> Triple(if (isEn) "Weak" else "Débil", Color(0xFFEF4444), 0.25f)
                            2 -> Triple(if (isEn) "Medium" else "Media", Color(0xFFF59E0B), 0.55f)
                            3 -> Triple(if (isEn) "Strong" else "Fuerte", Color(0xFF10B981), 0.80f)
                            else -> Triple(if (isEn) "Very Strong" else "Muy Fuerte", Color(0xFF06B6D4), 1.0f)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (isEn) "Key Entropy" else "Fortaleza de la clave",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = strengthLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = strengthColor
                                )
                            }
                            LinearProgressIndicator(
                                progress = { strengthFraction },
                                color = strengthColor,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(50))
                            )
                        }
                    }

                    // Botón de acción directa (Cifrar o Desencriptar aquí mismo)
                    Button(
                        onClick = {
                            val cleanPass = password.trim()
                            if (selectedFiles.isEmpty() || cleanPass.isEmpty()) return@Button
                            isProcessing = true
                            statusMessage = null
                            isErrorStatus = false

                            scope.launch {
                                val outcome = withContext(Dispatchers.IO) {
                                    runCatching {
                                        val jobResults = mutableListOf<ConversionJobResult>()
                                        val historyItems = mutableListOf<HistoryRecord>()
                                        val now = System.currentTimeMillis()

                                        if (vaultMode == 0) {
                                            // Cifrar archivos hacia .anywall
                                            selectedFiles.forEachIndexed { idx, item ->
                                                val tempVault = uriResolver.createTempWorkingFile("vault_direct_", "anywall")
                                                try {
                                                    uriResolver.openInputStream(item.uri).use { inputStream ->
                                                        FileOutputStream(tempVault).use { fos ->
                                                            BufferedOutputStream(fos, 64 * 1024).use { bos ->
                                                                AesVaultCrypto.encryptStreamToVault(
                                                                    input = inputStream,
                                                                    originalFileName = item.displayName,
                                                                    password = cleanPass,
                                                                    output = bos
                                                                )
                                                            }
                                                        }
                                                    }
                                                    val outName = UriFileResolver.buildOutputFileName(
                                                        item.baseNameWithoutExtension,
                                                        "anywall"
                                                    )
                                                    val saved = storageManager.saveTempFileToDownloads(
                                                        sourceFile = tempVault,
                                                        desiredDisplayName = outName,
                                                        mimeType = SupportedFormat.ANYWALL_VAULT.mimeType,
                                                        subFolder = storageManager.resolveEffectiveSubFolder(
                                                            relativeFolderPath = item.relativeFolderPath,
                                                            categorySubfolder = "Boveda_AES",
                                                            preserveTreeOverride = false
                                                        )
                                                    )
                                                    val res = ConversionJobResult(
                                                        sourceDisplayName = item.displayName,
                                                        sourceFormat = item.format,
                                                        sourceSizeBytes = item.sizeBytes,
                                                        outputDisplayName = saved.displayName,
                                                        outputFormat = SupportedFormat.ANYWALL_VAULT,
                                                        outputCategory = FormatCategory.DATA_TOOLS,
                                                        outputUri = saved.uri,
                                                        outputAbsolutePath = saved.absolutePath,
                                                        outputSizeBytes = saved.sizeBytes,
                                                        outputMimeType = saved.mimeType
                                                    )
                                                    jobResults.add(res)
                                                    historyItems.add(
                                                        HistoryRecord(
                                                            id = now + idx,
                                                            sourceDisplayName = res.sourceDisplayName,
                                                            sourceFormat = res.sourceFormat,
                                                            sourceSizeBytes = res.sourceSizeBytes,
                                                            outputDisplayName = res.outputDisplayName,
                                                            outputFormat = res.outputFormat,
                                                            category = res.outputCategory,
                                                            outputUriString = res.outputUri.toString(),
                                                            outputAbsolutePath = res.outputAbsolutePath,
                                                            outputSizeBytes = res.outputSizeBytes,
                                                            outputMimeType = res.outputMimeType,
                                                            createdAtMillis = now
                                                        )
                                                    )
                                                } finally {
                                                    tempVault.delete()
                                                }
                                            }
                                        } else {
                                            // Desencriptar archivos .anywall y restaurar originales
                                            selectedFiles.forEachIndexed { idx, item ->
                                                val tempPlain = uriResolver.createTempWorkingFile("unvault_direct_", "bin")
                                                try {
                                                    val restoredName = uriResolver.openInputStream(item.uri).use { inputStream ->
                                                        FileOutputStream(tempPlain).use { fos ->
                                                            BufferedOutputStream(fos, 64 * 1024).use { bos ->
                                                                AesVaultCrypto.decryptVaultStream(
                                                                    input = inputStream,
                                                                    password = cleanPass,
                                                                    output = bos
                                                                )
                                                            }
                                                        }
                                                    }
                                                    val detectedFormat = SupportedFormat.detect(restoredName, null) ?: SupportedFormat.TXT
                                                    val saved = storageManager.saveTempFileToDownloads(
                                                        sourceFile = tempPlain,
                                                        desiredDisplayName = UriFileResolver.sanitizeFileName(restoredName),
                                                        mimeType = detectedFormat.mimeType
                                                    )
                                                    val res = ConversionJobResult(
                                                        sourceDisplayName = item.displayName,
                                                        sourceFormat = SupportedFormat.ANYWALL_VAULT,
                                                        sourceSizeBytes = item.sizeBytes,
                                                        outputDisplayName = saved.displayName,
                                                        outputFormat = detectedFormat,
                                                        outputCategory = detectedFormat.category,
                                                        outputUri = saved.uri,
                                                        outputAbsolutePath = saved.absolutePath,
                                                        outputSizeBytes = saved.sizeBytes,
                                                        outputMimeType = saved.mimeType
                                                    )
                                                    jobResults.add(res)
                                                    historyItems.add(
                                                        HistoryRecord(
                                                            id = now + idx,
                                                            sourceDisplayName = res.sourceDisplayName,
                                                            sourceFormat = res.sourceFormat,
                                                            sourceSizeBytes = res.sourceSizeBytes,
                                                            outputDisplayName = res.outputDisplayName,
                                                            outputFormat = res.outputFormat,
                                                            category = res.outputCategory,
                                                            outputUriString = res.outputUri.toString(),
                                                            outputAbsolutePath = res.outputAbsolutePath,
                                                            outputSizeBytes = res.outputSizeBytes,
                                                            outputMimeType = res.outputMimeType,
                                                            createdAtMillis = now
                                                        )
                                                    )
                                                } finally {
                                                    tempPlain.delete()
                                                }
                                            }
                                        }
                                        jobResults to historyItems
                                    }
                                }

                                isProcessing = false
                                outcome.onSuccess { (jobs, records) ->
                                    onRecordDirectConversions(jobs)
                                    generatedRecords = records
                                    isErrorStatus = false
                                    statusMessage = if (vaultMode == 0) {
                                        if (isEn) "Encrypted ${records.size} file(s) into Downloads/AnyWall/Boveda_AES/"
                                        else "¡${records.size} archivo(s) cifrado(s) en Downloads/AnyWall/Boveda_AES/!"
                                    } else {
                                        if (isEn) "Decrypted & restored ${records.size} original file(s) into Downloads/AnyWall/"
                                        else "¡${records.size} archivo(s) desencriptado(s) y restaurado(s) en Downloads/AnyWall/!"
                                    }
                                }.onFailure { err ->
                                    isErrorStatus = true
                                    statusMessage = err.localizedMessage ?: err.message ?: "Error al procesar la bóveda AES-256"
                                }
                            }
                        },
                        enabled = !isProcessing && selectedFiles.isNotEmpty() && password.isNotBlank(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentTeal,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Icon(
                                imageVector = if (vaultMode == 0) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (vaultMode == 0) {
                                if (isEn) "Encrypt & Save .anywall Now" else "Cifrar y Guardar .anywall Ahora"
                            } else {
                                if (isEn) "Decrypt & Restore Original Now" else "Desencriptar y Restaurar Original Ahora"
                            },
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        // 3. Mensaje de Estado o Error
        if (statusMessage != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isErrorStatus) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (isErrorStatus) Icons.Default.Close else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (isErrorStatus) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            }
                        )
                        Text(
                            text = statusMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isErrorStatus) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            }
                        )
                    }
                }
            }
        }

        // 4. Tarjetas de Resultado Inmediato con Abrir / Compartir
        if (generatedRecords.isNotEmpty()) {
            items(
                items = generatedRecords,
                key = { "${it.id}_${it.outputDisplayName}" }
            ) { rec ->
                Surface(
                    onClick = { FileShareHelper.openConvertedFile(context, rec) },
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, accentTeal.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = rec.outputDisplayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${rec.outputFormat.label} • ${InputFileItem.formatBytes(rec.outputSizeBytes)} (Origen: ${rec.sourceDisplayName})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalButton(
                            onClick = { FileShareHelper.openConvertedFile(context, rec) },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isEn) "Open" else "Abrir", fontWeight = FontWeight.Bold)
                        }
                        IconButton(
                            onClick = { FileShareHelper.shareConvertedFile(context, rec) }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                tint = accentTeal
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun VaultModePill(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentTeal = Color(0xFF0D9488)
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) accentTeal.copy(alpha = 0.18f) else Color.Transparent,
        border = if (selected) BorderStroke(1.dp, accentTeal.copy(alpha = 0.5f)) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) accentTeal else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                color = if (selected) accentTeal else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun computePasswordStrengthScore(pw: String): Int {
    if (pw.length < 6) return 0
    var score = 1
    if (pw.length >= 10) score++
    if (pw.any { it.isUpperCase() } && pw.any { it.isLowerCase() }) score++
    if (pw.any { it.isDigit() } && pw.any { !it.isLetterOrDigit() }) score++
    return score.coerceIn(0, 4)
}

private fun generateSecureRandomKey(length: Int = 20): String {
    val upper = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    val lower = "abcdefghijkmnopqrstuvwxyz"
    val digits = "23456789"
    val symbols = "!@#$%&*-_"
    val allChars = upper + lower + digits + symbols
    val rng = SecureRandom()
    val chars = MutableList(length.coerceAtLeast(12)) { index ->
        when (index) {
            0 -> upper[rng.nextInt(upper.length)]
            1 -> lower[rng.nextInt(lower.length)]
            2 -> digits[rng.nextInt(digits.length)]
            3 -> symbols[rng.nextInt(symbols.length)]
            else -> allChars[rng.nextInt(allChars.length)]
        }
    }
    chars.shuffle(rng)
    return chars.joinToString("")
}

private val VAULT_PASSPHRASE_WORDS = listOf(
    "Argon", "Neon", "Titan", "Solar", "Prisma", "Vortex", "Nexo", "Aegis",
    "Quantum", "Zenith", "Cipher", "Helios", "Krypton", "Orbit", "Pulse", "Vector",
    "Matrix", "Obsidian", "Cobalt", "Aether", "Falcon", "Nova", "Quasar", "Orion",
    "Atlas", "Phoenix", "Cortex", "Spectra", "Delta", "Omega", "Apex", "Nexus"
)

private fun generateSecurePassphrase(): String {
    val rng = SecureRandom()
    val picked = VAULT_PASSPHRASE_WORDS.shuffled(rng).take(4)
    val pin = 1000 + rng.nextInt(9000)
    return "${picked.joinToString("-")}-$pin!"
}

