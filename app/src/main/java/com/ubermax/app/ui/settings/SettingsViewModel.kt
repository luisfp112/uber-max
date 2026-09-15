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
    val vehicleName: String = "Spark 2008 1.0L",
    val costPerKm: Double = 0.08,
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
                costPerKm = vc.costPerKm,
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
                blacklistEntries = bl
            )
        }
    }

    fun saveAll(
        vehicleName: String, costPerKm: Double, avgSpeed: Double,
        minFare: Double, minNetProfit: Double, minProfitPerKm: Double, minProfitPerHour: Double,
        maxPickupKm: Double, maxTripKm: Double, maxTotalMinutes: Int, 
        minPassengerRating: Double, maxPickupTripRatio: Double,
        blacklistEnabled: Boolean, autoAcceptEnabled: Boolean, aiEnabled: Boolean
    ) {
        viewModelScope.launch {
            configRepository.updateVehicleConfig(VehicleConfigEntity(
                vehicleName = vehicleName, costPerKm = costPerKm, avgSpeedKmh = avgSpeed
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
                aiEnabled = aiEnabled
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
