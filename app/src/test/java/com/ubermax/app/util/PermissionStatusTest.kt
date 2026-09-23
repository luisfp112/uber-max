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
        overlay = true
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
    fun `con todos los obligatorios esta listo`() {
        assertTrue(PermissionStatus.isReady(allGranted()))
        assertTrue(PermissionStatus.missingRequired(allGranted()).isEmpty())
    }

    @Test
    fun `si falta uno cualquiera no esta listo`() {
        assertFalse(PermissionStatus.isReady(allGranted().copy(overlay = false)))
        assertFalse(PermissionStatus.isReady(allGranted().copy(notifications = false)))
        assertFalse(PermissionStatus.isReady(allGranted().copy(accessibility = false)))
    }

    @Test
    fun `el checklist marca required en todos los permisos`() {
        val items = PermissionStatus.checklist(PermissionSnapshot())
        assertEquals(4, items.size)
        assertTrue(items.all { it.required })
    }
}