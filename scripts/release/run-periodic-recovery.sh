#!/usr/bin/env bash
# Periodic synthetic restore drill; never connects to a production database.
set -Eeuo pipefail
umask 077
JAR="${RAMUVI_RECOVERY_TEST_JAR:-}"
[[ -n "$JAR" && -f "$JAR" ]] || {
  printf 'ERRO: RAMUVI_RECOVERY_TEST_JAR deve apontar para o JAR testado\n' >&2
  exit 1
}
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
exec "$ROOT/scripts/release/rehearse-recovery.sh" \
  --backend-jar "$JAR" --confirm-synthetic-only
