package com.ubermax.app.util

import java.util.Locale

/**
 * Sanitiza la dirección de DESTINO extraída de la tarjeta de Uber antes de
 * geocodificarla o clasificarla.
 *
 * En Ecuador (y Ambato en particular) la dirección real tiene como máximo
 * DOS segmentos separados por coma:
 *
 *   "[Calle / Intersección / POI], [Parroquia / Sector]"
 *
 * ej.: "Dr Remigio Crespo Toral 154, Huachi Chico" o "Alaska, Huachi Grande".
 * El parser a veces no se detiene en el nodo de destino y concatena textos
 * inferiores de la pantalla de Uber (alertas de batería del sistema, banners
 * de Radar, puntos Uber Pro, sugerencias, estado de conexión, burbujas de
 * chat...). Esta clase aplica la regla de los 2 segmentos:
 *
 * 1. `street` = `partes[0]` (siempre la calle, vía o POI).
 * 2. `parish` = `partes[1]` solo si NO es una frase de interfaz conocida
 *    ([UI_GARBAGE]) ni un nombre de ciudad/cantón ([CITY_NAMES]).
 * 3. `partes[2..n]` se descartan siempre (cola de basura de UI).
 * 4. Símbolos huérfanos al final de la calle ("Av Atahualpa &", "Av Bolívar y")
 *    se eliminan.
 *
 * [SanitizedAddress.query] arma la consulta estricta de LocationIQ con el
 * sesgo a Ambato; [SanitizedAddress.normalized] es la forma sin ciudad para
 * el pipeline (OfferData, fingerprint, HUD).
 *
 * 100% JVM puro (sin Android, sin red) para testearse con JUnit.
 */
object AddressSanitizer {

    /** Dirección ya saneada: calle siempre; parroquia/sector solo si es válida. */
    data class SanitizedAddress(val street: String, val parish: String?) {

        /** Forma sin ciudad: "calle, parroquia" o solo "calle". */
        val normalized: String
            get() = if (parish.isNullOrBlank()) street else "$street, $parish"

        /** Consulta estricta para LocationIQ; vacío si no quedó calle. */
        val query: String
            get() = if (street.isBlank()) "" else "$normalized, $AMBATO_SUFFIX"
    }

    private const val AMBATO_SUFFIX = "Ambato, Ecuador"

    /**
     * Frases de interfaz que jamás son una parroquia/sector. Se comparan por
     * subcadena (lowercase). Cubre banners de Radar, batería del sistema,
     * conexión, puntos Uber Pro, sugerencias y métricas de tiempo ("2-7 min").
     */
    private val UI_GARBAGE = listOf(
        "radar", "buscando", "batería", "bateria", "desconectarse", "sugerencia",
        "uber pro", "platino", "oro", "puntos", "hacia tu destino", "cómo iniciar",
        "como iniciar", "limitado a", "pedidos nuevos", "min", "coincidencias",
        "solicitud"
    )

    /** Ciudades/cantones que se pegan al texto de la tarjeta y no son parroquia. */
    private val CITY_NAMES = setOf("ambato", "tungurahua", "ecuador")

    /** Símbolo huérfano al final de la calle: "Av Atahualpa &", "Av Bolívar y". */
    private val TRAILING_CONJUNCTION = Regex("""\s+(?:&|y)\s*$""", RegexOption.IGNORE_CASE)

    /**
     * Divide [raw] por comas y aplica la regla de 2 segmentos.
     * Devuelve street/parish saneadas; todo texto de cola y frases de UI se
     * descartan. Si la entrada es vacía o no queda calle, ambos campos van vacíos.
     */
    fun sanitize(raw: String?): SanitizedAddress {
        if (raw.isNullOrBlank()) return SanitizedAddress("", null)

        val parts = collapse(raw)
            .split(',')
            .map { it.trim().trim(',', '\u00a0', '-').trim() }
            .filter { it.isNotEmpty() }
        if (parts.isEmpty()) return SanitizedAddress("", null)

        val street = parts[0].replace(TRAILING_CONJUNCTION, "").trim()
        if (street.isBlank()) return SanitizedAddress("", null)

        val parish = parts.getOrNull(1)?.takeIf { isValidParish(it) }
        return SanitizedAddress(street, parish)
    }

    /** ¿[candidate] es un segundo segmento válido (parroquia/sector)? */
    private fun isValidParish(candidate: String): Boolean {
        val lower = candidate.lowercase(Locale.ROOT)
        if (lower in CITY_NAMES) return false
        return UI_GARBAGE.none { lower.contains(it) }
    }

    /** Colapsa espacios múltiples y espacios y separadores sobrantes. */
    private fun collapse(input: String): String =
        input.replace(Regex("""\s+"""), " ").trim()
}