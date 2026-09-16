package com.ubermax.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ubermax.app.domain.geometry.PolygonGeometry

/**
 * Zona geográfica de lista negra.
 *
 * v2: Soporta polígonos dibujados por el usuario.
 * El polígono se serializa como JSON "[[lat,lng],[lat,lng],...]".
 * La comparación se hace por:
 * 1. Nombre de la zona contra la dirección de destino (fallback texto)
 * 2. Keywords extraídas automáticamente del polígono (calles/barrios)
 * 3. Point-in-polygon (cuando haya coordenadas de la oferta)
 */
@Entity(tableName = "blacklist_zone")
data class BlacklistZoneEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo(name = "name")
    val name: String,           // Nombre/etiqueta de la zona (ej: "Ficoa", "Huachi")

    @ColumnInfo(name = "polygon_json")
    val polygonJson: String = "[]",  // Vértices del polígono como JSON

    // Campos legacy (círculos) — se mantienen para compatibilidad con zonas antiguas
    @ColumnInfo(name = "latitude")
    val latitude: Double = 0.0,

    @ColumnInfo(name = "longitude")
    val longitude: Double = 0.0,

    @ColumnInfo(name = "radius_meters")
    val radiusMeters: Double = 0.0,

    // Keywords extraídas automáticamente del polígono vía reverse geocoding.
    // Formato JSON: "["Av. Cevallos","Ficoa","Centro"]"
    @ColumnInfo(name = "extracted_keywords_json")
    val extractedKeywordsJson: String = "[]",

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
) {
    /** Vértices del polígono (lat, lng). Para círculos legacy, se convierte dinámicamente a un cuadrado. */
    val polygon: List<Pair<Double, Double>>
        get() {
            val parsed = PolygonGeometry.parsePolygonJson(polygonJson)
            if (parsed.size >= 3) return parsed
            // Fallback: círculo legacy → cuadrado equivalente
            if (radiusMeters > 0.0) {
                return PolygonGeometry.circleToSquarePolygon(latitude, longitude, radiusMeters)
            }
            return emptyList()
        }

    /** Keywords extraídas del polígono. */
    val extractedKeywords: Set<String>
        get() = if (extractedKeywordsJson.isBlank() || extractedKeywordsJson == "[]") {
            emptySet()
        } else {
            try {
                val array = org.json.JSONArray(extractedKeywordsJson)
                val set = mutableSetOf<String>()
                for (i in 0 until array.length()) {
                    set.add(array.getString(i))
                }
                set
            } catch (e: Exception) {
                emptySet()
            }
        }

    /** true si esta zona es un polígono (no un círculo legacy). */
    val isPolygon: Boolean
        get() = polygon.size >= 3

    /** true si esta zona sigue siendo un círculo legacy. */
    val isLegacyCircle: Boolean
        get() = !isPolygon && radiusMeters > 0.0

    /** Área de la zona en m² (útil para mostrar en UI). */
    val areaMeters2: Double
        get() = if (isPolygon) {
            PolygonGeometry.polygonAreaMeters(polygon)
        } else {
            Math.PI * radiusMeters * radiusMeters
        }
}