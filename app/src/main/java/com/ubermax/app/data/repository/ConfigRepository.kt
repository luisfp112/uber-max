package com.ubermax.app.data.repository

import com.ubermax.app.data.db.dao.BlacklistDao
import com.ubermax.app.data.db.dao.BlacklistZoneDao
import com.ubermax.app.data.db.dao.FilterRulesDao
import com.ubermax.app.data.db.dao.VehicleConfigDao
import com.ubermax.app.data.db.dao.AppSettingsDao
import com.ubermax.app.data.db.entity.AppSettingsEntity
import com.ubermax.app.data.db.entity.BlacklistEntryEntity
import com.ubermax.app.data.db.entity.BlacklistZoneEntity
import com.ubermax.app.data.db.entity.FilterRulesEntity
import com.ubermax.app.data.db.entity.VehicleConfigEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfigRepository @Inject constructor(
    private val vehicleConfigDao: VehicleConfigDao,
    private val filterRulesDao: FilterRulesDao,
    private val blacklistDao: BlacklistDao,
    private val blacklistZoneDao: BlacklistZoneDao,
    private val appSettingsDao: AppSettingsDao
) {
    // ── Vehicle Config ──
    suspend fun getVehicleConfig(): VehicleConfigEntity {
        return vehicleConfigDao.getConfig() ?: VehicleConfigEntity().also {
            vehicleConfigDao.insertOrUpdate(it)
        }
    }
    fun getVehicleConfigFlow(): Flow<VehicleConfigEntity?> = vehicleConfigDao.getConfigFlow()
    suspend fun updateVehicleConfig(config: VehicleConfigEntity) {
        vehicleConfigDao.insertOrUpdate(config)
    }

    // ── Filter Rules ──
    suspend fun getFilterRules(): FilterRulesEntity {
        return filterRulesDao.getRules() ?: FilterRulesEntity().also {
            filterRulesDao.insertOrUpdate(it)
        }
    }
    fun getFilterRulesFlow(): Flow<FilterRulesEntity?> = filterRulesDao.getRulesFlow()
    suspend fun updateFilterRules(rules: FilterRulesEntity) {
        filterRulesDao.insertOrUpdate(rules)
    }

    // ── Blacklist (texto) ──
    fun getBlacklistFlow(): Flow<List<BlacklistEntryEntity>> = blacklistDao.getAllEntriesFlow()
    suspend fun getAllBlacklistKeywords(): List<String> = blacklistDao.getAllKeywords()
    suspend fun getAllBlacklist(): List<BlacklistEntryEntity> = blacklistDao.getAll()
    suspend fun addBlacklistEntry(entry: BlacklistEntryEntity) { blacklistDao.insert(entry) }
    suspend fun removeBlacklistEntry(entry: BlacklistEntryEntity) { blacklistDao.delete(entry) }
    suspend fun removeBlacklistEntriesForZone(zoneName: String) {
        blacklistDao.deleteByReasonPattern("%Auto-extracted from zone '$zoneName'%")
    }

    /**
     * Retorna TODAS las keywords de la lista negra:
     * - Keywords manuales (blacklist_entry)
     * - Keywords extraídas automáticamente de polígonos (extracted_keywords_json de cada zona)
     * - Nombres de zona (como fallback para match por texto)
     *
     * Duplicados se eliminan automáticamente.
     */
    suspend fun getAllMergedBlacklistKeywords(): List<String> {
        val allKeywords = blacklistDao.getAllKeywords().toMutableSet()
        val zones = blacklistZoneDao.getAll()

        for (zone in zones) {
            allKeywords.add(zone.name)
            zone.extractedKeywords.forEach { kw -> allKeywords.add(kw) }
        }

        return allKeywords.toList()
    }

    // ── Blacklist Zones (mapa) ──
    suspend fun getAllBlacklistZones(): List<BlacklistZoneEntity> = blacklistZoneDao.getAll()
    fun getBlacklistZonesFlow(): Flow<List<BlacklistZoneEntity>> = blacklistZoneDao.getAllFlow()
    suspend fun getAllBlacklistZoneNames(): List<String> = blacklistZoneDao.getAllNames()
    suspend fun addBlacklistZone(zone: BlacklistZoneEntity): Long = blacklistZoneDao.insert(zone)
    suspend fun updateBlacklistZone(zone: BlacklistZoneEntity) { blacklistZoneDao.update(zone) }
    suspend fun removeBlacklistZone(zone: BlacklistZoneEntity) { blacklistZoneDao.delete(zone) }
    suspend fun removeBlacklistZoneById(id: Int) { blacklistZoneDao.deleteById(id) }

    // ── App Settings (operativos) ──
    suspend fun getAppSettings(): AppSettingsEntity {
        return appSettingsDao.getSettings() ?: AppSettingsEntity().also {
            appSettingsDao.insertOrUpdate(it)
        }
    }
    fun getAppSettingsFlow(): Flow<AppSettingsEntity?> = appSettingsDao.getSettingsFlow()
    suspend fun updateAppSettings(settings: AppSettingsEntity) {
        appSettingsDao.insertOrUpdate(settings)
    }
}
