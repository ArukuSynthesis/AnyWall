package com.anywall.converter.core

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ForegroundInfo
import com.anywall.converter.MainActivity
import com.anywall.converter.R
import kotlin.math.roundToInt

/**
 * Gestor de notificaciones locales para el procesamiento en segundo plano de `BatchConversionWorker`.
 */
class NotificationHelper(private val context: Context) {

    init {
        ensureChannelCreated()
    }

    fun ensureChannelCreated() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = context.getString(R.string.notification_channel_desc)
                    setShowBadge(false)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun createForegroundInfo(progress: Float, statusText: String): ForegroundInfo {
        ensureChannelCreated()
        val notification = buildProgressNotification(progress, statusText)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    fun buildProgressNotification(progress: Float, statusText: String): Notification {
        val percent = (progress.coerceIn(0f, 1f) * 100f).roundToInt()
        val pendingIntent = createLaunchPendingIntent()

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("AnyWall • Convirtiendo ($percent%)")
            .setContentText(statusText.ifBlank { "Procesando archivos localmente…" })
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent, percent <= 0)
            .setContentIntent(pendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun notifyBatchFinished(successCount: Int, errorMessage: String? = null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        ensureChannelCreated()
        val pendingIntent = createLaunchPendingIntent()

        val title: String
        val body: String
        val icon: Int
        if (errorMessage == null) {
            title = "Conversión completada ($successCount)"
            body = "Guardado en Downloads/AnyWall/"
            icon = android.R.drawable.stat_sys_download_done
        } else {
            title = "Error en la conversión"
            body = errorMessage
            icon = android.R.drawable.stat_notify_error
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(COMPLETION_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
        }
    }

    private fun createLaunchPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val CHANNEL_ID = "anywall_conversion_channel"
        const val FOREGROUND_NOTIFICATION_ID = 4101
        const val COMPLETION_NOTIFICATION_ID = 4102
    }
}
