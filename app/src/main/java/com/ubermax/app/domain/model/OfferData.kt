package com.ubermax.app.domain.model

/**
 * Datos brutos extraídos de la interfaz de Uber Driver por el OfferParser.
 * Representa una oferta de viaje antes de la evaluación económica.
 *
 * Estructura de la tarjeta de Uber Driver (capturada de screenshot real):
 *   UberX
 *   $4.68
 *   ★ 4.73 (42)
 *   A 6 min (1.8 km)        ← pickup
 *   Blvr. del Ejercito...    ← pickup address
 *   Viaje: 36 min (15.6 km) ← trip
 *   C. L-7, CUIDAD MERLIOT... ← destination
 *   [Viaje disponible]       ← accept button
 */
data class OfferData(
    val rawFare: Double,              // Tarifa bruta visible (USD)
    val pickupKm: Double,             // Distancia de recogida en km
    val tripKm: Double,               // Distancia del viaje en km
    val pickupMinutes: Int = 0,       // Tiempo de recogida en minutos
    val tripMinutes: Int = 0,         // Tiempo del viaje en minutos
    val estimatedMinutes: Int,        // Tiempo total estimado (pickup + trip)
    val passengerRating: Double,      // Calificación del pasajero (0.0-5.0)
    val passengerTrips: Int = 0,      // Número de viajes del pasajero
    val rideType: String = "",        // Tipo de viaje (UberX, Comfort, etc.)
    val destination: String = "",     // Dirección de destino del pasajero
    val pickupAddress: String = "",   // Dirección de recogida
    val rawTexts: List<String> = emptyList() // Textos brutos para debug/calibración
) {
    val totalKm: Double get() = pickupKm + tripKm
    val estimatedHours: Double get() = estimatedMinutes / 60.0
}
