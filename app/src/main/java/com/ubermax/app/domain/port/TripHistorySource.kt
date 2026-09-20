package com.ubermax.app.domain.port

import com.ubermax.app.data.db.entity.TripLogEntity
import kotlinx.coroutines.flow.Flow

/**
 * Puerto de dominio para acceder al historial de viajes.
 *
 * Permite que el SmartAdvisor (IA local) lea el historial SIN depender de la
 * capa de datos concreta (Room), facilitando tests JVM puros con un fake.
 */
interface TripHistorySource {
    /** Stream reactivo de todos los viajes registrados. */
    fun getAllTripsFlow(): Flow<List<TripLogEntity>>

    /** Los [limit] viajes más recientes (para análisis con memoria acotada). */
    suspend fun getRecentTrips(limit: Int): List<TripLogEntity>
}