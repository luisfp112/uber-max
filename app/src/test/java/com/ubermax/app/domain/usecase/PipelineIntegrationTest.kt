package com.ubermax.app.domain.usecase

import com.ubermax.app.data.db.entity.BlacklistZoneEntity
import com.ubermax.app.data.db.entity.FilterRulesEntity
import com.ubermax.app.data.db.entity.TripLogEntity
import com.ubermax.app.data.db.entity.VehicleConfigEntity
import com.ubermax.app.domain.ai.SmartAdvisor
import com.ubermax.app.domain.geometry.PolygonGeometry
import com.ubermax.app.domain.model.Action
import com.ubermax.app.domain.model.OfferData
import com.ubermax.app.domain.port.TripHistorySource
import com.ubermax.app.domain.rules.RuleEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test de integración del pipeline completo (JVM puro):
 *
 *   OfferData → EvaluateOfferUseCase → RuleEngine → SmartAdvisor → OfferDecision final
 *
 * La capa de datos se simula con un [FakeHistory] (puerto TripHistorySource),
 * igual que en SmartAdvisorTest: sin Mockito, sin dispositivo.
 */
class PipelineIntegrationTest {

    /** Fake del puerto de historial usado por el SmartAdvisor. */
    private class FakeHistory(var results: Flow<List<TripLogEntity>>? = null, var error: Throwable? = null) :
        TripHistorySource {
        override fun getAllTripsFlow(): Flow<List<TripLogEntity>> =
            error?.let { flow<List<TripLogEntity>> { throw it } } ?: results ?: flowOf(emptyList())
    }

    private val evaluator = EvaluateOfferUseCase()
    private val ruleEngine = RuleEngine()

    /** Vehículo con costo manual simple para que las cuentas sean predecibles. */
    private val vehicle = VehicleConfigEntity(manualCostPerKm = 0.08)

    /** Zona poligonal "Ficoa" sobre Ambato (calibración ecuatorial). */
    private val ficoaZone = BlacklistZoneEntity(
        name = "Ficoa",
        polygonJson = PolygonGeometry.serializePolygon(
            PolygonGeometry.circleToSquarePolygon(-1.2417, -78.6197, radiusMeters = 1000.0)
        )
    )

    private fun tripLog(destination: String, profitPerKm: Double, decision: String = "ACCEPT") = TripLogEntity(
        dayOfWeek = "domingo",
        hourOfDay = 14,
        rawFare = 10.0,
        pickupKm = 1.0,
        tripKm = 6.0,
        estimatedMinutes = 20,
        passengerRating = 4.8,
        destination = destination,
        pickupAddress = "Av. Cevallos",
        fuelCost = 1.0,
        netProfit = 6.0,
        profitPerKm = profitPerKm,
        profitPerHour = 12.0,
        totalKm = 7.0,
        decision = decision
    )

    /** Oferta rentable típica: 10 USD, 7 km totales → pasa todos los filtros. */
    private fun offerData(
        destination: String = "Centro Histórico, Ambato",
        pickupAddress: String = "Av. Cevallos, Terminal",
        destinationLatLng: Pair<Double, Double>? = null
    ) = OfferData(
        rawFare = 10.0,
        pickupKm = 1.0,
        tripKm = 6.0,
        pickupMinutes = 3,
        tripMinutes = 20,
        estimatedMinutes = 23,
        passengerRating = 4.8,
        destination = destination,
        pickupAddress = pickupAddress,
        destinationLatLng = destinationLatLng,
        pickupLatLng = null
    )

    // ═══════════════════════════════════════════════════════
    //  1. Point-in-polygon: CANCEL sin depender del texto
    // ═══════════════════════════════════════════════════════

    @Test
    fun `pipeline cancela oferta por coordenas dentro del poligono`() = runTest {
        // Destino propagandístico inocente PERO con GPS dentro de la zona Ficoa
        val offer = offerData(
            destination = "Parque Industrial, Llano Grande",
            destinationLatLng = -1.2440 to -78.6170
        )

        val evaluated = evaluator.evaluate(offer, vehicle)
        val decision = ruleEngine.evaluate(
            evaluated,
            FilterRulesEntity(),
            blacklistKeywords = emptyList(),
            blacklistZones = listOf(ficoaZone)
        )
        val finalDecision = SmartAdvisor(FakeHistory(results = flowOf(emptyList())))
            .analyzeOffer(decision)

        assertEquals(Action.CANCEL, finalDecision.action)
        assertTrue(finalDecision.failedFilters[0].contains("punto en polígono"))
        assertEquals(1.0, finalDecision.aiConfidence, 0.001)
        assertTrue(finalDecision.aiRecommendation.contains("confirmado"))
    }

    // ═══════════════════════════════════════════════════════
    //  2. Keyword de texto: CANCEL cuando no hay GPS
    // ═══════════════════════════════════════════════════════

    @Test
    fun `pipeline cancela por keyword de texto sin coordenadas`() = runTest {
        val offer = offerData(destination = "Av. FICOA #45 y Chile")

        val evaluated = evaluator.evaluate(offer, vehicle)
        val decision = ruleEngine.evaluate(
            evaluated,
            FilterRulesEntity(),
            blacklistKeywords = listOf("Av. Ficoa"),
            blacklistZones = emptyList()
        )
        val finalDecision = SmartAdvisor(FakeHistory(results = flowOf(emptyList())))
            .analyzeOffer(decision)

        assertEquals(Action.CANCEL, finalDecision.action)
        assertTrue(finalDecision.failedFilters[0].contains("keyword 'Av. Ficoa'"))
        assertEquals(1.0, finalDecision.aiConfidence, 0.001)
    }

    // ═══════════════════════════════════════════════════════
    //  3. Oferta rentable fuera de negra → ACCEPT + IA refuerza
    // ═══════════════════════════════════════════════════════

    @Test
    fun `pipeline acepta oferta rentable y la IA la refuerza`() = runTest {
        val offer = offerData(destination = "Centro Histórico, Ambato")

        val evaluated = evaluator.evaluate(offer, vehicle)
        val decision = ruleEngine.evaluate(
            evaluated,
            FilterRulesEntity(),
            blacklistKeywords = emptyList(),
            blacklistZones = emptyList()
        )
        val history = FakeHistory(
            results = flowOf(
                listOf(
                    tripLog("Centro Histórico, Ambato", profitPerKm = 0.6),
                    tripLog("Centro Histórico, Ambato", profitPerKm = 0.65)
                )
            )
        )
        val finalDecision = SmartAdvisor(history).analyzeOffer(decision)

        assertEquals(Action.ACCEPT, finalDecision.action)
        assertTrue(finalDecision.aiRecommendation.contains("Buena elección"))
        assertEquals(0.9, finalDecision.aiConfidence, 0.001)
    }

    // ═══════════════════════════════════════════════════════
    //  4. Historial vacío → IA prudente (baja confianza)
    // ═══════════════════════════════════════════════════════

    @Test
    fun `pipeline con historial vacio baja la confianza de la IA`() = runTest {
        val offer = offerData(destination = "Centro Histórico, Ambato")

        val evaluated = evaluator.evaluate(offer, vehicle)
        val decision = ruleEngine.evaluate(
            evaluated,
            FilterRulesEntity(),
            blacklistKeywords = emptyList(),
            blacklistZones = emptyList()
        )
        val finalDecision = SmartAdvisor(FakeHistory(results = flowOf(emptyList())))
            .analyzeOffer(decision)

        assertEquals(Action.ACCEPT, finalDecision.action)
        assertTrue(finalDecision.aiRecommendation.contains("dato"))
        assertEquals(0.1, finalDecision.aiConfidence, 0.001)
    }
}