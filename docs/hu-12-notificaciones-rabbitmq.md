# HU-12 — Notificaciones asíncronas con RabbitMQ

## 1. Objetivo

Desacoplar la creación de una reserva del procesamiento de sus notificaciones. La reserva se guarda y responde al usuario; la notificación se procesa después, en otro hilo, a través de RabbitMQ.

## 2. Decisión arquitectónica

```text
BookingService (application)
        ↓
NotificationPublisherPort        ← puerto de salida
        ↓
RabbitNotificationPublisher      ← adapter (infrastructure)
        ↓
notification.exchange (topic)
        ↓  routing key: notification.booking.created
notification.queue
        ↓
NotificationConsumer             ← adapter de entrada (infrastructure)
        ↓
ProcessNotificationUseCase → NotificationService
        ↓
NotificationSenderPort           ← puerto de salida
        ↓
SimulatedNotificationSender      ← adapter (notificación simulada)
```

- **RabbitMQ** se usa para procesar notificaciones de forma asíncrona.
- **Kafka no reemplaza esta función.** Kafka queda reservado para eventos de negocio durables y consumidores futuros.
- **Regla hexagonal:** `domain` y `application` no importan nada de `org.springframework.amqp` ni de `infrastructure`. Se verifica con:

```bash
grep -rn "org.springframework.amqp" src/main/java/com/riwi/skillbridge/application src/main/java/com/riwi/skillbridge/domain
grep -rn "infrastructure" src/main/java/com/riwi/skillbridge/application src/main/java/com/riwi/skillbridge/domain
```

Ambos comandos deben devolver vacío.

## 3. Contrato del mensaje

`NotificationMessage` (record en `application/port/out`, Java puro):

| Campo | Tipo | Descripción |
|---|---|---|
| `eventId` | `UUID` | Identificador único del evento (se genera al crear el mensaje) |
| `bookingId` | `UUID` | Reserva asociada |
| `userId` | `UUID` | Usuario a notificar (`customerId` de la reserva) |
| `notificationType` | `NotificationType` | Hoy solo `BOOKING_CREATED` |
| `occurredAt` | `Instant` | Momento en que ocurrió el evento |

Todos los campos son obligatorios: el constructor compacto valida que no sean nulos. Un mensaje incompleto no se puede deserializar y se rechaza (ver sección 5).

## 4. Topología de RabbitMQ

| Elemento | Nombre | Notas |
|---|---|---|
| Exchange | `notification.exchange` | `topic`, durable |
| Routing key | `notification.booking.created` | |
| Queue | `notification.queue` | durable, con DLX configurado |
| Dead letter exchange | `notification.dlx` | `direct`, durable |
| Dead letter routing key | `notification.failed` | |
| Dead letter queue | `notification.dlq` | durable |

`RabbitTemplate` se personaliza con `RabbitTemplateCustomizer`: `mandatory=true`, confirm callback y returns callback. Se activan con `publisher-confirm-type: correlated` y `publisher-returns: true`. El mensaje se envía en JSON con el `ObjectMapper` de Spring.

## 5. Qué ocurre cuando algo falla

Configuración relevante (`application.yml`, `spring.rabbitmq`):

```yaml
connection-timeout: 3s
publisher-confirm-type: correlated
publisher-returns: true
listener:
  simple:
    acknowledge-mode: auto
    default-requeue-rejected: false
    prefetch: 10
    retry:
      enabled: true
      initial-interval: 2s
      multiplier: 2.0
      max-interval: 10s
      max-attempts: 3
```

| Situación | Comportamiento |
|---|---|
| El consumer procesa bien | ACK automático; el mensaje sale de la queue |
| El consumer lanza una excepción | Retry local (Spring Retry): 3 intentos, esperas de ~2 s y ~4 s |
| Se agotan los reintentos | Se rechaza sin requeue (`RejectAndDontRequeueRecoverer`) y RabbitMQ lo envía por el DLX a `notification.dlq` |
| Mensaje inválido o no deserializable | Se rechaza y termina en `notification.dlq`; el caso de uso nunca se invoca |
| Consumer caído | El mensaje espera en la queue durable; la reserva ya fue creada y no se ve afectada |
| Broker caído al publicar | `RabbitNotificationPublisher` captura `AmqpException` y la registra; la reserva se mantiene |

**Acknowledgment:** modo `auto`. El ACK ocurre al terminar el método del listener sin excepción; si lanza, hay retry y luego rechazo.

**Independencia de la reserva:** `BookingService` solo llama a `publish` y continúa. No espera al consumer, que corre en otro hilo (`ntContainer#0-1` en los logs, frente a `nio-8080-exec-N` de la petición).

## 6. Evidencia

### Flujo exitoso (logs del backend)

Mismo `eventId` en las tres líneas, y el consumer en un hilo distinto al de la petición:

```text
Published eventId=3bfa880a-... exchange=notification.exchange routingKey=notification.booking.created
Consumed eventId=3bfa880a-... bookingId=75529847-...
[SIMULATED NOTIFICATION] type=BOOKING_CREATED userId=... bookingId=... eventId=3bfa880a-...
```

### Fallo, retry y DLQ (logs con una excepción temporal en el sender)

```text
00:09:59 Published eventId=6de29108-...
00:09:59 Consumed eventId=6de29108-...   (intento 1)
00:10:01 Consumed eventId=6de29108-...   (intento 2, +2 s)
00:10:05 Consumed eventId=6de29108-...   (intento 3, +4 s)
00:10:05 Retries exhausted ... Caused by: IllegalStateException: fallo simulado
```

En el panel de management, `notification.dlq` quedó con Ready = 1. El mensaje tenía `x-death` con `queue: notification.queue`, `exchange: notification.exchange`, `reason: rejected`, y el payload con los cinco campos del contrato. La reserva se creó correctamente desde Angular mientras el consumer fallaba.

### Capturas del panel (`http://localhost:15672`)

- `notification.exchange`: tipo `topic`, durable, binding hacia `notification.queue` con `notification.booking.created`.
- `notification.queue`: argumentos `x-dead-letter-exchange: notification.dlx` y `x-dead-letter-routing-key: notification.failed`, 1 consumer, Ready 0 y Unacked 0.
- `notification.dlq`: durable, binding `notification.failed` desde `notification.dlx`; Ready = 1 tras la prueba de fallo.
- Mensaje en la DLQ con headers `x-death`.

## 7. Tests

| Test | Tipo | Casos |
|---|---|---|
| `NotificationMessageTest` | Unitario | Mensaje válido, `eventId` distinto por mensaje, campos nulos rechazados (3) |
| `NotificationServiceTest` | Unitario | Delega en `NotificationSenderPort`; propaga el fallo del sender (2) |
| `NotificationConsumerTest` | Unitario | Delega en el caso de uso; propaga el fallo para que actúe el retry (2) |
| `RabbitNotificationPublisherTest` | Unitario | Exchange, routing key y correlation id correctos; un fallo del broker no se propaga (2) |
| `BookingServiceTest` | Unitario | Publica el mensaje al crear la reserva; no publica si la fecha es pasada, el servicio está inactivo o el usuario no existe (4) |
| `NotificationFlowIT` | Integración (Testcontainers, `rabbitmq:4-management-alpine`) | Publisher → exchange → queue → consumer; retry hasta éxito sin DLQ; DLQ al agotar reintentos; mensaje inválido a la DLQ (4) |

Cobertura frente a la HU: publisher, consumer, mensaje válido, fallo del consumer, retry y mensaje rechazado.

### Cómo ejecutarlos

```bash
mvn test                              # unitarios
mvn verify -Dit.test=NotificationFlowIT   # integración (requiere Docker)
mvn verify                            # todo
```

Los `*IT` se ejecutan con Failsafe (en `verify`), así `mvn test` sigue rápido y sin Docker. El IT carga el `application.yml` real, por lo que también valida la configuración de ack, retry y DLX; solo acorta el intervalo de retry a 100 ms.

**Nota sobre Docker:** con Docker Engine 29 y Testcontainers 1.21.3 puede aparecer `client version 1.32 is too old. Minimum supported API version is 1.40`. En ese caso se fijó la versión de API del cliente en `~/.docker-java.properties` (`api.version=1.44`). Es una configuración de la máquina, no del proyecto. `JpaOfferingRepositoryTest` también usa Testcontainers y necesita acceso a Docker (usuario en el grupo `docker`).

## 8. Limitaciones y mejoras futuras

- **Notificación perdida si el broker está caído al publicar.** Se registra el error pero no se reintenta. Solución robusta: patrón outbox.
- **Reproceso de la DLQ.** Hoy los mensajes quedan en `notification.dlq` y se revisan manualmente. Falta un mecanismo de reproceso o alerta.
- **`__TypeId__` ligado al paquete del record.** El header guarda el nombre completo de `NotificationMessage`; si se mueve o renombra, los mensajes ya encolados o en la DLQ no se podrán deserializar.
- **El usuario no se entera de una notificación fallida.** Informarlo requeriría guardar el estado de la notificación y exponerlo (endpoint, SSE o WebSocket).
- **`BookingService` no es `@Transactional`**, por lo que se publica después del commit del `save`. Si se vuelve transaccional, hay que publicar tras el commit para no notificar reservas con rollback.
- **Eventos de negocio.** Cuando existan consumidores de negocio, irán por Kafka (por ejemplo `domain/event/BookingCreated`), no por esta cola.
