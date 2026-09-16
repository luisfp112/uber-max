package com.ubermax.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ubermax.app.util.TextNormalizer

/**
 * Entrada de la lista negra para sectores, calles o zonas vetadas.
 *
 * El campo [keyword] almacena el texto real tal y como aparece en Uber Driver
 * (ej: "Av. Ficoa", "Huachi Grande", "Techo Propio"). La coincidencia contra la
 * dirección de destino/recogida se hace con texto NORMALIZADO mediante
 * [TextNormalizer], ignorando mayúsculas, tildes y caracteres especiales.
 */
@Entity(
    tableName = "blacklist_entry",
    indices = [Index(value = ["keyword"])]
)
data class BlacklistEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo(name = "keyword")
    val keyword: String, // Texto real (calle, barrio o sector) tal como aparece en Uber

    @ColumnInfo(name = "type")
    val type: String = "ZONE", // "SECTOR", "STREET", "ZONE"

    @ColumnInfo(name = "reason")
    val reason: String = "" // Razón del bloqueo (opcional, para referencia)
) {
    /** Forma canónica de [keyword]: minúsculas, sin tildes, sin caracteres especiales. */
    val normalizedKeyword: String get() = TextNormalizer.normalize(keyword)
}
