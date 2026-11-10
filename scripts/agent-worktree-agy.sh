#!/usr/bin/env bash
# agent-worktree-agy.sh — delegate a scoped task to Antigravity CLI (agy) inside an
# isolated Git worktree, collect evidence, and clean up only when it is provably safe.
#
# Subcommands:
#   run            create worktree + branch from an explicit base, run `agy -p` (or an interactive
#                  `agy -i` session in a new terminal window with --interactive), collect evidence
#   mark-reviewed  record that a human/Claude reviewed a run (required before cleanup)
#   cleanup        remove a clean, reviewed, system-created worktree (never forced)
#   help           show this text
#
# Run `scripts/agent-worktree-agy.sh help` for the full contract. Design rules:
#   * never touches the original workspace (no add/commit/reset/stash/clean/checkout there)
#   * never uses --force, `git clean`, `git reset --hard`, `git stash`, or recursive deletes
#   * never copies untracked/uncommitted/.env files into the worktree
#   * never parses run metadata with `source`/`eval`; it is read as plain key=value text

set -Eeuo pipefail

readonly PROG=${0##*/}
readonly SCHEMA="agent-worktree-run/1"
readonly CREATED_BY="agent-worktree-agy.sh"
readonly MAX_SPEC_BYTES=49152
readonly DEFAULT_TIMEOUT=1800
readonly AGY_BIN=${AGY_BIN:-agy}

# Exit codes
readonly EX_USAGE=2          # bad arguments
readonly EX_PRECONDITION=3   # validation/preflight failed; nothing was started
readonly EX_AGY_FAILED=4     # agy exited non-zero
readonly EX_AGY_TIMEOUT=5    # agy exceeded --timeout
readonly EX_VIOLATIONS=6     # agy exited 0 but guardrail violations were detected
readonly EX_CLEANUP_REFUSED=7
readonly EX_INTERRUPTED=130

# Paths no delegated task may modify (prefix match on repo-relative paths).
readonly -a ALWAYS_FORBIDDEN=(
  "docs/audit/findings.md"
  "docs/audit/generated"
  "docs/audit/evidence"
  "scripts/repo_audit.py"
  ".git"
)
readonly MIGRATION_DIR="backend/src/main/resources/db/migration"

# ----------------------------------------------------------------------------- output
info() { printf '[%s] %s\n' "$PROG" "$*" >&2; }
warn() { printf '[%s] WARNING: %s\n' "$PROG" "$*" >&2; }
die()  { local code=$1; shift; printf '[%s] ERROR: %s\n' "$PROG" "$*" >&2; exit "$code"; }

usage() {
  cat <<'EOF'
agent-worktree-agy.sh — run Antigravity (agy -p) in an isolated Git worktree.

USAGE
  agent-worktree-agy.sh run --task-id ID --base REF --spec FILE|- --scope PATH [--scope PATH ...]
                            --non-interactive|--interactive [options]
  agent-worktree-agy.sh mark-reviewed --run-id RUN_ID [--worktrees-root DIR]
  agent-worktree-agy.sh cleanup       --run-id RUN_ID [--worktrees-root DIR]
  agent-worktree-agy.sh help

RUN (required)
  --task-id ID          [A-Za-z0-9][A-Za-z0-9_-]{0,39}; no dots or slashes.
  --base REF            Explicit Git ref/commit the worktree starts from (resolved to a commit).
                        Uncommitted work in the original workspace is NOT included.
  --spec FILE|-         Task specification (Markdown, max 48 KiB). '-' reads stdin. It must contain
                        a heading with "Acceptance" or "Criterios" (verifiable criteria are mandatory).
  --scope PATH          Repo-relative file/dir agy may change. Repeatable, at least one. Rejects '.',
                        absolute paths, '..', globs and bare top-level directories.
  --non-interactive     Run headless with `agy -p` (output only when it finishes). Exactly one of
                        --non-interactive / --interactive is required: both acknowledge a model run.
  --interactive         Open a new terminal window running `agy -i "<prompt>"` inside the worktree,
                        so the human can watch and steer the agent. The script waits until that
                        session ends (agy exits or the window is closed), then collects evidence.
                        The agent writes its final report to <run folder>/report.md. --timeout is
                        not applied to interactive sessions.

RUN (optional)
  --finding ID          Audit finding/issue ID to investigate or fix. Repeatable.
  --context PATH        Doc agy must read first. Repeatable. Must exist in the base commit.
  --forbid PATH         Extra path agy must not modify. Repeatable (adds to the built-in list).
  --timeout SECONDS     Wall-clock limit for agy (default 1800).
  --worktrees-root DIR  Where worktrees/run folders live. Default: sibling of the repo,
                        <repo-parent>/<repo-name>-agent-worktrees. Must be outside the repo.
  --sandbox             Pass --sandbox to agy (terminal restrictions). Off by default.
  --dry-run             Validate everything and print the plan + prompt; create nothing.

ENVIRONMENT
  AGY_BIN               agy executable (default: agy). Used to substitute a fake in tests.
  AGY_TERMINAL          --interactive only: command that opens a terminal; the session script path
                        is appended as its last argument (e.g. "xterm -e bash"). Default: the first
                        of gnome-terminal, konsole, xterm, x-terminal-emulator found in PATH.
  AGENT_WT_TIMESTAMP    Override the UTC timestamp (YYYYMMDDTHHMMSSZ); for tests only.
  AGY_SETTINGS_FILE     agy settings path used for the (non-fatal) workspace-trust warning.
  AGY_WORKTREE_CHILD    Set to 1 by this script for the agy child; 'run' refuses to start
                        when it is already set (recursion guard).

EXIT CODES
  0 ok | 2 usage | 3 precondition failed | 4 agy failed | 5 agy timeout |
  6 agy ok but guardrail violations | 7 cleanup refused (worktree preserved) | 130 interrupted

RUN OUTPUT (<root>/<run-id>/): worktree/  prompt.md  task-spec.md  meta.env  stdout.log
  stderr.log  exit_code  report.md  changes.patch  changed-files.txt  diffstat.txt
  diff-check.txt  violations.txt  summary.md  (+ agy-session.sh, session_exit with --interactive)
EOF
}

# ----------------------------------------------------------------------------- validation
valid_task_id() { [[ $1 =~ ^[A-Za-z0-9][A-Za-z0-9_-]{0,39}$ ]]; }
valid_finding_id() { [[ $1 =~ ^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$ ]]; }
valid_run_id() { [[ $1 =~ ^[A-Za-z0-9][A-Za-z0-9_-]{0,39}-[0-9]{8}T[0-9]{6}Z$ ]]; }

# Strips leading ./ and trailing /; echoes the normalized path. Returns 1 if unsafe.
normalize_relpath() {
  local p=$1
  while [[ $p == ./* ]]; do p=${p#./}; done
  while [[ $p == */ && $p != "/" ]]; do p=${p%/}; done
  [[ -n $p && $p != "." ]] || return 1
  [[ $p != /* && $p != -* && $p != "~"* ]] || return 1
  [[ $p =~ ^[A-Za-z0-9._@+/-]+$ ]] || return 1
  [[ "/$p/" != *"/../"* && "/$p/" != *"/./"* && "$p" != *"//"* ]] || return 1
  printf '%s' "$p"
}

# True if $1 (repo-relative) equals or lies under prefix $2.
path_under() { [[ $1 == "$2" || $1 == "$2"/* ]]; }

need_val() { [[ $2 -ge 2 ]] || die "$EX_USAGE" "option $1 requires a value"; }

has_nul() {
  local f=$1
  [[ $(tr -d '\000' <"$f" | wc -c) -ne $(wc -c <"$f") ]]
}

# ----------------------------------------------------------------------------- metadata
# Plain key=value file, never sourced. Values are single-line.
meta_set() {
  local file=$1 key=$2 val=${3//$'\n'/ } tmp
  tmp=$(mktemp "$file.XXXXXX")
  { [[ -f $file ]] && grep -v -- "^${key}=" "$file" || true; printf '%s=%s\n' "$key" "$val"; } >"$tmp"
  mv -f -- "$tmp" "$file"
}
meta_get() { sed -n "s/^${2}=//p" "$1" 2>/dev/null | head -n 1; }

# ----------------------------------------------------------------------------- git helpers
git_toplevel() { git rev-parse --show-toplevel 2>/dev/null; }

# realpath that returns ONE path form. On Windows/Git Bash, `git rev-parse --show-toplevel` yields
# "C:/Users/..." while `realpath` of a Git-Bash-style path yields "/tmp/..." or "/c/Users/...". Comparing
# those by prefix silently defeats the "root must be outside the repository" guard, so every path that is
# compared is normalized to the mixed form ("C:/...") when cygpath exists. Elsewhere it is plain realpath.
realpath_n() {
  local out
  out=$(realpath "$@") || return
  if command -v cygpath >/dev/null 2>&1; then cygpath -m -- "$out"; else printf '%s\n' "$out"; fi
}

default_root() {
  local top=$1
  printf '%s/%s-agent-worktrees' "$(dirname "$top")" "$(basename "$top")"
}

# Fingerprint of the original workspace, to detect writes made behind our back.
origin_fingerprint() {
  local top=$1
  {
    git -C "$top" rev-parse HEAD
    git -C "$top" branch --show-current
    git -C "$top" status --porcelain=v1 -uall
    git -C "$top" diff HEAD
  } 2>/dev/null | sha256sum | cut -d' ' -f1
}

# ----------------------------------------------------------------------------- prompt
build_prompt() {
  # Variables are expanded once by the heredoc; their contents are never re-evaluated.
  local run_id=$1 base_commit=$2 branch=$3 spec=$4 interactive=$5 run_dir=$6
  local launcher report_rule
  if ((interactive)); then
    launcher='an interactive "agy -i" session opened by scripts/agent-worktree-agy.sh (a human is watching)'
    report_rule="FINAL REPORT (write it with exactly this Markdown to the file $run_dir/report.md -- the ONLY file
you may write outside the worktree -- then show it as your last message and tell the human the task is done)"
  else
    launcher='a child of "agy -p" started by scripts/agent-worktree-agy.sh'
    report_rule='FINAL REPORT (your last message must be exactly this Markdown, nothing else; do not write report files)'
  fi
  local scope_list context_list forbid_list finding_list f
  scope_list=""; for f in "${SCOPES[@]}"; do scope_list+="  - $f"$'\n'; done
  context_list=""; for f in "${CONTEXTS[@]:-}"; do [[ -n $f ]] && context_list+="  - $f"$'\n'; done
  [[ -n $context_list ]] || context_list="  (none listed; read docs/TESTING.md only if it exists in this worktree)"$'\n'
  finding_list=""; for f in "${FINDINGS[@]:-}"; do [[ -n $f ]] && finding_list+="  - $f"$'\n'; done
  [[ -n $finding_list ]] || finding_list="  (none listed; use only the task specification below)"$'\n'
  forbid_list=""; for f in "${ALWAYS_FORBIDDEN[@]}" "${EXTRA_FORBIDS[@]:-}"; do [[ -n $f ]] && forbid_list+="  - $f"$'\n'; done
  forbid_list+="  - $MIGRATION_DIR/* files that already exist (applied Flyway migrations are immutable)"$'\n'
  forbid_list+="  - any .env file or credentials (do not read, copy or print them)"$'\n'

  cat <<EOF
You are the WORKER agent for run "$run_id" in the SkillBridge AI repository.
You are running as $launcher.
The environment variable AGY_WORKTREE_CHILD=1 is set. NEVER start another agy process and NEVER run
scripts/agent-worktree-agy.sh. Do the work yourself, in this worktree, and report.

ENVIRONMENT
- Working directory: an isolated Git worktree on branch "$branch", created from commit $base_commit.
- Uncommitted files from the original workspace are NOT present here. If a file you need is missing,
  stop and say so in the report; do not recreate it from memory.

HARD RULES
1. Change ONLY files inside the authorized scope. Anything else is a violation.
2. Do NOT run: git commit, git add, git reset, git stash, git clean, git checkout, git switch,
   git branch, git rebase, git merge, git push, git worktree. Leave changes as plain working-tree edits.
3. Do NOT modify these paths:
${forbid_list}4. Do not touch the original workspace outside this worktree (no absolute paths to it).
5. Do not weaken tests, delete tests, or disable checks to make them pass.
6. Make the smallest change that satisfies each authorized finding. No drive-by refactors.
7. If a finding is already fixed, cannot be reproduced, or needs out-of-scope changes, do not force it:
   report it as DISCARDED or PENDING with evidence.
8. Never claim a test passed unless you ran it in this session and saw the output.

AUTHORIZED SCOPE (repo-relative files/directories)
$scope_list
FINDINGS / ISSUES TO INVESTIGATE OR FIX
$finding_list
CONTEXT TO READ FIRST (if present in this worktree)
$context_list
HOW TO VERIFY
- Run the checks relevant to the modules you touched, following docs/TESTING.md when it exists:
  backend: "mvn test" from backend/ (tests that need Docker may be unavailable: say so);
  frontend: "npm test -- --watch=false --browsers=ChromeHeadless" from frontend/;
  audit-service: its own mvn test / npm test.
- Then run and include the output of: git diff --check ; git status --short ; git diff --stat.

$report_rule
# Report $run_id
## Summary
(2-4 sentences)
## Findings
| ID | Status (RESOLVED/PENDING/DISCARDED) | Evidence (file:line, command output) | Change made |
## Files changed
(list; each must be inside the authorized scope)
## Tests executed
(exact command, exit status, key output) or "Not executed: <reason>"
## Checks
(git diff --check / git status --short / git diff --stat output)
## Risks and open questions

TASK SPECIFICATION (verbatim from the requester)
-----8<-----
$spec
-----8<-----
EOF
}

# ----------------------------------------------------------------------------- interactive
# Fills term_cmd (caller's local) with the command that opens a new terminal window; the session
# script path is appended as the last argument.
resolve_terminal() {
  if [[ -n ${AGY_TERMINAL:-} ]]; then
    read -r -a term_cmd <<<"$AGY_TERMINAL"
    return 0
  fi
  [[ -n ${DISPLAY:-}${WAYLAND_DISPLAY:-} ]] || return 1
  if command -v gnome-terminal >/dev/null 2>&1; then term_cmd=(gnome-terminal --wait --title "$task_id" -- bash)
  elif command -v konsole >/dev/null 2>&1;     then term_cmd=(konsole -e bash)
  elif command -v xterm >/dev/null 2>&1;       then term_cmd=(xterm -T "$task_id" -e bash)
  elif command -v x-terminal-emulator >/dev/null 2>&1; then term_cmd=(x-terminal-emulator -e bash)
  else return 1
  fi
}

# Writes <run_dir>/agy-session.sh, opens it in a terminal and waits for <run_dir>/session_exit.
# Sets agy_rc (caller's local). Values are embedded with printf %q, never re-evaluated.
run_interactive_session() {
  local session="$run_dir/agy-session.sh" done_file="$run_dir/session_exit"
  local -a agy_args=(-i)
  ((sandbox)) && agy_args=(--sandbox -i)
  {
    printf '#!/usr/bin/env bash\n# generated by %s for run %s\n' "$PROG" "$run_id"
    printf 'rc=129\n'
    printf "trap 'printf \"%%s\\\\n\" \"\$rc\" >%q' EXIT\n" "$done_file"
    printf "trap 'exit 129' HUP TERM\n"
    printf 'cd -- %q || exit 125\n' "$wt"
    printf 'export AGY_WORKTREE_CHILD=1 AGY_PARENT_RUN_ID=%q\n' "$run_id"
    printf 'printf %q %q\n' '\033]0;%s\007' "$task_id"
    printf 'echo %q\n' "== $task_id | worktree: $wt | report: $run_dir/report.md =="
    printf '%q %s "$(cat -- %q)"\n' "$AGY_BIN" "${agy_args[*]}" "$run_dir/prompt.md"
    # record the exit right away so evidence is collected while the window is still open
    printf 'rc=$?; printf "%%s\\n" "$rc" >%q\n' "$done_file"
    printf 'read -r -p "agy finished (exit $rc). Press Enter to close this window..." _ || true\n'
  } >"$session"
  chmod 700 -- "$session"

  info "opening interactive agy session for $run_id (${term_cmd[*]}); waiting for it to end"
  info "worktree $wt; the script resumes when agy exits or the window is closed"
  set +e
  "${term_cmd[@]}" "$session" >"$run_dir/stdout.log" 2>"$run_dir/stderr.log" </dev/null
  local term_rc=$?
  set -e
  # Some terminals return immediately (server process): poll for the session's own exit marker.
  while [[ ! -s $done_file ]]; do
    if ((term_rc != 0)) && [[ ! -e $done_file ]]; then
      sleep 2; [[ -s $done_file ]] && break
      warn "terminal command exited $term_rc and the session never started"; agy_rc=$EX_AGY_FAILED; return 0
    fi
    sleep 2
  done
  agy_rc=$(head -n 1 -- "$done_file")
  [[ $agy_rc =~ ^[0-9]+$ ]] || agy_rc=1
}

# ----------------------------------------------------------------------------- run
cmd_run() {
  local task_id="" base_ref="" spec_arg="" root="" timeout_s=$DEFAULT_TIMEOUT
  local noninteractive=0 interactive=0 sandbox=0 dry_run=0
  SCOPES=(); CONTEXTS=(); EXTRA_FORBIDS=(); FINDINGS=()

  while (($#)); do
    case $1 in
      --task-id)          need_val "$1" $#; task_id=$2; shift 2 ;;
      --base)             need_val "$1" $#; base_ref=$2; shift 2 ;;
      --spec)             need_val "$1" $#; spec_arg=$2; shift 2 ;;
      --scope)            need_val "$1" $#; SCOPES+=("$2"); shift 2 ;;
      --finding)          need_val "$1" $#; FINDINGS+=("$2"); shift 2 ;;
      --context)          need_val "$1" $#; CONTEXTS+=("$2"); shift 2 ;;
      --forbid)           need_val "$1" $#; EXTRA_FORBIDS+=("$2"); shift 2 ;;
      --timeout)          need_val "$1" $#; timeout_s=$2; shift 2 ;;
      --worktrees-root)   need_val "$1" $#; root=$2; shift 2 ;;
      --non-interactive)  noninteractive=1; shift ;;
      --interactive)      interactive=1; shift ;;
      --sandbox)          sandbox=1; shift ;;
      --dry-run)          dry_run=1; shift ;;
      -h|--help)          usage; exit 0 ;;
      *) die "$EX_USAGE" "unknown option for run: $1 (see '$PROG help')" ;;
    esac
  done

  if [[ -n ${AGY_WORKTREE_CHILD:-} ]]; then
    die "$EX_PRECONDITION" "AGY_WORKTREE_CHILD is set: refusing to launch agy from inside an agy worker (recursion guard)"
  fi

  # --- argument validation (nothing is created before all of this passes)
  [[ -n $task_id ]]  || die "$EX_USAGE" "--task-id is required"
  valid_task_id "$task_id" || die "$EX_USAGE" "invalid --task-id '$task_id' (allowed: [A-Za-z0-9][A-Za-z0-9_-]{0,39})"
  [[ -n $base_ref ]] || die "$EX_USAGE" "--base is required (explicit ref; never defaults to HEAD)"
  [[ $base_ref != -* ]] || die "$EX_USAGE" "--base must not start with '-'"
  [[ -n $spec_arg ]] || die "$EX_USAGE" "--spec is required (file or '-' for stdin)"
  ((${#SCOPES[@]} > 0)) || die "$EX_USAGE" "at least one --scope is required: undefined tasks are not delegated"
  ((noninteractive + interactive == 1)) \
    || die "$EX_USAGE" "exactly one of --non-interactive (agy -p) or --interactive (agy -i in a terminal) is required"
  [[ $timeout_s =~ ^[1-9][0-9]{0,5}$ ]] || die "$EX_USAGE" "--timeout must be a positive integer (seconds)"
  local f
  for f in "${FINDINGS[@]:-}"; do
    [[ -z $f ]] || valid_finding_id "$f" || die "$EX_USAGE" "invalid --finding '$f'"
  done

  # --- tools and repository
  command -v git >/dev/null 2>&1      || die "$EX_PRECONDITION" "git not found"
  command -v timeout >/dev/null 2>&1  || die "$EX_PRECONDITION" "timeout (coreutils) not found"
  command -v "$AGY_BIN" >/dev/null 2>&1 || die "$EX_PRECONDITION" "'$AGY_BIN' not found in PATH (set AGY_BIN to override)"
  local -a term_cmd=()
  if ((interactive)); then
    resolve_terminal || die "$EX_PRECONDITION" "--interactive needs a terminal: set AGY_TERMINAL or install gnome-terminal/konsole/xterm (and a DISPLAY)"
  fi

  local top
  top=$(git_toplevel) || die "$EX_PRECONDITION" "not inside a Git repository"
  top=$(realpath_n -- "$top")
  if [[ $(git -C "$top" rev-parse --absolute-git-dir) != $(git -C "$top" rev-parse --path-format=absolute --git-common-dir) ]]; then
    die "$EX_PRECONDITION" "run from the original workspace, not from a linked worktree ($top)"
  fi

  [[ -n $root ]] || root=$(default_root "$top")
  root=$(realpath_n -m -- "$root")
  case "$root/" in
    "$top"/*) die "$EX_PRECONDITION" "--worktrees-root ($root) must be outside the repository ($top)" ;;
  esac

  local base_commit
  base_commit=$(git -C "$top" rev-parse --verify --quiet "${base_ref}^{commit}") \
    || die "$EX_PRECONDITION" "--base '$base_ref' does not resolve to a commit"

  # scope / context / forbid are validated against the BASE commit, not the dirty workspace
  local -a nscopes=() ncontexts=() nforbids=()
  local n kind
  for f in "${SCOPES[@]}"; do
    n=$(normalize_relpath "$f") || die "$EX_USAGE" "invalid --scope '$f' (repo-relative, no '..', no globs, not '.')"
    kind=$(git -C "$top" cat-file -t "$base_commit:$n" 2>/dev/null || true)
    if [[ -z $kind ]]; then
      [[ $n == */* ]] && git -C "$top" cat-file -e "$base_commit:$(dirname "$n")" 2>/dev/null \
        || die "$EX_PRECONDITION" "--scope '$n' (and its parent) do not exist in base $base_ref"
    elif [[ $kind == tree && $n != */* ]]; then
      die "$EX_USAGE" "--scope '$n' is a whole top-level directory; narrow it to a module/file"
    fi
    nscopes+=("$n")
  done
  for f in "${CONTEXTS[@]:-}"; do
    [[ -n $f ]] || continue
    n=$(normalize_relpath "$f") || die "$EX_USAGE" "invalid --context '$f'"
    git -C "$top" cat-file -e "$base_commit:$n" 2>/dev/null \
      || die "$EX_PRECONDITION" "context '$n' is not in base $base_ref (it may be uncommitted in the original workspace). Commit it or choose a base that contains it; this script never stashes or commits for you."
    ncontexts+=("$n")
  done
  for f in "${EXTRA_FORBIDS[@]:-}"; do
    [[ -n $f ]] || continue
    n=$(normalize_relpath "$f") || die "$EX_USAGE" "invalid --forbid '$f'"
    nforbids+=("$n")
  done
  SCOPES=("${nscopes[@]}")
  CONTEXTS=("${ncontexts[@]:-}")
  EXTRA_FORBIDS=("${nforbids[@]:-}")

  # --- spec
  local spec_content
  if [[ $spec_arg == "-" ]]; then
    spec_content=$(head -c $((MAX_SPEC_BYTES + 1)); printf x); spec_content=${spec_content%x}
  else
    [[ -f $spec_arg && -r $spec_arg ]] || die "$EX_USAGE" "--spec '$spec_arg' is not a readable file"
    has_nul "$spec_arg" && die "$EX_USAGE" "--spec contains NUL bytes"
    spec_content=$(head -c $((MAX_SPEC_BYTES + 1)) -- "$spec_arg"; printf x); spec_content=${spec_content%x}
  fi
  (($(printf '%s' "$spec_content" | wc -c) <= MAX_SPEC_BYTES)) || die "$EX_USAGE" "spec exceeds $MAX_SPEC_BYTES bytes"
  [[ -n ${spec_content//[[:space:]]/} ]] || die "$EX_USAGE" "spec is empty"
  grep -qiE '^#{1,6}[[:space:]]+.*(acceptance|criterios)' <<<"$spec_content" \
    || die "$EX_USAGE" "spec needs a heading containing 'Acceptance' or 'Criterios' with verifiable criteria"

  # --- naming and collision checks
  local ts=${AGENT_WT_TIMESTAMP:-$(date -u +%Y%m%dT%H%M%SZ)}
  [[ $ts =~ ^[0-9]{8}T[0-9]{6}Z$ ]] || die "$EX_PRECONDITION" "invalid AGENT_WT_TIMESTAMP '$ts'"
  local run_id="${task_id}-${ts}" branch run_dir wt
  branch="agent/${run_id}"
  git check-ref-format --branch "$branch" >/dev/null 2>&1 || die "$EX_PRECONDITION" "invalid branch name '$branch'"
  run_dir="$root/$run_id"
  wt="$run_dir/worktree"
  [[ ! -e $run_dir ]] || die "$EX_PRECONDITION" "run folder already exists: $run_dir"
  ! git -C "$top" show-ref --verify --quiet "refs/heads/$branch" || die "$EX_PRECONDITION" "branch already exists: $branch"

  # --- original workspace state (recorded, never altered)
  local origin_branch origin_head dirty_tracked dirty_untracked origin_dirty=0
  origin_branch=$(git -C "$top" branch --show-current)
  origin_head=$(git -C "$top" rev-parse HEAD)
  dirty_tracked=$(git -C "$top" status --porcelain=v1 -uno | wc -l | tr -d ' ')
  dirty_untracked=$(git -C "$top" ls-files --others --exclude-standard | wc -l | tr -d ' ')
  ((dirty_tracked + dirty_untracked > 0)) && origin_dirty=1
  if ((origin_dirty)); then
    warn "the original workspace has uncommitted changes ($dirty_tracked tracked, $dirty_untracked untracked)."
    warn "They are NOT part of the worktree (it starts from commit ${base_commit:0:12}). Nothing was stashed, copied or discarded."
  fi
  [[ $base_commit == "$origin_head" ]] || warn "base ${base_commit:0:12} differs from the workspace HEAD ${origin_head:0:12}"

  # agy workspace-trust hint (non-fatal; we never edit agy settings)
  local settings=${AGY_SETTINGS_FILE:-$HOME/.gemini/antigravity-cli/settings.json}
  if [[ -r $settings ]] && command -v jq >/dev/null 2>&1; then
    if ! jq -e --arg p "$wt" '(.trustedWorkspaces // []) | any(. as $t | ($p == $t) or ($p | startswith($t + "/")))' "$settings" >/dev/null 2>&1; then
      warn "$wt is not in agy's trustedWorkspaces; agy -p may refuse or stall. Add it manually if needed (this script will not)."
    fi
  fi

  local prompt
  prompt=$(build_prompt "$run_id" "$base_commit" "$branch" "$spec_content" "$interactive" "$run_dir")

  if ((dry_run)); then
    {
      printf 'DRY RUN (nothing created)\n'
      printf 'run-id:       %s\nbranch:       %s\nbase:         %s (%s)\n' "$run_id" "$branch" "$base_ref" "$base_commit"
      printf 'worktree:     %s\nrun folder:   %s\norigin dirty: %s\n' "$wt" "$run_dir" "$origin_dirty"
      ((interactive)) && printf 'mode:         interactive (terminal: %s)\n' "${term_cmd[*]}"
      printf 'scope:        %s\n\n--- prompt ---\n%s\n' "${SCOPES[*]}" "$prompt"
    }
    return 0
  fi

  # --- create run folder + worktree (git refuses to overwrite; no force)
  mkdir -p -- "$root"
  # `mkdir -m` fails after creating the directory on Windows/Git Bash (NTFS ignores POSIX modes), which
  # aborted the run under `set -e`. Create first (still refuses an existing dir), then tighten the mode
  # where the filesystem supports it.
  mkdir -- "$run_dir"
  chmod 700 -- "$run_dir" 2>/dev/null || true
  local meta="$run_dir/meta.env"
  : >"$meta"
  meta_set "$meta" SCHEMA "$SCHEMA"
  meta_set "$meta" CREATED_BY "$CREATED_BY"
  meta_set "$meta" RUN_ID "$run_id"
  meta_set "$meta" TASK_ID "$task_id"
  meta_set "$meta" BRANCH "$branch"
  meta_set "$meta" BASE_REF "$base_ref"
  meta_set "$meta" BASE_COMMIT "$base_commit"
  meta_set "$meta" ORIGIN_ROOT "$top"
  meta_set "$meta" WORKTREE_PATH "$wt"
  meta_set "$meta" RUN_DIR "$run_dir"
  meta_set "$meta" CREATED_AT "$ts"
  meta_set "$meta" ORIGIN_BRANCH "$origin_branch"
  meta_set "$meta" ORIGIN_HEAD "$origin_head"
  meta_set "$meta" ORIGIN_DIRTY "$origin_dirty"
  meta_set "$meta" ORIGIN_DIRTY_TRACKED "$dirty_tracked"
  meta_set "$meta" ORIGIN_DIRTY_UNTRACKED "$dirty_untracked"
  meta_set "$meta" SCOPE "${SCOPES[*]}"
  meta_set "$meta" REVIEW_STATE "none"
  meta_set "$meta" STATUS "preparing"

  if ! git -C "$top" worktree add -b "$branch" -- "$wt" "$base_commit" >"$run_dir/worktree-add.log" 2>&1; then
    meta_set "$meta" STATUS "failed-worktree-add"
    cat -- "$run_dir/worktree-add.log" >&2
    die "$EX_PRECONDITION" "git worktree add failed; run folder kept at $run_dir"
  fi

  printf '%s\n' "$prompt" >"$run_dir/prompt.md"
  printf '%s' "$spec_content" >"$run_dir/task-spec.md"
  meta_set "$meta" STATUS "running"

  local fp_before fp_after
  fp_before=$(origin_fingerprint "$top")

  on_interrupt() {
    meta_set "$meta" STATUS "interrupted"
    meta_set "$meta" REVIEW_STATE "pending"
    warn "interrupted; worktree, branch and logs preserved at $run_dir"
    exit "$EX_INTERRUPTED"
  }
  trap on_interrupt INT TERM

  local agy_rc=0
  if ((interactive)); then
    run_interactive_session
  else
    # --- invoke agy: prompt passed as ONE argv element, never through a shell string
    local -a agy_cmd=("$AGY_BIN" -p "$prompt")
    ((sandbox)) && agy_cmd+=(--sandbox)
    info "launching agy in $wt (timeout ${timeout_s}s); logs in $run_dir"
    set +e
    (
      cd -- "$wt" || exit 125
      AGY_WORKTREE_CHILD=1 AGY_PARENT_RUN_ID="$run_id" \
        timeout --signal=TERM --kill-after=30 "$timeout_s" "${agy_cmd[@]}" \
        >"$run_dir/stdout.log" 2>"$run_dir/stderr.log" </dev/null
    )
    agy_rc=$?
    set -e
  fi
  trap - INT TERM
  printf '%s\n' "$agy_rc" >"$run_dir/exit_code"
  meta_set "$meta" AGY_EXIT "$agy_rc"
  if ((interactive)); then
    [[ -s $run_dir/report.md ]] || printf '# Report %s\n\nThe interactive agent did not write report.md (session exit %s).\n' "$run_id" "$agy_rc" >"$run_dir/report.md"
  else
    cp -- "$run_dir/stdout.log" "$run_dir/report.md"
  fi

  # --- evidence
  collect_evidence "$top" "$wt" "$run_dir" "$base_commit" "$branch" "$fp_before"

  local status ex
  if ((agy_rc == 124 || agy_rc == 137)); then status="timeout"; ex=$EX_AGY_TIMEOUT
  elif ((agy_rc != 0)); then status="failed"; ex=$EX_AGY_FAILED
  elif [[ -s $run_dir/violations.txt ]]; then status="completed-with-violations"; ex=$EX_VIOLATIONS
  else status="completed"; ex=0
  fi
  meta_set "$meta" STATUS "$status"
  meta_set "$meta" REVIEW_STATE "pending"

  render_summary "$run_dir" "$status" "$agy_rc" >"$run_dir/summary.md"
  cat -- "$run_dir/summary.md"
  return "$ex"
}

# Writes changes.patch, changed-files.txt, diffstat.txt, diff-check.txt, violations.txt.
collect_evidence() {
  local top=$1 wt=$2 run_dir=$3 base=$4 branch=$5 fp_before=$6
  local meta="$run_dir/meta.env" viol="$run_dir/violations.txt"
  : >"$viol"

  # Tracked + untracked changes versus the base commit, without staging anything.
  local -a changed=()
  local p
  while IFS= read -r -d '' p; do changed+=("$p"); done < <(git -C "$wt" diff --name-only --no-renames -z "$base" 2>/dev/null)
  while IFS= read -r -d '' p; do changed+=("$p"); done < <(git -C "$wt" ls-files --others --exclude-standard -z 2>/dev/null)
  printf '%s\n' "${changed[@]:-}" | sed '/^$/d' | LC_ALL=C sort -u >"$run_dir/changed-files.txt"

  {
    git -C "$wt" diff --no-renames "$base" 2>/dev/null
    while IFS= read -r -d '' p; do
      git -C "$wt" diff --no-index -- /dev/null "$p" 2>/dev/null || true
    done < <(git -C "$wt" ls-files --others --exclude-standard -z 2>/dev/null)
  } >"$run_dir/changes.patch"
  git -C "$wt" diff --stat --no-renames "$base" >"$run_dir/diffstat.txt" 2>/dev/null || true
  local rc=0
  git -C "$wt" diff --check "$base" >"$run_dir/diff-check.txt" 2>&1 || rc=$?
  meta_set "$meta" DIFF_CHECK_EXIT "$rc"
  meta_set "$meta" CHANGED_FILES "$(wc -l <"$run_dir/changed-files.txt" | tr -d ' ')"

  # Guardrail 1: branch/HEAD must be untouched and no commits made by the agent.
  local head_now branch_now commits
  head_now=$(git -C "$wt" rev-parse HEAD 2>/dev/null || echo unknown)
  branch_now=$(git -C "$wt" branch --show-current 2>/dev/null || echo unknown)
  commits=$(git -C "$wt" rev-list --count "$base..HEAD" 2>/dev/null || echo 0)
  [[ $commits == 0 ]] || echo "AGENT_COMMITS: $commits commit(s) exist on top of the base" >>"$viol"
  [[ $branch_now == "$branch" ]] || echo "BRANCH_CHANGED: worktree is on '$branch_now', expected '$branch'" >>"$viol"
  [[ $head_now == "$base" || $commits != 0 ]] || echo "HEAD_MOVED: HEAD is $head_now" >>"$viol"

  # Guardrail 2: scope, forbidden paths, applied migrations, env files.
  local -a scopes
  read -r -a scopes <<<"$(meta_get "$meta" SCOPE)"
  local s in_scope forb
  while IFS= read -r p; do
    [[ -n $p ]] || continue
    in_scope=0
    for s in "${scopes[@]}"; do path_under "$p" "$s" && { in_scope=1; break; }; done
    ((in_scope)) || echo "OUT_OF_SCOPE: $p" >>"$viol"
    for forb in "${ALWAYS_FORBIDDEN[@]}" "${EXTRA_FORBIDS[@]:-}"; do
      [[ -n $forb ]] && path_under "$p" "$forb" && echo "FORBIDDEN_PATH: $p (under $forb)" >>"$viol"
    done
    if path_under "$p" "$MIGRATION_DIR" && git -C "$top" cat-file -e "$base:$p" 2>/dev/null; then
      echo "APPLIED_MIGRATION_CHANGED: $p" >>"$viol"
    fi
    case "${p##*/}" in .env|.env.*) [[ ${p##*/} == ".env.example" ]] || echo "ENV_FILE: $p" >>"$viol" ;; esac
  done <"$run_dir/changed-files.txt"

  # Guardrail 3: the original workspace must look exactly as before.
  local fp_after
  fp_after=$(origin_fingerprint "$top")
  [[ $fp_after == "$fp_before" ]] || echo "ORIGIN_MODIFIED_DURING_RUN: the original workspace changed while agy ran (may be your own edits; check git status there)" >>"$viol"

  meta_set "$meta" VIOLATIONS "$(wc -l <"$viol" | tr -d ' ')"
}

render_summary() {
  local run_dir=$1 status=$2 agy_rc=$3
  local meta="$run_dir/meta.env"
  local wt branch base
  wt=$(meta_get "$meta" WORKTREE_PATH); branch=$(meta_get "$meta" BRANCH); base=$(meta_get "$meta" BASE_COMMIT)
  printf '# agent-worktree run %s\n\n' "$(meta_get "$meta" RUN_ID)"
  printf -- '- STATUS: **%s** (agy exit code %s)\n' "$status" "$agy_rc"
  case $status in
    completed) printf -- '- agy finished and no guardrail violation was detected. Its self-reported tests are UNVERIFIED until re-run.\n' ;;
    *)         printf -- '- The task is NOT confirmed complete. Do not treat this run as successful.\n' ;;
  esac
  printf -- '- Branch: %s\n- Base commit: %s\n- Worktree: %s\n- Run folder: %s\n' "$branch" "$base" "$wt" "$run_dir"
  printf -- '- Original workspace dirty at start: %s (not included in the worktree)\n' "$(meta_get "$meta" ORIGIN_DIRTY)"
  printf -- '- Files changed: %s | git diff --check exit: %s\n' "$(meta_get "$meta" CHANGED_FILES)" "$(meta_get "$meta" DIFF_CHECK_EXIT)"
  if [[ -s $run_dir/violations.txt ]]; then
    printf '\n## Guardrail violations\n'; sed 's/^/- /' "$run_dir/violations.txt"
  fi
  printf '\n## Review\n'
  printf -- '- Report:  %s/report.md\n- Diff:    %s/changes.patch (diffstat.txt, changed-files.txt)\n- Logs:    %s/stdout.log, %s/stderr.log\n' "$run_dir" "$run_dir" "$run_dir" "$run_dir"
  printf -- '- Inspect: git -C "%s" status --short && git -C "%s" diff %s\n' "$wt" "$wt" "$base"
  printf '\n## Next steps (nothing was integrated or removed automatically)\n'
  printf -- '1. Review the report and diff. Integrate by hand (e.g. git apply %s/changes.patch in the original workspace) once approved.\n' "$run_dir"
  printf -- '2. %s mark-reviewed --run-id %s\n' "$PROG" "$(meta_get "$meta" RUN_ID)"
  printf -- '3. %s cleanup --run-id %s   (refuses while the worktree has changes)\n' "$PROG" "$(meta_get "$meta" RUN_ID)"
}

# ----------------------------------------------------------------------------- run lookup
# Resolves <root>/<run-id>, validating the id before any path is built.
load_run() {
  local run_id="" root=""
  while (($#)); do
    case $1 in
      --run-id)         need_val "$1" $#; run_id=$2; shift 2 ;;
      --worktrees-root) need_val "$1" $#; root=$2; shift 2 ;;
      *) die "$EX_USAGE" "unknown option: $1" ;;
    esac
  done
  [[ -n $run_id ]] || die "$EX_USAGE" "--run-id is required"
  valid_run_id "$run_id" || die "$EX_USAGE" "invalid --run-id '$run_id'"
  command -v git >/dev/null 2>&1 || die "$EX_PRECONDITION" "git not found"
  LR_TOP=$(git_toplevel) || die "$EX_PRECONDITION" "not inside a Git repository"
  LR_TOP=$(realpath_n -- "$LR_TOP")
  [[ -n $root ]] || root=$(default_root "$LR_TOP")
  LR_ROOT=$(realpath_n -m -- "$root")
  case "$LR_ROOT/" in
    "$LR_TOP"/*) die "$EX_CLEANUP_REFUSED" "--worktrees-root ($LR_ROOT) is inside the repository ($LR_TOP); refusing" ;;
  esac
  LR_ID=$run_id
  LR_DIR="$LR_ROOT/$run_id"
  LR_META="$LR_DIR/meta.env"
  [[ -f $LR_META ]] || die "$EX_CLEANUP_REFUSED" "no run metadata at $LR_META: not a run created by this system"
  [[ $(meta_get "$LR_META" SCHEMA) == "$SCHEMA" && $(meta_get "$LR_META" CREATED_BY) == "$CREATED_BY" ]] \
    || die "$EX_CLEANUP_REFUSED" "$LR_META lacks this system's signature; refusing"
}

cmd_mark_reviewed() {
  load_run "$@"
  meta_set "$LR_META" REVIEW_STATE "reviewed"
  info "run $LR_ID marked as reviewed (worktree untouched)"
}

# ----------------------------------------------------------------------------- cleanup
refuse() {
  # refuse REASON — preserves everything and prints how to proceed manually.
  local reason=$1
  local wt branch base
  wt="$LR_DIR/worktree"; branch="agent/$LR_ID"; base=$(meta_get "$LR_META" BASE_COMMIT)
  {
    printf '[%s] CLEANUP REFUSED: %s\n' "$PROG" "$reason"
    printf '  worktree: %s\n  branch:   %s\n  run dir:  %s\n' "$wt" "$branch" "$LR_DIR"
    if [[ -d $wt ]]; then
      printf '  status:\n'; git -C "$wt" status --short 2>/dev/null | sed 's/^/    /' || true
      printf '  diff stat vs base:\n'; git -C "$wt" diff --stat "$base" 2>/dev/null | sed 's/^/    /' || true
    fi
    printf '  Nothing was removed. After you review and integrate (or deliberately abandon) the work, run:\n'
    printf '    %s mark-reviewed --run-id %s && %s cleanup --run-id %s\n' "$PROG" "$LR_ID" "$PROG" "$LR_ID"
    printf '  To abandon a dirty worktree you must remove it yourself with plain git, after reading the diff above.\n'
  } >&2
  exit "$EX_CLEANUP_REFUSED"
}

cmd_cleanup() {
  load_run "$@"
  local wt="$LR_DIR/worktree" branch="agent/$LR_ID"
  local m_wt m_branch base status review
  m_wt=$(meta_get "$LR_META" WORKTREE_PATH); m_branch=$(meta_get "$LR_META" BRANCH)
  base=$(meta_get "$LR_META" BASE_COMMIT); status=$(meta_get "$LR_META" STATUS)
  review=$(meta_get "$LR_META" REVIEW_STATE)

  # 1. ownership: metadata must agree with the path derived from the run-id (not the other way round)
  [[ $m_wt == "$wt" ]]        || refuse "metadata worktree path ($m_wt) does not match the expected run path"
  [[ $m_branch == "$branch" ]] || refuse "metadata branch ($m_branch) does not match agent/<run-id>"
  [[ $(meta_get "$LR_META" ORIGIN_ROOT) == "$LR_TOP" ]] || refuse "run belongs to a different repository"

  # 2. never the original workspace
  local real_wt
  real_wt=$(realpath_n -m -- "$wt")
  [[ $real_wt != "$LR_TOP" ]] || refuse "target is the original workspace"

  # 3. must be registered by git, as a non-main worktree on the expected branch
  local registered=0 reg_branch="" main_wt="" cur="" cur_branch="" uses=0 first=1
  while IFS= read -r line; do
    case $line in
      "worktree "*)
        cur=${line#worktree }; cur_branch=""
        if ((first)); then main_wt=$cur; first=0; fi ;;
      "branch "*)
        cur_branch=${line#branch }
        [[ $cur_branch == "refs/heads/$branch" ]] && uses=$((uses + 1)) ;;
      "")
        if [[ -n $cur && $(realpath_n -m -- "$cur") == "$real_wt" ]]; then registered=1; reg_branch=$cur_branch; fi ;;
    esac
  done < <(git -C "$LR_TOP" worktree list --porcelain; echo)
  ((registered)) || refuse "path is not a registered worktree in 'git worktree list' (already removed? run 'git worktree prune' yourself if so)"
  [[ $(realpath_n -m -- "$main_wt") != "$real_wt" ]] || refuse "target is the main worktree"
  [[ $reg_branch == "refs/heads/$branch" ]] || refuse "worktree is on '$reg_branch', expected refs/heads/$branch"
  ((uses == 1)) || refuse "branch $branch is used by $uses worktrees"
  [[ -d $wt ]] || refuse "worktree directory is missing"

  # 4. state: finished, reviewed, clean, no exclusive commits
  case $status in
    completed|completed-with-violations|failed|timeout|interrupted|failed-worktree-add|preparing) : ;;
    running) refuse "run status is 'running'; it may still be active" ;;
    *) refuse "unknown run status '$status'" ;;
  esac
  [[ $review != pending ]] || refuse "the result is pending review; run 'mark-reviewed' after reviewing it"
  local dirty
  dirty=$(git -C "$wt" status --porcelain=v1 -uall 2>/dev/null) || refuse "cannot read worktree status"
  [[ -z $dirty ]] || refuse "worktree has uncommitted or untracked changes"
  local tip head ahead
  head=$(git -C "$wt" rev-parse HEAD) || refuse "cannot read worktree HEAD"
  tip=$(git -C "$LR_TOP" rev-parse --verify --quiet "refs/heads/$branch") || refuse "branch $branch does not exist"
  ahead=$(git -C "$LR_TOP" rev-list --count "$base..$tip")
  [[ $head == "$tip" ]] || refuse "worktree HEAD differs from the branch tip"
  [[ $ahead == 0 && $tip == "$base" ]] || refuse "branch has $ahead commit(s) not in the base (unique work)"

  # 5. remove: no --force, ever
  git -C "$LR_TOP" worktree remove -- "$wt" || refuse "git worktree remove declined (it never forces)"
  # branch: delete only if it still points at the base commit (compare-and-delete)
  if git -C "$LR_TOP" update-ref -d "refs/heads/$branch" "$tip"; then
    meta_set "$LR_META" BRANCH_DELETED "1"
  else
    warn "worktree removed but branch $branch could not be deleted safely; left in place"
  fi
  meta_set "$LR_META" STATUS "cleaned"
  info "removed worktree $wt; run folder with logs kept at $LR_DIR"
}

# ----------------------------------------------------------------------------- main
main() {
  local sub=${1:-help}
  [[ $# -eq 0 ]] || shift
  case $sub in
    run)           cmd_run "$@" ;;
    mark-reviewed) cmd_mark_reviewed "$@" ;;
    cleanup)       cmd_cleanup "$@" ;;
    help|-h|--help) usage ;;
    *) printf 'unknown subcommand: %s\n\n' "$sub" >&2; usage >&2; exit "$EX_USAGE" ;;
  esac
}

main "$@"
