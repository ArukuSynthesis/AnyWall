package com.anywall.converter.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Representa el crédito y atribución de derechos de autor de una librería de código abierto
 * utilizada en AnyWall: Universal Converter.
 */
data class OpenSourceCreditItem(
    val name: String,
    val artifact: String,
    val author: String,
    val license: String,
    val repositoryUrl: String,
    val roleDescriptionEs: String
)

/**
 * Catálogo oficial de autoría y repositorios de terceros utilizados en AnyWall.
 */
object AnyWallCreditsCatalog {
    const val DEVELOPER_NAME = "SynthesizerEnd"
    const val PROJECT_REPOSITORY_URL = "https://github.com/ArukuSynthesis/AnyWall"
    const val PROJECT_LICENSE = "GNU Affero General Public License v3.0 (AGPL-3.0)"

    val thirdPartyLibraries: List<OpenSourceCreditItem> = listOf(
        OpenSourceCreditItem(
            name = "AndroidX & Jetpack Compose (Material 3, Room, WorkManager, Navigation, Lifecycle)",
            artifact = "androidx.compose.*, androidx.room:2.6.1, androidx.work:2.10.0",
            author = "Google LLC & The Android Open Source Project (AOSP)",
            license = "Apache License 2.0",
            repositoryUrl = "https://github.com/androidx/androidx",
            roleDescriptionEs = "Interfaz nativa Material 3, persistencia SQLite con Room y ejecución en segundo plano con WorkManager."
        ),
        OpenSourceCreditItem(
            name = "AndroidX Media3 (Transformer, Effect, Common, Muxer)",
            artifact = "androidx.media3:media3-*:1.4.1",
            author = "Google LLC & AndroidX Media3 Contributors",
            license = "Apache License 2.0",
            repositoryUrl = "https://github.com/androidx/media",
            roleDescriptionEs = "Transcodificación local de video, escalado de resolución, recorte temporal y extracción de pistas de audio."
        ),
        OpenSourceCreditItem(
            name = "PDFBox-Android",
            artifact = "com.tom-roush:pdfbox-android:2.0.27.0",
            author = "Tom Roush & The Apache Software Foundation (Apache PDFBox)",
            license = "Apache License 2.0",
            repositoryUrl = "https://github.com/TomRoush/PdfBox-Android",
            roleDescriptionEs = "Unión de archivos PDF, división/extracción de páginas, compresión, cifrado con contraseña y extracción de texto."
        ),
        OpenSourceCreditItem(
            name = "Java Mammoth (DOCX to HTML Converter)",
            artifact = "org.zwobble.mammoth:mammoth:1.5.0",
            author = "Michael Williamson (mwilliamson)",
            license = "BSD 2-Clause \"Simplified\" License",
            repositoryUrl = "https://github.com/mwilliamson/java-mammoth",
            roleDescriptionEs = "Conversión semántica de documentos Microsoft Word (.docx) a HTML limpio para renderizado local a PDF."
        ),
        OpenSourceCreditItem(
            name = "AndroidSVG",
            artifact = "com.caverock:androidsvg-aar:1.4",
            author = "Paul LeBeau (BigBadaboom)",
            license = "Apache License 2.0",
            repositoryUrl = "https://github.com/BigBadaboom/androidsvg",
            roleDescriptionEs = "Parser y renderizador de gráficos vectoriales SVG sobre Canvas y Bitmap nativos de Android."
        ),
        OpenSourceCreditItem(
            name = "Apache Commons Compress",
            artifact = "org.apache.commons:commons-compress:1.26.1",
            author = "The Apache Software Foundation",
            license = "Apache License 2.0",
            repositoryUrl = "https://github.com/apache/commons-compress",
            roleDescriptionEs = "Empaquetado y extracción de archivos comprimidos ZIP, TAR, TAR.GZ, TAR.XZ, TAR.BZ2, GZ, BZ2 y 7Z."
        ),
        OpenSourceCreditItem(
            name = "XZ for Java",
            artifact = "org.tukaani:xz:1.9",
            author = "Lasse Collin & The Tukaani Project",
            license = "Public Domain (0BSD / Dominio Público)",
            repositoryUrl = "https://github.com/tukaani-project/xz-java",
            roleDescriptionEs = "Algoritmos de compresión y descompresión LZMA, LZMA2 y contenedores .xz / .7z."
        ),
        OpenSourceCreditItem(
            name = "jump3r — Java LAME MP3 Encoder",
            artifact = "de.sciss:jump3r:1.0.5",
            author = "Hanns Holger Rutz (Sciss), Ken Händel & The LAME Project",
            license = "GNU Lesser General Public License v2.1+ (LGPL-2.1-or-later)",
            repositoryUrl = "https://github.com/Sciss/jump3r",
            roleDescriptionEs = "Motor de codificación de audio MP3 en Java puro basado en LAME 3.98.4."
        ),
        OpenSourceCreditItem(
            name = "ZXing (\"Zebra Crossing\") Core QR Engine",
            artifact = "com.google.zxing:core:3.5.3",
            author = "ZXing Authors & Contributors",
            license = "Apache License 2.0",
            repositoryUrl = "https://github.com/zxing/zxing",
            roleDescriptionEs = "Generación y decodificación 100% offline de matrices de códigos QR en PNG, SVG y sello PDF."
        ),
        OpenSourceCreditItem(
            name = "Kotlin Standard Library & Kotlinx Coroutines",
            artifact = "org.jetbrains.kotlin:2.0.21, kotlinx-coroutines-android:1.9.0",
            author = "JetBrains s.r.o. & Contributors",
            license = "Apache License 2.0",
            repositoryUrl = "https://github.com/JetBrains/kotlin",
            roleDescriptionEs = "Lenguaje base y concurrencia asíncrona estructurada."
        )
    )
}

/**
 * Diálogo 100% offline de Créditos, Autoría (`SynthesizerEnd`) y Licencias de Código Abierto.
 * Permite copiar las URLs de los repositorios al portapapeles sin realizar conexiones de red.
 */
@Composable
fun AboutCreditsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "Copiado al portapapeles: $text", Toast.LENGTH_SHORT).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Acerca de AnyWall y Créditos",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                OfflinePrivacyBadge()
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Tarjeta destacada del desarrollador: SynthesizerEnd
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Desarrollado por ${AnyWallCreditsCatalog.DEVELOPER_NAME}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "AnyWall: Universal Converter v0.3",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        copyToClipboard(
                                            "Repositorio AnyWall",
                                            AnyWallCreditsCatalog.PROJECT_REPOSITORY_URL
                                        )
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copiar URL del repositorio",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Text(
                                text = "Repositorio oficial: ${AnyWallCreditsCatalog.PROJECT_REPOSITORY_URL}",
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Licencia del proyecto: ${AnyWallCreditsCatalog.PROJECT_LICENSE}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                            )
                        }
                    }
                }

                item {
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Repositorios y Librerías de Código Abierto",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "AnyWall reconoce y agradece a los autores de los siguientes proyectos open source que hacen posible el procesamiento 100% local:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                items(
                    items = AnyWallCreditsCatalog.thirdPartyLibraries,
                    key = { it.name }
                ) { credit ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = credit.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        copyToClipboard(credit.name, credit.repositoryUrl)
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copiar URL de ${credit.name}",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Text(
                                text = "Autor: ${credit.author}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                            ) {
                                Text(
                                    text = credit.license,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }

                            Text(
                                text = credit.roleDescriptionEs,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Text(
                                text = credit.repositoryUrl,
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}
