package com.ubermax.app.data.geocoding

import com.ubermax.app.domain.geometry.PolygonGeometry
import com.ubermax.app.util.Logs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resultado de reverse geocoding de un punto geográfico.
 */
data class NominatimResult(
    val displayName: String,
    val road: String?,
    val neighbourhood: String?,
    val suburb: String?,
    val city: String?,
    val state: String?,
    val country: String?
)

/**
 * Cliente de reverse geocoding usando la API de Nominatim (OpenStreetMap).
 *
 * Política de uso de Nominatim:
 * - Máximo 1 request/segundo (implementado con cola FIFO + delay)
 * - User-Agent requerido y debe identificar correctamente la app
 * - Solo para uso personal / bajo volumen
 *
 * La extracción de keywords de un polígono funciona así:
 * 1. Genera una malla de puntos dentro del polígono (grid de ~200m)
 * 2. Reverse geocoding de cada punto con rate limiting
 * 3. Extrae calles, barrios y ciudades únicos
 * 4. Retorna el set de keywords listas para la lista negra
 */
@Singleton
class NominatimGeocoder @Inject constructor() {

    companion object {
        private const val TAG = "NominatimGeocoder"

        private const val BASE_URL = "https://nominatim.openstreetmap.org/reverse"
        private const val RATE_LIMIT_MS = 1100L
        private const val TIMEOUT_SECONDS = 15L
        private const val USER_AGENT = "UberMax/1.0 (personal use; ubermax-driver-app)"

        // Mínimo de letras significativas para considerar una keyword útil
        private const val MIN_KEYWORD_LENGTH = 4
        private const val MAX_POINTS_PER_POLYGON = 40
        private const val DEFAULT_GRID_SPACING_METERS = 200.0
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Reverse geocoding de un punto geográfico a dirección legible.
     *
     * @return [NominatimResult] con la dirección estructurada, o null si falla
     */
    suspend fun reverseGeocode(lat: Double, lng: Double): NominatimResult? {
        return withContext(Dispatchers.IO) {
            try {
                val url = "$BASE_URL?format=jsonv2&lat=$lat&lon=$lng&addressdetails=1&zoom=18"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Logs.w(TAG, "Nominatim HTTP ${response.code} para ($lat, $lng)")
                        return@use null
                    }

                    val body = response.body?.string() ?: return@use null
                    val json = JSONObject(body)
                    parseNominatimResponse(json)
                }
            } catch (e: Exception) {
                Logs.e(TAG, "Reverse geocode error: ${e.message}")
                null
            }
        }
    }

    /**
     * Extrae keywords (calles, barrios, ciudades) de un polígono.
     * Genera una malla de puntos y geocodes cada uno.
     *
     * @param polygon Vértices del polígono (lat, lng)
     * @param onProgress Callback de progreso (puntosProcesados, puntosTotales)
     * @return Set de keywords únicas extraídas
     */
    suspend fun extractKeywordsFromPolygon(
        polygon: List<Pair<Double, Double>>,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): Set<String> {
        if (polygon.size < 3) return emptySet()

        return withContext(Dispatchers.IO) {
            // Generar malla de puntos dentro del polígono (limitada)
            val gridPoints = PolygonGeometry.generateGridPoints(polygon, DEFAULT_GRID_SPACING_METERS)
            val limitedPoints = gridPoints.take(MAX_POINTS_PER_POLYGON)

            if (limitedPoints.isEmpty()) {
                // Polígono muy pequeño: al menos geocode el centroide
                val centroid = polygonCentroid(polygon)
                val singleResult = reverseGeocodeBounded(centroid.first, centroid.second)
                return@withContext if (singleResult != null) {
                    extractKeywordsFromResult(singleResult)
                } else {
                    emptySet()
                }
            }

            val keywords = mutableSetOf<String>()

            limitedPoints.forEachIndexed { index, (lat, lng) ->
                val result = reverseGeocodeBounded(lat, lng)
                if (result != null) {
                    keywords.addAll(extractKeywordsFromResult(result))
                }
                onProgress(index + 1, limitedPoints.size)
            }

            keywords
        }
    }

    /**
     * Reverse geocoding con rate limiting garantizado (mínimo 1s entre requests).
     */
    private suspend fun reverseGeocodeBounded(lat: Double, lng: Double): NominatimResult? {
        val result = reverseGeocode(lat, lng)
        delay(RATE_LIMIT_MS) // Espaciado entre llamadas
        return result
    }

    /**
     * Convierte un resultado de Nominatim en keywords útiles para la lista negra.
     */
    private fun extractKeywordsFromResult(result: NominatimResult): Set<String> {
        val keywords = mutableSetOf<String>()

        // Prioridad: calles > barrios > suburbios > ciudades
        result.road?.let { if (it.length >= MIN_KEYWORD_LENGTH) keywords.add(it) }
        result.neighbourhood?.let { if (it.length >= MIN_KEYWORD_LENGTH) keywords.add(it) }
        result.suburb?.let { if (it.length >= MIN_KEYWORD_LENGTH) keywords.add(it) }
        result.city?.let { if (it.length >= MIN_KEYWORD_LENGTH) keywords.add(it) }

        return keywords
    }

    /**
     * Calcula el centroide de un polígono.
     */
    private fun polygonCentroid(polygon: List<Pair<Double, Double>>): Pair<Double, Double> {
        if (polygon.isEmpty()) return 0.0 to 0.0

        val lats = polygon.map { it.first }
        val lngs = polygon.map { it.second }

        return (lats.sum() / lats.size) to (lngs.sum() / lngs.size)
    }

    /**
     * Parsea la respuesta JSON de Nominatim a [NominatimResult].
     */
    private fun parseNominatimResponse(json: JSONObject): NominatimResult {
        val address = json.optJSONObject("address")

        fun optText(key: String): String? =
            address?.optString(key)?.takeIf { it.isNotEmpty() }

        return NominatimResult(
            displayName = json.optString("display_name", ""),
            road = optText("road"),
            neighbourhood = optText("neighbourhood"),
            suburb = optText("suburb"),
            city = optText("city")
                ?: optText("town")
                ?: optText("village"),
            state = optText("state"),
            country = optText("country")
        )
    }
}