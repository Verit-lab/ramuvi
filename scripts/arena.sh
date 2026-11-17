#!/usr/bin/env bash
# Classroom operation for a single teacher: install, upgrade, backup,
# restore and status, with the classroom Compose profile (only the frontend
# on the LAN). Release tooling stays in scripts/release.
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
RELEASE_DIR="$SCRIPT_DIR/release"

fail() { printf 'ERRO: %s\n' "$*" >&2; exit 1; }
info() { printf '%s\n' "$*" >&2; }

usage() {
  cat <<'EOF'
Uso: scripts/arena.sh COMANDO [opções]

  install [--no-start]   cria o .env com senhas aleatórias (se ainda não existir)
                         e sobe o Arena Dev no perfil de sala
  upgrade TAG            confere uma tag estável, faz backup, atualiza por SHA,
                         reconstrói e valida saúde; exige rollback controlado
  backup [opções]        backup do banco (repassa opções a backup-postgres.sh,
                         ex.: --keep 14 --encrypt-to <chave-age>)
  restore ARQUIVO        restaura um backup (pede confirmação)
  status                 serviços, saúde e endereço para os alunos
  https-ca ARQUIVO       exporta o certificado raiz local (perfil HTTPS) para
                         instalar nos aparelhos

Perfil: compose.yaml + compose.classroom.yaml. Com ARENA_HTTPS=1 (ou
ARENA_HTTPS=1 no .env) acrescenta compose.https.yaml (Caddy com certificado
local, necessário para a PWA instalar o service worker nos celulares).
EOF
}

env_value() {
  local key="$1"
  [[ -f "$PROJECT_ROOT/.env" ]] || return 0
  sed -n "s/^${key}=//p" "$PROJECT_ROOT/.env" | tail -1
}

# Exported so the release scripts called by backup/restore/upgrade (which run
# plain `docker compose`) recreate services with the same classroom profile.
export_compose_files() {
  COMPOSE_FILE="compose.yaml:compose.classroom.yaml"
  if [[ "${ARENA_HTTPS:-$(env_value ARENA_HTTPS)}" == "1" ]]; then
    COMPOSE_FILE+=":compose.https.yaml"
  fi
  export COMPOSE_FILE
  # Proveniência da imagem: cada build iniciado pelo script registra o SHA fonte.
  export RAMUVI_BUILD_SHA="$(git -C "$PROJECT_ROOT" rev-parse HEAD 2>/dev/null || printf unknown)"
}

compose() {
  export_compose_files
  (cd "$PROJECT_ROOT" && docker compose "$@")
}

check_https_config() {
  [[ "${ARENA_HTTPS:-$(env_value ARENA_HTTPS)}" == "1" ]] || return 0
  [[ -n "$(env_value ARENA_HOSTNAME)" ]] || \
    fail "ARENA_HTTPS=1 exige ARENA_HOSTNAME no .env (nome ou IP que os aparelhos usam)"
}

random_secret() {
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -base64 "$1" | tr -d '\n'
  else
    head -c "$1" /dev/urandom | base64 | tr -d '\n'
  fi
}

# Passwords published in .env.example; a classroom install must not use them.
DEFAULT_SECRETS=(arena123 troque-esta-senha arena-dev-change-me)

check_env_secrets() {
  local key value bad=()
  for key in POSTGRES_PASSWORD APP_TEACHER_PASSWORD; do
    value="$(env_value "$key")"
    [[ -n "$value" ]] || { bad+=("$key (vazio)"); continue; }
    for default in "${DEFAULT_SECRETS[@]}"; do
      [[ "$value" != "$default" ]] || bad+=("$key (valor de exemplo)")
    done
  done
  ((${#bad[@]} == 0)) || fail "o .env usa senhas inseguras: ${bad[*]}. Troque-as antes de subir na sala (o banco existente mantém a senha antiga do Postgres; veja o README)."
}

lan_address() {
  local address=""
  if command -v hostname >/dev/null 2>&1; then
    address="$(hostname -I 2>/dev/null | awk '{print $1}')"
  fi
  if [[ -z "$address" ]] && command -v ipconfig >/dev/null 2>&1; then
    address="$(ipconfig getifaddr en0 2>/dev/null || true)"
  fi
  printf '%s' "${address:-<IP-desta-máquina>}"
}

student_url() {
  local port scheme="http"
  port="$(env_value FRONTEND_PORT)"
  if [[ "${ARENA_HTTPS:-$(env_value ARENA_HTTPS)}" == "1" ]]; then
    printf 'https://%s' "$(env_value ARENA_HOSTNAME || true)"
    return
  fi
  printf '%s://%s:%s' "$scheme" "$(lan_address)" "${port:-3000}"
}

wait_healthy() {
  local port url curl_flags=(-fsS --max-time 3)
  port="$(env_value FRONTEND_PORT)"
  url="http://127.0.0.1:${port:-3000}/api/health/ready"
  if [[ "${ARENA_HTTPS:-$(env_value ARENA_HTTPS)}" == "1" ]]; then
    # The local CA is not in this machine's trust store by default.
    url="https://$(env_value ARENA_HOSTNAME)/api/health/ready"
    curl_flags+=(-k)
  fi
  for _ in {1..60}; do
    if curl "${curl_flags[@]}" "$url" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  return 1
}

cmd_install() {
  local start=true
  [[ "${1:-}" != "--no-start" ]] || start=false
  cd "$PROJECT_ROOT"
  if [[ -f .env ]]; then
    info "Usando o .env existente."
  else
    [[ -f .env.example ]] || fail ".env.example ausente"
    umask 077
    local teacher_password
    teacher_password="$(random_secret 12 | tr '+/' '-_' | cut -c1-16)"
    sed \
      -e "s|^POSTGRES_PASSWORD=.*|POSTGRES_PASSWORD=$(random_secret 24 | tr '+/' '-_')|" \
      -e "s|^APP_TEACHER_PASSWORD=.*|APP_TEACHER_PASSWORD=${teacher_password}|" \
      -e "s|^APP_INTEGRATIONS_CREDENTIAL_ENCRYPTION_KEY=.*|APP_INTEGRATIONS_CREDENTIAL_ENCRYPTION_KEY=$(random_secret 32)|" \
      .env.example > .env
    chmod 600 .env
    info "Criado .env com senhas aleatórias (permissão 600)."
    info "Usuário do professor: $(env_value APP_TEACHER_USERNAME)"
    info "Senha do professor:   ${teacher_password}"
    info "Guarde a senha agora; ela fica só no .env."
  fi
  check_env_secrets
  check_https_config
  if $start; then
    command -v docker >/dev/null 2>&1 || fail "Docker não encontrado"
    compose up --detach --build --wait
    cmd_status
  fi
}

cmd_upgrade() {
  local target="${1:-}"
  [[ "$#" -eq 1 && "$target" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] || \
    fail "informe uma tag estável explícita, por exemplo: scripts/arena.sh upgrade v0.11.1"
  cd "$PROJECT_ROOT"
  check_env_secrets
  check_https_config
  command -v git >/dev/null 2>&1 || fail "git não encontrado"
  [[ -z "$(git status --porcelain --untracked-files=no)" ]] || \
    fail "há alterações locais rastreadas; não é seguro atualizar"
  info "1/4 Conferindo tag imutável em origin..."
  local remote_tag_sha target_sha previous
  remote_tag_sha="$(git ls-remote --refs --tags origin "refs/tags/${target}" | awk '{print $1}')" || \
    fail "não foi possível consultar o repositório remoto"
  [[ -n "$remote_tag_sha" ]] || fail "tag ${target} não foi publicada em origin"
  git fetch --tags origin
  [[ "$(git rev-parse "refs/tags/${target}")" == "$remote_tag_sha" ]] || \
    fail "a tag local não coincide com a referência remota"
  target_sha="$(git rev-parse --verify "refs/tags/${target}^{commit}" 2>/dev/null)" || \
    fail "a tag ${target} não aponta para um commit"
  previous="$(git rev-parse HEAD)"
  [[ "$previous" != "$target_sha" ]] || fail "esta instalação já está no commit ${target_sha}"
  git merge-base --is-ancestor "$previous" "$target_sha" || \
    fail "a tag escolhida não avança a versão atual; rollback exige procedimento separado"
  local backup_file keep
  keep="${BACKUP_KEEP:-$(env_value BACKUP_KEEP)}"
  info "2/4 Backup antes da atualização..."
  backup_file="$("$RELEASE_DIR/backup-postgres.sh" --keep "${keep:-0}")" || \
    fail "backup falhou; código e serviços não foram alterados"
  info "3/4 Alternando para ${target} (${target_sha}) e reconstruindo..."
  git -c advice.detachedHead=false switch --detach "$target_sha"
  if ! compose up --detach --build --wait; then
    info "A atualização não iniciou corretamente."
    info "Commit anterior: ${previous}; backup: ${backup_file}"
    info "NÃO restaure automaticamente: verifique a compatibilidade da migração do banco."
    fail "inspecione logs e faça rollback controlado com os dados preservados"
  fi
  info "4/4 Conferindo a saúde..."
  if ! wait_healthy; then
    info "Aplicação sem saúde confirmada. Commit anterior: ${previous}; backup: ${backup_file}"
    info "Confira logs e compatibilidade de schema antes de qualquer rollback."
    fail "health check falhou; implantação requer intervenção"
  fi
  info "Atualizado de ${previous} para ${target} (${target_sha}). Backup: ${backup_file}"
}

cmd_status() {
  compose ps
  info "Checkout local: $(git -C "$PROJECT_ROOT" rev-parse --short=12 HEAD 2>/dev/null || printf unknown)"
  local running_sha
  running_sha="$(compose exec -T backend printenv APP_BUILD_SHA 2>/dev/null || true)"
  info "SHA da imagem backend: ${running_sha:-indisponível}"
  if wait_healthy; then
    info "Saúde: OK"
  else
    info "Saúde: FALHOU (veja: docker compose logs backend frontend)"
  fi
  info "Endereço para os alunos: $(student_url)/join"
}

cmd_https_ca() {
  local target="${1:-}"
  [[ -n "$target" ]] || fail "informe o arquivo de destino, ex.: arena-ca.crt"
  ARENA_HTTPS=1 compose exec -T caddy cat /data/caddy/pki/authorities/local/root.crt > "$target"
  info "Certificado raiz salvo em ${target}. Instale-o como confiável em cada aparelho."
}

command="${1:-}"
shift || true
export_compose_files
case "$command" in
  install) cmd_install "$@" ;;
  upgrade) cmd_upgrade "$@" ;;
  backup) (cd "$PROJECT_ROOT" && "$RELEASE_DIR/backup-postgres.sh" "$@") ;;
  restore)
    [[ $# -eq 1 ]] || fail "use: scripts/arena.sh restore ARQUIVO"
    read -r -p "Isto substitui o banco atual por ${1}. Digite RESTAURAR para continuar: " answer
    [[ "$answer" == "RESTAURAR" ]] || fail "restauração cancelada"
    (cd "$PROJECT_ROOT" && "$RELEASE_DIR/restore-postgres.sh" "$1" --confirm-replace)
    ;;
  status) cmd_status ;;
  https-ca) cmd_https_ca "$@" ;;
  -h|--help|"") usage ;;
  *) fail "comando desconhecido: ${command} (veja --help)" ;;
esac
