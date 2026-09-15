# Reporte de usuario y oportunidades de mejora — Uber Max

## 1. Resumen general

El usuario realizó una evaluación funcional de **Uber Max** y señaló varios aspectos que deberían mejorarse para que la aplicación sea más clara, robusta, segura y, sobre todo, más automatizada.

El objetivo principal que identifica el usuario para Uber Max es que el conductor pueda **manejar sin tener que interactuar constantemente con el teléfono**, dejando que la aplicación analice cada viaje y decida automáticamente si debe aceptarlo, rechazarlo o postularse para él, de acuerdo con los parámetros previamente configurados.

A partir de su experiencia como conductor, identifica mejoras importantes en cinco áreas principales:

- Configuración de parámetros.
- Interfaz y reducción de información visual innecesaria.
- Automatización de aceptación y rechazo.
- Robustez de la lista negra y seguridad.
- Inteligencia y aprendizaje de la aplicación.

---

# 2. Parámetros de configuración demasiado ambiguos

Uno de los principales problemas identificados es que los datos que actualmente se solicitan al usuario para determinar automáticamente si una carrera es buena, conveniente o si debe aceptarse o rechazarse resultan **demasiado ambiguos**.

El usuario considera que una persona puede no comprender exactamente qué significa cada parámetro ni qué información debe introducir.

### Propuesta

Cada campo debería contar con una **tarjeta o ayuda contextual** que explique claramente:

- Qué significa el parámetro.
- Qué información debe introducir el usuario.
- En qué unidad debe introducirla.
- Cómo influye ese valor en la decisión final.
- Un ejemplo práctico de configuración.

El objetivo es que el usuario pueda configurar correctamente los parámetros sin necesidad de adivinar qué significa cada campo.

### Ejemplo

En lugar de mostrar únicamente:

> Ganancia mínima: ___

La aplicación podría explicar:

> **Ganancia mínima por viaje**  
> Define cuánto dinero mínimo debe generar un viaje para que Uber Max lo considere rentable.  
> **Ejemplo:** si estableces $5, cualquier viaje que genere menos de $5 será rechazado.

Esto permitiría que el sistema tome decisiones mucho más precisas porque los parámetros estarán correctamente configurados.

---

# 3. La aplicación debe ser independiente del vehículo

El usuario señala que actualmente existen referencias específicas al **Chevrolet Spark LT 2008**, lo que hace parecer que Uber Max fue desarrollado exclusivamente para ese vehículo.

Sin embargo, considera que la aplicación debería diseñarse desde el principio de forma **general y escalable**, de manera que pueda funcionar posteriormente con cualquier automóvil.

### Propuesta

La configuración del vehículo debería ser independiente de la lógica principal de Uber Max.

Por ejemplo:

- Marca.
- Modelo.
- Año.
- Cilindraje.
- Consumo aproximado.
- Combustible.
- Costo estimado por kilómetro.

De esta manera, el sistema puede utilizar las características del vehículo para calcular la rentabilidad, pero la aplicación no queda limitada a un modelo específico.

También deberían eliminarse de la interfaz textos o referencias como:

> "Samsung Galaxy A32"

cuando la intención de Uber Max es funcionar en **cualquier teléfono Android compatible**.

---

# 4. Exceso de información en pantalla

El usuario considera que actualmente existe **aglomeración visual**.

Cuando aparece una carrera, Uber Max muestra demasiada información en la pantalla. Desde el punto de vista del usuario, esto no es conveniente porque el conductor está manejando y no debería tener que leer grandes cantidades de texto.

La información mostrada debe ser mucho más simple.

## Información principal que debería mostrarse

Cuando aparece una carrera, el usuario propone mostrar únicamente:

- **Precio por kilómetro.**
- **Destino/dirección.**
- **Decisión:** Aceptar / Rechazar.
- **Motivo principal de la decisión.**

Por ejemplo:

> **$0,85/km**  
> 📍 Av. X — Sector Y  
>   
> ✅ **ACEPTADO**  
> Rentabilidad suficiente

O:

> **$0,42/km**  
> 📍 Calle X — Sector Y  
>   
> ❌ **RECHAZADO**  
> Zona incluida en lista negra

La aplicación puede realizar internamente todos los cálculos necesarios, pero **no debería mostrar al conductor todo el proceso de análisis**.

La lógica debe ser compleja internamente y la interfaz debe ser simple externamente.

---

# 5. Mostrar claramente por qué una carrera fue rechazada

El usuario considera importante que, cuando Uber Max rechace una carrera, se indique de forma clara el motivo.

Especialmente deberían destacarse situaciones críticas como:

- Zona peligrosa.
- Calle incluida en lista negra.
- Rentabilidad insuficiente.
- Distancia excesiva.
- Ganancia por kilómetro insuficiente.
- Cualquier otro parámetro que haya provocado el rechazo.

Sin embargo, no se debería mostrar una lista extensa de todas las reglas evaluadas.

La interfaz debería destacar únicamente **la razón principal de la decisión**.

---

# 6. Problema con las carreras "Viaje disponible"

El usuario reporta un comportamiento diferente dependiendo del tipo de carrera que aparece en Uber.

### Caso 1: carrera asignada directamente

Cuando un usuario solicita un viaje y Uber lo asigna directamente al conductor, la tarjeta de Uber muestra:

> **Aceptar**

En este caso, Uber Max funciona correctamente:

1. Detecta la carrera.
2. Analiza los parámetros.
3. Determina que debe aceptarse.
4. Ejecuta automáticamente la interacción correspondiente.
5. La carrera queda aceptada.

El usuario considera que este flujo funciona correctamente.

### Caso 2: "Viaje disponible"

El problema aparece cuando Uber muestra:

> **Viaje disponible**

En este escenario, aunque Uber Max determine que la carrera es buena y debería aceptarse, actualmente la aplicación únicamente cambia su propia interfaz y muestra:

> **Aceptado**

pero **no realiza realmente la interacción sobre la aplicación de Uber**.

Es decir, Uber Max determina correctamente la decisión, pero no ejecuta el gesto correspondiente en el teléfono.

### Requerimiento

Cuando una carrera de tipo **"Viaje disponible"** sea considerada aceptable, Uber Max debe:

1. Analizar la carrera.
2. Determinar que debe aceptarse.
3. Interactuar realmente con la pantalla de Uber.
4. Ejecutar el gesto/tap correspondiente.
5. Confirmar que la acción se realizó correctamente.

No debe limitarse a mostrar "Aceptado" dentro de Uber Max si la carrera todavía no fue aceptada realmente en Uber.

---

# 7. Bug crítico: rechazo de carreras de la lista negra

El usuario identifica este punto como el **problema más crítico de toda la aplicación**, debido al riesgo de seguridad que representa para el conductor.

La lista negra está diseñada para que Uber Max rechace automáticamente determinadas carreras.

Sin embargo, debido a que en determinados casos Uber no presenta un botón explícito de "Rechazar", la aplicación debería utilizar la **X disponible en la tarjeta de Uber** para descartar la carrera.

Actualmente, según el usuario, esta interacción no está funcionando correctamente.

Esto significa que una carrera que debería ser rechazada automáticamente puede permanecer disponible para el conductor.

El usuario considera esto especialmente grave porque una decisión incorrecta puede enviarlo a zonas donde existe riesgo de:

- Robo del vehículo.
- Secuestro.
- Agresión.
- Pérdida de la vida.

Por lo tanto, esta funcionalidad debe tratarse como una **prioridad crítica de seguridad**.

---

# 8. La lista negra actualmente no evalúa correctamente los sectores

El usuario también reporta un problema con la forma en que se configura la lista negra.

Actualmente existe un mapa en el que se pueden seleccionar sectores o zonas.

Sin embargo, según su experiencia, estos sectores seleccionados en el mapa **no están siendo evaluados correctamente al analizar una carrera**.

El problema es que Uber no necesariamente proporciona a Uber Max la misma información geográfica de la forma en que el usuario la configuró en el mapa.

Por ejemplo, en la tarjeta de Uber puede aparecer:

> Calle X, Sector Y

y esa información textual podría ser la que realmente debe utilizarse para determinar si el destino pertenece a una zona peligrosa.

---

# 9. Propuesta: hacer la lista negra basada en nombres reales de calles y sectores

El usuario propone eliminar o replantear el sistema actual basado exclusivamente en el mapa.

Su propuesta consiste en investigar exactamente:

- Cómo muestra Uber las direcciones.
- Qué nombres de calles aparecen.
- Qué nombres de sectores aparecen.
- Cómo aparecen escritos.
- Qué variaciones de nombres utiliza Uber.

Posteriormente, esos nombres podrían utilizarse directamente en la lista negra.

Por ejemplo:

> Calle X  
> Sector Y  
> Barrio Z  
> Av. Principal

De esta manera, cuando Uber Max detecte el texto correspondiente en una carrera, podrá compararlo contra la lista negra.

Esto permitiría construir una lista negra **mucho más robusta y compatible con la información real que aparece en Uber**.

El usuario considera que esta sección debe recibir especial atención porque está directamente relacionada con la seguridad del conductor.

---

# 10. Uber Max debería ser más inteligente

El usuario considera que Uber Max no debería limitarse a aplicar reglas estáticas.

En el futuro, la aplicación debería ser capaz de **analizar patrones y aprender del comportamiento del conductor**.

Una de las ideas mencionadas es utilizar información como:

- Historial de carreras.
- Zonas donde normalmente trabaja el conductor.
- Rentabilidad histórica.
- Distancia recorrida.
- Precio por kilómetro.
- Horarios.
- Sectores con mayor rentabilidad.
- Zonas que el conductor suele evitar.
- Comportamiento histórico de las carreras.

Con estos datos, Uber Max podría generar recomendaciones.

Por ejemplo:

> **Zona recomendada**  
> En esta zona históricamente has obtenido mejores ganancias por km.

---

# 11. Integración con el mapa de calor de Uber

El usuario también considera interesante aprovechar el **mapa de calor que ofrece Uber**.

Uber Max podría analizar las zonas de alta demanda y combinarlas con el historial del conductor.

De esta manera, la aplicación podría sugerir:

> **Zona recomendada para dirigirte**

considerando simultáneamente:

- Demanda actual.
- Historial de ganancias.
- Distancia.
- Tiempo.
- Ubicación actual.
- Preferencias del conductor.
- Lista negra.
- Rentabilidad esperada.

Esto convertiría a Uber Max en una herramienta de asistencia inteligente y no únicamente en un sistema de aceptación/rechazo automático.

---

# 12. Simplificar la pantalla después de aceptar una carrera

Cuando Uber Max determina que una carrera debe aceptarse, el usuario considera útil conocer brevemente **por qué fue aceptada**, pero sin generar ruido visual.

Por ejemplo:

> ✅ **ACEPTADO**  
> $0,85/km  
> Rentabilidad: buena

La información debería permanecer visible únicamente durante un período corto.

Después, la interfaz debería **limpiarse y contraerse automáticamente** para evitar distraer al conductor.

---

# 13. Utilizar el tiempo estimado de llegada de Uber

El usuario propone una mejora adicional:

Uber Max debería detectar el **tiempo estimado de llegada mostrado por Uber** y utilizarlo para controlar cuánto tiempo permanece visible la información de decisión.

Por ejemplo:

Si Uber indica que faltan 8 minutos para llegar al destino, Uber Max podría mantener inicialmente la información relevante y posteriormente reducirla o limpiarla cuando el conductor esté próximo a llegar.

La interfaz podría funcionar de forma dinámica:

### Al aceptar

> ✅ ACEPTADO  
> $0,85/km  
> Ganancia neta: $4,20

### Durante el viaje

La información podría reducirse progresivamente.

### Al acercarse al destino

La interfaz podría contraerse o desaparecer automáticamente.

El objetivo es que la pantalla no permanezca permanentemente llena de información que ya no es necesaria.

---

# 14. Mostrar la ganancia neta

El usuario considera especialmente útil que, cuando una carrera sea aceptada, Uber Max muestre la **ganancia neta calculada por la aplicación**.

No solamente el precio total del viaje, sino lo que realmente representa como ganancia después de considerar los costos configurados.

Por ejemplo:

> **Ganancia neta estimada: $4,20**

Esto permitiría al conductor saber rápidamente si el viaje realmente es rentable.

---

# 15. Mejorar el tamaño de la fuente

El usuario considera que la fuente actual es demasiado pequeña.

Esto representa un problema importante porque el conductor está manejando y no debería:

- Acercarse demasiado al teléfono.
- Desviar demasiado la mirada.
- Intentar leer textos pequeños.
- Manipular constantemente la pantalla.

La interfaz debería diseñarse considerando explícitamente un **entorno de conducción**.

Por lo tanto, se recomienda:

- Aumentar el tamaño de la fuente.
- Utilizar información corta.
- Priorizar elementos visuales grandes.
- Evitar párrafos.
- Utilizar indicadores claros.
- Reducir la cantidad de elementos simultáneos.

---

# 16. Principio fundamental de Uber Max: mínima interacción del conductor

El usuario plantea que el objetivo final de Uber Max debería ser que el conductor **no tenga que interactuar prácticamente con el teléfono mientras conduce**.

El flujo ideal sería:

**Uber recibe una carrera → Uber Max la detecta → analiza todos los parámetros → decide → ejecuta automáticamente la acción → informa brevemente al conductor → limpia la interfaz.**

El conductor debería poder concentrarse exclusivamente en conducir.

Uber Max debería encargarse automáticamente de:

- Analizar la carrera.
- Calcular rentabilidad.
- Evaluar distancia.
- Evaluar precio por km.
- Evaluar zonas.
- Consultar lista negra.
- Determinar aceptación o rechazo.
- Ejecutar el gesto correspondiente.
- Confirmar la acción.
- Mostrar brevemente el resultado.
- Limpiar posteriormente la interfaz.

La complejidad debe permanecer **detrás de escena**.

---

# 17. Compatibilidad con cualquier teléfono

Finalmente, el usuario identifica varias referencias específicas a:

> Samsung Galaxy A32

Considera que estas referencias no deberían existir en una aplicación que pretende ser general.

Uber Max debe estar diseñado para funcionar independientemente del modelo específico del teléfono.

La aplicación debería evitar:

- Textos específicos de un modelo.
- Resoluciones diseñadas exclusivamente para un dispositivo.
- Coordenadas de interacción dependientes de un único teléfono.
- Elementos que solamente funcionen correctamente en el Samsung Galaxy A32.

La automatización debería adaptarse a diferentes tamaños, resoluciones y relaciones de aspecto.

---

# 18. Prioridades recomendadas

A partir de todo el feedback recibido, se recomienda priorizar el desarrollo de la siguiente manera:

### 🔴 Prioridad crítica — Seguridad

1. Corregir el rechazo automático mediante la **X de Uber**.
2. Garantizar que la lista negra realmente se evalúe.
3. Validar que calles y sectores mostrados por Uber sean reconocidos.
4. Evitar que una carrera de una zona peligrosa pueda ser aceptada accidentalmente.

### 🟠 Prioridad alta — Automatización

5. Corregir la interacción con las carreras **"Viaje disponible"**.
6. Garantizar que Uber Max no solamente muestre "Aceptado", sino que ejecute realmente el tap.
7. Confirmar que la acción realizada sobre Uber fue exitosa.

### 🟡 Prioridad alta — Interfaz

8. Reducir drásticamente la cantidad de información mostrada.
9. Aumentar el tamaño de la fuente.
10. Mostrar únicamente información relevante para el conductor.
11. Mostrar claramente el motivo principal de aceptación/rechazo.
12. Contraer o limpiar automáticamente la interfaz después de un período determinado.

### 🟢 Prioridad media — Configuración

13. Explicar cada parámetro mediante tarjetas de información.
14. Añadir ejemplos de configuración.
15. Separar la configuración del vehículo de la lógica general de la aplicación.
16. Eliminar referencias específicas al Spark LT 2008 y Samsung Galaxy A32.

### 🔵 Futuro — Inteligencia

17. Analizar historial del conductor.
18. Analizar zonas rentables.
19. Integrar información del mapa de calor de Uber.
20. Generar recomendaciones de zonas.
21. Construir un sistema progresivamente más inteligente basado en los patrones del conductor.

---

# 19. Conclusión

El feedback del usuario deja claro que Uber Max debe evolucionar desde una aplicación que **muestra información y toma decisiones** hacia una herramienta que **ejecuta automáticamente esas decisiones con la mínima intervención posible del conductor**.

El concepto central debería ser:

> **La aplicación debe pensar mucho internamente y mostrar muy poco externamente.**

La prioridad inmediata debe ser garantizar que las decisiones críticas —especialmente las relacionadas con la **lista negra y las zonas peligrosas**— se ejecuten correctamente sobre Uber.

Posteriormente, el enfoque debería centrarse en simplificar la interfaz, aumentar la legibilidad y hacer que el sistema sea independiente tanto del vehículo como del teléfono.

Finalmente, Uber Max podría evolucionar hacia un sistema inteligente capaz de analizar el historial del conductor, las condiciones actuales y las zonas de demanda para no solamente decidir **qué viajes aceptar o rechazar**, sino también **qué zonas conviene buscar y dónde es más probable obtener una buena rentabilidad**.

El objetivo final debe ser que el conductor pueda **conducir y dejar que Uber Max haga el trabajo de análisis y decisión automáticamente**.