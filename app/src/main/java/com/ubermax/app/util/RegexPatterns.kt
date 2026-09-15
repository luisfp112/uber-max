package com.ubermax.app.util

/**
 * Patrones regex para extraer datos de la UI de Uber Driver.
 * Estos patrones deben calibrarse con la versión específica de Uber Driver
 * en uso. Los logs verbose del OfferParser facilitan esta calibración.
 *
 * Formato asumido: Pesos Colombianos (COP), distancias en km, tiempos en min.
 */
object RegexPatterns {

    /** Extrae tarifa monetaria: "$2.50", "$12.80", "$8,30", "COP 15.000" */
    val FARE_PATTERN = Regex(
        """(?:COP|USD|\$)\s?(\d{1,3}(?:[.,]\d{1,3})*(?:[.,]\d{1,2})?)"""
    )

    /** Extrae distancia en km: "2.3 km", "8,1km", "12 km" */
    val DISTANCE_KM_PATTERN = Regex(
        """(\d+[.,]?\d*)\s?km""", RegexOption.IGNORE_CASE
    )

    /** Extrae distancia en minutos: "5 min", "12min", "25 min" */
    val TIME_MINUTES_PATTERN = Regex(
        """(\d+)\s?min""", RegexOption.IGNORE_CASE
    )

    /** Extrae calificación del pasajero: "4.92", "4,80 ★", "4.95★" */
    val RATING_PATTERN = Regex(
        """(\d[.,]\d{1,2})\s?★?"""
    )

    /** Extrae distancia en millas (fallback para algunos mercados): "3.2 mi" */
    val DISTANCE_MI_PATTERN = Regex(
        """(\d+[.,]?\d*)\s?mi""", RegexOption.IGNORE_CASE
    )

    /**
     * Parsea una cadena de tarifa en COP a Double.
     * Ejemplos: "12.500" → 12500.0, "8,300" → 8300.0
     */
    fun parseFare(fareStr: String): Double {
        // Remover separadores de miles, manejar tanto punto como coma
        val cleaned = fareStr.replace(" ", "")
        // Si tiene formato X.XXX o X,XXX (separador de miles)
        return when {
            cleaned.matches(Regex("""\d{1,3}[.,]\d{3}""")) -> {
                cleaned.replace(".", "").replace(",", "").toDoubleOrNull() ?: 0.0
            }
            cleaned.matches(Regex("""\d{1,3}[.,]\d{3}[.,]\d{3}""")) -> {
                cleaned.replace(".", "").replace(",", "").toDoubleOrNull() ?: 0.0
            }
            else -> {
                cleaned.replace(",", ".").toDoubleOrNull() ?: 0.0
            }
        }
    }

    /**
     * Parsea una cadena de distancia a Double en km.
     * Ejemplo: "2,3" → 2.3, "8.1" → 8.1
     */
    fun parseDistance(distStr: String): Double {
        return distStr.replace(",", ".").toDoubleOrNull() ?: 0.0
    }

    /**
     * Parsea una cadena de calificación a Double.
     * Ejemplo: "4,92" → 4.92
     */
    fun parseRating(ratingStr: String): Double {
        return ratingStr.replace(",", ".").toDoubleOrNull() ?: 0.0
    }
}
