# Ramuvi v0.12.1 — Primeiro acesso simplificado (V70)

**Escopo:** branch de melhoria empilhada sobre a candidata PR #152. Não integrada à main, sem deploy nem alteração do banco de produção.

## Fluxo proposto e implementado

1. Aluno autentica via Google OIDC. Quando `email_verified=true`, a opção de vínculo automático pode procurar um aluno previamente cadastrado pelo professor com **exatamente o mesmo e-mail**, vínculo ainda livre, registro ativo e ao menos uma matrícula ativa. O vínculo preserva o mesmo `students.id` e, consequentemente, XP, progresso, registros e matrículas anteriores. Nomes nunca são usados como prova de identidade.
2. Se ainda não está vinculado, aluno pode entrar no ambiente `/learn?account=1` com um código compartilhado pelo professor ou abrir diretamente `/learn?account=1&classCode=CODIGO`. O código é preservado apenas durante o retorno do login Google e o navegador executa uma única tentativa de ingresso. A API exige OIDC + CSRF e não recebe accountId, studentId, e-mail nem nome informados pelo cliente.
3. O professor habilita **Ingresso por código da turma** em Gerenciar Turma. Começa desativado em todos os registros, inclusive históricos. Só é permitido com turma ativa e código de 8–40 caracteres ASCII alfanuméricos/hífen/underscore. Ao alterar o código ou arquivar, o ingresso compartilhado é desativado. O professor pode copiar o link direto para os alunos.
4. Servidor reutiliza aluno/matrícula existentes quando elegíveis; não recria o registro nem duplica XP. Se não houver cadastro do e-mail verificado e não existir colisão de nome sem e-mail na turma, cria um estudante novo e a matrícula correspondente. Matrícula desativada nunca é reativada automaticamente. Convite individual anterior permanece como alternativa excepcional.
5. Jornadas publicadas ficam disponíveis automaticamente para alunos vinculados se a turma estiver configurada para acesso a estudos. O código de Jornada do fluxo legado continua existindo apenas para acesso explícito sem conta, não como segunda etapa do acesso Google.

## Segurança e limites importantes

- **OIDC é a fonte da identidade**, obtida do Spring Security e de claims assinadas do provedor. Apenas `email_verified=true` autoriza comparação por e-mail; e-mail digitado e nome não autenticam.
- **Apenas e-mail de origem TEACHER** aceita vínculo automático de cadastro existente. `email_source=STUDENT`, e-mail de outra conta, matrícula/estudante desativados ou colisão de nome de cadastro legado exigem conciliação do professor; não mesclar dados sem comprovação.
- Conta Google não deve conferir `ROLE_TEACHER`; endpoints `/api/student-account/**` preservam `ROLE_STUDENT` e proteção CSRF. Ingresso por turma usa limite de tentativas, bloqueio transacional de conta e turma, índice único em `students.email` e constraint de unicidade da matrícula.
- Código da turma **não substitui autenticação** e é compartilhável; deve ser revogável pelo professor, não pode ser tratado como segredo equivalente ao token individual de 256 bits.
- Migração V70: adiciona `classrooms.self_enrollment_enabled` com default FALSE para evitar autoingresso inesperado. Não migra nem exclui estudantes, score_events ou progressos.
- Funcionalidade automatizada de vínculo e ingresso está atrás dos flags operacionais existentes ou novo: `APP_STUDENT_OIDC_ENABLED`, `APP_STUDENT_IDENTITY_ENROLLMENT_CLAIM_ENABLED`, `APP_STUDENT_IDENTITY_AUTO_EMAIL_LINK_ENABLED` (novo; default FALSE). Para que as Jornadas apareçam na conta, `APP_STUDENT_IDENTITY_STUDY_SESSION_ENABLED` e `student_study_access_enabled` da turma devem estar ativos.
- Quando o aluno é cadastrado **somente pelo nome** e há XP associado, o professor precisa conferir o e-mail antes de o sistema ligar a conta, ou utilizar o convite individual. Autoatribuição por nome é insegura e foi deliberadamente excluída.

## Validação e rollout

- `StudentSimpleOnboardingIT`: 11 cenários de PostgreSQL real isolado, incluindo email confiável, aluno inativo, rejeição de email auto-declarado, cadastro novo, idempotência, professor opt-in, matrícula inativa, código rotacionado, nome legado e concorrência.
- `frontend/e2e/onboarding-simple.spec.ts`: retorno do Google com código preservado (simulado), envio do código por POST e erro controlado; Chromium e WebKit.
- Regressão `learner-stabilization.spec.ts` preserva convites individuais.
- Ajustado contrato PWA de teste para comparar o cache ativo à versão **real** no `public/sw.js`, corrigindo um falso negativo na CI da PR #152.
- `scripts/release/verify-v0.12.1-contract.sh` exige V70, defaults seguros e código OIDC. `release-gate.sh` reconhece V70; template de aceites passa a **13 gates NO-GO**, com homologação do novo fluxo.

**Antes de habilitar em produção:** garantir autenticação Google efetiva, sessão persistente, revisão da lista de alunos/e-mails, consentimento e política LGPD, professor habilitando somente turmas adequadas, teste com aluno que já tinha XP, teste de revogação em dispositivo compartilhado e atualização de backup restaurável V69→V70. O código foi preparado, não ativado no servidor.

### Resultados locais de 19/11/2026

- Maven verify (antes do último teste adicional): regressão de integração aprovou 211 testes; o último teste específico de aluno inativo passou na suíte StudentSimpleOnboardingIT de 11/11, sem falhas. Build Java aprovado.
- Frontend: TypeScript, build de produção e 449 testes unitários/contratuais aprovados. Lint sem erros, com avisos históricos.
- E2E local: 20/20 cenários de primeiro acesso e estabilização no Chromium/WebKit móvel; 1/1 regressão de PWA Chromium aprovada, corrigindo o falso negativo de cache da CI #152.
- Scripts de release: 34 testes Python, bash -n, verificador v0.12.1 V70 passaram.
- CI oficial do SHA desta PR e Google OIDC real ainda precisam ser executados e homologados.
