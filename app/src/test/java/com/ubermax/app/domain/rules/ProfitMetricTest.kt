package com.ubermax.app.domain.rules

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests JVM puros del parser tolerante de la métrica principal.
 */
class ProfitMetricTest {

    @Test
    fun `parsea claves validas`() {
        assertEquals(ProfitMetric.PER_HOUR, ProfitMetric.fromKey("PER_HOUR"))
        assertEquals(ProfitMetric.PER_KM, ProfitMetric.fromKey("PER_KM"))
    }

    @Test
    fun `es tolerante a mayusculas`() {
        assertEquals(ProfitMetric.PER_KM, ProfitMetric.fromKey("per_km"))
        assertEquals(ProfitMetric.PER_HOUR, ProfitMetric.fromKey("per_hour"))
    }

    @Test
    fun `valores desconocidos o nulos caen en por hora`() {
        assertEquals(ProfitMetric.PER_HOUR, ProfitMetric.fromKey(null))
        assertEquals(ProfitMetric.PER_HOUR, ProfitMetric.fromKey(""))
        assertEquals(ProfitMetric.PER_HOUR, ProfitMetric.fromKey("lo_que_sea"))
    }
}
