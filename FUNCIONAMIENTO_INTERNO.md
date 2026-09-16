# Funcionamiento Interno de UberMax

Documento técnico detallado del funcionamiento interno de cada componente del sistema.

---

## 1. Pipeline Principal — `UberAccessibilityService`

**Archivo**: `service/UberAccessibilityService.kt`

El AccessibilityService es el orquestador central. Recibe eventos de la UI de Uber Driver y ejecuta el pipeline completo.

### Ciclo de vida

1. **`onCreate()`**: Se instancia `ActionExecutor` con referencia al servicio.
2. **`onServiceConnected()`**: Se marca `isRunning = true` y se carga la configuración inicial desde Room DB.
3. **`onAccessibilityEvent(event)`**: Filtra por paquete `com.ubercab.driver` y tipos `TYPE_WINDOW_CONTENT_CHANGED`, `TYPE_WINDOW_STATE_CHANGED`, `TYPE_WINDOWS_CHANGED`. Lanza `processEvent()` en `serviceScope`.
4. **`onDestroy()`**: Cancela el scope y limpia.

### `processEvent()` — Flujo paso a paso

```
1. Debounce: si han pasado < 3000ms desde el último procesamiento, retornar.
2. findUberWindow(): busca la ventana de Uber Driver entre todas las ventanas del sistema.
3. OfferParser.parseOffer(uberRoot): extrae datos de la tarjeta visible.
4. Deduplicación: si la tarifa es la misma y no han pasado 10s, ignorar.
5. loadConfig(): recarga vehicleConfig, filterRules, blacklist de Room (puede haber cambiado).
6. EvaluateOfferUseCase.evaluate(offer, vehicleConfig): calcula métricas económicas.
7. RuleEngine.evaluate(): aplica filtros y lista negra → OfferDecision.
8. SmartAdvisor.analyzeOffer(): enriquece con IA local (si aiEnabled).
9. _decisionFlow.emit(decision): notifica al HUD.
10. Ejecuta acción según decision.action (ACCEPT/CANCEL/WARN/IGNORE).
11. logTrip(): guarda en Room DB.
```

### `findUberWindow()`

Busca la ventana de Uber Driver de dos formas:
1. **`windows`** (lista de ventanas del sistema): Itera y busca por packageName. Esto permite encontrar Uber incluso como overlay sobre Waze/Maps.
2. **`rootInActiveWindow`** (fallback): Usa la ventana activa directamente.

### `executeWithRetry()`

Reintenta una acción hasta 5 veces con 300ms de espera entre intentos. Esto compensa cambios de UI o animaciones de Uber Driver que pueden hacer que el primer toque no tenga efecto.

### Debounce y deduplicación

- **Debounce temporal**: 3 segundos entre procesamientos (evita procesar el mismo evento múltiples veces).
- **Deduplicación por tarifa**: Si la tarifa extraída es igual a la última procesada y no han pasado 10 segundos, se ignora (misma oferta en pantalla).

---

## 2. Parser de Ofertas — `OfferParser`

**Archivo**: `accessibility/OfferParser.kt`

Extrae datos estructurados de la tarjeta de Uber Driver visible en la UI.

### Arquitectura del parser

- **`parseOffer(rootNode)`**: Punto de entrada Android. Recorre el árbol de accesibilidad y delega en el núcleo puro.
- **`parseFromTextNodes(nodes)`**: Núcleo 100% JVM puro. Acepta una lista de `TextNode` y produce `OfferData`. Este es testeable con JUnit.

### Estructura de la tarjeta de Uber Driver

```
┌─────────────────────────────────────────┐
│ ← UberX                              X │
│ $4.68                                   │
│ ★ 4.73 (42)                             │
│ ● A 6 min (1.8 km)                     │
│   Blvr. del Ejercito Nacional           │
│ ● Viaje: 36 min (15.6 km)              │
│   C. L-7, CIUDAD MERLIOT               │
│   Termina en 00:42                      │
│          [Viaje disponible]             │
└─────────────────────────────────────────┘
```

### Extracción de campos

| Campo | Patrón / Método | Ejemplo |
|-------|-----------------|---------|
| **Tarifa** | `RegexPatterns.FARE_PATTERN` | `$10.00` → `10.0` |
| **Tipo de viaje** | Lista hardcodeada (`UberX`, `Comfort`, etc.) | `UberX` |
| **Pickup** | `A X min (X.X km)` (regex local) | `A 3 min (1.0 km)` → `(1.0, 3)` |
| **Viaje** | `Viaje: X min (X.X km)` (regex local) | `Viaje: 30 min (12.0 km)` → `(12.0, 30)` |
| **Rating** | `★ X.XX (N)` o fallbacks | `★ 4.73 (42)` → `(4.73, 42)` |
| **Direcciones** | Texto entre patrones, filtrado de NON_ADDRESS_TEXTS | Excluye botones, countdowns |

### Fallback genérico

Cuando no se encuentran los patrones `A X min` ni `Viaje: X min`, se usa un extractor genérico que busca cualquier texto con `km` o `min`. Si no hay ninguna distancia, la oferta se descarta.

### Filtrado de textos no-dirección

El conjunto `NON_ADDRESS_TEXTS` excluye:
- Botones de acción: "aceptar", "viaje disponible", "postularse", etc.
- Temporizadores: "Termina en 00:42"
- Estados: "en camino", "asignado"

---

## 3. Ejecutor de Acciones — `ActionExecutor`

**Archivo**: `accessibility/ActionExecutor.kt`

Ejecuta taps reales sobre la UI de Uber Driver. Diseñado para funcionar en CUALQUIER dispositivo Android sin coordenadas absolutas.

### Estrategia de toque (CASCADE de 4 niveles)

#### Para ACCEPT (botón "Aceptar" / "Viaje disponible"):

1. **Búsqueda por texto**: `findActionNode()` recorre DFS el árbol buscando nodo cuyo `text` o `contentDescription` contenga una de las frases normalizadas: `"aceptar"` o `"viaje disponible"`.
2. **`tryRealTap(node)`**: 
   - Sube al padre clicable más cercano (`nearestClickableAncestor`)
   - Intenta `ACTION_CLICK` en el contenedor
   - Si falla, `dispatchGesture` en el centro del contenedor (bounds reales)
   - Si falla, gesto en el centro del propio nodo
3. **Verificación post-acción** (`verifyActionDone`): Espera 400ms y re-verifica que el botón ya no esté presente. Si sigue, retorna `false` para reintentar.
4. **Fallback relativo**: Si no se encontró ningún nodo, toca en proporciones de pantalla (50% ancho, 85% alto).

#### Para CANCEL (botón X):

1. **Por texto**: Busca nodos con textos: "cerrar", "descartar", "rechazar", "cancelar", "close", "dismiss", "X", etc.
2. **Por posición**: Detecta la tarjeta de la oferta (`findCardContainer`), recolecta nodos clicables, y usa `TapGeometry.chooseDismissCandidate()` para encontrar la X en el cuadrante superior derecho.
3. **Por ícono**: Busca `ImageButton`/`ImageView` clicables en la región superior derecha de la tarjeta.
4. **Fallback relativo**: Toca en proporciones de pantalla (94% ancho, 28% alto).

### `findActionNode()` — Algoritmo de puntuación

Recorre DFS y puntúa cada nodo coincidente:
- **Texto propio** > contentDescription
- **Clicable** > no clicable
- **Más profundo** > menos profundo (profundidad > 0)

El nodo con mayor puntuación se retorna. Los demás se reciclan.

### Manejo de memoria

Todos los `AccessibilityNodeInfo` se reciclan (`recycle()`) correctamente. Los `try/finally` garantizan el reciclaje incluso cuando la ventana cambia durante el recorrido (`IllegalStateException`).

---

## 4. Geometría de Toque — `TapGeometry`

**Archivo**: `accessibility/TapGeometry.kt`

100% JVM puro (sin dependencias Android). Geometría relativa universal.

### RectSpec

Rectángulo inmutable puro con propiedades computadas:
- `width`, `height`, `centerX`, `centerY`
- `isEmpty`: True si dimensiones <= 0
- `clamped()`: Limita a dimensiones de pantalla

### Puntos de toque relativos

- **`relativePointIn(rect, xRatio, yRatio)`**: Calcula un punto relativo dentro de un rect. `(0.5, 0.5)` = centro exacto.
- **`fallbackPoint(screenW, screenH, xRatio, yRatio)`**: Calcula punto en proporciones de pantalla completa.

### Proporciones de reserva

| Acción | X Ratio | Y Ratio | Significado |
|--------|---------|---------|-------------|
| ACCEPT | 0.50 | 0.85 | Centro horizontal, zona inferior (botón) |
| DISMISS | 0.94 | 0.28 | Borde derecho, cuarto superior (esquina X) |

### Detección de la X (botón descartar)

- **`isDismissSized(rect, container)`**: Verifica que el rect sea pequeño (ícono) pero no ínfimo. Tamaño entre 1.8% y 18.5% del ancho del contenedor.
- **`isInTopRightRegion(rect, container)`**: Verifica que el centro esté en el cuadrante superior derecho del contenedor.
- **`chooseDismissCandidate()`**: Filtra por tamaño + región, elige el más a la derecha.

---

## 5. Motor de Reglas — `RuleEngine`

**Archivo**: `domain/rules/RuleEngine.kt`

Evalúa una oferta contra las reglas configuradas por el conductor. Sistema de 3 niveles.

### Niveles de decisión

| Nivel | Condición | Acción |
|-------|-----------|--------|
| **CANCEL** | Destino/recogida coincide con lista negra | Auto-rechazar (tocar X) |
| **WARN** | Algún filtro económico falla | Aviso en HUD (conductor decide) |
| **ACCEPT** | Todos los filtros pasan | Auto-aceptar |

### Filtros evaluados (todos desactivables con valor 0)

1. **Tarifa mínima** (`minFare`): `$` bruta
2. **Ganancia neta mínima** (`minNetProfit`): `$` después de combustible
3. **$/km mínimo** (`minProfitPerKm`): Ganancia neta / km totales
4. **$/hr mínimo** (`minProfitPerHour`): Ganancia neta / horas estimadas
5. **Pickup máximo** (`maxPickupKm`): Distancia de recogida
6. **Viaje máximo** (`maxTripKm`): Distancia del viaje
7. **Tiempo máximo** (`maxTotalMinutes`): Tiempo total estimado
8. **Rating mínimo** (`minPassengerRating`): Calificación del pasajero
9. **Ratio pickup/trip** (`maxPickupTripRatio`): Relación recogida/viaje
10. **Deadhead**: Solo se reporta si la rentabilidad final (con retorno incluido) cae bajo los mínimos

### Lista negra

- **Por keyword**: Compara texto normalizado de destino + recogida contra keywords configuradas.
- **Por zona**: Compara nombres de zonas del mapa contra direcciones.
- **Normalización**: `TextNormalizer.normalize()` ignora mayúsculas, tildes y caracteres especiales.

### Decisión final

```kotlin
if (failedFilters.isEmpty()) {
    action = if (autoAcceptEnabled) ACCEPT else WARN
} else {
    action = WARN
}
```

---

## 6. Evaluación Económica — `EvaluateOfferUseCase`

**Archivo**: `domain/usecase/EvaluateOfferUseCase.kt`

Calcula métricas de rentabilidad a partir de datos brutos y configuración del vehículo.

### Fórmulas

```
Deadhead: Si tripKm > 8.0 → returnKm = tripKm (vuelta vacía asumida)
totalKm = pickupKm + tripKm + returnKm
fuelCost = totalKm × costPerKm
netProfit = rawFare - fuelCost
profitPerKm = netProfit / totalKm
returnMinutes = if (isFarTrip) tripMinutes else 0
totalEstimatedMinutes = estimatedMinutes + returnMinutes
profitPerHour = netProfit / (totalEstimatedMinutes / 60.0)
```

### Deadhead (vuelta vacía)

Cuando un viaje supera 8km, se asume que el conductor tendrá que regresar vacío. Se añaden los km de retorno al cálculo:
- Aumenta `totalKm` → más combustible
- Aumenta tiempo estimado → menor $/hr
- Solo se penaliza en `RuleEngine` si la rentabilidad final cae bajo los mínimos

### Costo por km (`VehicleConfigEntity.costPerKm`)

Cascada de prioridad:
1. `manualCostPerKm > 0` → se usa directamente
2. `computedCostPerKm > 0` → `fuelCostPerKm + maintenancePerKm`
3. `DEFAULT_COST_PER_KM = 0.10` → fallback

---

## 7. IA Local — `SmartAdvisor`

**Archivo**: `domain/ai/SmartAdvisor.kt`

Sistema basado en reglas y estadísticas que analiza el historial de viajes del conductor.

### Flujo

1. Obtiene historial reciente de viajes desde Room DB.
2. Si no hay datos, retorna recomendación genérica con confianza baja (0.1).
3. Calcula `destinationScore` para el destino actual.
4. Genera recomendación según la decisión original:

| Decisión | Score destino | Recomendación | Confianza |
|----------|--------------|---------------|-----------|
| ACCEPT | < 0.3 | "Precaución: destino históricamente poco rentable" | 0.8 |
| ACCEPT | >= 0.3 | "Buena elección basada en historial" | 0.9 |
| WARN | > 0.8 | "Recomendado aceptar: buena zona compensa fallos" | 0.75 |
| WARN | <= 0.8 | "Mejor rechazar: zona mala + filtros fallidos" | 0.9 |
| CANCEL | cualquier | "IA: rechazo mandatorio confirmado" | 1.0 |

### `calculateDestinationScore()`

```kotlin
// Extrae primer término de la dirección (ej: "Col. Escalón" → "col. escalón")
// Busca viajes históricos con ese término en destination o pickupAddress
// Score = (profitScore × 0.6) + (acceptRatio × 0.4)
// profitScore = (avgProfitKm / 0.25).coerceIn(0.0, 1.0)
```

- **60% peso** a ganancia/km promedio en esa zona
- **40% peso** a ratio de aceptación histórica de viajes a esa zona
- Zonas desconocidas retornan 0.5 (neutral)

---

## 8. HUD Flotante — `FloatingWindowService`

**Archivo**: `service/FloatingWindowService.kt`

Overlay minimalista que muestra la decisión y métricas clave.

### Características

- **Foreground service**: No se destruye bajo baja memoria.
- **Auto-colapso**: 6 segundos después de cada decisión, se contrae a burbuja mínima.
- **Drag & tap**: Arrastrar mueve la ventana; toque alterna colapsado/expandido.
- **Se comunica con el servicio principal** via `UberAccessibilityService.decisionFlow` (SharedFlow).

### Elementos visuales

| Elemento | Contenido |
|----------|-----------|
| Badge | "✅ ACEPTADO" / "❌ RECHAZADO" |
| Motivo | Línea única: "Tarifa muy baja", "Zona en lista negra", etc. |
| Ganancia neta | `$X.XX neta` (verde si positivo, rojo si negativo) |
| $/km | `$X.XX/km` |
| Destino | Dirección abreviada (máx 22 chars) |
| Burbuja colapsada | `✅ $X.XX` o `❌ $X.XX` |

### Flujo de actualización

```
UberAccessibilityService.decisionFlow.emit(decision)
  → FloatingWindowService.observeDecisions() (collectLatest)
    → updateHUD(decision)
      → expand() + scheduleAutoCollapse()
```

---

## 9. Servicio de Monitoreo — `MonitorForegroundService`

**Archivo**: `service/MonitorForegroundService.kt`

Foreground service que mantiene vivo el proceso de UberMax.

### Funciones

- **Notificación persistente**: Baja prioridad, con acción "Detener Monitoreo".
- **WakeLock parcial**: Previene que el CPU entre en suspensión durante 10 horas máximo.
- **START_STICKY**: Se reinicia automáticamente si Android lo mata.

---

## 10. Persistencia — Room Database

**Archivo**: `data/db/AppDatabase.kt`

Base de datos version 5 con 5 entidades y `fallbackToDestructiveMigration()`.

### Entidades

| Tabla | Propósito | Clave |
|-------|-----------|-------|
| `trip_log` | Historial de ofertas evaluadas | `id` (auto) |
| `vehicle_config` | Singleton de config del vehículo | `id = 1` |
| `filter_rules` | Singleton de filtros | `id = 1` |
| `blacklist_entry` | Keywords de lista negra | `id` (auto) |
| `blacklist_zone` | Zonas geográficas circulares | `id` (auto) |

### DAOs destacados

- **`TripLogDao`**: Consultas agregadas para estadísticas: `getBestHours()` (GROUP BY hour), `getBestZones()` (GROUP BY destination), `getTodayNetProfit()`.
- **`ConfigRepository`**: Abstrae todos los DAOs de configuración. Inicializa valores por defecto si la DB está vacía.

---

## 11. Utilidades

### `TextNormalizer`

Normalización de texto 100% JVM puro usando `java.text.Normalizer`:
```
"Techo Própio" → "techo propio"
"Av. FICOA, #45" → "av ficoa 45"
"CALLE 12 de Octubre,#" → "calle 12 de octubre"
```

### `RegexPatterns`

Patrones regex calibrados para la UI de Uber Driver:
- `FARE_PATTERN`: Detecta `$X.XX`, `COP X.XXX`, `USD X.XX`
- `DISTANCE_KM_PATTERN`: Detecta `X.X km`
- `TIME_MINUTES_PATTERN`: Detecta `X min`
- `parseFare()`: Maneja separadores de miles (`12.500` → `12500.0`)

### `Logs`

Wrapper de `android.util.Log` que usa `runCatching` para no lanzar excepciones en tests JVM puros (el `android.jar` mockable lanza `RuntimeException`).

---

## 12. Inyección de Dependencias — Hilt

### Módulos

- **`DatabaseModule`**: Provee `AppDatabase` (Room) y todos los DAOs como singletons.

### Anotaciones en uso

- `@HiltAndroidApp`: `UberMaxApplication`
- `@AndroidEntryPoint`: Activities, Services
- `@HiltViewModel`: `SettingsViewModel`
- `@Singleton`: `ConfigRepository`, `TripRepository`, `SmartAdvisor`, `AppDatabase`
- `@Inject constructor`: Repositorios y use cases

---

## 13. Tests Unitarios

Todos los tests son **100% JVM** (sin dispositivo, sin Robolectric).

### Suite de tests

| Archivo | Cubre |
|---------|-------|
| `OfferParserTest.kt` | Parsing de ofertas asignadas/abiertas, fallbacks, ratings, decisiones |
| `ActionExecutorTest.kt` | Match de texto (aceptar/descartar), geometría de toque, regiones |
| `VehicleConfigTest.kt` | Costo manual vs calculado, litros vs galones, fallback |
| `RuleEngineTest.kt` | Deadhead, lista negra normalizada, filtros económicos |

### Patrón de tests

```kotlin
// 1. Crear nodos simulados (TextNode puros)
val nodes = nodesOf("UberX", "\$10.00", "★ 4.73 (42)", ...)

// 2. Parsear con el parser JVM puro
val offer = parser.parseFromTextNodes(nodes)

// 3. Evaluar económicamente
val evaluated = evaluator.evaluate(offer, vehicle)

// 4. Aplicar reglas
val decision = ruleEngine.evaluate(evaluated, rules, keywords, zones)

// 5. Assert sobre la decisión
assertEquals(Action.ACCEPT, decision.action)
```
