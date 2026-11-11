---
name: verify
description: Run targeted verification and validation of changes in SkillBridge AI with minimal context consumption. Validates backend, frontend, audit-service, and docs against docs/TESTING.md.
user-invocable: true
disable-model-invocation: true
---

# SkillBridge AI Verification Skill (`/verify`)

Targeted, evidence-based verification runner for SkillBridge AI changes with minimal context consumption.

This skill discovers workspace changes, consults `docs/TESTING.md` and module architecture guides at runtime, executes only the pertinent checks, and provides an honest, evidence-based report.

---

## Operating Invariants & Strict Boundaries

1. **Verify, Do Not Mutate**:
   - Do NOT modify functional code, configuration, or documentation to make tests pass.
   - Do NOT run auto-formatters (`prettier`, `spotless`, etc.) or linter fixers.
   - Do NOT install dependencies (`npm install`, `mvn dependency:resolve`, system packages).
   - Do NOT touch `git add`, `git commit`, `git reset`, `git checkout`, `git stash`, or switch branches.
   - Do NOT modify `docs/RISK-REGISTER.md`, `docs/audit/findings.md`, `docs/audit/evidence/`, or `scripts/repo_audit.py`.
2. **Context Efficiency**:
   - Run only scoped checks matching the touched files; never run full project suites unnecessarily.
   - Keep command outputs compact. Capture exit codes, failure lines, and concise summaries rather than dumping full log streams into context.
3. **Evidence-Based Honesty**:
   - Never claim a test passes unless executed in this session and confirmed by exit code 0.
   - If a test was not executed, state explicitly: `NOT RUN — Not executed in this session`.
   - Never rely on another agent's assertions or build configuration files to claim success.

---

## Execution Workflow

### Step 1: Discover What Changed

Inspect the git working tree to identify all modified, staged, unstaged, and untracked files:

```bash
git status --short
git diff --cached --stat
git diff --stat
```

> [!IMPORTANT]
> Do NOT assume `git diff --name-only` contains all changes. Untracked files (`??`) and staged additions must be accounted for.

Categorize all touched paths into scopes:

| Scope | Path Pattern | Description |
|---|---|---|
| **Core Backend** | `backend/src/**`, `backend/pom.xml` | Spring Boot 3.5.6, Java 21, Hexagonal domain/application/infra |
| **Client Frontend** | `frontend/src/**`, `frontend/package.json` | Angular 20, Signals, Tailwind CSS v4 |
| **Audit Backend** | `audit-service/backend/**` | Spring Boot 3.5.6 + MongoDB 7 + Kafka consumer |
| **Audit Frontend** | `audit-service/frontend/**` | Angular 20 admin audit UI |
| **Documentation** | `docs/**`, `*.md` | Markdown architecture, guides, checklists |
| **Agent & Script Tooling** | `scripts/**`, `.claude/**`, `.agents/**` | Shell scripts, hooks, agent definitions |
| **Cross-Cutting / Infra** | `docker-compose*.yml`, `Makefile`, root config | Environment, container, orchestration specs |

Determine the **minimal validation scope**. If only frontend files changed, do not run backend validations. If only documentation changed, do not execute compilation commands.

---

### Step 2: Consult the Source of Truth

Read `docs/TESTING.md` at runtime:

```bash
# Read live testing taxonomy and matrix
cat docs/TESTING.md
```

Consult relevant module documentation when architectural or domain boundaries are modified:
- Core Backend: `docs/MODULES/backend.md`, `docs/architecture/overview.md`
- Client Frontend: `docs/MODULES/frontend.md`
- Audit Microservice: `docs/MODULES/audit-service.md`
- External Integrations & Events: `docs/INTEGRATIONS.md`
- Security & RBAC: `docs/SECURITY-MODEL.md`

Do NOT use rigid or stale command lists. Concrete test names and commands must match the current build files and live documentation.

---

### Step 3: Run Pertinent Checks (With Safety Gates)

#### Safety Gate: Docker & External Service Authorization
Before launching any test that requires Docker, containers, brokers, external APIs, or data alteration:
- Identify if the test needs Docker (`postgres:17-alpine`, `rabbitmq:4-management-alpine`, Testcontainers) or external credentials (Stripe, OpenAI, Gemini).
- **Known Docker dependency in `mvn test`**: `BookingPersistenceAdapterTest` requires a Docker daemon. If Docker is absent or unauthorized, do not run plain `mvn test` without filtering.
- **Rule**: Stop and explain the requirement to the user before running Docker-dependent or externally coupled tests. If unauthorized or unavailable, mark them as `NOT RUN — Not executed in this session`.

#### Scoped Commands

1. **Core Backend (`backend/`)**:
   - Fast syntax/compilation check:
     ```bash
     mvn -o validate -f backend/pom.xml
     ```
   - Pure domain & application unit tests (No Docker required):
     ```bash
     mvn test -f backend/pom.xml -Dtest=*Test -Dexclude=BookingPersistenceAdapterTest
     ```
   - Specific targeted unit test (e.g. for domain model changes):
     ```bash
     mvn test -f backend/pom.xml -Dtest=<TargetTestClass>
     ```
   - Slice / Persistence test (`BookingPersistenceAdapterTest`) or Integration tests (`mvn verify`):
     *Requires Docker daemon and user confirmation.*

2. **Client Frontend (`frontend/`)**:
   - Unit tests (Karma + Jasmine headless):
     ```bash
     cd frontend && npm test -- --watch=false --browsers=ChromeHeadless
     ```
   - Compilation & build validation:
     ```bash
     cd frontend && npm run build
     ```

3. **Audit Microservice (`audit-service/`)**:
   - Audit Backend (independent build):
     ```bash
     cd audit-service/backend && mvn test
     ```
   - Audit Frontend (independent build):
     ```bash
     cd audit-service/frontend && npm test -- --watch=false --browsers=ChromeHeadless
     ```

4. **Documentation & Markdown**:
   - Verify that relative file links point to existing files.
   - Verify table formatting and standard markdown rendering.
   - Confirm protected audit documents were not altered (`docs/audit/findings.md`, `docs/audit/evidence/`).

5. **Agent & Script Tooling**:
   - Run relevant offline test scripts:
     ```bash
     bash scripts/tests/protect-paths.test.sh
     bash scripts/tests/agent-parity.test.sh
     bash scripts/tests/skill-parity.test.sh
     ```

---

### Step 4: Record Results Honestly

Generate a concise report following this exact structure:

```markdown
# Verification Report

## 1. Scope & Changes Detected
- **Files Touched**: `<list of files with status [M/A/??/D]>`
- **Active Scopes**: `<Backend | Frontend | Audit-Backend | Audit-Frontend | Docs | Tooling>`
- **Validation Strategy**: `<Summary of targeted minimal check plan>`

## 2. Executed Checks
| Scope | Command | Working Directory | Exit Code | Result | Notes |
|---|---|---|---|---|---|
| ... | `...` | `...` | `0` | PASS | ... |

## 3. Checks Skipped / Not Executed
- `<Check/Suite Name>`: `NOT RUN — Not executed in this session` (Reason: `<Docker required / Out of scope / External credentials required>`)

## 4. Known Documented Disabled Tests
- `JpaOfferingRepositoryTest`: `@Disabled("Docker Bug Testcontainers")` — Silences PostgreSQL repository verification.
- `NotificationFlowIT`: `@Disabled("Docker Bug Testcontainers")` — Silences RabbitMQ publisher/consumer/retry/DLQ integration verification.

## 5. Architectural & Quality Findings
- **Hexagonal Boundary Conformance**: `[VERIFIED]` / `[DEFECT]` (Confirm `domain/` and `application/` have zero infrastructure imports).
- **Failures & Errors**: `<Detailed excerpt of any failures, or 'None'>`
- **Risks & Recommended Actions**: `<Next steps or warnings>`
```

---

### Step 5: Post-Verification Limits

- Report findings to the user with actionable next steps.
- Do NOT perform git commits or staging.
- Do NOT modify codebase files or apply automatic fixes.
