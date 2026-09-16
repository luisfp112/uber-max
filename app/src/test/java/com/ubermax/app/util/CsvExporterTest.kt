package com.ubermax.app.util

import com.ubermax.app.data.db.entity.TripLogEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvExporterTest {

    private fun makeTrip(
        dest: String = "Ficoa",
        pickup: String = "Centro",
        decision: String = "ACCEPT",
        fare: Double = 10.0,
        tripKm: Double = 6.0,
        pickupKm: Double = 1.0
    ) = TripLogEntity(
        dayOfWeek = "lunes",
        hourOfDay = 14,
        rawFare = fare,
        pickupKm = pickupKm,
        tripKm = tripKm,
        estimatedMinutes = 20,
        passengerRating = 4.8,
        destination = dest,
        pickupAddress = pickup,
        fuelCost = 1.0,
        netProfit = fare - 1.0,
        profitPerKm = (fare - 1.0) / tripKm,
        profitPerHour = (fare - 1.0) / (20.0 / 60.0),
        totalKm = pickupKm + tripKm,
        decision = decision,
        failedFilters = ""
    )

    @Test
    fun `cabecera tiene 17 columnas`() {
        val csv = CsvExporter.buildCsv(emptyList())
        val header = csv.lines().first().split(",")
        assertEquals(17, header.size)
    }

    @Test
    fun `viaje vacio produce solo cabecera`() {
        val csv = CsvExporter.buildCsv(emptyList())
        assertEquals(1, csv.lines().size)
    }

    @Test
    fun `dos viajes generan una linea por viaje mas cabecera`() {
        val csv = CsvExporter.buildCsv(listOf(makeTrip(), makeTrip(dest = "Huachi")))
        assertEquals(3, csv.lines().size)
        assertTrue(csv.contains("Ficoa"))
        assertTrue(csv.contains("Huachi"))
    }

    @Test
    fun `escape comas dobles`() {
        val value = CsvExporter.escape("A, B")
        assertEquals("\"A, B\"", value)
    }

    @Test
    fun `escape saltos de linea`() {
        val value = CsvExporter.escape("Primera\nSegunda")
        assertEquals("\"Primera\nSegunda\"", value)
    }

    @Test
    fun `escape comillas dobles`() {
        val value = CsvExporter.escape("Valor con \"comillas\"")
        assertEquals("\"Valor con \"\"comillas\"\"\"", value)
    }

    @Test
    fun `texto sin caracteres especiales no cambia`() {
        assertEquals("Ficoa", CsvExporter.escape("Ficoa"))
    }

    @Test
    fun `formatNum siempre dos decimales`() {
        assertEquals("10.00", CsvExporter.formatNum(10.0))
        assertEquals("3.14", CsvExporter.formatNum(3.14159))
        assertEquals("0.00", CsvExporter.formatNum(0.0))
    }
}