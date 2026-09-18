package com.ubermax.app.service

import android.Manifest
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.ubermax.app.data.repository.ConfigRepository
import com.ubermax.app.util.Logs
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Arranca el monitoreo cuando se conecta un dispositivo Bluetooth de audio
 * (típicamente el estéreo del auto) si el usuario activó "Auto-arranque por Bluetooth".
 *
 * En API 31+ se requiere `BLUETOOTH_CONNECT` para inspeccionar el dispositivo.
 * Sin ese permiso el receiver no intenta adivinar y no arranca nada.
 */
@AndroidEntryPoint
class CarConnectionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var configRepository: ConfigRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED) return
        if (!hasBluetoothConnect(context)) return

        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
        if (device == null || !isAudioDevice(device)) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!configRepository.getAppSettings().autoStartOnCarBt) return@launch
                val serviceIntent = Intent(context, MonitorForegroundService::class.java).apply {
                    action = MonitorForegroundService.ACTION_START
                }
                try {
                    ContextCompat.startForegroundService(context, serviceIntent)
                } catch (t: Throwable) {
                    Logs.w("CarConnectionReceiver", "No se pudo auto-arrancar: ${t.message}")
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun hasBluetoothConnect(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isAudioDevice(device: BluetoothDevice): Boolean {
        return try {
            val major = device.bluetoothClass?.majorDeviceClass
            major == BluetoothClass.Device.Major.AUDIO_VIDEO ||
                major == BluetoothClass.Device.Major.PHONE
        } catch (t: SecurityException) {
            false
        }
    }
}
