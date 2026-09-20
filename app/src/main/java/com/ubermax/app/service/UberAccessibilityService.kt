package com.ubermax.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import com.ubermax.app.accessibility.ActionExecutor
import com.ubermax.app.accessibility.OfferParser
import com.ubermax.app.data.db.entity.BlacklistZoneEntity
import com.ubermax.app.data.db.entity.FilterRulesEntity
import com.ubermax.app.data.db.entity.AppSettingsEntity
import com.ubermax.app.data.db.entity.TripLogEntity
import com.ubermax.app.data.db.entity.VehicleConfigEntity
import com.ubermax.app.data.repository.ConfigRepository
import com.ubermax.app.data.repository.TripRepository
import com.ubermax.app.domain.model.Action
import com.ubermax.app.domain.model.OfferData
import com.ubermax.app.domain.model.OfferDecision
import com.ubermax.app.domain.model.VoiceCommand
import com.ubermax.app.domain.rules.RuleEngine
import com.ubermax.app.domain.usecase.EvaluateOfferUseCase
import com.ubermax.app.domain.ai.SmartAdvisor
import com.ubermax.app.util.DecisionNotifier
import com.ubermax.app.util.OfferFingerprint
import com.ubermax.app.util.VoiceCommandBus
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
    private val notifier by lazy { DecisionNotifier(this) }

    // Debounce + dedup por identidad de oferta (tarifa + pickup + destino)
    private var lastOfferFingerprint = ""
    private var lastProcessedTime = 0L

    // Resolución de resultados: id del último WARN/IGNORE que quedó sin acción
    // (marcará TIMEOUT cuando llegue otra oferta o MANUAL_* vía voz).
    private var lastPendingTimeoutId = 0L

    // Config cacheada
    private var vehicleConfig: VehicleConfigEntity = VehicleConfigEntity()
    private var filterRules: FilterRulesEntity = FilterRulesEntity()
    private var blacklistKeywords: List<String> = emptyList()
    private var blacklistZones: List<BlacklistZoneEntity> = emptyList()
    private var appSettings: AppSettingsEntity = AppSettingsEntity()

    // False hasta que la configuración real del conductor se haya cargado al
    // menos una vez (no decidir con puros defaults en la primera oferta).
    private var configLoaded = false

    companion object {
        private const val TAG = "UberA11Y"
        private const val UBER_DRIVER_PACKAGE = "com.ubercab.driver"

        // Debounce: 3 segundos entre procesamientos de ofertas
        private const val DEBOUNCE_MS = 3_000L

        // Dedup por identidad de oferta: máxima antigüedad para re-ignorar la misma
        private const val DEDUP_MS = 10_000L

        // Max reintentos para auto-accept y auto-cancel
        private const val MAX_ACTION_RETRIES = 5
        private const val RETRY_DELAY_MS = 300L

        // Acción de simulación (SOLO debug, entregada vía SimulateOfferReceiver)
        const val ACTION_SIMULATE_OFFER = "com.ubermax.app.SIMULATE_OFFER"
        const val EXTRA_OFFER_INDEX = "offer_index"

        // Ofertas de prueba realistas para ejercitar el pipeline en el HUD.
        // Índice 0: rentable → ACCEPT · 1: tarifa baja → WARN · 2: viaje largo
        private val SIMULATED_OFFERS: List<List<String>> = listOf(
            listOf(
                "UberX",
                "\$10.00",
                "★ 4.73 (42)",
                "A 3 min (1.0 km)",
                "Blvr. del Ejercito Nacional, TERMINAL DE BUSES",
                "Viaje: 30 min (12.0 km)",
                "C. L-7, CIUDAD MERLOT - SAN SALVADOR",
                "Aceptar"
            ),
            listOf(
                "UberX",
                "\$1.20",
                "⭐ 4.73 (42)",
                "A 3 min (1.0 km)",
                "Parque Central, AMBATO",
                "Viaje: 20 min (8.0 km)",
                "Calle 12 y Av. Bolívar, CIUDAD MERLOT",
                "Postularse"
            ),
            listOf(
                "Comfort",
                "\$6.50",
                "★ 4.2 (120)",
                "A 12 min (5.0 km)",
                "Urdesa Norte, GUAYAQUIL",
                "Viaje: 45 min (25.0 km)",
                "Vía a la Costa, Km 12, GUAYAQUIL",
                "Aceptar"
            )
        )

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
        observeVoiceCommands()
        Log.i(TAG, "🟢 UberMax AccessibilityService creado")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        serviceScope.launch { loadConfig() }
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

    /** Escucha comandos de voz y los traduce a taps sobre la oferta visible. */
    private fun observeVoiceCommands() {
        serviceScope.launch {
            VoiceCommandBus.commands.collect { command ->
                handleVoiceCommand(command)
            }
        }
    }

    private suspend fun handleVoiceCommand(command: VoiceCommand) {
        if (!appSettings.voiceControlEnabled) return
        if (command == VoiceCommand.NEXT) {
            Log.d(TAG, "🎙️ Voz: esperar")
            return
        }
        val root = findUberWindow() ?: return
        try {
            when (command) {
                VoiceCommand.ACCEPT -> {
                    Log.i(TAG, "🎙️ Voz: aceptar")
                    val applied = executeWithRetry("VOICE_ACCEPT") { actionExecutor.clickAcceptButton(root) }
                    recordVoiceOverride(applied, "MANUAL_ACCEPT")
                }
                VoiceCommand.REJECT -> {
                    Log.i(TAG, "🎙️ Voz: rechazar")
                    val applied = executeWithRetry("VOICE_REJECT") { actionExecutor.clickDismissButton(root) }
                    recordVoiceOverride(applied, "MANUAL_REJECT")
                }
                VoiceCommand.NEXT, VoiceCommand.NONE -> Unit
            }
        } finally {
            root.recycle()
        }
    }

    /** Registra que el conductor overrideó (por voz) la última oferta sin acción. */
    private suspend fun recordVoiceOverride(applied: Boolean, resolution: String) {
        val tripId = lastPendingTimeoutId
        if (tripId == 0L) return
        lastPendingTimeoutId = 0L
        tripRepository.updateResolution(
            tripId,
            if (applied) resolution else "TAP_FAILED"
        )
    }

    /** Si había una oferta WARN/IGNORE sin resolver y llegó otra, la primera expiró. */
    private suspend fun flushPendingTimeout() {
        val tripId = lastPendingTimeoutId
        if (tripId == 0L) return
        lastPendingTimeoutId = 0L
        tripRepository.updateResolution(tripId, "TIMEOUT")
    }

    /** Arranca o detiene el servicio de voz según el ajuste del conductor. */
    private fun syncVoiceService(settings: AppSettingsEntity) {
        val shouldRun = settings.voiceControlEnabled
        if (shouldRun == VoiceCommandService.isRunning) return
        try {
            val intent = Intent(this, VoiceCommandService::class.java).apply {
                action = if (shouldRun) VoiceCommandService.ACTION_START
                else VoiceCommandService.ACTION_STOP
            }
            if (shouldRun) {
                ContextCompat.startForegroundService(this, intent)
            } else {
                startService(intent)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "No se pudo cambiar el servicio de voz: ${t.message}")
        }
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
            handleParsedOffer(offer, uberRoot)
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            Log.e(TAG, "Error procesando evento: ${e.message}", e)
        } finally {
            uberRoot.recycle()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Hook de simulación (debug): un receptor inyecta una oferta de prueba.
        if (intent?.action == ACTION_SIMULATE_OFFER) {
            val index = intent.getIntExtra(EXTRA_OFFER_INDEX, 0)
            serviceScope.launch { simulateOffer(index) }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    /**
     * Simula una oferta de prueba inyectada (solo debug / adb).
     * Recorre el MISMO pipeline que una oferta real salvo que nunca habrá
     * ventana de Uber Driver: en ACCEPT/CANCEL los taps se saltan (TAP_FAILED).
     * En modo simulación (dry-run) tampoco hay taps, igual que en producción.
     */
    private suspend fun simulateOffer(index: Int) {
        val texts = SIMULATED_OFFERS.getOrNull(index)
        if (texts == null) {
            Log.w(TAG, "🧪 Índice de oferta simulado inválido: $index")
            return
        }
        val offer = parser.parseFromTextNodes(
            texts.map { OfferParser.TextNode(text = it, contentDesc = "") }
        )
        if (offer == null) {
            Log.w(TAG, "🧪 Oferta simulada no se pudo parsear")
            return
        }
        Log.i(TAG, "🧪 SIMULANDO oferta de prueba $index: \$${offer.rawFare} → ${offer.destination}")
        handleParsedOffer(offer, null)
    }

    /**
     * Pipeline completo tras tener la oferta parseada: dedup por identidad,
     * carga de config, evaluación, reglas, IA, emisión al HUD, feedback,
     * acción (tap) y registro en el historial con resolución real.
     *
     * @param uberRoot ventana de Uber Driver para los taps; null en simulación.
     */
    private suspend fun handleParsedOffer(offer: OfferData, uberRoot: AccessibilityNodeInfo?) {
        val now = System.currentTimeMillis()

        // Dedup por identidad: ¿es la misma oferta (tarifa + pickup + destino)?
        val fingerprint = OfferFingerprint.of(offer)
        if (fingerprint == lastOfferFingerprint && now - lastProcessedTime < DEDUP_MS) {
            Log.d(TAG, "⏭️ Misma oferta, ignorando")
            return
        }
        lastOfferFingerprint = fingerprint
        lastProcessedTime = now

        // Si la última oferta (WARN/IGNORE) quedó sin acción y llegó otra
        // distinta, la anterior expiró (el conductor no actuó).
        flushPendingTimeout()

        try {
            // 2. Recargar configuración (puede haber cambiado)
            if (!loadConfig()) {
                Log.d(TAG, "⏳ Config aún no cargada — no se decide con defaults")
                return
            }

            // 3. Evaluar económicamente
            val evaluated = evaluator.evaluate(
                offer = offer,
                config = vehicleConfig,
                deadheadThresholdKm = filterRules.deadheadThresholdKm,
                deadheadReturnFactor = filterRules.deadheadReturnFactor
            )

            // 4. Aplicar reglas del conductor
            var decision = ruleEngine.evaluate(
                evaluated = evaluated,
                rules = filterRules,
                blacklistKeywords = blacklistKeywords,
                blacklistZones = blacklistZones
            )

            // 4.5 IA — Recomendación
            if (filterRules.aiEnabled) {
                val referenceProfitPerKm =
                    if (appSettings.aiGoodProfitPerKm > 0.0) appSettings.aiGoodProfitPerKm
                    else filterRules.minProfitPerKm
                decision = smartAdvisor.analyzeOffer(decision, referenceProfitPerKm)
            }

            // 4.6 Modo simulación: marca la decisión pero no ejecuta taps
            val simulated = appSettings.dryRunEnabled
            val finalDecision = if (simulated) decision.copy(simulated = true) else decision

            // 5. Emitir decisión al HUD
            _decisionFlow.emit(finalDecision)

            // 5.5 Feedback inmediato (notificación / vibración / sonido)
            notifier.feedback(
                decision = finalDecision,
                notify = appSettings.notifyDecisions,
                vibrate = appSettings.vibrateOnDecision,
                sound = appSettings.soundOnDecision
            )

            // 6. Ejecutar acción (salvo en modo simulación)
            var actionApplied = false
            if (simulated) {
                Log.i(TAG, "🧪 SIMULACIÓN — no se ejecuta acción (${decision.action})")
            } else {
                when (decision.action) {
                    Action.ACCEPT -> {
                        Log.i(TAG, "✅ AUTO-ACEPTANDO oferta: \$${offer.rawFare}")
                        actionApplied = if (uberRoot != null) {
                            executeWithRetry("ACCEPT") {
                                actionExecutor.clickAcceptButton(uberRoot)
                            }
                        } else {
                            Log.w(TAG, "⚠️ Sin ventana real de Uber (simulación) — TAP_FAILED")
                            false
                        }
                    }
                    Action.CANCEL -> {
                        Log.i(TAG, "🚫 CANCELANDO oferta (BLACKLIST): ${decision.failedFilters}")
                        actionApplied = if (uberRoot != null) {
                            executeWithRetry("CANCEL") {
                                actionExecutor.clickDismissButton(uberRoot)
                            }
                        } else {
                            Log.w(TAG, "⚠️ Sin ventana real de Uber (simulación) — TAP_FAILED")
                            false
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
            }

            // 7. Guardar en historial (decisión + resultado real de la acción)
            val resolution = when {
                simulated -> "UNKNOWN"
                decision.action == Action.WARN || decision.action == Action.IGNORE -> "UNKNOWN"
                actionApplied && decision.action == Action.ACCEPT -> "ASSIGNED"
                actionApplied && decision.action == Action.CANCEL -> "REJECTED"
                else -> "TAP_FAILED"
            }
            val tripId = logTrip(finalDecision, actionApplied, resolution)

            // Si la oferta no tuvo acción automática (WARN/IGNORE), queda en
            // espera: a la próxima oferta distinta se marcará TIMEOUT, y si el
            // conductor la acepta/rechaza por voz se marcará MANUAL_*.
            if (decision.action == Action.WARN || decision.action == Action.IGNORE) {
                lastPendingTimeoutId = tripId
            }

        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            Log.e(TAG, "Error procesando oferta: ${e.message}", e)
        }
    }

    /**
     * Ejecuta una acción con reintentos.
     * Si el primer intento falla, espera RETRY_DELAY_MS y vuelve a intentar
     * hasta MAX_ACTION_RETRIES veces.
     */
    private suspend fun executeWithRetry(actionName: String, action: suspend () -> Boolean): Boolean {
        for (attempt in 1..MAX_ACTION_RETRIES) {
            val success = try {
                action()
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                Log.e(TAG, "Error en $actionName intento $attempt: ${e.message}")
                false
            }

            if (success) {
                Log.i(TAG, "✅ $actionName exitoso en intento $attempt")
                return true
            }

            if (attempt < MAX_ACTION_RETRIES) {
                Log.d(TAG, "⏳ $actionName intento $attempt fallido, reintentando en ${RETRY_DELAY_MS}ms...")
                delay(RETRY_DELAY_MS)
            }
        }
        Log.e(TAG, "❌ $actionName falló después de $MAX_ACTION_RETRIES intentos")
        return false
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

/**
     * Carga la configuración completa de forma síncrona (suspend).
     *
     * @return true si hay una configuración cargada (la primera vez, o una
     *   recarga exitosa); false si nunca se ha podido cargar. El pipeline no
     *   debe evaluar ofertas con puros defaults si nunca llegó la config real.
     */
    private suspend fun loadConfig(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Se lee TODO primero y solo se publica si carga completa...
            val vc = configRepository.getVehicleConfig()
            val fr = configRepository.getFilterRules()
            val keywords = configRepository.getAllMergedBlacklistKeywords()
            val zones = configRepository.getAllBlacklistZones()
            val settings = configRepository.getAppSettings()

            // ...para que un fallo a mitad de camino no mezcle config nueva con vieja.
            vehicleConfig = vc
            filterRules = fr
            blacklistKeywords = keywords
            blacklistZones = zones
            appSettings = settings
            configLoaded = true

            // Sincronizar servicio de voz con el ajuste actual
            syncVoiceService(settings)
            true
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            // Cache fallback: las variables de clase conservan la ÚLTIMA configuración
            // válida (o los valores por defecto si nunca se cargó), así el pipeline
            // sigue decidiendo con los últimos valores conocidos del conductor.
            Log.w(TAG, "Error recargando config; se mantiene la última válida: ${e.message}")
            configLoaded
        }
    }

    private suspend fun logTrip(
        decision: OfferDecision,
        actionApplied: Boolean = false,
        resolution: String = "UNKNOWN"
    ): Long = withContext(Dispatchers.IO) {
        try {
            tripRepository.logDecision(
                decision = decision,
                actionApplied = actionApplied,
                resolution = resolution
            )
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando trip: ${e.message}")
            0L
        }
    }
}
