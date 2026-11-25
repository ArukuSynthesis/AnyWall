package com.anywall.converter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anywall.converter.core.AppAccentPalette
import com.anywall.converter.core.AppLanguageMode
import com.anywall.converter.core.AppThemeMode
import com.anywall.converter.core.OutputSubfolderMode
import com.anywall.converter.domain.model.InputFileItem

/**
 * Diálogo de Ajustes y Personalización de AnyWall (v0.4):
 * - Selector de tema visual (`SYSTEM`, `LIGHT`, `DARK`, `AMOLED_BLACK` `#000000`).
 * - Material You (`useDynamicColor`) y paleta de acento (`INDIGO_STUDIO`, `EMERALD`, `AMBER`, `ROSE`, `CYAN`, `MONOCHROME`).
 * - Selector de idioma bilingüe (`Sistema`, `Español`, `English`).
 * - Modo de organización de subcarpetas (`Downloads/AnyWall/`).
 * - Bloqueo biométrico / PIN opcional para la pestaña de Historial.
 * - Limpieza EXIF/GPS por defecto y gestión de caché temporal.
 * - Acceso directo a Créditos (`SynthesizerEnd`) y Licencias Open Source.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsDialog(
    currentThemeMode: AppThemeMode,
    useDynamicColor: Boolean = false,
    accentPalette: AppAccentPalette = AppAccentPalette.INDIGO_STUDIO,
    currentLanguageMode: AppLanguageMode = AppLanguageMode.ES,
    outputSubfolderMode: OutputSubfolderMode = OutputSubfolderMode.FLAT,
    customSubfolderName: String = "",
    biometricHistoryLockEnabled: Boolean = false,
    historyPinCode: String = "",
    autoCleanTempCache: Boolean,
    defaultStripExif: Boolean,
    tempCacheSizeBytes: Long,
    onSelectThemeMode: (AppThemeMode) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit = {},
    onSelectAccentPalette: (AppAccentPalette) -> Unit = {},
    onSelectLanguageMode: (AppLanguageMode) -> Unit = {},
    onSelectOutputSubfolderMode: (OutputSubfolderMode) -> Unit = {},
    onUpdateCustomSubfolderName: (String) -> Unit = {},
    onToggleBiometricHistoryLock: (Boolean) -> Unit = {},
    onUpdateHistoryPinCode: (String) -> Unit = {},
    onToggleAutoCleanCache: (Boolean) -> Unit,
    onToggleDefaultStripExif: (Boolean) -> Unit,
    onClearTempCacheNow: () -> Unit,
    onOpenCredits: () -> Unit,
    onDismiss: () -> Unit
) {
    val isEn = currentLanguageMode.isEnglishActive()
    var showGitHubUpdater by remember { mutableStateOf(false) }

    if (showGitHubUpdater) {
        GitHubUpdaterDialog(
            isEn = isEn,
            onDismiss = { showGitHubUpdater = false }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = if (isEn) "Settings • AnyWall v0.5" else "Ajustes • AnyWall v0.5",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isEn) "Developed by ${AnyWallCreditsCatalog.DEVELOPER_NAME}" else "Desarrollado por ${AnyWallCreditsCatalog.DEVELOPER_NAME}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Selector de Tema Visual (incluye AMOLED_BLACK #000000)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DarkMode,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (isEn) "Interface Theme" else "Tema de la interfaz",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AppThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = currentThemeMode == mode,
                                onClick = { onSelectThemeMode(mode) },
                                label = { Text(mode.localizedLabel(isEn)) }
                            )
                        }
                    }
                }

                // 2. Material You (useDynamicColor) y Paleta de Acento
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                    imageVector = Icons.Default.Palette,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (isEn) "Material You Dynamic Color" else "Colores Dinámicos (Material You)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = if (isEn) "Adapt accent colors to system wallpaper (Android 12+)" else "Adapta la paleta al fondo de pantalla del sistema (Android 12+)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = useDynamicColor,
                            onCheckedChange = onToggleDynamicColor
                        )
                    }

                    Text(
                        text = if (isEn) "Studio Accent Palette" else "Paleta de acento del estudio",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AppAccentPalette.entries.forEach { palette ->
                            FilterChip(
                                selected = accentPalette == palette,
                                onClick = {
                                    if (useDynamicColor) onToggleDynamicColor(false)
                                    onSelectAccentPalette(palette)
                                },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(Color(palette.previewColorHex))
                                    )
                                },
                                label = { Text(palette.localizedLabel(isEn)) }
                            )
                        }
                    }
                }

                HorizontalDivider()

                // 3. Selector de Idioma Bilingüe (ES / EN)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                            text = if (isEn) "Language / Idioma" else "Idioma / Language",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AppLanguageMode.entries.forEach { lang ->
                            FilterChip(
                                selected = currentLanguageMode == lang,
                                onClick = { onSelectLanguageMode(lang) },
                                label = { Text(if (isEn) lang.labelEn else lang.labelEs) }
                            )
                        }
                    }
                }

                HorizontalDivider()

                // 4. Organización de subcarpetas en Downloads/AnyWall/
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (isEn) "Output Folder Organization" else "Organización en Downloads/AnyWall/",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        OutputSubfolderMode.entries.forEach { mode ->
                            FilterChip(
                                selected = outputSubfolderMode == mode,
                                onClick = { onSelectOutputSubfolderMode(mode) },
                                label = { Text(mode.localizedLabel(isEn)) }
                            )
                        }
                    }
                    if (outputSubfolderMode == OutputSubfolderMode.CUSTOM) {
                        OutlinedTextField(
                            value = customSubfolderName,
                            onValueChange = onUpdateCustomSubfolderName,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text(if (isEn) "Custom subfolder name" else "Nombre de subcarpeta personalizada") }
                        )
                    }
                }

                HorizontalDivider()

                // 5. Bloqueo Biométrico / PIN del Historial
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                text = if (isEn) "Require device lock or custom PIN to view converted files" else "Solicita huella/credencial del dispositivo o PIN al abrir el Historial",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
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
                            label = { Text(if (isEn) "Backup PIN (optional)" else "PIN de acceso rápido (opcional, ej. 1234)") }
                        )
                    }
                }

                HorizontalDivider()

                // 6. Limpieza EXIF/GPS por defecto
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
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (isEn) "Strip EXIF/GPS Metadata" else "Limpiar metadatos EXIF/GPS",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = if (isEn) "Remove GPS and camera metadata by default" else "Elimina por defecto ubicación y datos de cámara/autor",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = defaultStripExif,
                        onCheckedChange = onToggleDefaultStripExif
                    )
                }

                HorizontalDivider()

                // 7. Gestión de caché temporal
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isEn) "Auto-clean temp cache on startup" else "Autolimpiar temporales al iniciar",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (isEn) "Clears temporary files when idle" else "Borra residuos de caché cuando no hay conversiones activas",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = autoCleanTempCache,
                        onCheckedChange = onToggleAutoCleanCache
                    )
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
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
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        FilledTonalButton(onClick = onClearTempCacheNow) {
                            Icon(
                                imageVector = Icons.Default.CleaningServices,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isEn) "Clear" else "Liberar")
                        }
                    }
                }

                // 8. Botón para buscar actualizaciones desde GitHub Releases
                FilledTonalButton(
                    onClick = { showGitHubUpdater = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isEn) "Check for Updates (GitHub)" else "Buscar actualizaciones (GitHub)")
                }

                // 9. Botón para abrir Créditos y Licencias de Código Abierto
                OutlinedButton(
                    onClick = onOpenCredits,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isEn) "Credits & Open Source Licenses" else "Créditos y Licencias Open Source")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isEn) "Close" else "Cerrar")
            }
        }
    )
}
