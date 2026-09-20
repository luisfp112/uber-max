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
    fun `cabecera tiene 29 columnas`() {
        val csv = CsvExporter.buildCsv(emptyList())
        val header = csv.lines().first().split(",")
        assertEquals(29, header.size)
    }

    @Test
    fun `cabecera incluye las columnas de resultados reales`() {
        val header = CsvExporter.buildCsv(emptyList()).lines().first()
        for (col in listOf(
            "tiene_rating", "minutos_estimados", "accion_aplicada", "resolucion",
            "recomendacion_ia", "confianza_ia", "pickup_lat", "pickup_lng",
            "dest_lat", "dest_lng"
        )) {
            assertTrue("Columna '$col' ausente en: $header", header.split(",").contains(col))
        }
    }

    @Test
    fun `la linea refleja resolucion accion aplicada rating y coordenadas`() {
        val trip = makeTrip().copy(
            hasRating = false,
            minutesEstimated = true,
            actionApplied = true,
            resolution = "TAP_FAILED",
            aiRecommendation = "IA local",
            aiConfidence = 0.9,
            pickupLat = -1.2345,
            pickupLng = -78.6102,
            destLat = -1.2800,
            destLng = -78.6500
        )
        val row = CsvExporter.buildCsv(listOf(trip)).lines().last()
        assertTrue(row.contains("false"))
        assertTrue(row.contains("true"))
        assertTrue(row.contains("TAP_FAILED"))
        assertTrue(row.contains("IA local"))
        assertTrue(row.contains("0.90"))
        assertTrue(row.contains("-78.61")) // pickupLng
        assertTrue(row.contains("-1.28")) // destLat
    }

    @Test
    fun `coordenadas nulas se exportan vacias`() {
        val row = CsvExporter.buildCsv(listOf(makeTrip())).lines().last()
        val fields = row.split(",")
        // pickup_lat / pickup_lng llegan al final vacíos por ser los últimos
        assertEquals(29, fields.size)
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