package com.ubermax.app.util

import com.ubermax.app.domain.model.OfferDecision
import com.ubermax.app.domain.model.ReasonMapper

/**
 * Construye el texto breve que se anuncia por voz (TTS) para una decisión.
 *
 * La app JAMÁS rechaza activamente: solo avisa y deja que el conductor decida.
 * Por eso la voz no dice "viaje rechazado": dice SOLO por qué —frases cortas,
 * una por filtro fallado— para que el conductor conozca con un vistazo
 * (en realidad con un oído) qué no cumplió la oferta y decida si le conviene.
 *
 * Reglas:
 *  - ACCEPT → "Viaje aceptado."
 *  - Recogida o destino NO geolocalizado → "Recogida no ubicada." /
 *    "Destino no ubicado." (el motivo es la falta de ubicación, no un filtro).
 *  - Filtros fallados → una frase breve por filtro (tarifa, $/km, recogida,
 *    recorrido, rating, zona, auto-accept...). Los WARN de "no confirmado"
 *    dentro de la zona ya están cubiertos por las frases de "no ubicada" y no
 *    se repiten.
 *  - Si hubo un WARN pero no quedó ninguna frase, se usa un fallback genérico.
 *
 * 100% puro (sin Context, sin strings de Android) para validarse con JUnit.
 */
object VoiceAlertBuilder {

    const val PHRASE_ACCEPTED = "Viaje aceptado"
    const val PHRASE_PICKUP_NOT_LOCATED = "Recogida no ubicada"
    const val PHRASE_DESTINATION_NOT_LOCATED = "Destino no ubicado"
    const val PHRASE_FALLBACK = "No cumple los filtros"

    /**
     * @param decision decisión del RuleEngine (acción + filtros fallados)
     * @param pickupLocated true si la recogida se geolocalizó con coordenadas
     * @param destinationLocated true si el destino se geolocalizó con coordenadas
     */
    fun build(
        decision: OfferDecision,
        pickupLocated: Boolean = true,
        destinationLocated: Boolean = true
    ): String {
        if (decision.isAccepted) return "$PHRASE_ACCEPTED."

        val phrases = mutableListOf<String>()

        if (!pickupLocated) phrases += PHRASE_PICKUP_NOT_LOCATED
        if (!destinationLocated) phrases += PHRASE_DESTINATION_NOT_LOCATED

        decision.failedFilters.forEach { filter ->
            shortReason(filter)?.let { phrase ->
                if (phrase !in phrases) phrases += phrase
            }
        }

        if (phrases.isEmpty()) phrases += PHRASE_FALLBACK

        return phrases.joinToString(" ") { "$it." }
    }

    /**
     * Traduce el texto un filtro fallado (ej. "🚙 Recogida muy lejos: 5.00min >
     * máx 4.00min") a una frase corta. Devuelve null cuando el filtro no aporta
     * una frase propia (p.ej. los WARN de "no confirmado dentro de la zona",
     * que ya los cubre la bandera de ubicación no localizada).
     */
    internal fun shortReason(filter: String): String? {
        val text = ReasonMapper.clean(filter)
        if (text.isEmpty()) return null
        return when {
            // Ya anunciado por pickupLocated/destinationLocated: no repetir.
            text.contains("no confirmad", ignoreCase = true) -> null
            text.contains("tarifa baja", ignoreCase = true) -> "Tarifa baja"
            text.contains("\$/km bajo", ignoreCase = true) -> "Precio por kilómetro bajo"
            text.contains("recogida muy lejos", ignoreCase = true) -> "Recogida muy lejos"
            text.contains("pickup lejos", ignoreCase = true) -> "Recogida lejana"
            text.contains("recorrido excede", ignoreCase = true) -> "Recorrido muy largo"
            text.contains("tiempo largo", ignoreCase = true) ||
                text.contains("viaje largo", ignoreCase = true) -> "Viaje muy largo"
            text.contains("rating", ignoreCase = true) -> "Rating bajo"
            text.contains("zona permitida", ignoreCase = true) ||
                text.contains("perímetro urbano", ignoreCase = true) ||
                text.contains("perimetro", ignoreCase = true) -> "Fuera de la zona permitida"
            text.contains("auto-accept", ignoreCase = true) -> "Auto-aceptación desactivada"
            else -> null
        }
    }
}