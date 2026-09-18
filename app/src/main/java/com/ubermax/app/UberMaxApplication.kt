package com.ubermax.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class UberMaxApplication : Application() {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "ubermax_monitor"
        const val DECISION_CHANNEL_ID = "ubermax_decisions"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            val monitorChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(monitorChannel)

            val decisionChannel = NotificationChannel(
                DECISION_CHANNEL_ID,
                getString(R.string.decision_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.decision_channel_description)
                setShowBadge(true)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(decisionChannel)
        }
    }
}
