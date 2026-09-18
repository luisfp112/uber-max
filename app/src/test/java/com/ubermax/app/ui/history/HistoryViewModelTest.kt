package com.ubermax.app.ui.history

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests JVM puros de los rangos temporales del historial.
 */
class HistoryViewModelTest {

    private val fixedNow = 1_700_000_000_000L
    private val dayMs = 24L * 60 * 60 * 1000

    @Test
    fun `hoy corta a la medianoche`() {
        val cutoff = HistoryViewModel.cutoffMillis(HistoryFilter.TODAY, fixedNow)
        val cal = Calendar.getInstance().apply { timeInMillis = cutoff }
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertEquals(0, cal.get(Calendar.SECOND))
        assertEquals(0, cal.get(Calendar.MILLISECOND))
    }

    @Test
    fun `ultimos 7 y 30 dias`() {
        assertEquals(fixedNow - 7 * dayMs, HistoryViewModel.cutoffMillis(HistoryFilter.LAST_7D, fixedNow))
        assertEquals(fixedNow - 30 * dayMs, HistoryViewModel.cutoffMillis(HistoryFilter.LAST_30D, fixedNow))
    }

    @Test
    fun `todo no corta nada`() {
        assertEquals(0L, HistoryViewModel.cutoffMillis(HistoryFilter.ALL, fixedNow))
    }

    @Test
    fun `startOfDay es anterior o igual al instante`() {
        val start = HistoryViewModel.startOfDay(fixedNow)
        assert(start <= fixedNow)
        assert(fixedNow - start < dayMs)
    }
}
