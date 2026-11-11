# Agent registry

Single source of truth for custom agents that exist on **both** platforms. A row exists only for an
agent whose definition really exists in `.claude/agents/`; nothing here is a placeholder.
`scripts/tests/agent-parity.test.sh` fails when a Claude agent has no Antigravity counterpart (or the
reverse), when names differ, when a row is missing here, or when shared invariants drift.

| Agent | Claude Code definition | Antigravity definition | Function | Platform differences | Verification status |
|---|---|---|---|---|---|
| `agy-worktree-orchestrator` | `.claude/agents/agy-worktree-orchestrator.md` | `.agents/agents/agy-worktree-orchestrator/agent.md` | Delegates one scoped task to `agy -p` in an isolated Git worktree (Claude), or performs that task as the worker child and writes the evidence report (Antigravity). Never integrates or deletes work. | **Claude:** orchestrator role; tools restricted by frontmatter `tools: Read, Grep, Glob, Bash` (no Edit/Write). **Antigravity:** frontmatter has only `name` + `description` (no tool-allowlist field exists), so limits are binding prose rules; has a *worker mode* guarded by `AGY_WORKTREE_CHILD` and may not remove the worktree it runs in. | Parity: verified by `scripts/tests/agent-parity.test.sh`. Antigravity discovery: **verified** with `agy agents` (lists `agy-worktree-orchestrator`, agy 1.4.4). Script and guardrails: verified offline with a fake `agy` (`scripts/tests/agent-worktree-agy.test.sh`). Claude discovery of the subagent and a real end-to-end `agy -p` run: **not executed** in the session that created it. |

## Rules for adding an agent
1. Create `.claude/agents/<name>.md` and `.agents/agents/<name>/agent.md` with the same `name`.
2. Use each platform's native metadata; do not copy `tools:` semantics across platforms.
3. Both files must keep the shared invariants checked by the parity test (protected paths, no commits,
   recursion guard where the agent can launch `agy`).
4. Add a row above and run `bash scripts/tests/agent-parity.test.sh`.

## Spec-Driven Development Tooling

Paired Spec-Driven Development (SDD) slash commands and workflows are established across both platforms:
- **Claude Code commands**: Configured in `.claude/commands/` (`speckit.constitution`, `speckit.specify`, `speckit.clarify`, `speckit.plan`, `speckit.tasks`, `speckit.analyze`, `speckit.implement`, `speckit.checklist`).
- **Antigravity workflows**: Configured in `.agents/workflows/` (`speckit.constitution`, `speckit.specify`, `speckit.clarify`, `speckit.plan`, `speckit.tasks`, `speckit.analyze`, `speckit.implement`, `speckit.checklist`).
- **Shared Assets & Governance**: Centralized in `.specify/` (`memory/constitution.md`, `templates/`, `scripts/bash/`).
- **Orchestration Bridge**: Connects tasks from `specs/<NNN>-<slug>/tasks.md` to `scripts/agent-worktree-agy.sh` via [SDD-WORKFLOW.md](SDD-WORKFLOW.md).
