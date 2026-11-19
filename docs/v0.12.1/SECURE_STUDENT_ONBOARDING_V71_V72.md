# Ramuvi v0.12.1 — Ingresso seguro de alunos (V71, V72)

Substitui o desenho de `SIMPLE_STUDENT_ONBOARDING_V70.md`. Decisões: ADR-0074 e ADR-0075.
**Estado:** branch local, sem push, sem deploy e sem execução em banco real.

## Migrations (todas aditivas; V70 preservada sem alteração)

| Versão | Conteúdo | Efeito em dados existentes |
|---|---|---|
| V70 | `classrooms.self_enrollment_enabled` (b99bb7e) | Coluna mantida, mas **não é lida** pelo código |
| V71 | `enrollment_device_claims.scope` (`LEGACY`/`LIVE`/`LIVE_STUDY`), default posterior `LIVE` | Claims existentes viram `LEGACY`; nada é revogado |
| V72 | `classroom_join_links` e `classroom_join_requests` | Tabelas novas e vazias |

## Flags (padrão de produção)

| Variável | Padrão | Observação |
|---|---|---|
| `APP_STUDENT_IDENTITY_CLASSROOM_JOIN_LINKS_ENABLED` | `false` | Exige também `APP_STUDENT_OIDC_ENABLED` e `APP_STUDENT_IDENTITY_ENROLLMENT_CLAIM_ENABLED` |
| `APP_STUDENT_IDENTITY_AUTO_EMAIL_LINK_ENABLED` | `false` | Só no aceite do link, com Google mais `email_verified` mais matrícula na mesma turma |
| `APP_STUDENT_IDENTITY_STUDY_SESSION_ENABLED` | `false` | Sem ela, a UI informa que as Jornadas ainda não estão liberadas |
| `APP_STUDY_LEGACY_DEVICE_CLAIMS_STUDY_ENABLED` | `true` | Kill switch dos claims anteriores à V71 (recomendado `false` após migrar para contas) |
| `APP_STUDY_LIVE_DEVICE_CLAIM_STUDY_ENABLED` | `false` | Opt-in de risco (ADR-0005): claim da aula ao vivo também estuda por 30 dias |

Todas foram adicionadas ao `compose.yaml`. O job de E2E do CI liga apenas o opt-in sintético
`APP_STUDY_LIVE_DEVICE_CLAIM_STUDY_ENABLED=true` para `journey-gradual-release.spec.ts`.

## Rollback

1. **Funcional (preferido):** desligar `APP_STUDENT_IDENTITY_CLASSROOM_JOIN_LINKS_ENABLED`. As rotas deixam
   de existir e as matrículas e vínculos já criados permanecem válidos.
2. **Binário para a RC anterior (V70):** o Flyway da versão antiga encontra V71/V72 já aplicadas como
   migrations "futuras". O padrão `ignoreMigrationPatterns=*:future` costuma aceitar isso, mas **não foi
   ensaiado**: repetir o ensaio isolado do ADR-0063 antes de contar com esse caminho. As
   colunas e tabelas extras são ignoradas pelo código antigo (o Hibernate `validate` não reclama de
   colunas extras). **Atenção:** o binário antigo volta a emitir claims pelo bootstrap por nome (P0
   reaberto). Por isso o rollback binário só é aceitável com o endpoint bloqueado no proxy.
3. **Não** apagar V71/V72 de um banco em uso sem backup. Restaurar o backup V70 é o caminho de reversão
   de schema.

## Gates antes de qualquer GO

- `mvn verify` (Testcontainers) e `npm test`, `typecheck`, `lint`, `build`, mais E2E Chromium e WebKit.
- `scripts/release/verify-v0.12.1-contract.sh` (agora cobre V71, V72, o 410 do bootstrap e as flags OFF)
  e `release-gate.sh` (schema esperado 72). O `verify-backup.sh` verifica `scope` e as tabelas V72.
- Homologação manual: Google real em HTTPS, aluno com XP pré-existente, link expirado/revogado,
  aprovação/recusa, celular físico (iOS/Android) e aparelho compartilhado com logout.
- Decisão registrada sobre `APP_STUDY_LEGACY_DEVICE_CLAIMS_STUDY_ENABLED` em produção.

## Limitações conhecidas

Ver o relatório `Ramuvi-Implementacao-Ingresso-2026-11-19.md`: sem `hd`, sem limite de usos por link, sem
mesclagem de cadastros, limitador em memória, sem trilha de auditoria dedicada, painel docente sem E2E
próprio e template de aceite JSON não atualizado.

## Gates específicos do ingresso (antes de GO)

O aceite da v0.12.1 exige 17 gates no template, incluindo:
- vínculo Google verificado somente com link de turma e preservação de ID, XP e Jornada;
- restauração representativa de produção e migrations V71/V72 com checksum e rollback exercitado;
- decisão registrada e configuração efetiva da política de revogação de device claims `LEGACY`;
- login Google real em HTTPS e testes de CSRF, papéis, expiração, revogação e revisão docente;
- definição explícita e comprovada do escopo de autorização de cada professor sobre turmas.

Todas as evidências começam `PENDING`. A aprovação de merge de código não atesta o GO da produção.
