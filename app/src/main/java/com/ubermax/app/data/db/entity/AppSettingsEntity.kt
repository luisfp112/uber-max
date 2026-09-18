package com.ubermax.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Ajustes operativos de la app (singleton, id = 1).
 *
 * Se separan de [FilterRulesEntity] (reglas de decisión) y [VehicleConfigEntity]
 * (costos) para no mezclar comportamiento con preferencias de funcionamiento.
 * Todos los flags son opt-in salvo [notifyDecisions] y [vibrateOnDecision].
 */
@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey
    val id: Int = 1,

    /** Modo simulación: evalúa y muestra el HUD, pero NO ejecuta taps en Uber. */
    @ColumnInfo(name = "dry_run_enabled")
    val dryRunEnabled: Boolean = false,

    /** Arranca el monitoreo automáticamente tras reiniciar el dispositivo. */
    @ColumnInfo(name = "auto_start_on_boot")
    val autoStartOnBoot: Boolean = false,

    /** Muestra una notificación (heads-up) por cada decisión. */
    @ColumnInfo(name = "notify_decisions")
    val notifyDecisions: Boolean = true,

    /** Vibra al emitir una decisión. */
    @ColumnInfo(name = "vibrate_on_decision")
    val vibrateOnDecision: Boolean = true,

    /** Reproduce un tono corto al emitir una decisión. */
    @ColumnInfo(name = "sound_on_decision")
    val soundOnDecision: Boolean = false,

    /** Habilita control por voz ("aceptar" / "rechazar") mientras monitorea. */
    @ColumnInfo(name = "voice_control_enabled")
    val voiceControlEnabled: Boolean = false,

    /** Arranca el monitoreo al conectarse un dispositivo Bluetooth de auto. */
    @ColumnInfo(name = "auto_start_on_car_bt")
    val autoStartOnCarBt: Boolean = false,

    /**
     * Referencia de "buena ganancia por km" (USD/km) usada por [com.ubermax.app.domain.ai.SmartAdvisor].
     * Si es 0, la IA usa `FilterRulesEntity.minProfitPerKm` o el default interno.
     */
    @ColumnInfo(name = "ai_good_profit_per_km")
    val aiGoodProfitPerKm: Double = 0.25
)
