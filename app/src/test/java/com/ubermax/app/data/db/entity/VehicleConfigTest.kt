package com.ubermax.app.data.db.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests JVM puros para VehicleConfigEntity: costo dinámico vs manual.
 *
 * Verifica que el conductor puede configurar:
 *  - Costo calculado automáticamente (consumo + precio + mantenimiento)
 *  - Costo manual que reemplaza el cálculo
 *  - Fallback a un costo por defecto cuando no hay configuración
 *  - Unidad de combustible litro vs galón
 */
class VehicleConfigTest {

    @Test
    fun `costo manual reemplaza el calculado`() {
        val v = VehicleConfigEntity(
            consumptionKmPerUnit = 12.0,
            fuelUnitId = 0, // litro
            fuelPricePerUnit = 0.86,
            maintenancePerKm = 0.03,
            manualCostPerKm = 0.08
        )
        assertEquals(0.08, v.costPerKm, 0.001)
    }

    @Test
    fun `costo calculado en litros`() {
        val v = VehicleConfigEntity(
            consumptionKmPerUnit = 10.0,  // 10 km por litro
            fuelUnitId = 0,               // litro
            fuelPricePerUnit = 1.00,      // $1.00/litro
            maintenancePerKm = 0.05,      // $0.05/km
            manualCostPerKm = 0.0         // sin manual
        )
        // fuelCost = 1.00 / 10 = 0.10 $/km; maintenance = 0.05 → total 0.15
        assertEquals(0.15, v.costPerKm, 0.001)
        assertEquals(0.10, v.fuelCostPerKm, 0.001)
        assertEquals(0.15, v.computedCostPerKm, 0.001)
        assertEquals("litro", v.fuelUnitLabel)
    }

    @Test
    fun `costo calculado en galones`() {
        val v = VehicleConfigEntity(
            consumptionKmPerUnit = 30.0,  // 30 km por galón
            fuelUnitId = 1,               // galón
            fuelPricePerUnit = 3.00,      // $3.00/galón
            maintenancePerKm = 0.02,
            manualCostPerKm = 0.0
        )
        // fuelCost = 3.00 / 30 = 0.10; maintenance 0.02 → total 0.12
        assertEquals(0.12, v.costPerKm, 0.001)
        assertEquals("galón", v.fuelUnitLabel)
    }

    @Test
    fun `sin configuracion de consumo usa fallback por defecto`() {
        val v = VehicleConfigEntity(
            consumptionKmPerUnit = 0.0,
            fuelUnitId = 0,
            fuelPricePerUnit = 0.86,
            maintenancePerKm = 0.0,
            manualCostPerKm = 0.0
        )
        // fuelCost 0 + maintenance 0 = 0 → computed = 0 → fallback
        assertEquals(0.0, v.computedCostPerKm, 0.001)
        assertEquals(VehicleConfigEntity.DEFAULT_COST_PER_KM, v.costPerKm, 0.001)
        assertEquals(0.10, v.costPerKm, 0.001)
    }

    @Test
    fun `valores por defecto del entity`() {
        val v = VehicleConfigEntity()
        assertEquals(12.0, v.consumptionKmPerUnit, 0.001)
        assertEquals(0.86, v.fuelPricePerUnit, 0.001)
        assertEquals(0.03, v.maintenancePerKm, 0.001)
        assertEquals(0.0, v.manualCostPerKm, 0.001)
        assertEquals(22.0, v.avgSpeedKmh, 0.001)
        // Default cost: (0.86/12)+0.03 ≈ 0.1017
        assertTrue(v.costPerKm > 0.0)
        assertTrue(v.costPerKm < 0.15)
    }

    @Test
    fun `manual en cero no invalida el calculo`() {
        val v = VehicleConfigEntity(
            consumptionKmPerUnit = 15.0,
            fuelPricePerUnit = 0.75,
            maintenancePerKm = 0.04,
            manualCostPerKm = 0.0
        )
        // 0.75/15 + 0.04 = 0.09
        assertEquals(0.09, v.costPerKm, 0.001)
    }
}
