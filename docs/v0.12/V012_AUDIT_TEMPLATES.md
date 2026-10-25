# v0.12 — Templates de auditoria, decisão e gates

**Modelos vazios, não resultados.** Sem evidência, usar PENDENTE. Usar os mesmos IDs do [registro mestre](V012_SCOPE_TRIAGE_REGISTER.md).

## T1 — A0: Tela/rota por persona

| ID/rota | Persona | Componente React | API/contrato | Estado observado | Figma | Preservar/refatorar/remover | Problema/benefício | Evidência | Decisão |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| P-START / descobrir | Professor | PENDENTE | PENDENTE | PENDENTE | Home professor | PENDENTE | — | — | PENDENTE |
| S-JOURNEY / descobrir | Aluno | PENDENTE | PENDENTE | PENDENTE | Mapa/timeline | PENDENTE | — | — | PENDENTE |
| LIVE-STAGE / descobrir | Projetor | PENDENTE | PENDENTE | PENDENTE | Arena/Projetor | PENDENTE | — | — | PENDENTE |

**Estados:** default, loading/skeleton, vazio, erro, offline/reconnect, disabled, foco, sucesso, acesso negado, timeout, conteúdo longo. **Larguras:** 320/360/390, 768, 1024, 1366/1440, 1920, ultrawide e projetor. Testar WCAG, teclado, toque, zoom 200/400%, reduced motion.

## T2 — E0/E1: Refatoração por evidência

| ID | Domínio/arquivo/método | Responsabilidade | Hipótese DRY/SOLID | Reprodução / custo atual | Impacto | Teste de caracterização | Solução candidata | Risco/rollback | Gate |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| E02 | ArenaApp e dependências | mapear | Acoplamento/estado | PENDENTE | PENDENTE | PENDENTE | PENDENTE | PENDENTE | E1/E2 |
| E06 | MechanicsService e realtime | mapear | Estado tipado/concorrência | Revalidar SOLID 20/10 | PENDENTE | PENDENTE | PENDENTE | PENDENTE | E1/E2 |
| E18 | Bootstrap | mapear | N+1/fan-out | >=350 turmas/340 sessões | P0 se confirmado | PENDENTE | PENDENTE | PENDENTE | G0/E1 |

## T3 — Deliberação de escopo

| ID catálogo | Categoria provisória | Evidência | Valor 0–3 | Fricção 0–3 | Qualidade 0–3 | Reuso 0–3 | Esforço 0–3 | Risco 0–3 | Dependências 0–3 | Decisão definitiva | Dono/data | Gate | Release atribuída |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| A09 | 12-DESIGN | PENDENTE | — | — | — | — | — | — | — | PENDENTE | — | A2/A4 | — |
| I02 | PÓS | PENDENTE | — | — | — | — | — | — | — | PENDENTE | — | Auth ADR | — |

## T4 — Checklist de PR autorizado

- Evidência anterior e hipótese testada com baseline.
- Contratos de domínio/API/autorização e estados preservados.
- Teste de caracterização antes de extrair/reordenar código.
- Mudança coesa, pequena e reversível; sem funcionalidades colaterais.
- Lint, TypeScript, backend/DB, E2E Chromium/WebKit, WCAG/mobile/baixo estímulo quando aplicável.
- Medir requests, p95 e comportamento sob carga para performance.
- Plano de rollback e acompanhamento pós-merge.
- Para mudança visual: link Figma **A4 aprovado**, inclusive mobile/estados.
- Aprovação humana, SHA da CI e ata de decisão do item.

## T5 — Registro de gates

| Gate | Evidência / link | Situação (APROVADO/AJUSTAR/REJEITADO/PENDENTE) | Dono/data | Bloqueio |
| --- | --- | --- | --- | --- |
| G0 | — | PENDENTE | — | CI Billing |
| A0 | — | PENDENTE | — | Inventário Figma/rotas |
| E0 | — | PENDENTE | — | Grafo/benchmark |
| A1 | — | PENDENTE | — | Tokens e marca |
| E1 | — | PENDENTE | — | Reproduzir achados |
| A2 | — | PENDENTE | — | Três pilotos |
| E2 | — | PENDENTE | — | Fatiamento |
| A3 | — | PENDENTE | — | Cobertura total |
| A4 | — | PENDENTE | — | Aceite visual |
| E3 | — | PENDENTE | — | Código autorizado |
| E4/A5 | — | PENDENTE | — | CI e homologação final |

**Referências:** [Plano executivo](V012_EXECUTION_PLAN.md) · [Registro mestre](V012_SCOPE_TRIAGE_REGISTER.md).
