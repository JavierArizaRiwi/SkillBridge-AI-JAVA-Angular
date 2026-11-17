#!/usr/bin/env bash
# Parity checks between .claude/skills/<name>/SKILL.md and .agents/skills/<name>/SKILL.md.
# Read-only; creates nothing. Usage: bash scripts/tests/skill-parity.test.sh
set -u
HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO=$(cd "$HERE/../.." && pwd)
# shellcheck source=lib.sh
. "$HERE/lib.sh"

CLAUDE_SKILLS_DIR="$REPO/.claude/skills"
AGY_SKILLS_DIR="$REPO/.agents/skills"

# First frontmatter value for KEY in FILE (only inside the leading --- block)
fm() { awk -v k="$2" 'NR==1&&$0!="---"{exit} NR>1&&$0=="---"{exit} index($0,k":")==1{sub("^"k": *","");print;exit}' "$1"; }

# Mandatory invariants every skill definition must carry on BOTH platforms
INVARIANTS=(
  "docs/TESTING.md"
  "git status --short"
  "BookingPersistenceAdapterTest"
  "NOT RUN — Not executed in this session"
  "JpaOfferingRepositoryTest"
  "NotificationFlowIT"
  "docs/audit/findings.md"
  "docs/audit/evidence"
  "scripts/repo_audit.py"
  "Verify, Do Not Mutate"
)

echo "skill parity"

assert_exists "claude skills directory exists" "$CLAUDE_SKILLS_DIR"
assert_exists "antigravity skills directory exists" "$AGY_SKILLS_DIR"

shopt -s nullglob
claude_skill_dirs=("$CLAUDE_SKILLS_DIR"/*/)
[ "${#claude_skill_dirs[@]}" -gt 0 ] && t_ok "found ${#claude_skill_dirs[@]} Claude skill definition(s)" || t_bad "no Claude skills found in $CLAUDE_SKILLS_DIR"

for csd in "${claude_skill_dirs[@]}"; do
  sname=$(basename "$csd")
  cf="$csd/SKILL.md"
  af="$AGY_SKILLS_DIR/$sname/SKILL.md"

  assert_exists "[$sname] claude SKILL.md exists" "$cf"
  assert_exists "[$sname] antigravity counterpart SKILL.md exists" "$af"
  [ -f "$cf" ] && [ -f "$af" ] || continue

  c_name=$(fm "$cf" name)
  a_name=$(fm "$af" name)
  assert_eq "[$sname] claude frontmatter name matches directory" "$sname" "$c_name"
  assert_eq "[$sname] antigravity frontmatter name matches claude name" "$c_name" "$a_name"

  [ -n "$(fm "$cf" description)" ] && t_ok "[$sname] claude description present" || t_bad "[$sname] claude description missing"
  [ -n "$(fm "$af" description)" ] && t_ok "[$sname] antigravity description present" || t_bad "[$sname] antigravity description missing"

  # Claude-specific execution control metadata
  c_invocable=$(fm "$cf" user-invocable)
  c_disable_model=$(fm "$cf" disable-model-invocation)
  assert_eq "[$sname] claude declares user-invocable: true" "true" "$c_invocable"
  assert_eq "[$sname] claude declares disable-model-invocation: true" "true" "$c_disable_model"

  # Antigravity native metadata checks (does not copy Claude-specific fields)
  [ -z "$(fm "$af" disable-model-invocation)" ] && t_ok "[$sname] antigravity does not copy Claude 'disable-model-invocation'" || t_bad "[$sname] antigravity definition copied 'disable-model-invocation'"
  [ -z "$(fm "$af" user-invocable)" ] && t_ok "[$sname] antigravity does not copy Claude 'user-invocable'" || t_bad "[$sname] antigravity definition copied 'user-invocable'"

  ctext=$(cat "$cf")
  atext=$(cat "$af")
  for inv in "${INVARIANTS[@]}"; do
    assert_contains "[$sname] claude carries '$inv'" "$ctext" "$inv"
    assert_contains "[$sname] antigravity carries '$inv'" "$atext" "$inv"
  done
done

# Ensure no Antigravity skill exists without a Claude counterpart
for asd in "$AGY_SKILLS_DIR"/*/; do
  an=$(basename "$asd")
  [ -f "$CLAUDE_SKILLS_DIR/$an/SKILL.md" ] && t_ok "[$an] antigravity skill has a Claude definition" || t_bad "[$an] antigravity skill has no .claude/skills/$an/SKILL.md"
done

t_summary
