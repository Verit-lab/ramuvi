#!/usr/bin/env bash
# v0.12.1 extends the canonical v0.12 identity security contract with V69-V72.
set -Eeuo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$ROOT"
fail() { printf 'ERRO: %s\n' "$*" >&2; exit 1; }

RAMUVI_V0121_CONTRACT=1 "$SCRIPT_DIR/verify-v0.12-contract.sh"
grep -q 'self_enrollment_enabled boolean not null default false' backend/src/main/resources/db/migration/V70__classroom_self_enrollment.sql ||
  fail 'V70 deve manter ingresso compartilhado desativado para turmas existentes'
grep -q 'APP_STUDENT_IDENTITY_AUTO_EMAIL_LINK_ENABLED:false' backend/src/main/resources/application.yml ||
  fail 'Vinculação automática por e-mail deve iniciar OFF'
# ADR-0075 (P0): bootstrap por nome/matrícula removido; claims da aula ao vivo sem estudo.
test -s backend/src/main/resources/db/migration/V71__device_claim_scope.sql ||
  fail 'V71 (escopo do device claim) ausente'
grep -q "alter column scope set default 'LIVE'" backend/src/main/resources/db/migration/V71__device_claim_scope.sql ||
  fail 'V71 deve falhar fechado (default LIVE)'
! grep -q 'redeemWithIdentity' backend/src/main/java/br/com/arenadev/journey/JourneyRedemptionService.java ||
  fail 'Bootstrap de Jornada por identidade digitada não pode existir'
grep -q 'HttpStatus.GONE' backend/src/main/java/br/com/arenadev/study/JourneyRedemptionStudyController.java ||
  fail 'Bootstrap legado deve responder 410'
grep -q 'APP_STUDY_LIVE_DEVICE_CLAIM_STUDY_ENABLED:false' backend/src/main/resources/application.yml ||
  fail 'Opt-in de estudo por claim da aula deve iniciar OFF'
# ADR-0074 (P1): link de ingresso dedicado; classrooms.code e login nunca vinculam.
test -s backend/src/main/resources/db/migration/V72__classroom_join_links_and_requests.sql ||
  fail 'V72 (links e pedidos de ingresso) ausente'
grep -q 'APP_STUDENT_IDENTITY_CLASSROOM_JOIN_LINKS_ENABLED:false' backend/src/main/resources/application.yml ||
  fail 'Link de ingresso deve iniciar OFF'
grep -q 'GOOGLE_ISSUER.equals' backend/src/main/java/br/com/arenadev/identity/account/StudentClassroomLinkController.java ||
  fail 'E-mail só pode ser usado com issuer Google'
grep -q 'getEmailVerified()' backend/src/main/java/br/com/arenadev/identity/account/StudentClassroomLinkController.java ||
  fail 'Vínculo Google deve exigir email_verified'
! grep -q 'linkExistingStudentByVerifiedEmail' backend/src/main/java/br/com/arenadev/identity/oidc/StudentOidcUserService.java ||
  fail 'Login não pode vincular cadastro por e-mail sem prova da turma'
! grep -rq 'self_enrollment_enabled' backend/src/main/java ||
  fail 'classrooms.self_enrollment_enabled (V70) não pode autorizar ingresso'
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
printf 'Contrato v0.12.1: V69-V72, bootstrap legado removido, link de ingresso OFF, identidade verificada, offline OFF e gate operacional pendente.\n'
