package com.ubermax.app.domain.usecase

import com.ubermax.app.data.db.entity.VehicleConfigEntity
import com.ubermax.app.domain.model.EvaluatedOffer
import com.ubermax.app.domain.model.OfferData
import kotlin.math.roundToInt

/**
 * Calcula las métricas económicas de una oferta de viaje.
 *
 * Modelo actual (el monto de la tarjeta de Uber ya es neto: la plataforma
 * descuenta su comisión antes de mostrarlo al conductor):
 *   Ganancia Neta = Tarifa − Costo
 *   Costo = (Recogida + Viaje) × Costo por Km
 *   $/km  = Ganancia Neta / Km totales
 *
 * Sin vuelta vacía, sin comisión extra, sin métricas por hora: la única métrica
 * decisoria es la ganancia neta por kilómetro ([EvaluatedOffer.profitPerKm]).
 */
class EvaluateOfferUseCase {

    companion object {
        /** Minutos de espera/abordaje fijos que el viaje "bloquea" al conductor. */
        const val BOARDING_MINUTES = 3.0
    }

    fun evaluate(offer: OfferData, config: VehicleConfigEntity): EvaluatedOffer {
        // ── Tiempos efectivos: respetar los de Uber o re-estimar con la velocidad real ──
        var effectiveTripMinutes = offer.tripMinutes

        if (offer.minutesAreEstimated) {
            effectiveTripMinutes = estimateMinutes(offer.totalKm, config.avgSpeedKmh)
        }

        val fuelCost = offer.totalKm * config.costPerKm
        val netProfit = offer.rawFare - fuelCost
        val profitPerKm = if (offer.totalKm > 0) netProfit / offer.totalKm else 0.0

        return EvaluatedOffer(
            offer = offer,
            fuelCost = fuelCost,
            netProfit = netProfit,
            profitPerKm = profitPerKm,
            totalKm = offer.totalKm
        )
    }

    private fun estimateMinutes(totalKm: Double, avgSpeedKmh: Double): Int {
        if (totalKm <= 0 || avgSpeedKmh <= 0) return 0
        return (totalKm / avgSpeedKmh * 60.0).roundToInt().coerceAtLeast(1)
    }
}