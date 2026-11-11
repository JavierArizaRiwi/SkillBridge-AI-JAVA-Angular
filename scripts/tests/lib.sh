#!/usr/bin/env bash
# Minimal assertion helpers shared by the offline agent-tooling tests.
# Sourced, not executed. Never deletes anything: the sandbox is left in place.

T_PASS=0
T_FAIL=0
T_FAILED_NAMES=()

t_sandbox() {
  # Creates a fresh sandbox under $TEST_SANDBOX_ROOT (default: mktemp under $TMPDIR or /tmp).
  local base=${TEST_SANDBOX_ROOT:-${TMPDIR:-/tmp}}
  mkdir -p -- "$base"
  mktemp -d -- "$base/agent-tooling-test.XXXXXX"
}

t_ok() { T_PASS=$((T_PASS + 1)); printf '  ok   - %s\n' "$1"; }
t_bad() {
  T_FAIL=$((T_FAIL + 1)); T_FAILED_NAMES+=("$1")
  printf '  FAIL - %s\n' "$1"
  [ -n "${2:-}" ] && printf '         %s\n' "$2"
}

# assert_eq NAME EXPECTED ACTUAL
assert_eq() {
  if [ "$2" = "$3" ]; then t_ok "$1"; else t_bad "$1" "expected [$2] got [$3]"; fi
}
# assert_contains NAME HAYSTACK NEEDLE
assert_contains() {
  case "$2" in *"$3"*) t_ok "$1" ;; *) t_bad "$1" "missing [$3] in: ${2:0:400}" ;; esac
}
assert_not_contains() {
  case "$2" in *"$3"*) t_bad "$1" "unexpected [$3] in: ${2:0:400}" ;; *) t_ok "$1" ;; esac
}
assert_exists()  { if [ -e "$2" ]; then t_ok "$1"; else t_bad "$1" "missing path: $2"; fi; }
assert_missing() { if [ ! -e "$2" ]; then t_ok "$1"; else t_bad "$1" "path should not exist: $2"; fi; }

# run_cmd VAR_RC VAR_OUT -- cmd...   (captures merged stdout+stderr and the exit code)
run_cmd() {
  local __rcvar=$1 __outvar=$2
  shift 3
  local __o __r
  __o=$("$@" 2>&1) && __r=0 || __r=$?
  printf -v "$__rcvar" '%s' "$__r"
  printf -v "$__outvar" '%s' "$__o"
}

t_summary() {
  printf '\n%d passed, %d failed\n' "$T_PASS" "$T_FAIL"
  if [ "$T_FAIL" -ne 0 ]; then
    printf 'Failed:\n'; printf '  - %s\n' "${T_FAILED_NAMES[@]}"
    return 1
  fi
}
