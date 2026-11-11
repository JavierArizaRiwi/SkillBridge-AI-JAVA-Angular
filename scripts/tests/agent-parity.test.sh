#!/usr/bin/env bash
# Parity checks between .claude/agents/*.md and .agents/agents/<name>/agent.md, plus docs/agents/REGISTRY.md.
# Read-only; creates nothing. Usage: bash scripts/tests/agent-parity.test.sh
set -u
HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO=$(cd "$HERE/../.." && pwd)
# shellcheck source=lib.sh
. "$HERE/lib.sh"

CLAUDE_DIR="$REPO/.claude/agents"
AGY_DIR="$REPO/.agents/agents"
REGISTRY="$REPO/docs/agents/REGISTRY.md"

# First frontmatter value for KEY in FILE (only inside the leading --- block)
fm() { awk -v k="$2" 'NR==1&&$0!="---"{exit} NR>1&&$0=="---"{exit} index($0,k":")==1{sub("^"k": *","");print;exit}' "$1"; }

# Invariants every agent that can launch or act as agy worker must carry on BOTH platforms
INVARIANTS=(
  "AGY_WORKTREE_CHILD"
  "docs/audit/findings.md"
  "docs/audit/generated"
  "docs/audit/evidence"
  "scripts/repo_audit.py"
  "scripts/agent-worktree-agy.sh"
  "--dangerously-skip-permissions"
  "migrations"
  ".env"
)

echo "agent parity"
shopt -s nullglob
claude_agents=("$CLAUDE_DIR"/*.md)
[ "${#claude_agents[@]}" -gt 0 ] && t_ok "found ${#claude_agents[@]} Claude agent definition(s)" || t_bad "no Claude agents found in $CLAUDE_DIR"

for cf in "${claude_agents[@]}"; do
  base=$(basename "$cf" .md)
  name=$(fm "$cf" name)
  assert_eq "[$base] claude filename equals frontmatter name" "$base" "$name"
  af="$AGY_DIR/$base/agent.md"
  assert_exists "[$base] antigravity counterpart exists" "$af"
  [ -f "$af" ] || continue
  assert_eq "[$base] antigravity name equals claude name" "$name" "$(fm "$af" name)"
  [ -n "$(fm "$cf" description)" ] && t_ok "[$base] claude description present" || t_bad "[$base] claude description missing"
  [ -n "$(fm "$af" description)" ] && t_ok "[$base] antigravity description present" || t_bad "[$base] antigravity description missing"

  ctext=$(cat "$cf"); atext=$(cat "$af")
  for inv in "${INVARIANTS[@]}"; do
    assert_contains "[$base] claude carries '$inv'" "$ctext" "$inv"
    assert_contains "[$base] antigravity carries '$inv'" "$atext" "$inv"
  done

  # Native-metadata checks: Claude restricts tools, and must not be able to edit files
  tools=$(fm "$cf" tools)
  [ -n "$tools" ] && t_ok "[$base] claude declares a tools allowlist" || t_bad "[$base] claude has no tools allowlist"
  case ",$tools," in *Edit*|*Write*|*NotebookEdit*|*MultiEdit*) t_bad "[$base] orchestrator must not have edit tools" "$tools" ;; *) t_ok "[$base] orchestrator has no edit tools" ;; esac
  # Antigravity has no tools field; make sure nobody pasted Claude's field blindly
  [ -z "$(fm "$af" tools)" ] && t_ok "[$base] antigravity does not copy Claude 'tools:' semantics" || t_bad "[$base] antigravity definition copied 'tools:'"
  assert_contains "[$base] antigravity forbids removing its own worktree" "$atext" "Never remove the worktree you are running in"
  assert_contains "[$base] antigravity documents worker mode" "$atext" "Worker mode"

  # Registry row with both paths
  row=$(grep -F "\`$base\`" "$REGISTRY" | grep -F ".claude/agents/$base.md" | grep -F ".agents/agents/$base/agent.md" || true)
  [ -n "$row" ] && t_ok "[$base] registry row lists both paths" || t_bad "[$base] registry row missing or incomplete"
done

# No Antigravity agent without a real Claude counterpart (no invented equivalents)
for ad in "$AGY_DIR"/*/; do
  an=$(basename "$ad")
  [ -f "$CLAUDE_DIR/$an.md" ] && t_ok "[$an] antigravity agent has a Claude definition" || t_bad "[$an] antigravity agent has no .claude/agents/$an.md"
done

# Registry must not mention agents that do not exist
while IFS= read -r listed; do
  [ -z "$listed" ] && continue
  [ -f "$CLAUDE_DIR/$listed.md" ] && t_ok "[$listed] registry entry exists on disk" || t_bad "[$listed] registry lists a non-existent Claude agent"
done < <(grep -oE '\.claude/agents/[A-Za-z0-9_-]+\.md' "$REGISTRY" | sed -E 's#.*/([^/]+)\.md#\1#' | sort -u)

t_summary
