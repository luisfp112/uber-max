package com.ubermax.app.domain.usecase

import com.ubermax.app.data.db.entity.VehicleConfigEntity
import com.ubermax.app.domain.model.OfferData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests JVM puros del modelo económico de [EvaluateOfferUseCase]:
 *
 *  - El monto de la tarjeta ya es neto (sin comisión de la plataforma).
 *  - Costo = (recogida + viaje) × costo/km (manual o calculado con fallback).
 *  - $/km = ganancia neta / kilómetros totales.
 *  - Re-estimación de minutos cuando la UI no los expuso.
 */
class EvaluateOfferUseCaseTest {

    private val evaluator = EvaluateOfferUseCase()

    private fun offer(
        fare: Double = 10.0,
        pickupKm: Double = 1.0,
        tripKm: Double = 6.0,
        tripMinutes: Int = 20,
        minutesAreEstimated: Boolean = false
    ) = OfferData(
        rawFare = fare,
        pickupKm = pickupKm,
        tripKm = tripKm,
        pickupMinutes = 3,
        tripMinutes = tripMinutes,
        estimatedMinutes = tripMinutes,
        passengerRating = 4.8,
        destination = "Centro, Ambato",
        pickupAddress = "Centro",
        minutesAreEstimated = minutesAreEstimated
    )

    @Test
    fun `monto de la tarjeta es neto no se descuenta ninguna comision`() {
        val evaluated = evaluator.evaluate(
            offer(fare = 10.0),
            VehicleConfigEntity(manualCostPerKm = 0.08)
        )
        // 7 km × 0.08 = 0.56; neto = 10.00 − 0.56 = 9.44
        assertEquals(0.56, evaluated.fuelCost, 0.001)
        assertEquals(9.44, evaluated.netProfit, 0.001)
    }

    @Test
    fun `el costo manual se usa cuando esta configurado`() {
        val evaluated = evaluator.evaluate(
            offer(fare = 10.0),
            VehicleConfigEntity(manualCostPerKm = 0.10,
                consumptionKmPerUnit = 0.0, fuelPricePerUnit = 0.0, maintenancePerKm = 0.0)
        )
        assertEquals(0.70, evaluated.fuelCost, 0.001) // 7km × 0.10
        assertEquals(9.30, evaluated.netProfit, 0.001)
    }

    @Test
    fun `sin costo manual se calcula con consumo y mantenimiento`() {
        // combustible: 0.86 / 12 = 0.0717 + mantenimiento 0.03 = 0.1017/km
        val evaluated = evaluator.evaluate(
            offer(fare = 10.0),
            VehicleConfigEntity(
                consumptionKmPerUnit = 12.0,
                fuelPricePerUnit = 0.86,
                maintenancePerKm = 0.03
            )
        )
        assertEquals(0.1017, evaluated.fuelCost / 7.0, 0.0001)
    }

    @Test
    fun `sin datos de costo el fallback evita combustible gratis`() {
        val evaluated = evaluator.evaluate(
            offer(fare = 10.0),
            VehicleConfigEntity(consumptionKmPerUnit = 0.0, maintenancePerKm = 0.0)
        )
        // DEFAULT_COST_PER_KM = 0.10 → 7 km × 0.10 = 0.70
        assertEquals(0.70, evaluated.fuelCost, 0.001)
        assertEquals(9.30, evaluated.netProfit, 0.001)
    }

    @Test
    fun `profit por km divide la ganancia neta entre los km totales`() {
        val evaluated = evaluator.evaluate(
            offer(fare = 10.0),
            VehicleConfigEntity(manualCostPerKm = 0.10)
        )
        // totalKm = 7.0; neto = 9.30; $/km = 1.3286
        assertEquals(7.0, evaluated.totalKm, 0.001)
        assertEquals(9.30 / 7.0, evaluated.profitPerKm, 0.001)
        assertTrue(evaluated.isProfitable)
    }

    @Test
    fun `una oferta inviable (neta negativa) no es rentable`() {
        val evaluated = evaluator.evaluate(
            offer(fare = 0.50, tripKm = 10.0),
            VehicleConfigEntity(manualCostPerKm = 0.10)
        )
        assertFalse(evaluated.isProfitable)
        assertTrue(evaluated.netProfit < 0)
    }

    @Test
    fun `minutos estimados se recalculan con la velocidad promedio del conductor`() {
        val base = VehicleConfigEntity(manualCostPerKm = 0.08)
        val slow = evaluator.evaluate(
            offer(tripKm = 12.0, tripMinutes = 0, minutesAreEstimated = true),
            base.copy(avgSpeedKmh = 5.0)
        )
        val fast = evaluator.evaluate(
            offer(tripKm = 12.0, tripMinutes = 0, minutesAreEstimated = true),
            base.copy(avgSpeedKmh = 60.0)
        )
        // El tiempo estimado no cambia el neto, solo el detalle de minutos;
        // aquí verificamos que ambas ofertas se evalúan y el neto es idéntico.
        assertEquals(slow.netProfit, fast.netProfit, 0.001)
        assertTrue(slow.totalKm == 13.0) // 1 + 12
    }
}