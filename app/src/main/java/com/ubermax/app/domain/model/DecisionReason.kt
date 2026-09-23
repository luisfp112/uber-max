package com.ubermax.app.domain.model

/**
 * Motivo principal identificable de un filtro fallido.
 *
 * Se mantiene desacoplado de Android para poder testearse en JVM puro y para
 * que la UI (HUD, notificaciones) decida qué texto mostrar por recurso.
 */
enum class DecisionReason {
    LOW_FARE,
    LOW_PER_KM,
    PICKUP_FAR,
    PICKUP_TIME,
    TRIP_LONG,
    TRIP_DISTANCE,
    LOW_RATING,
    OUT_OF_PERIMETER,
    AUTO_ACCEPT_OFF
}

/**
 * Asigna un string de filtro fallido (ej: el contenido de [OfferDecision.failedFilters])
 * a un [DecisionReason] estable.
 *
 * Devuelve `null` cuando el filtro no es reconocible (o está vacío): la UI
 * debe mostrar el texto crudo ([clean]) tal cual.
 *
 * La lógica es 100% pura (sin Context, sin strings) por lo que puede validarse
 * con JUnit sin dispositivo.
 */
object ReasonMapper {

    private val LEADING_JUNK = Regex("^[\\p{So}\\p{M}\\s]+")

    fun map(filter: String): DecisionReason? {
        val text = clean(filter)
        if (text.isEmpty()) return null
        return when {
            text.contains("tarifa baja", ignoreCase = true) -> DecisionReason.LOW_FARE
            text.contains("\$/km bajo", ignoreCase = true) -> DecisionReason.LOW_PER_KM
            text.contains("recogida muy lejos", ignoreCase = true) -> DecisionReason.PICKUP_TIME
            text.contains("pickup lejos", ignoreCase = true) -> DecisionReason.PICKUP_FAR
            text.contains("recorrido excede", ignoreCase = true) -> DecisionReason.TRIP_DISTANCE
            text.contains("viaje largo", ignoreCase = true) -> DecisionReason.TRIP_LONG
            text.contains("tiempo largo", ignoreCase = true) -> DecisionReason.TRIP_LONG
            text.contains("rating bajo", ignoreCase = true) -> DecisionReason.LOW_RATING
            text.contains("zona permitida", ignoreCase = true) -> DecisionReason.OUT_OF_PERIMETER
            text.contains("perímetro urbano", ignoreCase = true) -> DecisionReason.OUT_OF_PERIMETER
            text.contains("auto-accept", ignoreCase = true) -> DecisionReason.AUTO_ACCEPT_OFF
            else -> null
        }
    }

    fun clean(filter: String): String =
        filter.trim().replace(LEADING_JUNK, "")
}