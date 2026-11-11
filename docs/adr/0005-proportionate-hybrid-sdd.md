# ADR-0005: Proportionate Hybrid Spec-Driven Development

## Status

Accepted

## Date

2026-10-22

## Context

ADR-0004 established GitHub Spec Kit as the project SDD workflow. SkillBridge AI is an existing multi-module system with useful architectural constraints, but not every maintenance change has the risk or uncertainty that justifies creating and maintaining a complete set of specification, plan, design artifacts, and tasks.

The repository currently contains behavior specifications for planned features, while the operational workflow and plan template can generate several derived artifacts. Applying that full workflow indiscriminately would create documentation debt and consume agent context without improving a localized, low-risk change.

## Decision

Adopt a hybrid SDD policy described in `docs/agents/SDD-HYBRID-GUIDE.md`:

1. Classify every change before implementation as small, medium, or large/high-risk.
2. Keep the constitution and canonical architectural/module/security documentation alive when their respective contracts change.
3. Keep a behavior specification alive for medium and large changes while the feature is being delivered. For later changed intent, create a linked successor feature rather than rewriting completed delivery history.
4. Treat completed plans, task lists, research notes, and requirement-quality checklists as historical delivery records unless an active feature still needs them.
5. Require the complete `spec -> plan -> tasks -> analyze -> implement` workflow for large/high-risk work and for any work delegated through the worktree orchestrator.
6. Do not require `analyze` or a feature directory for small changes. Do not generate optional design artifacts unless they capture information that code, tests, configuration, or canonical documentation cannot express adequately.

`converge` is not yet part of the repository's vendored Spec Kit commands. It is therefore not a required local gate until a reviewed Spec Kit update deliberately adds it.

## Consequences

- **Positive**: High-risk and parallel agent work retains reviewable requirements, traceability, and bounded scopes.
- **Positive**: Routine fixes retain concise acceptance evidence without ceremonial artifacts.
- **Positive**: The team has a clear ownership model: behavior and canonical contracts stay current; implementation planning artifacts remain historical.
- **Trade-off**: Classification needs reviewer judgement. The guide uses risk and change characteristics, not lines of code, to keep decisions consistent.
- **Trade-off**: A later Spec Kit upgrade must be reviewed against the local commands, templates, and Claude/Antigravity integration before enabling `converge`.
