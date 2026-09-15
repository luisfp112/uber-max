package com.ubermax.app.domain.rules

import com.ubermax.app.data.db.entity.BlacklistEntryEntity
import com.ubermax.app.data.db.entity.FilterRulesEntity
import com.ubermax.app.data.db.entity.VehicleConfigEntity
import com.ubermax.app.domain.model.Action
import com.ubermax.app.domain.model.EvaluatedOffer
import com.ubermax.app.domain.model.OfferData
import com.ubermax.app.domain.usecase.EvaluateOfferUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests JVM puros del RuleEngine.
 *
 * Cubre:
 *  - Deadhead: un viaje largo (> 8 km) NO se marca como fallido por la distancia,
 *    solo si la rentabilidad final (con vuelta vacía) cae bajo los mínimos del usuario.
 *  - Lista negra: coincidencia robusta contra el texto real de recogida/destino usando
 *    normalización (mayúsculas, tildes y caracteres especiales).
 *  - Filtros económicos y modo de operación (ACCEPT / WARN / CANCEL).
 */
class RuleEngineTest {

    private val evaluator = EvaluateOfferUseCase()
    private val ruleEngine = RuleEngine()
    private val vehicle = VehicleConfigEntity() // costPerKm = $0.08 USD

    /** Construye una oferta rentable a 10 USD con viaje corto (sin deadhead). */
    private fun evaluatedOffer(
        fare: Double = 10.00,
        pickupKm: Double = 1.0,
        tripKm: Double = 6.0,
        pickupMinutes: Int = 3,
        tripMinutes: Int = 20,
        estimatedMinutes: Int = 23,
        rating: Double = 4.8,
        destination: String = "Centro Histórico, AMBATO",
        pickupAddress: String = "Av. Cevallos, Terminal"
    ): EvaluatedOffer = evaluator.evaluate(
        OfferData(
            rawFare = fare,
            pickupKm = pickupKm,
            tripKm = tripKm,
            pickupMinutes = pickupMinutes,
            tripMinutes = tripMinutes,
            estimatedMinutes = estimatedMinutes,
            passengerRating = rating,
            destination = destination,
            pickupAddress = pickupAddress
        ),
        vehicle
    )

    // ═══════════════════════════════════════════════════════
    //  DECISIÓN BASE — ACCEPT / WARN
    // ═══════════════════════════════════════════════════════

    @Test
    fun `oferta corta rentable se ACEPTA automaticamente`() {
        val decision = ruleEngine.evaluate(evaluatedOffer(), FilterRulesEntity())

        assertEquals(Action.ACCEPT, decision.action)
        assertTrue(decision.isAccepted)
        assertTrue(decision.failedFilters.isEmpty())
    }

    @Test
    fun `tarifa baja genera WARN con filtro de tarifa`() {
        val decision = ruleEngine.evaluate(
            evaluatedOffer(fare = 1.20, tripKm = 5.0),
            FilterRulesEntity()
        )

        assertEquals(Action.WARN, decision.action)
        assertTrue(decision.failedFilters.any { it.contains("Tarifa baja") })
    }

    @Test
    fun `rating bajo del pasajero genera WARN`() {
        val decision = ruleEngine.evaluate(
            evaluatedOffer(rating = 3.5),
            FilterRulesEntity()
        )

        assertEquals(Action.WARN, decision.action)
        assertTrue(decision.failedFilters.any { it.contains("Rating bajo") })
    }

    @Test
    fun `con auto-aceptar desactivado una oferta buena pasa a WARN informativo`() {
        val decision = ruleEngine.evaluate(
            evaluatedOffer(),
            FilterRulesEntity(autoAcceptEnabled = false)
        )

        assertEquals(Action.WARN, decision.action)
        assertTrue(decision.failedFilters.any { it.contains("Auto-accept") })
    }

    // ═══════════════════════════════════════════════════════
    //  DEADHEAD — NO marcar por distancia, SÍ por rentabilidad
    // ═══════════════════════════════════════════════════════

    @Test
    fun `viaje largo mayor a 8km pero rentable NO se marca fallido por deadhead`() {
        val evaluated = evaluatedOffer(
            fare = 10.00,   // neto $8.0, $0.32/km, $7.62/h con retorno
            pickupKm = 1.0,
            tripKm = 12.0,
            pickupMinutes = 3,
            tripMinutes = 30,
            estimatedMinutes = 33
        )
        assertTrue(evaluated.hasDeadheadPenalty) // trip > 8km

        val decision = ruleEngine.evaluate(evaluated, FilterRulesEntity())

        assertEquals(Action.ACCEPT, decision.action)
        assertFalse(decision.failedFilters.any { it.contains("Vuelta vacía") })
    }

    @Test
    fun `viaje largo pero rentable mantiene metrics con deadhead incluidas en totalKm`() {
        val evaluated = evaluatedOffer(
            fare = 10.00,
            pickupKm = 1.0,
            tripKm = 12.0,
            pickupMinutes = 3,
            tripMinutes = 30,
            estimatedMinutes = 33
        )

        assertEquals(12.0, evaluated.returnKm, 0.001)
        assertEquals(25.0, evaluated.totalKm, 0.001) // 1 + 12 + 12 retorno
        assertEquals(8.0, evaluated.netProfit, 0.001) // 10 - (25 × 0.08)
        assertTrue(evaluated.netProfit > FilterRulesEntity().minNetProfit)
        assertTrue(evaluated.profitPerKm > FilterRulesEntity().minProfitPerKm)
    }

    @Test
    fun `viaje largo cuyo deadhead hunde la rentabilidad final genera WARN con vuelta vacia`() {
        // Sin deadhead: neto $2.0, $0.20/km, $5.2/h → ACCEPT.
        // Con deadhead (9km de retorno): neto $1.28, $0.067/km, $1.79/h → bajo mínimos.
        val evaluated = evaluatedOffer(
            fare = 2.80,
            pickupKm = 1.0,
            tripKm = 9.0,
            pickupMinutes = 3,
            tripMinutes = 20,
            estimatedMinutes = 23
        )
        assertTrue(evaluated.hasDeadheadPenalty)
        assertTrue(evaluated.netProfit > FilterRulesEntity().minNetProfit)
        assertTrue(evaluated.profitPerKm < FilterRulesEntity().minProfitPerKm)

        val decision = ruleEngine.evaluate(evaluated, FilterRulesEntity())

        assertEquals(Action.WARN, decision.action)
        assertTrue(decision.failedFilters.any { it.contains("Vuelta vacía") })
        assertTrue(decision.failedFilters.any { it.contains("\$/km bajo") })
    }

    @Test
    fun `viaje largo que queda en perdida neta con deadhead genera WARN por ganancia neta`() {
        val evaluated = evaluatedOffer(
            fare = 3.00,
            pickupKm = 1.0,
            tripKm = 20.0,
            pickupMinutes = 3,
            tripMinutes = 40,
            estimatedMinutes = 43
        )
        assertTrue(evaluated.hasDeadheadPenalty)
        assertTrue(evaluated.netProfit < 0.0) // 3.00 - (41 × 0.08) = -0.28

        val decision = ruleEngine.evaluate(evaluated, FilterRulesEntity())

        assertEquals(Action.WARN, decision.action)
        assertTrue(decision.failedFilters.any { it.contains("Vuelta vacía") })
        assertTrue(decision.failedFilters.any { it.contains("Ganancia neta baja") })
    }

    @Test
    fun `deadhead cero valores configurables no anade filtro falso`() {
        // Sin filtros económicos activos, un viaje largo no debe reportar vuelta vacía.
        val rules = FilterRulesEntity(
            minNetProfit = 0.0,
            minProfitPerKm = 0.0,
            minProfitPerHour = 0.0
        )
        val evaluated = evaluatedOffer(
            fare = 3.00,
            pickupKm = 1.0,
            tripKm = 20.0,
            pickupMinutes = 3,
            tripMinutes = 40,
            estimatedMinutes = 43
        )
        assertTrue(evaluated.hasDeadheadPenalty)

        val decision = ruleEngine.evaluate(evaluated, rules)

        assertFalse(decision.failedFilters.any { it.contains("Vuelta vacía") })
    }

    // ═══════════════════════════════════════════════════════
    //  LISTA NEGRA — texto normalizado (mayúsculas, tildes, símbolos)
    // ═══════════════════════════════════════════════════════

    @Test
    fun `keyword con mayusculas tildes y caracteres especiales dispara CANCEL`() {
        val decision = ruleEngine.evaluate(
            evaluatedOffer(destination = "AV. FICOA, #45 - AMBATO"),
            FilterRulesEntity(),
            blacklistKeywords = listOf("Av. Ficoa, #45")
        )

        assertEquals(Action.CANCEL, decision.action)
        assertTrue(decision.failedFilters.any { it.contains("LISTA NEGRA") })
    }

    @Test
    fun `keyword de sector con tilde dispara CANCEL aunque Uber escriba distinto`() {
        val decision = ruleEngine.evaluate(
            evaluatedOffer(destination = "SECTOR HUACHÍ GRANDE, AMBATO"),
            FilterRulesEntity(),
            blacklistKeywords = listOf("huachi grande")
        )

        assertEquals(Action.CANCEL, decision.action)
    }

    @Test
    fun `keyword de calle coincide contra el texto real de la tarjeta de Uber`() {
        val decision = ruleEngine.evaluate(
            evaluatedOffer(
                destination = "C. L-7, CIUDAD MERLOT - SAN SALVADOR",
                pickupAddress = "Parque Central, AMBATO"
            ),
            FilterRulesEntity(),
            blacklistKeywords = listOf("C. L-7")
        )

        assertEquals(Action.CANCEL, decision.action)
    }

    @Test
    fun `el match por zona del mapa tambien es robusto a tildes`() {
        val decision = ruleEngine.evaluate(
            evaluatedOffer(destination = "Col. Escalón, SAN SALVADOR"),
            FilterRulesEntity(),
            blacklistKeywords = emptyList(),
            blacklistZones = listOf("Escalón")
        )

        assertEquals(Action.CANCEL, decision.action)
    }

    @Test
    fun `sin coincidencia en lista negra la oferta se evalua normal`() {
        val decision = ruleEngine.evaluate(
            evaluatedOffer(destination = "Centro Histórico, AMBATO"),
            FilterRulesEntity(),
            blacklistKeywords = listOf("peligro")
        )

        assertEquals(Action.ACCEPT, decision.action)
    }

    @Test
    fun `con lista negra desactivada no se cancela aunque el texto coincida`() {
        val rules = FilterRulesEntity(blacklistEnabled = false)
        val decision = ruleEngine.evaluate(
            evaluatedOffer(destination = "Av. Peligro, Sector Rojo"),
            rules,
            blacklistKeywords = listOf("peligro")
        )

        assertEquals(Action.ACCEPT, decision.action)
        assertFalse(decision.isCancelled)
    }

    @Test
    fun `la entidad de lista negra expone su keyword normalizado`() {
        val entry = BlacklistEntryEntity(keyword = "AV. FICOA, #45")
        assertEquals("av ficoa 45", entry.normalizedKeyword)

        val entryAccents = BlacklistEntryEntity(keyword = "Techo Propio")
        assertEquals("techo propio", entryAccents.normalizedKeyword)
    }
}