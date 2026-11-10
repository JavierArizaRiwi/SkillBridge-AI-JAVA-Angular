# AGENTS.md — General Coding Agent Instructions

This repository contains **SkillBridge AI**, a multi-module system consisting of a Java 21 Spring Boot core API, an Angular 20 web frontend, and an independent event-driven Audit microservice.

All coding agents (including Claude Code, Antigravity, OpenAI Codex, and Cursor) must adhere to these operating principles.

---

## 1. Operating Conventions & Standards

- **Hexagonal Architecture**:
  - Domain (`com.riwi.skillbridge.domain`) is pure Java. No framework imports (`org.springframework.*`, `jakarta.persistence.*`, etc.).
  - Application layer (`com.riwi.skillbridge.application`) defines Inbound Ports (`port/in`), Outbound Ports (`port/out`), and Use Cases (`service`).
  - Infrastructure layer (`com.riwi.skillbridge.infrastructure`) hosts Controllers, DB Adapters, Messaging Consumers/Publishers, Security, and Configuration.
- **Frontend Reactive Conventions**:
  - Angular 20 standalone components using Signals (`signal`, `computed`).
  - Tailwind CSS v4 for utility-first styling.
  - Environment variables injected dynamically at runtime via `public/env.js` and `apiBase()` ([frontend/src/app/core/api.ts](frontend/src/app/core/api.ts)).
- **Audit Microservice Separation**:
  - `audit-service/` is decoupled from `backend/`. It contains its own Maven build and Angular app. Do not introduce compile-time dependencies between `backend/` and `audit-service/`.

---

## 2. Evidence-Based Reasoning Requirements

1. **Verify Before Asserting**:
   - Check real file contents and line numbers before claiming an endpoint, bean, or test exists.
   - Do not trust generated indexes blindly (historical scripts had known omissions such as filtering out `adapter/out` and `port/out`).
2. **Explicit Verification States**:
   - Clearly mark architectural assertions as:
     - `[VERIFIED]`: Confirmed against current codebase and configuration.
     - `[INFERRED]`: Plausible deduction based on surrounding code, not explicitly proven.
     - `[UNKNOWN]`: Unconfirmed or requires human clarification.
3. **Test Results**:
   - Never claim a test passes unless execution output in this session confirms it. If tests were not run, state: *"Not executed in this session"*.

---

## 3. Scope & Change Boundaries

- **Strict Non-Destructive Policy**:
  - Never run `git reset`, `git checkout -- .`, `git stash`, or branch switching unless requested.
  - Never stage or commit files automatically.
  - Never touch secrets, credentials, API keys, or `.env` files.
- **Protected Files**:
  - Do not alter `docs/audit/findings.md` or historical evidence files in `docs/audit/evidence/` (except generating the migration map `06-documentation-migration.md` when assigned).
  - Do not modify `scripts/repo_audit.py` or `docs/audit/generated/` unless explicitly instructed.
- **Preserve User Changes**:
  - Any unstaged changes present when your task starts must remain intact.

---

## 4. Testing Expectations

- **Test Categories**:
  - **Unit Tests**: Pure JUnit 5/Mockito. Fast, run without Docker (`mvn test`).
  - **Slice / Persistence Tests**: `@DataJpaTest` using Testcontainers PostgreSQL. Requires a running Docker daemon. (Note: `BookingPersistenceAdapterTest` is run under `mvn test` and requires Docker).
  - **Integration Tests**: Suffix `*IT.java` run via `mvn verify`. Note: `NotificationFlowIT` and `JpaOfferingRepositoryTest` currently have `@Disabled("Docker Bug Testcontainers")`.
  - **Frontend Tests**: Jasmine/Karma executed headlessly: `npm test -- --watch=false --browsers=ChromeHeadless`.
- **Pre-Commit Verification**:
  - Ensure changes do not break existing compilation (`mvn -o validate` or `ng build`).

---

## 5. Documentation Selection & Navigation

Before beginning any task, select the relevant specialized documentation:

- **Quick Start & Command Routing**: See [CLAUDE.md](CLAUDE.md).
- **Product Overview & Workflows**: See [docs/PROJECT_CONTEXT.md](docs/PROJECT_CONTEXT.md).
- **Architecture & Event Topologies**: See [docs/architecture/overview.md](docs/architecture/overview.md).
- **Module Deep Dives**:
  - Backend API: [docs/MODULES/backend.md](docs/MODULES/backend.md)
  - Frontend SPA: [docs/MODULES/frontend.md](docs/MODULES/frontend.md)
  - Audit Microservice: [docs/MODULES/audit-service.md](docs/MODULES/audit-service.md)
- **Operations, Testing & Integrations**:
  - Testing Guide: [docs/TESTING.md](docs/TESTING.md)
  - External Integrations: [docs/INTEGRATIONS.md](docs/INTEGRATIONS.md)
  - Security & RBAC: [docs/SECURITY-MODEL.md](docs/SECURITY-MODEL.md)
- **Contributing & Adding Features**: See [docs/CONTRIBUTING.md](docs/CONTRIBUTING.md).
- **Known Vulnerabilities & Technical Debt**: See [docs/RISK-REGISTER.md](docs/RISK-REGISTER.md).

---

## 6. Spec-Driven Development (SDD)

- **GitHub Spec Kit Workflow**: Features and architectural tasks follow the SDD lifecycle: `constitution` → `specify` → `clarify` → `plan` → `tasks` → `implement`.
- **System Constitution**: Stored in [.specify/memory/constitution.md](.specify/memory/constitution.md). All specifications, plans, and implementations must strictly satisfy its principles (hexagonal purity, AI port isolation, zero PAN/CVV, correlation tracing, and truthful verification).
- **Feature Directories**: Specifications reside in `specs/<NNN>-<slug>/` where `<NNN>` corresponds to the 3-digit GitHub User Story / Issue number (e.g. `specs/025-ai-matching/`, `specs/027-booking-rescheduling/`).
- **Parallel Work Isolation**: When working on an assigned feature, do not touch or modify other feature folders (`specs/0*`).
- **Worktree Orchestration Bridge**: Tasks from `tasks.md` are executed via `scripts/agent-worktree-agy.sh` in isolated worktrees. See [docs/agents/SDD-WORKFLOW.md](docs/agents/SDD-WORKFLOW.md).
