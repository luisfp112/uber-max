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
        BlacklistZoneEntity::class,
        AppSettingsEntity::class
    ],
    version = 10,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripLogDao(): TripLogDao
    abstract fun vehicleConfigDao(): VehicleConfigDao
    abstract fun filterRulesDao(): FilterRulesDao
    abstract fun blacklistDao(): BlacklistDao
    abstract fun blacklistZoneDao(): BlacklistZoneDao
    abstract fun appSettingsDao(): AppSettingsDao

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

        /**
         * Migración v7 → v8: crea la tabla de ajustes operativos (singleton id = 1).
         * Los defaults de la tabla coinciden con [AppSettingsEntity].
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `app_settings` (
                        `id` INTEGER NOT NULL,
                        `dry_run_enabled` INTEGER NOT NULL DEFAULT 0,
                        `auto_start_on_boot` INTEGER NOT NULL DEFAULT 0,
                        `notify_decisions` INTEGER NOT NULL DEFAULT 1,
                        `vibrate_on_decision` INTEGER NOT NULL DEFAULT 1,
                        `sound_on_decision` INTEGER NOT NULL DEFAULT 0,
                        `voice_control_enabled` INTEGER NOT NULL DEFAULT 0,
                        `auto_start_on_car_bt` INTEGER NOT NULL DEFAULT 0,
                        `ai_good_profit_per_km` REAL NOT NULL DEFAULT 0.25,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * Migración v8 → v9: añade la métrica principal de rentabilidad.
         * Las filas existentes pasan a decidir por hora (comportamiento previo).
         * Los defaults de `min_fare`/`min_net_profit` son de entidad (solo afectan
         * a instalaciones nuevas), las filas existentes conservan sus valores.
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE filter_rules ADD COLUMN primary_metric TEXT NOT NULL DEFAULT 'PER_HOUR'")
            }
        }

        /**
         * Migración v9 → v10: enriquece el historial y añade configuración para
         * un modelo económico más realista.
         *
         *  - trip_log: rating visible, tipo de viaje, minutos estimados, resultado
         *    de la acción, resolución real, recomendación IA y coordenadas opcionales.
         *  - vehicle_config: comisión de la plataforma (%) — default 9.
         *  - filter_rules: factor de vuelta vacía (default 0 = desactivado, el
         *    conductor decide) y rechazo por rating desconocido (default off).
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // ── trip_log: datos enriquecidos ──
                db.execSQL("ALTER TABLE trip_log ADD COLUMN has_rating INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE trip_log ADD COLUMN ride_type TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE trip_log ADD COLUMN passenger_trips INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE trip_log ADD COLUMN minutes_estimated INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE trip_log ADD COLUMN action_applied INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE trip_log ADD COLUMN resolution TEXT NOT NULL DEFAULT 'UNKNOWN'")
                db.execSQL("ALTER TABLE trip_log ADD COLUMN ai_recommendation TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE trip_log ADD COLUMN ai_confidence REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE trip_log ADD COLUMN pickup_lat REAL")
                db.execSQL("ALTER TABLE trip_log ADD COLUMN pickup_lng REAL")
                db.execSQL("ALTER TABLE trip_log ADD COLUMN dest_lat REAL")
                db.execSQL("ALTER TABLE trip_log ADD COLUMN dest_lng REAL")

                // ── vehicle_config: comisión de la plataforma ──
                db.execSQL("ALTER TABLE vehicle_config ADD COLUMN platform_commission_percent REAL NOT NULL DEFAULT 9")

                // ── filter_rules: vuelta vacía configurable + rating desconocido ──
                db.execSQL("ALTER TABLE filter_rules ADD COLUMN deadhead_return_factor REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE filter_rules ADD COLUMN reject_on_unknown_rating INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}