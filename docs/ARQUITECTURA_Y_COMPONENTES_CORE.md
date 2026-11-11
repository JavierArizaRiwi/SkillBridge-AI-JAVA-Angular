# Arquitectura del Sistema, Auditoría, Mensajería, IA y Tendencias — Mapeo Completo al Código

Este documento detalla exhaustivamente el funcionamiento técnico y la arquitectura de **SkillBridge AI**, integrando la **ubicación exacta en el código (rutas de archivo, clases, métodos y números de línea verificados)** para cada componente:
1. **Publicación y Arquitectura de Mensajería (Kafka y RabbitMQ)**
2. **Audit Service de Extremo a Extremo** (Consumo de Kafka, Transformación, Persistencia en MongoDB, API REST y Polling Reactivo de 10s en Angular)
3. **Motor de Inteligencia Artificial Multimodal** (Hexagonal, Gemini/OpenAI, STT, Vision, TTS y Guardarraíles)
4. **Sistema de Analítica Predictiva y Tendencias** (Ingesta asíncrona, Métricas diarias, Fórmulas de TrendScore y Forecast)

---

## 1. Publicación de Mensajería: Kafka vs. RabbitMQ

El núcleo de la aplicación implementa una arquitectura híbrida donde cada broker cumple un propósito especializado y bien delimitado:

```
+-----------------------------------------------------------------------------------+
|                           Core Backend (Spring Boot 3.5.6)                        |
|                                                                                   |
|  [Caso de Uso: Reserva / Auditoría]        [Caso de Uso: Notificaciones / Pagos]  |
|                 |                                             |                   |
|                 v                                             v                   |
|      BookingEventPublisherPort                     NotificationPublisherPort      |
|      AuditEventPublisherPort                       PaymentEventPublisherPort      |
|                 |                                             |                   |
|                 v                                             v                   |
|   +----------------------------+               +----------------------------+     |
|   | Kafka Event Publishers     |               | Rabbit Event Publishers    |     |
|   | (KafkaTemplate)            |               | (RabbitTemplate)           |     |
|   +----------------------------+               +----------------------------+     |
+-----------------|---------------------------------------------|-------------------+
                  |                                             |
                  v                                             v
        +-------------------+                         +-------------------+
        |   Apache Kafka    |                         |     RabbitMQ      |
        |   (Log Inmutable) |                         | (Colas de Trabajo)|
        +-------------------+                         +-------------------+
          /               \                                     |
         /                 \                                    v
        v                   v                           [Consumidor Local]
 [Audit Service]    [Analytics Consumer]               NotificationConsumer
  (MongoDB)          (Métricas Postgres)               (Envío de Emails)
```

### 1.1. Publicación en Apache Kafka

Kafka se utiliza como un **registro de eventos inmutable (Event Stream)** para trazabilidad, auditoría forense y analítica desacoplada.

#### Envoltorio Canónico (`BusinessEvent<T>`)
Todos los eventos en Kafka se transmiten envueltos en la estructura tipada [`BusinessEvent<T>`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/application/port/out/event/BusinessEvent.java#L12-L28):
```java
public record BusinessEvent<T>(
    UUID eventId,
    String eventType,
    String aggregateId,
    String aggregateType,
    Instant occurredAt,
    String correlationId,
    int version,
    T payload,
    String actorUserId,
    String actorUsername,
    String actorRole,
    String action,
    String resource,
    String resourceId
) {}
```

#### 📌 Dónde se ve exactamente en el código:

| Componente | Archivo y Ruta | Líneas | Qué hace en el código |
|---|---|---|---|
| **Contrato del Envoltorio** | [`BusinessEvent.java`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/application/port/out/event/BusinessEvent.java) | `L12-L28` | Record inmutable con envelope estándar y metadata de trazabilidad. |
| **Captura de Correlation ID** | [`CorrelationIdFilter.java`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/filter/CorrelationIdFilter.java) | `L22-L40` | Extrae encabezado HTTP `X-Correlation-Id` (o genera UUID) y lo vincula a SLF4J MDC. |
| **Contexto ThreadLocal** | [`CorrelationIdHolder.java`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/application/common/CorrelationIdHolder.java) | `L7-L19` | Almacena el `correlationId` para inyectarlo en los eventos de dominio sin pasarlo por parámetros. |
| **Configuración de Topics** | [`application.yml`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/backend/src/main/resources/application.yml) | `L65-L75` | Configura `app.kafka.topic.booking-events: booking-events`, `audit-events: booking-events`, `recommendation-events: recommendation-events`. |
| **Publicador de Reservas** | [`KafkaBookingEventPublisher.java`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/KafkaBookingEventPublisher.java) | `L23-L40` (`publish`) | `CompletableFuture.runAsync()` invocando `kafkaTemplate.send(topic, event.aggregateId(), event)` con `aggregateId` como partition key. |
| **Publicador de Auditoría** | [`KafkaAuditEventPublisher.java`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/KafkaAuditEventPublisher.java) | `L23-L40` (`publish`) | Publica eventos de seguridad (`UserLoggedIn`, `UserRegistered`, `OfferingCreated`) en el topic unificado. |
| **Publicador de Recomendaciones** | [`KafkaRecommendationEventPublisher.java`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/KafkaRecommendationEventPublisher.java) | `L27-L41` (`publish`) | Publica eventos tipo `RecommendationGenerated` al topic `recommendation-events`. |
| **Disparo en Creación de Reserva** | [`BookingService.java`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/application/service/BookingService.java) | `L88-L92` (`createInternal`) | Construye `BusinessEvent<BookingCreatedPayload>` y llama a `eventPublisher.publish(event)`. |
| **Disparo en Cancelación de Reserva** | [`BookingService.java`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/application/service/BookingService.java) | `L148-L154` (`cancel`) | Construye `BusinessEvent<BookingCancelledPayload>` y llama a `eventPublisher.publish(event)`. |
| **Disparo en Auth (Registro)** | [`AuthService.java`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/application/service/AuthService.java) | `L44-L49` (`register`) | Construye `BusinessEvent<String>` con `eventType="UserRegistered"` y llama a `auditPublisher.publish(event)`. |
| **Disparo en Auth (Login)** | [`AuthService.java`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/application/service/AuthService.java) | `L67-L72` (`login`) | Construye `BusinessEvent<String>` con `eventType="UserLoggedIn"` y llama a `auditPublisher.publish(event)`. |
| **Disparo en Ofertas** | [`OfferingCommandService.java`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/application/service/OfferingCommandService.java) | `L49`, `L100-L108` (`publishAudit`) | Emite `OfferingCreated`, `OfferingUpdated`, `OfferingDeactivated`. |

---

### 1.2. Publicación en RabbitMQ

RabbitMQ se utiliza para **procesamiento de tareas y mensajería transaccional orientada a colas de trabajo (Workers)**: notificaciones inmediatas por email y eventos de pago.

#### 📌 Dónde se ve exactamente en el código:

| Componente | Archivo y Ruta | Líneas | Qué hace en el código |
|---|---|---|---|
| **Topología y Exchanges** | [`RabbitConfiguration.java`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/config/RabbitConfiguration.java) | `L30-L36` | Define beans: `notificationExchange()` (`notification.exchange`), `bookingExchange()` (`booking.events`), `paymentExchange()` (`payment.events`) y `deadLetterExchange()` (`dlx.exchange`). |
| **Cola y Dead Letter Queue (DLQ)** | [`RabbitConfiguration.java`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/config/RabbitConfiguration.java) | `L43-L53` | `notificationQueue()` con `.deadLetterExchange("notification.dlx")` y `.deadLetterRoutingKey("notification.failed")`. |
| **Bindings de Notificaciones** | [`RabbitConfiguration.java`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/config/RabbitConfiguration.java) | `L56-L73` | Enlaza `notification.queue` a `notification.booking.created` y `notification.booking.cancelled`. |
| **Confiabilidad y Confirm Callbacks** | [`RabbitConfiguration.java`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/config/RabbitConfiguration.java) | `L80-L95` | `notificationRabbitTemplateCustomizer()`: Activa `template.setMandatory(true)`, `ConfirmCallback` y `ReturnsCallback`. |
| **Publicador de Notificaciones** | [`RabbitNotificationPublisher.java`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/RabbitNotificationPublisher.java) | `L26-L50` (`publish`) | Llama a `rabbitTemplate.convertAndSend()` enviando `NotificationMessage` con `CorrelationData`. Captura `AmqpException` para no tumbar la reserva si el broker falla. |
| **Publicador de Pagos** | [`RabbitPaymentEventPublisher.java`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/RabbitPaymentEventPublisher.java) | `L22-L29` | Publica `PaymentApproved` (`payment.approved`) y `PaymentRejected` (`payment.rejected`) en `payment.events`. |
| **Disparo al Reservar** | [`BookingService.java`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/application/service/BookingService.java) | `L87` (`createInternal`) | Invoca `notificationPublisher.publish(NotificationMessage.bookingCreated(...))`. |
| **Disparo al Cancelar** | [`BookingService.java`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/application/service/BookingService.java) | `L147` (`cancel`) | Invoca `notificationPublisher.publish(NotificationMessage.bookingCancelled(...))`. |
| **Consumidor RabbitMQ** | [`NotificationConsumer.java`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/messaging/NotificationConsumer.java) | `L24-L35` (`onNotification`) | `@RabbitListener(queues = NOTIFICATION_QUEUE)`. En caso de `PermanentNotificationException`, lanza `AmqpRejectAndDontRequeueException` hacia la DLQ. |
| **Envío de Email** | [`EmailNotificationSender.java`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/notification/EmailNotificationSender.java) | `L35-L68` (`send`) | Construye `MimeMessage` con `JavaMailSender` y lo despacha al servidor SMTP (Mailpit en puerto 1025). |

---

## 2. El Microservicio de Auditoría (Audit Service) de Extremo a Extremo

El microservicio `audit-service/` es un proyecto Spring Boot y Angular totalmente desacoplado del monolito.

```
   Kafka Broker (Topic: booking-events)
                     |
                     v
   [KafkaAuditConsumer.java] (audit-service/backend)
                     |
                     v Deserialización y Folding
   [BusinessEvent.java] (Mapeo dinámico y plegado de actor)
                     |
                     v
   [AuditEventRepository.java] -> MongoDB 7 (Colección: audit_events)
                     |
                     v REST API (Puerto 8081)
   [AuditController.java] (/api/audit/events, /api/audit/stats)
                     ^
                     | HTTP GET (cada 10 segundos)
   [AuditService (audit.service.ts)]
                     ^
                     | interval(10000) + switchMap + combineLatest
   [DashboardComponent (dashboard.component.ts)] (Angular 20 Signals)
                     |
                     v Re-render reactivo
   [dashboard.component.html] (Filtros, KPIs, Modal con JSON completo)
```

#### 📌 Dónde se ve exactamente en el código:

| Componente | Archivo y Ruta | Líneas | Qué hace en el código |
|---|---|---|---|
| **Consumidor Kafka de Auditoría** | [`KafkaAuditConsumer.java`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/audit-service/backend/src/main/java/com/riwi/audit/consumer/KafkaAuditConsumer.java) | `L17-L23` (`consume`) | `@KafkaListener(topics = "${app.kafka.topic.booking-events}", groupId = "${spring.kafka.consumer.group-id}")`. Recibe `BusinessEvent` y ejecuta `auditEventRepository.save(event)`. |
| **Configuración de Consumidor y Deserializador** | [`audit-service/backend/application.yml`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/audit-service/backend/src/main/resources/application.yml) | `L10-L22` | Asigna `group-id: audit-service-group`, `ErrorHandlingDeserializer` y `JsonDeserializer` apuntando a `com.riwi.audit.model.BusinessEvent`. |
| **Modelo y Plegado Dinámico ("Folding")** | [`BusinessEvent.java`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/audit-service/backend/src/main/java/com/riwi/audit/model/BusinessEvent.java) | `L48-L78` (`fold`, `setPayload`) | Métodos Jackson (`setActorUserId`, etc.) que pliegan los campos de actor dentro del mapa dinámico `payload` sin sobrescribir valores existentes. Anotado con `@Document(collection = "audit_events")`. |
| **Inicialización de Índices en MongoDB** | [`AuditMongoInitializer.java`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/audit-service/backend/src/main/java/com/riwi/audit/config/AuditMongoInitializer.java) | `L23-L50` (`run`) | Implementa `ApplicationRunner`. Crea la colección `audit_events` e inicializa índices sobre `occurredAt` desc y `{ eventType: 1, occurredAt: -1 }`. |
| **Repositorio Mongo** | [`AuditEventRepository.java`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/audit-service/backend/src/main/java/com/riwi/audit/repository/AuditEventRepository.java) | `L11-L15` | `MongoRepository<BusinessEvent, UUID>`. Declara `findAllByOrderByOccurredAtDesc()`. |
| **Controlador REST de Auditoría** | [`AuditController.java`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/audit-service/backend/src/main/java/com/riwi/audit/controller/AuditController.java) | `L24-L51` | Expone `GET /api/audit/events`, `GET /api/audit/events/{eventId}` y `GET /api/audit/stats` calculando agregados en memoria (`totalEvents`, `bookingCreated`, `bookingCancelled`, `otherEvents`). |
| **Cliente Angular de Auditoría** | [`audit.service.ts`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/audit-service/frontend/src/app/audit.service.ts) | `L41-L51` | Métodos `getEvents()`, `getEvent(id)` y `getStats()` contra `environment.auditApiUrl`. |
| **Polling Reactivo de 10 Segundos** | [`dashboard.component.ts`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/audit-service/frontend/src/app/dashboard/dashboard.component.ts) | `L154-L177` (`startPolling`) | `interval(10000).pipe(startWith(0), takeUntil(this.destroy$), switchMap(() => combineLatest([getStats(), getEvents()])))`. Alimenta los Signals `this.stats.set()` y `this.events.set()`. |
| **Filtrado Reactivo con Signals** | [`dashboard.component.ts`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/audit-service/frontend/src/app/dashboard/dashboard.component.ts) | `L49-L89` (`filteredEvents`) | `computed(() => ...)`: Recalcula en tiempo real los eventos según tipo, acción, rol, recurso, texto libre y rango de fechas. |
| **Modal e Inspección de Payload JSON** | [`dashboard.component.ts`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/audit-service/frontend/src/app/dashboard/dashboard.component.ts) y [`dashboard.component.html`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/audit-service/frontend/src/app/dashboard/dashboard.component.html) | `L185-L191`, `L240` (`getFormattedPayload`) | Abre modal con `JSON.stringify(e.payload, null, 2)` para inspección de auditoría forense. |

---

## 3. Cómo Funciona la Inteligencia Artificial (IA)

Diseñada bajo arquitectura hexagonal estricta (ADR-001):

```
       [Cliente Web Angular]
                 |
                 | POST /api/ai/recommendations (Texto)
                 | POST /api/ai/recommendations/voice (Audio Multipart)
                 | POST /api/ai/recommendations/image (Imagen Multipart)
                 | POST /api/ai/tts (Texto a Voz)
                 v
         [AiController.java]
                 |
                 +---> [MediaFileValidator.java] (Valida extensiones, MIME y Magic Bytes)
                 +---> [GroqSpeechToTextAdapter] / [GeminiSpeechToTextAdapter] (Transcribe audio)
                 +---> [GeminiVisionAdapter] (Extrae descripción visual de imagen)
                 |
                 v
   [AiRecommendationService.java] (Orquestador del Caso de Uso)
                 |
                 +---> AuditEventPublisherPort ("RecommendationRequested" -> Kafka)
                 +---> OfferingRepositoryPort (Carga catálogo de servicios activos desde Postgres)
                 +---> AiRecommendationPort (Invoca al LLM con Prompt Engineering estructurado)
                 |          |
                 |          v
                 |     [GeminiAiAdapter.java] (Default: gemini-2.5-flash / Spring AI)
                 |     [OpenAiAiAdapter.java] (Fallback / Alternativo: gpt-4o-mini)
                 |          |
                 |          v
                 |     Valida JSON Schema estricto (Score 0-1, max 3 recomendaciones)
                 |     Filtra alucinaciones (comprueba IDs contra la DB)
                 |
                 +---> AuditEventPublisherPort ("RecommendationGenerated" -> Kafka)
                 |
                 v
       [RecommendationResult] -> Frontend
                 |
                 v (Opcional)
   [ElevenLabsTextToSpeechAdapter] -> Audio MP3 sintetizado
```

#### 📌 Dónde se ve exactamente en el código:

| Componente | Archivo y Ruta | Líneas | Qué hace en el código |
|---|---|---|---|
| **Controlador REST de IA** | [`AiController.java`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/AiController.java) | `L55-L85` | Endpoints: `POST /recommendations`, `POST /recommendations/voice`, `POST /recommendations/image`, `POST /tts`, `GET /trending`. |
| **Validación de Archivos Multimedia** | [`MediaFileValidator.java`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/validation/MediaFileValidator.java) | `L18-L45` | Inspecciona Magic Bytes y tipos MIME permitidos (`audio/mpeg`, `audio/wav`, `image/jpeg`, `image/png`) bloqueando subidas maliciosas. |
| **Transcripción de Voz (STT)** | [`GroqSpeechToTextAdapter.java`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/GroqSpeechToTextAdapter.java) y [`GeminiSpeechToTextAdapter.java`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/GeminiSpeechToTextAdapter.java) | `L25-L60` | Transcribe archivos de audio a texto usando Whisper-large-v3 en Groq o Gemini Audio API. |
| **Visión por Computadora (Vision)** | [`GeminiVisionAdapter.java`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/GeminiVisionAdapter.java) | `L20-L40` | Analiza imágenes subidas y devuelve un resumen textual del problema técnico o concepto educativo. |
| **Orquestación del Caso de Uso** | [`AiRecommendationService.java`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/application/service/AiRecommendationService.java) | `L34-L57` (`recommend`) | Emite evento `RecommendationRequested` a Kafka; carga catálogo activo de Postgres (`offerings.findAllActive()`); invoca `ai.recommend()`; filtra IDs inválidos; emite `RecommendationGenerated` a Kafka. |
| **Defensa contra Alucinaciones** | [`AiRecommendationService.java`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/application/service/AiRecommendationService.java) | `L44-L52` | Atrapa `IllegalArgumentException` al parsear `UUID.fromString(item.offeringId())` y descarta recomendaciones hallucinated. |
| **Adaptador LLM Principal (Gemini)** | [`GeminiAiAdapter.java`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/GeminiAiAdapter.java) | `L26-L28`, `L50-L68`, `L88-L99` | Define `RESPONSE_SCHEMA` JSON estricto; construye el prompt contextualizado con las ofertas; invoca `GoogleGenAiChatOptions.builder().responseSchema(...)` con Spring AI. |
| **Clasificación de Errores de IA** | [`GeminiAiAdapter.java`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/GeminiAiAdapter.java) | `L121-L133` (`classify`) | Mapea errores de API a `TIMEOUT`, `RATE_LIMITED` (429), `INVALID_INPUT` (401/403) o `UNAVAILABLE`. |
| **Adaptador LLM Alternativo (OpenAI)** | [`OpenAiAiAdapter.java`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/OpenAiAiAdapter.java) | `L20-L50` | Fallback opcional activado por `@ConditionalOnProperty(name="app.ai.provider", havingValue="openai")`. |
| **Síntesis de Voz (TTS)** | [`ElevenLabsTextToSpeechAdapter.java`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/ElevenLabsTextToSpeechAdapter.java) | `L35-L65` | Convierte texto en audio `audio/mpeg` mediante la API de ElevenLabs. |
| **Componente Angular de IA** | [`ai.component.ts`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/frontend/src/app/features/ai.component.ts) | `L1-L150` | Interfaz reactiva en `/ai` con soporte de grabación de micrófono, subida de capturas y visualización de ofertas recomendadas con botón de reproducción de audio. |

---

## 4. Cómo Funciona el Sistema de Tendencias (Demand Analytics)

El módulo de tendencias detecta qué habilidades y servicios tienen mayor tracción combinando recomendaciones con reservas reales:

```
   [Eventos Kafka]
   - RecommendationGenerated (topic: recommendation-events)
   - BookingCreated          (topic: booking-events)
   - BookingCancelled        (topic: booking-events)
              |
              v
   [KafkaAnalyticsConsumer.java] (Consumer Group: skillbridge-analytics-group)
              |
              v Idempotencia & Agregación
   [recommendation_daily_metrics] (Tabla PostgreSQL)
              |
              v Ventanas de 14 días (Reciente 7d vs Previo 7d)
   [DemandAnalyticsService.java]
              |
              +---> conversionRate = effectiveBookings / recommendations
              +---> growthRate = (recent - previous) / previous
              +---> trendScore = 50% conversión + 30% crecimiento + 20% volumen
              +---> forecastNext7Days = avgDaily * 7 * (1 + max(0, growthRate))
              |
              v GET /api/ai/trending
   [trending.component.ts] (Angular - /trending)
```

#### 📌 Dónde se ve exactamente en el código:

| Componente | Archivo y Ruta | Líneas | Qué hace en el código |
|---|---|---|---|
| **Consumidor Kafka de Analítica** | [`KafkaAnalyticsConsumer.java`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/messaging/KafkaAnalyticsConsumer.java) | `L50-L97` (`consumeRecommendationEvent`) | Escucha `recommendation-events` con grupo `skillbridge-analytics-group`. Comprueba idempotencia con `eventRepository.existsById(eventId)`. Llama a `incrementRecommendations()`. |
| **Consumidor de Reservas para Analítica** | [`KafkaAnalyticsConsumer.java`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/messaging/KafkaAnalyticsConsumer.java) | `L101-L144` (`consumeBookingEvent`) | Escucha `booking-events`. Al llegar `BookingCreated` ejecuta `incrementBookings()` y `registerUniqueUser()`. Al llegar `BookingCancelled` ejecuta `incrementCancellations()`. |
| **Entidad de Métricas Diarias** | [`RecommendationDailyMetricEntity.java`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/entity/RecommendationDailyMetricEntity.java) | `L15-L55` | Mapea la tabla `recommendation_daily_metrics` con contadores de recomendaciones, reservas y cancelaciones por servicio y fecha. |
| **Repositorio JPA de Métricas** | [`JpaRecommendationDailyMetricRepository.java`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/repository/JpaRecommendationDailyMetricRepository.java) | `L20-L45` | Define consultas nativas con `ON CONFLICT DO UPDATE` para incrementos concurrentes seguros. |
| **Servicio de Analítica de Demanda** | [`DemandAnalyticsService.java`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/application/service/DemandAnalyticsService.java) | `L66-L134` (`getTrending`) | Consulta métricas de los últimos 14 días (`today.minusDays(13)` a `today`). Agrupa por `offeringId`. Divide en ventana reciente (7d) y previa (7d). |
| **Fórmula de Conversión** | [`DemandAnalyticsService.java`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/application/service/DemandAnalyticsService.java) | `L142-L145` (`conversionRate`) | `effectiveBookings / recommendations`. Retorna `0.0` si `recommendations == 0`. Bounded a `[0.0, 1.0]`. |
| **Fórmula de Crecimiento** | [`DemandAnalyticsService.java`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/application/service/DemandAnalyticsService.java) | `L151-L154` (`growthRate`) | `(recent - previous) / previous`. Retorna `0.0` si `previous == 0`. |
| **Fórmula Compuesta de TrendScore** | [`DemandAnalyticsService.java`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/application/service/DemandAnalyticsService.java) | `L162-L179` (`trendScore`) | 50% Conversión (`conversionRate * 100 * 0.50`), 30% Crecimiento normalizado (`(clamp(growthRate, -1, 2) + 1) / 3 * 100 * 0.30`), 20% Volumen (`(recs / maxRecs) * 100 * 0.20`). |
| **Fórmula Predictiva de Forecast** | [`DemandAnalyticsService.java`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/application/service/DemandAnalyticsService.java) | `L185-L189` (`forecastNext7Days`) | `avgDailyBookings * 7 * (1 + Math.max(0.0, growthRate))`. |
| **DTO de Respuesta** | [`TrendingServiceResponse.java`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/dto/TrendingServiceResponse.java) | `L5-L15` | Record con `offeringId`, `name`, `trendScore`, `forecastNext7Days`, `recentRecommendations`, `recentBookings`, `conversionRate`, `growthRate`. |
| **Frontend de Tendencias** | [`trending.component.ts`](file:///C:/Users/santiago/Downloads/SkillBridge-AI-JAVA-Angular/frontend/src/app/features/trending.component.ts) | `L8-L130` | Renderiza la tabla ordenada por `trendScore` descendente con badges de crecimiento y estimaciones de demanda en `/trending`. |
