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
    val failedFilters: String = "", // Comma-separated list of failed filter names

    // ── Datos enriquecidos (v10) ──
    /** False si el parser no pudo leer el rating del pasajero. */
    @ColumnInfo(name = "has_rating")
    val hasRating: Boolean = true,

    @ColumnInfo(name = "ride_type")
    val rideType: String = "",

    @ColumnInfo(name = "passenger_trips")
    val passengerTrips: Int = 0,

    /** True si el estimado de minutos se calculó por fallback (velocidad promedio). */
    @ColumnInfo(name = "minutes_estimated")
    val minutesEstimated: Boolean = false,

    /** True si la acción recomendada se ejecutó y se verificó en pantalla. */
    @ColumnInfo(name = "action_applied")
    val actionApplied: Boolean = false,

    /**
     * Resultado real de la oferta:
     * - ASSIGNED    → el ACCEPT se aplicó (o el conductor aceptó manualmente) y se vio la pantalla de viaje
     * - TIMEOUT     → nadie actuó y llegó otra oferta (WARN/IGNORE expiró)
     * - MANUAL_ACCEPT / MANUAL_REJECT → el conductor overrideó por voz
     * - TAP_FAILED  → la acción automática no se pudo ejecutar/verificar
     * - UNKNOWN     → aún sin resolver
     */
    @ColumnInfo(name = "resolution")
    val resolution: String = "UNKNOWN",

    @ColumnInfo(name = "ai_recommendation")
    val aiRecommendation: String = "",

    @ColumnInfo(name = "ai_confidence")
    val aiConfidence: Double = 0.0,

    @ColumnInfo(name = "pickup_lat")
    val pickupLat: Double? = null,

    @ColumnInfo(name = "pickup_lng")
    val pickupLng: Double? = null,

    @ColumnInfo(name = "dest_lat")
    val destLat: Double? = null,

    @ColumnInfo(name = "dest_lng")
    val destLng: Double? = null
)
