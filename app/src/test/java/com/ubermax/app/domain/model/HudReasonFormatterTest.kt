package com.ubermax.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests JVM puros del formateador del HUD (motivo + abreviación de destino).
 */
class HudReasonFormatterTest {

    private fun evaluatedOffer(destination: String = "Ficoa, Ambato") = EvaluatedOffer(
        offer = OfferData(
            rawFare = 8.0,
            pickupKm = 1.0,
            tripKm = 6.0,
            pickupMinutes = 3,
            tripMinutes = 20,
            estimatedMinutes = 23,
            passengerRating = 4.8,
            destination = destination,
            pickupAddress = "Centro"
        ),
        fuelCost = 1.2,
        netProfit = 5.0,
        profitPerKm = 0.6,
        profitPerHour = 13.0,
        totalKm = 7.0
    )

    private fun decision(action: Action, filters: List<String> = emptyList()) =
        OfferDecision(evaluatedOffer(), action, failedFilters = filters)

    @Test
    fun `accept siempre muestra el motivo de aceptacion`() {
        assertEquals(HudReason.Accepted, HudReasonFormatter.mainReason(decision(Action.ACCEPT)))
    }

    @Test
    fun `cancel siempre muestra lista negra`() {
        assertEquals(HudReason.Blacklist, HudReasonFormatter.mainReason(decision(Action.CANCEL)))
    }

    @Test
    fun `warn con filtro reconocido devuelve el motivo mapeado`() {
        val result = HudReasonFormatter.mainReason(
            decision(Action.WARN, listOf("❌ Tarifa baja"))
        )
        assertEquals(HudReason.Known(DecisionReason.LOW_FARE), result)
    }

    @Test
    fun `warn con filtro desconocido devuelve el texto crudo limpio`() {
        val result = HudReasonFormatter.mainReason(
            decision(Action.WARN, listOf("⚠️ Motivo raro"))
        )
        assertEquals(HudReason.Raw("Motivo raro"), result)
    }

    @Test
    fun `warn sin filtros no muestra motivo`() {
        assertEquals(HudReason.None, HudReasonFormatter.mainReason(decision(Action.WARN)))
    }

    @Test
    fun `abrevia el destino al primer segmento`() {
        assertEquals("Ficoa", HudReasonFormatter.abbreviateDestination("Ficoa, Ambato"))
        assertEquals("Centro", HudReasonFormatter.abbreviateDestination("Centro - Quito"))
    }

    @Test
    fun `recorta destinos largos con elipsis`() {
        val long = "Avenida Muy Larga Del Norte De La Ciudad"
        val result = HudReasonFormatter.abbreviateDestination(long)
        assertEquals(22, result.length)
        assertTrue(result.endsWith("…"))
    }

    @Test
    fun `destino vacio devuelve cadena vacia`() {
        assertEquals("", HudReasonFormatter.abbreviateDestination(""))
        assertEquals("", HudReasonFormatter.abbreviateDestination("   "))
    }

    @Test
    fun `destino corto no se modifica`() {
        assertEquals("Ficoa", HudReasonFormatter.abbreviateDestination("Ficoa"))
    }
}
