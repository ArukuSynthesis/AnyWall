package com.anywall.converter.core

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.anywall.converter.MainActivity

/**
 * Acceso rápido en el Panel de Ajustes Rápidos de Android (Quick Settings Tile - v0.3).
 * Permite abrir AnyWall: Universal Converter con un solo toque desde la barra superior del sistema.
 */
class AnyWallQuickTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = "AnyWall"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = "100% Offline"
            }
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_QUICK_ACTION, ACTION_OPEN_CONVERTER)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                1001,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(launchIntent)
        }
    }

    companion object {
        const val EXTRA_QUICK_ACTION = "com.anywall.converter.EXTRA_QUICK_ACTION"
        const val ACTION_OPEN_CONVERTER = "open_converter"
        const val ACTION_OPEN_HISTORY = "open_history"
        const val ACTION_OPEN_TOOLS = "open_tools"
    }
}
