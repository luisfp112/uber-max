package com.ubermax.app.domain.model

/**
 * Decisión final del motor de reglas sobre una oferta evaluada.
 */
data class OfferDecision(
    val evaluatedOffer: EvaluatedOffer,
    val action: Action,
    val failedFilters: List<String> = emptyList(), // Nombres de filtros que fallaron
    val aiRecommendation: String = "",             // Recomendación de la IA
    val aiConfidence: Double = 0.0                 // Confianza de la IA (0.0-1.0)
) {
    val isAccepted: Boolean get() = action == Action.ACCEPT
    val isCancelled: Boolean get() = action == Action.CANCEL
    val isWarning: Boolean get() = action == Action.WARN
}

/**
 * Acciones posibles ante una oferta de viaje.
 *
 * ACCEPT: inyectar clic en el botón "Viaje disponible" para aceptar
 * WARN:   no cumple filtros del conductor → mostrar aviso en HUD (conductor decide)
 * CANCEL: destino en lista negra → tocar la X para rechazar automáticamente
 * IGNORE: no hacer nada (dejar correr temporizador, caso extremo)
 */
enum class Action {
    ACCEPT,
    WARN,
    CANCEL,
    IGNORE
}
