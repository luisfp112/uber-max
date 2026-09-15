package com.ubermax.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ubermax.app.data.db.dao.*
import com.ubermax.app.data.db.entity.*

@Database(
    entities = [
        TripLogEntity::class,
        VehicleConfigEntity::class,
        FilterRulesEntity::class,
        BlacklistEntryEntity::class,
        BlacklistZoneEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripLogDao(): TripLogDao
    abstract fun vehicleConfigDao(): VehicleConfigDao
    abstract fun filterRulesDao(): FilterRulesDao
    abstract fun blacklistDao(): BlacklistDao
    abstract fun blacklistZoneDao(): BlacklistZoneDao
}
