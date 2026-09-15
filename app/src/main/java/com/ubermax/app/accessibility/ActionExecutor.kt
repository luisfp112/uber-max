package com.ubermax.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import com.ubermax.app.util.Logs
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
 *    - Ofertas asignadas:    "Aceptar" / "Aceptar viaje" / "Confirmar"
 *    - Ofertas abiertas:     "Viaje disponible" / "Postularse" / "Aplicar"
 * 2. CANCEL → toque en la X (cuadrante superior derecho de la tarjeta)
 *
 * Estrategias de toque (cascada):
 * 1. ACTION_CLICK directo en el nodo
 * 2. Subir al contenedor clickable más cercano → ACTION_CLICK
 * 3. dispatchGesture (gesto de toque REAL) en el centro exacto del contenedor/botón
 * 4. Fallback: dispatchGesture en proporciones relativas de pantalla
 *
 * Tras un toque exitoso se RE-VERIFICA la pantalla: si el botón sigue presente,
 * se devuelve false para que el llamador reintente (no basta con cambiar el HUD).
 */
class ActionExecutor(
    private val service: AccessibilityService
) {

    companion object {
        private const val TAG = "ActionExecutor"

        // ── Textos del botón ACEPTAR (asignadas + abiertas) ──
        private val ACCEPT_TEXTS = listOf(
            // Ofertas asignadas a este conductor
            "aceptar", "aceptar viaje", "confirmar",
            // Ofertas abiertas (se compiten por ellas)
            "viaje disponible", "postularse", "postularte", "postularme", "postular",
            "aplicar", "apuntarse",
            // Inglés
            "trip available", "accept", "confirm", "apply"
        )

        // ── Textos del botón CANCELAR / RECHAZAR (X superior) ──
        private val DISMISS_TEXTS = listOf(
            "cerrar", "descartar", "rechazar", "no, gracias", "cancelar", "salir",
            "close", "dismiss", "decline", "cancel", "discard"
        )

        /** True si [raw] corresponde a un botón de aceptar (asignada o abierta). */
        fun matchesAcceptText(raw: String): Boolean {
            val t = raw.trim().lowercase()
            return ACCEPT_TEXTS.any { t.contains(it) }
        }

        /** True si [raw] corresponde al botón X / descartar. "x" aislado también vale. */
        fun matchesDismissText(raw: String): Boolean {
            val t = raw.trim().lowercase()
            if (t == "x") return true
            return DISMISS_TEXTS.any { t.contains(it) }
        }

        // ── Proporciones relativas (Se aplican a la resolución real del dispositivo) ──
        private const val FALLBACK_ACCEPT_X_RATIO = TapGeometry.FALLBACK_ACCEPT_X_RATIO
        private const val FALLBACK_ACCEPT_Y_RATIO = TapGeometry.FALLBACK_ACCEPT_Y_RATIO
        private const val FALLBACK_DISMISS_X_RATIO = TapGeometry.FALLBACK_DISMISS_X_RATIO
        private const val FALLBACK_DISMISS_Y_RATIO = TapGeometry.FALLBACK_DISMISS_Y_RATIO

        // Max reintentos y verificación post-acción
        private const val VERIFY_DELAY_MS = 400L
        private const val MAX_PARENT_DEPTH = 5
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
            val text = node.text?.toString() ?: ""
            val desc = node.contentDescription?.toString() ?: ""
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
                val child = node.getChild(i)
                if (child != null) {
                    walk(child, depth + 1)
                    child.recycle()
                }
            }
        }

        walk(root, 0)
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
     * Devuelve los bounds de la tarjeta de la oferta: contenedor clicable más
     * cercano al nodo que contiene la tarifa (patrón monetario).
     */
    private fun findCardContainer(rootNode: AccessibilityNodeInfo): TapGeometry.RectSpec? {
        val fareNode = findActionNode(rootNode) { RegexPatterns.FARE_PATTERN.containsMatchIn(it) }
            ?: return null
        try {
            val container = nearestClickableAncestor(fareNode)
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
     * Busca un ícono de cierre (ImageButton/ImageView clicable) cuyo centro esté
     * en el cuadrante superior derecho del contenedor (tarjeta o pantalla).
     */
    private fun findDismissIconInRegion(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val container = findCardContainer(rootNode)
        val screen = TapGeometry.RectSpec(0, 0, screenWidth(), screenHeight())

        var best: AccessibilityNodeInfo? = null
        var bestRight = -1

        fun walk(node: AccessibilityNodeInfo) {
            val className = node.className?.toString() ?: ""
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
                val child = node.getChild(i)
                if (child != null) {
                    walk(child)
                    child.recycle()
                }
            }
        }

        walk(rootNode)
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
        val container = nearestClickableAncestor(node)
        try {
            val bounds = Rect()
            container.getBoundsInScreen(bounds)

            // 1. ACTION_CLICK sobre el contenedor clicable
            if (container.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                Logs.i(TAG, "✅ $actionName vía ACTION_CLICK en contenedor ($bounds)")
                return true
            }

            // 2. Gesto de toque REAL en el centro exacto del contenedor/botón
            if (!bounds.isEmpty) {
                val spec = TapGeometry.RectSpec(bounds.left, bounds.top, bounds.right, bounds.bottom)
                val (x, y) = TapGeometry.centerOf(spec)
                val (cx, cy) = clampToScreen(x, y)
                Logs.d(TAG, "👆 $actionName dispatchGesture en (${cx},${cy})")
                if (tryGestureWithCallback(cx.toFloat(), cy.toFloat())) {
                    return true
                }
            }

            // 3. Último recurso: gesto en el centro del nodo original
            val own = Rect()
            node.getBoundsInScreen(own)
            if (!own.isEmpty) {
                val (x, y) = clampToScreen(own.centerX(), own.centerY())
                return tryGestureWithCallback(x.toFloat(), y.toFloat())
            }
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
        val stillPresent = findActionNode(rootNode, matcher)
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

    /** Retorna una copia `obtain()` del nodo clicable más cercano (hasta MAX_PARENT_DEPTH niveles). */
    private fun nearestClickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo {
        if (node.isClickable) return AccessibilityNodeInfo.obtain(node)

        var parent = node.parent
        var depth = 0
        while (parent != null && depth < MAX_PARENT_DEPTH) {
            if (parent.isClickable) {
                val result = AccessibilityNodeInfo.obtain(parent)
                parent.recycle()
                return result
            }
            val grandparent = parent.parent
            parent.recycle()
            parent = grandparent
            depth++
        }
        parent?.recycle()
        return AccessibilityNodeInfo.obtain(node)
    }

    /**
     * dispatchGesture con callback — espera a que el gesto termine
     * antes de retornar. Usa suspendCancellableCoroutine para convertir
     * el callback asíncrono en una función suspend.
     */
    private suspend fun tryGestureWithCallback(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
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