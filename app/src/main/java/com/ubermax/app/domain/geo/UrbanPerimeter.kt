package com.ubermax.app.domain.geo

/**
 * Contorno geográfico que define las zonas permitidas de destino.
 *
 * Única implementación: [GeoJsonZones], con los polígonos oficiales del
 * conductor cargados desde `assets/zonas-permitidasv3.geojson`. Ya no existe
 * el círculo de fallback: sin GeoJSON válido el filtro se omite (fail-open).
 *
 * 100% JVM puro (sin Android) para poder testearse con JUnit. Las ofertas cuyo
 * destino geocodificado cae fuera responden [contains] = false y la
 * [com.ubermax.app.domain.rules.RuleEngine] lo traduce a un WARN (rechazo
 * pasivo: solo avisa, nunca pulsa la X).
 */
interface UrbanBoundary {

    /** True si [lat], [lng] caen dentro (o en el borde) de la zona permitida. */
    fun contains(lat: Double, lng: Double): Boolean

    /** Mensaje descriptivo cuando el punto está fuera (jamás se llama dentro). */
    fun outsideReason(lat: Double, lng: Double): String
}

/**
 * Referencias geográficas del núcleo urbano de Ambato (Parque Montalvo).
 * Se conserva como constante de referencia para los tests del perímetro.
 */
object UrbanPerimeter {

    /** Coordenadas del núcleo urbano de Ambato (Parque Montalvo). */
    const val AMBATO_CENTER_LAT = -1.2432
    const val AMBATO_CENTER_LNG = -78.6267
}