# Feature Specification: Análisis predictivo de demanda — cierre de alcance (HU-22)

**Feature Branch**: `feature/022-demand-analytics` | **Created**: 2026-11-09 | **Status**: Draft (pendiente de aprobación humana) | **Issue**: [#33](https://github.com/Danilo-Doria/SkillBridge-AI-JAVA-Angular/issues/33) | **Sucede a**: entrega parcial en la rama `feature/HU-22-demand-analytics`
**Input**: Como plataforma, quiero identificar servicios en tendencia y estimar su demanda con datos fiables, para tomar decisiones sobre el catálogo. HU-22 NO se da por entregada: esta spec define lo que falta.

---

## Estado verificado de la entrega parcial `[VERIFIED]`

Existen: `GetTrendingServicesUseCase`, `DemandAnalyticsService`, `RecommendationAnalyticsRepositoryPort`, `GET /api/ai/trending` (`AiController`), migraciones V11 (`recommendation_events`) y V12 (`recommendation_daily_metrics`), `KafkaAnalyticsConsumer` (idempotente por `eventId`), 21 pruebas unitarias de las fórmulas (`DemandAnalyticsServiceTest`), una prueba del consumidor (`KafkaAnalyticsConsumerTest`) y la vista Angular `TrendingComponent`.

## Defecto previo verificado: el evento de recomendación no sirve para medir demanda

Hallado el 2026-11-09 leyendo el código (`[VERIFIED]` por lectura; **no ejecutado**: no hay Docker/PostgreSQL en la sesión):

1. `AiRecommendationService` publica `RecommendationGenerated` con `payload = request.inputType()` (solo el enum `TEXT|VOICE|IMAGE`); **no incluye las ofertas recomendadas**, aunque el resultado (`RecommendationResult.recommendations`) sí las conoce.
2. `KafkaAnalyticsConsumer` espera un `RecommendationGeneratedPayload(recommendationId, userId, goal, inputType, modelUsed, latencyMs)`, que tampoco lista ofertas. Con el payload realmente publicado, esos campos llegan nulos `[INFERRED]`.
3. El consumidor llama `incrementRecommendations(payload.userId(), fecha)` pero el puerto declara `incrementRecommendations(UUID offeringId, …)`: **pasa un id de usuario donde se espera un id de oferta**. La tabla `recommendation_daily_metrics.offering_id` es `NOT NULL` con clave foránea a `offerings`, así que esa inserción no puede ser válida `[INFERRED]`.
4. `KafkaAnalyticsConsumerTest` verifica `incrementRecommendations(eq(userId), …)` con el repositorio simulado: **la prueba fija el comportamiento erróneo** y por eso pasa.
5. En `recommendation_events` el `offeringId` se guarda siempre como `null` ("not available at recommendation time").

Consecuencia: el denominador de la conversión (recomendaciones por oferta) **no se puede calcular** con los eventos actuales. Esto bloquea tanto HU-22 como HU-25, que necesitan saber qué ofertas se recomendaron.

## Frontera con HU-25

HU-22 sirve las métricas **en línea** desde PostgreSQL. HU-25 (Spark) reprocesa el histórico fuera de línea y las valida. Ver tabla en `specs/025-spark-analytics/spec.md`. Esta spec NO añade procesamiento batch ni dependencias de Spark.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Conversión atribuida a recomendaciones reales (Priority: P1)

Como analista de negocio, quiero que la conversión mida cuántas reservas **provienen de una recomendación**, para no sobrestimar el valor de la IA con reservas que nunca pasaron por ella.

**Why this priority**: Hoy la conversión es `reservas efectivas de la oferta / recomendaciones de la oferta`. `BookingCreatedPayload` solo trae `bookingId, offeringId, customerId, status`; no hay `recommendationId` `[VERIFIED]`. Por tanto **toda** reserva de una oferta cuenta como conversión aunque el cliente la haya encontrado sin recomendación. La métrica responde a otra pregunta de la que anuncia.

**Independent Test**: Con 10 recomendaciones de una oferta y 4 reservas de esa oferta, de las cuales solo 1 se creó desde una recomendación, la conversión atribuida es 0,1 (1/10) y la tasa de reservas totales por recomendación sigue siendo 0,4 y se reporta aparte.

**Acceptance Scenarios**:
1. **Given** una reserva creada a partir de una recomendación, **When** se publica `BookingCreated`, **Then** el evento incluye el `recommendationId` de origen.
2. **Given** una reserva creada sin recomendación, **When** se publica `BookingCreated`, **Then** `recommendationId` es nulo y la reserva NO cuenta para la conversión atribuida.
3. **Given** una reserva atribuida que luego se cancela, **When** se procesa `BookingCancelled`, **Then** la conversión atribuida se reduce (reserva efectiva = creadas − canceladas, ambas atribuidas).
4. **Given** reservas anteriores a este cambio (sin atribución), **When** se consultan las métricas, **Then** siguen visibles como "no atribuidas" y no se reescribe el histórico.

### User Story 2 - Cobertura de pruebas del extremo a extremo (Priority: P2)

Como equipo, quiero pruebas del endpoint de tendencias y de la vista, para detectar regresiones de seguridad y de contrato.

**Why this priority**: Las fórmulas están bien cubiertas, pero `GET /api/ai/trending` no tiene prueba de controlador/seguridad y la vista Angular no tiene `.spec.ts` `[VERIFIED]`.

**Acceptance Scenarios**:
1. **Given** una petición sin autenticar, **When** se llama a `GET /api/ai/trending`, **Then** responde 401.
2. **Given** un usuario autenticado, **When** consulta tendencias, **Then** la respuesta cumple el contrato (campos, orden descendente por `trendScore`, límite y rango del parámetro).
3. **Given** la vista de tendencias, **When** hay datos, vacío o error, **Then** muestra lista, estado vacío o mensaje de error respectivamente.

### User Story 3 - Recalcular desde eventos almacenados (Priority: P3)

Como equipo, quiero poder reconstruir `recommendation_daily_metrics` desde `recommendation_events`, para recuperarme de errores de cálculo.

**Why this priority**: El issue lo exige ("los cálculos pueden realizarse nuevamente a partir de los eventos almacenados"). Aquí significa una operación de reconstrucción **dentro del monolito y a escala actual**; el reprocesamiento masivo es HU-25.

**Acceptance Scenarios**:
1. **Given** métricas diarias alteradas o borradas, **When** se ejecuta la reconstrucción, **Then** se obtienen los mismos valores que el cálculo incremental sobre los mismos eventos.

---

### Edge Cases

- Una reserva atribuida a una recomendación de otra oferta distinta: se rechaza la atribución (el `offeringId` debe coincidir) y la reserva queda no atribuida.
- Un `recommendationId` inexistente o ajeno al cliente: se ignora para la atribución; la reserva NO falla.
- Evento duplicado de Kafka: se mantiene la idempotencia por `eventId`.
- División por cero con 0 recomendaciones: conversión 0.

---

## Requirements *(mandatory)*

- **FR-001**: `BookingCreated` y `BookingCancelled` DEBEN poder transportar un `recommendationId` opcional (cambio **aditivo** y versionado, `version = 2`; los consumidores existentes lo ignoran). Contrato: `docs/contracts/analytics-events.md`.
- **FR-002**: La API de creación de reservas DEBE aceptar un `recommendationId` opcional y validar que corresponda a una recomendación **del mismo cliente y de la misma oferta**.
- **FR-003**: El sistema DEBE calcular y exponer por separado: conversión **atribuida** (reservas efectivas con recomendación / recomendaciones) y reservas **totales** de la oferta. La primera es la conversión oficial.
- **FR-004**: Las métricas anteriores a este cambio NO se reescriben; se rotulan como no atribuidas.
- **FR-005**: DEBE existir una operación de reconstrucción de `recommendation_daily_metrics` desde `recommendation_events`, idempotente y restringida a `ROLE_ADMIN`.
- **FR-006**: DEBEN existir pruebas automatizadas del controlador de tendencias (401, contrato, ordenación), de la atribución y del componente Angular.
- **FR-007**: NO se introduce Spark, ni microservicio de ML, ni lectura desde otros servicios.
- **FR-008 (contrato del evento de recomendación)**: `RecommendationGenerated` DEBE publicarse con un payload tipado que incluya `recommendationId`, `userId`, `inputType` y la lista de ofertas recomendadas (`offeringId`, `score`). Cambio **aditivo** y versionado (`version = 2`); el contrato se documenta en `docs/contracts/` para que lo compartan HU-22 y HU-25.
- **FR-009 (acumulación por oferta)**: el consumidor DEBE acumular recomendaciones y usuarios únicos **por oferta** (una fila por oferta recomendada y día), nunca por usuario. Los usuarios únicos se cuentan con unicidad real por (oferta, usuario, día).
- **FR-010 (prueba que detecte esto)**: DEBE existir una prueba de integración con PostgreSQL real (Testcontainers) que publique un evento y compruebe las filas de `recommendation_daily_metrics`; una prueba con repositorio simulado no basta.

## Decisiones & Aclaraciones

1. **Atribución de la reserva — DECIDIDO 2026-11-09: opción A.** El frontend envía `recommendationId` al reservar desde una recomendación (el botón `goToBooking` de `ai.component.ts` ya navega a `/book?offeringId=…`; solo se añade el parámetro). Se persiste en la reserva (columna opcional, **nueva migración Flyway**, nunca editar las existentes) y viaja en `BookingCreated` y `BookingCancelled`. Se descartó la heurística por usuario y oferta en N días (B): impreciso, exige fijar un N arbitrario, y su ventaja —atribuir el histórico— no aplica porque no hay histórico real. Detalle en `docs/contracts/analytics-events.md` §2.4: un `recommendationId` inválido **no falla la reserva**, simplemente no se atribuye.
2. **Fórmula de la conversión — DECIDIDO:** se mantiene la fórmula actual cambiando solo el numerador a reservas atribuidas efectivas. Definiciones completas en `docs/contracts/analytics-events.md` §3.

## Dependencies & Out of Scope

- **Dependencia**: HU-25 usa la definición de conversión que se fije aquí.
- **Fuera de alcance**: procesamiento batch (HU-25), modelos de ML, cambios en el servicio de mentorías, histórico retroactivo.

## Clasificación SDD

**Grande / alto riesgo**: cambia un evento de negocio (Kafka), una API pública y requiere migración. Exige `plan.md`, `tasks.md` y `analyze` antes de implementar (`docs/agents/SDD-HYBRID-GUIDE.md` §1).
