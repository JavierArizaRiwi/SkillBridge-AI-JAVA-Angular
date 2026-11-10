# Module Documentation: Core Backend (`backend/`)

The Core Backend is a Spring Boot application structured following Hexagonal Architecture (Ports and Adapters). It serves as the primary system of record, hosting authentication, catalog, booking lifecycle, payments, and multimodal AI services.

---

## 1. Module Overview & Entry Points

- **Root Directory**: `backend/`
- **Main Class Entry Point**: [SkillBridgeApplication.java](../../backend/src/main/java/com/riwi/skillbridge/SkillBridgeApplication.java)
- **Primary Configuration**: [application.yml](../../backend/src/main/resources/application.yml)
- **Runtime Environment**: Java 21 (Temurin), Spring Boot 3.5.6, Maven 3.9+.

---

## 2. Directory Layout & Layer Responsibilities

```text
backend/src/main/java/com/riwi/skillbridge/
├── application/
│   ├── port/
│   │   ├── in/           # Inbound Use Case interfaces (CreateBookingUseCase, etc.)
│   │   ├── out/          # Outbound Port interfaces (BookingRepositoryPort, AiRecommendationPort, etc.)
│   │   └── out/event/    # Event publisher ports and event envelopes (BusinessEvent)
│   ├── service/          # Application orchestrators implementing Inbound Ports
│   └── common/           # Shared application value objects
├── domain/               # Pure Java domain model (Zero framework dependencies)
│   ├── model/            # Core business aggregates (Booking, Offering, Category, UserAccount)
│   ├── policy/           # Business policies (OfferingAccessPolicy, UserManagementPolicy)
│   ├── service/          # Domain services (BookingCancellationPolicy)
│   ├── event/            # Pure domain events (PaymentApproved, PaymentRejected)
│   └── exception/        # Domain exceptions (BusinessRuleException, DomainNotFoundException)
└── infrastructure/       # Framework adapters and configurations
    ├── adapter/
    │   ├── in/
    │   │   ├── rest/     # Controllers, DTOs, Webhooks, Exception Handlers
    │   │   └── messaging/# Inbound message listeners (KafkaAuditConsumer, NotificationConsumer)
    │   └── out/
    │       ├── persistence/ # Spring Data JPA entities, repositories, and persistence adapters
    │       ├── cache/       # Redis caching adapters (RedisOfferingCacheAdapter)
    │       ├── messaging/   # Kafka and RabbitMQ message publishers
    │       ├── ai/          # Gemini, OpenAI, Groq STT, ElevenLabs TTS adapters
    │       └── payment/     # Stripe Feign client and simulated payment adapters
    ├── config/           # Spring @Configuration classes (Security, Rabbit, AI, Domain)
    └── security/         # JWT filters, password hasher, cookie handlers, UserDetailsService
```

---

## 3. Important Classes & Components

### 3.1. Inbound Adapters (REST Controllers)
- [BookingController.java](../../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/BookingController.java): Manages booking creation (`POST /api/bookings`) with idempotency and cancellation (`PATCH /api/bookings/{id}/cancel`).
- [OfferingController.java](../../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/OfferingController.java): Public catalog query (`GET /api/offerings`), provider offering mutations (`POST`, `PUT`, `activate`, `deactivate`).
- [CategoryController.java](../../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/CategoryController.java): Category listing and management.
- [AiController.java](../../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/AiController.java): Recommendations via text, voice (`/voice`), image (`/image`), and speech synthesis (`/tts`).
- [PaymentController.java](../../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/PaymentController.java): Authorizes payments using idempotent payment commands.
- [StripeWebhookController.java](../../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/webhook/StripeWebhookController.java): Verifies cryptographic Stripe signatures and handles webhook events.
- [AdminUserController.java](../../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/AdminUserController.java): Admin-only user role and status modifications (`ACTIVE` / `SUSPENDED`).

### 3.2. Application Services
- [BookingService.java](../../backend/src/main/java/com/riwi/skillbridge/application/service/BookingService.java): Coordinates booking creation, cancellation rules, status updates, and event emission.
- [OfferingCommandService.java](../../backend/src/main/java/com/riwi/skillbridge/application/service/OfferingCommandService.java) & [OfferingQueryService.java](../../backend/src/main/java/com/riwi/skillbridge/application/service/OfferingQueryService.java): Manage offerings and invalidate Redis catalog caches.
- [AiRecommendationService.java](../../backend/src/main/java/com/riwi/skillbridge/application/service/AiRecommendationService.java): Coordinates speech transcription, vision parsing, and catalog-grounded LLM inference.

### 3.3. Outbound Adapters
- [BookingPersistenceAdapter.java](../../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/BookingPersistenceAdapter.java): Translates between domain `Booking` and JPA `BookingEntity`.
- [RedisOfferingCacheAdapter.java](../../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/cache/RedisOfferingCacheAdapter.java): Serializes/deserializes offering lists in Redis.
- [StripePaymentAdapter.java](../../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/payment/stripe/StripePaymentAdapter.java): Calls Stripe API via OpenFeign to create PaymentIntents.
- [KafkaBookingEventPublisher.java](../../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/KafkaBookingEventPublisher.java): Publishes business events to Kafka topic `booking-events`.

---

## 4. Configuration Keys & Environment Variables

Key settings configured in [backend/src/main/resources/application.yml](../../backend/src/main/resources/application.yml):

| Configuration Key | Env Variable Override | Default Value | Description |
|---|---|---|---|
| `server.port` | `PORT` | `8080` | HTTP port |
| `spring.datasource.url` | `DATABASE_URL` | `jdbc:postgresql://localhost:5432/skillbridge` | PostgreSQL JDBC connection URL |
| `spring.data.redis.url` | `REDIS_URL` | `redis://localhost:6379` | Redis connection URL |
| `spring.rabbitmq.addresses` | `RABBITMQ_URL` | `amqp://guest:guest@localhost:5672` | RabbitMQ connection string |
| `spring.kafka.bootstrap-servers` | `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker address |
| `app.jwt.private-key` | `JWT_PRIVATE_KEY` | *(none: required)* | RSA private key, PKCS#8 PEM. JWTs are signed with RS256. The backend does not start without it unless ephemeral keys are enabled |
| `app.jwt.public-key` | `JWT_PUBLIC_KEY` | *(derived from the private key)* | Optional X.509 PEM; must match the private key |
| `app.jwt.key-id` | `JWT_KEY_ID` | `skillbridge-1` | `kid` header of issued tokens |
| `app.jwt.legacy-secret` | `JWT_LEGACY_SECRET` | *(empty)* | Migration only: still **validates** (never issues) old HS256 tokens |
| `app.jwt.generate-ephemeral-keys` | `JWT_GENERATE_EPHEMERAL_KEYS` | `false` | Local development only: in-memory key pair, sessions lost on restart |
| `app.ai.provider` | `AI_PROVIDER` | `gemini` | Primary AI provider (`gemini` or `openai`) |
| `app.payment.provider` | `PAYMENT_PROVIDER` | `stripe` | Payment adapter (`stripe` or `simulated`) |
| `stripe.api-url` | `STRIPE_API_URL` | `https://api.stripe.com` | Base URL for Stripe REST API |

---

## 5. Concrete Files to Inspect Before Modifying

1. **Before modifying any Endpoint/DTO**:
   - Controller: `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/`
   - DTOs: `.../in/rest/dto/`
   - Security Config: `backend/src/main/java/com/riwi/skillbridge/infrastructure/config/SecurityConfiguration.java`
2. **Before modifying Domain Logic or Policies**:
   - Target Aggregate: `backend/src/main/java/com/riwi/skillbridge/domain/model/`
   - Target Policy: `backend/src/main/java/com/riwi/skillbridge/domain/policy/` or `domain/service/`
   - Inbound Port: `backend/src/main/java/com/riwi/skillbridge/application/port/in/`
3. **Before modifying DB Schema or Queries**:
   - Flyway Scripts: `backend/src/main/resources/db/migration/`
   - JPA Entities: `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/entity/`
   - Repositories: `.../persistence/repository/`
   - Outbound Port: `backend/src/main/java/com/riwi/skillbridge/application/port/out/`

---

## 6. Testing & Build Instructions

```bash
cd backend

# Execute unit and slice tests
mvn test

# Execute full verification (including Testcontainers integration tests)
# Note: Requires Docker daemon running on the host
mvn verify
```
