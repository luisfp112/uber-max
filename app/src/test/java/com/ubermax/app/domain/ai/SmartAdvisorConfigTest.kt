package com.ubermax.app.domain.ai

import com.ubermax.app.data.db.entity.TripLogEntity
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests JVM puros del scoring IA configurable (referencia de $/km).
 */
class SmartAdvisorConfigTest {

    private val noopHistory = object : com.ubermax.app.domain.port.TripHistorySource {
        override fun getAllTripsFlow() = flowOf(emptyList<TripLogEntity>())
        override suspend fun getRecentTrips(limit: Int) = emptyList<TripLogEntity>()
    }

    private fun trip(profitPerKm: Double, decision: String = "ACCEPT") = TripLogEntity(
        dayOfWeek = "lunes",
        hourOfDay = 12,
        rawFare = 8.0,
        pickupKm = 1.0,
        tripKm = 6.0,
        estimatedMinutes = 20,
        passengerRating = 4.8,
        destination = "Ficoa, Ambato",
        pickupAddress = "Centro",
        fuelCost = 1.0,
        netProfit = profitPerKm * 7,
        profitPerKm = profitPerKm,
        profitPerHour = 10.0,
        totalKm = 7.0,
        decision = decision
    )

    @Test
    fun `una referencia mas alta baja el score`() {
        val advisor = SmartAdvisor(noopHistory)
        val history = listOf(trip(0.25), trip(0.25))

        val strict = advisor.calculateDestinationScore("Ficoa, Ambato", history, goodProfitPerKm = 0.5)
        val lenient = advisor.calculateDestinationScore("Ficoa, Ambato", history, goodProfitPerKm = 0.25)

        assertTrue(strict < lenient)
        assertEquals(1.0, lenient, 0.001)
    }

    @Test
    fun `referencia invalida usa el default`() {
        val advisor = SmartAdvisor(noopHistory)
        val history = listOf(trip(0.25))
        val withZero = advisor.calculateDestinationScore("Ficoa, Ambato", history, goodProfitPerKm = 0.0)
        val withDefault = advisor.calculateDestinationScore(
            "Ficoa, Ambato",
            history,
            goodProfitPerKm = SmartAdvisor.DEFAULT_GOOD_PROFIT_PER_KM
        )
        assertEquals(withDefault, withZero, 0.001)
    }

    @Test
    fun `destino desconocido devuelve neutro`() {
        val advisor = SmartAdvisor(noopHistory)
        assertEquals(0.5, advisor.calculateDestinationScore("Zona Nueva", emptyList(), 0.25), 0.001)
    }
}
