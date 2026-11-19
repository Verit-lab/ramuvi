#!/usr/bin/env bash
# v0.12.1 extends the canonical v0.12 identity security contract with V69.
set -Eeuo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$ROOT"
fail() { printf 'ERRO: %s\n' "$*" >&2; exit 1; }

RAMUVI_V0121_CONTRACT=1 "$SCRIPT_DIR/verify-v0.12-contract.sh"
grep -q 'offline_allowed boolean NOT NULL DEFAULT false' backend/src/main/resources/db/migration/V69__journey_offline_availability.sql ||
  fail 'Jornadas existentes devem manter offline OFF'
grep -q 'ALTER COLUMN offline_allowed SET DEFAULT true' backend/src/main/resources/db/migration/V69__journey_offline_availability.sql ||
  fail 'Padrão das Jornadas novas não está documentado'
grep -q 'APP_STUDENT_IDENTITY_OFFLINE_DOWNLOAD_ENABLED:false' backend/src/main/resources/application.yml ||
  fail 'Flag de download offline deve iniciar OFF'
grep -q 'class VerifiedOfflineJourneyController' backend/src/main/java/br/com/arenadev/identity/account/VerifiedOfflineJourneyController.java ||
  fail 'Endpoint offline autorizado ausente'
grep -q 'verifiedAccount()' backend/src/main/java/br/com/arenadev/study/JourneyStudyService.java ||
  fail 'Política de identidade verificada ausente'
grep -q 'OFFLINE_READER' frontend/public/sw.js ||
  fail 'Service Worker offline não versionado'
test -s frontend/public/offline-journeys.js || fail 'Leitor offline ausente'
test -s frontend/public/offline-journeys.html || fail 'Página offline ausente'
python3 scripts/release/v011-readiness.py \
  --attestation docs/v0.12.1/RELEASE_ACCEPTANCE_TEMPLATE.json \
  --expected-version 0.12.1 --check-template >/dev/null ||
  fail 'Aceite v0.12.1 deve iniciar NO-GO incluindo homologação offline'
printf 'Contrato v0.12.1: V69, identidade verificada, offline OFF e gate operacional pendente.\n'
