#!/usr/bin/env bash
# Offline tests for scripts/agent-worktree-agy.sh. A fake `agy` replaces the real one:
# the real Antigravity CLI is never launched and no application code is touched.
# All Git repos live in a throwaway sandbox that is left in place (nothing is deleted).
#
# Usage: TEST_SANDBOX_ROOT=/some/dir bash scripts/tests/agent-worktree-agy.test.sh
set -u
HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
SCRIPT=$(cd "$HERE/.." && pwd)/agent-worktree-agy.sh
# shellcheck source=lib.sh
. "$HERE/lib.sh"

SB=$(t_sandbox)
SB=$(cd "$SB" && pwd -P)
ORIG="$SB/repo"
ROOT="$SB/repo-agent-worktrees"          # default sibling root for ORIG
FAKE="$SB/fake-agy"
SPEC="$SB/spec.md"
FAKELOG="$SB/fake.log"
echo "agent-worktree-agy tests (sandbox: $SB)"

# ---------------------------------------------------------------- fixtures
mkdir -p "$ORIG"
git -C "$ORIG" init -q -b main
git -C "$ORIG" config user.email t@example.invalid
git -C "$ORIG" config user.name tester
(
  cd "$ORIG" || exit 1
  mkdir -p backend/src/main/java backend/src/main/resources/db/migration docs/audit/evidence frontend scripts
  printf '.env\n' > .gitignore
  printf 'readme v1\n' > README.md
  printf 'class App {}\n' > backend/src/main/java/App.java
  printf 'CREATE TABLE a(id int);\n' > backend/src/main/resources/db/migration/V1__init.sql
  printf 'findings\n' > docs/audit/findings.md
  printf 'evidence\n' > docs/audit/evidence/e.md
  printf 'testing guide\n' > docs/TESTING.md
  printf 'print(1)\n' > scripts/repo_audit.py
  printf 'export {}\n' > frontend/app.ts
  git add -A && git commit -q -m base
)
BASE_COMMIT=$(git -C "$ORIG" rev-parse HEAD)

cat > "$SPEC" <<'EOF'
# Task T
Fix finding SEC-003 in App.java.

## Acceptance criteria
- App.java compiles
- tests for App pass
EOF

cat > "$FAKE" <<'EOF'
#!/usr/bin/env bash
# fake agy: records how it was called, then behaves according to FAKE_AGY_MODE
{
  printf 'arg1=%s\n' "${1:-}"
  printf 'argc=%s\n' "$#"
  i=0; for a in "$@"; do i=$((i+1)); [ "${#a}" -gt 200 ] && a="<long:${#a}>"; printf 'arg%s=%s\n' "$i" "$a"; done
  printf 'prompt_has_worker=%s\n' "$(printf '%s' "${2:-}" | grep -c 'WORKER agent')"
  printf 'pwd=%s\nchild=%s\nparent=%s\n' "$PWD" "${AGY_WORKTREE_CHILD:-}" "${AGY_PARENT_RUN_ID:-}"
} > "${FAKE_AGY_LOG:?}"
report() { printf '# Report fake\n## Summary\nmode %s\n## Tests executed\nNot executed: fake\n' "$FAKE_AGY_MODE"; }
case "${FAKE_AGY_MODE:-ok}" in
  ok)        mkdir -p backend/src/main/java; echo 'class Fixed {}' > backend/src/main/java/Fixed.java; report ;;
  noop)      report ;;
  fail)      echo "boom from fake agy" >&2; exit 3 ;;
  sleep)     exec sleep 60 ;;
  commit)    echo 'class C {}' > backend/src/main/java/C.java; git -c user.name=x -c user.email=x@x.invalid add -A; git -c user.name=x -c user.email=x@x.invalid commit -q -m agent; report ;;
  scope)     mkdir -p frontend; echo 'x' > frontend/out.txt; report ;;
  protected) echo 'tampered' >> docs/audit/findings.md; report ;;
  migration) echo '-- tampered' >> backend/src/main/resources/db/migration/V1__init.sql; report ;;
  origin)    echo 'tampered' >> "${FAKE_AGY_ORIG:?}/README.md"; report ;;
esac
EOF
chmod +x "$FAKE"

TS_N=0
next_ts() { TS_N=$((TS_N + 1)); printf '20260101T0000%02dZ' "$TS_N"; }
LAST_TS=""

# agy_run TASK MODE [extra args...] -> RC OUT RID RUNDIR WT
agy_run() {
  local task=$1 mode=$2; shift 2
  LAST_TS=$(next_ts)
  RID="$task-$LAST_TS"; RUNDIR="$ROOT/$RID"; WT="$RUNDIR/worktree"
  RC=0
  OUT=$(cd "$ORIG" && env AGY_BIN="$FAKE" FAKE_AGY_MODE="$mode" FAKE_AGY_LOG="$FAKELOG" FAKE_AGY_ORIG="$ORIG" \
        AGENT_WT_TIMESTAMP="$LAST_TS" AGY_SETTINGS_FILE=/nonexistent \
        bash "$SCRIPT" run --task-id "$task" --base main --spec "$SPEC" --scope backend/src --non-interactive "$@" 2>&1) || RC=$?
}

# raw_run ARGS... (no defaults; for validation tests)
raw_run() {
  RC=0
  OUT=$(cd "$ORIG" && env AGY_BIN="${RAW_AGY:-$FAKE}" FAKE_AGY_LOG="$FAKELOG" FAKE_AGY_MODE=ok AGY_SETTINGS_FILE=/nonexistent \
        bash "$SCRIPT" "$@" 2>&1) || RC=$?
}

meta() { sed -n "s/^$2=//p" "$1/meta.env" | head -n 1; }
no_run_dirs() { [ ! -d "$ROOT" ] || [ -z "$(ls -A "$ROOT" 2>/dev/null)" ]; }

# ================================================================ 1. argument validation
echo "# 1. argument validation"
raw_run;                                    assert_eq "no args prints help (rc 0)" 0 "$RC"
raw_run bogus;                              assert_eq "unknown subcommand rc 2" 2 "$RC"
raw_run run --bogus;                        assert_eq "unknown run option rc 2" 2 "$RC"
BAD_IDS=('../x' 'a/b' '-x' 'a b' 'x;rm' '$(id)' 'a.b' '' 'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA')
for id in "${BAD_IDS[@]}"; do
  raw_run run --task-id "$id" --base main --spec "$SPEC" --scope backend/src --non-interactive
  assert_eq "rejects task-id [$id] (rc 2)" 2 "$RC"
done
raw_run run --base main --spec "$SPEC" --scope backend/src --non-interactive;                 assert_eq "requires --task-id" 2 "$RC"
raw_run run --task-id T --spec "$SPEC" --scope backend/src --non-interactive;                 assert_eq "requires explicit --base" 2 "$RC"
raw_run run --task-id T --base main --scope backend/src --non-interactive;                    assert_eq "requires --spec" 2 "$RC"
raw_run run --task-id T --base main --spec "$SPEC" --non-interactive;                         assert_eq "requires --scope" 2 "$RC"
assert_contains "scope error explains undefined tasks" "$OUT" "undefined tasks"
raw_run run --task-id T --base main --spec "$SPEC" --scope backend/src;                       assert_eq "requires --non-interactive" 2 "$RC"
for sc in '.' '..' '/etc' '../x' 'backend/../docs' 'docs/*' 'backend' '-x' ''; do
  raw_run run --task-id T --base main --spec "$SPEC" --scope "$sc" --non-interactive
  assert_eq "rejects scope [$sc]" 2 "$RC"
done
printf '# Task\nno criteria here\n' > "$SB/nocrit.md"
raw_run run --task-id T --base main --spec "$SB/nocrit.md" --scope backend/src --non-interactive
assert_eq "rejects spec without acceptance criteria" 2 "$RC"
assert_contains "criteria error is explicit" "$OUT" "Acceptance"
raw_run run --task-id T --base main --spec "$SB/missing.md" --scope backend/src --non-interactive; assert_eq "rejects missing spec" 2 "$RC"
raw_run run --task-id T --base main --spec "$SPEC" --scope backend/src --non-interactive --timeout abc; assert_eq "rejects bad --timeout" 2 "$RC"
raw_run run --task-id T --base main --spec "$SPEC" --scope backend/src --non-interactive --finding 'a b';  assert_eq "rejects bad --finding" 2 "$RC"
raw_run run --task-id T --base -x --spec "$SPEC" --scope backend/src --non-interactive;       assert_eq "rejects base starting with dash" 2 "$RC"
raw_run run --task-id T --base nope --spec "$SPEC" --scope backend/src --non-interactive;     assert_eq "unknown base ref rc 3" 3 "$RC"
raw_run run --task-id T --base main --spec "$SPEC" --scope backend/nonexistent/dir/file --non-interactive; assert_eq "scope absent from base rc 3" 3 "$RC"
raw_run run --task-id T --base main --spec "$SPEC" --scope backend/src --non-interactive --worktrees-root "$ORIG/inside"
assert_eq "root inside repo rejected (rc 3)" 3 "$RC"
RAW_AGY=/nonexistent/agy raw_run run --task-id T --base main --spec "$SPEC" --scope backend/src --non-interactive
assert_eq "missing agy rc 3" 3 "$RC"; unset RAW_AGY
OUT=$(cd "$ORIG" && env AGY_BIN="$FAKE" AGY_WORKTREE_CHILD=1 bash "$SCRIPT" run --task-id T --base main --spec "$SPEC" --scope backend/src --non-interactive 2>&1) && RC=0 || RC=$?
assert_eq "recursion guard blocks run when AGY_WORKTREE_CHILD is set" 3 "$RC"
assert_contains "recursion guard message" "$OUT" "recursion guard"
OUT=$(cd "$SB" && env AGY_BIN="$FAKE" bash "$SCRIPT" run --task-id T --base main --spec "$SPEC" --scope backend/src --non-interactive 2>&1) && RC=0 || RC=$?
assert_eq "outside a git repo rc 3" 3 "$RC"
# uncommitted context doc must stop the run and ask for a proper base
echo "draft" > "$ORIG/docs/UNCOMMITTED.md"
raw_run run --task-id T --base main --spec "$SPEC" --scope backend/src --non-interactive --context docs/UNCOMMITTED.md
assert_eq "uncommitted context doc stops the run (rc 3)" 3 "$RC"
assert_contains "context error mentions uncommitted" "$OUT" "uncommitted"
assert_eq "validation failures created nothing" "yes" "$(no_run_dirs && echo yes || echo no)"
assert_eq "validation failures created no branches" "" "$(git -C "$ORIG" branch --list 'agent/*')"

# ================================================================ 2. dry-run and stdin spec
echo "# 2. dry-run"
OUT=$(cd "$ORIG" && env AGY_BIN="$FAKE" AGY_SETTINGS_FILE=/nonexistent bash "$SCRIPT" run --task-id DRY --base main --spec - \
      --scope backend/src --finding SEC-003 --context docs/TESTING.md --non-interactive --dry-run < "$SPEC" 2>&1) && RC=0 || RC=$?
assert_eq "dry-run rc 0" 0 "$RC"
assert_contains "dry-run prints the prompt" "$OUT" "WORKER agent"
assert_contains "dry-run includes finding" "$OUT" "SEC-003"
assert_contains "dry-run includes forbidden findings.md" "$OUT" "docs/audit/findings.md"
assert_contains "dry-run includes spec text" "$OUT" "tests for App pass"
assert_contains "dry-run includes recursion warning" "$OUT" "NEVER start another agy"
assert_eq "dry-run created nothing" "yes" "$(no_run_dirs && echo yes || echo no)"

# ================================================================ 3. collisions
echo "# 3. existing path / branch"
mkdir -p "$ROOT/COL-20260101T000099Z"
RC=0; OUT=$(cd "$ORIG" && env AGY_BIN="$FAKE" AGENT_WT_TIMESTAMP=20260101T000099Z AGY_SETTINGS_FILE=/nonexistent bash "$SCRIPT" run --task-id COL --base main --spec "$SPEC" --scope backend/src --non-interactive 2>&1) || RC=$?
assert_eq "existing run path rejected (rc 3)" 3 "$RC"
assert_contains "path error" "$OUT" "already exists"
git -C "$ORIG" branch agent/COLB-20260101T000098Z main
RC=0; OUT=$(cd "$ORIG" && env AGY_BIN="$FAKE" AGENT_WT_TIMESTAMP=20260101T000098Z AGY_SETTINGS_FILE=/nonexistent bash "$SCRIPT" run --task-id COLB --base main --spec "$SPEC" --scope backend/src --non-interactive 2>&1) || RC=$?
assert_eq "existing branch rejected (rc 3)" 3 "$RC"
assert_contains "branch error" "$OUT" "branch already exists"
assert_missing "no run dir for colliding branch" "$ROOT/COLB-20260101T000098Z"

# ================================================================ 4. dirty origin + isolated creation
echo "# 4. dirty origin and isolated worktree creation"
echo "local edit" >> "$ORIG/README.md"
echo "scratch" > "$ORIG/notes.txt"
echo "SECRET=1" > "$ORIG/.env"
STATUS_BEFORE=$(git -C "$ORIG" status --short)
HEAD_BEFORE=$(git -C "$ORIG" rev-parse HEAD)
BRANCH_BEFORE=$(git -C "$ORIG" branch --show-current)
README_BEFORE=$(cat "$ORIG/README.md")
agy_run OKRUN ok --finding SEC-003 --context docs/TESTING.md
assert_eq "successful fake run rc 0" 0 "$RC"
assert_contains "warns the dirty workspace is not included" "$OUT" "uncommitted changes"
assert_contains "warning says nothing was stashed" "$OUT" "Nothing was stashed"
assert_eq "original status unchanged" "$STATUS_BEFORE" "$(git -C "$ORIG" status --short)"
assert_eq "original HEAD unchanged" "$HEAD_BEFORE" "$(git -C "$ORIG" rev-parse HEAD)"
assert_eq "original branch unchanged" "$BRANCH_BEFORE" "$(git -C "$ORIG" branch --show-current)"
assert_eq "original README content untouched" "$README_BEFORE" "$(cat "$ORIG/README.md")"
assert_exists "uncommitted notes.txt still in original" "$ORIG/notes.txt"
assert_eq "meta ORIGIN_DIRTY=1" 1 "$(meta "$RUNDIR" ORIGIN_DIRTY)"
assert_eq "meta tracked dirty count" 1 "$(meta "$RUNDIR" ORIGIN_DIRTY_TRACKED)"
assert_eq "meta untracked dirty count (notes.txt + UNCOMMITTED.md)" 2 "$(meta "$RUNDIR" ORIGIN_DIRTY_UNTRACKED)"
assert_eq "meta BASE_COMMIT recorded" "$BASE_COMMIT" "$(meta "$RUNDIR" BASE_COMMIT)"
assert_eq "meta BRANCH" "agent/$RID" "$(meta "$RUNDIR" BRANCH)"
assert_exists "worktree created" "$WT/.git"
case "$WT" in "$ORIG"/*) t_bad "worktree is outside the original repo" "$WT" ;; *) t_ok "worktree is outside the original repo" ;; esac
assert_eq "worktree is a sibling-root path" "$SB/repo-agent-worktrees" "$ROOT"
assert_contains "git lists the worktree" "$(git -C "$ORIG" worktree list)" "$WT"
assert_eq "worktree on exclusive branch" "agent/$RID" "$(git -C "$WT" branch --show-current)"
assert_eq "worktree started at base commit" "$BASE_COMMIT" "$(git -C "$WT" rev-parse HEAD)"
assert_missing "uncommitted notes.txt NOT copied" "$WT/notes.txt"
assert_missing ".env NOT copied" "$WT/.env"
assert_eq "worktree README is the committed one" "readme v1" "$(cat "$WT/README.md")"
assert_contains "fake agy got -p" "$(cat "$FAKELOG")" "arg1=-p"
assert_contains "fake agy ran inside the worktree" "$(cat "$FAKELOG")" "pwd=$WT"
assert_contains "child marker env set" "$(cat "$FAKELOG")" "child=1"
assert_contains "parent run id env set" "$(cat "$FAKELOG")" "parent=$RID"
assert_contains "prompt reached agy as one argument" "$(cat "$FAKELOG")" "prompt_has_worker=1"
assert_not_contains "no permission bypass flag" "$(cat "$FAKELOG")" "dangerously"
assert_eq "agy got exactly -p and one prompt" "argc=2" "$(grep '^argc=' "$FAKELOG")"
assert_contains "prompt lists scope" "$(cat "$RUNDIR/prompt.md")" "backend/src"
assert_contains "prompt lists finding" "$(cat "$RUNDIR/prompt.md")" "SEC-003"
assert_contains "prompt lists context doc" "$(cat "$RUNDIR/prompt.md")" "docs/TESTING.md"
assert_contains "prompt forbids repo_audit.py" "$(cat "$RUNDIR/prompt.md")" "scripts/repo_audit.py"
assert_contains "prompt forbids commits" "$(cat "$RUNDIR/prompt.md")" "git commit"
assert_contains "prompt defines report format" "$(cat "$RUNDIR/prompt.md")" "## Tests executed"
assert_contains "report captured" "$(cat "$RUNDIR/report.md")" "# Report fake"
assert_eq "exit_code file" "0" "$(cat "$RUNDIR/exit_code")"
assert_contains "changed-files lists new file" "$(cat "$RUNDIR/changed-files.txt")" "backend/src/main/java/Fixed.java"
assert_contains "patch includes untracked new file" "$(cat "$RUNDIR/changes.patch")" "class Fixed"
assert_eq "STATUS completed" "completed" "$(meta "$RUNDIR" STATUS)"
assert_eq "REVIEW_STATE pending" "pending" "$(meta "$RUNDIR" REVIEW_STATE)"
assert_eq "agent did not commit or stage" "" "$(git -C "$WT" diff --cached --name-only)"
assert_contains "summary says nothing integrated automatically" "$OUT" "nothing was integrated or removed automatically"
OK_RID=$RID; OK_RUNDIR=$RUNDIR; OK_WT=$WT

# ================================================================ 5. preservation of worktrees with changes
echo "# 5. cleanup preserves worktrees with changes"
run_cmd CRC COUT -- bash -c "cd '$ORIG' && bash '$SCRIPT' cleanup --run-id '$OK_RID'"
assert_eq "cleanup refused while review pending (rc 7)" 7 "$CRC"
assert_contains "refusal says pending review" "$COUT" "pending review"
bash -c "cd '$ORIG' && bash '$SCRIPT' mark-reviewed --run-id '$OK_RID'" >/dev/null 2>&1
assert_eq "mark-reviewed recorded" reviewed "$(meta "$OK_RUNDIR" REVIEW_STATE)"
run_cmd CRC COUT -- bash -c "cd '$ORIG' && bash '$SCRIPT' cleanup --run-id '$OK_RID'"
assert_eq "cleanup still refused: worktree has changes (rc 7)" 7 "$CRC"
assert_contains "refusal explains changes" "$COUT" "uncommitted or untracked"
assert_contains "refusal shows the path" "$COUT" "$OK_WT"
assert_contains "refusal shows the branch" "$COUT" "agent/$OK_RID"
assert_contains "refusal shows the changed file" "$COUT" "Fixed.java"
assert_contains "refusal states nothing removed" "$COUT" "Nothing was removed"
assert_exists "dirty worktree preserved" "$OK_WT/backend/src/main/java/Fixed.java"
assert_exists "dirty run folder preserved" "$OK_RUNDIR/report.md"
assert_eq "branch preserved" yes "$(git -C "$ORIG" show-ref --verify --quiet "refs/heads/agent/$OK_RID" && echo yes || echo no)"

# ================================================================ 6. failed / timed out / violating runs
echo "# 6. failure handling and guardrails"
agy_run FAILRUN fail
assert_eq "agy failure -> rc 4" 4 "$RC"
assert_eq "STATUS failed" failed "$(meta "$RUNDIR" STATUS)"
assert_eq "agy exit code recorded" 3 "$(meta "$RUNDIR" AGY_EXIT)"
assert_contains "stderr log preserved" "$(cat "$RUNDIR/stderr.log")" "boom from fake agy"
assert_contains "summary refuses to claim success" "$OUT" "NOT confirmed complete"
assert_not_contains "summary does not say completed" "$OUT" "STATUS: **completed**"
assert_exists "failed worktree preserved" "$WT/.git"
assert_eq "failed run is pending review" pending "$(meta "$RUNDIR" REVIEW_STATE)"

agy_run SLOWRUN sleep --timeout 1
assert_eq "timeout -> rc 5" 5 "$RC"
assert_eq "STATUS timeout" timeout "$(meta "$RUNDIR" STATUS)"
assert_contains "timeout summary not successful" "$OUT" "NOT confirmed complete"

agy_run VIOL1 scope
assert_eq "out-of-scope change -> rc 6" 6 "$RC"
assert_contains "violation OUT_OF_SCOPE" "$(cat "$RUNDIR/violations.txt")" "OUT_OF_SCOPE: frontend/out.txt"
assert_eq "STATUS completed-with-violations" completed-with-violations "$(meta "$RUNDIR" STATUS)"
agy_run VIOL2 protected
assert_eq "protected file change -> rc 6" 6 "$RC"
assert_contains "violation FORBIDDEN_PATH" "$(cat "$RUNDIR/violations.txt")" "FORBIDDEN_PATH: docs/audit/findings.md"
agy_run VIOL3 migration
assert_eq "applied migration change -> rc 6" 6 "$RC"
assert_contains "violation APPLIED_MIGRATION_CHANGED" "$(cat "$RUNDIR/violations.txt")" "APPLIED_MIGRATION_CHANGED"
agy_run VIOL4 commit
assert_eq "agent commit -> rc 6" 6 "$RC"
assert_contains "violation AGENT_COMMITS" "$(cat "$RUNDIR/violations.txt")" "AGENT_COMMITS"
COMMIT_RID=$RID; COMMIT_RUNDIR=$RUNDIR; COMMIT_WT=$WT
agy_run VIOL5 origin
assert_eq "origin tampering -> rc 6" 6 "$RC"
assert_contains "violation ORIGIN_MODIFIED_DURING_RUN" "$(cat "$RUNDIR/violations.txt")" "ORIGIN_MODIFIED_DURING_RUN"
# restore the README line the fake appended so later assertions stay meaningful
printf '%s\n' "$README_BEFORE" > "$ORIG/README.md"

# a branch carrying agent commits must never be cleaned
bash -c "cd '$ORIG' && bash '$SCRIPT' mark-reviewed --run-id '$COMMIT_RID'" >/dev/null 2>&1
run_cmd CRC COUT -- bash -c "cd '$ORIG' && bash '$SCRIPT' cleanup --run-id '$COMMIT_RID'"
assert_eq "worktree with exclusive commits refused (rc 7)" 7 "$CRC"
assert_contains "refusal cites unique work" "$COUT" "not in the base"
assert_exists "committed worktree preserved" "$COMMIT_WT/.git"

# interruption keeps everything
if command -v setsid >/dev/null 2>&1; then
  ITS=$(next_ts); IRID="INTR-$ITS"
  ( cd "$ORIG" && exec setsid env AGY_BIN="$FAKE" FAKE_AGY_MODE=sleep FAKE_AGY_LOG="$FAKELOG" AGENT_WT_TIMESTAMP="$ITS" AGY_SETTINGS_FILE=/nonexistent \
      bash "$SCRIPT" run --task-id INTR --base main --spec "$SPEC" --scope backend/src --non-interactive >"$SB/intr.out" 2>&1 ) &
  IPID=$!
  for _ in $(seq 1 100); do [ "$(meta "$ROOT/$IRID" STATUS 2>/dev/null)" = running ] && break; sleep 0.1; done
  kill -TERM -- "-$IPID" 2>/dev/null
  wait "$IPID"; IRC=$?
  assert_eq "SIGTERM -> rc 130" 130 "$IRC"
  assert_eq "STATUS interrupted" interrupted "$(meta "$ROOT/$IRID" STATUS)"
  assert_exists "interrupted worktree preserved" "$ROOT/$IRID/worktree/.git"
else
  echo "  skip - setsid unavailable; interruption test not run"
fi

# ================================================================ 7. clean worktree cleanup
echo "# 7. cleanup of a clean worktree"
agy_run CLEANRUN noop
assert_eq "noop run rc 0" 0 "$RC"
CL_RID=$RID; CL_RUNDIR=$RUNDIR; CL_WT=$WT
assert_eq "noop run has no changes" 0 "$(meta "$CL_RUNDIR" CHANGED_FILES)"
run_cmd CRC COUT -- bash -c "cd '$ORIG' && bash '$SCRIPT' cleanup --run-id '$CL_RID'"
assert_eq "clean but unreviewed worktree is kept (rc 7)" 7 "$CRC"
assert_exists "unreviewed worktree preserved" "$CL_WT/.git"
bash -c "cd '$ORIG' && bash '$SCRIPT' mark-reviewed --run-id '$CL_RID'" >/dev/null 2>&1
HEAD_BEFORE_CLEAN=$(git -C "$ORIG" rev-parse HEAD)
STATUS_BEFORE_CLEAN=$(git -C "$ORIG" status --short)
run_cmd CRC COUT -- bash -c "cd '$ORIG' && bash '$SCRIPT' cleanup --run-id '$CL_RID'"
assert_eq "reviewed clean worktree removed (rc 0)" 0 "$CRC"
assert_missing "worktree directory removed" "$CL_WT"
assert_eq "git no longer lists it" "" "$(git -C "$ORIG" worktree list | grep -F "$CL_WT")"
assert_eq "temp branch deleted (no unique work)" "" "$(git -C "$ORIG" branch --list "agent/$CL_RID")"
assert_exists "run logs kept" "$CL_RUNDIR/report.md"
assert_exists "meta kept" "$CL_RUNDIR/meta.env"
assert_eq "status cleaned" cleaned "$(meta "$CL_RUNDIR" STATUS)"
assert_eq "cleanup did not move original HEAD" "$HEAD_BEFORE_CLEAN" "$(git -C "$ORIG" rev-parse HEAD)"
assert_eq "cleanup did not touch original status" "$STATUS_BEFORE_CLEAN" "$(git -C "$ORIG" status --short)"
run_cmd CRC COUT -- bash -c "cd '$ORIG' && bash '$SCRIPT' cleanup --run-id '$CL_RID'"
assert_eq "second cleanup refused (nothing registered)" 7 "$CRC"

# ================================================================ 8. foreign worktrees and the original workspace
echo "# 8. foreign worktrees / original workspace"
run_cmd CRC COUT -- bash -c "cd '$ORIG' && bash '$SCRIPT' cleanup --run-id '../etc'"
assert_eq "path traversal in run-id rejected (rc 2)" 2 "$CRC"
run_cmd CRC COUT -- bash -c "cd '$ORIG' && bash '$SCRIPT' cleanup --run-id 'NOPE-20260101T000000Z'"
assert_eq "unknown run-id refused (rc 7)" 7 "$CRC"
run_cmd CRC COUT -- bash -c "cd '$ORIG' && bash '$SCRIPT' cleanup --run-id '$OK_RID' --worktrees-root '$ORIG/sub'"
assert_eq "root inside original repo refused (rc 7)" 7 "$CRC"
assert_exists "original untouched after in-repo root refusal" "$ORIG/README.md"

# (a) hand-made worktree, no metadata
FA_RID="FOREIGN-20260101T000050Z"
git -C "$ORIG" worktree add -q -b "agent/$FA_RID" "$ROOT/$FA_RID/worktree" main
run_cmd CRC COUT -- bash -c "cd '$ORIG' && bash '$SCRIPT' cleanup --run-id '$FA_RID'"
assert_eq "worktree without this system's metadata refused" 7 "$CRC"
assert_exists "foreign worktree preserved" "$ROOT/$FA_RID/worktree/.git"
# (b) metadata with the wrong signature
printf 'SCHEMA=other\nCREATED_BY=someone\n' > "$ROOT/$FA_RID/meta.env"
run_cmd CRC COUT -- bash -c "cd '$ORIG' && bash '$SCRIPT' cleanup --run-id '$FA_RID'"
assert_eq "unsigned metadata refused" 7 "$CRC"
assert_exists "foreign worktree still preserved" "$ROOT/$FA_RID/worktree/.git"
# (c) valid signature but path not registered in git
FC_RID="GHOST-20260101T000051Z"
mkdir -p "$ROOT/$FC_RID/worktree"
cat > "$ROOT/$FC_RID/meta.env" <<EOF
SCHEMA=agent-worktree-run/1
CREATED_BY=agent-worktree-agy.sh
RUN_ID=$FC_RID
BRANCH=agent/$FC_RID
BASE_COMMIT=$BASE_COMMIT
ORIGIN_ROOT=$ORIG
WORKTREE_PATH=$ROOT/$FC_RID/worktree
REVIEW_STATE=reviewed
STATUS=completed
EOF
run_cmd CRC COUT -- bash -c "cd '$ORIG' && bash '$SCRIPT' cleanup --run-id '$FC_RID'"
assert_eq "signed but unregistered path refused" 7 "$CRC"
assert_contains "refusal says not registered" "$COUT" "not a registered worktree"
assert_exists "unregistered directory preserved" "$ROOT/$FC_RID/worktree"
# (d) metadata pointing at the original workspace
FD_RID="EVIL-20260101T000052Z"
mkdir -p "$ROOT/$FD_RID"
sed -e "s#$FC_RID#$FD_RID#g" -e "s#^WORKTREE_PATH=.*#WORKTREE_PATH=$ORIG#" "$ROOT/$FC_RID/meta.env" > "$ROOT/$FD_RID/meta.env"
run_cmd CRC COUT -- bash -c "cd '$ORIG' && bash '$SCRIPT' cleanup --run-id '$FD_RID'"
assert_eq "metadata pointing at original refused" 7 "$CRC"
assert_exists "original workspace intact (d)" "$ORIG/.git"
# (e) run folder whose 'worktree' is a symlink to the original workspace
FE_RID="LINK-20260101T000053Z"
mkdir -p "$ROOT/$FE_RID"
ln -s "$ORIG" "$ROOT/$FE_RID/worktree"
sed -e "s#$FC_RID#$FE_RID#g" "$ROOT/$FC_RID/meta.env" > "$ROOT/$FE_RID/meta.env"
run_cmd CRC COUT -- bash -c "cd '$ORIG' && bash '$SCRIPT' cleanup --run-id '$FE_RID'"
assert_eq "symlink to original workspace refused" 7 "$CRC"
assert_contains "refusal names the original workspace" "$COUT" "original workspace"
assert_exists "original workspace intact (e)" "$ORIG/README.md"
assert_exists "original .git intact (e)" "$ORIG/.git"
assert_eq "original files still tracked" "App.java" "$(git -C "$ORIG" ls-files backend/src/main/java | xargs -n1 basename | head -1)"

# ================================================================ 9. run from a linked worktree
echo "# 9. running from inside a worktree"
RC=0; OUT=$(cd "$OK_WT" && env AGY_BIN="$FAKE" AGY_SETTINGS_FILE=/nonexistent bash "$SCRIPT" run --task-id NEST --base main --spec "$SPEC" --scope backend/src --non-interactive 2>&1) || RC=$?
assert_eq "refuses to run from a linked worktree" 3 "$RC"
assert_contains "message explains" "$OUT" "linked worktree"

# ================================================================ 9b. interactive mode
echo "# 9b. --interactive (agy -i in a terminal)"
raw_run run --task-id T --base main --spec "$SPEC" --scope backend/src --non-interactive --interactive
assert_eq "rejects both --non-interactive and --interactive" 2 "$RC"
IT_TS=$(next_ts)
RC=0; OUT=$(cd "$ORIG" && env -u DISPLAY -u WAYLAND_DISPLAY -u AGY_TERMINAL AGY_BIN="$FAKE" AGENT_WT_TIMESTAMP="$IT_TS" \
      AGY_SETTINGS_FILE=/nonexistent bash "$SCRIPT" run --task-id ITNOTERM --base main --spec "$SPEC" --scope backend/src --interactive 2>&1) || RC=$?
assert_eq "interactive without a terminal rc 3" 3 "$RC"
assert_missing "no run folder without a terminal" "$ROOT/ITNOTERM-$IT_TS"
IT_TS=$(next_ts); IT_DIR="$ROOT/ITOK-$IT_TS"
# AGY_TERMINAL=bash runs the session script synchronously in place of a terminal window
RC=0; OUT=$(cd "$ORIG" && env AGY_BIN="$FAKE" AGY_TERMINAL=bash FAKE_AGY_MODE=ok FAKE_AGY_LOG="$FAKELOG" AGENT_WT_TIMESTAMP="$IT_TS" \
      AGY_SETTINGS_FILE=/nonexistent bash "$SCRIPT" run --task-id ITOK --base main --spec "$SPEC" --scope backend/src --interactive 2>&1 </dev/null) || RC=$?
assert_eq "interactive run rc 0" 0 "$RC"
assert_eq "agy called with -i" "-i" "$(sed -n 's/^arg1=//p' "$FAKELOG" | head -n 1)"
assert_eq "prompt passed as one argument" "2" "$(sed -n 's/^argc=//p' "$FAKELOG")"
assert_eq "interactive child runs inside the worktree" "$IT_DIR/worktree" "$(sed -n 's/^pwd=//p' "$FAKELOG")"
assert_eq "interactive child has recursion guard" "1" "$(sed -n 's/^child=//p' "$FAKELOG")"
assert_eq "session exit recorded" "0" "$(cat "$IT_DIR/session_exit")"
assert_contains "prompt tells the agent where to write the report" "$(cat "$IT_DIR/prompt.md")" "$IT_DIR/report.md"
assert_contains "placeholder report when the agent wrote none" "$(cat "$IT_DIR/report.md")" "did not write report.md"
assert_eq "evidence collected after the session" "backend/src/main/java/Fixed.java" "$(cat "$IT_DIR/changed-files.txt")"

# ================================================================ 10. static safety of the script
echo "# 10. static checks on the script"
# comments and the worker prompt (which names the git commands it forbids) are excluded
FORBIDDEN_RE='--force|reset --hard|rm -rf|rm -r |branch -D|dangerously|\beval\b|git clean|git stash|git add|git commit'
assert_eq "static check sanity: grep can see the script" yes "$(grep -q -e 'worktree remove' "$SCRIPT" && echo yes || echo no)"
# self-check of the detector on a poisoned copy (sandbox only)
printf 'git worktree remove --force x\n' > "$SB/poisoned.sh"
assert_contains "static check detector flags --force" "$(grep -nE -e "$FORBIDDEN_RE" "$SB/poisoned.sh" || true)" "--force"
HITS=$(grep -nE -e "$FORBIDDEN_RE" "$SCRIPT" | grep -vE '^[0-9]+:[[:space:]]*#' | grep -v 'Do NOT run' || true)
assert_eq "script contains no forced/destructive commands" "" "$HITS"
assert_eq "script passes bash -n" "ok" "$(bash -n "$SCRIPT" && echo ok)"

t_summary
