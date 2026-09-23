package com.ubermax.app.util

import java.util.Random
import kotlin.math.roundToLong

/**
 * Timing de taps "humanizados" (100% JVM, testeable sin dispositivo).
 *
 * Antes de pulsar sobre la UI de Uber se espera un retardo gaussiano para
 * simular el tiempo de reacción de una persona, y el punto exacto de toque
 * se desplaza con un pequeño jitter para no producir coordenadas idénticas
 * (patrón típico de autómatas).
 *
 * Parámetros (definidos en `ubicacion.md`):
 *  - Retardo: N(1450, 250²) ms, recortado a [850, 2350] ms.
 *  - Jitter: ±14 px en X, ±10 px en Y.
 *  - Duración del trazo: 60–90 ms.
 */
object HumanTapTiming {

    const val MEAN_DELAY_MS = 1450.0
    const val STD_DEVIATION_MS = 250.0
    const val MIN_DELAY_MS = 850L
    const val MAX_DELAY_MS = 2350L

    const val STROKE_MIN_MS = 60L
    const val STROKE_MAX_MS = 90L

    const val JITTER_X_PX = 14f
    const val JITTER_Y_PX = 10f

    /** Retardo gaussiano antes del gesto, recortado al rango [MIN, MAX]. */
    fun gaussianDelay(random: Random = Random()): Long {
        val raw = random.nextGaussian() * STD_DEVIATION_MS + MEAN_DELAY_MS
        return raw.roundToLong().coerceIn(MIN_DELAY_MS, MAX_DELAY_MS)
    }

    /** Duración del trazo del gesto (ms), uniforme en [STROKE_MIN, STROKE_MAX]. */
    fun strokeDuration(random: Random = Random()): Long {
        val span = STROKE_MAX_MS - STROKE_MIN_MS + 1
        return STROKE_MIN_MS + (random.nextLong().ushr(1) % span)
    }

    /** Desplaza el punto de toque con el jitter configurado. */
    fun jitterPoint(x: Float, y: Float, random: Random = Random()): Pair<Float, Float> {
        val jx = x + (random.nextFloat() * 2 - 1) * JITTER_X_PX
        val jy = y + (random.nextFloat() * 2 - 1) * JITTER_Y_PX
        return jx to jy
    }
}