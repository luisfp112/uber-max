package com.ubermax.app.domain.model

/**
 * Motivo del HUD ya interpretado, listo para que la capa Android lo mapee a
 * un recurso de string. No depende de `Context` para poder testearse en JVM.
 */
sealed interface HudReason {
    /** La oferta fue aceptada por el motor. */
    data object Accepted : HudReason

    /** Motivo reconocido y traducible. */
    data class Known(val reason: DecisionReason) : HudReason

    /** Motivo no reconocido: se muestra el texto crudo tal cual. */
    data class Raw(val text: String) : HudReason

    /** Sin motivo que mostrar. */
    data object None : HudReason
}

/**
 * Lógica de presentación pura del HUD: qué motivo mostrar y cómo abreviar el
 * destino. Extraída de [com.ubermax.app.service.FloatingWindowService] para
 * poder validarla sin dispositivo.
 */
object HudReasonFormatter {

    const val DEFAULT_DESTINATION_MAX = 22

    /** Motivo principal de la decisión, en una sola línea. */
    fun mainReason(decision: OfferDecision): HudReason {
        if (decision.action == Action.ACCEPT) return HudReason.Accepted

        val filter = decision.failedFilters.firstOrNull() ?: return HudReason.None
        val text = ReasonMapper.clean(filter)
        if (text.isEmpty()) return HudReason.None

        val reason = ReasonMapper.map(text) ?: return HudReason.Raw(text)
        return HudReason.Known(reason)
    }

    /**
     * Toma el primer segmento del destino (antes de coma o guion) y lo recorta
     * a [maxLength] caracteres (dejando uno para el elipsis).
     */
    fun abbreviateDestination(
        destination: String,
        maxLength: Int = DEFAULT_DESTINATION_MAX
    ): String {
        val first = destination.split(",", " - ").firstOrNull()?.trim().orEmpty()
        if (first.isEmpty()) return ""
        if (first.length <= maxLength) return first
        val cut = (maxLength - 1).coerceAtLeast(1)
        return first.take(cut).trimEnd() + "…"
    }
}