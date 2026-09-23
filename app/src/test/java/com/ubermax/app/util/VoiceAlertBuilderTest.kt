package com.ubermax.app.util

import com.ubermax.app.data.db.entity.VehicleConfigEntity
import com.ubermax.app.domain.model.Action
import com.ubermax.app.domain.model.EvaluatedOffer
import com.ubermax.app.domain.model.OfferData
import com.ubermax.app.domain.model.OfferDecision
import com.ubermax.app.domain.usecase.EvaluateOfferUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests JVM puros de las frases breves por voz ([VoiceAlertBuilder]): una por
 * filtro fallado, ubicación no localizada aparte, y nunca la frase genérica
 * "viaje rechazado" (la app avisa y el conductor decide).
 */
class VoiceAlertBuilderTest {

    private val evaluator = EvaluateOfferUseCase()

    private fun offer(
        fare: Double = 10.0,
        pickupKm: Double = 1.0,
        tripKm: Double = 6.0,
        tripMinutes: Int = 20,
        rating: Double = 4.8
    ) = OfferData(
        rawFare = fare,
        pickupKm = pickupKm,
        tripKm = tripKm,
        pickupMinutes = 3,
        tripMinutes = tripMinutes,
        estimatedMinutes = tripMinutes,
        passengerRating = rating,
        destination = "Centro, Ambato",
        pickupAddress = "Centro"
    )

    private fun decision(
        failedFilters: List<String>,
        action: Action = Action.WARN
    ): OfferDecision {
        val evaluated = evaluator.evaluate(offer(), VehicleConfigEntity(manualCostPerKm = 0.08))
        return OfferDecision(evaluatedOffer = evaluated, action = action, failedFilters = failedFilters)
    }

    @Test
    fun `accepted dice solo viaje aceptado`() {
        val text = VoiceAlertBuilder.build(decision(emptyList(), Action.ACCEPT))
        assertEquals("Viaje aceptado.", text)
    }

    @Test
    fun `recogida no ubicada reemplaza a viaje rechazado`() {
        val text = VoiceAlertBuilder.build(
            decision(listOf("🚙 Recogida no confirmada dentro de la zona permitida")),
            pickupLocated = false
        )
        assertEquals("Recogida no ubicada.", text)
        assertFalse(text.contains("rechazado", ignoreCase = true))
    }

    @Test
    fun `destino no ubicado se anuncia por separado`() {
        val text = VoiceAlertBuilder.build(
            decision(listOf("📍 Destino no confirmado dentro de la zona permitida")),
            destinationLocated = false
        )
        assertEquals("Destino no ubicado.", text)
        assertFalse(text.contains("rechazado", ignoreCase = true))
    }

    @Test
    fun `recogida y destino no ubicados se anuncian juntos`() {
        val text = VoiceAlertBuilder.build(
            decision(listOf("🚙 Recogida no confirmada dentro de la zona permitida")),
            pickupLocated = false,
            destinationLocated = false
        )
        assertEquals("Recogida no ubicada. Destino no ubicado.", text)
    }

    @Test
    fun `no confirmado no se repite como fuera de zona`() {
        val text = VoiceAlertBuilder.build(
            decision(listOf("🚙 Recogida no confirmada dentro de la zona permitida")),
            pickupLocated = false
        )
        assertFalse(text.contains("zona", ignoreCase = true))
    }

    @Test
    fun `filtro de tarifa baja tiene su frase corta`() {
        val text = VoiceAlertBuilder.build(
            decision(listOf("💰 Tarifa baja: \$5.00 < mín \$8.00"))
        )
        assertEquals("Tarifa baja.", text)
    }

    @Test
    fun `varios filtros fallados cada uno con su frase`() {
        val text = VoiceAlertBuilder.build(
            decision(
                listOf(
                    "💰 Tarifa baja: \$5.00 < mín \$8.00",
                    "🚙 Recogida muy lejos: 6.00min > máx 5.00min",
                    "🛣️ Recorrido excede el máximo: 30.00min > máx 20.00min"
                )
            )
        )
        assertTrue(text.contains("Tarifa baja."))
        assertTrue(text.contains("Recogida muy lejos."))
        assertTrue(text.contains("Recorrido muy largo."))
        assertFalse(text.contains("rechazado", ignoreCase = true))
    }

    @Test
    fun `ubicacion no localizada mas filtro fallado se combinan`() {
        val text = VoiceAlertBuilder.build(
            decision(listOf("💰 Tarifa baja: \$5.00 < mín \$8.00")),
            pickupLocated = false
        )
        assertEquals("Recogida no ubicada. Tarifa baja.", text)
    }

    @Test
    fun `fuera de la zona permitida tiene su frase`() {
        val text = VoiceAlertBuilder.build(
            decision(listOf("Zona prohibida — fuera del perímetro urbano"))
        )
        assertEquals("Fuera de la zona permitida.", text)
    }

    @Test
    fun `profit por km bajo tiene su frase`() {
        val text = VoiceAlertBuilder.build(
            decision(listOf("📏 \$/km bajo: \$0.10 < mín \$0.30"))
        )
        assertEquals("Precio por kilómetro bajo.", text)
    }

    @Test
    fun `rating bajo tiene su frase`() {
        val text = VoiceAlertBuilder.build(
            decision(listOf("⭐ Rating bajo: 4.10 < mín 4.50"))
        )
        assertEquals("Rating bajo.", text)
    }

    @Test
    fun `auto accept desactivado tiene su frase`() {
        val text = VoiceAlertBuilder.build(
            decision(listOf("ℹ️ Auto-accept desactivado"))
        )
        assertEquals("Auto-aceptación desactivada.", text)
    }

    @Test
    fun `warn sin filtros reconocibles usa el fallback generico`() {
        val text = VoiceAlertBuilder.build(
            decision(listOf("ℹ️ Algo inesperado ocurrió"))
        )
        assertEquals("No cumple los filtros.", text)
    }

    @Test
    fun `frases duplicadas se anuncian una sola vez`() {
        val text = VoiceAlertBuilder.build(
            decision(
                listOf(
                    "💰 Tarifa baja: \$5.00 < mín \$8.00",
                    "💰 Tarifa baja: \$4.00 < mín \$8.00"
                )
            )
        )
        assertEquals("Tarifa baja.", text)
    }
}