package com.ubermax.app.domain.geometry

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests unitarios JVM puros para PolygonGeometry.
 * Sin dependencias Android — ejecuta con testDebugUnitTest.
 */
class PolygonGeometryTest {

    // Polígono cuadrado simple: cuadrante noroeste de Ambato
    // Vértices en sentido antihorario
    private val squarePolygon = listOf(
        -1.2300 to -78.6200,  // noroeste
        -1.2300 to -78.6100,  // noreste
        -1.2400 to -78.6100,  // sureste
        -1.2400 to -78.6200   // suroeste
    )

    // Polígono triangular
    private val trianglePolygon = listOf(
        -1.2400 to -78.6200,  // vértice 1
        -1.2350 to -78.6100,  // vértice 2
        -1.2450 to -78.6100   // vértice 3
    )

    // Polígono concavo (forma de L)
    private val concavePolygon = listOf(
        -1.2300 to -78.6200,
        -1.2300 to -78.6150,
        -1.2350 to -78.6150,
        -1.2350 to -78.6100,
        -1.2400 to -78.6100,
        -1.2400 to -78.6200
    )

    // ==================== isPointInPolygon ====================

    @Test
    fun `punto dentro del cuadrado - retorna true`() {
        val point = -1.2350 to -78.6150  // centro del cuadrado
        assertTrue(PolygonGeometry.isPointInPolygon(point.first, point.second, squarePolygon))
    }

    @Test
    fun `punto fuera del cuadrado - retorna false`() {
        val point = -1.2500 to -78.6300  // fuera del cuadrado
        assertFalse(PolygonGeometry.isPointInPolygon(point.first, point.second, squarePolygon))
    }

    @Test
    fun `punto en el borde del cuadrado - comportamiento indefinido`() {
        // En el borde exacto, el algoritmo puede retornar true o false
        // Lo importante es que no crashee
        val point = -1.2300 to -78.6150  // borde norte
        val result = PolygonGeometry.isPointInPolygon(point.first, point.second, squarePolygon)
        // Solo verificamos que no lanza excepción
        assertNotNull(result)
    }

    @Test
    fun `punto dentro del triangulo - retorna true`() {
        val point = -1.2400 to -78.6130  // dentro del triángulo
        assertTrue(PolygonGeometry.isPointInPolygon(point.first, point.second, trianglePolygon))
    }

    @Test
    fun `punto fuera del triangulo - retorna false`() {
        val point = -1.2300 to -78.6130  // arriba del triángulo
        assertFalse(PolygonGeometry.isPointInPolygon(point.first, point.second, trianglePolygon))
    }

    @Test
    fun `punto dentro del poligono concavo - retorna true`() {
        // Punto en la parte inferior de la L (donde debería estar dentro)
        val point = -1.2380 to -78.6150
        assertTrue(PolygonGeometry.isPointInPolygon(point.first, point.second, concavePolygon))
    }

    @Test
    fun `punto en el hueco del concavo - retorna false`() {
        // Punto en el hueco de la L
        val point = -1.2320 to -78.6120
        assertFalse(PolygonGeometry.isPointInPolygon(point.first, point.second, concavePolygon))
    }

    @Test
    fun `poligono con menos de 3 vertices - retorna false`() {
        val tinyPolygon = listOf(-1.2400 to -78.6200, -1.2350 to -78.6100)
        assertFalse(PolygonGeometry.isPointInPolygon(-1.2370, -78.6150, tinyPolygon))
    }

    @Test
    fun `poligono vacio - retorna false`() {
        assertFalse(PolygonGeometry.isPointInPolygon(-1.2370, -78.6150, emptyList()))
    }

    // ==================== haversineDistance ====================

    @Test
    fun `distancia entre puntos identicos es cero`() {
        val dist = PolygonGeometry.haversineDistance(-1.2400, -78.6200, -1.2400, -78.6200)
        assertEquals(0.0, dist, 0.01)
    }

    @Test
    fun `distancia conocida - Ambato centro a 1km sur`() {
        // 1 grado de latitud ≈ 111,320 metros
        // 0.009 grados ≈ 1 km
        val dist = PolygonGeometry.haversineDistance(-1.2400, -78.6200, -1.2490, -78.6200)
        assertEquals(1000.0, dist, 50.0)  //容忍 50m de error
    }

    @Test
    fun `distancia conocida - Ambato centro a 1km este`() {
        // En latitud -1.24, 1 grado de longitud ≈ 111,280 metros
        val dist = PolygonGeometry.haversineDistance(-1.2400, -78.6200, -1.2400, -78.6110)
        assertEquals(1000.0, dist, 50.0)
    }

    // ==================== generateGridPoints ====================

    @Test
    fun `genera puntos dentro del cuadrado`() {
        val points = PolygonGeometry.generateGridPoints(squarePolygon, 300.0)
        assertTrue("Debería generar al menos 1 punto", points.isNotEmpty())

        // Todos los puntos deben estar dentro del polígono
        for ((lat, lng) in points) {
            assertTrue(
                "Punto ($lat, $lng) debería estar dentro del polígono",
                PolygonGeometry.isPointInPolygon(lat, lng, squarePolygon)
            )
        }
    }

    @Test
    fun `puntos generados son unicos`() {
        val points = PolygonGeometry.generateGridPoints(squarePolygon, 300.0)
        val uniquePoints = points.toSet()
        assertEquals("No debería haber duplicados", points.size, uniquePoints.size)
    }

    @Test
    fun `espaciado menor genera mas puntos`() {
        val pointsLarge = PolygonGeometry.generateGridPoints(squarePolygon, 500.0)
        val pointsSmall = PolygonGeometry.generateGridPoints(squarePolygon, 200.0)
        assertTrue("Espaciado menor debería generar más puntos", pointsSmall.size >= pointsLarge.size)
    }

    @Test
    fun `poligono muy pequeño genera pocos puntos`() {
        val tinyPolygon = listOf(
            -1.2400 to -78.6200,
            -1.2400 to -78.6195,
            -1.2405 to -78.6195,
            -1.2405 to -78.6200
        )
        val points = PolygonGeometry.generateGridPoints(tinyPolygon, 200.0)
        // Con 200m de espaciado en un polígino de ~50m, debería haber 0-1 puntos
        assertTrue("Polígono tiny debería tener pocos puntos", points.size <= 2)
    }

    // ==================== parsePolygonJson / serializePolygon ====================

    @Test
    fun `serialize y parse son inversos`() {
        val original = listOf(
            -1.2300 to -78.6200,
            -1.2300 to -78.6100,
            -1.2400 to -78.6100,
            -1.2400 to -78.6200
        )

        val json = PolygonGeometry.serializePolygon(original)
        val parsed = PolygonGeometry.parsePolygonJson(json)

        assertEquals(original.size, parsed.size)
        for (i in original.indices) {
            assertEquals(original[i].first, parsed[i].first, 0.0001)
            assertEquals(original[i].second, parsed[i].second, 0.0001)
        }
    }

    @Test
    fun `parse JSON vacio retorna lista vacia`() {
        val parsed = PolygonGeometry.parsePolygonJson("[]")
        assertTrue(parsed.isEmpty())
    }

    @Test
    fun `parse JSON invalido retorna lista vacia`() {
        val parsed = PolygonGeometry.parsePolygonJson("not json")
        assertTrue(parsed.isEmpty())
    }

    @Test
    fun `serialize punto unico`() {
        val points = listOf(-1.2400 to -78.6200)
        val json = PolygonGeometry.serializePolygon(points)
        assertEquals("[[-1.24,-78.62]]", json)
    }

    // ==================== isPointInAnyPolygonZone ====================

    @Test
    fun `punto dentro de zona poligonal - retorna nombre`() {
        val zones = listOf(
            "Ficoa" to squarePolygon,
            "Huachi" to trianglePolygon
        )

        val result = PolygonGeometry.isPointInAnyPolygonZone(-1.2350, -78.6150, zones)
        assertEquals("Ficoa", result)
    }

    @Test
    fun `punto fuera de todas las zonas - retorna null`() {
        val zones = listOf(
            "Ficoa" to squarePolygon,
            "Huachi" to trianglePolygon
        )

        val result = PolygonGeometry.isPointInAnyPolygonZone(-1.2500, -78.6300, zones)
        assertNull(result)
    }

    // ==================== polygonAreaMeters ====================

    @Test
    fun `area del cuadrado es razonable`() {
        val area = PolygonGeometry.polygonAreaMeters(squarePolygon)
        // Cuadrado de ~1km x ~1km ≈ 1,000,000 m²
        assertTrue("Área debería ser > 500,000 m²", area > 500_000)
        assertTrue("Área debería ser < 2,000,000 m²", area < 2_000_000)
    }

    @Test
    fun `area de poligono vacio es cero`() {
        assertEquals(0.0, PolygonGeometry.polygonAreaMeters(emptyList()), 0.01)
    }
}
