package com.ubermax.app.util

import android.content.Context
import android.speech.tts.TextToSpeech
import com.ubermax.app.R
import com.ubermax.app.domain.model.OfferDecision
import java.util.Locale

/**
 * Anuncio por voz (TTS) de una decisión.
 *
 * Se eliminaron la notificación heads-up, la vibración y el tono de sonido
 * (features descartadas). La app jamás rechaza activamente: solo avisa y el
 * conductor decide. Por eso la voz no dice "viaje rechazado": [VoiceAlertBuilder]
 * emite SOLO frases cortas del porqué (ubicación no localizada y uno por cada
 * filtro fallado), y "Viaje aceptado" cuando la oferta cumple todo.
 *
 * No es lógica de decisión: nunca lanza si falla (motor de voz no disponible,
 * hilo ocupado), porque un fallo aquí no debe tumbar el pipeline.
 */
class DecisionNotifier(private val context: Context) {

    fun feedback(
        decision: OfferDecision,
        speak: Boolean = false,
        pickupLocated: Boolean = true,
        destinationLocated: Boolean = true
    ) {
        if (speak) runCatching {
            speak(VoiceAlertBuilder.build(decision, pickupLocated, destinationLocated))
        }
    }

    // ── Anuncio por voz (TTS) ──
    // El motor se crea bajo demanda y de forma idempotente. Si la primera
    // decisión llega antes de que termine de inicializar el motor (onInit),
    // se encola y se pronuncia en cuanto esté listo.

    @Volatile
    private var tts: TextToSpeech? = null

    @Volatile
    private var ttsReady = false

    private val pendingSpeaks = ArrayDeque<String>()

    @Synchronized
    private fun speak(text: String) {
        val engine = tts ?: TextToSpeech(context.applicationContext) { status ->
            synchronized(this@DecisionNotifier) {
                ttsReady = status == TextToSpeech.SUCCESS
                if (ttsReady) {
                    engineLanguage()
                    while (pendingSpeaks.isNotEmpty()) {
                        tts?.speak(pendingSpeaks.removeFirst(), TextToSpeech.QUEUE_FLUSH, null, "ubermax")
                    }
                }
            }
        }.also { tts = it }

        if (ttsReady) {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ubermax")
        } else {
            pendingSpeaks.add(text)
        }
    }

    private fun engineLanguage() {
        val result = tts?.setLanguage(Locale("es", "ES"))
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            // Sin voz en español instalada se intenta igualmente con el locale activo.
            tts?.language = Locale.getDefault()
        }
    }
}