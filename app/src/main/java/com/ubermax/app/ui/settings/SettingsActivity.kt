package com.ubermax.app.ui.settings

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ubermax.app.R
import com.ubermax.app.databinding.ActivitySettingsBinding
import com.ubermax.app.ui.blacklist.BlacklistMapActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Cuestionario estilo Maxymo + Gestión de lista negra.
 */
@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {

    private lateinit var b: ActivitySettingsBinding
    private val vm: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnBack.setOnClickListener { finish() }
        b.btnSave.setOnClickListener { save() }
        b.btnAddBlacklist.setOnClickListener { showAddBlacklistDialog() }
        b.btnOpenMap.setOnClickListener {
            startActivity(Intent(this, BlacklistMapActivity::class.java))
        }

        lifecycleScope.launch {
            vm.settingsState.collectLatest { s ->
                // Vehicle
                b.etVehicleName.setText(s.vehicleName)
                b.etCostPerKm.setText(s.costPerKm.toString())
                b.etAvgSpeed.setText(s.avgSpeed.toString())
                // Fare filters
                b.etMinFare.setText(s.minFare.toString())
                // Distance filters
                b.etMaxPickupKm.setText(s.maxPickupKm.toString())
                b.etMaxTripKm.setText(s.maxTripKm.toString())
                // Time
                b.etMaxTotalMinutes.setText(s.maxTotalMinutes.toString())
                // Rating
                b.etMinPassengerRating.setText(s.minPassengerRating.toString())
                // Net profit
                b.etMinNetProfit.setText(s.minNetProfit.toString())
                b.etMinProfitPerKm.setText(s.minProfitPerKm.toString())
                b.etMinProfitPerHour.setText(s.minProfitPerHour.toString())
                // Toggles
                b.switchBlacklist.isChecked = s.blacklistEnabled
                b.switchAutoAccept.isChecked = s.autoAcceptEnabled
                b.switchAi.isChecked = s.aiEnabled

                // Blacklist entries
                b.blacklistContainer.removeAllViews()
                s.blacklistEntries.forEach { entry ->
                    val tv = TextView(this@SettingsActivity).apply {
                        text = "❌ ${entry.keyword}"
                        textSize = 14f
                        setTextColor(getColor(R.color.text_primary))
                        setPadding(0, 8, 0, 8)
                        setOnClickListener {
                            vm.removeBlacklistEntry(entry)
                            Toast.makeText(context, "Eliminado: ${entry.keyword}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    b.blacklistContainer.addView(tv)
                }
            }
        }
    }

    private fun save() {
        try {
            vm.saveAll(
                vehicleName = b.etVehicleName.text.toString(),
                costPerKm = b.etCostPerKm.text.toString().toDouble(),
                avgSpeed = b.etAvgSpeed.text.toString().toDouble(),
                minFare = b.etMinFare.text.toString().toDouble(),
                minNetProfit = b.etMinNetProfit.text.toString().toDouble(),
                minProfitPerKm = b.etMinProfitPerKm.text.toString().toDouble(),
                minProfitPerHour = b.etMinProfitPerHour.text.toString().toDouble(),
                maxPickupKm = b.etMaxPickupKm.text.toString().toDouble(),
                maxTripKm = b.etMaxTripKm.text.toString().toDouble(),
                maxTotalMinutes = b.etMaxTotalMinutes.text.toString().toInt(),
                minPassengerRating = b.etMinPassengerRating.text.toString().toDouble(),
                maxPickupTripRatio = 1.0, // default if not exposed in UI
                blacklistEnabled = b.switchBlacklist.isChecked,
                autoAcceptEnabled = b.switchAutoAccept.isChecked,
                aiEnabled = b.switchAi.isChecked
            )
            Toast.makeText(this, R.string.saved_successfully, Toast.LENGTH_SHORT).show()
        } catch (e: NumberFormatException) {
            Toast.makeText(this, "Error: valores numéricos inválidos", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAddBlacklistDialog() {
        val input = EditText(this).apply {
            hint = "Ej: Ficoa, Huachi, Terminal..."
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this)
            .setTitle("Agregar a Lista Negra")
            .setMessage("Escribe la dirección o zona que deseas evitar:")
            .setView(input)
            .setPositiveButton("Agregar") { _, _ ->
                val keyword = input.text.toString().trim()
                if (keyword.isNotEmpty()) {
                    vm.addBlacklistEntry(keyword)
                    Toast.makeText(this, "Agregado: $keyword", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
