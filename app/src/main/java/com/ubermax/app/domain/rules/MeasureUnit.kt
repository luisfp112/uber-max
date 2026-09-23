package com.ubermax.app.domain.rules

/**
 * Unidad de medida de los filtros de recogida/viaje.
 * El valor SIEMPRE sale en la tarjeta de Uber (sean minutos o km).
 */
enum class MeasureUnit(val label: String) {
    MIN("min"),
    KM("km");

    companion object {
        fun from(key: String): MeasureUnit =
            values().firstOrNull { it.name == key } ?: MIN
    }
}