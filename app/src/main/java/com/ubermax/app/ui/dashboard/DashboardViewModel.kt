package com.ubermax.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ubermax.app.data.db.dao.HourStat
import com.ubermax.app.data.db.dao.ZoneStat
import com.ubermax.app.data.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Estado de métricas reales del día mostradas en el Dashboard.
 */
data class DashboardStats(
    val todayOffers: Int = 0,
    val todayAccepted: Int = 0,
    val todayNetProfit: Double = 0.0,
    val bestHours: List<HourStat> = emptyList(),
    val bestZones: List<ZoneStat> = emptyList()
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val tripRepository: TripRepository
) : ViewModel() {

    private val _stats = MutableStateFlow(DashboardStats())
    val stats: StateFlow<DashboardStats> = _stats.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _stats.value = DashboardStats(
                todayOffers = tripRepository.getTodayOfferCount(),
                todayAccepted = tripRepository.getTodayAcceptedCount(),
                todayNetProfit = tripRepository.getTodayNetProfit(),
                bestHours = tripRepository.getBestHours(),
                bestZones = tripRepository.getBestZones()
            )
        }
    }

    /** CSV de todo el historial listo para compartir vía Intent. */
    suspend fun buildExportCsv(): String =
        com.ubermax.app.util.CsvExporter.buildCsv(tripRepository.getAllTrips())
}
