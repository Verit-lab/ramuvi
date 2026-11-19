#!/usr/bin/env bash
# DESTRUCTIVE ONLY TO A UNIQUE, EPHEMERAL, SELF-CREATED COMPOSE PROJECT.
# Never touches the configured project of the operator or any production DB.
set -Eeuo pipefail
umask 077
fail() { printf 'ERRO: %s\n' "$*" >&2; exit 1; }

usage() {
  printf 'Uso: %s --backend-jar /caminho/ramuvi-api.jar --confirm-synthetic-only\n' "$0"
}
[[ "$#" -eq 3 && "$1" == --backend-jar && "$3" == --confirm-synthetic-only ]] ||
  { usage >&2; exit 2; }
jar_path="$2"
[[ -f "$jar_path" ]] || fail "JAR de backend inexistente"
for tool in docker java python3; do
  command -v "$tool" >/dev/null 2>&1 || fail "dependência ausente: $tool"
done

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WORK_DIR="$(mktemp -d -t ramuvi-recovery-drill-XXXXXXXX)"
project="ramuvi_recovery_drill_$$"
[[ "$project" == ramuvi_recovery_drill_* ]] || fail "nome de projeto de teste inválido"
export COMPOSE_FILE="$ROOT/compose.yaml"
export COMPOSE_PROJECT_NAME="$project"
export POSTGRES_BIND=127.0.0.1
export POSTGRES_PORT="$(python3 -c 'import socket; s=socket.socket(); s.bind(("127.0.0.1",0)); print(s.getsockname()[1]); s.close()')"
export POSTGRES_DB=ramuvi_recovery_drill
export POSTGRES_USER=ramuvi_drill
export POSTGRES_PASSWORD=synthetic_test_only_never_use_in_production
export BACKUP_DIR="$WORK_DIR"
export BACKUP_KEEP=0
export BACKUP_REQUIRE_ENCRYPTION=0
export BACKUP_REQUIRE_CHECKSUM=1
backend_pid=""

cleanup() {
  result=$?
  trap - EXIT
  if [[ -n "$backend_pid" ]]; then
    kill "$backend_pid" >/dev/null 2>&1 || true
    wait "$backend_pid" >/dev/null 2>&1 || true
  fi
  # The only Compose project ever addressed here was created above.
  docker compose --project-name "$project" down --volumes --remove-orphans >/dev/null 2>&1 || true
  rm -rf -- "$WORK_DIR"
  exit "$result"
}
trap cleanup EXIT

printf 'RECOVERY_DRILL_PROJECT=%s; PostgreSQL isolado na porta efêmera %s\n' "$project" "$POSTGRES_PORT" >&2
docker compose --project-name "$project" up --detach --wait postgres >/dev/null
# Flyway runs on the disposable DB only. Expose the backend on localhost with
# random port; no Google OAuth nor live external service is invoked by the drill.
(
  export SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:${POSTGRES_PORT}/${POSTGRES_DB}"
  export SPRING_DATASOURCE_USERNAME="$POSTGRES_USER"
  export SPRING_DATASOURCE_PASSWORD="$POSTGRES_PASSWORD"
  export APP_TEACHER_PASSWORD=synthetic-only-not-a-real-credential
  java -jar "$jar_path" --server.port=0 --server.address=127.0.0.1
) > "$WORK_DIR/backend.log" 2>&1 &
backend_pid=$!

expected="$(find "$ROOT/backend/src/main/resources/db/migration" -maxdepth 1 -name 'V*__*.sql' -printf '%f\n' | sed -nE 's/^V([0-9]+)__.*$/\1/p' | sort -n | tail -1)"
[[ "$expected" =~ ^[0-9]+$ ]] || fail "nenhuma migration Flyway detectada"
ready=false
for _ in {1..90}; do
  if ! kill -0 "$backend_pid" 2>/dev/null; then
    tail -18 "$WORK_DIR/backend.log" >&2
    fail "backend de teste encerrou antes de migrar banco"
  fi
  current="$(docker compose --project-name "$project" exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tAc "select coalesce(max(version::integer),0) from flyway_schema_history where success and version ~ '^[0-9]+$'" 2>/dev/null || true)"
  if [[ "$current" == "$expected" ]]; then
    ready=true
    break
  fi
  sleep 1
done
$ready || fail "Flyway não alcançou V$expected em 90 s"
kill "$backend_pid" >/dev/null 2>&1 || true
wait "$backend_pid" >/dev/null 2>&1 || true
backend_pid=""

marker="RAMUVI_RECOVERY_SYNTHETIC_$$"
docker compose --project-name "$project" exec -T postgres   psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"   -c 'create table recovery_drill_probe (marker text primary key)'   -c "insert into recovery_drill_probe(marker) values ('$marker')" >/dev/null

backup_file="$(BACKUP_REQUIRE_ENCRYPTION=0 BACKUP_KEEP=0 "$ROOT/scripts/release/backup-postgres.sh")"
[[ -s "$backup_file" && -s "${backup_file}.sha256" ]] || fail "dump/checksum não criado"
BACKUP_REQUIRE_CHECKSUM=1 "$ROOT/scripts/release/verify-backup.sh" "$backup_file" > "$WORK_DIR/verify.log"
# Run the ACTUAL destructive restore script, but only against the synthetic
# project that this script created, never against an existing deployment.
"$ROOT/scripts/release/restore-postgres.sh" "$backup_file" --confirm-replace > "$WORK_DIR/restore.log"

restored="$(docker compose --project-name "$project" exec -T postgres psql -X -tA -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c 'select marker from recovery_drill_probe')"
[[ "$restored" == "$marker" ]] || fail "dado sintético não recuperado"
post_schema="$(docker compose --project-name "$project" exec -T postgres psql -X -tA -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "select max(version::integer) from flyway_schema_history where success and version ~ '^[0-9]+$'")"
[[ "$post_schema" == "$expected" ]] || fail "Flyway não preservado após restore"

printf 'RECOVERY_DRILL_PASS=1 FLYWAY_VERSION=%s DATA_SYNTHETIC_RESTORED=1\n' "$expected"
