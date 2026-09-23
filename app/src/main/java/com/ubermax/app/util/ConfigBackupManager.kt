package com.ubermax.app.util

import com.ubermax.app.data.db.entity.AppSettingsEntity
import com.ubermax.app.data.db.entity.FilterRulesEntity
import com.ubermax.app.data.db.entity.VehicleConfigEntity
import org.json.JSONObject

/**
 * Serialización JSON de la configuración del conductor (vehículo, filtros y
 * ajustes operativos).
 *
 * Es 100% puro (solo `org.json`): se valida con JUnit sin dispositivo y no
 * depende de `Context`. La UI se encarga de leer/escribir el archivo y de
 * compartirlo vía FileProvider.
 *
 * Los backups antiguos pueden contener bloques ya descartados (`blacklist`,
 * `zones`, comisión de plataforma, economía de zona, IA, dry-run, ...).
 * `fromJson` los ignora: las claves desconocidas se omiten y las conocidas de
 * features eliminadas no se restauran.
 */
object ConfigBackupManager {

    const val FORMAT_VERSION = 2

    data class Backup(
        val vehicle: VehicleConfigEntity,
        val filter: FilterRulesEntity,
        val settings: AppSettingsEntity
    )

    fun toJson(backup: Backup): String {
        val root = JSONObject()
        root.put("version", FORMAT_VERSION)
        root.put("vehicle", vehicleToJson(backup.vehicle))
        root.put("filter", filterToJson(backup.filter))
        root.put("settings", settingsToJson(backup.settings))
        return root.toString(2)
    }

    /**
     * Parsea un backup. Lanza [IllegalArgumentException] si el JSON es inválido
     * o de una versión no soportada.
     */
    fun fromJson(json: String): Backup {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw IllegalArgumentException("JSON de configuración inválido", e)
        }

        val version = root.optInt("version", 1)
        require(version in 1..FORMAT_VERSION) {
            "Versión de backup no soportada: $version (soportada: 1..$FORMAT_VERSION)"
        }

        return Backup(
            vehicle = vehicleFromJson(root.optJSONObject("vehicle")),
            filter = filterFromJson(root.optJSONObject("filter")),
            settings = settingsFromJson(root.optJSONObject("settings"))
        )
    }

    private fun vehicleToJson(v: VehicleConfigEntity) = JSONObject().apply {
        put("vehicleName", v.vehicleName)
        put("consumptionKmPerUnit", v.consumptionKmPerUnit)
        put("fuelUnitId", v.fuelUnitId)
        put("fuelPricePerUnit", v.fuelPricePerUnit)
        put("maintenancePerKm", v.maintenancePerKm)
        put("avgSpeedKmh", v.avgSpeedKmh)
        put("engineIdleCostPerHour", v.engineIdleCostPerHour)
        put("manualCostPerKm", v.manualCostPerKm)
    }

    private fun vehicleFromJson(o: JSONObject?) = VehicleConfigEntity(
        vehicleName = o?.optString("vehicleName", "") ?: "",
        consumptionKmPerUnit = o?.optDouble("consumptionKmPerUnit", 12.0) ?: 12.0,
        fuelUnitId = o?.optInt("fuelUnitId", 0) ?: 0,
        fuelPricePerUnit = o?.optDouble("fuelPricePerUnit", 0.86) ?: 0.86,
        maintenancePerKm = o?.optDouble("maintenancePerKm", 0.03) ?: 0.03,
        avgSpeedKmh = o?.optDouble("avgSpeedKmh", 22.0) ?: 22.0,
        engineIdleCostPerHour = o?.optDouble("engineIdleCostPerHour", 0.20) ?: 0.20,
        manualCostPerKm = o?.optDouble("manualCostPerKm", 0.0) ?: 0.0
    )

    private fun filterToJson(f: FilterRulesEntity) = JSONObject().apply {
        put("minFare", f.minFare)
        put("minProfitPerKm", f.minProfitPerKm)
        put("pickupMeasure", f.pickupMeasure)
        put("pickupMax", f.pickupMax)
        put("tripMeasure", f.tripMeasure)
        put("tripMax", f.tripMax)
        put("minPassengerRating", f.minPassengerRating)
        put("rejectOnUnknownRating", f.rejectOnUnknownRating)
        put("autoAcceptEnabled", f.autoAcceptEnabled)
    }

    private fun filterFromJson(o: JSONObject?) = FilterRulesEntity(
        minFare = o?.optDouble("minFare", 0.0) ?: 0.0,
        minProfitPerKm = o?.optDouble("minProfitPerKm", 0.15) ?: 0.15,
        pickupMeasure = o?.optString("pickupMeasure", "MIN") ?: "MIN",
        pickupMax = o?.optDouble("pickupMax", 5.0) ?: 5.0,
        tripMeasure = o?.optString("tripMeasure", "MIN") ?: "MIN",
        tripMax = o?.optDouble("tripMax", 0.0) ?: 0.0,
        minPassengerRating = o?.optDouble("minPassengerRating", 0.0) ?: 0.0,
        rejectOnUnknownRating = o?.optBoolean("rejectOnUnknownRating", false) ?: false,
        autoAcceptEnabled = o?.optBoolean("autoAcceptEnabled", true) ?: true
    )

    private fun settingsToJson(s: AppSettingsEntity) = JSONObject().apply {
        put("voiceAnnounceEnabled", s.voiceAnnounceEnabled)
        put("humanTapsEnabled", s.humanTapsEnabled)
        put("geoCheckEnabled", s.geoCheckEnabled)
        put("geoApiKey", s.geoApiKey)
    }

    private fun settingsFromJson(o: JSONObject?) = AppSettingsEntity(
        voiceAnnounceEnabled = o?.optBoolean("voiceAnnounceEnabled", true) ?: true,
        humanTapsEnabled = o?.optBoolean("humanTapsEnabled", true) ?: true,
        geoCheckEnabled = o?.optBoolean("geoCheckEnabled", false) ?: false,
        geoApiKey = o?.optString("geoApiKey", "") ?: ""
    )
}