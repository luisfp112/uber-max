package com.ubermax.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receptor SOLO DEBUG para inyectar ofertas de prueba desde adb y ejercitar
 * el pipeline completo (parser → evaluación → reglas → HUD → registro).
 *
 * En release NO existe (está en `src/debug/`), así que el hook no puede usarse
 * contra conductores reales.
 *
 * Uso:
 *   adb shell am broadcast \
 *     -a com.ubermax.app.SIMULATE_OFFER \
 *     -n com.ubermax.app/.service.SimulateOfferReceiver \
 *     --ei offer_index 0     # 0=rentable, 1=tarifa baja, 2=viaje largo
 */
class SimulateOfferReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val index = intent.getIntExtra(UberAccessibilityService.EXTRA_OFFER_INDEX, 0)
        val target = Intent(context, UberAccessibilityService::class.java).apply {
            action = UberAccessibilityService.ACTION_SIMULATE_OFFER
            putExtra(UberAccessibilityService.EXTRA_OFFER_INDEX, index)
        }
        context.startService(target)
    }
}