package com.ubermax.app.ui.dashboard

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.ubermax.app.R
import com.ubermax.app.databinding.ActivityDashboardBinding
import com.ubermax.app.service.FloatingWindowService
import com.ubermax.app.service.MonitorForegroundService
import com.ubermax.app.service.UberAccessibilityService
import com.ubermax.app.ui.settings.SettingsActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * Panel de control simplificado.
 * Solo muestra estado del sistema y controles de inicio/parada.
 */
@AndroidEntryPoint
class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.btnToggleMonitor.setOnClickListener {
            if (MonitorForegroundService.isRunning) stopMonitoring() else startMonitoring()
        }

        binding.btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun startMonitoring() {
        if (!UberAccessibilityService.isRunning) {
            Toast.makeText(this, R.string.enable_accessibility, Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }

        ContextCompat.startForegroundService(this,
            Intent(this, MonitorForegroundService::class.java).apply {
                action = MonitorForegroundService.ACTION_START
            })

        if (checkOverlayPermission()) {
            startService(Intent(this, FloatingWindowService::class.java).apply {
                action = FloatingWindowService.ACTION_SHOW
            })
        }

        updateStatus()
    }

    private fun stopMonitoring() {
        startService(Intent(this, MonitorForegroundService::class.java).apply {
            action = MonitorForegroundService.ACTION_STOP
        })
        startService(Intent(this, FloatingWindowService::class.java).apply {
            action = FloatingWindowService.ACTION_HIDE
        })
        updateStatus()
    }

    private fun updateStatus() {
        val a11y = UberAccessibilityService.isRunning
        val monitor = MonitorForegroundService.isRunning

        binding.tvA11yStatus.text = if (a11y) "✅ Activo" else "❌ Inactivo"
        binding.tvMonitorStatus.text = if (monitor) "✅ Monitoreando" else "⏸️ Detenido"

        binding.btnToggleMonitor.text = if (monitor)
            getString(R.string.stop_monitoring) else getString(R.string.start_monitoring)
    }

    private fun checkOverlayPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")))
            return false
        }
        return true
    }
}
