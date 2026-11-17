# Implementation Plan: AI Fallback Configuration, Resilience & Observability (HU-28)

**Branch**: `feature/HU-28-Configuracion,-resiliencia-y-observabilidad-del-fallback-de-IA` | **Date**: 2026-11-10 | **Spec**: [specs/028-ai-fallback-config/spec.md](spec.md)

**Input**: Feature specification from `specs/028-ai-fallback-config/spec.md` and architectural decisions from `docs/adr/0007-ai-provider-fallback-chain.md`.

---

## Summary

Extender el mecanismo de fallback de IA (HU-27) con configuración tipada y jerárquica bajo `app.ai.fallback.*` por capacidad (`chat`, `vision`, `stt`, `tts`, `rag`, `embedding`), soporte dual para sobreescritura mediante variables de entorno (sintaxis compacta `AI_<CAPABILITY>_FALLBACK_CHAIN=provider:model,...` más relaxed binding de Spring) manteniendo total retrocompatibilidad con `AI_PROVIDER` y `AI_AUDIO_PROVIDER`. Implementar validación estricta al arranque (*fail-fast* ante cadenas vacías, timeouts <= 0 o proveedores no soportados). Incorporar aislamiento y resiliencia con **Resilience4j** mediante instancias de Circuit Breaker independientes por tupla `capacidad:proveedor:modelo` que omiten llamadas a la red en < 5 ms cuando el circuito está abierto. Completar la observabilidad exportando métricas Micrometer (`ai.fallback.attempts`, `ai.fallback.provider.used`, `ai.fallback.circuit.state`), trazabilidad con `X-Correlation-Id` en logs (sin fugas de credenciales), un endpoint de administración `GET /api/admin/ai/providers` restringido a `ROLE_ADMIN` y un `HealthIndicator` agregado (`AiFallbackHealthIndicator`) en Actuator (`UP`/`DEGRADED`). Documentar variables en `.env.example`, `docker-compose.yml` y `docs/INTEGRATIONS.md`.

---

## Technical Context

- **Language/Version**: Java 21 LTS
- **Primary Dependencies**: Spring Boot 3.5.x, Spring AI 1.1.x, Spring Boot Actuator, Micrometer Prometheus, Spring Security 6, Resilience4j 2.2.0 (`resilience4j-spring-boot3` / `resilience4j-circuitbreaker`, `resilience4j-micrometer`), SLF4J/Logback, Jackson
- **Storage**: No requiere persistencia en base de datos ni migraciones Flyway (PostgreSQL permanece inalterado)
- **Testing**: JUnit 5, Mockito, AssertJ, Spring MockMvc (unit tests aislados sin red ni claves reales)
- **Target Platform**: Linux / JVM en contenedor
- **Project Type**: Spring Boot Modular Monolith (backend core)
- **Performance Goals**: Omitir proveedores con circuito abierto en < 5 ms; detección de fallos de configuración al arranque en < 5 s; respuesta del endpoint admin en < 50 ms
- **Constraints**:
  - Arquitectura Hexagonal estricta: todo componente de configuración, adaptación, endpoints y telemetría reside en `infrastructure/`. El dominio (`com.riwi.skillbridge.domain`) permanece puro sin frameworks.
  - Cero mocks en producción: agotamiento de la cadena devuelve HTTP 503 (`AiProviderException(UNAVAILABLE)` con `ProblemDetail`).
  - Cero secretos expuestos: ninguna clave de API ni credencial debe aparecer en logs, métricas ni respuestas del endpoint administrativo.
  - Fail-fast estricto al arrancar si la configuración contiene timeouts <= 0, cadenas vacías sin candidatos habilitados o proveedores desconocidos.
- **Scale/Scope**: 6 capacidades soportadas (`chat`, `vision`, `stt`, `tts`, `rag`, `embedding`), compatibilidad con proveedores existentes (`gemini`, `openai`, `groq`, `elevenlabs`, `noop`).

---

## Constitution Check

*GATE: All principles from `.specify/memory/constitution.md` evaluated.*

| Constitution Article | Compliance Analysis | Status |
|---|---|---|
| **Article I: Hexagonal Architecture & Pure Domain** | La configuración tipada, validación, circuit breaker, endpoint administrativo y métricas residen exclusivamente en `infrastructure/` (`config`, `adapter/in/rest`, `adapter/out/ai/resilience`). El paquete `com.riwi.skillbridge.domain` y los puertos `application/port/out` no se modifican ni se acoplan a frameworks. | **PASS** |
| **Article II: Multimodal AI Integration Ports & Boundaries** | Los puertos `AiRecommendationPort`, `VisionPort`, `SpeechToTextPort`, `TextToSpeechPort` continúan desacoplados. La gestión del circuit breaker se orquesta dentro de los adaptadores de infraestructura o ejecutores de candidatos. | **PASS** |
| **Article III: Security, Tokenization & Sensitive Data Invariants** | El endpoint `GET /api/admin/ai/providers` exige `@PreAuthorize("hasRole('ADMIN')")`. Acceso no autenticado retorna 401 y sin rol retorna 403. Ni la respuesta JSON, ni el HealthIndicator, ni los logs imprimen credenciales o API keys. | **PASS** |
| **Article IV: Observability, Traceability & Correlation IDs** | Toda traza de log del circuit breaker, conmutación y administración incluye `X-Correlation-Id` extraído de `CorrelationIdHolder`. Las métricas de Micrometer registran `ai.fallback.attempts`, `ai.fallback.provider.used` y estados de circuitos. | **PASS** |
| **Article V: Truthful Verification & No Silent Mocks** | Prohibición absoluta de respuestas simuladas o heurísticas ante agotamiento de la cadena (HTTP 503 con ProblemDetail). Pruebas unitarias de Resilience4j, configuración y seguridad ejecutadas físicamente en la sesión con salida verificada. | **PASS** |
| **Article VI: Strict Non-Destructive Operations & Change Boundaries** | No se modifican rutas protegidas (`docs/audit/**`, migraciones Flyway existentes). Trabajo acotado estrictamente a `backend/`, `specs/028-ai-fallback-config/`, y documentación (`.env.example`, `docker-compose.yml`, `docs/INTEGRATIONS.md`). | **PASS** |

---

## Project Structure & Impacted Files

```text
backend/
├── pom.xml                                               # Añadir dependencia resilience4j-spring-boot3
├── src/main/java/com/riwi/skillbridge/
│   ├── infrastructure/
│   │   ├── config/
│   │   │   ├── AiProviderProperties.java                 # Retrocompatibilidad y referencia a fallback properties
│   │   │   ├── AiFallbackProperties.java                 # Configuración tipada @ConfigurationProperties(prefix = "app.ai.fallback")
│   │   │   ├── AiFallbackValidator.java                  # Validador fail-fast al arranque
│   │   │   ├── AiFallbackConfiguration.java              # Integración de properties, Circuit Breaker Manager y adaptadores
│   │   │   └── AiFallbackEnvironmentPostProcessor.java    # Parser y binding para sintaxis compacta de variables de entorno
│   │   ├── adapter/out/ai/
│   │   │   ├── resilience/
│   │   │   │   ├── AiCircuitBreakerManager.java          # Registro y gestión de CircuitBreakers Resilience4j por capacidad:proveedor:modelo
│   │   │   │   └── ProviderCircuitStatus.java            # DTO/Record inmutable para estado de circuitos
│   │   │   ├── metrics/
│   │   │   │   └── AiFallbackMetricsRecorder.java        # Soporte para ai.fallback.provider.used y métricas de circuitos
│   │   │   └── fallback/
│   │   │       ├── FallbackAiRecommendationAdapter.java  # Integración con Circuit Breaker (salto inmediato cuando OPEN)
│   │   │       ├── FallbackVisionAdapter.java            # Integración con Circuit Breaker
│   │   │       ├── FallbackSpeechToTextAdapter.java      # Integración con Circuit Breaker
│   │   │       └── FallbackTextToSpeechAdapter.java      # Integración con Circuit Breaker
│   │   ├── adapter/in/rest/
│   │   │   └── admin/
│   │   │       ├── AiAdminController.java                # GET /api/admin/ai/providers (@PreAuthorize("hasRole('ADMIN')"))
│   │   │       └── dto/
│   │   │           └── AiProvidersStatusResponse.java    # DTO de respuesta segura sin claves
│   │   └── health/
│   │       └── AiFallbackHealthIndicator.java            # Spring Boot Actuator HealthIndicator (UP / DEGRADED)
│   └── application/                                      # Sin modificaciones de contratos
│
└── src/test/java/com/riwi/skillbridge/
    └── infrastructure/
        ├── config/
        │   ├── AiFallbackPropertiesTest.java             # Pruebas de binding, retrocompatibilidad y sintaxis compacta
        │   └── AiFallbackValidatorTest.java              # Pruebas de fail-fast al arranque
        ├── adapter/out/ai/resilience/
        │   └── AiCircuitBreakerManagerTest.java          # Pruebas de Circuit Breaker (open, skip < 5ms, half-open, close)
        ├── adapter/in/rest/admin/
        │   └── AiAdminControllerTest.java                # Pruebas de seguridad RBAC (401, 403, 200) y sanitización
        └── health/
            └── AiFallbackHealthIndicatorTest.java        # Pruebas de indicador de salud agregado

.env.example                                              # Documentación de variables de fallback
docker-compose.yml                                        # Mapeo de variables de entorno en servicio backend
docs/INTEGRATIONS.md                                      # Documentación técnica de configuración y resiliencia
specs/028-ai-fallback-config/
├── spec.md                                               # Especificación aprobada
├── plan.md                                               # Este plan de implementación
└── tasks.md                                              # Desglose de tareas ejecutables
```

---

## Architectural & Design Details

### 1. Modelo de Configuración Tipada (`app.ai.fallback.*`)

La clase `AiFallbackProperties` (`@ConfigurationProperties(prefix = "app.ai.fallback")`) modelará de forma estructurada las capacidades:

```java
public class AiFallbackProperties {
    private CapabilityConfig chat = new CapabilityConfig();
    private CapabilityConfig vision = new CapabilityConfig();
    private CapabilityConfig stt = new CapabilityConfig();
    private CapabilityConfig tts = new CapabilityConfig();
    private CapabilityConfig rag = new CapabilityConfig();
    private CapabilityConfig embedding = new CapabilityConfig();
    // getters and setters...
}
```

Cada `CapabilityConfig` contiene:
- `List<ChainElement> chain`: Lista ordenada de candidatos `{provider, model, enabled, timeoutSeconds}`.
- `CircuitBreakerSettings circuitBreaker`: `{failureRateThreshold: 50.0f, slidingWindowSize: 10, waitDurationInOpenStateSeconds: 30, permittedNumberOfCallsInHalfOpenState: 3}`.
- `RetrySettings retry`: `{maxAttempts: 3, initialIntervalMs: 500, multiplier: 1.5}`.

### 2. Soporte Dual de Variables de Entorno & Retrocompatibilidad

1. **Sintaxis compacta**: Variables como `AI_CHAT_FALLBACK_CHAIN=gemini:gemini-3.8-flash,openai:gpt-4o-mini` o `AI_VISION_FALLBACK_CHAIN=...` se parsean normalizando espacios en blanco (`trim()`) e inyectando elementos habilitados con timeout por defecto.
2. **Standard Relaxed Binding**: Se pueden usar variables nativas de Spring como `APP_AI_FALLBACK_CHAT_CHAIN_0_PROVIDER=gemini`.
3. **Retrocompatibilidad**: Si la cadena para `chat` o `stt`/`tts` no se especifica explícitamente, se lee `AI_PROVIDER` (default `gemini`) y `AI_AUDIO_PROVIDER` (default `gemini`), construyendo una cadena primaria compatible.

### 3. Validación al Arranque (*Fail-Fast*)

`AiFallbackValidator` se ejecuta en la inicialización del contexto (mediante `@PostConstruct` o `InitializingBean`):
- Verifica que cada capacidad activa tenga al menos un candidato en su `chain` con `enabled == true`.
- Valida que todos los `provider` pertenezcan a los proveedores conocidos (`gemini`, `openai`, `groq`, `elevenlabs`, `noop`).
- Valida que `timeoutSeconds > 0` para todos los eslabones.
- Si alguna regla se incumple, lanza `IllegalStateException` con un mensaje descriptivo unívoco, impidiendo que la aplicación arranque en estado corrupto o inconsistente.

### 4. Circuit Breaker por Candidato y Capacidad (Resilience4j)

`AiCircuitBreakerManager` encapsula un `CircuitBreakerRegistry` de Resilience4j configurado con los parámetros de `AiFallbackProperties`:
- Para cada tupla `capability:provider:model`, se genera un nombre único (ej. `chat:gemini:gemini-3.8-flash`).
- Antes de invocar a un candidato, el adaptador consulta `circuitBreaker.tryAcquirePermission()`.
  - Si el circuito está `OPEN`, se omite inmediatamente (tiempo de desvío < 5 ms) registrando log WARN estructurado con `X-Correlation-Id`.
  - Si tiene permiso, se invoca la llamada y se reporta `onSuccess` o `onError` según la clasificación tipada de `AiErrorClassifier` (errores no recuperables como `INVALID_INPUT` no incrementan la tasa de fallos del circuito).

### 5. Observabilidad & Métricas

- Micrometer `MeterRegistry`:
  - `ai.fallback.attempts`: contador con tags `capability`, `provider`, `model`, `status`, `error_type`.
  - `ai.fallback.provider.used`: contador con tags `capability`, `provider`, `model` emitido únicamente por el candidato que resolvió la llamada con éxito.
  - Registro de métricas de Resilience4j (`TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry).bindTo(meterRegistry)`).
- Logs estructurados con MDC `X-Correlation-Id`.
- **Cero secretos**: Se auditan todas las salidas de logs y serializaciones JSON para garantizar la ausencia total de tokens o credenciales.

### 6. Endpoint Administrativo & Health Indicator

- Controller `AiAdminController` en `/api/admin/ai/providers`:
  - Anotado con `@RestController`, `@RequestMapping("/api/admin/ai/providers")`, `@PreAuthorize("hasRole('ADMIN')")`.
  - Retorna `AiProvidersStatusResponse` con la lista de capacidades, orden de candidatos en la cadena, timeout, estado actual de su circuit breaker (`CLOSED`, `OPEN`, `HALF_OPEN`), y tasa de fallos reciente.
  - No contiene campos de API keys, URLs con credenciales ni secretos.
- `AiFallbackHealthIndicator` implementa `HealthIndicator`:
  - Si todos los circuitos de todas las capacidades están `CLOSED`: `Health.up()`.
  - Si algún circuito está `OPEN` o `HALF_OPEN`, pero hay candidatos de respaldo disponibles: `Health.status("DEGRADED")`.
  - Si una capacidad tiene todos sus circuitos `OPEN`: `Health.down()`.
  - Expone conteos y estados de alto nivel sin exponer configuraciones sensibles.

---

## Verification & Acceptance Plan

1. **Unit Tests**:
   - `AiFallbackPropertiesTest`: Comprueba el binding desde YAML, relaxed env vars, sintaxis compacta y retrocompatibilidad con `AI_PROVIDER`.
   - `AiFallbackValidatorTest`: Comprueba el lanzamiento inmediato de `IllegalStateException` ante cadena vacía, proveedor inválido y timeouts <= 0.
   - `AiCircuitBreakerManagerTest`: Comprueba transiciones `CLOSED -> OPEN -> HALF_OPEN -> CLOSED`, y salto en < 5 ms en estado OPEN.
   - `FallbackAiRecommendationAdapterTest`: Verifica que la recomendación se salta un candidato con circuito OPEN y delega al siguiente sin demora.
   - `AiAdminControllerTest`: MockMvc verifica 401 sin auth, 403 con `ROLE_CUSTOMER` y 200 con `ROLE_ADMIN` (comprobando ausencia de claves).
   - `AiFallbackHealthIndicatorTest`: Verifica estados UP, DEGRADED y DOWN según el estado de los circuitos.
2. **Build & Package Verification**:
   - `mvn -f backend/pom.xml test` ejecuta la suite de pruebas unitarias con 0 fallos.
3. **Documentation**:
   - Actualización de `.env.example`, `docker-compose.yml` y `docs/INTEGRATIONS.md`.
