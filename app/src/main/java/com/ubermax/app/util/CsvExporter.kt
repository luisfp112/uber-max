package com.ubermax.app.util

import com.ubermax.app.data.db.entity.TripLogEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Generador de CSV del historial de viajes.
 *
 * 100% JVM puro (sin Android) para poder testearse con JUnit.
 */
object CsvExporter {

    fun buildCsv(trips: List<TripLogEntity>): String {
        val header = listOf(
            "timestamp", "dia", "hora", "tarifa_bruta", "pickup_km", "trip_km",
            "minutos", "rating", "destino", "pickup", "costo_combustible",
            "ganancia_neta", "ganancia_km", "ganancia_hora", "km_total",
            "decision", "filtros_fallidos"
        ).joinToString(",")

        val rows = trips.map { t ->
            listOf(
                formatTimestamp(t.timestamp),
                escape(t.dayOfWeek),
                t.hourOfDay.toString(),
                formatNum(t.rawFare),
                formatNum(t.pickupKm),
                formatNum(t.tripKm),
                t.estimatedMinutes.toString(),
                formatNum(t.passengerRating),
                escape(t.destination),
                escape(t.pickupAddress),
                formatNum(t.fuelCost),
                formatNum(t.netProfit),
                formatNum(t.profitPerKm),
                formatNum(t.profitPerHour),
                formatNum(t.totalKm),
                t.decision,
                escape(t.failedFilters)
            ).joinToString(",")
        }

        return (listOf(header) + rows).joinToString("\n")
    }

    fun formatTimestamp(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))

    fun formatNum(value: Double): String =
        String.format(Locale.US, "%.2f", value)

    fun escape(field: String): String {
        val safe = field.replace("\"", "\"\"")
        return if (safe.contains(',') || safe.contains('\n') || safe.contains('"')) {
            "\"$safe\""
        } else {
            safe
        }
    }
}