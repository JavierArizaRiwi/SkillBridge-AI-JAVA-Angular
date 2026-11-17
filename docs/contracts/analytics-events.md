# Contrato de eventos de analítica (HU-22 y HU-25)

Define los eventos, las definiciones de métricas y el formato de exportación que comparten el cálculo en línea del monolito (HU-22) y el job batch de Spark (HU-25). **Estado: propuesto** (decisiones del 2026-11-09: atribución por `recommendationId` y entrada por archivos JSONL).

Marcas: `[VERIFIED]` comprobado en el código · `[PROPOSED]` cambio que este contrato exige y aún no está implementado.

## 1. Envelope

Todos los eventos usan el envelope `BusinessEvent` descrito en [`mentorships/events.md` §1](mentorships/events.md). Reglas para los consumidores: ignorar campos desconocidos, no descartar `eventType` desconocidos, deduplicar por `eventId`.

## 2. Eventos

### 2.1 `RecommendationGenerated` — v2 `[PROPOSED]`

- Topic `recommendation-events` `[VERIFIED]` (`KafkaRecommendationEventPublisher`). `aggregateType = "Recommendation"`, `aggregateId = recommendationId`.
- **Defecto actual `[VERIFIED]`:** el monolito publica `payload = request.inputType()` (solo el enum), sin ofertas. Ver `specs/022-demand-analytics/spec.md`, «Defecto previo verificado». v2 lo sustituye por un payload tipado.

```json
{
  "recommendationId": "uuid",
  "userId": "uuid",
  "inputType": "TEXT | VOICE | IMAGE",
  "offerings": [ { "offeringId": "uuid", "score": 0.0 } ],
  "provider": "string | null",
  "model": "string | null",
  "latencyMs": 0
}
```

- `offerings` **obligatorio**; puede estar vacío si el modelo no recomendó ninguna oferta válida. Cada `offeringId` aparece una sola vez por evento.
- `provider` y `model` son opcionales y los rellena la cadena de fallback (ADR-0007). El payload v1 los llamaba `modelUsed`.
- El payload se pasa como **record tipado**, no como enum: el envelope serializa el payload con `convertValue(payload, Map)` y un enum no es convertible a `Map` (el método `BusinessEvent.getPayloadForAudit` existe `[VERIFIED]`; que `convertValue` falle con un enum es deducción de Jackson `[INFERRED]`, no ejecutada).

### 2.2 `BookingCreated` — v2 `[PROPOSED]`

Topic `booking-events`. Payload actual `[VERIFIED]`: `bookingId, offeringId, customerId, scheduledAt, status`. v2 añade, de forma aditiva:

```json
{ "recommendationId": "uuid | null" }
```

Es la recomendación de origen, o `null` si la reserva no vino de una. Los eventos v1 (sin el campo) se interpretan como **no atribuidos**.

### 2.3 `BookingCancelled` — v2 `[PROPOSED]`

Payload actual `[VERIFIED]`: `bookingId, offeringId, customerId, status`. v2 añade `recommendationId` (copiado de la reserva, `null` si no tenía). Así el evento se basta a sí mismo: ningún consumidor necesita consultar la reserva para saber si estaba atribuida.

### 2.4 Atribución en la API de reservas `[PROPOSED]`

`POST /api/bookings` acepta `recommendationId` **opcional**. El backend lo valida y, si no es válido, **lo ignora y crea la reserva sin atribución** (nunca falla la reserva por esto):

- la recomendación existe;
- pertenece al **mismo usuario** que reserva;
- el `offeringId` de la reserva figura en las `offerings` de esa recomendación.

Se persiste en la reserva (columna opcional, **migración nueva**; nunca editar las existentes).

## 3. Definiciones de métricas

Granularidad: `(offeringId, date)`. `date` = fecha **UTC** de `occurredAt`.

| Métrica | Definición |
|---|---|
| `recommendations` | Nº de eventos `RecommendationGenerated` (deduplicados por `eventId`) cuyo `offerings` contiene la oferta |
| `unique_users` | Nº de `userId` distintos entre esos eventos (exacto, no aproximado) |
| `bookings_total` | Nº de `BookingCreated` de la oferta |
| `cancellations_total` | Nº de `BookingCancelled` de la oferta |
| `bookings_attributed` | `BookingCreated` con `recommendationId != null` |
| `cancellations_attributed` | `BookingCancelled` con `recommendationId != null` |
| `effective_attributed` | `max(0, bookings_attributed - cancellations_attributed)` |
| `conversion_rate` (**oficial**) | `effective_attributed / recommendations` si `recommendations > 0`, si no `0.0` |
| `total_booking_rate` (informativa) | `max(0, bookings_total - cancellations_total) / recommendations`, con la misma regla de cero |

Notas:
- `conversion_rate` puede superar 1,0 en un día concreto (una recomendación de un día se reserva otro día). Es una propiedad de medir por fecha de evento; no se trunca.
- Una cancelación se cuenta el día en que ocurre, no el de la reserva. Es lo que ya hace `DemandAnalyticsService` y se mantiene por paridad.
- Los eventos v1 pasan a `*_total` y **no** a `*_attributed`. El histórico no se reescribe.

## 4. Exportación para el job batch (JSONL) `[PROPOSED]`

El job de Spark **solo lee archivos**; no se conecta a Kafka ni a ninguna base de datos (ADR-0006, regla 1).

```text
<dataset>/
  manifest.json
  events-0001.jsonl
  events-0002.jsonl
```

- **`events-*.jsonl`**: un `BusinessEvent` por línea, UTF-8, sin saltos de línea dentro del JSON. Pueden mezclar `recommendation-events` y `booking-events`.
- **`manifest.json`** (obligatorio):

```json
{
  "origin": "production | synthetic",
  "exportedAt": "2026-11-09T12:00:00Z",
  "topics": ["booking-events", "recommendation-events"],
  "eventCount": 0,
  "generator": { "name": "string", "seed": 0 }
}
```

`origin` es obligatorio y **`synthetic` nunca se mezcla con `production`**. `generator` solo existe en datasets sintéticos.

- **Cómo se obtiene en producción:** volcado de ambos topics desde el principio (por ejemplo con `kafka-console-consumer --from-beginning`), documentado en el README del módulo. La profundidad del historial la limita la retención de Kafka, que en `docker-compose.yml` no está configurada `[VERIFIED]` (rige el valor por defecto del broker).
- **Cómo se obtiene para pruebas y demo:** un generador **determinista** (semilla fija) que produce datasets `synthetic`, incluidos duplicados, eventos en bordes de medianoche UTC y eventos futuros.

## 5. Reglas de procesamiento (idénticas en HU-22, la referencia y Spark)

1. **Deduplicación por `eventId`.** Si dos eventos comparten `eventId` pero difieren, se conserva el de menor `occurredAt`; si empatan, el de menor JSON canónico (claves ordenadas, sin espacios). Regla total y determinista; no depende del orden de lectura.
2. **Eventos sin `offeringId`** (reserva con campo nulo) o recomendaciones con `offerings` vacío: no generan filas por oferta.
3. **Eventos con `occurredAt` posterior al instante de corte del lote**: se apartan a cuarentena (`quarantine/`) y **no** abortan el proceso; se informa su número.
4. **Lote vacío**: éxito con 0 filas de salida.
5. **Salida idempotente**: reejecutar el mismo lote sobre la misma fecha sobrescribe esa partición; nunca duplica.

## 6. Quién implementa qué

| Pieza | Responsable | Historia |
|---|---|---|
| Publicar `RecommendationGenerated` v2 y acumular por oferta | Monolito | HU-22 |
| `recommendationId` en reserva, `BookingCreated`/`BookingCancelled` v2, migración | Monolito + frontend | HU-22 |
| Generador sintético, exportación documentada, job PySpark y motor de referencia | `analytics/spark/` (Python) | HU-25 |

HU-25 puede empezar contra este contrato con datos sintéticos **sin esperar** a HU-22; solo necesita HU-22 para trabajar con datos reales.
