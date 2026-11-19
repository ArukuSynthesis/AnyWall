package android.print

import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * Puente en el paquete `android.print` que acopla un `PrintDocumentAdapter` (como el generado
 * por un `WebView` offscreen) para escribir directamente al `ParcelFileDescriptor` del archivo
 * PDF destino de forma 100% local y sin intervención de diálogos del sistema.
 */
object PdfPrintCallbackBridge {

    fun printToFile(
        adapter: PrintDocumentAdapter,
        attributes: PrintAttributes,
        outputFile: File,
        onComplete: (Result<File>) -> Unit
    ) {
        val cancellationSignal = CancellationSignal()
        try {
            adapter.onStart()
            val layoutCallback = object : PrintDocumentAdapter.LayoutResultCallback() {
                override fun onLayoutFinished(info: PrintDocumentInfo?, changed: Boolean) {
                    var pfd: ParcelFileDescriptor? = null
                    try {
                        if (outputFile.exists()) {
                            outputFile.delete()
                        }
                        outputFile.parentFile?.mkdirs()
                        outputFile.createNewFile()
                        pfd = ParcelFileDescriptor.open(
                            outputFile,
                            ParcelFileDescriptor.MODE_CREATE or
                                ParcelFileDescriptor.MODE_TRUNCATE or
                                ParcelFileDescriptor.MODE_READ_WRITE
                        )

                        val writeCallback = object : PrintDocumentAdapter.WriteResultCallback() {
                            override fun onWriteFinished(pages: Array<out PageRange>?) {
                                try {
                                    pfd.close()
                                } catch (_: Throwable) {
                                }
                                try {
                                    adapter.onFinish()
                                } catch (_: Throwable) {
                                }
                                onComplete(Result.success(outputFile))
                            }

                            override fun onWriteFailed(error: CharSequence?) {
                                try {
                                    pfd.close()
                                } catch (_: Throwable) {
                                }
                                try {
                                    adapter.onFinish()
                                } catch (_: Throwable) {
                                }
                                onComplete(
                                    Result.failure(
                                        IllegalStateException(
                                            error?.toString()
                                                ?: "Error al escribir el PDF mediante PrintDocumentAdapter"
                                        )
                                    )
                                )
                            }

                            override fun onWriteCancelled() {
                                try {
                                    pfd.close()
                                } catch (_: Throwable) {
                                }
                                try {
                                    adapter.onFinish()
                                } catch (_: Throwable) {
                                }
                                onComplete(
                                    Result.failure(
                                        IllegalStateException("La escritura del PDF fue cancelada.")
                                    )
                                )
                            }
                        }

                        adapter.onWrite(
                            arrayOf(PageRange.ALL_PAGES),
                            pfd,
                            cancellationSignal,
                            writeCallback
                        )
                    } catch (t: Throwable) {
                        try {
                            pfd?.close()
                        } catch (_: Throwable) {
                        }
                        try {
                            adapter.onFinish()
                        } catch (_: Throwable) {
                        }
                        onComplete(Result.failure(t))
                    }
                }

                override fun onLayoutFailed(error: CharSequence?) {
                    try {
                        adapter.onFinish()
                    } catch (_: Throwable) {
                    }
                    onComplete(
                        Result.failure(
                            IllegalStateException(
                                error?.toString()
                                    ?: "Error de maquetación (onLayoutFailed) en PrintDocumentAdapter"
                            )
                        )
                    )
                }

                override fun onLayoutCancelled() {
                    try {
                        adapter.onFinish()
                    } catch (_: Throwable) {
                    }
                    onComplete(
                        Result.failure(
                            IllegalStateException("La maquetación del documento fue cancelada.")
                        )
                    )
                }
            }

            val extras = Bundle().apply {
                putBoolean(PrintDocumentAdapter.EXTRA_PRINT_PREVIEW, false)
            }
            adapter.onLayout(
                attributes,
                attributes,
                cancellationSignal,
                layoutCallback,
                extras
            )
        } catch (t: Throwable) {
            onComplete(Result.failure(t))
        }
    }
}
