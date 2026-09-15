package com.ubermax.app.service

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.ubermax.app.R
import com.ubermax.app.domain.model.Action
import com.ubermax.app.domain.model.OfferDecision
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

/**
 * FloatingWindowService — HUD overlay compacto con métricas de rentabilidad,
 * destino del pasajero, y warnings/recomendaciones.
 *
 * Muestra:
 * - Decisión: ✅ ACEPTADO / ⚠️ NO RECOMENDABLE / 🚫 CANCELANDO / ⏳ ESPERANDO
 * - Ganancia neta
 * - Destino del pasajero (prominente)
 * - Distancias (pickup + trip)
 * - Costo combustible
 * - Métricas de rentabilidad ($/km, $/hr)
 * - Rating pasajero
 * - Warnings detallados (qué filtros fallaron)
 * - Recomendación IA (si activada)
 */
@AndroidEntryPoint
class FloatingWindowService : Service() {

    companion object {
        const val ACTION_SHOW = "com.ubermax.ACTION_SHOW_HUD"
        const val ACTION_HIDE = "com.ubermax.ACTION_HIDE_HUD"
        var isShowing = false
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var isCollapsed = false
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> {
                removeOverlay()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                if (floatingView == null) {
                    createOverlay()
                    observeDecisions()
                }
            }
        }
        return START_STICKY
    }

    private fun createOverlay() {
        val inflater = LayoutInflater.from(this)
        floatingView = inflater.inflate(R.layout.layout_hud_overlay, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 100
        }

        setupDragAndCollapse(params)
        windowManager?.addView(floatingView, params)
        isShowing = true
    }

    private fun setupDragAndCollapse(params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isClick = true

        floatingView?.findViewById<View>(R.id.hud_header)?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isClick = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    if (deltaX * deltaX + deltaY * deltaY > 100) isClick = false
                    params.x = initialX + deltaX
                    params.y = initialY + deltaY
                    windowManager?.updateViewLayout(floatingView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isClick) toggleCollapse()
                    true
                }
                else -> false
            }
        }
    }

    private fun toggleCollapse() {
        isCollapsed = !isCollapsed
        floatingView?.findViewById<View>(R.id.expanded_content)?.visibility =
            if (isCollapsed) View.GONE else View.VISIBLE
    }

    private fun observeDecisions() {
        serviceScope.launch {
            UberAccessibilityService.decisionFlow.collectLatest { decision ->
                updateHUD(decision)
            }
        }
    }

    private fun updateHUD(decision: OfferDecision) {
        val view = floatingView ?: return
        val offer = decision.evaluatedOffer.offer
        val eval = decision.evaluatedOffer

        // ── Decisión (badge superior) ──
        val tvDecision = view.findViewById<TextView>(R.id.tv_decision)
        when (decision.action) {
            Action.ACCEPT -> {
                tvDecision.text = "✅ ACEPTADO"
                tvDecision.setTextColor(getColor(R.color.hud_accept))
            }
            Action.WARN -> {
                tvDecision.text = "⚠️ NO RECOMENDABLE"
                tvDecision.setTextColor(getColor(R.color.warning))
            }
            Action.CANCEL -> {
                tvDecision.text = "🚫 CANCELANDO"
                tvDecision.setTextColor(getColor(R.color.hud_ignore))
            }
            Action.IGNORE -> {
                tvDecision.text = "⏳ IGNORADO"
                tvDecision.setTextColor(getColor(R.color.text_secondary))
            }
        }

        // ── Ganancia neta ──
        val tvProfit = view.findViewById<TextView>(R.id.tv_net_profit)
        val profitText = "💰 Ganancia: \$%.2f".format(eval.netProfit)
        tvProfit.text = profitText
        tvProfit.setTextColor(
            if (eval.netProfit > 0) getColor(R.color.profit_positive)
            else getColor(R.color.profit_negative)
        )

        // ── Destino del pasajero (prominente) ──
        val tvDest = view.findViewById<TextView>(R.id.tv_destination)
        if (offer.destination.isNotEmpty()) {
            tvDest.text = "📍 Destino: ${offer.destination}"
            tvDest.visibility = View.VISIBLE
        } else {
            tvDest.text = "📍 Destino: no disponible"
            tvDest.visibility = View.VISIBLE
        }

        // ── Distancias ──
        val tvDistances = view.findViewById<TextView>(R.id.tv_distances)
        tvDistances.text = buildString {
            append("🚶 Recogida: ${offer.pickupKm}km")
            if (offer.pickupMinutes > 0) append(" (${offer.pickupMinutes}min)")
            append(" | 🚗 Viaje: ${offer.tripKm}km")
            if (offer.tripMinutes > 0) append(" (${offer.tripMinutes}min)")
        }

        // ── Costo combustible ──
        val tvFuel = view.findViewById<TextView>(R.id.tv_fuel_cost)
        tvFuel.text = "⛽ Combustible: \$%.2f".format(eval.fuelCost)

        // ── Métricas de rentabilidad ──
        val tvMetrics = view.findViewById<TextView>(R.id.tv_profit_metrics)
        tvMetrics.text = "📊 \$%.2f/km | \$%.2f/hr".format(eval.profitPerKm, eval.profitPerHour)

        // ── Rating pasajero ──
        val tvRating = view.findViewById<TextView>(R.id.tv_rating)
        tvRating.text = buildString {
            append("⭐ ${offer.passengerRating}")
            if (offer.passengerTrips > 0) append(" (${offer.passengerTrips} viajes)")
            if (offer.rideType.isNotEmpty()) append(" | ${offer.rideType}")
        }

        // ── Warnings (filtros fallidos) ──
        val tvWarnings = view.findViewById<TextView>(R.id.tv_warnings)
        if (decision.failedFilters.isNotEmpty() && decision.action != Action.ACCEPT) {
            tvWarnings.text = decision.failedFilters.joinToString("\n")
            tvWarnings.visibility = View.VISIBLE
        } else {
            tvWarnings.visibility = View.GONE
        }

        // ── Recomendación IA ──
        val tvAI = view.findViewById<TextView>(R.id.tv_ai_recommendation)
        if (decision.aiRecommendation.isNotEmpty()) {
            tvAI.text = "🧠 ${decision.aiRecommendation}"
            tvAI.visibility = View.VISIBLE
        } else {
            tvAI.visibility = View.GONE
        }

        // Expandir si estaba colapsado para que el conductor vea los datos
        if (isCollapsed && decision.action != Action.IGNORE) {
            toggleCollapse()
        }
    }

    private fun removeOverlay() {
        floatingView?.let {
            windowManager?.removeView(it)
            floatingView = null
        }
        isShowing = false
    }

    override fun onDestroy() {
        serviceScope.cancel()
        removeOverlay()
        super.onDestroy()
    }
}
