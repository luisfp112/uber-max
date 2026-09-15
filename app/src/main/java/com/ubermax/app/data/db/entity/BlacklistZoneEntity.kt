package com.ubermax.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Zona geográfica de lista negra.
 * Representa un círculo en el mapa donde el conductor NO quiere ir.
 * La comparación se hace por nombre de la zona contra la dirección de destino.
 */
@Entity(tableName = "blacklist_zone")
data class BlacklistZoneEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo(name = "name")
    val name: String,           // Nombre/etiqueta de la zona (ej: "Ficoa", "Huachi")

    @ColumnInfo(name = "latitude")
    val latitude: Double,       // Centro del círculo (latitud)

    @ColumnInfo(name = "longitude")
    val longitude: Double,      // Centro del círculo (longitud)

    @ColumnInfo(name = "radius_meters")
    val radiusMeters: Double = 500.0 // Radio en metros
)
