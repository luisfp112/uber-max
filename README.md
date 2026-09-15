# 🚗 UberMax

Automatiza tu decisión sobre cada carrera de **Uber Driver**: analiza la rentabilidad en tiempo real, aplica tus filtros, respeta tu lista negra de zonas y ejecuta la acción (aceptar/rechazar) automáticamente mientras conduces.

> **Piensa mucho internamente, muestra muy poco externamente.**

---

## ✨ Características

- **Análisis económico instantáneo** por carrera: tarifa bruta, ganancia neta (descontando combustible), `$/km`, `$/h` y costo real del trayecto.
- **Motor de reglas de 3 niveles** (`ACCEPT` / `WARN` / `CANCEL`) configurable por el conductor.
- **Deadhead inteligente**: las carreras largas se penalizan con la vuelta vacía, pero solo se rechazan si la rentabilidad final cae bajo tus mínimos — no por la distancia.
- **Lista negra robusta por texto**: calles, barrios y sectores se evalúan contra el texto real de recogida y destino con *normalización* (ignora mayúsculas, tildes y símbolos).
- **Zonas en mapa**: círculos de exclusión geográfica usando OSMDroid.
- **Ejecución automática** sobre Uber Driver (aceptar / tocar la X) mediante AccessibilityService, con reintentos y debounce.
- **HUD flotante** minimalista con la decisión y el motivo principal.
- **Estadísticas e historial local**: mejores horas, mejores zonas, ganancia neta diaria (Room).
- **SmartAdvisor local**: recomendaciones por IA offline basadas en el historial del conductor.
- **UI independiente del vehículo**: costo por km y velocidad promedio configurables.

---

## 🏗️ Arquitectura

**Clean Architecture + MVVM**, con inyección de dependencias por **Hilt**.

```
┌────────────────────────────────────────────────────────────┐
│                           UI (.ui)                        │
│   Dashboard · Settings · Blacklist (Mapa) · HUD flotante  │
│                    ViewModel + StateFlow                  │
├────────────────────────────────────────────────────────────┤
│                       DOMAIN (.domain)                    │
│   RuleEngine (reglas) · SmartAdvisor (IA) · UseCases      │
│                    Modelos puros Kotlin                   │
├────────────────────────────────────────────────────────────┤
│                        DATA (.data)                       │
│   Room (DAO + Entities) · Repositories · ConfigRepository │
├────────────────────────────────────────────────────────────┤
│        PERFORMANCE (.service + .accessibility)            │
│   UberAccessibilityService · Forecast/overlays · Parser    │
└────────────────────────────────────────────────────────────┘
```

### Flujo de una oferta

```
Evento de accesibilidad (Uber Driver)
      │
      ▼
OfferParser ──► OfferData ──► EvaluateOfferUseCase ──► EvaluatedOffer
                                                            │
      ┌─────────────────────────────────────────────────────┘
      ▼
RuleEngine (lista negra → filtros económicos → decisión)
      │
      ▼
SmartAdvisor (recomendación IA con historial local)
      │
      ▼
ActionExecutor (auto-aceptar / auto-cancelar con reintentos)
      │
      ▼
TripRepository (historial en Room) + HUD flotante
```

### Decisión de 3 niveles

| Acción  | Significado                                                    |
|---------|----------------------------------------------------------------|
| `ACCEPT`| Cumple todos los filtros → auto-aceptar                        |
| `WARN`  | Algún filtro económico falla → aviso en HUD (decide el conductor) |
| `CANCEL`| Coincide con la lista negra → auto-rechazar (tocar la X)       |

---

## 🧰 Stack tecnológico

| Capa          | Tecnología                                                        |
|---------------|-------------------------------------------------------------------|
| Lenguaje      | Kotlin 1.9 · JVM 17                                               |
| UI            | ViewBinding + Material Design 3                                   |
| DI            | Hilt 2.51                                                         |
| Persistencia  | Room 2.6 (Flow + Coroutines)                                      |
| Concurrencia  | Kotlin Coroutines 1.8                                             |
| Mapa          | OSMDroid 6.1                                                      |
| Preferencias  | DataStore Preferences                                             |
| Test (JVM)    | JUnit 4 · kotlinx-coroutines-test                                 |

---

## 📂 Estructura del proyecto

```
app/src/main/java/com/ubermax/app/
├── accessibility/   # Parser + ActionExecutor + geometría de taps
├── data/            # Room (db/dao, db/entity) + Repositories
├── di/              # Módulos de Hilt
├── domain/          # Reglas, IA, use cases y modelos
├── service/         # AccessibilityService + HUD + foreground service
├── ui/              # Actividades + ViewModels
└── util/            # Normalizador de texto, regex, logging
```

---

## 📋 Requisitos

- **Android 8.0+** (minSdk 26) · targetSdk 34
- Android Studio Hedgehog o superior
- JDK 17
- Permisos de *Accesibilidad* para el paquete de Uber Driver (`com.ubercab.driver`)

## 🛠️ Build y tests

```bash
# Compilar APK de debug
./gradlew assembleDebug

# Ejecutar todos los tests unitarios (JVM)
./gradlew testDebugUnitTest

# Lint de Android
./gradlew lintDebug
```

Los tests unitarios son **100% JVM** (sin dispositivo): cubren el parser, el motor de reglas, la lista negra normalizada y la penalización por vuelta vacía.

---

## 🤝 Contribuciones

1. Haz *fork* del repositorio y crea una rama: `git checkout -b feature/mi-mejora`.
2. Respeta Clean Architecture: la lógica vive en `domain`, los datos en `data`, la UI en `ui`.
3. Añade tests unitarios para todo el comportamiento decisional.
4. Envía un *pull request*.

## ⚠️ Aviso legal

**UberMax es un proyecto independiente y no está afiliado, respaldado ni patrocinado por Uber Technologies Inc.** Su uso es responsabilidad del conductor y debe cumplir con las políticas de Uber y la legislación local. La automatización de interacciones puede contravenir los términos de uso de plataformas de terceros.

---

## 📄 Licencia

[MIT](LICENSE) © 2026 luisfp112