# Ramuvi v0.12.2 — Relatório de implementação

- **Data:** 20/11/2026
- **Branch:** `feat/v0.12.2-classroom-library` (empilhada sobre a candidata PR #154, `0aad407`)
- **Normativo:** [ADR-0076](../adr/ADR-0076-classroom-session-journey-library-v0122.md) e
  [BASELINE_AND_CONTRACTS.md](BASELINE_AND_CONTRACTS.md) (I0)
- **Decisão desta entrega:** **NO-GO para produção.** O código das cinco fatias está implementado e
  testado localmente; os gates operacionais (CI oficial no SHA final, OIDC real, aparelhos físicos,
  backup representativo da VPS, aceite por coorte) não foram executados nesta sessão. Nenhum merge,
  tag, deploy ou alteração de VPS foi feito.

## 1. Commits

| Commit | Fatia | Conteúdo |
| --- | --- | --- |
| `846237f` | — | ADR-0076 (cherry-pick, já existente) |
| `c950a59` | I0 | Baseline, contratos, RBAC, flags e riscos congelados |
| `c1a9adc` | I1 | Central da Turma, 409 tipado, troca confirmada de aula, participante tardio |
| `b1cc83e` | I2 | Membro OIDC entra na aula ativa sem código |
| `47e17ce` | I3a/I3b backend + release | V73, snapshots, links VIEW/SAVE, Minha Biblioteca, contrato/gates v0.12.2 |
| `68f0dd3` | I3 frontend | Compartilhar, prévia pública, Minha Biblioteca, QR |
| `a766072` | I4 | E2E, retomada pós-login de uso único, flags no Compose |
| (este commit) | I4 | E2E Chrome/WebKit, retomada pós-login de uso único, flags no Compose, relatório |

## 2. Funcionalidades por incremento

### I0 — Fundação
- Base reconciliada: `origin/main` (`a75e398`) é ancestral do HEAD; nenhuma PR foi mesclada.
- Constatado que o invariante "≤ 1 sessão ativa por turma" já existe desde a **V2** (índice parcial);
  não foi necessária migration para ele.
- Lacunas registradas (corrida no `start` virando 400, participante tardio ausente, ausência de
  snapshot e de coleção pessoal, `hardDelete` em cascata sobre recursos da turma).

### I1 — Turma e aulas
- `SessionService.start` trava a linha da turma (`SELECT … FOR UPDATE`) e responde **409**
  `{code: ACTIVE_SESSION_EXISTS, activeSession}`; vale também para o `POST /api/sessions` legado.
- `switchActive` ("Encerrar e iniciar outra"): exige `confirm:true` e o `expectedActiveSessionId` que o
  professor viu; encerra e inicia na mesma transação com `flush` entre as transições (o Hibernate
  ordena INSERT antes de UPDATE e violaria o índice V2); eventos `SESSION_FINISHED` e
  `SESSION_STARTED` (com `replacesSessionId`); 409 `ACTIVE_SESSION_CHANGED` se a ativa mudou.
- Sessão esquecida: `stale=true` quando a aula tem mais de 12 h ou o código expirou. **Somente sinal**;
  nenhum job encerra sessão (o contrato de release falha se surgir `@Scheduled` em `session/`).
- Matrícula tardia: `SessionParticipantSync` insere `SessionParticipant` com `present=false` via
  `ON CONFLICT DO NOTHING` em matrícula, reativação, ingresso por código e ingresso de membro.
  Presença só no ingresso efetivo ou por ação docente.
- Read model `GET /api/classrooms/{id}/hub` (aula ativa, histórico das últimas 20 aulas com presença
  por encontro, totais de alunos, Jornadas publicadas/rascunho, atividades e aulas). Não devolve
  código, token nem lista de alunos.
- UI: `ClassroomSessionHub` na tela Início da turma (com a flag): iniciar com título sugerido
  ("Aula de dd/mm") em uma ação, retomar, encerrar e iniciar outra com diálogo de confirmação
  acessível por teclado, histórico por encontro.

### I2 — Aluno sem código repetido
- `GET /api/student-account/live-sessions` e `POST …/{sessionId}/join`: o backend resolve
  `(issuer, sub)` e revalida em **uma consulta por requisição** identidade não revogada, conta ativa,
  aluno ativo e não anonimizado, matrícula ativa, turma ativa, sessão `ACTIVE` e código transitório
  não revogado (o TTL do código não se aplica ao membro; ver §9). O cliente não envia `accountId`/`studentId`/`enrollmentId`. Negações: 404 uniforme.
- O membro continua `SessionParticipant` da sessão específica; novo ingresso pela conta substitui o
  token de dispositivo anterior (outro aparelho perde o acesso). Nenhum `X-Study-Token` é emitido.
- `/join` por código, claims `LIVE` e o bootstrap `410` (ADR-0075) permanecem intactos.
- UI: `MemberLiveClasses` em `/learn` ("Entrar na aula", sem código) reaproveitando o runtime de
  `/join` pela mesma chave de `sessionStorage`.

### I3a — Compartilhar Biblioteca
- `LearningResource` e formatos `MARKDOWN`, `PLAIN_TEXT`, `LINK` preservados.
- Snapshot imutável por (`source_resource_id`, `revision`, `content_sha256`); `LINK` guarda só a
  referência `http(s)` validada (sem `javascript:`, sem credenciais na URL).
- Token de 160 bits (`SecureRandom`, base32), mostrado só na criação (`no-store`), persistido como
  SHA-256; listagem com estado e contadores agregados, nunca token ou leitores.
- VIEW: prévia anônima. SAVE: exige conta OIDC verificada para abrir e salvar (decisão do I0).
- Prévia pública `POST` com token no corpo, rate limit por IP (falhas não zeram no sucesso),
  `Cache-Control: no-store`, `X-Robots-Tag: noindex`, `Referrer-Policy: no-referrer`.
- Revogar e expirar param novas aberturas e salvamentos; itens já salvos permanecem.
- QR por `POST /api/library/shares/qr` (origem autorizada, token no corpo).
- UI: ação **Compartilhar** distinta de **Adicionar a uma Jornada** e da apresentação na Arena;
  página `/learn/library/shared` lê `#acesso=`, limpa a barra antes de qualquer requisição, não mostra
  navegação de aluno a visitantes, é `noindex`/`no-referrer`; Markdown via `SafeMarkdown`.

### I3b — Minha Biblioteca
- `student_library_items` por `StudentAccount`, `UNIQUE(account_id, source_resource_id)`; funciona
  sem `Student`/`Enrollment` e não cria nenhum dos dois.
- Salvar é idempotente (`SAVED` → `ALREADY_SAVED`); versão diferente só substitui com
  `replaceVersion=true` (`UPDATED`). Leitura e remoção só pelo titular (IDOR → 404); remover não
  apaga o recurso do professor. Nenhum `score_event`.
- `hardDelete` da turma apaga os recursos da turma (cascata da V29), mas snapshots ficam com
  `resource_id = NULL` e itens pessoais continuam legíveis; links desse recurso deixam de funcionar.
- LGPD: a anonimização do aluno apaga a coleção das contas vinculadas antes de desativá-las; apagar a
  conta apaga a coleção (`ON DELETE CASCADE`).
- UI: `/learn/library` (vazio, erro, sem conta, recurso desligado), leitura da versão congelada em
  `/learn/library/{id}`, remoção confirmada, link no menu e na tela da conta só com a capacidade;
  retorno ao material após o login é de **uso único** e só quando o visitante escolhe entrar.

### I4 — Integração
- Jornada não foi alterada: continua vinculada à Turma, com publicação, liberação de capítulos,
  `JourneyStageProgress` por matrícula e `StudentJourneyStageProgress` legado sem fusão; não depende
  de sessão ativa. Offline segue ADR-0073 (nenhum arquivo offline foi tocado além da versão do SW).
  Biblioteca offline fica fora.
- Presença/XP/progresso seguem separados: o hub mostra presença por encontro com o próprio
  denominador; Biblioteca não gera XP, presença ou progresso.
- Versão 0.12.2 (pom, package, Service Worker `v0.12.2-library-1`).

## 3. Contratos, migrations e flags

| Item | Valor |
| --- | --- |
| Migration | **V73** `library_snapshots_shares_personal_library` — só `create table/index`; nenhuma FK para `classrooms`, `students` ou `enrollments`; V68–V72 intocadas |
| Flags (todas `false` em `application.yml`, `.env.example` e `compose.yaml`) | `APP_CLASSROOM_HUB_ENABLED`, `APP_LIVE_MEMBER_JOIN_ENABLED` (+OIDC +ENROLLMENT_CLAIM), `APP_LIBRARY_SHARING_ENABLED`, `APP_LIBRARY_PERSONAL_ENABLED` (+OIDC +SHARING) |
| Capacidades | `GET /api/teacher/capabilities`, `GET /api/student-account/capabilities` (mesmas condições dos controllers) |
| Mudança de contrato existente | `POST /api/sessions` em conflito: **400 → 409** tipado (mensagem nova). `TransactionalRulesIT` atualizado |
| Release | `scripts/release/verify-v0.12.2-contract.sh`; `release-gate.sh` e `verify-backup.sh` reconhecem V73; `v011-readiness.py --expected-version 0.12.2`; template `docs/v0.12.2/RELEASE_ACCEPTANCE_TEMPLATE.json` NO-GO com 23 gates (17 herdados + 6 novos) |

## 4. Evidência de testes (local, mesma máquina; não substitui CI oficial)

| Suite | Comando | Baseline | Final |
| --- | --- | --- | --- |
| Backend unit (Surefire) | `cd backend && mvn -B verify` | 222 | **222, 0 falhas** |
| Backend IT (Failsafe, PostgreSQL 17 Testcontainers) | idem | 219 | **244, 0 falhas**, `BUILD SUCCESS` (2m40s) |
| Frontend typecheck | `npm run typecheck` | OK | **OK** |
| Frontend unit | `npm test` | 452 | **472, 0 falhas** |
| Frontend lint | `npm run lint` | 0 erros, 97 avisos | **0 erros, 97 avisos** |
| Frontend format | `npm run format:check` | OK | **OK** |
| Frontend build | `npm run build` | — | **OK** (rotas `/learn/library`, `/learn/library/[itemId]`, `/learn/library/shared`) |
| Release scripts | `python3 -m unittest discover -s scripts/release/tests` | 34 | **39, OK**; `verify-v0.12.2-contract.sh` e `check-metadata.sh` aprovados |
| E2E v0.12.2 (backend real, hub + sharing ON) | `E2E_FRONTEND_URL=http://localhost:3100 npx playwright test e2e/v0122-classroom-library.spec.ts` | — | **16/16** (Chromium + WebKit iPhone 13) |
| E2E regressão (backend real, flags v0.12.2 OFF) | `npx playwright test --grep-invert v0122` | comparada com 0aad407 | 256 aprovados, 37 falhas, 4 pulados — **nenhuma falha atribuível a esta branch** (§4.3) |

### 4.1 ITs novos (PostgreSQL real)
- `ClassroomHubHttpIT` (6): RBAC/CSRF; início em uma requisição sem segredos no read model;
  **8 inícios simultâneos (legado + central) → exatamente 1 ativa e 7× 409 tipado**; troca confirmada
  preservando presença, XP e eventos da aula anterior; sessão de 3 dias marcada `stale` e não
  encerrada; matrícula tardia idempotente sem presença.
- `LiveMemberJoinHttpIT` (6): duas aulas sem código; outro dispositivo substitui o token; **4 ingressos
  simultâneos → 1 participante e 1 token válido**; matrícula tardia por SQL; IDOR, conta sem
  matrícula, anônimo, professor, CSRF, matrícula bloqueada, turma inativa e identidade revogada
  (revalidação por requisição); `/join` de convidado e bootstrap **410** preservados.
- `LibraryShareHttpIT` (11): token só na criação e só como hash; VIEW anônimo dos três formatos com
  `no-store`/`noindex`/`no-referrer` e sem dados acadêmicos; snapshot imutável após edição; URL
  `javascript:` recusada; SAVE exige conta; VIEW não salva; conta **sem matrícula** salva de forma
  idempotente e lê em **outro dispositivo**; **5 salvamentos simultâneos → 1 item**; política de
  versão; revogação/expiração com 404 uniforme e itens preservados; IDOR; `hardDelete` da turma
  preserva item; anonimização apaga coleção; QR por POST e origem autorizada.
- `LibraryShareRateLimitHttpIT` (1): tentativas inválidas por IP → 429 com `Retry-After`.
- `V0122FlagsOffHttpIT` (1): com os padrões de produção as rotas novas não existem, capacidades são
  `false` e o início legado mantém 1 sessão com 409 tipado.

### 4.2 E2E
- Spec nova `e2e/v0122-classroom-library.spec.ts`: central (início, retomada, troca com diálogo por
  teclado/Escape, histórico), conflito vindo de outra aba, 320 px sem rolagem horizontal; compartilhar
  real + prévia anônima real (fragmento removido da barra, token ausente das requisições, `<script>` do
  Markdown não executa, `meta robots noindex`, revogação → indisponível); diálogo de compartilhar por
  teclado em 320 px; fluxos de aluno com endpoints de conta **simulados** (Minha Biblioteca sem
  matrícula, SAVE sem conta pede login, membro entra sem código).
- Spec final reexecutada no build de produção final contra banco novo: **16/16** (Chromium + WebKit).

### 4.3 Regressão E2E e comparação com a baseline

Ambiente: Next de produção local (`next start`, porta 3100) + backend 0.12.2 com as flags v0.12.2 OFF
+ PostgreSQL 17 efêmero sintético. Resultado da suíte completa (298 testes, Chromium + WebKit iPhone 13):
**256 aprovados, 37 falhas, 4 pulados** em 20,2 min.

Para separar regressão de falha preexistente, a **baseline real** (frontend e backend compilados de
`0aad407` via `git archive`, portas 3200/8081, banco novo) executou os mesmos 12 arquivos com falha:

| Conjunto | Resultado |
| --- | --- |
| Chromium | 1 falha (`journey-gradual-release`): **também falha na baseline** (401 no resgate; o spec exige o opt-in `LIVE_STUDY`, pendência já registrada na ADR-0075) |
| WebKit — falhas em ambas | **34** (Arena/Projetor em tempo real, gaveta de navegação, reflow móvel, PWA, WCAG das telas principais e do projetor, histórico, retorno do aluno) |
| WebKit — só na candidata | `accessibility-wcag-aa.spec.ts:201` (tema claro): **passou 4/4** ao reexecutar → instável. `arena-shell.spec.ts:1278` (overflow de 87 px em "Verificar aprendizagem" a 320 px): falhou 2/2 no banco usado pela suíte inteira e **passou 2/2 com banco novo** → depende de dados acumulados (largura intrínseca do `<select>` de objetivos no WebKit), não do código desta branch |
| WebKit — só na baseline | `teacher-history.spec.ts:101` (instável na baseline) |

As 34 falhas WebKit compartilhadas são preexistentes nesta forma de execução local (o handoff v0.12.1
registrou apenas uma seleção de 60 E2E). Elas **não** foram declaradas aprovadas e continuam exigindo o
E2E oficial em Compose/CI. A fragilidade do `<select>` com muitos objetivos é um risco de UX
preexistente a registrar fora desta release.

## 5. Gates

| Gate | Situação |
| --- | --- |
| Backend verify com PostgreSQL real | **Aprovado localmente** |
| Frontend typecheck/test/lint/build | **Aprovado localmente** |
| E2E Chrome/WebKit das rotas novas, 320 px, teclado | **Aprovado localmente** (OIDC simulado nos fluxos de aluno) |
| Regressão E2E completa | **Sem regressão atribuível** (§4.3); 34 falhas WebKit preexistentes na baseline seguem abertas |
| Concorrência/corrida, CSRF, IDOR, revogação | **Aprovado em IT** |
| Upgrade V72→V73 e rollback de binário (sintético) | **Aprovado** (§6) |
| CI oficial GitHub Actions no SHA final | **Pendente**: `.github/workflows/ci.yml` só dispara em PR/push para `main`; a PR #156 (base `feat/secure-student-onboarding-v0121`) não executa CI. Requer rebase/PR para `main` depois da #154, mais a regularização de billing do handoff v0.12.1 |
| Google/Microsoft OIDC real em HTTPS na VPS | **Pendente — gate operacional**; nada foi assumido disponível |
| iOS/Android físicos, VoiceOver/zoom | **Pendente** |
| Backup/restauração com dump representativo da VPS (V73) | **Pendente** |
| Revisão de privacidade/direitos autorais dos links; retenção/LGPD da coleção | **Pendente** (decisões abertas da ADR-0076 §7 itens 2–3) |
| Piloto docente e aceite por coorte; flags em produção | **Pendente**; flags OFF |

## 6. Ensaio de upgrade e rollback de binário (sintético, isolado)

Banco PostgreSQL 17 efêmero (`docker run postgres:17-alpine`, dados sintéticos, sem acesso à VPS):

1. Binário **0.12.1 real** compilado de `0aad407` via `git archive` → Flyway em **V72**; criados por
   API turma, aluno, matrícula, aula ativa com presença e recurso de Biblioteca.
2. Binário **0.12.2** (hub + sharing ON) → aplicou **somente V73** (28 ms); contagens idênticas;
   hub mostrou a aula legada (`presentCount=1`); novo início → 409 com a sessão legada; compartilhar
   e prévia do recurso legado funcionaram.
3. **Rollback** para 0.12.1 sobre o banco V73 → subiu (`Successfully validated 73 migrations`; o
   Flyway ignora a migration futura por padrão); listou turmas, encerrou a aula e editou o recurso.
   O snapshot entregue continuou com o conteúdo anterior ("# Antes da V73").
4. Novo upgrade para 0.12.2 → "Schema is up to date"; dados íntegros.

Limite: não usa dump da VPS nem mede RPO/RTO.

## 7. Pendências e riscos

- **OIDC real:** I2 e I3b dependem de login Google/Microsoft homologado; com OIDC OFF as capacidades
  respondem `false` e a UI não oferece as ações.
- **Participante em runtime:** o token do participante continua válido até o fim da aula mesmo se a
  identidade for revogada depois do ingresso (mesmo comportamento do `/join`); as rotas de membro
  revalidam a cada chamada.
- **Código transitório no runtime do membro:** corrigido na revisão final (§9). O runtime da aula
  (`JoinAccessView`/`validateJoinAccess`) continua indexado pelo código; o token de participante emitido
  segue válido só enquanto o código estiver utilizável, então após mais 12 h o aluno reentra por
  **Ao vivo** (nova renovação) — não precisa digitar código. Quem já estava no runtime com o código
  antigo vencido precisa reentrar pela conta (antes da correção também não funcionava).
- **Rate limit em memória por instância** (igual ADR-0070/0074/0075).
- **Links externos (`LINK`)** são referências; o destino pode mudar.
- **Retenção de snapshots** sem política formal; exclusão excepcional é operação manual auditada.
- **Revogação excepcional de itens pessoais** (direitos/segurança) não tem API; requer SQL auditado.
- **Participação de visitante** continua o `/join` atual por nome declarado (ADR-0005/0075); nenhum
  novo modo visitante foi criado (decisão aberta 1 da ADR-0076).
- Testes de membro e de Minha Biblioteca no navegador usam endpoints de conta simulados; o backend
  correspondente é coberto por ITs com o fluxo OIDC real do Spring contra provedor local.

## 8. GO/NO-GO

**NO-GO** para merge na `main`, tag e produção até: CI oficial verde no SHA final; homologação OIDC
real; aparelhos físicos; backup/restauração representativo com V73; decisões de privacidade, direitos
e retenção; e aceite formal no template `docs/v0.12.2/RELEASE_ACCEPTANCE_TEMPLATE.json`. Mesmo com GO,
publicar com as quatro flags OFF e ligar por coorte (professor piloto → alunos piloto → Biblioteca →
geral).

## 9. Revisão final bloqueante — ingresso de membro com código expirado

**Defeito confirmado (base `7681298`).** `MemberLiveSessionService.ELIGIBLE` exigia
`session_join_codes.active AND expires_at > now()` e `SessionJoinService.issueMemberAccess()` exigia código
utilizável. Numa aula `ACTIVE` que passou de 12 h, o aluno OIDC matriculado deixava de ver a aula em
**Ao vivo** e recebia 404 ao entrar, contrariando a ADR-0076 D4 (entrar pela conta e matrícula, sem repetir
código). Comprovado com IT: os dois testes novos de expiração **falham no código anterior** (404) e passam
com a correção.

**Correção (sem nova migration, sem mudança de rota/DTO):**
- Descoberta: a consulta de elegibilidade passa a exigir só `j.active` (não revogado); o TTL deixa de
  filtrar o membro. Identidade, conta, aluno, matrícula, turma e sessão `ACTIVE` continuam revalidados
  em toda requisição.
- Ingresso: `issueMemberAccess` trava a sessão com `FOR SHARE` (o `finish` usa `FOR UPDATE`, então ou o
  ingresso termina antes do encerramento ou observa `FINISHED`) e o código com `FOR UPDATE`. Código
  revogado (`active=false`) → 404 sem alteração; código expirado e ativo → `rotate` (novo valor aleatório,
  novo TTL de 12 h — a mesma rotação que o `ensureCode` do professor já fazia); código válido → reutilizado.
- `ensureCode`/`rotate` do professor passam a travar a mesma linha (`FOR UPDATE`), evitando que a renovação
  do professor e a do membro gerem dois códigos (atualização perdida).
- `/join` de convidado inalterado: código expirado continua recusado; o código antigo **não** volta a
  valer após a renovação (passa a 404). Nenhum bootstrap por nome; bootstrap `410` preservado.

**Evidências (PostgreSQL 17 Testcontainers, `LiveMemberJoinHttpIT`, 11/11):**

| Teste novo | Verifica |
| --- | --- |
| `expiredGuestCodeDoesNotLockTheMemberOutOfAnActiveClass` | aula `ACTIVE` com código vencido: convidado recusado (400); membro lista e entra (200), código renovado ≠ antigo, `validateJoinAccess` aceita, presença registrada; código antigo 404; segundo membro reutiliza o código renovado |
| `revokedCodeIsNeverReactivatedByAMemberJoin` | código `active=false` (válido ou vencido): lista vazia, 404 uniforme, linha do código idêntica, sem presença |
| `finishedClassWithExpiredCodeIsNeverReopened` | sessão `FINISHED` com código vencido → 404; mesmo com a linha do código forçada para `active=true` (dado legado inconsistente) → 404; sessão segue `FINISHED`, código intacto |
| `concurrentMemberJoinsRenewAnExpiredCodeExactlyOnce` | 4 membros + `ensureCode` do professor simultâneos sobre código vencido → todos 200, **um único** código novo |
| `memberRenewalRacingWithFinishNeverReopensTheClass` | 3 rodadas de 3 membros × `finish` simultâneos → respostas 200/404, tokens emitidos inválidos após o encerramento, sessão `FINISHED`, código `active=false`, lista vazia |

Comando: `cd backend && mvn -o -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=LiveMemberJoinHttpIT verify`.
Prova de regressão: mesma suíte com `src/main` revertido para `7681298` → **2 falhas** (os dois testes de
expiração, 404).

Suíte completa após a correção (`cd backend && mvn -o verify`, PostgreSQL 17 Testcontainers):
**Surefire 222/0 falhas; Failsafe 249/0 falhas (244 anteriores + 5 novos); `BUILD SUCCESS`.**
Frontend sem alteração (o cliente já usa o `code` devolvido pelo ingresso): `npm test` **472/0 falhas**,
`npm run typecheck` OK.

### 9.1 Revisão de IDOR, URLs e flags (sem defeito concreto encontrado)
- **Biblioteca do professor** (`/api/library/**`): `ROLE_TEACHER` + CSRF; a aplicação tem um único
  professor (`app.teacher.*`), então `resourceId`/`shareId` não cruzam titulares.
- **Prévia pública**: única rota `permitAll` (só `POST`), token no corpo, busca por SHA-256 com
  `revoked_at is null and expires_at > now() and resource_id is not null`; `SAVE` exige conta OIDC verificada
  derivada do principal e revalidada no banco; rate limit por IP; `no-store`/`noindex`.
- **Minha Biblioteca**: toda consulta/remoção filtra por `account_id` derivado do servidor
  (`where id = ? and account_id = ?`), IDOR → 404 (coberto por `LibraryShareHttpIT`).
- **URLs**: snapshot aceita só `http(s)` com host e sem credenciais; o front revalida o protocolo, abre com
  `rel="noopener noreferrer"`; Markdown via `SafeMarkdown`; token só no fragmento `#acesso=`, removido da
  barra antes de qualquer requisição; QR por `POST` com origem na lista permitida.
- **Flags**: as quatro com padrão `false` em `application.yml`/`compose.yaml`; capacidades usam exatamente
  as condições dos `@ConditionalOnProperty`; `V0122FlagsOffHttpIT` cobre a ausência das rotas.

### 9.2 Riscos remanescentes desta correção
- A renovação pelo membro troca o código exibido ao professor/projetor (o anterior já estava vencido);
  o projetor/QR precisa recarregar o código, como já acontecia após `ensureCode`.
- O membro recebe o código renovado em `JoinAccessView` (como já recebia o válido) e poderia repassá-lo a
  convidados; é o mesmo escopo transitório do código projetado (ADR-0076 D5).
- Sessão `ACTIVE` sem linha em `session_join_codes` (não ocorre pelo fluxo atual, que cria o código no
  início) não é listada ao membro; o professor a regulariza ao abrir o código.
- O descolamento completo do runtime em relação ao código (token de participante autossuficiente)
  exigiria reestruturar `validateJoinAccess`, WebSocket e rotas `/api/join/{code}/…`; **não** foi feito
  nesta correção e fica registrado como evolução.

