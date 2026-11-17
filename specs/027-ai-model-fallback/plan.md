# Implementation Plan: AI Model Fallback Chain (HU-27)

**Branch**: `feature/HU-27-Modelos-de-IA-de-respaldo-(fallback-multi-proveedor)` | **Date**: 2026-11-10 | **Spec**: [specs/027-ai-model-fallback/spec.md](spec.md)

**Input**: Feature specification from `specs/027-ai-model-fallback/spec.md` and architectural decisions from `docs/adr/0007-ai-provider-fallback-chain.md`.

---

## Summary

Implementar el mecanismo de conmutación por error (fallback chain) para los cuatro puertos de salida de IA en SkillBridge AI (`AiRecommendationPort`, `VisionPort`, `SpeechToTextPort`, `TextToSpeechPort`). Cada puerto contará con un adaptador compuesto que evaluará secuencialmente una cadena ordenada de candidatos (`proveedor:modelo`), conmutando únicamente ante errores recuperables (HTTP 429 cuota/rate limit, 5xx upstream, timeouts reales y respuestas vacías o no parseables), propagando de inmediato los errores no recuperables (4xx del cliente/validación) sin conmutar. Si se agota la cadena completa, el sistema retornará HTTP 503 (`ProblemDetail`) sin emitir datos simulados (cero mocks). Cada intento registrará latencia y resultado para observabilidad, y el evento `RecommendationGenerated` registrará el proveedor y modelo efectivos.

---

## Technical Context

- **Language/Version**: Java 21 LTS
- **Primary Dependencies**: Spring Boot 3.4.x, Spring AI 1.0.x (Google GenAI, OpenAI, Groq, ElevenLabs SDK/REST), Jackson, Micrometer, SLF4J/Logback
- **Storage**: No requiere persistencia relacional ni migraciones de base de datos (PostgreSQL inalterado para esta entrega)
- **Testing**: JUnit 5, Mockito, AssertJ (Unit tests aislados sin red ni claves reales)
- **Target Platform**: Linux / Containerized JVM
- **Project Type**: Spring Boot Microservices / Modular Monolith (backend core)
- **Performance Goals**: Sobrecarga de conmutación (overhead de la cadena) < 25 ms excluyendo el tiempo de red del proveedor; timeouts aplicados estrictamente por candidato
- **Constraints**:
  - Arquitectura Hexagonal estricta: dominio puro (`com.riwi.skillbridge.domain`) sin dependencias de frameworks
  - Los casos de uso de aplicación (`AiRecommendationService`, `TextToSpeechService`, etc.) no cambian de firma ni conocen la cadena
  - Prohibido deducir errores mediante inspección de texto/substrings; clasificación por tipo de excepción y código de estado HTTP
  - Cero mocks en producción: ante agotamiento siempre lanzar `AiProviderException(UNAVAILABLE)` mapeada a HTTP 503
  - Ninguna clave ni credencial expuesta en logs, métricas ni respuestas
- **Scale/Scope**: 4 puertos de IA con soporte para múltiples proveedores y múltiples modelos por proveedor

---

## Constitution Check

*GATE: All principles from `.specify/memory/constitution.md` evaluated.*

| Constitution Article | Compliance Analysis | Status |
|---|---|---|
| **Article I: Hexagonal Architecture & Pure Domain** | Las interfaces de puertos (`port/out/`) y contratos de dominio permanecen intactos. Los adaptadores compuestos y la clasificación técnica residen exclusivamente en `infrastructure/adapter/out/ai/`. Ningún framework entra a `com.riwi.skillbridge.domain`. | **PASS** |
| **Article II: Multimodal AI Integration Ports & Boundaries** | Los puertos `AiRecommendationPort`, `VisionPort`, `SpeechToTextPort`, `TextToSpeechPort` se implementan mediante adaptadores compuestos inyectables como `@Primary`. Proveedores y modelos quedan encapsulados en infraestructura. | **PASS** |
| **Article III: Security & Sensitive Data Invariants** | Los proveedores sin API key son excluidos al inicializar con log WARN sin registrar valores parciales ni completos de claves. Cero exposición de credenciales en logs ni excepciones. | **PASS** |
| **Article IV: Observability & Correlation Tracing** | Propagación de `X-Correlation-Id` vía MDC en todos los logs de intentos. Registro de métricas por intento (`candidate`, `status`, `latencyMs`) y enriquecimiento de metadata (`provider`, `model`) en eventos de dominio `RecommendationGenerated`. | **PASS** |
| **Article V: Truthful Verification & No Silent Mocks** | Prohibición absoluta de heurísticas o respuestas estáticas falsas ante fallas; agotamiento genera 503 tipado `UNAVAILABLE`. Pruebas unitarias sin red ejecutadas con evidencia real de salida. | **PASS** |
| **Article VI: Strict Non-Destructive Operations** | No se modifican paths protegidos (`docs/audit/**`, migraciones Flyway existentes). Trabajo acotado estrictamente a `backend/` y `specs/027-ai-model-fallback/`. | **PASS** |

---

## Project Structure & Impacted Files

```text
backend/src/main/java/com/riwi/skillbridge/
├── application/
│   ├── port/out/
│   │   ├── AiRecommendationPort.java               # Conservado intacto
│   │   ├── VisionPort.java                         # Conservado intacto
│   │   ├── SpeechToTextPort.java                   # Conservado intacto
│   │   ├── TextToSpeechPort.java                   # Conservado intacto
│   │   └── event/
│   │       └── RecommendationGeneratedPayload.java  # Enriquecido con provider/model
│   └── service/
│       └── AiRecommendationService.java           # Actualización menor para pasar metadata de resolución
│
├── domain/
│   └── exception/
│       └── AiProviderException.java                # Conservado con ErrorType estándar
│
└── infrastructure/
    ├── adapter/out/ai/
    │   ├── classification/
    │   │   ├── AiErrorClassifier.java              # Clasificador tipado (excepción + HTTP status)
    │   │   └── ErrorClassification.java            # RECOVERABLE vs NON_RECOVERABLE
    │   ├── candidate/
    │   │   ├── AiModelCandidate.java               # Registro candidato (provider, model, timeout)
    │   │   └── CandidateExecutionResult.java       # Resultado de intento (éxito/fallo, latencia)
    │   ├── fallback/
    │   │   ├── FallbackAiRecommendationAdapter.java # Compuesto para AiRecommendationPort (@Primary)
    │   │   ├── FallbackVisionAdapter.java           # Compuesto para VisionPort (@Primary)
    │   │   ├── FallbackSpeechToTextAdapter.java     # Compuesto para SpeechToTextPort (@Primary)
    │   │   └── FallbackTextToSpeechAdapter.java     # Compuesto para TextToSpeechPort (@Primary)
    │   ├── metrics/
    │   │   └── AiFallbackMetricsRecorder.java      # Grabador de métricas Micrometer por intento
    │   ├── GeminiAiAdapter.java                    # Adaptado para timeout real y clasificación tipada
    │   ├── OpenAiAiAdapter.java                    # Adaptado para timeout real y clasificación tipada
    │   ├── GroqSpeechToTextAdapter.java            # Adaptado para verificación de timeout
    │   └── ElevenLabsTextToSpeechAdapter.java      # Verificado
    │
    └── config/
        └── AiFallbackConfiguration.java             # Registro de beans y resolución de candidatos activos

backend/src/test/java/com/riwi/skillbridge/
└── infrastructure/adapter/out/ai/
    ├── classification/
    │   └── AiErrorClassifierTest.java               # Pruebas de clasificación tipada
    └── fallback/
        ├── FallbackAiRecommendationAdapterTest.java # Pruebas unitarias de cadena (P1)
        ├── FallbackVisionAdapterTest.java           # Pruebas unitarias de visión (P2)
        ├── FallbackSpeechToTextAdapterTest.java     # Pruebas unitarias de STT (P2)
        └── FallbackTextToSpeechAdapterTest.java     # Pruebas unitarias de TTS (P2)
```

---

## Detailed Architectural Design

### 1. Clasificación Tipada de Errores (`AiErrorClassifier`)

Se prohíbe explícitamente el uso de `String.contains("timeout")` o subcadenas similares en el mensaje de error. La clasificación se efectúa mediante introspección de la jerarquía de excepciones y códigos de estado HTTP estándar:

- **Recuperables (`RECOVERABLE`)**:
  - `AiProviderException` con `ErrorType.TIMEOUT`, `RATE_LIMITED` o `UNAVAILABLE`
  - Errores de timeout: `java.util.concurrent.TimeoutException`, `java.net.SocketTimeoutException`, `org.springframework.web.client.ResourceAccessException` por timeout
  - Errores HTTP del upstream: `WebClientResponseException` o `HttpStatusCodeException` con códigos:
    - `429` (Too Many Requests / Quota Exceeded) -> `RATE_LIMITED`
    - `500`, `502`, `503`, `504` (Server Error upstream) -> `UNAVAILABLE`
    - `401`, `403` en tiempo de ejecución (falla de autenticación de proveedor específico) -> tratado como proveedor degradado/inválido para conmutar sin exponer credenciales
  - Errores de respuesta inválida: `JsonProcessingException` / deserialización corrupta generada por el LLM -> `INTERNAL_ERROR` del candidato
  - Errores de conectividad de red: `java.net.ConnectException`, `java.net.UnknownHostException`
- **No Recuperables (`NON_RECOVERABLE`)**:
  - `AiProviderException` con `ErrorType.INVALID_INPUT`
  - Errores de validación de cliente: `IllegalArgumentException`, `MethodArgumentNotValidException`, `ConstraintViolationException`
  - Errores HTTP 400 (Bad Request originado por parámetros del cliente)
  - Interrupciones de seguridad o reglas de negocio de la plataforma

### 2. Cadena de Adaptadores Compuestos (`FallbackAiRecommendationAdapter`, etc.)

Cada adaptador compuesto implementa el puerto correspondiente y orquesta la evaluación secuencial:
1. Obtiene la lista de candidatos disponibles configurados y con credenciales válidas.
2. Si la lista está vacía -> lanza de inmediato `AiProviderException("No AI providers configured or available with valid credentials", ErrorType.UNAVAILABLE)`.
3. Itera secuencialmente sobre los candidatos:
   - Inicia medición de latencia (`System.nanoTime()`).
   - Aplica el timeout asignado al candidato (usando timeout del cliente o `CompletableFuture.supplyAsync().orTimeout(...)`).
   - Si la invocación es exitosa:
     - Registra métrica de éxito y latencia.
     - Emite log estructurado con `provider`, `model`, latencia y correlation ID.
     - Retorna el resultado estructurado.
   - Si ocurre una excepción:
     - Clasifica el error con `AiErrorClassifier`.
     - Si es `NON_RECOVERABLE`: registra la métrica y re-lanza inmediatamente la excepción sin probar el siguiente candidato.
     - Si es `RECOVERABLE`: registra la métrica de fallo por intento, emite log WARN estructurado y continúa con el siguiente candidato.
4. Si se agotan todos los candidatos sin éxito -> lanza `AiProviderException("All AI providers in fallback chain exhausted", ErrorType.UNAVAILABLE)`.

### 3. Convivencia de Beans y Registro de Candidatos

Para evitar colisiones de `@ConditionalOnProperty(matchIfMissing=true)` que anteriormente impedían instanciar múltiples adaptadores:
- Los adaptadores concretos (`GeminiAiAdapter`, `OpenAiAiAdapter`, etc.) se registran como componentes con identificador de bean específico (o se definen como candidatos ejecutores en `AiFallbackConfiguration`).
- El adaptador compuesto (`FallbackAiRecommendationAdapter`) se marca como `@Primary` para la inyección de `AiRecommendationPort` en los servicios de aplicación.
- En arranque, cada candidato valida la presencia de su API key: si no está presente o está vacía, no se añade a la cadena de ejecución activa y se emite un log `WARN` sin revelar datos sensibles.

### 4. Timeouts Reales por Candidato

- Configuración de timeout por candidato (defecto: `app.ai.timeout-seconds` o propiedad específica del candidato).
- Si el cliente HTTP subyacente soporta timeout nativo (ej. WebClient / RestClient / OkHttp), se sincroniza con el timeout configurado.
- Adicionalmente, el ejecutor de la cadena garantiza interrupción mediante límite temporal (`CompletableFuture` / `TimeLimiter`), previniendo bloqueos indefinidos de hilos.

### 5. Observabilidad y Métricas

- Registro de métricas usando Micrometer `MeterRegistry`:
  - `ai.fallback.attempts` con tags `capability` (recommendation, vision, stt, tts), `provider`, `model`, `status` (success, failure), `error_type`.
  - `ai.fallback.latency` (Timer) por candidato.
- Evento de negocio `RecommendationGenerated`:
  - `RecommendationGeneratedPayload` transporta `provider` y `modelUsed` efectivos.
  - Registro en SLF4J MDC del `provider` y `model` resuelto para trazabilidad en logs con `X-Correlation-Id`.

---

## Plan de Pruebas Unitarias (TDD)

1. **`AiErrorClassifierTest`**:
   - Clasificación de `TimeoutException` -> `RECOVERABLE` / `TIMEOUT`
   - Clasificación de HTTP 429 -> `RECOVERABLE` / `RATE_LIMITED`
   - Clasificación de HTTP 503 -> `RECOVERABLE` / `UNAVAILABLE`
   - Clasificación de `JsonProcessingException` de LLM -> `RECOVERABLE` / `INTERNAL_ERROR`
   - Clasificación de `IllegalArgumentException` / invalid input -> `NON_RECOVERABLE` / `INVALID_INPUT`
   - Verificación de que NO se usan subcadenas arbitrarias en strings de mensajes
2. **`FallbackAiRecommendationAdapterTest`**:
   - Primario exitoso -> Secundario no se invoca, resultado devuelto con metadata del primario
   - Primario falla 429 recuperable -> Secundario invocado y devuelve resultado exitoso (HTTP 200)
   - Fallo intra-proveedor (Gemini Pro -> Gemini Flash) antes de inter-proveedor (OpenAI)
   - Primario falla por validación de entrada (no recuperable) -> Se detiene inmediatamente, secundario nunca se invoca
   - Toda la cadena falla con errores recuperables -> Se lanza `AiProviderException(UNAVAILABLE)`, cero mocks devueltos
   - Cadena sin credenciales válidas -> Falla de inmediato con 503 controlado
3. **Pruebas de Multimodalidad (P2)**:
   - Fallback de STT (Groq -> Gemini STT)
   - Fallback de Visión (Gemini Vision primario -> secundario)
   - Fallback de TTS (ElevenLabs -> degradación o secundario)

---

## Complexity Tracking

| Componente | Justificación | Alternativa más simple rechazada |
|---|---|---|
| Adaptador Compuesto por Puerto | Permite mantener intactos los servicios de aplicación (`AiRecommendationService`) cumpliendo la Arquitectura Hexagonal y Open/Closed Principle. | Modificar `AiRecommendationService` para tener loops de reintento: rechazada porque violaría la separación de capas al filtrar lógica de infraestructura en la capa de aplicación. |
| Clasificador Tipado de Errores | Garantiza exactitud al discriminar entre fallas temporales de red/cuota y errores sintácticos de cliente sin depender de textos frágiles en inglés/español. | Análisis de texto del mensaje de error: rechazada por fragilidad demostrada en revisiones de ADR-001. |
