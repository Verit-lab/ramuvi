#!/usr/bin/env bash
# v0.12.2 (ADR-0076) extends the v0.12.1 contract with V73 and four switches OFF by default.
set -Eeuo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$ROOT"
fail() { printf 'ERRO: %s\n' "$*" >&2; exit 1; }

RAMUVI_V012_EXPECTED_VERSION=0.12.2 "$SCRIPT_DIR/verify-v0.12.1-contract.sh" >/dev/null
node -e 'if(require("./frontend/package.json").version!=="0.12.2") process.exit(1)' ||
  fail "v0.12.2 exige artefatos 0.12.2 alinhados"
grep -q "<version>0.12.2</version>" backend/pom.xml || fail "backend/pom.xml deve estar em 0.12.2"
V73=backend/src/main/resources/db/migration/V73__library_snapshots_shares_personal_library.sql
test -s "$V73" || fail 'V73 (snapshots, links e Minha Biblioteca) ausente'
! grep -Eqi '^\s*(drop|alter|update|delete|truncate)\b' "$V73" ||
  fail 'V73 deve ser aditiva (somente create table/index)'
! grep -Eq 'references (classrooms|students|enrollments)' "$V73" ||
  fail 'Tabelas da Biblioteca não podem depender de turma, aluno ou matrícula'
grep -q 'token_hash bytea not null unique' "$V73" || fail 'Link deve guardar só o hash do token'
grep -q 'unique (account_id, source_resource_id)' "$V73" || fail 'Item pessoal deve ser único por conta e recurso'
for flag in APP_CLASSROOM_HUB_ENABLED APP_LIVE_MEMBER_JOIN_ENABLED APP_LIBRARY_SHARING_ENABLED APP_LIBRARY_PERSONAL_ENABLED; do
  grep -q "\${$flag:false}" backend/src/main/resources/application.yml || fail "$flag deve iniciar OFF"
done
grep -q 'uk_class_sessions_one_active_per_classroom' backend/src/main/resources/db/migration/V2__one_active_session_per_classroom.sql ||
  fail 'Índice único de sessão ativa por turma ausente'
grep -q 'findByIdForUpdate' backend/src/main/java/br/com/arenadev/session/SessionService.java ||
  fail 'Início de aula deve serializar pela turma'
! grep -rq '@Scheduled' backend/src/main/java/br/com/arenadev/session ||
  fail 'Sessão nunca pode ser encerrada por temporizador'
grep -q 'byte\[\] secret = new byte\[20\]' backend/src/main/java/br/com/arenadev/content/share/LibraryShareService.java ||
  fail 'Token de compartilhamento deve ter 160 bits'
grep -q '"/api/library/shares/preview"' backend/src/main/java/br/com/arenadev/config/SecurityConfig.java ||
  fail 'Prévia pública deve ser a única rota anônima da Biblioteca'
grep -q 'student_library_items' backend/src/main/java/br/com/arenadev/classroom/StudentAnonymizationService.java ||
  fail 'Anonimização deve apagar a coleção pessoal (LGPD)'
python3 scripts/release/v011-readiness.py \
  --attestation docs/v0.12.2/RELEASE_ACCEPTANCE_TEMPLATE.json \
  --expected-version 0.12.2 --check-template >/dev/null ||
  fail 'Aceite v0.12.2 deve iniciar NO-GO'
printf 'Contrato v0.12.2: V73 aditiva, central/ingresso de membro/compartilhamento/Minha Biblioteca OFF, gate operacional pendente.\n'
