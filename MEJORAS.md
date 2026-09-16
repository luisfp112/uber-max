**Posibles Mejoras — UberMax**  
Análisis de mejoras organizadas por prioridad y categoría.  
![](data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAnEAAAACCAYAAAA3pIp+AAAABmJLR0QA/wD/AP+gvaeTAAAACXBIWXMAAA7EAAAOxAGVKw4bAAAANklEQVR4nO3OQQmAABRAsSfYxZo/kSGMYQLPJrCCNxG2BFtmZquOAAD4i3Ot7mr/egIAwGvXA4qrBdGuSdJuAAAAAElFTkSuQmCC)  
**CRÍTICAS — Antes de producción**  
**1. Migraciones de Room**  
**Archivo**: data/db/AppDatabase.kt  
fallbackToDestructiveMigration() destruye TODOS los datos del usuario al cambiar el schema. En producción esto significa pérdida de historial, configuración y lista negra.  
**Mejora**: Implementar migraciones explícitas con Migration(fromVersion, toVersion) para cada incremento de versión.  
**2. Seguridad del WakeLock**  
**Archivo**: service/MonitorForegroundService.kt  
El WakeLock se adquiere con un timeout de 10 horas (10 * 60 * 60 * 1000L) pero no hay mecanismo para liberarlo proactivamente si el servicio se detiene inesperadamente. Además, no se valida si el usuario realmente está conduciendo.  
**Mejora**:  
- Liberar el WakeLock en onDestroy() (ya se hace, pero verificar el flujo de excepciones).  
- Añadir un timeout más corto (ej: 4 horas) y re-adquirirlo con cada evento procesado.  
- Considerar detener el monitoreo si no se reciben eventos de Uber Driver por un tiempo prolongado.  
**3. Validación de entrada en Settings**  
**Archivo**: ui/settings/SettingsActivity.kt  
save() hace toDouble() directamente sobre el texto de EditText sin validación previa. Si el campo está vacío o tiene texto no numérico, lanza NumberFormatException que se atrapa genéricamente.  
**Mejora**:  
- Validar cada campo antes de convertir.  
- Mostrar errores específicos por campo (TextInputLayout con error).  
- Deshabilitar el botón "Guardar" si algún campo es inválido.  
**4. Errores silenciosos en SmartAdvisor**  
**Archivo**: domain/ai/SmartAdvisor.kt  
El catch (e: Exception) en analyzeOffer() devuelve lista vacía silenciosamente. Si la DB falla, el sistema piensa que "necesita más datos" cuando en realidad es un error.  
**Mejora**: Distinguir entre "sin datos" y "error de DB". Logear errores reales. Considerar un flag aiAvailable en el decision.  
![](data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAnEAAAACCAYAAAA3pIp+AAAABmJLR0QA/wD/AP+gvaeTAAAACXBIWXMAAA7EAAAOxAGVKw4bAAAANUlEQVR4nO3OMQ2AABAAsSPBCj7fFjsymJHAjAU2QtIq6DIzW7UHAMBfnGt1V8fXEwAAXrsexNkF4H1/HJoAAAAASUVORK5CYII=)  
**ALTA PRIORIDAD**  
**5. Tests para SmartAdvisor**  
**Archivo**: domain/ai/SmartAdvisor.kt  
No tiene tests unitarios. El cálculo de destinationScore y la lógica de recomendación son comportamiento decisional crítico.  
**Mejora**: Añadir tests JVM puros que cubran:  
- Con historial vacío → confianza baja  
- Con historial positivo → recomendación de aceptar  
- Con historial negativo → recomendación de rechazar  
- Con destino desconocido → score neutral (0.5)  
**6. Tests para FloatingWindowService**  
No tiene tests. La lógica de auto-colapso, el mapeo de motivos (mainReason()) y la actualización del HUD son propensos a bugs.  
**Mejora**: Extraer la lógica de mapeo de motivos a una función pura testeable.  
**7. Tests de integración del pipeline completo**  
Los tests actuales cubren componentes individuales pero no el flujo completo: Evento → Parser → Evaluate → RuleEngine → Decisión.  
**Mejora**: Test de integración que simule una secuencia de ofertas y verifique que la cadena completa funciona.  
**8. Exportación CSV**  
**Archivo**: data/repository/TripRepository.kt  
getAllTrips() existe pero no hay UI para exportar. El string export_csv está definido pero no se usa.  
**Mejora**: Añadir botón en Dashboard o Settings que genere un CSV y lo comparta vía Intent.  
**9. Dashboard sin datos reales**  
**Archivo**: ui/dashboard/DashboardActivity.kt  
El Dashboard muestra estado del sistema pero no muestra métricas reales (ganancias del día, ofertas recibidas, etc.) a pesar de que TripRepository tiene todos los métodos necesarios.  
**Mejora**: Añadir un ViewModel que consulte getTodayTrips(), getTodayNetProfit(), getBestHours(), getBestZones() y mostrarlos en la UI.  
**10. Coordenadas de zona hardcoded**  
**Archivo**: ui/blacklist/BlacklistMapActivity.kt  
El mapa siempre se centra en Ambato, Ecuador (-1.2417, -78.6197). No es portable a otras ciudades.  
**Mejora**:  
- Usar la última ubicación conocida del dispositivo (si hay permisos).  
- O permitir al usuario configurar la ciudad base.  
- O empezar en la vista global y hacer zoom a la ubicación del usuario.  
![](data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAnEAAAACCAYAAAA3pIp+AAAABmJLR0QA/wD/AP+gvaeTAAAACXBIWXMAAA7EAAAOxAGVKw4bAAAALUlEQVR4nO3OQQ0AIAwEsAMlSJ0UrOFkGngRklZBR1WtJDsAAPzizNcDAADuNcKwAyU+nb+5AAAAAElFTkSuQmCC)  
**MEDIA PRIORIDAD**  
**11. Room sin índices**  
Las tablas trip_log y blacklist_entry no tienen índices. Con miles de registros, las consultas por destination, hour_of_day o keyword serán lentas.  
**Mejora**: Añadir @Index en columnas consultadas frecuentemente:  
@Entity(tableName = "trip_log", indices = [  
     Index("destination"), Index("hour_of_day"), Index("timestamp"), Index("decision")  
 ])  
   
**12. Configuración de idioma/región**  
**Archivo**: util/RegexPatterns.kt  
Los patrones están calibrados para español ("A X min", "Viaje:"). No funcionan en inglés u otros idiomas.  
**Mejora**:  
- Detectar el idioma del dispositivo.  
- Mantener patrones alternativos para inglés ("In X min", "Trip:").  
- O configurar el idioma de Uber Driver en Settings.  
**13. Patrón de deadhead hardcodeado**  
**Archivo**: domain/usecase/EvaluateOfferUseCase.kt  
El umbral de deadhead (8.0 km) está hardcodeado. No es configurable por el conductor.  
**Mejora**: Añadir campo deadheadThresholdKm a FilterRulesEntity o VehicleConfigEntity.  
**14. Score de IA hardcodeado**  
**Archivo**: domain/ai/SmartAdvisor.kt  
La referencia de "buen profit/km" es $0.25 hardcodeado. Debería derivarse de la configuración del conductor.  
**Mejora**: Usar FilterRulesEntity.minProfitPerKm como referencia en lugar de un valor fijo.  
**15. Falta tipado de errores**  
Varios catch (e: Exception) retornan valores por defecto sin distinguir el tipo de error. Esto dificulta el debugging.  
**Mejora**: Usar sealed class para resultados con error:  
sealed class Result<out T> {  
     data class Success<T>(val data: T) : Result<T>()  
     data class Error(val exception: Exception) : Result<Nothing>()  
 }  
   
**16. GlobalScope implícito**  
**Archivo**: service/UberAccessibilityService.kt  
loadConfig() lanza en serviceScope pero no maneja el resultado. Si falla, la config queda desactualizada silenciosamente.  
**Mejora**:  
- Cachear la config exitosa y usar la última conocida si falla la recarga.  
- Añadir un timeout o retry con backoff.  
**17. Sin linting estático**  
El CI ejecuta testDebugUnitTest y assembleDebug pero no lintDebug.  
**Mejora**: Añadir ./gradlew lintDebug al pipeline de CI y fallar si hay errores.  
**18. ProGuard/R8 sin reglas específicas**  
**Archivo**: app/build.gradle.kts  
isMinifyEnabled = true en release pero no se ven reglas ProGuard personalizadas. Room, Hilt y OSMDroid pueden fallar con R8 si no se configuran las reglas.  
**Mejora**: Verificar/registrar reglas ProGuard para las dependencias principales.  
![](data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAnEAAAACCAYAAAA3pIp+AAAABmJLR0QA/wD/AP+gvaeTAAAACXBIWXMAAA7EAAAOxAGVKw4bAAAANklEQVR4nO3OMQ2AABAAsSNBCkJfFEIwwIgHRiywEZJWQZeZ2ao9AAD+4lyruzq+ngAA8Nr1AOHsBegrsOrIAAAAAElFTkSuQmCC)  
**BAJA PRIORIDAD — UX y pulido**  
**19. Pantalla de historial**  
No hay forma de ver el historial de ofertas dentro de la app. Los datos están en Room pero no se exponen.  
**Mejora**: Añadir una actividad con RecyclerView que muestre trip_log con filtros por día.  
**20. Gráficas de rendimiento**  
TripLogDao tiene consultas para mejores horas y zonas pero no hay visualización gráfica.  
**Mejora**: Añadir gráficas simples (MPAndroidChart o similar) en el Dashboard.  
**21. Notificación de decisión**  
Las decisiones se muestran en el HUD pero no generan notificación. Si el conductor está lejos del teléfono, pierde la info.  
**Mejora**: Opcionalmente mostrar una notificación rápida (Heads-up) con la decisión y el motivo  
**22. Tema claro**  
Actualmente la app solo tiene tema oscuro (colores hardcoded en colors.xml).  
**Mejora**: Añadir tema claro con values-night/colors.xml o Theme.Material3 dynamism.  
**23. Idioma de la app**  
No hay soporte multi-idioma. Todos los strings están en español sin values-en/.  
**Mejora**: Externalizar todos los strings hardcodeados en código a strings.xml y añadir traducciones.  
**24. Animaciones en el HUD**  
El HUD aparece/desaparece sin transiciones.  
**Mejora**: Añadir animaciones de fade/slide para expand/colapso.  
**25. Backup de configuración**  
No hay forma de exportar/importar la configuración del conductor.  
**Mejora**: Añadir exportación a JSON/CSV e importación desde archivo.  
**26. Gestión de múltiples vehículos**  
Solo se permite configurar un vehículo (vehicle_config es singleton).  
**Mejora**: Permitir múltiples perfiles de vehículo con selección rápida.  
**27. Geocodificación inversa para zonas**  
Las BlacklistZoneEntity guardan lat/lng pero la comparación con direcciones es solo por nombre de zona. Si Uber muestra coordenadas en vez de nombre, no matchea.  
**Mejora**: Añadir geocodificación inversa (Nominatim/OSM) para convertir coordenadas a nombre y comparar.  
**28. Control por voz**  
No hay integración con asistentes de voz.  
**Mejora**: Añadir comando de voz "aceptar" / "rechazar" como alternativa al toque.  
![](data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAnEAAAACCAYAAAA3pIp+AAAABmJLR0QA/wD/AP+gvaeTAAAACXBIWXMAAA7EAAAOxAGVKw4bAAAANElEQVR4nO3OUQmAABBAsSeIWMICprwEpjSIFfwTYUuwZWaO6goAgL+412qrzq8nAAC8tj8tdQNNdXaCdAAAAABJRU5ErkJggg==)  
**Resumen por prioridad**  
| | | |  
|-|-|-|  
| **Prioridad** | **Mejoras** | **Esfuerzo** |   
| Crítica | #1, #2, #3, #4 | Medio |   
| Alta | #5-#10 | Medio-Alto |   
| Media | #11-#18 | Variable |   
| Baja | #19-#28 | Bajo-Medio |   
   
