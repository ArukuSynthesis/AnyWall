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
}
