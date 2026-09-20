package com.ubermax.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Filtros estilo Maxymo para la evaluación automática de ofertas.
 * Todos los valores monetarios en USD. Distancias en km.
 *
 * Filtros para evaluar la rentabilidad de las ofertas de Uber.
 * Se ajustan según el costo real del conductor (ver VehicleConfigEntity).
 * Valor 0 = filtro desactivado.
 */
@Entity(tableName = "filter_rules")
data class FilterRulesEntity(
    @PrimaryKey
    val id: Int = 1, // Singleton

    // ── Filtros de Tarifa Bruta (guarda opcional, 0 = desactivado) ──
    @ColumnInfo(name = "min_fare")
    val minFare: Double = 0.0,               // USD mínimo tarifa bruta

    // ── Métrica principal de rentabilidad ──
    // "PER_HOUR" (default) o "PER_KM". Solo la métrica elegida decide;
    // la otra se ignora aunque su umbral sea > 0.
    @ColumnInfo(name = "primary_metric")
    val primaryMetric: String = "PER_HOUR",

    // ── Filtros de Ganancia Neta (después de combustible) ──
    // Ganancia absoluta: guarda opcional (0 = desactivado).
    @ColumnInfo(name = "min_net_profit")
    val minNetProfit: Double = 0.0,          // USD mínimo ganancia neta

    @ColumnInfo(name = "min_profit_per_km")
    val minProfitPerKm: Double = 0.15,       // USD/km neto mínimo (solo si primary = PER_KM)

    @ColumnInfo(name = "min_profit_per_hour")
    val minProfitPerHour: Double = 3.00,     // USD/hr neto mínimo (solo si primary = PER_HOUR)

    // ── Filtros de Distancia ──
    @ColumnInfo(name = "max_pickup_km")
    val maxPickupKm: Double = 5.0,           // Máximo 5km de recogida

    @ColumnInfo(name = "max_trip_km")
    val maxTripKm: Double = 30.0,            // Máximo 30km de viaje

    // ── Filtros de Tiempo ──
    @ColumnInfo(name = "max_total_minutes")
    val maxTotalMinutes: Int = 60,           // Máximo 60 min total

    // ── Filtros de Pasajero ──
    @ColumnInfo(name = "min_passenger_rating")
    val minPassengerRating: Double = 4.5,    // Rating mínimo del pasajero

    // ── Filtros de Ratio ──
    @ColumnInfo(name = "max_pickup_trip_ratio")
    val maxPickupTripRatio: Double = 1.0,    // Max ratio pickup/trip (0 = desactivado)

    // ── Lista Negra ──
    @ColumnInfo(name = "blacklist_enabled")
    val blacklistEnabled: Boolean = true,

    // ── Modo de Operación ──
    @ColumnInfo(name = "auto_accept_enabled")
    val autoAcceptEnabled: Boolean = true,   // Auto-aceptar las que PASAN filtros

    // ── IA ──
    @ColumnInfo(name = "ai_enabled")
    val aiEnabled: Boolean = true,           // Activar recomendaciones IA

// ── Deadhead ──
    @ColumnInfo(name = "deadhead_threshold_km")
    val deadheadThresholdKm: Double = 8.0,   // Umbral base de km para vuelta vacía

    /**
     * Factor de vuelta vacía (0 = desactivado, 0.5 = mitad, 1.0 = completa).
     * Solo se aplica cuando el conductor lo activa (> 0): penaliza en rampa
     * suave desde el umbral hasta el doble del umbral.
     */
    @ColumnInfo(name = "deadhead_return_factor")
    val deadheadReturnFactor: Double = 0.0,

    /**
     * Con rating desconocido (el parser no lo ve en la tarjeta):
     * false = el filtro de rating se omite; true = la oferta no cumple (WARN).
     */
    @ColumnInfo(name = "reject_on_unknown_rating")
    val rejectOnUnknownRating: Boolean = false
)
