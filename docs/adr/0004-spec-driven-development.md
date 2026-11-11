# ADR-0004: Adoption of Spec-Driven Development (SDD) with GitHub Spec Kit

## Status
Accepted

## Date
2026-10-22

## Context
SkillBridge AI is a complex, multi-module distributed system comprising a Java 21 Spring Boot core backend (hexagonal architecture), an Angular 20 reactive frontend (signals, Tailwind CSS v4), and an independent MongoDB-backed audit microservice. Architectural boundaries, pure domain isolation, multimodal AI integration ports, and zero-PAN payment compliance represent non-negotiable invariants.

As development velocity accelerates with multiple coding agents (Claude Code, Antigravity CLI/IDE) working in parallel, an ad-hoc implementation workflow presents several critical risks:
1. **Architectural Drift**: Agents implementing features without formal specifications frequently leak framework dependencies into pure domain models or bypass port/adapter abstractions.
2. **Hallucinated or Unbounded Scopes**: Unstructured instructions lead to broad, uncontrolled diffs, regressions in sensitive paths, or premature commitments without adequate test evidence.
3. **Collision in Parallel Work**: Multiple agents working across the repository simultaneously can conflict without standardized feature naming and isolated execution spaces.
4. **Lack of Traceability**: Requirements from GitHub User Stories (HUs) become decoupled from the actual code modifications and tests delivered.

## Decision
Adopt **GitHub Spec Kit** and the **Spec-Driven Development (SDD)** lifecycle as the official standard for specifying, planning, and implementing features in SkillBridge AI:

1. **Structured SDD Lifecycle**:
   - `constitution`: Establishes system-wide invariants in `.specify/memory/constitution.md` (Version 1.0.0).
   - `specify`: Generates structured functional specifications with prioritized user stories (P1/P2/P3) and Given-When-Then scenarios.
   - `clarify`: Detects edge cases, ambiguities, and missing non-functional requirements.
   - `plan`: Formulates the technical architecture, verifying domain purity, ports/adapters, and data models against the constitution.
   - `tasks`: Decomposes the approved plan into dependency-ordered, independently verifiable tasks.
   - `analyze`: Automatically analyzes consistency across spec, plan, and tasks.
   - `implement`: Implements tasks phase-by-phase.
   - `checklist`: Evaluates review-owned quality gates.

2. **Feature Directory & Branching Conventions**:
   - All feature artifacts are isolated in `specs/<NNN>-<slug>/`, where `<NNN>` is a 3-digit zero-padded number matching the GitHub User Story / Issue (`HU-25` → `025-ai-recommendations`).
   - Feature branches follow `feature/<NNN>-<slug>`.
   - Every `spec.md` explicitly references its originating GitHub Issue.

3. **Dual Platform Parity (Claude Code & Antigravity)**:
   - Provide identical slash commands for both platforms:
     - Claude Code: `.claude/commands/speckit.<command>.md`
     - Antigravity (agy): `.agents/workflows/speckit.<command>.md`
   - Maintain shared infrastructure, templates, and scripts in `.specify/`.

4. **Integration with Worktree Orchestration**:
   - Bridge SDD with `scripts/agent-worktree-agy.sh` as documented in `docs/agents/SDD-WORKFLOW.md`.
   - Human approval is mandatory for functional requirements (`spec.md`) and technical design (`plan.md`).
   - Implementation tasks from `tasks.md` are delegated to Antigravity worker agents running in isolated Git worktrees with explicit `--scope` boundaries and committed `--context specs/<NNN>-*/plan.md`.

## Consequences
- **Positive**: Eliminates architectural drift by enforcing automated and human constitution checks prior to any code generation.
- **Positive**: Provides end-to-end auditability and traceability from GitHub User Stories down to individual test executions and patches.
- **Positive**: Enables safe parallel development: multiple agents can simultaneously specify, plan, and implement features in independent `specs/<NNN>-<slug>/` directories and worktree branches without conflict.
- **Positive**: Full operational symmetry between Claude Code and Antigravity tooling.
- **Trade-off / Mitigation**: Requires upfront specification and planning before writing code. Mitigated by automated Spec Kit commands (`/speckit.specify`, `/speckit.plan`) that rapidly scaffold standard templates.
- **Trade-off / Mitigation**: Worktree workers can only read files committed to `--base`. Mitigated by standardizing feature branch commits for `spec.md` and `plan.md` before delegating implementation tasks.
