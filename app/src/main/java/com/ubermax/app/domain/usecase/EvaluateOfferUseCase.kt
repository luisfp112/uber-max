package com.ubermax.app.domain.usecase

import com.ubermax.app.data.db.entity.VehicleConfigEntity
import com.ubermax.app.domain.model.EvaluatedOffer
import com.ubermax.app.domain.model.OfferData

/**
 * Calcula las métricas económicas de una oferta de viaje.
 *
 * Fórmulas:
 *   Costo Combustible = (Km Recogida + Km Viaje) × Costo por Km
 *   Ganancia Neta = Tarifa Bruta - Costo Combustible
 *   Rentabilidad/Km = Ganancia Neta / Km Totales
 *   Rentabilidad/Hora = Ganancia Neta / Horas Estimadas
 */
class EvaluateOfferUseCase {

    /**
     * Evalúa económicamente una oferta de viaje.
     * @param offer Datos brutos extraídos de Uber Driver
     * @param config Configuración del vehículo con costo por km
     * @return EvaluatedOffer con todas las métricas calculadas
     */
    fun evaluate(offer: OfferData, config: VehicleConfigEntity): EvaluatedOffer {
        // Heurística de vuelta perdida (Deadhead penalty):
        // Si el viaje es mayor a 8km, es muy probable que termine en una zona sin demanda.
        // Se asume que el conductor tendrá que hacer una "vuelta vacía" de la misma distancia.
        val isFarTrip = offer.tripKm > 8.0
        val returnKm = if (isFarTrip) offer.tripKm else 0.0
        
        val totalKm = offer.totalKm + returnKm
        val fuelCost = totalKm * config.costPerKm
        val netProfit = offer.rawFare - fuelCost

        val profitPerKm = if (totalKm > 0) netProfit / totalKm else 0.0
        
        // Si hay vuelta vacía, el tiempo también aumenta (se asume misma velocidad que el viaje de ida)
        val returnMinutes = if (isFarTrip) offer.tripMinutes else 0
        val totalEstimatedMinutes = offer.estimatedMinutes + returnMinutes
        val estimatedHours = totalEstimatedMinutes / 60.0
        
        val profitPerHour = if (estimatedHours > 0) netProfit / estimatedHours else 0.0

        return EvaluatedOffer(
            offer = offer,
            fuelCost = fuelCost,
            netProfit = netProfit,
            profitPerKm = profitPerKm,
            profitPerHour = profitPerHour,
            totalKm = totalKm,
            hasDeadheadPenalty = isFarTrip,
            returnKm = returnKm
        )
    }
}
