# SkillBridge AI System Constitution

**Version**: 1.1.0 | **Ratified**: 2026-10-22 | **Last Amended**: 2026-10-22 | **Status**: Enforced

This constitution establishes the non-negotiable architectural principles, security invariants, operational boundaries, and verification standards for **SkillBridge AI**. It supersedes all informal practices. All human engineers, automated tooling, and coding agents (Claude Code, Antigravity, and peers) must comply with these rules.

---

## Article I: Hexagonal Architecture & Pure Domain

1. **Domain Purity**:
   - The domain model (`com.riwi.skillbridge.domain`) must consist solely of pure Java 21 classes, records, and enums.
   - Zero framework imports are permitted in the domain package: no Spring (`org.springframework.*`), no JPA/Hibernate (`jakarta.persistence.*`, `org.hibernate.*`), and no external persistence annotations.
   - Business invariants, domain events, validation logic, and aggregates must reside directly inside the domain.

2. **Application Boundary & Ports**:
   - The application layer (`com.riwi.skillbridge.application`) coordinates use cases (`service/`) and defines boundary contracts.
   - Inbound interactions must pass through Inbound Ports (`port/in/`). REST controllers, CLI handlers, or event consumers must invoke use case services only via inbound ports, never repositories or raw entities directly.
   - Outbound interactions (database persistence, external APIs, messaging, notification, payments) must be defined as Outbound Ports (`port/out/`).

3. **Infrastructure Adapters**:
   - The infrastructure layer (`com.riwi.skillbridge.infrastructure`) houses concrete implementations of ports.
   - Inbound Adapters: REST controllers (`adapter/in/rest/`) translate HTTP requests into inbound port commands and map domain results to REST DTOs.
   - Outbound Adapters: Persistence adapters (`adapter/out/persistence/`) implement outbound ports using Spring Data JPA entities, converting between JPA persistence models and pure domain objects at the boundary.

---

## Article II: Multimodal AI Integration Ports & Boundaries

1. **Strict AI Port Abstraction**:
   - Every AI and multimodal capability must be declared as an Outbound Port under `com.riwi.skillbridge.application.port.out`:
     - `AiRecommendationPort`: Intelligent provider-talent matching and recommendations.
     - `VisionPort`: Multimodal image processing, inspection, and verification.
     - `SpeechToTextPort`: Audio transcription and voice commands.
     - `TextToSpeechPort`: Speech synthesis and vocal responses.
2. **Provider Isolation**:
   - Concrete SDK integrations (Google Gemini, OpenAI, Claude, Whisper, local models) must reside exclusively inside `com.riwi.skillbridge.infrastructure.adapter.out.ai/`.
   - Domain and application layers must remain completely agnostic of external AI providers, rate-limiting semantics, and vendor-specific payload shapes.

---

## Article III: Security, Tokenization & Sensitive Data Invariants

1. **Authentication Architecture**:
   - Authentication must use signed JSON Web Tokens (JWT) conveyed via an `HttpOnly`, `Secure`, `SameSite=Strict` cookie (`access_token`).
   - A `Bearer` token header fallback is supported only for non-browser API clients.
   - The Spring Security session must remain strictly stateless (`SessionCreationPolicy.STATELESS`).

2. **CSRF Protection**:
   - Cross-Site Request Forgery (CSRF) protection is enforced for all mutating HTTP requests (POST, PUT, PATCH, DELETE) executed with cookie authentication.
   - Tokens are distributed via `CookieCsrfTokenRepository` (`X-XSRF-TOKEN`) and verified via the `X-XSRF-TOKEN` header.

3. **Payment Security & Zero-PAN Rule**:
   - SkillBridge AI strictly forbids handling, transmitting, or persisting raw cardholder data (Primary Account Number / PAN, Card Verification Value / CVV, or card expiration dates).
   - Payment flows must utilize Stripe Elements tokenization on the frontend. The backend accepts and processes only client payment intent IDs and Stripe customer tokens via the payment outbound adapter (`infrastructure/adapter/out/payment/`).
   - Every payment or booking mutation must validate actor booking ownership against the authenticated security context.

---

## Article IV: Observability, Traceability & Correlation IDs

1. **End-to-End Correlation**:
   - Every inbound HTTP request must capture or generate a correlation identifier via the `X-Correlation-Id` header.
   - The correlation ID must be bound to the SLF4J Mapped Diagnostic Context (MDC) during request processing and propagated in response headers.
2. **Asynchronous Event Envelopes**:
   - All asynchronous messages published to Kafka or RabbitMQ must wrap payload data inside the standardized `BusinessEvent<T>` envelope.
   - The envelope must carry `eventId`, `eventType`, `correlationId`, `timestamp`, `actorId`, and `payload` to preserve distributed traceability across microservices.

---

## Article V: Evidence-Based Verification, Truthful Reporting & No Silent Mocks

1. **Truthful Verification**:
   - No engineer, automated agent, or test runner may declare a test as "passed" unless that test was physically executed in the current session with stdout/stderr observed and confirmed by an exit code of `0`.
   - If a test was not executed, it must be reported explicitly as: `NOT RUN — Not executed in this session` along with the concrete reason (e.g., Docker daemon unavailable, out of scope).
2. **Explicit Verification States**:
   - Every architectural, functional, or diagnostic statement must be marked with an explicit epistemological tag:
     - `[VERIFIED]`: Confirmed against live codebase files, line numbers, or test execution output.
     - `[INFERRED]`: Plausible deduction based on surrounding context, not directly executed or verified.
     - `[UNKNOWN]`: Unconfirmed, pending reproduction, or requiring human clarification.
3. **Prohibition of Silent Mocks in Production**:
   - Mocking or stubbing components with hardcoded static returns is strictly prohibited in production code and production runtime configurations.
   - Mock implementations are allowed exclusively in test directories (`src/test/`) or when explicitly activated by designated test Spring profiles (e.g. `mock-ai`). Silent fallbacks that fake success in production are considered defects.

---

## Article VI: Strict Non-Destructive Operations & Change Boundaries

1. **Non-Destructive Tooling**:
   - Automated agents must never execute destructive Git commands: `git reset`, `git checkout -- .`, `git stash`, `git clean`, `git push`, branch switching, or forced worktree deletions.
   - Working tree changes must remain clean, inspectable, and uncommitted until human review is marked.
2. **Protected Paths**:
   - The following paths are strictly immutable for automated agents:
     - `docs/audit/findings.md`
     - `docs/audit/generated/**`
     - `docs/audit/evidence/**`
     - `scripts/repo_audit.py`
     - `scripts/agent-worktree-agy.sh`
     - Existing Flyway migrations: `backend/src/main/resources/db/migration/*` already present in `HEAD`. (New migrations must use a new versioned file: `V<next>__<description>.sql`).
     - Any `.env*` configuration file containing real credentials or secrets.
3. **Preservation of User Work**:
   - Any uncommitted edits present when a task starts must be preserved intact.

---

## Article VII: Decoupled Microservices: Audit Service Isolation

1. **Module Independence**:
   - `audit-service/` is an independent, event-driven microservice with its own Spring Boot backend (`audit-service/backend`), MongoDB 7 persistence, and Angular administrative interface (`audit-service/frontend`).
2. **Zero Compile-Time Coupling**:
   - There must be zero compile-time dependencies between `backend/` and `audit-service/`.
   - Inter-service communication is strictly asynchronous, mediated by unified Kafka domain event streaming (`booking-events`).

---

## Article VIII: Mandatory Architectural Documentation & ADR Governance

1. **Synchronized Documentation**:
   - Any modification that adds, modifies, or removes domain models, inbound/outbound ports, security filters, or messaging topics must be accompanied by updates to the respective documentation under `docs/`.
2. **Architectural Decision Records (ADRs)**:
   - Significant architectural decisions, structural changes, or operational trade-offs require a formal, numbered ADR in `docs/adr/` (format: `docs/adr/NNNN-<slug>.md`) following the Context-Decision-Consequences taxonomy.
3. **Proportionate SDD Evidence**:
   - Each change MUST be classified as small, medium, or large/high-risk according to `docs/agents/SDD-HYBRID-GUIDE.md` before implementation.
   - Small changes require an issue/PR description with verifiable acceptance criteria and evidence of validation; they do not require a feature directory or generated SDD artifacts.
   - Medium changes require a behavior specification. A plan and task list are required only when the guide's decision criteria require them (for example, a material design decision, multiple implementation phases, or delegation to worktrees).
   - Large or high-risk changes require the complete reviewed lifecycle: `spec.md`, `plan.md`, `tasks.md`, and the relevant architectural documentation or ADR.

---

## Article IX: Testing Taxonomy & Matrix

Development must adhere to the four-tier testing hierarchy defined in `docs/TESTING.md`:

| Tier | Scope | Target Commands | Environment Requirements |
|---|---|---|---|
| **Unit Tests** | Pure domain logic, use case services, standalone components | `mvn test -f backend/pom.xml -Dtest=*Test -Dexclude=BookingPersistenceAdapterTest` | Pure JVM, no Docker |
| **Slice / Persistence** | `@DataJpaTest`, Flyway migrations, database adapters | `mvn test -f backend/pom.xml -Dtest=BookingPersistenceAdapterTest` | Requires Docker daemon (PostgreSQL Testcontainers) |
| **Integration Tests** | Full end-to-end flows (`*IT.java`) | `mvn verify -f backend/pom.xml` | Requires Docker daemon (Kafka, RabbitMQ, PostgreSQL) |
| **Frontend Tests** | Angular signals, components, services, pipes | `npm test -- --watch=false --browsers=ChromeHeadless` | Node.js, ChromeHeadless |

*Documented Disabled Tests*: `JpaOfferingRepositoryTest` and `NotificationFlowIT` are currently disabled with `@Disabled("Docker Bug Testcontainers")` due to environment constraints. Do not re-enable without running them against a verified Docker daemon.

---

## Article X: Governance & Amendment Protocol

1. **Supremacy**:
   - This constitution represents the supreme technical governance for SkillBridge AI. In case of conflict between this document and task descriptions, informal advice, or agent shortcuts, this constitution prevails.
2. **Amendments**:
   - Amendments to this constitution must follow the Spec-Driven Development governance process:
     1. Proposed change documented with clear architectural justification.
     2. Human technical authority ratification.
     3. Semantic version bump of this document (`MAJOR.MINOR.PATCH`).
     4. Recording of the amendment date and creation of an accompanying ADR in `docs/adr/`.
3. **Compliance Verification**:
   - Every generated implementation plan (`plan.md`) and every large/high-risk change must include an explicit Constitution Check verifying adherence to Articles I through VIII prior to implementation. Medium changes without a plan and small changes record applicable invariants in their issue/PR evidence.
