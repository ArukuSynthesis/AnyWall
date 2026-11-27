package com.anywall.converter.core

import android.content.Context
import com.anywall.converter.data.engines.ArchiveEngineImpl
import com.anywall.converter.data.engines.DataAndSecurityEngineImpl
import com.anywall.converter.data.engines.DocumentEngineImpl
import com.anywall.converter.data.engines.ForensicMetadataHelper
import com.anywall.converter.data.engines.ImageEngineImpl
import com.anywall.converter.data.engines.MediaEngineImpl
import com.anywall.converter.data.engines.WebViewPdfGenerator
import com.anywall.converter.data.local.AnyWallDatabase
import com.anywall.converter.data.local.ConversionHistoryRepositoryImpl
import com.anywall.converter.domain.engine.ConverterEngineRegistry
import com.anywall.converter.domain.engine.FileConverterEngine
import com.anywall.converter.domain.repository.ConversionHistoryRepository
import com.anywall.converter.workers.ConversionWorkScheduler

/**
 * Contenedor de Inyección de Dependencias manual limpia para AnyWall: Universal Converter (v0.3).
 * Inicializado en `AnyWallApplication` sin necesidad de Hilt/Dagger.
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    val appPreferences: AppPreferences by lazy {
        AppPreferences(appContext)
    }

    val uriFileResolver: UriFileResolver by lazy {
        UriFileResolver(appContext)
    }

    val thumbnailLoader: LocalThumbnailLoader by lazy {
        LocalThumbnailLoader(appContext, uriFileResolver)
    }

    val scopedStorageManager: ScopedStorageManager by lazy {
        ScopedStorageManager(appContext, appPreferences)
    }

    val notificationHelper: NotificationHelper by lazy {
        NotificationHelper(appContext)
    }

    val database: AnyWallDatabase by lazy {
        AnyWallDatabase.getInstance(appContext)
    }

    val historyRepository: ConversionHistoryRepository by lazy {
        ConversionHistoryRepositoryImpl(
            dao = database.conversionHistoryDao(),
            storageManager = scopedStorageManager
        )
    }

    val forensicMetadataHelper: ForensicMetadataHelper by lazy {
        ForensicMetadataHelper(
            context = appContext,
            uriResolver = uriFileResolver
        )
    }

    val webViewPdfGenerator: WebViewPdfGenerator by lazy {
        WebViewPdfGenerator(appContext)
    }

    val imageEngine: FileConverterEngine by lazy {
        ImageEngineImpl(
            context = appContext,
            uriResolver = uriFileResolver,
            storageManager = scopedStorageManager
        )
    }

    val documentEngine: FileConverterEngine by lazy {
        DocumentEngineImpl(
            context = appContext,
            uriResolver = uriFileResolver,
            storageManager = scopedStorageManager,
            webViewPdfGenerator = webViewPdfGenerator
        )
    }

    val mediaEngine: FileConverterEngine by lazy {
        MediaEngineImpl(
            context = appContext,
            uriResolver = uriFileResolver,
            storageManager = scopedStorageManager
        )
    }

    val archiveEngineImpl: ArchiveEngineImpl by lazy {
        ArchiveEngineImpl(
            uriResolver = uriFileResolver,
            storageManager = scopedStorageManager
        )
    }

    val archiveEngine: FileConverterEngine
        get() = archiveEngineImpl

    val dataAndSecurityEngine: FileConverterEngine by lazy {
        DataAndSecurityEngineImpl(
            uriResolver = uriFileResolver,
            storageManager = scopedStorageManager,
            webViewPdfGenerator = webViewPdfGenerator
        )
    }

    val converterEngineRegistry: ConverterEngineRegistry by lazy {
        ConverterEngineRegistry(
            imageEngine = imageEngine,
            documentEngine = documentEngine,
            mediaEngine = mediaEngine,
            archiveEngine = archiveEngine,
            dataAndSecurityEngine = dataAndSecurityEngine
        )
    }

    val workScheduler: ConversionWorkScheduler by lazy {
        ConversionWorkScheduler(appContext)
    }
}
