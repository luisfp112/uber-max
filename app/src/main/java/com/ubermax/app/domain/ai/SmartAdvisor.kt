package com.ubermax.app.domain.ai

import com.ubermax.app.data.db.entity.TripLogEntity
import com.ubermax.app.domain.model.OfferDecision
import com.ubermax.app.domain.port.TripHistorySource
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SmartAdvisor v1 — IA Local (Sistema Basado en Reglas y Estadísticas)
 *
 * Analiza el historial de viajes (TripLogs) para generar recomendaciones
 * contextuales sin necesidad de conexión externa.
 * Funciona offline aprendiendo de los patrones locales del conductor.
 */
@Singleton
class SmartAdvisor @Inject constructor(
    private val tripHistory: TripHistorySource
) {

    suspend fun analyzeOffer(decision: OfferDecision): OfferDecision {
        val offer = decision.evaluatedOffer.offer

        // 1. Obtener historial reciente para contexto
        val recentTrips = try {
            tripHistory.getAllTripsFlow().firstOrNull() ?: emptyList()
        } catch (e: Exception) {
            // No distinguimos el tipo de error aquí: el flujo baja a "sin datos"
            // (confianza 0.1) en ambos casos. Solo logueamos para diagnosticar
            // si la DB falla de verdad en producción.
            com.ubermax.app.util.Logs.e("SmartAdvisor", "Error accediendo al historial: ${e.message}", e)
            emptyList()
        }

        // CANCEL es mandatorio: el conductor configuró un bloqueo duro (lista
        // negra). La IA SIEMPRE lo confirma a máxima confianza, incluso sin
        // historial, para no relajar un auto-rechazo ya decidido.
        if (decision.isCancelled) {
            return decision.copy(
                aiRecommendation = "IA: Rechazo mandatorio confirmado",
                aiConfidence = 1.0
            )
        }

        if (recentTrips.isEmpty()) {
            return decision.copy(
                aiRecommendation = "Necesita más datos para IA",
                aiConfidence = 0.1
            )
        }

        // 2. Analizar zona de destino
        val destScore = calculateDestinationScore(offer.destination, recentTrips)
        
        // 3. Generar recomendación basada en la decisión original y el análisis IA
        var recommendation = ""
        var confidence = 0.5
        
        if (decision.isAccepted) {
            if (destScore < 0.3) {
                recommendation = "Precaución: Destino históricamente poco rentable"
                confidence = 0.8
            } else {
                recommendation = "Buena elección basada en historial"
                confidence = 0.9
            }
        } else if (decision.isWarning) {
            if (destScore > 0.8) {
                recommendation = "Recomendado aceptar: Buena zona compensa fallos"
                confidence = 0.75
            } else {
                recommendation = "Mejor rechazar: Zona mala + filtros fallidos"
                confidence = 0.9
            }
        }

        return decision.copy(
            aiRecommendation = recommendation,
            aiConfidence = confidence
        )
    }

    /**
     * Calcula un score (0.0 a 1.0) para un destino basado en el historial de viajes.
     * Evalúa si históricamente se gana bien ($/km) y si se suelen aceptar viajes hacia allá.
     */
    private fun calculateDestinationScore(destination: String, history: List<TripLogEntity>): Double {
        if (destination.isBlank()) return 0.5

        // Buscar viajes similares en historial (coincidencia parcial básica)
        val term = destination.split(",").firstOrNull()?.trim()?.lowercase() ?: return 0.5
        if (term.length < 4) return 0.5

        val matchingTrips = history.filter { 
            it.destination.lowercase().contains(term) || it.pickupAddress.lowercase().contains(term)
        }

        if (matchingTrips.isEmpty()) return 0.5 // Zona desconocida

        val acceptedTrips = matchingTrips.filter { it.decision == "ACCEPT" }
        val acceptRatio = if (matchingTrips.isNotEmpty()) acceptedTrips.size.toDouble() / matchingTrips.size else 0.0

        val avgProfitKm = if (acceptedTrips.isNotEmpty()) {
            acceptedTrips.map { it.profitPerKm }.average()
        } else {
            0.0
        }

        // Score heurístico: 60% peso a profit, 40% a ratio de aceptación
        // Asumiendo que un buen profit/km en Ambato es > $0.25
        val profitScore = (avgProfitKm / 0.25).coerceIn(0.0, 1.0)
        
        return (profitScore * 0.6) + (acceptRatio * 0.4)
    }
}
