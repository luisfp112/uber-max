package com.ubermax.app.accessibility

/**
 * TapGeometry — Geometría pura de toque para la UI de Uber Driver.
 *
 * Sin ninguna dependencia de Android (ni Rect, ni View, ni metrics), para que
 * toda la lógica de coordenadas pueda validarse con tests unitarios en JVM puro.
 *
 * Principio: NO hay coordenadas absolutas ni calibración por modelo de teléfono.
 * Todas las posiciones se calculan como PROPORCIONES relativas respecto a:
 *  - el contenedor detectado de la tarjeta de Uber (getBoundsInScreen), o
 *  - la pantalla completa (Resources.getSystem().displayMetrics).
 *
 * Así funciona igual en cualquier dispositivo Android, independientemente de su
 * resolución o densidad de pantalla.
 */
object TapGeometry {

    // ═══════════════════════════════════════════════════════
    // RectSpec — rectángulo puro (espejo inmutable de android.graphics.Rect
    // solo con los campos necesarios)
    // ═══════════════════════════════════════════════════════
    data class RectSpec(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    ) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top

        /** Centro exacto del rectángulo (punto para el tap). */
        val centerX: Int get() = left + width / 2
        val centerY: Int get() = top + height / 2

        /** Un rect vacío o invertido no es un objetivo de toque válido. */
        val isEmpty: Boolean get() = width <= 0 || height <= 0

        fun clamped(maxWidth: Int, maxHeight: Int): RectSpec = RectSpec(
            left.coerceIn(0, maxWidth),
            top.coerceIn(0, maxHeight),
            right.coerceIn(0, maxWidth),
            bottom.coerceIn(0, maxHeight)
        )
    }

    fun centerOf(rect: RectSpec): Pair<Int, Int> = rect.centerX to rect.centerY

    /**
     * Punto de toque RELATIVO dentro de [rect] (botón/ventana visible).
     *
     * Convención: (0,0) = esquina superior izquierda, (1,1) = inferior derecha.
     * Se usa para:
     *  - tocar el CENTRO exacto de un botón visible → relativePointIn(boton, 0.5f, 0.5f)
     *  - tocar un punto dentro de la tarjeta sin depender del nodo clicable exacto.
     *
     * Ej. relativePointIn(RectSpec(0,0,1000,200), 0.5f, 0.5f) → (500, 100).
     */
    fun relativePointIn(rect: RectSpec, xRatio: Float, yRatio: Float): Pair<Int, Int> =
        (rect.left + (rect.width * xRatio).toInt()) to
                (rect.top + (rect.height * yRatio).toInt())

    // ═══════════════════════════════════════════════════════
    // Puntos de toque de RESERVA (solo cuando no se detectó ningún nodo).
    // Proporciones relativas: se multiplican por las métricas reales de pantalla
    // en tiempo de ejecución, nunca por resoluciones fijas.
    // ═══════════════════════════════════════════════════════
    const val FALLBACK_ACCEPT_X_RATIO = 0.50f   // Centro horizontal
    const val FALLBACK_ACCEPT_Y_RATIO = 0.85f   // Tercio inferior (zona del botón)
    const val FALLBACK_DISMISS_X_RATIO = 0.94f  // Borde derecho (esquina de la X)
    const val FALLBACK_DISMISS_Y_RATIO = 0.28f  // Cuarto superior (esquina de la X)

    /**
     * Calcula el punto de toque de reserva para una resolución de pantalla dada.
     * Ej.: fallbackPoint(anchoPx, altoPx, 0.5f, 0.85f) → (50% ancho, 85% alto).
     */
    fun fallbackPoint(
        screenWidth: Int,
        screenHeight: Int,
        xRatio: Float,
        yRatio: Float
    ): Pair<Int, Int> = (screenWidth * xRatio).toInt() to (screenHeight * yRatio).toInt()

    fun fallbackAcceptPoint(screenWidth: Int, screenHeight: Int): Pair<Int, Int> =
        fallbackPoint(screenWidth, screenHeight, FALLBACK_ACCEPT_X_RATIO, FALLBACK_ACCEPT_Y_RATIO)

    fun fallbackDismissPoint(screenWidth: Int, screenHeight: Int): Pair<Int, Int> =
        fallbackPoint(screenWidth, screenHeight, FALLBACK_DISMISS_X_RATIO, FALLBACK_DISMISS_Y_RATIO)

    // ═══════════════════════════════════════════════════════
    // Botón X de descarte — búsqueda por región relativa
    // ═══════════════════════════════════════════════════════
    // Región esperada: cuadrante superior derecho de la TARJETA de Uber
    // (o de la pantalla como contenedor de reserva). Tamaño pequeño (ícono).
    const val DISMISS_TOP_RATIO = 0.375f          // Centro del candidato en el 37.5% superior
    const val DISMISS_RIGHT_RATIO = 0.741f        // Centro del candidato a la derecha del 74.1% del ancho
    const val DISMISS_MAX_WIDTH_RATIO = 0.185f    // Ícono: no más del 18.5% del ancho del contenedor
    const val DISMISS_MIN_WIDTH_RATIO = 0.018f    // Ícono: al menos 1.8% del ancho (evita falsos positivos)

    /**
     * True si [rect] es un candidato plausible de "X" por tamaño:
     * pequeño (ícono) pero no ínfimo, medido contra el [container].
     */
    fun isDismissSized(rect: RectSpec, container: RectSpec): Boolean {
        if (rect.isEmpty) return false
        val maxSize = container.width * DISMISS_MAX_WIDTH_RATIO
        val minSize = container.width * DISMISS_MIN_WIDTH_RATIO
        return rect.width <= maxSize &&
            rect.height <= maxSize &&
            rect.width >= minSize &&
            rect.height >= minSize
    }

    /**
     * True si el CENTRO de [rect] está en el cuadrante superior derecho del [container].
     * Las proporciones son relativas al contenedor (tarjeta o pantalla), no absolutas.
     */
    fun isInTopRightRegion(
        rect: RectSpec,
        container: RectSpec,
        topRatio: Float = DISMISS_TOP_RATIO,
        rightRatio: Float = DISMISS_RIGHT_RATIO
    ): Boolean {
        val regionTop = container.top + (container.height * topRatio).toInt()
        val regionLeft = container.left + (container.width * (1f - rightRatio)).toInt()
        return rect.centerY < regionTop && rect.centerX > regionLeft
    }

    /**
     * Elige el candidato de botón X entre todos los nodos clickables:
     *  — con tamaño de ícono (isDismissSized), y
     *  — con centro en el cuadrante superior derecho del [container].
     * Devuelve el más a la derecha (la X está en la esquina).
     * Retorna null si ninguno cumple.
     */
    fun chooseDismissCandidate(
        candidates: List<RectSpec>,
        container: RectSpec
    ): RectSpec? =
        candidates
            .filter { isDismissSized(it, container) && isInTopRightRegion(it, container) }
            .maxByOrNull { it.right }
}