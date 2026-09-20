# Changelog

Todas las versiones notables de UberMax se documentan aquí.
Formato basado en [Keep a Changelog](https://keepachangelog.com/es/1.1.0/) y [SemVer](https://semver.org/lang/es/).

## [1.1.0] - 2026-09-20

### Añadido

- Reconocimiento de la variante A/B de Uber Driver: ofertas abiertas con botón
  "Me interesa" además de "Viaje disponible" / "Postularse" (parser + tap + tests).
- HUD flotante: dirección de destino completa y prominente (2 líneas) junto al motivo.

### Mejoras

- Releases profesionales: `assembleRelease` firmado con keystore de release dedicado
  (`keystore/ubermax-release.jks`) y artefacto limpio `UberMax-1.1.0.apk`.
- Depuración en dispositivo: hook de simulación de ofertas (solo build debug) para
  inyectar tarifas de prueba por adb sin tocar Uber.
- Dashboard: refresco de estado corregido al iniciar/detener el monitoreo.

## [1.0.0] - 2026-09-17

Versión inicial publicada.

### Añadido

- Automatización del pipeline de ofertas de Uber Driver:
  `OfferParser` -> `EvaluateOfferUseCase` -> `RuleEngine` (ACCEPT / WARN / CANCEL) -> `SmartAdvisor` -> `ActionExecutor`.
- Análisis económico por viaje: tarifa bruta, ganancia neta (combustible + mantenimiento), $/km, $/hora y penalización por vuelta vacía (deadhead configurable).
- Métrica principal de rentabilidad configurable (`PER_HOUR` / `PER_KM`).
- Lista negra por texto con normalización (mayúsculas, tildes y símbolos) y por zonas geográficas dibujadas en mapa OSMDroid.
- Geocodificación inversa (Nominatim) para extraer keywords de calles/sectores desde un polígono.
- HUD flotante (foreground service) con auto-colapso y motivo de decisión abreviado.
- Notificaciones con vibración/sonido por decisión y modo simulación (dry-run).
- Historial de ofertas con filtros temporales y gráfico de ganancia por hora.
- Panel de control con métricas del día y exportación a CSV.
- Onboarding de permisos (accesibilidad, notificaciones, batería, overlay, ubicación, micrófono, Bluetooth).
- Control por voz ("aceptar" / "rechazar") y auto-arranque por reinicio o Bluetooth.
- Backup/restore de configuración (JSON) con importación/exportación.
- SmartAdvisor local (IA basada en historial) con puerto `TripHistorySource` y resultado tipado.

### Mejoras

- Estándares de código aplicados: coroutines cancellation-safe (reseñalar `CancellationException`), colección de StateFlow con `repeatOnLifecycle`, escritura de archivos fuera del hilo principal, helpers duplicados eliminados.
- Artefacto versionado: `UberMax-<versión>-<buildType>.apk`.

## [Sin publicar]

- Releases publicadas mediante tags `vX.Y.Z` y GitHub Actions (`release.yml`).