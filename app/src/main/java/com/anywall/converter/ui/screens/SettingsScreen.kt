package com.anywall.converter.ui.screens

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anywall.converter.core.AppAccentPalette
import com.anywall.converter.core.AppLanguageMode
import com.anywall.converter.core.AppThemeMode
import com.anywall.converter.core.OutputSubfolderMode
import com.anywall.converter.domain.model.InputFileItem
import com.anywall.converter.ui.components.AboutCreditsDialog
import com.anywall.converter.ui.components.AnyWallCreditsCatalog
import com.anywall.converter.ui.components.GitHubUpdaterDialog

/**
 * Pantalla completa de Ajustes y Personalización (`SettingsScreen` - v0.5).
 * Reemplaza el antiguo `AlertDialog` saturado por una pestaña nativa en la barra de navegación inferior,
 * organizada en tarjetas modernas:
 * - Cabecera del proyecto `AnyWall v0.5 • SynthesizerEnd` con acceso directo a Actualizaciones (GitHub) y Créditos
 * - Apariencia: Modo de Tema (`Sistema`, `Claro`, `Oscuro`, `Negro AMOLED #000000`), Material You,
 *   selector visual de muestras de color para las 6 paletas de acento e idioma (`Sistema`, `Español`, `English`)
 * - Privacidad y Seguridad: limpieza EXIF/GPS por defecto y bloqueo Biométrico/PIN del historial
 * - Almacenamiento y Caché: subcarpetas en `Downloads/AnyWall/` y gestión de caché temporal
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    currentThemeMode: AppThemeMode,
    useDynamicColor: Boolean,
    accentPalette: AppAccentPalette,
    currentLanguageMode: AppLanguageMode,
    outputSubfolderMode: OutputSubfolderMode,
    customSubfolderName: String,
    biometricHistoryLockEnabled: Boolean,
    historyPinCode: String,
    autoCleanTempCache: Boolean,
    defaultStripExif: Boolean,
    tempCacheSizeBytes: Long,
    onSelectThemeMode: (AppThemeMode) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
    onSelectAccentPalette: (AppAccentPalette) -> Unit,
    onSelectLanguageMode: (AppLanguageMode) -> Unit,
    onSelectOutputSubfolderMode: (OutputSubfolderMode) -> Unit,
    onUpdateCustomSubfolderName: (String) -> Unit,
    onToggleBiometricHistoryLock: (Boolean) -> Unit,
    onUpdateHistoryPinCode: (String) -> Unit,
    onToggleAutoCleanCache: (Boolean) -> Unit,
    onToggleDefaultStripExif: (Boolean) -> Unit,
    onClearTempCacheNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isEn = currentLanguageMode.isEnglishActive()
    var showGitHubUpdater by remember { mutableStateOf(false) }
    var showCreditsDialog by remember { mutableStateOf(false) }

    if (showGitHubUpdater) {
        GitHubUpdaterDialog(
            isEn = isEn,
            onDismiss = { showGitHubUpdater = false }
        )
    }

    if (showCreditsDialog) {
        AboutCreditsDialog(
            onDismiss = { showCreditsDialog = false }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isEn) "Settings & Appearance" else "Ajustes y Apariencia",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "AnyWall v0.5 • ${AnyWallCreditsCatalog.DEVELOPER_NAME}",
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Tarjeta Hero de Versión, Actualizador GitHub y Créditos
            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                        Color.Transparent
                                    )
                                )
                            )
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "AnyWall: Universal Converter v0.5",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = if (isEn) {
                                        "By ${AnyWallCreditsCatalog.DEVELOPER_NAME} • Build 5 (AGPL-3.0-only)"
                                    } else {
                                        "Por ${AnyWallCreditsCatalog.DEVELOPER_NAME} • Build 5 (AGPL-3.0-only)"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { showGitHubUpdater = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SystemUpdate,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isEn) "Updates" else "Actualizaciones",
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            OutlinedButton(
                                onClick = { showCreditsDialog = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isEn) "Credits" else "Créditos",
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // 2. Grupo Apariencia, Tema AMOLED y Paletas de Acento
            item {
                SettingsGroupCard(
                    title = if (isEn) "Appearance & Theme" else "Apariencia y Tema Visual",
                    icon = Icons.Default.DarkMode
                ) {
                    Text(
                        text = if (isEn) "Theme Mode" else "Modo de pantalla",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AppThemeMode.entries.forEach { mode ->
                            val selected = currentThemeMode == mode
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectThemeMode(mode) },
                                shape = RoundedCornerShape(12.dp),
                                label = {
                                    Text(
                                        text = mode.localizedLabel(isEn),
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isEn) "Material You (Dynamic Color)" else "Material You (Colores dinámicos)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (isEn) "Adapt color palette to your wallpaper" else "Toma los colores del fondo de pantalla de Android 12+",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = useDynamicColor,
                                onCheckedChange = onToggleDynamicColor
                            )
                        }
                    }

                    if (!useDynamicColor || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                        Text(
                            text = if (isEn) "Studio Accent Palette" else "Paleta de acento del estudio",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // Selector visual con muestras circulares de color + nombre
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AppAccentPalette.entries.forEach { palette ->
                                val isSelected = accentPalette == palette
                                val swatchColor = Color(palette.previewColorHex.toInt())
                                Surface(
                                    onClick = { onSelectAccentPalette(palette) },
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                    } else {
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
                                    },
                                    border = BorderStroke(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) swatchColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clip(CircleShape)
                                                .background(swatchColor)
                                                .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = palette.localizedLabel(isEn),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (isEn) "Interface Language" else "Idioma de la interfaz",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AppLanguageMode.entries.forEach { lang ->
                            val selected = currentLanguageMode == lang
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectLanguageMode(lang) },
                                shape = RoundedCornerShape(12.dp),
                                label = {
                                    Text(
                                        text = if (isEn) lang.labelEn else lang.labelEs,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // 3. Grupo Privacidad y Seguridad
            item {
                SettingsGroupCard(
                    title = if (isEn) "Privacy & History Lock" else "Privacidad y Bloqueo",
                    icon = Icons.Default.Security
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isEn) "Strip EXIF/GPS Metadata by default" else "Limpiar metadatos EXIF/GPS por defecto",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (isEn) "Automatically remove GPS location and camera metadata from converted images" else "Elimina automáticamente coordenadas GPS y datos de cámara al convertir imágenes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = defaultStripExif,
                            onCheckedChange = onToggleDefaultStripExif
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fingerprint,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (isEn) "Lock History (Biometric / PIN)" else "Bloquear Historial (Huella / PIN)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = if (isEn) "Require fingerprint or custom PIN to view converted files" else "Solicita huella del dispositivo o PIN personalizado al abrir el Historial",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = biometricHistoryLockEnabled,
                            onCheckedChange = onToggleBiometricHistoryLock
                        )
                    }

                    if (biometricHistoryLockEnabled) {
                        OutlinedTextField(
                            value = historyPinCode,
                            onValueChange = onUpdateHistoryPinCode,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            label = {
                                Text(
                                    if (isEn) "Backup PIN (optional, e.g. 1234)"
                                    else "PIN de acceso rápido (opcional, ej. 1234)"
                                )
                            }
                        )
                    }
                }
            }

            // 4. Grupo Almacenamiento y Caché Temporal
            item {
                SettingsGroupCard(
                    title = if (isEn) "Storage & Temp Cache" else "Almacenamiento y Caché",
                    icon = Icons.Default.Folder
                ) {
                    Text(
                        text = if (isEn) "Folder structure in Downloads/AnyWall/" else "Organización de carpetas en Downloads/AnyWall/",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutputSubfolderMode.entries.forEach { mode ->
                            val selected = outputSubfolderMode == mode
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectOutputSubfolderMode(mode) },
                                shape = RoundedCornerShape(12.dp),
                                label = {
                                    Text(
                                        text = mode.localizedLabel(isEn),
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            )
                        }
                    }

                    if (outputSubfolderMode == OutputSubfolderMode.CUSTOM) {
                        OutlinedTextField(
                            value = customSubfolderName,
                            onValueChange = onUpdateCustomSubfolderName,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            label = {
                                Text(
                                    if (isEn) "Custom subfolder name"
                                    else "Nombre de subcarpeta personalizada"
                                )
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isEn) "Auto-clean temp cache on startup" else "Autolimpiar caché temporal al iniciar",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (isEn) "Automatically deletes temporary working files when idle" else "Elimina archivos temporales de trabajo cuando no hay tareas activas",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = autoCleanTempCache,
                            onCheckedChange = onToggleAutoCleanCache
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isEn) "Working Temp Cache" else "Caché temporal de trabajo",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = InputFileItem.formatBytes(tempCacheSizeBytes),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            FilledTonalButton(
                                onClick = onClearTempCacheNow,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CleaningServices,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isEn) "Clear now" else "Liberar")
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
}

@Composable
private fun SettingsGroupCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
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
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            content()
        }
    }
}
