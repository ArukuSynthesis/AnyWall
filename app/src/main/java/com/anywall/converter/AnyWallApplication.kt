package com.anywall.converter

import android.app.Application
import com.anywall.converter.core.AppContainer
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

/**
 * Clase `Application` principal de AnyWall: Universal Converter.
 * Inicializa el contenedor manual de dependencias `AppContainer` y los recursos locales de PDFBox.
 */
class AnyWallApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        container = AppContainer(applicationContext)
    }
}
