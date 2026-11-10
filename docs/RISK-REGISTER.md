# Risk & Technical Debt Register — SkillBridge AI

This document serves as the canonical risk and technical debt register for the SkillBridge AI repository. It consolidates findings across Security, DevOps/Build, and Codebase Inventory audits.

---

## 1. Status & Severity Definitions

### Status Conventions
- **CONFIRMADO**: Reproduced and verified directly against source code, configuration, or execution logs.
- **HIPÓTESIS**: Plausible vulnerability or defect requiring deployment or runtime verification.
- **PENDIENTE DE VERIFICACIÓN**: Requires additional testing or environment configuration.
- **RESUELTO**: Fix verified by test execution.

### Severity Conventions
- **ALTA**: Critical security flaw, broken build/orchestration, or data integrity issue.
- **MEDIA**: Architectural defect, test suite coupling, or compliance/PCI scope risk.
- **BAJA / INFORMATIVA**: Minor hygiene issue, dead code, or documentation gap.

---

## 2. Security Risks

| ID | Title & Summary | Severity | Status | Evidence & Location | Owner | Mitigation Strategy | Verification Criteria |
|---|---|---|---|---|---|---|---|
| **SEC-001** | User enumeration via divergent HTTP status codes on login | MEDIA | CONFIRMADO | [AuthService.java#L55-L65](../backend/src/main/java/com/riwi/skillbridge/application/service/AuthService.java#L55-L65) | Unassigned | Return generic HTTP 401 for both unknown emails and bad passwords with constant-time check. | Integration test verifying identical response code and timing for valid vs invalid emails. |
| **SEC-002** | Weak default JWT secret hardcoded in `application.yml` | ALTA | MITIGADO EN CÓDIGO el 2026-11-09 (RS256, sin clave por defecto; sin commit ni verificar en despliegue) / HIPÓTESIS (despliegue) | [application.yml#L123](../backend/src/main/resources/application.yml#L123) | Unassigned | Enforce mandatory `${JWT_SECRET}` without default fallback in production. | Verify application fails to boot when `JWT_SECRET` is unset in prod profile. |
| **SEC-003** | **BOLA / IDOR in payment authorization (`/api/payments/authorize`)** | ALTA | CONFIRMADO | [PaymentController.java#L21-L35](../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/PaymentController.java#L21-L35) | Unassigned | Validate that `booking.customerId().equals(actor.id())` before authorizing payment. | Automated test asserting HTTP 403 when user A attempts to pay for user B's booking. |
| **SEC-004** | **Unauthenticated Audit Service**: No auth filter on port 8081 | ALTA | CONFIRMADO | [AuditController.java#L18-L50](../audit-service/backend/src/main/java/com/riwi/audit/controller/AuditController.java#L18-L50) | Unassigned | Add Spring Security starter to `audit-service/backend` and restrict routes to `ADMIN`. | Test asserting HTTP 401/403 for unauthenticated `GET /api/audit/events`. |
| **SEC-005** | Excessive Category Permissions: Provider can delete global categories | MEDIA | CONFIRMADO | [CategoryController.java#L61](../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/CategoryController.java#L61) | Unassigned | Change `@PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")` on DELETE to `hasRole('ADMIN')`. | Unit test verifying PROVIDER receives 403 on category deletion. |
| **SEC-006A** | Logout CSRF via `SameSite=None` cookie and ignored CSRF matcher | MEDIA | CONFIRMADO | [SecurityConfiguration.java#L74](../backend/src/main/java/com/riwi/skillbridge/infrastructure/config/SecurityConfiguration.java#L74) | Unassigned | Require CSRF token (`X-XSRF-TOKEN`) on `POST /api/auth/logout`. | Security test asserting 403 on logout without CSRF token. |
| **SEC-006B** | CSRF bypass hypothesis via Bearer vs Cookie token desynchronization | MEDIA | HIPÓTESIS | [JwtAuthenticationFilter.java#L55](../backend/src/main/java/com/riwi/skillbridge/infrastructure/security/JwtAuthenticationFilter.java#L55) | Unassigned | Prioritize cookie over header or enforce consistent header verification. | Browser PoC demonstrating CSRF execution with mixed credentials. |
| **SEC-007** | Catalog Exposure Inconsistency: `GET /api/categories` requires auth | BAJA | CONFIRMADO | [SecurityConfiguration.java#L87-L90](../backend/src/main/java/com/riwi/skillbridge/infrastructure/config/SecurityConfiguration.java#L87-L90) | Unassigned | Add `.requestMatchers(HttpMethod.GET, "/api/categories").permitAll()`. | Integration test verifying unauthenticated `GET /api/categories` returns 200 OK. |
| **SEC-008** | Lack of JWT Blacklisting / Revocation upon voluntary logout | MEDIA | CONFIRMADO | [JwtService.java#L44-L48](../backend/src/main/java/com/riwi/skillbridge/infrastructure/security/JwtService.java#L44-L48) | Unassigned | Store revoked token JTI or hash in Redis with TTL matching token expiration. | Automated test checking revoked token receives 401 before expiry. |
| **SEC-009** | Plaintext Card Data in Application DTO (`PaymentRequestDto`) | MEDIA | CONFIRMADO (PCI Scope) | [PaymentRequestDto.java#L6-L12](../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/dto/payment/PaymentRequestDto.java#L6-L12) | Unassigned | Deprecate PAN/CVV fields; tokenize entirely on frontend via Stripe Elements. | DTO inspect confirming absence of PAN/CVV attributes. |
| **SEC-010** | Absence of Rate Limiting on authentication and AI endpoints | MEDIA | CONFIRMADO | [AiController.java#L48-L72](../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/AiController.java#L48-L72) | Unassigned | Integrate Bucket4j backed by Redis on `/api/auth/**` and `/api/ai/**`. | Load test checking 429 Too Many Requests after threshold. |

---

## 3. DevOps, Build & Quality Assurance Risks

| ID | Title & Summary | Severity | Status | Evidence & Location | Owner | Mitigation Strategy | Verification Criteria |
|---|---|---|---|---|---|---|---|
| **QA-001** | Syntax error in `Makefile:12` ("missing separator") breaks `make` | ALTA | RESUELTO | [Makefile#L12](../Makefile#L12) | Unassigned | Corregida sangría con tabulador en receta `reset:`. | `make -n up` y `make ps` completan con exit code 0. |
| **QA-002** | Absence of Maven Wrapper (`mvnw` / `mvnw.cmd`) in repository | MEDIA | CONFIRMADO | Workspace root inspection | Unassigned | Generate and commit Maven Wrapper (`mvn wrapper:wrapper`). | `./mvnw -v` executes successfully. |
| **QA-003** | Monorepo without root parent POM or npm workspace aggregator | BAJA | CONFIRMADO | Root workspace inspection | Unassigned | Create parent aggregator POM or npm workspaces. | Single root build command triggers all submodule builds. |
| **QA-004** | **CI Pipeline completely omits `audit-service`** | ALTA | CONFIRMADO | [.github/workflows/ci.yml](../.github/workflows/ci.yml) | Unassigned | Add build, test, and docker jobs for `audit-service` in `ci.yml`. | GitHub Actions executes audit backend and frontend steps. |
| **QA-005** | Git tracks 53MB of compiled binaries in `audit-service/backend/target/` | MEDIA | CONFIRMADO | `git ls-files audit-service/backend/target` | Unassigned | Run `git rm -r --cached` on target directory and verify `.gitignore`. | `git status` shows clean tree after `mvn clean`. |
| **QA-006** | Testcontainers execution coupled to unit test phase (`mvn test`) | MEDIA | CONFIRMADO | [BookingPersistenceAdapterTest.java#L25](../backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/BookingPersistenceAdapterTest.java#L25) | Unassigned | Rename to `BookingPersistenceAdapterIT.java` to run only under `mvn verify`. | `mvn test` completes without contacting Docker daemon. |
| **QA-007** | Integration tests silenced with `@Disabled("Docker Bug Testcontainers")` | MEDIA | CONFIRMADO | [NotificationFlowIT.java#L56](../backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/in/messaging/NotificationFlowIT.java#L56), [JpaOfferingRepositoryTest.java#L16](../backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/JpaOfferingRepositoryTest.java#L16) | Unassigned | Resolve Testcontainers Docker environment issue and remove `@Disabled`. | Tests run and pass during `mvn verify`. |
| **QA-008** | Inconsistent frontend dependency install (`npm install` vs `npm ci`) | BAJA | CONFIRMADO | [frontend/Dockerfile#L4](../frontend/Dockerfile#L4) | Unassigned | Replace `npm install` with `npm ci` in `frontend/Dockerfile`. | Docker build uses exact package-lock versions. |
| **QA-009** | Phantom service documented in demo script (`DemandAnalyticsService`) | MEDIA | CONFIRMADO | [scripts/demo-commands.sh#L49](../scripts/demo-commands.sh#L49) | Unassigned | Remove phantom references from demo scripts. | `scripts/demo-commands.sh` runs without masking errors (`|| true`). |
| **QA-010** | Missing linting and code quality gates in CI pipeline | BAJA | CONFIRMADO | [frontend/package.json](../frontend/package.json), `.github/workflows/ci.yml` | Unassigned | Add ESLint and Spotless/Checkstyle plugins and CI steps. | PR checks fail if code style is violated. |
| **QA-011** | Frontend build mutates tracked git file (`public/env.js`) | BAJA | CONFIRMADO | [generate-env.mjs#L17](../frontend/scripts/generate-env.mjs#L17) | Unassigned | Add `public/env.js` to `.gitignore`; keep `public/env.template.js`. | `npm run build` leaves `git status --short` clean. |
| **QA-012** | Missing integration tests for Kafka and Redis | MEDIA | CONFIRMADO | [BookingCancellationIT.java#L76](../backend/src/test/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/BookingCancellationIT.java#L76) | Unassigned | Implement integration tests with `KafkaContainer` and Redis Testcontainer. | Automated test verifies message published to Kafka and cached in Redis. |

---

## 4. Codebase Inventory Tool Risks

| ID | Title & Summary | Severity | Status | Evidence & Location | Owner | Mitigation Strategy | Verification Criteria |
|---|---|---|---|---|---|---|---|
| **AUD-INV-001**| Exclusion of `out/` dirs hid `adapter/out` and `port/out` | MEDIA | RESUELTO | [scripts/repo_audit.py#L22](../scripts/repo_audit.py#L22) | Unassigned | Filter only `./out` at root rather than substrings matching `out`. | `05-inventory-fix.md`: Todos los 78 archivos hexagonales de salida indexados. |
| **AUD-INV-002**| Lack of MongoDB and Redis classification in persistence | BAJA | RESUELTO | [scripts/repo_audit.py#L48](../scripts/repo_audit.py#L48) | Unassigned | Add `MongoRepository` and `RedisTemplate` patterns. | Persistencia no relacional detectada en `01-java-index.md`. |
| **AUD-INV-003**| Lack of categories for Kafka/RabbitMQ and Feign clients | BAJA | RESUELTO | [scripts/repo_audit.py#L38-L56](../scripts/repo_audit.py#L38-L56) | Unassigned | Add regex patterns for `@KafkaListener`, `@RabbitListener`, `@FeignClient`. | Mensajería y clientes Feign clasificados en `00-summary.md`. |
| **AUD-INV-004**| Omission of programmatic initializers (`AuditMongoInitializer`) | BAJA | RESUELTO | [scripts/repo_audit.py#L256](../scripts/repo_audit.py#L256) | Unassigned | Extend migration detector to match `*Initializer.java`. | `AuditMongoInitializer` y `AdminSeeder` indexados. |
| **AUD-INV-005**| Historical omission of 78 ports/adapters and 11 tests in indexes | MEDIA | RESUELTO | `docs/audit/evidence/01-inventory-validation.md` | Unassigned | Regenerate indexes using updated `scripts/repo_audit.py`. | Conteos generados coinciden exactamente con `git ls-files` (229 Java files). |
| **AUD-INV-006**| Java file count discrepancy (229 tracked files vs 150 reported) | INFORMATIVA | CONFIRMADO (Ajustado) | `git ls-files -- '*.java' \| wc -l` | Unassigned | Record exact 229 Java files in baseline inventory. | Verified output of `git ls-files`. |
| **AUD-INV-007**| False positive capturing `JpaUserRepository` in security index | BAJA | CONFIRMADO | `01-java-index.md:214` | Unassigned | Fix regex parsing so parameter types are not treated as class declarations. | `01-java-index.md` contains only declared classes. |
