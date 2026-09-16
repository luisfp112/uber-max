package com.ubermax.app.accessibility

import com.ubermax.app.accessibility.TapGeometry.RectSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests JVM puros para ActionExecutor y TapGeometry.
 *
 * Valida que el reconocimiento de botones cubra ofertas asignadas y abiertas
 * ("Aceptar", "Viaje disponible", "Postularse") y que TODA la geometría de toque
 * sea RELATIVA (proporciones de pantalla o región de la tarjeta), sin ninguna
 * coordenada absoluta fija a un modelo de teléfono.
 */
class ActionExecutorTest {

    // ═══════════════════════════════════════════════════════
    //  Reconocimiento del botón ACEPTAR (asignada + abierta)
    // ═══════════════════════════════════════════════════════

    @Test
    fun `reconoce boton Aceptar de oferta asignada`() {
        assertTrue(ActionExecutor.matchesAcceptText("Aceptar"))
        assertTrue(ActionExecutor.matchesAcceptText("Aceptar viaje"))
        assertTrue(ActionExecutor.matchesAcceptText("Confirmar"))
    }

    @Test
    fun `reconoce boton Viaje disponible de oferta abierta`() {
        assertTrue(ActionExecutor.matchesAcceptText("Viaje disponible"))
        assertTrue(ActionExecutor.matchesAcceptText("VIAJE DISPONIBLE"))
        assertTrue(ActionExecutor.matchesAcceptText("Trip available"))
    }

    @Test
    fun `reconoce boton Postularse de oferta abierta`() {
        assertTrue(ActionExecutor.matchesAcceptText("Postularse"))
        assertTrue(ActionExecutor.matchesAcceptText("Postularte"))
        assertTrue(ActionExecutor.matchesAcceptText("Postularme"))
        assertTrue(ActionExecutor.matchesAcceptText("Postular"))
        assertTrue(ActionExecutor.matchesAcceptText("Aplicar"))
    }

    @Test
    fun `reconoce variantes adicionales de oferta abierta`() {
        assertTrue(ActionExecutor.matchesAcceptText("Disponible"))
        assertTrue(ActionExecutor.matchesAcceptText("VIAJE DISPONIBLE"))
        assertTrue(ActionExecutor.matchesAcceptText("Solicitar viaje"))
        assertTrue(ActionExecutor.matchesAcceptText("Solicitar"))
        assertTrue(ActionExecutor.matchesAcceptText("Solicita"))
        assertTrue(ActionExecutor.matchesAcceptText("Solicito"))
        assertTrue(ActionExecutor.matchesAcceptText("Apuntarme"))
        assertTrue(ActionExecutor.matchesAcceptText("Participar"))
        assertTrue(ActionExecutor.matchesAcceptText("Request trip"))
        assertTrue(ActionExecutor.matchesAcceptText("Request"))
        assertTrue(ActionExecutor.matchesAcceptText("Join"))
    }

    @Test
    fun `no confunde botones de rechazo con aceptar`() {
        assertFalse(ActionExecutor.matchesAcceptText("Rechazar"))
        assertFalse(ActionExecutor.matchesAcceptText("Cerrar"))
        assertFalse(ActionExecutor.matchesAcceptText("Descartar"))
        assertFalse(ActionExecutor.matchesAcceptText("No, gracias"))
        assertFalse(ActionExecutor.matchesAcceptText("Dismiss"))
        assertFalse(ActionExecutor.matchesAcceptText(""))
    }

    // ═══════════════════════════════════════════════════════
    //  Reconocimiento de la X de descarte
    // ═══════════════════════════════════════════════════════

    @Test
    fun `reconoce la X por contentDescription y texto`() {
        assertTrue(ActionExecutor.matchesDismissText("Cerrar"))
        assertTrue(ActionExecutor.matchesDismissText("CERRAR"))
        assertTrue(ActionExecutor.matchesDismissText("Descartar"))
        assertTrue(ActionExecutor.matchesDismissText("Rechazar"))
        assertTrue(ActionExecutor.matchesDismissText("No, gracias"))
        assertTrue(ActionExecutor.matchesDismissText("Close"))
        assertTrue(ActionExecutor.matchesDismissText("Dismiss"))
    }

    @Test
    fun `reconoce X aislada como descartar`() {
        assertTrue(ActionExecutor.matchesDismissText("X"))
        assertTrue(ActionExecutor.matchesDismissText("x"))
    }

    @Test
    fun `no confunde aceptar con descartar`() {
        assertFalse(ActionExecutor.matchesDismissText("Aceptar"))
        assertFalse(ActionExecutor.matchesDismissText("Aceptar viaje"))
        assertFalse(ActionExecutor.matchesDismissText("Viaje disponible"))
        assertFalse(ActionExecutor.matchesDismissText("Postularse"))
        assertFalse(ActionExecutor.matchesDismissText(""))
    }

    // ═══════════════════════════════════════════════════════
    //  TapGeometry — centro exacto del rect
    // ═══════════════════════════════════════════════════════

    @Test
    fun `centro exacto de un rectangulo`() {
        val r = RectSpec(left = 100, top = 200, right = 300, bottom = 500)
        assertEquals(200, r.centerX)
        assertEquals(350, r.centerY)
        assertEquals(200, r.width)
        assertEquals(300, r.height)
        assertEquals(200 to 350, TapGeometry.centerOf(r))
    }

    @Test
    fun `rect vacio o invertido no es objetivo de toque`() {
        assertTrue(RectSpec(0, 0, 0, 0).isEmpty)
        assertTrue(RectSpec(100, 100, 50, 50).isEmpty)
        assertFalse(RectSpec(100, 100, 300, 300).isEmpty)
    }

    // ═══════════════════════════════════════════════════════
    //  Puntos de reserva — proporcionales a la pantalla real
    // ═══════════════════════════════════════════════════════

    @Test
    fun `fallback de aceptar es relativo y escala con la pantalla`() {
        // 1080x2400 (densidad típica) vs 1440x3120 (otro dispositivo):
        // la PROPORCIÓN es idéntica, nunca una coordenada fija.
        val a32 = TapGeometry.fallbackAcceptPoint(1080, 2400)
        assertEquals(540 to 2040, a32) // 50% ancho, 85% alto

        val otro = TapGeometry.fallbackAcceptPoint(1440, 3120)
        assertEquals(720 to 2652, otro) // misma proporción

        val r1 = a32.first.toFloat() / 1080
        val r2 = otro.first.toFloat() / 1440
        assertEquals(r1, r2, 0.001f)
    }

    @Test
    fun `fallback de descartar es relativo y escala con la pantalla`() {
        assertEquals(1015 to 672, TapGeometry.fallbackDismissPoint(1080, 2400))   // 94% / 28%
        assertEquals(1353 to 873, TapGeometry.fallbackDismissPoint(1440, 3120))
    }

    // ═══════════════════════════════════════════════════════
    //  Búsqueda de la X por región (cuadrante superior derecho)
    // ═══════════════════════════════════════════════════════

    private val screen1080x2400 = RectSpec(0, 0, 1080, 2400)

    @Test
    fun `elige la X en el cuadrante superior derecho de la tarjeta`() {
        val xIcon = RectSpec(950, 120, 990, 160)     // ícono pequeño, top-right
        val bigButton = RectSpec(400, 1600, 900, 1850) // botón grande → no es X
        val bottomLeft = RectSpec(80, 1800, 120, 1840) // otra esquina → no es X

        val chosen = TapGeometry.chooseDismissCandidate(
            candidates = listOf(bigButton, xIcon, bottomLeft),
            container = screen1080x2400
        )

        assertEquals(xIcon, chosen)
    }

    @Test
    fun `si hay dos X validas elige la mas a la derecha`() {
        val leftX = RectSpec(600, 100, 640, 140)
        val rightX = RectSpec(950, 100, 996, 140)

        val chosen = TapGeometry.chooseDismissCandidate(
            candidates = listOf(leftX, rightX),
            container = screen1080x2400
        )

        assertEquals(rightX, chosen)
    }

    @Test
    fun `sin candidato en la region correcta devuelve null`() {
        val bottomRight = RectSpec(950, 2000, 990, 2040) // abajo → no es X

        assertNull(
            TapGeometry.chooseDismissCandidate(listOf(bottomRight), screen1080x2400)
        )
        assertNull(TapGeometry.chooseDismissCandidate(emptyList(), screen1080x2400))
    }

    @Test
    fun `la region de la X se mide relativa al contenedor de la tarjeta`() {
        // Tarjeta de oferta: ocupa solo una franja vertical de la pantalla
        val card = RectSpec(0, 400, 1080, 1800)

        val xOnCard = RectSpec(900, 450, 940, 490)    // top-right DE LA TARJETA ✓
        val statusBar = RectSpec(100, 100, 140, 140)  // fuera de la tarjeta, izquierda ✗

        val chosen = TapGeometry.chooseDismissCandidate(
            candidates = listOf(statusBar, xOnCard),
            container = card
        )

        assertEquals(xOnCard, chosen)
        assertNotNull(chosen)
    }

    @Test
    fun `un nodo grande aunque este arriba no se confunde con la X`() {
        val giantHeader = RectSpec(0, 80, 1080, 500) // ocupa todo el ancho

        assertNull(
            TapGeometry.chooseDismissCandidate(listOf(giantHeader), screen1080x2400)
        )
    }

    @Test
    fun `predicados de region y tamano son consistentes`() {
        val xIcon = RectSpec(950, 120, 990, 160)
        assertTrue(TapGeometry.isDismissSized(xIcon, screen1080x2400))
        assertTrue(TapGeometry.isInTopRightRegion(xIcon, screen1080x2400))

        val bigButton = RectSpec(400, 1600, 900, 1850)
        assertFalse(TapGeometry.isDismissSized(bigButton, screen1080x2400))
    }

    // ═══════════════════════════════════════════════════════
    //  relativePointIn — tap centrado dentro de un botón visible
    // ═══════════════════════════════════════════════════════

    @Test
    fun `relativePointIn centro de un boton`() {
        val btn = RectSpec(200, 600, 800, 700) // 600×100
        val (x, y) = TapGeometry.relativePointIn(btn, 0.5f, 0.5f)
        assertEquals(500, x) // 200 + 600*0.5 = 500
        assertEquals(650, y) // 600 + 100*0.5 = 650
    }

    @Test
    fun `relativePointIn esquina superior izquierda`() {
        val r = RectSpec(100, 100, 500, 500)
        assertEquals(100 to 100, TapGeometry.relativePointIn(r, 0f, 0f))
    }

    @Test
    fun `relativePointIn esquina inferior derecha`() {
        val r = RectSpec(100, 100, 500, 500)
        assertEquals(500 to 500, TapGeometry.relativePointIn(r, 1f, 1f))
    }
}