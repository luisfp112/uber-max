package com.ubermax.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripLogDao(): TripLogDao
    abstract fun vehicleConfigDao(): VehicleConfigDao
    abstract fun filterRulesDao(): FilterRulesDao
    abstract fun blacklistDao(): BlacklistDao
    abstract fun blacklistZoneDao(): BlacklistZoneDao

    companion object {
        /**
         * Migración v5 → v6: añade columnas de polígono a blacklist_zone.
         * Las zonas legacy (círculos) mantienen sus campos originales
         * (latitude/longitude/radius) y la conversión a polígono se hace
         * dinámicamente en [com.ubermax.app.domain.geometry.PolygonGeometry].
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE blacklist_zone ADD COLUMN polygon_json TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE blacklist_zone ADD COLUMN extracted_keywords_json TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE blacklist_zone ADD COLUMN created_at INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Migración v6 → v7: añade índices para acelerar consultas de historial,
         * exportación y comparación de blacklist.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_trip_log_timestamp ON trip_log (timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_trip_log_destination ON trip_log (destination)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_trip_log_hour_of_day ON trip_log (hour_of_day)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_trip_log_decision ON trip_log (decision)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_blacklist_entry_keyword ON blacklist_entry (keyword)")
                // Umbral de deadhead configurable (por defecto 8.0 km)
                db.execSQL("ALTER TABLE filter_rules ADD COLUMN deadhead_threshold_km REAL NOT NULL DEFAULT 8.0")
            }
        }
    }
}