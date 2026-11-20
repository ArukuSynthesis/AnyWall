# AnyWall: Universal Converter (v0.3)

**Conversor Universal 100% Offline para Android** desarrollado por **SynthesizerEnd**.

**AnyWall** (`com.anywall.converter`) convierte imágenes, documentos Office/PDF/XLSX/EPUB, video, audio, archivos comprimidos y datos estructurados/bóvedas criptográficas ejecutando todo el procesamiento **100% en el hardware local del dispositivo**, sin permisos de red (`INTERNET` ni `ACCESS_NETWORK_STATE`) y funcionando íntegramente en modo avión.

---

## Características Principales (v0.3)

- **100% Offline y Privado:** 0 permisos de red en el APK final (auditado automáticamente con `aapt2 dump permissions`).
- **Motor 1 — Imágenes, Editor Visual, Collages e Íconos (`ImageEngineImpl`):**
  - Conversión entre `JPG`, `PNG`, `WEBP`, `BMP` (24-bit), **`.ICO`**, **`GIF` animado** y **Paquete de Íconos ZIP (`48/96/192/512px + favicon.ico`)**, más soporte de entrada `HEIC`/`HEIF`, **`AVIF`**, `GIF` y `SVG`.
  - **Editor Visual de Imágenes en Vista Previa (`ImageVisualEditorDialog`):** recorte interactivo por proporciones, volteo H/V, filtros (`Grises`, `Escáner B/N Alto Contraste`, `Invertir`), encuadre con fondo Difuminado (*Blur*) o sólido, censura/pixelado por zonas y marca de agua de texto.
  - **Compresión por peso máximo objetivo (`KB`)** mediante búsqueda binaria local, **Collages (`2×2`, `3×3`)** y **Tiras continuas (`Vertical`, `Horizontal`)**.
- **Motor 2 — Documentos, PDF Profesional, Firma, `XLSX` y `EPUB` (`DocumentEngineImpl`):**
  - **Modificador Visual de PDF en Vista Previa (`PdfVisualEditorDialog`):** reordenamiento libre, rotación (`+90°`), exclusión, duplicación de páginas, inserción de páginas separadoras en blanco, censura sobre páginas y **Firma Manuscrita local (`SignaturePadDialog`)**.
  - Marca de agua diagonal en PDF, numeración automática `"Página X de Y"`, sello QR en PDF, división cada *N* páginas, **extractor de imágenes originales embebidas** y **modo Folleto/Cuadrícula (`2-en-1` y `4-en-1`)**.
  - Soporte en Kotlin puro de hojas de cálculo **`.xlsx`** (`XlsxPureParser`) y libros electrónicos **`.epub` (EPUB 3)** (`EpubPureConverter`), además de `DOCX`, `HTML`, `TXT` y `MD`.
- **Motor 3 — Audio y Video Avanzado (`MediaEngineImpl`):**
  - Conversión de video (`MP4`, `MKV`, `MOV`, `WEBM`) con resolución (`Original`, `1080p`, `720p`, `480p`), silenciado, recorte (`Trim`), **velocidad (`0.5x..2.0x`)**, **rotación/espejo**, **compresión por peso objetivo en `MB`**, **unión (concatenación) de clips** y **Video → GIF animado / Fotogramas**.
  - Audio (`MP3`, `AAC`, `M4A`, `WAV`) con entrada **`FLAC`**, **`OGG`** y **`OPUS`**, selector de **bitrate (`128`/`192`/`320 kbps`)**, **Fade In/Out** y modo **Ringtone**.
- **Motor 4 — Archivos Comprimidos y Particionado (`ArchiveEngineImpl`):**
  - Empaquetado en `.zip`, `.tar`, `.tar.gz`, `.tar.xz` y `.tar.bz2` con selector de nivel (`Rápido`, `Equilibrado`, `Máximo`) y preservación de subcarpetas.
  - **Explorador Previo (`ArchivePreviewDialog`)** con extracción selectiva y **Divisor/Reconstructor de archivos grandes (`.part001`, `.part002`…)** con verificación `SHA-256`.
- **Motor 5 — Datos Estructurados, Bóveda `AES-256-GCM` y Códigos QR (`DataAndSecurityEngineImpl`):**
  - Conversión entre `CSV`, `JSON`, `XML`, `YAML`, `BASE64`, `HTML`, `TXT` y `PDF`, con *Pretty Print*, conversión de codificación (`UTF-8`, `ISO-8859-1`, `Windows-1252`, `UTF-16`) y saltos de línea (`LF` ↔ `CRLF`).
  - **Bóveda Universal `AES-256-GCM` (`.anywall`)** con derivación `PBKDF2-HMAC-SHA256` (120 000 iteraciones) y **Generador/Escáner QR Offline (`PNG` y `SVG`)**.
- **Suite de Herramientas Offline, Presets Room v3 y Automatización:**
  - **Inspector Forense de Metadatos (EXIF/GPS/PDF/Media) y Verificador `SHA-256` / `MD5`**, **Comparador Local Diff (LCS)** y **Comparador Visual «Antes / Después»**.
  - **Perfiles / Presets Rápidos** guardables (Room v3), captura desde **Cámara / Escáner**, plantillas de renombrado (`{nombre}`, `{indice}`, `{fecha}`, `{formato}`), **Pipelines de 2 pasos**, estadísticas de ahorro, bloqueo biométrico/PIN del historial, soporte bilingüe (`ES`/`EN`), **App Shortcuts** y **Quick Settings Tile**.

---

## Autoría y Créditos Open Source

- **Desarrollador Principal:** **SynthesizerEnd** (https://github.com/ArukuSynthesis/AnyWall)
- **Licencia del Proyecto:** `AGPL-3.0-only` (ver [LICENSE](LICENSE))
- **Créditos y Repositorios de Terceros:** Consulta [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) o la sección **"Acerca de AnyWall y Créditos"** dentro de la aplicación:
  - **AndroidX & Jetpack Compose** (`Apache-2.0`) — https://github.com/androidx/androidx
  - **AndroidX Media3** (`Apache-2.0`) — https://github.com/androidx/media
  - **PDFBox-Android** (`Apache-2.0`) — https://github.com/TomRoush/PdfBox-Android
  - **Java Mammoth** (`BSD-2-Clause`) — https://github.com/mwilliamson/java-mammoth
  - **AndroidSVG** (`Apache-2.0`) — https://github.com/BigBadaboom/androidsvg
  - **Apache Commons Compress** (`Apache-2.0`) — https://github.com/apache/commons-compress
  - **XZ for Java** (`0BSD`) — https://github.com/tukaani-project/xz-java
  - **jump3r (Java LAME MP3)** (`LGPL-2.1-or-later`) — https://github.com/Sciss/jump3r
  - **ZXing Core (QR Engine)** (`Apache-2.0`) — https://github.com/zxing/zxing
  - **Kotlin & Coroutines** (`Apache-2.0`) — https://github.com/JetBrains/kotlin

---

## Compilación

```bash
./gradlew assembleRelease testReleaseUnitTest
```

Para más detalles técnicos de arquitectura, consulta [CONTEXT.md](CONTEXT.md).
