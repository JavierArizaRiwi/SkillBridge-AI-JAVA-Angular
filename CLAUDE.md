@AGENTS.md

# CLAUDE.md — SkillBridge AI Project Context

SkillBridge AI is a service booking and talent matching platform featuring multimodal AI recommendations, event-driven messaging, and audit logging.

> [!IMPORTANT]
> General operating principles, evidence-based reasoning, non-destructive boundaries, and test categories are defined in `@AGENTS.md` (loaded above). Below are Claude Code-specific directives, architecture invariants, execution commands, and the on-demand context routing table.

## System Modules
| Module | Location | Primary Technologies | Responsibilities |
|---|---|---|---|
| **Backend API** | `backend/` | Java 21, Spring Boot 3.5.6, PostgreSQL, Redis, RabbitMQ, Kafka | Core business logic, auth, booking lifecycle, AI recommendations, payments. |
| **Frontend Web** | `frontend/` | Angular 20, TypeScript 5.9, Tailwind CSS v4, Signals | Customer & provider portal, booking UI, multimodal AI chat, Stripe modal. |
| **Audit Service** | `audit-service/` | Spring Boot 3.5.6 + MongoDB 7 (`backend/`), Angular 20 (`frontend/`) | Independent Kafka consumer tracking persistent domain events and admin audit UI. |

## Essential Commands
```bash
# Full local environment
docker compose up --build -d

# Backend (run from backend/)
mvn test                               # Unit & slice tests
mvn verify                             # Integration tests (requires Docker daemon)
mvn -o validate                        # Fast pre-commit validation

# Frontend (run from frontend/)
npm test -- --watch=false              # Unit tests (Karma/Jasmine ChromeHeadless)
npm run build                          # Production build

# Audit Service (run from audit-service/backend or frontend)
cd audit-service/backend && mvn test   # Audit backend tests
cd audit-service/frontend && npm test -- --watch=false
```

## Backend Architecture Invariants (Hexagonal Extensions)
*(Complements `AGENTS.md` Section 1)*
- **Controller Rule**: REST controllers (`infrastructure/adapter/in/rest/`) implement inbound adapters and call Inbound Ports (`application/port/in/`), never repositories or domain entities directly.
- **Persistence Boundary**: Outbound adapters (`infrastructure/adapter/out/persistence/`) implement Outbound Ports (`application/port/out/`) using Spring Data JPA entities, converting between JPA entities and pure domain models.
- **Observability & Traceability**: Maintain correlation IDs across HTTP headers (`X-Correlation-Id`), SLF4J MDC, and asynchronous event envelopes (`BusinessEvent`).

## Security & Integration Invariants
- **Authentication**: JWT via HttpOnly cookie `access_token` (with Bearer header fallback). Stateless Spring Security session.
- **CSRF**: Enforced via `CookieCsrfTokenRepository` (`X-XSRF-TOKEN`) on cookie-authenticated mutating requests.
- **AI Integration**: Abstracted behind `AiRecommendationPort`, `VisionPort`, `SpeechToTextPort`, and `TextToSpeechPort`.
- **Payment Safety**: Never process raw PAN/CVV through custom persistence; use Stripe tokenization. Validate actor booking ownership.

## Context Routing Table (Where to Look)
*Do NOT load the whole codebase or read documentation globally. Inspect only the documents and paths relevant to the current task:*

| Task Type | First Document to Read | Primary Directory / Files to Inspect |
|---|---|---|
| REST endpoints & controllers | `docs/CONTRIBUTING.md`, `docs/MODULES/backend.md` | `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/`, `backend/src/main/java/com/riwi/skillbridge/application/port/in/` |
| Application services & use cases | `docs/architecture/overview.md`, `docs/CONTRIBUTING.md` | `backend/src/main/java/com/riwi/skillbridge/application/service/`, `backend/src/main/java/com/riwi/skillbridge/application/port/` |
| Pure domain logic, models, rules | `docs/architecture/overview.md` | `backend/src/main/java/com/riwi/skillbridge/domain/` |
| Persistence, JPA & Flyway migrations | `docs/MODULES/backend.md` | `backend/src/main/resources/db/migration/`, `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/` |
| AI, Multimodal, Audio, Vision | `docs/INTEGRATIONS.md`, `docs/adr/ADR-001-AI-PROVIDER-ABSTRACTION.md` | `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/ai/`, `backend/src/main/java/com/riwi/skillbridge/application/port/out/` |
| Asynchronous events (Kafka/Rabbit) | `docs/INTEGRATIONS.md`, `docs/adr/0003-kafka-event-topic-unification.md` | `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/`, `backend/src/main/java/com/riwi/skillbridge/infrastructure/config/` |
| Payments & Stripe integration | `docs/INTEGRATIONS.md`, `docs/MODULES/backend.md` | `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/payment/`, `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/` |
| Frontend feature views & routing | `docs/MODULES/frontend.md` | `frontend/src/app/features/`, `frontend/src/app/guards/` |
| Frontend core, API & runtime config | `docs/MODULES/frontend.md` | `frontend/src/app/core/`, `frontend/public/env.js` |
| Audit service & event streaming | `docs/MODULES/audit-service.md` | `audit-service/backend/`, `audit-service/frontend/` |
| Security, Auth, RBAC, CSRF | `docs/SECURITY-MODEL.md` | `backend/src/main/java/com/riwi/skillbridge/infrastructure/config/SecurityConfiguration.java`, `backend/src/main/java/com/riwi/skillbridge/infrastructure/security/` |
| Test suite execution & debugging | `docs/TESTING.md` | `backend/src/test/`, `frontend/src/app/**/*.spec.ts`, `audit-service/backend/src/test/` |
| Deployment & container config | `docs/DEPLOYMENT-CHECKLIST.md`, `docs/guides/free-tier-deployment.md` | `docker-compose.yml`, `backend/Dockerfile`, `frontend/Dockerfile` |
| Known risks & open defects | `docs/RISK-REGISTER.md` | `docs/audit/findings.md` *(read-only)*, `docs/audit/evidence/` *(read-only)* |
| Delegating to worktrees & agents | `docs/agents/WORKTREE-ORCHESTRATION.md` | `docs/agents/REGISTRY.md`, `docs/agents/GUARDRAILS.md`, `scripts/agent-worktree-agy.sh` |
| Spec-Driven Development (clasificación, specs, plans, tasks) | `docs/agents/SDD-HYBRID-GUIDE.md`, `docs/agents/SDD-WORKFLOW.md` | `specs/`, `.specify/` |

## Spec-Driven Development (SDD)
- **Clasificación primero**: Sigue `docs/agents/SDD-HYBRID-GUIDE.md`. Los cambios pequeños usan evidencia concisa en issue/PR; los medianos requieren una spec de comportamiento; los grandes/alto riesgo o delegados requieren el ciclo completo.
- **GitHub Spec Kit Slash Commands**: Execute the SDD workflow using slash commands:
  - `/speckit.constitution`: Consult or amend system governance (`.specify/memory/constitution.md`).
  - `/speckit.specify [description]`: Generate or refine functional specification in `specs/<NNN>-<slug>/spec.md`.
  - `/speckit.clarify`: Clarify ambiguous requirements and identify edge cases.
  - `/speckit.plan`: Formulate the technical design and architectural plan in `specs/<NNN>-<slug>/plan.md`.
  - `/speckit.tasks`: Create dependency-ordered, independently verifiable tasks in `specs/<NNN>-<slug>/tasks.md`.
  - `/speckit.analyze`: Cross-check consistency between spec, plan, and tasks.
  - `/speckit.implement`: Implement tasks phase-by-phase when a task list exists.
  - `/speckit.checklist`: Review quality checklists and non-functional requirements.
- **Worktree Delegation**: Delegating to `agy` worktrees requires a reviewed `spec.md`, `plan.md`, and `tasks.md`; follow [docs/agents/SDD-WORKFLOW.md](docs/agents/SDD-WORKFLOW.md). Never modify parallel feature folders (`specs/0*`).


## Claude Code Execution Directives
1. **Targeted Context Loading**: Never read entire directories recursively or load the full codebase. Always consult the Context Routing Table above and read only the primary files relevant to your task.
2. **On-Demand Docs**: Do not preload documentation from `docs/` unless needed for the specific task at hand.
3. **Architecture Changes**: When modifying domain boundaries, ports, or adding messaging topics, update the corresponding documentation under `docs/` and add/update an ADR in `docs/adr/`.
4. **Follow AGENTS.md**: Adhere strictly to the non-destructive editing policy, protected files list, and evidence-based verification rules imported via `@AGENTS.md`.
