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
    OVERLAY
}

/** Foto del estado actual de cada permiso relevante. */
data class PermissionSnapshot(
    val accessibility: Boolean = false,
    val notifications: Boolean = false,
    val batteryOptimizationIgnored: Boolean = false,
    val overlay: Boolean = false
)

/** Un permiso con su estado y si es obligatorio para operar. */
data class PermissionItem(
    val permission: AppPermission,
    val granted: Boolean,
    val required: Boolean
)

object PermissionStatus {

    /** Checklist ordenado por importancia. Todos los permisos son obligatorios. */
    fun checklist(snapshot: PermissionSnapshot): List<PermissionItem> = listOf(
        PermissionItem(AppPermission.ACCESSIBILITY, snapshot.accessibility, required = true),
        PermissionItem(AppPermission.NOTIFICATIONS, snapshot.notifications, required = true),
        PermissionItem(AppPermission.BATTERY_OPTIMIZATION, snapshot.batteryOptimizationIgnored, required = true),
        PermissionItem(AppPermission.OVERLAY, snapshot.overlay, required = true)
    )

    /** Permisos obligatorios que aún no están concedidos. */
    fun missingRequired(snapshot: PermissionSnapshot): List<AppPermission> =
        checklist(snapshot)
            .filter { it.required && !it.granted }
            .map { it.permission }

    /** `true` si no falta ningún permiso obligatorio. */
    fun isReady(snapshot: PermissionSnapshot): Boolean = missingRequired(snapshot).isEmpty()
}
