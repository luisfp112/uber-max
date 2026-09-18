package com.ubermax.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ubermax.app.data.db.dao.HourStat
import com.ubermax.app.data.db.entity.TripLogEntity
import com.ubermax.app.data.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Rango temporal del historial. */
enum class HistoryFilter {
    TODAY,
    LAST_7D,
    LAST_30D,
    ALL
}

data class HistoryState(
    val trips: List<TripLogEntity> = emptyList(),
    val hours: List<HourStat> = emptyList(),
    val filter: HistoryFilter = HistoryFilter.TODAY
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val tripRepository: TripRepository
) : ViewModel() {

    private val filter = MutableStateFlow(HistoryFilter.TODAY)
    private val hours = MutableStateFlow<List<HourStat>>(emptyList())

    val state: StateFlow<HistoryState> = combine(
        tripRepository.getAllTripsFlow(),
        filter,
        hours
    ) { trips, activeFilter, hourStats ->
        HistoryState(
            trips = trips.filter { it.timestamp >= cutoffMillis(activeFilter) },
            hours = hourStats,
            filter = activeFilter
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HistoryState()
    )

    init {
        viewModelScope.launch {
            hours.value = tripRepository.getBestHours()
        }
    }

    fun setFilter(value: HistoryFilter) {
        filter.value = value
    }

    companion object {
        /** Milisegundos de corte inferior para el filtro dado. */
        fun cutoffMillis(filter: HistoryFilter, now: Long = System.currentTimeMillis()): Long =
            when (filter) {
                HistoryFilter.TODAY -> startOfDay(now)
                HistoryFilter.LAST_7D -> now - 7L * 24 * 60 * 60 * 1000
                HistoryFilter.LAST_30D -> now - 30L * 24 * 60 * 60 * 1000
                HistoryFilter.ALL -> 0L
            }

        fun startOfDay(now: Long): Long {
            val cal = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return cal.timeInMillis
        }
    }
}
