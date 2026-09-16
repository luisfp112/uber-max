package com.ubermax.app.domain.geometry

import org.json.JSONArray
import kotlin.math.*

/**
 * Utilidades geométricas puras para polígonos de lista negra.
 * 100% JVM — sin dependencias Android. Totalmente testeable con JUnit.
 *
 * Algoritmo de ray casting para point-in-polygon:
 * https://en.wikipedia.org/wiki/Point_in_polygon
 */
object PolygonGeometry {

    /**
     * Radio de la Tierra en metros (WGS84).
     */
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    /**
     * Determina si un punto (lat, lng) está dentro de un polígono usando ray casting.
     * O(n) donde n = número de vértices.
     *
     * El polígono se asume cerrado (el último vértice se conecta al primero).
     *
     * @param lat Latitud del punto a verificar
     * @param lng Longitud del punto a verificar
     * @param polygon Lista de vértices (lat, lng) en orden horario o antihorario
     * @return true si el punto está dentro del polígono
     */
    fun isPointInPolygon(lat: Double, lng: Double, polygon: List<Pair<Double, Double>>): Boolean {
        if (polygon.size < 3) return false

        var inside = false
        val n = polygon.size

        var j = n - 1
        for (i in 0 until n) {
            // En el sistema geográfico: lat = eje Y, lng = eje X
            val (latI, lngI) = polygon[i]
            val (latJ, lngJ) = polygon[j]

            // Ray casting: el rayo horizontal cruza el segmento (i, j)
            val intersect = ((latI > lat) != (latJ > lat)) &&
                    (lng < (lngJ - lngI) * (lat - latI) / (latJ - latI) + lngI)

            if (intersect) {
                inside = !inside
            }
            j = i
        }

        return inside
    }

    /**
     * Distancia Haversine entre dos puntos en metros.
     * Precisión: ~0.3% de error (suficiente para geocoding urbano).
     */
    fun haversineDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)

        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLng / 2).pow(2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return EARTH_RADIUS_METERS * c
    }

    /**
     * Genera una malla de puntos dentro del bounding box del polígono,
     * filtrando solo los que caen dentro del polígono real.
     *
     * @param polygon Vértices del polígono
     * @param spacingMeters Espaciado entre puntos en metros (mínimo 100m)
     * @return Lista de puntos (lat, lng) dentro del polígono
     */
    fun generateGridPoints(
        polygon: List<Pair<Double, Double>>,
        spacingMeters: Double = 200.0
    ): List<Pair<Double, Double>> {
        if (polygon.size < 3) return emptyList()

        val safeSpacing = spacingMeters.coerceAtLeast(100.0)

        // Calcular bounding box
        val lats = polygon.map { it.first }
        val lngs = polygon.map { it.second }

        val minLat = lats.min()
        val maxLat = lats.max()
        val minLng = lngs.min()
        val maxLng = lngs.max()

        // Convertir espaciado a grados (aproximación para latitudes ecuatoriales)
        val latStep = metersToLatitudeDegrees(safeSpacing)
        val lngStep = metersToLongitudeDegrees(safeSpacing, (minLat + maxLat) / 2)

        val points = mutableListOf<Pair<Double, Double>>()

        var lat = minLat
        while (lat <= maxLat) {
            var lng = minLng
            while (lng <= maxLng) {
                if (isPointInPolygon(lat, lng, polygon)) {
                    points.add(lat to lng)
                }
                lng += lngStep
            }
            lat += latStep
        }

        return points
    }

    /**
     * Verifica si un punto está dentro de alguna zona poligonal de la lista.
     *
     * @param lat Latitud del punto
     * @param lng Longitud del punto
     * @param zones Lista (nombre, polígono) de zonas
     * @return Nombre de la zona que hizo match, o null
     */
    fun isPointInAnyPolygonZone(
        lat: Double,
        lng: Double,
        zones: List<Pair<String, List<Pair<Double, Double>>>>
    ): String? {
        for ((name, polygon) in zones) {
            if (polygon.size >= 3 && isPointInPolygon(lat, lng, polygon)) {
                return name
            }
        }
        return null
    }

    /**
     * Convierte un círculo legacy (centro + radio) a un polígono cuadrado
     * con el radio como semi-lado. Usado para compatibilidad con zonas
     * guardadas antes de la versión de polígonos.
     */
    fun circleToSquarePolygon(centerLat: Double, centerLng: Double, radiusMeters: Double): List<Pair<Double, Double>> {
        if (radiusMeters <= 0.0) return emptyList()

        val latOffset = radiusMeters / 111_320.0
        val lngOffset = radiusMeters / (111_320.0 * cos(Math.toRadians(centerLat)))

        return listOf(
            (centerLat - latOffset) to (centerLng - lngOffset),
            (centerLat + latOffset) to (centerLng - lngOffset),
            (centerLat + latOffset) to (centerLng + lngOffset),
            (centerLat - latOffset) to (centerLng + lngOffset)
        )
    }

    /**
     * Parsea un JSON de polígono a lista de puntos.
     * Formato: "[[lat1,lng1],[lat2,lng2],...]"
     */
    fun parsePolygonJson(json: String): List<Pair<Double, Double>> {
        if (json.isBlank() || json == "[]") return emptyList()

        return try {
            val array = JSONArray(json)
            val points = mutableListOf<Pair<Double, Double>>()

            for (i in 0 until array.length()) {
                val pair = array.getJSONArray(i)
                val lat = pair.getDouble(0)
                val lng = pair.getDouble(1)
                points.add(lat to lng)
            }

            points
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Serializa una lista de puntos a JSON.
     * Formato: "[[lat1,lng1],[lat2,lng2],...]"
     */
    fun serializePolygon(polygon: List<Pair<Double, Double>>): String {
        val array = JSONArray()

        for ((lat, lng) in polygon) {
            val pair = JSONArray()
            pair.put(lat)
            pair.put(lng)
            array.put(pair)
        }

        return array.toString()
    }

    /**
     * Calcula el área aproximada de un polígono en metros cuadrados
     * usando la fórmula de shoelace con coordenadas geográficas.
     */
    fun polygonAreaMeters(polygon: List<Pair<Double, Double>>): Double {
        if (polygon.size < 3) return 0.0

        var area = 0.0
        val n = polygon.size

        for (i in 0 until n) {
            val j = (i + 1) % n
            val (latI, lngI) = polygon[i]
            val (latJ, lngJ) = polygon[j]

            area += Math.toRadians(lngJ - lngI) * (2 + sin(Math.toRadians(latI)) + sin(Math.toRadians(latJ)))
        }

        area = abs(area) * EARTH_RADIUS_METERS * EARTH_RADIUS_METERS / 2.0

        return area
    }

    /**
     * Convierte metros a grados de latitud (aproximación lineal).
     */
    private fun metersToLatitudeDegrees(meters: Double): Double {
        return meters / 111_320.0
    }

    /**
     * Convierte metros a grados de longitud (depende de la latitud).
     */
    private fun metersToLongitudeDegrees(meters: Double, atLatitude: Double): Double {
        return meters / (111_320.0 * cos(Math.toRadians(atLatitude)))
    }
}
