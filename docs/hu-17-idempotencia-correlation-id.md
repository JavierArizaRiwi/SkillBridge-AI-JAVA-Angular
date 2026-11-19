# HU-17: Idempotencia y Correlation ID

## Alcance backend

`POST /api/bookings` exige `Idempotency-Key`. La clave se asocia al usuario autenticado y al hash de `offeringId` y `scheduledAt`.

| Caso | Resultado |
|---|---|
| Primera petición | Crea una reserva, una notificación y un evento. |
| Misma clave y mismo cuerpo | Retorna la reserva original sin efectos nuevos. |
| Misma clave y cuerpo distinto | `409 Conflict`. |
| Clave ausente o vacía | `400 Bad Request`. |

La migración `V5__booking_idempotency.sql` añade la clave, el hash y un índice único parcial por usuario y clave. Los errores de negocio no persisten una reserva, por lo que no consumen una clave exitosa.

## Correlation ID

`CorrelationIdFilter` acepta o genera `X-Correlation-Id`, lo devuelve en la respuesta, lo coloca en MDC y en `CorrelationIdHolder`. El patrón de consola incorpora el valor de MDC. `BusinessEvent` conserva el valor en su envelope Kafka y `KafkaAuditConsumer` lo vuelve a incorporar al contexto mientras procesa el evento.

## Evidencia

- `BookingServiceTest`: repetición no persiste, notifica ni publica de nuevo.
- `BookingControllerSecurityTest`: clave ausente retorna `400`.
- `BookingPersistenceAdapterTest`: Flyway aplica V5 contra PostgreSQL real.
- `CorrelationIdFilterTest`: propaga y genera el identificador HTTP, y limpia el contexto al terminar.
