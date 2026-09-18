package com.ubermax.app.domain.model

/** Comando reconocido por voz durante el monitoreo. */
enum class VoiceCommand {
    ACCEPT,
    REJECT,
    NEXT,
    NONE
}

/**
 * Interpreta el texto reconocido por `SpeechRecognizer` como un comando.
 *
 * Puro (sin Android) para poder testearse en JVM. Se normaliza el texto
 * quitando mayúsculas, tildes y signos antes de comparar.
 */
object VoiceCommandParser {

    private val ACCEPT_WORDS = listOf(
        "aceptar", "acepta", "acepto", "si", "confirma", "confirmar", "accept", "yes"
    )
    private val REJECT_WORDS = listOf(
        "rechazar", "rechaza", "rechazo", "no", "cancelar", "cancela", "declina", "reject", "decline"
    )
    private val NEXT_WORDS = listOf(
        "siguiente", "espera", "esperar", "next", "wait"
    )

    fun parse(rawText: String?): VoiceCommand {
        if (rawText.isNullOrBlank()) return VoiceCommand.NONE
        val text = normalize(rawText)
        val tokens = text.split(' ').filter { it.isNotEmpty() }

        fun matches(words: List<String>): Boolean =
            words.any { word -> text == word || tokens.contains(word) }

        return when {
            matches(ACCEPT_WORDS) -> VoiceCommand.ACCEPT
            matches(REJECT_WORDS) -> VoiceCommand.REJECT
            matches(NEXT_WORDS) -> VoiceCommand.NEXT
            else -> VoiceCommand.NONE
        }
    }

    private fun normalize(input: String): String =
        java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
}
