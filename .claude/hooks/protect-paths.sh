#!/usr/bin/env bash
# PreToolUse hook (Edit|Write|MultiEdit|NotebookEdit): blocks edits to protected paths.
#
# Protected paths mirror AGENTS.md section 3 plus Flyway immutability:
#   - docs/audit/findings.md, docs/audit/evidence/**, docs/audit/generated/**
#   - scripts/repo_audit.py
#   - backend/src/main/resources/db/migration/* that already exist in HEAD
#     (an applied migration must never change; add a new V<n+1> instead)
#   - .env files (defense in depth next to permissions.deny)
#
# Contract: reads the hook JSON on stdin. Exit 0 = allow, exit 2 = block (stderr is
# shown to Claude). Fails closed when jq is missing or the input is unparseable.
# Scope: only Claude Code's file-edit tools. Bash commands (sed -i, redirects, ...)
# are NOT inspected; see docs/agents/GUARDRAILS.md.
#
# Windows / Git Bash: the same file can arrive as "C:\a\b", "C:/a/b", "/c/a/b" or "/tmp/x", and jq.exe ends
# lines with CRLF. Comparing those forms textually made the guard fail OPEN (a protected file looked like it
# was "outside the project"). Every path is therefore normalized to ONE form with cygpath before comparing,
# trailing CRs are stripped, and matching is case-insensitive when cygpath exists (NTFS ignores case).

set -u

deny() {
  printf 'BLOCKED by .claude/hooks/protect-paths.sh: %s\n' "$1" >&2
  exit 2
}

command -v jq >/dev/null 2>&1 || deny "jq is required to evaluate this guard; install jq or fix the hook."

have_cygpath=0
command -v cygpath >/dev/null 2>&1 && have_cygpath=1

# is_abs PATH: POSIX absolute, drive-letter (C:\ or C:/) or UNC.
is_abs() {
  case "$1" in
    /*|[A-Za-z]:[\\/]*|\\\\*) return 0 ;;
    *) return 1 ;;
  esac
}

# norm PATH: resolve to a single canonical absolute form (mixed "C:/..." on Windows, plain realpath elsewhere).
norm() {
  local p=$1 q
  if [ "$have_cygpath" = 1 ]; then
    q=$(cygpath -m -- "$p" 2>/dev/null) && p=$q
  fi
  p=$(realpath -m -- "$p") || return 1
  if [ "$have_cygpath" = 1 ]; then
    q=$(cygpath -m -- "$p" 2>/dev/null) && p=$q
  fi
  printf '%s\n' "$p"
}

input=$(cat)
file=$(jq -r '.tool_input.file_path // .tool_input.notebook_path // empty' <<<"$input" 2>/dev/null) \
  || deny "could not parse hook input; refusing the edit."
file=${file%$'\r'}
[ -n "$file" ] || exit 0

cwd=$(jq -r '.cwd // empty' <<<"$input" 2>/dev/null)
cwd=${cwd%$'\r'}
root=${CLAUDE_PROJECT_DIR:-$cwd}
root=${root%$'\r'}
[ -n "$root" ] || deny "cannot determine the project root; refusing the edit."

if is_abs "$file"; then
  target=$file
else
  target="${cwd:-$root}/$file"
fi
abs=$(norm "$target") || deny "cannot resolve the target path; refusing the edit."
root=$(norm "$root") || deny "cannot resolve the project root; refusing the edit."

# On Windows the file system is case-insensitive, so are the protected-path patterns.
[ "$have_cygpath" = 1 ] && shopt -s nocasematch

case "$abs" in
  "$root"/*) rel=${abs:$((${#root} + 1))} ;;
  *) exit 0 ;; # outside the project: not this hook's concern
esac

case "$rel" in
  docs/audit/findings.md)
    deny "$rel is the central audit register (AGENTS.md section 3). Ask the user to edit it." ;;
  docs/audit/evidence/*|docs/audit/generated/*)
    deny "$rel is historical/generated audit evidence (AGENTS.md section 3). Ask the user to edit it." ;;
  scripts/repo_audit.py)
    deny "$rel is protected (AGENTS.md section 3). Ask the user to edit it." ;;
  .env|*/.env|.env.local|*/.env.local|.env.production|*/.env.production|.env.development|*/.env.development|.env.staging|*/.env.staging|.env.test|*/.env.test)
    deny "$rel may contain secrets. Never read or edit .env files; use .env.example for documentation." ;;
  backend/src/main/resources/db/migration/*)
    # Case-insensitive, whole-line, fixed-string match against the files tracked in HEAD.
    if git -C "$root" ls-tree -r --name-only HEAD -- backend/src/main/resources/db/migration 2>/dev/null \
         | tr -d '\r' | grep -qixF -- "$rel"; then
      deny "$rel already exists in HEAD, so Flyway may have applied it (checksum would break). Create a new V<next>__description.sql migration instead."
    fi ;;
esac

exit 0
