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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anywall.converter.core.AnyWallQuickTileService
import com.anywall.converter.core.AppThemeMode
import com.anywall.converter.ui.navigation.AnyWallNavHost
import com.anywall.converter.ui.screens.MainViewModel
import com.anywall.converter.ui.theme.AnyWallTheme

/**
 * Actividad principal de AnyWall: Universal Converter (v0.3).
 * Gestiona la recepción de archivos compartidos desde Android Share Sheet (`ACTION_SEND`
 * y `ACTION_SEND_MULTIPLE`), accesos rápidos desde App Shortcuts / Quick Settings Tile,
 * y aplica en vivo la preferencia de tema claro/oscuro.
 */
class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels {
        val app = application as AnyWallApplication
        MainViewModel.provideFactory(app.container)
    }

    private var activeQuickAction by mutableStateOf<String?>(null)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* No bloqueante: WorkManager opera con o sin permiso concedido */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()

        if (savedInstanceState == null) {
            handleIncomingShareIntent(intent)
            activeQuickAction = intent?.getStringExtra(AnyWallQuickTileService.EXTRA_QUICK_ACTION)
        }

        setContent {
            val themeMode by mainViewModel.themeMode.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }

            AnyWallTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AnyWallNavHost(
                        viewModel = mainViewModel,
                        initialQuickAction = activeQuickAction
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingShareIntent(intent)
        activeQuickAction = intent.getStringExtra(AnyWallQuickTileService.EXTRA_QUICK_ACTION)
    }

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
