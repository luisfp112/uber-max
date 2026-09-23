package com.ubermax.app.data.geocoding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests JVM puros del sanitizador de direcciones (pre-geocoding).
 */
class AddressCleanerTest {

    @Test
    fun `elimina tarifas y simbolos monetarios`() {
        assertEquals("Calle 12 y Av. Bolívar", AddressCleaner.clean("Calle 12 y Av. Bolívar \$8.40"))
        assertEquals("Ficoa", AddressCleaner.clean("$1.75 Ficoa"))
        assertEquals("Av. Cevallos", AddressCleaner.clean("Av. Cevallos \$ 2,50"))
    }

    @Test
    fun `elimina calificaciones estrellas y conteo de viajes`() {
        assertEquals("Ficoa, Ambato", AddressCleaner.clean("Ficoa, Ambato ★ 4.73 (42)"))
        assertEquals("Ficoa, Ambato", AddressCleaner.clean("Ficoa, Ambato 4.73 (42)"))
        assertEquals("Ficoa", AddressCleaner.clean("4.92 ★ Ficoa"))
        assertEquals("Ficoa", AddressCleaner.clean("⭐ 5 Ficoa"))
        assertEquals("Parque Central", AddressCleaner.clean("Calificación del pasajero: 4.80 estrellas Parque Central"))
    }

    @Test
    fun `elimina metricas de tiempo y distancia`() {
        assertEquals("Calle Los Sauces", AddressCleaner.clean("Calle Los Sauces A 4 min (1.0 km)"))
        assertEquals("Av. Cevallos", AddressCleaner.clean("Av. Cevallos 18 min (15.6 km)"))
        assertEquals("Sector Los Molinos", AddressCleaner.clean("Sector Los Molinos 850 m"))
        assertEquals("Vía a San Pablo", AddressCleaner.clean("Vía a San Pablo 2,5 km"))
    }

    @Test
    fun `elimina countdowns y relojes`() {
        assertEquals("Parque Central", AddressCleaner.clean("Termina en 00:45 Parque Central"))
        assertEquals("Parque Central", AddressCleaner.clean("Parque Central 00:45"))
    }

    @Test
    fun `elimina etiquetas y productos de Uber`() {
        assertEquals("Col. Escalón", AddressCleaner.clean("UberX Col. Escalón"))
        assertEquals("Av. Confraternidad", AddressCleaner.clean("Av. Confraternidad Flash"))
        assertEquals("Parque Central", AddressCleaner.clean("Parque Central Viaje disponible"))
        assertEquals("Parque Central", AddressCleaner.clean("Aceptar Parque Central"))
        assertEquals("Av. Bolívar", AddressCleaner.clean("Prioridad Av. Bolívar"))
    }

    @Test
    fun `no toca numeros que son parte real de la direccion`() {
        assertEquals(
            "Calle 12 de Noviembre y Av. 5 de Junio",
            AddressCleaner.clean("Calle 12 de Noviembre y Av. 5 de Junio")
        )
    }

    @Test
    fun `colapsa espacios y separadores sobrantes`() {
        assertEquals("Av. Cevallos, Ambato", AddressCleaner.clean("  Av.  Cevallos ,  Ambato  "))
        assertEquals("y no deja fragmentos huerfanos", AddressCleaner.clean("y no deja fragmentos huerfanos"))
    }

    @Test
    fun `usable solo con al menos 3 caracteres alfanumericos`() {
        assertTrue(AddressCleaner.isUsable("Av. Cevallos"))
        assertTrue(AddressCleaner.isUsable("Calle 12"))
        assertFalse(AddressCleaner.isUsable(""))
        assertFalse(AddressCleaner.isUsable("ab"))
        assertFalse(AddressCleaner.isUsable("   "))
    }

    @Test
    fun `texto de puro ruido queda inutilizable`() {
        val garbage = "\$8.40 ★ 4.73 (42) A 4 min (1.0 km) Termina en 00:45 UberX"
        assertFalse(AddressCleaner.isUsable(AddressCleaner.clean(garbage)))
    }
}