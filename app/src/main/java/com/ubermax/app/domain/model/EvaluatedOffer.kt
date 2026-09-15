package com.ubermax.app.domain.model

/**
 * Resultado del cálculo económico aplicado a una oferta.
 * Contiene todas las métricas de rentabilidad calculadas.
 */
data class EvaluatedOffer(
    val offer: OfferData,
    val fuelCost: Double,       // Costo total de combustible (recogida + viaje)
    val netProfit: Double,      // Ganancia neta = tarifa bruta - costo combustible
    val profitPerKm: Double,    // Ganancia neta / km totales
    val profitPerHour: Double,  // Ganancia neta / horas estimadas
    val totalKm: Double,        // Km totales (recogida + viaje + vuelta vacía si aplica)
    val hasDeadheadPenalty: Boolean = false, // True si se aplicó penalización por vuelta vacía
    val returnKm: Double = 0.0  // Km de la vuelta vacía
) {
    /** Retorna true si la oferta es económicamente viable (ganancia neta positiva) */
    val isProfitable: Boolean get() = netProfit > 0
}
