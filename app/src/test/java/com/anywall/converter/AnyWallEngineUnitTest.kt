package com.anywall.converter

import com.anywall.converter.core.UriFileResolver
import com.anywall.converter.data.engines.AesVaultCrypto
import com.anywall.converter.data.engines.AnimatedGifPureEncoder
import com.anywall.converter.data.engines.ArchiveEngineImpl
import com.anywall.converter.data.engines.DocumentEngineImpl
import com.anywall.converter.data.engines.EpubPureConverter
import com.anywall.converter.data.engines.FileSplitterAndJoiner
import com.anywall.converter.data.engines.IcoPureEncoder
import com.anywall.converter.data.engines.ImageEngineImpl
import com.anywall.converter.data.engines.LineDiffEngine
import com.anywall.converter.data.engines.PcmWavMp3AudioHelper
import com.anywall.converter.data.engines.QrCodeHelper
import com.anywall.converter.data.engines.StructuredDataConverter
import com.anywall.converter.data.engines.XlsxPureParser
import com.anywall.converter.domain.model.ArchiveCompressionLevel
import com.anywall.converter.domain.model.ConversionOptions
import com.anywall.converter.domain.model.FormatCategory
import com.anywall.converter.domain.model.HistoryRecord
import com.anywall.converter.domain.model.ImageAspectCanvasMode
import com.anywall.converter.domain.model.ImageBatchLayoutMode
import com.anywall.converter.domain.model.ImageEditSpec
import com.anywall.converter.domain.model.ImageFilterPreset
import com.anywall.converter.domain.model.InputFileItem
import com.anywall.converter.domain.model.LineEndingOption
import com.anywall.converter.domain.model.NormalizedRedactionBox
import com.anywall.converter.domain.model.OutputNamingTemplateResolver
import com.anywall.converter.domain.model.PdfPageEditItem
import com.anywall.converter.domain.model.SecondaryPipelineAction
import com.anywall.converter.domain.model.SupportedFormat
import com.anywall.converter.ui.components.AnyWallCreditsCatalog
import com.anywall.converter.data.engines.BatchFileRenamer
import com.anywall.converter.data.engines.DuplicateFilesDetector
import com.anywall.converter.data.engines.LibArchiveRarBridge
import com.anywall.converter.domain.model.AudioMetadataSpec
import com.anywall.converter.domain.model.CensorEffectMode
import com.anywall.converter.domain.model.CropAspectRatioPreset
import com.anywall.converter.domain.model.NormalizedRedactionStroke
import com.anywall.converter.domain.model.OutputSizeEstimator
import com.anywall.converter.domain.model.PdfAnnotationType
import com.anywall.converter.domain.model.PdfPageAnnotation
import com.anywall.converter.domain.model.PerspectiveQuadSpec
import com.anywall.converter.domain.model.VideoCropSpec
import com.anywall.converter.ui.components.GitHubUpdaterHelper
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.sin

class AnyWallEngineUnitTest {

    @Test
    fun creditsCatalog_includesSynthesizerEndAndAllThirdPartyRepositories() {
        assertEquals("SynthesizerEnd", AnyWallCreditsCatalog.DEVELOPER_NAME)
        assertTrue(AnyWallCreditsCatalog.thirdPartyLibraries.size >= 10)
        assertTrue(
            AnyWallCreditsCatalog.thirdPartyLibraries.all {
                it.repositoryUrl.startsWith("https://github.com/") && it.license.isNotBlank()
            }
        )
    }

    @Test
    fun pdfPageEditItem_andImageEditSpec_serializeAndDeserializeCorrectly() {
        val pdfOriginal = listOf(
            PdfPageEditItem(
                id = "content://pdf/1#0",
                sourceUriString = "content://pdf/1",
                sourceDisplayName = "doc1.pdf",
                originalPageIndex = 0,
                totalPagesInSource = 3,
                rotationDegrees = 90,
                included = true,
                isBlankSeparator = false,
                hasSignatureStamp = true,
                redactionBoxes = listOf(NormalizedRedactionBox(0.1f, 0.2f, 0.8f, 0.35f, false))
            ),
            PdfPageEditItem(
                id = "blank_1",
                sourceUriString = "",
                sourceDisplayName = "En blanco",
                originalPageIndex = 0,
                totalPagesInSource = 1,
                rotationDegrees = 0,
                included = true,
                isBlankSeparator = true
            )
        )

        val serializedPdf = PdfPageEditItem.toJsonArrayString(pdfOriginal)
        val restoredPdf = PdfPageEditItem.fromJsonArrayString(serializedPdf)
        assertEquals(2, restoredPdf.size)
        assertEquals(90, restoredPdf[0].normalizedRotation)
        assertTrue(restoredPdf[0].hasSignatureStamp)
        assertEquals(1, restoredPdf[0].redactionBoxes.size)
        assertTrue(restoredPdf[1].isBlankSeparator)

        val imgSpec = ImageEditSpec(
            cropLeftNorm = 0.1f,
            cropTopNorm = 0.15f,
            cropRightNorm = 0.85f,
            cropBottomNorm = 0.9f,
            flipHorizontal = true,
            flipVertical = false,
            filterPreset = ImageFilterPreset.DOCUMENT_BW_HIGH_CONTRAST,
            aspectCanvasMode = ImageAspectCanvasMode.SQUARE_1_1_BLUR,
            watermarkText = "© SynthesizerEnd",
            watermarkOpacityPercent = 60,
            redactionBoxes = listOf(NormalizedRedactionBox(0.2f, 0.3f, 0.7f, 0.5f, true))
        )
        val rawImg = imgSpec.toSerializedString()
        val restoredImg = ImageEditSpec.fromSerializedString(rawImg)
        assertTrue(restoredImg.flipHorizontal)
        assertEquals(ImageFilterPreset.DOCUMENT_BW_HIGH_CONTRAST, restoredImg.filterPreset)
        assertEquals(ImageAspectCanvasMode.SQUARE_1_1_BLUR, restoredImg.aspectCanvasMode)
        assertEquals("© SynthesizerEnd", restoredImg.watermarkText)
        assertEquals(1, restoredImg.redactionBoxes.size)
        assertTrue(restoredImg.redactionBoxes[0].isPixelated)
    }

    @Test
    fun conversionOptions_andNamingTemplates_resolveAndSerializeCorrectly() {
        val resolvedName = OutputNamingTemplateResolver.resolveBaseName(
            templateOrCustomName = "Factura_{nombre}_{indice}_{fecha}_{formato}",
            originalBaseName = "Enero",
            batchIndexOneBased = 3,
            batchTotalCount = 12,
            targetFormat = SupportedFormat.PDF,
            fixedDateString = "20261119"
        )
        assertEquals("Factura_Enero_03_20261119_pdf", resolvedName)

        val opts = ConversionOptions(
            qualityPercent = 78,
            targetImageMaxSizeKb = 350,
            imageBatchLayoutMode = ImageBatchLayoutMode.COLLAGE_GRID_2X2,
            audioBitrateKbps = 320,
            pdfWatermarkText = "CONFIDENCIAL",
            addPdfPageNumbers = true,
            archiveCompressionLevel = ArchiveCompressionLevel.MAXIMUM,
            secondaryPipelineAction = SecondaryPipelineAction.PACK_ZIP
        )
        val raw = opts.serializeToString()
        val restored = ConversionOptions.deserializeFromString(raw)
        assertEquals(78, restored.qualityPercent)
        assertEquals(350, restored.targetImageMaxSizeKb)
        assertEquals(ImageBatchLayoutMode.COLLAGE_GRID_2X2, restored.imageBatchLayoutMode)
        assertEquals(320, restored.audioBitrateKbps)
        assertEquals("CONFIDENCIAL", restored.pdfWatermarkText)
        assertTrue(restored.addPdfPageNumbers)
        assertEquals(ArchiveCompressionLevel.MAXIMUM, restored.archiveCompressionLevel)
        assertEquals(SecondaryPipelineAction.PACK_ZIP, restored.secondaryPipelineAction)
    }

    @Test
    fun detectFormats_recognizesAllV03ExtensionsAndMimeTypes() {
        assertEquals(SupportedFormat.JPG, SupportedFormat.detect("foto.jpeg", "image/jpeg"))
        assertEquals(SupportedFormat.AVIF, SupportedFormat.detect("moderna.avif", "image/avif"))
        assertEquals(SupportedFormat.ICO, SupportedFormat.detect("favicon.ico", "image/x-icon"))
        assertEquals(SupportedFormat.HEIC, SupportedFormat.detect("iphone.heic", "image/heic"))
        assertEquals(SupportedFormat.GIF, SupportedFormat.detect("animacion.gif", "image/gif"))
        assertEquals(SupportedFormat.PDF, SupportedFormat.detect("informe.pdf", "application/pdf"))
        assertEquals(SupportedFormat.XLSX, SupportedFormat.detect("ventas.xlsx", null))
        assertEquals(SupportedFormat.EPUB, SupportedFormat.detect("novela.epub", "application/epub+zip"))
        assertEquals(SupportedFormat.FLAC, SupportedFormat.detect("estudio.flac", "audio/flac"))
        assertEquals(SupportedFormat.OGG, SupportedFormat.detect("audio.ogg", "audio/ogg"))
        assertEquals(SupportedFormat.OPUS, SupportedFormat.detect("nota.opus", "audio/opus"))
        assertEquals(SupportedFormat.TAR_XZ, SupportedFormat.detect("backup.tar.xz", "application/x-xz"))
        assertEquals(SupportedFormat.FILE_PARTS, SupportedFormat.detect("video.mp4.part001", null))
        assertEquals(SupportedFormat.CSV, SupportedFormat.detect("tabla.csv", "text/csv"))
        assertEquals(SupportedFormat.JSON, SupportedFormat.detect("config.json", "application/json"))
        assertEquals(SupportedFormat.XML, SupportedFormat.detect("datos.xml", "application/xml"))
        assertEquals(SupportedFormat.YAML, SupportedFormat.detect("pipeline.yaml", "application/x-yaml"))
        assertEquals(SupportedFormat.ANYWALL_VAULT, SupportedFormat.detect("secreto.anywall", null))
    }

    @Test
    fun aesVaultCrypto_encryptsAndDecryptsWithAuthenticationAndHashes() {
        val originalText = "Documento ultrasecreto de SynthesizerEnd en AnyWall v0.3 • 100% Offline"
        val plainBytes = originalText.toByteArray(Charsets.UTF_8)

        val vaultBytes = AesVaultCrypto.encryptBytesToVault(
            plainBytes = plainBytes,
            originalFileName = "secreto.txt",
            password = "ClaveSegura2026!"
        )
        assertEquals("AWV1", String(vaultBytes, 0, 4, Charsets.US_ASCII))

        val decrypted = AesVaultCrypto.decryptVaultBytes(vaultBytes, "ClaveSegura2026!")
        assertEquals("secreto.txt", decrypted.originalFileName)
        assertArrayEquals(plainBytes, decrypted.plainBytes)

        var failedWithWrongPass = false
        try {
            AesVaultCrypto.decryptVaultBytes(vaultBytes, "ClaveIncorrecta")
        } catch (_: Throwable) {
            failedWithWrongPass = true
        }
        assertTrue("Debe fallar la autenticación GCM con contraseña incorrecta", failedWithWrongPass)

        val sha256 = AesVaultCrypto.computeSha256Hex(plainBytes)
        val md5 = AesVaultCrypto.computeMd5Hex(plainBytes)
        assertEquals(64, sha256.length)
        assertEquals(32, md5.length)
    }

    @Test
    fun structuredDataConverter_convertsCsvJsonXmlYamlAndBase64() {
        val csv = "nombre,rol\nNeon,Arquitecto\nArgon,Constructor"
        val json = StructuredDataConverter.convertStructuredText(
            sourceText = csv,
            sourceFormat = SupportedFormat.CSV,
            targetFormat = SupportedFormat.JSON,
            prettyPrint = false
        )
        assertTrue(json.contains("\"nombre\":\"Neon\""))
        assertTrue(json.contains("\"rol\":\"Constructor\""))

        val xml = StructuredDataConverter.convertStructuredText(
            sourceText = json,
            sourceFormat = SupportedFormat.JSON,
            targetFormat = SupportedFormat.XML,
            prettyPrint = false
        )
        assertTrue(xml.contains("<nombre>Neon</nombre>"))

        val yaml = StructuredDataConverter.convertStructuredText(
            sourceText = xml,
            sourceFormat = SupportedFormat.XML,
            targetFormat = SupportedFormat.YAML
        )
        assertTrue(yaml.contains("nombre: Neon"))

        val backToCsv = StructuredDataConverter.convertStructuredText(
            sourceText = yaml,
            sourceFormat = SupportedFormat.YAML,
            targetFormat = SupportedFormat.CSV,
            lineEndingOption = LineEndingOption.CRLF
        )
        assertTrue(backToCsv.contains("\r\n"))
        assertTrue(backToCsv.contains("Neon,Arquitecto"))
    }

    @Test
    fun xlsxPureParser_andEpubPureConverter_roundTripValidFiles() {
        // 1. Crear XLSX sintético en memoria
        val xlsxBytes = ByteArrayOutputStream().use { baos ->
            ZipOutputStream(baos).use { zos ->
                zos.putNextEntry(ZipEntry("xl/sharedStrings.xml"))
                zos.write(
                    "<sst><si><t>Producto</t></si><si><t>Precio</t></si><si><t>AnyWall Pro</t></si></sst>"
                        .toByteArray(Charsets.UTF_8)
                )
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("xl/workbook.xml"))
                zos.write("<workbook><sheets><sheet name=\"Ventas\"/></sheets></workbook>".toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
                zos.write(
                    """
                    <worksheet><sheetData>
                      <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c></row>
                      <row r="2"><c r="A2" t="s"><v>2</v></c><c r="B2"><v>100</v></c></row>
                    </sheetData></worksheet>
                    """.trimIndent().toByteArray(Charsets.UTF_8)
                )
                zos.closeEntry()
            }
            baos.toByteArray()
        }

        val sheets = XlsxPureParser.parseXlsxStream(ByteArrayInputStream(xlsxBytes))
        assertEquals(1, sheets.size)
        assertEquals("Ventas", sheets[0].sheetName)
        assertEquals(listOf("Producto", "Precio"), sheets[0].rows[0])
        assertEquals(listOf("AnyWall Pro", "100"), sheets[0].rows[1])

        // 2. Round-trip EPUB 3
        val epubBytes = ByteArrayOutputStream().use { baos ->
            EpubPureConverter.writeEpubFromHtmlBody(
                title = "Manual Offline AnyWall",
                author = "SynthesizerEnd",
                bodyHtml = "<p>Capítulo uno completamente sin conexión.</p>",
                output = baos
            )
            baos.toByteArray()
        }
        val parsedEpub = EpubPureConverter.readEpubStream(ByteArrayInputStream(epubBytes))
        assertEquals("Manual Offline AnyWall", parsedEpub.title)
        assertEquals("SynthesizerEnd", parsedEpub.author)
        assertTrue(parsedEpub.plainText.contains("Capítulo uno completamente sin conexión."))
    }

    @Test
    fun fileSplitterAndJoiner_splitsReconstructsAndVerifiesSha256() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "anywall_split_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        val sourceFile = File(tempDir, "dataset.bin")
        val rejoinedFile = File(tempDir, "rejoined.bin")
        try {
            val payload = ByteArray(10_500) { idx -> (idx % 251).toByte() }
            sourceFile.writeBytes(payload)

            val splitRes = FileSplitterAndJoiner.splitFileIntoParts(
                sourceFile = sourceFile,
                originalFileName = "dataset.bin",
                outputDir = tempDir,
                maxPartSizeBytes = 4096L
            )
            assertEquals(3, splitRes.partFiles.size)
            assertTrue(splitRes.manifestFile.exists())

            val expectedHash = FileSplitterAndJoiner.parseExpectedSha256FromManifest(
                splitRes.manifestFile.readText(Charsets.UTF_8)
            )
            assertEquals(splitRes.originalSha256, expectedHash)

            val joinRes = FileSplitterAndJoiner.joinPartFiles(
                partFiles = splitRes.partFiles.shuffled(),
                outputFile = rejoinedFile,
                expectedSha256Hex = expectedHash
            )
            assertTrue(joinRes.verifiedWithManifest)
            assertArrayEquals(payload, rejoinedFile.readBytes())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun lineDiffEngine_animatedGif_ico_andQrSvg_generateValidOutputs() {
        // 1. Diff LCS
        val report = LineDiffEngine.computeDiff(
            leftText = "linea 1\nlinea 2\nlinea 3",
            rightText = "linea 1\nlinea 2 modificada\nlinea 3\nlinea 4"
        )
        assertEquals(2, report.addedCount)
        assertEquals(1, report.removedCount)
        assertEquals(2, report.unchangedCount)

        // 2. GIF89a animado
        val frame1 = AnimatedGifPureEncoder.GifFrame(4, 4, IntArray(16) { 0xFFFF0000.toInt() })
        val frame2 = AnimatedGifPureEncoder.GifFrame(4, 4, IntArray(16) { 0xFF00FF00.toInt() })
        val gifBytes = AnimatedGifPureEncoder.encodeAnimatedGifToBytes(listOf(frame1, frame2), fps = 4)
        assertEquals("GIF89a", String(gifBytes, 0, 6, Charsets.US_ASCII))
        assertEquals(0x3B.toByte(), gifBytes.last())

        // 3. ICO container
        val fakePng = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        val icoBytes = IcoPureEncoder.encodeSinglePngToIco(64, 64, fakePng)
        assertEquals(0, icoBytes[0].toInt())
        assertEquals(0, icoBytes[1].toInt())
        assertEquals(1, icoBytes[2].toInt())
        assertEquals(0, icoBytes[3].toInt())

        // 4. QR SVG con ZXing Core
        val qrSvg = QrCodeHelper.generateQrSvg("https://github.com/ArukuSynthesis/AnyWall")
        assertTrue(qrSvg.contains("<svg"))
        assertTrue(qrSvg.contains("<path"))

        val decodedQr = QrCodeHelper.decodeQrFromArgbPixels(
            width = 128,
            height = 128,
            argbPixels = run {
                val matrix = QrCodeHelper.encodeQrMatrix("AnyWall v0.3 SynthesizerEnd", 128)
                IntArray(128 * 128) { idx ->
                    val x = idx % 128
                    val y = idx / 128
                    if (matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                }
            }
        )
        assertEquals("AnyWall v0.3 SynthesizerEnd", decodedQr)
    }

    @Test
    fun parsePageIndices_formatBytes_zipSlip_markdown_andPcmAudio_workCorrectly() {
        val indices = ImageEngineImpl.parsePageIndices("1-3, 5, 8-9", totalPages = 10)
        assertEquals(listOf(0, 1, 2, 4, 7, 8), indices)

        assertEquals("512 B", InputFileItem.formatBytes(512))
        assertEquals("2.0 KB", InputFileItem.formatBytes(2048))

        val record = HistoryRecord(
            id = 1L,
            sourceDisplayName = "original.png",
            sourceFormat = SupportedFormat.PNG,
            sourceSizeBytes = 1_000_000L,
            outputDisplayName = "convertido.webp",
            outputFormat = SupportedFormat.WEBP,
            category = FormatCategory.IMAGE,
            outputUriString = "content://downloads/1",
            outputAbsolutePath = "/storage/emulated/0/Download/AnyWall/convertido.webp",
            outputSizeBytes = 350_000L,
            outputMimeType = "image/webp",
            createdAtMillis = 1700000000000L
        )
        assertEquals(-65, record.sizeChangePercent)

        val (folder, name) = ArchiveEngineImpl.splitEntryFolderAndName(
            rootSubFolder = "paquete",
            rawEntryName = "../../../etc/subcarpeta/secreto.txt"
        )
        assertEquals("paquete/etc/subcarpeta", folder)
        assertEquals("secreto.txt", name)

        val html = DocumentEngineImpl.convertMarkdownToHtml("# Hola\nTexto con **negrita**.")
        assertTrue(html.contains("<h1>Hola</h1>"))
        assertTrue(html.contains("<strong>negrita</strong>"))

        val sampleRate = 22050
        val numSamples = sampleRate / 2
        val pcmBytes = ByteArray(numSamples * 2)
        val bb = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until numSamples) {
            val sample = (sin(2.0 * Math.PI * 440.0 * i / sampleRate) * 16000).toInt().toShort()
            bb.putShort(sample)
        }

        val tempPcm = File.createTempFile("test_pcm_", ".pcm")
        val tempFaded = File.createTempFile("test_faded_", ".pcm")
        val tempWav = File.createTempFile("test_wav_", ".wav")
        val tempMp3 = File.createTempFile("test_mp3_", ".mp3")
        try {
            tempPcm.writeBytes(pcmBytes)
            val info = PcmWavMp3AudioHelper.DecodedPcmInfo(
                pcmFile = tempPcm,
                sampleRate = sampleRate,
                channelCount = 1,
                totalPcmBytes = pcmBytes.size.toLong()
            )
            val fadedInfo = PcmWavMp3AudioHelper.applyFadeInOutToPcm16Le(info, 1, 1, tempFaded)
            assertNotNull(fadedInfo)
            PcmWavMp3AudioHelper.writeWavFileFromPcm16Le(fadedInfo, tempWav)
            assertEquals(44L + pcmBytes.size, tempWav.length())

            PcmWavMp3AudioHelper.encodePcm16LeToMp3(
                pcmInfo = fadedInfo,
                outputMp3File = tempMp3,
                qualityPercent = 80,
                explicitBitrateKbps = 192,
                onProgress = {}
            )
            assertTrue(tempMp3.length() > 256L)
        } finally {
            tempPcm.delete()
            tempFaded.delete()
            tempWav.delete()
            tempMp3.delete()
        }
    }

    @Test
    fun v04_photoStudio_pdfTouchAnnotations_videoCrop_andAudioMetadata_roundTripSerialization() {
        val stroke = NormalizedRedactionStroke(
            points = listOf(0.12f to 0.18f, 0.45f to 0.52f, 0.78f to 0.84f),
            strokeWidthNorm = 0.06f,
            effectMode = CensorEffectMode.PIXELATE
        )
        val quad = PerspectiveQuadSpec(
            tlX = 0.05f, tlY = 0.06f,
            trX = 0.94f, trY = 0.04f,
            brX = 0.96f, brY = 0.95f,
            blX = 0.03f, blY = 0.92f
        )
        assertTrue(quad.isModified)

        val specV04 = ImageEditSpec(
            cropLeftNorm = 0.08f,
            cropTopNorm = 0.12f,
            cropRightNorm = 0.88f,
            cropBottomNorm = 0.92f,
            flipHorizontal = true,
            flipVertical = false,
            filterPreset = ImageFilterPreset.WARM_VINTAGE,
            aspectCanvasMode = ImageAspectCanvasMode.ORIGINAL,
            watermarkText = "SynthesizerEnd v0.4",
            watermarkOpacityPercent = 82,
            redactionBoxes = listOf(
                NormalizedRedactionBox(0.2f, 0.3f, 0.6f, 0.5f, isPixelated = false, effectMode = CensorEffectMode.BLUR)
            ),
            straightenDegrees = -14.5f,
            rotation90Degrees = 90,
            cropAspectRatioPreset = CropAspectRatioPreset.RATIO_4_5,
            perspectiveQuad = quad,
            exposure = 25.0f,
            brightness = 0.15f,
            contrast = 0.30f,
            saturation = -0.20f,
            temperature = 0.40f,
            sharpness = 55.0f,
            vignette = 0.35f,
            filterIntensityPercent = 75,
            redactionStrokes = listOf(stroke),
            watermarkPosXNorm = 0.42f,
            watermarkPosYNorm = 0.76f,
            watermarkScale = 1.45f,
            watermarkRotationDegrees = -22f,
            watermarkColorArgb = 0xFF00E5FF.toInt(),
            applyToAllBatch = false
        )

        val rawSpec = specV04.toSerializedString()
        val restoredSpec = ImageEditSpec.fromSerializedString(rawSpec)
        assertEquals(-14.5f, restoredSpec.straightenDegrees, 0.02f)
        assertEquals(90, restoredSpec.normalizedRotation90)
        assertEquals(CropAspectRatioPreset.RATIO_4_5, restoredSpec.cropAspectRatioPreset)
        assertTrue(restoredSpec.perspectiveQuad.isModified)
        assertEquals(25.0f, restoredSpec.exposure, 0.05f)
        assertEquals(55.0f, restoredSpec.sharpness, 0.05f)
        assertEquals(75, restoredSpec.filterIntensityPercent)
        assertEquals(1, restoredSpec.redactionStrokes.size)
        assertEquals(3, restoredSpec.redactionStrokes[0].points.size)
        assertEquals(CensorEffectMode.BLUR, restoredSpec.redactionBoxes[0].effectMode)
        assertEquals(1.45f, restoredSpec.watermarkScale, 0.01f)
        assertEquals(-22f, restoredSpec.watermarkRotationDegrees, 0.1f)
        assertFalse(restoredSpec.applyToAllBatch)

        val pdfItemV04 = PdfPageEditItem(
            id = "pdf_v04#0",
            sourceUriString = "content://pdf/v04",
            sourceDisplayName = "contrato.pdf",
            originalPageIndex = 0,
            totalPagesInSource = 2,
            rotationDegrees = 180,
            included = true,
            hasSignatureStamp = true,
            signatureXNorm = 0.58f,
            signatureYNorm = 0.78f,
            signatureWidthNorm = 0.32f,
            annotations = listOf(
                PdfPageAnnotation(
                    type = PdfAnnotationType.HIGHLIGHTER,
                    xNorm = 0.10f,
                    yNorm = 0.25f,
                    endXNorm = 0.80f,
                    endYNorm = 0.28f,
                    text = "",
                    colorArgb = 0xFFFFEB3B.toInt()
                ),
                PdfPageAnnotation(
                    type = PdfAnnotationType.DATE_STAMP,
                    xNorm = 0.70f,
                    yNorm = 0.12f,
                    text = "2026-11-24"
                )
            ),
            perspectiveQuad = quad
        )

        val pdfRaw = PdfPageEditItem.toJsonArrayString(listOf(pdfItemV04))
        val pdfRestored = PdfPageEditItem.fromJsonArrayString(pdfRaw).single()
        assertEquals(0.58f, pdfRestored.signatureXNorm, 0.01f)
        assertEquals(0.32f, pdfRestored.signatureWidthNorm, 0.01f)
        assertEquals(2, pdfRestored.annotations.size)
        assertEquals(PdfAnnotationType.HIGHLIGHTER, pdfRestored.annotations[0].type)
        assertEquals("2026-11-24", pdfRestored.annotations[1].text)
        assertTrue(pdfRestored.perspectiveQuad.isModified)

        val cropSpec = VideoCropSpec(leftNorm = 0.10f, topNorm = 0.15f, rightNorm = 0.90f, bottomNorm = 0.85f)
        assertTrue(cropSpec.isActive)
        val restoredCrop = VideoCropSpec.deserialize(cropSpec.serialize())
        assertEquals(0.10f, restoredCrop.leftNorm, 0.005f)
        assertEquals(0.85f, restoredCrop.bottomNorm, 0.005f)
        assertTrue(restoredCrop.ndcLeft < restoredCrop.ndcRight)
        assertTrue(restoredCrop.ndcBottom < restoredCrop.ndcTop)

        val audioMeta = AudioMetadataSpec(
            title = "Neon Horizon",
            artist = "SynthesizerEnd",
            album = "AnyWall v0.4",
            year = "2026",
            genre = "Synthwave",
            coverArtPathOrUri = "content://covers/1"
        )
        val restoredMeta = AudioMetadataSpec.deserialize(audioMeta.serialize())
        assertEquals("Neon Horizon", restoredMeta.title)
        assertEquals("SynthesizerEnd", restoredMeta.artist)
        assertEquals("content://covers/1", restoredMeta.coverArtUriString)
    }

    @Test
    fun v04_libArchiveRarBridge_sanitizesZipSlip_ordersVolumes_andExtractsRar5WithPassword() {
        val rar5Header = byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x01, 0x00)
        assertTrue(LibArchiveRarBridge.isRarMagicHeader(rar5Header))
        assertTrue(LibArchiveRarBridge.isRar5MagicHeader(rar5Header))

        assertEquals(
            "etc/shadow",
            LibArchiveRarBridge.sanitizeRarEntryPath("../../../etc/shadow")
        )
        assertEquals(
            "Windows/System32/drivers/etc/hosts",
            LibArchiveRarBridge.sanitizeRarEntryPath("C:\\Windows\\System32\\..\\drivers\\etc\\hosts")
        )

        val f1 = File("pack.part3.rar")
        val f2 = File("pack.part1.rar")
        val f3 = File("pack.part2.rar")
        val ordered = LibArchiveRarBridge.orderMultiVolumeRarFiles(listOf(f1, f2, f3))
        assertEquals(listOf("pack.part1.rar", "pack.part2.rar", "pack.part3.rar"), ordered.map { it.name })

        val rarBytes = LibArchiveRarBridge.buildSyntheticRar5ContainerForTesting(
            entries = listOf(
                "docs/leeme.txt" to "Hola desde RAR5 con libarchive BSD-2-Clause".toByteArray(Charsets.UTF_8),
                "../../escape/secreto.txt" to "Contenido protegido anti-Zip-Slip".toByteArray(Charsets.UTF_8)
            ),
            passphrase = "ClaveRar2026!"
        )
        val tempRar = File.createTempFile("test_v04_", ".rar")
        val extractDir = File(tempRar.parentFile, "rar5_out_${System.nanoTime()}").apply { mkdirs() }
        try {
            tempRar.writeBytes(rarBytes)
            val entries = LibArchiveRarBridge.inspectRarEntries(
                volumeFiles = listOf(tempRar),
                passphrase = "ClaveRar2026!"
            )
            assertEquals(2, entries.size)
            assertTrue(entries.all { it.isEncrypted })
            assertEquals("escape/secreto.txt", entries[1].entryPath)

            val extractedFiles = LibArchiveRarBridge.extractRarArchive(
                volumeFiles = listOf(tempRar),
                outputDir = extractDir,
                selectedEntries = setOf("docs/leeme.txt"),
                passphrase = "ClaveRar2026!"
            )
            assertEquals(1, extractedFiles.size)
            assertEquals(
                "Hola desde RAR5 con libarchive BSD-2-Clause",
                extractedFiles[0].readText(Charsets.UTF_8)
            )
        } finally {
            tempRar.delete()
            extractDir.deleteRecursively()
        }
    }

    @Test
    fun v04_audioAutoGain_waveformPeaks_andId3v23ApicTag_workCorrectly() {
        val sampleRate = 22050
        val sampleCount = 4096
        val quietPcm = ByteArray(sampleCount * 2)
        val bb = ByteBuffer.wrap(quietPcm).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until sampleCount) {
            val v = (sin(2.0 * Math.PI * 440.0 * i / sampleRate) * 3200.0).toInt().toShort()
            bb.putShort(v)
        }

        val inPcm = File.createTempFile("quiet_", ".pcm")
        val outPcm = File.createTempFile("norm_", ".pcm")
        val mp3File = File.createTempFile("tagged_", ".mp3")
        try {
            inPcm.writeBytes(quietPcm)
            val peaks = PcmWavMp3AudioHelper.extractWaveformPeaksFromPcmFile(inPcm, bucketCount = 32)
            assertEquals(32, peaks.size)
            assertTrue(peaks.all { it in 0.06f..1.0f })

            val info = PcmWavMp3AudioHelper.DecodedPcmInfo(
                pcmFile = inPcm,
                sampleRate = sampleRate,
                channelCount = 1,
                totalPcmBytes = quietPcm.size.toLong()
            )
            val normalizedInfo = PcmWavMp3AudioHelper.normalizePcm16LeToTargetDbFs(
                pcmInfo = info,
                outputPcmFile = outPcm,
                targetDbFs = -1.0
            )
            val newPeak = PcmWavMp3AudioHelper.computePeakAmplitudePcm16Le(normalizedInfo.pcmFile)
            // -1.0 dBFS de 32767 es ~29204
            assertTrue("Expected normalized peak near 29204, got $newPeak", newPeak in 28800..29500)

            PcmWavMp3AudioHelper.encodePcm16LeToMp3(
                pcmInfo = normalizedInfo,
                outputMp3File = mp3File,
                qualityPercent = 85,
                explicitBitrateKbps = 192,
                onProgress = {}
            )
            val fakeCoverJpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3, 4)
            val meta = AudioMetadataSpec(
                title = "Pista Normalizada",
                artist = "SynthesizerEnd",
                album = "AnyWall Studio",
                year = "2026",
                genre = "Electronic"
            )
            PcmWavMp3AudioHelper.writeId3v23TagsToMp3File(
                mp3File = mp3File,
                metadata = meta,
                coverArtBytes = fakeCoverJpeg,
                coverMimeType = "image/jpeg"
            )
            val parsedTags = PcmWavMp3AudioHelper.parseId3v23TagsFromBytes(mp3File.readBytes())
            assertEquals("Pista Normalizada", parsedTags["TIT2"])
            assertEquals("SynthesizerEnd", parsedTags["TPE1"])
            assertEquals("AnyWall Studio", parsedTags["TALB"])
            assertEquals("2026", parsedTags["TYER"])
            assertTrue((parsedTags["APIC_BYTES"]?.toIntOrNull() ?: 0) > fakeCoverJpeg.size)
        } finally {
            inPcm.delete()
            outPcm.delete()
            mp3File.delete()
        }
    }

    @Test
    fun v04_batchFileRenamer_duplicateDetector_andGitHubUpdaterHelper_workCorrectly() {
        val originals = listOf("Foto Vacaciones.JPG", "Foto Vacaciones.JPG", "Reporte Final.pdf")
        val renameSpec = BatchFileRenamer.BatchRenameSpec(
            template = "{fecha}_{nombre}_{indice}",
            prefix = "AW_",
            suffix = "_OK",
            findText = "Vacaciones",
            replaceText = "Playa",
            caseMode = BatchFileRenamer.RenameCaseMode.SNAKE_CASE,
            startIndex = 1,
            paddingDigits = 2
        )
        val preview = BatchFileRenamer.generatePreview(
            originalFileNames = originals,
            spec = renameSpec,
            fixedDateString = "20261124"
        )
        assertEquals(3, preview.size)
        assertEquals("aw_20261124_foto_playa_01_ok.JPG", preview[0].newFileName)
        assertEquals("aw_20261124_foto_playa_02_ok.JPG", preview[1].newFileName)

        val dup1 = File.createTempFile("dup_a_", ".bin")
        val dup2 = File.createTempFile("dup_b_", ".bin")
        val sameSizeDiff = File.createTempFile("diff_c_", ".bin")
        val uniqueSize = File.createTempFile("uniq_d_", ".bin")
        try {
            val payloadA = "Bloque identico para prueba SHA-256 en streaming".toByteArray(Charsets.UTF_8)
            val payloadB = "Bloque distinto pero exactamente del mismo peso!".toByteArray(Charsets.UTF_8)
            assertEquals(payloadA.size, payloadB.size)
            dup1.writeBytes(payloadA)
            dup2.writeBytes(payloadA)
            sameSizeDiff.writeBytes(payloadB)
            uniqueSize.writeBytes("Corto".toByteArray(Charsets.UTF_8))

            val report = DuplicateFilesDetector.scanFilesForDuplicates(
                listOf(dup1, dup2, sameSizeDiff, uniqueSize)
            )
            assertTrue(report.hasDuplicates)
            assertEquals(1, report.duplicateGroups.size)
            assertEquals(1, report.totalRedundantFilesCount)
            assertEquals(payloadA.size.toLong(), report.totalReclaimableBytes)
            assertEquals(3, report.deduplicatedList.size)
        } finally {
            dup1.delete()
            dup2.delete()
            sameSizeDiff.delete()
            uniqueSize.delete()
        }

        assertTrue(GitHubUpdaterHelper.compareVersions("0.4", "v0.5") < 0)
        assertTrue(GitHubUpdaterHelper.compareVersions("0.4", "v0.4.1") < 0)
        assertEquals(0, GitHubUpdaterHelper.compareVersions("0.4", "v0.4"))
        assertTrue(GitHubUpdaterHelper.compareVersions("0.4", "v0.3") > 0)

        val sampleReleaseJson = """
            {
              "tag_name": "v0.5",
              "name": "AnyWall: Universal Converter v0.5",
              "published_at": "2026-11-24T12:00:00Z",
              "html_url": "https://github.com/ArukuSynthesis/AnyWall/releases/tag/v0.5",
              "body": "Mejoras de rendimiento y nuevos filtros.",
              "assets": [
                {
                  "name": "AnyWall-v0.5-release.apk",
                  "size": 18450944,
                  "browser_download_url": "https://github.com/ArukuSynthesis/AnyWall/releases/download/v0.5/AnyWall-v0.5-release.apk"
                }
              ]
            }
        """.trimIndent()
        val parsedRelease = GitHubUpdaterHelper.parseLatestReleaseJson(sampleReleaseJson)
        assertNotNull(parsedRelease)
        assertEquals("v0.5", parsedRelease!!.tagName)
        assertEquals("AnyWall-v0.5-release.apk", parsedRelease.apkFileName)
        assertEquals(18450944L, parsedRelease.apkSizeBytes)
    }
}
