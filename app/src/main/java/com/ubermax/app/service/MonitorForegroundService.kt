package com.ubermax.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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

        // Timeout máximo: 4 horas. Se re-adquiere con cada evento procesado
        // por UberAccessibilityService.processEvent() para evitar que un fallo
        // inesperado deje el lock activo consumiendo batería para siempre.
        private const val WAKE_LOCK_TIMEOUT_MS = 4 * 60 * 60 * 1000L

        // Cada 55 minutos se re-adquiere el lock para que nunca expire mientras
        // el servicio siga activo (el timeout es la red de seguridad, no el plan).
        private const val WAKE_LOCK_RENEW_MS = 55 * 60 * 1000L

        var isRunning = false
            private set
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private val wakeLockHandler = Handler(Looper.getMainLooper())
    private val renewWakeLock: Runnable = Runnable { acquireWakeLock() }

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
        wakeLockHandler.postDelayed(renewWakeLock, WAKE_LOCK_RENEW_MS)

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

        // Niveles no soportados (p. ej. dispositivos low-power) → operar sin lock
        if (!powerManager.isWakeLockLevelSupported(PowerManager.PARTIAL_WAKE_LOCK)) return

        // Liberar el anterior si existe (para no fugar locks)
        wakeLock?.let {
            if (it.isHeld) it.release()
        }

        try {
            val lock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "UberMax::MonitorWakeLock"
            )
            if (!lock.isHeld) {
                // Tiempo máximo para garantizar que un fallo inesperado nunca
                // deje el dispositivo con batería drenándose de forma indefinida.
                lock.acquire(WAKE_LOCK_TIMEOUT_MS)
            }
            wakeLock = lock
        } catch (e: SecurityException) {
            // Sin permiso WAKE_LOCK (p. ej. revoked) → el servicio sigue activo
            // sin wake lock; la operación no debe crashear por esto.
            wakeLock = null
        } catch (e: RuntimeException) {
            // newWakeLock/acquire pueden fallar en ROMs agresivas → degradar
            // con elegancia en lugar de tumbar el foreground service.
            wakeLock = null
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        wakeLockHandler.removeCallbacks(renewWakeLock)
        releaseWakeLock()
        super.onDestroy()
    }
}
