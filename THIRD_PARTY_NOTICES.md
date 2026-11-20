# Créditos, Autoría y Repositorios de Terceros — AnyWall: Universal Converter

## 1. Autoría del Proyecto Principal

- **Proyecto:** AnyWall: Universal Converter (`com.anywall.converter`)
- **Desarrollador / Autor Principal:** **SynthesizerEnd**
- **Repositorio Oficial:** https://github.com/ArukuSynthesis/AnyWall
- **Identificador SPDX de Licencia:** `AGPL-3.0-only` (ver archivo [LICENSE](LICENSE))

---

## 2. Repositorios y Librerías de Código Abierto Utilizados

**AnyWall: Universal Converter** utiliza las siguientes bibliotecas de código abierto verificadas desde Google Maven y Maven Central para ejecutar todo el procesamiento multimedia y documental de forma 100% local:

| Componente | Artefacto Maven | Autor / Titular | Licencia SPDX | Repositorio Oficial |
| :--- | :--- | :--- | :--- | :--- |
| **AndroidX & Jetpack Compose** | `androidx.compose.*`, `androidx.room:2.6.1`, `androidx.work:2.10.0`, `androidx.navigation:2.8.5`, `androidx.lifecycle:2.8.7` | Google LLC / AOSP | `Apache-2.0` | https://github.com/androidx/androidx |
| **AndroidX Media3** | `androidx.media3:media3-{transformer,effect,common,muxer}:1.4.1` | Google LLC / AndroidX Media3 | `Apache-2.0` | https://github.com/androidx/media |
| **PDFBox-Android** | `com.tom-roush:pdfbox-android:2.0.27.0` | Tom Roush / Apache PDFBox | `Apache-2.0` | https://github.com/TomRoush/PdfBox-Android |
| **Java Mammoth** | `org.zwobble.mammoth:mammoth:1.5.0` | Michael Williamson (`mwilliamson`) | `BSD-2-Clause` | https://github.com/mwilliamson/java-mammoth |
| **AndroidSVG** | `com.caverock:androidsvg-aar:1.4` | Paul LeBeau (`BigBadaboom`) | `Apache-2.0` | https://github.com/BigBadaboom/androidsvg |
| **Apache Commons Compress** | `org.apache.commons:commons-compress:1.26.1` | The Apache Software Foundation | `Apache-2.0` | https://github.com/apache/commons-compress |
| **XZ for Java** | `org.tukaani:xz:1.9` | Lasse Collin / Tukaani Project | `0BSD` (Dominio Público) | https://github.com/tukaani-project/xz-java |
| **jump3r (Java LAME MP3)** | `de.sciss:jump3r:1.0.5` | Hanns Holger Rutz (`Sciss`) / Ken Händel / Proyecto LAME | `LGPL-2.1-or-later` | https://github.com/Sciss/jump3r |
| **Kotlin & Coroutines** | `org.jetbrains.kotlin:2.0.21`, `kotlinx-coroutines-android:1.9.0` | JetBrains s.r.o. | `Apache-2.0` | https://github.com/JetBrains/kotlin |

---

## 3. Notas de Atribución por Componente

1. **AndroidX, Jetpack Compose y Media3 (`Apache-2.0`):**
   - Desarrollados por Google LLC y colaboradores de AOSP. Proveen la interfaz Material 3, Room SQLite, WorkManager y el motor de transcodificación local de video/audio.
   - Código fuente y textos de licencia en https://github.com/androidx/androidx y https://github.com/androidx/media.
2. **PDFBox-Android (`Apache-2.0`):**
   - Adaptación para Android mantenida por Tom Roush basada en Apache PDFBox de The Apache Software Foundation.
   - Código fuente y aviso legal en https://github.com/TomRoush/PdfBox-Android y https://github.com/apache/pdfbox.
3. **Java Mammoth (`BSD-2-Clause`):**
   - Desarrollado por Michael Williamson (`mwilliamson`) para la conversión de documentos `.docx` a HTML.
   - Código fuente y licencia en https://github.com/mwilliamson/java-mammoth.
4. **AndroidSVG (`Apache-2.0`):**
   - Desarrollado por Paul LeBeau (`BigBadaboom`) para el renderizado local de gráficos vectoriales `.svg`.
   - Código fuente y licencia en https://github.com/BigBadaboom/androidsvg.
5. **Apache Commons Compress (`Apache-2.0`):**
   - Desarrollado por The Apache Software Foundation para el manejo de archivos `.zip`, `.tar`, `.gz`, `.bz2`, `.xz` y `.7z`.
   - Código fuente y licencia en https://github.com/apache/commons-compress.
6. **XZ for Java (`0BSD` / Dominio Público):**
   - Desarrollado por Lasse Collin (Tukaani Project) para compresión y descompresión LZMA/LZMA2 y `.xz`.
   - Código fuente en https://github.com/tukaani-project/xz-java.
7. **jump3r (`LGPL-2.1-or-later`):**
   - Mantenido por Hanns Holger Rutz (`Sciss`), basado en `jmp3r` de Ken Händel y el codificador LAME. Enlazado como dependencia Maven sin modificaciones (`de.sciss:jump3r:1.0.5`).
   - Código fuente y licencia en https://github.com/Sciss/jump3r.
8. **Kotlin Standard Library & Coroutines (`Apache-2.0`):**
   - Desarrollados por JetBrains s.r.o.
   - Código fuente y licencia en https://github.com/JetBrains/kotlin y https://github.com/Kotlin/kotlinx.coroutines.
