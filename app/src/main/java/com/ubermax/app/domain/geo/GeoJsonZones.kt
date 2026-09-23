package com.ubermax.app.domain.geo

import org.json.JSONArray
import org.json.JSONObject

/** Una pareja (lat, lng) de un vértice del polígono. */
typealias GeoVertex = Pair<Double, Double>

/** Anillo: lista de vértices en orden. */
typealias GeoRing = List<GeoVertex>

/** Polígono: el anillo índice 0 es el exterior; el resto son agujeros. */
typealias GeoPolygons = List<GeoRing>

/**
 * Zonas permitidas del conductor definidas por geometría GeoJSON.
 *
 * El archivo `assets/zonas-permitidasv3.geojson` (bundled en el APK) representa
 * el perímetro urbano "oficial" que el usuario configuró. Cuando un destino
 * geocodificado cae FUERA de cualquier polígono, [outsideReason] devuelve el
 * mensaje de rechazo pasivo "zona prohibida": la app avisa (WARN) pero jamás
 * pulsa la X.
 *
 * Soporta `FeatureCollection`, `Feature` y geometrías `Polygon` / `MultiPolygon`
 * en un solo JSON. Los vértices usan el orden estándar GeoJSON **[lng, lat]**.
 * 100% JVM puro (org.json está disponible en Android y en los tests).
 */
class GeoJsonZones(
    val polygons: List<GeoPolygons>
) : UrbanBoundary {

    /** True si [lat]/[lng] cae dentro del exterior de algún polígono y fuera
     *  de sus agujeros (ray casting, paridad par = fuera). */
    override fun contains(lat: Double, lng: Double): Boolean =
        polygons.any { polygon ->
            val exterior = polygon.firstOrNull()
            exterior != null &&
                pointInRing(exterior, lat, lng) &&
                polygon.drop(1).none { pointInRing(it, lat, lng) }
        }

    override fun outsideReason(lat: Double, lng: Double): String =
        "🚫 Zona prohibida — destino fuera del perímetro urbano"

    companion object {

        /** Nombre del asset bundled que contiene las zonas permitidas. */
        const val ASSET_NAME = "zonas-permitidasv3.geojson"

        /**
         * Parsea un documento GeoJSON y devuelve las zonas, o null si no hay
         * ningún polígono utilizable (fail-open: sin zonas se omite el filtro).
         */
        fun parse(json: String): GeoJsonZones? {
            return try {
                val root = JSONObject(json)
                val zones = mutableListOf<GeoPolygons>()
                when (root.optString("type")) {
                    "FeatureCollection" -> {
                        val features = root.optJSONArray("features")
                        if (features != null) {
                            for (i in 0 until features.length()) {
                                features.optJSONObject(i)
                                    ?.optJSONObject("geometry")
                                    ?.let { geometry -> collectGeometry(geometry, zones) }
                            }
                        }
                    }
                    "Feature" -> root.optJSONObject("geometry")
                        ?.let { geometry -> collectGeometry(geometry, zones) }
                    else -> collectGeometry(root, zones)
                }
                if (zones.isEmpty()) null else GeoJsonZones(zones)
            } catch (e: Exception) {
                null
            }
        }

        /** Añade los polígonos de una geometría Polygon/MultiPolygon a [zones]. */
        private fun collectGeometry(geometry: JSONObject, zones: MutableList<GeoPolygons>) {
            when (geometry.optString("type")) {
                "Polygon" -> parsePolygon(geometry.optJSONArray("coordinates"))?.let(zones::add)
                "MultiPolygon" -> {
                    val coordinates = geometry.optJSONArray("coordinates")
                    if (coordinates != null) {
                        for (i in 0 until coordinates.length()) {
                            parsePolygon(coordinates.optJSONArray(i))?.let(zones::add)
                        }
                    }
                }
            }
        }

        /** Parsea los anillos de un Polygon (coordenadas = array de anillos). */
        private fun parsePolygon(coordinates: JSONArray?): GeoPolygons? {
            if (coordinates == null || coordinates.length() == 0) return null
            val rings = mutableListOf<GeoRing>()
            for (i in 0 until coordinates.length()) {
                val ringJson = coordinates.optJSONArray(i) ?: continue
                val ring = mutableListOf<GeoVertex>()
                for (j in 0 until ringJson.length()) {
                    val vertex = ringJson.optJSONArray(j) ?: continue
                    if (vertex.length() < 2) continue
                    val lng = vertex.optDouble(0, Double.NaN)
                    val lat = vertex.optDouble(1, Double.NaN)
                    if (!lng.isNaN() && !lat.isNaN()) {
                        ring += lat to lng
                    }
                }
                if (ring.size >= 3) rings += ring
            }
            return if (rings.isEmpty()) null else rings
        }

        /**
         * Punto en anillo por ray casting (even-odd): se traza un rayo desde el
         * punto hacia +inf en X y se cuentan cruces con los lados del polígono.
         * Los vértices se modelan como (lat, lng) → (y, x).
         */
        private fun pointInRing(ring: GeoRing, lat: Double, lng: Double): Boolean {
            var inside = false
            var j = ring.size - 1
            for (i in ring.indices) {
                val yi = ring[i].first
                val xi = ring[i].second
                val yj = ring[j].first
                val xj = ring[j].second
                val crosses = (xi > lng) != (xj > lng)
                if (crosses) {
                    val slope = (yj - yi) / (xj - xi)
                    if (lat < slope * (lng - xi) + yi) {
                        inside = !inside
                    }
                }
                j = i
            }
            return inside
        }
    }
}