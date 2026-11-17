# Ramuvi v0.12 — I2: vínculo autenticado entre conta e matrícula

**Estado:** IMPLEMENTADO, AUDITADO E VALIDADO NA CI OFICIAL 3/3 EM SHA `a901d3c`; revisão complementar em andamento na mesma PR. **Não representa autorização para merge, release ou deploy.**
**Base imutável:** I0 PR #136 (V68) + I1 PR #137 (OIDC) `ee5d0b3`.
**Branch:** `feat/v0.12-enrollment-claim-i2`, PR draft dependente das duas anteriores.
**Rastreabilidade:** issue #119, ADR-0068, ADR-0069, ADR-0070 (proposta).

## 1. Problema e limites

Um `Student` global já participa de diversas `Enrollments` (turmas) e preserva XP/Jornada. A nova `StudentAccount` autentica uma pessoa, mas não prova por si só que aquela pessoa é o `Student` cadastrado pelo professor. A I2 entrega uma vinculação **explícita por convite individual, previamente autorizada pelo docente**, sem descobrir alunos pelo e-mail, nome ou dispositivo. Um pedido genérico para entrar numa turma (via código da turma + aprovação docente) é um fluxo distinto, ainda não incluído na I2.

**Não alterar:** autenticação do professor, token anônimo da Arena, aluno em `/join`, modelo de progresso/XP, publicação da Jornada, migrações V1–V68, dispositivos lembrados. **Não entregar:** transferência/desvinculação manual de contas, mecanismo de recuperação, SSO validado em Google/Microsoft reais, OTP, portal completo de Jornada da I3.

## 2. Contratos e limites de domínio

`StudentAccount 0..1 ── 0..1 Student 1 ── 0..N Enrollment N ── 1 Classroom`

- Conta autenticada por `(issuer,sub)` de **ID Token OIDC validado** pelo backend. A conta pode existir sem `Student`.
- Convite individual liga à `Enrollment`; permite confirmar acesso a um `Student` **preexistente**. Não cria aluno, matrícula ou turma.
- Um `Student` mantém múltiplas matrículas; listar as turmas ativas de `Student` **não requer um convite novo para cada matrícula** após o primeiro vínculo.
- `student_accounts.student_id UNIQUE` impede duas contas de reivindicar o mesmo aluno. Vínculos a alunos diferentes não podem ser fundidos automaticamente. Erros devem ser genéricos.
- `student_enrollment_invites` é registro de auditoria (issued_at, expires_at, revoked_at, claimed_at, claimed_by_account_id). O hash SHA-256 do token aleatório de 256 bits é o único segredo persistido. O professor só recebe o token bruto na criação.
- Sem migration nova: usar V68. As rotas I2 não criam tabelas nem estendem privilégios dos endpoints `/api/learn/**`, que continuam exigindo `X-Study-Token`.

## 3. APIs e controle de acesso

| Método | Endpoint | Principal | Contrato |
| --- | --- | --- | --- |
| `GET` | `/api/student-account-invitations/capabilities` | ROLE_TEACHER | `{enabled: boolean}` para exibir ação docente somente quando funcionalidades ligadas |
| `POST` | `/api/student-account-invitations/enrollments/{id}` | ROLE_TEACHER + CSRF + flag já existente | `{inviteId,enrollmentId,token,expiresAt}` em resposta **no-store**; revoga convite anterior pendente |
| `DELETE` | `/api/student-account-invitations/{id}` | ROLE_TEACHER + CSRF | Revoga convite pendente |
| `POST` | `/api/student-account/enrollments/claim` | ROLE_STUDENT, ID Token OIDC + CSRF + flag I2 | entrada `{token}` só por POST, retorna `{studentId,enrollmentId,classroomId,classroomName}`; não aceita accountId por JSON |
| `GET` | `/api/student-account/classrooms` | ROLE_STUDENT, OIDC ativo + flag I2 | turmas **ativas** da conta corrente, sem lista geral, sem email de outros alunos, `studentStudyAccessEnabled` explícito |

Todos os retornos da área pessoal usam `Cache-Control: no-store`. Nenhuma rota que consome token será GET, para evitar exposição em histórico, referrer e logs de URL. O token é digitado ou colado em campo local; não persistir em `localStorage`, `sessionStorage` ou nos logs. Após sucesso, limpar o campo imediatamente.

### Proteção de autorização

- **Não confiar em `accountId` de cliente**: extrair `issuer/sub` do `@AuthenticationPrincipal OidcUser` autenticado pelo Spring, consultar `student_account_identities` e `student_accounts` **dentro da transação** de resgate.
- Rejeitar principal ausente, conta desativada, identidade revogada, matrícula/turma/aluno inativos ou anonimizados.
- **Emissão concorrente:** `SELECT ... FOR UPDATE OF e` da matrícula antes de revogar convites pendentes e inserir o novo token. Garante um único convite pendente por matrícula mesmo com dois professores/abas emitindo simultaneamente.
- Resgate atômico: `SELECT ... FOR UPDATE` do convite, travas de conta, `UNIQUE(student_id)`, `claimed_at` único, transação integral. Rejeitar replay, duplicidade e token expirado/revogado.
- Evitar diferenciação entre tokens inexistentes, usados ou expirados; exceção de conta vinculada a outro aluno pode informar conflito apenas ao usuário já autenticado.
- Listar somente `enrollments.active`, `classrooms.active`, `students.active`, `anonymized_at is null`, `student_accounts.status=ACTIVE`, identidade **exata** ativa para a sessão. Exibir booleano de acesso à Jornada, mas **não estender autorização das rotas legadas** antes do I3.
- Acesso docente aos convites não concede ROLE_STUDENT e login discente não concede ROLE_TEACHER. CSRF nas mutações, cookies HttpOnly/SameSite, Secure em HTTPS.

## 4. UX mínima e modo degradado

- **Professor → Turma → Participantes → ⋯ → Gerar convite para conta:** exibir o token uma vez, data de expiração e botão de cópia, com aviso para comunicar individualmente; nenhuma associação automática com o e-mail cadastrado. A ação só aparece se o backend informar capacidades habilitadas.
- **Aluno → /learn/login:** após login OIDC, campo `Código individual do convite`, confirmação e turmas ativas reconhecidas; acessibilidade com label, status/erro e teclado. O código de sessão curto não é equivalente ao token individual de 43 caracteres.
- Se desabilitado `APP_STUDENT_IDENTITY_ENROLLMENT_CLAIM_ENABLED=false`, funcionalidade não aparece; acesso por código `/join` funciona normalmente. OIDC desligado também implica I2 desligado.
- UI e escrita permanecem **pre-release**; redesign geral e UX de Jornada continuam com aprovação Figma (I3).

## 5. Implementação e gates

| Gate | Entrega técnica | Aceite |
| --- | --- | --- |
| I2.0 | ADR, plano e isolamento de branch | Dependência I0 → I1 → I2 explícita; nenhuma alteração em main/VPS |
| I2.1 | principal confiável e POST transacional de resgate | 401/403 sem OIDC, 403 sem CSRF, rejeita token inválido/replay e conflitos, revogação |
| I2.2 | GET restrito de turmas | múltiplas matrículas do mesmo Student, apenas ativas, nenhum vazamento entre contas |
| I2.3 | UI mínima professor/aluno e feature flags OFF | fluxo completo de emissão → login → vincular → listar, sem salvar token no navegador |
| I2.4 | validação | testes Java completos, frontend lint/test/build, E2E Chromium/WebKit, CI oficial 3/3 no SHA da PR |
| I2.5 | homologação (não incluída na PR) | OIDC Google/Microsoft real em HTTPS, Safari físico, backup V68, auditoria e decisão GO assinada |

## 6. Matriz negativa de segurança

- 1. Sem sessão / sessão docente na rota do aluno: 401/403; sessão aluno na rota docente: 403.
- 2. CSRF ausente, método GET de resgate, payload com accountId forjado: não produz vínculo.
- 3. Token inexistente, formato inválido, expirado, revogado ou reutilizado: nenhum vínculo novo.
- 4. Aluno vinculado a outra conta ou conta vinculada a outro Student: transação falha e token não é consumido.
- 5. Matrícula desativada/excluída, aluno anonimizado, turma inativa, identidade OIDC revogada: sem resgate/listagem.
- 6. Multiturmas: mesmo Student pode aparecer em duas turmas ativas; conta de outro Student não consegue descobrir essas turmas.
- 7. Concorrência: dupla submissão ao mesmo token → no máximo um resgate; **emissão paralela de convites para a mesma matrícula → exatamente um token pendente**. A rotação invalida o anterior.
- 8. Logs, referrer, cache e UI: não reter ou transmitir token em URL ou armazenamento durável.
- 9. Regressão: docente, Arena, Jornada e Student já existentes seguem operacionais com flags desligadas.

## 7. Riscos, rollback e operação

| Risco | Mitigação |
| --- | --- |
| Convite compartilhado/encaminhado indevidamente | alerta docente, expiração curta, revogação, confirmação da identidade via OIDC, uso único; suporte pedagógico para eventual disputa, sem remapeamento automático |
| Assunção indevida por endereço de e-mail | `issuer/sub` verificado e convite específico de matrícula; e-mail cadastrado nunca autoriza |
| Acesso após desativação | consulta SQL de status da conta, identidade, matrícula e turma em toda nova rota |
| Mudanças V68 já aplicadas | backup/restore isolado; **não** tratar rollback como simples troca de imagem após Flyway V68 |
| Feature incompleta em produção | flags `APP_STUDENT_OIDC_ENABLED`, `APP_STUDENT_IDENTITY_INVITATION_ISSUANCE_ENABLED` e `APP_STUDENT_IDENTITY_ENROLLMENT_CLAIM_ENABLED`, todas OFF por padrão |
| Segredos em logs/suporte | no-store, POST apenas, campo limpo após resgate, não transmitir segredo em query/fragment |
| Autoaprovação por código genérico | explicitamente fora de escopo; aprovação docente já ocorreu ao emitir o convite individual |

**NO-GO operacional enquanto** I0/I1/I2 não forem revisadas e integradas sequencialmente; os provedores reais, permissões, backup/restore e controles de privacidade não forem homologados. Sem tag/release/deploy por esta etapa.


## 8. Evidências da implementação I2 (17/11/2026)

- Backend: execução inicial da PR #138 com **184 testes**. Auditoria adicionou testes de conta desativada, aluno inativo e emissões concorrentes; testes dirigidos após correção tiveram **16/16 PASS**, com bloqueio de linha no banco. A regressão completa após o último commit precisa ser revalidada sequencialmente; uma rodada concorrente de Maven contaminou `target/classes` e não é considerada evidência.
- Segurança HTTP: sessão anônima e docente rejeitadas; mutações com CSRF; `StudentEnrollmentClaimControllerTest` cobre principal derivado somente do ID Token e rate limiting de tentativas.
- Frontend: **425/425 testes**; lint/TypeScript/build aprovados.
- E2E focal: **10/10** em Chromium e WebKit mobile, com API mockada, incluindo POST de resgate, limpeza do segredo e flags OFF.
- Contratos: Flyway V68, versão `0.12.0-dev.3`, verificação de scripts e Compose local/sala aprovadas.
- **Sem evidências ainda:** login real nos provedores externos, avaliação iOS físico, teste funcional com alunos, backup/restore de produção e decisão GO. A [CI inicial da PR #138](https://github.com/Verit-lab/ramuvi/actions/runs/44106105927) passou 3/3; commits de auditoria posteriores exigem nova CI.

**Ressalva de testes:** a suíte de navegador bloqueia PWA service workers para permitir interceptação de APIs mockadas; a regressão de PWA real permanece nos testes existentes do projeto, inclusive CI completa.


## 9. Plano técnico integral: I2 → I3 → I4 (revisão executável)

### 9.1 Fronteiras e invariantes arquiteturais

| Agregado/componente | Responsabilidade | Invariante |
| --- | --- | --- |
| `StudentAccount` | Identidade autenticada, não identidade pedagógica | Um vínculo opcional e único com `Student`; `issuer + sub` validado, revogável |
| `Student` | Registro acadêmico global e histórico do aluno | Não criar ou fundir alunos por e-mail, nickname ou claim de dispositivo |
| `Enrollment` | Participação em turma específica | Estar ativo e pertencer ao mesmo `Student` do principal para conceder acesso |
| `Classroom` | Limites de turma e habilitação de Jornada | Turma desativada ou conteúdo não publicado não aparece como acesso autorizado |
| `StudentEnrollmentInvite` | Prova de aprovação docente para associação | Token de alta entropia, uso único, rotação, expiração, hash e auditoria |
| `StudyPrincipal` / `StudyAccessPolicy` | Autorização para Jornadas e evidências | A I2 **não** concede acesso a `/api/learn/**`; I3 exige novo contrato auditado |
| `SessionParticipant` / `EnrollmentDeviceClaim` | Participação ao vivo e retorno no navegador | Compatibilidade intacta; nunca equivalentes a identidade OIDC |

**Fluxo I2:** professor autenticado → matrícula ativa → convite individual → comunicação segura ao destinatário → aluno valida OIDC → POST com CSRF → serviço abre transação e trava convite + conta → `UNIQUE(student_id)` → marca consumo → aluno consulta apenas turmas ativas. A conta não recebe acesso novo a Jornada nesta fase.

### 9.2 Contratos HTTP e erros

- **Emissão:** `POST /api/student-account-invitations/enrollments/{enrollmentId}`, `ROLE_TEACHER`, CSRF, token **somente na resposta inicial**, `Cache-Control: no-store`. Reemissão revoga todos os anteriores pendentes da mesma matrícula.
- **Revogação:** `DELETE /api/student-account-invitations/{inviteId}`, docente autenticado, CSRF; já consumido não pode ser revogado por esse endpoint (a desassociação exige procedimento separado).
- **Resgate:** `POST /api/student-account/enrollments/claim` com corpo `{"token":"..."}`, `ROLE_STUDENT`, CSRF, somente principal OIDC verificado no backend; sem `accountId`, `studentId`, `email` ou `classroomId` enviado pelo cliente.
- **Descoberta:** `GET /api/student-account/classrooms` devolve apenas matrículas ativas da conta atual, sem PII e sem token de acesso à Jornada.
- **Exceções:** sem sessão 401; papel inadequado/CSRF ausente 403; token inválido, expirado, revogado ou consumido 400 com mensagem genérica; vínculo conflitante 400; abuso 429 com `Retry-After`; flag desativada 404; indisponibilidade de conta revogada resulta em acesso negado.
- **Auditoria posterior:** persistir eventos estruturados sem valor bruto do convite, emitido/rotacionado/revogado/consumido, ID de matrícula, ID de conta e timestamp; definir retenção e acesso administrativo antes do GO.

### 9.3 Gates de engenharia e aceite

| Gate | Verificação / critério | Evidência |
| --- | --- | --- |
| G1 — Integridade | Flyway até V68 sem drift; `student_id` único; nenhuma migração destrutiva nem reatribuição implícita | Maven, PostgreSQL de teste, diff |
| G2 — Identidade | OIDC válido, `issuer/sub` estritamente verificado, revogação ou anonimização invalida operações | testes SQL e HTTP; homologação de token real ainda pendente |
| G3 — Token | Entropia 256 bits, hash único, expiração, rotação, revogação, replay e concorrência tanto de **resgate quanto de emissão** | testes de integração e duas requisições concorrentes |
| G4 — Autorização | docente não resgata, aluno não emite, anônimo não acessa, CSRF obrigatório, dados não cruzam contas, conta desativada ou aluno inativo perde acesso | matriz negativa HTTP e serviços |
| G5 — Multiturmas | mesmo `Student` em N turmas sem links duplicados; desativação retira apenas a turma desativada | integração PostgreSQL |
| G6 — Compatibilidade | Arena, login docente, Jornada, claims de dispositivo e projeção sem regressão | CI de backend, frontend, Compose e E2E |
| G7 — UX mínima | convite visível apenas após emissão, cópia por ação docente; aluno digita sem URL/storage, consegue listar e sair; teclado e mobile | testes E2E Chromium/WebKit + avaliação física pendente |
| G8 — Operação | flags OFF por padrão, backup V68 restaurável, auditoria, privacidade e GO documentado | execução em homologação, não apenas testes sintéticos |

**Definição de pronto para I2 (código):** G1–G7 com CI 3/3 no SHA final, documentação alinhada, nenhuma alteração em `main`, PR draft aguardando decisão. **Definição de pronto para produção:** também G8, credenciais reais dos IdPs, Safari/iOS físico, testes de recuperação, revisão de segurança e autorização explícita.

### 9.4 I3, continuidade pedagógica entre dispositivos

A I3 deve ser implementada **após** revisão da I2, em uma PR independente, sem transformar o `StudyPrincipal` em acesso genérico por role.

1. **Contrato de sessão acadêmica:** `StudentAccount` autenticada → resolução de `Student` vinculado → `Enrollment` ativa para determinada `Classroom`. Toda leitura e escrita de Jornada reconsulta estado e política de publicação; não confiar em `studentId` enviado por query.
2. **Estratégia de compatibilidade:** manter `X-Study-Token` e ingresso por código para convidados. Uma sessão OIDC verificada só poderá percorrer matrículas do seu `Student`. Evitar dois caminhos de escrita com regras divergentes de XP/progresso, centralizando política de autorização.
3. **Plano de migração do progresso:** **não mover registros de progresso** para `student_accounts`; preservar chaves canônicas `Student` + `Enrollment` e reconciliar dados de dispositivos existentes somente após evidência de posse/convite, com trilha de auditoria.
4. **Telas:** Minha conta, Minhas turmas, Jornada, Atividades, Ao vivo, Sair/Trocar conta. Loading/empty/error, sessão expirada e conta compartilhada; design homologado em Figma antes do redesign.
5. **Aceite I3:** reingresso em 2 dispositivos com a mesma conta mantém progresso; outra conta não lê/grava dados; revogação e matrícula desativada bloqueiam imediatamente; desafios de Safari e acessibilidade AA testados.

### 9.5 I4, publicação controlada e reversão

1. Revisar e aprovar ADR-0068/0069/0070 e permissões; aceitar versões e dependências.
2. Homologar Google/Microsoft reais sobre HTTPS com credenciais distintas de integrações delegadas. Testar erro, troca de conta, expiração, logout e CSRF.
3. Criar backup **real e verificável** antes da V68, restaurar em banco isolado, verificar histórico de progresso, matrícula, convidados e compatibilidade; planejar reversão por restauração, **não** prometer downgrade Flyway.
4. Implantação em homologação com flags OFF, monitoramento mínimo (sucesso/falha de login/convite, rejeições, 429, latência) sem tokens/PII em logs. Habilitar em turma piloto restrita com consentimento e procedimento de suporte.
5. Preparar critérios objetivos de parada: 401/403 inesperado, exposição de matrícula entre contas, perda de Jornada/XP, falha de revogação, abuso de convites. GO/NO-GO formal com responsável e evidências; só então tag e release.

### 9.6 Custos e decisões pendentes

- **OIDC:** registrar aplicações Google/Microsoft e consentimento do escopo mínimo; contas sem provedor exigem decidir OTP com e-mail transacional, entregabilidade, limites, recuperação e custos. OTP não pode ser improvisado com e-mail autodeclarado.
- **Compartilhamento de dispositivos:** explicitar ação Sair/Trocar conta e não confiar em localStorage como sessão.
- **Suporte para vínculo incorreto:** definir procedimento manual auditável de transferência/desvinculação sem perda de progresso; não implementar auto-merge.
- **Convites por turma inteira:** opcional futuro; código genérico não deve atribuir automaticamente matrícula de terceiro. Exigir confirmação do docente.
- **Evidência de usuário real:** não disponível até homologação do callback com IdP, Safari físico e piloto.

## 10. Estado de validação verificado em 17/11/2026

A [PR #138](https://github.com/Verit-lab/ramuvi/pull/138), SHA inicial `a901d3c`, já possui [CI oficial 3/3 aprovada](https://github.com/Verit-lab/ramuvi/actions/runs/44106105927) e está em **draft**. Os testes locais anteriores incluíram Maven 184, frontend 425, E2E focal 10/10; esse histórico não substitui revalidar commits posteriores. Não há autorização para merge na `main`, habilitar flags na VPS, executar migrations em produção ou considerar homologado Google/Microsoft real.
