package com.ubermax.app.domain.ai

import com.ubermax.app.data.db.entity.TripLogEntity
import com.ubermax.app.domain.model.Action
import com.ubermax.app.domain.model.EvaluatedOffer
import com.ubermax.app.domain.model.OfferData
import com.ubermax.app.domain.model.OfferDecision
import com.ubermax.app.domain.port.TripHistorySource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests JVM puros del SmartAdvisor (IA local basada en historial).
 *
 * Se usa un [FakeHistory] (implementa TripHistorySource) en lugar de depender
 * de Room o Mockito → 100% JVM, sin dispositivo, sin mocking.
 */
class SmartAdvisorTest {

    /** Fake del puerto de historial: permite inyectar trips o lanzar error. */
    private class FakeHistory(var results: Flow<List<TripLogEntity>>? = null, var error: Throwable? = null) :
        TripHistorySource {
        override fun getAllTripsFlow(): Flow<List<TripLogEntity>> =
            error?.let { flow<List<TripLogEntity>> { throw it } } ?: results ?: flowOf(emptyList())
        override suspend fun getRecentTrips(limit: Int): List<TripLogEntity> =
            error?.let { throw it } ?: results?.firstOrNull() ?: emptyList()
    }

    private fun tripLog(
        destination: String,
        profitPerKm: Double,
        decision: String = "ACCEPT",
        resolution: String = "ASSIGNED",
        netProfit: Double = 3.5,
        rawFare: Double = 10.0,
        pickupAddress: String = "Av. Cevallos",
        pickupKm: Double = 1.0,
        tripKm: Double = 6.0
    ) = TripLogEntity(
        dayOfWeek = "domingo",
        hourOfDay = 14,
        rawFare = rawFare,
        pickupKm = pickupKm,
        tripKm = tripKm,
        estimatedMinutes = 20,
        passengerRating = 4.8,
        destination = destination,
        pickupAddress = pickupAddress,
        fuelCost = 1.0,
        netProfit = netProfit,
        profitPerKm = profitPerKm,
        profitPerHour = 8.0,
        totalKm = pickupKm + tripKm,
        decision = decision,
        resolution = resolution
    )

    private fun evaluatedOffer(
        destination: String = "Ficoa, Ambato",
        fare: Double = 8.0
    ) = EvaluatedOffer(
        offer = OfferData(
            rawFare = fare,
            pickupKm = 1.0,
            tripKm = 6.0,
            pickupMinutes = 3,
            tripMinutes = 20,
            estimatedMinutes = 23,
            passengerRating = 4.8,
            destination = destination,
            pickupAddress = "Centro"
        ),
        fuelCost = 1.2,
        netProfit = 5.0,
        profitPerKm = 0.6,
        profitPerHour = 13.0,
        totalKm = 7.0
    )

    private fun acceptedDecision(destination: String = "Ficoa, Ambato") =
        OfferDecision(evaluatedOffer(destination), Action.ACCEPT)

    private fun warnDecision(destination: String = "Ficoa, Ambato") =
        OfferDecision(evaluatedOffer(destination), Action.WARN, failedFilters = listOf("Tarifa baja"))

    private fun cancelDecision(destination: String = "Ficoa, Ambato") =
        OfferDecision(evaluatedOffer(destination), Action.CANCEL, failedFilters = listOf("LISTA NEGRA"))

    // ═══════════════════════════════════════════════════════
    //  Historial vacío → bajo nivel de confianza
    // ═══════════════════════════════════════════════════════

    @Test
    fun `historial vacio baja la confianza`() = runTest {
        val advisor = SmartAdvisor(FakeHistory(results = flowOf(emptyList())))
        val result = advisor.analyzeOffer(acceptedDecision())

        assertTrue(result.aiRecommendation.contains("dato"))
        assertEquals(0.1, result.aiConfidence, 0.001)
    }

    // ═══════════════════════════════════════════════════════
    //  DECISIÓN ACCEPT
    // ═══════════════════════════════════════════════════════

    @Test
    fun `accept con zona rentable - buena eleccion`() = runTest {
        val history = FakeHistory(
            results = flowOf(
                listOf(
                    tripLog("Ficoa, Ambato", profitPerKm = 0.6, decision = "ACCEPT"),
                    tripLog("Ficoa, Ambato", profitPerKm = 0.55, decision = "ACCEPT")
                )
            )
        )

        val result = SmartAdvisor(history).analyzeOffer(acceptedDecision("Ficoa, Ambato"))

        assertTrue(result.aiRecommendation.contains("Buena elección"))
        assertEquals(0.9, result.aiConfidence, 0.001)
    }

    @Test
    fun `accept con zona poco rentable - precaucion`() = runTest {
        val history = FakeHistory(
            results = flowOf(
                listOf(
                    tripLog("Ficoa, Ambato", profitPerKm = 0.05, decision = "WARN", resolution = "TIMEOUT"),
                    tripLog("Ficoa, Ambato", profitPerKm = 0.03, decision = "WARN", resolution = "TIMEOUT")
                )
            )
        )

        val result = SmartAdvisor(history).analyzeOffer(acceptedDecision("Ficoa, Ambato"))

        assertTrue(result.aiRecommendation.contains("Precaución"))
        assertEquals(0.8, result.aiConfidence, 0.001)
    }

    // ═══════════════════════════════════════════════════════
    //  DECISIÓN WARN
    // ═══════════════════════════════════════════════════════

    @Test
    fun `warn con zona buena - recomienda aceptar`() = runTest {
        val history = FakeHistory(
            results = flowOf(
                listOf(
                    tripLog("Ficoa, Ambato", profitPerKm = 0.7, decision = "ACCEPT"),
                    tripLog("Ficoa, Ambato", profitPerKm = 0.65, decision = "ACCEPT")
                )
            )
        )

        val result = SmartAdvisor(history).analyzeOffer(warnDecision("Ficoa, Ambato"))

        assertTrue(result.aiRecommendation.contains("Recomendado aceptar"))
        assertEquals(0.75, result.aiConfidence, 0.001)
    }

    @Test
    fun `warn con zona mala - recomienda rechazar`() = runTest {
        val history = FakeHistory(
            results = flowOf(
                listOf(
                    tripLog("Ficoa, Ambato", profitPerKm = 0.05, decision = "WARN", resolution = "TIMEOUT"),
                    tripLog("Ficoa, Ambato", profitPerKm = 0.02, decision = "WARN", resolution = "TIMEOUT")
                )
            )
        )

        val result = SmartAdvisor(history).analyzeOffer(warnDecision("Ficoa, Ambato"))

        assertTrue(result.aiRecommendation.contains("Mejor rechazar"))
        assertEquals(0.9, result.aiConfidence, 0.001)
    }

    // ═══════════════════════════════════════════════════════
    //  DECISIÓN CANCEL
    // ═══════════════════════════════════════════════════════

    @Test
    fun `cancel siempre se confirma al maximo`() = runTest {
        val history = FakeHistory(
            results = flowOf(listOf(tripLog("Ficoa, Ambato", profitPerKm = 0.6)))
        )

        val result = SmartAdvisor(history).analyzeOffer(cancelDecision("Ficoa, Ambato"))

        assertEquals(1.0, result.aiConfidence, 0.001)
        assertTrue(result.aiRecommendation.contains("confirmado"))
    }

    // ═══════════════════════════════════════════════════════
    //  Robustez ante errores del historial
    // ═══════════════════════════════════════════════════════

    @Test
    fun `si el historial lanza error no crashea`() = runTest {
        val history = FakeHistory(error = RuntimeException("DB error"))

        val result = SmartAdvisor(history).analyzeOffer(acceptedDecision())

        // Error tipado: se distingue "sin datos" de "no disponible".
        assertTrue(result.aiRecommendation.contains("historial"))
        assertEquals(0.1, result.aiConfidence, 0.001)
    }
}