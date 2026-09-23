package com.ubermax.app.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.ubermax.app.accessibility.ActionExecutor
import com.ubermax.app.accessibility.OfferParser
import com.ubermax.app.data.db.entity.AppSettingsEntity
import com.ubermax.app.data.db.entity.FilterRulesEntity
import com.ubermax.app.data.db.entity.VehicleConfigEntity
import com.ubermax.app.data.geocoding.LocationIqGeocoder
import com.ubermax.app.data.repository.ConfigRepository
import com.ubermax.app.domain.geo.GeoJsonZones
import com.ubermax.app.domain.geo.UrbanBoundary
import com.ubermax.app.domain.model.Action
import com.ubermax.app.domain.model.OfferData
import com.ubermax.app.domain.model.OfferDecision
import com.ubermax.app.domain.rules.RuleEngine
import com.ubermax.app.domain.usecase.EvaluateOfferUseCase
import com.ubermax.app.util.DecisionNotifier
import com.ubermax.app.util.OfferFingerprint
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
 * 3. (opcional) LocationIqGeocoder → coordenadas del destino
 * 4. EvaluateOfferUseCase → EvaluatedOffer
 * 5. RuleEngine → OfferDecision (ACCEPT / WARN)
 * 6. ActionExecutor:
 *    - ACCEPT → clickAcceptButton() con retry
 *    - WARN   → no hacer nada, solo mostrar en HUD
 * 7. Emitir decisión al HUD vía SharedFlow + anuncio de voz (TTS opcional)
 */
@AndroidEntryPoint
class UberAccessibilityService : AccessibilityService() {

    @Inject lateinit var configRepository: ConfigRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val parser = OfferParser()
    private val ruleEngine = RuleEngine()
    private val evaluator = EvaluateOfferUseCase()
    private lateinit var actionExecutor: ActionExecutor
    private val notifier by lazy { DecisionNotifier(this) }

    // Debounce + dedup por identidad de oferta (tarifa + pickup + destino)
    private var lastOfferFingerprint = ""
    private var lastProcessedTime = 0L

    // Config cacheada
    private var vehicleConfig: VehicleConfigEntity = VehicleConfigEntity()
    private var filterRules: FilterRulesEntity = FilterRulesEntity()
    private var appSettings: AppSettingsEntity = AppSettingsEntity()

    // Zonas permitidas del conductor (assets/zonas-permitidasv3.geojson).
    // Se parsean una sola vez; si el asset falta o no es válido, se deja null
    // y el filtro geográfico se omite (fail-open).
    private var allowedZones: GeoJsonZones? = null
    private var allowedZonesLoaded = false

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

        // Max reintentos para auto-accept
        private const val MAX_ACTION_RETRIES = 5
        private const val RETRY_DELAY_MS = 300L

        @Volatile
        var isRunning = false
            private set

        // Instancia activa del servicio (debug). Usada SOLO por el simulador de
        // tarjetas del source set debug, que inyecta una oferta en el pipeline real.
        @Volatile
        private var activeService: UberAccessibilityService? = null

        private val _decisionFlow = MutableSharedFlow<OfferDecision>(
            replay = 1,
            extraBufferCapacity = 5
        )
        val decisionFlow = _decisionFlow.asSharedFlow()

        /**
         * [DEBUG] Inyecta una oferta simulada en el pipeline real (parse → geocode →
         * evaluación → reglas → HUD/TTS). Sin ventana de Uber: un ACCEPT solo se
         * registrará en logs como "sin ventana real", sin pulsar nada en pantalla.
         */
        @JvmStatic
        fun simulateOffer(offer: OfferData) {
            val service = activeService ?: return
            service.serviceScope.launch {
                service.handleParsedOffer(offer, null)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        actionExecutor = ActionExecutor(this)
        Log.i(TAG, "🟢 UberMax AccessibilityService creado")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        activeService = this
        serviceScope.launch { loadConfig() }
        Log.i(TAG, "🟢 Servicio de accesibilidad conectado")
    }

    override fun onDestroy() {
        isRunning = false
        activeService = null
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
            handleParsedOffer(offer, uberRoot)
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            Log.e(TAG, "Error procesando evento: ${e.message}", e)
        } finally {
            uberRoot.recycle()
        }
    }

    /**
     * Pipeline completo tras tener la oferta parseada: dedup por identidad,
     * carga de config, geolocalización (opcional), evaluación, reglas, emisión
     * al HUD, feedback de voz y acción (tap en ACCEPT).
     *
     * @param uberRoot ventana de Uber Driver para los taps.
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

        try {
            // 2. Recargar configuración (puede haber cambiado)
            if (!loadConfig()) {
                Log.d(TAG, "⏳ Config aún no cargada — no se decide con defaults")
                return
            }

            // 2.5 Geolocalizar RECOGIDA y DESTINO si el conductor activó las zonas
            // permitidas (el perímetro protege una geografía completa, no solo el
            // destino). Geocoding oportunista: si la tarjeta ya trae coordenadas se
            // usan esas. Fail-open: si no hay key, no hay red o un punto no se
            // verifica con LocationIQ, ese punto queda sin coordenadas y RuleEngine
            // lo traduce a un WARN "no confirmado" (jamás auto-acepta la zona).
            val geoActive = appSettings.geoCheckEnabled && appSettings.geoApiKey.isNotBlank()
            var offer = offer
            var pickupLocated = true
            var destinationLocated = true
            if (geoActive) {
                val apiKey = appSettings.geoApiKey
                if (offer.pickupLatLng == null) {
                    val coords = LocationIqGeocoder.geocode(offer.pickupAddress, apiKey)
                    if (coords != null) {
                        offer = offer.copy(pickupLatLng = coords)
                        Log.i(TAG, "📥 Recogida geolocalizada: ${coords.first}, ${coords.second}")
                    }
                }
                if (offer.destinationLatLng == null) {
                    val coords = LocationIqGeocoder.geocode(offer.destination, apiKey)
                    if (coords != null) {
                        offer = offer.copy(destinationLatLng = coords)
                        Log.i(TAG, "📍 Destino geolocalizado: ${coords.first}, ${coords.second}")
                    }
                }
                pickupLocated = offer.pickupLatLng != null
                destinationLocated = offer.destinationLatLng != null
            }

            // 3. Evaluar económicamente
            val evaluated = evaluator.evaluate(offer = offer, config = vehicleConfig)

            // 3.5 Zona permitida de destino: los polígonos del GeoJSON del
            // conductor. Sin GeoJSON válido → null (fail-open, se omite el filtro).
            val boundary: UrbanBoundary? = if (geoActive) allowedZones else null

            // 4. Aplicar reglas del conductor
            val decision = ruleEngine.evaluate(
                evaluated = evaluated,
                rules = filterRules,
                avgSpeedKmh = vehicleConfig.avgSpeedKmh,
                boundary = boundary
            )

            // 5. Emitir decisión al HUD
            _decisionFlow.emit(decision)

            // 5.5 Feedback por voz (TTS, opt-in). Si la recogida o el destino no se
            // pudieron geolocalizar, la voz los anuncia antes de la decisión
            // ("Recogida no ubicada" / "Destino no ubicado").
            notifier.feedback(
                decision = decision,
                speak = appSettings.voiceAnnounceEnabled,
                pickupLocated = pickupLocated,
                destinationLocated = destinationLocated
            )

            // 6. Ejecutar acción
            when (decision.action) {
                Action.ACCEPT -> {
                    Log.i(TAG, "✅ AUTO-ACEPTANDO oferta: \$${offer.rawFare}")
                    if (uberRoot != null) {
                        executeWithRetry("ACCEPT") {
                            actionExecutor.clickAcceptButton(uberRoot)
                        }
                    } else {
                        Log.w(TAG, "⚠️ Sin ventana real de Uber — TAP_FAILED")
                    }
                }
                Action.WARN -> {
                    Log.i(TAG, "⚠️ ADVERTENCIA — no recomendable: ${decision.failedFilters}")
                    // No hacer nada — el conductor ve el aviso en el HUD y decide
                }
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
            val settings = configRepository.getAppSettings()

            // ...para que un fallo a mitad de camino no mezcle config nueva con vieja.
            vehicleConfig = vc
            filterRules = fr
            appSettings = settings
            configLoaded = true

            // El executor se crea (o recrea) con el ajuste de taps humanizados
            actionExecutor = ActionExecutor(this@UberAccessibilityService, appSettings.humanTapsEnabled)

            // Cargar las zonas permitidas del GeoJSON una sola vez
            loadAllowedZonesOnce()
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

    /**
     * Carga una sola vez las zonas permitidas desde `assets/zonas-permitidasv3.geojson`.
     * Si el asset falta o no es un GeoJSON válido, [allowedZones] queda null y el
     * filtro geográfico se omite (fail-open, nunca bloquea por esto).
     * Debe llamarse desde un contexto IO.
     */
    private fun loadAllowedZonesOnce() {
        if (allowedZonesLoaded) return
        allowedZonesLoaded = true
        allowedZones = try {
            val raw = assets.open(GeoJsonZones.ASSET_NAME)
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            GeoJsonZones.parse(raw).also {
                if (it != null) Log.i(TAG, "🗺️ Zonas permitidas cargadas (${GeoJsonZones.ASSET_NAME})")
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo cargar ${GeoJsonZones.ASSET_NAME}; el filtro geográfico se omite. ${e.message}")
            null
        }
    }
}