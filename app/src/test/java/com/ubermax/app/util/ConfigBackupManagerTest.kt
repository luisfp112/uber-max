package com.ubermax.app.util

import com.ubermax.app.data.db.entity.AppSettingsEntity
import com.ubermax.app.data.db.entity.BlacklistEntryEntity
import com.ubermax.app.data.db.entity.BlacklistZoneEntity
import com.ubermax.app.data.db.entity.FilterRulesEntity
import com.ubermax.app.data.db.entity.VehicleConfigEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests JVM puros del backup de configuración JSON (round-trip, versiones y errores).
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
            primaryMetric = "PER_KM",
            minNetProfit = 1.0,
            minProfitPerKm = 0.2,
            minProfitPerHour = 4.0,
            maxPickupKm = 6.0,
            maxTripKm = 40.0,
            maxTotalMinutes = 75,
            minPassengerRating = 4.6,
            maxPickupTripRatio = 0.8,
            blacklistEnabled = false,
            autoAcceptEnabled = true,
            aiEnabled = false,
            deadheadThresholdKm = 10.0
        ),
        blacklist = listOf(
            BlacklistEntryEntity(keyword = "Ficoa", type = "SECTOR", reason = "mala zona"),
            BlacklistEntryEntity(keyword = "Terminal", type = "ZONE")
        ),
        zones = listOf(
            BlacklistZoneEntity(
                name = "Centro",
                polygonJson = "[[1.0,2.0],[3.0,4.0],[5.0,6.0]]",
                latitude = -1.24,
                longitude = -78.62,
                radiusMeters = 500.0,
                extractedKeywordsJson = "[\"Centro\"]",
                createdAt = 123456789L
            )
        ),
        settings = AppSettingsEntity(
            dryRunEnabled = true,
            autoStartOnBoot = true,
            notifyDecisions = false,
            vibrateOnDecision = false,
            soundOnDecision = true,
            voiceControlEnabled = true,
            autoStartOnCarBt = true,
            aiGoodProfitPerKm = 0.4
        )
    )

    @Test
    fun `round trip conserva toda la configuracion`() {
        val parsed = ConfigBackupManager.fromJson(ConfigBackupManager.toJson(sampleBackup()))

        assertEquals(sampleBackup().vehicle, parsed.vehicle)
        assertEquals(sampleBackup().filter, parsed.filter)
        assertEquals(sampleBackup().settings, parsed.settings)

        assertEquals(2, parsed.blacklist.size)
        assertEquals("Ficoa", parsed.blacklist[0].keyword)
        assertEquals("SECTOR", parsed.blacklist[0].type)
        assertEquals("mala zona", parsed.blacklist[0].reason)

        assertEquals(1, parsed.zones.size)
        val zone = parsed.zones[0]
        assertEquals("Centro", zone.name)
        assertEquals("[[1.0,2.0],[3.0,4.0],[5.0,6.0]]", zone.polygonJson)
        assertEquals(500.0, zone.radiusMeters, 0.0)
        assertEquals(123456789L, zone.createdAt)
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
        assertEquals(VehicleConfigEntity(), parsed.vehicle)
        assertEquals(FilterRulesEntity(), parsed.filter)
        assertEquals(AppSettingsEntity(), parsed.settings)
        assertTrue(parsed.blacklist.isEmpty())
        assertTrue(parsed.zones.isEmpty())
    }

    @Test
    fun `backup antiguo sin primaryMetric cae en por hora`() {
        val parsed = ConfigBackupManager.fromJson("""{"version":1,"filter":{"minFare":1.5}}""")
        assertEquals("PER_HOUR", parsed.filter.primaryMetric)
    }
}
