package com.ubermax.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests JVM puros de [AddressSanitizer]: la regla de 2 segmentos que depura el
 * destino de la tarjeta de Uber (contaminado con textos de pantalla) antes de
 * geocodificar o clasificar. Casos reales extraídos del audit con datos de
 * producción de Ambato.
 */
class AddressSanitizerTest {

    // ═══════════════════════════════════════════════════════
    //  Regla de 2 segmentos — casos reales del audit
    // ═══════════════════════════════════════════════════════

    @Test
    fun `calle con numeracion y parroquia se separan en 2 segmentos`() {
        val result = AddressSanitizer.sanitize("Dr Remigio Crespo Toral 154, Huachi Chico")

        assertEquals("Dr Remigio Crespo Toral 154", result.street)
        assertEquals("Huachi Chico", result.parish)
        assertEquals("Dr Remigio Crespo Toral 154, Huachi Chico", result.normalized)
    }

    @Test
    fun `cola de textos de pantalla se descarta tras el segundo segmento`() {
        val result = AddressSanitizer.sanitize(
            "Alaska, Huachi Grande, No es posible desconectarse, Buscando solicitud de viaje..."
        )

        assertEquals("Alaska", result.street)
        assertEquals("Huachi Grande", result.parish)
        assertTrue(result.normalized.contains("Huachi Grande"))
        assertFalse(result.normalized.contains("desconectarse"))
        assertFalse(result.normalized.contains("solicitud"))
    }

    @Test
    fun `simbolo huerfano se elimina al final de la calle`() {
        val result = AddressSanitizer.sanitize("Av Atahualpa &, Huachi Chico, Hacia tu destino")

        assertEquals("Av Atahualpa", result.street)
        assertEquals("Huachi Chico", result.parish)
    }

    @Test
    fun `segundo segmento con frases de UI descarta la parroquia`() {
        // "Radar de solicitud de viaje" y "2-7 min" son banners, no parroquia.
        val result = AddressSanitizer.sanitize("C. Rocafuerte 3y, Radar de solicitud de viaje, 2-7 min")

        assertEquals("C. Rocafuerte 3y", result.street)
        assertNull(result.parish)
        assertEquals("C. Rocafuerte 3y", result.normalized)
    }

    @Test
    fun `sector unico sin coma se conserva como calle`() {
        val result = AddressSanitizer.sanitize("Tambo Centro")

        assertEquals("Tambo Centro", result.street)
        assertNull(result.parish)
    }

    // ═══════════════════════════════════════════════════════
    //  Construcción de la consulta de LocationIQ
    // ═══════════════════════════════════════════════════════

    @Test
    fun `query con calle y parroquia lleva ambos segmentos`() {
        val result = AddressSanitizer.sanitize("Dr Remigio Crespo Toral 154, Huachi Chico")

        assertEquals(
            "Dr Remigio Crespo Toral 154, Huachi Chico, Ambato, Ecuador",
            result.query
        )
    }

    @Test
    fun `query sin parroquia solo lleva la calle`() {
        assertEquals(
            "Tambo Centro, Ambato, Ecuador",
            AddressSanitizer.sanitize("Tambo Centro").query
        )
    }

    @Test
    fun `query de sector contaminado se limpia antes de geocodificar`() {
        assertEquals(
            "C. Rocafuerte 3y, Ambato, Ecuador",
            AddressSanitizer.sanitize("C. Rocafuerte 3y, Radar de solicitud de viaje, 2-7 min").query
        )
    }

    // ═══════════════════════════════════════════════════════
    //  Frases de interfaz adicionales
    // ═══════════════════════════════════════════════════════

    @Test
    fun `frases de UI en segunda posicion descartan la parroquia`() {
        val phrases = listOf(
            "Radar de solicitud de viaje",
            "Buscando solicitud de viaje",
            "Batería baja del sistema",
            "Cómo iniciar la primera viaje",
            "Uber Pro Platino",
            "Sugerencia para ti",
            "Limitado a zona roja",
            "Pedidos nuevos cerca",
            "Hacia tu destino"
        )
        for (phrase in phrases) {
            val result = AddressSanitizer.sanitize("Calle Falsa 123, $phrase, 2-7 min")
            assertNull("debió descartarse: '$phrase'", result.parish)
        }
    }

    // ═══════════════════════════════════════════════════════
    //  Nombre de ciudad como segundo segmento
    // ═══════════════════════════════════════════════════════

    @Test
    fun `ciudad pegada al texto no se trata como parroquia`() {
        assertNull(AddressSanitizer.sanitize("Parque Central, AMBATO").parish)
        assertNull(AddressSanitizer.sanitize("Calle 12, Tungurahua").parish)
        assertEquals("Parque Central", AddressSanitizer.sanitize("Parque Central, AMBATO").street)
    }

    // ═══════════════════════════════════════════════════════
    //  Entradas vacías / de solo ruido
    // ═══════════════════════════════════════════════════════

    @Test
    fun `entrada vacia devuelve direccion vacia sin query`() {
        for (input in listOf(null, "", "   ", ",,,,", " , , ")) {
            val result = AddressSanitizer.sanitize(input)
            assertEquals("", result.street)
            assertNull(result.parish)
            assertEquals("", result.query)
        }
    }

    @Test
    fun `y huerfano al final de la calle se elimina`() {
        assertEquals("Av Bolívar", AddressSanitizer.sanitize("Av Bolívar y").street)
        assertEquals("Av Bolívar", AddressSanitizer.sanitize("Av Bolívar &").street)
        // Un "y" pegado a una palabra (dirección real) NO se toca.
        assertEquals("C. Rocafuerte 3y", AddressSanitizer.sanitize("C. Rocafuerte 3y").street)
    }
}