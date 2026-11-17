# Ramuvi v0.12 — identidade única do aluno

**Estado:** I0 PR #136, I1 PR #137, I2 PR #138 e I3a de Jornada em branch empilhada. Revisão de ADRs, CI final e GO pendentes. Não é release publicada.
**Base:** `main@6893377940f2761693a6854f5a305f1af71122a1` (CI 3/3 aprovado).
**Branch:** `feat/v0.12-student-account-foundation`.
**Rastreabilidade:** [issue #119](https://github.com/Verit-lab/ramuvi/issues/119), ADR-0068, [roadmap Notion](https://app.notion.com/p/3f9f3eb198c38136b319e5807170f8bf).

## Problema

Ingresso repetido via código de aula/Jornada; a lembrança de dispositivo não autentica uma pessoa em outro aparelho. O email de `Student` é declarativo. A plataforma precisa de conta verificável sem eliminar aula presencial/convite pontual nem antecipar multi-tenant docente.

## Plano de entrega e gates

| Incremento | Resultado | Gate técnico |
| --- | --- | --- |
| **I0 — fundação** | V68 com conta, identidades externas e convites individuais; emissão/revogação docente, resgate somente interno; anonimização revoga vínculo | Testes de esquema, integridade, anonimização, autenticação docente, regressão Java/Next |
| **I1 — autenticação** | OIDC Google/Microsoft Authorization Code + PKCE, issuer/sub verificados; alternativa email OTP se viável | Sessão HttpOnly/Secure/SameSite, CSRF, anti-replay, rate limiting, rotas administrativas permanecem protegidas |
| **I2 — ingresso** | Resgatar convite com conta autenticada; tratamento de associação de aluno existente, convite expirado/recusado, aprovação manual | 2 dispositivos, associação indevida, revogação concorrente, professor e convidado |
| **I3a — Jornada do aluno** | Catálogo multi-turma, capítulos, leitura e progresso pelo OIDC, usando a matrícula canônica | HTTP, PostgreSQL, Chromium/WebKit, revogação e CSRF |
| **I3b — demais experiências** | Atividades/submissões, Arena, Perfil e Conquistas entre dispositivos | Contratos de autorização específicos, Figma e novos testes; pendente |
| **I4 — homologação** | CI 3/3, backup real restaurável em isolado, testes de fluxo e métricas, aprovação do responsável | GO assinado, tag/release/deploy explícitos |

## Implementado em I0

- `V68__student_account_foundation.sql` **aditiva**, sem preencher `student_accounts` a partir dos alunos existentes.
- `StudentAccountService` distingue identidade externa verificada, conta e vínculo `Student`; resgate exige token opaco de uso único e conta já autenticada, atualmente **sem endpoint**.
- Endpoints docentes `POST /api/student-account-invitations/enrollments/{enrollmentId}` e `DELETE /api/student-account-invitations/{inviteId}` protegidos por `ROLE_TEACHER` e CSRF, **desabilitados por padrão** até `APP_STUDENT_IDENTITY_INVITATION_ISSUANCE_ENABLED=true` (`app.student-identity.invitation-issuance-enabled`). Um novo convite revoga os anteriores pendentes. Token não deve ser persistido no navegador nem registrado nos logs.
- `StudentAnonymizationService` remove identidade, convite e vínculo antes de concluir a anonimização.
- Manifestos marcados `0.12.0-dev.1`; contrato `verify-v0.12-contract.sh` mantém gate final indisponível para esta pré-release.

## Limites do incremento

- **Limites da I0:** não havia login discente. A I1 acrescentou login OIDC e tela de acesso atrás de feature flag; **ainda faltam** ativar provedores com credenciais reais, OTP, rota de resgate autenticado, auto-descoberta de turmas e aprovação docente. O redesign/UX geral continua dependente de Figma.
- **Não há rollback seguro de schema por troca de imagem** se V68 já foi aplicada. Backup/restauração em ambiente isolado antes do primeiro deploy.
- **Não deployar V68 na VPS** nem habilitar a funcionalidade como disponível para alunos apenas por ter passado na CI.
- Professor ainda é operador único por implantação (ADR-0053).

## Decisões pendentes

- IdP(s) Google/Microsoft habilitados e política para usuários sem email institucional;
- transporte SMTP/transacional e custo/limites do OTP; recuperação de conta sem link automático por coincidência de email;
- convite específico versus código genérico + aprovação docente, vencimento, revogação e auditoria;
- integração à Jornada e acesso a atividades com invariantes de associação e revogação;
- aceitação de UX no Figma para login, primeira matrícula, múltiplas turmas e aparelhos compartilhados.

## Critérios mínimos de I0

- [x] Maven verify e testes de migração V1–V68 verdes (172 testes na primeira suíte completa; novos testes HTTP incluídos na validação final).
- [x] Frontend lint/typecheck/test/build (425/425 testes) e contratos de pré-release; 12 testes de scripts operacionais aprovados.
- [x] CI 3/3 da PR #136 no SHA da fundação I0 aprovada; cada incremento posterior exige sua própria CI.
- [ ] revisão de segurança da emissão do token e do controle de autorização;
- [ ] PR para revisão, sem merge automático na `main` nem deploy.


## I2 — vínculo de matrícula

- Implementação: `docs/v0.12/ENROLLMENT_I2_TECHNICAL_PLAN.md`.
- ADR proposta: `docs/adr/ADR-0070-student-enrollment-claim-authorization.md`.
- Rotas docentes com convite individual e capabilities; resgate OIDC autenticado com CSRF e limite de tentativas; turmas ativas de múltiplas matrículas; interface aluno e professor sob flags OFF.
- Versão de desenvolvimento `0.12.0-dev.3`, sem migrações posteriores a V68.
- **I3a implementado na branch** `feat/v0.12-verified-journey-i3`: Jornada multi-turma e conclusão por sessão OIDC, mantendo progresso canônico da matrícula. Ver `docs/v0.12/JOURNEY_I3_IMPLEMENTATION.md` e ADR-0071.
- **I3b pendente:** autorização de Atividades/submissões, Arena, Perfil e Conquistas, revisão UX no Figma e piloto presencial.
