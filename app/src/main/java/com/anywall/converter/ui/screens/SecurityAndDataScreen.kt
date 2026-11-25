package com.anywall.converter.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anywall.converter.core.AppLanguageMode
import com.anywall.converter.core.ScopedStorageManager
import com.anywall.converter.core.UriFileResolver
import com.anywall.converter.data.engines.ForensicMetadataHelper
import com.anywall.converter.domain.model.FormatCategory
import com.anywall.converter.domain.model.InputFileItem
import com.anywall.converter.domain.model.SupportedFormat
import com.anywall.converter.ui.components.FormatSpecificPickerOption
import com.anywall.converter.ui.components.ToolsUtilityInlinePanel
import com.anywall.converter.ui.components.specificFormatOptionsForCategory

/**
 * Pestaña dedicada en la barra de navegación inferior para **Datos, Seguridad e Inspección** (`SecurityAndDataScreen` - v0.5):
 * - **Datos Estructurados y Bóveda Criptográfica `AES-256-GCM` (`.anywall`)**: selección por formato específico
 *   (`Bóveda .anywall`, `JSON`, `CSV`, `XML`, `YAML`, `BASE64`) y botón directo para cifrar cualquier archivo en una bóveda `.anywall`.
 * - **Suite de Utilidades de Inspección y Seguridad (en pantalla completa)**:
 *   - Limpiador de Duplicados `SHA-256`
 *   - Inspector Forense de Metadatos (EXIF/GPS/PDF/Media) y Verificador `SHA-256` / `MD5`
 *   - Generador y Escáner de Códigos QR (`PNG` / `SVG`)
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
    onFilesSelectedForConversion: (List<Uri>, SupportedFormat?) -> Unit,
    onDeduplicateQueue: (List<InputFileItem>) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isEn = languageMode.isEnglishActive()
    val accentTeal = Color(0xFF0D9488)

    var activeUtilityTab by remember { mutableIntStateOf(4) } // Por defecto Duplicados SHA-256
    var pendingFormatOption by remember { mutableStateOf<FormatSpecificPickerOption?>(null) }
    var encryptToVaultMode by remember { mutableStateOf(false) }

    val dataFormatOptions = remember { specificFormatOptionsForCategory(FormatCategory.DATA_TOOLS) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            if (encryptToVaultMode) {
                encryptToVaultMode = false
                onFilesSelectedForConversion(uris, SupportedFormat.ANYWALL_VAULT)
            } else {
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
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isEn) "Data, Vault & Security" else "Datos, Bóveda e Inspección",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = if (isEn) {
                                "AES-256 Vault, JSON/CSV/XML/YAML, SHA-256 Duplicates, EXIF & QR"
                            } else {
                                "Bóveda AES-256, JSON/CSV/XML/YAML, Duplicados SHA-256, EXIF y QR"
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
            // 1. Tarjeta compacta de Datos Estructurados y Bóveda AES-256-GCM por Formato Específico
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                border = BorderStroke(1.dp, accentTeal.copy(alpha = 0.32f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
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
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = accentTeal,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = if (isEn) "Convert Structured Data & Vault" else "Convertir Datos y Bóveda (.anywall)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                encryptToVaultMode = true
                                pendingFormatOption = null
                                filePickerLauncher.launch(arrayOf("*/*"))
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isEn) "Encrypt → .anywall" else "Cifrar → .anywall",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        FilledTonalButton(
                            onClick = {
                                encryptToVaultMode = false
                                val vaultOpt = dataFormatOptions.first { it.id == "ANYWALL_VAULT" }
                                pendingFormatOption = vaultOpt
                                filePickerLauncher.launch(vaultOpt.mimeTypes)
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LockOpen,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isEn) "Decrypt .anywall" else "Desencriptar .anywall",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        dataFormatOptions.forEach { opt ->
                            Surface(
                                onClick = {
                                    encryptToVaultMode = false
                                    pendingFormatOption = opt
                                    filePickerLauncher.launch(opt.mimeTypes)
                                },
                                shape = RoundedCornerShape(9.dp),
                                color = accentTeal.copy(alpha = 0.15f),
                                border = BorderStroke(0.8.dp, accentTeal.copy(alpha = 0.38f))
                            ) {
                                Text(
                                    text = opt.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = accentTeal,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 2. Chips de selección de las 5 Utilidades de Inspección y Seguridad
            val utilityTabs = if (isEn) {
                listOf(
                    Triple(4, "SHA-256 Duplicates", Icons.Default.FileCopy),
                    Triple(0, "Forensic Metadata & Hash", Icons.Default.VerifiedUser),
                    Triple(1, "QR Codes (PNG/SVG)", Icons.Default.QrCode2),
                    Triple(2, "Text Diff (LCS)", Icons.Default.Difference),
                    Triple(3, "Before / After", Icons.Default.Compare)
                )
            } else {
                listOf(
                    Triple(4, "Duplicados SHA-256", Icons.Default.FileCopy),
                    Triple(0, "Metadatos & SHA-256", Icons.Default.VerifiedUser),
                    Triple(1, "Códigos QR", Icons.Default.QrCode2),
                    Triple(2, "Comparador Diff", Icons.Default.Difference),
                    Triple(3, "Antes / Después", Icons.Default.Compare)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
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

            // 3. Espacio de trabajo interactivo en pantalla completa para la utilidad seleccionada
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
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
