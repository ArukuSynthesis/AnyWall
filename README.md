# AnyWall: Universal Converter (v0.2)

**Conversor Universal 100% Offline para Android** desarrollado por **SynthesizerEnd**.

**AnyWall** (`com.anywall.converter`) convierte imágenes, documentos Office/PDF, video, audio y archivos comprimidos ejecutando todo el procesamiento **100% en el hardware local del dispositivo**, sin permisos de red (`INTERNET` ni `ACCESS_NETWORK_STATE`) y funcionando íntegramente en modo avión.

---

## Características Principales (v0.2)

- **100% Offline y Privado:** 0 permisos de red en el APK final (auditado automáticamente con `aapt2 dump permissions`).
- **Motor de Imágenes (`ImageEngineImpl`):**
  - Conversión entre `JPG`, `PNG`, `WEBP` y `BMP` (codificador nativo 24-bit), más soporte de entrada `HEIC`/`HEIF`, `GIF` y `SVG`.
  - Control de calidad (`10% - 100%`), redimensionado máximo, rotación (`0°`, `90°`, `180°`, `270°`) y limpieza de metadatos EXIF/GPS.
  - Conversión de una o múltiples imágenes a un único `PDF` (con cifrado opcional por contraseña) y extracción de páginas PDF a imágenes individuales.
- **Motor Documental y PDF Avanzado (`DocumentEngineImpl`):**
  - Unión de múltiples PDFs (`PDFMergerUtility`), extracción de rangos de páginas, división completa (`Splitter`) y optimización/compresión.
  - **Protección de PDF con contraseña** (`StandardProtectionPolicy`) y **desbloqueo de PDF**.
  - Conversión 100% local de `DOCX`, `HTML`, `TXT` y `MD` a `PDF`, `HTML` o `TXT` con selector de tamaño de página (`A4` / `Carta`) y orientación (`Vertical` / `Horizontal`).
- **Motor de Audio y Video (`MediaEngineImpl`):**
  - Conversión de video (`MP4`, `MKV`, `MOV`, `WEBM`) con selector de resolución (`Original`, `1080p`, `720p`, `480p`), opción de **silenciar video** y **recorte de tiempo (Trim inicio–fin)**.
  - Extracción y conversión de audio (`MP3`, `AAC`, `M4A`, `WAV`) con reporte de progreso continuo.
- **Motor de Archivos Comprimidos (`ArchiveEngineImpl`):**
  - Empaquetado por lote en `.zip`, `.tar`, `.tar.gz`, `.tar.xz` y `.tar.bz2`.
  - Extracción completa de `.zip`, `.tar`, `.tar.gz`, `.tar.xz`, `.tar.bz2`, `.gz`, `.xz`, `.bz2` y `.7z` en `Downloads/AnyWall/` con protección contra *Zip-Slip*.
- **Productividad y UX Material 3:**
  - Integración con **Android Share Sheet** (`ACTION_SEND` y `ACTION_SEND_MULTIPLE`).
  - Reordenamiento de archivos del lote (`↑` / `↓`), miniaturas locales, insignia de ahorro de peso (`Antes → Después, -%`), selección múltiple en Historial y Ajustes locales con selector de tema (`Sistema`, `Claro`, `Oscuro`) y gestor de caché.

---

## Autoría y Créditos Open Source

- **Desarrollador Principal:** **SynthesizerEnd** (https://github.com/ArukuSynthesis/AnyWall)
- **Licencia del Proyecto:** `AGPL-3.0-only` (ver [LICENSE](LICENSE))
- **Créditos y Repositorios de Terceros:** Consulta [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) o la sección **"Acerca de AnyWall y Créditos"** dentro de la aplicación para ver la atribución completa de:
  - **AndroidX & Jetpack Compose** (`Apache-2.0`) — https://github.com/androidx/androidx
  - **AndroidX Media3** (`Apache-2.0`) — https://github.com/androidx/media
  - **PDFBox-Android** (`Apache-2.0`) — https://github.com/TomRoush/PdfBox-Android
  - **Java Mammoth** (`BSD-2-Clause`) — https://github.com/mwilliamson/java-mammoth
  - **AndroidSVG** (`Apache-2.0`) — https://github.com/BigBadaboom/androidsvg
  - **Apache Commons Compress** (`Apache-2.0`) — https://github.com/apache/commons-compress
  - **XZ for Java** (`0BSD`) — https://github.com/tukaani-project/xz-java
  - **jump3r (Java LAME MP3)** (`LGPL-2.1-or-later`) — https://github.com/Sciss/jump3r
  - **Kotlin & Coroutines** (`Apache-2.0`) — https://github.com/JetBrains/kotlin

---

## Compilación

```bash
./gradlew assembleDebug testDebugUnitTest
```

Para más detalles técnicos de arquitectura, consulta [CONTEXT.md](CONTEXT.md).
