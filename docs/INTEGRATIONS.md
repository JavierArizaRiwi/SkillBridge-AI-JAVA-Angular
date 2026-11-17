# External & Infrastructure Integrations — SkillBridge AI

This document catalogs all verified infrastructure, broker, external API, and middleware integrations utilized across the SkillBridge AI platform.

---

## 1. Integrations Overview Matrix

| Integration | Technology / Provider | Role & Purpose | Port / Host Mapping | Config & Source Reference |
|---|---|---|---|---|
| **Relational Database** | PostgreSQL 17 Alpine | Primary system of record for users, offerings, bookings, and categories. | `127.0.0.1:5433:5432` | `application.yml:57`, `docker-compose.yml:3-21` |
| **In-Memory Cache** | Redis 8 Alpine | Cache-aside for the active public offerings catalog. | Internal: `6379` | `application.yml:74-76`, `docker-compose.yml:22-39` |
| **Message Broker** | RabbitMQ 4 Management | Asynchronous background processing for booking notifications. | `127.0.0.1:15672:15672`, Internal: `5672` | `application.yml:78-92`, `RabbitConfiguration.java` |
| **Event Streaming** | Apache Kafka 7.8 (KRaft) | Persistent event distribution for audit logging and analytics. | `127.0.0.1:9092:9092`, Internal: `29092` | `application.yml:94-104`, `docker-compose.yml:154-185` |
| **Document Database** | MongoDB 7 | Scalable event store for the audit microservice. | `127.0.0.1:27018:27017` | `audit-service/.../application.yml:9` |
| **Payment Gateway** | Stripe (or Simulated) | Credit card authorization and webhook settlement. | External: `https://api.stripe.com` | `StripePaymentAdapter.java`, `StripeFeignClient.java` |
| **Primary LLM** | Google Gemini (`gemini-3.8-flash`) | Context-grounded catalog recommendations and vision analysis. | External Google GenAI API | `GeminiAiAdapter.java`, `GeminiVisionAdapter.java` |
| **Alternative LLM** | OpenAI (`gpt-4o-mini`) | Fallback or production alternative provider. | External OpenAI API | `OpenAiAiAdapter.java` |
| **Speech-to-Text** | Groq / Gemini | Audio transcription for voice queries (`/voice`). | External Groq / Google API | `GroqSpeechToTextAdapter.java`, `GeminiSpeechToTextAdapter.java` |
| **Text-to-Speech** | ElevenLabs | Converts AI recommendations to natural audio streams. | External ElevenLabs API | `ElevenLabsTextToSpeechAdapter.java` |
| **Email Mock** | Mailpit | SMTP test server for transactional booking emails. | `127.0.0.1:8025:8025` (UI), Internal: `1025` | `docker-compose.yml:235-240`, `application.yml:17-26` |
| **Metrics & Telemetry** | Prometheus 3.5.0 + Grafana 12.1.1 | JVM, HTTP, and Micrometer metrics monitoring. | `127.0.0.1:9090`, `127.0.0.1:3000` | `docker-compose.yml:127-153`, `ops/` |

---

## 2. Relational Persistence: PostgreSQL 17

- **Driver**: `org.postgresql.Driver` with HikariCP connection pool.
- **Connection Configuration**:
  - Pool Size: Default `DB_POOL_SIZE=5`, minimum idle 1 ([application.yml:61-62](../backend/src/main/resources/application.yml#L61-L62)).
  - DDL Validation: `spring.jpa.hibernate.ddl-auto: validate`. Schema modification is strictly reserved for Flyway.
- **Flyway Migrations**:
  - `V1__init.sql`: Creates core tables (`users`, `offerings`, `bookings`).
  - `V2__seed_data.sql`: Seeds initial admin, provider, and customer credentials.
  - `V3__offering_ownership.sql`: Adds `provider_id` FK to `offerings`.
  - `V4__booking_cancellation_audit.sql`: Adds `booking_status_history` table for cancellation audit trailing.
  - `V5__user_status.sql`: Adds `status` enum (`ACTIVE`, `SUSPENDED`) to `users`.
  - `V6__categories.sql`: Adds `categories` table and `category_id` FK on `offerings`.
  - `V7__booking_idempotency.sql`: Adds `idempotency_key` unique constraint to `bookings`.

---

## 3. In-Memory Caching: Redis 8

- **Adapter**: [RedisOfferingCacheAdapter.java](../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/cache/RedisOfferingCacheAdapter.java).
- **Pattern**: Cache-Aside.
- **Key Schema**: `catalog::offerings` stores serialized JSON list of active offerings.
- **TTL**: Configured via `app.cache.offerings-ttl-minutes: 10` (10 minutes) ([application.yml:132](../backend/src/main/resources/application.yml#L132)).
- **Connection Timeout**: `spring.data.redis.timeout: 2s`.
- **Eviction Strategy**: Calling `OfferingCommandService` write methods (`create`, `update`, `activate`, `deactivate`) triggers `cachePort.evictCatalog()`.

---

## 4. Asynchronous Messaging: RabbitMQ 4

- **Configuration Class**: [RabbitConfiguration.java](../backend/src/main/java/com/riwi/skillbridge/infrastructure/config/RabbitConfiguration.java).
- **Topology**:
  - Exchange: `skillbridge.notifications` (Topic Exchange).
  - Main Queue: `skillbridge.notifications.queue` bound with routing key `notification.*`.
  - Dead Letter Exchange (DLX): `skillbridge.notifications.dlx`.
  - Dead Letter Queue (DLQ): `skillbridge.notifications.dlq`.
- **Verified Retry & Backoff Configuration**:
  - Initial Interval: `2s`.
  - Multiplier: `2.0`.
  - Max Interval: `10s`.
  - Max Attempts: `3`.
  - Requeue on Failure: `false` (moves to DLQ after 3 failures).
- **Consumer Behavior**:
  - [NotificationConsumer.java](../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/messaging/NotificationConsumer.java) handles messages.
  - If `PermanentNotificationException` occurs, message is discarded/sent directly to DLQ without retrying.

---

## 5. Persistent Event Streaming: Apache Kafka 7.8

- **Topology & Topics**:
  - Primary Topic: `booking-events` ([application.yml:153](../backend/src/main/resources/application.yml#L153)).
  - Audit Topic: Mapped to `booking-events` ([application.yml:154](../backend/src/main/resources/application.yml#L154)) to accommodate the audit-service consumer.
- **Publishers**:
  - [KafkaBookingEventPublisher.java](../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/KafkaBookingEventPublisher.java): Publishes `BookingCreated` and `BookingCancelled`.
  - [KafkaAuditEventPublisher.java](../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/KafkaAuditEventPublisher.java): Publishes security events (`UserLoggedIn`, `UserRegistered`).
- **Consumer**:
  - [KafkaAuditConsumer.java](../audit-service/backend/src/main/java/com/riwi/audit/consumer/KafkaAuditConsumer.java) in `audit-service/backend`:
  - Consumer Group: `audit-service-group`.
  - Deserializer: `org.springframework.kafka.support.serializer.ErrorHandlingDeserializer` wrapping `JsonDeserializer`.
  - Target Datastore: MongoDB collection `audit_events`.

---

## 6. Payment Processing: Stripe & Simulated Fallback

- **Configuration**:
  - `app.payment.provider`: `stripe` (default) or `simulated` ([application.yml:156](../backend/src/main/resources/application.yml#L156)).
- **Stripe Mode**:
  - [StripePaymentAdapter.java](../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/payment/stripe/StripePaymentAdapter.java) invokes the Stripe API using OpenFeign ([StripeFeignClient.java](../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/payment/stripe/StripeFeignClient.java)) to create a `PaymentIntent`.
  - Returns `clientSecret` to the client for frontend confirmation via Stripe Elements.
  - Asynchronous webhook reception: [StripeWebhookController.java](../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/webhook/StripeWebhookController.java) cryptographically validates `Stripe-Signature` header via the Stripe Java SDK (`Webhook.constructEvent(...)`).
- **Simulated Mode**:
  - [SimulatedPaymentAdapter.java](../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/payment/SimulatedPaymentAdapter.java) approves any request with card ending in an even digit and declines odd digits.

---

## 7. AI & Multimodal Providers (Fallback Chain Architecture)

- **Provider Switching Architecture**: Complies with [ADR-0007](adr/0007-ai-provider-fallback-chain.md) (supersedes single-adapter model from ADR-001).
- **Composite Fallback Adapters**:
  - `FallbackAiRecommendationAdapter`: Primary composite adapter implementing `AiRecommendationPort` (`gemini:gemini-3.8-flash` → `openai:gpt-4o-mini`).
  - `FallbackSpeechToTextAdapter`: Primary composite adapter implementing `SpeechToTextPort` (`groq:whisper-large-v3-turbo` → `gemini:gemini-audio`).
  - `FallbackVisionAdapter`: Primary composite adapter implementing `VisionPort` (`gemini:gemini-3.8-flash` → fallback).
  - `FallbackTextToSpeechAdapter`: Primary composite adapter implementing `TextToSpeechPort` (`elevenlabs:multilingual-v2` → `noop:noop`).
- **Error Classification & Recoverability**:
  - Centralized in `AiErrorClassifier` based strictly on exception hierarchy and upstream HTTP status codes (no arbitrary string matching).
  - **Recoverable**: HTTP 429 (rate limit), 5xx (server error), timeouts (`TimeoutException`, `SocketTimeoutException`), network disconnects, and malformed LLM JSON.
  - **Non-Recoverable**: HTTP 4xx client errors, `IllegalArgumentException`, input validation failures (aborts fallback immediately).
- **Timeouts & Limits**:
  - Candidate-specific timeout enforced asynchronously via `CompletableFuture.orTimeout`.
  - Max audio upload size: `app.ai.media.max-audio-bytes: 10485760` (10 MB).
  - Max image upload size: `app.ai.media.max-image-bytes: 5242880` (5 MB).
  - Max TTS character length: `app.ai.tts.max-chars: 2000` characters.
- **Observability & Metrics**:
  - Metrics recorded via `AiFallbackMetricsRecorder` using Micrometer: `ai.fallback.attempts`, `ai.fallback.latency`, and `ai.fallback.provider.used`.
  - Event `RecommendationGenerated` v2 enriched with `provider` and `model` resolution metadata.
  - Candidate execution tracked in logs with `X-Correlation-Id` and SLF4J MDC.
- **Declarative Configuration & Resilience (HU-28)**:
  - Typed configuration under `app.ai.fallback.*` for `chat`, `vision`, `stt`, `tts`, `rag`, and `embedding`.
  - Dual environment variable support: compact syntax (`AI_<CAPABILITY>_FALLBACK_CHAIN=gemini:gemini-3.8-flash,openai:gpt-4o-mini`) with whitespace tolerance, and standard Spring relaxed binding. Full backward compatibility with `AI_PROVIDER` and `AI_AUDIO_PROVIDER`.
  - Fail-fast startup validation (`AiFallbackValidator`): Aborts boot immediately if a chain is empty, has non-positive timeout (`timeout-seconds <= 0`), or configures unsupported providers.
  - Independent Circuit Breakers per candidate (`capability:provider:model`) managed by Resilience4j (`AiCircuitBreakerManager`). OPEN circuits are skipped instantly (< 5 ms) without network calls.
  - Administration & Health: `GET /api/admin/ai/providers` restricted to `ROLE_ADMIN` (returns chain and circuit statuses without exposing secrets), and Actuator `AiFallbackHealthIndicator` reporting `UP`, `DEGRADED`, or `DOWN`.

## 8. Embeddings y RAG (HU-26)

| Pieza | Detalle |
|---|---|
| Embeddings | `gemini-embedding-001`, 768 dimensiones, clave propia `GEMINI_EMBEDDING_API_KEY` (nunca se reutiliza `GEMINI_API_KEY`). Sin clave: 503 controlado, no hay respuesta solo con BM25. |
| Almacén vectorial | PostgreSQL 17 con extensión `pgvector` (imagen `pgvector/pgvector:pg17`), schema `knowledge`, índice HNSW `vector_cosine_ops`. En Railway hay que crear la extensión (`CREATE EXTENSION vector;`, T002 pendiente). |
| Generación | `GeminiKnowledgeAnswerAdapter` tras `KnowledgeAnswerPort`, con timeout en cliente. |
| Corpus público | `backend/src/main/resources/knowledge/*.md` con `visibility: public`; no se indexa `docs/`. |
