package com.ubermax.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.ubermax.app.data.repository.ConfigRepository
import com.ubermax.app.util.Logs
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Arranca el monitoreo tras reiniciar el dispositivo si el usuario lo activó.
 *
 * En Android 12+ el arranque desde `BOOT_COMPLETED` es una excepción permitida
 * para foreground services; aun así se envuelve en try/catch porque algunas ROMs
 * y versiones futuras restringen tipos `specialUse`.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var configRepository: ConfigRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = configRepository.getAppSettings()
                if (!settings.autoStartOnBoot) return@launch

                val serviceIntent = Intent(context, MonitorForegroundService::class.java).apply {
                    action = MonitorForegroundService.ACTION_START
                }
                try {
                    ContextCompat.startForegroundService(context, serviceIntent)
                } catch (t: Throwable) {
                    Logs.w("BootReceiver", "No se pudo auto-arrancar el monitor: ${t.message}")
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
