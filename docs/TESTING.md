# Testing Guide & Quality Assurance — SkillBridge AI

This document provides verified instructions and technical details regarding test suites, execution categories, test container requirements, disabled tests, and known coverage gaps across the repository.

---

## 1. Quick Test Commands Matrix

| Scope | Category | Working Directory | Command | Requirements / Notes |
|---|---|---|---|---|
| **Core Backend** | Unit & Slice Tests | `backend/` | `mvn test` | Runs pure unit tests and JPA slice tests. **Requires Docker daemon** due to `BookingPersistenceAdapterTest`. |
| **Core Backend** | Integration Tests | `backend/` | `mvn verify` | Runs Failsafe plugin (`*IT.java`). Starts Testcontainers. |
| **Client Frontend** | Unit Tests | `frontend/` | `npm test -- --watch=false --browsers=ChromeHeadless` | Karma + Jasmine executed headlessly. |
| **Audit Backend** | Unit Tests | `audit-service/backend/` | `mvn test` | Tests MongoDB repository and Kafka serialization. |
| **Audit Frontend** | Unit Tests | `audit-service/frontend/` | `npm test -- --watch=false --browsers=ChromeHeadless` | Karma + Jasmine executed headlessly. |

---

## 2. Test Categories & Taxonomy

### 2.1. Core Backend (`backend/src/test/java`)

The backend tests are segregated according to Hexagonal architecture layers:

1. **Domain Unit Tests (Zero Framework, In-Memory)**:
   - Location: `backend/src/test/java/com/riwi/skillbridge/domain/`
   - Key Suites:
     - [BookingTest.java](../backend/src/test/java/com/riwi/skillbridge/domain/model/BookingTest.java): Tests domain model invariants, status transitions, and validations.
     - [BookingCancellationPolicyTest.java](../backend/src/test/java/com/riwi/skillbridge/domain/service/BookingCancellationPolicyTest.java): Tests 24-hour advance cancellation policy.
     - [OfferingAccessPolicyTest.java](../backend/src/test/java/com/riwi/skillbridge/domain/policy/OfferingAccessPolicyTest.java): Tests provider offering ownership rules.
     - [UserManagementPolicyTest.java](../backend/src/test/java/com/riwi/skillbridge/domain/policy/UserManagementPolicyTest.java): Tests role changes and status rules.

2. **Application Service Tests (Mockito-Mocked Boundaries)**:
   - Location: `backend/src/test/java/com/riwi/skillbridge/application/service/`
   - Key Suites:
     - [BookingServiceTest.java](../backend/src/test/java/com/riwi/skillbridge/application/service/BookingServiceTest.java): Tests booking orchestration, idempotency checks, and event publication.
     - [OfferingCacheConsistencyTest.java](../backend/src/test/java/com/riwi/skillbridge/application/service/OfferingCacheConsistencyTest.java): Tests that write operations evict or update the Redis catalog cache.
     - [AiRecommendationServiceTest.java](../backend/src/test/java/com/riwi/skillbridge/application/service/AiRecommendationServiceTest.java): Tests catalog prompt construction and LLM response parsing.
     - [PaymentAuthorizationServiceTest.java](../backend/src/test/java/com/riwi/skillbridge/application/service/PaymentAuthorizationServiceTest.java): Tests idempotent payment processing and event emission.

3. **Inbound Web Slice Tests (`@WebMvcTest`)**:
   - Location: `backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/`
   - Key Suites:
     - [BookingControllerSecurityTest.java](../backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/BookingControllerSecurityTest.java): Tests RBAC and route protection.
     - [OfferingSecurityTest.java](../backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/OfferingSecurityTest.java): Tests public vs provider permissions.
     - [StripeWebhookControllerTest.java](../backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/webhook/StripeWebhookControllerTest.java): Tests HMAC signature header validation.

4. **Integration Tests (`*IT.java` via Maven Failsafe)**:
   - [BookingCancellationIT.java](../backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/BookingCancellationIT.java): Full flow testing of booking cancellation using Testcontainers PostgreSQL.

---

## 3. Docker & Testcontainers Requirements

Running certain test suites requires the host system to have an operational Docker daemon:
- **Image dependencies**: `postgres:17-alpine`, `rabbitmq:4-management-alpine`.
- **Known Issue with `BookingPersistenceAdapterTest` (`QA-006`)**:
  - [BookingPersistenceAdapterTest.java](../backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/BookingPersistenceAdapterTest.java) uses `@Testcontainers` with PostgreSQL.
  - Because it ends with `*Test.java` rather than `*IT.java`, it is executed by the Maven Surefire plugin during the `mvn test` phase. If Docker is not running locally, `mvn test` will fail.

---

## 4. Verified Disabled Tests

Two tests in the backend are explicitly disabled in source code:

1. **[JpaOfferingRepositoryTest.java#L16](../backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/JpaOfferingRepositoryTest.java#L16)**:
   ```java
   @org.junit.jupiter.api.Disabled("Docker Bug Testcontainers")
   @Testcontainers
   @DataJpaTest
   class JpaOfferingRepositoryTest { ... }
   ```
   *Reason*: Marked with `"Docker Bug Testcontainers"`. Silences PostgreSQL repository verification.

2. **[NotificationFlowIT.java#L56](../backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/in/messaging/NotificationFlowIT.java#L56)**:
   ```java
   @org.junit.jupiter.api.Disabled("Docker Bug Testcontainers")
   @Testcontainers
   class NotificationFlowIT { ... }
   ```
   *Reason*: Marked with `"Docker Bug Testcontainers"`. Silences end-to-end RabbitMQ publisher/consumer/retry/DLQ integration verification.

---

## 5. Known Coverage Gaps & Limitations

1. **Absence of Integration Tests for Kafka (`QA-012`)**:
   - There are no tests utilizing `EmbeddedKafka` or `KafkaContainer` to verify real end-to-end event publication from `backend/` to `audit-service/`.
2. **Absence of Redis Integration Tests**:
   - `RedisOfferingCacheAdapter` is verified via unit mocks; no test validates live cache-aside behavior against a real Redis Testcontainer.
3. **CI Pipeline Omits Audit Service (`QA-004`)**:
   - `.github/workflows/ci.yml` runs only `backend` and `frontend`. Tests in `audit-service/backend/src/test` are never run automatically in CI.
4. **Makefile Target Syntax Defect (`QA-001`)**:
   - `Makefile` line 12 has an indentation syntax error, preventing developers from running `make test-back` without fixing the Makefile first.

## 6. Matriz de pruebas del asistente RAG (HU-26)

| Prueba | Requiere Docker | Qué cubre |
|---|---|---|
| `Bm25ScorerTest`, `CosineSimilarityTest`, `ReciprocalRankFusionTest`, `ThresholdRuleTest` | No | Dominio puro |
| `AskKnowledgeServiceTest`, `KnowledgeEarlyRejectionTest` | No | Flujo, umbral, rechazo temprano sin LLM |
| `KnowledgeAskFlowIT` | No | Flujo HTTP con corpus real y dobles deterministas (ES/EN, datos privados, inyección, 400, 401, timeout 503, embeddings caídos 503) |
| `KnowledgeEvaluationTest` | No | Genera `specs/026-rag-knowledge/evaluation-report.md` (dobles deterministas: no mide calidad con Gemini) |
| `KnowledgeControllerTest` | No | Contrato REST (MockMvc) |
| `AiRecommendationShortlistTest` | No | Preselección RAG de ofertas y fallbacks (FR-020) |
| `PgVectorKnowledgeAdapterTest` | **Sí** (`pgvector/pgvector:pg17`) | Búsqueda y `contentHash` sobre PostgreSQL real |

`KnowledgeAskFlowIT` se ejecuta con `mvn test -Dtest=KnowledgeAskFlowIT` (no usa contenedores).
