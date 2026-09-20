package com.ubermax.app.util

import com.ubermax.app.domain.model.OfferData

/**
 * Identidad de una oferta para deduplicar y resolver resultados.
 *
 * Dos ofertas distintas con la misma tarifa ya no se confunden: el fingerprint
 * combina la tarifa con las direcciones normalizadas, de modo que solo se
 * considera "la misma oferta" si coincide tarifa + pickup + destino.
 *
 * 100% JVM puro → testeable con JUnit sin dispositivo.
 */
object OfferFingerprint {

    fun of(offer: OfferData): String {
        val fare = offer.rawFare.toString()
        val pickup = TextNormalizer.normalize(offer.pickupAddress)
        val destination = TextNormalizer.normalize(offer.destination)
        return "$fare|$pickup|$destination"
    }
}