package com.ubermax.app.util

import com.ubermax.app.data.db.entity.AppSettingsEntity
import com.ubermax.app.data.db.entity.FilterRulesEntity
import com.ubermax.app.data.db.entity.VehicleConfigEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests JVM puros del backup de configuración JSON (round-trip, versiones,
 * errores y tolerancia a bloques de features eliminadas).
 */
class ConfigBackupManagerTest {

    private fun sampleBackup() = ConfigBackupManager.Backup(
        vehicle = VehicleConfigEntity(
            vehicleName = "Chevrolet Aveo",
            consumptionKmPerUnit = 14.0,
            fuelUnitId = 1,
            fuelPricePerUnit = 3.25,
            maintenancePerKm = 0.04,
            avgSpeedKmh = 25.0,
            engineIdleCostPerHour = 0.30,
            manualCostPerKm = 0.09
        ),
        filter = FilterRulesEntity(
            minFare = 2.0,
            minProfitPerKm = 0.2,
            pickupMeasure = "KM",
            pickupMax = 3.5,
            tripMeasure = "MIN",
            tripMax = 45.0,
            minPassengerRating = 4.6,
            rejectOnUnknownRating = true,
            autoAcceptEnabled = true
        ),
        settings = AppSettingsEntity(
            voiceAnnounceEnabled = false,
            humanTapsEnabled = false,
            geoCheckEnabled = true,
            geoApiKey = "pk.examplekey"
        )
    )

    @Test
    fun `round trip conserva toda la configuracion`() {
        val parsed = ConfigBackupManager.fromJson(ConfigBackupManager.toJson(sampleBackup()))

        assertEquals(sampleBackup().vehicle, parsed.vehicle)
        assertEquals(sampleBackup().filter, parsed.filter)
        assertEquals(sampleBackup().settings, parsed.settings)
    }

    @Test
    fun `round trip conserva los toggles operativos`() {
        val parsed = ConfigBackupManager.fromJson(ConfigBackupManager.toJson(sampleBackup()))

        assertEquals(false, parsed.settings.voiceAnnounceEnabled)
        assertEquals(false, parsed.settings.humanTapsEnabled)
        assertEquals(true, parsed.settings.geoCheckEnabled)
        assertEquals("pk.examplekey", parsed.settings.geoApiKey)
    }

    @Test
    fun `json invalido lanza IllegalArgumentException`() {
        try {
            ConfigBackupManager.fromJson("esto no es json")
            org.junit.Assert.fail("Debió lanzar IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("inválido"))
        }
    }

    @Test
    fun `version no soportada lanza IllegalArgumentException`() {
        try {
            ConfigBackupManager.fromJson("""{"version":99}""")
            org.junit.Assert.fail("Debió lanzar IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("Versión"))
        }
    }

    @Test
    fun `campos faltantes usan defaults`() {
        val parsed = ConfigBackupManager.fromJson("""{"version":1}""")
        assertEquals(VehicleConfigEntity(engineIdleCostPerHour = 0.20), parsed.vehicle)
        assertEquals(FilterRulesEntity(minProfitPerKm = 0.15), parsed.filter)
        assertEquals(AppSettingsEntity(), parsed.settings)
    }

    @Test
    fun `backup antiguo con bloques eliminados se importa ignorandolos`() {
        val json = """
            {"version":1,
             "filter":{"minFare":1.5,"primaryMetric":"PER_HOUR","minProfitPerHour":4.0,
                       "blacklistEnabled":true,"aiEnabled":true},
             "blacklist":[{"keyword":"Ficoa","type":"SECTOR","reason":"mala zona"}],
             "zones":[{"name":"Centro","polygonJson":"[[1,2]]","latitude":-1.24}],
             "settings":{"dryRunEnabled":true,"autoStartOnBoot":true,"notifyDecisions":false,
                         "voiceControlEnabled":true,"geoRadiusKm":12.0},
             "vehicle":{"platformCommissionPercent":9.0}}
        """.trimIndent()
        val parsed = ConfigBackupManager.fromJson(json)

        assertEquals(1.5, parsed.filter.minFare, 0.001)
        // el backup guardaba primaryMetric/hora, pero la entidad actual solo conserva lo vigente:
        assertEquals(0.15, parsed.filter.minProfitPerKm, 0.001)
        assertEquals(AppSettingsEntity(geoCheckEnabled = false), parsed.settings)
        assertEquals(VehicleConfigEntity(), parsed.vehicle)
    }

    @Test
    fun `backup con campos vigentes sobreescribe los defaults`() {
        val json = """
            {"version":2,
             "filter":{"minProfitPerKm":0.35},
             "settings":{"voiceAnnounceEnabled":true,"geoCheckEnabled":true,"geoApiKey":"pk.x"}}
        """.trimIndent()
        val parsed = ConfigBackupManager.fromJson(json)

        assertEquals(0.35, parsed.filter.minProfitPerKm, 0.001)
        assertTrue(parsed.settings.voiceAnnounceEnabled)
        assertTrue(parsed.settings.geoCheckEnabled)
        assertEquals("pk.x", parsed.settings.geoApiKey)
    }
}