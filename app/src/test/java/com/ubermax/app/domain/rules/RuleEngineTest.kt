package com.ubermax.app.domain.rules

import com.ubermax.app.data.db.entity.BlacklistEntryEntity
import com.ubermax.app.data.db.entity.BlacklistZoneEntity
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
    private val vehicle = VehicleConfigEntity(
        manualCostPerKm = 0.08, // costPerKm manual = $0.08 USD
        platformCommissionPercent = 0.0 // comisión: 0 para validar deadhead con cuentas exactas
    )

    /** Construye una oferta rentable a 10 USD con viaje corto (sin deadhead). */
    private fun evaluatedOffer(
        fare: Double = 10.00,
        pickupKm: Double = 1.0,
        tripKm: Double = 6.0,
        pickupMinutes: Int = 3,
        tripMinutes: Int = 20,
        estimatedMinutes: Int = 23,
        rating: Double = 4.8,
        hasRating: Boolean = true,
        destination: String = "Centro Histórico, AMBATO",
        pickupAddress: String = "Av. Cevallos, Terminal",
        deadheadReturnFactor: Double = 0.0,
        deadheadThresholdKm: Double = 8.0
    ): EvaluatedOffer = evaluator.evaluate(
        OfferData(
            rawFare = fare,
            pickupKm = pickupKm,
            tripKm = tripKm,
            pickupMinutes = pickupMinutes,
            tripMinutes = tripMinutes,
            estimatedMinutes = estimatedMinutes,
            passengerRating = rating,
            hasRating = hasRating,
            destination = destination,
            pickupAddress = pickupAddress
        ),
        vehicle,
        deadheadThresholdKm = deadheadThresholdKm,
        deadheadReturnFactor = deadheadReturnFactor
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
            FilterRulesEntity(minFare = 1.50)
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
    fun `rating desconocido sin toggle no marca fallido por un dato que no se pudo leer`() {
        val decision = ruleEngine.evaluate(
            evaluatedOffer(rating = 0.0, hasRating = false),
            FilterRulesEntity(minPassengerRating = 4.5)
        )

        assertEquals(Action.ACCEPT, decision.action)
        assertFalse(decision.failedFilters.any { it.contains("Rating") })
    }

    @Test
    fun `rating desconocido con toggle activado genera WARN explicito`() {
        val decision = ruleEngine.evaluate(
            evaluatedOffer(rating = 0.0, hasRating = false),
            FilterRulesEntity(minPassengerRating = 4.5, rejectOnUnknownRating = true)
        )

        assertEquals(Action.WARN, decision.action)
        assertTrue(decision.failedFilters.any { it.contains("Rating desconocido") })
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
        // factor 1.0 → vuelta vacía con rampa: 12km * ((12-8)/8) = 6km de retorno.
        // Falta neta $8.48, $0.45/km → sigue rentable.
        val evaluated = evaluatedOffer(
            fare = 10.00,
            pickupKm = 1.0,
            tripKm = 12.0,
            pickupMinutes = 3,
            tripMinutes = 30,
            estimatedMinutes = 33,
            deadheadReturnFactor = 1.0
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
            estimatedMinutes = 33,
            deadheadReturnFactor = 1.0
        )

        assertEquals(6.0, evaluated.returnKm, 0.001)
        assertEquals(19.0, evaluated.totalKm, 0.001) // 1 + 12 + 6 retorno
        assertEquals(8.48, evaluated.netProfit, 0.001) // 10 - (19 × 0.08)
        assertTrue(evaluated.netProfit > FilterRulesEntity().minNetProfit)
        assertTrue(evaluated.profitPerKm > FilterRulesEntity().minProfitPerKm)
    }

    @Test
    fun `viaje largo cuyo deadhead hunde la rentabilidad final genera WARN con vuelta vacia`() {
        // Sin deadhead: neto $2.56, $0.16/km → bordea el mínimo.
        // Con deadhead (13.1km de retorno): neto $0.67, $0.023/km → bajo mínimos.
        val evaluated = evaluatedOffer(
            fare = 3.00,
            pickupKm = 1.0,
            tripKm = 15.0,
            pickupMinutes = 3,
            tripMinutes = 20,
            estimatedMinutes = 23,
            deadheadReturnFactor = 1.0
        )
        assertTrue(evaluated.hasDeadheadPenalty)
        assertTrue(evaluated.netProfit > FilterRulesEntity().minNetProfit)
        assertTrue(evaluated.profitPerKm < FilterRulesEntity().minProfitPerKm)

        val decision = ruleEngine.evaluate(evaluated, FilterRulesEntity(primaryMetric = "PER_KM"))

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
            estimatedMinutes = 43,
            deadheadReturnFactor = 1.0
        )
        assertTrue(evaluated.hasDeadheadPenalty)
        assertTrue(evaluated.netProfit < 0.0) // 3.00 - (41 × 0.08) = -0.28

        val decision = ruleEngine.evaluate(evaluated, FilterRulesEntity(minNetProfit = 0.80))

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
            estimatedMinutes = 43,
            deadheadReturnFactor = 1.0
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
        // Polígono de la zona "Escalón" (San Salvador)
        val escalonZone = BlacklistZoneEntity(
            name = "Escalón",
            polygonJson = """[[13.6900,-89.1900],[13.7000,-89.1900],[13.7000,-89.1800],[13.6900,-89.1800]]"""
        )

        val decision = ruleEngine.evaluate(
            evaluatedOffer(destination = "Col. Escalón, SAN SALVADOR"),
            FilterRulesEntity(),
            blacklistKeywords = emptyList(),
            blacklistZones = listOf(escalonZone)
        )

        assertEquals(Action.CANCEL, decision.action)
    }

    @Test
    fun `punto en poligono cancela la oferta aunque el texto no coincida`() {
        // Polígono alrededor de "Ficoa" (Ambato): noroeste/sureste
        val ficoaZone = BlacklistZoneEntity(
            name = "Ficoa",
            polygonJson = """[[-1.2300,-78.6200],[-1.2300,-78.6100],[-1.2400,-78.6100],[-1.2400,-78.6200]]"""
        )

        // Destino con TEXTO no coincidente pero COORDENADA dentro del polígono
        val decision = ruleEngine.evaluate(
            evaluator.evaluate(
                OfferData(
                    rawFare = 8.0,
                    pickupKm = 1.0,
                    tripKm = 5.0,
                    pickupMinutes = 3,
                    tripMinutes = 15,
                    estimatedMinutes = 18,
                    passengerRating = 4.8,
                    destination = "Dirección genérica sin coincidencia",
                    pickupAddress = "Otro lugar normal",
                    destinationLatLng = (-1.2350 to -78.6150) // centro de Ficoa
                ),
                vehicle
            ),
            FilterRulesEntity(),
            blacklistKeywords = emptyList(),
            blacklistZones = listOf(ficoaZone)
        )

        assertEquals(Action.CANCEL, decision.action)
    }

    @Test
    fun `punto fuera del poligono no cancela`() {
        val ficoaZone = BlacklistZoneEntity(
            name = "Ficoa",
            polygonJson = """[[-1.2300,-78.6200],[-1.2300,-78.6100],[-1.2400,-78.6100],[-1.2400,-78.6200]]"""
        )

        val decision = ruleEngine.evaluate(
            evaluator.evaluate(
                OfferData(
                    rawFare = 8.0,
                    pickupKm = 1.0,
                    tripKm = 5.0,
                    pickupMinutes = 3,
                    tripMinutes = 15,
                    estimatedMinutes = 18,
                    passengerRating = 4.8,
                    destination = "Dirección genérica",
                    pickupAddress = "Otro lugar normal",
                    destinationLatLng = (-1.2500 to -78.6300) // fuera del polígono
                ),
                vehicle
            ),
            FilterRulesEntity(),
            blacklistKeywords = emptyList(),
            blacklistZones = listOf(ficoaZone)
        )

        assertEquals(Action.ACCEPT, decision.action)
    }

    @Test
    fun `keyword extraida del poligono cancela la oferta por texto`() {
        val zone = BlacklistZoneEntity(
            name = "Huachi",
            polygonJson = """[[-1.2500,-78.6300],[-1.2400,-78.6300],[-1.2400,-78.6200],[-1.2500,-78.6200]]""",
            extractedKeywordsJson = """["Av. Huachi","Barrio Huachi Grande"]"""
        )

        val decision = ruleEngine.evaluate(
            evaluatedOffer(destination = "Av. HUACHI, Sector Norte"),
            FilterRulesEntity(),
            blacklistKeywords = emptyList(),
            blacklistZones = listOf(zone)
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

    // ═══════════════════════════════════════════════════════
    //  MÉTRICA PRINCIPAL — solo la elegida decide
    // ═══════════════════════════════════════════════════════

    @Test
    fun `modo por hora ignora el minimo por km`() {
        // $/km = 0.145 (< 0.15) pero $/hr ≈ 6.96 (> 3.0): en modo hora debe ACEPTAR.
        val evaluated = evaluatedOffer(
            fare = 1.80,
            pickupKm = 1.0,
            tripKm = 7.0,
            pickupMinutes = 2,
            tripMinutes = 8,
            estimatedMinutes = 10
        )
        assertTrue(evaluated.profitPerKm < 0.15)
        assertTrue(evaluated.profitPerHour > 3.0)

        val decision = ruleEngine.evaluate(evaluated, FilterRulesEntity(primaryMetric = "PER_HOUR"))

        assertEquals(Action.ACCEPT, decision.action)
        assertFalse(decision.failedFilters.any { it.contains("\$/km bajo") })
    }

    @Test
    fun `modo por hora rechaza si baja la ganancia por hora`() {
        // $/km = 0.42 (bueno) pero $/hr = 1.68 (< 3.0): en modo hora debe WARN.
        val evaluated = evaluatedOffer(
            fare = 2.00,
            pickupKm = 1.0,
            tripKm = 3.0,
            pickupMinutes = 5,
            tripMinutes = 55,
            estimatedMinutes = 60
        )
        assertTrue(evaluated.profitPerKm > 0.15)
        assertTrue(evaluated.profitPerHour < 3.0)

        val decision = ruleEngine.evaluate(evaluated, FilterRulesEntity(primaryMetric = "PER_HOUR"))

        assertEquals(Action.WARN, decision.action)
        assertTrue(decision.failedFilters.any { it.contains("\$/hr bajo") })
    }

    @Test
    fun `modo por km ignora el minimo por hora`() {
        // Mismo viaje anterior: $/km bueno y $/hr bajo → en modo km debe ACEPTAR.
        val evaluated = evaluatedOffer(
            fare = 2.00,
            pickupKm = 1.0,
            tripKm = 3.0,
            pickupMinutes = 5,
            tripMinutes = 55,
            estimatedMinutes = 60
        )

        val decision = ruleEngine.evaluate(evaluated, FilterRulesEntity(primaryMetric = "PER_KM"))

        assertEquals(Action.ACCEPT, decision.action)
        assertFalse(decision.failedFilters.any { it.contains("\$/hr bajo") })
    }

    @Test
    fun `modo por km rechaza si baja la ganancia por km`() {
        val evaluated = evaluatedOffer(
            fare = 1.80,
            pickupKm = 1.0,
            tripKm = 7.0,
            pickupMinutes = 2,
            tripMinutes = 8,
            estimatedMinutes = 10
        )

        val decision = ruleEngine.evaluate(evaluated, FilterRulesEntity(primaryMetric = "PER_KM"))

        assertEquals(Action.WARN, decision.action)
        assertTrue(decision.failedFilters.any { it.contains("\$/km bajo") })
    }

    @Test
    fun `las guardas absolutas de tarifa y neto siguen aplicando en cualquier modo`() {
        val rules = FilterRulesEntity(minFare = 5.0, minNetProfit = 4.0, primaryMetric = "PER_KM")
        val decision = ruleEngine.evaluate(evaluatedOffer(fare = 2.00, tripKm = 3.0), rules)

        assertEquals(Action.WARN, decision.action)
        assertTrue(decision.failedFilters.any { it.contains("Tarifa baja") })
        assertTrue(decision.failedFilters.any { it.contains("Ganancia neta baja") })
    }
}