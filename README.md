# UberMax

Aplicación Android que automatiza la decisión sobre cada oferta de Uber Driver: analiza la
rentabilidad en tiempo real, aplica los filtros del conductor, respeta su lista negra de zonas
y ejecuta la acción (aceptar/rechazar) automáticamente mientras conduce.

## Descarga e instalación

UberMax se distribuye como APK a través de GitHub Releases. Los artefactos son instalables
directamente (sin tienda).

- Ultima version: [releases/latest](https://github.com/luisfp112/uber-max/releases/latest)
- Historial completo: [releases](https://github.com/luisfp112/uber-max/releases)

Pasos de instalacion:

1. Descarga el APK `UberMax-<version>-debug.apk` desde los releases.
2. Abre el archivo desde el gestor de descargas o el navegador.
3. Si Android solicita permisos de "origenes desconocidos", concedelos para esta instalacion.
4. Completa el asistente de configuracion inicial (permisos obligatorios) en la app.

> Las versiones se publican creando un tag `vX.Y.Z`; el pipeline de GitHub Actions
> (`release.yml`) ejecuta tests, lint y genera el APK versionado automaticamente.

## Caracteristicas

- **Analisis economico por viaje**: tarifa bruta, ganancia neta (combustible y mantenimiento),
  rentabilidad por km, por hora y costo real del trayecto.
- **Motor de reglas de 3 niveles** (`ACCEPT` / `WARN` / `CANCEL`) configurable por el conductor,
  con metrica principal de rentabilidad (`$/hora` o `$/km`).
- **Deadhead inteligente**: las carreras largas se penalizan con la vuelta vacia, y solo se
  rechazan si la rentabilidad final cae bajo los minimos configurados, nunca por la distancia.
- **Lista negra por texto**: calles, barrios y sectores evaluados contra la informacion real de
  recogida y destino con normalizacion (ignora mayusculas, tildes y simbolos).
- **Zonas en mapa**: circulos o poligonos de exclusion geografica sobre OSMDroid, con extraccion
  automatica de keywords por geocodificacion inversa (Nominatim).
- **Ejecucion automatica**: acepta o rechaza la oferta sobre Uber Driver mediante
  AccessibilityService, con reintentos y debounce.
- **HUD flotante**: superposicion minimalista con la decision, la ganancia neta y el motivo principal.
- **Historial local**: histial de ofertas con filtros temporales (hoy / 7 dias / 30 dias / todo)
  y grafico de ganancia por hora.
- **SmartAdvisor local**: recomendaciones por IA offline basadas en el historial del conductor,
  con referencia configurable de "buena ganancia por km".
- **Control por voz** (opcional): comandos "aceptar" / "rechazar" durante el monitoreo.
- **Auto-arranque** (opcional): al reiniciar el dispositivo o al conectar el Bluetooth del auto.
- **Modo simulacion**: evalua y muestra el HUD sin pulsar en Uber (dry-run).
- **Backup de configuracion**: exportacion e importacion de ajustes, filtros, lista negra y zonas.
- **Panel de control**: metricas reales del dia y exportacion a CSV del historial completo.

## Arquitectura

Clean Architecture + MVVM con inyeccion de dependencias mediante Hilt.

```
UI (ui/)
  Dashboard · Settings · Blacklist (mapa) · Historial · Onboarding
  ViewModel + StateFlow

DOMAIN (domain/)
  RuleEngine (reglas) · SmartAdvisor (IA) · UseCases · Modelos

DATA (data/)
  Room (DAO + Entities) · Repositories · Geocoding (Nominatim)

PERFORMANCE (service/ + accessibility/)
  UberAccessibilityService · Foreground services · HUD · Parser · ActionExecutor
```

> Nota de diseno: la separacion entre capas no es estricta. `domain/` consume entidades Room
> de `data/` en reglas, IA y puerto de historial (`TripHistorySource`, bindeado a
> `TripRepository` via `@Binds`). Solo `domain/model/*` es Kotlin puro.

### Flujo de una oferta

```
Evento de accesibilidad (Uber Driver)
   -> OfferParser.load()            -> OfferData
   -> EvaluateOfferUseCase          -> EvaluatedOffer
   -> RuleEngine                    -> OfferDecision (ACCEPT / WARN / CANCEL)
   -> SmartAdvisor                  -> Recomendacion con historial local
   -> dry-run activo?               -> marca simulacion, omite taps
   -> ActionExecutor                -> tap Aceptar / X con reintentos
   -> TripRepository (Room) + HUD flotante + notificacion
```

### Decision de 3 niveles

| Accion  | Significado                                                  |
|---------|--------------------------------------------------------------|
| ACCEPT  | Cumple todos los filtros -> auto-aceptar                     |
| WARN    | Alguna guarda economica falla -> aviso en HUD (decide el conductor) |
| CANCEL  | Coincide con lista negra / filtra duro -> auto-rechazar (tocar la X) |

## Stack tecnologico

| Capa          | Tecnologia                              |
|---------------|-----------------------------------------|
| Lenguaje      | Kotlin 1.9 · JVM 17                     |
| UI            | ViewBinding + Material Design 3         |
| DI            | Hilt 2.51                               |
| Persistencia  | Room 2.6 (Flow + Coroutines)            |
| Concurrencia  | Kotlin Coroutines 1.8                   |
| Mapa          | OSMDroid 6.1 (OSM, sin API key)         |
| Geocoding     | Nominatim (OpenStreetMap)               |
| Test (JVM)    | JUnit 4 · kotlinx-coroutines-test       |

## Estructura del proyecto

```
app/src/main/java/com/ubermax/app/
  accessibility/   Parser, ActionExecutor y geometria de taps
  data/            Room (db/dao, db/entity) y Repositories
  di/              Modulos de Hilt
  domain/          Reglas, IA, use cases y modelos
  service/         AccessibilityService, foreground services, HUD, voz, receivers
  ui/              Actividades y ViewModels
  util/            Normalizador, regex, logging, notificaciones, backup
```

## Requisitos

- Android 8.0+ (minSdk 26) · targetSdk 34.
- Android Studio Hedgehog o superior y JDK 17 para compilar.
- Permiso de Accesibilidad orientado al paquete de Uber Driver (`com.ubercab.driver`).

## Build y tests

```bash
./gradlew assembleDebug            # APK: app/build/outputs/apk/debug/UberMax-1.0.0-debug.apk
./gradlew testDebugUnitTest        # Tests unitarios JVM
./gradlew lintDebug                # Lint de Android (falla con errores)
```

Los tests unitarios son 100% JVM (sin dispositivo) y cubren el parser, el motor de reglas, la
lista negra normalizada, la penalizacion por vuelta vacia, la geometria de poligonos, el
SmartAdvisor, el backup de configuracion, los comandos de voz y el estado de permisos.

## Versionado

Se sigue [SemVer](https://semver.org/lang/es/) (MAJOR.MINOR.PATCH).

- `versionName` = version visible (p. ej. `1.0.0`) y `versionCode` = entero unico por release,
  ambos en `app/build.gradle.kts`.
- Cada version publicable se etiqueta en git como `v<versionName>` (p. ej. `v1.0.0`).
- El tag dispara `release.yml` (tests + lint + build), que adjunta el APK versionado al release.
- Los cambios se registran en [CHANGELOG.md](CHANGELOG.md).

## Contribuciones

1. Crea una rama desde `main`: `git checkout -b feature/nombre-de-la-mejora`.
2. Respeta Clean Architecture: la logica vive en `domain`, los datos en `data`, la UI en `ui`.
3. Anade tests unitarios para todo el comportamiento decisional.
4. Actualiza el changelog y envia un pull request.

## Aviso legal

UberMax es un proyecto independiente y no esta afiliado, respaldado ni patrocinado por Uber
Technologies Inc. Su uso es responsabilidad del conductor y debe cumplir con las politicas de
Uber y la legislacion local. La automatizacion de interacciones puede contravenir los terminos
de uso de plataformas de terceros.

## Licencia

[MIT](LICENSE) &copy; 2026 luisfp112