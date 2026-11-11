# Claude Code guardrails (`.claude/settings.json` + hook)

Deterministic enforcement of rules that `AGENTS.md` section 3 only states in prose.

## What is enforced

| Mechanism | Where | Effect |
|---|---|---|
| `permissions.deny` | `.claude/settings.json` | Claude cannot **Read** or **Edit** `.env`, `.env.local`, `.env.production`, `.env.development`, `.env.staging`, `.env.test` at any depth. `.env.example` stays readable. |
| `PreToolUse` hook (`Edit\|Write\|MultiEdit\|NotebookEdit`) | `.claude/hooks/protect-paths.sh` | Blocks edits to `docs/audit/findings.md`, `docs/audit/evidence/**`, `docs/audit/generated/**`, `scripts/repo_audit.py`, `.env*` files, and any `backend/src/main/resources/db/migration/*` file that already exists in `HEAD` (applied Flyway migrations are immutable: add `V<next>__...sql` instead). New, not-yet-committed migrations stay editable. |
| `permissions.allow` | `.claude/settings.json` | Read-only Git (`status`, `diff`, `log`, `worktree list`) and `mvn test`, `mvn -o validate`, `npm test` no longer prompt. `mvn verify` (needs Docker) still prompts. |

The hook fails **closed**: if `jq` is missing or the hook input cannot be parsed, the edit is blocked.
Exit code 2 sends the reason back to Claude so it can change course.

## Known limits (be honest about them)
- Only Claude Code's file-edit tools are guarded. A Bash command such as `sed -i`, `tee`, `> file`, or
  `git checkout -- <file>` is **not** inspected. `Read` denies do not stop `cat .env` in Bash.
  Use Claude Code's sandbox or review Bash prompts if that matters to you.
- "Applied migration" is approximated by "exists in the local `HEAD`". A migration committed locally but
  never deployed is also frozen; a migration deployed from another branch but absent from `HEAD` is not.
- The `AGENTS.md` exception that lets an assigned task generate
  `docs/audit/evidence/06-documentation-migration.md` is **not** honored: the whole evidence directory is
  blocked. Edit that file yourself or relax the hook deliberately.
- The hook is a plain file in the repo. A user (or a change you approve) can edit it; it protects against
  mistakes, not against someone with write access.
- `settings.json` and the hook are loaded when Claude Code starts; restart the session (or open `/hooks`)
  after changing them.

## Testing
```bash
bash scripts/tests/protect-paths.test.sh   # 23 offline checks, uses a throwaway sandbox repo
```
Set `TEST_SANDBOX_ROOT=<dir>` to choose where the sandbox is created. The test never deletes anything.

## Maintenance
- New protected path: add a `case` branch in `protect-paths.sh` **and** a test case.
- Applied migrations need no upkeep; they are detected from `HEAD`.
- Keep `AGENTS.md` section 3 and this table consistent (the script and agents also list these paths).
