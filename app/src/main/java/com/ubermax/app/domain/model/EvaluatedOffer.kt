package com.ubermax.app.domain.model

/**
 * Resultado del cálculo económico aplicado a una oferta.
 * Contiene las métricas de rentabilidad calculadas.
 */
data class EvaluatedOffer(
    val offer: OfferData,
    val fuelCost: Double,       // Costo de combustible/depreciación (recogida + viaje)
    val netProfit: Double,      // Ganancia neta = tarifa (ya sin comisión en la tarjeta) - costo
    val profitPerKm: Double,    // Ganancia neta / km totales
    val totalKm: Double         // Km totales (recogida + viaje)
) {
    /** True si la oferta es económicamente viable (ganancia neta positiva). */
    val isProfitable: Boolean get() = netProfit > 0
}