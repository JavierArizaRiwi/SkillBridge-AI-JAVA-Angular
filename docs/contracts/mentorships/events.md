# Mentorías — contrato de eventos

El contrato vinculante es el **JSON**; las clases Java/TypeScript se derivan de él.

## 1. Envelope `BusinessEvent` (v1)

Topic Kafka `booking-events` (ADR-0003). Campos de primer nivel:

| Campo | Tipo | Oblig. | Notas |
|---|---|---|---|
| `eventId` | UUID | Sí | Idempotencia del consumidor |
| `eventType` | string | Sí | Ver catálogo |
| `aggregateId` | string | Sí | Para mentorías: `assignmentId` |
| `aggregateType` | string | Sí | `Mentorship` |
| `occurredAt` | ISO-8601 UTC | Sí | |
| `correlationId` | string | Recomendado | Propaga `X-Correlation-Id` |
| `version` | int | Sí | Versión del esquema del payload |
| `payload` | objeto | Sí | Específico del evento |
| `actorUserId` · `actorUsername` · `actorRole` | string | No | Quién causó el hecho; `SYSTEM` si es automático |
| `action` · `resource` · `resourceId` | string | No | Usados por `audit-service` |

**Particularidad verificada en el monolito:** al serializar, `payload` incluye además copias de
`actorUserId`, `actorUsername`, `actorRole`, `action`, `resource`, `resourceId` (método
`getPayloadForAudit`), y esos mismos campos también aparecen en el primer nivel. Los consumidores
**deben tolerar la duplicación** y leer el primer nivel como fuente; el servicio Nest **no** debe
duplicarlos.

Los consumidores **ignoran campos desconocidos** y **no descartan** `eventType` desconocidos.

## 2. `PaymentApproved` v2 (RabbitMQ) — monolito → mentorías

- Exchange `payment.events` (topic), routing key `payment.approved`.
- Cola del servicio: `mentorship.payment.approved.queue` (enlazada a la misma clave; la cola actual
  del monolito `booking.payment.approved.queue` no se altera).
- DLQ: `mentorship.payment.dlq` tras 3 reintentos exponenciales.
- Antes de este cambio el monolito publicaba solo `{ "bookingId": "..." }` (v1). **Ya publica v2**; la forma
  mínima v1 solo se emite como degradación si falta la reserva, la oferta o el cliente (ver `gap-matrix.md`).
- `paymentId` es **opcional** (`null`) cuando la aprobación llega por el webhook de Stripe.
- `mode` y `durationHours` son obligatorios en v2; el JSON no contiene ningún otro campo (fijado por `PaymentApprovedJsonContractTest`).

```json
{
  "version": 2,
  "eventId": "uuid",
  "occurredAt": "2026-11-05T14:00:00Z",
  "correlationId": "string",
  "bookingId": "uuid",
  "paymentId": "uuid",
  "customerId": "uuid",
  "customerEmail": "string",
  "offeringId": "uuid",
  "offeringTitle": "string",
  "providerId": "uuid",
  "scheduledAt": "2026-11-05T15:00:00Z",
  "durationHours": 2,
  "mode": "ASYNCHRONOUS | LIVE"
}
```

Reglas de compatibilidad:
- Aditivo sobre v1: `bookingId` conserva nombre y tipo, así el consumidor actual del monolito no se rompe.
- Ventana de acceso = `[scheduledAt, scheduledAt + durationHours)`.
- `mode` y `durationHours` salen de `Offering` (campos nuevos, migración V13).
- Cero HTTP síncrono durante el aprovisionamiento. Mensaje estructuralmente inválido → DLQ sin reintentar.
- Idempotencia: `booking_id` único; duplicado = ACK sin crear nada.

## 3. Eventos de mentoría (Kafka `booking-events`) — mentorías → auditoría

Publicados con Transactional Outbox (`outbox_events`) en la misma transacción que el cambio de estado
(HU-MNT-08). `aggregateType = "Mentorship"`.

| `eventType` | Cuándo | Campos de `payload` |
|---|---|---|
| `MentorshipProvisioned` | `PENDING_PAYMENT`/nueva → `SCHEDULED` | `assignmentId, bookingId, customerId, offeringId, providerId, mode, accessStartsAt, accessEndsAt` |
| `MentorshipStarted` | → `IN_PROGRESS` | `assignmentId, startedAt` |
| `MentorshipProgressUpdated` | Cambio de progreso | `assignmentId, previousPercent, progressPercent` |
| `MentorshipCompleted` | → `COMPLETED` | `assignmentId, completedAt` |
| `MentorshipCancelled` | → `CANCELLED` | `assignmentId, reason` |
| `MentorshipExpired` | → `EXPIRED` | `assignmentId` |
| `MentorshipNoShow` | → `NO_SHOW` | `assignmentId` |
| `MentorshipRescheduled` | Nueva ventana | `assignmentId, previousStartsAt, previousEndsAt, accessStartsAt, accessEndsAt, reason` |
| `MentorshipLiveRoomCreated` | Sala creada | `assignmentId, provider, fallbackUsed` (sin passcode ni URL secreta) |

Prohibido en cualquier payload: contraseñas, `zoomPasscode`, firmas SDK, tokens, datos de tarjeta.

`audit-service` debe persistir estos eventos y **toleran tipos desconocidos** (ADR-0006 §6).
