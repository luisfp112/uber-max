# AGENTS.md — Guía para Agentes (UberMax)

## Estado del repo (léelo primero)

- `main` (HEAD) está en v1.1.0 PERO el working tree contiene un **refactor v1.2.0 sin commitear**:
  muchos archivos de HEAD están borrados y otros son nuevos. `git status` muestra el alcance real.
- **README.md y CHANGELOG.md están desactualizados**: describen features que ya NO existen en el
  working tree (mapa OSMDroid, lista negra de texto y zonas, historial, SmartAdvisor/IA, comandos
  de voz, auto-arranque, dry-run, export CSV, deadhead, métrica por hora, decisión ACCEPT/WARN/CANCEL).
- Fuente de verdad = código + las migraciones Room de `AppDatabase` (documentan cada feature que se
  quitó entre v10→v15). No "restaures" features del README; el refactor las eliminó deliberadamente.

## Arquitectura (estado actual)

Clean Architecture + MVVM + Hilt, un solo módulo `:app`. `applicationId`/`namespace` = `com.ubermax.app`.

- `ui/` (dashboard, settings, onboarding — sin ViewModel en dashboard) · `domain/` (rules, model,
  geo, usecase) · `data/` (Room DB/DAO/entity, geocoding, repository) · `accessibility/` (parser,
  taps) · `service/` (AccessibilityService, foreground services, HUD) · `util/` (normalización,
  regex, logging, backup).
- **La separación de capas NO es estricta.** `domain/` importa entidades Room
  (`data.db.entity.*`) en `EvaluateOfferUseCase`, `RuleEngine`. Solo `domain/model/*` y
  `domain/geo/*` son JVM puro. No intentes "purificar" `domain/` sin que se pida.
- Directorios `domain/ai`, `domain/port`, tests en `domain/geometry`, `app/src/androidTest/` están
  vacíos (restos de features eliminadas): ignóralos.

## Comandos

```bash
./gradlew assembleDebug          # APK: app/build/outputs/apk/debug/UberMax-<version>-debug.apk
./gradlew assembleRelease        # APK firmado: app/build/outputs/apk/release/UberMax-<version>.apk (sin sufijo)
./gradlew testDebugUnitTest      # tests JVM (sin dispositivo)
./gradlew lintDebug              # FALLA con errores de lint (no hay baseline)
```

- Un solo test: `./gradlew testDebugUnitTest --tests "com.ubermax.app.domain.rules.RuleEngineTest"`
- CI (`.github/workflows/ci.yml`) ejecuta: `./gradlew testDebugUnitTest assembleDebug lintDebug --no-daemon --stacktrace`
- JDK 17 tiene que estar disponible; funciona también con JDK 21 de sistema (target 17 via jvmTarget).
- Gotcha de esta máquina: el daemon de Gradle/KSP falla a veces de forma esporádica; reintentar con
  `--no-daemon` normalmente lo resuelve.

## Testing

- Tests 100% JVM puro: sin dispositivo, sin Robolectric, sin Mockito (fue eliminado; no lo reintroduzcas).
- En lógica testeable usa `util/Logs` (no `android.util.Log`): en la JVM de test `android.jar`
  mockable lanza `RuntimeException("not mocked")`.
- Todo comportamiento decisional nuevo requiere tests. `LocationIqGeocoder` testea solo la parte
  pura (parse/buildQuery/nameMatches), nunca red.

## Pipeline de una oferta

```
Evento de Uber Driver (com.ubercab.driver)
  -> UberAccessibilityService.processEvent()   # debounce 3s + dedup por huella 10s
    -> OfferParser.parseOffer(rootNode)        -> OfferData
    -> LocationIqGeocoder.geocode()            # SOLO si geoCheckEnabled + geoApiKey (fail-open)
    -> EvaluateOfferUseCase.evaluate()         -> EvaluatedOffer
    -> RuleEngine.evaluate()                   -> OfferDecision (ACCEPT | WARN)
    -> decisionFlow.emit()                     -> HUD flotante
    -> DecisionNotifier.feedback()             -> TTS opt-in (voice_announce_enabled)
    -> ActionExecutor.clickAcceptButton()      # SOLO en ACCEPT
```

- **Solo hay ACCEPT y WARN.** WARN = rechazo pasivo: se muestra en HUD, NUNCA se pulsa la X.
  `ActionExecutor.clickDismissButton()` existe pero nadie lo llama. No reintroduzcas CANCEL.
- No dupliques debounce/dedup: el servicio ya trae 3s entre eventos y 10s por misma tarifa
  (`OfferFingerprint`).

## Reglas de decisión (v1.2.0)

- **Una sola métrica económica decide: ganancia neta por km** (`FilterRulesEntity.minProfitPerKm`).
  La tarifa de la tarjeta ya viene neta (Uber descuenta su comisión), así que NO hay métricas por
  hora, comisión de plataforma ni vuelta vacía. No vuelvas a evaluar `$/km` y `$/hora` juntos.
- `0` = filtro desactivado. Filtros actuales: `minFare`, `minProfitPerKm`, pickup (`pickup_measure`
  MIN/KM + `pickupMax`), viaje (`trip_measure` + `tripMax`), rating (`minPassengerRating`,
  `rejectOnUnknownRating`), `autoAcceptEnabled`.
- **Normaliza antes de comparar**: todo match de texto (botones del parser, motivos) pasa por
  `TextNormalizer.normalize()` (ignora mayúsculas, tildes, símbolos).
- `TapGeometry`/`ActionExecutor` son universales: nada de coordenadas absolutas ni calibración por
  modelo; todo es relativo a `RectSpec` o proporciones de pantalla. `HumanTapTiming` añade retardo
  gaussiano + jitter opt-in (`humanTapsEnabled`).

## Feature geográfica (la única "zona" que queda)

- Control opt-in: `geoCheckEnabled` + `geoApiKey` (token de LocationIQ que el conductor pega en
  Ajustes). **La API key se guarda en Room y jamás se commitea.**
- Las zonas son `assets/zonas-permitidasv3.geojson` (GeoJSON `[lng, lat]`, Polygon/MultiPolygon,
  con agujeros). Se parsean una vez con `GeoJsonZones`.
- **Fail-open**: sin key, sin red, sin geocode o sin GeoJSON válido, el filtro se OMITE (WARN
  "no confirmada"). La geolocalización nunca bloquea ni auto-acepta por sí sola.

## Room y migraciones

- `AppDatabase` en **versión 15**, `exportSchema = false`. Migraciones explícitas
  `MIGRATION_5_6`…`MIGRATION_14_15` en el companion object de `AppDatabase`.
- **No hay `fallbackToDestructiveMigration()`.** Al cambiar cualquier esquema: sube `version`,
  añade un `Migration` con DDL manual y regístralo en `DatabaseModule.provideDatabase().addMigrations(...)`.
- Room valida el esquema EXACTO (columna por columna). Las migraciones 13→14 y 14→15 dejan
  constancia de cómo se eliminaron tablas/columnas; lee ahí antes de tocar el esquema.
- Tablas actuales: `vehicle_config` (singleton, costos) · `filter_rules` (singleton, reglas) ·
  `app_settings` (singleton, toggles).

## Simulador de ofertas (solo debug)

`SimulateOfferReceiver` (source set `debug`) inyecta una tarjeta en el pipeline real:

```bash
adb shell am broadcast -n com.ubermax.app/com.ubermax.app.debug.SimulateOfferReceiver \
  -a com.ubermax.app.debug.SIMULATE_OFFER \
  --es fare '$4.50' --es rating '4.92' --es trips '(120)' \
  --es pickup 'A 3 min (1.2 km)' --es pickupAddress 'Heiraway, Oscar Wilde' \
  --es trip 'Viaje: 9 min (3.2 km)' --es dest 'Av. Condor, Ingapirca'
```

Mínimo para una tarjeta válida: `fare`, `pickup` y `trip` (con patrones min/km), porque el parser
rechaza tarjetas sin tarifa y sin distancias.

## Operación, release y versionado

- `MonitorForegroundService` y `FloatingWindowService` son foreground (`specialUse`) para no ser
  matados. `MonitorForegroundService` adquiere WakeLock con timeout 4h y lo renueva cada 55 min;
  degrada con gracia en ROMs agresivas.
- Versiones: `versionCode` y `versionName` (SemVer) en `app/build.gradle.kts`. Cada release se
  etiqueta `v<versionName>` → `.github/workflows/release.yml` corre tests+lint, compila
  `assembleRelease` y adjunta el APK. Cambios en `CHANGELOG.md` (español, formato Keep a Changelog).
- **Keystore intencionalmente commiteado**: `keystore/ubermax-release.jks` + `keystore/keystore.properties`
  están versionados a propósito (builds reproducibles sin secrets de CI; repo privado, solo el `.jks`
  se permite vía .gitignore). No los "arregles" ni los elimines, y no commitees la API key de LocationIQ.
- `local.properties` (con `sdk.dir`) y `.vscode/` están gitignoreados; no los subas.

## Archivos críticos

| Archivo | Rol |
|---------|-----|
| `accessibility/OfferParser.kt` | Extrae datos de la tarjeta de Uber (corazón; 100% JVM vía `parseFromTextNodes`) |
| `accessibility/ActionExecutor.kt` | Taps (cascada de estrategias, verificación post-tap) |
| `domain/rules/RuleEngine.kt` | Decisiones ACCEPT/WARN |
| `domain/usecase/EvaluateOfferUseCase.kt` | Cálculo económico ($/km) |
| `domain/geo/GeoJsonZones.kt` | Zonas permitidas del conductor (JVM puro) |
| `data/geocoding/LocationIqGeocoder.kt` | Forward geocoding LocationIQ (fail-open, timeouts 1.5s) |
| `data/db/AppDatabase.kt` | Esquema + historial de migraciones (v5→v15) |
| `service/UberAccessibilityService.kt` | Orquestador del pipeline |
| `util/TextNormalizer.kt` / `util/Logs.kt` | Normalización / logging seguro para tests |
| `util/ConfigBackupManager.kt` | Backup JSON `FORMAT_VERSION=2`; ignora bloques de features eliminadas |