package com.ubermax.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Ajustes operativos de la app (singleton, id = 1).
 *
 * Se separan de [FilterRulesEntity] (reglas de decisión) y [VehicleConfigEntity]
 * (costos) para no mezclar comportamiento con preferencias de funcionamiento.
 * Todos los flags son opt-in.
 *
 * Se eliminaron: modo simulación, auto-arranque, notificaciones/vibración/
 * sonido de decisión, control por voz, radio/centro del perímetro circular y
 * el toggle de IA. El anuncio por voz (TTS) y los taps humanizados se mantienen.
 */
@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey
    val id: Int = 1,

    /** Anuncia en voz ("viaje aceptado / viaje rechazado") las decisiones. */
    @ColumnInfo(name = "voice_announce_enabled")
    val voiceAnnounceEnabled: Boolean = true,

    /**
     * Taps "humanizados": retardo gaussiano + jitter antes de pulsar sobre la
     * UI de Uber (evita patrones de autómatas).
     */
    @ColumnInfo(name = "human_taps_enabled")
    val humanTapsEnabled: Boolean = true,

    /**
     * Control geográfico de destino (LocationIQ Forward Geocoding). Cuando está
     * activo y hay [geoApiKey], la app geolocaliza el destino de cada oferta y
     * verifica que caiga dentro de los polígonos de
     * `assets/zonas-permitidasv3.geojson` (WARN, nunca rechazo automático).
     * Fail-open: sin key, sin red o sin resultado, el filtro se omite.
     */
    @ColumnInfo(name = "geo_check_enabled")
    val geoCheckEnabled: Boolean = false,

    /**
     * Token de la API de LocationIQ. Se introduce manualmente en Ajustes; nunca
     * se commitea al repositorio (miembro del conductor, libre por defecto).
     */
    @ColumnInfo(name = "geo_api_key")
    val geoApiKey: String = ""
)