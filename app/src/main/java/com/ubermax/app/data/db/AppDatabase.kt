package com.ubermax.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ubermax.app.data.db.dao.*
import com.ubermax.app.data.db.entity.*

@Database(
    entities = [
        VehicleConfigEntity::class,
        FilterRulesEntity::class,
        AppSettingsEntity::class
    ],
    version = 15,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vehicleConfigDao(): VehicleConfigDao
    abstract fun filterRulesDao(): FilterRulesDao
    abstract fun appSettingsDao(): AppSettingsDao

    companion object {
        /**
         * Migración v5 → v6 (histórica): añade columnas de polígono a blacklist_zone.
         * Esa tabla ya no existe (la feature de mapa se eliminó en v10→v11), por lo
         * que esta migración solo se conserva para dispositivos que pasaron por v6–v10.
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

        /**
         * Migración v10 → v11: refactor de la decisión (v1.2.0).
         *
         *  - filter_rules: filtros de recogida/viaje con unidad configurable
         *    (min/km de la tarjeta), evaluación de zonas distantes (B/C/D), switch
         *    de exclusión nocturna de periferia y factor de agresividad de retorno.
         *    El rating mínimo pasa a 0 (desactivado por defecto).
         *  - app_settings: taps humanizados (retardo + jitter) opt-in, default ON.
         *  - trip_log: datos de la zona clasificada (anillo, nombre, km de retorno
         *    y minutos bloqueados).
         *  - Se elimina la feature de mapa: `blacklist_zone` se descarta (la lista
         *    negra queda solo por palabras en `blacklist_entry`).
         *  - Las columnas legacy de filter_rules (max_pickup_km, max_trip_km,
         *    deadhead_*...) se conservan temporalmente durante las migraciones
         *    intermedias y se eliminan en v14→v15 (Room exige esquema exacto).
         */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // ── filter_rules: filtros unit-aware + zonas distantes ──
                db.execSQL("ALTER TABLE filter_rules ADD COLUMN pickup_measure TEXT NOT NULL DEFAULT 'MIN'")
                db.execSQL("ALTER TABLE filter_rules ADD COLUMN pickup_max REAL NOT NULL DEFAULT 5.0")
                db.execSQL("ALTER TABLE filter_rules ADD COLUMN trip_measure TEXT NOT NULL DEFAULT 'MIN'")
                db.execSQL("ALTER TABLE filter_rules ADD COLUMN trip_max REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE filter_rules ADD COLUMN dest_zone_check INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE filter_rules ADD COLUMN night_block_periphery INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE filter_rules ADD COLUMN deadhead_aggressiveness TEXT NOT NULL DEFAULT 'MODERADO'")
                db.execSQL("UPDATE filter_rules SET min_passenger_rating = 0")

                // ── app_settings: taps humanizados ──
                db.execSQL("ALTER TABLE app_settings ADD COLUMN human_taps_enabled INTEGER NOT NULL DEFAULT 1")

                // ── trip_log: zona clasificada ──
                db.execSQL("ALTER TABLE trip_log ADD COLUMN zone_ring TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE trip_log ADD COLUMN matched_zone TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE trip_log ADD COLUMN return_km REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE trip_log ADD COLUMN blocked_minutes REAL NOT NULL DEFAULT 0")

                // ── Elimina la feature de mapa (lista negra por zonas) ──
                db.execSQL("DROP TABLE IF EXISTS blacklist_zone")
            }
        }

        /**
         * Migración v11 → v12: control del perímetro urbano por geolocalización.
         *
         * Añade a `app_settings` el switch del control geográfico (opt-in), la API
         * key de LocationIQ (nunca commiteada) y la geografía de referencia del
         * perímetro de Ambato (centro + radio en km). Los defaults coinciden con
         * [AppSettingsEntity]: control apagado, perímetro de 8 km centrado en el
         * núcleo urbano. Requiere permiso de red (INTERNET, ya declarado).
         */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN geo_check_enabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN geo_api_key TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN geo_radius_km REAL NOT NULL DEFAULT 8.0")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN geo_center_lat REAL NOT NULL DEFAULT -1.2432")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN geo_center_lng REAL NOT NULL DEFAULT -78.6267")
            }
        }

        /**
         * Migración v12 → v13: anuncio de decisiones en voz (TTS).
         *
         * Añade a `app_settings` el switch de voz "viaje aceptado / viaje
         * rechazado" (default 1 = activado, igual que [AppSettingsEntity]).
         * No requiere permisos adicionales: usa TextToSpeech del sistema.
         */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN voice_announce_enabled INTEGER NOT NULL DEFAULT 1")
            }
        }

        /**
         * Migración v13 → v14: elimina las features descartadas.
         *
         *  - Se descartan las tablas `trip_log` (persistencia/entrenamiento de IA)
         *    y `blacklist_entry` (lista negra por palabras). Los servicios que las
         *    usaban (IA, historial, exportación de dataset) se eliminaron.
         *  - Las columnas obsoletas de `filter_rules`, `vehicle_config` y
         *    `app_settings` (comisión de plataforma, dry-run, economía de zona,
         *    radio de perímetro, IA, ...) se limpian en v14→v15, porque Room
         *    valida el esquema contra las entidades de forma EXACTA y no tolera
         *    tablas con columnas que las entidades ya no declaran.
         */
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS trip_log")
                db.execSQL("DROP TABLE IF EXISTS blacklist_entry")
            }
        }

        /**
         * Migración v14 → v15: reconstruye las tablas para que coincidan EXACTA-
         * MENTE con las entidades.
         *
         * Las tablas antiguas conservan columnas de features eliminadas
         * (platform_commission_percent, primary_metric, max_trip_km,
         * dry_run_enabled, geo_radius_km, ...). Room compara el esquema real de la
         * base contra el de las entidades columna por columna (incluido longitud
         * del mapa) y lanza "Migration didn't properly handle" si sobran columnas.
         *
         * Esta migración reconstruye las 3 tablas (copia solo las columnas aún en
         * uso y descarta el resto) y respeta los valores del conductor.
         */
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // ── vehicle_config: se elimina platform_commission_percent ──
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `vehicle_config_new` (
                        `id` INTEGER NOT NULL,
                        `vehicle_name` TEXT NOT NULL,
                        `consumption_km_per_unit` REAL NOT NULL,
                        `fuel_unit_id` INTEGER NOT NULL,
                        `fuel_price_per_unit` REAL NOT NULL,
                        `maintenance_per_km` REAL NOT NULL,
                        `avg_speed_kmh` REAL NOT NULL,
                        `engine_idle_cost_per_hour` REAL NOT NULL,
                        `manual_cost_per_km` REAL NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `vehicle_config_new` (`id`, `vehicle_name`,
                        `consumption_km_per_unit`, `fuel_unit_id`, `fuel_price_per_unit`,
                        `maintenance_per_km`, `avg_speed_kmh`, `engine_idle_cost_per_hour`,
                        `manual_cost_per_km`)
                    SELECT `id`, `vehicle_name`, `consumption_km_per_unit`, `fuel_unit_id`,
                        `fuel_price_per_unit`, `maintenance_per_km`, `avg_speed_kmh`,
                        `engine_idle_cost_per_hour`, `manual_cost_per_km`
                    FROM `vehicle_config`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `vehicle_config`")
                db.execSQL("ALTER TABLE `vehicle_config_new` RENAME TO `vehicle_config`")

                // ── filter_rules: se eliminan métricas y topes legacy ──
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `filter_rules_new` (
                        `id` INTEGER NOT NULL,
                        `min_fare` REAL NOT NULL,
                        `min_profit_per_km` REAL NOT NULL,
                        `pickup_measure` TEXT NOT NULL,
                        `pickup_max` REAL NOT NULL,
                        `trip_measure` TEXT NOT NULL,
                        `trip_max` REAL NOT NULL,
                        `min_passenger_rating` REAL NOT NULL,
                        `reject_on_unknown_rating` INTEGER NOT NULL,
                        `auto_accept_enabled` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `filter_rules_new` (`id`, `min_fare`, `min_profit_per_km`,
                        `pickup_measure`, `pickup_max`, `trip_measure`, `trip_max`,
                        `min_passenger_rating`, `reject_on_unknown_rating`, `auto_accept_enabled`)
                    SELECT `id`, `min_fare`, `min_profit_per_km`, `pickup_measure`,
                        `pickup_max`, `trip_measure`, `trip_max`, `min_passenger_rating`,
                        `reject_on_unknown_rating`, `auto_accept_enabled`
                    FROM `filter_rules`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `filter_rules`")
                db.execSQL("ALTER TABLE `filter_rules_new` RENAME TO `filter_rules`")

                // ── app_settings: solo quedan voz, taps y control geográfico ──
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `app_settings_new` (
                        `id` INTEGER NOT NULL,
                        `voice_announce_enabled` INTEGER NOT NULL,
                        `human_taps_enabled` INTEGER NOT NULL,
                        `geo_check_enabled` INTEGER NOT NULL,
                        `geo_api_key` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `app_settings_new` (`id`, `voice_announce_enabled`,
                        `human_taps_enabled`, `geo_check_enabled`, `geo_api_key`)
                    SELECT `id`, `voice_announce_enabled`, `human_taps_enabled`,
                        `geo_check_enabled`, `geo_api_key`
                    FROM `app_settings`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `app_settings`")
                db.execSQL("ALTER TABLE `app_settings_new` RENAME TO `app_settings`")
            }
        }
    }
}