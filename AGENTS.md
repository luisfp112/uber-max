# AGENTS.md — Guia para Agentes de IA

## Visión General

**UberMax** es una app Android que automatiza la decisión sobre ofertas de Uber Driver usando un AccessibilityService. Analiza rentabilidad en tiempo real y ejecuta aceptar/rechazar automáticamente.

## Arquitectura

```
Clean Architecture + MVVM + Hilt DI
```

- **UI** (`ui/`): Actividades + ViewModels con StateFlow
- **Domain** (`domain/`): Reglas de negocio, modelos puros Kotlin, IA local
- **Data** (`data/`): Room DB (DAOs + Entities), Repositories
- **Accessibility** (`accessibility/`): Parser de la UI de Uber, ejecutor de taps, geometría
- **Service** (`service/`): AccessibilityService, ForegroundService, HUD flotante
- **Util** (`util/`): Normalización de texto, regex, logging

## Convenciones

### Formato
- Kotlin con indentación de 4 espacios
- Trailing comma permitida
- UTF-8, LF line endings
- Ver `.editorconfig` para detalles completos

### Arquitectura por capas
- La lógica de negocio **NUNCA** va en UI o Services
- Los modelos de dominio son **puros Kotlin** (sin dependencias Android)
- `domain/` no importa `data/` ni `ui/`
- `data/` solo expone repositorios; `ui/` consume ViewModels

### Testing
- Tests unitarios JVM puros (sin dispositivo, sin Robolectric)
- Cubren: Parser, RuleEngine, TapGeometry, VehicleConfig, ActionExecutor text matching
- Para Android Log en tests se usa `Logs` wrapper (no `android.util.Log` directamente)
- **Siempre** añadir tests para comportamiento decisional nuevo

### Dependencias
- **Hilt** para DI: `@HiltAndroidApp`, `@AndroidEntryPoint`, `@HiltViewModel`, `@Singleton`
- **Room** para persistencia: DAOs con `suspend` + `Flow`, entities con `@ColumnInfo`
- **KSP** (no kapt) para Room y Hilt
- **Coroutines** para concurrencia: `viewModelScope`, `serviceScope` con `SupervisorJob`
- **OSMDroid** para mapa interactivo
- **DataStore Preferences** (aunque actualmente se usa Room para config)

## Flujo de una Oferta

```
AccessibilityEvent (Uber Driver)
  -> UberAccessibilityService.processEvent()
    -> OfferParser.parseOffer(rootNode)     -> OfferData
    -> EvaluateOfferUseCase.evaluate()      -> EvaluatedOffer
    -> RuleEngine.evaluate()                -> OfferDecision
    -> SmartAdvisor.analyzeOffer()          -> OfferDecision (enriquecido)
    -> ActionExecutor.clickAccept/Dismiss() -> ejecución táctil
    -> TripRepository.logDecision()         -> Room DB
    -> decisionFlow.emit()                  -> HUD se actualiza
```

## Comandos de Build

```bash
./gradlew assembleDebug          # Compilar debug
./gradlew testDebugUnitTest      # Tests unitarios JVM
./gradlew lintDebug              # Lint de Android
```

## Reglas Clave

1. **No romper el parser**: `OfferParser.parseFromTextNodes()` es 100% JVM puro. Cualquier cambio debe mantener los tests existentes.
2. **TapGeometry es universal**: NO usar coordenadas absolutas ni calibrar por modelo de teléfono. Todo es relativo a `RectSpec` o proporciones de pantalla.
3. **TextNormalizer para comparaciones**: Cualquier match de texto (lista negra, botones) debe pasar por `TextNormalizer.normalize()` para ignorar mayúsculas, tildes y símbolos.
4. **Deadhead no es distancia**: La penalización por vuelta vacía NO rechaza por distancia larga; solo penaliza cuando la rentabilidad final (con retorno incluido) cae bajo los mínimos.
5. **Debounce en el servicio**: El servicio tiene debounce de 3s y deduplica por tarifa. No duplicar esta lógica.
6. **Foregound services**: Tanto `MonitorForegroundService` como `FloatingWindowService` corren como foreground para evitar que Android los mate bajo baja memoria.

## Archivos Críticos

| Archivo | Rol |
|---------|-----|
| `accessibility/OfferParser.kt` | Extrae datos de la UI de Uber (el corazón del sistema) |
| `accessibility/ActionExecutor.kt` | Ejecuta taps sobre la UI de Uber (CASCADE de 4 estrategias) |
| `accessibility/TapGeometry.kt` | Geometría pura de toque (100% JVM) |
| `domain/rules/RuleEngine.kt` | Motor de reglas de 3 niveles (ACCEPT/WARN/CANCEL) |
| `domain/usecase/EvaluateOfferUseCase.kt` | Cálculo económico (fuel, net profit, deadhead) |
| `domain/ai/SmartAdvisor.kt` | IA local basada en historial |
| `service/UberAccessibilityService.kt` | Orquestador principal del pipeline |
| `service/FloatingWindowService.kt` | HUD flotante con auto-colapso |
| `util/TextNormalizer.kt` | Normalización de texto para comparaciones robustas |
| `util/RegexPatterns.kt` | Patrones regex para parsing de la UI |

## Estructura de Room DB

- `trip_log`: Historial de ofertas evaluadas (决策, métricas económicas)
- `vehicle_config`: Singleton con config del vehículo (consumo, precio combustible)
- `filter_rules`: Singleton con filtros del conductor
- `blacklist_entry`: Keywords de texto para lista negra
- `blacklist_zone`: Zonas geográficas circulares en mapa

**Nota**: `fallbackToDestructiveMigration()` está activo. Si cambias el schema, considera añadir migraciones reales antes de producción.

## CI

GitHub Actions ejecuta `testDebugUnitTest` + `assembleDebug` en cada push/PR a `main`.
