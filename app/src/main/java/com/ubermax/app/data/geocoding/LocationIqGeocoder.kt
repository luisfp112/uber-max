package com.ubermax.app.data.geocoding

import com.ubermax.app.util.AddressSanitizer
import com.ubermax.app.util.Logs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.Locale

/**
 * Cliente de la API de Forward Geocoding de LocationIQ.
 *
 * Convierte el texto de una ubicación (recogida o destino) de una oferta en
 * coordenadas lat/lng para que la [com.ubermax.app.domain.geo.UrbanPerimeter]
 * pueda verificar si queda dentro de las zonas permitidas del conductor.
 *
 * La parte pura ([parseLatLng], [parseResult], [buildQuery], [nameMatches]) se
 * testea en JVM sin red; la llamada de red ([geocode]) es fail-open: ante
 * cualquier error devuelve `null` y el pipeline avisa "ubicación no verificada"
 * (nunca auto-acepta sin poder confirmar la zona).
 *
 * LocationIQ, cuando no encuentra la calle, NO devuelve vacío sino el PUNTO
 * CENTRAL de la ciudad. Para no aceptar por error una ubicación que no existe:
 * la respuesta se contrasta con [nameMatches] (tokens significativos de la
 * dirección vs. `display_name`). Si no hay coincidencia → se descarta como no
 * verificable.
 */
object LocationIqGeocoder {

    private const val TAG = "LocationIQ"
    private const val ENDPOINT = "https://us1.locationiq.com/v1/search"
    private const val FORMAT = "json"

    // Timeout estricto: la decisión geo es oportunista. Si la red tarda más de
    // 1.5s se degrada de inmediato al flujo de texto (fail-open: punto sin
    // coordenadas → WARN "no confirmado") sin retrasar el resto del pipeline.
    private const val CONNECT_TIMEOUT_MS = 1_500
    private const val READ_TIMEOUT_MS = 1_500

    /**
     * Arma la query de búsqueda a partir del texto de destino de la tarjeta.
     *
     * 1. [AddressCleaner] elimina tarifas, ratings, métricas de tiempo/distancia,
     *    countdowns y etiquetas de Uber que a veces se pegan al destino.
     * 2. Si tras limpiar quedan menos de [AddressCleaner.MIN_VALID_CHARS]
     *    caracteres válidos devuelve "" → la llamada a la API se aborta y se
     *    aplica el fallback local (fail-open: se omite el filtro del perímetro).
     * 3. La query final se formatea estrictamente como
     *    `"{DIRECCIÓN_LIMPIA}, Ambato, Ecuador"`.
     */
    fun buildQuery(destination: String): String {
        val cleanAddress = AddressCleaner.clean(destination)
        if (!AddressCleaner.isUsable(cleanAddress)) return ""
        return "${withoutCitySuffix(cleanAddress)}, Ambato, Ecuador"
    }

    /** Evita duplicar la ciudad: quita un "Ambato[, Ecuador]" final si viniera en la dirección. */
    private fun withoutCitySuffix(address: String): String {
        var t = address.trim()
        t = t.replace(Regex("""\s*,\s*ambato\s*,\s*ecuador\s*$""", RegexOption.IGNORE_CASE), "")
        t = t.replace(Regex("""\s*,\s*ambato\s*$""", RegexOption.IGNORE_CASE), "")
        t = t.replace(Regex("""\s+ambato\s+ecuador\s*$""", RegexOption.IGNORE_CASE), "")
        return t.trim().trim(',').trim()
    }

    /** Resultado enriquecido con el nombre legible devuelto por la API. */
    data class GeoResult(val lat: Double, val lng: Double, val displayName: String)

    /**
     * Extrae los candidatos `lat`/`lon`/`display_name` del JSON de LocationIQ.
     *
     * Respuesta real (array de resultados, ordenados por relevancia):
     *   [
     *     {"lat":"-1.243288","lon":"-78.626709","display_name":"Av. Los Guaytambos, Ambato, ..."},
     *     ...
     *   ]
     *
     * Devuelve los resultados con coordenadas válidas, o lista vacía si el JSON
     * no se puede parsear. Con `limit=1` LocationIQ a veces degrada un cruce de
     * calles "X y Y" al centroide de la ciudad (o de otra parroquia); tener todos
     * los candidatos permite elegir el que realmente corresponde a la dirección.
     */
    fun parseResults(json: String): List<GeoResult> {
        if (json.isBlank()) return emptyList()
        return try {
            val root = if (json.trimStart().startsWith('{')) JSONArray("[$json]") else JSONArray(json)
            buildList {
                for (i in 0 until root.length()) {
                    val obj = root.optJSONObject(i) ?: continue
                    val lat = obj.optString("lat", "").toDoubleOrNull() ?: continue
                    val lng = obj.optString("lon", "").toDoubleOrNull() ?: continue
                    if (lat !in -90.0..90.0 || lng !in -180.0..180.0) continue
                    add(GeoResult(lat, lng, obj.optString("display_name", "")))
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Extrae el primer par `"lat","lon"` del JSON de LocationIQ (array de resultados).
     *
     * Devuelve (lat, lng) o null si no hay resultado / el JSON no matchea.
     */
    fun parseLatLng(json: String): Pair<Double, Double>? =
        parseResults(json).firstOrNull()?.let { it.lat to it.lng }

    /**
     * Como [parseLatLng] pero conserva el `display_name` para verificar después
     * que la respuesta corresponde realmente a la dirección buscada. El nombre se
     * decodifica con un parser JSON real: LocationIQ manda los acentos escapados
     * (`C\u00f3ndor`) que una regex cruda dejaría literales y rompería la
     * verificación por tokens.
     */
    fun parseResult(json: String): GeoResult? = parseResults(json).firstOrNull()

    /**
     * Tokens significativos de un texto (palabras ≥ 3 letras que no sean
     * conectores ni abreviaturas viales, con acentos normalizados).
     */
    fun significantTokens(text: String): List<String> {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("""\p{M}"""), "")
            .lowercase(Locale.ROOT)
        return normalized
            .split(Regex("""[^a-z0-9]+"""))
            .filter { it.length >= 3 && it !in STOPWORDS }
    }

    /**
     * ¿La respuesta de la API coincide con la dirección buscada?
     *
     * LocationIQ cae al centroide de la ciudad cuando no encuentra la calle
     * (p.ej. "Heiraway, Oscar Wilde, Ambato, Ecuador" → "Ambato, Ambato, ...").
     * Sin esta verificación ese centroide se trataría como punto válido y una
     * recogida inexistente "pasaría" el filtro de zona. Si ningún token de la
     * dirección aparece en el `display_name` → la ubicación NO es verificable.
     */
    fun nameMatches(address: String, displayName: String): Boolean {
        val tokens = significantTokens(address)
        if (tokens.isEmpty()) return false
        val display = significantTokens(displayName).toSet()
        return tokens.any { it in display }
    }

    /**
     * Divide un cruce de calles ("X y Y") en sus calles componentes.
     *
     * LocationIQ a veces NO entiende el texto combinado de la tarjeta de Uber en
     * Ecuador ("Av. Victor Hugo y Batalla de Tarqui, Ambato, Ecuador" → centroide
     * de "La Victoria, Pelileo"); en cambio cada calle por separado sí se
     * geocodifica. Este split alimenta el fallback de [geocode].
     *
     * Se separa por " y " y por comas, y se descartan los nombres de ciudad/
     * provincia que a veces se pegan ("..., Ambato, Ecuador").
     */
    fun streetComponents(address: String): List<String> {
        val cleanAddress = AddressCleaner.clean(address)
        return cleanAddress
            .split(Regex("""\s+y\s+|\s*,\s*"""))
            .map { it.trim().trim(',', '-', ' ').trim() }
            .filter {
                it.length >= AddressCleaner.MIN_VALID_CHARS &&
                    it.lowercase(Locale.ROOT) !in CITY_TOKENS
            }
    }

    /**
     * Geocodifica una ubicación (recogida o destino). `null` ante cualquier
     * fallo o cuando ninguna de las respuestas coincide con la dirección
     * (fail-open: el pipeline avisa "no verificada", jamás auto-acepta sin
     * confirmar la zona).
     *
     * Antes de armar la query el texto pasa por [AddressSanitizer] (regla de
     * 2 segmentos de Ambato: se corta la cola de UI y se descartan frases
     * de interfaz), así geocodificar no se envenena con textos de pantalla.
     *
     * Estrategia en dos pasos (intersecciones de Ecuador):
     *   1. Query completa "X y Y, Ambato, Ecuador" → se toma el primer candidato
     *      cuyos tokens coincidan con la dirección (calle o POI real).
     *   2. Si ninguno verifica (LocationIQ degradó al centroide u otra parroquia),
     *      se re-intenta con CADA calle del cruce por separado ("X, Ambato, Ecuador").
     *
     * @param apiKey token de la API de LocationIQ (nunca se commitea; se guarda en Room).
     */
    suspend fun geocode(address: String, apiKey: String): Pair<Double, Double>? {
        val sanitized = AddressSanitizer.sanitize(address)
        val query = buildQuery(sanitized.query)
        if (query.isEmpty() || apiKey.isBlank()) return null
        val addressForMatch = sanitized.normalized
        return withContext(Dispatchers.IO) {
            val match = resolveCandidate(query, addressForMatch, apiKey)
            if (match != null) return@withContext match.lat to match.lng

            for (component in streetComponents(addressForMatch)) {
                val componentQuery = buildQuery(component)
                if (componentQuery.isEmpty() || componentQuery == query) continue
                val componentMatch = resolveCandidate(componentQuery, component, apiKey)
                    ?: continue
                Logs.i(
                    TAG,
                    "🔀 '$query' verificado vía el cruce: ${componentMatch.displayName}"
                )
                return@withContext componentMatch.lat to componentMatch.lng
            }

            Logs.w(
                TAG,
                "🚩 '$query' sin calle/POI coincidente; se descarta (no verificable)"
            )
            null
        }
    }

    /** Consulta un query y devuelve el primer candidato que verifica por tokens. */
    private suspend fun resolveCandidate(
        query: String,
        verifyAgainst: String,
        apiKey: String
    ): GeoResult? {
        val candidates = fetchCandidates(query, apiKey)
        val match = candidates.firstOrNull { nameMatches(verifyAgainst, it.displayName) }
        if (match == null) {
            Logs.w(
                TAG,
                "🚩 '$query' cayó a '${candidates.firstOrNull()?.displayName ?: "sin resultado"}'" +
                    " sin coincidencia; se descarta (no verificable)"
            )
            return null
        }
        Logs.i(TAG, "📍 Geolocalizado '$query' → ${match.lat}, ${match.lng} (${match.displayName})")
        return match
    }

    /**
     * Lista de candidatos de la API para un query. `limit=3` para poder elegir el
     * candidato correcto cuando el primer resultado es un centroide de ciudad o de
     * otra parroquia. Fail-open: ante cualquier error devuelve lista vacía.
     */
    private suspend fun fetchCandidates(query: String, apiKey: String): List<GeoResult> {
        var connection: HttpURLConnection? = null
        return try {
            val params = "key=$apiKey" +
                "&q=${URLEncoder.encode(query, StandardCharsets.UTF_8.name())}" +
                "&format=$FORMAT&limit=3&countrycodes=ec"
            connection = (URL("$ENDPOINT?$params").openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
            }

            val code = connection.responseCode
            if (code !in 200..299) {
                Logs.w(TAG, "Geocode HTTP $code para '$query'")
                emptyList()
            } else {
                val body = connection.inputStream
                    .bufferedReader(StandardCharsets.UTF_8)
                    .use { it.readText() }
                parseResults(body)
            }
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (e: Exception) {
            Logs.w(TAG, "Geocode falló: ${e.message}")
            emptyList()
        } finally {
            connection?.disconnect()
        }
    }

    /** Nombres de ciudad/provincia que se pegan a veces al texto y no son calles. */
    private val CITY_TOKENS = setOf("ambato", "tungurahua", "ecuador")

    /** Conectores y abreviaturas que no sirven para verificar la dirección. */
    private val STOPWORDS = setOf(
        "av", "blvr", "calle", "avenida", "boulevard", "pasaje", "via",
        "de", "del", "la", "el", "los", "las", "y", "e", "u", "o",
        "san", "santa", "par", "ambiente", "elizabeth",
        "ambato", "tungurahua", "ecuador"
    )
}