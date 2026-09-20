package com.ubermax.app.util

import com.ubermax.app.domain.model.OfferData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Tests JVM puros del fingerprint de ofertas (dedup por identidad).
 *
 * Dos ofertas con la misma tarifa pero distinto destino/pickup NO deben
 * confundirse; la normalización ignora mayúsculas, tildes y símbolos.
 */
class OfferFingerprintTest {

    private fun offer(
        fare: Double = 10.0,
        pickup: String = "Av. Cevallos, Terminal",
        dest: String = "Ficoa, Ambato"
    ) = OfferData(
        rawFare = fare,
        pickupKm = 1.0,
        tripKm = 6.0,
        pickupMinutes = 3,
        tripMinutes = 20,
        estimatedMinutes = 23,
        passengerRating = 4.8,
        destination = dest,
        pickupAddress = pickup
    )

    @Test
    fun `misma tarifa con distinto destino genera fingerprints distintos`() {
        assertNotEquals(
            OfferFingerprint.of(offer(dest = "Ficoa, Ambato")),
            OfferFingerprint.of(offer(dest = "Huachi, Ambato"))
        )
    }

    @Test
    fun `misma tarifa con distinto pickup genera fingerprints distintos`() {
        assertNotEquals(
            OfferFingerprint.of(offer(pickup = "Terminal")),
            OfferFingerprint.of(offer(pickup = "Mall de los Andes"))
        )
    }

    @Test
    fun `misma oferta con distinta tarifa genera fingerprints distintos`() {
        assertNotEquals(
            OfferFingerprint.of(offer(fare = 10.0)),
            OfferFingerprint.of(offer(fare = 10.5))
        )
    }

    @Test
    fun `la oferta identica es estable e ignora mayusculas y tildes`() {
        val a = OfferFingerprint.of(offer(pickup = "AV. CEVALLOS, TERMINAL", dest = "FICOA, AMBATO"))
        val b = OfferFingerprint.of(offer())
        assertEquals(a, b)
    }
}