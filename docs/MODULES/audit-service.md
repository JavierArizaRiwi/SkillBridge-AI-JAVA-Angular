# Module Documentation: Audit Microservice (`audit-service/`)

The **Audit Microservice** is an independent, event-driven subsystem responsible for capturing, persisting, and visualizing domain and security events emitted by the core SkillBridge backend.

It is physically and logically decoupled from the core backend, utilizing Apache Kafka for asynchronous ingestion and MongoDB for high-throughput, schema-agnostic event storage.

---

## 1. Subsystem Architecture

The microservice consists of two autonomous applications:

```text
                  Apache Kafka
           (Topic: booking-events)
                      |
                      v
+-------------------------------------------------+
|      audit-service/backend (Port 8081)          |
|  - KafkaAuditConsumer (spring-kafka)            |
|  - AuditMongoInitializer (Mongo schema setup)   |
|  - AuditEventRepository (MongoRepository)       |
|  - AuditController (REST: /api/audit/*)         |
+---------------------+---------------------------+
                      |
                      v
             MongoDB 7 (audit-mongo)
           Collection: audit_events
                      ^
                      | REST Queries (/api/audit/events, /stats)
+---------------------+---------------------------+
|      audit-service/frontend (Port 8090)         |
|  - Angular 20 Standalone App                    |
|  - Tailwind CSS v4 Admin Dashboard              |
|  - Live Polling & Payload Modal Inspector       |
+-------------------------------------------------+
```

---

## 2. Backend Component (`audit-service/backend/`)

- **Root Directory**: `audit-service/backend/`
- **Main Class Entry Point**: [AuditServiceApplication.java](../../audit-service/backend/src/main/java/com/riwi/audit/AuditServiceApplication.java)
- **Configuration File**: [application.yml](../../audit-service/backend/src/main/resources/application.yml)
- **Tech Stack**: Java 21, Spring Boot 3.5.6, Spring Data MongoDB, Spring Kafka.

### Key Backend Classes:
- [KafkaAuditConsumer.java](../../audit-service/backend/src/main/java/com/riwi/audit/consumer/KafkaAuditConsumer.java):
  - Subscribes to `${app.kafka.topic.booking-events}` (`booking-events`) using consumer group `audit-service-group`.
  - Deserializes messages into `BusinessEvent` and persists them immediately via `AuditEventRepository`.
- [AuditMongoInitializer.java](../../audit-service/backend/src/main/java/com/riwi/audit/config/AuditMongoInitializer.java):
  - An `ApplicationRunner` that version-controls MongoDB schema and index initialization (simulating Flyway for non-relational storage).
  - Creates the `audit_events` collection and sets compound indexes on `occurredAt` (descending) and `eventType`.
- [AuditController.java](../../audit-service/backend/src/main/java/com/riwi/audit/controller/AuditController.java):
  - `GET /api/audit/events`: Returns the chronological list of recorded business and security events.
  - `GET /api/audit/events/{eventId}`: Fetches details for a single event.
  - `GET /api/audit/stats`: Aggregates counts for total events, `BookingCreated`, `BookingCancelled`, etc.

---

## 3. Frontend Component (`audit-service/frontend/`)

- **Root Directory**: `audit-service/frontend/`
- **Application Bootstrap**: [audit-service/frontend/src/main.ts](../../audit-service/frontend/src/main.ts)
- **Tech Stack**: Angular 20, TypeScript 5.9, Tailwind CSS v4.

### Key Frontend Features:
- **Admin Dashboard**: Live event feed polling `/api/audit/events` every 10 seconds.
- **Filtering**: Filters by event type, action, actor, resource ID, or correlation ID.
- **Payload Inspection Modal**: Displays full JSON payload and metadata for audit trails.
- **Auth Interceptor ([auth.interceptor.ts](../../audit-service/frontend/src/app/auth.interceptor.ts))**:
  - Tries to read `skillbridge_token` from `localStorage` and attach it as `Authorization: Bearer <token>`. *(Note: See Known Discrepancies below)*.

---

## 4. Configuration Keys & Environment Variables

| Variable | Default Value | Description |
|---|---|---|
| `PORT` | `8081` | HTTP port for Audit Backend |
| `AUDIT_MONGO_URI` | `mongodb://localhost:27017/audit` | Connection string to MongoDB instance |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker bootstrap address (`kafka:29092` inside Docker Compose) |
| `KAFKA_CONSUMER_GROUP` | `audit-service-group` | Consumer group identifier |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:8090,http://localhost:4200` | Allowed origins for Audit REST API |

---

## 5. Known Limitations & Architectural Findings

1. **Missing Authentication on Backend (`SEC-004`)**:
   - `audit-service/backend` does not include `spring-boot-starter-security`.
   - All audit endpoints (`/api/audit/events`, `/api/audit/stats`) are completely open to unauthenticated HTTP requests on port 8081.
2. **Frontend Auth Token Desynchronization**:
   - The audit frontend reads `localStorage.getItem('skillbridge_token')`.
   - However, the main backend migrated authentication to HttpOnly cookies (`access_token`). Thus, an administrator logged into the main app cannot automatically authenticate to the audit frontend unless a separate token is provisioned.
3. **CI Pipeline Omission (`QA-004`)**:
   - The root GitHub Actions workflow (`.github/workflows/ci.yml`) does not include jobs to compile, test, or build `audit-service/backend` or `audit-service/frontend`.
4. **Git Repository Hygiene (`QA-005`)**:
   - `audit-service/backend/target/` was previously committed to Git, leaving tracked binary JARs and compiled classes in version control.

---

## 6. Concrete Files to Inspect Before Modifying

- **Backend Logic / Ingestion**:
  - Consumer: [KafkaAuditConsumer.java](../../audit-service/backend/src/main/java/com/riwi/audit/consumer/KafkaAuditConsumer.java)
  - Event Model: [BusinessEvent.java](../../audit-service/backend/src/main/java/com/riwi/audit/model/BusinessEvent.java)
  - Repository: [AuditEventRepository.java](../../audit-service/backend/src/main/java/com/riwi/audit/repository/AuditEventRepository.java)
- **Frontend Dashboard / State**:
  - Service: [audit.service.ts](../../audit-service/frontend/src/app/audit.service.ts)
  - Dashboard Component: [audit-service/frontend/src/app/dashboard/](../../audit-service/frontend/src/app/dashboard)

---

## 7. Testing & Build Instructions

```bash
# Test Audit Backend
cd audit-service/backend
mvn test

# Test Audit Frontend
cd audit-service/frontend
npm ci
npm test -- --watch=false --browsers=ChromeHeadless
```

---

## 8. Running the Audit Service

The audit service is part of the main `docker-compose.yml`. Start it with the rest of the infrastructure:

```bash
docker compose up -d audit-backend audit-frontend
```

To run only the frontend locally:

```bash
cd audit-service/frontend
npm install
npm start
```

Once running:
- **Backend API**: <http://localhost:8081>
- **Frontend Dashboard**: <http://localhost:8090> (or <http://localhost:4200> when started with `npm start`)

Inside Docker the service reaches Kafka at `kafka:29092` (`KAFKA_BOOTSTRAP_SERVERS`) and joins the `audit-service-group` consumer group.
