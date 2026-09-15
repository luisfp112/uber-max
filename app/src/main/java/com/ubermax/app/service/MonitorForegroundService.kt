package com.ubermax.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.ubermax.app.R
import com.ubermax.app.UberMaxApplication
import com.ubermax.app.ui.dashboard.DashboardActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * ForegroundService que mantiene vivo el proceso de UberMax.
 * Muestra una notificación persistente de baja prioridad y
 * gestiona un WakeLock parcial para asegurar la operación continua.
 */
@AndroidEntryPoint
class MonitorForegroundService : Service() {

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.ubermax.app.START_MONITOR"
        const val ACTION_STOP = "com.ubermax.app.STOP_MONITOR"

        var isRunning = false
            private set
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        startForeground(NOTIFICATION_ID, createNotification())
        acquireWakeLock()

        return START_STICKY
    }

    private fun createNotification(): Notification {
        val openIntent = Intent(this, DashboardActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, MonitorForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, UberMaxApplication.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .addAction(0, getString(R.string.stop_monitoring), stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "UberMax::MonitorWakeLock"
        ).apply {
            acquire(10 * 60 * 60 * 1000L) // 10 horas máximo
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        super.onDestroy()
    }
}
