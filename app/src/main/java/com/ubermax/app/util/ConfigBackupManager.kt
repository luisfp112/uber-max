package com.ubermax.app.util

import com.ubermax.app.data.db.entity.AppSettingsEntity
import com.ubermax.app.data.db.entity.BlacklistEntryEntity
import com.ubermax.app.data.db.entity.BlacklistZoneEntity
import com.ubermax.app.data.db.entity.FilterRulesEntity
import com.ubermax.app.data.db.entity.VehicleConfigEntity
import org.json.JSONArray
import org.json.JSONObject

/**
 * Serialización JSON de la configuración del conductor (vehículo, filtros,
 * lista negra, zonas y ajustes operativos).
 *
 * Es 100% puro (solo `org.json`): se valida con JUnit sin dispositivo y no
 * depende de `Context`. La UI se encarga de leer/escribir el archivo y de
 * compartirlo vía FileProvider.
 */
object ConfigBackupManager {

    const val FORMAT_VERSION = 1

    data class Backup(
        val vehicle: VehicleConfigEntity,
        val filter: FilterRulesEntity,
        val blacklist: List<BlacklistEntryEntity>,
        val zones: List<BlacklistZoneEntity>,
        val settings: AppSettingsEntity
    )

    fun toJson(backup: Backup): String {
        val root = JSONObject()
        root.put("version", FORMAT_VERSION)
        root.put("vehicle", vehicleToJson(backup.vehicle))
        root.put("filter", filterToJson(backup.filter))
        root.put("settings", settingsToJson(backup.settings))

        val blacklist = JSONArray()
        backup.blacklist.forEach { entry ->
            blacklist.put(JSONObject().apply {
                put("keyword", entry.keyword)
                put("type", entry.type)
                put("reason", entry.reason)
            })
        }
        root.put("blacklist", blacklist)

        val zones = JSONArray()
        backup.zones.forEach { zone ->
            zones.put(JSONObject().apply {
                put("name", zone.name)
                put("polygonJson", zone.polygonJson)
                put("latitude", zone.latitude)
                put("longitude", zone.longitude)
                put("radiusMeters", zone.radiusMeters)
                put("extractedKeywordsJson", zone.extractedKeywordsJson)
                put("createdAt", zone.createdAt)
            })
        }
        root.put("zones", zones)

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

        val version = root.optInt("version", 0)
        require(version in 1..FORMAT_VERSION) {
            "Versión de backup no soportada: $version (soportada: 1..$FORMAT_VERSION)"
        }

        return Backup(
            vehicle = vehicleFromJson(root.optJSONObject("vehicle")),
            filter = filterFromJson(root.optJSONObject("filter")),
            settings = settingsFromJson(root.optJSONObject("settings")),
            blacklist = blacklistFromJson(root.optJSONArray("blacklist")),
            zones = zonesFromJson(root.optJSONArray("zones"))
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
        put("platformCommissionPercent", v.platformCommissionPercent)
    }

    private fun vehicleFromJson(o: JSONObject?) = VehicleConfigEntity(
        vehicleName = o?.optString("vehicleName", "") ?: "",
        consumptionKmPerUnit = o?.optDouble("consumptionKmPerUnit", 12.0) ?: 12.0,
        fuelUnitId = o?.optInt("fuelUnitId", 0) ?: 0,
        fuelPricePerUnit = o?.optDouble("fuelPricePerUnit", 0.86) ?: 0.86,
        maintenancePerKm = o?.optDouble("maintenancePerKm", 0.03) ?: 0.03,
        avgSpeedKmh = o?.optDouble("avgSpeedKmh", 22.0) ?: 22.0,
        engineIdleCostPerHour = o?.optDouble("engineIdleCostPerHour", 0.20) ?: 0.20,
        manualCostPerKm = o?.optDouble("manualCostPerKm", 0.0) ?: 0.0,
        platformCommissionPercent = o?.optDouble("platformCommissionPercent", 9.0) ?: 9.0
    )

    private fun filterToJson(f: FilterRulesEntity) = JSONObject().apply {
        put("minFare", f.minFare)
        put("primaryMetric", f.primaryMetric)
        put("minNetProfit", f.minNetProfit)
        put("minProfitPerKm", f.minProfitPerKm)
        put("minProfitPerHour", f.minProfitPerHour)
        put("maxPickupKm", f.maxPickupKm)
        put("maxTripKm", f.maxTripKm)
        put("maxTotalMinutes", f.maxTotalMinutes)
        put("minPassengerRating", f.minPassengerRating)
        put("maxPickupTripRatio", f.maxPickupTripRatio)
        put("blacklistEnabled", f.blacklistEnabled)
        put("autoAcceptEnabled", f.autoAcceptEnabled)
        put("aiEnabled", f.aiEnabled)
        put("deadheadThresholdKm", f.deadheadThresholdKm)
        put("deadheadReturnFactor", f.deadheadReturnFactor)
        put("rejectOnUnknownRating", f.rejectOnUnknownRating)
    }

    private fun filterFromJson(o: JSONObject?) = FilterRulesEntity(
        minFare = o?.optDouble("minFare", 0.0) ?: 0.0,
        primaryMetric = o?.optString("primaryMetric", "PER_HOUR") ?: "PER_HOUR",
        minNetProfit = o?.optDouble("minNetProfit", 0.0) ?: 0.0,
        minProfitPerKm = o?.optDouble("minProfitPerKm", 0.15) ?: 0.15,
        minProfitPerHour = o?.optDouble("minProfitPerHour", 3.00) ?: 3.00,
        maxPickupKm = o?.optDouble("maxPickupKm", 5.0) ?: 5.0,
        maxTripKm = o?.optDouble("maxTripKm", 30.0) ?: 30.0,
        maxTotalMinutes = o?.optInt("maxTotalMinutes", 60) ?: 60,
        minPassengerRating = o?.optDouble("minPassengerRating", 4.5) ?: 4.5,
        maxPickupTripRatio = o?.optDouble("maxPickupTripRatio", 1.0) ?: 1.0,
        blacklistEnabled = o?.optBoolean("blacklistEnabled", true) ?: true,
        autoAcceptEnabled = o?.optBoolean("autoAcceptEnabled", true) ?: true,
        aiEnabled = o?.optBoolean("aiEnabled", true) ?: true,
        deadheadThresholdKm = o?.optDouble("deadheadThresholdKm", 8.0) ?: 8.0,
        deadheadReturnFactor = o?.optDouble("deadheadReturnFactor", 0.0) ?: 0.0,
        rejectOnUnknownRating = o?.optBoolean("rejectOnUnknownRating", false) ?: false
    )

    private fun settingsToJson(s: AppSettingsEntity) = JSONObject().apply {
        put("dryRunEnabled", s.dryRunEnabled)
        put("autoStartOnBoot", s.autoStartOnBoot)
        put("notifyDecisions", s.notifyDecisions)
        put("vibrateOnDecision", s.vibrateOnDecision)
        put("soundOnDecision", s.soundOnDecision)
        put("voiceControlEnabled", s.voiceControlEnabled)
        put("autoStartOnCarBt", s.autoStartOnCarBt)
        put("aiGoodProfitPerKm", s.aiGoodProfitPerKm)
    }

    private fun settingsFromJson(o: JSONObject?) = AppSettingsEntity(
        dryRunEnabled = o?.optBoolean("dryRunEnabled", false) ?: false,
        autoStartOnBoot = o?.optBoolean("autoStartOnBoot", false) ?: false,
        notifyDecisions = o?.optBoolean("notifyDecisions", true) ?: true,
        vibrateOnDecision = o?.optBoolean("vibrateOnDecision", true) ?: true,
        soundOnDecision = o?.optBoolean("soundOnDecision", false) ?: false,
        voiceControlEnabled = o?.optBoolean("voiceControlEnabled", false) ?: false,
        autoStartOnCarBt = o?.optBoolean("autoStartOnCarBt", false) ?: false,
        aiGoodProfitPerKm = o?.optDouble("aiGoodProfitPerKm", 0.25) ?: 0.25
    )

    private fun blacklistFromJson(array: JSONArray?): List<BlacklistEntryEntity> {
        if (array == null) return emptyList()
        val result = mutableListOf<BlacklistEntryEntity>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            result += BlacklistEntryEntity(
                keyword = o.optString("keyword", ""),
                type = o.optString("type", "ZONE"),
                reason = o.optString("reason", "")
            )
        }
        return result
    }

    private fun zonesFromJson(array: JSONArray?): List<BlacklistZoneEntity> {
        if (array == null) return emptyList()
        val result = mutableListOf<BlacklistZoneEntity>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            result += BlacklistZoneEntity(
                name = o.optString("name", ""),
                polygonJson = o.optString("polygonJson", "[]"),
                latitude = o.optDouble("latitude", 0.0),
                longitude = o.optDouble("longitude", 0.0),
                radiusMeters = o.optDouble("radiusMeters", 0.0),
                extractedKeywordsJson = o.optString("extractedKeywordsJson", "[]"),
                createdAt = o.optLong("createdAt", 0L)
            )
        }
        return result
    }
}
