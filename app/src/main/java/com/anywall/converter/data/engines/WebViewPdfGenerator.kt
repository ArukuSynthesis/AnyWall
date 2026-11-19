package com.anywall.converter.data.engines

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PdfPrintCallbackBridge
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.ceil
import kotlin.math.max

/**
 * Renderizador 100% local de HTML a PDF usando un `WebView` offscreen en el hilo principal
 * acoplado a un `PrintDocumentAdapter` personalizado que escribe directamente al archivo destino.
 */
class WebViewPdfGenerator(private val context: Context) {

    init {
        try {
            WebView.enableSlowWholeDocumentDraw()
        } catch (_: Throwable) {
        }
    }

    /**
     * Adaptador `PrintDocumentAdapter` personalizado que envuelve el adaptador nativo del
     * `WebView` offscreen y gestiona el ciclo de vida de impresión hacia archivo local.
     */
    private class OffscreenWebViewPrintAdapter(
        private val delegate: PrintDocumentAdapter,
        private val onFinishedCallback: () -> Unit
    ) : PrintDocumentAdapter() {

        override fun onStart() {
            delegate.onStart()
        }

        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback,
            extras: android.os.Bundle?
        ) {
            delegate.onLayout(oldAttributes, newAttributes, cancellationSignal, callback, extras)
        }

        override fun onWrite(
            pages: Array<out PageRange>,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback
        ) {
            delegate.onWrite(pages, destination, cancellationSignal, callback)
        }

        override fun onFinish() {
            try {
                delegate.onFinish()
            } finally {
                onFinishedCallback()
            }
        }
    }

    /**
     * Renderiza una cadena HTML completa a un archivo PDF en `outputFile`.
     */
    suspend fun renderHtmlToPdfFile(
        htmlContent: String,
        documentTitle: String,
        outputFile: File
    ): File = withTimeout(90_000L) {
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val completed = AtomicBoolean(false)
                val webView = WebView(context.applicationContext)

                fun cleanupWebView() {
                    try {
                        webView.stopLoading()
                        webView.loadUrl("about:blank")
                        webView.clearHistory()
                        webView.removeAllViews()
                        webView.destroy()
                    } catch (_: Throwable) {
                    }
                }

                continuation.invokeOnCancellation {
                    cleanupWebView()
                }

                // Configuración estrictamente offline del WebView
                webView.settings.apply {
                    javaScriptEnabled = false
                    allowFileAccess = false
                    allowContentAccess = false
                    blockNetworkLoads = true
                    blockNetworkImage = true
                    loadsImagesAutomatically = true // Permite imágenes inline data:image/... de DOCX
                    cacheMode = WebSettings.LOAD_NO_CACHE
                    defaultTextEncodingName = "UTF-8"
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        safeBrowsingEnabled = false
                    }
                }

                // Dimensionar el WebView offscreen en proporción A4 (595 x 842 pt a 2x = 1190 x 1684 px)
                val pageWidthPx = 1190
                val pageHeightPx = 1684
                webView.measure(
                    View.MeasureSpec.makeMeasureSpec(pageWidthPx, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(pageHeightPx, View.MeasureSpec.EXACTLY)
                )
                webView.layout(0, 0, pageWidthPx, pageHeightPx)

                webView.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val scheme = request?.url?.scheme?.lowercase().orEmpty()
                        // Bloquear cualquier intento de carga externa http/https en un HTML de usuario
                        if (scheme == "http" || scheme == "https") {
                            return WebResourceResponse(
                                "text/plain",
                                "UTF-8",
                                ByteArrayInputStream(ByteArray(0))
                            )
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        if (completed.get()) return

                        // Dar un ciclo de mensaje en el hilo principal para asegurar que el DOM y layout estén listos
                        view.postDelayed({
                            if (completed.get() || !continuation.isActive) return@postDelayed

                            val attributes = PrintAttributes.Builder()
                                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                                .setResolution(PrintAttributes.Resolution("pdf", "pdf", 300, 300))
                                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                                .build()

                            val baseAdapter = view.createPrintDocumentAdapter(
                                documentTitle.ifBlank { "AnyWall_Document" }
                            )
                            val customAdapter = OffscreenWebViewPrintAdapter(baseAdapter) {
                                // Limpieza diferida al terminar la impresión
                            }

                            PdfPrintCallbackBridge.printToFile(
                                adapter = customAdapter,
                                attributes = attributes,
                                outputFile = outputFile
                            ) { result ->
                                if (result.isSuccess && outputFile.exists() && outputFile.length() > 0L) {
                                    if (completed.compareAndSet(false, true)) {
                                        cleanupWebView()
                                        continuation.resume(outputFile)
                                    }
                                } else {
                                    // Fallback seguro: dibujar el WebView offscreen paginado directamente en PdfDocument
                                    try {
                                        renderWebViewCanvasFallback(view, outputFile)
                                        if (completed.compareAndSet(false, true)) {
                                            cleanupWebView()
                                            continuation.resume(outputFile)
                                        }
                                    } catch (fallbackErr: Throwable) {
                                        if (completed.compareAndSet(false, true)) {
                                            cleanupWebView()
                                            continuation.resumeWithException(
                                                result.exceptionOrNull() ?: fallbackErr
                                            )
                                        }
                                    }
                                }
                            }
                        }, 120L)
                    }
                }

                val styledHtml = wrapWithCleanPrintStyle(htmlContent, documentTitle)
                webView.loadDataWithBaseURL(
                    "about:blank",
                    styledHtml,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        }
    }

    /**
     * Fallback 100% local que pagina el contenido del `WebView` offscreen sobre un `PdfDocument`
     * nativo en caso de que el subsistema `PrintDocumentAdapter` de una ROM específica falle.
     */
    private fun renderWebViewCanvasFallback(webView: WebView, outputFile: File) {
        val pageWidth = 595
        val pageHeight = 842
        webView.measure(
            View.MeasureSpec.makeMeasureSpec(pageWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val totalContentHeight = max(webView.measuredHeight, pageHeight)
        webView.layout(0, 0, pageWidth, totalContentHeight)

        val totalPages = ceil(totalContentHeight.toDouble() / pageHeight.toDouble()).toInt().coerceIn(1, 500)
        val pdfDocument = PdfDocument()
        try {
            for (pageIndex in 0 until totalPages) {
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas: Canvas = page.canvas
                canvas.drawColor(Color.WHITE)
                canvas.save()
                canvas.translate(0f, -(pageIndex * pageHeight).toFloat())
                webView.draw(canvas)
                canvas.restore()
                pdfDocument.finishPage(page)
            }
            FileOutputStream(outputFile).use { fos ->
                BufferedOutputStream(fos, 64 * 1024).use { bos ->
                    pdfDocument.writeTo(bos)
                    bos.flush()
                }
            }
        } finally {
            pdfDocument.close()
        }
    }

    companion object {
        fun wrapWithCleanPrintStyle(rawHtml: String, title: String): String {
            val trimmed = rawHtml.trim()
            val hasHtmlTag = trimmed.contains("<html", ignoreCase = true)
            if (hasHtmlTag) return trimmed

            return """
                <!DOCTYPE html>
                <html lang="es">
                <head>
                    <meta charset="UTF-8" />
                    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                    <title>${escapeHtml(title)}</title>
                    <style>
                        @page {
                            margin: 18mm 16mm;
                        }
                        body {
                            font-family: -apple-system, Roboto, "Segoe UI", Helvetica, Arial, sans-serif;
                            font-size: 11.5pt;
                            line-height: 1.55;
                            color: #111827;
                            background-color: #FFFFFF;
                            margin: 0;
                            padding: 24px 28px;
                            word-wrap: break-word;
                            overflow-wrap: break-word;
                        }
                        h1, h2, h3, h4, h5, h6 {
                            color: #0F172A;
                            margin-top: 1.1em;
                            margin-bottom: 0.45em;
                            line-height: 1.25;
                        }
                        h1 { font-size: 20pt; border-bottom: 1px solid #E2E8F0; padding-bottom: 6px; }
                        h2 { font-size: 16pt; }
                        h3 { font-size: 13.5pt; }
                        p { margin: 0 0 0.75em 0; }
                        pre, code {
                            font-family: "JetBrains Mono", "Roboto Mono", monospace;
                            background-color: #F1F5F9;
                            border-radius: 4px;
                        }
                        code {
                            padding: 2px 5px;
                            font-size: 10pt;
                        }
                        pre {
                            padding: 12px;
                            white-space: pre-wrap;
                            word-break: break-word;
                            border: 1px solid #E2E8F0;
                            font-size: 9.5pt;
                        }
                        blockquote {
                            margin: 0.8em 0;
                            padding: 6px 14px;
                            border-left: 4px solid #38BDF8;
                            background-color: #F8FAFC;
                            color: #334155;
                        }
                        table {
                            width: 100%;
                            border-collapse: collapse;
                            margin: 1em 0;
                        }
                        th, td {
                            border: 1px solid #CBD5E1;
                            padding: 6px 10px;
                            text-align: left;
                            vertical-align: top;
                        }
                        th {
                            background-color: #F1F5F9;
                            font-weight: 600;
                        }
                        img {
                            max-width: 100%;
                            height: auto;
                        }
                        hr {
                            border: none;
                            border-top: 1px solid #CBD5E1;
                            margin: 1.2em 0;
                        }
                    </style>
                </head>
                <body>
                $trimmed
                </body>
                </html>
            """.trimIndent()
        }

        fun escapeHtml(text: String): String {
            val sb = StringBuilder(text.length + 32)
            for (ch in text) {
                when (ch) {
                    '&' -> sb.append("&amp;")
                    '<' -> sb.append("&lt;")
                    '>' -> sb.append("&gt;")
                    '"' -> sb.append("&quot;")
                    '\'' -> sb.append("&#39;")
                    else -> sb.append(ch)
                }
            }
            return sb.toString()
        }
    }
}
