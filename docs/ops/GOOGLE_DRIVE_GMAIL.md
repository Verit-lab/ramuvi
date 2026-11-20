# Ramuvi: Google Drive pessoal da Verit como destino de backup

Estado: preparação documentada; OAuth da Verit e agendamentos da VPS NÃO ATIVADOS.

## Decisão

Destino remoto: gdrive-ramuvi:Backups/Ramuvi. Enviar exclusivamente dumps PostgreSQL criptografados por age (extensão .dump.age) e manifestos SHA-256. O script backup-offsite.sh já usa rclone para copiar e ler novamente os bytes para verificação. Retenção local: 14 backups cifrados. Não excluir remotamente sem política aprovada.

## 1. Autorização pessoal na conta da Verit

Com o navegador autenticado na conta Gmail da Verit, abrir https://console.cloud.google.com/

1. Criar projeto específico Verit Ramuvi Backups, ativar Google Drive API.
2. Configurar OAuth consent screen / Google Auth Platform. Audience External, com a conta Verit autorizada.
3. Criar OAuth Client ID do tipo Desktop app, com nome Ramuvi Backup.
4. Usar client_id e client_secret próprios. O cliente compartilhado do rclone está em descontinuação em 2026.
5. Preferir escopo drive.file (https://www.googleapis.com/auth/drive.file), que restringe acesso aos arquivos e pastas criados por essa integração.
6. Para uso contínuo, verificar publicação adequada do app: no modo Testing, concessões podem expirar em sete dias. Podem existir requisitos adicionais de verificação e consentimento do Google.

Fonte oficial: https://rclone.org/drive/#making-your-own-client-id

## 2. Autorizar rclone em computador com navegador

Executar rclone config. Criar novo remote de nome gdrive-ramuvi, tipo drive, preencher client ID e secret do projeto Google da Verit, selecionar scope drive.file, deixar service_account_file vazio (conta Gmail pessoal), autorizar via navegador com a conta Verit e responder NÃO para Shared Drive.

Não compartilhar senha, tokens, client_secret ou o arquivo rclone.conf via chat ou Git. Esse arquivo contém tokens com acesso aos backups.

Verificação não destrutiva:

    rclone about gdrive-ramuvi:
    rclone lsd gdrive-ramuvi:

No escopo drive.file, pastas criadas manualmente no Drive podem não ser visíveis. Os diretórios de Backups/Ramuvi podem ser criados pelo próprio script de transferência.

## 3. Configuração protegida na VPS

Em servidor sem navegador, seguir https://rclone.org/remote_setup/ e copiar rclone.conf por canal SSH autenticado, com permissão 0600 e propriedade da conta do serviço, para /etc/ramuvi/rclone.conf.

Exemplo em /etc/ramuvi/backup.env (arquivo não versionado):

    COMPOSE_FILE=/opt/ramuvi/compose.yaml:/opt/ramuvi/compose.prod.yaml
    COMPOSE_PROJECT_NAME=ramuvi
    BACKUP_DIR=/var/backups/ramuvi
    BACKUP_REQUIRE_ENCRYPTION=1
    BACKUP_AGE_RECIPIENT=age1...CHAVE_PUBLICA_DA_VERIT...
    BACKUP_OFFSITE_REMOTE=gdrive-ramuvi:Backups/Ramuvi
    BACKUP_KEEP_LOCAL=14
    RCLONE_CONFIG=/etc/ramuvi/rclone.conf

COMPOSE_FILE, COMPOSE_PROJECT_NAME e os caminhos deverão ser conferidos contra o deploy real. A chave privada de age permanece FORA da VPS, em custódia independente.

## 4. Critérios antes de habilitar timer

1. Confirmar identidade Google autorizada e espaço disponível, sem exibir tokens.
2. Testar upload/download e SHA-256 de um arquivo sintético cifrado.
3. Executar o script backup-offsite.sh uma vez, em operação autorizada; comprovar a existência de .dump.age e .sha256 no Drive.
4. Restaurar uma cópia em PostgreSQL isolado e medir tempo real; nunca substituir produção durante testes.
5. Revisar permissões Docker, logs, RPO/RTO, alertas e política de retenção remota.
6. Só depois instalar as unidades systemd e habilitar backup diário.

A rotina atual não exclui backups no Drive. Isso pode esgotar o espaço da conta. Google Drive pessoal também não fornece, por si só, garantia de imutabilidade contra exclusão por token comprometido. Usar MFA na conta Verit e considerar cópia adicional independente.

Nenhum OAuth real, arquivo de produção ou agendamento da VPS foi configurado por esta documentação.
