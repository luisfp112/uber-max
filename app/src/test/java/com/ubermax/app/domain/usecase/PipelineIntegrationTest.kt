package com.ubermax.app.domain.usecase

import com.ubermax.app.data.db.entity.FilterRulesEntity
import com.ubermax.app.data.db.entity.VehicleConfigEntity
import com.ubermax.app.domain.model.Action
import com.ubermax.app.domain.model.OfferData
import com.ubermax.app.domain.rules.RuleEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test de integración del pipeline completo (JVM puro):
 *
 *   OfferData → EvaluateOfferUseCase → RuleEngine → OfferDecision final
 *
 *  Sin IA, sin historial, sin zonas económicas: la única métrica es $/km.
 */
class PipelineIntegrationTest {

    private val evaluator = EvaluateOfferUseCase()
    private val ruleEngine = RuleEngine()

    /** Vehículo con costo manual simple para que las cuentas sean predecibles. */
    private val vehicle = VehicleConfigEntity(manualCostPerKm = 0.08, avgSpeedKmh = 25.0)

    /** Oferta rentable típica: 10 USD, 7 km totales → $/km ≈ 1.35. */
    private fun offerData(
        destination: String = "Centro Histórico, Ambato",
        pickupAddress: String = "Av. Cevallos, Terminal",
        fare: Double = 10.0,
        tripKm: Double = 6.0
    ) = OfferData(
        rawFare = fare,
        pickupKm = 1.0,
        tripKm = tripKm,
        pickupMinutes = 3,
        tripMinutes = 20,
        estimatedMinutes = 23,
        passengerRating = 4.8,
        destination = destination,
        pickupAddress = pickupAddress,
        destinationLatLng = null,
        pickupLatLng = null
    )

    // ═══════════════════════════════════════════════════════
    //  1. Oferta rentable → ACCEPT
    // ═══════════════════════════════════════════════════════

    @Test
    fun `pipeline acepta oferta rentable sin filtros fallidos`() {
        val offer = offerData(destination = "Centro Histórico, Ambato")

        val evaluated = evaluator.evaluate(offer, vehicle)
        val decision = ruleEngine.evaluate(evaluated, FilterRulesEntity())

        assertEquals(Action.ACCEPT, decision.action)
        assertTrue(decision.failedFilters.isEmpty())
        assertTrue(decision.evaluatedOffer.profitPerKm > 1.0)
        assertTrue(decision.evaluatedOffer.isProfitable)
    }

    // ═══════════════════════════════════════════════════════
    //  2. Oferta con $/km bajo → WARN
    // ═══════════════════════════════════════════════════════

    @Test
    fun `pipeline advierte cuando la ganancia por km no alcanza el minimo`() {
        val offer = offerData(destination = "Huachi Grande, Ambato", fare = 1.80, tripKm = 7.0)

        val evaluated = evaluator.evaluate(offer, vehicle)
        val decision = ruleEngine.evaluate(
            evaluated,
            FilterRulesEntity(minProfitPerKm = 0.15)
        )

        assertEquals(Action.WARN, decision.action)
        assertTrue(decision.failedFilters.any { it.contains("\$/km bajo") })
        assertTrue(evaluated.profitPerKm < 0.15)
    }

    // ═══════════════════════════════════════════════════════
    //  3. Oferta fuera del perímetro → WARN (rechazo pasivo)
    // ═══════════════════════════════════════════════════════

    @Test
    fun `pipeline advierte si el destino cae fuera de la zona permitida`() {
        val boundary = com.ubermax.app.domain.geo.GeoJsonZones.parse(
            """
            {"type":"Polygon","coordinates":[[[-78.66,-1.26],[-78.59,-1.26],
            [-78.59,-1.22],[-78.66,-1.22],[-78.66,-1.26]]]}
            """.trimIndent()
        )!!
        val offer = offerData(
            destination = "Pelileo, TUNGURAHUA",
            pickupAddress = "Av. Cevallos"
        ).copy(destinationLatLng = -1.3300 to -78.5422)

        val evaluated = evaluator.evaluate(offer, vehicle)
        val decision = ruleEngine.evaluate(evaluated, FilterRulesEntity(), boundary = boundary)

        assertEquals(Action.WARN, decision.action)
        assertTrue(decision.failedFilters.any { it.contains("Zona prohibida") })
    }
}