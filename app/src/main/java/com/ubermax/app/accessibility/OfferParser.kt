package com.ubermax.app.accessibility

import android.graphics.Rect
import com.ubermax.app.util.Logs
import android.view.accessibility.AccessibilityNodeInfo
import com.ubermax.app.domain.model.OfferData
import com.ubermax.app.util.RegexPatterns

/**
 * OfferParser v4 — Captura precisa basada en la estructura real de la tarjeta de Uber Driver.
 *
 * Soporta los DOS tipos de oferta que muestra Uber Driver:
 *   1. ASIGNADA  → tarjeta con botón "Aceptar" / "Aceptar viaje"
 *   2. ABIERTA    → tarjeta con botón "Viaje disponible" / "Postularse"
 *
 * Estructura de la tarjeta de oferta (confirmada en screenshot):
 *
 *   ┌─────────────────────────────────────────┐
 *   │ ← UberX                              X │
 *   │ $4.68                                   │
 *   │ ✓ Documento de identidad verificado     │
 *   │ ★ 4.73 (42)                             │
 *   │ ● A 6 min (1.8 km)                     │
 *   │   Blvr. del Ejercito Nacional, TERMINAL │
 *   │   DE BUSES DE ORIENTE...                │
 *   │ ● Viaje: 36 min (15.6 km)              │
 *   │   C. L-7, CUIDAD MERLIOT- SANTA TECLA  │
 *   │   - SAN SALVADOR                        │
 *   │   Termina en 00:42                      │  ← countdown (se ignora)
 *   │          [Viaje disponible]             │  ← botón de oferta abierta
 *   │          [Aceptar]                      │  ← botón de oferta asignada
 *   └─────────────────────────────────────────┘
 *
 * Patrones clave:
 * - "A X min (X.X km)" → pickup distance/time
 * - "Viaje: X min (X.X km)" → trip distance/time
 * - Texto DESPUÉS de pickup pattern → pickup address
 * - Texto DESPUÉS de trip pattern → destination address
 * - Temporizadores ("Termina en 00:42") y botones ("Viaje disponible", "Postularse")
 *   se excluyen de las direcciones.
 *
 * El núcleo de extracción [parseFromTextNodes] es 100% JVM puro (sin tipos Android)
 * y se valida con tests unitarios. [collectNodes] es el único puente con el árbol
 * de accesibilidad de Android.
 */
class OfferParser {

    companion object {
        private const val TAG = "OfferParser"

        // Patrón: "A 6 min (1.8 km)" — recogida
        private val PICKUP_PATTERN = Regex(
            """A\s?(\d+)\s?min\s?\((\d+[.,]?\d*)\s?km\)""", RegexOption.IGNORE_CASE
        )

        // Variante en inglés: "6 min (1.8 km) away" — recogida
        private val PICKUP_PATTERN_EN = Regex(
            """(\d+)\s?min\s?\((\d+[.,]?\d*)\s?km\)\s?away""", RegexOption.IGNORE_CASE
        )

        // Patrón: "Viaje: 36 min (15.6 km)" / "Trip: 36 min (15.6 km)" — viaje
        private val TRIP_PATTERN = Regex(
            """(?:Viaje|Trip)\s?:?\s?(\d+)\s?min\s?\((\d+[.,]?\d*)\s?km\)""", RegexOption.IGNORE_CASE
        )

        // Patrón: "★ 4.73 (42)" o "4.73 ★" — rating
        private val RATING_PATTERN = Regex(
            """★?\s?([1-5][.,]\d{1,2})\s?★?\s?(?:\((\d+)\))?"""
        )

        // Temporizador de cuenta regresiva: "Termina en 00:42" o "00:42"
        private val COUNTDOWN_PATTERN = Regex("""\d{1,2}:\d{2}""")

        // Textos que NO son direcciones (botones, estados, temporizadores)
        private val NON_ADDRESS_TEXTS = setOf(
            // Botones de oferta asignada
            "aceptar", "aceptar viaje", "confirmar", "rechazar", "cancelar", "no, gracias",
            // Botones de oferta abierta
            "viaje disponible", "postularse", "postularte", "postularme", "postular",
            "aplicar", "apply", "apuntarse", "trip available", "accept",
            // Miscelanáneos de la tarjeta
            "cerrar", "close", "dismiss", "decline",
            "en camino", "encamino", "asignado", "termina en", "finaliza en", "llega en"
        )
    }

    /** Nodo de texto puro y ligero para el núcleo de parsing (testeable en JVM). */
    data class TextNode(
        val text: String,
        val contentDesc: String
    )

    /** Metadata enriquecida de un nodo de accesibilidad (solo lado Android). */
    data class NodeData(
        val text: String,
        val contentDesc: String,
        val className: String,
        val viewId: String,
        val depth: Int,
        val bounds: Rect,
        val index: Int
    )

    /**
     * Punto de entrada Android: recolecta el árbol y delega en el núcleo puro.
     */
    fun parseOffer(rootNode: AccessibilityNodeInfo?): OfferData? {
        if (rootNode == null) return null

        val nodes = mutableListOf<NodeData>()
        collectNodes(rootNode, nodes, depth = 0)

        if (nodes.isEmpty()) return null

        // ── Log detallado para calibración ──
        Logs.d(TAG, "═══ NODOS CAPTURADOS (${nodes.size}) ═══")
        nodes.forEach { n ->
            val prefix = "  ".repeat(n.depth.coerceAtMost(6))
            val info = buildString {
                append("${prefix}[${n.index}] ")
                if (n.text.isNotEmpty()) append("T=\"${n.text}\" ")
                if (n.contentDesc.isNotEmpty()) append("D=\"${n.contentDesc}\" ")
                if (n.viewId.isNotEmpty()) append("ID=${n.viewId} ")
                append("(${n.className.substringAfterLast('.')})")
                append(" [${n.bounds.left},${n.bounds.top}-${n.bounds.right},${n.bounds.bottom}]")
            }
            Logs.d(TAG, info)
        }

        return parseFromTextNodes(nodes.map { TextNode(it.text, it.contentDesc) })
    }

    /**
     * NÚCLEO PURO — analiza una oferta a partir de los textos visibles de la tarjeta.
     *
     * Acepta tanto ofertas ASIGNADAS ("Aceptar") como ABIERTAS ("Viaje disponible" /
     * "Postularse"); la etiqueta del botón nunca afecta a la extracción porque se filtra.
     *
     * @param nodes textos y contentDescriptions de los nodos, en orden DFS.
     * @return OfferData con los campos extraídos, o null si no es una oferta válida.
     */
    fun parseFromTextNodes(nodes: List<TextNode>): OfferData? {
        if (nodes.isEmpty()) return null

        // ── Extraer tarifa ──
        val fareNode = nodes.firstOrNull { n ->
            val combined = "${n.text} ${n.contentDesc}"
            RegexPatterns.FARE_PATTERN.containsMatchIn(combined)
        }
        if (fareNode == null) {
            Logs.d(TAG, "❌ No se encontró tarifa — no es una oferta")
            return null
        }
        val fareText = "${fareNode.text} ${fareNode.contentDesc}"
        val fareMatch = RegexPatterns.FARE_PATTERN.find(fareText)!!
        val rawFare = RegexPatterns.parseFare(fareMatch.groupValues[1])
        if (rawFare <= 0) return null

        // ── Extraer tipo de viaje (UberX, Comfort, etc.) ──
        val rideType = extractRideType(nodes)

        // ── Extraer pickup: "A X min (X.X km)" ──
        var pickupKm = 0.0
        var pickupMinutes = 0
        var pickupNodeIndex = -1

        for ((index, n) in nodes.withIndex()) {
            val combined = "${n.text} ${n.contentDesc}"
            val match = PICKUP_PATTERN.find(combined) ?: PICKUP_PATTERN_EN.find(combined)
            if (match != null) {
                pickupMinutes = match.groupValues[1].toIntOrNull() ?: 0
                pickupKm = RegexPatterns.parseDistance(match.groupValues[2])
                pickupNodeIndex = index
                Logs.d(TAG, "📍 Pickup: ${pickupMinutes}min, ${pickupKm}km (nodo $index)")
                break
            }
        }

        // ── Extraer trip: "Viaje: X min (X.X km)" ──
        var tripKm = 0.0
        var tripMinutes = 0
        var tripNodeIndex = -1

        for ((index, n) in nodes.withIndex()) {
            val combined = "${n.text} ${n.contentDesc}"
            val match = TRIP_PATTERN.find(combined)
            if (match != null) {
                tripMinutes = match.groupValues[1].toIntOrNull() ?: 0
                tripKm = RegexPatterns.parseDistance(match.groupValues[2])
                tripNodeIndex = index
                Logs.d(TAG, "🚗 Trip: ${tripMinutes}min, ${tripKm}km (nodo $index)")
                break
            }
        }

        // ── Fallback: si no se encontraron patrones específicos, usar regex genérico ──
        if (pickupNodeIndex == -1 && tripNodeIndex == -1) {
            val distances = extractDistancesFallback(nodes)
            val times = extractTimesFallback(nodes)
            when {
                distances.size >= 2 -> {
                    pickupKm = distances[0]
                    tripKm = distances[1]
                }
                distances.size == 1 -> {
                    tripKm = distances[0]
                }
                else -> {
                    Logs.d(TAG, "❌ Sin distancias — no es una oferta válida")
                    return null
                }
            }
            if (times.size >= 2) {
                pickupMinutes = times[0]
                tripMinutes = times[1]
            } else if (times.size == 1) {
                tripMinutes = times[0]
            }
        }

        val estimatedMinutes = if (pickupMinutes + tripMinutes > 0) {
            pickupMinutes + tripMinutes
        } else {
            ((pickupKm + tripKm) / 22.0 * 60.0).toInt()
        }

        // ── Extraer rating ──
        val (passengerRating, passengerTrips) = extractRating(nodes)

        // ── Extraer direcciones ──
        val pickupAddress = extractAddressAfterNode(nodes, pickupNodeIndex, tripNodeIndex)
        val destination = extractAddressAfterNode(nodes, tripNodeIndex, nodes.size)

        Logs.i(TAG, "═══ OFERTA PARSEADA ═══")
        Logs.i(TAG, "  💰 Tarifa: \$$rawFare | Tipo: $rideType")
        Logs.i(TAG, "  📍 Pickup: ${pickupKm}km, ${pickupMinutes}min → $pickupAddress")
        Logs.i(TAG, "  🚗 Viaje: ${tripKm}km, ${tripMinutes}min → $destination")
        Logs.i(TAG, "  ⭐ Rating: $passengerRating ($passengerTrips viajes)")

        return OfferData(
            rawFare = rawFare,
            pickupKm = pickupKm,
            tripKm = tripKm,
            pickupMinutes = pickupMinutes,
            tripMinutes = tripMinutes,
            estimatedMinutes = estimatedMinutes,
            passengerRating = passengerRating,
            passengerTrips = passengerTrips,
            rideType = rideType,
            destination = destination,
            pickupAddress = pickupAddress,
            rawTexts = nodes.map { it.text }.filter { it.isNotEmpty() }
        )
    }

    /**
     * Extrae el tipo de viaje (UberX, Comfort, etc.)
     */
    private fun extractRideType(nodes: List<TextNode>): String {
        val rideTypes = listOf(
            "UberX", "Uber X", "Comfort", "UberXL", "Uber XL",
            "Black", "UberBlack", "Green", "Planet", "Flash"
        )
        for (n in nodes) {
            val text = n.text.trim()
            for (type in rideTypes) {
                if (text.contains(type, ignoreCase = true)) {
                    return type
                }
            }
        }
        return ""
    }

    /**
     * Extrae rating del pasajero.
     * Busca: "★ 4.73 (42)" o "4.73 ★" o similar.
     * Retorna (rating, tripCount).
     */
    private fun extractRating(nodes: List<TextNode>): Pair<Double, Int> {
        // Buscar nodos con ★
        for (n in nodes) {
            val combined = "${n.text} ${n.contentDesc}"
            if (!combined.contains("★") && !combined.contains("⭐")) continue

            val match = RATING_PATTERN.find(combined)
            if (match != null) {
                val rating = RegexPatterns.parseRating(match.groupValues[1])
                val trips = match.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
                if (rating in 1.0..5.0) {
                    return rating to trips
                }
            }
        }

        // Fallback: buscar contentDescription con "calificación" o "rating"
        for (n in nodes) {
            val desc = n.contentDesc.lowercase()
            if (desc.contains("calificación") || desc.contains("rating") ||
                desc.contains("estrellas") || desc.contains("stars")
            ) {
                val ratingRegex = Regex("""([1-5][.,]\d{1,2})""")
                val match = ratingRegex.find("${n.text} ${n.contentDesc}")
                if (match != null) {
                    return RegexPatterns.parseRating(match.groupValues[1]) to 0
                }
            }
        }

        // Fallback: buscar número aislado 4.00-5.00
        for (n in nodes) {
            val text = n.text.trim()
            if (text.isEmpty()) continue
            val ratingRegex = Regex("""([4-5][.,]\d{2})""")
            val match = ratingRegex.find(text) ?: continue
            val value = RegexPatterns.parseRating(match.groupValues[1])
            val hasUnits = text.contains("km", true) || text.contains("min", true) ||
                text.contains("$") || text.contains("COP", true)
            if (!hasUnits && value in 4.0..5.0) {
                return value to 0
            }
        }

        return 5.0 to 0 // Default
    }

    /**
     * Extrae la dirección que aparece DESPUÉS del nodo con índice [afterIndex]
     * y ANTES del nodo con índice [beforeIndex].
     *
     * Basado en la estructura real: la dirección es el texto que aparece
     * entre el patrón de distancia/tiempo y el siguiente patrón o botón.
     * Excluye botones de acción ("Viaje disponible", "Aceptar", "Postularse"),
     * temporizadores de cuenta regresiva ("Termina en 00:42") y textos vacíos.
     */
    private fun extractAddressAfterNode(
        nodes: List<TextNode>,
        afterIndex: Int,
        beforeIndex: Int
    ): String {
        if (afterIndex < 0) return ""

        val addressParts = mutableListOf<String>()

        nodes.forEachIndexed { index, n ->
            // Solo nodos que están DESPUÉS del índice dado
            if (index <= afterIndex) return@forEachIndexed
            // Y ANTES del siguiente delimitador
            if (index >= beforeIndex) return@forEachIndexed

            val text = n.text.trim()
            if (text.isEmpty()) return@forEachIndexed
            if (text.length < 3) return@forEachIndexed

            // Filtrar textos que NO son direcciones
            val lower = text.lowercase()
            if (NON_ADDRESS_TEXTS.any { lower == it }) return@forEachIndexed
            if (NON_ADDRESS_TEXTS.any { lower.startsWith("$it ") || lower.endsWith(" $it") || lower.contains(" $it ") }) return@forEachIndexed
            if (RegexPatterns.FARE_PATTERN.containsMatchIn(text)) return@forEachIndexed
            if (PICKUP_PATTERN.containsMatchIn(text)) return@forEachIndexed
            if (PICKUP_PATTERN_EN.containsMatchIn(text)) return@forEachIndexed
            if (TRIP_PATTERN.containsMatchIn(text)) return@forEachIndexed
            if (text.matches(Regex("""[\d.,\$\s★]+"""))) return@forEachIndexed

            // Filtrar temporizadores de cuenta regresiva: "Termina en 00:42", "00:42"
            if (COUNTDOWN_PATTERN.containsMatchIn(text)) return@forEachIndexed

            // ¿Parece una dirección?
            val hasLetters = text.any { it.isLetter() }
            val isLongEnough = text.length >= 5

            if (hasLetters && isLongEnough) {
                addressParts.add(text)
            }
        }

        return addressParts.joinToString(", ").take(200) // Limitar longitud
    }

    // ── Fallback extractors (cuando no se encuentran patrones "A X min" / "Viaje: X min") ──

    private fun extractDistancesFallback(nodes: List<TextNode>): List<Double> {
        return nodes.flatMap { n ->
            val combined = "${n.text} ${n.contentDesc}"
            RegexPatterns.DISTANCE_KM_PATTERN.findAll(combined).map {
                RegexPatterns.parseDistance(it.groupValues[1])
            }.toList()
        }
    }

    private fun extractTimesFallback(nodes: List<TextNode>): List<Int> {
        return nodes.flatMap { n ->
            val combined = "${n.text} ${n.contentDesc}"
            RegexPatterns.TIME_MINUTES_PATTERN.findAll(combined).map {
                it.groupValues[1].toIntOrNull() ?: 0
            }.toList()
        }
    }

    /**
     * Recorre el árbol DFS y recolecta metadata de cada nodo (solo lado Android).
     */
    private fun collectNodes(node: AccessibilityNodeInfo, list: MutableList<NodeData>, depth: Int) {
        val text = node.text?.toString()?.trim() ?: ""
        val desc = node.contentDescription?.toString()?.trim() ?: ""
        val className = node.className?.toString() ?: ""
        val viewId = node.viewIdResourceName ?: ""
        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        if (text.isNotEmpty() || desc.isNotEmpty()) {
            list.add(NodeData(
                text = text,
                contentDesc = desc,
                className = className,
                viewId = viewId,
                depth = depth,
                bounds = bounds,
                index = list.size
            ))
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                collectNodes(child, list, depth + 1)
                child.recycle()
            }
        }
    }
}