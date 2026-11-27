# AnyWall: Universal Converter (v0.6)

**Conversor Universal para Android** desarrollado por **SynthesizerEnd**.

**AnyWall** (`com.anywall.converter`) convierte y edita imágenes, documentos Office/PDF/XLSX/EPUB, video, audio, cómics, archivos comprimidos y datos estructurados ejecutando todo el procesamiento en el hardware del dispositivo. El APK final declara **0 permisos de red** (`INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`), verificado automáticamente en CI con `aapt2 dump permissions`.

- **Versión:** `versionCode = 6` • `versionName = "0.6"`
- **Android mínimo:** 8.0 (API 26) • **Target SDK:** 35

---

## Novedades de v0.6 (Optimización del -67% de Tamaño APK, Bóveda `.anywall` Directa y Refinamiento Integral de UX/UI)

- **Optimización extrema del APK (-67% de peso):**
  - Activación de *tree-shaking* R8 (`isMinifyEnabled = true`, `isShrinkResources = true` con `-dontobfuscate` para trazas legibles), exclusión del paquete `org.bouncycastle.pqc`, banderas NDK/CMake `-Os -Wl,--gc-sections -Wl,-s` para `libarchive` y *splits* nativos por arquitectura (`arm64-v8a` de **~8.9 MB** y APK `universal` de **~12.0 MB**, frente a los 27.0 MB de v0.5).
- **Asociación Nativa de Archivos en Android (`ACTION_VIEW`) y Enrutamiento Inteligente:**
  - Tocar cualquier archivo `.anywall`, `.rar`, `.7z`, `.zip`, `.cbz`, `.cbr`, `.tar`, `.gz`, `.bz2` o `.xz` desde el explorador de archivos o apps de mensajería abre AnyWall directamente. Los contenedores `.anywall` se enrutan automáticamente a la pestaña **Seguridad → Bóveda `.anywall`** en modo Desencriptar.
- **Gestor Directo de Bóveda `.anywall` (`AES-256-GCM`) y Generador `SecureRandom`:**
  - Panel dedicado en la pestaña **Seguridad** con generador criptográfico de 1 toque (llave de 20 caracteres de alta entropía o *passphrase* memorable de 4 palabras), botón de copia al portapapeles e indicador de fortaleza en tiempo real.
- **Resultado Inmediato Post-Conversión (`Antes → Después`):**
  - Tarjetas de resultado en `ConversionProgressCard` y en la Bóveda `.anywall` con miniatura visual, comparativa de tamaño y botones directos **Abrir** y **Compartir** sin cambiar de pestaña.
- **Escáner QR Universal + Asistente Wi-Fi + Paleta Dominante `#HEX`:**
  - Escáner QR y códigos de barras desde imagen con soporte para QR invertidos y decodificador de redes `WIFI:S:...` con copia de contraseña en 1 toque.
  - Extractor de paleta de 6 colores dominantes `#HEX` en el Inspector Forense de imágenes.
- **Refinamiento de UX/UI y Estudios Táctiles:**
  - **Inicio (`HomeScreen`):** barra de entrada rápida en 1 columna con botones horizontales de ancho completo (`Elegir Archivos`, `Importar Carpeta`, `Capturar con Cámara`) sin truncamiento de texto, y selector de formato con `ModalBottomSheet` y opción `"Todos"`.
  - **Estudio de Fotos (`ImageVisualEditorDialog`) y Estudio PDF (`PdfVisualEditorDialog`):** contenedores modales acotados dentro del área segura de la pantalla (`96% × 86%`) con botones **Guardar** siempre visibles y accesibles tanto en la barra superior como en la barra inferior.
  - **Recorte y Línea de Tiempo de Video (`MediaTimelineTrimmerDialog` & `VideoCropDialog`):** cálculo exacto de caja `(fitW, fitH)` para videos 16:9 horizontales, protección de estado `isPlayerPrepared` en `MediaPlayer` y arrastre 1:1 de los 4 bordes y 4 esquinas.

## Novedades de v0.5 (Rediseño Integral de UI y Arquitectura de Navegación)

- **Barra de Navegación Inferior Moderna de 4 Pestañas (`AnyWallNavHost`):**
  1. **Convertir:** Dashboard estilo **Bento Grid 2×2 + 1** con Hero Dropzone, banner de lote activo con contador en badge y acceso rápido a perfiles guardados.
  2. **Estudios (`ToolsHubScreen`):** pantalla completa nativa (sin `AlertDialog`s gigantes) que organiza en tarjetas Bento los **6 Estudios Táctiles** (Estudio de Fotos, Estudio PDF y Firma, Línea de Tiempo Video/Audio, Encuadre de Video, Explorador `.RAR`/`.ZIP`/`.7Z` y Renombrador Masivo) y el espacio de trabajo integrado para las **5 Utilidades Técnicas** (Duplicados `SHA-256`, Metadatos & Hashes, Códigos QR, Comparador `Diff` y `Antes / Después`).
  3. **Historial (`HistoryScreen`):** métricas de ahorro de almacenamiento, filtro por categorías, búsqueda instantánea y multiselección.
  4. **Ajustes (`SettingsScreen`):** pantalla completa nativa con tarjeta Hero `AnyWall v0.5`, acceso directo al **Actualizador de GitHub Releases (`GitHubUpdaterDialog`)**, selector visual con muestras circulares de las **6 paletas de acento**, modo **Negro AMOLED (`#000000`)** + **Material You**, idioma (`Sistema`/`ES`/`EN`), bloqueo Biométrico/PIN y gestión de caché.
- **Nuevo Flujo de Conversión en 3 Pasos (`ConfigureScreen`):**
  - **Paso 1 (Archivos en Cola):** tarjetas `InputFileCard` rediseñadas con insignias de formato coloreadas, *Drag & Drop* táctil, *Quick Peek* y barra compacta de orden/renombrado/deduplicación.
  - **Estudios Táctiles Contextuales Destacados:** aparecen tarjetas Hero solo para los formatos presentes en el lote activo.
  - **Paso 2 (Formato Destino y Control Esencial):** selector segmentado de 2 modos (*Convertir* vs *Comprimir / Extraer*), estimador de tamaño en vivo (`OutputSizeEstimator`) y solo el ajuste primario indispensable visible por defecto.
  - **Acordeones Colapsables de "Opciones Avanzadas":** cerrados por defecto para evitar saturación visual, agrupando ajustes avanzados del formato, etiquetas ID3v2.3 + carátula, plantillas de nombre y pipelines de 2 pasos.
  - **Paso 3 (Barra Inferior Fija / Sticky Bottom CTA):** botón principal `"Convertir a [FORMATO]"` y guardado de Preset siempre accesibles en la parte inferior sin necesidad de hacer scroll.

## Novedades de v0.4

- **Temas y personalización (`AppPreferences` + `SettingsDialog`):** modos `Sistema`, `Claro`, `Oscuro` y **`Negro AMOLED` (`#000000`)**, **Material You** (`useDynamicColor`, Android 12+) y paleta de acento `Indigo Studio`, `Esmeralda`, `Ámbar`, `Rosa`, `Cian` y `Monocromo`.
- **Cola inteligente en `ConfigureScreen`:** reordenamiento táctil *Drag & Drop* (mantener pulsado y arrastrar), **Vista Rápida (`QuickPeekDialog`)** con zoom de imagen, mini reproductor de video/audio y visor de PDF/texto/comprimidos, y **estimador de tamaño de salida (`OutputSizeEstimator`)**.
- **Smart Share Sheet (`SmartShareSheetDialog`):** al compartir archivos hacia AnyWall (`ACTION_SEND` / `ACTION_SEND_MULTIPLE`) se ofrecen acciones directas de 1 toque según el tipo MIME.
- **Estudio de Fotos 100% táctil (`ImageVisualEditorDialog`):** lienzo oscuro a pantalla completa (`#0B0D10`) sin sliders de recorte:
  - Recorte con **8 tiradores**, cuadrícula 3×3 al arrastrar, *pinch-to-zoom* + *pan*, **dial de enderezado** (`-45°..+45°`), rotación `90°`, volteo H/V y proporciones `Libre`, `Original`, `1:1`, `4:5`, `3:4`, `16:9`, `9:16`.
  - **Escáner de perspectiva de 4 esquinas** con lupa 2.5× (`Matrix.setPolyToPoly`).
  - **Ajustes en vivo:** Exposición, Brillo, Contraste, Saturación, Temperatura, Nitidez y Viñeta.
  - **Filtros con miniaturas reales** de la foto + intensidad `0–100%`.
  - **Censura táctil:** pincel libre y rectángulos arrastrables (`Pixelado`, `Blur`, `Negro sólido`).
  - **Texto / marca de agua interactiva** (arrastrar con 1 dedo, escalar/rotar con 2), **Deshacer / Rehacer**, *mantener para comparar* (Antes/Después) y **"Aplicar a todo el lote"**.
- **`.rar` / `.rar5` con `libarchive` (`BSD-2-Clause`, NDK + JNI):** exploración, extracción total o selectiva, contraseñas, volúmenes `.part1.rar` y protección anti-*Zip-Slip*, más **re-empaquetado en 1 paso** a `.zip`, `.7z`, `.tar.gz`, `.tar.xz` y `.tar.bz2`. AnyWall **nunca crea** archivos `.rar`.
- **Cómics:** `.cbz` (entrada/salida) y `.cbr` (entrada) ↔ `PDF` / imágenes / `.cbz`.
- **Video y audio táctil:**
  - **`MediaTimelineTrimmerDialog`:** línea de tiempo con tira de fotogramas reales (video) o forma de onda PCM (audio), 2 tiradores de inicio/fin, reproductor integrado y **"Capturar fotograma exacto"**.
  - **`VideoCropDialog`:** recorte táctil del encuadre aplicado con `androidx.media3.effect.Crop`.
  - **Auto-Gain** (normalización a `-1.0 dBFS`) y editor de etiquetas **ID3v2.3 + carátula** (`AudioMetadataSpec`) para `MP3`/`M4A`.
- **Estudio PDF táctil (`PdfVisualEditorDialog`):** *Drag & Drop* de páginas, vista con *pinch-to-zoom*, firma manuscrita arrastrable/redimensionable, cajas de censura trazadas con el dedo, anotaciones (texto, sello de fecha y resaltador translúcido con `PDExtendedGraphicsState`) y escáner de 4 esquinas.
- **Herramientas de archivos:**
  - **Renombrador masivo (`BatchRenamerDialog` + `BatchFileRenamer`):** plantillas, prefijo/sufijo, buscar/reemplazar (texto o regex), capitalización y vista previa Antes → Después, sin reconvertir.
  - **Detector/limpiador de duplicados (`DuplicateFilesDetector`):** agrupación por tamaño + `SHA-256` en streaming.

## Funciones heredadas (v0.1–v0.3)

- **Imágenes (`ImageEngineImpl`):** `JPG`, `PNG`, `WEBP`, `BMP`, `.ICO`, `GIF` animado, paquete de íconos ZIP; entrada `HEIC`/`HEIF`, `AVIF`, `GIF`, `SVG`; compresión por peso objetivo (`KB`), collages y tiras.
- **Documentos (`DocumentEngineImpl`):** PDF (unir, dividir, comprimir, cifrar/descifrar, numeración, marca de agua, folleto 2-en-1/4-en-1, extractor de imágenes), `DOCX`, `HTML`, `TXT`, `MD`, `XLSX` y `EPUB 3`.
- **Audio/Video (`MediaEngineImpl`):** `MP4`, `MKV`, `MOV`, `WEBM`, `MP3`, `AAC`, `M4A`, `WAV` (+ entrada `FLAC`/`OGG`/`OPUS`), resolución, velocidad, rotación/espejo, concatenación, Video → GIF/fotogramas, bitrate, Fade In/Out y Ringtone.
- **Comprimidos (`ArchiveEngineImpl`):** `.zip`, `.7z`, `.tar`, `.tar.gz`, `.tar.xz`, `.tar.bz2`, `.gz`, `.xz`, `.bz2` y divisor/reconstructor `.part001` con verificación `SHA-256`.
- **Datos y seguridad (`DataAndSecurityEngineImpl`):** `CSV` ↔ `JSON` ↔ `XML` ↔ `YAML` ↔ `BASE64`, bóveda `AES-256-GCM` (`.anywall`) y generador/escáner QR (`PNG`/`SVG`).
- **Herramientas:** inspector de metadatos, verificador `SHA-256`/`MD5`, comparador Diff, comparador visual Antes/Después, presets Room, pipelines de 2 pasos, bloqueo biométrico/PIN del historial, interfaz bilingüe (`ES`/`EN`), App Shortcuts y Quick Settings Tile.

---

## Autoría y Créditos Open Source

- **Desarrollador Principal:** **SynthesizerEnd** (https://github.com/ArukuSynthesis/AnyWall)
- **Licencia del Proyecto:** `AGPL-3.0-only` (ver [LICENSE](LICENSE))
- **Créditos y Repositorios de Terceros:** consulta [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) o **"Acerca de AnyWall y Créditos"** dentro de la aplicación:
  - **AndroidX & Jetpack Compose** (`Apache-2.0`) — https://github.com/androidx/androidx
  - **AndroidX Media3** (`Apache-2.0`) — https://github.com/androidx/media
  - **PDFBox-Android** (`Apache-2.0`) — https://github.com/TomRoush/PdfBox-Android
  - **Java Mammoth** (`BSD-2-Clause`) — https://github.com/mwilliamson/java-mammoth
  - **AndroidSVG** (`Apache-2.0`) — https://github.com/BigBadaboom/androidsvg
  - **Apache Commons Compress** (`Apache-2.0`) — https://github.com/apache/commons-compress
  - **XZ for Java** (`0BSD`) — https://github.com/tukaani-project/xz-java
  - **jump3r (Java LAME MP3)** (`LGPL-2.1-or-later`) — https://github.com/Sciss/jump3r
  - **ZXing Core (QR Engine)** (`Apache-2.0`) — https://github.com/zxing/zxing
  - **libarchive** (`BSD-2-Clause`) — https://github.com/libarchive/libarchive
  - **Kotlin & Coroutines** (`Apache-2.0`) — https://github.com/JetBrains/kotlin

---

## Compilación

Requisitos: JDK 17, Android SDK 35 (Build Tools `35.0.0`), **Android NDK `27.0.12077973`** y **CMake `3.22.1`** (para `libarchive`).

```bash
sdkmanager "ndk;27.0.12077973" "cmake;3.22.1"
./gradlew assembleRelease testReleaseUnitTest
```

Para más detalles técnicos de arquitectura, consulta [CONTEXT.md](CONTEXT.md).
