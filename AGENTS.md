# AGENTS.md — Guia para Agentes (UberMax)

## Visión General

**UberMax** es una app Android que automatiza aceptar/rechazar ofertas de Uber Driver
mediante un `AccessibilityService`, evaluando rentabilidad en tiempo real.
Clean Architecture + MVVM + Hilt. `applicationId` / `namespace` = `com.ubermax.app`.

## Arquitectura real (verificada)

- `ui/`: Activities + ViewModels (StateFlow) · `domain/`: reglas, IA, use cases, modelos ·
  `data/`: Room (DAO + Entity) + repositorios + geocoding · `accessibility/`: parser, taps,
  geometría · `service/`: AccessibilityService, foreground services, HUD · `util/`: texto,
  regex, logging, CSV.
- **La separación de capas NO es estricta.** `domain/` importa `com.ubermax.app.data.db.entity.*`
  (entidades Room) en `EvaluateOfferUseCase`, `RuleEngine`, `SmartAdvisor` y `domain/port/TripHistorySource`.
  Solo `domain/model/*` es Kotlin puro. No intentes "purificar" `domain/` sin que se pida.
- `SmartAdvisor` no depende del repositorio: depende del puerto `domain/port/TripHistorySource`,
  bindeado a `TripRepository` en `di/DomainModule` (`@Binds`). Los tests usan fakes de ese puerto;
  **Mockito fue eliminado del proyecto** (no lo reintroduzcas).
- Directorios vacíos (placeholders): `data/datastore`, `data/worker`, `screen`, `ui/overlay`,
  `ui/theme`. La dependencia DataStore existe pero **no se usa**; la config vive en Room.

## Comandos

```bash
./gradlew assembleDebug          # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # tests JVM (sin dispositivo)
./gradlew lintDebug              # FALLA si hay errores de lint (no hay baseline)
```

- Un solo test: `./gradlew testDebugUnitTest --tests "com.ubermax.app.domain.rules.RuleEngineTest"`
- CI (`.github/workflows/ci.yml`) corre: `./gradlew testDebugUnitTest assembleDebug lintDebug --no-daemon --stacktrace`
- **Gotcha de entorno:** en esta máquina el daemon de Gradle se detiene solo y KSP lanza
  errores internos esporádicos. Reintentar con `--no-daemon` normalmente lo resuelve.

## Testing

- Todo **JVM puro**: sin dispositivo, sin Robolectric, sin Mockito. `app/src/androidTest/` está vacío.
- Para logs en lógica pura usa `util/Logs` (no `android.util.Log`): en la JVM de test, `android.jar`
  mockable lanza `RuntimeException("not mocked")`.
- Cualquier comportamiento decisional nuevo requiere tests. Suite actual: parser, ActionExecutor,
  VehicleConfig, RuleEngine, PolygonGeometry, SmartAdvisor (+ config), PipelineIntegration, CsvExporter,
  ReasonMapper, HudReasonFormatter, PermissionStatus, ConfigBackupManager, VoiceCommandParser,
  HistoryViewModel.

## Flujo de una Oferta

```
Evento de Uber Driver (com.ubercab.driver)
  -> UberAccessibilityService.processEvent()   # debounce 3s + dedup por tarifa 10s
    -> OfferParser.parseOffer(rootNode)        -> OfferData
    -> EvaluateOfferUseCase.evaluate()         -> EvaluatedOffer
    -> RuleEngine.evaluate()                   -> OfferDecision
    -> SmartAdvisor.analyzeOffer()             -> OfferDecision enriquecido
    -> dryRunEnabled? marca simulated=true, omite taps y notifica
    -> ActionExecutor.clickAccept/Dismiss()    -> taps sobre la UI
    -> TripRepository.logDecision()            -> Room DB
    -> decisionFlow.emit()                     -> HUD flotante
    -> DecisionNotifier.feedback()             -> notificación + vibración/sonido
  VoiceCommandBus -> UberAccessibilityService.handleVoiceCommand() -> taps
```

Notas de features nuevas:
- **Modo simulación** (`AppSettingsEntity.dryRunEnabled`): evalúa, registra y muestra el HUD con
  badge "SIMULACIÓN", pero nunca pulsa en Uber.
- **Comandos de voz** (opt-in `voiceControlEnabled`, requiere READ_AUDIO): `VoiceCommandService`
  publica en `VoiceCommandBus`; el parser puro es `VoiceCommandParser`.
- **Auto-arranque**: `BootReceiver` (al reiniciar) y `CarConnectionReceiver` (Bluetooth de audio),
  ambos condicionados por `AppSettingsEntity`.
- **Backup**: `ConfigBackupManager` (JSON puro) + export/import en Ajustes vía FileProvider/OpenDocument.

## Reglas Clave

1. **No romper el parser**: `OfferParser.parseFromTextNodes()` es 100% JVM puro y está cubierto por tests.
2. **TapGeometry es universal**: nada de coordenadas absolutas ni calibración por modelo; todo es
   relativo a `RectSpec` o proporciones de pantalla.
3. **Normaliza antes de comparar**: cualquier match de texto (lista negra, botones) pasa por
   `TextNormalizer.normalize()` para ignorar mayúsculas, tildes y símbolos.
4. **Deadhead no es distancia**: un viaje largo no se rechaza por km; solo se reporta cuando la
   rentabilidad final (con vuelta vacía incluida) cae bajo los mínimos. Umbral configurable
   (`FilterRulesEntity.deadheadThresholdKm`, default 8.0).
5. **Una sola métrica principal decide**: `RuleEngine` usa `FilterRulesEntity.primaryMetric`
   (`PER_HOUR` default / `PER_KM`, ver `ProfitMetric`). La otra métrica se **ignora**. `minFare`
   y `minNetProfit` son guardas absolutas opcionales (default 0 = off). No volver a evaluar
   `$/km` y `$/hr` juntos: era contradictorio.
6. **No dupliques debounce/dedup**: el servicio ya trae 3s entre eventos y 10s por misma tarifa.
7. **Foreground services**: `MonitorForegroundService` y `FloatingWindowService` lo son para no ser
   matados bajo memoria. El WakeLock se adquiere con timeout (4h) y se renueva cada 55 min.
8. **Lint `MissingPermission`**: el detector no sigue métodos wrapper (p. ej. `hasLocationPermission()`).
   Si usas una API con permiso, haz el `checkSelfPermission` inline o envuélvelo en try/catch.

## Mapa (OSMDroid — sin API key)

- `BlacklistMapActivity` usa `org.osmdroid.views.MapView` con id `map_fragment` (**no es un
  `SupportMapFragment`**) y tile source `TileSourceFactory.MAPNIK` en **tema claro**.
- Ubicación con `FusedLocationProviderClient` (`play-services-location`). Nunca se usa el SDK de Google Maps.
- `NominatimGeocoder` (geocodificación inversa) es online y respeta la política de uso:
  rate limit 1100 ms, grid de 200 m, máx. 40 puntos por polígono, User-Agent obligatorio.
- API de osmdroid 6.1.20 (aprendida a golpes): `Polygon` usa `strokeColor`/`strokeWidth`
  (no `outline*`); **no existen** `IconFactory` ni `Marker.relatedObject` (asocia zona↔marker con un `Map`).

## Room DB y Migraciones

- `AppDatabase` en **versión 9**. Migraciones explícitas `MIGRATION_5_6`, `MIGRATION_6_7`,
  `MIGRATION_7_8` (crea `app_settings`) y `MIGRATION_8_9` (añade `filter_rules.primary_metric`).
  **No hay `fallbackToDestructiveMigration()`** (la nota antigua era falsa). Al cambiar el esquema:
  sube la versión y añade un `Migration` con DDL manual.
- `exportSchema = false` (aunque `room.schemaLocation` está declarado): `app/schemas/` solo contiene
  un `1.json` obsoleto; no confíes en schemas exportados.
- `blacklist_zone` es v2: polígonos en `polygon_json` + keywords en `extracted_keywords_json`.
  Las columnas legacy (`latitude`/`longitude`/`radius_meters`) se convierten a cuadrado al leer vía
  `PolygonGeometry.circleToSquarePolygon()`; la comparación usa point-in-polygon + texto normalizado.

## Tablas

- `trip_log`: historial de ofertas evaluadas (métricas económicas + decisión)
- `vehicle_config`: singleton (`id = 1`) con costo/km, consumo, precio combustible
- `filter_rules`: singleton (`id = 1`) con filtros y `deadhead_threshold_km`
- `blacklist_entry`: keywords de texto
- `blacklist_zone`: zonas (polígonos) con keywords extraídas
- `app_settings`: singleton (`id = 1`) con toggles (dry-run, auto-arranque, notificaciones, voz, IA $/km)

## Archivos Críticos

| Archivo | Rol |
|---------|-----|
| `accessibility/OfferParser.kt` | Extrae datos de la UI de Uber (corazón del sistema) |
| `accessibility/ActionExecutor.kt` | Taps sobre la UI de Uber (cascade de 4 estrategias) |
| `accessibility/TapGeometry.kt` | Geometría pura de toque (100% JVM) |
| `domain/rules/RuleEngine.kt` | Reglas de 3 niveles (ACCEPT/WARN/CANCEL) + blacklist |
| `domain/usecase/EvaluateOfferUseCase.kt` | Cálculo económico (fuel, net profit, deadhead) |
| `domain/geometry/PolygonGeometry.kt` | Point-in-polygon, área, círculo→polígono (100% JVM) |
| `domain/ai/SmartAdvisor.kt` | IA local basada en historial (usa `TripHistorySource`) |
| `data/geocoding/NominatimGeocoder.kt` | Reverse geocoding → keywords de zona |
| `service/UberAccessibilityService.kt` | Orquestador principal del pipeline |
| `service/MonitorForegroundService.kt` | Foreground service + WakeLock renovado |
| `service/FloatingWindowService.kt` | HUD flotante con auto-colapso |
| `service/VoiceCommandService.kt` | SpeechRecognizer (opt-in) → `VoiceCommandBus` |
| `service/BootReceiver.kt` / `CarConnectionReceiver.kt` | Auto-arranque por reinicio / Bluetooth |
| `util/TextNormalizer.kt` | Normalización de texto para matches robustos |
| `util/Logs.kt` | Wrapper de log seguro para tests JVM |
| `util/DecisionNotifier.kt` | Notificación heads-up + vibración/sonido por decisión |
| `util/ConfigBackupManager.kt` | Backup/restore de config en JSON puro |
| `util/PermissionStatus.kt` | Checklist puro de permisos (onboarding) |
| `domain/model/HudReasonFormatter.kt` | Motivo del HUD + abreviación de destino (puro) |
| `domain/model/VoiceCommandParser.kt` | Parser puro de comandos de voz |

## Build / Toolchain

- JDK 17, AGP 8.5.1, Kotlin 1.9.24, KSP (no kapt) para Room y Hilt, Room 2.6.1, Hilt 2.51.1,
  OSMDroid 6.1.20. Versionado en `gradle/libs.versions.toml`.
- `local.properties` (contiene `sdk.dir`) está gitignoreado; no lo subas.
- `AndroidManifest` declara `android:usesCleartextTraffic="true"` y `foregroundServiceType="specialUse"`.
