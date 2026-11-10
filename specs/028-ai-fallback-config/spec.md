# Feature Specification: Configuración y Resiliencia del Fallback de IA

**Feature Branch**: `feature/028-ai-fallback-config` | **Created**: 2026-10-22 | **Status**: Draft (refinada 2026-11-09; pendiente de aprobación humana) | **Issue**: #79  
**Input**: Como Admin / operador de la plataforma, quiero configurar sin recompilar el orden de los modelos de IA de respaldo, sus timeouts, reintentos y circuit breakers, y ver qué proveedor está respondiendo, para controlar coste, latencia y disponibilidad de las funciones de IA en cada entorno.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Configuración declarativa de cadenas de fallback y validación temprana al arranque (Priority: P1)

Como operador de infraestructura y plataforma, quiero definir en la configuración de la aplicación la lista ordenada de proveedores de respaldo por capacidad (`chat`, `vision`, `stt`, `tts`, `rag`) con sus timeouts y reintentos, pudiendo sobreescribirlos mediante variables de entorno, y con validación estricta al arranque para evitar fallos silenciosos en producción.

**Why this priority**: Es el núcleo funcional indispensable (MVP). Permite desacoplar los modelos de IA del código compilado, estructurar formalmente las cadenas de fallback jerárquicas y garantizar que cualquier inconsistencia de configuración aborte inmediatamente el arranque del servicio (*fail-fast*), protegiendo la integridad operativa.

**Independent Test**: Arrancar el servicio con distintas configuraciones (por defecto, sobreescritura por variables de entorno y combinaciones inválidas como cadena vacía, timeouts negativos o proveedores desconocidos). Verificar que las propiedades se cargan correctamente o que el contexto falla inmediatamente al boot con mensajes claros y descriptivos.

**Acceptance Scenarios**:
1. **Scenario 1.1 (Arranque con configuración válida por defecto)**:  
   *Given* la configuración estándar de la aplicación con el prefijo `app.ai.fallback.*`  
   *When* la aplicación inicia su ciclo de vida  
   *Then* las cadenas de fallback para `chat`, `vision`, `stt`, `tts` y `rag` se cargan como listas ordenadas con proveedor, modelo, estado habilitado y timeout en segundos.
2. **Scenario 1.2 (Sobreescritura declarativa mediante variable de entorno)**:  
   *Given* la variable de entorno `AI_CHAT_FALLBACK_CHAIN=gemini:gemini-3.8-flash,openai:gpt-4o-mini` configurada en el sistema  
   *When* la aplicación inicializa  
   *Then* la cadena efectiva para la capacidad `chat` establece a Gemini como proveedor primario y a OpenAI como respaldo secundario con sus respectivos modelos.
3. **Scenario 1.3 (Compatibilidad regresiva con configuración legacy)**:  
   *Given* un entorno donde no se configuran las nuevas variables de fallback pero están presentes las variables heredadas `AI_PROVIDER=gemini` y `AI_AUDIO_PROVIDER=gemini`  
   *When* la aplicación inicializa  
   *Then* el sistema genera automáticamente una cadena de fallback por defecto manteniendo a Gemini como proveedor activo sin interrumpir el servicio ni requerir cambios manuales inmediatos.
4. **Scenario 1.4 (Fallo al arranque por timeout inválido o proveedor desconocido)**:  
   *Given* una configuración o variable de entorno donde se define un `timeout-seconds` menor o igual a cero, o un nombre de proveedor no reconocido por el sistema  
   *When* la aplicación intenta arrancar  
   *Then* el arranque se detiene de inmediato (*fail-fast*) con una excepción explicativa detallando el campo erróneo y el valor rechazado.
5. **Scenario 1.5 (Fallo al arranque por cadena vacía)**:  
   *Given* una capacidad activa cuya cadena de proveedores de respaldo está vacía o carece de proveedores habilitados  
   *When* la aplicación intenta arrancar  
   *Then* el arranque se detiene de inmediato indicando que la capacidad requiere al menos un proveedor disponible.

---

### User Story 2 - Aislamiento y resiliencia de proveedores mediante Circuit Breaker (Priority: P2)

Como operador de la plataforma, quiero que el sistema aísle automáticamente a los proveedores que presenten fallos repetidos o degradación severa mediante un circuit breaker por proveedor/modelo, para que las peticiones subsecuentes los omitan instantáneamente durante el periodo de enfriamiento sin incurrir en penalizaciones de timeout ni degradar la experiencia de usuario.

**Why this priority**: Protege la disponibilidad global del sistema y la latencia percibida por el usuario final, evitando que peticiones en curso queden bloqueadas esperando timeouts sucesivos cuando un proveedor externo sufre una caída masiva.

**Independent Test**: Simular fallos consecutivos en el proveedor primario hasta superar el umbral configurado (`failure-rate-threshold`), verificar la transición del circuito a estado ABIERTO (`OPEN`), constatar que las llamadas subsiguientes se delegan de inmediato al siguiente proveedor de la cadena sin consumir tiempo de espera, y validar la transición a SEMI-ABIERTO (`HALF-OPEN`) y CERRADO (`CLOSED`) al recuperarse el proveedor.

**Acceptance Scenarios**:
1. **Scenario 2.1 (Apertura del circuito ante fallos continuos)**:  
   *Given* un proveedor en la cadena que supera el umbral porcentual de fallos dentro de la ventana de llamadas configurada  
   *When* entra una nueva solicitud que requiere dicha capacidad  
   *Then* el estado del circuito para ese proveedor/modelo transiciona a `OPEN` y la llamada se deriva de inmediato al siguiente proveedor habilitado sin esperar el timeout del proveedor degradado.
2. **Scenario 2.2 (Salto inmediato de proveedor con circuito abierto)**:  
   *Given* un proveedor con su circuito en estado `OPEN` y tiempo de espera remanente  
   *When* se despacha una solicitud para esa capacidad  
   *Then* el sistema omite el proveedor degradado en menos de 5 ms y ejecuta la llamada directamente sobre el siguiente eslabón disponible.
3. **Scenario 2.3 (Prueba de recuperación en Half-Open y cierre)**:  
   *Given* un circuito en estado `OPEN` cuyo tiempo de apertura (`wait-duration-in-open-state`) ha transcurrido  
   *When* se recibe una nueva solicitud  
   *Then* el circuito transiciona a `HALF-OPEN`, permite un número controlado de llamadas de sondeo y, si resultan exitosas, transiciona de vuelta a `CLOSED`.
4. **Scenario 2.4 (Reintentos controlados con backoff exponencial)**:  
   *Given* un error transitorio recuperable en la invocación a un proveedor  
   *When* el número de intentos ejecutados es menor que `max-attempts`  
   *Then* el sistema aplica un retardo calculado con el multiplicador de backoff configurado antes de reintentar o derivar al respaldo.

---

### User Story 3 - Telemetría, métricas de resiliencia y endpoint administrativo de inspección (Priority: P3)

Como administrador del sistema, quiero disponer de métricas estandarizadas en Micrometer (exportables a Prometheus), trazabilidad distribuida con `X-Correlation-Id`, y un endpoint administrativo restringido a `ROLE_ADMIN` para inspeccionar la cadena efectiva y el estado de los circuitos, sin exponer credenciales ni claves de API en logs o payloads.

**Why this priority**: Garantiza la gobernanza operativa, monitoreo continuo de disponibilidad y costos, diagnóstico en vivo y cumplimiento de las directivas de seguridad sin filtración de secretos.

**Independent Test**: Invocar el endpoint administrativo `/api/admin/ai/providers` con usuarios autenticados (con y sin `ROLE_ADMIN`) y anónimos; consultar el endpoint de métricas Prometheus comprobando los medidores `ai.fallback.attempts` y `ai.fallback.provider.used`; inspeccionar logs estructurados asegurando presencia de `X-Correlation-Id` y ausencia total de secretos.

**Acceptance Scenarios**:
1. **Scenario 3.1 (Consulta autorizada de la cadena efectiva y circuitos)**:  
   *Given* un usuario autenticado con el rol `ROLE_ADMIN`  
   *When* realiza una petición `GET` a `/api/admin/ai/providers`  
   *Then* el sistema responde con código HTTP 200 y un JSON detallando por capacidad: orden de la cadena, proveedor y modelo configurados, timeout y el estado actual de cada circuito (`CLOSED`, `OPEN`, `HALF_OPEN`), sin incluir claves ni secretos.
2. **Scenario 3.2 (Rechazo de acceso no autorizado al endpoint administrativo)**:  
   *Given* un usuario no autenticado o autenticado con rol `ROLE_CUSTOMER` o `ROLE_PROVIDER`  
   *When* intenta acceder a `GET /api/admin/ai/providers`  
   *Then* la solicitud es rechazada con código HTTP 401 Unauthorized o HTTP 403 Forbidden según corresponda.
3. **Scenario 3.3 (Registro de métricas de telemetría en Micrometer)**:  
   *Given* la ejecución de solicitudes de IA que utilizan proveedores primarios o de respaldo  
   *When* se inspecciona el endpoint de métricas `/actuator/prometheus`  
   *Then* se registran los contadores `ai.fallback.attempts` (etiquetados por capacidad, proveedor y resultado) y `ai.fallback.provider.used` (reflejando el proveedor final que resolvió con éxito).
4. **Scenario 3.4 (Trazabilidad y seguridad de secretos en logs)**:  
   *Given* una solicitud entrante con cabecera `X-Correlation-Id`  
   *When* el sistema procesa transiciones, reintentos o saltos de fallback  
   *Then* cada registro de log incluye el identificador `X-Correlation-Id` y no contiene en ningún caso claves de API, tokens o información confidencial.

---

### Edge Cases

- **Agotamiento total de la cadena (All Providers Down)**: Cuando todos los proveedores de una capacidad se encuentran con el circuito abierto o fallan por timeout/error, el sistema debe abortar el ciclo de fallback de forma controlada y devolver un error formal tipado (`AiProviderException` con estado de indisponibilidad), evitando bucles infinitos.
- **Configuración mixta o duplicada**: Si se provee tanto la variable simplificada de entorno (ej. `AI_CHAT_FALLBACK_CHAIN`) como propiedades específicas por índice en YAML/env vars, la variable de entorno explícita tiene precedencia y se loguea una advertencia de sobreescritura.
- **Timeout en el límite exacto de la ventana del Circuit Breaker**: Si un proveedor responde justo en el límite del timeout configurado, la llamada debe computarse como fallo de timeout en el Circuit Breaker de forma atómica y sin condiciones de carrera entre hilos.
- **Valores con espacios o separadores redundantes en variables de entorno**: El sistema debe tolerar y normalizar espacios en blanco accidentales (ej. `gemini:gemini-3.8-flash , openai:gpt-4o-mini `) durante el parsing de cadenas compactas.
- **Reinicio con proveedor caído**: Si un proveedor está indisponible al arrancar la aplicación, el servicio arranca normalmente (si la configuración sintáctica es válida) y el circuito abre tras las primeras fallas de sondeo en ejecución.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE exponer una configuración tipada y jerárquica bajo el prefijo `app.ai.fallback.*` estructurada por capacidades: `chat`, `vision`, `stt`, `tts` y `rag`.
- **FR-002**: El sistema DEBE permitir definir para cada capacidad una lista ordenada de respaldo (`chain`) compuesta por los atributos `{provider, model, enabled, timeout-seconds}`.
- **FR-003**: El sistema DEBE permitir configurar por capacidad los parámetros de resiliencia: `max-attempts` (número máximo de intentos), `retry-backoff` (tiempo y multiplicador de reintento) y `circuit-breaker` (umbral de tasa de fallos, tamaño de ventana deslizante y tiempo de espera en estado abierto).
- **FR-004**: El sistema DEBE admitir la sobreescritura de todas las propiedades de fallback mediante variables de entorno en tiempo de despliegue, incluyendo un formato simplificado por capacidad (ej. `AI_CHAT_FALLBACK_CHAIN=gemini:gemini-3.8-flash,openai:gpt-4o-mini`).
- **FR-005**: El sistema DEBE mantener retrocompatibilidad total con las variables de configuración heredadas `AI_PROVIDER` y `AI_AUDIO_PROVIDER`, utilizándolas como valor primario de la cadena cuando la configuración avanzada de fallback no sea provista explícitamente.
- **FR-006**: El sistema DEBE validar de forma estricta (*fail-fast*) durante el arranque la integridad de la configuración: si una cadena de fallback resulta vacía para una capacidad activa, contiene un proveedor no registrado/soportado o especifica un timeout menor o igual a cero, la aplicación DEBE detener su inicio emitiendo un mensaje de error explicativo en consola y logs.
- **FR-007**: El sistema DEBE gestionar una instancia de Circuit Breaker independiente por cada tupla `provider:model` dentro de cada capacidad.
- **FR-008**: El sistema DEBE saltar de forma inmediata a los proveedores cuyo Circuit Breaker se encuentre en estado ABIERTO (`OPEN`), delegando la invocación al siguiente proveedor habilitado de la cadena sin consumir tiempo de espera ni generar llamadas a la red.
- **FR-009**: El sistema DEBE registrar métricas estandarizadas a través de Micrometer: `ai.fallback.attempts` (contador de intentos por capacidad, proveedor y resultado), `ai.fallback.provider.used` (contador del proveedor que resolvió exitosamente la solicitud) y el estado dinámico del circuito (`ai.fallback.circuit.state`).
- **FR-010**: El sistema DEBE registrar en los logs de aplicación el flujo de fallback vinculando cada evento al identificador de correlación `X-Correlation-Id` extraído del contexto o generado para la solicitud.
- **FR-011**: El sistema DEBE garantizar que ninguna clave de API, secreto o credencial sea impresa en los logs, traces o respuestas HTTP de error o diagnóstico.
- **FR-012**: El sistema DEBE proveer un endpoint administrativo `GET /api/admin/ai/providers` restringido exclusivamente a usuarios con rol `ROLE_ADMIN` que exponga la cadena de fallback efectiva y el estado operativo de cada proveedor y circuit breaker.
- **FR-013**: El sistema DEBE rechazar cualquier petición no autenticada al endpoint administrativo con código HTTP 401 Unauthorized y peticiones autenticadas sin `ROLE_ADMIN` con código HTTP 403 Forbidden.
- **FR-014**: El sistema DEBE documentar las nuevas variables de entorno en `.env.example`, `docker-compose.yml` y `docs/INTEGRATIONS.md`, utilizando únicamente valores ficticios o placeholders seguros.
- **FR-015**: El sistema DEBE contar con una suite de pruebas automatizadas que valide el enlace y validación de propiedades, apertura y cierre del circuit breaker, y seguridad RBAC del endpoint administrativo.

---

### Key Entities

- **FallbackCapabilityConfig**: Configuración por capacidad (`chat`, `vision`, `stt`, `tts`, `rag`). Contiene la lista ordenada de eslabones (`chain`), la configuración de reintentos (`RetryConfig`) y la configuración del interruptor de circuito (`CircuitBreakerConfig`).
- **FallbackChainElement**: Representa un eslabón ordenado en la cadena de fallback. Atributos: `provider` (nombre del proveedor, p. ej. `gemini`, `openai`, `groq`), `model` (identificador del modelo, p. ej. `gemini-3.8-flash`, `gpt-4o-mini`), `enabled` (booleano), `timeoutSeconds` (entero positivo mayor a cero).
- **CircuitBreakerConfig**: Parámetros de resiliencia del interruptor: `failureRateThreshold` (porcentaje de fallos para abrir el circuito), `slidingWindowSize` (número de llamadas evaluadas), `waitDurationInOpenStateSeconds` (segundos de enfriamiento en estado OPEN), `permittedNumberOfCallsInHalfOpenState` (llamadas de prueba en estado HALF-OPEN).
- **RetryConfig**: Parámetros de reintentos: `maxAttempts` (número máximo de intentos permitidos), `initialIntervalMs` (intervalo base de espera), `multiplier` (factor multiplicador para retroceso exponencial).
- **ProviderCircuitStatus**: Estado operativo en tiempo de ejecución de un proveedor/modelo: `capability`, `provider`, `model`, `circuitState` (`CLOSED`, `OPEN`, `HALF_OPEN`), `failureRate`, `lastFailureTimestamp`.
- **EffectiveChainSnapshot**: Representación administrativa de lectura que expone la cadena efectiva resuelta por capacidad, proveedores activos y estado actual de salud.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de las variables y listas de fallback pueden ser configuradas y sobreescritas mediante variables de entorno o archivos de configuración sin requerir recompilación ni empaquetado del código.
- **SC-002**: El 100% de los despliegues preexistentes que dependan exclusivamente de `AI_PROVIDER` y `AI_AUDIO_PROVIDER` arrancan y operan con normalidad manteniendo su proveedor primario activo (compatibilidad regresiva total).
- **SC-003**: El tiempo de detección de configuraciones inválidas durante el arranque es inferior a 5 segundos desde el inicio del proceso, abortando el boot con código de salida no cero y diagnóstico explícito en consola/logs.
- **SC-004**: Cuando un proveedor se encuentra con su circuito en estado ABIERTO (`OPEN`), el tiempo de delegación al siguiente proveedor en la cadena es inferior a 5 milisegundos (evitando el 100% del tiempo de timeout del proveedor caído).
- **SC-005**: El 100% de las invocaciones a modelos de IA emiten las métricas `ai.fallback.attempts` y `ai.fallback.provider.used` exportadas vía Prometheus y mantienen el `X-Correlation-Id` en cada traza de log.
- **SC-006**: El 100% de los intentos de acceso al endpoint administrativo `GET /api/admin/ai/providers` por parte de clientes anónimos o sin rol `ROLE_ADMIN` son bloqueados con códigos HTTP 401 o 403 en menos de 50 milisegundos.
- **SC-007**: Cero por ciento (0%) de secretos, tokens o API keys aparecen en texto plano en logs, métricas o respuestas del endpoint de diagnóstico administrativo.
- **SC-008**: La suite de pruebas unitarias y de integración cubre al menos el 90% de ramas y líneas de las clases de configuración, validación de propiedades y lógica de circuit breaker.

---

## Assumptions

### Hechos Verificados en el Código Base (`[VERIFIED]`)
- `[VERIFIED: backend/src/main/resources/application.yml:133-145]`: La configuración de IA actual reside en `app.ai` con `provider: ${AI_PROVIDER:gemini}`, `audio-provider: ${AI_AUDIO_PROVIDER:gemini}`, `timeout-seconds: ${AI_TIMEOUT_SECONDS:10}`, `gemini.enabled` y `openai.enabled`.
- `[VERIFIED: backend/src/main/java/com/riwi/skillbridge/infrastructure/config/AiProviderProperties.java:6-13]`: La clase `AiProviderProperties` está anotada con `@ConfigurationProperties(prefix = "app.ai")` y sirve de raíz para estructurar las nuevas propiedades de fallback.
- `[VERIFIED: backend/src/main/java/com/riwi/skillbridge/infrastructure/config/SecurityConfiguration.java:37-38,82-90]`: El backend utiliza Spring Security 6 con `@EnableMethodSecurity`. Los endpoints `/actuator/health` y `/actuator/health/**` tienen acceso público (`permitAll()`), mientras que las rutas protegidas requieren autenticación y roles mediante `@PreAuthorize("hasRole('ADMIN')")`.
- `[VERIFIED: backend/src/main/java/com/riwi/skillbridge/application/service/AiRecommendationService.java:36-37]`: Se utiliza `CorrelationIdHolder.get()` para propagar el `X-Correlation-Id` en las operaciones y eventos de IA.
- `[VERIFIED: backend/src/main/java/com/riwi/skillbridge/domain/exception/AiProviderException.java:13-37]`: El dominio define `AiProviderException` y `ErrorType` (`TIMEOUT`, `UNAVAILABLE`, `RATE_LIMITED`, `INVALID_INPUT`, `INTERNAL_ERROR`), permitiendo tipar los fallos para el cómputo de resiliencia.
- `[VERIFIED: backend/pom.xml:83-89]`: El proyecto ya cuenta con las dependencias `spring-boot-starter-actuator` y `micrometer-registry-prometheus`.
- `[VERIFIED: .env.example:50-71]`: Registra las variables de entorno actuales para IA (`AI_PROVIDER`, `AI_TIMEOUT_SECONDS`, `GEMINI_API_KEY`, `OPENAI_API_KEY`, `AI_AUDIO_PROVIDER`, etc.).
- `[VERIFIED: docker-compose.yml:71-76]`: Inyecta las variables de entorno de IA al contenedor de la aplicación.
- `[VERIFIED: docs/INTEGRATIONS.md:103-114]`: Documenta la integración de proveedores multimodales y los límites actuales de timeout y tamaño de archivos.
- `[VERIFIED: docs/adr/ADR-001-AI-PROVIDER-ABSTRACTION.md:73-81]`: Documenta el patrón de abstracción de proveedores de IA y la intención original de desacoplamiento de vendors.

### Supuestos e Inferencias (`[INFERRED]`)
- `[INFERRED]`: Resilience4j (mediante `resilience4j-spring-boot3` o adaptador equivalente de resiliencia) es el estándar compatible para gestionar el ciclo de vida del Circuit Breaker y Retry sin acoplamiento no deseado, integrándose nativamente con Micrometer y Actuator.
- `[INFERRED]`: Las capacidades `rag` y `vision` compartirán el mismo esquema de metadatos de configuración de fallback que `chat`, aunque sus modelos específicos difieran según disponibilidad del proveedor.

### Decisiones de Producto y Negocio

Decididas el 2026-11-09:

- **Estructura**: segunda entrega de la épica HU-27/HU-28 (ver HU-27). Depende del mecanismo de HU-27.
- **Agotamiento de la cadena**: **503 sin mock** (antes opción B de esta spec, descartada). La opción de devolver una respuesta heurística o mock por defecto **queda prohibida**: enmascara fallos de infraestructura y contradice HU-26 FR-009 y HU-27 FR-006.
- **Implementación del circuit breaker**: Resilience4j (`resilience4j-spring-boot3`), integrado con Micrometer.

Pendiente de confirmar (se aplica la opción recomendada hasta que se indique lo contrario):

- `[NEEDS CLARIFICATION: Formato de variables de entorno]` Recomendada la opción A: soporte dual (cadena compacta `provider:model,provider:model` y relaxed binding estándar de Spring). Los ejemplos de documentación DEBEN usar nombres de modelo ficticios o tomados de la documentación vigente del proveedor; no se afirma que un nombre de modelo exista sin verificarlo.
- `[NEEDS CLARIFICATION: Exposición de estado]` Recomendada la opción A: `GET /api/admin/ai/providers` (solo `ROLE_ADMIN`) con detalle completo, más un `HealthIndicator` agregado (`UP`/`DEGRADED`) sin configuración interna.

---

## Dependencies & Out of Scope

### Dependencies
- **HU-27 (Cadena de ejecución de Fallback)**: HU-28 provee la configuración tipada, la validación al arranque, el circuit breaker, la telemetría y el endpoint de administración que la cadena de ejecución de HU-27 consume para despachar las llamadas.
- **Infraestructura de Observabilidad**: Requiere Spring Boot Actuator y Micrometer Prometheus (ya presentes en `pom.xml`) e incorporación de Resilience4j en la fase de implementación.

### Out of Scope
- **Algoritmo de ejecución de llamadas**: La lógica secuencial de ejecución de adapters e invocación efectiva pertenece a HU-27.
- **Nuevos adaptadores de proveedores no soportados**: La incorporación de SDKs para proveedores adicionales (p. ej. Anthropic Claude, Mistral) está fuera del alcance de esta especificación.
- **Interfaz gráfica de administración en frontend**: Esta HU abarca únicamente la configuración, el endpoint backend y la observabilidad; cualquier UI de administración en Angular será tratada en una HU posterior.
- **Cifrado o bóveda de secretos en tiempo de ejecución**: La gestión de secretos sigue delegada a las variables de entorno de la infraestructura (HashiCorp Vault, Kubernetes Secrets, etc.).
