# ADR-0071 — Acesso à Jornada pela conta verificada e matrícula canônica

- **Status:** Proposto para revisão na v0.12 I3.
- **Data:** 17/11/2026
- **Relacionados:** ADR-0028, ADR-0053, ADR-0068, ADR-0069, ADR-0070 e issue #119.
- **Base:** `feat/v0.12-enrollment-claim-i2@9e7b796`.

## Contexto

O aluno já tem `Student`, `Enrollment`, `JourneyStageProgress` e a sessão transitória `EnrollmentDeviceClaim`. As contas persistentes OIDC foram adicionadas pela V68, mas o leitor de Jornada ainda exigia `X-Study-Token` do dispositivo. Um novo repositório de progresso por conta geraria duplicação, reconciliação e risco de perda histórica.

## Decisão proposta

1. **Uma identidade acadêmica, duas provas de acesso.** `StudyPrincipal` distingue `DEVICE_CLAIM` e `VERIFIED_ACCOUNT`. A conta autenticada é resolvida *em cada requisição* por `(issuer, sub)` validado e consulta ao banco, recuperando o `Student` vinculado. O cliente não informa accountId, studentId nem enrollmentId.
2. **Autorizar sempre por matrícula.** O serviço `JourneyStudyService` permanece único para leitura, início e conclusão. Para prova `VERIFIED_ACCOUNT`, somente há acesso à Jornada publicada se existir matrícula ativa do aluno na turma, aluno e turma ativos e `student_study_access_enabled=true`. Portas de liberação de capítulos e estados de conteúdo continuam aplicadas.
3. **Sem fallback de autorização.** Uma sessão OIDC ativa não pode herdar permissões de `StudentJourneyAccess` resgatadas por código. Na interface, uma resposta 404/403 ao ler Jornada pela conta não pode disparar tentativa com token local de outro dispositivo. O fluxo legado segue disponível por caminhos próprios.
4. **Persistência canônica.** Escrever `JourneyStageProgress` da matrícula existente; não criar tabela de progresso em `StudentAccount` e não transferir progresso entre alunos por correspondência de nome ou e-mail.
5. **Rotas isoladas.** `/api/student-account/study/journeys/**` exige `ROLE_STUDENT` e CSRF em POST; docente não recebe acesso a partir da sessão de professor. Resposta `no-store`. A feature exige simultaneamente `oidc.enabled`, `enrollment-claim-enabled` e `study-session-enabled`; todas desabilitadas por padrão.
6. **Compatibilidade/reversão.** Permanecem intactos `/api/learn/**`, sessões de Arena e ingressos por código. Desabilitar a feature volta ao fluxo antigo sem mover dados. Nenhuma nova migração além de V68.
7. **UI incremental.** A experiência existente de Jornada reutiliza o leitor; quando a sessão autenticada está habilitada, consulta a API da conta e exibe o progresso canônico. O redesign Figma do roadmap não está autorizado por esta ADR.

## Consequências e trade-offs

- **Benefícios:** progresso consistente entre aparelhos, autorização centralizada, revogação imediata, nenhum esquema acadêmico duplicado.
- **Riscos:** OIDC revogado enquanto há cookies vivos, matrículas suspensas, dispositivo compartilhado, recusa entre accounts, confusão com códigos legados. Mitigação: leitura de conta por solicitação, checagem de matrícula, isolamento de prova e UI de login/saída.
- **Alternativas rejeitadas:** copiar progresso para tabela da conta (drift/migração desnecessária), converter `ROLE_STUDENT` em autorização global (vazamento), transmitir token OIDC por JavaScript/localStorage (maior exposição), substituir fluxo ao vivo antes de homologação (regressão).
- **Limite atual:** este incremento cobre catálogo, leitura, início e conclusão de **conteúdo da Jornada**. Atividades com submissão, provas/quiz, Conquistas e Arena têm contratos próprios ainda associados ao acesso legado e exigem avaliação separada antes de afirmar continuidade completa de toda a plataforma.

## Gates

- Integração PostgreSQL: mesmo `Enrollment` guarda progresso de conta/dispositivo; acesso cross-student, turma desativada, matrícula suspensa, professor sem liberação e identidade revogada são negados.
- Segurança HTTP: anônimo e professor não acessam o endpoint OIDC; mutações sob CSRF; cliente não fornece identidade acadêmica.
- E2E Chromium e WebKit móvel: catálogo com conta, 404 sem fallback local, conclusão no primeiro navegador recuperada no segundo.
- Release: contratos, regressão CI 3/3 no SHA final e homologação com Google/Microsoft reais antes de qualquer habilitação.
