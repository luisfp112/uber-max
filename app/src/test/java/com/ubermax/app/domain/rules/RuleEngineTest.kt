package com.ubermax.app.domain.rules

import com.ubermax.app.data.db.entity.FilterRulesEntity
import com.ubermax.app.data.db.entity.VehicleConfigEntity
import com.ubermax.app.domain.geo.GeoJsonZones
import com.ubermax.app.domain.model.Action
import com.ubermax.app.domain.model.EvaluatedOffer
import com.ubermax.app.domain.model.OfferData
import com.ubermax.app.domain.usecase.EvaluateOfferUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests JVM puros del [RuleEngine].
 *
 * Cubre: la única métrica decisoria es $/km; guardas opcionales de tarifa y
 * rating; auto-accept apagado → WARN informativo; filtros de recogida/viaje
 * con ambas unidades; perímetro urbano GeoJSON → WARN (rechazo pasivo, jamás
 * CANCEL); nunca se pulsa la X (solo ACCEPT o WARN).
 */
class RuleEngineTest {

    private val evaluator = EvaluateOfferUseCase()
    private val ruleEngine = RuleEngine()
    private val vehicle = VehicleConfigEntity(manualCostPerKm = 0.08, avgSpeedKmh = 25.0)

    /** Oferta rentable por defecto ($/km alto). */
    private fun evaluatedOffer(
        fare: Double = 10.00,
        pickupKm: Double = 1.0,
        tripKm: Double = 6.0,
        pickupMinutes: Int = 3,
        tripMinutes: Int = 20,
        rating: Double = 4.8,
        hasRating: Boolean = true,
        destination: String = "Centro Histórico, AMBATO",
        pickupAddress: String = "Av. Cevallos, Terminal",
        destLatLng: Pair<Double, Double>? = null,
        pickupLatLng: Pair<Double, Double>? = null
    ): EvaluatedOffer = evaluator.evaluate(
        OfferData(
            rawFare = fare,
            pickupKm = pickupKm,
            tripKm = tripKm,
            pickupMinutes = pickupMinutes,
            tripMinutes = tripMinutes,
            estimatedMinutes = tripMinutes,
            passengerRating = rating,
            hasRating = hasRating,
            destination = destination,
            pickupAddress = pickupAddress,
            destinationLatLng = destLatLng,
            pickupLatLng = pickupLatLng
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
            FilterRulesEntity(minFare = 1.50)
        )

        assertEquals(Action.WARN, decision.action)
        assertTrue(decision.failedFilters.any { it.contains("Tarifa baja") })
    }

    @Test
    fun `rating bajo genera WARN cuando hay umbral configurado`() {
        val decision = ruleEngine.evaluate(
            evaluatedOffer(rating = 3.5),
            FilterRulesEntity(minPassengerRating = 4.5)
        )

        assertEquals(Action.WARN, decision.action)
        assertTrue(decision.failedFilters.any { it.contains("Rating bajo") })
    }

    @Test
    fun `sin umbral de rating el filtro esta desactivado`() {
        val decision = ruleEngine.evaluate(
            evaluatedOffer(rating = 3.0),
            FilterRulesEntity()
        )

        assertEquals(Action.ACCEPT, decision.action)
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
    //  FILTROS DE RECOGIDA / VIAJE (min o km de la tarjeta)
    // ═══════════════════════════════════════════════════════

    @Test
    fun `recogida sobre el maximo en minutos genera WARN`() {
        val decision = ruleEngine.evaluate(
            evaluatedOffer(pickupMinutes = 12),
            FilterRulesEntity(pickupMeasure = "MIN", pickupMax = 5.0)
        )

        assertEquals(Action.WARN, decision.action)
        assertTrue(decision.failedFilters.any { it.contains("Recogida muy lejos") })
    }

    @Test
    fun `recogida en km configura la unidad del filtro`() {
        val decision = ruleEngine.evaluate(
            evaluatedOffer(pickupKm = 3.0, pickupMinutes = 30),
            FilterRulesEntity(pickupMeasure = "KM", pickupMax = 2.0)
        )

        assertEquals(Action.WARN, decision.action)
        assertTrue(decision.failedFilters.any { it.contains("> máx 2.00") })
    }

    @Test
    fun `recogida en km respeta el valor real de la tarjeta y acepta`() {
        val decision = ruleEngine.evaluate(
            evaluatedOffer(pickupKm = 1.5, pickupMinutes = 12),
            FilterRulesEntity(pickupMeasure = "KM", pickupMax = 2.0)
        )

        assertEquals(Action.ACCEPT, decision.action)
    }

    @Test
    fun `viaje sobre el maximo en minutos genera WARN`() {
        val decision = ruleEngine.evaluate(
            evaluatedOffer(tripMinutes = 30),
            FilterRulesEntity(tripMeasure = "MIN", tripMax = 15.0)
        )

        assertEquals(Action.WARN, decision.action)
        assertTrue(decision.failedFilters.any { it.contains("Recorrido excede") })
    }

    @Test
    fun `viaje en km con tope activo rechaza`() {
        val decision = ruleEngine.evaluate(
            evaluatedOffer(tripKm = 18.0, tripMinutes = 20),
            FilterRulesEntity(tripMeasure = "KM", tripMax = 12.0)
        )

        assertEquals(Action.WARN, decision.action)
        assertTrue(decision.failedFilters.any { it.contains("Recorrido excede") })
    }

    @Test
    fun `tope de viaje en cero no limita`() {
        val decision = ruleEngine.evaluate(
            evaluatedOffer(tripKm = 18.0, tripMinutes = 40),
            FilterRulesEntity(tripMeasure = "MIN", tripMax = 0.0)
        )

        assertEquals(Action.ACCEPT, decision.action)
    }

    // ═══════════════════════════════════════════════════════
    //  MÉTRICA ÚNICA — $/km decide
    // ═══════════════════════════════════════════════════════

    @Test
    fun `oferta con la ganancia por km bajo el minimo genera WARN`() {
        val decision = ruleEngine.evaluate(
            evaluatedOffer(fare = 1.80, tripKm = 7.0, tripMinutes = 8),
            FilterRulesEntity(minProfitPerKm = 0.15)
        )

        assertEquals(Action.WARN, decision.action)
        assertTrue(decision.failedFilters.any { it.contains("\$/km bajo") })
    }

    @Test
    fun `oferta con la ganancia por km sobre el minimo se ACEPTA`() {
        val decision = ruleEngine.evaluate(
            evaluatedOffer(fare = 10.00, tripKm = 6.0),
            FilterRulesEntity(minProfitPerKm = 0.15)
        )

        assertEquals(Action.ACCEPT, decision.action)
    }

    @Test
    fun `minimo por km en cero deja la metricas desactivadas`() {
        val decision = ruleEngine.evaluate(
            evaluatedOffer(fare = 1.80, tripKm = 7.0),
            FilterRulesEntity(minProfitPerKm = 0.0)
        )

        assertEquals(Action.ACCEPT, decision.action)
    }

    // ═══════════════════════════════════════════════════════
    //  ZONAS PERMITIDAS (geolocalización LocationIQ + GeoJSON)
    // ═══════════════════════════════════════════════════════

    @Test
    fun `destino fuera de los poligonos del GeoJSON se bloquea con zona prohibida`() {
        val zones = GeoJsonZones.parse(
            """
            {"type":"FeatureCollection","features":[{"type":"Feature","properties":{},
            "geometry":{"type":"Polygon","coordinates":[[[-78.66,-1.26],[-78.59,-1.26],
            [-78.59,-1.22],[-78.66,-1.22],[-78.66,-1.26]]]}}]}
            """.trimIndent()
        )!!
        val evaluated = evaluatedOffer(
            destLatLng = -1.3300 to -78.5422,
            destination = "Pelileo, TUNGURAHUA"
        )

        val decision = ruleEngine.evaluate(evaluated, FilterRulesEntity(), boundary = zones)

        assertEquals(Action.WARN, decision.action)
        assertTrue(decision.failedFilters.any { it.contains("Zona prohibida") })
    }

    @Test
    fun `destino dentro de los poligonos del GeoJSON se acepta`() {
        val zones = GeoJsonZones.parse(
            """
            {"type":"Polygon","coordinates":[[[-78.66,-1.26],[-78.59,-1.26],
            [-78.59,-1.22],[-78.66,-1.22],[-78.66,-1.26]]]}
            """.trimIndent()
        )!!
        val evaluated = evaluatedOffer(
            destLatLng = -1.24 to -78.62,
            pickupLatLng = -1.24 to -78.63
        )

        val decision = ruleEngine.evaluate(evaluated, FilterRulesEntity(), boundary = zones)

        assertEquals(Action.ACCEPT, decision.action)
        assertFalse(decision.failedFilters.any { it.contains("Zona prohibida") })
        assertFalse(decision.failedFilters.any { it.contains("no confirmada") })
    }

    @Test
    fun `recogida fuera de los poligonos del GeoJSON genera WARN de recogida`() {
        val zones = GeoJsonZones.parse(
            """
            {"type":"Polygon","coordinates":[[[-78.66,-1.26],[-78.59,-1.26],
            [-78.59,-1.22],[-78.66,-1.22],[-78.66,-1.26]]]}
            """.trimIndent()
        )!!
        val evaluated = evaluatedOffer(
            destLatLng = -1.24 to -78.62,
            pickupLatLng = -1.3300 to -78.5422,
            pickupAddress = "Heiraway, Oscar Wilde"
        )

        val decision = ruleEngine.evaluate(evaluated, FilterRulesEntity(), boundary = zones)

        assertEquals(Action.WARN, decision.action)
        assertTrue(decision.failedFilters.any { it.contains("Recogida fuera de la zona permitida") })
    }

    @Test
    fun `recogida sin coordenadas se advierte como no confirmada`() {
        val zones = GeoJsonZones.parse(
            """
            {"type":"Polygon","coordinates":[[[-78.66,-1.26],[-78.59,-1.26],
            [-78.59,-1.22],[-78.66,-1.22],[-78.66,-1.26]]]}
            """.trimIndent()
        )!!

        val decision = ruleEngine.evaluate(
            evaluatedOffer(pickupLatLng = null, destLatLng = -1.24 to -78.62),
            FilterRulesEntity(),
            boundary = zones
        )

        assertEquals(Action.WARN, decision.action)
        assertTrue(decision.failedFilters.any { it.contains("Recogida no confirmada dentro de la zona permitida") })
    }

    @Test
    fun `sin zona permitida el filtro de perimetro queda inactivo aunque haya coordenadas`() {
        val evaluated = evaluatedOffer(
            destLatLng = -1.3300 to -78.5422,
            destination = "Pelileo, TUNGURAHUA"
        )

        val decision = ruleEngine.evaluate(evaluated, FilterRulesEntity())

        assertEquals(Action.ACCEPT, decision.action)
        assertFalse(decision.failedFilters.any { it.contains("perímetro urbano") })
    }

    @Test
    fun `con perimetro activo y puntos sin coordenadas se advierte en vez de auto-aceptar`() {
        val evaluated = evaluatedOffer()
        val boundary = GeoJsonZones.parse(
            """
            {"type":"Polygon","coordinates":[[[-78.66,-1.26],[-78.59,-1.26],
            [-78.59,-1.22],[-78.66,-1.22],[-78.66,-1.26]]]}
            """.trimIndent()
        )!!

        val decision = ruleEngine.evaluate(evaluated, FilterRulesEntity(), boundary = boundary)

        assertEquals(Action.WARN, decision.action)
        assertTrue(decision.failedFilters.any { it.contains("no confirmada") })
    }
}