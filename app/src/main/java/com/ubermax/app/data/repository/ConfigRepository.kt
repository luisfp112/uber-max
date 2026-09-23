package com.ubermax.app.data.repository

import com.ubermax.app.data.db.dao.AppSettingsDao
import com.ubermax.app.data.db.dao.FilterRulesDao
import com.ubermax.app.data.db.dao.VehicleConfigDao
import com.ubermax.app.data.db.entity.AppSettingsEntity
import com.ubermax.app.data.db.entity.FilterRulesEntity
import com.ubermax.app.data.db.entity.VehicleConfigEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfigRepository @Inject constructor(
    private val vehicleConfigDao: VehicleConfigDao,
    private val filterRulesDao: FilterRulesDao,
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