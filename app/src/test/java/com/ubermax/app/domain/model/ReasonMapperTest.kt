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
    fun `km bajo se mapea a LOW_PER_KM`() {
        assertEquals(DecisionReason.LOW_PER_KM, ReasonMapper.map("📏 $/km bajo: $0.10 < mín $0.15"))
    }

    @Test
    fun `recogida lejos se mapea a PICKUP_TIME`() {
        assertEquals(DecisionReason.PICKUP_TIME, ReasonMapper.map("🚙 Recogida muy lejos: 12.00 min > máx 5.00 min"))
    }

    @Test
    fun `recorrido excede se mapea a TRIP_DISTANCE`() {
        assertEquals(DecisionReason.TRIP_DISTANCE, ReasonMapper.map("🛣️ Recorrido excede el máximo: 18.00 km > máx 12.00 km"))
    }

    @Test
    fun `rating bajo se mapea a LOW_RATING`() {
        assertEquals(DecisionReason.LOW_RATING, ReasonMapper.map("⭐ Rating bajo: 4.2"))
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
    fun `fuera del perimetro se mapea a OUT_OF_PERIMETER`() {
        assertEquals(
            DecisionReason.OUT_OF_PERIMETER,
            ReasonMapper.map("Destino fuera del perímetro urbano: 13.5 km > máx 8.0 km")
        )
    }

    @Test
    fun `recogida fuera de la zona permitida se mapea a OUT_OF_PERIMETER`() {
        assertEquals(
            DecisionReason.OUT_OF_PERIMETER,
            ReasonMapper.map("🚙 Recogida fuera de la zona permitida")
        )
    }

    @Test
    fun `ubicacion no confirmada en la zona se mapea a OUT_OF_PERIMETER`() {
        assertEquals(
            DecisionReason.OUT_OF_PERIMETER,
            ReasonMapper.map("Destino no confirmado dentro de la zona permitida")
        )
        assertEquals(
            DecisionReason.OUT_OF_PERIMETER,
            ReasonMapper.map("Recogida no confirmada dentro de la zona permitida")
        )
    }

    @Test
    fun `texto vacio devuelve null`() {
        assertNull(ReasonMapper.map(""))
        assertNull(ReasonMapper.map("  "))
    }

    @Test
    fun `limpiar elimina emojis y simbolos del inicio`() {
        assertEquals("Tarifa baja", ReasonMapper.clean("❌🚫 Tarifa baja"))
    }
}