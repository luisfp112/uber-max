package com.ubermax.app.data.geocoding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests JVM puros de la parte sin red de [LocationIqGeocoder]: parseo del JSON
 * de LocationIQ y construcción de la query de búsqueda. La llamada HTTP no se
 * testea en JVM (es fail-open y depende de la red).
 */
class LocationIqGeocoderTest {

    @Test
    fun `parsea el primer resultado del JSON de LocationIQ`() {
        val json = """
            [
              {
                "place_id": "296461212",
                "licence": "https://locationiq.com/attribution",
                "lat": "-1.243288",
                "lon": "-78.626709",
                "display_name": "Ambato, Ecuador"
              }
            ]
        """.trimIndent()

        val result = LocationIqGeocoder.parseLatLng(json)

        assertEquals(-1.243288, result!!.first, 0.000001)
        assertEquals(-78.626709, result.second, 0.000001)
    }

    @Test
    fun `parsea lat y lon sin prefijos ni decimales fijos`() {
        val json = """[{"lat":"12.34","lon":"-99.0"}]"""
        assertEquals(12.34 to -99.0, LocationIqGeocoder.parseLatLng(json))
    }

    @Test
    fun `resultado vacio devuelve null`() {
        assertNull(LocationIqGeocoder.parseLatLng("[]"))
        assertNull(LocationIqGeocoder.parseLatLng(""))
        assertNull(LocationIqGeocoder.parseLatLng("no json"))
    }

    @Test
    fun `coordenadas fuera de rango se descartan`() {
        assertNull(LocationIqGeocoder.parseLatLng("""[{"lat":"91","lon":"-78.6"}]"""))
        assertNull(LocationIqGeocoder.parseLatLng("""[{"lat":"-1.24","lon":"181"}]"""))
    }

    @Test
    fun `query con ciudad se normaliza y no duplica Ambato`() {
        assertEquals(
            "Av. Bolívar y Guayaquil, Ambato, Ecuador",
            LocationIqGeocoder.buildQuery("Av. Bolívar y Guayaquil, Ambato")
        )
    }

    @Test
    fun `query de una direccion parcial se sesga a Ambato`() {
        assertEquals(
            "Calle 12 de Noviembre, Ambato, Ecuador",
            LocationIqGeocoder.buildQuery("Calle 12 de Noviembre")
        )
    }

    @Test
    fun `query con otra ciudad se formatea igual con Ambato`() {
        assertEquals(
            "Malecon 2000, Guayaquil, Ambato, Ecuador",
            LocationIqGeocoder.buildQuery("Malecon 2000, Guayaquil")
        )
    }

    @Test
    fun `query con canton se formatea estricto con Ambato Ecuador`() {
        assertEquals(
            "Av. Municipalidad, Baños, Ambato, Ecuador",
            LocationIqGeocoder.buildQuery("Av. Municipalidad, Baños")
        )
    }

    @Test
    fun `query vacia devuelve vacio`() {
        assertEquals("", LocationIqGeocoder.buildQuery("  "))
    }

    // ═══════════════════════════════════════════════════════
    //  LIMPIEZA — el destino puede llegar contaminado con UI de la tarjeta
    // ═══════════════════════════════════════════════════════

    @Test
    fun `limpia tarifa calificacion y metricas pegadas al destino`() {
        assertEquals(
            "Calle Los Sauces y Huachi, Ambato, Ecuador",
            LocationIqGeocoder.buildQuery(
                "\$8.40 ★ 4.73 (42) A 4 min (1.0 km) Calle Los Sauces y Huachi, Ambato"
            )
        )
    }

    @Test
    fun `limpia etiquetas de producto y countdown del destino`() {
        assertEquals(
            "Col. Escalón, San Salvador, Ambato, Ecuador",
            LocationIqGeocoder.buildQuery(
                "Termina en 00:45 UberX Col. Escalón, San Salvador Viaje disponible"
            )
        )
    }

    @Test
    fun `limpia rating en contentDescription`() {
        assertEquals(
            "Parque Central, Ambato, Ecuador",
            LocationIqGeocoder.buildQuery(
                "Calificación del pasajero: 4.80 estrellas Parque Central, Ambato"
            )
        )
    }

    @Test
    fun `aborta si tras limpiar queda menos de 3 caracteres validos`() {
        // Solo ruido de tarjeta, sin dirección real → no llamar a la API
        assertEquals("", LocationIqGeocoder.buildQuery("\$8.40 ★ 4.73 (42)"))
        assertEquals("", LocationIqGeocoder.buildQuery("A 4 min (1.0 km) 12 min (85 km)"))
        assertEquals("", LocationIqGeocoder.buildQuery("Termina en 00:45 $1.75 Flash"))
    }

    @Test
    fun `el fallback local aplica cuando la query queda vacia`() {
        // geocode no puede llamar a la red si la query es "" → devuelve null sin HTTP
        assertNull(
            kotlinx.coroutines.runBlocking {
                LocationIqGeocoder.geocode("\$8.40 ★ 4.73 (42)", "pk.test")
            }
        )
    }

    // ═══════════════════════════════════════════════════════
    //  parseResult + nameMatches — no aceptar centroides de ciudad
    // ═══════════════════════════════════════════════════════

    @Test
    fun `parseResult conserva el display_name de la respuesta`() {
        val json = """
            [{"lat":"-1.2447497","lon":"-78.6392316",
            "display_name":"Avenida Los Guaytambos, Ambato, Tungurahua, 180101, Ecuador"}]
        """.trimIndent()

        val result = LocationIqGeocoder.parseResult(json)!!

        assertEquals(-1.2447497, result.lat, 0.0000001)
        assertEquals(-78.6392316, result.lng, 0.0000001)
        assertTrue(result.displayName.contains("Guaytambos"))
    }

    @Test
    fun `parseResult devuelve null cuando la respuesta no tiene coordenadas validas`() {
        assertNull(LocationIqGeocoder.parseResult("[]"))
        assertNull(LocationIqGeocoder.parseResult("""[{"lat":"91","lon":"-78.6"}]"""))
    }

    @Test
    fun `nameMatches reconoce una calle real de Ambato por tokens`() {
        assertTrue(
            LocationIqGeocoder.nameMatches(
                "Av. Los Guaytambos",
                "Avenida Los Guaytambos, Ambato, Tungurahua, Ecuador"
            )
        )
    }

    @Test
    fun `nameMatches descarta el centroide cuando la calle no existe`() {
        // LocationIQ cae al centro de la ciudad si la calle no existe:
        // "Heiraway, Oscar Wilde" → "Ambato, Ambato, Tungurahua, Ecuador" (sin tokens)
        assertFalse(
            LocationIqGeocoder.nameMatches(
                "Heiraway, Oscar Wilde",
                "Ambato, Ambato, Tungurahua, Ecuador"
            )
        )
    }

    @Test
    fun `nameMatches tolera acentos y variantes de abreviaturas viales`() {
        assertTrue(
            LocationIqGeocoder.nameMatches(
                "Av. Cóndor, Ingapirca",
                "Avenida Del Condor, Cuenca, Azuay, Ecuador"
            )
        )
    }

    @Test
    fun `parseResult decodifica acentos escapados del JSON de LocationIQ`() {
        // LocationIQ manda los acentos como escapes Unicode (C\u00f3ndor = Cóndor).
        val json = """
            [{"lat":"-1.269738","lon":"-78.616272",
            "display_name":"Avenida Cordillera del C\u00f3ndor, Ambato, Tungurahua, Ecuador"}]
        """.trimIndent()

        val result = LocationIqGeocoder.parseResult(json)!!

        assertTrue(result.displayName.contains("Cóndor"))
        // Con el escape sin decodificar ("condor" vs "C\u00f3ndor") esto fallaría.
        assertTrue(
            LocationIqGeocoder.nameMatches("Av. Condor, Ingapirca", result.displayName)
        )
    }

    @Test
    fun `nameMatches no considera conectores como tokens validos`() {
        assertTrue(LocationIqGeocoder.significantTokens("Calle de la y el").isEmpty())
    }

    @Test
    fun `parseResults devuelve todos los candidatos validos en orden`() {
        // El primer result es el centroide de otra parroquia (basura); el segundo
        // es la calle real. parseResults los conserva para poder elegir.
        val json = """
            [
              {"lat":"-1.318125","lon":"-78.595421","display_name":"La Victoria, Pelileo, Tungurahua, Ecuador"},
              {"lat":"-1.266145","lon":"-78.631086","display_name":"Avenida Victor Hugo, Ambato, Tungurahua, Ecuador"},
              {"lat":"91","lon":"-78.0","display_name":"inválido"}
            ]
        """.trimIndent()

        val results = LocationIqGeocoder.parseResults(json)

        assertEquals(2, results.size)
        assertEquals(-1.318125, results[0].lat, 0.000001)
        assertTrue(results[1].displayName.contains("Victor Hugo"))
    }

    @Test
    fun `parseResults tolera un unico objeto sin array`() {
        val result = LocationIqGeocoder.parseResult(
            """{"lat":"-1.243288","lon":"-78.626709","display_name":"Ambato, Ecuador"}"""
        )

        assertEquals(-1.243288, result!!.lat, 0.000001)
    }

    @Test
    fun `streetComponents separa un cruce con y en sus calles`() {
        assertEquals(
            listOf("Av. Bolívar", "Guayaquil"),
            LocationIqGeocoder.streetComponents("Av. Bolívar y Guayaquil")
        )
    }

    @Test
    fun `streetComponents conserva un sector sin interseccion`() {
        assertEquals(
            listOf("Parque Montalvo"),
            LocationIqGeocoder.streetComponents("Parque Montalvo")
        )
    }

    @Test
    fun `streetComponents separa por coma y quita ciudad pegada`() {
        assertEquals(
            listOf("Av. Municipalidad", "Baños"),
            LocationIqGeocoder.streetComponents("Av. Municipalidad, Baños, Ambato")
        )
    }

    @Test
    fun `streetComponents limpia basura de tarjeta antes de separar`() {
        assertEquals(
            listOf("Av. Cevallos", "Montalvo"),
            LocationIqGeocoder.streetComponents("\$8.40 ★ 4.73 (42) Av. Cevallos y Montalvo")
        )
    }

    @Test
    fun `streetComponents de un cruce falso conserva sus calles`() {
        assertEquals(
            listOf("Heiraway", "Oscar Wilde"),
            LocationIqGeocoder.streetComponents("Heiraway, Oscar Wilde")
        )
    }
}