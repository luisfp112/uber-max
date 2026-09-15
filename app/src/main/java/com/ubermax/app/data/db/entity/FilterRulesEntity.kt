package com.ubermax.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Filtros estilo Maxymo para la evaluación automática de ofertas.
 * Todos los valores monetarios en USD. Distancias en km.
 *
 * Valores por defecto calculados para maximizar ganancias en Ambato, Ecuador
 * conduciendo un Chevrolet Spark 2008 1.0L:
 * - Costo combustible: ~$0.08 USD/km
 * - Velocidad promedio urbana: ~22 km/h
 * - Tarifa Uber Ambato promedio: ~$1.50-3.00 USD por viaje corto
 *
 * Los defaults rechazan viajes que cuestan más en gasolina de lo que generan.
 * Valor 0 = filtro desactivado.
 */
@Entity(tableName = "filter_rules")
data class FilterRulesEntity(
    @PrimaryKey
    val id: Int = 1, // Singleton

    // ── Filtros de Tarifa Bruta ──
    @ColumnInfo(name = "min_fare")
    val minFare: Double = 1.50,              // USD mínimo tarifa bruta

    // ── Filtros de Ganancia Neta (después de combustible) ──
    @ColumnInfo(name = "min_net_profit")
    val minNetProfit: Double = 0.80,         // USD mínimo ganancia neta

    @ColumnInfo(name = "min_profit_per_km")
    val minProfitPerKm: Double = 0.15,       // USD/km neto mínimo

    @ColumnInfo(name = "min_profit_per_hour")
    val minProfitPerHour: Double = 3.00,     // USD/hr neto mínimo

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
    val aiEnabled: Boolean = true            // Activar recomendaciones IA
)
