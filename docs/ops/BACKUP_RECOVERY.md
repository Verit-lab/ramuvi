# Ramuvi — Plano operacional de backup e recuperação do PostgreSQL

**Estado:** implementação em branch. Não habilitado na VPS, sem validação de backups reais da produção, sem RTO/RPO operacional comprovado. Último ensaio isolado com schema Flyway V68, dados sintéticos (19/11/2026).

## Escopo e premissas

O banco PostgreSQL armazena turmas, matrículas, Jornadas, avaliações, progresso, XP e autenticação acadêmica. Um backup do banco **não** substitui controle de versão do código, exportação das credenciais/segredos necessários às integrações, certificados TLS e eventual conteúdo hospedado fora da aplicação. O repositório/Compose não deve armazenar dados reais de alunos nem chaves privadas de recuperação.

Objetivos a aprovar com o responsável operacional:
- **RPO:** backup diário sugere até 24 horas de risco de perda de gravações, mas não comprova garantia. Se o negócio exigir menor RPO, avaliar WAL/PITR e armazenamento contínuo.
- **RTO:** definir após ensaio com volume representativo, restauração de arquivos, configuração de serviços e login real.
- Retenção e exclusão: definir com avaliação LGPD e necessidade acadêmica, além de política do provedor offsite. Exemplo de **14 cópias locais** criptografadas não é política de retenção legal nem substitui política de remoto.

## Como funciona a proteção

1. `scripts/release/backup-postgres.sh` gera dump custom, com SHA-256 e arquivo restrito. Na configuração **BACKUP_REQUIRE_ENCRYPTION=1**, age é obrigatório e recebe pg_dump diretamente por pipe. Não existe cópia temporária descriptografada no host. Se pg_dump/age/checksum falhar, arquivos parciais são eliminados e nada é anunciado como backup concluído.
2. `scripts/release/backup-offsite.sh` **recusa execução** sem `BACKUP_AGE_RECIPIENT` e `BACKUP_OFFSITE_REMOTE`. Usa rclone para copiar o dump criptografado e o manifesto SHA-256 para armazenamento independente. Só considera o ciclo bem-sucedido quando `rclone check --download` confere os bytes recuperados do destino externo. Nunca grava a chave privada de age ou dados em claro no backup remoto.
3. `BACKUP_KEEP_LOCAL=0` não remove arquivos locais. O padrão da rotina externa é 14 cópias cifradas. Se N > 0, a limpeza considera apenas os arquivos novos `ramuvi-*.dump.age` e **revalida os dois arquivos de cada ponto de recuperação no remoto** antes de excluir sua cópia local. Backups históricos legados `arena-dev-*` não são apagados automaticamente.
4. `scripts/release/verify-backup.sh` verifica o dump em PostgreSQL 17 isolado, com contêiner sem acesso à rede, conferindo tabelas, Flyway, constraints e colunas de contrato. `BACKUP_REQUIRE_CHECKSUM=1` proíbe arquivo sem manifesto.
5. `scripts/release/restore-postgres.sh` **é destrutivo**, exige `--confirm-replace`, confere checksum antes de tocar no banco, ensaia em PostgreSQL isolado e faz um backup de segurança do estado anterior. Arquivos sem checksum só podem ser recuperados com `BACKUP_ALLOW_UNVERIFIED=1`, exceção manual de alto risco. Falha durante a substituição mantém a aplicação parada para intervenção, sem declarar sucesso.

## Configuração do agendamento (não instalada automaticamente)

Dependências no servidor de backup: Docker Compose, `age`, `rclone`, `flock`, `sha256sum`, Python 3 e permissão estritamente controlada de operar os contêineres PostgreSQL. Acesso a Docker equivale, na prática, a privilégio elevado: delimitar a conta do serviço e auditar suas credenciais.

- Gerar chave de cifragem com `age-keygen` em ambiente protegido. Guardar a **identidade privada fora da VPS**, em local controlado. O servidor recebe apenas o destinatário público `age1...`.
- Configurar um remote do rclone apontando para armazenamento **independente da VPS**, com credenciais limitadas e redundância. Proteger a configuração `/etc/ramuvi/rclone.conf` com permissão 0600.
- Preparar `/etc/ramuvi/backup.env` com as variáveis relevantes, sem compartilhar valores privados no Git:

```dotenv
COMPOSE_FILE=/opt/ramuvi/compose.yaml:/opt/ramuvi/compose.prod.yaml
COMPOSE_PROJECT_NAME=ramuvi
BACKUP_DIR=/var/backups/ramuvi
BACKUP_AGE_RECIPIENT=age1...PUBLIC_KEY...
BACKUP_OFFSITE_REMOTE=remote-configurado:ramuvi/producao
BACKUP_KEEP_LOCAL=14
RCLONE_CONFIG=/etc/ramuvi/rclone.conf
BACKUP_REQUIRE_ENCRYPTION=1
```

**Ajustar** `COMPOSE_FILE` e `COMPOSE_PROJECT_NAME` com os valores REAIS do deploy. Não utilizar o exemplo diretamente. Não guardar senha de banco aqui desnecessariamente; Docker Compose obtém a configuração existente da implantação segura.

- Arquivos de exemplo: `scripts/release/systemd/ramuvi-backup.service` e `ramuvi-backup.timer`. O serviço usa `WorkingDirectory=/opt/ramuvi` e `EnvironmentFile=/etc/ramuvi/backup.env`. Verificar localização da instalação, conta do serviço, permissões Docker e timezone do servidor.
- Somente após revisão de configuração e ensaio manual: instalar as unidades em `/etc/systemd/system`, fazer `systemctl daemon-reload`, testar uma execução manual, inspecionar logs e **então** habilitar o timer. Nada nesta PR instala ou habilita unidades automaticamente.
- Monitorar a última execução bem-sucedida e idade do último backup externo. Falhas de backup devem alertar o operador; um timer sozinho não assegura disponibilidade.

## Verificação periódica de recuperação

Foi incluído um **timer mensal opcional** para ensaio sintético:
- scripts/release/run-periodic-recovery.sh
- scripts/release/systemd/ramuvi-recovery-drill.service
- scripts/release/systemd/ramuvi-recovery-drill.timer

A unidade requer /etc/ramuvi/recovery.env contendo
RAMUVI_RECOVERY_TEST_JAR=/caminho/do/ramuvi-api-0.12.0.jar. Ela cria
contêineres isolados e os remove após o teste. **Não habilitar** sem
confirmar que o JAR da versão é compatível com Flyway no checkout e
que existe uma conta autorizada a usar Docker. Falhas devem gerar
alertas e acompanhamento do operador.

Esse teste **não utiliza backup real de produção**. A cada trimestre,
realizar adicionalmente em estação de recuperação autorizada a
restauração de um pacote cifrado baixado do offsite, com a chave privada
mantida fora da VPS; medir RTO e perda potencial de dados e registrar
o resultado. Nenhum timer foi instalado ou ativado nesta PR.

## Validação de restauração

### Sintética, automatizável e sem tocar produção

```bash
scripts/release/rehearse-recovery.sh \
  --backend-jar backend/target/ramuvi-api-0.12.0.jar \
  --confirm-synthetic-only
```

O ensaio cria projeto Compose, porta TCP e volume efêmeros próprios, sobe a API apenas contra esse PostgreSQL, aplica Flyway, insere marcador sintético, executa os **scripts reais** de backup/verificação/restauração e confere marcador + versão Flyway; ao sair, remove contêineres, volume e dumps de teste. Não aponte esse ensaio para dados de produção. A versão do JAR precisa corresponder ao schema a verificar.

Resultado em 19/11: `RECOVERY_DRILL_PASS=1 FLYWAY_VERSION=68 DATA_SYNTHETIC_RESTORED=1`, após cerca de 37 s no notebook conectado. **Esse tempo não é RTO de produção.**

### Cópias reais, em estação isolada, após autorização operacional

1. Obter cópia criptografada e manifesto SHA-256 do remoto, em ambiente controlado. Conferir o manifesto do ciphertext antes de descriptografar.
2. Descriptografar em espaço temporário protegido (0600) usando a identidade privada **na estação de recuperação**, jamais anexada ao repositório:
   `age --decrypt --identity /caminho/protegido/identity.txt --output /caminho/seguro/recovery.dump backup.dump.age`
3. Rodar `BACKUP_REQUIRE_CHECKSUM=0 scripts/release/verify-backup.sh /caminho/seguro/recovery.dump` no ambiente isolado, pois o SHA-256 verificado é do arquivo criptografado original.
4. Validar contagem/consistência de alunos, Jornadas, progresso e XP, schema Flyway, possibilidade de login e compatibilidade da imagem de aplicação com migrations do backup.
5. Eliminar a cópia temporária em claro em estação autorizada; considerar persistência em SSD e preferir volume temporário criptografado.
6. Documentar medidas observadas de RTO/RPO, riscos de dados gravados após snapshot, responsável e aceite.

**Nunca executar `restore-postgres.sh` no ambiente de produção sem autorização explícita, janela de manutenção, ponto de retorno e rollback aprovado.**

## Limites e riscos residuais

- Não foi configurado remote real, timer na VPS, chave pública age de produção ou rotina de alertas; o código está pronto para configuração, não em operação.
- Em 19/11 foi feita **validação integrada local com age v1.3.1 e rclone reais** (instalados temporariamente no notebook), chave age temporária, PostgreSQL simulado sem dados pessoais e destino rclone local isolado: cifragem em streaming, cópia externa, leitura remota, SHA-256 e descriptografia do arquivo sintético passaram. A chave privada sintética e os arquivos foram descartados após o teste. **Não foi validado o provedor de armazenamento externo da VPS**, nem o age instalado no servidor.
- Os testes unitários da rotina usam age/rclone simulados para falhas e cleanup; complementarmente, o teste acima comprovou comportamento com os binários reais. O rclone --download transfere os bytes cifrados novamente para comparação, com potencial custo do provedor.
- Retenção remota, imutabilidade, versionamento, proteção anti-ransomware e escrows de segredos precisam de decisões da Verit.
- `BACKUP_KEEP=14` da instalação de sala é legado e não torna cópias necessariamente criptografadas. Em produção o fluxo recomendado é `backup-offsite.sh`, que mantém os backups até comprovar o destino externo.
- A conclusão do ensaio isolado e a aprovação dos testes locais **não** significam que o backup de produção da VPS é restaurável; esse gate permanece aberto.
