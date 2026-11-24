package com.anywall.converter.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.anywall.converter.domain.model.InputFileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

/**
 * Información de una release publicada en GitHub Releases (`ArukuSynthesis/AnyWall`).
 */
data class GitHubReleaseInfo(
    val tagName: String,
    val title: String,
    val publishedAt: String,
    val htmlUrl: String,
    val apkDownloadUrl: String,
    val apkFileName: String,
    val apkSizeBytes: Long,
    val releaseNotes: String
)

/**
 * Información verificada localmente de un paquete `.apk` descargado en el dispositivo.
 */
data class LocalApkUpdateCandidate(
    val fileName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val sizeBytes: Long,
    val sha256Hex: String,
    val tempApkFile: File,
    val comparisonWithInstalled: Int
)

/**
 * Utilidad determinista para comparar versiones semánticas (`v0.4` vs `v0.5`, `0.4.1`, etc.)
 * y parsear respuestas JSON de GitHub Releases API sin dependencias externas.
 */
object GitHubUpdaterHelper {
    const val CURRENT_VERSION_NAME = "0.4"
    const val CURRENT_VERSION_CODE = 4L
    const val GITHUB_REPO_SLUG = "ArukuSynthesis/AnyWall"
    const val GITHUB_RELEASES_LATEST_URL = "https://github.com/ArukuSynthesis/AnyWall/releases/latest"
    const val GITHUB_RELEASES_LIST_URL = "https://github.com/ArukuSynthesis/AnyWall/releases"
    const val GITHUB_API_LATEST_RELEASE_URL = "https://api.github.com/repos/ArukuSynthesis/AnyWall/releases/latest"

    /**
     * Compara `currentVersion` con `remoteTag`.
     * Devuelve:
     * - `< 0` si `remoteTag` es más reciente que `currentVersion` (¡hay actualización disponible!)
     * - `0` si ambas versiones son equivalentes
     * - `> 0` si `currentVersion` es más reciente que `remoteTag`
     */
    fun compareVersions(currentVersion: String, remoteTag: String): Int {
        val currParts = parseNumericVersionParts(currentVersion)
        val remParts = parseNumericVersionParts(remoteTag)
        val maxLen = maxOf(currParts.size, remParts.size)
        for (i in 0 until maxLen) {
            val c = currParts.getOrElse(i) { 0 }
            val r = remParts.getOrElse(i) { 0 }
            if (c != r) return c.compareTo(r)
        }
        return 0
    }

    fun parseNumericVersionParts(rawVersion: String): List<Int> {
        val cleaned = rawVersion.trim().removePrefix("v").removePrefix("V")
        val mainSegment = cleaned.substringBefore('-').substringBefore('+')
        if (mainSegment.isBlank()) return listOf(0)
        return mainSegment.split('.')
            .map { token -> token.filter { it.isDigit() }.toIntOrNull() ?: 0 }
            .ifEmpty { listOf(0) }
    }

    /**
     * Extrae los campos relevantes del JSON devuelto por `GET /repos/ArukuSynthesis/AnyWall/releases/latest`.
     * Implementado con un parser ligero en Kotlin puro para funcionar tanto en Android como en JVM unit tests.
     */
    fun parseLatestReleaseJson(jsonText: String): GitHubReleaseInfo? {
        val trimmed = jsonText.trim()
        if (!trimmed.startsWith("{") || !trimmed.contains("\"tag_name\"")) return null

        val tagName = extractJsonStringField(trimmed, "tag_name").orEmpty().trim()
        if (tagName.isBlank()) return null

        val title = extractJsonStringField(trimmed, "name")?.takeIf { it.isNotBlank() } ?: tagName
        val publishedAt = extractJsonStringField(trimmed, "published_at").orEmpty().take(10)
        val htmlUrl = extractJsonStringField(trimmed, "html_url")
            ?.takeIf { it.isNotBlank() }
            ?: GITHUB_RELEASES_LATEST_URL
        val body = extractJsonStringField(trimmed, "body").orEmpty()

        // Buscar el primer asset con extensión .apk dentro de "assets": [ ... ]
        var apkUrl = ""
        var apkName = ""
        var apkSize = 0L

        val assetsIdx = trimmed.indexOf("\"assets\"")
        if (assetsIdx >= 0) {
            val assetsSection = trimmed.substring(assetsIdx)
            val urlRegex = Regex("\"browser_download_url\"\\s*:\\s*\"([^\"]+\\.apk)\"", RegexOption.IGNORE_CASE)
            val match = urlRegex.find(assetsSection)
            if (match != null) {
                apkUrl = unescapeJsonString(match.groupValues[1])
                apkName = apkUrl.substringAfterLast('/')
                // Buscar "size": <num> cerca del asset encontrado
                val windowStart = (match.range.first - 450).coerceAtLeast(0)
                val windowEnd = (match.range.last + 250).coerceAtMost(assetsSection.length)
                val assetWindow = assetsSection.substring(windowStart, windowEnd)
                val sizeMatch = Regex("\"size\"\\s*:\\s*(\\d+)").find(assetWindow)
                if (sizeMatch != null) {
                    apkSize = sizeMatch.groupValues[1].toLongOrNull() ?: 0L
                }
            }
        }

        if (apkUrl.isBlank()) {
            apkUrl = "$GITHUB_RELEASES_LATEST_URL"
            apkName = "AnyWall-$tagName-release.apk"
        }

        return GitHubReleaseInfo(
            tagName = tagName,
            title = title,
            publishedAt = publishedAt,
            htmlUrl = htmlUrl,
            apkDownloadUrl = apkUrl,
            apkFileName = apkName,
            apkSizeBytes = apkSize,
            releaseNotes = body
        )
    }

    private fun extractJsonStringField(json: String, key: String): String? {
        val pattern = Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        val match = pattern.find(json) ?: return null
        return unescapeJsonString(match.groupValues[1])
    }

    private fun unescapeJsonString(raw: String): String {
        val sb = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val ch = raw[i]
            if (ch == '\\' && i + 1 < raw.length) {
                when (val next = raw[i + 1]) {
                    'n' -> {
                        sb.append('\n')
                        i += 2
                    }
                    'r' -> {
                        sb.append('\r')
                        i += 2
                    }
                    't' -> {
                        sb.append('\t')
                        i += 2
                    }
                    '"' -> {
                        sb.append('"')
                        i += 2
                    }
                    '\\' -> {
                        sb.append('\\')
                        i += 2
                    }
                    '/' -> {
                        sb.append('/')
                        i += 2
                    }
                    else -> {
                        sb.append(next)
                        i += 2
                    }
                }
            } else {
                sb.append(ch)
                i++
            }
        }
        return sb.toString()
    }
}

/**
 * Diálogo provisional de Actualizaciones desde GitHub Releases (`GitHubUpdaterDialog` - v0.4).
 *
 * - Permite consultar la última release en `github.com/ArukuSynthesis/AnyWall/releases/latest`.
 * - En compilaciones con 0 permisos de red (donde el proceso de la app no abre sockets directos),
 *   permite abrir con 1 toque la página oficial de GitHub Releases en el navegador del sistema
 *   (`Intent.ACTION_VIEW` no requiere permisos de red en el APK) y verificar/instalar el `.apk` descargado
 *   comparando `versionName`, `versionCode` y huella `SHA-256`.
 */
@Composable
fun GitHubUpdaterDialog(
    isEn: Boolean = false,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isCheckingRemote by remember { mutableStateOf(false) }
    var remoteRelease by remember { mutableStateOf<GitHubReleaseInfo?>(null) }
    var checkStatusMessage by remember { mutableStateOf<String?>(null) }
    var isZeroNetworkBuildFallback by remember { mutableStateOf(false) }

    var isInspectingApk by remember { mutableStateOf(false) }
    var localApkCandidate by remember { mutableStateOf<LocalApkUpdateCandidate?>(null) }

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
        val msg = if (isEn) "Copied: $text" else "Copiado: $text"
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    fun openExternalUrl(url: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure {
            copyToClipboard("GitHub Releases URL", url)
        }
    }

    val apkPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            isInspectingApk = true
            scope.launch {
                val candidate = withContext(Dispatchers.IO) {
                    inspectDownloadedApkUri(context, uri)
                }
                isInspectingApk = false
                if (candidate != null) {
                    localApkCandidate = candidate
                } else {
                    checkStatusMessage = if (isEn) {
                        "The selected file is not a valid Android APK package."
                    } else {
                        "El archivo seleccionado no es un paquete APK válido."
                    }
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = if (isEn) "GitHub Releases Updater" else "Actualizador desde GitHub",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = GitHubUpdaterHelper.GITHUB_REPO_SLUG,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 470.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Tarjeta de versión instalada actualmente
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (isEn) "Installed Version" else "Versión instalada actualmente",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        Text(
                            text = "AnyWall v${GitHubUpdaterHelper.CURRENT_VERSION_NAME} (build ${GitHubUpdaterHelper.CURRENT_VERSION_CODE})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = if (isEn) {
                                "Author: SynthesizerEnd • Channel: GitHub Releases"
                            } else {
                                "Autor: SynthesizerEnd • Canal: GitHub Releases"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                        )
                    }
                }

                // Botón principal: Buscar actualizaciones en GitHub
                Button(
                    onClick = {
                        isCheckingRemote = true
                        checkStatusMessage = null
                        isZeroNetworkBuildFallback = false
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                fetchLatestReleaseFromGitHub()
                            }
                            isCheckingRemote = false
                            result.onSuccess { info ->
                                remoteRelease = info
                                val cmp = GitHubUpdaterHelper.compareVersions(
                                    currentVersion = GitHubUpdaterHelper.CURRENT_VERSION_NAME,
                                    remoteTag = info.tagName
                                )
                                checkStatusMessage = when {
                                    cmp < 0 -> if (isEn) {
                                        "New version available on GitHub: ${info.tagName}!"
                                    } else {
                                        "¡Nueva actualización disponible en GitHub: ${info.tagName}!"
                                    }
                                    cmp == 0 -> if (isEn) {
                                        "You already have the latest version (${info.tagName})."
                                    } else {
                                        "Ya tienes instalada la última versión (${info.tagName})."
                                    }
                                    else -> if (isEn) {
                                        "Installed version (v${GitHubUpdaterHelper.CURRENT_VERSION_NAME}) is newer than remote (${info.tagName})."
                                    } else {
                                        "La versión instalada (v${GitHubUpdaterHelper.CURRENT_VERSION_NAME}) es más reciente que la publicada (${info.tagName})."
                                    }
                                }
                            }.onFailure { err ->
                                val isNoNetworkPerm = err is SecurityException
                                isZeroNetworkBuildFallback = true
                                checkStatusMessage = if (isNoNetworkPerm) {
                                    if (isEn) {
                                        "This APK is compiled with 0 network permissions. Tap below to check & download the latest release directly in your browser, then select the APK to verify and install it."
                                    } else {
                                        "Este APK está compilado con 0 permisos de red. Pulsa abajo para abrir la última release de GitHub en tu navegador y luego selecciona el APK descargado para verificarlo e instalarlo."
                                    }
                                } else {
                                    if (isEn) {
                                        "Could not query GitHub API directly (${err.javaClass.simpleName}). You can open GitHub Releases in your browser below."
                                    } else {
                                        "No se pudo consultar la API directamente (${err.javaClass.simpleName}). Puedes abrir GitHub Releases en tu navegador abajo."
                                    }
                                }
                            }
                        }
                    },
                    enabled = !isCheckingRemote,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isCheckingRemote) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isEn) "Check for Updates on GitHub" else "Buscar actualizaciones en GitHub",
                        fontWeight = FontWeight.Bold
                    )
                }

                // Mensaje de estado o resultado de la consulta
                if (checkStatusMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = checkStatusMessage.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            if (isZeroNetworkBuildFallback) {
                                FilledTonalButton(
                                    onClick = { openExternalUrl(GitHubUpdaterHelper.GITHUB_RELEASES_LATEST_URL) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.OpenInBrowser,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        if (isEn) "Open Latest Release on GitHub"
                                        else "Abrir última Release en GitHub (latest)"
                                    )
                                }
                            }
                        }
                    }
                }

                // Tarjeta con detalle de la release remota si se obtuvo por API
                val release = remoteRelease
                if (release != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NewReleases,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "${release.title} (${release.tagName})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (release.publishedAt.isNotBlank()) {
                                Text(
                                    text = if (isEn) "Published: ${release.publishedAt}" else "Publicado: ${release.publishedAt}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (release.releaseNotes.isNotBlank()) {
                                Text(
                                    text = release.releaseNotes.take(420),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = { openExternalUrl(release.apkDownloadUrl) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isEn) "Download ${release.apkFileName}"
                                    else "Descargar ${release.apkFileName}"
                                )
                            }
                        }
                    }
                }

                // Acciones rápidas hacia GitHub Releases (funcionan con 0 permisos de red)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { openExternalUrl(GitHubUpdaterHelper.GITHUB_RELEASES_LATEST_URL) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInBrowser,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isEn) "Latest Release" else "Última Release")
                    }
                    OutlinedButton(
                        onClick = {
                            copyToClipboard(
                                "AnyWall Releases",
                                GitHubUpdaterHelper.GITHUB_RELEASES_LATEST_URL
                            )
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                HorizontalDivider()

                // Verificador e instalador local de APK descargado
                Text(
                    text = if (isEn) {
                        "Verify & Install Downloaded APK"
                    } else {
                        "Verificar e instalar APK descargado"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isEn) {
                        "Select an AnyWall-*.apk downloaded from GitHub Releases to verify its version, package signature and SHA-256 checksum before installing."
                    } else {
                        "Selecciona un archivo AnyWall-*.apk descargado desde GitHub Releases para comprobar su versión, identificador de paquete y suma SHA-256 antes de lanzar el instalador."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FilledTonalButton(
                    onClick = {
                        apkPickerLauncher.launch(
                            arrayOf("application/vnd.android.package-archive", "*/*")
                        )
                    },
                    enabled = !isInspectingApk,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isInspectingApk) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isEn) "Select downloaded .apk file"
                        else "Seleccionar archivo .apk descargado"
                    )
                }

                val candidate = localApkCandidate
                if (candidate != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "${candidate.fileName} (${InputFileItem.formatBytes(candidate.sizeBytes)})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            val statusBadge = when {
                                candidate.packageName != context.packageName -> if (isEn) {
                                    "Warning: Package ID differs (${candidate.packageName})"
                                } else {
                                    "Aviso: El paquete difiere (${candidate.packageName})"
                                }
                                candidate.comparisonWithInstalled < 0 -> if (isEn) {
                                    "Newer update detected (v${candidate.versionName} • build ${candidate.versionCode})"
                                } else {
                                    "¡Actualización más reciente detectada! (v${candidate.versionName} • build ${candidate.versionCode})"
                                }
                                candidate.comparisonWithInstalled == 0 -> if (isEn) {
                                    "Same version as installed (v${candidate.versionName} • build ${candidate.versionCode})"
                                } else {
                                    "Misma versión que la instalada (v${candidate.versionName} • build ${candidate.versionCode})"
                                }
                                else -> if (isEn) {
                                    "Older version (v${candidate.versionName} • build ${candidate.versionCode})"
                                } else {
                                    "Versión anterior a la instalada (v${candidate.versionName} • build ${candidate.versionCode})"
                                }
                            }

                            Text(
                                text = statusBadge,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "SHA-256: ${candidate.sha256Hex.take(24)}…",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { copyToClipboard("APK SHA-256", candidate.sha256Hex) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    launchPackageInstallerForTempApk(context, candidate.tempApkFile, isEn)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SystemUpdate,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isEn) "Install APK Update" else "Instalar actualización APK",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
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

private fun fetchLatestReleaseFromGitHub(): Result<GitHubReleaseInfo> = runCatching {
    val url = URL(GitHubUpdaterHelper.GITHUB_API_LATEST_RELEASE_URL)
    val conn = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 7000
        readTimeout = 7000
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("User-Agent", "AnyWall-Universal-Converter/${GitHubUpdaterHelper.CURRENT_VERSION_NAME}")
    }
    try {
        val code = conn.responseCode
        if (code !in 200..299) {
            throw IllegalStateException("HTTP $code")
        }
        val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        GitHubUpdaterHelper.parseLatestReleaseJson(body)
            ?: throw IllegalStateException("Invalid GitHub Release JSON")
    } finally {
        conn.disconnect()
    }
}

private fun inspectDownloadedApkUri(context: Context, uri: Uri): LocalApkUpdateCandidate? {
    return try {
        val workDir = File(context.cacheDir, "anywall_work").apply { mkdirs() }
        val tempApk = File(workDir, "anywall_update_candidate.apk")
        val digest = MessageDigest.getInstance("SHA-256")
        var totalBytes = 0L

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempApk).use { output ->
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val read = input.read(buf)
                    if (read <= 0) break
                    digest.update(buf, 0, read)
                    output.write(buf, 0, read)
                    totalBytes += read
                }
            }
        } ?: return null

        if (totalBytes <= 64L) return null

        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(tempApk.absolutePath, PackageManager.PackageInfoFlags.of(0L))
        } else {
            pm.getPackageArchiveInfo(tempApk.absolutePath, 0)
        } ?: return null

        val verName = pkgInfo.versionName ?: "0.0"
        @Suppress("DEPRECATION")
        val verCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pkgInfo.longVersionCode
        } else {
            pkgInfo.versionCode.toLong()
        }

        val shaHex = digest.digest().joinToString("") { b ->
            String.format(Locale.US, "%02x", b.toInt() and 0xFF)
        }

        val cmpByCode = GitHubUpdaterHelper.CURRENT_VERSION_CODE.compareTo(verCode)
        val cmp = if (cmpByCode != 0) {
            cmpByCode
        } else {
            GitHubUpdaterHelper.compareVersions(GitHubUpdaterHelper.CURRENT_VERSION_NAME, verName)
        }

        LocalApkUpdateCandidate(
            fileName = "AnyWall-v$verName.apk",
            packageName = pkgInfo.packageName.orEmpty(),
            versionName = verName,
            versionCode = verCode,
            sizeBytes = totalBytes,
            sha256Hex = shaHex,
            tempApkFile = tempApk,
            comparisonWithInstalled = cmp
        )
    } catch (_: Exception) {
        null
    }
}

private fun launchPackageInstallerForTempApk(context: Context, apkFile: File, isEn: Boolean) {
    runCatching {
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
    }.onFailure { err ->
        val msg = if (isEn) {
            "Could not open system package installer: ${err.localizedMessage ?: err.javaClass.simpleName}"
        } else {
            "No se pudo abrir el instalador del sistema: ${err.localizedMessage ?: err.javaClass.simpleName}"
        }
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    }
}
