# ADR-0068 — Conta verificada separada do aluno, matrícula e dispositivo

- **Estado:** Proposto para revisão na v0.12 (implementação I0 em PR).
- **Data:** 2026-11-17
- **Questão:** issue #119, identidade cross-device sem assumir que um email digitado prova identidade.
- **Relacionados:** ADR-0028 (Student/Enrollment/device claim/participant token), ADR-0053 (docente único por implantação).

## Decisão proposta

1. **StudentAccount** representa a conta autenticada e não substitui `Student`, `Enrollment`, `EnrollmentDeviceClaim` ou `SessionParticipant`. Uma conta está vinculada a no máximo um `Student`; o `Student` pode ter várias matrículas. A ausência de vínculo é válida durante cadastro.
2. **Identidades externas** pertencem a `StudentAccount` com `(issuer, subject)` único e sensível à caixa, `method`, `verified_at`, `revoked_at`. `email_source=TEACHER/STUDENT` **não constitui prova**. Não reconciliar por nome, matrícula, email ou device claim.
3. **Convite individual** identifica `Enrollment` (não apenas `Classroom`). O professor pode gerar ou revogar convite de 7 dias; cada emissão revoga convites anteriores pendentes da matrícula, e o banco retém somente SHA-256 do token aleatório de 256 bits. A API docente está atrás de feature flag desligada por padrão. Resgate de uso único em transação, com bloqueio de linha, requer conta ativa com identidade verificada e matrícula ativa.
4. **Resgate público fica bloqueado** até existir controlador que obtenha `accountId` de um contexto autenticado no servidor (OIDC ou email OTP). Nunca receber `accountId` como parâmetro controlado pelo navegador. Emissão/revogação pelo professor mantém `ROLE_TEACHER` e CSRF.
5. A **anonimização** remove identidades externas (incluindo endereço de OTP), convites e vínculo, desativa conta, mas preserva Student pseudonimizado, matrícula e histórico/XP com as regras já existentes.
6. Compatibilidade: a migração V68 é aditiva e não cria conta automaticamente nem altera sessão ao vivo, login docente ou fluxo legado `/join`. Nenhum professor multi-tenant é habilitado.

## Consequências

- **Ganhos:** unificação futura entre dispositivos; revogação controlada; associação explícita sem usurpação de matrícula; privacidade e migração reversível no código, sem downgrade automático de esquema.
- **Limites:** a fundação **não é login funcional**. Ainda faltam OIDC Authorization Code/PKCE, OTP por email com entrega/rate limiting, sessão autenticada/CSRF, política de compartilhamento de dispositivos, tratamento de convites para turmas inteiras, aprovação docente, recuperação e interface.
- **Riscos:** convite encaminhado à pessoa errada transfere autoridade da matrícula; a emissão precisa de UX que informe destinatário e risco. O vínculo fica sujeito a `UNIQUE(student_id)` para impedir duas contas reivindicando o mesmo aluno. Devem existir métricas/auditoria de uso e revogação sem gravar token.
- **Desdobramento:** adicionar modelos de autorização e claims tipados no próximo incremento, seguido de migração de sessões e jornadas; não alterar a ADR-0053 sem isolamento multi-docente auditado.

## Evidências e aceite I0

- Flyway V68 validada com 68 migrações, três tabelas novas e verificações contratuais;
- testes de contas verificadas, idempotência, convite seguro, expiração/revogação, impedimento de reutilização/vínculo conflitante e anonimização;
- rota de emissão docente precisa rejeitar cliente anônimo com token CSRF válido;
- testes de regressão existentes aprovados e CI 3/3 na PR antes de merge.

**Critério de revisão:** aceitar ou alterar esta ADR antes de incorporar login funcional e exposição pública de resgate.
