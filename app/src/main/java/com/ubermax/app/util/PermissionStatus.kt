package com.ubermax.app.util

/**
 * Lógica pura (JVM) del estado de permisos de UberMax.
 *
 * No toca `Context` ni APIs de Android para poder testearse sin dispositivo.
 * [com.ubermax.app.ui.onboarding.OnboardingActivity] construye el
 * [PermissionSnapshot] consultando el sistema y usa estos métodos para decidir
 * qué pedir y si la app está lista para operar.
 */
enum class AppPermission {
    ACCESSIBILITY,
    NOTIFICATIONS,
    BATTERY_OPTIMIZATION,
    OVERLAY,
    LOCATION,
    MICROPHONE,
    BLUETOOTH
}

/** Foto del estado actual de cada permiso relevante. */
data class PermissionSnapshot(
    val accessibility: Boolean = false,
    val notifications: Boolean = false,
    val batteryOptimizationIgnored: Boolean = false,
    val overlay: Boolean = false,
    val location: Boolean = false,
    val microphone: Boolean = false,
    val bluetooth: Boolean = false
)

/** Un permiso con su estado y si es obligatorio para operar. */
data class PermissionItem(
    val permission: AppPermission,
    val granted: Boolean,
    val required: Boolean
)

object PermissionStatus {

    /**
     * Checklist ordenado por importancia. [requireVoice] y [requireBluetooth] sólo
     * vuelven obligatorios el micrófono / Bluetooth cuando el usuario activó voz o
     * auto-arranque por Bluetooth.
     */
    fun checklist(
        snapshot: PermissionSnapshot,
        requireVoice: Boolean = false,
        requireBluetooth: Boolean = false
    ): List<PermissionItem> = listOf(
        PermissionItem(AppPermission.ACCESSIBILITY, snapshot.accessibility, required = true),
        PermissionItem(AppPermission.NOTIFICATIONS, snapshot.notifications, required = true),
        PermissionItem(AppPermission.BATTERY_OPTIMIZATION, snapshot.batteryOptimizationIgnored, required = true),
        PermissionItem(AppPermission.OVERLAY, snapshot.overlay, required = true),
        PermissionItem(AppPermission.LOCATION, snapshot.location, required = false),
        PermissionItem(AppPermission.MICROPHONE, snapshot.microphone, required = requireVoice),
        PermissionItem(AppPermission.BLUETOOTH, snapshot.bluetooth, required = requireBluetooth)
    )

    /** Permisos obligatorios que aún no están concedidos. */
    fun missingRequired(
        snapshot: PermissionSnapshot,
        requireVoice: Boolean = false,
        requireBluetooth: Boolean = false
    ): List<AppPermission> = checklist(snapshot, requireVoice, requireBluetooth)
        .filter { it.required && !it.granted }
        .map { it.permission }

    /** `true` si no falta ningún permiso obligatorio. */
    fun isReady(
        snapshot: PermissionSnapshot,
        requireVoice: Boolean = false,
        requireBluetooth: Boolean = false
    ): Boolean = missingRequired(snapshot, requireVoice, requireBluetooth).isEmpty()
}
