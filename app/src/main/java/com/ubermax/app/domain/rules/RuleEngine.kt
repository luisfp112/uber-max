package com.ubermax.app.domain.rules

import com.ubermax.app.data.db.entity.FilterRulesEntity
import com.ubermax.app.domain.geo.UrbanBoundary
import com.ubermax.app.domain.model.Action
import com.ubermax.app.domain.model.EvaluatedOffer
import com.ubermax.app.domain.model.OfferDecision
import java.util.Locale

/**
 * Motor de reglas con 2 salidas:
 *
 * ACCEPT → Cumple TODOS los filtros → Auto-aceptar (si [FilterRulesEntity.autoAcceptEnabled])
 * WARN   → No cumple algún filtro (incluida la zona fuera del perímetro urbano)
 *          o el auto-accept está apagado → Mostrar en qué falló (rechazo pasivo).
 *
 * La única métrica económica que decide es la ganancia neta por km
 * ([FilterRulesEntity.minProfitPerKm]): la oferta nunca se rechaza con un toque,
 * solo se avisa. Los filtros con valor 0 están desactivados (no se evalúan).
 */
class RuleEngine {

    companion object {
        const val DEFAULT_AVG_SPEED_KMH = 22.0
    }

    /**
     * Evalúa una oferta contra las reglas del conductor.
     *
     * @param evaluated Oferta con métricas económicas calculadas
     * @param rules Reglas de filtrado configuradas por el conductor
     * @param avgSpeedKmh Velocidad promedio (fallback de unidades cuando falta el valor de la tarjeta)
     * @param boundary Zona permitida del conductor (polígonos del GeoJSON). Si no
     *   es null, se evalúan AMBAS ubicaciones de la oferta —recogida y destino—:
     *   un punto fuera añade un WARN ([UrbanBoundary.outsideReason]); un punto sin
     *   coordenadas (no verificable) añade un WARN "no confirmado". Rechazo
     *   siempre pasivo: jamás se pulsa la X.
     */
    fun evaluate(
        evaluated: EvaluatedOffer,
        rules: FilterRulesEntity,
        avgSpeedKmh: Double = DEFAULT_AVG_SPEED_KMH,
        boundary: UrbanBoundary? = null
    ): OfferDecision {

        val failedFilters = mutableListOf<String>()

        // ── Zonas permitidas (geolocalización): recogida Y destino dentro del área ──
        if (boundary != null) {
            val pickup = evaluated.offer.pickupLatLng
            if (pickup == null) {
                failedFilters.add("🚙 Recogida no confirmada dentro de la zona permitida")
            } else if (!boundary.contains(pickup.first, pickup.second)) {
                failedFilters.add("🚙 Recogida fuera de la zona permitida")
            }

            val dest = evaluated.offer.destinationLatLng
            if (dest == null) {
                failedFilters.add("📍 Destino no confirmado dentro de la zona permitida")
            } else if (!boundary.contains(dest.first, dest.second)) {
                failedFilters.add(boundary.outsideReason(dest.first, dest.second))
            }
        }

        // ── Filtro de recogida (min/km de la tarjeta, unidad configurable) ──
        val pickupMax = rules.pickupMax
        if (pickupMax > 0) {
            val pickupValue = valueIn(
                MeasureUnit.from(rules.pickupMeasure),
                evaluated.offer.pickupMinutes.toDouble(),
                evaluated.offer.pickupKm,
                avgSpeedKmh
            )
            if (pickupValue > 0 && pickupValue > pickupMax) {
                failedFilters.add(
                    "🚙 Recogida muy lejos: ${fmt(pickupValue)}${unit(rules.pickupMeasure)} > máx ${fmt(pickupMax)}${unit(rules.pickupMeasure)}"
                )
            }
        }

        // ── Filtro de viaje (siempre aplica, 0 = sin tope) ──
        val tripMax = rules.tripMax
        if (tripMax > 0) {
            val tripValue = valueIn(
                MeasureUnit.from(rules.tripMeasure),
                evaluated.offer.tripMinutes.toDouble(),
                evaluated.offer.tripKm,
                avgSpeedKmh
            )
            if (tripValue > 0 && tripValue > tripMax) {
                failedFilters.add(
                    "🛣️ Recorrido excede el máximo: ${fmt(tripValue)}${unit(rules.tripMeasure)} > máx ${fmt(tripMax)}${unit(rules.tripMeasure)}"
                )
            }
        }

        // ── Métrica principal de rentabilidad: $/km neto ──
        if (rules.minProfitPerKm > 0 && evaluated.profitPerKm < rules.minProfitPerKm) {
            failedFilters.add("📏 \$/km bajo: \$${fmt(evaluated.profitPerKm)} < mín \$${fmt(rules.minProfitPerKm)}")
        }

        // ── Tarifa mínima (guarda opcional, 0 = desactivado) ──
        if (rules.minFare > 0 && evaluated.offer.rawFare < rules.minFare) {
            failedFilters.add("💰 Tarifa baja: \$${fmt(evaluated.offer.rawFare)} < mín \$${fmt(rules.minFare)}")
        }

        // ── Rating mínimo del pasajero (configurado por el conductor, 0 = off) ──
        if (rules.minPassengerRating > 0 && !evaluated.offer.hasRating && rules.rejectOnUnknownRating) {
            failedFilters.add("⭐ Rating desconocido — rechazar por configuración")
        } else if (rules.minPassengerRating > 0 &&
            evaluated.offer.hasRating &&
            evaluated.offer.passengerRating < rules.minPassengerRating
        ) {
            failedFilters.add("⭐ Rating bajo: ${fmt(evaluated.offer.passengerRating)} < mín ${fmt(rules.minPassengerRating)}")
        }

        // ═══════════════════════════════════════════════
        // DECISIÓN FINAL: solo ACCEPT si NO falló NINGÚN filtro
        // ═══════════════════════════════════════════════
        return if (failedFilters.isEmpty()) {
            OfferDecision(
                evaluatedOffer = evaluated,
                action = if (rules.autoAcceptEnabled) Action.ACCEPT else Action.WARN,
                failedFilters = if (rules.autoAcceptEnabled) emptyList()
                else listOf("ℹ️ Auto-accept desactivado")
            )
        } else {
            OfferDecision(
                evaluatedOffer = evaluated,
                action = Action.WARN,
                failedFilters = failedFilters
            )
        }
    }

    /**
     * Convierte el valor medido según [MeasureUnit]: si está en la unidad
     * configurada se usa tal cual; si falta, se estima con la velocidad promedio.
     */
    private fun valueIn(
        measure: MeasureUnit,
        minutes: Double,
        km: Double,
        avgSpeedKmh: Double
    ): Double = when (measure) {
        MeasureUnit.MIN -> if (minutes > 0) {
            minutes
        } else if (km > 0 && avgSpeedKmh > 0) {
            km * 60.0 / avgSpeedKmh
        } else {
            0.0
        }

        MeasureUnit.KM -> if (km > 0) {
            km
        } else if (minutes > 0 && avgSpeedKmh > 0) {
            minutes * avgSpeedKmh / 60.0
        } else {
            0.0
        }
    }

    private fun unit(measure: String): String = if (MeasureUnit.from(measure) == MeasureUnit.KM) "km" else "min"

    /** Formatea double a 2 decimales */
    private fun fmt(value: Double): String = String.format(Locale.US, "%.2f", value)
}