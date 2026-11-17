#!/usr/bin/env bash
# v0.12 release contract: additive V68 identity and default-OFF flags.
# Operational publication is gated separately by release-gate.sh final.
set -Eeuo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$PROJECT_ROOT"

fail() { printf 'ERRO: %s\n' "$*" >&2; exit 1; }
"$SCRIPT_DIR/verify-v0.11-contract.sh"
[[ -f backend/src/main/resources/db/migration/V68__student_account_foundation.sql ]] || \
  fail "a fundação de identidade requer V68"
next=(backend/src/main/resources/db/migration/V69__*.sql)
[[ ! -e "${next[0]}" ]] || fail "V69 requer um gate próprio"
node -e 'if(require("./frontend/package.json").version!=="0.12.0") process.exit(1)' || \
  fail "v0.12.0 estável exige artefatos 0.12.0 alinhados"

grep -q 'uk_student_account_external_identity' backend/src/main/resources/db/migration/V68__*.sql || \
  fail "não há unicidade do identificador verificado"
grep -q 'ck_student_invite_hash' backend/src/main/resources/db/migration/V68__*.sql || \
  fail "convite deve armazenar somente hash"
grep -q 'claimEnrollmentInvite' backend/src/main/java/br/com/arenadev/identity/account/StudentAccountService.java || \
  fail "não existe validação de resgate"
# A student claim endpoint is permitted only when it derives the identity from
# Spring Security's OIDC principal, without user-controlled account/student IDs.
if grep -Rq '@PostMapping.*claim' backend/src/main/java/br/com/arenadev/identity/account; then
  grep -q '@AuthenticationPrincipal OidcUser principal' backend/src/main/java/br/com/arenadev/identity/account/StudentEnrollmentClaimController.java ||
    fail "resgate de convite deve exigir principal OIDC"
  grep -q 'requestMatchers("/api/student-account/\*\*").hasRole("STUDENT")' backend/src/main/java/br/com/arenadev/config/SecurityConfig.java ||
    fail "resgate não protegido por ROLE_STUDENT"
fi
printf 'Contrato v0.12 I0 validado: Flyway V68, contas e convites sem auto-link.\n'

# Production redirects from the IdP are cross-site top-level GET navigations.
# SameSite=Strict would lose the session-stored OAuth state/nonce on callback.
grep -Eq '^[[:space:]]*same-site: lax([[:space:]]|$)' backend/src/main/resources/application-prod.yml || \
  fail 'OIDC em produção requer SameSite=Lax para retorno do provedor'
grep -Eq '^[[:space:]]*secure: true([[:space:]]|$)' backend/src/main/resources/application-prod.yml || \
  fail 'OIDC em produção exige cookie Secure'

# I1 OIDC cannot be silently dropped from the release.
grep -q 'spring-boot-starter-security-oauth2-client' backend/pom.xml || fail 'OAuth2 client ausente'
grep -q 'OAuth2AuthorizationRequestCustomizers.withPkce()' backend/src/main/java/br/com/arenadev/config/SecurityConfig.java || fail 'PKCE não configurado'
grep -q 'findActiveVerifiedOidcAccount' backend/src/main/java/br/com/arenadev/identity/account/StudentAccountService.java || fail 'revogação da sessão não validada'
printf 'Contrato v0.12 I1: OIDC + PKCE, sessão revisada, flag default OFF.\n'

# I2: POST claim must use the authenticated OIDC principal, not request-controlled IDs.
grep -q '@AuthenticationPrincipal OidcUser principal' backend/src/main/java/br/com/arenadev/identity/account/StudentEnrollmentClaimController.java || fail 'resgate exige principal OIDC'
grep -q 'claimEnrollmentInviteForVerifiedOidc' backend/src/main/java/br/com/arenadev/identity/account/StudentEnrollmentClaimController.java || fail 'resgate não validado'
grep -q 'APP_STUDENT_IDENTITY_ENROLLMENT_CLAIM_ENABLED:false' backend/src/main/resources/application.yml || fail 'flag I2 deve estar OFF'
printf 'Contrato v0.12 I2 validado: principal autenticado, resgate via POST, flag OFF.\n'

# I3: the authenticated study API is role/CSRF protected and OFF by default.
grep -q 'APP_STUDENT_IDENTITY_STUDY_SESSION_ENABLED:false' backend/src/main/resources/application.yml || fail 'I3 deve iniciar OFF'
grep -q 'class VerifiedStudentStudyController' backend/src/main/java/br/com/arenadev/identity/account/VerifiedStudentStudyController.java || fail 'I3: roteamento ausente'
grep -q 'verifiedAccount()' backend/src/main/java/br/com/arenadev/study/JourneyStudyService.java || fail 'I3: isolamento de estudo ausente'
printf 'Contrato v0.12 I3 validado: OIDC/CSRF e Jornada isolada, flag OFF.\n'
python3 scripts/release/v011-readiness.py \
  --attestation docs/v0.12/RELEASE_ACCEPTANCE_TEMPLATE.json \
  --expected-version 0.12.0 --check-template >/dev/null || \
  fail "template de v0.12 deve iniciar como NO-GO"
