package com.ubermax.app.domain.rules

/**
 * Métrica de rentabilidad que decide una oferta.
 *
 * Solo la métrica principal se evalúa en [RuleEngine]: la otra se ignora por
 * completo (aunque su umbral sea > 0), evitando filtros económicos contradictorios.
 *
 * - [PER_HOUR]: gana el conductor por hora trabajada (tiempo). Default.
 * - [PER_KM]: eficiencia por kilómetro recorrido (distancia / deadhead).
 */
enum class ProfitMetric(val key: String) {
    PER_HOUR("PER_HOUR"),
    PER_KM("PER_KM");

    companion object {
        /** Parseo tolerante: cualquier valor desconocido cae en [PER_HOUR]. */
        fun fromKey(key: String?): ProfitMetric =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: PER_HOUR
    }
}
