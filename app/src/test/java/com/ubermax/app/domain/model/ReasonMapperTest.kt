package com.ubermax.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests JVM puros de la extracción de motivo principal (flujo HUD/notificaciones).
 */
class ReasonMapperTest {

    @Test
    fun `tarifa baja se mapea a LOW_FARE`() {
        assertEquals(DecisionReason.LOW_FARE, ReasonMapper.map("❌ Tarifa baja: $3.00"))
    }

    @Test
    fun `ganancia neta se mapea a LOW_NET`() {
        assertEquals(DecisionReason.LOW_NET, ReasonMapper.map("Ganancia neta insuficiente"))
    }

    @Test
    fun `km bajo se mapea a LOW_PER_KM`() {
        assertEquals(DecisionReason.LOW_PER_KM, ReasonMapper.map("❌ $/km bajo la tarifa"))
    }

    @Test
    fun `por hora bajo se mapea a LOW_PER_HOUR`() {
        assertEquals(DecisionReason.LOW_PER_HOUR, ReasonMapper.map("⚠ Solo $/hr de ganancia"))
    }

    @Test
    fun `pickup lejos se mapea a PICKUP_FAR`() {
        assertEquals(DecisionReason.PICKUP_FAR, ReasonMapper.map("Pickup lejos: 3.2 km"))
    }

    @Test
    fun `viaje largo se mapea a TRIP_LONG`() {
        assertEquals(DecisionReason.TRIP_LONG, ReasonMapper.map("Viaje largo: 40 km"))
    }

    @Test
    fun `tiempo largo se mapea a TRIP_LONG`() {
        assertEquals(DecisionReason.TRIP_LONG, ReasonMapper.map("Tiempo largo: 90 min"))
    }

    @Test
    fun `rating bajo se mapea a LOW_RATING`() {
        assertEquals(DecisionReason.LOW_RATING, ReasonMapper.map("Rating bajo: 4.2"))
    }

    @Test
    fun `vuelta vacia se mapea a DEADHEAD`() {
        assertEquals(DecisionReason.DEADHEAD, ReasonMapper.map("Vuelta vacía: 12 km"))
    }

    @Test
    fun `lista negra se mapea a BLACKLIST`() {
        assertEquals(DecisionReason.BLACKLIST, ReasonMapper.map("LISTA NEGRA: Ficoa"))
    }

    @Test
    fun `auto-accept apagado se mapea a AUTO_ACCEPT_OFF`() {
        assertEquals(DecisionReason.AUTO_ACCEPT_OFF, ReasonMapper.map("Auto-accept desactivado"))
    }

    @Test
    fun `filtro desconocido devuelve null para mostrar texto crudo`() {
        assertNull(ReasonMapper.map("Algo raro pasó"))
    }

    @Test
    fun `texto vacio o sin emojis devuelve null`() {
        assertNull(ReasonMapper.map(""))
        assertNull(ReasonMapper.map("  "))
    }

    @Test
    fun `limpiar elimina emojis y simbolos del inicio`() {
        assertEquals("Tarifa baja", ReasonMapper.clean("❌🚫 Tarifa baja"))
    }
}