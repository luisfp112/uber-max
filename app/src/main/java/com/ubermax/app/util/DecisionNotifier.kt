package com.ubermax.app.util

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.ubermax.app.R
import com.ubermax.app.UberMaxApplication
import com.ubermax.app.domain.model.Action
import com.ubermax.app.domain.model.DecisionReason
import com.ubermax.app.domain.model.HudReason
import com.ubermax.app.domain.model.HudReasonFormatter
import com.ubermax.app.domain.model.OfferDecision

/**
 * Feedback inmediato de una decisión: notificación heads-up, vibración y/o
 * sonido. Cada canal es independiente y respeta los ajustes del conductor.
 *
 * No es lógica de decisión: nunca lanza si falla (ROMs sin vibrador, audífono
 * ocupado, permiso revocado), porque un fallo aquí no debe tumbar el pipeline.
 */
class DecisionNotifier(private val context: Context) {

    fun feedback(
        decision: OfferDecision,
        notify: Boolean,
        vibrate: Boolean,
        sound: Boolean
    ) {
        if (notify) runCatching { notifyDecision(decision) }
        if (vibrate) runCatching { vibrate(decision.isAccepted) }
        if (sound) runCatching { playTone(decision.isAccepted) }
    }

    private fun notifyDecision(decision: OfferDecision) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return

        val title = when {
            decision.simulated -> context.getString(R.string.decision_notification_simulated)
            decision.isAccepted -> context.getString(R.string.decision_notification_accepted)
            else -> context.getString(R.string.decision_notification_rejected)
        }
        val reason = reasonText(decision)
        val net = "\$%.2f · \$%.2f/km".format(
            decision.evaluatedOffer.netProfit,
            decision.evaluatedOffer.profitPerKm
        )
        val text = if (reason.isBlank()) net else "$reason · $net"

        val notification = NotificationCompat.Builder(context, UberMaxApplication.DECISION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setTimeoutAfter(8000)
            .setOnlyAlertOnce(true)
            .build()

        manager.notify(DECISION_NOTIFICATION_ID, notification)
    }

    private fun reasonText(decision: OfferDecision): String =
        when (val reason = HudReasonFormatter.mainReason(decision)) {
            HudReason.Accepted -> context.getString(R.string.hud_reason_accepted)
            HudReason.Blacklist -> context.getString(R.string.hud_reason_blacklist)
            is HudReason.Raw -> reason.text
            is HudReason.Known -> labelFor(reason.reason)
            HudReason.None -> ""
        }

    private fun labelFor(reason: DecisionReason): String = when (reason) {
        DecisionReason.LOW_FARE -> context.getString(R.string.hud_reason_low_fare)
        DecisionReason.LOW_NET -> context.getString(R.string.hud_reason_low_net)
        DecisionReason.LOW_PER_KM -> context.getString(R.string.hud_reason_low_per_km)
        DecisionReason.LOW_PER_HOUR -> context.getString(R.string.hud_reason_low_per_hour)
        DecisionReason.PICKUP_FAR -> context.getString(R.string.hud_reason_pickup_far)
        DecisionReason.TRIP_LONG -> context.getString(R.string.hud_reason_trip_long)
        DecisionReason.LOW_RATING -> context.getString(R.string.hud_reason_low_rating)
        DecisionReason.DEADHEAD -> context.getString(R.string.hud_reason_deadhead)
        DecisionReason.BLACKLIST -> context.getString(R.string.hud_reason_blacklist)
        DecisionReason.AUTO_ACCEPT_OFF -> context.getString(R.string.hud_reason_auto_accept_off)
    }

    @SuppressLint("MissingPermission")
    private fun vibrate(accepted: Boolean) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return

        if (!vibrator.hasVibrator()) return

        val timings = if (accepted) longArrayOf(0, 60, 60, 60) else longArrayOf(0, 120)
        vibrator.vibrate(VibrationEffect.createWaveform(timings, -1))
    }

    private fun playTone(accepted: Boolean) {
        val toneType = if (accepted) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_NACK
        val toneGenerator = runCatching {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, TONE_VOLUME)
        }.getOrNull() ?: return
        try {
            toneGenerator.startTone(toneType, 150)
        } finally {
            // startTone es asíncrono; se libera en un hilo aparte tras el tono.
            Thread {
                Thread.sleep(400)
                runCatching { toneGenerator.release() }
            }.start()
        }
    }

    companion object {
        private const val DECISION_NOTIFICATION_ID = 2001
        private const val TONE_VOLUME = 70
    }
}
