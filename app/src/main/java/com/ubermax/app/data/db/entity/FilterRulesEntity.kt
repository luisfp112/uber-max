package com.ubermax.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Filtros estilo Maxymo para la evaluación automática de ofertas.
 * Todos los valores monetarios en USD. Distancias en km.
 *
 * Decisión del conductor: una sola métrica principal, la ganancia neta por km
 * ([minProfitPerKm]). El monto de la tarjeta de Uber ya viene neto (sin la
 * comisión de la plataforma), así que no hay filtros de ganancia por hora ni
 * de ganancia total mínima.
 *
 * Valor 0 = filtro desactivado.
 */
@Entity(tableName = "filter_rules")
data class FilterRulesEntity(
    @PrimaryKey
    val id: Int = 1, // Singleton

    // ── Filtros de Tarifa Bruta (guarda opcional, 0 = desactivado) ──
    @ColumnInfo(name = "min_fare")
    val minFare: Double = 0.0,               // USD mínimo tarifa bruta

    // ── Métrica de rentabilidad: $/km ──
    @ColumnInfo(name = "min_profit_per_km")
    val minProfitPerKm: Double = 0.15,       // USD/km neto mínimo

    // ── Filtro de Recogida (valor de la tarjeta, unidad configurable) ──
    // "MIN" = minutos de llegada a recogida (default) · "KM" = km de recogida.
    // El valor aparece SIEMPRE en la tarjeta de Uber; 0 = filtro desactivado.
    @ColumnInfo(name = "pickup_measure")
    val pickupMeasure: String = "MIN",

    @ColumnInfo(name = "pickup_max")
    val pickupMax: Double = 5.0,

    // ── Filtro de Viaje (valor de la tarjeta, unidad configurable) ──
    // Un viaje largo solo se acepta si además la ganancia por km es buena.
    // 0 = sin tope.
    @ColumnInfo(name = "trip_measure")
    val tripMeasure: String = "MIN",

    @ColumnInfo(name = "trip_max")
    val tripMax: Double = 0.0,

    // ── Filtros de Pasajero ──
    @ColumnInfo(name = "min_passenger_rating")
    val minPassengerRating: Double = 0.0,    // Rating mínimo (0 = desactivado, WARN)

    /**
     * Con rating desconocido (el parser no lo ve en la tarjeta):
     * false = el filtro de rating se omite; true = la oferta no cumple (WARN).
     */
    @ColumnInfo(name = "reject_on_unknown_rating")
    val rejectOnUnknownRating: Boolean = false,

    // ── Modo de Operación ──
    @ColumnInfo(name = "auto_accept_enabled")
    val autoAcceptEnabled: Boolean = true    // Auto-aceptar las que PASAN filtros
)