package com.ubermax.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.ubermax.app.R
import com.ubermax.app.databinding.ActivitySettingsBinding
import com.ubermax.app.ui.onboarding.OnboardingActivity
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Cuestión de filtros (estilo Maxymo), vehículo, voz y perímetro urbano.
 * La decisión la toma solo la ganancia neta por km; el resto son guardas
 * opcionales (recogida, viaje, rating, tarifa mínima).
 */
@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {

    private lateinit var b: ActivitySettingsBinding
    private val vm: SettingsViewModel by viewModels()

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val json = withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
            }
            if (json.isNullOrBlank()) {
                Toast.makeText(this@SettingsActivity, R.string.config_import_error, Toast.LENGTH_LONG).show()
                return@launch
            }
            try {
                vm.restoreBackup(json)
                Toast.makeText(this@SettingsActivity, R.string.config_imported, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@SettingsActivity,
                    "${getString(R.string.config_import_error)}: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnBack.setOnClickListener { finish() }
        b.btnSave.setOnClickListener { save() }
        b.btnOpenOnboarding.setOnClickListener {
            startActivity(Intent(this, OnboardingActivity::class.java))
        }
        b.btnExportConfig.setOnClickListener { exportConfig() }
        b.btnImportConfig.setOnClickListener {
            importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.settingsState.collectLatest { s ->
                    // Vehicle
                    b.etVehicleName.setText(s.vehicleName)
                    b.etManualCostPerKm.setText(s.manualCostPerKm.toString())
                    b.etAvgSpeed.setText(s.avgSpeed.toString())
                    // Pickup
                    b.etPickupMax.setText(s.pickupMax.toString())
                    b.rbPickupMin.isChecked = s.pickupMeasure != "KM"
                    b.rbPickupKm.isChecked = s.pickupMeasure == "KM"
                    // Trip
                    b.etTripMax.setText(s.tripMax.toString())
                    b.rbTripMin.isChecked = s.tripMeasure != "KM"
                    b.rbTripKm.isChecked = s.tripMeasure == "KM"
                    // Rating
                    b.etMinPassengerRating.setText(s.minPassengerRating.toString())
                    // Profit
                    b.etMinProfitPerKm.setText(s.minProfitPerKm.toString())
                    // Toggles
                    b.switchAutoAccept.isChecked = s.autoAcceptEnabled
                    b.switchHumanTaps.isChecked = s.humanTapsEnabled
                    b.switchVoiceAnnounce.isChecked = s.voiceAnnounceEnabled
                    b.switchGeoCheck.isChecked = s.geoCheckEnabled
                    b.etGeoApiKey.setText(s.geoApiKey)
                }
            }
        }
    }

    private fun save() {
        val numericFields = listOf(
            "Costo manual/km" to b.etManualCostPerKm,
            "Velocidad promedio" to b.etAvgSpeed,
            "Pickup máx" to b.etPickupMax,
            "Viaje máx" to b.etTripMax,
            "Ganancia/km mínima" to b.etMinProfitPerKm,
            "Rating mín" to b.etMinPassengerRating
        )

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
                vehicleName = b.etVehicleName.text.toString().trim(),
                manualCostPerKm = b.etManualCostPerKm.parseNumeric(),
                avgSpeed = b.etAvgSpeed.parseNumeric(),
                pickupMeasure = if (b.rbPickupKm.isChecked) "KM" else "MIN",
                pickupMax = b.etPickupMax.parseNumeric(),
                tripMeasure = if (b.rbTripKm.isChecked) "KM" else "MIN",
                tripMax = b.etTripMax.parseNumeric(),
                minProfitPerKm = b.etMinProfitPerKm.parseNumeric(),
                minPassengerRating = b.etMinPassengerRating.parseNumeric(),
                autoAcceptEnabled = b.switchAutoAccept.isChecked
            )
            vm.updateBehavior(
                voiceAnnounceEnabled = b.switchVoiceAnnounce.isChecked,
                humanTapsEnabled = b.switchHumanTaps.isChecked,
                geoCheckEnabled = b.switchGeoCheck.isChecked,
                geoApiKey = b.etGeoApiKey.text.toString().trim()
            )
            Toast.makeText(this, R.string.saved_successfully, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error al guardar: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportConfig() {
        lifecycleScope.launch {
            val json = runCatching { vm.buildBackupJson() }.getOrNull()
            if (json.isNullOrBlank()) {
                Toast.makeText(this@SettingsActivity, R.string.config_import_error, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val file = File(cacheDir, "ubermax_config.json")
            withContext(Dispatchers.IO) { file.writeText(json) }
            val uri: Uri = FileProvider.getUriForFile(this@SettingsActivity, "$packageName.fileprovider", file)
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(share, getString(R.string.export_config)))
            Toast.makeText(this@SettingsActivity, R.string.config_exported, Toast.LENGTH_SHORT).show()
        }
    }

    /** Extrae número desde EditText, tanto con coma como con punto (locales ES). */
    private fun EditText.parseNumeric(): Double =
        text.toString().trim().replace(',', '.').toDouble()
}