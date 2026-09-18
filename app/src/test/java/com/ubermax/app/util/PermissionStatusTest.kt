package com.ubermax.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests JVM puros del checklist de permisos (sin Context, sin dispositivo).
 */
class PermissionStatusTest {

    private fun allGranted() = PermissionSnapshot(
        accessibility = true,
        notifications = true,
        batteryOptimizationIgnored = true,
        overlay = true,
        location = true,
        microphone = true,
        bluetooth = true
    )

    @Test
    fun `sin permisos faltan los cuatro obligatorios`() {
        val missing = PermissionStatus.missingRequired(PermissionSnapshot())
        assertEquals(
            listOf(
                AppPermission.ACCESSIBILITY,
                AppPermission.NOTIFICATIONS,
                AppPermission.BATTERY_OPTIMIZATION,
                AppPermission.OVERLAY
            ),
            missing
        )
        assertFalse(PermissionStatus.isReady(PermissionSnapshot()))
    }

    @Test
    fun `ubicacion no es obligatoria`() {
        val snapshot = allGranted().copy(location = false)
        assertTrue(PermissionStatus.isReady(snapshot))
        assertFalse(PermissionStatus.missingRequired(snapshot).contains(AppPermission.LOCATION))
    }

    @Test
    fun `microfono solo es obligatorio si se activa voz`() {
        val snapshot = allGranted().copy(microphone = false)
        assertTrue(PermissionStatus.isReady(snapshot))
        assertTrue(
            PermissionStatus.missingRequired(snapshot, requireVoice = true)
                .contains(AppPermission.MICROPHONE)
        )
    }

    @Test
    fun `bluetooth solo es obligatorio si se activa auto-arranque BT`() {
        val snapshot = allGranted().copy(bluetooth = false)
        assertTrue(PermissionStatus.isReady(snapshot))
        assertTrue(
            PermissionStatus.missingRequired(snapshot, requireBluetooth = true)
                .contains(AppPermission.BLUETOOTH)
        )
    }

    @Test
    fun `con todos los obligatorios esta listo`() {
        assertTrue(PermissionStatus.isReady(allGranted()))
        assertTrue(PermissionStatus.missingRequired(allGranted()).isEmpty())
    }

    @Test
    fun `el checklist marca required correctamente`() {
        val items = PermissionStatus.checklist(PermissionSnapshot(), requireVoice = true)
        assertEquals(7, items.size)
        assertEquals(
            AppPermission.MICROPHONE,
            items.first { it.permission == AppPermission.MICROPHONE }.permission
        )
        assertTrue(items.first { it.permission == AppPermission.MICROPHONE }.required)
        assertFalse(items.first { it.permission == AppPermission.LOCATION }.required)
    }
}
