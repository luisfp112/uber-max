package com.ubermax.app.accessibility

import com.ubermax.app.data.db.entity.FilterRulesEntity
import com.ubermax.app.data.db.entity.VehicleConfigEntity
import com.ubermax.app.domain.model.Action
import com.ubermax.app.domain.rules.RuleEngine
import com.ubermax.app.domain.usecase.EvaluateOfferUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests JVM puros para OfferParser + use cases de decisión.
 *
 * Se usan STRINGS SIMULADOS de las tarjetas reales de Uber Driver, cubriendo:
 *  - Ofertas ASIGNADAS (botón "Aceptar")
 *  - Ofertas ABIERTAS (botón "Viaje disponible" / "Postularse")
 *  - Variantes de rating, temporizadores de cuenta regresiva y fallbacks.
 *
 * No requiere dispositivo ni Robolectric: el núcleo del parser es 100% JVM.
 */
class OfferParserTest {

    private val parser = OfferParser()
    private val evaluator = EvaluateOfferUseCase()
    private val ruleEngine = RuleEngine()
    private val vehicle = VehicleConfigEntity(
        manualCostPerKm = 0.08, // costPerKm manual = $0.08 USD
        platformCommissionPercent = 0.0 // comisión: 0 para cuentas exactas de deadhead
    )

    // ═══════════════════════════════════════════════════════
    //  Datos simulados de Uber Driver
    // ═══════════════════════════════════════════════════════

    /** Oferta ASIGNADA: botón "Aceptar". */
    private fun assignedOfferTexts() = listOf(
        "UberX",
        "\$10.00",
        "★ 4.73 (42)",
        "A 3 min (1.0 km)",
        "Blvr. del Ejercito Nacional, TERMINAL DE BUSES",
        "Viaje: 30 min (12.0 km)",
        "C. L-7, CIUDAD MERLOT - SAN SALVADOR",
        "Aceptar"
    )

    /** Oferta ABIERTA: botón "Viaje disponible" + countdown. */
    private fun openOfferViajeDisponibleTexts() = listOf(
        "UberX",
        "\$8.40",
        "⭐ 4.73 (42)",
        "A 4 min (1.0 km)",
        "Parque Central, AMBATO",
        "Viaje: 20 min (8.0 km)",
        "Termina en 00:45",
        "Calle 12 y Av. Bolívar, CIUDAD MERLOT",
        "Viaje disponible"
    )

    /** Oferta ABIERTA: botón "Postularse". */
    private fun openOfferPostularseTexts() = listOf(
        "UberX",
        "\$8.40",
        "★ 4.92 (120)",
        "A 4 min (1.0 km)",
        "Parque Central, AMBATO",
        "Viaje: 20 min (8.0 km)",
        "Termina en 00:45",
        "Col. Escalón, SAN SALVADOR",
        "Postularse"
    )

    /** Oferta ABIERTA: botón "Me interesa" (nueva variante A/B de Uber). */
    private fun openOfferMeInteresaTexts() = listOf(
        "UberX",
        "\$8.40",
        "★ 4.92 (120)",
        "A 4 min (1.0 km)",
        "Parque Central, AMBATO",
        "Viaje: 20 min (8.0 km)",
        "Termina en 00:45",
        "Calle 12 y Av. Bolívar, CIUDAD MERLOT",
        "Me interesa"
    )

    private fun nodesOf(vararg texts: String): List<OfferParser.TextNode> =
        texts.map { OfferParser.TextNode(text = it, contentDesc = "") }

    // ═══════════════════════════════════════════════════════
    //  PARSE — Oferta asignada
    // ═══════════════════════════════════════════════════════

    @Test
    fun `oferta asignada con boton Aceptar se parsea completa`() {
        val offer = parser.parseFromTextNodes(nodesOf(*assignedOfferTexts().toTypedArray()))

        assertNotNull(offer)
        assertEquals(10.00, offer!!.rawFare, 0.001)
        assertEquals(1.0, offer.pickupKm, 0.001)
        assertEquals(3, offer.pickupMinutes)
        assertEquals(12.0, offer.tripKm, 0.001)
        assertEquals(30, offer.tripMinutes)
        assertEquals(33, offer.estimatedMinutes)
        assertEquals(4.73, offer.passengerRating, 0.001)
        assertEquals(42, offer.passengerTrips)
        assertEquals("UberX", offer.rideType)
        assertEquals("C. L-7, CIUDAD MERLOT - SAN SALVADOR", offer.destination)
        assertEquals("Blvr. del Ejercito Nacional, TERMINAL DE BUSES", offer.pickupAddress)
    }

    @Test
    fun `la etiqueta Aceptar nunca contamina la direccion`() {
        val offer = parser.parseFromTextNodes(nodesOf(*assignedOfferTexts().toTypedArray()))
        assertNotNull(offer)
        assertFalse(offer!!.destination.contains("Aceptar", ignoreCase = true))
        assertFalse(offer.pickupAddress.contains("Aceptar", ignoreCase = true))
    }

    // ═══════════════════════════════════════════════════════
    //  PARSE — Oferta abierta ("Viaje disponible" / "Postularse")
    // ═══════════════════════════════════════════════════════

    @Test
    fun `oferta abierta con Viaje disponible se parsea completa`() {
        val offer = parser.parseFromTextNodes(
            nodesOf(*openOfferViajeDisponibleTexts().toTypedArray())
        )

        assertNotNull(offer)
        assertEquals(8.40, offer!!.rawFare, 0.001)
        assertEquals(1.0, offer.pickupKm, 0.001)
        assertEquals(4, offer.pickupMinutes)
        assertEquals(8.0, offer.tripKm, 0.001)
        assertEquals(20, offer.tripMinutes)
        assertEquals(24, offer.estimatedMinutes)
        assertEquals(4.73, offer.passengerRating, 0.001)
        assertEquals(42, offer.passengerTrips)
        assertEquals("UberX", offer.rideType)
        assertEquals("Calle 12 y Av. Bolívar, CIUDAD MERLOT", offer.destination)
        assertEquals("Parque Central, AMBATO", offer.pickupAddress)
    }

    @Test
    fun `oferta abierta con Postularse se parsea completa`() {
        val offer = parser.parseFromTextNodes(
            nodesOf(*openOfferPostularseTexts().toTypedArray())
        )

        assertNotNull(offer)
        assertEquals(8.40, offer!!.rawFare, 0.001)
        assertEquals(4.92, offer.passengerRating, 0.001)
        assertEquals(120, offer.passengerTrips)
        assertEquals("Col. Escalón, SAN SALVADOR", offer.destination)
        assertEquals("Parque Central, AMBATO", offer.pickupAddress)
    }

    @Test
    fun `oferta abierta con Me interesa se parsea completa`() {
        val offer = parser.parseFromTextNodes(
            nodesOf(*openOfferMeInteresaTexts().toTypedArray())
        )

        assertNotNull(offer)
        assertEquals(8.40, offer!!.rawFare, 0.001)
        assertEquals(4.92, offer.passengerRating, 0.001)
        assertEquals(120, offer.passengerTrips)
        assertEquals("Calle 12 y Av. Bolívar, CIUDAD MERLOT", offer.destination)
        assertEquals("Parque Central, AMBATO", offer.pickupAddress)
    }

    @Test
    fun `el boton Me interesa nunca contamina el destino`() {
        val offer = parser.parseFromTextNodes(
            nodesOf(*openOfferMeInteresaTexts().toTypedArray())
        )
        assertNotNull(offer)
        assertFalse(offer!!.destination.contains("Me interesa", ignoreCase = true))
        assertFalse(offer.destination.contains("interesa", ignoreCase = true))
        assertFalse(offer.pickupAddress.contains("Me interesa", ignoreCase = true))
    }

    @Test
    fun `el countdown Termina en hh colon mm se excluye del destino`() {
        val offer = parser.parseFromTextNodes(
            nodesOf(*openOfferViajeDisponibleTexts().toTypedArray())
        )
        assertNotNull(offer)
        assertFalse(offer!!.destination.contains("Termina", ignoreCase = true))
        assertFalse(offer.destination.contains("00:45"))
        assertEquals("Calle 12 y Av. Bolívar, CIUDAD MERLOT", offer.destination)
    }

    @Test
    fun `el boton Viaje disponible nunca contamina el destino`() {
        val offer = parser.parseFromTextNodes(
            nodesOf(*openOfferViajeDisponibleTexts().toTypedArray())
        )
        assertNotNull(offer)
        assertFalse(offer!!.destination.contains("Viaje disponible", ignoreCase = true))
        assertFalse(offer.destination.contains("Postular", ignoreCase = true))
    }

    // ═══════════════════════════════════════════════════════
    //  PARSE — Rechazos
    // ═══════════════════════════════════════════════════════

    @Test
    fun `textos sin tarifa no son una oferta`() {
        assertNull(
            parser.parseFromTextNodes(
                nodesOf("Buenos días", "Menú principal", "Historial de viajes")
            )
        )
        assertNull(parser.parseFromTextNodes(emptyList()))
    }

    @Test
    fun `tarifa cero o invalida no es una oferta`() {
        assertNull(
            parser.parseFromTextNodes(
                nodesOf("UberX", "\$0.00", "Viaje disponible")
            )
        )
    }

    // ═══════════════════════════════════════════════════════
    //  PARSE — Variantes de rating
    // ═══════════════════════════════════════════════════════

    @Test
    fun `rating sin estrella se detecta en texto aislado`() {
        val offer = parser.parseFromTextNodes(
            nodesOf("UberX", "\$8.40", "4.92", "A 4 min (1.0 km)", "Viaje: 20 min (8.0 km)", "Destino")
        )
        assertNotNull(offer)
        assertEquals(4.92, offer!!.passengerRating, 0.001)
    }

    @Test
    fun `rating en contentDescription se detecta`() {
        val nodes = listOf(
            OfferParser.TextNode("UberX", ""),
            OfferParser.TextNode("\$8.40", ""),
            OfferParser.TextNode("", "Calificación del pasajero: 4.80 estrellas"),
            OfferParser.TextNode("A 4 min (1.0 km)", ""),
            OfferParser.TextNode("Viaje: 20 min (8.0 km)", ""),
            OfferParser.TextNode("Destino, CIUDAD", "")
        )
        val offer = parser.parseFromTextNodes(nodes)
        assertNotNull(offer)
        assertEquals(4.80, offer!!.passengerRating, 0.001)
    }

    @Test
    fun `sin rating visible se usa default honesto 0 con hasRating false`() {
        val offer = parser.parseFromTextNodes(
            nodesOf("UberX", "\$8.40", "A 4 min (1.0 km)", "Viaje: 20 min (8.0 km)", "Destino")
        )
        assertNotNull(offer)
        assertEquals(0.0, offer!!.passengerRating, 0.001)
        assertFalse(offer.hasRating) // el parser ya NO inventa un 5.0
    }

    @Test
    fun `rating visible marca hasRating true`() {
        val offer = parser.parseFromTextNodes(
            nodesOf(*assignedOfferTexts().toTypedArray())
        )
        assertNotNull(offer)
        assertTrue(offer!!.hasRating)
        assertEquals(4.73, offer.passengerRating, 0.001)
    }

    // ═══════════════════════════════════════════════════════
    //  PARSE — Coordenadas oportunistas (lat/lng en la tarjeta)
    // ═══════════════════════════════════════════════════════

    @Test
    fun `primer par de coordenadas es pickup y segundo destino`() {
        val offer = parser.parseFromTextNodes(
            nodesOf(
                "UberX", "\$10.00", "★ 4.73 (42)",
                "A 3 min (1.0 km)", "Pickup",
                "-1.2345,-78.6102",
                "Viaje: 30 min (12.0 km)", "Destino",
                "-1.2800,-78.6500",
                "Aceptar"
            )
        )
        assertNotNull(offer)
        assertEquals(-1.2345 to -78.6102, offer!!.pickupLatLng)
        assertEquals(-1.2800 to -78.6500, offer.destinationLatLng)
    }

    @Test
    fun `sin coordenadas en la tarjeta los latlng quedan nulos`() {
        val offer = parser.parseFromTextNodes(
            nodesOf(*assignedOfferTexts().toTypedArray())
        )
        assertNotNull(offer)
        assertNull(offer!!.pickupLatLng)
        assertNull(offer.destinationLatLng)
    }

    // ═══════════════════════════════════════════════════════
    //  PARSE — Fallback genérico (sin "A X min" / "Viaje: X min")
    // ═══════════════════════════════════════════════════════

    @Test
    fun `sin patrones de distancia se usa el fallback generico`() {
        val offer = parser.parseFromTextNodes(
            nodesOf("UberX", "\$10.00", "1.2 km", "18 min", "12.5 km", "Calle Falsa 123", "Aceptar")
        )
        assertNotNull(offer)
        assertEquals(10.00, offer!!.rawFare, 0.001)
        assertEquals(1.2, offer.pickupKm, 0.001)
        assertEquals(12.5, offer.tripKm, 0.001)
        assertEquals(18, offer.tripMinutes)
        // Sin patrones los minutos se estiman por distancia (a 22 km/h) y se marca como estimado
        assertEquals(37, offer.estimatedMinutes) // (1.2 + 12.5) / 22 * 60
        assertTrue(offer.minutesAreEstimated)
    }

    @Test
    fun `sin ninguna distancia no es una oferta valida`() {
        assertNull(
            parser.parseFromTextNodes(nodesOf("UberX", "\$10.00", "Viaje disponible"))
        )
    }

    // ═══════════════════════════════════════════════════════
    //  DECISIÓN — use cases (EvaluateOfferUseCase + RuleEngine)
    // ═══════════════════════════════════════════════════════

    @Test
    fun `oferta asignada rentable se ACEPTA automaticamente`() {
        val offer = parser.parseFromTextNodes(nodesOf(*assignedOfferTexts().toTypedArray()))!!
        val evaluated = evaluator.evaluate(offer, vehicle)
        val decision = ruleEngine.evaluate(
            evaluated, FilterRulesEntity(), emptyList(), emptyList()
        )

        assertEquals(Action.ACCEPT, decision.action)
        assertTrue(decision.isAccepted)
        assertTrue(evaluated.isProfitable)
        assertTrue(decision.failedFilters.isEmpty())
    }

    @Test
    fun `oferta abierta con Viaje disponible se ACEPTA`() {
        val offer = parser.parseFromTextNodes(
            nodesOf(*openOfferViajeDisponibleTexts().toTypedArray())
        )!!
        val decision = ruleEngine.evaluate(
            evaluator.evaluate(offer, vehicle), FilterRulesEntity(), emptyList(), emptyList()
        )

        assertEquals(Action.ACCEPT, decision.action)
    }

    @Test
    fun `oferta abierta con Postularse se ACEPTA`() {
        val offer = parser.parseFromTextNodes(
            nodesOf(*openOfferPostularseTexts().toTypedArray())
        )!!
        val decision = ruleEngine.evaluate(
            evaluator.evaluate(offer, vehicle), FilterRulesEntity(), emptyList(), emptyList()
        )

        assertEquals(Action.ACCEPT, decision.action)
    }

    @Test
    fun `oferta abierta con Me interesa se ACEPTA`() {
        val offer = parser.parseFromTextNodes(
            nodesOf(*openOfferMeInteresaTexts().toTypedArray())
        )!!
        val decision = ruleEngine.evaluate(
            evaluator.evaluate(offer, vehicle), FilterRulesEntity(), emptyList(), emptyList()
        )

        assertEquals(Action.ACCEPT, decision.action)
    }

    @Test
    fun `viaje largo aplica penalizacion por vuelta vacia cuando el factor esta activo`() {
        val offer = parser.parseFromTextNodes(nodesOf(*assignedOfferTexts().toTypedArray()))!!
        // factor activo (1.0): vuelta vacía con rampa desde 8km → 12km * 1.0 * ((12-8)/8) = 6km
        val evaluated = evaluator.evaluate(
            offer, vehicle, deadheadThresholdKm = 8.0, deadheadReturnFactor = 1.0
        )

        assertTrue(evaluated.hasDeadheadPenalty) // trip > 8km
        assertEquals(6.0, evaluated.returnKm, 0.001)
        assertEquals(19.0, evaluated.totalKm, 0.001) // 1 + 12 + 6 de retorno
    }

    @Test
    fun `viaje largo sin factor activo no aplica vuelta vacia`() {
        val offer = parser.parseFromTextNodes(nodesOf(*assignedOfferTexts().toTypedArray()))!!
        // factor por defecto 0.0 → deadhead desactivado (el conductor debe habitarlo)
        val evaluated = evaluator.evaluate(
            offer, vehicle, deadheadThresholdKm = 8.0, deadheadReturnFactor = 0.0
        )

        assertFalse(evaluated.hasDeadheadPenalty)
        assertEquals(0.0, evaluated.returnKm, 0.001)
        assertEquals(13.0, evaluated.totalKm, 0.001) // 1 + 12, sin retorno
    }

    @Test
    fun `tarifa baja genera WARN en vez de aceptar`() {
        val nodes = nodesOf(
            "UberX", "\$1.20", "★ 4.73 (42)",
            "A 3 min (1.0 km)", "Pickup", "Viaje: 30 min (12.0 km)", "Destino", "Aceptar"
        )
        val offer = parser.parseFromTextNodes(nodes)!!
        val decision = ruleEngine.evaluate(
            evaluator.evaluate(offer, vehicle), FilterRulesEntity(minFare = 1.50), emptyList(), emptyList()
        )

        assertEquals(Action.WARN, decision.action)
        assertTrue(decision.failedFilters.any { it.contains("Tarifa baja") })
    }

    @Test
    fun `destino en lista negra por keyword genera CANCEL`() {
        val nodes = listOf(
            OfferParser.TextNode("UberX", ""),
            OfferParser.TextNode("\$12.00", ""),
            OfferParser.TextNode("★ 4.9 (30)", ""),
            OfferParser.TextNode("A 2 min (0.5 km)", ""),
            OfferParser.TextNode("Alamos", ""),
            OfferParser.TextNode("Viaje: 15 min (6.0 km)", ""),
            OfferParser.TextNode("Av. Peligro, Sector Rojo", ""),
            OfferParser.TextNode("Aceptar", "")
        )
        val offer = parser.parseFromTextNodes(nodes)!!
        val decision = ruleEngine.evaluate(
            evaluator.evaluate(offer, vehicle),
            FilterRulesEntity(), // blacklistEnabled = true por defecto
            blacklistKeywords = listOf("peligro"),
            blacklistZones = emptyList()
        )

        assertEquals(Action.CANCEL, decision.action)
        assertTrue(decision.failedFilters.any { it.contains("LISTA NEGRA") })
        assertEquals("Av. Peligro, Sector Rojo", offer.destination)
    }

    @Test
    fun `destino en lista negra por zona del mapa genera CANCEL`() {
        val offer = parser.parseFromTextNodes(
            nodesOf(*openOfferPostularseTexts().toTypedArray())
        )!!
        val escalonZone = com.ubermax.app.data.db.entity.BlacklistZoneEntity(
            name = "escalón",
            polygonJson = """[[13.6900,-89.1900],[13.7000,-89.1900],[13.7000,-89.1800],[13.6900,-89.1800]]"""
        )
        val decision = ruleEngine.evaluate(
            evaluator.evaluate(offer, vehicle),
            FilterRulesEntity(),
            blacklistKeywords = emptyList(),
            blacklistZones = listOf(escalonZone)
        )

        assertEquals(Action.CANCEL, decision.action)
    }

    @Test
    fun `con auto-aceptar desactivado una foda buena pasa a WARN informativo`() {
        val offer = parser.parseFromTextNodes(nodesOf(*assignedOfferTexts().toTypedArray()))!!
        val rules = FilterRulesEntity(autoAcceptEnabled = false)
        val decision = ruleEngine.evaluate(
            evaluator.evaluate(offer, vehicle), rules, emptyList(), emptyList()
        )

        assertEquals(Action.WARN, decision.action)
        assertTrue(decision.failedFilters.any { it.contains("Auto-accept") })
    }
}