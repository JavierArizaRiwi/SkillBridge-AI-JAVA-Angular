# Contributing & Developer Workflow Guide — SkillBridge AI

This guide establishes the concrete, verified workflow for developing, testing, and documenting new features in SkillBridge AI without breaking hexagonal boundaries or incurring unnecessary context loading.

---

## 1. Feature Investigation Procedure

Before writing any code:
1. **Locate Existing Patterns**:
   - Trace similar features already implemented in the codebase (e.g., trace `Booking` for multi-step transactional flows, `Offering` for CRUD + caching, `Category` for simple master entities).
2. **Review Database Schema**:
   - Check `backend/src/main/resources/db/migration/` to understand existing tables, constraints, and column data types.
3. **Check Routing & Security**:
   - Verify if the route requires authentication or specific roles in [SecurityConfiguration.java](../backend/src/main/java/com/riwi/skillbridge/infrastructure/config/SecurityConfiguration.java).

---

## 2. Feature Tracing Guide (End-to-End Walkthrough)

To understand how data flows across the system, follow an existing feature such as **Booking Creation**:

```text
1. Frontend Form:
   frontend/src/app/features/booking.component.ts
   └── Calls BookingService: frontend/src/app/core/booking.service.ts
       └── Sends HTTP POST /api/bookings with 'Idempotency-Key' and JWT Cookie
2. Gateway / Security:
   SecurityConfiguration.java -> JwtAuthenticationFilter validates JWT -> sets SecurityContext
3. Inbound Controller:
   BookingController.java: Receives CreateBookingRequest DTO, resolves CurrentActor
   └── Invokes Inbound Port: CreateBookingUseCase.create(...)
4. Application Service:
   BookingService.java:
   ├── Checks idempotency via BookingRepositoryPort
   ├── Validates business invariants (dates, capacity)
   └── Saves aggregate via BookingRepositoryPort
5. Outbound Persistence Adapter:
   BookingPersistenceAdapter.java:
   ├── Maps domain Booking to BookingEntity
   └── Saves via JpaBookingRepository -> writes to PostgreSQL table 'bookings'
6. Asynchronous Side Effects:
   BookingService.java:
   ├── Publishes to RabbitMQ: RabbitNotificationPublisher -> exchange 'skillbridge.notifications'
   └── Publishes to Kafka: KafkaBookingEventPublisher -> topic 'booking-events'
7. Audit Consumer:
   audit-service/backend/.../KafkaAuditConsumer.java:
   └── Receives BusinessEvent<BookingCreatedPayload> -> writes to MongoDB 'audit_events'
```

---

## 3. Where Things Belong (Verified Repository Conventions)

| Artifact | Correct Location | Rules & Conventions |
|---|---|---|
| **Domain Model** | `backend/.../domain/model/` | Pure Java record or class. **Zero annotations** from Spring or Jakarta. |
| **Domain Policy** | `backend/.../domain/policy/` or `domain/service/` | Invariant rules (e.g. `BookingCancellationPolicy`). Pure Java. |
| **Inbound Port** | `backend/.../application/port/in/` | Java interface named `*UseCase.java` or command record `*Command.java`. |
| **Outbound Port** | `backend/.../application/port/out/` | Java interface named `*Port.java` or `*RepositoryPort.java`. |
| **Application Service** | `backend/.../application/service/` | Implements Inbound Port, orchestrates domain and calls Outbound Ports. `@Service`. |
| **REST Controller** | `backend/.../infrastructure/adapter/in/rest/` | `@RestController`, `@RequestMapping("/api/...")`. Depends ONLY on Inbound Ports. |
| **DTOs & Validations** | `backend/.../infrastructure/adapter/in/rest/dto/` | Immutable Java records with Jakarta Validation annotations (`@NotBlank`, `@NotNull`). |
| **JPA Entities & Repos**| `backend/.../infrastructure/adapter/out/persistence/` | Spring Data JPA `@Entity`, `JpaRepository`, and mapping persistence adapters. |
| **Database Migrations**| `backend/src/main/resources/db/migration/` | Versioned SQL scripts (`V8__feature_name.sql`). Immutable once merged. |
| **Messaging Adapters** | `backend/.../infrastructure/adapter/out/messaging/` | Implements `*EventPublisherPort`. Uses `KafkaTemplate` or `RabbitTemplate`. |
| **Frontend Component** | `frontend/src/app/features/` | Angular 20 standalone component using Tailwind CSS and Signals. |
| **Frontend Service**   | `frontend/src/app/core/` | Injected service using `HttpClient` and `apiBase()`. |

---

## 4. How to Add a New Feature Step-by-Step

### 4.1. Adding a New REST Endpoint
1. Define the domain model or method in `domain/model/`.
2. Define the Inbound Port interface in `application/port/in/` (e.g., `UpdateUserProfileUseCase.java`).
3. Implement the use case logic in `application/service/` (e.g., `UserProfileService.java`).
4. Define the request/response DTO records in `infrastructure/adapter/in/rest/dto/`.
5. Add the `@RestController` method in `infrastructure/adapter/in/rest/`, annotated with `@PreAuthorize` if restricted.
6. Verify route accessibility in `SecurityConfiguration.java`.
7. Write unit tests:
   - Use case test in `backend/src/test/java/.../application/service/`.
   - Controller security test in `backend/src/test/java/.../infrastructure/adapter/in/rest/`.

### 4.2. Adding a Database Change
1. Create the next Flyway script in `backend/src/main/resources/db/migration/` (e.g., `V8__add_user_bio.sql`).
2. Update the corresponding JPA entity in `infrastructure/adapter/out/persistence/entity/`.
3. Update the persistence adapter to map the new column to the domain model.

### 4.3. Adding a New Asynchronous Event
1. Define the event payload record in `application/port/out/event/` (e.g., `OfferingUpdatedPayload.java`).
2. Add the method to `AuditEventPublisherPort` or `BookingEventPublisherPort`.
3. Wrap in `BusinessEvent<T>` and publish to Kafka `booking-events` topic inside the adapter.

---

## 5. Architectural Decision Records (ADR) Process

### When is an ADR Required?
Create a new ADR in `docs/adr/` when making architectural changes such as:
- Introducing a new external datastore, broker, or cloud dependency.
- Altering the bounded context or extracting a microservice.
- Modifying authentication/authorization paradigms (e.g., migrating session tokens).
- Changing AI provider integrations or models.

### ADR Status Convention:
- `Proposed`: Created for review; not yet merged or approved.
- `Accepted`: Approved and implemented in codebase.
- `Superseded`: Replaced by a subsequent ADR.

---

## 6. Pull Request Requirements & Definition of Done (DoD)

Before opening a Pull Request:
1. **Compilation**: `mvn -o validate` (backend) and `npm run build` (frontend) must pass.
2. **Tests**:
   - All unit tests pass (`mvn test`).
   - If modifying DB or integration flows, verify against Testcontainers (`mvn verify`).
   - Frontend tests pass headlessly (`npm test -- --watch=false --browsers=ChromeHeadless`).
3. **No Tracked Binaries / Generated Files**:
   - Do not stage `target/`, `node_modules/`, `dist/`, or modified `public/env.js`.
4. **No Plaintext Secrets**:
   - Never commit passwords, API keys, or `.env` files.
5. **Documentation**:
   - Update relevant module documentation in `docs/MODULES/` if contracts or ports changed.
   - If introducing architectural changes, include an ADR in `docs/adr/`.
