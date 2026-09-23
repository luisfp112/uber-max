package com.ubermax.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Configuración del vehículo para cálculo dinámico de costos.
 *
 * El costo por km puede calcularse automáticamente a partir de:
 *   - Consumo (km/litro o km/galón)
 *   - Precio del combustible por unidad (litro o galón)
 *   - Margen estimado de mantenimiento/depreciación por km
 *
 * O ingresarse directamente manualmente (manualCostPerKm > 0).
 *
 * La tarjeta de Uber muestra el monto neto ya descontada la comisión de la
 * plataforma, por lo que ya no se modela ningún descuento aquí.
 *
 * Si el conductor no configura el consumo, se usa un costo
 * por defecto razonable (DEFAULT_COST_PER_KM).
 */
@Entity(tableName = "vehicle_config")
data class VehicleConfigEntity(
    @PrimaryKey
    val id: Int = 1,

    @ColumnInfo(name = "vehicle_name")
    val vehicleName: String = "",

    // ── Consumo de combustible ──

    /** Km recorridos por cada unidad de combustible (litro o galón). 0 = no configurado. */
    @ColumnInfo(name = "consumption_km_per_unit")
    val consumptionKmPerUnit: Double = 12.0,

    /** Unidad: 0 = litro, 1 = galón */
    @ColumnInfo(name = "fuel_unit_id")
    val fuelUnitId: Int = 0,

    /** Precio del combustible por la unidad seleccionada (USD/litro o USD/galón). */
    @ColumnInfo(name = "fuel_price_per_unit")
    val fuelPricePerUnit: Double = 0.86,

    /** Margen estimado de mantenimiento/depreciación por km (USD/km). */
    @ColumnInfo(name = "maintenance_per_km")
    val maintenancePerKm: Double = 0.03,

    // ── Velocidad y ralentí ──

    @ColumnInfo(name = "avg_speed_kmh")
    val avgSpeedKmh: Double = 22.0,

    @ColumnInfo(name = "engine_idle_cost_per_hour")
    val engineIdleCostPerHour: Double = 0.20,

    // ── Costo por km manual (0 = usar cálculo automático) ──

    /** Si es mayor a 0, reemplaza el costo calculado automáticamente. */
    @ColumnInfo(name = "manual_cost_per_km")
    val manualCostPerKm: Double = 0.0
) {

    companion object {
        /**
         * Costo por defecto cuando el consumo no está configurado (consumptionKmPerUnit = 0
         * y manualCostPerKm = 0). Evita que un viaje tenga costo 0 (combustible "gratis").
         */
        const val DEFAULT_COST_PER_KM = 0.10
    }

    /** Unidad legible de combustible: "litro" o "galón". */
    val fuelUnitLabel: String
        get() = when (fuelUnitId) {
            1 -> "galón"
            else -> "litro"
        }

    /** Costo de combustible por km según la fórmula: (Precio/unidad) / (km/unidad). */
    val fuelCostPerKm: Double
        get() = if (consumptionKmPerUnit > 0) fuelPricePerUnit / consumptionKmPerUnit else 0.0

    /** Costo por km calculado (combustible + mantenimiento). */
    val computedCostPerKm: Double
        get() = fuelCostPerKm + maintenancePerKm

    /**
     * Costo efectivo por km:
     *  1. Si manualCostPerKm > 0 → se usa ese valor (el conductor lo configuró manualmente)
     *  2. Si no, se calcula a partir de consumo, precio y mantenimiento
     *  3. Si el cálculo da 0 → fallback a DEFAULT_COST_PER_KM
     */
    val costPerKm: Double
        get() = when {
            manualCostPerKm > 0.0 -> manualCostPerKm
            computedCostPerKm > 0.0 -> computedCostPerKm
            else -> DEFAULT_COST_PER_KM
        }
}