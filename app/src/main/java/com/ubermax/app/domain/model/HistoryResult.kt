package com.ubermax.app.domain.model

import com.ubermax.app.data.db.entity.TripLogEntity

/**
 * Resultado tipado de leer el historial de viajes.
 *
 * Evita el `catch (e: Exception)` silencioso: la IA distingue "sin datos aún"
 * ([Success] con lista vacía) de "no se pudo leer" ([Failure]), aunque en ambos
 * casos degrade a baja confianza.
 */
sealed interface HistoryResult {
    data class Success(val trips: List<TripLogEntity>) : HistoryResult
    data class Failure(val error: Throwable) : HistoryResult
}
