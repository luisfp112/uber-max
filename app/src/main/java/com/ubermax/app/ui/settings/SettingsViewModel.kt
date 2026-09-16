package com.ubermax.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ubermax.app.data.db.entity.BlacklistEntryEntity
import com.ubermax.app.data.db.entity.FilterRulesEntity
import com.ubermax.app.data.db.entity.VehicleConfigEntity
import com.ubermax.app.data.repository.ConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsState(
    // Vehicle
    val vehicleName: String = "",
    val consumptionKmPerUnit: Double = 12.0,
    val fuelUnitId: Int = 0,
    val fuelPricePerUnit: Double = 0.86,
    val maintenancePerKm: Double = 0.03,
    val manualCostPerKm: Double = 0.0,
    val avgSpeed: Double = 22.0,

    // Filtros de Tarifa Bruta
    val minFare: Double = 1.50,

    // Filtros de Ganancia Neta
    val minNetProfit: Double = 0.80,
    val minProfitPerKm: Double = 0.15,
    val minProfitPerHour: Double = 3.00,

    // Filtros de Distancia
    val maxPickupKm: Double = 5.0,
    val maxTripKm: Double = 30.0,

    // Filtros de Tiempo
    val maxTotalMinutes: Int = 60,

    // Filtros de Pasajero
    val minPassengerRating: Double = 4.5,

    // Filtros de Ratio
    val maxPickupTripRatio: Double = 1.0,

    // Toggles
    val blacklistEnabled: Boolean = true,
    val autoAcceptEnabled: Boolean = true,
    val aiEnabled: Boolean = true,

    // Deadhead
    val deadheadThresholdKm: Double = 8.0,

    // Blacklist
    val blacklistEntries: List<BlacklistEntryEntity> = emptyList()
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val configRepository: ConfigRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val settingsState: StateFlow<SettingsState> = _state

    init { load() }

    private fun load() {
        viewModelScope.launch {
            val vc = configRepository.getVehicleConfig()
            val fr = configRepository.getFilterRules()
            val bl = configRepository.getAllBlacklist()
            _state.value = SettingsState(
                vehicleName = vc.vehicleName,
                consumptionKmPerUnit = vc.consumptionKmPerUnit,
                fuelUnitId = vc.fuelUnitId,
                fuelPricePerUnit = vc.fuelPricePerUnit,
                maintenancePerKm = vc.maintenancePerKm,
                manualCostPerKm = vc.manualCostPerKm,
                avgSpeed = vc.avgSpeedKmh,
                minFare = fr.minFare,
                minNetProfit = fr.minNetProfit,
                minProfitPerKm = fr.minProfitPerKm,
                minProfitPerHour = fr.minProfitPerHour,
                maxPickupKm = fr.maxPickupKm,
                maxTripKm = fr.maxTripKm,
                maxTotalMinutes = fr.maxTotalMinutes,
                minPassengerRating = fr.minPassengerRating,
                maxPickupTripRatio = fr.maxPickupTripRatio,
                blacklistEnabled = fr.blacklistEnabled,
                autoAcceptEnabled = fr.autoAcceptEnabled,
                aiEnabled = fr.aiEnabled,
                deadheadThresholdKm = fr.deadheadThresholdKm,
                blacklistEntries = bl
            )
        }
    }

    fun saveAll(
        vehicleName: String,
        consumptionKmPerUnit: Double,
        fuelUnitId: Int,
        fuelPricePerUnit: Double,
        maintenancePerKm: Double,
        manualCostPerKm: Double,
        avgSpeed: Double,
        minFare: Double,
        minNetProfit: Double,
        minProfitPerKm: Double,
        minProfitPerHour: Double,
        maxPickupKm: Double,
        maxTripKm: Double,
        maxTotalMinutes: Int,
        minPassengerRating: Double,
        maxPickupTripRatio: Double,
        blacklistEnabled: Boolean,
        autoAcceptEnabled: Boolean,
        aiEnabled: Boolean,
        deadheadThresholdKm: Double = 8.0
    ) {
        viewModelScope.launch {
            configRepository.updateVehicleConfig(VehicleConfigEntity(
                vehicleName = vehicleName,
                consumptionKmPerUnit = consumptionKmPerUnit,
                fuelUnitId = fuelUnitId,
                fuelPricePerUnit = fuelPricePerUnit,
                maintenancePerKm = maintenancePerKm,
                manualCostPerKm = manualCostPerKm,
                avgSpeedKmh = avgSpeed
            ))
            configRepository.updateFilterRules(FilterRulesEntity(
                minFare = minFare,
                minNetProfit = minNetProfit,
                minProfitPerKm = minProfitPerKm,
                minProfitPerHour = minProfitPerHour,
                maxPickupKm = maxPickupKm,
                maxTripKm = maxTripKm,
                maxTotalMinutes = maxTotalMinutes,
                minPassengerRating = minPassengerRating,
                maxPickupTripRatio = maxPickupTripRatio,
                blacklistEnabled = blacklistEnabled,
                autoAcceptEnabled = autoAcceptEnabled,
                aiEnabled = aiEnabled,
                deadheadThresholdKm = deadheadThresholdKm
            ))
        }
    }

    fun addBlacklistEntry(keyword: String, reason: String = "") {
        viewModelScope.launch {
            configRepository.addBlacklistEntry(BlacklistEntryEntity(keyword = keyword, reason = reason))
            load()
        }
    }

    fun removeBlacklistEntry(entry: BlacklistEntryEntity) {
        viewModelScope.launch {
            configRepository.removeBlacklistEntry(entry)
            load()
        }
    }
}
