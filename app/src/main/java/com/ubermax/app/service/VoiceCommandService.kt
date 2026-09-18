package com.ubermax.app.service

import android.Manifest
import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.ubermax.app.R
import com.ubermax.app.UberMaxApplication
import com.ubermax.app.domain.model.VoiceCommandParser
import com.ubermax.app.util.Logs
import com.ubermax.app.util.VoiceCommandBus

/**
 * Escucha comandos de voz ("aceptar" / "rechazar") mientras el monitoreo está
 * activo. Es opt-in (`voiceControlEnabled`) y requiere permiso RECORD_AUDIO.
 *
 * Corre como foreground service para que el sistema no corte el micrófono.
 * Reinicia la escucha automáticamente tras cada resultado/error.
 */
@android.annotation.SuppressLint("MissingPermission")
class VoiceCommandService : Service() {

    companion object {
        const val ACTION_START = "com.ubermax.app.START_VOICE"
        const val ACTION_STOP = "com.ubermax.app.STOP_VOICE"
        private const val NOTIFICATION_ID = 1003
        private const val RESTART_DELAY_MS = 500L
        private const val TAG = "VoiceCommandService"

        @Volatile
        var isRunning = false
            private set
    }

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var listening = false

    private val restartTask = Runnable { startListening() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopListening()
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        if (!hasMicPermission()) {
            Logs.w(TAG, "Sin permiso RECORD_AUDIO; no se inicia reconocimiento")
            stopSelf()
            return START_NOT_STICKY
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Logs.w(TAG, "Reconocimiento de voz no disponible en este dispositivo")
            stopSelf()
            return START_NOT_STICKY
        }

        startAsForeground()
        isRunning = true
        createRecognizer()
        startListening()
        return START_STICKY
    }

    private fun startAsForeground() {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, UberMaxApplication.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.voice_notification_title))
            .setContentText(getString(R.string.voice_notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

    private fun createRecognizer() {
        if (recognizer != null) return
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(listener)
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { listening = false }
        override fun onEvent(eventType: Int, params: Bundle?) {}
        override fun onPartialResults(partialResults: Bundle?) {
            handleResults(partialResults)
        }
        override fun onResults(results: Bundle?) {
            handleResults(results)
            scheduleRestart()
        }
        override fun onError(error: Int) {
            listening = false
            Logs.d(TAG, "Error de reconocimiento: $error")
            scheduleRestart()
        }
    }

    private fun handleResults(bundle: Bundle?) {
        val texts = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val first = texts?.firstOrNull() ?: return
        val command = VoiceCommandParser.parse(first)
        if (command != com.ubermax.app.domain.model.VoiceCommand.NONE) {
            Logs.i(TAG, "Comando de voz: $command ('$first')")
            VoiceCommandBus.emit(command)
        }
    }

    private fun startListening() {
        if (!isRunning) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        try {
            recognizer?.startListening(intent)
            listening = true
        } catch (t: Throwable) {
            Logs.w(TAG, "No se pudo iniciar escucha: ${t.message}")
            scheduleRestart()
        }
    }

    private fun scheduleRestart() {
        if (!isRunning) return
        handler.removeCallbacks(restartTask)
        handler.postDelayed(restartTask, RESTART_DELAY_MS)
    }

    private fun stopListening() {
        isRunning = false
        listening = false
        handler.removeCallbacks(restartTask)
        try {
            recognizer?.stopListening()
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (t: Throwable) {
            Logs.d(TAG, "Error liberando recognizer: ${t.message}")
        }
        recognizer = null
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        stopListening()
        super.onDestroy()
    }
}
