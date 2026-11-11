# Refinamiento de HUs — noviembre 2026

Contexto nuevo: arquitectura **híbrida** (monolito + `audit-service` + `mentorship-service` NestJS + job Spark),
ver [ADR-0006](../adr/0006-hybrid-monolith-and-services.md). Este documento propone cambios a las HUs de la lista;
**nada de lo propuesto está aprobado**. Las decisiones abiertas están marcadas **[DECIDIR]** con una recomendación.

Convención: `[VERIFIED]` confirmado en el repo en esta sesión · `[INFERRED]` deducido · `[UNKNOWN]` sin confirmar.

> **Matriz de relaciones:** Para consultar el mapa de dependencias directas, prerrequisitos técnicos y la ruta de ejecución recomendada entre estas HUs, ver [Matriz y Grafo de Dependencias de HUs Pendientes](hu-dependencies.md).

## 0. Panorama

| HU | Issue | Estado real | Acción propuesta |
|---|---|---|---|
| HU-16 Observabilidad | #10 | Parcial: hay Actuator, Prometheus y Grafana, pero pensado para un solo backend | Reescribir al contexto multi-sistema (§4) |
| HU-22 Demanda | #33 | **Casi entregada** en el monolito, issue aún abierta | Cerrar alcance y separar de Spark (§3) |
| HU-25 Spark | #76 | Spec en Draft, sin código | Acotar a métricas diarias; relación con HU-22 (§3) |
| HU-26 RAG | #77 | Spec en Draft, sin código | Ajustar corpus y sincronización al contexto nuevo (§1) |
| HU-27 Fallback IA | #78 | Spec en Draft, sin código | Fusionar o secuenciar con HU-28 (§2) |
| HU-28 Resiliencia IA | #79 | Spec en Draft, sin código | Idem; resolver contradicción de "mock" (§2) |
| MCP / consumidor agéntico | — | **No existe HU ni spec** | Justificación y alcance propuestos (§5) |
| Mentorías MNT-01..09 | #83–#91 | Contratos escritos; monolito adaptado | Ver `docs/contracts/mentorships/` |

## 1. HU-26 — Asistente RAG (#77)

**Hechos [VERIFIED]:** no hay código de RAG. Existen `OfferingRepositoryPort.findAllActive()`,
`CategoryRepositoryPort`, `BookingCancellationPolicy` (24 h) y Spring AI 1.1.8. La UI de IA es
`frontend/src/app/features/ai.component.ts`.

**Qué cambió desde que se escribió la spec:**
1. Las ofertas ahora llevan `mode` y `durationHours` (V13). El fragmento de oferta debe incluirlos, o el
   asistente no podrá responder "¿es en vivo o asincrónica?".
2. Las mentorías migran a un servicio externo. El RAG **no debe consultar** al servicio Nest (regla: sin
   dependencias síncronas entre servicios). Las reglas de mentoría que explique salen de **documentos de
   política públicos y curados**, no de los contratos de ingeniería.
3. La spec dice que sincronizar el índice "con eventos `OfferingCreated`…". **Esos son eventos de auditoría
   en Kafka, no eventos de dominio** [VERIFIED en `OfferingCommandService`]. Engancharse a ellos acopla el RAG a la
   auditoría.

**Cambios propuestos a la spec:**
- FR-004: añadir `mode`/`durationHours` al fragmento `OFFERING`. Añadir fuente `POLICY_DOC` en
  `backend/src/main/resources/knowledge/*.md` (cancelación, "1 pago = 1 mentoría", estados visibles al usuario,
  FAQ). Prohibido indexar `docs/contracts/**`, `docs/audit/**`, `RISK-REGISTER.md`.
- Sincronización (decisión 2 de la spec): refrescar el índice **cuando se invalida la caché de ofertas**
  (`cache.evictActiveOfferings()` ya existe), no con eventos de auditoría.
- Dependencia: HU-27/28 pasan de "dependencia" a **requisito de integración**, no de arranque (el RAG puede
  empezar con el adaptador actual y adoptar la cadena después).
- Mantener sin cambios: BM25 en memoria, rechazo sin invocar al LLM, `sources` solo si fueron citadas,
  `HTTP 503` sin mock.

**[DECIDIR]** Idioma (spec §Decisiones 1): recomendado **A** (siempre español).
**[DECIDIR]** UI de fuentes (3): recomendado **A** (tarjetas con enlace), pero P3 si hay que recortar.

## 2. HU-27 y HU-28 — Fallback y resiliencia de IA (#78, #79)

**Hechos [VERIFIED]:** hay adaptadores `GeminiAiAdapter`, `OpenAiAiAdapter`, `GeminiVisionAdapter`,
`GeminiSpeechToTextAdapter`, `GroqSpeechToTextAdapter`, `ElevenLabsTextToSpeechAdapter`, `NoOpTextToSpeechAdapter`.
Selección por `AI_PROVIDER` y `AI_AUDIO_PROVIDER`. **No hay Resilience4j** en `pom.xml` [VERIFIED]; solo
Actuator + Micrometer-Prometheus. `AiProviderException` ya tiene tipos `TIMEOUT`/`UNAVAILABLE`.

**Problemas de las specs:**
1. **Solapamiento fuerte.** 027 = adaptador compuesto + cadena; 028 = configuración de la cadena + circuit breaker
   + métricas + endpoint admin. 028 no tiene sentido sin 027 y ambas tocan los mismos puertos.
2. **Contradicción:** 028 (degradación, opción B) admite devolver un *mock* al agotarse la cadena; 026 FR-009 y
   027 FR-006 lo **prohíben**. Debe cerrarse como **503 sin mock** (recomendado y coherente con RAG-001).
3. 028 FR-004 define `AI_CHAT_FALLBACK_CHAIN=gemini:gemini-3.8-flash,…` con un nombre de modelo que no
   verifiqué; los ejemplos deben ser placeholders o salir de la documentación del proveedor.
4. Falta resolver **cómo** se implementa el circuit breaker: Resilience4j (dependencia nueva) o propio.

**[DECIDIR] Estructura** — recomendado: **una sola épica en dos entregas**, manteniendo los dos issues:
- *Entrega 1 (HU-27):* adaptador compuesto, cadena ordenada, clasificación recuperable/no recuperable,
  503 sin mock, métricas por intento.
- *Entrega 2 (HU-28):* configuración `app.ai.fallback.*` con fail-fast, circuit breaker por `provider:model`,
  `GET /api/admin/ai/providers`, `HealthIndicator` agregado.
**[DECIDIR] Circuit breaker** — recomendado: **Resilience4j** (librería estándar, integra con Micrometer; coherente
con el `opossum`/breaker que usará el equipo Nest en MNT-09, mismo patrón en ambos lenguajes).
**[DECIDIR]** Exponer proveedor/modelo en la respuesta REST (spec 027): recomendado **no** exponerlo al cliente;
solo en `RecommendationGenerated`, métricas y el endpoint admin.

## 3. HU-22 (#33) y HU-25 Spark (#76)

**Hechos [VERIFIED]:** HU-22 ya tiene `GetTrendingServicesUseCase`, `DemandAnalyticsService`,
`RecommendationAnalyticsRepositoryPort`, `AiController` (`GET /api/ai/trending`), `TrendingServiceResponse`,
migraciones **V11 (eventos de recomendación) y V12 (métricas diarias)**, consumidor `KafkaAnalyticsConsumer` y
vista Angular (`trending.service.ts`, rutas). Es decir, **el camino "en línea" ya existe**.

**El conflicto de fondo:** HU-22 exige "los cálculos pueden realizarse de nuevo a partir de los eventos
almacenados" y "no introducir un microservicio de ML innecesario"; HU-25 propone Spark para calcular lo mismo.
Sin una frontera clara, se duplica el cálculo y hay dos fuentes de verdad.

**Frontera propuesta:**

| | HU-22 (monolito) | HU-25 (Spark) |
|---|---|---|
| Rol | Servir tendencias **en línea** a la app | **Reprocesar el histórico** fuera del camino transaccional |
| Datos | Postgres: `recommendation_events`, `recommendation_daily_metrics` | Eventos exportados de Kafka (JSON/Parquet) |
| Métricas | `trendScore`, `conversionRate`, forecast 7 días | Agregados diarios por oferta: recomendaciones, reservas, cancelaciones, conversión |
| Escribe en | Tablas del monolito | **Solo** Parquet/almacén analítico aparte, nunca tablas OLTP |
| Cuándo | Tiempo real / incremental | Batch bajo demanda (perfil `analytics` de compose) |

Esto coincide con la opción A recomendada en la propia spec 025 (MVP = métricas directas; `trend_score` y
forecast quedan en HU-22).

**Cómo trabajan juntas:** Spark **valida** a HU-22 (mismo resultado en un dataset compartido, FR-007 de la spec)
y habilita volúmenes que el monolito no debería cargar. HU-22 no depende de Spark para funcionar.

**[DECIDIR]** ¿Cerrar HU-22 con lo que hay? Faltan por confirmar con el PO: el criterio "relacionar recomendaciones
con reservas" y la cobertura de pruebas de `trendScore`. **[UNKNOWN]** no ejecuté las pruebas de analítica a fondo ni
verifiqué el flujo Kafka real (requiere Docker).
**[DECIDIR]** Spec 025, destino analítico: recomendado **Parquet** (opción A). Timestamps futuros: **cuarentena** (A).
**[DECIDIR]** ¿Se justifica Spark hoy? La spec 025 exige "umbrales cuantitativos de adopción" en su README
(FR-011). Con el volumen actual (< 100 ofertas) Spark es sobredimensionado; su valor es **educativo/arquitectónico**
y como validador independiente. Recomendado: mantenerlo **opcional** (perfil `analytics`) y decir esto
explícitamente en la spec para no engañar sobre su necesidad.

## 4. HU-16 — Observabilidad (#10)

**Hechos [VERIFIED]:** `spring-boot-starter-actuator` y `micrometer-registry-prometheus`; endpoints expuestos
`health,info,prometheus,metrics`; etiqueta `application`; perfil `observability` de compose con Prometheus 3.5.0 y
Grafana 12.1.1; `ops/prometheus/prometheus.yml` y `ops/grafana/provisioning/`. Credenciales de Grafana
`admin/admin` en compose [VERIFIED] (aceptable solo en local; el binding es `127.0.0.1`).

**Por qué la HU actual se queda corta:** está redactada para "el sistema" como si fuera un solo proceso. Ahora hay
**cuatro** (monolito, audit-service, mentorship-service, job Spark) y dos brokers.

**Reescritura propuesta (criterios nuevos):**
1. Cada servicio expone health y métricas Prometheus con `application=<nombre>` (Spring: Actuator; Nest:
   `/metrics` con `prom-client`; contrato en `docs/contracts/`).
2. Prometheus raspa los cuatro; **un** dashboard con: tasa de errores 4xx/5xx y latencia por servicio, lag de
   consumidores Kafka/RabbitMQ, mensajes en DLQ (`mentorship.payment.dlq`, DLQ de notificaciones).
3. **Trazabilidad entre sistemas:** `X-Correlation-Id` se propaga monolito → RabbitMQ → Nest → Kafka → audit.
   Criterio verificable: dado un `correlationId` se encuentra el rastro completo en `audit-service`.
4. Métricas de IA de HU-28 (`ai.fallback.*`) aparecen en el mismo dashboard.
5. Sin secretos ni datos sensibles en métricas/logs (se mantiene). Credenciales de Grafana por variable de
   entorno, no `admin/admin` fuera de local.
6. Se mantiene la regla: **no bloquear** otras HUs por un dashboard avanzado.

**Orden de entrega:** (1)+(2) mínimos primero; (3) depende de que Nest propague el header (cláusula de contrato en
MNT); (4) tras HU-28.

**[UNKNOWN]** No verifiqué el contenido de `ops/grafana/provisioning/` ni qué dashboards existen.
**[DECIDIR]** ¿Alertas (Alertmanager) dentro de HU-16 o aparte? Recomendado: **aparte**, para no inflar la HU.

## 5. MCP y consumidor agéntico — justificación

No hay HU. Para no inventar una necesidad, se parte del **dolor real que introduce la arquitectura híbrida**:
diagnosticar un fallo que cruza varios sistemas. Hoy un incidente tipo "pagué y no veo mi mentoría" exige mirar
logs del monolito, la cola RabbitMQ, el servicio Nest y `audit-service`.

**Propuesta A (recomendada) — Servidor MCP de operaciones, solo lectura.**
Herramientas: `audit_by_correlation(correlationId)`, `booking_status(bookingId)`, `ai_providers_status()` (HU-28),
`trending(days)` (HU-22), `dlq_summary()`. Un Admin pregunta en lenguaje natural y el agente cruza fuentes.
- Justificación: depende de lo ya construido (correlation IDs HU-17, `audit-service`, métricas HU-16/28).
- Seguridad: el servidor MCP **no tiene credenciales propias de administrador**; actúa con el JWT RS256 del usuario
  que lo invoca y hereda sus permisos. Solo lectura en el MVP.

**Propuesta B (P2) — Agente consumidor para el Customer.**
Herramientas: `search_offerings`, `ask_knowledge` (HU-26), `propose_booking`. **La creación de la reserva y el pago
requieren confirmación humana explícita**; el agente nunca ejecuta `authorize payment` (regla de pagos: sin
PAN/CVV, Stripe tokenizado). Valor: demuestra HU-26 como herramienta reutilizable.

**No recomendado:** un agente con permisos de escritura sobre reservas/pagos sin confirmación.

**Riesgos que la spec deberá cubrir:** inyección de prompt vía datos (nombres de ofertas, descripciones),
exposición de PII en `audit_by_correlation`, y límites de tasa/coste.
**[DECIDIDO 2026-11-09]** Es una HU nueva: **HU-29, issue #93**, spec en `specs/029-incident-triage-agent/`
(la carpeta sigue el número de HU, no el del issue). Se condiciona a una **prueba de utilidad** frente a una línea
base sin IA; ver §7.

## 6. Decisiones que necesito de ti (resumen)

1. HU-27/28: ¿una épica en dos entregas con **Resilience4j**, 503 **sin mock**? (recomendado: sí)
2. HU-26: idioma (A: español) y sincronización por invalidación de caché (no por eventos de auditoría).
3. HU-22: ¿se da por entregada con lo que hay, tras revisar con el PO los dos puntos pendientes?
4. HU-25: Parquet, cuarentena de timestamps futuros, y Spark declarado **opcional**.
5. HU-16: ¿se reescribe como propone §4, con alertas aparte?
6. MCP: ¿Propuesta A como HU nueva (y B como P2)?

Después de tus respuestas actualizo las specs 025–028, creo la de MCP y dejo plan/tareas donde la clasificación SDD
lo exija. No creo ni edito issues de GitHub sin tu confirmación.

## 7. Registro de decisiones (2026-11-09)

| # | Tema | Decisión | Estado |
|---|---|---|---|
| 1 | HU-27/28 | Una épica en dos entregas; Resilience4j; **503 sin mock** | Decidido; aplicado en specs 027 y 028 |
| 2 | HU-26 idioma | **Español e inglés** (responde en el idioma de la pregunta) | Decidido; ver límite léxico en spec 026 FR-016 |
| 2 | HU-26 índice | Reconstrucción por invalidación de la caché de ofertas, sustitución atómica | Decidido; aplicado |
| 2 | HU-26 corpus | Documentos de política bilingües creados en `backend/src/main/resources/knowledge/` | Hecho |
| 3 | HU-22 | **No se da por entregada**; solo se cierran los puntos pendientes | Decidido; `specs/022-demand-analytics/`, issue #33 reescrito |
| 4 | HU-25 | Frontera con HU-22 aplicada; las 3 decisiones de producto (Parquet, métricas, timestamps) siguen abiertas | **Abierto** (a explicar) |
| 5 | HU-16 | Reescrita al contexto multi-servicio; alertas aparte | Hecho; issue #10 reescrito |
| 6 | MCP | HU nueva con issue; la IA debe ser la solución, no un adorno | HU-29, issue #93, con prueba de utilidad |

### Puntos de HU-22 que quedan por cerrar (verificados)

1. `BookingCreated` no lleva `recommendationId`: la conversión actual no mide conversión de recomendaciones. Decisión abierta A (frontend + migración) o B (heurística).
2. Faltan pruebas de `GET /api/ai/trending` y de la vista Angular.
3. Falta la operación de reconstrucción de métricas desde `recommendation_events`.

### Lo que NO está definido y por eso no se documentó en el corpus RAG

Reembolsos, política de ausencia y reprogramación de mentorías (issue #68). El asistente responderá "sin información suficiente" hasta que el Product Owner las defina.

### Pendiente de ejecutar

- Implementación de HU-26, 27, 28, 29 y de lo que falta de HU-22 (solo existen specs).
- `plan.md`/`tasks.md` de las HUs clasificadas como grandes (022, 026, 027, 028, 029).
- Consolidación de documentación (delegada a `agy`, tarea `DOCS-CONSOLIDATION-001`): el resultado se revisa antes de aplicarlo.

## 8. Decisiones de Spark y atribución (2026-11-09)

| Tema | Decisión |
|---|---|
| HU-25: obligatoriedad | Spark es **obligatorio** (requisito del curso); motor ligero como referencia de validación |
| HU-25: lenguaje | **Python (PySpark)**, independiente del proyecto Java (Spark necesita un JDK en tiempo de ejecución, encapsulado en su contenedor) |
| HU-25: entrada | **Archivos JSONL exportados** + `manifest.json` (`production` o `synthetic`); el job no se conecta a Kafka ni a bases de datos |
| HU-25: destino | Parquet particionado por fecha |
| HU-25: alcance | Solo agregados diarios por oferta (tendencia y pronóstico siguen en HU-22) |
| HU-25: eventos futuros | Cuarentena, sin abortar |
| HU-22: atribución | **Opción A**: `recommendationId` en la reserva, `BookingCreated`/`BookingCancelled` v2, migración nueva |
| HU-22: defecto previo | `RecommendationGenerated` no lleva ofertas y el consumidor acumula por `userId`; hay que arreglarlo antes (FR-008 a FR-010) |

Contrato compartido: [`docs/contracts/analytics-events.md`](../contracts/analytics-events.md).

## 9. Recuperación del RAG (2026-11-09)

El propietario decidió **adoptar embeddings y un almacén vectorial** en HU-26 (antes: solo BM25). Motivo: aprendizaje y cruce de idiomas; con este volumen BM25 bastaría. **No se conserva BM25** (decisión posterior del mismo día): solo embeddings y almacén vectorial. Proveedor de embeddings: Gemini, con la integración y clave ya existentes (modelo y detalles se verifican en el plan). Decisión: [ADR-0008](../adr/0008-rag-retrieval-embeddings-and-vector-store.md); explicación: [guía](../guides/rag-bm25-vs-embeddings.md). Pendiente de confirmar: almacén (recomendado pgvector; hay que verificar la extensión en la PostgreSQL de Railway). Impacto: spec 026 (FR-005, FR-016 a FR-019, SC-002, fuera de alcance), spec 028 (capacidad `embedding`) y migración nueva para el almacén vectorial.
