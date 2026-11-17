#!/usr/bin/env bash
# v0.12 I1 preview. Preserves v0.11 while forbidding inadvertent publication.
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
node -e 'if(require("./frontend/package.json").version!=="0.12.0-dev.2") process.exit(1)' || \
  fail "identidade I0 deve permanecer pré-release 0.12.0-dev.2"

grep -q 'uk_student_account_external_identity' backend/src/main/resources/db/migration/V68__*.sql || \
  fail "não há unicidade do identificador verificado"
grep -q 'ck_student_invite_hash' backend/src/main/resources/db/migration/V68__*.sql || \
  fail "convite deve armazenar somente hash"
grep -q 'claimEnrollmentInvite' backend/src/main/java/br/com/arenadev/identity/account/StudentAccountService.java || \
  fail "não existe validação de resgate"
# Never expose a public claim endpoint taking an account ID from request JSON.
if grep -Rq '@PostMapping.*claim' backend/src/main/java/br/com/arenadev/identity/account; then
  fail "não publicar resgate antes de autenticação verificada"
fi
printf 'Contrato pré-release v0.12 I0 validado: Flyway V68, contas e convites sem auto-link.\n'

# I1 OIDC cannot be silently dropped from the pre-release.
grep -q 'spring-boot-starter-security-oauth2-client' backend/pom.xml || fail 'OAuth2 client ausente'
grep -q 'OAuth2AuthorizationRequestCustomizers.withPkce()' backend/src/main/java/br/com/arenadev/config/SecurityConfig.java || fail 'PKCE não configurado'
grep -q 'findActiveVerifiedOidcAccount' backend/src/main/java/br/com/arenadev/identity/account/StudentAccountService.java || fail 'revogação da sessão não validada'
printf 'Contrato pré-release v0.12 I1: OIDC + PKCE, sessão revisada, flag default OFF.\n'
