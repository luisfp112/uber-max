package com.ubermax.app.domain.model

/**
 * Decisión final del motor de reglas sobre una oferta evaluada.
 */
data class OfferDecision(
    val evaluatedOffer: EvaluatedOffer,
    val action: Action,
    val failedFilters: List<String> = emptyList() // Nombres de filtros que fallaron
) {
    val isAccepted: Boolean get() = action == Action.ACCEPT
    val isWarning: Boolean get() = action == Action.WARN
}

/**
 * Acciones posibles ante una oferta de viaje.
 *
 * ACCEPT: inyectar clic en el botón "Viaje disponible" para aceptar
 * WARN:   no cumple filtros del conductor → mostrar aviso en HUD (rechazo pasivo)
 */
enum class Action {
    ACCEPT,
    WARN
}