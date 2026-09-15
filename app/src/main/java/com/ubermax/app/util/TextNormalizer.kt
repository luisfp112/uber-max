package com.ubermax.app.util

import java.text.Normalizer
import java.util.Locale

/**
 * Normalización de texto para comparaciones robustas de la lista negra.
 *
 * Convierte cualquier cadena a una forma canónica que ignora:
 *  - MAYÚSCULAS / minúsculas
 *  - Tildes y diacríticos ("Techo Própio" → "techo propio")
 *  - Caracteres especiales y puntuación ("Av. Ficoa, #45" → "av ficoa 45")
 *
 * Así, una oferta que diga "TECHO PRÓPIO." dispara CANCEL aunque el conductor
 * haya configurado la keyword como "Techo Propio".
 *
 * 100% JVM puro (java.text.Normalizer) → testeable con JUnit sin dispositivo.
 */
object TextNormalizer {

    /** Marcas diacríticas combinadas (después de descomponer con NFD). */
    private val COMBINING_MARKS = Regex("\\p{M}+")
    /** Cualquier carácter que no sea letra, dígito o espacio. */
    private val NON_ALPHANUMERIC = Regex("[^\\p{L}\\p{N}\\s]+")
    /** Espacios consecutivos. */
    private val MULTI_SPACE = Regex("\\s+")

    /**
     * Forma canónica de [input]: minúsculas, sin tildes, sin caracteres
     * especiales, espacios colapsados.
     *
     * Ejemplos:
     *  "Techo Propio"          → "techo propio"
     *  "Av. TECHO PRÓPIO."     → "av techo propio"
     *  "CALLE 12 de Octubre,#" → "calle 12 de octubre"
     */
    fun normalize(input: String): String {
        if (input.isEmpty()) return ""

        val lower = input.lowercase(Locale.ROOT)
        val decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD)
        val withoutAccents = COMBINING_MARKS.replace(decomposed, "")
        val withoutSpecial = NON_ALPHANUMERIC.replace(withoutAccents, " ")
        return MULTI_SPACE.replace(withoutSpecial, " ").trim()
    }
}