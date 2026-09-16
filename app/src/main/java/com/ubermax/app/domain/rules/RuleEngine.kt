package com.ubermax.app.domain.rules

import com.ubermax.app.data.db.entity.BlacklistZoneEntity
import com.ubermax.app.data.db.entity.FilterRulesEntity
import com.ubermax.app.domain.geometry.PolygonGeometry
import com.ubermax.app.domain.model.Action
import com.ubermax.app.domain.model.EvaluatedOffer
import com.ubermax.app.domain.model.OfferDecision
import com.ubermax.app.util.TextNormalizer

/**
 * Motor de reglas estilo Maxymo con 3 niveles de decisión.
 *
 * ACCEPT → Cumple todos los filtros del conductor → Auto-aceptar
 * WARN   → No cumple algún filtro económico → Mostrar aviso (conductor decide)
 * CANCEL → Destino en lista negra → Auto-rechazar (clic en X)
 *
 * Los filtros con valor 0 están desactivados (no se evalúan).
 */
class RuleEngine {

    companion object {
        private const val TAG = "RuleEngine"
    }

    /**
     * Evalúa una oferta contra las reglas del conductor.
     *
     * @param evaluated Oferta con métricas económicas calculadas
     * @param rules Reglas de filtrado configuradas por el conductor
     * @param blacklistKeywords Palabras clave de la lista negra por texto
     * @param blacklistZones Polígonos de la lista negra (zona + keywords + vértices)
     * @return Decisión con acción y filtros fallidos
     */
    fun evaluate(
        evaluated: EvaluatedOffer,
        rules: FilterRulesEntity,
        blacklistKeywords: List<String> = emptyList(),
        blacklistZones: List<BlacklistZoneEntity> = emptyList()
    ): OfferDecision {

        // ═══════════════════════════════════════════════
        // PRIORIDAD 1: LISTA NEGRA → CANCEL automático
        // ═══════════════════════════════════════════════
        if (rules.blacklistEnabled) {
            val blacklistHit = checkBlacklist(evaluated, blacklistKeywords, blacklistZones)
            if (blacklistHit != null) {
                return OfferDecision(
                    evaluatedOffer = evaluated,
                    action = Action.CANCEL,
                    failedFilters = listOf("🚫 LISTA NEGRA: $blacklistHit")
                )
            }
        }

        // ═══════════════════════════════════════════════
        // PRIORIDAD 2: FILTROS ECONÓMICOS → WARN si falla
        // ═══════════════════════════════════════════════
        val failedFilters = mutableListOf<String>()

        // ── Tarifa mínima ──
        if (rules.minFare > 0 && evaluated.offer.rawFare < rules.minFare) {
            failedFilters.add("💰 Tarifa baja: \$${f(evaluated.offer.rawFare)} < mín \$${f(rules.minFare)}")
        }

        // ── Ganancia neta mínima ──
        if (rules.minNetProfit > 0 && evaluated.netProfit < rules.minNetProfit) {
            failedFilters.add("📉 Ganancia neta baja: \$${f(evaluated.netProfit)} < mín \$${f(rules.minNetProfit)}")
        }

        // ── Ganancia/km mínima ──
        if (rules.minProfitPerKm > 0 && evaluated.profitPerKm < rules.minProfitPerKm) {
            failedFilters.add("📏 \$/km bajo: \$${f(evaluated.profitPerKm)} < mín \$${f(rules.minProfitPerKm)}")
        }

        // ── Ganancia/hora mínima ──
        if (rules.minProfitPerHour > 0 && evaluated.profitPerHour < rules.minProfitPerHour) {
            failedFilters.add("⏱️ \$/hr bajo: \$${f(evaluated.profitPerHour)} < mín \$${f(rules.minProfitPerHour)}")
        }

        // ── Distancia máxima de recogida ──
        if (rules.maxPickupKm > 0 && evaluated.offer.pickupKm > rules.maxPickupKm) {
            failedFilters.add("📍 Pickup lejos: ${f(evaluated.offer.pickupKm)}km > máx ${f(rules.maxPickupKm)}km")
        }

        // ── Distancia máxima del viaje ──
        if (rules.maxTripKm > 0 && evaluated.offer.tripKm > rules.maxTripKm) {
            failedFilters.add("🛣️ Viaje largo: ${f(evaluated.offer.tripKm)}km > máx ${f(rules.maxTripKm)}km")
        }

        // ── Tiempo máximo total ──
        if (rules.maxTotalMinutes > 0 && evaluated.offer.estimatedMinutes > rules.maxTotalMinutes) {
            failedFilters.add("⏰ Tiempo largo: ${evaluated.offer.estimatedMinutes}min > máx ${rules.maxTotalMinutes}min")
        }

        // ── Rating mínimo del pasajero ──
        if (rules.minPassengerRating > 0 && evaluated.offer.passengerRating < rules.minPassengerRating) {
            failedFilters.add("⭐ Rating bajo: ${f(evaluated.offer.passengerRating)} < mín ${f(rules.minPassengerRating)}")
        }

        // ── Ratio pickup/trip ──
        if (rules.maxPickupTripRatio > 0 && evaluated.offer.tripKm > 0) {
            val ratio = evaluated.offer.pickupKm / evaluated.offer.tripKm
            if (ratio > rules.maxPickupTripRatio) {
                failedFilters.add("📐 Ratio pickup/trip alto: ${f(ratio)} > máx ${f(rules.maxPickupTripRatio)}")
            }
        }

        // ── Penalización por Vuelta Vacía (Deadhead) ──
        // El evaluador económico ya incluye la vuelta vacía en netProfit / profitPerKm /
        // profitPerHour cuando el viaje supera el umbral (ej: > 8 km). Por eso un viaje
        // largo NO se marca como fallido solo por la distancia: únicamente se reporta
        // cuando la rentabilidad FINAL (ya con deadhead) cae bajo los mínimos del usuario.
        if (evaluated.hasDeadheadPenalty && (rules.minNetProfit > 0 || rules.minProfitPerKm > 0 || rules.minProfitPerHour > 0)) {
            val netBelowMin = rules.minNetProfit > 0 && evaluated.netProfit < rules.minNetProfit
            val perKmBelowMin = rules.minProfitPerKm > 0 && evaluated.profitPerKm < rules.minProfitPerKm
            val perHourBelowMin = rules.minProfitPerHour > 0 && evaluated.profitPerHour < rules.minProfitPerHour

            if (netBelowMin || perKmBelowMin || perHourBelowMin) {
                failedFilters.add("🔙 Vuelta vacía ${f(evaluated.returnKm)}km: rentabilidad final bajo mínimos")
            }
        }

        // ═══════════════════════════════════════════════
        // DECISIÓN FINAL
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
     * Verifica si el destino o la dirección de recogida coinciden con la lista negra.
     *
     * Estrategia de match (en orden de prioridad):
     * 1. Keywords de texto (calles, barrios) — incluye keywords auto-extraídas de polígonos
     * 2. Nombre de zona del mapa
     * 3. Keywords extraídas del polígono (almacenadas en la zona)
     * 4. Point-in-polygon (ray casting) cuando la oferta tiene coordenadas GPS
     *
     * Tanto el texto real de la oferta como las keywords y zonas se normalizan con
     * [TextNormalizer], de modo que las coincidencias ignoran mayúsculas, tildes y
     * caracteres especiales ("Av. FICOA, #45" ↔ keyword "av ficoa 45").
     *
     * @return El keyword/zona que hizo match, o null si no hay coincidencia
     */
    private fun checkBlacklist(
        evaluated: EvaluatedOffer,
        keywords: List<String>,
        zones: List<BlacklistZoneEntity>
    ): String? {
        val destination = TextNormalizer.normalize(evaluated.offer.destination)
        val pickup = TextNormalizer.normalize(evaluated.offer.pickupAddress)
        val combined = "$destination $pickup".trim()

        // 4. Point-in-polygon: si la oferta trae coordenadas, es la verificación
        // más precisa (no depende del texto). Se ejecuta ANTES que el texto porque
        // no requiere que la dirección exacta coincida con una keyword.
        val coords = listOfNotNull(
            evaluated.offer.destinationLatLng,
            evaluated.offer.pickupLatLng
        )

        if (coords.isNotEmpty()) {
            for (zone in zones) {
                val polygon = zone.polygon
                if (polygon.size < 3) continue

                val inPolygon = coords.any { (lat, lng) ->
                    PolygonGeometry.isPointInPolygon(lat, lng, polygon)
                }

                if (inPolygon) {
                    return "zona '${zone.name}' (punto en polígono)"
                }
            }
        }

        if (combined.isEmpty()) return null

        // 1. Match por keywords de texto (calles, barrios, sectores, manuales y auto-extraídas)
        for (keyword in keywords) {
            val kw = TextNormalizer.normalize(keyword)
            if (kw.isNotEmpty() && combined.contains(kw)) {
                return "keyword '$keyword'"
            }
        }

        // 2. Match por nombres de zonas del mapa
        for (zone in zones) {
            val zn = TextNormalizer.normalize(zone.name)
            if (zn.isNotEmpty() && combined.contains(zn)) {
                return "zona '${zone.name}'"
            }
        }

        // 3. Match por keywords extraídas automáticamente del polígono
        for (zone in zones) {
            for (kw in zone.extractedKeywords) {
                val nkw = TextNormalizer.normalize(kw)
                if (nkw.isNotEmpty() && combined.contains(nkw)) {
                    return "zona '${zone.name}' (keyword '$kw')"
                }
            }
        }

        return null
    }

    /** Formatea double a 2 decimales */
    private fun f(value: Double): String = "%.2f".format(value)
}
