package com.ubermax.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ubermax.app.data.db.entity.FilterRulesEntity
import com.ubermax.app.data.db.entity.VehicleConfigEntity
import com.ubermax.app.data.repository.ConfigRepository
import com.ubermax.app.util.ConfigBackupManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsState(
    // ── Vehículo ──
    val vehicleName: String = "",
    val manualCostPerKm: Double = 0.0,
    val avgSpeed: Double = 22.0,

    // Campos internos (dormantes en la UI, se conservan al guardar)
    val consumptionKmPerUnit: Double = 12.0,
    val fuelUnitId: Int = 0,
    val fuelPricePerUnit: Double = 0.86,
    val maintenancePerKm: Double = 0.03,

    // ── Recogida (valor de la tarjeta, unidad configurable) ──
    val pickupMeasure: String = "MIN",
    val pickupMax: Double = 5.0,

    // ── Viaje (valor de la tarjeta, unidad configurable; 0 = sin tope) ──
    val tripMeasure: String = "MIN",
    val tripMax: Double = 0.0,

    // ── Rentabilidad ($/km decide) ──
    val minProfitPerKm: Double = 0.15,

    // ── Pasajero (0 = desactivado, WARN) ──
    val minPassengerRating: Double = 0.0,

    // ── Toggles ──
    val autoAcceptEnabled: Boolean = true,
    val voiceAnnounceEnabled: Boolean = true,
    val humanTapsEnabled: Boolean = true,

    // ── Geolocalización del perímetro (GeoJSON + LocationIQ) ──
    val geoCheckEnabled: Boolean = false,
    val geoApiKey: String = ""
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val configRepository: ConfigRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val settingsState: StateFlow<SettingsState> = _state.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            val vc = configRepository.getVehicleConfig()
            val fr = configRepository.getFilterRules()
            val settings = configRepository.getAppSettings()
            _state.value = SettingsState(
                vehicleName = vc.vehicleName,
                manualCostPerKm = vc.manualCostPerKm,
                avgSpeed = vc.avgSpeedKmh,
                consumptionKmPerUnit = vc.consumptionKmPerUnit,
                fuelUnitId = vc.fuelUnitId,
                fuelPricePerUnit = vc.fuelPricePerUnit,
                maintenancePerKm = vc.maintenancePerKm,
                pickupMeasure = fr.pickupMeasure,
                pickupMax = fr.pickupMax,
                tripMeasure = fr.tripMeasure,
                tripMax = fr.tripMax,
                minProfitPerKm = fr.minProfitPerKm,
                minPassengerRating = fr.minPassengerRating,
                autoAcceptEnabled = fr.autoAcceptEnabled,
                voiceAnnounceEnabled = settings.voiceAnnounceEnabled,
                humanTapsEnabled = settings.humanTapsEnabled,
                geoCheckEnabled = settings.geoCheckEnabled,
                geoApiKey = settings.geoApiKey
            )
        }
    }

    /**
     * Guarda vehículo + filtros con la UI actual. Los campos internos
     * (consumo, precio, mantenimiento) se conservan tal cual.
     */
    fun saveAll(
        vehicleName: String,
        manualCostPerKm: Double,
        avgSpeed: Double,
        pickupMeasure: String,
        pickupMax: Double,
        tripMeasure: String,
        tripMax: Double,
        minProfitPerKm: Double,
        minPassengerRating: Double,
        autoAcceptEnabled: Boolean
    ) {
        val s = _state.value
        viewModelScope.launch {
            configRepository.updateVehicleConfig(VehicleConfigEntity(
                vehicleName = vehicleName,
                consumptionKmPerUnit = s.consumptionKmPerUnit,
                fuelUnitId = s.fuelUnitId,
                fuelPricePerUnit = s.fuelPricePerUnit,
                maintenancePerKm = s.maintenancePerKm,
                manualCostPerKm = manualCostPerKm,
                avgSpeedKmh = avgSpeed
            ))
            configRepository.updateFilterRules(FilterRulesEntity(
                pickupMeasure = pickupMeasure,
                pickupMax = pickupMax,
                tripMeasure = tripMeasure,
                tripMax = tripMax,
                minProfitPerKm = minProfitPerKm,
                minPassengerRating = minPassengerRating,
                autoAcceptEnabled = autoAcceptEnabled
            ))
        }
    }

    fun updateBehavior(
        voiceAnnounceEnabled: Boolean,
        humanTapsEnabled: Boolean,
        geoCheckEnabled: Boolean,
        geoApiKey: String
    ) {
        viewModelScope.launch {
            val current = configRepository.getAppSettings()
            configRepository.updateAppSettings(
                current.copy(
                    voiceAnnounceEnabled = voiceAnnounceEnabled,
                    humanTapsEnabled = humanTapsEnabled,
                    geoCheckEnabled = geoCheckEnabled,
                    geoApiKey = geoApiKey.trim()
                )
            )
        }
    }

    /** Serializa toda la configuración a JSON para exportar. */
    suspend fun buildBackupJson(): String = ConfigBackupManager.toJson(
        ConfigBackupManager.Backup(
            vehicle = configRepository.getVehicleConfig(),
            filter = configRepository.getFilterRules(),
            settings = configRepository.getAppSettings()
        )
    )

    /**
     * Restaura la configuración desde un JSON. Reemplaza vehículo, filtros y
     * ajustes operativos. Lanza [IllegalArgumentException] si el JSON es inválido.
     */
    suspend fun restoreBackup(json: String) {
        val backup = ConfigBackupManager.fromJson(json)
        configRepository.updateVehicleConfig(backup.vehicle)
        configRepository.updateFilterRules(backup.filter)
        configRepository.updateAppSettings(backup.settings)
        load()
    }
}