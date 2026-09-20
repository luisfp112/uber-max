package com.ubermax.app.domain.usecase

import com.ubermax.app.data.db.entity.VehicleConfigEntity
import com.ubermax.app.domain.model.EvaluatedOffer
import com.ubermax.app.domain.model.OfferData
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Calcula las métricas económicas de una oferta de viaje.
 *
 * Fórmulas:
 *   Tarifa Neta = Tarifa Bruta × (1 − comisión de plataforma)
 *   Costo Combustible = (Km Recogida + Km Viaje + Vuelta Vacía) × Costo por Km
 *   Ganancia Neta = Tarifa Neta − Costo Combustible − Costo de Ralentí
 *   Rentabilidad/Km = Ganancia Neta / Km Totales
 *   Rentabilidad/Hora = Ganancia Neta / Horas Estimadas
 *
 * Detalles del modelo:
 *  - Comisión de plataforma configurable (default 9%), 0 = sin comisión.
 *  - Si los minutos vinieron de un fallback ([OfferData.minutesAreEstimated]),
 *    el tiempo se recalcula con la velocidad promedio del conductor
 *    ([VehicleConfigEntity.avgSpeedKmh]) en lugar de un valor fijo.
 *  - Costo de ralentí: se estima el tiempo de espera en la recogida como la
 *    parte del pickupMinutes no explicada por recorrer pickupKm a la velocidad
 *    promedio, y se multiplica por [VehicleConfigEntity.engineIdleCostPerHour].
 *  - Vuelta vacía (deadhead): solo se aplica si el conductor activa el factor
 *    ([deadheadReturnFactor] > 0). La penalización arranca en rampa suave
 *    desde el umbral (0 km de retorno) hasta el doble del umbral (retorno
 *    completo × factor).
 *
 * @param deadheadThresholdKm Umbral base en km para la vuelta vacía.
 * @param deadheadReturnFactor Factor de retorno (0 = desactivado; 1 = retorno completo).
 */
class EvaluateOfferUseCase {

    fun evaluate(
        offer: OfferData,
        config: VehicleConfigEntity,
        deadheadThresholdKm: Double = 8.0,
        deadheadReturnFactor: Double = 0.0
    ): EvaluatedOffer {
        // ── Tarifa neta tras la comisión de la plataforma ──
        val commissionFraction = (config.platformCommissionPercent / 100.0).coerceIn(0.0, 1.0)
        val netFare = offer.rawFare * (1.0 - commissionFraction)

        // ── Tiempos efectivos: respetar los de Uber o re-estimar con la velocidad real ──
        var effectivePickupMinutes = offer.pickupMinutes
        var effectiveTripMinutes = offer.tripMinutes
        var effectiveEstimatedMinutes = offer.estimatedMinutes

        if (offer.minutesAreEstimated) {
            val estimated = estimateMinutesFromDistance(offer.totalKm, config.avgSpeedKmh)
            effectiveEstimatedMinutes = estimated
            // Con fallback no hay desglose pickup/trip fiable → solo importa el total.
            effectivePickupMinutes = 0
            effectiveTripMinutes = estimated
        }

        // ── Costo de ralentí (espera de recogida estimada) ──
        val waitingMinutes = if (offer.minutesAreEstimated) {
            0.0
        } else {
            val drivingPickupMinutes = if (config.avgSpeedKmh > 0 && offer.pickupKm > 0) {
                offer.pickupKm / config.avgSpeedKmh * 60.0
            } else {
                offer.pickupMinutes.toDouble()
            }
            max(offer.pickupMinutes.toDouble() - drivingPickupMinutes, 0.0)
        }
        val idleCost = config.engineIdleCostPerHour * (waitingMinutes / 60.0)

        // ── Vuelta vacía: solo si el conductor activó el factor ──
        val canApplyDeadhead = deadheadReturnFactor > 0 &&
            deadheadThresholdKm > 0 &&
            offer.tripKm > deadheadThresholdKm
        val returnKm = if (canApplyDeadhead) {
            val ramp = ((offer.tripKm - deadheadThresholdKm) / deadheadThresholdKm).coerceIn(0.0, 1.0)
            offer.tripKm * deadheadReturnFactor * ramp
        } else {
            0.0
        }
        val hasDeadheadPenalty = returnKm > 0

        val totalKm = offer.totalKm + returnKm
        val fuelCost = totalKm * config.costPerKm
        val netProfit = netFare - fuelCost - idleCost

        val profitPerKm = if (totalKm > 0) netProfit / totalKm else 0.0

        // Si hay vuelta vacía, el tiempo también aumenta (misma velocidad que la ida)
        val returnMinutes = if (hasDeadheadPenalty) effectiveTripMinutes else 0
        val totalEstimatedMinutes = effectiveEstimatedMinutes + returnMinutes
        val estimatedHours = totalEstimatedMinutes / 60.0

        val profitPerHour = if (estimatedHours > 0) netProfit / estimatedHours else 0.0

        return EvaluatedOffer(
            offer = offer,
            fuelCost = fuelCost,
            netProfit = netProfit,
            profitPerKm = profitPerKm,
            profitPerHour = profitPerHour,
            totalKm = totalKm,
            hasDeadheadPenalty = hasDeadheadPenalty,
            returnKm = returnKm
        )
    }

    private fun estimateMinutesFromDistance(totalKm: Double, avgSpeedKmh: Double): Int {
        if (totalKm <= 0 || avgSpeedKmh <= 0) return 0
        return (totalKm / avgSpeedKmh * 60.0).roundToInt().coerceAtLeast(1)
    }
}