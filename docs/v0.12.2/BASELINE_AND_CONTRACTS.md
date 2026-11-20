# Ramuvi v0.12.2 — I0: baseline e contratos congelados

- **Data:** 20/11/2026
- **Branch de trabalho:** `feat/v0.12.2-classroom-library`
- **Normativo:** [ADR-0076](../adr/ADR-0076-classroom-session-journey-library-v0122.md). Este documento fixa
  rotas, tabelas, flags e status HTTP que a ADR deixou para o I0 (§4.4 e §7, itens 1–5). Nada aqui
  relaxa ADR-0071, ADR-0073, ADR-0074 ou ADR-0075.

## 1. Baseline de código

| Item | Estado verificado |
| --- | --- |
| HEAD de partida | `846237f` (ADR-0076 cherry-picked) sobre `0aad407` (candidata PR #154) |
| `origin/main` | `a75e398`; é ancestral do HEAD (`git merge-base HEAD origin/main = a75e398`); nenhum commit da `main` falta na base |
| PRs abertas relevantes | #154 (candidata v0.12.1, base desta branch), #155 (ADR-0076, já incluída por cherry-pick), #152/#153 e #140–#151 (já consolidadas em #154 ou substituídas por ela), Dependabot #132–#134 (não integradas) |
| Política de integração | Nenhuma PR foi mesclada automaticamente. Esta branch empilha sobre #154; a PR da v0.12.2 aponta para `feat/secure-student-onboarding-v0121` para manter o diff revisável |
| SHA na VPS | **Não verificado** (sem acesso SSH nesta sessão, ver handoff v0.12.1, gate 2). Divergência registrada: o desenvolvimento ocorre contra a candidata, não contra o SHA instalado |
| Último Flyway | **V72** (`classroom_join_links_and_requests`). V68–V72 não são reescritas. Novas migrations começam em **V73** |
| Constraint de sessão ativa | Já existe desde **V2**: `uk_class_sessions_one_active_per_classroom` (índice único parcial `where status='ACTIVE'`). Não é preciso nova migration para o invariante; falta tratar a violação como conflito tipado |

### Baseline de testes (antes de qualquer mudança, mesma máquina)

| Suite | Comando | Resultado |
| --- | --- | --- |
| Backend unit (Surefire) | `cd backend && mvn -B verify` | 222 testes, 0 falhas |
| Backend IT (Failsafe, PostgreSQL 17 Testcontainers) | idem | 219 testes, 0 falhas; `BUILD SUCCESS` em 2m35s |
| Frontend typecheck | `cd frontend && npm ci && npm run typecheck` | aprovado |
| Frontend unit | `npm test` | 452 testes, 0 falhas |
| Frontend lint | `npm run lint` | 0 erros, 97 avisos (preexistentes) |

## 2. Lacunas encontradas no código (motivam I1–I3)

1. `SessionService.start` usa *check-then-insert* sem lock: duas requisições simultâneas passam pela
   checagem; a segunda viola o índice V2 e vira **400 genérico** ("viola uma regra de integridade").
   Não há referência à sessão existente.
2. `SessionParticipant` só é criado no `start` para matrículas ativas naquele instante. Aluno matriculado
   depois não consegue entrar na aula em curso (o `/join` só procura entre participantes existentes).
3. Não há "encerrar e iniciar outra" atômico; o professor precisa entrar na Arena, encerrar e voltar.
4. Não existe descoberta de aula para conta OIDC: `/api/learn/live` só aceita `X-Study-Token` (device claim).
5. `LearningResource` só guarda a revisão atual; não há snapshot entregue, link de compartilhamento nem
   coleção pessoal. `learning_resources.classroom_id` tem `on delete cascade` (V29): `hardDelete` da turma
   apaga recursos da turma — qualquer nova tabela não pode depender de FK em cascata a partir deles.
6. `StudentAnonymizationService` desativa a conta e apaga identidades; uma coleção pessoal nova precisa ser
   apagada junto (LGPD).

## 3. Modelo e estados (contrato)

| Conceito | Entidade | Estados | Invariantes |
| --- | --- | --- | --- |
| Turma | `Classroom` | `active=true/false` | `classrooms.code` é administrativo e nunca autentica (ADR-0074) |
| Aula | `ClassSession` | `ACTIVE → FINISHED` (só pelo professor) | ≤ 1 `ACTIVE` por turma (índice V2 + lock de linha da turma); nunca encerrada por tempo, rede ou idade |
| Participante da aula | `SessionParticipant` | `present`, `connected`, token de dispositivo | 1 por (sessão, aluno); criado sem presença; `present=true` só ao ingresso efetivo ou ação docente |
| Jornada | `Journey` | `DRAFT → PUBLISHED → ARCHIVED` | Autorizada por matrícula + publicação + `student_study_access_enabled`; independe de sessão |
| Recurso editorial | `LearningResource` | `library_visible`, `archived_at`, `revision` | Formatos `MARKDOWN`, `PLAIN_TEXT`, `LINK` preservados |
| Snapshot entregue | `learning_resource_snapshots` (V73) | imutável | Único por (`source_resource_id`, `revision`, `content_sha256`) |
| Link de compartilhamento | `library_share_links` (V73) | `ACTIVE`, `EXPIRED`, `REVOKED` (derivado) | Só hash SHA-256 do token; aponta para um snapshot |
| Item pessoal | `student_library_items` (V73) | existe / removido (delete) | Único por (`account_id`, `source_resource_id`); aponta para snapshot congelado |

### Sessão "esquecida"

`stale = true` quando a aula ativa começou há mais de **12 h** (mesmo TTL do código de ingresso) ou quando o
código de ingresso já não é utilizável. É **somente sinalização** na central: a UI oferece *Retomar aula* ou
*Encerrar e iniciar outra* (com confirmação explícita). Nenhum job encerra sessão.

## 4. RBAC e autorização (matriz congelada)

| Operação | Ator | Proteções |
| --- | --- | --- |
| Central da turma, iniciar/trocar aula | `ROLE_TEACHER` (operador único, ADR-0053) | CSRF; 404 para turma inexistente; 409 tipado em conflito |
| Descobrir/entrar em aula ao vivo como membro | `ROLE_STUDENT` (OIDC) | CSRF no POST; conta, identidade, aluno, matrícula, turma e sessão revalidados **no servidor em cada requisição**; cliente não envia `accountId`/`studentId`/`enrollmentId`; 404 uniforme (sem enumeração) |
| `/join` por código | anônimo (convidado) | inalterado: código transitório de 12 h, rate limit, claim `LIVE` (ADR-0075) |
| Criar/listar/revogar compartilhamento | `ROLE_TEACHER` | CSRF; token só na resposta de criação, `no-store`; listagem nunca devolve token nem leitores |
| Prévia de link `VIEW` | anônimo ou qualquer sessão | token no corpo do POST; rate limit por IP; `no-store`, `X-Robots-Tag: noindex`, `Referrer-Policy: no-referrer` |
| Prévia/salvar link `SAVE` | `ROLE_STUDENT` (OIDC) com conta ativa | CSRF; mesmo rate limit; anônimo recebe 401 `ACCOUNT_REQUIRED` |
| Minha Biblioteca (listar/ler/remover) | titular OIDC | CSRF nas mutações; `WHERE id=? AND account_id=?` (IDOR → 404) |

Decisão de I0 sobre `VIEW`/`SAVE` (ADR-0076 §7 item 2): **prévia anônima existe apenas para links `VIEW`**.
Um link `SAVE` é tratado como material que exige conta verificada para abrir e salvar. Um link `VIEW` nunca
permite salvar, mesmo com conta.

## 5. Rotas congeladas

### I1 — Turma e aulas (docente)

| Método e rota | Resposta |
| --- | --- |
| `GET /api/classrooms/{id}/hub` | `ClassroomHubView`: turma, aula ativa (`stale`, contagens de participantes/presença/conexão), histórico das últimas 20 aulas, totais de matrículas ativas, Jornadas publicadas/rascunho e atividades |
| `POST /api/classrooms/{id}/sessions` `{title?}` | `201 SessionView`; `409 {code:"ACTIVE_SESSION_EXISTS", activeSession}` |
| `POST /api/classrooms/{id}/sessions/switch` `{expectedActiveSessionId, title?, confirm:true}` | `201 {finished, started}`; `400` sem `confirm`; `409 {code:"ACTIVE_SESSION_CHANGED", activeSession}` se a ativa mudou |
| `POST /api/sessions` (legado da Arena) | mesmo lock e mesmo `409` tipado (antes: 400) |

### I2 — Membro (aluno OIDC)

| Método e rota | Resposta |
| --- | --- |
| `GET /api/student-account/live-sessions` | aulas ativas das matrículas ativas (`sessionId`, `classroomId`, `classroomName`, `sessionTitle`, `startedAt`); sem código, sem lista de alunos |
| `POST /api/student-account/live-sessions/{sessionId}/join` | `JoinAccessView` (token de participante da sessão específica + código transitório para o runtime existente); `404` uniforme |

Um novo ingresso pela conta substitui o token de dispositivo anterior do mesmo participante (a conta prova
titularidade; o aparelho antigo perde acesso). Isto é diferente do `/join` por nome, que continua exigindo
liberação docente.

### I3a/I3b — Biblioteca

| Método e rota | Resposta |
| --- | --- |
| `POST /api/library/resources/{id}/shares` `{permission: VIEW\|SAVE, validDays 1..90, label?}` | `201` com `token` e `path` (`/learn/library/shared#acesso=TOKEN`), `no-store` |
| `GET /api/library/resources/{id}/shares` | estados e contadores agregados; nunca token/leitores |
| `POST /api/library/shares/{shareId}/revoke` | `204` (idempotente) |
| `POST /api/library/shares/qr` `{baseUrl, token}` | PNG `no-store`; docente; origem precisa estar autorizada; token só no corpo |
| `POST /api/library/shares/preview` `{token}` | snapshot autorizado; `401 ACCOUNT_REQUIRED` para `SAVE` sem conta; `404` uniforme para inválido/expirado/revogado |
| `POST /api/student-account/library/items` `{token, replaceVersion?}` | `SAVED`, `ALREADY_SAVED` ou `UPDATED`; idempotente |
| `GET /api/student-account/library/items` | itens do titular (metadados) |
| `GET /api/student-account/library/items/{id}` | conteúdo do snapshot congelado |
| `DELETE /api/student-account/library/items/{id}` | `204`; não altera o recurso editorial |

Token de compartilhamento: 160 bits de `SecureRandom` em base32 (32 caracteres), só o SHA-256 é persistido.
Viaja no fragmento; o front o move para memória, limpa a barra (`history.replaceState`) e envia apenas no
corpo do POST.

## 6. Persistência (V73, aditiva)

- `learning_resource_snapshots`: `resource_id` **nullable** com `on delete set null` e `source_resource_id`
  imutável sem FK — exclusão do recurso ou da turma não apaga snapshots.
- `library_share_links`: `resource_id on delete set null`; link sem recurso deixa de funcionar (novas
  visualizações/resgates), sem afetar itens já salvos.
- `student_library_items`: `account_id references student_accounts on delete cascade` (apagar a conta apaga a
  coleção), `snapshot_id` sem cascata. Anonimização do aluno apaga os itens das contas vinculadas.
- Nenhuma tabela nova referencia `classrooms`, `students` ou `enrollments`.

## 7. Flags (todas OFF por padrão; flag não substitui autorização)

| Propriedade | Variável | Exige também | Efeito |
| --- | --- | --- | --- |
| `app.classroom-hub.enabled` | `APP_CLASSROOM_HUB_ENABLED` | — | Central da turma (read model, iniciar/trocar aula pela turma) |
| `app.live-member-join.enabled` | `APP_LIVE_MEMBER_JOIN_ENABLED` | OIDC + `enrollment-claim-enabled` | Aluno entra na aula ativa pela conta |
| `app.library.sharing.enabled` | `APP_LIBRARY_SHARING_ENABLED` | — | Links VIEW/SAVE e prévia |
| `app.library.personal.enabled` | `APP_LIBRARY_PERSONAL_ENABLED` | OIDC + `library.sharing.enabled` | Minha Biblioteca |

Capacidades para a UI: `GET /api/teacher/capabilities` (docente) e `GET /api/student-account/capabilities`
(aluno). A condição é a mesma usada pelos controllers (`@ConditionalOnProperty`), para que a UI nunca
ofereça uma ação inexistente.

## 8. Riscos registrados

| Risco | Mitigação nesta release | Residual |
| --- | --- | --- |
| Corrida ao iniciar aula | lock `FOR UPDATE` da turma + índice V2 + 409 tipado | — |
| Troca de aparelho do membro derruba o anterior | comportamento explícito e documentado | professor vê conexão mudar |
| Token de compartilhamento vazado | expiração ≤ 90 d, revogação, `SAVE` exige conta, rate limit | limitador em memória por instância (igual ADR-0070/0074) |
| Link externo (`LINK`) muda no destino | snapshot guarda só a referência e aviso de instabilidade | sem cópia do conteúdo remoto |
| Direitos autorais/retenção de snapshots | snapshots não são apagados pela turma; exclusão excepcional é operação auditada manual | política formal de retenção é decisão aberta (ADR-0076 §7 item 3) |
| OIDC real indisponível na VPS | I2/I3b atrás de flags que exigem OIDC; nada é anunciado com OIDC OFF | homologação Google/Microsoft real é gate operacional |
| Rollback de binário anterior com V73 | migration só cria tabelas novas; ensaio sintético 0.12.1→0.12.2→0.12.1→0.12.2 aprovado (o Flyway do 0.12.1 trata V73 como migration futura e valida; ver IMPLEMENTATION_REPORT §6) | ensaio com dump representativo da VPS ainda pendente; desligar flags continua sendo o fallback principal |
