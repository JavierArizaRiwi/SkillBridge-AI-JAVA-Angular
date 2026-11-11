# Delegating tasks to Antigravity in an isolated Git worktree

Claude Code (or you) hands one **scoped** task to Antigravity CLI (`agy -p`). Antigravity works in a
separate Git worktree on its own branch, runs checks, and returns a report. Claude reviews it. Nothing is
integrated or deleted automatically.

Components: `scripts/agent-worktree-agy.sh` (the mechanism), `.claude/agents/agy-worktree-orchestrator.md`
and `.agents/agents/agy-worktree-orchestrator/agent.md` (the two agents, see [REGISTRY.md](REGISTRY.md)),
`.claude/hooks/protect-paths.sh` (see [GUARDRAILS.md](GUARDRAILS.md)).

## Script contract

```
scripts/agent-worktree-agy.sh run --task-id ID --base REF --spec FILE|- --scope PATH [--scope PATH ...] --non-interactive|--interactive
                                  [--finding ID ...] [--context PATH ...] [--forbid PATH ...]
                                  [--timeout SECONDS] [--worktrees-root DIR] [--sandbox] [--dry-run]
scripts/agent-worktree-agy.sh mark-reviewed --run-id RUN_ID [--worktrees-root DIR]
scripts/agent-worktree-agy.sh cleanup       --run-id RUN_ID [--worktrees-root DIR]
```

| Rule | Behavior |
|---|---|
| Task id | `[A-Za-z0-9][A-Za-z0-9_-]{0,39}` — no dots, slashes, spaces, leading dash. |
| Base | Mandatory, explicit, resolved to a commit. The worktree contains **only committed** content. |
| Scope | Mandatory repo-relative files/dirs; rejects `.`, absolute, `..`, globs, whole top-level directories. Must exist in the base (or its parent dir must). |
| Spec | Markdown ≤ 48 KiB (file or `-` for stdin) with a heading containing "Acceptance" or "Criterios". |
| Context docs | Must exist **in the base commit**. If they are only uncommitted in your workspace the run stops (exit 3) and asks for a proper base; the script never stashes or commits. |
| Location | Default `<repo-parent>/<repo-name>-agent-worktrees/<run-id>/worktree` (sibling, never inside the repo). Branch `agent/<task-id>-<UTC timestamp>`. Path and branch must not already exist. |
| Dirty workspace | Reported (counts + warning), recorded in `meta.env`, never copied, stashed or discarded. |
| agy call | `agy -p "<prompt>"` from inside the worktree; prompt is one argv element read from a file; stdin is `/dev/null`; env `AGY_WORKTREE_CHILD=1`, `AGY_PARENT_RUN_ID`. No permission-bypass flag is ever passed; `--sandbox` is opt-in. |
| Interactive mode | `--interactive` (instead of `--non-interactive`) opens a new terminal window (`AGY_TERMINAL`, else gnome-terminal/konsole/xterm) running `agy -i "<prompt>"` in the worktree, so you can watch and steer the agent. The script waits until agy exits or the window closes (`session_exit`), then collects the same evidence. The agent writes its report to `<run folder>/report.md`. `--timeout` does not apply. |
| Limits | `--timeout` (default 1800 s) with TERM then KILL after 30 s. Ctrl-C/TERM preserves everything. |
| Guardrail checks after the run | agent commits or moved HEAD/branch; changes outside scope; forbidden paths (`docs/audit/*`, `scripts/repo_audit.py`, extra `--forbid`); modified applied migrations; `.env` files; and whether the **original** workspace changed during the run. Violations are reported, never auto-reverted. |
| Cleanup | Separate subcommand, never implicit. Refuses unless: signed run metadata, registered `git worktree`, not the main/original workspace, status finished, review marked, clean tree (no untracked), no commits beyond base, branch used by one worktree. Uses `git worktree remove` **without force**; deletes the branch only by compare-and-delete when it still equals the base commit. Run folders and logs are always kept. |

Exit codes: `0` ok · `2` usage · `3` precondition failed · `4` agy failed · `5` timeout ·
`6` agy ok but guardrail violations · `7` cleanup refused (nothing removed) · `130` interrupted.

Run folder `<root>/<run-id>/`: `worktree/`, `prompt.md`, `task-spec.md`, `meta.env`, `stdout.log`,
`stderr.log`, `exit_code`, `report.md`, `changes.patch`, `changed-files.txt`, `diffstat.txt`,
`diff-check.txt`, `violations.txt`, `summary.md`.

## First real task — procedure

1. **Commit what the task needs.** The worktree only sees committed files. Today `CLAUDE.md`, `AGENTS.md`,
   `docs/TESTING.md` and the other new docs are untracked, so `--context docs/TESTING.md` will stop with exit 3
   until they are committed on the branch you use as `--base`. (Do that yourself; the tooling will not.)
2. **Write the spec** (example below) with explicit scope, findings and acceptance criteria.
3. **Dry run** and read the prompt:
   ```bash
   scripts/agent-worktree-agy.sh run --task-id SEC-005 --base develop \
     --scope backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/CategoryController.java \
     --finding SEC-005 --spec /path/spec.md --non-interactive --dry-run
   ```
4. **Trust the workspace in agy.** `~/.gemini/antigravity-cli/settings.json` lists `trustedWorkspaces`; the new
   `<repo>-agent-worktrees/...` path is not in it, so the script warns. Add the run path (or the root) there
   yourself if `agy -p` refuses or stalls. The script never edits agy settings.
5. **Run** (drop `--dry-run`), either directly or by asking Claude to use `agy-worktree-orchestrator`.
6. Claude/you read `summary.md`, `report.md`, `violations.txt`, `changes.patch`.

Spec template:
```markdown
# Task SEC-005 — restrict category deletion
Only ADMIN may delete global categories (RISK-REGISTER SEC-005).
## Acceptance criteria
- DELETE /api/categories/{id} returns 403 for a PROVIDER (new or updated unit test shows it).
- `mvn test` in backend/ passes for the touched classes.
- No change outside the authorized scope.
```

## Reviewing and integrating
```bash
RUN=<root>/<run-id>
cat  $RUN/summary.md $RUN/report.md $RUN/violations.txt
git -C $RUN/worktree status --short && git -C $RUN/worktree diff <base>     # or: less $RUN/changes.patch
# re-run the checks yourself inside the worktree before trusting the report
git apply --check $RUN/changes.patch          # in the ORIGINAL workspace, dry check first
git apply $RUN/changes.patch                  # then review with git diff; commit it yourself
```
The agent's tests are self-reported. Treat them as unverified until you (or Claude) re-run them.

## Cleanup afterwards
```bash
scripts/agent-worktree-agy.sh mark-reviewed --run-id <run-id>
scripts/agent-worktree-agy.sh cleanup       --run-id <run-id>
```
While the worktree has changes the command refuses (exit 7) and prints path, branch, status and diffstat.
Abandoning a dirty worktree is deliberately **not** automated: inspect it, then remove it with plain Git
yourself. Logs in `<root>/<run-id>/` are never deleted by the tooling.

## Tests (offline; the real `agy` is never launched)
```bash
export TEST_SANDBOX_ROOT=/some/scratch/dir     # optional
bash scripts/tests/protect-paths.test.sh
bash scripts/tests/agent-worktree-agy.test.sh
bash scripts/tests/agent-parity.test.sh
```
They cover argument rejection (dangerous ids, scopes, specs), existing path/branch refusal, dirty-origin
detection, isolated worktree creation, failed/timed-out/interrupted fake `agy`, guardrail violations,
preservation of dirty worktrees, cleanup of clean ones, refusal for foreign worktrees and the original
workspace (including a symlink to it), and agent parity. Sandboxes are left on disk for inspection.

## Limitations and risks
- `agy` is **not sandboxed by Git**: a worktree isolates files, not the process. A misbehaving agent could
  write elsewhere using absolute paths. The script only *detects* changes to the original workspace
  (fingerprint before/after; your own edits during the run also trigger it). `--sandbox` helps but may
  break test runs.
- The user-level agy setting `toolPermission: always-proceed` (in `~/.gemini/antigravity-cli/settings.json`)
  auto-approves tools in print mode. This tooling does not change it; consider tightening it.
- Commits by the agent are detected, not prevented (hooks are shared across worktrees).
- Specs with spaces in paths are unsupported (paths are restricted to `[A-Za-z0-9._@+/-]`).
- Tests needing Docker (Testcontainers) may not run inside the agent; ask for evidence of what ran.
- Timeouts rely on `timeout` (GNU coreutils). Concurrent runs are independent but share the original `.git`.
- The `agy` print-mode prompt is passed as an argument: prompts are capped at ~48 KiB spec + template.
