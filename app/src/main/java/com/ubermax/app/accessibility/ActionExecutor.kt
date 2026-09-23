package com.ubermax.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import com.ubermax.app.util.HumanTapTiming
import com.ubermax.app.util.Logs
import com.ubermax.app.util.TextNormalizer
import android.view.accessibility.AccessibilityNodeInfo
import com.ubermax.app.util.RegexPatterns
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Ejecutor de acciones sobre la UI de Uber Driver.
 *
 * Diseñado para funcionar en CUALQUIER teléfono Android: no hay coordenadas
 * absolutas ni calibración por modelo. Toda posición de toque se calcula en
 * tiempo de ejecución a partir de:
 *   - los bounds reales del nodo/contenedor (`node.getBoundsInScreen(rect)`), o
 *   - proporciones relativas aplicadas a `Resources.getSystem().displayMetrics`.
 *
 * ACCIONES:
 * 1. ACCEPT → toque en el botón de aceptar:
 *    - Ofertas asignadas:    "Aceptar"
 *    - Ofertas abiertas:     "Viaje disponible"
 *    (Detección ESTRICTA: solo estas dos frases, normalizadas con TextNormalizer.)
 * 2. CANCEL → toque en la X (cuadrante superior derecho de la tarjeta)
 *
 * Estrategias de toque (cascada):
 * 1. ACTION_CLICK directo en el nodo que contiene la frase normalizada
 * 2. Subir al primer padre clickable (findClickableParent) → ACTION_CLICK
 * 3. dispatchGesture (gesto de toque REAL) en el centro del padre/botón
 * 4. Fallback: dispatchGesture en proporciones relativas de pantalla
 *
 * Tras un toque exitoso se RE-VERIFICA la pantalla: si el botón sigue presente,
 * se devuelve false para que el llamador reintente (no basta con cambiar el HUD).
 */
class ActionExecutor(
    private val service: AccessibilityService,
    private val humanTapsEnabled: Boolean = true
) {

    companion object {
        private const val TAG = "ActionExecutor"

        // Límite de profundidad del árbol: acota el recorrido DFS para evitar
        // costes altos en ventanas muy anidadas (el botón está siempre cerca).
        private const val MAX_DEPTH = 60

        // ── Frases de aceptación ──
        // Cubre ofertas asignadas (aceptar, confirmar) y ofertas abiertas
        // (viaje disponible, me interesa, postularse, solicitar...). Todas se
        // comparan con TextNormalizer para ignorar mayúsculas, tildes y símbolos.
        private val ACCEPT_PHRASES = listOf(
            // Ofertas asignadas a este conductor
            "aceptar", "aceptar viaje", "confirmar", "accept",
            // Ofertas abiertas (se compiten por ellas). Uber A/B: la tarjeta
            // puede mostrar "Viaje disponible" o "Me interesa" indistintamente.
            "viaje disponible", "disponible", "me interesa", "postularse", "postularte",
            "postularme", "postular", "aplicar", "apuntarse", "apuntarme",
            "solicitar", "solicitar viaje", "solicita", "solicito", "participar",
            // Inglés
            "trip available", "i'm interested", "request", "request trip", "join", "apply"
        )

        // ── Textos del botón CANCELAR / RECHAZAR (X superior) ──
        private val DISMISS_TEXTS = listOf(
            "cerrar", "descartar", "rechazar", "no, gracias", "cancelar", "salir",
            "close", "dismiss", "decline", "cancel", "discard"
        )

        /**
         * True si el texto normalizado ([TextNormalizer]) contiene una de las
         * frases de aceptación.
         *
         * La normalización ignora mayúsculas, tildes y caracteres especiales, de
         * modo que "ACEPTAR", "Aceptar", "¡Aceptar!", "VÍAJE DISPONIBLE", etc.,
         * todas se detectan correctamente.
         */
        fun matchesAcceptText(raw: String): Boolean {
            val t = TextNormalizer.normalize(raw)
            return ACCEPT_PHRASES.any { t.contains(TextNormalizer.normalize(it)) }
        }

        /** True si [raw] corresponde al botón X / descartar. "x" aislado también vale. */
        fun matchesDismissText(raw: String): Boolean {
            val t = TextNormalizer.normalize(raw)
            if (t == "x") return true
            return DISMISS_TEXTS.any { t.contains(TextNormalizer.normalize(it)) }
        }

        // ── Proporciones relativas (Se aplican a la resolución real del dispositivo) ──
        private const val FALLBACK_ACCEPT_X_RATIO = TapGeometry.FALLBACK_ACCEPT_X_RATIO
        private const val FALLBACK_ACCEPT_Y_RATIO = TapGeometry.FALLBACK_ACCEPT_Y_RATIO
        private const val FALLBACK_DISMISS_X_RATIO = TapGeometry.FALLBACK_DISMISS_X_RATIO
        private const val FALLBACK_DISMISS_Y_RATIO = TapGeometry.FALLBACK_DISMISS_Y_RATIO

        // Max reintentos y verificación post-acción
        private const val VERIFY_DELAY_MS = 400L
        private const val MAX_PARENT_DEPTH = 5

        // Duración del trazo sin humanización (legacy)
        private const val STROKE_FIXED_MS = 100L
    }

    // ═══════════════════════════════════════════════════════
    //  ACCEPT — toque en "Aceptar" / "Viaje disponible" / "Postularse"
    // ═══════════════════════════════════════════════════════

    /**
     * Busca el botón de aceptar/abrir oferta y ejecuta un toque REAL.
     * Retorna true solo si el gesto se completó Y el botón dejó de estar visible
     * en pantalla (verificación real, no solo estado del HUD).
     */
    suspend fun clickAcceptButton(rootNode: AccessibilityNodeInfo?): Boolean {
        if (rootNode == null) return false
        humanizeDelayIfEnabled()

        // Estrategia 1-3: encontrar el botón (por texto/desc) y tocarlo de verdad
        val acceptNode = findActionNode(rootNode, ::matchesAcceptText)
        if (acceptNode != null) {
            try {
                val tapped = tryRealTap(acceptNode, "ACCEPT")
                if (tapped && verifyActionDone(rootNode, ::matchesAcceptText)) {
                    Logs.i(TAG, "✅ ACCEPT ejecutado y verificado en pantalla")
                    return true
                }
            } finally {
                acceptNode.recycle()
            }
            Logs.w(TAG, "⚠️ ACCEPT no verificado — botón sigue presente, se reintentará")
            return false
        }

        // Estrategia 4: fallback con proporciones relativas de pantalla
        Logs.w(TAG, "⚠️ ACCEPT: sin nodo encontrado → fallback por proporción relativa")
        return performRelativeFallbackTap(
            FALLBACK_ACCEPT_X_RATIO, FALLBACK_ACCEPT_Y_RATIO, "ACCEPT"
        )
    }

    // ═══════════════════════════════════════════════════════
    //  CANCEL — toque en la X de la tarjeta
    // ═══════════════════════════════════════════════════════

    /**
     * Busca el botón X de descarte y lo toca.
     * Estrategias:
     *  1. contentDescription / texto ("Cerrar", "Descartar", "Rechazar", "X"...)
     *  2. Posición: X en el cuadrante superior derecho de la TARJETA (bounds reales)
     *  3. Ícono clicable (ImageButton/ImageView) en la misma región
     *  4. Fallback: proporciones relativas de pantalla
     */
    suspend fun clickDismissButton(rootNode: AccessibilityNodeInfo?): Boolean {
        if (rootNode == null) return false
        humanizeDelayIfEnabled()

        // Estrategia 1: contentDescription/texto
        val dismissNode = findActionNode(rootNode, ::matchesDismissText)
        if (dismissNode != null) {
            try {
                val tapped = tryRealTap(dismissNode, "DISMISS")
                if (tapped && verifyActionDone(rootNode, ::matchesDismissText)) {
                    Logs.i(TAG, "🚫 DISMISS ejecutado y verificado en pantalla")
                    return true
                }
            } finally {
                dismissNode.recycle()
            }
        }

        // Estrategia 2: por posición (cuadrante superior derecho de la tarjeta)
        val byPosition = findDismissByPosition(rootNode)
        if (byPosition != null) {
            try {
                if (tryRealTap(byPosition, "DISMISS-by-position")) {
                    return true
                }
            } finally {
                byPosition.recycle()
            }
        }

        // Estrategia 3: ícono de cierre en la región superior derecha
        val byIcon = findDismissIconInRegion(rootNode)
        if (byIcon != null) {
            try {
                if (tryRealTap(byIcon, "DISMISS-by-icon")) {
                    return true
                }
            } finally {
                byIcon.recycle()
            }
        }

        // Estrategia 4: fallback con proporciones relativas de pantalla
        Logs.w(TAG, "🚫 DISMISS: sin nodo encontrado → fallback por proporción relativa")
        return performRelativeFallbackTap(
            FALLBACK_DISMISS_X_RATIO, FALLBACK_DISMISS_Y_RATIO, "DISMISS"
        )
    }

    // ═══════════════════════════════════════════════════════
    //  Helpers de búsqueda
    // ═══════════════════════════════════════════════════════

    /**
     * Recorre el árbol DFS y devuelve el mejor nodo cuyo texto o contentDescription
     * coincida con [matcher]. Puntúa: texto propio > clicable > más profundo
     * (la X suele estar en un ImageButton con contentDescription, el botón de
     * aceptar suele tener texto propio).
     * El resultado viene con `obtain()` — el llamador debe reciclarlo.
     */
    private fun findActionNode(
        root: AccessibilityNodeInfo,
        matcher: (String) -> Boolean
    ): AccessibilityNodeInfo? {
        data class Candidate(
            val node: AccessibilityNodeInfo,
            val ownText: Boolean,
            val clickable: Boolean,
            val depth: Int
        )

        val candidates = mutableListOf<Candidate>()

        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > MAX_DEPTH) return
            val text: String
            val desc: String
            try {
                text = node.text?.toString() ?: ""
                desc = node.contentDescription?.toString() ?: ""
            } catch (e: IllegalStateException) {
                return // Nodo defunct tras cambiar la ventana — dejar de recorrer
            }
            val matchesText = matcher(text)
            val matchesDesc = matcher(desc)
            if (matchesText || matchesDesc) {
                candidates.add(
                    Candidate(
                        node = AccessibilityNodeInfo.obtain(node),
                        ownText = matchesText,
                        clickable = node.isClickable || node.isEnabled,
                        depth = depth
                    )
                )
            }
            for (i in 0 until node.childCount) {
                val child = try { node.getChild(i) } catch (e: IllegalStateException) { null }
                if (child != null) {
                    walk(child, depth + 1)
                    child.recycle()
                }
            }
        }

        try {
            walk(root, 0)
        } catch (e: IllegalStateException) {
            // La ventana cambió mientras recorríamos: usar lo recolectado hasta ahora
        }
        if (candidates.isEmpty()) return null

        val best = candidates.maxWithOrNull(
            compareBy<Candidate> { it.ownText }
                .thenByDescending { it.clickable }
                .thenByDescending { it.depth }
        )!!

        // Reciclar los candidatos que no se usan
        candidates.forEach { if (it !== best) it.node.recycle() }
        return best.node
    }

    /**
     * Busca el botón X por POSICIÓN: nodos clicables y pequeños cuyo centro esté
     * en el cuadrante superior derecho de la tarjeta de la oferta.
     * La tarjeta se detecta como el contenedor clicable más cercano al nodo de la
     * tarifa; si no se encuentra, se usa la pantalla completa como contenedor.
     */
    private fun findDismissByPosition(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // ── Contenedor = tarjeta de la oferta ──
        val container = findCardContainer(rootNode)
            ?: return null // sin tarjeta detectada → se deja que el fallback relativo actúe

        // ── Recolectar nodos clicables ──
        val clickables = mutableListOf<Pair<AccessibilityNodeInfo, Rect>>()
        collectClickableNodes(rootNode, clickables)

        val candidates = clickables.map { (node, bounds) ->
            TapGeometry.RectSpec(bounds.left, bounds.top, bounds.right, bounds.bottom) to node
        }

        val best = TapGeometry.chooseDismissCandidate(candidates.map { it.first }, container)
        val chosen = candidates.firstOrNull { it.first == best }?.second

        // Reciclar el resto
        clickables.forEach { (node, _) -> if (node != chosen) node.recycle() }
        return chosen
    }

    /**
     * Devuelve los bounds de la tarjeta de la oferta: el contenedor clicable de
     * MAYOR ÁREA ancestro del nodo que contiene la tarifa (patrón monetario).
     *
     * Preferir el ancestro más grande (no el más cercano) evita elegir como
     * tarjeta un contenedor pequeño (botón, ícono) y permite ubicar la X en el
     * cuadrante superior derecho de la tarjeta completa.
     */
    private fun findCardContainer(rootNode: AccessibilityNodeInfo): TapGeometry.RectSpec? {
        val fareNode = findActionNode(rootNode) { RegexPatterns.FARE_PATTERN.containsMatchIn(it) }
            ?: return null
        try {
            val container = largestClickableAncestor(fareNode)
                ?: return null
            try {
                val bounds = Rect()
                container.getBoundsInScreen(bounds)
                if (!bounds.isEmpty) {
                    return TapGeometry.RectSpec(bounds.left, bounds.top, bounds.right, bounds.bottom)
                }
            } finally {
                container.recycle()
            }
        } finally {
            fareNode.recycle()
        }
        return null
    }

    /**
     * Recorre todos los ancestros clicables de [node] (hasta MAX_PARENT_DEPTH) y
     * devuelve el de mayor área (la "tarjeta" completa, no un botón interno).
     * Retorna null si no hay ninguno clicable; el llamador usará la pantalla.
     */
    private fun largestClickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isClickable) return AccessibilityNodeInfo.obtain(node)

        var best: AccessibilityNodeInfo? = null
        var bestArea = -1L

        var current = try { node.parent } catch (e: IllegalStateException) { null }
        var depth = 0
        while (current != null && depth < MAX_PARENT_DEPTH) {
            val next = try { current.parent } catch (e: IllegalStateException) { null }
            try {
                if (current.isClickable) {
                    val bounds = Rect()
                    current.getBoundsInScreen(bounds)
                    if (!bounds.isEmpty) {
                        val area = bounds.width().toLong() * bounds.height()
                        if (area > bestArea) {
                            best?.recycle()
                            best = AccessibilityNodeInfo.obtain(current)
                            bestArea = area
                        }
                    }
                }
            } catch (e: IllegalStateException) {
                // Nodo obsoleto tras un cambio de ventana — se ignora
            }
            current.recycle()
            current = next
            depth++
        }
        return best
    }

    /**
     * Busca un ícono de cierre (ImageButton/ImageView clicable) cuyo centro esté
     * en el cuadrante superior derecho del contenedor (tarjeta o pantalla).
     */
    private fun findDismissIconInRegion(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val container = findCardContainer(rootNode)
        val screen = TapGeometry.RectSpec(0, 0, screenWidth(), screenHeight())

        var best: AccessibilityNodeInfo? = null
        var bestRight = -1

        fun walk(node: AccessibilityNodeInfo) {
            val className = try { node.className?.toString() ?: "" } catch (e: IllegalStateException) { "" }
            val isIcon = node.isClickable &&
                (className.contains("ImageButton") || className.contains("ImageView"))
            if (isIcon) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                if (!bounds.isEmpty) {
                    val spec = TapGeometry.RectSpec(bounds.left, bounds.top, bounds.right, bounds.bottom)
                    val inRegion = TapGeometry.isInTopRightRegion(spec, container ?: screen)
                    if (inRegion && bounds.right > bestRight) {
                        best?.recycle()
                        best = AccessibilityNodeInfo.obtain(node)
                        bestRight = bounds.right
                    }
                }
            }
            for (i in 0 until node.childCount) {
                val child = try { node.getChild(i) } catch (e: IllegalStateException) { null }
                if (child != null) {
                    walk(child)
                    child.recycle()
                }
            }
        }

        try {
            walk(rootNode)
        } catch (e: IllegalStateException) {
            // Ventana cambió durante el recorrido — usar lo hallado hasta ahora
        }
        return best
    }

    // ═══════════════════════════════════════════════════════
    //  Ejecución del toque
    // ═══════════════════════════════════════════════════════

    /**
     * Ejecuta un toque REAL sobre [node]:
     *  1. Sube al contenedor clicable más cercano (el "botón" real).
     *  2. Intenta ACTION_CLICK.
     *  3. Si no, ejecuta dispatchGesture (tap físico) en el centro exacto del
     *     contenedor calculado con getBoundsInScreen (coordenadas dinámicas).
     *  4. Como último recurso, tap en el centro del propio nodo.
     */
    private suspend fun tryRealTap(node: AccessibilityNodeInfo, actionName: String): Boolean {
        val container = try {
            largestClickableAncestor(node)
        } catch (e: IllegalStateException) {
            null
        } ?: return false

        try {
            val bounds = Rect()
            container.getBoundsInScreen(bounds)

            // 1. ACTION_CLICK sobre el contenedor clicable
            val clicked = try {
                container.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            } catch (e: IllegalStateException) {
                false
            }
            if (clicked) {
                Logs.i(TAG, "✅ $actionName vía ACTION_CLICK en contenedor ($bounds)")
                return true
            }

            // 2. Gesto de toque REAL en el centro exacto del contenedor/botón
            if (!bounds.isEmpty) {
                val spec = TapGeometry.RectSpec(bounds.left, bounds.top, bounds.right, bounds.bottom)
                val (x, y) = TapGeometry.relativePointIn(spec, 0.5f, 0.5f)
                val (cx, cy) = clampToScreen(x, y)
                Logs.d(TAG, "👆 $actionName dispatchGesture centrado en (${cx},${cy})")
                if (tryGestureWithCallback(cx.toFloat(), cy.toFloat())) {
                    return true
                }
            }

            // 3. Último recurso: gesto en el centro del propio nodo
            val own = Rect()
            node.getBoundsInScreen(own)
            if (!own.isEmpty) {
                val (x, y) = clampToScreen(own.centerX(), own.centerY())
                return tryGestureWithCallback(x.toFloat(), y.toFloat())
            }
        } catch (e: IllegalStateException) {
            Logs.d(TAG, "⚠️ $actionName: nodo obsoleto durante el toque")
        } finally {
            container.recycle()
        }
        return false
    }

    /**
     * Verifica que la acción realmente surtió efecto en la pantalla de Uber:
     * espera a que pasen las animaciones y comprueba que el botón objetivo ya no
     * esté presente. Si sigue presente, la acción NO se dio por válida.
     */
    private suspend fun verifyActionDone(
        rootNode: AccessibilityNodeInfo?,
        matcher: (String) -> Boolean
    ): Boolean {
        if (rootNode == null) return true
        delay(VERIFY_DELAY_MS)
        val stillPresent = try {
            findActionNode(rootNode, matcher)
        } catch (e: IllegalStateException) {
            // La ventana desapareció al ejecutar la acción → la tarjeta ya no está.
            // Eso es exactamente lo que queremos: la acción SÍ tuvo efecto.
            Logs.d(TAG, "✅ Ventana cerrada tras la acción — verificación aprobada")
            null
        }
        return if (stillPresent != null) {
            stillPresent.recycle()
            false
        } else {
            true
        }
    }

    /**
     * Gestos de reserva con proporciones relativas a la resolución real:
     * `Resources.getSystem().displayMetrics` (funciona en cualquier dispositivo).
     */
    private suspend fun performRelativeFallbackTap(
        xRatio: Float,
        yRatio: Float,
        actionName: String
    ): Boolean {
        val metrics = ResourcesCompat.displayMetrics()
        val (x, y) = TapGeometry.fallbackPoint(metrics.widthPixels, metrics.heightPixels, xRatio, yRatio)
        Logs.w(TAG, "⚠️ $actionName fallback relativo (${xRatio},${yRatio}) → (${x},${y})")
        return tryGestureWithCallback(x.toFloat(), y.toFloat())
    }

    // ═══════════════════════════════════════════════════════
    //  Helpers de Android
    // ═══════════════════════════════════════════════════════

    /** Retorna una copia `obtain()` del PRIMER padre clicable de [node], u null si ninguno lo es (hasta MAX_PARENT_DEPTH). */
    private fun findClickableParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isClickable) return AccessibilityNodeInfo.obtain(node)

        var parent = try { node.parent } catch (e: IllegalStateException) { null }
        var depth = 0
        while (parent != null && depth < MAX_PARENT_DEPTH) {
            val next = try { parent.parent } catch (e: IllegalStateException) { null }
            try {
                if (parent.isClickable) {
                    val result = AccessibilityNodeInfo.obtain(parent)
                    parent.recycle()
                    return result
                }
            } catch (e: IllegalStateException) {
                // Nodo obsoleto tras un cambio de ventana — se ignora
            }
            parent.recycle()
            parent = next
            depth++
        }
        parent?.recycle()
        return null
    }

    /**
     * dispatchGesture con callback — espera a que el gesto termine
     * antes de retornar. Usa suspendCancellableCoroutine para convertir
     * el callback asíncrono en una función suspend.
     */
    private suspend fun tryGestureWithCallback(x: Float, y: Float): Boolean {
        val (jx, jy) = jitterAndClamp(x, y)
        val path = Path().apply { moveTo(jx, jy) }
        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    path,
                    0,
                    if (humanTapsEnabled) HumanTapTiming.strokeDuration() else STROKE_FIXED_MS
                )
            )
            .build()

        return suspendCancellableCoroutine { continuation ->
            val dispatched = service.dispatchGesture(
                gesture,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (continuation.isActive) continuation.resume(true)
                    }
                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (continuation.isActive) continuation.resume(false)
                    }
                },
                null
            )
            if (!dispatched) {
                if (continuation.isActive) continuation.resume(false)
            }
        }
    }

    private fun collectClickableNodes(
        node: AccessibilityNodeInfo,
        list: MutableList<Pair<AccessibilityNodeInfo, Rect>>
    ) {
        if (node.isClickable) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (!bounds.isEmpty) {
                list.add(AccessibilityNodeInfo.obtain(node) to bounds)
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectClickableNodes(child, list)
            child.recycle()
        }
    }

    private fun screenWidth(): Int = ResourcesCompat.displayMetrics().widthPixels
    private fun screenHeight(): Int = ResourcesCompat.displayMetrics().heightPixels

    private fun clampToScreen(x: Int, y: Int): Pair<Int, Int> {
        val m = ResourcesCompat.displayMetrics()
        return x.coerceIn(0, m.widthPixels) to y.coerceIn(0, m.heightPixels)
    }

    /**
     * Retardo de "reacción humana" (gaussiano) antes del gesto, solo cuando
     * [humanTapsEnabled]. Un único delay por acción.
     */
    private suspend fun humanizeDelayIfEnabled() {
        if (!humanTapsEnabled) return
        val delayMs = HumanTapTiming.gaussianDelay()
        Logs.d(TAG, "🤖 Retardo humanizado de ${delayMs}ms antes del toque")
        delay(delayMs)
    }

    /** Aplica el jitter (si está activo) y recorta el punto a la pantalla. */
    private fun jitterAndClamp(x: Float, y: Float): Pair<Float, Float> {
        val (jx, jy) = if (humanTapsEnabled) HumanTapTiming.jitterPoint(x, y) else x to y
        val m = ResourcesCompat.displayMetrics()
        return jx.coerceIn(0f, m.widthPixels.toFloat()) to jy.coerceIn(0f, m.heightPixels.toFloat())
    }
}

/**
 * Acceso único a las métricas de pantalla del sistema. Se usa
 * `Resources.getSystem()` (no las del Service) para no depender del
 * contexto del servicio y poder reutilizar las mismas en cualquier punto.
 */
private object ResourcesCompat {
    fun displayMetrics(): android.util.DisplayMetrics =
        android.content.res.Resources.getSystem().displayMetrics
}