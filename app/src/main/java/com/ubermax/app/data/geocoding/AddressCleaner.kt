package com.ubermax.app.data.geocoding

/**
 * Limpia el texto de destino extraído de la tarjeta de Uber antes de enviarlo a
 * LocationIQ. El parser a veces deja entre el destino textos de UI pegados
 * (calificaciones, tarifas, métricas de tiempo/distancia, etiquetas de producto),
 * que no deben contaminar la búsqueda geográfica.
 *
 * 100% JVM puro (sin Android, sin red) para testearse con JUnit. Ante cualquier
 * texto que quede con menos de [MIN_VALID_CHARS] caracteres válidos
 * ([isUsable]) el pipeline aborta la llamada a la API y aplica el fallback local
 * (fail-open: se omite el filtro del perímetro).
 */
object AddressCleaner {

    /** Mínimo de caracteres alfanuméricos para considerar la dirección válida. */
    const val MIN_VALID_CHARS = 3

    // ── Tarifas y símbolos monetarios (ej. "$8.40", "$ 1,75") ──
    private val MONEY = Regex("""\$\s?\d+(?:[.,]\d+)*""")

    // ── Calificaciones y estrellas (ej. "★ 4.73 (42)", "4.92 ★", "4.85", "(42)") ──
    private val STAR_ANY = Regex("""[★⭐]+""")
    private val STAR_RATING = Regex("""(?:★+|⭐+)\s*\d+(?:[.,]\d+)?(?:\s*\(\d+\))?""")
    private val RATING_BEFORE_STAR = Regex("""\d+(?:[.,]\d+)?\s*(?:★+|⭐+)""")
    private val RATING_LABEL = Regex("""calificación.*?estrellas?""", RegexOption.IGNORE_CASE)
    private val RATING_WITH_TRIPS = Regex("""\b\d(?:[.,]\d+)?\s*\(\d+\)""")
    private val PAREN_WITH_DIGITS = Regex("""\([^()]*\d[^()]*\)""")
    private val PAREN_EMPTY = Regex("""\(\s*\)""")

    // ── Métricas de tiempo y distancia (ej. "4 min", "2.1 km", "850 m", "30 min") ──
    // La línea de la tarjeta "A 4 min (1.0 km)" y "Viaje:/Recogida: 20 min" se
    // quitan completas (con su etiqueta) para no dejar restos "A" o "Viaje:" colgando.
    private val PICKUP_METRIC = Regex(
        """\bA\s+\d+(?:[.,]\d+)?\s*(?:min|km)\b""",
        RegexOption.IGNORE_CASE
    )
    private val LABELED_METRIC = Regex(
        """\b(?:viaje|recogida)\s*:\s*\d+(?:[.,]\d+)?\s*(?:min|km)\b""",
        RegexOption.IGNORE_CASE
    )
    private val TIME_DISTANCE = Regex(
        """\b\d+(?:[.,]\d+)?\s*(?:min|mins|minuto|minutos|min\.|km|kms|km\.|kms\.|kilómetros?|metros?|hrs?)\b""",
        RegexOption.IGNORE_CASE
    )
    private val DISTANCE_M = Regex("""\b\d+(?:[.,]\d+)?\s*m\b""", RegexOption.IGNORE_CASE)

    // ── Countdowns y relojes (ej. "Termina en 00:45", "00:45") ──
    private val COUNTDOWN = Regex("""\btermina en\s*\d{1,2}:\d{2}\b""", RegexOption.IGNORE_CASE)
    private val CLOCK = Regex("""\b\d{1,2}:\d{2}\b""")

    // ── Nombres de producto / etiquetas de Uber (ej. "UberX", "Flash", "Prioridad") ──
    private val PRODUCT_LABELS = Regex(
        """\b(?:viaje disponible|me interesa|postularse|postularte|postularme|postular|""" +
            """aceptar|solicitar viaje|solicitar|pedir viaje|termina en|en camino|llegando|""" +
            """asignado|reservado|uberx|uber xl|uberblack|uber black|comfort|flash|prioridad|""" +
            """viaje|recogida|destino|pickup)\b""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Limpia el texto de destino: elimina tarifas, ratings, métricas, countdowns
     * y etiquetas de Uber; colapsa espacios y separadores sobrantes.
     */
    fun clean(raw: String): String {
        val step1 = raw
            .replace(MONEY, "")
            .replace(PICKUP_METRIC, "")
            .replace(LABELED_METRIC, "")
            .replace(STAR_RATING, "")
            .replace(RATING_BEFORE_STAR, "")
            .replace(RATING_LABEL, "")
            .replace(RATING_WITH_TRIPS, "")
            .replace(TIME_DISTANCE, "")
            .replace(DISTANCE_M, "")
            .replace(COUNTDOWN, "")
            .replace(CLOCK, "")
            .replace(PRODUCT_LABELS, "")
            .replace(STAR_ANY, "")
            .replace(PAREN_WITH_DIGITS, "")
            .replace(PAREN_EMPTY, "")
        return collapse(step1)
    }

    /**
     * True si el texto (ya limpio) tiene al menos [MIN_VALID_CHARS] caracteres
     * alfanuméricos, es decir si vale la pena llamar a LocationIQ.
     */
    fun isUsable(cleanAddress: String): Boolean =
        cleanAddress.count { it.isLetter() || it.isDigit() } >= MIN_VALID_CHARS

    /** Colapsa espacios múltiples, colones y separadores sobrantes. No toca "y". */
    private fun collapse(input: String): String {
        var t = input.replace(Regex("""\s+"""), " ").trim()
        t = t.replace(Regex("""\s*,\s*"""), ", ")
        t = t.replace(Regex("""\s*-\s*"""), " - ")
        t = t.replace(Regex("""\s*:\s*"""), " ")
        t = t.replace(Regex("""\s*-\s*$"""), "")
        return t.trim().trim(',', '-', ' ').trim()
    }
}