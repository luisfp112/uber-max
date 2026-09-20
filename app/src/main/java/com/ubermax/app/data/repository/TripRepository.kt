package com.ubermax.app.data.repository

import com.ubermax.app.data.db.dao.TripLogDao
import com.ubermax.app.data.db.dao.HourStat
import com.ubermax.app.data.db.dao.ZoneStat
import com.ubermax.app.data.db.entity.TripLogEntity
import com.ubermax.app.domain.model.EvaluatedOffer
import com.ubermax.app.domain.model.OfferDecision
import com.ubermax.app.domain.port.TripHistorySource
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repositorio para gestionar el historial de ofertas de viaje.
 * Abstrae el acceso a Room y proporciona métodos de alto nivel.
 */
@Singleton
class TripRepository @Inject constructor(
    private val tripLogDao: TripLogDao
) : TripHistorySource {
    /** Guardar una decisión de oferta en la base de datos */
    suspend fun logDecision(
        decision: OfferDecision,
        actionApplied: Boolean = false,
        resolution: String = "UNKNOWN"
    ): Long {
        val calendar = Calendar.getInstance()
        val offer = decision.evaluatedOffer.offer
        val entity = TripLogEntity(
            timestamp = System.currentTimeMillis(),
            dayOfWeek = calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale("es")) ?: "Desconocido",
            hourOfDay = calendar.get(Calendar.HOUR_OF_DAY),
            rawFare = offer.rawFare,
            pickupKm = offer.pickupKm,
            tripKm = offer.tripKm,
            estimatedMinutes = offer.estimatedMinutes,
            passengerRating = offer.passengerRating,
            destination = offer.destination,
            pickupAddress = offer.pickupAddress,
            fuelCost = decision.evaluatedOffer.fuelCost,
            netProfit = decision.evaluatedOffer.netProfit,
            profitPerKm = decision.evaluatedOffer.profitPerKm,
            profitPerHour = decision.evaluatedOffer.profitPerHour,
            totalKm = decision.evaluatedOffer.totalKm,
            decision = decision.action.name,
            failedFilters = decision.failedFilters.joinToString(","),
            hasRating = offer.hasRating,
            rideType = offer.rideType,
            passengerTrips = offer.passengerTrips,
            minutesEstimated = offer.minutesAreEstimated,
            actionApplied = actionApplied,
            resolution = resolution,
            aiRecommendation = decision.aiRecommendation,
            aiConfidence = decision.aiConfidence,
            pickupLat = offer.pickupLatLng?.first,
            pickupLng = offer.pickupLatLng?.second,
            destLat = offer.destinationLatLng?.first,
            destLng = offer.destinationLatLng?.second
        )
        return tripLogDao.insert(entity)
    }

    /** Actualiza la resolución de un viaje ya registrado (resultado real). */
    suspend fun updateResolution(id: Long, resolution: String) {
        tripLogDao.updateResolution(id, resolution)
    }

    /** Stream reactivo de todos los viajes */
    override fun getAllTripsFlow(): Flow<List<TripLogEntity>> = tripLogDao.getAllTripsFlow()

    /** Los [limit] viajes más recientes */
    override suspend fun getRecentTrips(limit: Int): List<TripLogEntity> =
        tripLogDao.getRecentTrips(limit)

    /** Viajes de hoy */
    suspend fun getTodayTrips(): List<TripLogEntity> {
        val (start, end) = getTodayRange()
        return tripLogDao.getTripsForDay(start, end)
    }

    /** Estadísticas: mejores horas */
    suspend fun getBestHours(): List<HourStat> = tripLogDao.getBestHours()

    /** Estadísticas: mejores zonas */
    suspend fun getBestZones(): List<ZoneStat> = tripLogDao.getBestZones()

    /** Conteo de ofertas de hoy */
    suspend fun getTodayOfferCount(): Int {
        val (start, end) = getTodayRange()
        return tripLogDao.getTotalOffersForDay(start, end)
    }

    /** Conteo de aceptadas de hoy */
    suspend fun getTodayAcceptedCount(): Int {
        val (start, end) = getTodayRange()
        return tripLogDao.getAcceptedOffersForDay(start, end)
    }

    /** Ganancia neta total de hoy */
    suspend fun getTodayNetProfit(): Double {
        val (start, end) = getTodayRange()
        return tripLogDao.getTotalNetProfitForDay(start, end) ?: 0.0
    }

    /** Todos los viajes para exportación CSV */
    suspend fun getAllTrips(): List<TripLogEntity> = tripLogDao.getAllTrips()

    private fun getTodayRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val end = cal.timeInMillis
        return start to end
    }
}
