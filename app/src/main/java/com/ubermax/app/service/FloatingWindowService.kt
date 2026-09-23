package com.ubermax.app.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.ubermax.app.R
import com.ubermax.app.UberMaxApplication
import com.ubermax.app.domain.model.Action
import com.ubermax.app.domain.model.DecisionReason
import com.ubermax.app.domain.model.HudReason
import com.ubermax.app.domain.model.HudReasonFormatter
import com.ubermax.app.domain.model.OfferDecision
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

/**
 * FloatingWindowService — HUD flotante minimalista para conducción.
 *
 * Muestra únicamente 4 datos de alto contraste:
 *  - Ganancia neta (ej: "$4.20 neta")
 *  - Tarifa por km (ej: "$0.85/km")
 *  - Destino/Sector abreviado
 *  - Badge de decisión ACEPTADO/RECHAZADO + motivo principal en una línea
 *
 * Auto-colapso inteligente: 6 segundos después de cada decisión la ventana
 * se contrae a una burbuja mínima no intrusiva. Tocar la burbuja la expande.
 *
 * Corre como servicio en primer plano (foreground) para no ser destruido bajo
 * baja memoria mientras Uber Driver está en pantalla.
 */
@AndroidEntryPoint
class FloatingWindowService : Service() {

    companion object {
        const val ACTION_SHOW = "com.ubermax.ACTION_SHOW_HUD"
        const val ACTION_HIDE = "com.ubermax.ACTION_HIDE_HUD"
        const val NOTIFICATION_ID = 1002
        private const val AUTO_COLLAPSE_DELAY_MS = 6_000L
        var isShowing = false
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var isCollapsed = false
    private var collapseJob: Job? = null
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
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                if (floatingView == null) {
                    createOverlay()
                    observeDecisions()
                    scheduleAutoCollapse()
                }
                startAsForeground()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        collapseJob?.cancel()
        serviceScope.cancel()
        removeOverlay()
        super.onDestroy()
    }

    private fun startAsForeground() {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            createNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
    }

    private fun createNotification(): Notification =
        NotificationCompat.Builder(this, UberMaxApplication.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.hud_notification_title))
            .setContentText(getString(R.string.hud_notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

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

        setupDragAndTap(params)
        windowManager?.addView(floatingView, params)
        isShowing = true
    }

    /** Arrastrar mueve la ventana; un toque (sin arrastre) alterna colapsado/expandido. */
    private fun setupDragAndTap(params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isTap = true

        floatingView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isTap = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    if (deltaX * deltaX + deltaY * deltaY > 100) isTap = false
                    params.x = initialX + deltaX
                    params.y = initialY + deltaY
                    windowManager?.updateViewLayout(floatingView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isTap) toggleCollapse()
                    true
                }
                else -> false
            }
        }
    }

    private fun toggleCollapse() {
        if (isCollapsed) expand() else collapse()
    }

    private fun collapse() {
        collapseJob?.cancel()
        isCollapsed = true
        setCollapsedVisibility(true)
    }

    private fun expand() {
        collapseJob?.cancel()
        isCollapsed = false
        setCollapsedVisibility(false)
    }

    private fun setCollapsedVisibility(collapsed: Boolean) {
        val view = floatingView ?: return
        view.findViewById<View>(R.id.expanded_content)?.visibility =
            if (collapsed) View.GONE else View.VISIBLE
        view.findViewById<View>(R.id.collapsed_content)?.visibility =
            if (collapsed) View.VISIBLE else View.GONE
    }

    private fun scheduleAutoCollapse() {
        collapseJob?.cancel()
        collapseJob = serviceScope.launch {
            delay(AUTO_COLLAPSE_DELAY_MS)
            collapse()
        }
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
        val action = decision.action

        updateDecisionBadge(view, action)
        updateReason(view, decision)
        updateNetProfit(view, eval.netProfit)
        view.findViewById<TextView>(R.id.tv_profit_per_km)?.text = "\$%.2f/km".format(eval.profitPerKm)
        updateDestination(view, offer.destination)

        val symbol = if (action == Action.ACCEPT) "✅" else "❌"
        view.findViewById<TextView>(R.id.tv_collapsed)?.text = "$symbol \$%.2f".format(eval.netProfit)

        expand()
        scheduleAutoCollapse()
    }

    private fun updateDecisionBadge(view: View, action: Action) {
        val badge = view.findViewById<TextView>(R.id.tv_decision)
        when (action) {
            Action.ACCEPT -> {
                badge.text = "✅ ${getString(R.string.hud_accepted)}"
                badge.setBackgroundResource(R.drawable.hud_badge_accept)
            }
            Action.WARN -> {
                // Sin acción automática: el conductor decide mirando el HUD.
                badge.text = "⚠️ ${getString(R.string.hud_warned)}"
                badge.setBackgroundResource(R.drawable.hud_badge_warn)
            }
        }
    }

    private fun updateReason(view: View, decision: OfferDecision) {
        val tvReason = view.findViewById<TextView>(R.id.tv_reason)
        val reason = mainReason(decision)
        tvReason.text = reason
        tvReason.visibility = if (reason.isBlank()) View.GONE else View.VISIBLE
    }

    /** Motivo principal de la decisión en una sola línea legible. */
    private fun mainReason(decision: OfferDecision): String =
        when (val reason = HudReasonFormatter.mainReason(decision)) {
            HudReason.Accepted -> getString(R.string.hud_reason_accepted)
            is HudReason.Raw -> reason.text
            is HudReason.Known -> getReasonLabel(reason.reason)
            HudReason.None -> ""
        }

    private fun getReasonLabel(reason: DecisionReason): String = when (reason) {
        DecisionReason.LOW_FARE -> getString(R.string.hud_reason_low_fare)
        DecisionReason.LOW_PER_KM -> getString(R.string.hud_reason_low_per_km)
        DecisionReason.PICKUP_FAR -> getString(R.string.hud_reason_pickup_far)
        DecisionReason.PICKUP_TIME -> getString(R.string.hud_reason_pickup_time)
        DecisionReason.TRIP_LONG -> getString(R.string.hud_reason_trip_long)
        DecisionReason.TRIP_DISTANCE -> getString(R.string.hud_reason_trip_distance)
        DecisionReason.OUT_OF_PERIMETER -> getString(R.string.hud_reason_out_of_perimeter)
        DecisionReason.LOW_RATING -> getString(R.string.hud_reason_low_rating)
        DecisionReason.AUTO_ACCEPT_OFF -> getString(R.string.hud_reason_auto_accept_off)
    }

    private fun updateNetProfit(view: View, netProfit: Double) {
        val tvProfit = view.findViewById<TextView>(R.id.tv_net_profit)
        tvProfit.text = "\$%.2f neta".format(netProfit)
        tvProfit.setTextColor(
            if (netProfit > 0) getColor(R.color.profit_positive)
            else getColor(R.color.profit_negative)
        )
    }

    private fun updateDestination(view: View, destination: String) {
        val tvDest = view.findViewById<TextView>(R.id.tv_destination)
        val text = if (destination.isBlank()) getString(R.string.hud_no_destination)
                   else destination.replace(Regex("\\s+"), " ").trim()
        tvDest.text = "📍 $text"
    }

    private fun removeOverlay() {
        floatingView?.let {
            windowManager?.removeView(it)
            floatingView = null
        }
        isShowing = false
    }
}