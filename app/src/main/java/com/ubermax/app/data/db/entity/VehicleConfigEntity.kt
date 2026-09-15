package com.ubermax.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Configuración del vehículo para cálculo de costos.
 *
 * Chevrolet Spark 2008 1.0L en Ambato, Ecuador:
 * - Consumo ciudad: ~9.5 L/100km
 * - Gasolina Extra: $3.24 USD/galón = ~$0.856 USD/litro
 * - Costo por km: 0.095 L/km × $0.856/L = $0.081 ≈ $0.08 USD/km
 * - Velocidad promedio Ambato (zona montañosa, tráfico): ~22 km/h
 */
@Entity(tableName = "vehicle_config")
data class VehicleConfigEntity(
    @PrimaryKey
    val id: Int = 1,

    @ColumnInfo(name = "vehicle_name")
    val vehicleName: String = "Spark 2008 1.0L",

    @ColumnInfo(name = "cost_per_km")
    val costPerKm: Double = 0.08, // USD por km

    @ColumnInfo(name = "avg_speed_kmh")
    val avgSpeedKmh: Double = 22.0, // km/h promedio Ambato

    @ColumnInfo(name = "engine_idle_cost_per_hour")
    val engineIdleCostPerHour: Double = 0.20 // USD/hora ralentí
)
