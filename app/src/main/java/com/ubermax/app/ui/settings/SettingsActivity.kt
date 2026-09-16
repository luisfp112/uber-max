package com.ubermax.app.ui.settings

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.ubermax.app.R
import com.ubermax.app.databinding.ActivitySettingsBinding
import com.ubermax.app.ui.blacklist.BlacklistMapActivity
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Cuestionario estilo Maxymo + Gestión de lista negra.
 *
 * Cada filtro relevante incluye un ícono de ayuda (ℹ) que explica qué es,
 * en qué unidad debe configurarse y un ejemplo práctico.
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

        setupHelpIcons()

        lifecycleScope.launch {
            vm.settingsState.collectLatest { s ->
                // Vehicle
                b.etVehicleName.setText(s.vehicleName)
                b.etConsumption.setText(s.consumptionKmPerUnit.toString())
                when (s.fuelUnitId) {
                    1 -> b.rbGallon.isChecked = true
                    else -> b.rbLiter.isChecked = true
                }
                b.etFuelPrice.setText(s.fuelPricePerUnit.toString())
                b.etMaintenancePerKm.setText(s.maintenancePerKm.toString())
                b.etManualCostPerKm.setText(s.manualCostPerKm.toString())
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
                // Deadhead
                b.etDeadheadThresholdKm.setText(s.deadheadThresholdKm.toString())

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

    private fun setupHelpIcons() {
                val helpData = listOf(
            Triple(b.tilMaxPickupKm, R.string.help_max_pickup_title, R.string.help_max_pickup_body),
            Triple(b.tilMaxTripKm, R.string.help_max_trip_title, R.string.help_max_trip_body),
            Triple(b.tilMinPassengerRating, R.string.help_rating_title, R.string.help_rating_body),
            Triple(b.tilMinNetProfit, R.string.help_net_profit_title, R.string.help_net_profit_body),
            Triple(b.tilMinProfitPerKm, R.string.help_profit_km_title, R.string.help_profit_km_body)
        )
        for ((til, titleResId, bodyResId) in helpData) {
            til.setEndIconOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle(getString(titleResId))
                    .setMessage(getString(bodyResId))
                    .setPositiveButton("Entendido", null)
                    .show()
            }
        }
    }

    private fun save() {
        // Validar TODOS los campos numéricos antes de guardar.
        // Muestra un error específico por campo en lugar de un try/catch genérico.
        val numericFields = listOf(
            "Consumo" to b.etConsumption,
            "Precio combustible" to b.etFuelPrice,
            "Mantenimiento/km" to b.etMaintenancePerKm,
            "Costo manual/km" to b.etManualCostPerKm,
            "Velocidad promedio" to b.etAvgSpeed,
            "Tarifa mínima" to b.etMinFare,
            "Ganancia neta mínima" to b.etMinNetProfit,
            "Ganancia/km mínima" to b.etMinProfitPerKm,
            "Ganancia/hora mínima" to b.etMinProfitPerHour,
            "Pickup máx" to b.etMaxPickupKm,
            "Viaje máx" to b.etMaxTripKm,
            "Tiempo máx (min)" to b.etMaxTotalMinutes,
            "Umbral vuelta vacía (km)" to b.etDeadheadThresholdKm,
            "Rating mín" to b.etMinPassengerRating
        )

        // Recolectar errores de validación
        val errors = mutableListOf<String>()
        for ((label, editText) in numericFields) {
            val text = editText.text.toString().trim()
            if (text.isEmpty()) {
                errors += "$label: vacío"
            } else {
                try {
                    editText.parseNumeric()
                } catch (e: NumberFormatException) {
                    errors += "$label: '${text}' no es numérico"
                }
            }
        }

        if (errors.isNotEmpty()) {
            val msg = errors.take(4).joinToString("\n") + if (errors.size > 4) "\n... y ${errors.size - 4} más" else ""
            Toast.makeText(this, "Error en:\n$msg", Toast.LENGTH_LONG).show()
            return
        }

        try {
vm.saveAll(
                vehicleName = b.etVehicleName.text.toString(),
                consumptionKmPerUnit = b.etConsumption.parseNumeric(),
                fuelUnitId = if (b.rbGallon.isChecked) 1 else 0,
                fuelPricePerUnit = b.etFuelPrice.parseNumeric(),
                maintenancePerKm = b.etMaintenancePerKm.parseNumeric(),
                manualCostPerKm = b.etManualCostPerKm.parseNumeric(),
                avgSpeed = b.etAvgSpeed.parseNumeric(),
                minFare = b.etMinFare.parseNumeric(),
                minNetProfit = b.etMinNetProfit.parseNumeric(),
                minProfitPerKm = b.etMinProfitPerKm.parseNumeric(),
                minProfitPerHour = b.etMinProfitPerHour.parseNumeric(),
                maxPickupKm = b.etMaxPickupKm.parseNumeric(),
                maxTripKm = b.etMaxTripKm.parseNumeric(),
                maxTotalMinutes = b.etMaxTotalMinutes.parseNumeric().toInt(),
                minPassengerRating = b.etMinPassengerRating.parseNumeric(),
                maxPickupTripRatio = 1.0,
                blacklistEnabled = b.switchBlacklist.isChecked,
                autoAcceptEnabled = b.switchAutoAccept.isChecked,
                aiEnabled = b.switchAi.isChecked,
                deadheadThresholdKm = b.etDeadheadThresholdKm.parseNumeric()
            )
            Toast.makeText(this, R.string.saved_successfully, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error al guardar: ${e.message}", Toast.LENGTH_SHORT).show()
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

    /** Extrae número desde EditText, tanto con coma como con punto (locales ES). */
    private fun EditText.parseNumeric(): Double =
        text.toString().trim().replace(',', '.').toDouble()
}
