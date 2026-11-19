# Ramuvi v0.12.1 — Release Candidate integrada (19/11/2026)

**Situação: código consolidado em branch; NO-GO para main/tag/VPS até encerramento dos gates abaixo.**

## Conteúdo de integração

A branch `integration/v0.12.1-release-candidate` consolida as seguintes PRs de origem, preservando histórico, arquivos legais ainda não aprovados e feature flags inicialmente OFF:

| PR | Escopo |
|---|---|
| #140 | Jornadas offline: política docente, pacote AES-GCM, IndexedDB, PWA, sincronização controlada e Flyway V69 |
| #144 | Estabilização do Learn, login/logout, convites e estados de aulas ao vivo |
| #146 | Landing profissional, autenticação simplificada, identidade Verit, documentos legais, política de senha docente |
| #147 | Origem de QR autorizada e Next.js 16.4.6 |
| #148 | Testes WebKit móvel de navegação e acessibilidade |
| #149 | Isolamento do CSS público, dependente de #146 |
| #150 | Idempotência de XP em lote e reenvio controlado |
| #151 | Criptografia e verificação de backup, cópia externa, ensaio isolado de restauração |

Conflitos resolvidos manualmente: `student-entry-routing.spec.ts`, `accessibility-context-audit.spec.ts` e `study.module.css`, preservando os comportamentos de estabilidade Learn e link de download offline.

Versões de pacote: backend 0.12.1, frontend 0.12.1, Next.js 16.4.6. **V69** é aditiva: Jornadas existentes ficam `offline_allowed=false`; Jornadas novas começam com política `true`. O download verificado permanece OFF pelo `APP_STUDENT_IDENTITY_OFFLINE_DOWNLOAD_ENABLED=false`. Não ligue sem autorização real dos acessos OIDC e teste dos dispositivos.

## Evidência local da branch combinada

- Maven `verify`: **422 testes** aprovados (JUnit/Surefire/Failsafe, PostgreSQL isolado), **0 falhas**; `BUILD SUCCESS`. Conferência Flyway V69.
- Frontend `npm run typecheck`, `npm run lint`, `npm run format:check`, `npm run build`: aprovados; lint mantém 94 avisos e nenhum erro.
- Frontend `npm test`: **446 testes** aprovados após atualizar metadados para 0.12.1.
- `npm audit --omit=dev --audit-level=high`: **0 vulnerabilidades** de produção no momento da verificação. No npm ci completo, ainda aparecem vulnerabilidades moderadas relacionadas a dependências de desenvolvimento; não confundir os escopos.
- E2E selecionadas em servidor Next de produção local: **56 aprovadas e 4 puladas**, Chromium + WebKit móvel, cobrindo landing, termos, login, estabilização Learn, leitura/sincronização offline e revogação simulada. **Não substitui iPhone físico com cold start offline** nem E2E completa com backend autenticado em Compose.
- Release scripts: `bash -n`; **34 testes Python** aprovados; `verify-v0.12.1-contract.sh` aprovado. Novo template v0.12.1 permanece **NO-GO com 12 gates pendentes**.
- Ensaio isolado anterior da PR #151: backup/restauração real em PostgreSQL 17 com dados sintéticos **V68**. A V69 no pacote integrado foi exercitada nos testes Java, mas restauração representativa após V69 e recuperação da VPS **não** estão homologadas.

Essas evidências são locais e não demonstram CI oficial no SHA final.

## Gates NO-GO pendentes

1. **CI oficial GitHub Actions:** jobs backend/frontend falham com zero steps. A anotação encontrada nos runs recentes informa cobrança/spending limit do repositório/conta. CI 3/3 no SHA exato NÃO foi aprovada.
2. **VPS:** SSH para o IP publicamente resolvido, cujo host key já estava em `known_hosts`, retornou `Permission denied (publickey,password)` com a chave local disponível em modo BatchMode. Não se tentou trocar credenciais ou desabilitar verificação de identidade. Sem leitura de serviços, volumes, secrets, backup real, nginx ou acesso aos arquivos de deploy.
3. **Backup real:** há testes sintéticos, mas ainda faltam destinatário `age` real, destino offsite independente, restauração do dump representativo da VPS em ambiente isolado e RPO/RTO definidos.
4. **Físico e OIDC:** não homologados cold start offline em Safari/iPhone, Android/PWA, login Google/Microsoft real, convites/revogação e sincronização com sessão efetiva em dispositivo compartilhado.
5. **Legal/Design:** `LEGAL_STATUS.approved=false`; revisão jurídica de Termos/Privacidade, confirmação oficial do CNPJ, contato de privacidade, retenção, Brand Governance e aceite Figma ainda pendentes.
6. **Release owner:** o aceite geral de implementação no chat não equivale à atestação operacional dos 12 gates, nem à prova de uma restauração real.

## Sequência para o release sem regressões

1. Regularizar o billing GitHub Actions e desbloquear a chave SSH **no notebook do responsável**, sem compartilhar segredo no chat.
2. Executar o pipeline oficial completo no commit final da candidata (e após qualquer mudança), obter 3/3 verde e E2E real em Compose.
3. Revisar a PR de consolidação, resolver divergências com mudanças posteriores; homologar a identidade visual e documentos legais.
4. Na VPS, apenas **pré-voo de leitura**: confirmar usuário/host, pastas, Compose, bancos, volumes, imagens, certificados, espaço e SHA atual.
5. Produzir backup cifrado real e comprovável fora da VPS, ensaiar restauração na instalação isolada e conferir compatibilidade Flyway V68→V69.
6. Homologar aluno/professor, OIDC e Jornada offline em aparelhos reais, inclusive perda de rede, expiração, revogação e troca de conta.
7. Anexar atestação privada para `docs/v0.12.1/RELEASE_ACCEPTANCE_TEMPLATE.json` sem modificar o template NO-GO; exigir gates PASS com responsável, data e evidência.
8. Só após GO no SHA auditado: integrar a `main`, passar `release-gate.sh final`, criar tag `v0.12.1`, publicar Github Release, realizar deploy reversível na VPS e verificar HTTPS/health/rotas e logs após atualização.

**Proibido usar esta página como certificado de publicação.** Não houve merge na `main`, criação de tag, alteração na VPS ou reconfiguração de produção durante esta consolidação.

## Adendo: primeiro acesso simplificado (PR posterior empilhada)

O primeiro acesso simplificado acrescenta Flyway V70 e ingresso por código da turma autorizado pelo professor, além de associação opcional por e-mail OIDC verificado ao aluno já cadastrado. A conta continua vinculada ao identificador original do aluno e, portanto, ao XP, matrícula e progresso existentes. A flag APP_STUDENT_IDENTITY_AUTO_EMAIL_LINK_ENABLED inicia false; ingresso compartilhado por turma também inicia false. Requer novo aceite explícito no gate onboarding_verified_email_classroom_code_xp_continuity e revalidação de toda a CI no SHA resultante.

Este adendo não altera o fato de que a candidata PR #152 (V69) já tinha seus próprios resultados registrados; resultados adicionais precisam ser comprovados na branch que contém V70.
