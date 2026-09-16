package com.ubermax.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ubermax.app.data.db.dao.HourStat
import com.ubermax.app.data.db.dao.ZoneStat
import com.ubermax.app.data.db.entity.TripLogEntity
import com.ubermax.app.data.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    val stats: StateFlow<DashboardStats> = _stats

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

    fun buildCsv(trips: List<TripLogEntity>): String {
        val header = listOf(
            "timestamp", "dia", "hora", "tarifa_bruta", "pickup_km", "trip_km",
            "minutos", "rating", "destino", "pickup", "costo_combustible",
            "ganancia_neta", "ganancia_km", "ganancia_hora", "km_total",
            "decision", "filtros_fallidos"
        ).joinToString(",")

        val rows = trips.map { t ->
            listOf(
                formatTimestamp(t.timestamp),
                escape(t.dayOfWeek),
                t.hourOfDay.toString(),
                formatNum(t.rawFare),
                formatNum(t.pickupKm),
                formatNum(t.tripKm),
                t.estimatedMinutes.toString(),
                formatNum(t.passengerRating),
                escape(t.destination),
                escape(t.pickupAddress),
                formatNum(t.fuelCost),
                formatNum(t.netProfit),
                formatNum(t.profitPerKm),
                formatNum(t.profitPerHour),
                formatNum(t.totalKm),
                t.decision,
                escape(t.failedFilters)
            ).joinToString(",")
        }

        return (listOf(header) + rows).joinToString("\n")
    }

    private fun formatTimestamp(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))

    private fun formatNum(value: Double): String =
        String.format(Locale.US, "%.2f", value)

    private fun escape(field: String): String {
        val safe = field.replace("\"", "\"\"")
        return if (safe.contains(',') || safe.contains('\n') || safe.contains('"')) {
            "\"$safe\""
        } else {
            safe
        }
    }
}