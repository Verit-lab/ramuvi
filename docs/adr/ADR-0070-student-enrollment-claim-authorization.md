# ADR-0070 — Resgate de convite individual com identidade OIDC verificada

**Status:** PROPOSTA (não aceita; PR I2 em revisão).
**Data:** 17/11/2026.
**Contexto:** issue #119, ADR-0028, ADR-0053, ADR-0068 e ADR-0069.

## Contexto e decisão

O cadastro global `Student` já possui diversas `Enrollments`, enquanto `StudentAccount` autentica a pessoa por `issuer + sub`. Nenhum identificador livre (nome, email, registro, código de sessão, claim de dispositivo) comprova que a pessoa é o aluno já cadastrado.

**Decisão proposta:** vínculo entre `StudentAccount` e `Student` somente por **convite individual emitido pelo professor para matrícula existente**, token aleatório 256-bit armazenado apenas como SHA-256. Professor que emite o convite realiza a aprovação prévia. O aluno autentica pelo OIDC e envia o segredo por POST protegido por CSRF. O backend obtém `issuer/sub` do SecurityContext, revalida a identidade e consome o token atomicamente (trava da linha + unicidade da conta + uso único). O mesmo `Student` pode constar em várias turmas e seu vínculo revela apenas matrículas ativas, não dados dos colegas.

Não há criação automática de matrícula; nem login via e-mail não verificado; nem uso do token de dispositivo como prova. **Não alterar** `X-Study-Token` das Jornadas ou os tokens da Arena nesta fase. A visão de turmas é um inventário, não autorização para acessar material ainda não liberado pelo professor.

## Controles

- Role `STUDENT` obrigatória em `/api/student-account/**`; role `TEACHER` para emissão e revogação, CSRF para mutações.
- Revalidar identidade OIDC exata e status da conta, aluno, matrícula e turma a cada operação.
- Convite de 7 dias, uso único, rotação revoga o anterior; expiração, revogação e replay resultam em erro não enumerável.
- Limite de tentativas por identidade verificada e IP usando limitador existente (em memória por instância; aplicações multi-réplica deverão migrar a contadores compartilhados).
- Frontend não põe token em URL, localStorage/sessionStorage ou logs; limpa estado após consumo; resposta HTTP `no-store`.
- Feature flags `APP_STUDENT_OIDC_ENABLED`, `APP_STUDENT_IDENTITY_INVITATION_ISSUANCE_ENABLED` e `APP_STUDENT_IDENTITY_ENROLLMENT_CLAIM_ENABLED` mantidas **OFF** por padrão.
- Edição de e-mail feita pelo professor e device claim nunca autorizam o vínculo. Duas contas tentando o mesmo Student têm conflito fail-closed, sem auto-merge.

## Alternativas rejeitadas ou adiadas

1. **Autoassociar por e-mail:** inseguro; professor pode inserir dados de terceiros.
2. **Código genérico de turma vincula automaticamente:** permitiria apropriação de cadastro; somente com fila de aprovação explícita e modelo de auditoria em release posterior.
3. **Converter device claim em login:** não verifica a identidade nem funciona entre dispositivos com garantias de revogação suficientes.
4. **Introduzir nova tabela account-enrollments:** duplicaria `Student → Enrollment`, criaria drift de integridade e regras de concorrência desnecessárias.

## Consequências e próximos gates

O professor ainda precisará entregar um convite individual ao aluno (custo pedagógico/operacional), e a transferência segura de um cadastro reivindicado equivocadamente requer procedimento de suporte auditado. A tela I2 de turmas é somente uma prévia; o painel completo e continuidade de Jornada entre dispositivos pertencem ao I3.

**Critérios para aceite:** regressão Maven, frontend, E2E Chromium/WebKit, CI 3/3 no SHA e revisão de segurança; autenticação real Google/Microsoft em HTTPS, iOS físico e backup/restore V68 antes de ativar em produção.

Detalhes em `docs/v0.12/ENROLLMENT_I2_TECHNICAL_PLAN.md`.
