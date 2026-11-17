#!/usr/bin/env bash
# Offline tests for .claude/hooks/protect-paths.sh (no Claude, no network).
# Usage: bash scripts/tests/protect-paths.test.sh
set -u
HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO=$(cd "$HERE/../.." && pwd)
HOOK="$REPO/.claude/hooks/protect-paths.sh"
# shellcheck source=lib.sh
. "$HERE/lib.sh"

SB=$(t_sandbox)
P="$SB/project"
mkdir -p "$P/backend/src/main/resources/db/migration" "$P/docs/audit/evidence" "$P/docs/audit/generated" "$P/scripts" "$P/backend/src/main/java"
git -C "$P" init -q
git -C "$P" config user.email t@example.invalid
git -C "$P" config user.name tester
printf 'CREATE TABLE a(id int);\n' > "$P/backend/src/main/resources/db/migration/V1__init.sql"
printf 'x\n' > "$P/docs/audit/findings.md"
git -C "$P" add -A
git -C "$P" commit -q -m base
printf 'CREATE TABLE b(id int);\n' > "$P/backend/src/main/resources/db/migration/V2__uncommitted.sql"

# hook_case NAME EXPECTED_RC TOOL FILE
hook_case() {
  local name=$1 want=$2 tool=$3 file=$4 rc out json
  json=$(jq -n --arg t "$tool" --arg f "$file" --arg c "$P" \
    '{hook_event_name:"PreToolUse",tool_name:$t,cwd:$c,tool_input:{file_path:$f}}')
  run_cmd rc out -- env CLAUDE_PROJECT_DIR="$P" bash "$HOOK" <<<"$json"
  assert_eq "$name (rc)" "$want" "$rc"
  LAST_OUT=$out
}

echo "protect-paths hook (sandbox: $SB)"
hook_case "blocks findings.md"                2 Edit  "$P/docs/audit/findings.md"
assert_contains "findings.md message names the file" "$LAST_OUT" "docs/audit/findings.md"
hook_case "blocks evidence file"              2 Write "$P/docs/audit/evidence/new.md"
hook_case "blocks generated file"             2 Edit  "$P/docs/audit/generated/files.csv"
hook_case "blocks repo_audit.py"              2 Edit  "$P/scripts/repo_audit.py"
hook_case "blocks relative path"              2 Edit  "docs/audit/findings.md"
hook_case "blocks .. traversal into protected" 2 Edit "$P/backend/../docs/audit/findings.md"
hook_case "blocks .env"                       2 Write "$P/.env"
hook_case "blocks nested backend/.env"        2 Write "$P/backend/.env"
hook_case "blocks committed migration"        2 Edit  "$P/backend/src/main/resources/db/migration/V1__init.sql"
assert_contains "migration message suggests a new migration" "$LAST_OUT" "new V<next>"
hook_case "allows .env.example"               0 Edit  "$P/.env.example"
hook_case "allows new (uncommitted) migration" 0 Edit "$P/backend/src/main/resources/db/migration/V2__uncommitted.sql"
hook_case "allows brand-new migration"        0 Write "$P/backend/src/main/resources/db/migration/V3__new.sql"
hook_case "allows application source"         0 Edit  "$P/backend/src/main/java/App.java"
hook_case "allows files outside the project"  0 Edit  "/tmp/somewhere/else.txt"
hook_case "allows docs outside audit dirs"    0 Edit  "$P/docs/TESTING.md"

# NotebookEdit uses notebook_path
json=$(jq -n --arg f "$P/docs/audit/evidence/n.ipynb" --arg c "$P" '{cwd:$c,tool_name:"NotebookEdit",tool_input:{notebook_path:$f}}')
run_cmd rc out -- env CLAUDE_PROJECT_DIR="$P" bash "$HOOK" <<<"$json"
assert_eq "blocks NotebookEdit notebook_path" 2 "$rc"

# no file path at all (e.g. other tool) -> allow
run_cmd rc out -- env CLAUDE_PROJECT_DIR="$P" bash "$HOOK" <<<'{"tool_name":"Edit","tool_input":{}}'
assert_eq "allows input without a path" 0 "$rc"

# unparseable input -> fail closed
run_cmd rc out -- env CLAUDE_PROJECT_DIR="$P" bash "$HOOK" <<<'not json'
assert_eq "fails closed on unparseable input" 2 "$rc"

# settings.json is valid JSON and wires the hook
run_cmd rc out -- jq -e '.hooks.PreToolUse[0].hooks[0].command | contains("protect-paths.sh")' "$REPO/.claude/settings.json"
assert_eq "settings.json wires the hook" 0 "$rc"
run_cmd rc out -- jq -e '.permissions.deny | index("Read(**/.env)") != null' "$REPO/.claude/settings.json"
assert_eq "settings.json denies reading .env" 0 "$rc"
# .env.example must stay readable
run_cmd rc out -- jq -e '[.permissions.deny[] | select(test("env\\.example"))] | length == 0' "$REPO/.claude/settings.json"
assert_eq "settings.json does not deny .env.example" 0 "$rc"

t_summary
