package com.ubermax.app.domain.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests del parser GeoJSON + intersección de puntos ([GeoJsonZones]).
 * Geometría 100% JVM pura: Features/Polygons con orden de vértices [lng, lat].
 */
class GeoJsonZonesTest {

    private fun squareZones(json: String): GeoJsonZones? = GeoJsonZones.parse(json)

    // ═══════════════════════════════════════════════════════
    //  PARSING
    // ═══════════════════════════════════════════════════════

    @Test
    fun `parsea una FeatureCollection con un Polygon`() {
        val zones = squareZones(featureCollectionSquare)!!

        assertNotNull(zones)
        assertEquals(1, zones.polygons.size)
        assertEquals(1, zones.polygons[0].size)
        // Cuadrado [-78.66..-78.59] x [-1.26..-1.22] → centro (Parque Montalvo) dentro.
        assertTrue(zones.contains(UrbanPerimeter.AMBATO_CENTER_LAT, UrbanPerimeter.AMBATO_CENTER_LNG))
    }

    @Test
    fun `parsea un Polygon suelto sin FeatureCollection`() {
        val zones = squareZones(barePolygonSquare)!!

        assertNotNull(zones)
        assertEquals(1, zones.polygons.size)
    }

    @Test
    fun `parsea un Feature suelto`() {
        val zones = squareZones(
            """
            {"type":"Feature","properties":{},"geometry":{"type":"Polygon",
            "coordinates":[[[-78.66,-1.26],[-78.59,-1.26],[-78.59,-1.22],[-78.66,-1.22],[-78.66,-1.26]]]}}
            """.trimIndent()
        )!!

        assertNotNull(zones)
        assertTrue(zones.contains(UrbanPerimeter.AMBATO_CENTER_LAT, UrbanPerimeter.AMBATO_CENTER_LNG))
    }

    @Test
    fun `parsea un MultiPolygon con varios anillos exteriores`() {
        val zones = squareZones(
            """
            {"type":"MultiPolygon","coordinates":[
              [[[-78.66,-1.26],[-78.59,-1.26],[-78.59,-1.22],[-78.66,-1.22],[-78.66,-1.26]]],
              [[[-78.5,-1.4],[-78.45,-1.4],[-78.45,-1.35],[-78.5,-1.35],[-78.5,-1.4]]]
            ]}
            """.trimIndent()
        )!!

        assertEquals(2, zones.polygons.size)
        assertTrue(zones.contains(UrbanPerimeter.AMBATO_CENTER_LAT, UrbanPerimeter.AMBATO_CENTER_LNG))
        assertTrue(zones.contains(-1.38, -78.47))
    }

    @Test
    fun `respeta los agujeros del poligono`() {
        val zones = squareZones(
            """
            {"type":"Polygon","coordinates":[
              [[-78.66,-1.26],[-78.59,-1.26],[-78.59,-1.22],[-78.66,-1.22],[-78.66,-1.26]],
              [[-78.64,-1.25],[-78.61,-1.25],[-78.61,-1.23],[-78.64,-1.23],[-78.64,-1.25]]
            ]}
            """.trimIndent()
        )!!

        assertTrue(zones.contains(-1.24, -78.65))
        // Un punto dentro del agujero NO es zona permitida.
        assertFalse(zones.contains(-1.24, -78.625))
    }

    @Test
    fun `json invalido devuelve null (fail-open)`() {
        assertNull(GeoJsonZones.parse("no soy json"))
        assertNull(GeoJsonZones.parse("{\"type\":\"FeatureCollection\",\"features\":[]}"))
        assertNull(GeoJsonZones.parse("{\"type\":\"Point\",\"coordinates\":[-78.6,-1.24]}"))
        assertNull(GeoJsonZones.parse("{\"type\":\"Polygon\",\"coordinates\":[]}"))
    }

    @Test
    fun `poligono con menos de 3 vertices se descarta`() {
        val zones = squareZones(
            """
            {"type":"Polygon","coordinates":[[[-78.66,-1.26],[-78.59,-1.26]]]}
            """.trimIndent()
        )

        assertNull(zones)
    }

    // ═══════════════════════════════════════════════════════
    //  CONTENCIÓN (ray casting)
    // ═══════════════════════════════════════════════════════

    @Test
    fun `punto dentro del poligono es zona permitida`() {
        val zones = squareZones(barePolygonSquare)!!

        assertTrue(zones.contains(-1.24, -78.63))
        assertTrue(zones.contains(-1.25, -78.60))
    }

    @Test
    fun `punto fuera del poligono no es zona permitida`() {
        val zones = squareZones(barePolygonSquare)!!

        // Pelileo, al sureste del cuadrado.
        assertFalse(zones.contains(-1.33, -78.54))
        // Al norte del cuadrado.
        assertFalse(zones.contains(-1.20, -78.62))
        // Al oeste del cuadrado.
        assertFalse(zones.contains(-1.24, -78.70))
    }

    @Test
    fun `punto en el borde del poligono se considera dentro`() {
        val zones = squareZones(barePolygonSquare)!!

        // Vértice suroeste del cuadrado.
        assertTrue(zones.contains(-1.26, -78.66))
        // En el lado sur.
        assertTrue(zones.contains(-1.26, -78.62))
    }

    @Test
    fun `outsideReason anuncia zona prohibida y no depende del punto`() {
        val zones = squareZones(barePolygonSquare)!!

        val reason = zones.outsideReason(-1.33, -78.54)

        assertTrue(reason.contains("Zona prohibida"))
        assertTrue(reason.contains("perímetro urbano"))
    }

    @Test
    fun `el asset empaquetado de zonas permite se parsea y contiene el centro de Ambato`() {
        val file = java.io.File("src/main/assets/${GeoJsonZones.ASSET_NAME}")
        assertTrue("El asset ${GeoJsonZones.ASSET_NAME} debe existir en app/src/main/assets", file.exists())

        val zones = GeoJsonZones.parse(file.readText())
        assertNotNull("El asset debe ser un GeoJSON válido parseable", zones)
        assertTrue(zones!!.polygons.isNotEmpty())

        val inBounds = zones.contains(
            UrbanPerimeter.AMBATO_CENTER_LAT,
            UrbanPerimeter.AMBATO_CENTER_LNG
        )
        assertTrue("El centro de Ambato debe caer dentro del perímetro oficial", inBounds)
    }

    // ═══════════════════════════════════════════════════════
    //  DOCUMENTOS DE PRUEBA
    // ═══════════════════════════════════════════════════════

    private val barePolygonSquare = """
        {"type":"Polygon","coordinates":[[[-78.66,-1.26],[-78.59,-1.26],
         [-78.59,-1.22],[-78.66,-1.22],[-78.66,-1.26]]]}
    """.trimIndent()

    private val featureCollectionSquare = """
        {"type":"FeatureCollection","features":[
            {"type":"Feature","properties":{},"geometry":{"type":"Polygon","coordinates":[
                [[-78.66,-1.26],[-78.59,-1.26],[-78.59,-1.22],[-78.66,-1.22],[-78.66,-1.26]]
            ]}}
        ]}
    """.trimIndent()
}