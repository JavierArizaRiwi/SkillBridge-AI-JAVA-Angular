# ADR-0007: Cadena de fallback de proveedores de IA

## Status

Proposed (requiere aprobación humana). Sustituye parcialmente a [ADR-001](ADR-001-AI-PROVIDER-ABSTRACTION.md): reemplaza la selección de **un único adaptador activo por propiedad**; conserva los puertos, el aislamiento del dominio y el uso de Spring AI.

## Date

2026-11-09

## Context

[ADR-001](ADR-001-AI-PROVIDER-ABSTRACTION.md) permite cambiar de proveedor de IA por configuración, pero solo uno a la vez y solo al arrancar. Su revisión (`docs/refinement/adr-001-review.md`) y la de las specs HU-26/27/28 muestran por qué ya no basta `[VERIFIED]`:

- **No hay continuidad ante fallos.** Si el proveedor activo falla, se agota su cuota o tarda demasiado, la función de IA queda caída; ADR-001 admite el riesgo ("single point of failure") pero no lo resuelve.
- **El timeout declarado no se aplica.** `app.ai.timeout-seconds` no se usa en los adaptadores de chat.
- **Los errores se clasifican por el texto del mensaje** en Gemini y se colapsan a `UNAVAILABLE` en OpenAI, de forma inconsistente.
- **El sistema ya no es solo recomendaciones.** Hay puertos de visión, voz a texto y texto a voz, y el RAG (HU-26) añade otra capacidad. ADR-001 no los cubre.
- **Existe precedente de degradación peligrosa:** el hallazgo RAG-001 documentó un adaptador que, ante una clave inválida o un timeout, devolvía respuestas simuladas con precios fijos, ocultando una caída real.

## Decision

Cada puerto de salida de IA tendrá un **adaptador compuesto** que ejecuta una **cadena ordenada** de candidatos (`proveedor:modelo`) y es el bean inyectado en la aplicación.

1. **Un compuesto por puerto.** `AiRecommendationPort`, `VisionPort`, `SpeechToTextPort` y `TextToSpeechPort`; el RAG (HU-26) tiene su propia capacidad `rag` con su propia cadena (spec 028), y los embeddings del almacén vectorial son otra capacidad, `embedding` ([ADR-0008](0008-rag-retrieval-embeddings-and-vector-store.md)). Los servicios de aplicación no cambian de firma ni conocen la cadena.
2. **Cadena ordenada.** Puede mezclar modelos del mismo proveedor y de proveedores distintos. Se ejecuta en orden empezando por el primario.
3. **Conmutación solo ante errores recuperables:** timeout, cuota agotada (HTTP 429), errores 5xx y fallos de red. **Nunca** ante errores no recuperables (entrada inválida del cliente, 4xx por parámetros, reglas de negocio): se propagan sin probar otro modelo.
4. **Clasificación por tipo, no por texto.** Se decide por tipo de excepción y código HTTP; queda prohibido deducir el error buscando subcadenas en el mensaje.
5. **Timeouts aplicados en el cliente** (cliente HTTP / `ChatClient` o cancelación explícita), por candidato.
6. **Agotamiento = HTTP 503, sin simulación.** Si falla toda la cadena, se lanza `AiProviderException` tipada `UNAVAILABLE` y se responde `503` con `ProblemDetail`. **Queda prohibido devolver respuestas simuladas o heurísticas** salvo `app.ai.provider=mock` fijado explícitamente (entornos de prueba).
7. **Configuración declarativa** bajo `app.ai.fallback.*`, por capacidad (`chat`, `vision`, `stt`, `tts`, `rag`): lista `chain` con `{provider, model, enabled, timeout-seconds}`, más `max-attempts`, `retry-backoff` y `circuit-breaker`. Sobrescribible por variables de entorno. `AI_PROVIDER` y `AI_AUDIO_PROVIDER` siguen funcionando como valor primario de la cadena. **Validación al arrancar (fail-fast):** cadena vacía, proveedor no soportado o timeout ≤ 0 detienen el inicio con un mensaje claro.
8. **Circuit breaker por `proveedor:modelo` y capacidad**, con Resilience4j integrado con Micrometer. Un circuito abierto se salta sin esperar ni llamar a la red. Los proveedores sin credenciales se omiten al arrancar con un aviso que nunca imprime la clave.
9. **Observabilidad.** Métricas `ai.fallback.attempts`, `ai.fallback.provider.used` y estado del circuito; el proveedor y modelo que respondió se registran en el evento `RecommendationGenerated`, en logs y métricas, con el `X-Correlation-Id`. Endpoint administrativo `GET /api/admin/ai/providers` (solo `ROLE_ADMIN`) con la cadena efectiva y el estado de cada circuito. Ninguna clave ni secreto aparece en logs, trazas ni respuestas.

### Entrega

En dos pasos, manteniendo dos issues: **HU-27 (#78)** entrega el mecanismo (compuesto, cadena, clasificación, 503, métricas por intento) y **HU-28 (#79)** entrega configuración declarativa, circuit breaker, endpoint y salud.

### Decisiones aún abiertas

Se aplica la opción recomendada hasta que se indique lo contrario (ver specs 027 y 028):

- Priorizar disponibilidad y baja latencia aceptando explicaciones más concisas del modelo secundario, frente a limitar el fallback a modelos de capacidad equivalente.
- No exponer `provider`/`model` en la respuesta HTTP hacia el frontend (solo en eventos, logs, métricas y endpoint admin).
- Formato de variables de entorno: cadena compacta `provider:model,provider:model` más el *relaxed binding* estándar de Spring.
- Exposición de estado: endpoint administrativo con detalle completo más un `HealthIndicator` agregado (`UP`/`DEGRADED`) sin configuración interna.

## Alternatives considered

- **Mantener ADR-001 (un adaptador activo).** Rechazada: no da continuidad ante fallos y exige reiniciar para cambiar.
- **Degradar a una respuesta simulada o heurística al agotarse la cadena.** Rechazada: oculta fallos de infraestructura y contradice RAG-001, HU-26 FR-009 y HU-27 FR-006.
- **Enrutar por petición (A/B testing).** Fuera de alcance: es otro problema (reparto de tráfico, no continuidad) y requeriría un enrutador explícito; ADR-001 lo prometía pero su arquitectura no lo permite.
- **Circuit breaker propio.** Rechazada: Resilience4j es una librería establecida y se integra con Micrometer y Actuator, que ya están en el proyecto.

## Consequences

**Positivas**
- La función de IA sigue disponible ante la caída o el agotamiento de un proveedor.
- Los timeouts y la clasificación de errores se vuelven reales y consistentes entre proveedores.
- Se puede operar y diagnosticar sin recompilar (configuración, métricas, endpoint).
- El mismo mecanismo cubre chat, visión, voz y RAG.

**Negativas / riesgos**
- Un modelo secundario puede dar respuestas de distinta calidad o formato; todos deben producir la estructura unificada del puerto.
- Un fallback consume cuota y coste de otro proveedor; hay que vigilarlo con las métricas.
- Más configuración que validar y documentar (`.env.example`, `docker-compose.yml`, `INTEGRATIONS.md`).
- Dependencia nueva (Resilience4j) en el backend.
- Las comparativas de proveedores existentes son orientativas; el **orden** de la cadena debe decidirse con mediciones propias (≥ 100 muestras, varios prompts, datos versionados).

## Out of scope

Proveedores nuevos (Claude, Mistral, Cohere), interfaz gráfica de administración, bóveda de secretos, enrutado por petición y nuevas tablas o migraciones.

## References

- `specs/027-ai-model-fallback/spec.md` (#78) y `specs/028-ai-fallback-config/spec.md` (#79).
- [ADR-001](ADR-001-AI-PROVIDER-ABSTRACTION.md) y su [errata](ADR-001-AI-PROVIDER-ABSTRACTION.md#errata-2026-11-09).
- `docs/refinement/adr-001-review.md`.
- [ADR-0006](0006-hybrid-monolith-and-services.md): reglas de comunicación entre el monolito y los servicios satélite.
