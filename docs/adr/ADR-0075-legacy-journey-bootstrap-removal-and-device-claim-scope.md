# ADR-0075 — Remoção do bootstrap legado de Jornada e escopo dos device claims

- **Status:** Proposto (implementado na branch `feat/secure-student-onboarding-v0121`; não integrado, não implantado).
- **Data:** 2026-11-19
- **Decisores:** equipe Ramuvi
- **Escopo:** segurança | dados | UX
- **Origem:** auditoria `Ramuvi-Auditoria-Ingresso-Alunos-2026-11-19` (F-01, F-02, F-14).

## Contexto

`POST /api/learn/journeys/redeem/bootstrap` era público, sem CSRF e sem limite de tentativas. Recebia o
código compartilhado da Jornada (`J-` mais 6 caracteres, divulgado para a turma inteira) e um texto
livre (matrícula, e-mail **ou nome completo**). Com isso, emitia um `enrollment_device_claim` de 180 dias
(renovado a cada uso) que funciona como `X-Study-Token`. Ou seja: quem tivesse o código e soubesse o
nome de um colega obtinha uma credencial de estudo persistente **como esse colega**. Com ela era
possível ler o perfil (matrícula e e-mail), concluir conteúdos e trocar o e-mail do perfil.

Havia um caminho equivalente fora da Jornada: `POST /api/join/{code}` com `rememberDevice=true`
(ADR-0005). A identidade continua declarada (nome/matrícula/e-mail digitados), mas há o código
projetado da aula ao vivo, com validade de 12 horas. O claim emitido também era aceito como
`X-Study-Token`. Por isso, desligar só o bootstrap não fechava o bypass.

Matrícula ou e-mail digitados **não provam posse**. Restringir o bootstrap a "somente matrícula" não
resolve, porque a matrícula também é informação conhecida por colegas e pode estar impressa em listas.

## Decisão

1. **Bootstrap removido definitivamente, sem flag.** `POST /api/learn/journeys/redeem/bootstrap`
   responde `410 Gone` com orientação para `/learn?account=1`, sem ler o corpo, sem consultar alunos e
   sem emitir claim. O método `JourneyRedemptionService.redeemWithIdentity` foi apagado, e com ele a
   busca por nome, apelido, nome preferido, e-mail ou matrícula.
2. **Escopo do device claim** (migration aditiva V71, coluna `enrollment_device_claims.scope`):
   - `LIVE` (novo padrão de `/api/join`): só reentra em aulas ao vivo (`/api/join/{code}/device*`) e
     consulta a descoberta de aulas (`/api/learn/live`). Recusado com `401` em Jornadas, perfil,
     conquistas e resgate de código.
   - `LIVE_STUDY`: opt-in explícito do operador (`APP_STUDY_LIVE_DEVICE_CLAIM_STUDY_ENABLED=true`,
     padrão `false`). Representa a aceitação documentada do risco do ADR-0005. Validade de 30 dias, sem
     renovação além desse prazo. Deixa de valer para estudo se o opt-in for desligado.
   - `LEGACY`: linhas existentes antes da V71 (bootstrap ou `/api/join`). Ver compatibilidade abaixo.
   - O default da coluna passa a ser `LIVE`, e a entidade recusa emitir `LEGACY`. Um insert que esqueça
     o escopo falha fechado.
3. **Limite de tentativas** com o `joinRateLimitService` existente, por IP de cliente (após o valve de
   proxies confiáveis), em `GET /api/learn/journeys/redeem/{code}` e `POST /api/learn/journeys/redeem`.
   Um acerto não zera a janela, para que um código válido intercalado não "lave" as tentativas.
4. **Caminho legítimo** para estudar: conta OIDC (ADR-0069), mais link de ingresso da turma (ADR-0074)
   ou convite individual (ADR-0070). Depois disso, novas Jornadas chegam pela matrícula, sem código.

## Compatibilidade com acessos já estabelecidos

- **Nenhum claim é apagado ou revogado pela migration.** Os claims `LEGACY` continuam aceitos para
  estudo enquanto `APP_STUDY_LEGACY_DEVICE_CLAIMS_STUDY_ENABLED=true` (padrão, para não cortar
  alunos em uso). Mas **deixam de ser renovados** no uso, então expiram no máximo 180 dias após o
  último uso anterior à V71.
- Não é possível distinguir um claim `LEGACY` legítimo de um obtido por personificação. **Recomendação
  operacional:** depois de migrar a turma para contas verificadas, definir
  `APP_STUDY_LEGACY_DEVICE_CLAIMS_STUDY_ENABLED=false`. Se houver suspeita de abuso, revogar por SQL
  auditado (`update enrollment_device_claims set revoked_at = now() where scope = 'LEGACY'`), depois
  de backup.
- Alunos que dependiam do "lembrar dispositivo" da aula para estudar em casa passam a ver, em
  `/learn/journeys`, a orientação para entrar com conta. A reentrada em aulas ao vivo não muda.

## Consequências

### Positivas

- Fecha a personificação persistente por código compartilhado mais nome (F-01) e o bypass via `/api/join` (F-14).
- A troca de e-mail de perfil por quem tem só um claim de aula deixa de ser possível por padrão.
- Respostas não enumeram alunos: `410` fixo no bootstrap e `401` genérico para claim sem escopo.

### Custos e riscos

- Instalações **sem OIDC** perdem o primeiro acesso a estudos por dispositivo, até habilitarem OIDC ou o
  opt-in `LIVE_STUDY` (que reintroduz o risco do ADR-0005 por até 30 dias e precisa ser aceito por escrito).
- O limitador é em memória por instância (mesma limitação do ADR-0070).
- Claims `LEGACY` comprometidos permanecem válidos até expirar ou até o kill switch ser desligado.

## Alternativas consideradas

- **Aceitar só a matrícula no bootstrap:** rejeitada; matrícula não é segredo.
- **Manter o bootstrap atrás de flag:** rejeitada; o pedido de segurança é impedir definitivamente a emissão
  por informação declarada.
- **Revogar todos os claims na migration:** rejeitada nesta etapa; cortaria alunos sem aviso e não é
  aditiva. Fica como ação operacional documentada.
- **Encurtar o claim `LIVE`:** adiada. Um claim `LIVE` só reentra em aula com código projetado, o que não
  é mais forte que digitar o nome de novo.

## Critérios de validação

- [x] `LegacyStudyCredentialHardeningIT`: `410` sem claim e sem acesso a Jornada para nome, matrícula e
      e-mail da vítima. Claim `LIVE` recusado em Jornadas, perfil, conquistas e resgate, e aceito em
      `/api/learn/live`. `LEGACY` sem renovação, com kill switch. `LIVE_STUDY` com no máximo 30 dias.
      Limitador `404 ×5` e depois `429`.
- [ ] E2E `journey-gradual-release.spec.ts` no stack de CI com o opt-in sintético.
- [ ] Decisão do operador sobre `APP_STUDY_LEGACY_DEVICE_CLAIMS_STUDY_ENABLED` em produção.

## Relações

- ADRs relacionados: ADR-0005 (entrada por código ao vivo), ADR-0068, ADR-0069, ADR-0070, ADR-0074.
- Substitui: parcialmente o bootstrap de primeiro acesso por código de Jornada (v0.10/v0.11).
- Substituído por: nenhum.
