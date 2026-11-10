# SkillBridge AI — Project Context

SkillBridge AI is a multi-tier platform designed for discovering, booking, and managing professional mentoring and technical skill-sharing services. It features a multimodal AI recommendation assistant (text, audio transcription, vision image analysis, text-to-speech), an event-driven notification pipeline, and a dedicated audit microservice.

---

## 1. System Scope & Architecture Topology

The solution consists of three primary software subsystems and shared infrastructure services:

```text
               +--------------------------------------------------+
               |              Browser Client                      |
               +-------------------+------------------------------+
                                   |
             +---------------------+---------------------+
             | [Port 8088]                               | [Port 8090]
             v                                           v
+--------------------------+               +--------------------------+
|      frontend/           |               |  audit-service/frontend  |
|  (Angular 20 SPA)        |               |  (Admin Audit Dashboard) |
+------------+-------------+               +-------------+------------+
             |                                           |
             | Reverse proxy / API                       | REST API
             v                                           v
+--------------------------+               +--------------------------+
|       backend/           |               |  audit-service/backend   |
| (Spring Boot 3.5.6 API)  |               | (Spring Boot + Mongo)    |
|       [Port 8080]        |               |       [Port 8081]        |
+---+------+-------+---+---+               +-------------+------------+
    |      |       |   |                                 ^
    |      |       |   +--------- Kafka -----------------+
    |      |       |              (booking-events topic)
    |      |       v
    |      |   RabbitMQ (notifications exchange)
    |      v
    |   Redis 8 (Catalog cache-aside)
    v
PostgreSQL 17 (Relational System of Record)
```

---

## 2. System Components & Responsibilities

| Subsystem | Location | Technologies | Key Responsibilities |
|---|---|---|---|
| **Core API Backend** | `backend/` | Java 21, Spring Boot 3.5.6, Spring Security 6, Spring AI 1.1.8 | Domain logic, catalog, bookings, payments, multimodal AI, notifications, auth. |
| **Client Frontend** | `frontend/` | Angular 20, TypeScript 5.9, Tailwind CSS v4 | End-user interface: service catalog, user authentication, booking creation, AI chat, payment modal. |
| **Audit Backend** | `audit-service/backend/` | Spring Boot 3.5.6, Spring Data MongoDB, Spring Kafka | Consumes Kafka events, stores immutable audit history in MongoDB, exposes audit REST endpoints. |
| **Audit Frontend** | `audit-service/frontend/` | Angular 20, Tailwind CSS v4, Signals | Administrative UI for searching and inspecting live domain and security events. |
| **PostgreSQL 17** | Container `postgres` | Port 5433 (host) -> 5432 (internal) | Primary ACID relational database managed via Flyway (`V1` to `V7`). |
| **Redis 8** | Container `redis` | Port 6379 | In-memory cache-aside store for public catalog offerings. |
| **RabbitMQ 4** | Container `rabbitmq` | Ports 5672 (AMQP), 15672 (UI) | Asynchronous task queue for email notifications with retry and DLQ. |
| **Apache Kafka 7.8** | Container `kafka` | Ports 9092 (host), 29092 (internal) | Event streaming backbone distributing domain events to audit consumers. |
| **MongoDB 7** | Container `audit-mongo` | Port 27018 (host) -> 27017 (internal) | Document store for persistent audit events and statistics. |
| **Mailpit** | Container `mailpit` | Ports 1025 (SMTP), 8025 (Web UI) | Local mock SMTP server for inspecting transactional notification emails. |

---

## 3. Technology Matrix & Entry Points

- **Backend Entry Point**: [SkillBridgeApplication.java](../backend/src/main/java/com/riwi/skillbridge/SkillBridgeApplication.java)
- **Frontend Entry Point**: [frontend/src/main.ts](../frontend/src/main.ts), configured via [frontend/src/app/app.routes.ts](../frontend/src/app/app.routes.ts)
- **Audit Backend Entry Point**: [AuditServiceApplication.java](../audit-service/backend/src/main/java/com/riwi/audit/AuditServiceApplication.java)
- **Audit Frontend Entry Point**: [audit-service/frontend/src/main.ts](../audit-service/frontend/src/main.ts)
- **Orchestration**: [docker-compose.yml](../docker-compose.yml), [Makefile](../Makefile)

---

## 4. Major Domain Concepts & Workflows

### 4.1. Actors & Roles
- **CUSTOMER**: Can browse offerings, request AI recommendations, book services, view personal bookings, and cancel eligible bookings.
- **PROVIDER**: Can create, update, activate, and deactivate their own service offerings.
- **ADMIN**: Can manage categories, view and toggle user status (ACTIVE / SUSPENDED), promote roles, and oversee system metrics.

### 4.2. Core Workflows
1. **Public Catalog Browsing (`GET /api/offerings`)**:
   - Cache-aside via Redis (`RedisOfferingCacheAdapter`, key `catalog::offerings`, TTL 10 minutes).
   - If missing from Redis, queries PostgreSQL via `JpaOfferingRepository` and repopulates cache.
2. **Idempotent Booking Creation (`POST /api/bookings`)**:
   - Requires `Authorization: Bearer <jwt>` (or HttpOnly cookie) and `Idempotency-Key` header.
   - Saves `Booking` aggregate (`PENDING`) in PostgreSQL with unique idempotency constraint.
   - Publishes `BookingCreated` domain event to RabbitMQ (for notification queue) and Kafka topic `booking-events` (for audit logging).
3. **Booking Cancellation (`PATCH /api/bookings/{id}/cancel`)**:
   - Enforces 24-hour advance notice policy via `BookingCancellationPolicy`.
   - Records cancellation history in `booking_status_history` table.
   - Emits `BookingCancelled` event to Kafka topic `booking-events`.
4. **Multimodal AI Recommendations (`POST /api/ai/recommendations*`)**:
   - Text prompts: Grounded against the active service catalog using Google Gemini (`gemini-3.8-flash`) or OpenAI.
   - Voice prompts: Audio transcribed via Groq Whisper or Gemini (`SpeechToTextPort`).
   - Image analysis: Processed via Gemini Vision (`VisionPort`).
   - Text-to-Speech: Converted to audio stream via ElevenLabs or NoOp fallback (`TextToSpeechPort`).
5. **Payment Processing (`POST /api/payments/authorize`)**:
   - Authorizes booking payment via Stripe PaymentIntent or simulated adapter.
   - Asynchronous confirmation handled via Stripe Webhook (`StripeWebhookController`).

---

## 5. Module Navigation Links

For comprehensive module-specific guides, consult:
- **Backend Architecture & Layers**: [docs/MODULES/backend.md](MODULES/backend.md)
- **Frontend Architecture & Components**: [docs/MODULES/frontend.md](MODULES/frontend.md)
- **Audit Microservice Architecture**: [docs/MODULES/audit-service.md](MODULES/audit-service.md)
- **Testing & Quality Assurance**: [docs/TESTING.md](TESTING.md)
- **External Integrations & Brokers**: [docs/INTEGRATIONS.md](INTEGRATIONS.md)
- **Security & Authorization Model**: [docs/SECURITY-MODEL.md](SECURITY-MODEL.md)
- **Contribution Guide**: [docs/CONTRIBUTING.md](CONTRIBUTING.md)

---

## 6. Known Limitations & Open Questions

- **Audit Service Exposure (`SEC-004`)**: `audit-service/backend` exposes endpoints publicly on port 8081 without authentication.
- **Payment Ownership Boundary (`SEC-003`)**: `PaymentController.authorizePayment()` does not verify whether the authenticated actor owns the target `bookingId`.
- **Makefile Syntax Error (`QA-001`)**: Line 12 in `Makefile` contains an unindented command, causing `make` to fail with exit code 2.
- **CI Pipeline Scope (`QA-004`)**: `.github/workflows/ci.yml` builds only `backend/` and `frontend/`, completely ignoring `audit-service/`.
- **Testcontainers in Unit Builds (`QA-006` / `QA-007`)**: `BookingPersistenceAdapterTest` requires Docker during `mvn test`. Two tests (`NotificationFlowIT`, `JpaOfferingRepositoryTest`) are silenced with `@Disabled("Docker Bug Testcontainers")`.
- **See Full Risk Register**: [docs/RISK-REGISTER.md](RISK-REGISTER.md).

---

## 7. Adding Features Safely

1. **Maintain Inbound/Outbound Isolation**:
   - New use cases belong in `application/port/in/` and are implemented in `application/service/`.
   - External dependencies (DB, Redis, HTTP, Queues) belong in `application/port/out/` and are implemented in `infrastructure/adapter/out/`.
2. **Never leak Web or JPA into Domain**:
   - Domain entities in `domain/model/` must remain pure Java records or classes.
3. **Verify Database Schema**:
   - New database changes must be added as sequential Flyway migrations (`V8__...sql`) in `backend/src/main/resources/db/migration/`.
