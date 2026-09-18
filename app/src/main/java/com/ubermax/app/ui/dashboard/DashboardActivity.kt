package com.ubermax.app.ui.dashboard

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ubermax.app.R
import com.ubermax.app.databinding.ActivityDashboardBinding
import com.ubermax.app.service.FloatingWindowService
import com.ubermax.app.service.MonitorForegroundService
import com.ubermax.app.service.UberAccessibilityService
import com.ubermax.app.ui.history.HistoryActivity
import com.ubermax.app.ui.settings.SettingsActivity
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Panel de control.
 * Estado del sistema + métricas reales del día + exportación a CSV.
 */
@AndroidEntryPoint
class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding

    private val viewModel: DashboardViewModel by viewModels()

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

        binding.btnExportCsv.setOnClickListener {
            exportCsv()
        }

        binding.btnHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        observeStats()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        viewModel.refresh()
    }

    private fun observeStats() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.stats.collect { stats ->
                    binding.tvTodayNetProfit.text = "\$%.2f".format(stats.todayNetProfit)
                    binding.tvTodayNetProfit.setTextColor(
                        ContextCompat.getColor(
                            this@DashboardActivity,
                            if (stats.todayNetProfit >= 0) R.color.profit_positive else R.color.profit_negative
                        )
                    )
                    binding.tvTodayOffers.text = stats.todayOffers.toString()
                    binding.tvTodayAccepted.text = stats.todayAccepted.toString()

                    binding.tvBestHours.text = if (stats.bestHours.isEmpty()) {
                        getString(R.string.best_hours_empty)
                    } else {
                        stats.bestHours.joinToString("\n") { h ->
                            getString(R.string.hour_stat_line, h.hour_of_day, h.avgProfit, h.count)
                        }
                    }

                    binding.tvBestZones.text = if (stats.bestZones.isEmpty()) {
                        getString(R.string.best_zones_empty)
                    } else {
                        stats.bestZones.joinToString("\n") { z ->
                            getString(R.string.zone_stat_line, z.destination, z.avgProfitPerKm, z.count)
                        }
                    }
                }
            }
        }
    }

    private fun exportCsv() {
        Toast.makeText(this, R.string.exporting_csv, Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val csv = viewModel.buildExportCsv()
            if (csv.isBlank()) {
                Toast.makeText(this@DashboardActivity, R.string.export_empty, Toast.LENGTH_SHORT).show()
                return@launch
            }
            shareCsv(csv)
        }
    }

    private suspend fun shareCsv(csv: String) {
        // Escritura de archivo fuera del hilo principal (no bloquear la UI)
        val uri: Uri = withContext(Dispatchers.IO) {
            val file = File(cacheDir, "ubermax_export.csv")
            file.writeText(csv)
            FileProvider.getUriForFile(this@DashboardActivity, "$packageName.fileprovider", file)
        }

        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(share, getString(R.string.export_csv)))
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
            ContextCompat.startForegroundService(this,
                Intent(this, FloatingWindowService::class.java).apply {
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
