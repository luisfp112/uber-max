package com.ubermax.app.domain.usecase

import com.ubermax.app.data.db.entity.VehicleConfigEntity
import com.ubermax.app.domain.model.OfferData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests JVM puros del nuevo modelo económico de [EvaluateOfferUseCase]:
 *
 *  - Comisión de la plataforma (default 9%) descontada del bruto.
 *  - Re-estimación de minutos cuando la UI no los expuso (minutesAreEstimated),
 *    usando la velocidad promedio del conductor.
 *  - Costo de ralentí cuando el pickup estimado excede el tiempo de conducción.
 *  - Vuelta vacía SOLO con factor activo y rampa desde el umbral hasta 2× umbral.
 */
class EvaluateOfferUseCaseTest {

    private val evaluator = EvaluateOfferUseCase()

    /** pickupMinutes = 0 evita el ruido del ralentí y hace las cuentas exactas. */
    private fun offer(
        fare: Double = 10.0,
        pickupKm: Double = 1.0,
        tripKm: Double = 6.0,
        pickupMinutes: Int = 0,
        tripMinutes: Int = 20,
        estimatedMinutes: Int = 23,
        minutesAreEstimated: Boolean = false
    ) = OfferData(
        rawFare = fare,
        pickupKm = pickupKm,
        tripKm = tripKm,
        pickupMinutes = pickupMinutes,
        tripMinutes = tripMinutes,
        estimatedMinutes = estimatedMinutes,
        passengerRating = 4.8,
        destination = "Ficoa, Ambato",
        pickupAddress = "Centro",
        minutesAreEstimated = minutesAreEstimated
    )

    @Test
    fun `comision por defecto del 9 por ciento se descuenta del bruto`() {
        val evaluated = evaluator.evaluate(
            offer(fare = 10.0),
            VehicleConfigEntity(manualCostPerKm = 0.08, platformCommissionPercent = 9.0)
        )
        // neto 9.10 = 10 - 9% ; combustible 7km * 0.08 = 0.56
        assertEquals(9.10, evaluated.netProfit + evaluated.fuelCost, 0.001)
        assertEquals(8.54, evaluated.netProfit, 0.001) // 9.10 - 0.56
    }

    @Test
    fun `comision cero no descuenta nada`() {
        val evaluated = evaluator.evaluate(
            offer(fare = 10.0),
            VehicleConfigEntity(manualCostPerKm = 0.10, platformCommissionPercent = 0.0)
        )
        assertEquals(0.70, evaluated.fuelCost, 0.001) // 7km * 0.10
        assertEquals(9.30, evaluated.netProfit, 0.001) // 10.00 - 0.70
    }

    @Test
    fun `ralenti cuenta cuando el pickup estimado excede el tiempo de conduccion`() {
        // pickup de 30 min para 2 km a 22 km/h → 24.55 min de espera pura
        val evaluated = evaluator.evaluate(
            offer(pickupKm = 2.0, pickupMinutes = 30),
            VehicleConfigEntity(
                manualCostPerKm = 0.08,
                platformCommissionPercent = 0.0,
                engineIdleCostPerHour = 1.0
            )
        )
        val expectedIdleHours = (30.0 - 2.0 / 22.0 * 60.0) / 60.0 // ≈ 0.4091 h
        // netProfit + fuelCost = netFare (10) - idleCost → idleCost = 10 - (netProfit + fuelCost)
        assertEquals(expectedIdleHours, 10.0 - (evaluated.netProfit + evaluated.fuelCost), 0.01)
    }

    @Test
    fun `minutos estimados se recalculan con la velocidad promedio del conductor`() {
        val base = VehicleConfigEntity(manualCostPerKm = 0.08, platformCommissionPercent = 0.0)
        val slow = evaluator.evaluate(
            offer(tripKm = 12.0, minutesAreEstimated = true),
            base.copy(avgSpeedKmh = 5.0)
        )
        val fast = evaluator.evaluate(
            offer(tripKm = 12.0, minutesAreEstimated = true),
            base.copy(avgSpeedKmh = 60.0)
        )
        assertTrue(fast.profitPerHour > slow.profitPerHour)
        assertTrue(slow.profitPerHour > 0)
    }

    @Test
    fun `deadhead requiere factor mayor que cero`() {
        val evaluated = evaluator.evaluate(
            offer(tripKm = 20.0),
            VehicleConfigEntity(manualCostPerKm = 0.08, platformCommissionPercent = 0.0),
            deadheadThresholdKm = 8.0,
            deadheadReturnFactor = 0.0
        )
        assertFalse(evaluated.hasDeadheadPenalty)
        assertEquals(0.0, evaluated.returnKm, 0.001)
        assertEquals(21.0, evaluated.totalKm, 0.001)
    }

    @Test
    fun `deadhead con factor 1 rampa suave desde el umbral hasta el doble`() {
        // 12km: rampa (12-8)/8 = 0.5 → retorno = 12*1.0*0.5 = 6
        val atStart = evaluator.evaluate(
            offer(tripKm = 12.0),
            VehicleConfigEntity(manualCostPerKm = 0.08, platformCommissionPercent = 0.0),
            deadheadThresholdKm = 8.0,
            deadheadReturnFactor = 1.0
        )
        assertEquals(6.0, atStart.returnKm, 0.001)

        // 24km: rampa saturada (1.0) → retorno = 24
        val saturated = evaluator.evaluate(
            offer(tripKm = 24.0),
            VehicleConfigEntity(manualCostPerKm = 0.08, platformCommissionPercent = 0.0),
            deadheadThresholdKm = 8.0,
            deadheadReturnFactor = 1.0
        )
        assertEquals(24.0, saturated.returnKm, 0.001)

        // Con factor 0.5 la vuelta se reduce a la mitad
        val half = evaluator.evaluate(
            offer(tripKm = 24.0),
            VehicleConfigEntity(manualCostPerKm = 0.08, platformCommissionPercent = 0.0),
            deadheadThresholdKm = 8.0,
            deadheadReturnFactor = 0.5
        )
        assertEquals(12.0, half.returnKm, 0.001)
    }
}