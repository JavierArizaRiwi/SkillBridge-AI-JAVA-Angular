# Feature Specification: Modelos de IA de respaldo (AI Model Fallback)

**Feature Branch**: `feature/027-ai-model-fallback` | **Created**: 2026-10-22 | **Status**: Draft (refinada 2026-11-09; pendiente de aprobación humana) | **Issue**: #78  
**Input**: Como usuario de las funciones de IA (recomendaciones multimodales, visión, voz y RAG), quiero que si el proveedor de IA principal falla, se agota o excede su cuota, el sistema responda usando automáticamente un modelo de respaldo, para que la funcionalidad siga disponible sin errores visibles.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Conmutación por error (fallback) transparente en recomendaciones de catálogo (Priority: P1)

Como usuario que busca servicios o habilidades en el catálogo de SkillBridge mediante lenguaje natural, quiero que el sistema obtenga recomendaciones utilizando de forma automática un modelo o proveedor secundario si el modelo principal experimenta un fallo temporal o agota su cuota de peticiones, para recibir sugerencias pertinentes sin interrupción de mi flujo ni mensajes de error.

- **Why this priority**:
  El motor de recomendaciones contextuales es la funcionalidad central de inteligencia artificial de SkillBridge AI. Si el proveedor principal falla por cuota o indisponibilidad momentánea, el usuario percibe una falla total de la plataforma. La conmutación automática en este flujo constituye el MVP que protege la propuesta de valor principal del producto.
- **Independent Test**:
  Simular una respuesta de cuota excedida (HTTP 429) o indisponibilidad (HTTP 503 / Timeout) en el modelo primario y verificar de manera aislada que una solicitud de recomendación (`POST /api/ai/recommendations`) concluye con código HTTP 200 y una lista válida de recomendaciones estructuradas provenientes del modelo secundario, sin alterar la firma del caso de uso.

#### Acceptance Scenarios

1. **Scenario 1.1: Modelo primario disponible y saludable**
   - **Given** que el modelo de IA primario de la cadena de recomendaciones está configurado y responde adecuadamente,
   - **When** un usuario envía una consulta de recomendación válida con su necesidad u objetivo de aprendizaje,
   - **Then** el sistema procesa la solicitud a través del modelo primario y devuelve las recomendaciones correspondientes sin invocar modelos secundarios de la cadena.

2. **Scenario 1.2: Modelo primario falla por error recuperable y secundario responde con éxito**
   - **Given** que el modelo primario de recomendaciones responde con un error recuperable (ej. límite de cuota HTTP 429, tiempo de espera agotado o error interno del proveedor upstream 5xx),
   - **When** un usuario solicita recomendaciones de catálogo,
   - **Then** el sistema conmuta de forma automática e inmediata al siguiente modelo de respaldo configurado en la cadena,
   - **And** el usuario recibe una respuesta exitosa (HTTP 200) con las recomendaciones pertinentes sin percibir la falla transitoria.

3. **Scenario 1.3: Conmutación intra-proveedor antes de conmutación inter-proveedor**
   - **Given** una cadena configurada con un modelo primario (ej. `gemini-3.8-flash`) y un modelo secundario del mismo proveedor (ej. `gemini-3.8-flash-lite`), seguidos de un proveedor alternativo (ej. OpenAI `gpt-4o-mini`),
   - **When** el modelo primario falla por un error recuperable,
   - **Then** el sistema intenta primero resolver la solicitud con el modelo secundario del mismo proveedor antes de invocar al proveedor externo alternativo.

4. **Scenario 1.4: Error no recuperable no activa conmutación a modelos de respaldo**
   - **Given** una solicitud que contiene datos que violan las reglas de validación del cliente o generan un error no recuperable del cliente (HTTP 4xx propio de validación),
   - **When** el sistema procesa la solicitud,
   - **Then** el sistema detiene inmediatamente la ejecución, devuelve el error de validación correspondiente al cliente y NO ejecuta peticiones adicionales hacia los modelos de respaldo.

---

### User Story 2 - Resiliencia y fallback en capacidades multimodales de Visión y Voz (Priority: P2)

Como usuario que interactúa mediante notas de voz (audio) o imágenes (documentos/certificados para extracción de habilidades), quiero que el procesamiento de audio e imagen conmute a modelos y proveedores alternativos si el servicio multimodal primario no está disponible, para mantener la fluidez en canales interactivos sin rechazar mis archivos multimedia.

- **Why this priority**:
  Las interacciones multimodales (transcripción de audio y análisis de imágenes) son vías clave de accesibilidad y adquisición de usuarios en SkillBridge. Protegerlas mediante adaptadores de respaldo evita fricción en el embudo de recomendación por voz o imagen.
- **Independent Test**:
  Inyectar un fallo recuperable en el servicio primario de transcripción de voz (`SpeechToTextPort`) o en el de visión (`VisionPort`) y verificar que el endpoint correspondiente (`/api/ai/recommendations/voice` o `/api/ai/recommendations/image`) conmuta al adaptador secundario y completa la extracción de texto y recomendación satisfactoriamente.

#### Acceptance Scenarios

1. **Scenario 2.1: Conmutación exitosa en transcripción de audio (Speech-to-Text)**
   - **Given** que el proveedor primario de transcripción de audio (ej. Groq Whisper) experimenta indisponibilidad o timeout de conexión,
   - **When** un usuario envía un archivo de audio para solicitar recomendaciones por voz (`POST /api/ai/recommendations/voice`),
   - **Then** el sistema pasa de forma transparente la petición al adaptador secundario de transcripción (ej. Gemini Audio STT),
   - **And** la transcripción resultante se envía al motor de recomendaciones sin que el usuario reciba un error.

2. **Scenario 2.2: Conmutación en extracción de contexto visual (Vision)**
   - **Given** que el adaptador de visión primario no logra procesar una imagen por error 5xx del proveedor o saturación de cuota,
   - **When** un usuario sube una imagen de certificación o portafolio (`POST /api/ai/recommendations/image`),
   - **Then** el sistema conmuta al modelo de visión secundario configurado en la cadena para extraer el contexto textual,
   - **And** genera las recomendaciones con base en el contexto extraído.

3. **Scenario 2.3: Síntesis de voz (Text-to-Speech) con fallback o degradación elegante**
   - **Given** que el servicio de síntesis de voz primario (ej. ElevenLabs) agota su cuota de caracteres o sufre timeout,
   - **When** se solicita la síntesis de audio de una recomendación generada (`POST /api/ai/tts`),
   - **Then** el sistema evalúa el siguiente adaptador TTS de respaldo si existe, o retorna un error 503 controlado sin corromper la recomendación textual previa.

---

### User Story 3 - Diagnóstico, telemetría y respuesta controlada ante agotamiento total (Priority: P3)

Como operador de plataforma y desarrollador de SkillBridge, quiero que cada intento en la cadena de fallback registre métricas de latencia, modelo utilizado y estado, y que en caso de que todos los modelos configurados fallen se entregue una respuesta de error HTTP 503 estándar sin respuestas simuladas (mocks) en producción, para garantizar la auditabilidad técnica, el control de costos y la integridad del sistema.

- **Why this priority**:
  Una arquitectura con múltiples modelos requiere observabilidad rigurosa para supervisar costos por token, latencia acumulada y frecuencia de conmutación, además de asegurar que bajo ninguna circunstancia se presenten datos simulados o engañosos a los usuarios finales en producción.
- **Independent Test**:
  Simular la indisponibilidad simultánea de todos los modelos en la cadena de recomendaciones y comprobar que: (1) se devuelve un error HTTP 503 normalizado bajo el estándar RFC 7807 (ProblemDetail), (2) no se retorna ninguna recomendación mock con IDs fijos, y (3) se emiten métricas y logs con el desglose de cada intento fallido.

#### Acceptance Scenarios

1. **Scenario 3.1: Agotamiento total de la cadena resulta en HTTP 503 sin datos mock**
   - **Given** que todos los modelos y proveedores de la cadena de fallback fallan sucesivamente por errores recuperables,
   - **When** el usuario realiza una solicitud de recomendación,
   - **Then** el sistema responde con estado HTTP 503 (Service Unavailable) y formato estructurado estándar ProblemDetail,
   - **And** en ningún caso se generan recomendaciones ficticias, datos mock ni identificadores de catálogo inventados en entornos no de prueba.

2. **Scenario 3.2: Registro de proveedor y modelo en eventos de negocio y telemetría**
   - **Given** que la recomendación fue resuelta por un modelo secundario tras fallar el primario,
   - **When** el sistema finaliza exitosamente la operación,
   - **Then** el evento de dominio publicado (`RecommendationGenerated`) incluye explícitamente el proveedor (`provider`) y el modelo (`model`) que generó la respuesta,
   - **And** se registran métricas de latencia individual y agregada para cada intento ejecutado en la cadena.

3. **Scenario 3.3: Exclusión segura en el arranque de proveedores sin API key**
   - **Given** que un proveedor o modelo está declarado en la configuración de la cadena pero carece de API key configurada en las variables de entorno,
   - **When** la aplicación arranca e inicializa los adaptadores de IA,
   - **Then** dicho proveedor es excluido de la cadena de ejecución activa,
   - **And** se emite un registro de advertencia (WARN) indicando su omisión sin imprimir valores de credenciales ni secretos en los logs.

---

### Edge Cases

1. **Cadena vacía o sin proveedores configurados con credenciales válidas**:
   - Si al iniciar la aplicación ningún proveedor de la cadena posee una API key válida, la cadena se inicializa vacía. Toda petición en tiempo de ejecución DEBE fallar inmediatamente con HTTP 503 (Service Unavailable), informando que el servicio de IA no está disponible, sin arrojar excepciones no controladas ni colapsar el proceso del servidor.
2. **Respuesta corrupta, vacía o con JSON no parseable emitida por un LLM**:
   - Si un modelo responde con código HTTP 200 pero el cuerpo de la respuesta está vacío, truncado por límite de tokens de salida, o incumple el esquema JSON esperado (haciendo fallar el deserializador), el sistema DEBE clasificar esta situación como error recuperable y conmutar de inmediato al siguiente modelo en la cadena.
3. **Latencia acumulada y presupuesto de tiempo global (Timeout Presupuestado)**:
   - Si un modelo primario consume 9 segundos antes de fallar por timeout y el límite global de la operación es de 10 segundos, el intento en el modelo secundario debe respetar el tiempo remanente disponible para evitar que el cliente HTTP experimente un bloqueo indefinido o un timeout de puerta de enlace sin respuesta controlada.
4. **Errores de validación de entrada del cliente (4xx Propios)**:
   - Errores causados por parámetros nulos, identificadores malformados, violaciones de tipos de entrada (`InputType`), o archivos que superan el tamaño máximo permitido DEBEN ser rechazados inmediatamente sin activar la cadena de respaldo, protegiendo los recursos y cuotas de los modelos secundarios.
5. **Errores de autenticación con el proveedor (401/403 Upstream durante ejecución)**:
   - Si un proveedor configurado rechaza una petición en ejecución por clave inválida o expirada (HTTP 401/403 de la API externa), el sistema DEBE tratarlo como una falla de configuración de ese candidato específico, registrar una advertencia de seguridad (sin divulgar la clave) y conmutar al siguiente proveedor disponible en la cadena.
6. **Aislamiento de fallos entre modalidades**:
   - Una falla no recuperable o el agotamiento de la cadena en la síntesis de voz (TTS) no debe invalidar ni cancelar la recomendación textual ya obtenida y guardada; la recomendación escrita permanece accesible para el usuario.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE proveer un adaptador compuesto por cada puerto de salida de IA (`AiRecommendationPort`, `VisionPort`, `SpeechToTextPort`, `TextToSpeechPort`) que encapsule una cadena ordenada de adaptadores/modelos candidatos y sea el componente inyectado en los casos de uso y servicios de aplicación, sin alterar la firma ni el contrato de dichos servicios.
- **FR-002**: El sistema DEBE ejecutar secuencialmente los modelos configurados en la cadena comenzando por el modelo primario, conmutando al siguiente candidato ÚNICAMENTE ante errores recuperables (tiempo de espera/timeout, cuota de peticiones excedida HTTP 429, error 5xx del servicio externo, fallos de conectividad/red, y respuestas vacías o con JSON que no satisface el esquema).
- **FR-003**: El sistema DEBE interrumpir de inmediato el procesamiento y propagar la excepción sin activar ningún modelo de respaldo ante errores clasificados como no recuperables (errores de validación de entrada del cliente, HTTP 4xx originados por parámetros inválidos del usuario o violaciones de restricciones de negocio).
- **FR-004**: El sistema DEBE soportar la definición secuencial de múltiples modelos del mismo proveedor (ej. modelo primario y secundario de Google GenAI) además de modelos entre distintos proveedores (ej. Google GenAI, OpenAI, Groq) en la misma cadena de ejecución.
- **FR-005**: El sistema DEBE verificar durante la inicialización de la aplicación la presencia de credenciales (API keys) para cada modelo/proveedor de la cadena, omitiendo de la cadena activa a aquellos que carezcan de clave y emitiendo un registro de advertencia (WARN) que bajo ninguna circunstancia divulgue ni registre secretos o credenciales parciales.
- **FR-006**: Si todos los candidatos configurados en la cadena fallan por errores recuperables sucesivos, el sistema DEBE retornar una respuesta de error controlado con estado HTTP 503 (Service Unavailable) bajo el estándar RFC 7807 (ProblemDetail), quedando estrictamente prohibido emitir datos simulados o mocks con identificadores fijos en entornos no de prueba.
- **FR-007**: El sistema DEBE identificar de forma precisa en el evento de negocio resultante (`RecommendationGenerated`) y en la telemetría asociada el proveedor (`provider`) y modelo (`model`) específicos que generaron satisfactoriamente la respuesta, junto con la latencia observada.
- **FR-008**: El sistema DEBE registrar métricas de observabilidad por cada intento individual de ejecución en la cadena, incluyendo el identificador del candidato (proveedor/modelo), el resultado (éxito o tipo de error) y la latencia en milisegundos.
- **FR-009**: El sistema DEBE ser verificable mediante un conjunto exhaustivo de pruebas unitarias aisladas para la cadena (caso de éxito en primario, primario falla recuperable con éxito en secundario, agotamiento de toda la cadena con 503, y error no recuperable que no conmuta) sin dependencias de red externa ni necesidad de claves de API reales.
- **FR-010**: El sistema DEBE mantener consistencia con los principios arquitectónicos de desacoplamiento de proveedores estipulados en ADR-001, dejando registrado el diseño del adaptador compuesto en la documentación técnica (`docs/adr/` o actualización de ADR-001).

---

### Key Entities

```
┌────────────────────────────────────────────────────────┐
│                    FallbackChain                       │
│  - portIdentifier: String                              │
│  - candidates: List<AiModelCandidate>                  │
└───────────────────────────┬────────────────────────────┘
                            │ 1..*
┌───────────────────────────▼────────────────────────────┐
│                  AiModelCandidate                      │
│  - providerName: String (e.g. "gemini", "openai")      │
│  - modelIdentifier: String (e.g. "gemini-3.8-flash")   │
│  - priorityOrder: int                                  │
│  - isConfigured: boolean                               │
└────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────┐
│                 ExecutionAttemptResult                 │
│  - candidate: AiModelCandidate                         │
│  - outcome: AttemptOutcome (SUCCESS | FAILURE)         │
│  - errorClassification: RecoverabilityType             │
│  - latencyMs: long                                     │
└────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────┐
│                RecommendationOutcome                   │
│  - structuredResponse: AiStructuredResponse            │
│  - resolvedByProvider: String                          │
│  - resolvedByModel: String                             │
│  - attemptsHistory: List<ExecutionAttemptResult>       │
│  - totalLatencyMs: long                                │
└────────────────────────────────────────────────────────┘
```

- **AiModelCandidate**: Representa un destino de inferencia configurado que combina un proveedor de IA (`providerName`), un identificador de modelo (`modelIdentifier`), un orden de evaluación en la cadena (`priorityOrder`) y un estado de disponibilidad de credenciales (`isConfigured`).
- **FallbackChain**: Encapsula la secuencia ordenada de candidatos aplicables a un puerto de IA específico, gobernando las transiciones de ejecución entre candidatos.
- **ExecutionAttemptResult**: Registro inmutable de cada intento de invocación sobre un candidato, conteniendo la latencia observada, el resultado (éxito o fallo) y la clasificación del error presentado.
- **AiErrorClassification**: Clasificación unificada de errores que distingue entre:
  - *RECOVERABLE*: Tiempo de espera agotado (`TIMEOUT`), límite de tasa/cuota superado (`RATE_LIMITED` / 429), indisponibilidad del servicio remoto (`UNAVAILABLE` / 5xx), error de socket/red, respuesta vacía o JSON inválido (`INTERNAL_ERROR` del proveedor).
  - *NON_RECOVERABLE*: Entrada de usuario inválida (`INVALID_INPUT` / 4xx), violación de esquema del cliente, o violación de reglas de seguridad/negocio.
- **RecommendationOutcome**: Resultado final de una operación exitosa que complementa los datos de dominio recomendados con la metadata de auditoría (`provider`, `model`, latencia e historial de intentos).

---

### Traceability Matrix (Issue #78 → Spec Coverage)

| Criterio de Aceptación del Issue #78 | Historia de Usuario | Requisito Funcional (FR) | Criterio de Éxito (SC) |
|---|---|---|---|
| Spec SDD aprobada en `specs/<NNN>-ai-model-fallback/` antes de implementar | N/A (Fase SDD) | FR-001 a FR-010 | SC-001 a SC-007 |
| Por cada puerto de IA existe un adaptador compuesto que recorre una cadena ordenada y es el bean inyectado; servicios de aplicación no cambian | Story 1, Story 2 | FR-001, FR-002 | SC-001, SC-003 |
| Se pasa al siguiente modelo solo ante errores recuperables (timeout, 429, 5xx, red, vacío/no parseable); errores 4xx propios no activan fallback | Story 1, Story 2 | FR-002, FR-003 | SC-001, SC-002 |
| Se soportan varios modelos del mismo proveedor además de varios proveedores | Story 1 | FR-004 | SC-001 |
| Proveedor sin API key se omite al arrancar (con log de WARN, sin exponer clave) | Story 3 | FR-005 | SC-006 |
| Si toda la cadena falla -> error controlado (503 con formato estándar), nunca un mock con datos fijos fuera de test | Story 3 | FR-006 | SC-007 |
| Respuesta/evento indica qué proveedor/modelo respondió (`RecommendationGenerated` incluye provider y model) y métricas por intento | Story 3 | FR-007, FR-008 | SC-005 |
| Pruebas unitarias de la cadena (primario OK, primario falla -> secundario, todos fallan, error no recuperable no salta) sin red ni claves | Story 1, Story 3 | FR-009 | SC-001, SC-002, SC-007 |
| `docs/adr/ADR-001-AI-PROVIDER-ABSTRACTION.md` actualizado o nuevo ADR en `docs/adr/` | N/A (Documentación) | FR-010 | SC-005 |

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de las solicitudes de recomendación o capacidades multimodales que experimenten un error recuperable en el modelo primario y cuenten con al menos un modelo secundario saludable disponible concluyen satisfactoriamente (HTTP 200) sin error percibido por el usuario final.
- **SC-002**: El 0% de los errores originados por validaciones de cliente no recuperables (HTTP 4xx propio) activan llamadas secundarias en la cadena de respaldo, preservando las cuotas de los proveedores alternativos.
- **SC-003**: El tiempo de sobrecarga de decisión de la cadena de fallback (overhead interno de conmutación entre adaptadores) es menor a 25 milisegundos, excluyendo la latencia de red y cómputo propia de los modelos.
- **SC-004**: La disponibilidad global del servicio de recomendaciones y transcripción se mantiene en al menos un 99.5% durante incidentes de agotamiento de cuota (HTTP 429) o indisponibilidad temporal (HTTP 5xx) que afecten a un único proveedor de IA.
- **SC-005**: El 100% de los eventos de negocio `RecommendationGenerated` emitidos tras una conmutación exitosa registran con exactitud los atributos `provider` y `model` correspondientes al candidato que produjo el resultado final.
- **SC-006**: El 100% de los candidatos sin credenciales válidas son excluidos durante la inicialización de la aplicación, emitiendo una alerta WARN en los logs sin exponer fragmentos de credenciales ni secretos en ningún registro.
- **SC-007**: Ante el agotamiento total de todos los candidatos de la cadena, el 100% de las respuestas corresponden a HTTP 503 estructurado conforme a RFC 7807 (ProblemDetail), con cero incidencias de filtración de trazas de excepción interna y sin emisión de respuestas simuladas (mocks) en perfiles de producción.

---

## Assumptions

### Verified Codebase Context

- `[VERIFIED]` La arquitectura del repositorio SkillBridge AI se adhiere a principios hexagonales estrictos: el dominio es Java puro, la capa de aplicación expone puertos de entrada (`application/port/in`) y salida (`application/port/out`), y la infraestructura aloja los adaptadores de terceros en `infrastructure/adapter/out/ai` ([AGENTS.md:9-14](../../AGENTS.md#L9-L14)).
- `[VERIFIED]` Los puertos de salida de capacidades de IA existentes son: `AiRecommendationPort` ([AiRecommendationPort.java:7-9](../../backend/src/main/java/com/riwi/skillbridge/application/port/out/AiRecommendationPort.java#L7-L9)), `VisionPort` ([VisionPort.java:3-5](../../backend/src/main/java/com/riwi/skillbridge/application/port/out/VisionPort.java#L3-L5)), `SpeechToTextPort` ([SpeechToTextPort.java:3-5](../../backend/src/main/java/com/riwi/skillbridge/application/port/out/SpeechToTextPort.java#L3-L5)), y `TextToSpeechPort` ([TextToSpeechPort.java:3-7](../../backend/src/main/java/com/riwi/skillbridge/application/port/out/TextToSpeechPort.java#L3-L7)).
- `[VERIFIED]` Los adaptadores actuales de IA son `GeminiAiAdapter` ([GeminiAiAdapter.java:24](../../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/GeminiAiAdapter.java#L24)), `OpenAiAiAdapter` ([OpenAiAiAdapter.java:19](../../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/OpenAiAiAdapter.java#L19)), `GeminiVisionAdapter` ([GeminiVisionAdapter.java:11](../../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/GeminiVisionAdapter.java#L11)), `GeminiSpeechToTextAdapter` ([GeminiSpeechToTextAdapter.java:16](../../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/GeminiSpeechToTextAdapter.java#L16)), `GroqSpeechToTextAdapter` ([GroqSpeechToTextAdapter.java:24](../../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/GroqSpeechToTextAdapter.java#L24)), `ElevenLabsTextToSpeechAdapter` ([ElevenLabsTextToSpeechAdapter.java:18](../../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/ElevenLabsTextToSpeechAdapter.java#L18)), y `NoOpTextToSpeechAdapter` ([NoOpTextToSpeechAdapter.java:10](../../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/NoOpTextToSpeechAdapter.java#L10)).
- `[VERIFIED]` La taxonomía de excepciones de IA del dominio está centralizada en `AiProviderException` con tipos `TIMEOUT`, `UNAVAILABLE`, `RATE_LIMITED`, `INVALID_INPUT`, e `INTERNAL_ERROR` ([AiProviderException.java:31-36](../../backend/src/main/java/com/riwi/skillbridge/domain/exception/AiProviderException.java#L31-L36)).
- `[VERIFIED]` El manejador global de excepciones mapea `AiProviderException` a ProblemDetail con códigos HTTP 503 para `TIMEOUT` y `UNAVAILABLE`, 429 para `RATE_LIMITED`, y 502 para `INVALID_INPUT` e `INTERNAL_ERROR` ([GlobalExceptionHandler.java:67-86](../../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/GlobalExceptionHandler.java#L67-L86)).
- `[VERIFIED]` El payload del evento de dominio `RecommendationGeneratedPayload` actualmente contiene `modelUsed` y `latencyMs`, pero no incluye explícitamente el atributo `provider` ([RecommendationGeneratedPayload.java:12-19](../../backend/src/main/java/com/riwi/skillbridge/application/port/out/event/RecommendationGeneratedPayload.java#L12-L19)).
- `[VERIFIED]` La selección actual de proveedor en la configuración se basa en propiedades simples como `app.ai.provider` y `app.ai.audio-provider` ([application.yml:134-135](../../backend/src/main/resources/application.yml#L134-L135)).

### Inferred System Behavior

- `[INFERRED]` Los servicios de la capa de aplicación (`AiRecommendationService`, `TextToSpeechService`) no deben cambiar de firma ni conocer la existencia de la cadena de fallback; el adaptador compuesto implementa directamente el puerto respectivo (`AiRecommendationPort`, `VisionPort`, `SpeechToTextPort`, `TextToSpeechPort`) y actúa como bean primario inyectable.
- `[INFERRED]` Todos los modelos candidatos en la cadena deben formatear y adaptar su salida hacia la estructura unificada del puerto (ej. `AiStructuredResponse`), de forma que los casos de uso procesen las recomendaciones de manera idéntica independientemente del proveedor que resolvió la petición.

### Product & Design Decisions

Decididas el 2026-11-09:

- **Estructura**: HU-27 y HU-28 forman **una épica en dos entregas**. HU-27 (esta) entrega el mecanismo: adaptador compuesto por puerto, cadena ordenada, clasificación recuperable/no recuperable y métricas por intento. HU-28 entrega la configuración declarativa, el circuit breaker y la administración. Se mantienen dos issues para trazabilidad.
- **Agotamiento de la cadena**: HTTP 503 (`AiProviderException` tipada `UNAVAILABLE`) **sin mock ni datos de ejemplo**, en toda capacidad y en todo entorno salvo `app.ai.provider=mock` explícito. Coherente con HU-26 FR-009 y con el hallazgo RAG-001.
- **Circuit breaker**: Resilience4j (se incorpora en HU-28; HU-27 no depende de él).

Pendiente de confirmar (se aplica la opción recomendada hasta que se indique lo contrario):

- `[NEEDS CLARIFICATION: Criterio de degradación de calidad vs latencia en fallback multimodelo]` Recomendada la opción 1: priorizar disponibilidad aceptando explicaciones más concisas del modelo secundario, siempre que cumpla el esquema JSON estricto y los IDs válidos del catálogo. Opción 2: restringir el fallback a modelos de capacidad equivalente y devolver 503 si no hay.
- `[NEEDS CLARIFICATION: Exposición del proveedor y modelo en la respuesta REST]` Recomendada la opción 1: `provider` y `model` solo en eventos Kafka, métricas, logs y el endpoint administrativo de HU-28; el cuerpo HTTP hacia el frontend permanece neutral.

---

## Dependencies & Out of Scope

### Dependencies

- **HU-28 (Configuración y Resiliencia Avanzada)**: La parametrización dinámica del orden de la cadena en `application.yml`, timeouts específicos por proveedor, circuit breakers con ventanas de fallo (`resilience4j`) y reintentos automáticos se definen e implementan en HU-28. La presente especificación (HU-27) define el comportamiento operativo de la cadena de fallback, la taxonomía de errores recuperables vs no recuperables, y la propagación uniforme de resultados y eventos.
- **Spring AI y SDKs de Proveedores**: Dependencias de `spring-ai-starter-model-google-genai`, `spring-ai-starter-openai`, y clientes HTTP para Groq y ElevenLabs ya declarados en el gestor de dependencias (`backend/pom.xml`).

### Out of Scope

- Implementación de lógica de Circuit Breaker con estados (Open, Half-Open, Closed) basada en ventanas de fallo históricas (alcance exclusivo de HU-28).
- Incorporación de proveedores de IA externos no contemplados en el ecosistema de SkillBridge (ej. Anthropic Claude, Mistral, Cohere) más allá de los previstos en ADR-001 (Gemini, OpenAI, Groq, ElevenLabs).
- Modificaciones a la interfaz de usuario en el frontend Angular (`frontend/src/app`).
- Creación de nuevas entidades o tablas persistentes en la base de datos relacional PostgreSQL (no requiere migraciones Flyway).
- Generación de respuestas simuladas o mocks estáticos con datos ficticios para mitigar fallas en entornos de producción.
