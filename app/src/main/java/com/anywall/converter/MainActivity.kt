package com.anywall.converter

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.anywall.converter.ui.navigation.AnyWallNavHost
import com.anywall.converter.ui.screens.MainViewModel
import com.anywall.converter.ui.theme.AnyWallTheme

/**
 * Actividad principal de AnyWall: Universal Converter.
 * Gestiona la recepción de archivos compartidos desde Android Share Sheet (`ACTION_SEND`
 * y `ACTION_SEND_MULTIPLE`) tanto al iniciar la aplicación como en `onNewIntent`.
 */
class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels {
        val app = application as AnyWallApplication
        MainViewModel.provideFactory(app.container)
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* No bloqueante: WorkManager opera con o sin permiso concedido */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()

        if (savedInstanceState == null) {
            handleIncomingShareIntent(intent)
        }

        setContent {
            AnyWallTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AnyWallNavHost(viewModel = mainViewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingShareIntent(intent)
    }

    /**
     * Extrae una o múltiples URIs recibidas desde WhatsApp, Galería o cualquier explorador
     * mediante `Intent.ACTION_SEND` o `Intent.ACTION_SEND_MULTIPLE` y abre directo `ConfigureScreen`.
     */
    private fun handleIncomingShareIntent(incomingIntent: Intent?) {
        if (incomingIntent == null) return
        val action = incomingIntent.action ?: return
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) {
            return
        }

        val sharedUris = linkedSetOf<Uri>()

        when (action) {
            Intent.ACTION_SEND -> {
                val singleUri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    incomingIntent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    incomingIntent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                if (singleUri != null) {
                    sharedUris.add(singleUri)
                }
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                val multipleUris: List<Uri>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    incomingIntent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    incomingIntent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                }
                if (!multipleUris.isNullOrEmpty()) {
                    sharedUris.addAll(multipleUris)
                }
            }
        }

        // También inspeccionar ClipData por si la app emisora adjuntó las URIs ahí
        val clipData = incomingIntent.clipData
        if (clipData != null) {
            for (i in 0 until clipData.itemCount) {
                clipData.getItemAt(i)?.uri?.let(sharedUris::add)
            }
        }

        if (sharedUris.isNotEmpty()) {
            mainViewModel.onFilesReceivedFromShareSheet(sharedUris.toList())
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
