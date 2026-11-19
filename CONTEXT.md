# AnyWall: Universal Converter — Documento de Contexto Arquitectónico

Este documento sirve como referencia técnica viva para futuras iteraciones, mantenimiento y evolución de **AnyWall: Universal Converter** (`com.anywall.converter`). Contiene las reglas inquebrantables, decisiones de diseño, arquitectura de paquetes, detalle de los 4 motores locales, flujos de datos y guías de compilación.

---

## 1. Identidad y Propósito del Proyecto

- **Nombre de la app:** AnyWall: Universal Converter
- **Application ID / Namespace:** `com.anywall.converter`
- **Idioma de la interfaz:** 100% español
- **Propósito:** Conversor universal nativo de Android (imágenes, documentos/PDF, audio, video y archivos comprimidos) con procesamiento **100% local en el hardware del dispositivo**, sin dependencias en la nube y operable íntegramente en modo avión.
- **Estado actual:** MVP completo, funcional, sin stubs ni `// TODO`, compilando limpiamente (`BUILD SUCCESSFUL`) con pruebas unitarias pasando.

---

## 2. Reglas Inquebrantables de Arquitectura

Cualquier iteración futura **debe respetar estrictamente** estas 7 reglas:

1. **ESTRICTAMENTE 100% OFFLINE:**
   - Prohibido añadir `<uses-permission android:name="android.permission.INTERNET" />` o `<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />` en `app/src/main/AndroidManifest.xml`.
   - Prohibido integrar APIs remotas, telemetría, Crashlytics, analítica o descarga de recursos externos.
   - En `WebViewPdfGenerator`, el `WebView` offscreen bloquea cargas de red (`blockNetworkLoads = true`, `blockNetworkImage = true`) e intercepta `http`/`https` devolviendo respuestas vacías.
2. **CERO PLACEHOLDERS O STUBS:**
   - No dejar funciones vacías, comentarios `// TODO` ni conversiones simuladas.
3. **CLEAN ARCHITECTURE + MVVM SIN HILT/DAGGER:**
   - Kotlin puro + Jetpack Compose (Material 3).
   - Inyección de dependencias manual limpia mediante `AppContainer` inicializado en `AnyWallApplication.kt`.
   - KSP se usa **únicamente** para `androidx.room:room-compiler`.
4. **INTERFAZ COMÚN DE MOTORES (`FileConverterEngine`):**
   - Todos los motores implementan:
     - `fun canHandle(format: SupportedFormat): Boolean`
     - `fun getSupportedTargetFormats(sourceFormats: List<SupportedFormat>): List<SupportedFormat>`
     - `suspend fun convert(inputs: List<InputFileItem>, targetFormat: SupportedFormat, options: ConversionOptions, onProgress: (Float, String) -> Unit): List<ConversionJobResult>`
5. **INTEGRACIÓN CON ANDROID SHARE SHEET:**
   - `MainActivity` (`launchMode="singleTop"`) declara `intent-filter` para `ACTION_SEND` y `ACTION_SEND_MULTIPLE` con `*/*`.
   - Recibe URIs en `onCreate` y `onNewIntent` (tanto `EXTRA_STREAM` como `ClipData`) y navega automáticamente a `ConfigureScreen`.
6. **PROCESAMIENTO EN SEGUNDO PLANO CON WORKMANAGER:**
   - Todas las conversiones se ejecutan en `BatchConversionWorker` (`CoroutineWorker`) con `ForegroundInfo` (`FOREGROUND_SERVICE_TYPE_DATA_SYNC`) mostrando una notificación persistente con barra de progreso real.
7. **ALMACENAMIENTO SEGURO (SCOPED STORAGE) + ROOM:**
   - Lectura exclusiva mediante `ContentResolver` (`UriFileResolver`).
   - Escritura en `Downloads/AnyWall/` usando `MediaStore.Downloads` con `IS_PENDING` en API 29+ y almacenamiento público + `FileProvider` (con fallback a `getExternalFilesDir`) en API 26-28.
   - Registro automático de cada archivo generado en Room (`anywall_converter.db`) y apertura/compartición segura mediante `FileProvider` / `MediaStore`.

---

## 3. Stack Técnico y Versiones Exactas

Definido en `gradle/libs.versions.toml` y `app/build.gradle.kts`:

| Componente | Versión / Configuración |
| :--- | :--- |
| **SDK Android** | `minSdk = 26`, `targetSdk = 35`, `compileSdk = 35` |
| **Gradle / AGP / Kotlin / KSP** | Gradle `8.9`, AGP `8.7.3`, Kotlin `2.0.21`, KSP `2.0.21-1.0.28`, JVM Target `17` |
| **UI & Compose** | Compose BOM `2024.12.01` (Material 3, Material Icons Extended), `navigation-compose:2.8.5`, `lifecycle-viewmodel-compose:2.8.7`, `activity-compose:1.9.3` |
| **Persistencia & Background** | `room-runtime/ktx/compiler:2.6.1`, `work-runtime-ktx:2.10.0`, `kotlinx-coroutines-android:1.9.0` |
| **Imágenes / SVG** | `com.caverock:androidsvg-aar:1.4` + APIs nativas (`ImageDecoder`, `BitmapFactory`, `PdfRenderer`, `PdfDocument`) |
| **Documentos / PDF** | `com.tom-roush:pdfbox-android:2.0.27.0` + `org.zwobble.mammoth:mammoth:1.5.0` |
| **Audio / Video** | `androidx.media3:media3-{transformer,effect,common,muxer}:1.4.1` + `de.sciss:jump3r:1.0.5` |
| **Archivos Comprimidos** | `org.apache.commons:commons-compress:1.26.1` + `org.tukaani:xz:1.9` |

> **Nota sobre dependencias adicionales justificadas:**
> - `androidx.media3:media3-muxer:1.4.1`: Es dependencia `runtime` de `media3-transformer`, declarada como `implementation` para poder implementar el `Muxer.Factory` personalizado (`WebmMatroskaMuxerFactory`) para contenedores `WEBM`/`MKV`.
> - `de.sciss:jump3r:1.0.5`: Implementación pura en Java de LAME para codificar MP3 de forma real sin binarios nativos externos ni `javax.sound.sampled`.

---

## 4. Mapa de Paquetes y Archivos

```text
app/src/main/
├── AndroidManifest.xml
├── res/
│   ├── drawable/ic_launcher_{background,foreground}.xml
│   ├── mipmap-anydpi-v26/ic_launcher{,_round}.xml
│   ├── values/{strings,themes}.xml
│   └── xml/file_paths.xml
└── java/
    ├── android/print/
    │   └── PdfPrintCallbackBridge.kt          # Puente package-private para LayoutResultCallback/WriteResultCallback
    └── com/anywall/converter/
        ├── AnyWallApplication.kt              # Inicializa PDFBoxResourceLoader y AppContainer
        ├── MainActivity.kt                    # Share Sheet (ACTION_SEND / SEND_MULTIPLE), POST_NOTIFICATIONS, NavHost
        ├── core/
        │   ├── AppContainer.kt                # Contenedor DI manual
        │   ├── FileShareHelper.kt             # Abrir (ACTION_VIEW) y compartir (ACTION_SEND) con FileProvider/MediaStore
        │   ├── NotificationHelper.kt          # Canal de notificaciones, ForegroundInfo y notificación de finalización
        │   ├── ScopedStorageManager.kt        # Guardado en Downloads/AnyWall/ (MediaStore API 29+ / Legacy API 26-28)
        │   └── UriFileResolver.kt             # ContentResolver, detección MIME/extensión/magic bytes, staging temporal
        ├── domain/
        │   ├── engine/
        │   │   ├── FileConverterEngine.kt     # Interfaz común de motores
        │   │   └── ConverterEngineRegistry.kt # Enrutamiento inteligente origen/destino -> motor
        │   ├── model/
        │   │   ├── ActiveJobState.kt
        │   │   ├── ConversionJobResult.kt
        │   │   ├── ConversionOptions.kt
        │   │   ├── FormatCategory.kt
        │   │   ├── HistoryRecord.kt
        │   │   ├── InputFileItem.kt
        │   │   ├── PdfActionMode.kt
        │   │   ├── SupportedFormat.kt
        │   │   └── VideoResolution.kt
        │   └── repository/
        │       └── ConversionHistoryRepository.kt
        ├── data/
        │   ├── local/
        │   │   ├── AnyWallDatabase.kt
        │   │   ├── ConversionHistoryRepositoryImpl.kt
        │   │   ├── dao/ConversionHistoryDao.kt
        │   │   └── entity/ConversionHistoryEntity.kt
        │   └── engines/
        │       ├── ImageEngineImpl.kt
        │       ├── DocumentEngineImpl.kt
        │       ├── WebViewPdfGenerator.kt
        │       ├── MediaEngineImpl.kt
        │       ├── PcmWavMp3AudioHelper.kt
        │       └── ArchiveEngineImpl.kt
        ├── workers/
        │   ├── BatchConversionWorker.kt       # CoroutineWorker con progreso en vivo y registro en Room
        │   └── ConversionWorkScheduler.kt     # Serialización de lote JSON + WorkManager unique expedited work
        └── ui/
            ├── theme/{Color,Theme,Type}.kt
            ├── navigation/{AnyWallDestination,AnyWallNavHost}.kt
            ├── components/
            │   ├── CategoryQuickCard.kt
            │   ├── ConversionProgressCard.kt
            │   ├── FormatChipSelector.kt
            │   ├── HistoryItemCard.kt
            │   ├── InputFileCard.kt
            │   └── OfflinePrivacyBadge.kt
            └── screens/
                ├── MainViewModel.kt
                ├── HomeScreen.kt
                ├── ConfigureScreen.kt
                └── HistoryScreen.kt
```

Pruebas unitarias actuales:
- `app/src/test/java/com/anywall/converter/AnyWallEngineUnitTest.kt`

---

## 5. Detalle Técnico y Decisiones Clave de los 4 Motores

### Motor 1: `ImageEngineImpl`
- **Formatos:** `JPG`, `PNG`, `WEBP`, `BMP`, `SVG`, y conversión bidireccional con `PDF`.
- **Decodificación:** Usa `ImageDecoder` (con `ALLOCATOR_SOFTWARE`) en API 28+ con fallback a `BitmapFactory` (calculando `inSampleSize` según `maxDimensionPx`). Aplana el canal alfa sobre blanco al exportar a `JPG`, `BMP` o `PDF`.
- **BMP nativo:** Android no incluye `Bitmap.CompressFormat.BMP`; se implementó `writeBitmapAs24BitBmp()` escribiendo cabeceras `BITMAPFILEHEADER` (14 B) + `BITMAPINFOHEADER` (40 B) y filas BGR bottom-up con relleno de 4 bytes.
- **SVG:** Renderizado mediante `com.caverock.androidsvg.SVG` escalado al tamaño objetivo.
- **Imágenes → PDF:** `android.graphics.pdf.PdfDocument`, con opción de unir en un único PDF (`mergeIntoSinglePdf`) o generar PDFs individuales; aplica compresión JPEG intermedia cuando `quality < 98`.
- **PDF → Imágenes (`JPG` / `PNG`):** `android.graphics.pdf.PdfRenderer` a escala 2x (~144 DPI) respetando `maxDimensionPx` y rango de páginas (`parsePageIndices`, ej. `"1-3, 5"`).

### Motor 2: `DocumentEngineImpl` + `WebViewPdfGenerator` + `PdfPrintCallbackBridge`
- **PDFBox:** Inicializado en `AnyWallApplication` y verificado con `PDFBoxResourceLoader.isReady()` en `DocumentEngineImpl`.
- **Operaciones sobre PDF (`PdfActionMode`):**
  - `MERGE_OR_CONVERT`: Une múltiples PDFs con `PDFMergerUtility` (usando `MemoryUsageSetting.setupMixed(32 MB)`).
  - `SPLIT_RANGE`: Importa páginas seleccionadas en un nuevo `PDDocument`.
  - `SPLIT_ALL_PAGES`: Divide todas las páginas con `Splitter`.
  - `OPTIMIZE_COMPRESS`: Aplica estrategia dual — primero recomprime `PDImageXObject` preservando texto vectorial, y si calidad < 90% evalúa rasterización controlada con `PdfRenderer` + `JPEGFactory`, eligiendo el resultado de menor tamaño.
- **DOCX / HTML / TXT / MD → PDF:**
  - `DOCX` se transforma a HTML mediante `org.zwobble.mammoth.DocumentConverter`.
  - `MD` dispone de un parser completo integrado (encabezados, listas ordenadas/desordenadas, bloques de código, citas, negrita, cursiva, código inline, enlaces sin red).
  - El HTML estilizado se renderiza en `Dispatchers.Main` con un `WebView` offscreen (`WebViewPdfGenerator`) acoplado a un `PrintDocumentAdapter` personalizado (`OffscreenWebViewPrintAdapter`) y `android.print.PdfPrintCallbackBridge` (ubicado intencionalmente en el paquete `android.print` para instanciar los constructores package-private de `LayoutResultCallback` y `WriteResultCallback`).
  - Incluye un **fallback seguro** (`renderWebViewCanvasFallback`) que pagina el `WebView` sobre `PdfDocument` si el subsistema de impresión del dispositivo fallara.
- **Exportación a TXT / HTML:** Extracción de texto desde PDF con `PDFTextStripper`, desde DOCX con `DocumentConverter.extractRawText`, y limpieza de etiquetas desde HTML.

### Motor 3: `MediaEngineImpl` + `PcmWavMp3AudioHelper`
- **Video (`MP4`, `MKV`, `MOV`, `WEBM`):**
  - Usa `Media3 Transformer` con `Presentation.createForHeight(targetHeight)` para `1080p`, `720p` y `480p`.
  - Para `WEBM`/`MKV`, intenta primero `VP8` + `Opus` mediante `WebmMatroskaMuxerFactory` (`MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM`), y si el hardware del dispositivo no dispone de encoder VP8/Opus cae de forma segura a `H.264` + `AAC` con `DefaultMuxer`.
- **Audio (`MP3`, `AAC`, `M4A`, `WAV`):**
  - Fase 1: Extrae la pista de audio en `Media3 Transformer` usando `EditedMediaItem.Builder(mediaItem).setRemoveVideo(true).build()`.
  - `M4A`: Salida directa de Media3 Transformer.
  - `AAC`: `PcmWavMp3AudioHelper.extractAdtsAacFromMp4()` envuelve cada trama AAC con cabecera ADTS de 7 bytes.
  - `WAV`: `decodeMediaFileToPcm16Le()` (`MediaExtractor` + `MediaCodec`, con atajo directo para WAV PCM 16-bit) + `writeWavFileFromPcm16Le()` (RIFF WAVE de 44 bytes).
  - `MP3`: `encodePcm16LeToMp3()` instancia directamente los submódulos de `de.sciss.jump3r.mp3.Lame` evitando `LameEncoder` (que referencia `javax.sound.sampled`, no presente en Android).
- **Progreso continuo:** Polling en `Dispatchers.Main` cada `150 ms` sobre `transformer.getProgress(progressHolder)`.

### Motor 4: `ArchiveEngineImpl`
- **Empaquetado (`ZIP`, `TAR`, `TAR.GZ`):** Usa `ZipArchiveOutputStream`, `TarArchiveOutputStream` (`LONGFILE_POSIX`, `BIGNUMBER_POSIX`) y `GzipCompressorOutputStream`. Resuelve colisiones de nombres de entrada automáticamente.
- **Extracción (`ZIP`, `TAR`, `TAR.GZ`, `GZ`, `7Z`):**
  - Soporta `.7z` mediante `SevenZFile.builder().setFile(temp7z).get()` junto con `org.tukaani:xz`.
  - Detecta archivos `.gz` que contienen internamente un `.tar`.
  - Extrae en subcarpetas organizadas bajo `Downloads/AnyWall/<nombre_archivo>/`.
  - **Seguridad Zip-Slip:** `splitEntryFolderAndName()` elimina cualquier segmento `.`, `..` y caracteres inválidos antes de delegar el guardado a `ScopedStorageManager`.

---

## 6. Flujo de Trabajo, Concurrencia y Persistencia

1. **Selección de archivos:**
   - Desde `HomeScreen` (`ActivityResultContracts.OpenMultipleDocuments` general o filtrado por MIME de categoría), `ConfigureScreen` (botón "Añadir"), o Android Share Sheet (`MainActivity.handleIncomingShareIntent`).
   - `MainViewModel.addInputUris()` resuelve las URIs en `Dispatchers.IO` vía `UriFileResolver` y recalcula los formatos destino disponibles consultando `ConverterEngineRegistry.getAvailableTargetFormats()`.
2. **Encolado en `WorkManager`:**
   - Para evitar el límite de 10 KB de `androidx.work.Data` en lotes grandes, `ConversionWorkScheduler` serializa la lista completa de `InputFileItem` y `ConversionOptions` en un archivo JSON temporal en `cacheDir/anywall_payloads/batch_<uuid>.json` y pasa únicamente su ruta al Worker.
   - Se encola como trabajo único (`UNIQUE_WORK_NAME = "anywall_active_batch_conversion"`) con `ExistingWorkPolicy.REPLACE` y `OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST`.
3. **Ejecución en `BatchConversionWorker`:**
   - Llama a `setForeground(notificationHelper.createForegroundInfo(...))` al iniciar.
   - Propaga progreso al `WorkManager` (`setProgressAsync`) y actualiza la notificación con throttling cada `220 ms`.
   - Al terminar, persiste todos los `ConversionJobResult` en Room (`historyRepository.recordConversions(results)`), emite notificación de finalización y elimina el archivo JSON temporal de payload.
4. **Historial en Room (`AnyWallDatabase`):**
   - Tabla: `conversion_history` (versión `1`, `exportSchema = false`).
   - Al eliminar un registro desde la UI, `ConversionHistoryRepositoryImpl.deleteRecord()` borra tanto el archivo físico (vía `ContentResolver.delete` y/o `File.delete`) como el registro en Room.

---

## 7. Guía de Compilación y Verificación

En un entorno estándar con Android SDK 35 y JDK 17:

```bash
# Compilar APK debug y ejecutar pruebas unitarias
./gradlew assembleDebug testDebugUnitTest

# Verificar que no existan permisos de red ni TODOs
grep -i "INTERNET\|ACCESS_NETWORK_STATE" app/src/main/AndroidManifest.xml
grep -rn "TODO" app/src/
```

**Artefacto verificado en la iteración inicial:**
- `app/build/outputs/apk/debug/app-debug.apk` (~30 MB, compilado con `BUILD SUCCESSFUL`).
- Pruebas unitarias en `AnyWallEngineUnitTest` pasando al 100% (detección de formatos, parseo de rangos PDF, formateo de bytes, síntesis WAV y codificación LAME MP3 real).

> **Nota de ProGuard / Release:** Actualmente `isMinifyEnabled = false` en `release` para evitar roturas por reflexión en PDFBox, Mammoth y Commons Compress. Si en una iteración futura se activa R8 (`isMinifyEnabled = true`), revisar y probar exhaustivamente `app/proguard-rules.pro`.

---

## 8. Pendientes y Hoja de Ruta Sugerida para Futuras Iteraciones

1. **Control de versiones (Git):**
   - Todos los archivos generados están actualmente sin commitear en el directorio de trabajo local (solo existe `Initial commit`). En la próxima sesión o cuando lo indiques, se puede hacer commit y push al repositorio `ArukuSynthesis/AnyWall`.
2. **Pruebas instrumentadas (`androidTest`):**
   - Añadir pruebas en emulador/dispositivo real para validar:
     - `WebViewPdfGenerator` + `PdfPrintCallbackBridge` en Android 13/14/15.
     - `MediaEngineImpl` con video real (resolución 720p/480p y extracción a MP3/M4A/AAC/WAV).
     - `ScopedStorageManager` en API 29+ y API 26-28.
     - `AnyWallDatabase` y migraciones de Room.
3. **Mejoras funcionales potenciales:**
   - **Cola paralela / historial de trabajos fallidos:** Permitir encolar múltiples lotes secuenciales (`ExistingWorkPolicy.APPEND_OR_REPLACE`) en lugar de reemplazar el trabajo activo.
   - **Limpieza periódica de caché:** Invocar `uriFileResolver.cleanupTempFiles()` al iniciar la app si no hay trabajos activos en `WorkManager`, para limpiar residuos ante cierres inesperados.
   - **Previsualización de archivos:** Miniaturas locales con `BitmapFactory`/`ThumbnailUtils` en `InputFileCard` e `HistoryItemCard`.
   - **Más parámetros avanzados:** Control de bitrate explícito para audio/video, rotación/recorte de imágenes, contraseña para PDFs cifrados (PDFBox) y archivos ZIP protegidos.
   - **Migraciones Room con esquema exportado:** Cambiar `exportSchema = true` en `AnyWallDatabase` cuando se agreguen columnas nuevas para mantener historial de migraciones seguro.
   - **Build Release firmado y optimización de tamaño:** Configurar `signingConfigs`, habilitar `isMinifyEnabled = true` + `isShrinkResources = true` con pruebas completas sobre `proguard-rules.pro`, y considerar App Bundle (`.aab`).
