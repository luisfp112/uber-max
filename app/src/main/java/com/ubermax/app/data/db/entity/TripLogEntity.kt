package com.ubermax.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Registro de cada oferta de viaje recibida de Uber Driver.
 * Almacena datos brutos extraídos, cálculos económicos y la decisión tomada.
 *
 * Los índices aceleran las consultas de historial (filtros por día, destino,
 * hora y decisión) y de exportación. Nombres generados como lo espera Room:
 * `index_<tabla>_<columna>`.
 */
@Entity(
    tableName = "trip_log",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["destination"]),
        Index(value = ["hour_of_day"]),
        Index(value = ["decision"])
    ]
)
data class TripLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "day_of_week")
    val dayOfWeek: String,

    @ColumnInfo(name = "hour_of_day")
    val hourOfDay: Int,

    // Datos brutos extraídos de la UI de Uber
    @ColumnInfo(name = "raw_fare")
    val rawFare: Double,

    @ColumnInfo(name = "pickup_km")
    val pickupKm: Double,

    @ColumnInfo(name = "trip_km")
    val tripKm: Double,

    @ColumnInfo(name = "estimated_minutes")
    val estimatedMinutes: Int,

    @ColumnInfo(name = "passenger_rating")
    val passengerRating: Double,

    @ColumnInfo(name = "destination")
    val destination: String = "",

    @ColumnInfo(name = "pickup_address")
    val pickupAddress: String = "",

    // Cálculos económicos
    @ColumnInfo(name = "fuel_cost")
    val fuelCost: Double,

    @ColumnInfo(name = "net_profit")
    val netProfit: Double,

    @ColumnInfo(name = "profit_per_km")
    val profitPerKm: Double,

    @ColumnInfo(name = "profit_per_hour")
    val profitPerHour: Double,

    @ColumnInfo(name = "total_km")
    val totalKm: Double,

    // Decisión
    @ColumnInfo(name = "decision")
    val decision: String, // Action.name — "ACCEPT" | "WARN" | "CANCEL" | "IGNORE"

    @ColumnInfo(name = "failed_filters")
    val failedFilters: String = "" // Comma-separated list of failed filter names
)
