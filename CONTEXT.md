# AnyWall: Universal Converter — Documento de Contexto Arquitectónico (v0.3)

Este documento sirve como referencia técnica viva para futuras iteraciones, mantenimiento y evolución de **AnyWall: Universal Converter** (`com.anywall.converter`). Contiene las reglas inquebrantables, decisiones de diseño, arquitectura de paquetes, detalle de los 5 motores locales, flujos de datos y guías de compilación actualizadas a la versión **v0.3**.

---

## 1. Identidad y Propósito del Proyecto

- **Nombre de la app:** AnyWall: Universal Converter
- **Autor / Desarrollador:** **SynthesizerEnd** (`https://github.com/ArukuSynthesis/AnyWall`)
- **Application ID / Namespace:** `com.anywall.converter`
- **Versión actual:** `versionCode = 3` • `versionName = "0.3"`
- **Idioma de la interfaz:** Bilingüe Español / Inglés (`AppLanguageMode`: `Sistema`, `Español`, `English`)
- **Propósito:** Conversor universal nativo de Android (imágenes, documentos/PDF/XLSX/EPUB, audio, video, archivos comprimidos, datos estructurados y seguridad criptográfica) con procesamiento **100% local en el hardware del dispositivo**, sin dependencias en la nube y operable íntegramente en modo avión.
- **Estado actual:** Versión `v0.3` completa, funcional, sin stubs ni `// TODO`, con 5 motores locales (`ImageEngineImpl`, `DocumentEngineImpl`, `MediaEngineImpl`, `ArchiveEngineImpl`, `DataAndSecurityEngineImpl`), migraciones de Room (`v1 -> v2 -> v3` con `conversion_presets`), editores visuales de PDF e Imágenes, Firma Manuscrita, Bóveda Universal `AES-256-GCM`, QR offline, Inspector Forense & `SHA-256`, Diff local, Comparador Antes/Después, File Splitter/Joiner, Pipelines de 2 pasos y suite completa de pruebas unitarias pasando al 100%.

---

## 2. Reglas Inquebrantables de Arquitectura

Cualquier iteración futura **debe respetar estrictamente** estas 7 reglas:

1. **ESTRICTAMENTE 100% OFFLINE:**
   - Prohibido añadir `<uses-permission android:name="android.permission.INTERNET" />` o habilitar `ACCESS_NETWORK_STATE` en `app/src/main/AndroidManifest.xml` (de hecho, `ACCESS_NETWORK_STATE` tiene `tools:node="remove"` para eliminar el permiso transitivo de WorkManager).
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
   - Lectura exclusiva mediante `ContentResolver` (`UriFileResolver`), incluyendo recorrido recursivo de árboles SAF (`OpenDocumentTree`).
   - Escritura en `Downloads/AnyWall/` usando `MediaStore.Downloads` con `IS_PENDING` en API 29+ y almacenamiento público + `FileProvider` (con fallback a `getExternalFilesDir`) en API 26-28.
   - Registro automático de cada archivo generado en Room (`anywall_converter.db`, esquema v2) y apertura/compartición individual o múltiple mediante `FileProvider` / `MediaStore`.

---

## 3. Stack Técnico y Versiones Exactas

Definido en `gradle/libs.versions.toml` y `app/build.gradle.kts`:

| Componente | Versión / Configuración |
| :--- | :--- |
| **SDK Android** | `minSdk = 26`, `targetSdk = 35`, `compileSdk = 35` |
| **Gradle / AGP / Kotlin / KSP** | Gradle `8.9`, AGP `8.7.3`, Kotlin `2.0.21`, KSP `2.0.21-1.0.28`, JVM Target `17` |
| **UI & Compose** | Compose BOM `2024.12.01` (Material 3, Material Icons Extended), `navigation-compose:2.8.5`, `lifecycle-viewmodel-compose:2.8.7`, `activity-compose:1.9.3` |
| **Persistencia & Background** | `room-runtime/ktx/compiler:2.6.1` (Esquema v2 + `MIGRATION_1_2`), `work-runtime-ktx:2.10.0`, `kotlinx-coroutines-android:1.9.0` |
| **Imágenes / SVG** | `com.caverock:androidsvg-aar:1.4` + APIs nativas (`ImageDecoder`, `BitmapFactory`, `PdfRenderer`, `PdfDocument`) |
| **Documentos / PDF** | `com.tom-roush:pdfbox-android:2.0.27.0` + `org.zwobble.mammoth:mammoth:1.5.0` |
| **Audio / Video** | `androidx.media3:media3-{transformer,effect,common,muxer}:1.4.1` + `de.sciss:jump3r:1.0.5` |
| **Archivos Comprimidos** | `org.apache.commons:commons-compress:1.26.1` + `org.tukaani:xz:1.9` |

---

## 4. Mapa de Paquetes y Archivos (v0.2)

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
        ├── MainActivity.kt                    # Share Sheet, POST_NOTIFICATIONS, tema dinámico/manual, NavHost
        ├── core/
        │   ├── AppContainer.kt                # Contenedor DI manual
        │   ├── AppPreferences.kt              # Preferencias locales (tema Sistema/Claro/Oscuro, autolimpieza, EXIF)
        │   ├── FileShareHelper.kt             # Abrir (ACTION_VIEW) y compartir uno o varios (ACTION_SEND / SEND_MULTIPLE)
        │   ├── LocalThumbnailLoader.kt        # Miniaturas locales 96x96 con LruCache (imágenes, SVG, PDF pág. 1, video frame)
        │   ├── NotificationHelper.kt          # Canal de notificaciones, ForegroundInfo y notificación de finalización
        │   ├── ScopedStorageManager.kt        # Guardado en Downloads/AnyWall/ (MediaStore API 29+ / Legacy API 26-28)
        │   └── UriFileResolver.kt             # ContentResolver, SAF OpenDocumentTree recursivo, detección MIME/magic bytes
        ├── domain/
        │   ├── engine/
        │   │   ├── FileConverterEngine.kt     # Interfaz común de motores
        │   │   └── ConverterEngineRegistry.kt # Enrutamiento inteligente origen/destino -> motor (incl. lotes mixtos Imagen+Doc)
        │   ├── model/
        │   │   ├── ActiveJobState.kt
        │   │   ├── ConversionJobResult.kt     # Incluye sourceSizeBytes en v0.2
        │   │   ├── ConversionOptions.kt       # Calidad, rotación, EXIF, PDF password/página, pdfPageEditsJson, video mute, trim
        │   │   ├── FormatCategory.kt
        │   │   ├── HistoryRecord.kt           # Calcula ahorro de peso (Antes -> Después, -%)
        │   │   ├── InputFileItem.kt
        │   │   ├── PdfActionMode.kt           # Incluye PROTECT_PASSWORD y UNLOCK_PASSWORD
        │   │   ├── PdfPageConfig.kt           # PdfPageSize (A4 / Carta) y PdfPageOrientation (Vertical / Horizontal)
        │   │   ├── PdfPageEditSpec.kt         # Modelo y serializador determinista de páginas editadas/rotadas/reordenadas
        │   │   ├── SupportedFormat.kt         # Incluye HEIC, GIF, TAR_XZ, TAR_BZ2, XZ, BZ2
        │   │   └── VideoResolution.kt
        │   └── repository/
        │       └── ConversionHistoryRepository.kt
        ├── data/
        │   ├── local/
        │   │   ├── AnyWallDatabase.kt         # Esquema v2 con MIGRATION_1_2 y exportSchema = true
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
        │   └── ConversionWorkScheduler.kt     # Encolado seguro APPEND_OR_REPLACE + verificación de trabajos activos
        └── ui/
            ├── theme/{Color,Theme,Type}.kt
            ├── navigation/{AnyWallDestination,AnyWallNavHost}.kt
            ├── components/
            │   ├── AboutCreditsDialog.kt      # Créditos de SynthesizerEnd y catálogo de 9 repositorios Open Source
            │   ├── CategoryQuickCard.kt
            │   ├── ConversionProgressCard.kt
            │   ├── FormatChipSelector.kt
            │   ├── HistoryItemCard.kt         # Miniatura + insignia de ahorro de peso + modo multiselección
            │   ├── InputFileCard.kt           # Miniatura + botones ↑/↓ de reordenamiento del lote
            │   ├── OfflinePrivacyBadge.kt
            │   ├── PdfVisualEditorDialog.kt   # Modificador visual de páginas PDF en cuadrícula + zoom de página completa
            │   ├── SettingsDialog.kt          # Ajustes locales (tema, EXIF, autolimpieza, liberación de caché y acceso a Créditos)
            │   └── ThumbnailPreviewBox.kt     # Previsualización asíncrona con respaldo de insignia
            └── screens/
                ├── MainViewModel.kt
                ├── HomeScreen.kt
                ├── ConfigureScreen.kt
                └── HistoryScreen.kt
```

Pruebas unitarias y avisos legales actuales:
- `app/src/test/java/com/anywall/converter/AnyWallEngineUnitTest.kt`
- `THIRD_PARTY_NOTICES.md` y `README.md`

---

## 5. Novedades Técnicas de v0.2 en los 4 Motores y la UI

### Motor 1: `ImageEngineImpl`
- **Formatos de entrada:** `JPG`, `PNG`, `WEBP`, `BMP`, `SVG`, y ahora también **`HEIC`/`HEIF`** y **`GIF`** (con detección por extensión, MIME e inspección de cabeceras mágicas `GIF8` y `ftypheic`/`ftypmif1`).
- **Rotación y Privacidad:** Soporta rotación `0°`, `90°`, `180°` y `270°` mediante `Matrix.postRotate()`, y limpieza de metadatos EXIF/GPS al recodificar las muestras de píxeles.
- **Imágenes → PDF cifrado y extracción de páginas seleccionadas:** Permite aplicar contraseña de protección (`StandardProtectionPolicy` de PDFBox) directamente al PDF generado a partir de un lote de imágenes, y al exportar PDF a imágenes (`JPG`, `PNG`, `WEBP`) respeta el orden, rotación por página y páginas excluidas del modificador visual (`PdfPageEditSpec`).

### Motor 2: `DocumentEngineImpl` + `WebViewPdfGenerator`
- **Modificador Visual de PDF en Vista Previa (`PdfVisualEditorDialog` + `PdfPageEditSpec`):**
  - Renderiza miniaturas reales de todas las páginas de todos los PDFs cargados en el lote mediante `PdfRenderer` en segundo plano.
  - Permite **rotar páginas individuales** (`+90°`), **reordenar páginas** libremente (`←` / `→`), **excluir o incluir páginas** con un toque y abrir una **vista previa ampliada** de cualquier página.
  - Al exportar o unir PDFs (o exportar a `TXT` / imágenes), aplica exactamente la secuencia, rotación (`PDPage.rotation`) y selección de páginas configurada en la vista previa.
- **Fusión de lotes mixtos (Imágenes + Documentos/PDF):** Permite mezclar en un mismo lote archivos PDF, documentos (`DOCX`, `HTML`, `MD`, `TXT`) e imágenes (`JPG`, `PNG`, `WEBP`, `HEIC`, `GIF`, `BMP`, `SVG`) para generar un único PDF consolidado o convertirlos individualmente.
- **Protección y Desbloqueo de PDF con contraseña:**
  - `PdfActionMode.PROTECT_PASSWORD`: cifra archivos PDF con `AccessPermission` + `StandardProtectionPolicy` (128 bits).
  - `PdfActionMode.UNLOCK_PASSWORD`: abre un PDF cifrado con su clave actual y elimina la seguridad con `doc.isAllSecurityToBeRemoved = true`.
- **Tamaño y orientación de página:** `WebViewPdfGenerator` soporta `PdfPageSize.A4` (`ISO_A4`) y `PdfPageSize.LETTER` (`NA_LETTER`), tanto en `PdfPageOrientation.PORTRAIT` como `PdfPageOrientation.LANDSCAPE`.

### Motor 3: `MediaEngineImpl` + `PcmWavMp3AudioHelper`
- **Silenciar video:** Interruptor `muteVideoAudio` que configura `EditedMediaItem.Builder(mediaItem).setRemoveAudio(true)` en `Media3 Transformer`.
- **Recorte de tiempo (Trim inicio–fin):** Campos de segundo inicial y final aplicados mediante `MediaItem.ClippingConfiguration` tanto para conversión de video como para extracción/conversión de audio (`MP3`, `AAC`, `M4A`, `WAV`).

### Motor 4: `ArchiveEngineImpl`
- **Nuevos formatos de empaquetado y extracción:**
  - Empaquetado por lote en `.zip`, `.tar`, `.tar.gz`, **`.tar.xz`** (`XZCompressorOutputStream`) y **`.tar.bz2`** (`BZip2CompressorOutputStream`).
  - Extracción completa de `.zip`, `.tar`, `.tar.gz`, **`.tar.xz`**, **`.tar.bz2`**, `.gz`, **`.xz`**, **`.bz2`** y `.7z` con detección automática de TAR interno y sanitización anti-*Zip-Slip* (`splitEntryFolderAndName`).

### Soporte Avanzado Multi-Archivo, Carpetas, Créditos y Room v2
- **Importación de carpetas completas (`OpenDocumentTree`):** `UriFileResolver.resolveFolderTreeUris` recorre recursivamente cualquier directorio seleccionado por el usuario mediante `DocumentsContract.buildChildDocumentsUriUsingTree` e importa todos los archivos compatibles de una sola vez.
- **Herramientas rápidas de ordenamiento por lote:** Botones en `ConfigureScreen` para ordenar el lote por **Nombre (A–Z)**, **Tamaño**, **Invertir orden**, o mover archivos individualmente (`↑`/`↓`).
- **Créditos y Licencias Open Source (`AboutCreditsDialog` + `THIRD_PARTY_NOTICES.md`):** Atribución oficial a **SynthesizerEnd** y listado detallado de los 9 proyectos Open Source utilizados con sus identificadores SPDX y enlaces copiables a sus repositorios oficiales.
- **Room v2 (`MIGRATION_1_2`) e insignia de ahorro de peso:** Añade `source_size_bytes` a `conversion_history` con migración SQL explícita y muestra la variación porcentual (`Antes → Después, -%`).
- **Miniaturas locales (`LocalThumbnailLoader`) y Multiselección en Historial:** Caché `LruCache` de 4 MB en memoria para miniaturas 96x96 px y modo de selección múltiple en `HistoryScreen` para compartir o eliminar varios archivos en bloque.
- **Cola segura y Autolimpieza de caché:** `ConversionWorkScheduler` usa `ExistingWorkPolicy.APPEND_OR_REPLACE`, y `MainViewModel` limpia automáticamente `anywall_work` / `anywall_payloads` al iniciar la app cuando no hay trabajos activos.

---

## 6. Guía de Compilación y Verificación

```bash
# Compilar APK debug/release y ejecutar pruebas unitarias
./gradlew assembleDebug testDebugUnitTest

# Compilar APK release v0.2 firmado mediante variables de entorno
ANYWALL_KEYSTORE_PATH=/ruta/keystore.jks \
ANYWALL_KEYSTORE_PASSWORD="..." \
ANYWALL_KEY_ALIAS="..." \
ANYWALL_KEY_PASSWORD="..." \
./gradlew assembleRelease testReleaseUnitTest

# Auditar permisos reales en el APK final (confirmar 0 permisos de red)
aapt2 dump permissions app/build/outputs/apk/release/app-release.apk
grep -rn "TODO" app/src/
```
