# ADR-0074 — Link de ingresso da turma e vínculo por e-mail com prova de turma

- **Status:** Proposto (implementado na branch `feat/secure-student-onboarding-v0121`; não integrado, não implantado).
- **Data:** 2026-11-19
- **Decisores:** equipe Ramuvi
- **Escopo:** segurança | produto | UX | dados
- **Origem:** auditoria `Ramuvi-Auditoria-Ingresso-Alunos-2026-11-19` (F-03 a F-10, F-12, R-01).

## Contexto

O ADR-0070 só permitia vincular por convite individual: 43 caracteres, gerado aluno por aluno. Uma
turma de 40 alunos exigia 40 convites. A b99bb7e (não integrada) simplificou o fluxo, mas:

- usou `classrooms.code`, o código administrativo escolhido pelo professor, como prova de ingresso. Ele
  pode ter baixa entropia, não expira e já é devolvido a todo aluno em `JourneyStudySummary` (F-05);
- vinculava por e-mail **mesmo com `auto-email-link-enabled=false`**, e a partir de registros de
  **qualquer turma** (F-03, F-07);
- vinculava por e-mail **no login**, sem nenhuma prova de turma (`linkExistingStudentByVerifiedEmail`);
- efetivava o ingresso automaticamente a partir da URL, sem confirmação (F-10);
- prometia "pontuações recuperadas" e "Jornadas disponíveis" sem verificar se isso era verdade (F-09);
- contrariava o ADR-0070:27 sem um ADR substituto (F-04).

## Decisão

1. **Link de ingresso dedicado** (V72, `classroom_join_links`):
   - token aleatório de 160 bits (`SecureRandom`), em base32 de 32 caracteres; só o SHA-256 é persistido;
   - mostrado ao professor **uma única vez**, na resposta do `POST` com `Cache-Control: no-store`;
   - validade de 1 a 30 dias e no máximo um link aberto por turma (índice parcial). Gerar um novo revoga
     o anterior, há revogação explícita, e arquivar a turma revoga o link;
   - opção "exigir aprovação" por link, desligada por padrão;
   - o segredo viaja no **fragmento** (`/learn?account=1#ingresso=TOKEN`), que não chega a servidores,
     proxies, logs de acesso nem ao `Referer`. O front move o token para o `sessionStorage` da aba,
     limpa a barra de endereço e o envia só no corpo de `POST`.
2. **`classrooms.code` nunca autentica.** A V70 (`self_enrollment_enabled`) é preservada como coluna
   morta, sem leitura no código. A rota `POST /api/student-account/classrooms/join` e o
   `PATCH /api/classrooms/{id}/self-enrollment` foram removidos.
3. **Login nunca vincula por e-mail.** O `StudentOidcUserService` só provisiona `(issuer, sub)`.
4. **Ao aceitar um link** (`ClassroomJoinLinkService.accept`), sob lock da conta e do link:
   - conta já vinculada: matricula, ou responde `ALREADY_MEMBER`. Uma matrícula desativada **nunca é
     reativada** (409). Se o link exige aprovação, vira pedido;
   - e-mail considerado só se o issuer for exatamente `https://accounts.google.com` e
     `email_verified=true` no ID Token validado pelo Spring. O e-mail do Entra é ignorado (R-01);
   - **vínculo automático** somente com `auto-email-link-enabled=true`, correspondência exata (índice
     único `lower(email)`), `email_source=TEACHER`, aluno ativo e não anonimizado, sem outra conta, e
     **matrícula ativa na turma do link**;
   - e-mail conhecido fora desses critérios (flag OFF, `email_source=STUDENT`, cadastro de outra turma):
     **pedido para o professor**, sem criar duplicata;
   - e-mail de cadastro já vinculado a outra conta, ou cadastro desativado: recusa explícita (409);
   - sem e-mail correspondente: se a turma tem cadastros sem conta que são **só nome**, ou têm o mesmo
     nome normalizado, vira **pedido** (possível duplicata, F-08). Se o link exige aprovação, também.
     Caso contrário, cria aluno e matrícula. O nome é **apenas sinal de colisão**, nunca prova.
5. **Fila docente** (`classroom_join_requests`):
   - um pedido `PENDING` por turma e conta;
   - aprovar como "cadastro novo" ou "vincular a" um aluno escolhido pelo professor (precisa estar ativo
     e sem conta). O backend nunca escolhe por nome;
   - recusar é definitivo para aquele link;
   - o e-mail verificado é apagado ao decidir, e a anonimização apaga os pedidos.
6. **Rotas e autorização:**
   - docente em `/api/classrooms/{id}/join-link` e `/join-requests` (`ROLE_TEACHER` mais CSRF);
   - aluno em `/api/student-account/classroom-links/{preview,accept,requests}` (`ROLE_STUDENT` mais
     CSRF). Nenhuma rota recebe `accountId`, `studentId` ou e-mail do cliente;
   - IDOR: pedido e turma são casados no SQL (`id` **e** `classroom_id`).
7. **Flags** (todas OFF por padrão): `APP_STUDENT_IDENTITY_CLASSROOM_JOIN_LINKS_ENABLED`, além de OIDC e
   `ENROLLMENT_CLAIM` (mesma condição nos controllers e na capability usada pela UI);
   `APP_STUDENT_IDENTITY_AUTO_EMAIL_LINK_ENABLED` só é consultada no aceite do link.
8. **UX:**
   - prévia "Entrar na turma X?" antes de qualquer efeito;
   - "Tentar novamente" só para erros transitórios (rede, 429, 5xx), e o link pendente sobrevive a eles;
   - mensagens honestas: XP recuperado só com `LINKED_EXISTING_STUDENT`, e "Jornadas aparecerão quando
     o professor liberar" se o acesso a estudos estiver OFF (na turma ou na flag I3);
   - convite individual (ADR-0070) mantido como exceção recolhida.
9. **Limite de tentativas:** tokens inválidos contam por conta mais IP e por IP, sem reset no sucesso.
   A corrida que viola uma `unique` vira 409 "tente novamente", não 500 (F-12).

## Relação com ADRs anteriores

- **ADR-0068:** mantém `StudentAccount` separado de `Student`. O vínculo continua sendo só
  `student_accounts.student_id`, sem mover XP.
- **ADR-0069:** mantém "e-mail e nome não associam automaticamente". O vínculo por e-mail passa a ser
  uma exceção opt-in, condicionada à prova de turma do link e ao `email_verified` do Google.
- **ADR-0070:** substitui em parte a alternativa rejeitada 1 ("autoassociar por e-mail"), agora
  restrita, e a alternativa 2 ("código genérico de turma"), agora com token dedicado e **fila de
  aprovação explícita**, como o próprio ADR-0070 exigia. O convite individual continua válido.
- **Desenho V70 (b99bb7e):** substituído integralmente. A migration não é reescrita.

## Consequências

### Positivas

- Aluno novo: 1 link mais Google. Aluno pré-cadastrado com o mesmo e-mail Google na turma, com a flag
  ON: mesmo `students.id`, XP e progresso. Aluno recorrente: só login. Novas Jornadas chegam pela
  matrícula, sem código.
- Nenhuma duplicata silenciosa quando há indício de colisão.

### Custos e riscos

- Turmas com cadastros só por nome geram pedidos que o professor precisa resolver.
- Contas Microsoft e e-mails Google diferentes do cadastrado criam um cadastro novo, que o professor
  pode unir manualmente. Não há ferramenta de mesclagem.
- Link vazado: qualquer conta Google pode pedir ingresso até expirar ou ser revogado. Use aprovação ou
  validade curta. Não há `hd` (domínio institucional) nem limite de usos.
- O limitador é em memória por instância.
- Não há trilha de auditoria dedicada além de `classroom_join_requests` e `accepted_count`.

## Critérios de validação

- [x] `ClassroomJoinLinkHttpIT` (10 cenários, OIDC real do Spring contra provedor local),
      `ClassroomJoinLinkFlagsHttpIT` (flag de e-mail OFF e limitador),
      `ClassroomJoinLinksDisabledHttpIT` (flag OFF), `SecurityAndMigrationIT` (V71, V72).
- [x] Unit e E2E de front (`classroom-join-link.test.ts`, `onboarding-simple.spec.ts`).
- [ ] Login Google real em HTTPS, iOS/Android físicos e backup/restore V70→V72 antes de ativar.

## Relações

- ADRs relacionados: ADR-0068, ADR-0069, ADR-0070, ADR-0075.
- Substitui: em parte ADR-0070 (alternativas 1 e 2) e o desenho V70 da b99bb7e.
- Substituído por: nenhum.
