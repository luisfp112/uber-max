package com.ubermax.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ubermax.app.accessibility.OfferParser
import com.ubermax.app.service.UberAccessibilityService

/**
 * [DEBUG] Inyecta una tarjeta de oferta simulada en el pipeline real de
 * UberMax (parser → geocoder → evaluación → reglas → HUD/TTS).
 *
 * Solo existe en builds de debug: jamás llega a release. El pipeline es el de
 * producción; lo único artificial es el origen de la tarjeta.
 *
 * Ejemplo de uso (adb):
 *   adb shell am broadcast -n com.ubermax.app/com.ubermax.app.debug.SimulateOfferReceiver \
 *     -a com.ubermax.app.debug.SIMULATE_OFFER \
 *     --es fare '$4.50' --es rating '4.92' --es trips '(120)' \
 *     --es pickup 'A 3 min (1.2 km)' --es pickupAddress 'Heiraway, Oscar Wilde' \
 *     --es trip 'Viaje: 9 min (3.2 km)' --es dest 'Av. Condor, Ingapirca'
 *
 * Todas las claves son Strings; las mínimas para una tarjeta válida son fare,
 * pickup y trip (con patrones de distancia min/km), porque el parser rechaza
 * tarjetas sin tarifa y sin distancias.
 */
class SimulateOfferReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // ── Reconstruir la tarjeta como lista de TextNodes (mismo layout real) ──
        val nodes = mutableListOf(
            OfferParser.TextNode(rideType(intent), ""),
            OfferParser.TextNode(intent.getStringExtra("fare") ?: "$4.50", ""),
            OfferParser.TextNode("✓ Documento de identidad verificado", ""),
            OfferParser.TextNode(ratingLine(intent), ""),
            OfferParser.TextNode(intent.getStringExtra("pickup") ?: "A 3 min (1.2 km)", ""),
            OfferParser.TextNode(intent.getStringExtra("pickupAddress") ?: "Heiraway, Oscar Wilde", ""),
            OfferParser.TextNode(intent.getStringExtra("trip") ?: "Viaje: 9 min (3.2 km)", ""),
            OfferParser.TextNode(intent.getStringExtra("dest") ?: "Av. Condor, Ingapirca", ""),
            OfferParser.TextNode("Termina en 12:45", ""),
            OfferParser.TextNode("Aceptar", "")
        )

        val offer = OfferParser().parseFromTextNodes(nodes)
        if (offer == null) {
            Log.w(TAG, "⚠️ Simulación: la tarjeta no pasó el parser — revisa los patrones min/km")
            return
        }

        Log.i(TAG, "🧪 Simulando tarjeta → \$${offer.rawFare} → ${offer.pickupAddress} → ${offer.destination}")
        UberAccessibilityService.simulateOffer(offer)
    }

    private fun ratingLine(intent: Intent): String {
        val rating = intent.getStringExtra("rating")
        val trips = intent.getStringExtra("trips") ?: ""
        return when {
            rating == null -> ""
            trips.isNotEmpty() -> "★ $rating $trips"
            else -> "★ $rating"
        }
    }

    private fun rideType(intent: Intent): String =
        "← ${intent.getStringExtra("rideType") ?: "UberX"}"

    companion object {
        private const val TAG = "SimulateOffer"
        const val ACTION = "com.ubermax.app.debug.SIMULATE_OFFER"
    }
}