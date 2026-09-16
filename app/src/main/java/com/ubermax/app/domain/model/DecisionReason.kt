package com.ubermax.app.domain.model

/**
 * Motivo principal identificable de un filtro fallido.
 *
 * Se mantiene desacoplado de Android para poder testearse en JVM puro y para
 * que la UI (HUD, notificaciones) decida qué texto mostrar por recurso.
 */
enum class DecisionReason {
    LOW_FARE,
    LOW_NET,
    LOW_PER_KM,
    LOW_PER_HOUR,
    PICKUP_FAR,
    TRIP_LONG,
    LOW_RATING,
    DEADHEAD,
    BLACKLIST,
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
            text.contains("ganancia neta", ignoreCase = true) -> DecisionReason.LOW_NET
            text.contains("\$/km bajo", ignoreCase = true) -> DecisionReason.LOW_PER_KM
            text.contains("\$/hr", ignoreCase = true) -> DecisionReason.LOW_PER_HOUR
            text.contains("pickup lejos", ignoreCase = true) -> DecisionReason.PICKUP_FAR
            text.contains("viaje largo", ignoreCase = true) -> DecisionReason.TRIP_LONG
            text.contains("tiempo largo", ignoreCase = true) -> DecisionReason.TRIP_LONG
            text.contains("rating bajo", ignoreCase = true) -> DecisionReason.LOW_RATING
            text.contains("vuelta vacía", ignoreCase = true) -> DecisionReason.DEADHEAD
            text.contains("lista negra", ignoreCase = true) -> DecisionReason.BLACKLIST
            text.contains("auto-accept", ignoreCase = true) -> DecisionReason.AUTO_ACCEPT_OFF
            else -> null
        }
    }

    fun clean(filter: String): String =
        filter.trim().replace(LEADING_JUNK, "")
}