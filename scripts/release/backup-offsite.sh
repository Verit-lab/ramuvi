#!/usr/bin/env bash
# Scheduled production backup: encrypted locally, copied offsite and VERIFIED
# by downloading the remote ciphertext. No restore is performed on production.
set -Eeuo pipefail
umask 077

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
OUTPUT_DIR="${BACKUP_DIR:-$PROJECT_ROOT/backups}"
REMOTE="${BACKUP_OFFSITE_REMOTE:-}"
RECIPIENT="${BACKUP_AGE_RECIPIENT:-}"
KEEP_LOCAL="${BACKUP_KEEP_LOCAL:-0}"

fail() { printf 'ERRO: %s\n' "$*" >&2; exit 1; }

[[ -n "$RECIPIENT" ]] ||
  fail "BACKUP_AGE_RECIPIENT obrigatório: não gerar cópia pessoal sem criptografia"
[[ "$REMOTE" =~ ^[A-Za-z0-9_-]+:.+ ]] ||
  fail "BACKUP_OFFSITE_REMOTE obrigatório (exemplo: ramuvi-backups:producao)"
[[ "$KEEP_LOCAL" =~ ^[0-9]+$ ]] ||
  fail "BACKUP_KEEP_LOCAL deve ser inteiro >= 0"
command -v age >/dev/null 2>&1 || fail "age não instalado"
command -v rclone >/dev/null 2>&1 || fail "rclone não instalado"
command -v flock >/dev/null 2>&1 || fail "flock não instalado"

mkdir -p "$OUTPUT_DIR"
chmod 700 "$OUTPUT_DIR"
exec 9>"$OUTPUT_DIR/.ramuvi-backup.lock"
flock -n 9 || fail "outro backup Ramuvi já está em andamento"

# No early retention: never delete the previous local recovery point BEFORE
# proving that the new encrypted copy exists and is readable offsite.
backup_file="$(BACKUP_REQUIRE_ENCRYPTION=1 BACKUP_KEEP=0 "$SCRIPT_DIR/backup-postgres.sh" --output-dir "$OUTPUT_DIR")"
[[ "$backup_file" == *.dump.age && -s "$backup_file" ]] ||
  fail "backup criptografado indisponível"
checksum_file="${backup_file}.sha256"
[[ -s "$checksum_file" ]] || fail "checksum do backup não encontrado"
(cd "$(dirname "$backup_file")" && sha256sum --check "$(basename "$checksum_file")" >&2) ||
  fail "checksum local inválido; cópia externa cancelada"

remote_root="${REMOTE%/}"
name="$(basename "$backup_file")"
rclone copyto "$backup_file" "$remote_root/$name" >&2
rclone copyto "$checksum_file" "$remote_root/$name.sha256" >&2

check_file="$(mktemp "$OUTPUT_DIR/.ramuvi-check.XXXXXXXX")"
trap 'rm -f -- "$check_file"' EXIT
printf '%s\n%s\n' "$name" "$name.sha256" > "$check_file"
# This reads the actual remote bytes; size-only checks are insufficient for PII.
rclone check --one-way --download --files-from "$check_file" "$OUTPUT_DIR" "$remote_root" >&2 ||
  fail "verificação externa falhou: backup local preservado, sem rotação"

if (( KEEP_LOCAL > 0 )); then
  shopt -s nullglob
  archives=("$OUTPUT_DIR"/ramuvi-*.dump.age)
  if (("${#archives[@]}" > KEEP_LOCAL)); then
    # Newest-first; daily backups use timestamped names. Only delete an older
    # backup after rechecking both remote ciphertext and its checksum.
    mapfile -t sorted_archives < <(ls -1t -- "${archives[@]}")
    for ((i=KEEP_LOCAL; i<${#sorted_archives[@]}; i++)); do
      old="${sorted_archives[i]}"
      [[ -f "$old" && -f "${old}.sha256" ]] || continue
      printf '%s\n%s\n' "$(basename "$old")" "$(basename "$old").sha256" > "$check_file"
      rclone check --one-way --download --files-from "$check_file" "$OUTPUT_DIR" "$remote_root" >&2 ||
        fail "cópia antiga não comprovada no destino: rotação local suspensa"
      rm -f -- "$old" "${old}.sha256"
    done
  fi
fi

printf 'Backup cifrado, SHA-256 e cópia externa verificada: %s\n' "$name" >&2
printf '%s\n' "$backup_file"
