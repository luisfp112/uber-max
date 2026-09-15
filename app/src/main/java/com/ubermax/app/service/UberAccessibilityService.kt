package com.ubermax.app.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.ubermax.app.accessibility.ActionExecutor
import com.ubermax.app.accessibility.OfferParser
import com.ubermax.app.data.db.entity.FilterRulesEntity
import com.ubermax.app.data.db.entity.TripLogEntity
import com.ubermax.app.data.db.entity.VehicleConfigEntity
import com.ubermax.app.data.repository.ConfigRepository
import com.ubermax.app.data.repository.TripRepository
import com.ubermax.app.domain.model.Action
import com.ubermax.app.domain.model.EvaluatedOffer
import com.ubermax.app.domain.model.OfferData
import com.ubermax.app.domain.model.OfferDecision
import com.ubermax.app.domain.rules.RuleEngine
import com.ubermax.app.domain.usecase.EvaluateOfferUseCase
import com.ubermax.app.domain.ai.SmartAdvisor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject

/**
 * Servicio de Accesibilidad principal de UberMax.
 *
 * Pipeline:
 * 1. Evento → findUberWindow() → rootNode
 * 2. OfferParser.parseOffer(rootNode) → OfferData
 * 3. EvaluateOfferUseCase → EvaluatedOffer
 * 4. RuleEngine → OfferDecision (ACCEPT / WARN / CANCEL)
 * 5. ActionExecutor:
 *    - ACCEPT → clickAcceptButton() con retry
 *    - CANCEL → clickDismissButton() con retry
 *    - WARN   → no hacer nada, solo mostrar en HUD
 * 6. Emitir decisión al HUD via SharedFlow
 */
@AndroidEntryPoint
class UberAccessibilityService : AccessibilityService() {

    @Inject lateinit var configRepository: ConfigRepository
    @Inject lateinit var tripRepository: TripRepository
    @Inject lateinit var smartAdvisor: SmartAdvisor

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val parser = OfferParser()
    private val ruleEngine = RuleEngine()
    private val evaluator = EvaluateOfferUseCase()
    private lateinit var actionExecutor: ActionExecutor

    // Debounce: evitar procesar la misma oferta múltiples veces
    private var lastProcessedFare = 0.0
    private var lastProcessedTime = 0L
    private val DEBOUNCE_MS = 3000L // 3 segundos entre procesamiento de ofertas

    // Config cacheada
    private var vehicleConfig: VehicleConfigEntity = VehicleConfigEntity()
    private var filterRules: FilterRulesEntity = FilterRulesEntity()
    private var blacklistKeywords: List<String> = emptyList()
    private var blacklistZoneNames: List<String> = emptyList()

    companion object {
        private const val TAG = "UberA11Y"
        private const val UBER_DRIVER_PACKAGE = "com.ubercab.driver"

        // Max reintentos para auto-accept y auto-cancel
        private const val MAX_ACTION_RETRIES = 5
        private const val RETRY_DELAY_MS = 300L

        @Volatile
        var isRunning = false
            private set

        private val _decisionFlow = MutableSharedFlow<OfferDecision>(
            replay = 1,
            extraBufferCapacity = 5
        )
        val decisionFlow = _decisionFlow.asSharedFlow()
    }

    override fun onCreate() {
        super.onCreate()
        actionExecutor = ActionExecutor(this)
        Log.i(TAG, "🟢 UberMax AccessibilityService creado")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        loadConfig()
        Log.i(TAG, "🟢 Servicio de accesibilidad conectado")
    }

    override fun onDestroy() {
        isRunning = false
        serviceScope.cancel()
        super.onDestroy()
        Log.i(TAG, "🔴 Servicio de accesibilidad detenido")
    }

    override fun onInterrupt() {
        Log.w(TAG, "⚠️ Servicio interrumpido")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val eventType = event.eventType
        val packageName = event.packageName?.toString() ?: return

        // Solo procesar eventos de Uber Driver
        if (packageName != UBER_DRIVER_PACKAGE) return

        // Solo eventos de cambio de contenido o ventana
        when (eventType) {
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                // Ok, procesar
            }
            else -> return
        }

        serviceScope.launch {
            processEvent()
        }
    }

    private suspend fun processEvent() {
        // Debounce
        val now = System.currentTimeMillis()
        if (now - lastProcessedTime < DEBOUNCE_MS) return

        // Buscar la ventana de Uber Driver
        val uberRoot = findUberWindow()
        if (uberRoot == null) {
            Log.v(TAG, "No se encontró ventana de Uber Driver")
            return
        }

        try {
            // 1. Parsear la oferta
            val offer = parser.parseOffer(uberRoot) ?: return

            // Debounce: ¿es la misma oferta?
            if (offer.rawFare == lastProcessedFare &&
                now - lastProcessedTime < 10000L) {
                Log.d(TAG, "⏭️ Misma oferta, ignorando")
                return
            }

            lastProcessedFare = offer.rawFare
            lastProcessedTime = now

            // 2. Recargar configuración (puede haber cambiado)
            loadConfig()

            // 3. Evaluar económicamente
            val evaluated = evaluator.evaluate(offer, vehicleConfig)

            // 4. Aplicar reglas del conductor
            var decision = ruleEngine.evaluate(
                evaluated = evaluated,
                rules = filterRules,
                blacklistKeywords = blacklistKeywords,
                blacklistZones = blacklistZoneNames
            )

            // 4.5 IA — Recomendación
            if (filterRules.aiEnabled) {
                decision = smartAdvisor.analyzeOffer(decision)
            }

            // 5. Emitir decisión al HUD
            _decisionFlow.emit(decision)

            // 6. Ejecutar acción
            when (decision.action) {
                Action.ACCEPT -> {
                    Log.i(TAG, "✅ AUTO-ACEPTANDO oferta: \$${offer.rawFare}")
                    executeWithRetry("ACCEPT") {
                        actionExecutor.clickAcceptButton(uberRoot)
                    }
                }
                Action.CANCEL -> {
                    Log.i(TAG, "🚫 CANCELANDO oferta (BLACKLIST): ${decision.failedFilters}")
                    executeWithRetry("CANCEL") {
                        actionExecutor.clickDismissButton(uberRoot)
                    }
                }
                Action.WARN -> {
                    Log.i(TAG, "⚠️ ADVERTENCIA — no recomendable: ${decision.failedFilters}")
                    // No hacer nada — el conductor ve el aviso en el HUD y decide
                }
                Action.IGNORE -> {
                    Log.d(TAG, "⏳ IGNORANDO — dejando correr temporizador")
                }
            }

            // 7. Guardar en historial
            logTrip(offer, evaluated, decision)

        } catch (e: Exception) {
            Log.e(TAG, "Error procesando evento: ${e.message}", e)
        } finally {
            uberRoot.recycle()
        }
    }

    /**
     * Ejecuta una acción con reintentos.
     * Si el primer intento falla, espera RETRY_DELAY_MS y vuelve a intentar
     * hasta MAX_ACTION_RETRIES veces.
     */
    private suspend fun executeWithRetry(actionName: String, action: suspend () -> Boolean) {
        for (attempt in 1..MAX_ACTION_RETRIES) {
            val success = try {
                action()
            } catch (e: Exception) {
                Log.e(TAG, "Error en $actionName intento $attempt: ${e.message}")
                false
            }

            if (success) {
                Log.i(TAG, "✅ $actionName exitoso en intento $attempt")
                return
            }

            if (attempt < MAX_ACTION_RETRIES) {
                Log.d(TAG, "⏳ $actionName intento $attempt fallido, reintentando en ${RETRY_DELAY_MS}ms...")
                delay(RETRY_DELAY_MS)
            }
        }
        Log.e(TAG, "❌ $actionName falló después de $MAX_ACTION_RETRIES intentos")
    }

    /**
     * Busca la ventana de Uber Driver usando getWindows().
     * Esto permite encontrar Uber incluso cuando está como overlay sobre Waze/Maps.
     */
    private fun findUberWindow(): AccessibilityNodeInfo? {
        // Primero intentar getWindows() para multi-ventana
        try {
            val windows = windows
            for (window in windows) {
                val root = window.root ?: continue
                val pkg = root.packageName?.toString() ?: ""
                if (pkg == UBER_DRIVER_PACKAGE) {
                    return root
                }
                root.recycle()
            }
        } catch (e: Exception) {
            Log.d(TAG, "getWindows() no disponible: ${e.message}")
        }

        // Fallback: rootInActiveWindow
        val root = rootInActiveWindow ?: return null
        val pkg = root.packageName?.toString() ?: ""
        if (pkg == UBER_DRIVER_PACKAGE) {
            return root
        }
        root.recycle()
        return null
    }

    private fun loadConfig() {
        serviceScope.launch(Dispatchers.IO) {
            try {
                vehicleConfig = configRepository.getVehicleConfig()
                filterRules = configRepository.getFilterRules()
                blacklistKeywords = configRepository.getAllBlacklistKeywords()
                blacklistZoneNames = configRepository.getAllBlacklistZoneNames()
            } catch (e: Exception) {
                Log.e(TAG, "Error cargando config: ${e.message}")
            }
        }
    }

    private suspend fun logTrip(offer: OfferData, evaluated: EvaluatedOffer, decision: OfferDecision) {
        withContext(Dispatchers.IO) {
            try {
                tripRepository.logDecision(decision)
            } catch (e: Exception) {
                Log.e(TAG, "Error guardando trip: ${e.message}")
            }
        }
    }
}
